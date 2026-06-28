package com.gxlydlyf.flexloginui;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.chat.clickevent.CustomClickEvent;
import com.github.retrooper.packetevents.protocol.dialog.CommonDialogData;
import com.github.retrooper.packetevents.protocol.dialog.DialogAction;
import com.github.retrooper.packetevents.protocol.dialog.MultiActionDialog;
import com.github.retrooper.packetevents.protocol.dialog.action.DynamicCustomAction;
import com.github.retrooper.packetevents.protocol.dialog.action.StaticAction;
import com.github.retrooper.packetevents.protocol.dialog.body.PlainMessage;
import com.github.retrooper.packetevents.protocol.dialog.body.PlainMessageDialogBody;
import com.github.retrooper.packetevents.protocol.dialog.button.ActionButton;
import com.github.retrooper.packetevents.protocol.dialog.button.CommonButtonData;
import com.github.retrooper.packetevents.protocol.dialog.input.*;
import com.github.retrooper.packetevents.protocol.nbt.NBTByte;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerShowDialog;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.ViaAPI;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

import static com.gxlydlyf.flexloginui.PacketListeners.getUser;
import static com.gxlydlyf.flexloginui.ViaVersionUtil.sendDialog;

public class DialogUtil {
    private static final ConfigUtil config = FlexLoginUI.config;
    public static final String DIALOG_NAMESPACE = "flex_login_ui";
    public static final String LOGIN_DIALOG_ID = DIALOG_NAMESPACE + ":login";
    public static final String REGISTER_DIALOG_ID = DIALOG_NAMESPACE + ":register";
    public static final String CAPTCHA_DIALOG_ID = DIALOG_NAMESPACE + ":captcha";
    public static final String REGISTER_CAPTCHA_DIALOG_ID = CAPTCHA_DIALOG_ID + "/register";
    public static final String LOGIN_CAPTCHA_DIALOG_ID = CAPTCHA_DIALOG_ID + "/login";

    // ---------- 基础对话框构造器 ----------
    private static DialogEncoder.DialogDefinition createBaseDialog(String titleText, String headText) {
        DialogEncoder.DialogDefinition def = new DialogEncoder.DialogDefinition();
        def.type = "minecraft:multi_action";
        def.title = new DialogEncoder.TextComponent(titleText);
        def.canCloseWithEscape = false;
        def.pause = true;

        // body 提示文字
        DialogEncoder.BodyElement hint = new DialogEncoder.BodyElement("minecraft:plain_message");
        hint.contents = new DialogEncoder.TextComponent(headText);
        hint.width = 200;
        def.body.add(hint);

        def.columns = 1;

        return def;
    }

    // ---------- 添加输入框的通用方法 ----------
    private static void addInputControl(DialogEncoder.DialogDefinition def, String key, String label,
                                        int maxLength) {
        DialogEncoder.InputControl input = new DialogEncoder.InputControl("minecraft:text", key,
                new DialogEncoder.TextComponent(label));
        input.width = 150;
        input.maxLength = maxLength;
        def.inputs.add(input);
    }

    // ---------- 添加按钮的通用方法 ----------
    private static void addButton(DialogEncoder.DialogDefinition def, String buttonText) {
        DialogEncoder.Button btn = new DialogEncoder.Button(new DialogEncoder.TextComponent(buttonText));
        def.actions.add(btn);
    }

    private static void addButtonWithAction(DialogEncoder.DialogDefinition def, String buttonText,
                                            String actionId) {
        DialogEncoder.DialogAction action = DialogEncoder.DialogAction.createDynamicCustom(actionId);
        DialogEncoder.Button btn = new DialogEncoder.Button(new DialogEncoder.TextComponent(buttonText), action);
        def.actions.add(btn);
    }


    private static void addExitButton(DialogEncoder.DialogDefinition def, String buttonText, String actionId) {
        // 创建 additions 的 ByteBuf
        ByteBuf additions = Unpooled.buffer();
        NbtEncoder.tagBoolean(additions, "close", true);
        DialogEncoder.DialogAction action = DialogEncoder.DialogAction.createDynamicCustom(actionId, additions);
        DialogEncoder.Button btn = new DialogEncoder.Button(new DialogEncoder.TextComponent(buttonText), action);
        def.actions.add(btn);
    }

    // ---------- 重构后的登录对话框 ----------
    public static DialogEncoder.DialogDefinition createLoginDialog(String titleText, String headText,
                                                                   String passwordKey, String passwordLabel,
                                                                   String loginButtonText, String closeButtonText) {
        return createSingleInputDialog(LOGIN_DIALOG_ID,
                titleText, headText,
                passwordKey, passwordLabel,
                loginButtonText, closeButtonText);
    }

