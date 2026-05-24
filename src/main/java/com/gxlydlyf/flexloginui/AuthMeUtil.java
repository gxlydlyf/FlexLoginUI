package com.gxlydlyf.flexloginui;

import fr.xephi.authme.AuthMe;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.service.ValidationService;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.bukkit.Bukkit.getServer;

public class AuthMeUtil {
    public static ValidationService validationService;
    public static Messages messages;


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

        } catch (Exception e) {
            FlexLoginUI.logger.severe("获取 AuthMe ValidationService 失败！");
            e.printStackTrace();
        }
    }
}
