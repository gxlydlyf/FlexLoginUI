package com.gxlydlyf.flexloginui;

import fr.xephi.authme.AuthMe;
import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.data.TempbanManager;
import fr.xephi.authme.data.captcha.LoginCaptchaManager;
import fr.xephi.authme.data.captcha.RegistrationCaptchaManager;
import fr.xephi.authme.data.limbo.LimboMessageType;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import fr.xephi.authme.settings.properties.SecuritySettings;
import fr.xephi.authme.util.PlayerUtils;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static com.gxlydlyf.flexloginui.DialogUtil.logCaptchaText;
import static com.gxlydlyf.flexloginui.DialogUtil.regCaptchaText;
import static org.bukkit.Bukkit.getServer;

public class AuthMeUtil {
    private static final AuthMeApi authMeApi = AuthMeApi.getInstance();
    protected static ValidationService validationService;
    protected static Messages messages;
    // 用 Object 接收，不直接定义 DialogAdapter 类型！兼容低版本
    protected static Object dialogAdapter;
    // 是否支持 Dialog（isDialogSupported 的结果）
    protected static boolean isDialogSupported = false;
    protected static CommonService commonService;
    protected static LoginCaptchaManager loginCaptchaManager;
    private static Field useDialogUiField;
    private static Method getPropertyMethod;
    private static RegistrationCaptchaManager registrationCaptchaManager;
    private static LimboService limboService;
    private static TempbanManager tempbanManager;


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
            limboService = (LimboService) getSingletonMethod.invoke(injector, LimboService.class);

