package com.gxlydlyf.flexloginui;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.Direction;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Objects;

import static com.gxlydlyf.flexloginui.PacketListeners.getUser;

public class ViaVersionUtil {
    public static boolean enabled = false;
    private static Method sendDialogMethod;

    /**
     * 尝试加载 ViaVersion Hook（在 onEnable 中调用）
     */
    public static void tryLoadHook() {
        if (!enabled) {
            return;
        }

        try {
            // 检查 ViaVersion 核心类是否存在
            Class.forName("com.viaversion.viaversion.api.Via");

            // 动态加载 Hook 类
            Class<?> hookClass = Class.forName("com.gxlydlyf.flexloginui.ViaVersionHook");

            // 获取方法
            Method registerPacketMethod = hookClass.getMethod("registerPacket");
            sendDialogMethod = hookClass.getMethod("sendDialog", Player.class, DialogEncoder.DialogDefinition.class);

            // 调用注册
            registerPacketMethod.invoke(null);

//            FlexLoginUI.instance.getLogger().info("ViaVersion Hook 已加载");
        } catch (ClassNotFoundException e) {
            FlexLoginUI.instance.getLogger().warning("ViaVersion Hook 类未找到，可能编译问题");
            enabled = false;
        } catch (Exception e) {
            FlexLoginUI.instance.getLogger().severe("ViaVersion Hook 加载失败: " + e.getMessage());
            enabled = false;
        }
    }

    public static void sendDialog(Player player, DialogEncoder.DialogDefinition def) {
        if (!enabled) {
            return;
        }
        try {
            if (sendDialogMethod != null) {
                sendDialogMethod.invoke(null, player, def);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static UserConnection getUserConnection(Player player) {
        return Via.getAPI().getConnection(player.getUniqueId());
    }

    @SuppressWarnings("unchecked")
    public static boolean isLowVersion(Player player) {
        if (!enabled) {
            return !DialogUtil.isHighServerVersion();
        }
        return Via.getAPI().getPlayerProtocolVersion(player).olderThan(ProtocolVersion.v1_21_6);
    }
}