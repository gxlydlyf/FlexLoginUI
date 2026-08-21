package com.gxlydlyf.flexloginui;

import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemLore;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.nbt.NBTType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import io.github.retrooper.packetevents.adventure.serializer.gson.GsonComponentSerializer;
import io.github.retrooper.packetevents.adventure.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.List;

import static com.gxlydlyf.flexloginui.FlexLoginUI.serverVersion;

public class AnvilVersion {
    private static int ANVIL_WINDOW_TYPE = 7;

    public static void setAnvilWindowType() {
        ServerVersion version = serverVersion;
        if (version.isNewerThanOrEquals(ServerVersion.V_26_1)) {
            ANVIL_WINDOW_TYPE = MinecraftUtil.getAnvilMenuId();
        } else if (version.isNewerThanOrEquals(ServerVersion.V_1_20_3)) {
            ANVIL_WINDOW_TYPE = 8;
        } else if (version.isNewerThanOrEquals(ServerVersion.V_1_14)) {
            ANVIL_WINDOW_TYPE = 7;
        }
    }

    public static boolean isServerOlderThanV1_14() {
        return serverVersion.isOlderThan(ServerVersion.V_1_14);
    }

    public static String componentToLegacyString(Component str) {
        return LegacyComponentSerializer.legacySection().serialize(str);
    }

    public static void sendServerAnvilWindow(Player player, int windowId, Component title) {
        User user = PacketListeners.getUser(player);
        if (user == null) {
            return;
        }
        WrapperPlayServerOpenWindow open;
        if (isServerOlderThanV1_14()) {
            open = new WrapperPlayServerOpenWindow(
                    windowId,
                    "minecraft:anvil",
                    title,
                    0,
                    0
            );
        } else {
            open = new WrapperPlayServerOpenWindow(
                    windowId,
                    ANVIL_WINDOW_TYPE,
                    title
            );
        }
        if (ViaVersionUtil.isClientOlderThanV1_14(player)) {
            // 由于 1.14 以下版本铁砧标题无法应用，发送消息提示玩家
            player.sendMessage(componentToLegacyString(title));
        }
        user.sendPacket(open);
    }

    // 通用物品构建器
    public static String componentToJson(Component component) {
        return GsonComponentSerializer.gson().serialize(component);
    }

    /**
     * 创建自定义物品：名称 + Lore（自动白色、无格式、正体）
     * 物品按【服务器版本】构建，ViaVersion负责向下兼容客户端转换
     *
     * @param type     物品类型
     * @param name     显示名
     * @param loreList Lore
     * @return 构建好的物品
     */
    public static ItemStack createItem(ItemType type, String name, List<String> loreList) {
        ItemStack.Builder builder = ItemStack.builder()
                .type(type)
                .amount(1);

        if (serverVersion.isNewerThanOrEquals(ServerVersion.V_1_20_5)) {
            // -------------------------- 1.20.5+ 组件系统 --------------------------
            ItemStack item = builder.build();
            if (name != null) {
                Component nameComponent = newTextComponent(name);
                item.setComponent(ComponentTypes.CUSTOM_NAME, nameComponent);
            }
            if (loreList != null) {
                var loreComponents = loreList.stream()
                        .map(AnvilVersion::newTextComponent)
                        .toList();
                item.setComponent(ComponentTypes.LORE, new ItemLore(loreComponents));
            }
            return item;
        } else {
            // -------------------------- <1.20.5 NBT模式 --------------------------
            NBTCompound root = new NBTCompound();
            NBTCompound display = new NBTCompound();

            // 物品名称
            if (name != null) {
                Component nameComp = newTextComponent(name);
                String nameValue;
                if (serverVersion.isNewerThanOrEquals(ServerVersion.V_1_13)) {
                    //1.13 ~ 1.20.4: JSON字符串
                    nameValue = componentToJson(nameComp);
                } else {
                    //1.8 ~1.12.2: § legacy格式字符串
                    nameValue = componentToLegacyString(nameComp);
                }
                display.setTag("Name", new NBTString(nameValue));
            }

            // Lore
            if (loreList != null && !loreList.isEmpty()) {
                NBTList<NBTString> loreNbt = new NBTList<>(NBTType.STRING);
                for (String line : loreList) {
                    Component comp = newTextComponent(line);
                    String loreText;
                    if (serverVersion.isNewerThanOrEquals(ServerVersion.V_1_13)) {
                        loreText = componentToJson(comp);
                    } else {
                        loreText = componentToLegacyString(comp);
                    }
                    loreNbt.addTag(new NBTString(loreText));
                }
                display.setTag("Lore", loreNbt);
            }

            if (!display.getTags().isEmpty()) {
                root.setTag("display", display);
            }
            builder.nbt(root);
            return builder.build();
        }
    }

    // ====================== 文本样式统一（白色 + 正体） ======================
    private static Component newTextComponent(String text) {
        return Component.text(text)
                .color(TextColor.color(255, 255, 255))
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, false)
                .decoration(TextDecoration.UNDERLINED, false)
                .decoration(TextDecoration.STRIKETHROUGH, false)
                .decoration(TextDecoration.OBFUSCATED, false);
    }
}
