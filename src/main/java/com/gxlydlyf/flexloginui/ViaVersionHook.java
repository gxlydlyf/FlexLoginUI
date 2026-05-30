package com.gxlydlyf.flexloginui;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.Tag;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.ProtocolInfo;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.ProtocolManager;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.protocol.packet.VersionedPacketTransformer;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.Protocol1_21_5To1_21_6;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.packet.ClientboundPackets1_21_6;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.packet.ServerboundPackets1_21_6;
import com.viaversion.viaversion.util.Key;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class ViaVersionHook {
    private static Player getPlayer(UserConnection user) {
        return Bukkit.getPlayer(user.getProtocolInfo().getUuid());
    }

    public static void registerPacket() {
        // 在 1.21.6 最后的转换层注册监听器
        Protocol1_21_5To1_21_6 protocol = Via.getManager().getProtocolManager().getProtocol(Protocol1_21_5To1_21_6.class);
        if (protocol != null) {
            protocol.appendServerbound(ServerboundPackets1_21_6.PLAYER_LOADED, wrapper -> {
                PacketListeners.handlePlayerDialog(getPlayer(wrapper.user()));
            });
            protocol.appendServerbound(ServerboundPackets1_21_6.CUSTOM_CLICK_ACTION, wrapper -> {
                // 1. 读取 ID
                Key identifier = wrapper.passthrough(Types.IDENTIFIER);

                boolean isLogin = identifier.equals(DialogUtil.LOGIN_DIALOG_ID);
                boolean isRegister = identifier.equals(DialogUtil.REGISTER_DIALOG_ID);

                if (isLogin || isRegister) {
                    // 2. 读取 NBT
                    Tag payloadTag = wrapper.passthrough(Types.CUSTOM_CLICK_ACTION_TAG);

                    // 核心：读取 password
                    if (payloadTag instanceof CompoundTag compound) { // 转成 CompoundTag
                        boolean close = compound.getBoolean("close", false);
                        PacketListeners.handleCustomClickAction(
                                getPlayer(wrapper.user()),
                                isLogin,
                                close,
                                compound.getString("password"),
                                compound.getString("confirm")
                        );
//                        System.out.println("Payload: " + payloadTag);
                    }
                }

                wrapper.resetReader();
            });
        }
    }

    public static void sendDialog(Player player, DialogEncoder.DialogDefinition def) {
        if (!ViaVersionUtil.enabled) {
            return;
        }
        UserConnection user = Via.getAPI().getConnection(player.getUniqueId());
        if (user == null) return;
        ProtocolInfo protocolInfo = user.getProtocolInfo();
        if (!protocolInfo.protocolVersion().newerThanOrEqualTo(ProtocolVersion.v1_21_6)) {
            return;
        }
        if (!protocolInfo.getClientState().equals(State.PLAY)) {
            return;
        }

        // 1. 构造 1.21.6 的 dialog 包内容
        ByteBuf buffer = Unpooled.buffer();
        def.writeTo(buffer);

        try {
            ProtocolManager protocolManager = Via.getManager().getProtocolManager();

            VersionedPacketTransformer<ClientboundPackets1_21_6, ?> transformer =
                    protocolManager.createPacketTransformer(
                            ProtocolVersion.v1_21_6,
                            ClientboundPackets1_21_6.class,  // 发往客户端 = Clientbound
                            null
                    );

            // 2. 创建 PacketWrapper（基于 1.21.6 包版本）
            PacketWrapper wrapper = PacketWrapper.create(
                    ClientboundPackets1_21_6.SHOW_DIALOG,
                    buffer,
                    user
            );

            // 3. 【关键】自动转换版本 + 发送
            boolean success = transformer.scheduleSend(wrapper);

            if (FlexLoginUI.config.isDebug()) {
                if (success) {
                    System.out.println("已通过 ViaVersion 发送对话框给 " + player.getName());
                } else {
                    System.out.println("ViaVersion 对话框发送被取消");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (buffer.refCnt() > 0) {
                buffer.release();
            }
        }
    }

}