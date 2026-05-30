package com.gxlydlyf.flexloginui;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.nbt.tag.Tag;
import com.viaversion.viabackwards.protocol.v1_21_6to1_21_5.Protocol1_21_6To1_21_5;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.minecraft.Holder;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.protocol.packet.mapping.PacketMapping;
import com.viaversion.viaversion.api.protocol.packet.mapping.PacketMappings;
import com.viaversion.viaversion.api.protocol.remapper.PacketHandler;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.packet.ClientboundConfigurationPackets1_21_6;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.packet.ClientboundPackets1_21_6;

import java.lang.reflect.Field;

public class ViaBackwardsHook {
    public static void interceptDialogs() {
        Protocol1_21_6To1_21_5 protocol = Via.getManager().getProtocolManager()
                .getProtocol(Protocol1_21_6To1_21_5.class);
        if (protocol == null) {
            FlexLoginUI.logger.warning("Protocol1_21_6To1_21_5 not found, skipping dialog interception.");
            return;
        }
        // 获取原始的 PacketHandler（通过反射读取 clientboundMappings）
        PacketHandler originalPlayHandler = getOriginalHandler(protocol,
                State.PLAY,
                ClientboundPackets1_21_6.SHOW_DIALOG.getId());
        PacketHandler originalConfigHandler = getOriginalHandler(protocol,
                State.CONFIGURATION,
                ClientboundConfigurationPackets1_21_6.SHOW_DIALOG.getId());

        if (originalPlayHandler == null || originalConfigHandler == null) {
            FlexLoginUI.logger.warning("Failed to retrieve original SHOW_DIALOG handlers.");
            return;
        }

        // 替换 PLAY 状态的 SHOW_DIALOG
        protocol.replaceClientbound(ClientboundPackets1_21_6.SHOW_DIALOG, wrapper -> {
            if (isFromAuthMe(wrapper)) {
                wrapper.cancel();  // AuthMe 对话，不显示
                wrapper.resetReader();
            } else {
                wrapper.resetReader();
                // 非 AuthMe，调用原始逻辑
                originalPlayHandler.handle(wrapper);
            }
        });

        // 替换 CONFIGURATION 状态的 SHOW_DIALOG
        protocol.replaceClientbound(ClientboundConfigurationPackets1_21_6.SHOW_DIALOG, wrapper -> {
            if (isFromAuthMe(wrapper)) {
                wrapper.cancel();
            } else {
                originalConfigHandler.handle(wrapper);
            }
        });

        FlexLoginUI.logger.info("Successfully intercepted SHOW_DIALOG packets for AuthMe.");
    }

    /**
     * 判断数据包是否为 AuthMe 登录/注册窗口
     */
    private static boolean isFromAuthMe(PacketWrapper wrapper) {
        // 1. 获取NBT标签
        Holder<CompoundTag> nbtHolder = wrapper.passthrough(Types.TRUSTED_COMPOUND_TAG_HOLDER);
        wrapper.resetReader();

        // 非直接值 → 不是目标数据包
        if (!nbtHolder.isDirect()) {
            return false;
        }

        CompoundTag root = nbtHolder.value();
        // 2. 校验根类型必须是 minecraft:multi_action
        String type = root.getString("type");
        if (type == null || !type.equals("minecraft:multi_action")) {
            return false;
        }

        // 3. 获取actions数组，必须非空
        ListTag<?> actionsList = root.getListTag("actions");
        if (actionsList == null || actionsList.isEmpty()) {
            return false;
        }

        // 4. 遍历动作，校验命令模板
        boolean validAction = false;
        for (Tag actionTag : actionsList) {
            if (!(actionTag instanceof CompoundTag actionCompound)) continue;

            // 获取action子标签
            CompoundTag action = actionCompound.getCompoundTag("action");
            if (action == null) continue;

            // 动作类型必须是 minecraft:dynamic/run_command
            String actionType = action.getString("type");
            if (!"minecraft:dynamic/run_command".equals(actionType)) continue;

            // 核心：命令模板包含 login 或 register（AuthMe固定指令）
            String template = action.getString("template");
            if (template != null && (template.startsWith("login ") || template.startsWith("register "))) {
                validAction = true;
                break;
            }
        }
        if (!validAction) return false;

        // 5. 校验存在inputs输入框（密码输入）
        ListTag<?> inputs = root.getListTag("inputs");
        return inputs != null && !inputs.isEmpty();
    }

    /**
     * 通过反射获取协议中指定包类型的原始 PacketHandler。
     */
    private static PacketHandler getOriginalHandler(Protocol1_21_6To1_21_5 protocol, State state, int packetId) {
        try {
            Class<?> clazz = protocol.getClass();
            Field mappingsField = null;
            // 向上遍历父类链，直到找到 clientboundMappings 字段
            while (clazz != null) {
                try {
                    mappingsField = clazz.getDeclaredField("clientboundMappings");
                    break; // 找到了
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass(); // 继续向上
                }
            }
            if (mappingsField == null) {
                FlexLoginUI.logger.warning("clientboundMappings field not found in any superclass");
                return null;
            }
            mappingsField.setAccessible(true);
            PacketMappings mappings = (PacketMappings) mappingsField.get(protocol);
            PacketMapping mapping = mappings.mappedPacket(state, packetId);
            return mapping != null ? mapping.handler() : null;
        } catch (IllegalAccessException e) {
            FlexLoginUI.logger.warning("Failed to access clientboundMappings");
            e.printStackTrace();
            return null;
        }
    }
}