    // ---------- 重构后的注册对话框 ----------
    public static DialogEncoder.DialogDefinition createRegisterDialog(String titleText, String headText,
                                                                      String passwordKey, String passwordLabel,
                                                                      String confirmKey, String confirmLabel,
                                                                      String regButtonText, String closeButtonText) {
        DialogEncoder.DialogDefinition def = createBaseDialog(titleText, headText);

        // 密码输入框
        addInputControl(def, passwordKey, passwordLabel, 32);

        // 确认密码输入框
        addInputControl(def, confirmKey, confirmLabel, 32);

        // 按钮
        addButtonWithAction(def, regButtonText, REGISTER_DIALOG_ID);
        addExitButton(def, closeButtonText, REGISTER_DIALOG_ID);
        if (isHorizontalButtons()) {
            def.columns = 2;
        } else {
            def.columns = 1;
        }

        return def;
    }

    public static DialogEncoder.DialogDefinition createSingleInputDialog(String actionId, String titleText, String headText,
                                                                         String inputKey, String inputLabel,
                                                                         String submitButtonText, String closeButtonText) {
        DialogEncoder.DialogDefinition def = createBaseDialog(titleText, headText);

        addInputControl(def, inputKey, inputLabel, 32);

        addButtonWithAction(def, submitButtonText, actionId);
        addExitButton(def, closeButtonText, actionId);
        if (isHorizontalButtons()) {
            def.columns = 2;
        } else {
            def.columns = 1;
        }

        return def;
    }

    public static DialogEncoder.DialogDefinition createLogCaptchaDialog(String tip) {
        return createSingleInputDialog(
                LOGIN_CAPTCHA_DIALOG_ID,
                logCaptchaText("title"), tip,
                "captcha", logCaptchaText("label"),
                logCaptchaText("verify"), getLoginCloseButtonText()
        );
    }


    public static DialogEncoder.DialogDefinition createRegCaptchaDialog(String tip) {
        return createSingleInputDialog(
                REGISTER_CAPTCHA_DIALOG_ID,
                regCaptchaText("title"), tip,
                "captcha", regCaptchaText("label"),
                regCaptchaText("verify"), getRegisterCloseButtonText()
        );
    }

