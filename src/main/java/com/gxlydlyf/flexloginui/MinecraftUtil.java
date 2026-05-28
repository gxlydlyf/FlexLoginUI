package com.gxlydlyf.flexloginui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class MinecraftUtil {
    public static int getAnvilMenuId() {
        try {
            // 1. 获取 BuiltInRegistries 类
            Class<?> builtInRegistriesClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");

            // 2. 获取 MENU 字段
            Field menuField = builtInRegistriesClass.getField("MENU");
            Object menuRegistry = menuField.get(null);

            // 3. 获取 MenuType.ANVIL
            Class<?> menuTypeClass = Class.forName("net.minecraft.world.inventory.MenuType");
            Field anvilField = menuTypeClass.getField("ANVIL");
            Object anvilMenuType = anvilField.get(null);

            // 4. 调用 getId()
            Method getIdMethod = menuRegistry.getClass().getMethod("getId", Object.class);
            return (int) getIdMethod.invoke(menuRegistry, anvilMenuType);

        } catch (Exception e) {
            e.printStackTrace();
            return 8;
        }
    }
}
