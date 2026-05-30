package com.gxlydlyf.flexloginui;

import fr.xephi.authme.AuthMe;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.bukkit.Bukkit.getServer;

public class AuthMeUtil {
    protected static ValidationService validationService;
    protected static Messages messages;
    // 用 Object 接收，不直接定义 DialogAdapter 类型！兼容低版本
    protected static Object dialogAdapter;
    // 是否支持 Dialog（isDialogSupported 的结果）
    protected static boolean isDialogSupported = false;
    protected static CommonService commonService;
    private static Field useDialogUiField;
    private static Method getPropertyMethod;


    public static void getAuthMe() {
        try {
            // 1. 获取 AuthMe 实例
            Plugin authMePlugin = getServer().getPluginManager().getPlugin("AuthMe");
            if (!(authMePlugin instanceof AuthMe)) {
                return;
            }

            // 2. 反射获取 private injector 字段（不做类型强转！）
            Field injectorField = AuthMe.class.getDeclaredField("injector");
            injectorField.setAccessible(true);
            Object injector = injectorField.get(authMePlugin); // 用 Object 接收！避免类冲突

            // 3. 反射调用 getSingleton 方法（完全不接触 ch.jalu.injector 类！）
            Method getSingletonMethod = injector.getClass().getMethod("getSingleton", Class.class);
            validationService = (ValidationService) getSingletonMethod.invoke(injector, ValidationService.class);
            messages = (Messages) getSingletonMethod.invoke(injector, Messages.class);

            // 从 injector 获取 CommonService（你需要的）
            commonService = (CommonService) getSingletonMethod.invoke(injector, CommonService.class);

            // 获取 DialogAdapter】
            try {
                // 用 Class.forName 动态加载，不存在就跳过，不会报错
                Class<?> dialogAdapterClass = Class.forName("fr.xephi.authme.platform.DialogAdapter");
                // 从 injector 获取实例
                dialogAdapter = getSingletonMethod.invoke(injector, dialogAdapterClass);

                // 调用 isDialogSupported() 获取支持状态
                if (dialogAdapter != null) {
                    Method isSupportedMethod = dialogAdapterClass.getMethod("isDialogSupported");
                    isDialogSupported = (boolean) isSupportedMethod.invoke(dialogAdapter);
                }
            } catch (ClassNotFoundException ignored) {
                // 低版本 AuthMe 没有 DialogAdapter，安全忽略
                isDialogSupported = false;
            } catch (Exception e) {
                // 获取 Dialog 失败也不影响主逻辑
                isDialogSupported = false;
            }

            // AuthMe 配置 USE_DIALOG_UI
            try {
                // 查找并缓存字段 + 方法（只执行1次！）
                useDialogUiField = RegistrationSettings.class.getField("USE_DIALOG_UI");
                getPropertyMethod = CommonService.class.getMethod("getProperty", Class.forName("ch.jalu.configme.properties.Property"));
            } catch (Exception ignored) {
                // 不存在就保持 null，后面自动返回 false
            }
        } catch (Exception e) {
            FlexLoginUI.logger.severe("获取 AuthMe ValidationService 失败！");
            e.printStackTrace();
        }
    }

    public static boolean isUseDialogUI() {
        // 缓存不存在 → 直接返回 false（旧版本）
        if (useDialogUiField == null || getPropertyMethod == null || commonService == null) {
            return false;
        }

        try {
            // 从缓存字段获取属性
            Object property = useDialogUiField.get(null);
            return (boolean) getPropertyMethod.invoke(commonService, property);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isEnabledAuthMeDialog() {
        return isDialogSupported && isUseDialogUI();
    }
}
