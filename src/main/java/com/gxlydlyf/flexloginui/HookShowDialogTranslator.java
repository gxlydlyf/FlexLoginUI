package com.gxlydlyf.flexloginui;

import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.registry.JavaRegistries;
import org.geysermc.geyser.session.dialog.DialogButton;
import org.geysermc.geyser.session.dialog.input.DialogInput;
import org.geysermc.geyser.session.dialog.input.ParsedInputs;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.text.MessageTranslator;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundShowDialogConfigurationPacket;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class HookShowDialogTranslator extends PacketTranslator<ClientboundShowDialogConfigurationPacket> {
    private final PacketTranslator<ClientboundShowDialogConfigurationPacket> originTranslator;

    // 缓存反射方法
    private static Method sendDialogFormMethod;
    private static Method customFormBuilderMethod;
    private static Method buildMethod;
    private static Class<?> customFormBuilderClass;
    private static Class<?> formClass;
    private static Class<?> customFormClass;
    private static Class<?> customFormResponseClass;
    private static Class<?> parsedInputsClass;
    private static Constructor<?> parsedInputsConstructor;
    private static Method parsedInputsHasErrorsMethod;
    private static Method dialogActionRunMethod;

    static {
        try {
            initReflectionMethods();
        } catch (Exception e) {
            FlexLoginUI.logger.severe("Failed to initialize reflection methods: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 初始化方法
    private static void initReflectionMethods() throws Exception {
        if (sendDialogFormMethod != null) {
            return; // 已经初始化过
        }

        // 【关键】使用 GeyserSession 的类加载器
        ClassLoader geyserClassLoader = GeyserApi.api().getClass().getClassLoader();

        // 加载 Geyser 内部的类
        formClass = Class.forName("org.geysermc.cumulus.form.Form", true, geyserClassLoader);
        customFormClass = Class.forName("org.geysermc.cumulus.form.CustomForm", true, geyserClassLoader);
        customFormBuilderClass = Class.forName("org.geysermc.cumulus.form.CustomForm$Builder", true, geyserClassLoader);
        customFormResponseClass = Class.forName("org.geysermc.cumulus.response.CustomFormResponse", true, geyserClassLoader);

        // 加载 ParsedInputs 类
        parsedInputsClass = Class.forName("org.geysermc.geyser.session.dialog.input.ParsedInputs", true, geyserClassLoader);
        parsedInputsConstructor = parsedInputsClass.getConstructor(List.class, customFormResponseClass);
        parsedInputsHasErrorsMethod = parsedInputsClass.getMethod("hasErrors");

        // 加载 DialogAction 的 run 方法
        Class<?> dialogActionClass = Class.forName("org.geysermc.geyser.session.dialog.action.DialogAction", true, geyserClassLoader);
        dialogActionRunMethod = dialogActionClass.getMethod("run", GeyserSession.class, parsedInputsClass);

        // 获取方法
        sendDialogFormMethod = GeyserSession.class.getMethod("sendDialogForm", formClass);
        customFormBuilderMethod = customFormClass.getMethod("builder");
        buildMethod = customFormBuilderClass.getMethod("build");

        if (FlexLoginUI.isDebug()) {
            FlexLoginUI.logger.info("Geyser reflection methods initialized successfully");
        }
    }

    public HookShowDialogTranslator(PacketTranslator<ClientboundShowDialogConfigurationPacket> origin) {
        this.originTranslator = origin;
    }

    @Override
    public void translate(GeyserSession session, ClientboundShowDialogConfigurationPacket packet) {
        if (FlexLoginUI.config.isDebug()) {
            FlexLoginUI.logger.info("Handle Geyser ClientboundShowDialogConfigurationPacket");
        }

        boolean isAuthMe = false;
        NbtMap dialog = packet.getDialog();
        List<NbtMap> actions = dialog.getList("actions", NbtType.COMPOUND, null);
        if (actions != null && !actions.isEmpty()) {
            for (NbtMap action : actions) {
                NbtMap map = action.getCompound("action", null);
                if (map != null) {
                    if (map.getString("id").startsWith("authme:")) {
                        isAuthMe = true;
                        break;
                    }
                }
            }
        }

        if (isAuthMe) {
            try {
                session.prepareForConfigurationForm();
                session.closeForm();

                // 使用反射构建并发送表单
                Object formBuilder = buildAuthMeFormReflective(session, dialog);
                Object customForm = buildMethod.invoke(formBuilder);
                sendDialogFormMethod.invoke(session, customForm);

            } catch (Throwable e) {
                FlexLoginUI.logger.warning("Failed to send dialog form via reflection: " + e.getMessage());
                e.printStackTrace();
                // 回退到原始逻辑
                originTranslator.translate(session, packet);
            }
        } else {
            originTranslator.translate(session, packet);
        }
    }

    /**
     * 使用反射构建 CustomForm.Builder
     */
    private Object buildAuthMeFormReflective(GeyserSession session, NbtMap dialogNbt) throws Exception {
        // 1. 解析标题
        String title = MessageTranslator.convertFromNullableNbtTag(Optional.of(session), dialogNbt.get("title"));
        if (title == null) {
            title = "";
        }

        // 2. 解析 body
        List<String> labelLines = new ArrayList<>();
        NbtMap bodyTag = dialogNbt.getCompound("body");
        if ("minecraft:plain_message".equals(bodyTag.getString("type"))) {
            Object contentsMap = bodyTag.get("contents");
            if (contentsMap instanceof NbtList<?> listMap) {
                listMap.forEach(nbt -> {
                    if (nbt instanceof NbtMap map) {
                        String text = MessageTranslator.convertFromNullableNbtTag(Optional.of(session), map);
                        labelLines.add(text);
                    }
                });
            } else if (contentsMap instanceof NbtMap map) {
                String text = MessageTranslator.convertFromNullableNbtTag(Optional.of(session), map);
                labelLines.add(text);
            }
        }

        // 3. 解析输入框
        List<DialogInput<?>> inputs = new ArrayList<>();
        List<NbtMap> inputTags = dialogNbt.getList("inputs", org.cloudburstmc.nbt.NbtType.COMPOUND);
        for (NbtMap inputTag : inputTags) {
            inputs.add(DialogInput.read(Optional.of(session), inputTag));
        }

        // 4. 解析按钮
        DialogButton submitButton = null;
        DialogButton cancelButton = null;
        List<NbtMap> actionTags = dialogNbt.getList("actions", org.cloudburstmc.nbt.NbtType.COMPOUND);
        for (NbtMap actionTag : actionTags) {
            Optional<DialogButton> btn = DialogButton.read(Optional.of(session), actionTag,
                    key -> JavaRegistries.DIALOG.networkId(session, key));
            if (btn.isPresent()) {
                DialogButton button = btn.get();
                String actionId = actionTag.getCompound("action").getString("id");
                if (actionId.contains("submit")) {
                    submitButton = button;
                } else if (actionId.contains("cancel")) {
                    cancelButton = button;
                }
            }
        }

        // 5. 创建 Builder
        Object builder = customFormBuilderMethod.invoke(null);

        // 6. 设置标题
        Method titleMethod = customFormBuilderClass.getMethod("title", String.class);
        builder = titleMethod.invoke(builder, title);

        // 7. 添加标签行
        Method labelMethod = customFormBuilderClass.getMethod("label", String.class);
        for (String line : labelLines) {
            builder = labelMethod.invoke(builder, line);
        }

        // 8. 添加输入框
        if (!inputs.isEmpty()) {
            Method inputMethod = customFormBuilderClass.getMethod("input", String.class, String.class);
            for (NbtMap inputTag : inputTags) {
                builder = inputMethod.invoke(
                        builder,
                        MessageTranslator.convertFromNullableNbtTag(Optional.of(session),
                                inputTag.getCompound("label")),
                        ""
                );
            }
        }

        // 9. 设置表单处理器 - 使用 Consumer<Object> 而不是 Consumer<CustomFormResponse>
        final DialogButton finalSubmit = submitButton;
        final List<DialogInput<?>> finalInputs = inputs;
        Method validResultHandlerMethod = customFormBuilderClass.getMethod("validResultHandler", Consumer.class);

        // 创建 Consumer<Object> - 接受任意对象
        Consumer<Object> responseConsumer = responseObj -> {
            try {
                if (finalSubmit != null && finalSubmit.action().isPresent() && responseObj != null) {
                    // 使用反射创建 ParsedInputs
                    Object parsedInputs = parsedInputsConstructor.newInstance(finalInputs, responseObj);

                    // 检查是否有错误
                    boolean hasErrors = (boolean) parsedInputsHasErrorsMethod.invoke(parsedInputs);

                    if (!hasErrors) {
                        // 获取 DialogAction
                        Object dialogAction = finalSubmit.action().get();

                        // 使用反射调用 run 方法
                        dialogActionRunMethod.invoke(dialogAction, session, parsedInputs);
                    }
                }
            } catch (Exception e) {
                FlexLoginUI.logger.severe("Error handling form response: " + e.getMessage());
                e.printStackTrace();
            }
        };

        builder = validResultHandlerMethod.invoke(builder, responseConsumer);

        // 10. 设置关闭处理器
        final DialogButton finalCancel = cancelButton;
        Method closedOrInvalidResultHandlerMethod = customFormBuilderClass.getMethod("closedOrInvalidResultHandler", Runnable.class);

        Runnable closeHandler = () -> {
            if (finalCancel != null && finalCancel.action().isPresent()) {
                finalCancel.action().get().run(session, ParsedInputs.EMPTY);
            }
        };

        builder = closedOrInvalidResultHandlerMethod.invoke(builder, closeHandler);

        return builder;
    }
}