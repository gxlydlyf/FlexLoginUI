package com.gxlydlyf.flexloginui;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public class ViaBackwardsUtil {
    public static boolean enabled = false;

    public static void tryLoadHook() {
        if (!enabled) {
            return;
        }

        try {
            // 动态加载 Hook 类
            Class<?> hookClass = Class.forName("com.gxlydlyf.flexloginui.ViaBackwardsHook");

            // 获取方法
            Method interceptDialogsMethod = hookClass.getMethod("interceptDialogs");

            // 调用注册
            interceptDialogsMethod.invoke(null);
        } catch (ClassNotFoundException e) {
            FlexLoginUI.instance.getLogger().warning("ViaBackwars Hook 类未找到，可能编译问题");
            enabled = false;
        } catch (Exception e) {
            FlexLoginUI.instance.getLogger().severe("ViaBackwars Hook 加载失败: " + e.getMessage());
            enabled = false;
        }
    }
}