    public static boolean isHighServerVersion() {
        return PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_21_6);
    }

    public static String loginText(String key) {
        return config.getLoginText(key);
    }

    public static String registerText(String key) {
        return config.getRegisterText(key);
    }

    public static String regCaptchaText(String key) {
        return config.getRegCaptchaText(key);
    }

    public static String logCaptchaText(String key) {
        return config.getLogCaptchaText(key);
    }


    public static boolean isHorizontalButtons() {
        return config.getBoolean("pages.dialog.horizontal_buttons");
    }

    public static boolean allowClose() {
        return config.getBoolean("pages.dialog.allow_close");
    }

    public static String getLoginCloseButtonText() {
        return allowClose() ? loginText("close_button") : loginText("exit_button");
    }

    public static void sendLoginDialog(Player player, String headText) {
        if (isHighServerVersion()) {
            sendNativeLoginDialog(player, headText);
        } else {
            DialogEncoder.DialogDefinition def = createLoginDialog(
                    loginText("title"), headText,
                    "password", loginText("password_label"),
                    loginText("login_button"), getLoginCloseButtonText()
            );
            sendDialog(player, def);
        }
    }

    public static void sendLoginDialog(Player player) {
        sendLoginDialog(player, loginText("tip"));
    }

    @Deprecated
    public static void sendTestNotice(Player player) {
        DialogEncoder.DialogDefinition def = DialogEncoder.createTestNoticeDialog();
        sendDialogRaw(player, def);
    }

    public static String getRegisterCloseButtonText() {
        return allowClose() ? registerText("close_button") : registerText("exit_button");
    }

    public static void sendRegisterDialog(Player player, String headText) {
        if (isHighServerVersion()) {
            sendNativeRegisterDialog(player, headText);
        } else {
            DialogEncoder.DialogDefinition def = createRegisterDialog(
                    registerText("title"), headText,
                    "password", registerText("password_label"),
                    "confirm", registerText("confirm_label"),
                    registerText("register_button"), getRegisterCloseButtonText()
            );
            sendDialog(player, def);
        }
    }

    public static void sendRegisterDialog(Player player) {
        sendRegisterDialog(player, registerText("tip"));
    }

    public static void sendLogCaptchaDialog(Player player, String tip) {
        if (isHighServerVersion()) {
            sendNativeLogCaptchaDialog(player, tip);
        } else {
            sendDialog(player, createLogCaptchaDialog(tip));
        }
    }

    public static void sendRegCaptchaDialog(Player player, String tip) {
        if (isHighServerVersion()) {
            sendNativeRegCaptchaDialog(player, tip);
        } else {
            sendDialog(player, createRegCaptchaDialog(tip));
        }
    }

    @Deprecated
    private static void sendDialogRaw(Player player, DialogEncoder.DialogDefinition def) {
        ViaAPI<Player> viaAPI = Via.getAPI();
        if (!Objects.requireNonNull(viaAPI.getConnection(player.getUniqueId())).getProtocolInfo().protocolVersion().newerThanOrEqualTo(ProtocolVersion.v1_21_6)) {
            return;
        }
        ByteBuf buffer = DialogEncoder.buildPacket(def);
        try {
            // retainedDuplicate 是必要的
            viaAPI.sendRawPacket(player.getUniqueId(), buffer.retainedDuplicate());
            if (config.isDebug()) {
                System.out.println("已通过 ViaVersion 发送对话框给 " + player.getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (buffer.refCnt() > 0) {
                buffer.release();
            }
        }
    }

    @SuppressWarnings({"PatternValidation"})
    public static void sendCommonDialog(
            Player player,
            String title,
            String contentText,
            List<Input> inputs,
            String actionPath,
            String buttonText,
            String closeText
    ) {
        User user = getUser(player);
        // 1. 消息内容
        PlainMessage message = new PlainMessage(Component.text(contentText), 200);
        PlainMessageDialogBody body = new PlainMessageDialogBody(message);

        // 2. 对话框基础数据
        CommonDialogData commonData = new CommonDialogData(
                Component.text(title),
                null,
                false,
                true,
                DialogAction.CLOSE,
                List.of(body),
                inputs
        );

        // 3. 主操作按钮
        ActionButton actionButton = new ActionButton(
                new CommonButtonData(
                        Component.text(buttonText),
                        Component.empty(),
                        150
                ),
                new DynamicCustomAction(
                        new ResourceLocation(DIALOG_NAMESPACE, actionPath),
                        null
                )
        );

        // 4. 关闭按钮（固定逻辑）
        NBTCompound closeCompound = new NBTCompound();
        closeCompound.setTag("close", new NBTByte((byte) 1));

        ActionButton closeButton = new ActionButton(
                new CommonButtonData(
                        Component.text(closeText),
                        Component.empty(),
                        150
                ),
                new StaticAction(
                        new CustomClickEvent(
                                new ResourceLocation(DIALOG_NAMESPACE, actionPath),
                                closeCompound
                        )
                )
        );

        // 5. 构建并发送对话框
        MultiActionDialog dialog = new MultiActionDialog(
                commonData,
                List.of(actionButton, closeButton),
                null,
                isHorizontalButtons() ? 2 : 1
        );
        WrapperPlayServerShowDialog packet = new WrapperPlayServerShowDialog(dialog);
        user.sendPacket(packet);
        if (config.isDebug()) {
            System.out.println("已通过 PacketEvents 发送对话框给 " + player.getName());
        }
    }

    public static Input createInput(String key, String label) {
        return new Input(
                key,
                new TextInputControl(
                        150,
                        Component.text(label),
                        true,
                        "",
                        32,
                        null
                )
        );
    }

    public static void sendNativeLoginDialog(Player player, String headText) {
        sendCommonDialog(
                player,
                loginText("title"),               // 标题
                headText,                 // 内容
                List.of(
                        createInput("password", loginText("password_label"))
                ),     // 输入框
                "login",              // 行为ID
                loginText("login_button"),
                getLoginCloseButtonText()
        );
    }


    public static void sendNativeRegisterDialog(Player player, String headText) {
        sendCommonDialog(
                player,
                registerText("title"),               // 标题
                headText,                 // 内容
                List.of(
                        createInput("password", registerText("password_label")),
                        createInput("confirm", registerText("confirm_label"))
                ), // 两个输入框
                "register",           // 行为ID
                registerText("register_button"),
                getRegisterCloseButtonText()
        );
    }


    public static void sendNativeRegCaptchaDialog(Player player, String tip) {
        sendCommonDialog(
                player,
                regCaptchaText("title"),
                tip,
                List.of(
                        createInput("captcha", regCaptchaText("label"))
                ),
                "captcha/register",
                regCaptchaText("verify"),
                getRegisterCloseButtonText()
        );
    }

    public static void sendNativeLogCaptchaDialog(Player player, String tip) {
        sendCommonDialog(
                player,
                logCaptchaText("title"),
                tip,
                List.of(
                        createInput("captcha", logCaptchaText("label"))
                ),
                "captcha/login",
                logCaptchaText("verify"),
                getLoginCloseButtonText()
        );
    }
}