            // 获取 DialogAdapter
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
                // 查找并缓存字段 + 方法
                useDialogUiField = RegistrationSettings.class.getField("USE_DIALOG_UI");
                getPropertyMethod = CommonService.class.getMethod("getProperty", ch.jalu.configme.properties.Property.class);
            } catch (Exception ignored) {
                // 不存在就保持 null，后面自动返回 false
            }

            // Captcha
            loginCaptchaManager = (LoginCaptchaManager) getSingletonMethod.invoke(injector, LoginCaptchaManager.class);
            registrationCaptchaManager = (RegistrationCaptchaManager) getSingletonMethod.invoke(injector, RegistrationCaptchaManager.class);

            // Tempban
            tempbanManager = (TempbanManager) getSingletonMethod.invoke(injector, TempbanManager.class);
        } catch (Exception e) {
            FlexLoginUI.logger.severe("获取 AuthMe ValidationService 失败！");
            e.printStackTrace();
        }
    }

    public static boolean isUseDialogUI() {
        // 缓存不存在 → 直接返回 false（旧版本）
        if (useDialogUiField == null) {
            return false;
        }

        return getBooleanSetting(useDialogUiField);
    }

    public static boolean useLoginFailureCaptcha() {
        return getBooleanSetting(SecuritySettings.ENABLE_LOGIN_FAILURE_CAPTCHA);
    }


    public static boolean useRegisterCaptcha() {
        return getBooleanSetting(SecuritySettings.ENABLE_CAPTCHA_FOR_REGISTRATION);
    }

    public static boolean useTempban() {
        return getBooleanSetting(SecuritySettings.TEMPBAN_ON_MAX_LOGINS);
    }

    public static boolean kickOnWrongPassword() {
        return getBooleanSetting(RestrictionSettings.KICK_ON_WRONG_PASSWORD);
    }

    public static boolean isLoginCaptchaRequired(String name) {
        if (loginCaptchaManager == null) {
            return false;
        }
        return useLoginFailureCaptcha() && loginCaptchaManager.isCaptchaRequired(name);
    }

    public static boolean isCaptchaRequired(String name) {
        return authMeApi.isRegistered(name) ? isLoginCaptchaRequired(name) : isRegisterCaptchaRequired(name);
    }

    public static boolean isCaptchaRequired(Player player) {
        return isCaptchaRequired(player.getName());
    }

    public static void increaseLoginFailureCount(String name) {
        if (loginCaptchaManager != null) {
            loginCaptchaManager.increaseLoginFailureCount(name);
        }
    }

    public static void resetLoginFailureCount(String name) {
        if (loginCaptchaManager != null) {
            loginCaptchaManager.resetLoginFailureCount(name);
        }
    }

    public static void increaseTempbanCount(Player player) {
        if (tempbanManager != null) {
            tempbanManager.increaseCount(PlayerUtils.getPlayerIp(player), player.getName());
        }
    }

    public static boolean shouldTempban(Player player) {
        if (tempbanManager != null) {
            return tempbanManager.shouldTempban(PlayerUtils.getPlayerIp(player));
        }
        return false;
    }

    public static void tempbanPlayer(Player player) {
        if (tempbanManager != null) {
            tempbanManager.tempbanPlayer(player);
        }
    }

    public static boolean checkLoginCaptcha(Player player, String code) {
        if (loginCaptchaManager != null) {
            return loginCaptchaManager.checkCode(player, code);
        }
        return false;
    }

    public static boolean checkRegisterCaptcha(Player player, String code) {
        if (registrationCaptchaManager != null) {
            return registrationCaptchaManager.checkCode(player, code);
        }
        return false;
    }

    public static String getLoginCaptcha(String name) {
        if (loginCaptchaManager != null) {
            return loginCaptchaManager.getCaptchaCodeOrGenerateNew(name);
        }
        return null;
    }

    public static boolean isRegisterCaptchaRequired(String name) {
        if (registrationCaptchaManager == null) {
            return false;
        }
        return useRegisterCaptcha() && registrationCaptchaManager.isCaptchaRequired(name);
    }

    public static boolean isRegisterCaptchaRequired(Player player) {
        return isRegisterCaptchaRequired(player.getName());
    }

    public static String getRegisterCaptcha(String name) {
        if (registrationCaptchaManager != null) {
            return registrationCaptchaManager.getCaptchaCodeOrGenerateNew(name);
        }
        return null;
    }

    public static String getRegCaptchaTip(Player player, String tip) {
        String msg = regCaptchaText("tip");
        tip = tip == null ? msg : tip + "\n" + msg;
        String code = getRegisterCaptcha(player.getName());
        return tip.replaceAll("%captcha_code%", code == null ? regCaptchaText("invalid") : code);
    }

    public static String getRegCaptchaTip(Player player) {
        return getRegCaptchaTip(player, null);
    }

    public static String getLogCaptchaTip(Player player, String tip) {
        String msg = logCaptchaText("tip");
        tip = tip == null ? msg : tip + "\n" + msg;
        String code = getLoginCaptcha(player.getName());
        return tip.replaceAll("%captcha_code%", code == null ? logCaptchaText("invalid") : code);
    }

    public static String getLogCaptchaTip(Player player) {
        return getLogCaptchaTip(player, null);
    }

    public static Object getSetting(Object property, Object defaultValue) {
        if (getPropertyMethod == null || commonService == null || property == null) {
            return defaultValue;
        }

        if (property instanceof Field field) {
            try {
                property = field.get(null);
            } catch (IllegalAccessException e) {
                return defaultValue;
            }
        }

        try {
            return getPropertyMethod.invoke(commonService, property);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static boolean getBooleanSetting(Object property) {
        return (boolean) getSetting(property, false);
    }

    public static boolean isEnabledAuthMeDialog() {
        return isDialogSupported && isUseDialogUI();
    }

    public static void sendMessage(Player player, MessageKey key) {
        commonService.send(player, key);
    }

    public static void sendMessage(Player player, MessageKey key, String... replacement) {
        commonService.send(player, key, replacement);
    }

    public static void unmuteMessageTask(Player player) {
        limboService.unmuteMessageTask(player);
    }

    public static void resetMessageTask(Player player, LimboMessageType messageType) {
        limboService.resetMessageTask(player, messageType);
    }

    public static void muteMessageTask(Player player) {
        limboService.muteMessageTask(player);
    }

}
