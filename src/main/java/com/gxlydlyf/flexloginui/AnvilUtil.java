package com.gxlydlyf.flexloginui;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemLore;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.nbt.NBTType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import io.github.retrooper.packetevents.adventure.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

import static com.gxlydlyf.flexloginui.DialogUtil.loginText;
import static com.gxlydlyf.flexloginui.DialogUtil.registerText;
import static com.gxlydlyf.flexloginui.PacketListeners.getUser;


public class AnvilUtil {
    // ====================== 固定常量 ======================
    public static final int WINDOW_ID_LOGIN = 58;
    public static final int WINDOW_ID_REGISTER = 59;
    private static int ANVIL_WINDOW_TYPE = 7;
    private static final int STATE_ID = 0;

    public static void setAnvilWindowType() {
        ServerVersion version = PacketEvents.getAPI().getServerManager().getVersion();
        if (version.isNewerThanOrEquals(ServerVersion.V_26_1)) {
            ANVIL_WINDOW_TYPE = MinecraftUtil.getAnvilMenuId();
        } else if (version.isNewerThanOrEquals(ServerVersion.V_1_20_3)) {
            ANVIL_WINDOW_TYPE = 8;
        }
    }

    // ====================== 最通用：打开自定义铁砧 UI ======================

    /**
     * 打开完全自定义的铁砧界面
     *
     * @param player     玩家
     * @param title      窗口标题
     * @param leftItem   左槽物品
     * @param rightItem  右槽物品
     * @param outputItem 输出槽物品
     */
    public static void openAnvil(Player player,
                                 Component title,
                                 ItemStack leftItem,
                                 ItemStack rightItem,
                                 ItemStack outputItem,
                                 int windowId, boolean refresh) {
        User user = getUser(player);
        if (user == null) {
            return;
        }

        // 1. 打开铁砧窗口
        if (!refresh) {
            WrapperPlayServerOpenWindow open = new WrapperPlayServerOpenWindow(
                    windowId,
                    ANVIL_WINDOW_TYPE,
                    title
            );
            user.sendPacket(open);
        }

        // 2. 设置所有槽位物品
        WrapperPlayServerWindowItems items = new WrapperPlayServerWindowItems(
                windowId,
                STATE_ID,
                Arrays.asList(leftItem, rightItem, outputItem),
                ItemStack.EMPTY
        );
        user.sendPacket(items);

        PacketListeners.ANVIL_MANUALLY_CLOSE.remove(player.getUniqueId());
    }

    public static void closeAnvil(Player player, int windowId) {
        User user = getUser(player);
        if (user != null) {
            user.sendPacket(new WrapperPlayServerCloseWindow(windowId));
        }
    }

    public static void closeRegisterAnvil(Player player) {
        closeAnvil(player, WINDOW_ID_REGISTER);
    }

    public static void closeLoginAnvil(Player player) {
        closeAnvil(player, WINDOW_ID_LOGIN);
    }

    public static boolean allowClose() {
        return FlexLoginUI.config.getBoolean("pages.anvil.allow_close");
    }

    public static String getLoginCloseButtonText() {
        return allowClose() ? loginText("close_button") : loginText("exit_button");
    }

    public static String getRegisterCloseButtonText() {
        return allowClose() ? registerText("close_button") : registerText("exit_button");
    }

    // ====================== 快捷方法：打开 登录 UI ======================
    public static void openLoginAnvil(Player player, String msg, boolean refresh) {
        ItemStack left = createItem(ItemTypes.REDSTONE, "", List.of(getLoginCloseButtonText()));
        ItemStack right = createItem(ItemTypes.PAPER, loginText("title"), List.of(msg));
        ItemStack output = createItem(ItemTypes.ARROW, loginText("login_button"), null);
        String title = loginText("title");
        if (!msg.isEmpty()) {
            title = title + "-" + msg;
        }
        openAnvil(player, Component.text(title), left, right, output, WINDOW_ID_LOGIN, refresh);
    }

    public static void openLoginAnvil(Player player) {
        openLoginAnvil(player, false);
    }

    public static void openLoginAnvil(Player player, boolean refresh) {
        openLoginAnvil(player, loginText("tip"), refresh);
    }

    // ====================== 快捷方法：打开 注册 UI ======================

    public static void openRegisterAnvil(Player player, String msg, boolean refresh) {
        ItemStack left = createItem(ItemTypes.REDSTONE, "", List.of(getRegisterCloseButtonText()));
        ItemStack right = createItem(ItemTypes.PAPER, registerText("title"), List.of(msg));
        ItemStack output = createItem(ItemTypes.ARROW, registerText("register_button"), null);
        String title = registerText("title");
        if (!msg.isEmpty()) {
            title = title + "-" + msg;
        }
        openAnvil(player, Component.text(title), left, right, output, WINDOW_ID_REGISTER, refresh);
    }

    public static void openRegisterAnvil(Player player) {
        openRegisterAnvil(player, false);
    }

    public static void openRegisterAnvil(Player player, boolean refresh) {
        openRegisterAnvil(player, registerText("tip_password"), refresh);
    }

    // ====================== 通用物品构建器（核心） ======================

    /**
     * 创建自定义物品：名称 + Lore（自动白色、无格式、正体）
     * 满足你需求：左侧物品名称会自动放入 Lore 第一行
     *
     * @param type     物品类型
     * @param name     显示名
     * @param loreList Lore
     * @return 构建好的物品
     */
    public static ItemStack createItem(ItemType type, String name, List<String> loreList) {
        ItemStack item = ItemStack.builder().type(type).amount(1).build();
        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_20_5)) {
            // 设置物品名称
            if (name != null) {
                Component nameComponent = newTextComponent(name);
                item.setComponent(ComponentTypes.CUSTOM_NAME, nameComponent);
            }

            // 设置 Lore
            if (loreList != null) {
                List<Component> loreComponents = new java.util.ArrayList<>();
                for (String line : loreList) {
                    loreComponents.add(newTextComponent(line));
                }
                item.setComponent(ComponentTypes.LORE, new ItemLore(loreComponents));
            }
        } else {
            NBTCompound root = item.getOrCreateTag();
            NBTCompound display = new NBTCompound();

            // 显示名
            if (name != null) {
                Component nameComp = newTextComponent(name);
                display.setTag("Name", new NBTString(GsonComponentSerializer.gson().serialize(nameComp)));
            }

            // Lore
            if (loreList != null && !loreList.isEmpty()) {
                NBTList<NBTString> lore = new NBTList<>(NBTType.STRING);
                for (String line : loreList) {
                    lore.addTag(new NBTString(GsonComponentSerializer.gson().serialize(newTextComponent(line))));
                }
                display.setTag("Lore", lore);
            }


            root.setTag("display", display);
        }
        return item;
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