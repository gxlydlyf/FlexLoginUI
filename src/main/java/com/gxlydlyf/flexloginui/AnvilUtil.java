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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.gxlydlyf.flexloginui.DialogUtil.*;
import static com.gxlydlyf.flexloginui.PacketListeners.getUser;


public class AnvilUtil {
    // ====================== 固定常量 ======================
    public static final int WINDOW_ID = 58;
    private static int ANVIL_WINDOW_TYPE = 7;
    private static final int STATE_ID = 0;
    public static final ConcurrentHashMap<UUID, AnvilPage> OPENED_ANVIL = new ConcurrentHashMap<>();

    public static void setAnvilWindowType() {
        ServerVersion version = PacketEvents.getAPI().getServerManager().getVersion();
        if (version.isNewerThanOrEquals(ServerVersion.V_26_1)) {
            ANVIL_WINDOW_TYPE = MinecraftUtil.getAnvilMenuId();
        } else if (version.isNewerThanOrEquals(ServerVersion.V_1_20_3)) {
            ANVIL_WINDOW_TYPE = 8;
        } else if (version.isNewerThanOrEquals(ServerVersion.V_1_14)) {
            ANVIL_WINDOW_TYPE = 7;
        }
    }

    public enum AnvilPageType {
        REGISTER,
        LOGIN,
        REGISTER_CAPTCHA,
        LOGIN_CAPTCHA
    }

    public static class AnvilPage {
        private AnvilPageType type;
        public String input = "";
        public String confirmPassword = null;
        private boolean manuallyClose = false;
        public String tip = "";

        public boolean hasType() {
            return type != null;
        }

        public boolean isRegConfirm() {
            return isType(AnvilPageType.REGISTER) && confirmPassword != null;
        }

        public void clearConfirm() {
            confirmPassword = null;
        }

        public boolean isType(AnvilPageType type) {
            return this.type == type;
        }

        public void manuallyClose(boolean manuallyClose) {
            this.manuallyClose = manuallyClose;
        }

        public void manuallyClose() {
            manuallyClose(true);
        }

        public boolean isManuallyClose() {
            return manuallyClose;
        }

        public void restoreAnvilPage(Player player, boolean refresh) {
            if (isType(AnvilPageType.LOGIN)) {
                AnvilUtil.openLoginAnvil(player, tip, refresh);
            } else if (isType(AnvilPageType.REGISTER)) {
                AnvilUtil.openRegisterAnvil(player, tip, refresh);
            } else if (isType(AnvilPageType.LOGIN_CAPTCHA)) {
                AnvilUtil.openLogCaptchaAnvil(player, tip, refresh);
            } else if (isType(AnvilPageType.REGISTER_CAPTCHA)) {
                AnvilUtil.openRegCaptchaAnvil(player, tip, refresh);
            }
        }

        public void restoreAnvilPage(Player player) {
            restoreAnvilPage(player, false);
        }

        public void setType(AnvilPageType type) {
            this.type = type;
        }
    }

    public static void createAnvilPage(UUID uuid) {
        OPENED_ANVIL.computeIfAbsent(uuid, k -> new AnvilPage());
    }

    public static void closeAnvilPage(UUID uuid) {
        OPENED_ANVIL.remove(uuid);
    }

    public static void closeAnvilPage(Player player) {
        OPENED_ANVIL.remove(player.getUniqueId());
    }

    public static AnvilPage getAnvilPage(UUID uuid) {
        createAnvilPage(uuid);
        return OPENED_ANVIL.get(uuid);
    }

    public static AnvilPage getAnvilPage(Player player) {
        return getAnvilPage(player.getUniqueId());
    }

    public static boolean isActiveAnvilPage(UUID uuid) {
        return OPENED_ANVIL.containsKey(uuid);
    }

    public static boolean isActiveAnvilPage(Player player) {
        return isActiveAnvilPage(player.getUniqueId());
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
        AnvilPage page = getAnvilPage(player.getUniqueId());
        if (page != null) {
            page.manuallyClose(false);
        }

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
    }

    public static void closeAnvil(Player player, int windowId) {
        User user = getUser(player);
        if (user != null) {
            user.sendPacket(new WrapperPlayServerCloseWindow(windowId));
        }
    }

    public static void closeRegisterAnvil(Player player) {
        closeAnvil(player, WINDOW_ID);
    }

    public static void closeLoginAnvil(Player player) {
        closeAnvil(player, WINDOW_ID);
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

    public static void openCommonAnvil(Player player,
                                       AnvilPageType pageType,
                                       boolean refresh,
                                       String title,
                                       String closeText,
                                       String msg,
                                       String submitText) {
        if (msg == null) {
            msg = "";
        }
        ItemStack left = createItem(ItemTypes.REDSTONE, "", List.of(closeText));
        ItemStack right = createItem(ItemTypes.PAPER, title, List.of(msg.split("\n")));
        ItemStack output = createItem(ItemTypes.ARROW, submitText, null);
        if (!msg.isEmpty()) {
            title = title + "-" + msg.replace("\n", " ");
        }
        openAnvil(player, Component.text(title), left, right, output, WINDOW_ID, refresh);

        AnvilPage page = getAnvilPage(player.getUniqueId());
        page.setType(pageType);
        page.tip = msg;
    }

    // 打开 登录 UI
    public static void openLoginAnvil(Player player, String msg, boolean refresh) {
        openCommonAnvil(player, AnvilPageType.LOGIN, refresh, loginText("title"),
                getLoginCloseButtonText(), msg, loginText("login_button"));
    }

    public static void openLoginAnvil(Player player) {
        openLoginAnvil(player, false);
    }

    public static void openLoginAnvil(Player player, boolean refresh) {
        openLoginAnvil(player, loginText("tip"), refresh);
    }

    // 打开 注册 UI
    public static void openRegisterAnvil(Player player, String msg, boolean refresh) {
        openCommonAnvil(player, AnvilPageType.REGISTER, refresh, registerText("title"),
                getRegisterCloseButtonText(), msg, registerText("register_button"));
    }

    public static void openRegisterAnvil(Player player) {
        openRegisterAnvil(player, false);
    }

    public static void openRegisterAnvil(Player player, boolean refresh) {
        openRegisterAnvil(player, registerText("tip_password"), refresh);
    }

    public static void openLogCaptchaAnvil(Player player, String msg, boolean refresh) {
        openCommonAnvil(player, AnvilPageType.LOGIN_CAPTCHA, refresh, logCaptchaText("title"),
                getLoginCloseButtonText(), msg, logCaptchaText("verify"));
    }

    public static void openLogCaptchaAnvil(Player player, String msg) {
        openLogCaptchaAnvil(player, msg, false);
    }

    public static void openRegCaptchaAnvil(Player player, String msg, boolean refresh) {
        openCommonAnvil(player, AnvilPageType.REGISTER_CAPTCHA, refresh, regCaptchaText("title"),
                getRegisterCloseButtonText(), msg, regCaptchaText("verify"));
    }

    public static void openRegCaptchaAnvil(Player player, String msg) {
        openRegCaptchaAnvil(player, msg, false);
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
            // 新数据组件
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
            // 旧 NBT
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