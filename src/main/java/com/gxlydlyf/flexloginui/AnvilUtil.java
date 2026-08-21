package com.gxlydlyf.flexloginui;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.gxlydlyf.flexloginui.DialogUtil.*;
import static com.gxlydlyf.flexloginui.PacketListeners.getUser;


public class AnvilUtil {
    // ====================== 固定常量 ======================
    public static final int WINDOW_ID = 58;
    public static final ConcurrentHashMap<UUID, AnvilPage> OPENED_ANVIL = new ConcurrentHashMap<>();

    public enum AnvilPageType {
        REGISTER,
        LOGIN,
        REGISTER_CAPTCHA,
        LOGIN_CAPTCHA,
        CHANGE_PASSWORD
    }

    public static class AnvilPage {
        private AnvilPageType type;
        public String input = "";
        public String confirmPassword = null;
        public String oldPassword = null;
        public String newPassword = null;
        private boolean manuallyClose = false;
        public String tip = "";
        private int stateId = 0;

        public boolean hasType() {
            return type != null;
        }

        public boolean isRegConfirm() {
            return isType(AnvilPageType.REGISTER) && confirmPassword != null;
        }

        public void clearConfirm() {
            confirmPassword = null;
        }

        public boolean isChangePassword() {
            return isType(AnvilPageType.CHANGE_PASSWORD);
        }

        public boolean isChangePwdOld() {
            return this.isChangePassword() && oldPassword == null;
        }

        public boolean isChangePwdNew() {
            return this.isChangePassword() && oldPassword != null && newPassword == null;
        }

        public boolean isChangePwdConfirm() {
            return this.isChangePassword() && oldPassword != null && newPassword != null;
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
            switch (type) {
                case LOGIN -> AnvilUtil.openLoginAnvil(player, tip, refresh);
                case REGISTER -> AnvilUtil.openRegisterAnvil(player, tip, refresh);
                case LOGIN_CAPTCHA -> AnvilUtil.openLogCaptchaAnvil(player, tip, refresh);
                case REGISTER_CAPTCHA -> AnvilUtil.openRegCaptchaAnvil(player, tip, refresh);
                case CHANGE_PASSWORD -> AnvilUtil.openChangePasswordAnvil(player, tip, refresh);
                default -> {
                }
            }
        }

        public void restoreAnvilPage(Player player) {
            restoreAnvilPage(player, false);
        }

        public void setType(AnvilPageType type) {
            this.type = type;
        }

        public int nextStateId() {
            return ++stateId;
        }
    }

    public static void createAnvilPage(UUID uuid) {
        OPENED_ANVIL.computeIfAbsent(uuid, k -> new AnvilPage());
    }

    public static int nextStateId(UUID uuid) {
        AnvilPage page = getAnvilPage(uuid);
        return page != null ? page.nextStateId() : 0;
    }

    public static int nextStateId(Player player) {
        return nextStateId(player.getUniqueId());
    }

    public static void closeAnvilPage(UUID uuid) {
        OPENED_ANVIL.remove(uuid);
    }

    public static void closeAnvilPage(Player player) {
        OPENED_ANVIL.remove(player.getUniqueId());
    }

    public static AnvilPage getAnvilPage(UUID uuid) {
        return OPENED_ANVIL.get(uuid);
    }

    public static AnvilPage getAnvilPage(Player player) {
        return getAnvilPage(player.getUniqueId());
    }

    public static AnvilPage getOrCreateAnvilPage(UUID uuid) {
        createAnvilPage(uuid);
        return getAnvilPage(uuid);
    }

    public static AnvilPage getOrCreateAnvilPage(Player player) {
        return getOrCreateAnvilPage(player.getUniqueId());
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
        AnvilPage page = getOrCreateAnvilPage(player);
        page.manuallyClose(false);

        User user = getUser(player);
        if (user == null) {
            return;
        }

        // 1. 打开铁砧窗口
        if (!refresh) {
            AnvilVersion.sendServerAnvilWindow(player, windowId, title);
        }

        int stateId = page.nextStateId();
        // 2. 设置所有槽位物品
        WrapperPlayServerWindowItems items = new WrapperPlayServerWindowItems(
                windowId,
                stateId,
                List.of(leftItem, rightItem, outputItem),
                ItemStack.EMPTY
        );

        // 额外确保第 3 槽物品
        WrapperPlayServerSetSlot outputSlot = new WrapperPlayServerSetSlot(
                windowId,
                stateId,
                2,
                outputItem
        );
        user.sendPacket(items);
        user.sendPacket(outputSlot);
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
        ItemStack left = AnvilVersion.createItem(ItemTypes.REDSTONE, "", List.of(closeText));
        ItemStack right = AnvilVersion.createItem(ItemTypes.PAPER, title, List.of(msg.split("\n")));
        ItemStack output = AnvilVersion.createItem(ItemTypes.ARROW, submitText, null);
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

    // ====================== 更改密码 UI ======================

    public static void openChangePasswordAnvil(Player player, String msg, boolean refresh) {
        openCommonAnvil(player, AnvilPageType.CHANGE_PASSWORD, refresh, changePasswordText("title"),
                getChangePasswordCloseButtonText(), msg, changePasswordText("change_button"));
    }

    public static void openChangePasswordAnvil(Player player, String msg) {
        openChangePasswordAnvil(player, msg, false);
    }

    public static void openChangePasswordAnvil(Player player, boolean refresh) {
        openChangePasswordAnvil(player, changePasswordText("tip_old"), refresh);
    }

    public static void openChangePasswordAnvil(Player player) {
        openChangePasswordAnvil(player, false);
    }
}