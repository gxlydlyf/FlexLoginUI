package com.gxlydlyf.flexloginui;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.dialog.CommonDialogData;
import com.github.retrooper.packetevents.protocol.dialog.MultiActionDialog;
import com.github.retrooper.packetevents.protocol.dialog.action.Action;
import com.github.retrooper.packetevents.protocol.dialog.action.DynamicCustomAction;
import com.github.retrooper.packetevents.protocol.dialog.action.DynamicRunCommandAction;
import com.github.retrooper.packetevents.protocol.dialog.button.ActionButton;
import com.github.retrooper.packetevents.protocol.nbt.NBT;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.common.server.WrapperCommonServerShowDialog;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerShowDialog;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCustomClickAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientNameItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerShowDialog;
import fr.xephi.authme.data.limbo.LimboMessageType;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.ValidationService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jetbrains.annotations.Nullable;
import com.gxlydlyf.flexloginui.AnvilUtil.AnvilPageType;
import com.gxlydlyf.flexloginui.AnvilUtil.AnvilPage;

import java.util.*;

import static com.gxlydlyf.flexloginui.CommandExecutors.authMeApi;

public class PacketListeners implements PacketListener, Listener {
    public void refreshCustomAnvil(Player player) {
        if (AnvilUtil.isActiveAnvilPage(player)) {
            AnvilUtil.getAnvilPage(player).restoreAnvilPage(player, true);
        }
    }

    public boolean isCustomAnvil(int windowId) {
        return windowId == AnvilUtil.WINDOW_ID;
    }

    public static void kickPlayer(Player player, String msg) {
        Bukkit.getScheduler().runTask(FlexLoginUI.instance, () -> player.kickPlayer(msg));
    }

    public static void disallowCloseKick(Player player, boolean isLogin) {
        if (authMeApi.isAuthenticated(player) || authMeApi.isUnrestricted(player)) {
            return;
        }
        kickPlayer(player, isLogin ? DialogUtil.loginText("exit_message") : DialogUtil.registerText("exit_message"));
    }

    public static void disallowCloseKick(Player player, AnvilPage page) {
        disallowCloseKick(player, page.isType(AnvilPageType.LOGIN) || page.isType(AnvilPageType.LOGIN_CAPTCHA));
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent e) {
        Player player = e.getPlayer();
        if (player == null) {
            return;
        }
        User user = e.getUser();
        UUID uuid = player.getUniqueId();

        PacketTypeCommon packetTypeCommon = e.getPacketType();
        if (packetTypeCommon instanceof PacketType.Play.Client clientType) {
            switch (clientType) {
                case NAME_ITEM:
                    if (AnvilUtil.isActiveAnvilPage(uuid)) {
                        AnvilUtil.getAnvilPage(uuid).input = new WrapperPlayClientNameItem(e).getItemName();
                        refreshCustomAnvil(player);
                    }
                    break;
                case CLICK_WINDOW: {
                    if (!AnvilUtil.isActiveAnvilPage(uuid)) {
                        return;
                    }
                    WrapperPlayClientClickWindow packet = new WrapperPlayClientClickWindow(e);
                    int windowId = packet.getWindowId();
                    // 只处理我们的铁砧窗口
                    if (!isCustomAnvil(windowId)) return;
                    e.setCancelled(true);
                    refreshCustomAnvil(player);

                    AnvilPage anvilPage = AnvilUtil.getAnvilPage(uuid);

                    boolean isLogin = anvilPage.isType(AnvilPageType.LOGIN);
                    boolean isRegister = anvilPage.isType(AnvilPageType.REGISTER);
                    boolean isLogCaptcha = anvilPage.isType(AnvilPageType.LOGIN_CAPTCHA);
                    boolean isRegCaptcha = anvilPage.isType(AnvilPageType.REGISTER_CAPTCHA);
                    int slot = packet.getSlot();
                    if (slot == 0) {
                        if (FlexLoginUI.config.getBoolean("pages.anvil.allow_close")) {
                            anvilPage.manuallyClose();
                            user.closeInventory();
                            sendPlayerReopen(player, isLogin);
                        } else {
                            disallowCloseKick(player, anvilPage);
                        }
                    }
                    // 点击输出槽 2
                    else if (slot == 2) {
                        user.closeInventory();
                        String inputText = anvilPage.input;

                        if (inputText != null) {
                            if (isLogCaptcha) {
                                onPlayerVerifyLogCaptcha(player, inputText);
                            } else if (isRegCaptcha) {
                                onPlayerVerifyRegCaptcha(player, inputText);
                            } else if (isLogin) {
                                onPlayerSubmitLogin(player, inputText);
                            } else if (isRegister) {
                                if (anvilPage.isRegConfirm()) {
                                    onPlayerSubmitRegister(player, anvilPage.confirmPassword, inputText);
                                } else {
                                    anvilPage.confirmPassword = inputText;
                                    AnvilUtil.openRegisterAnvil(player, DialogUtil.registerText("tip_confirm"), false);
                                }
                            }
                        } else {
                            anvilPage.restoreAnvilPage(player);
                        }
                    }
                    Bukkit.getScheduler().runTask(FlexLoginUI.instance, player::updateInventory);
                    break;
                }
                case CLOSE_WINDOW: {
                    if (!AnvilUtil.isActiveAnvilPage(uuid)) {
                        return;
                    }
                    int windowId = new WrapperPlayClientCloseWindow(e).getWindowId();
                    if (isCustomAnvil(windowId)) {
                        if (!authMeApi.isUnrestricted(player) && !authMeApi.isAuthenticated(player)) {
                            AnvilPage anvilPage = AnvilUtil.getAnvilPage(uuid);
                            if (!anvilPage.isManuallyClose()) {
                                if (!player.isDead()) {
                                    anvilPage.restoreAnvilPage(player);
                                    break;
                                }
                            }
                        }
                        AnvilUtil.closeAnvilPage(uuid);
                        user.closeInventory();
                    }
                    break;
                }
                case CUSTOM_CLICK_ACTION: {
                    WrapperPlayClientCustomClickAction packet = new WrapperPlayClientCustomClickAction(e);
                    String id = packet.getId().toString();
                    boolean isLogin = id.equals(DialogUtil.LOGIN_DIALOG_ID);
                    boolean isRegister = id.equals(DialogUtil.REGISTER_DIALOG_ID);
                    boolean isLogCaptcha = id.equals(DialogUtil.LOGIN_CAPTCHA_DIALOG_ID);
                    boolean isRegCaptcha = id.equals(DialogUtil.REGISTER_CAPTCHA_DIALOG_ID);
                    NBT nbt = packet.getPayload();
                    if (nbt instanceof NBTCompound payload) {
                        boolean close = payload.getBooleanOr("close", false);
                        if (isLogin || isRegister) {
                            handleCustomClickAction(
                                    player,
                                    isLogin,
                                    close,
                                    payload.getStringTagValueOrDefault("password", ""),
                                    payload.getStringTagValueOrDefault("confirm", "")
                            );
                        } else if (isLogCaptcha || isRegCaptcha) {
                            handleCaptchaCustomClickAction(
                                    player,
                                    isLogCaptcha,
                                    close,
                                    payload.getStringTagValueOrDefault("captcha", "")
                            );
                        }
                    }
                    break;
                }
                default:
                    break;
            }
        }
    }

    public static void sendPlayerReopen(Player player, boolean isLogin) {
        player.sendMessage(isLogin ? DialogUtil.loginText("reopen") : DialogUtil.registerText("reopen"));
    }

    public static void handleCustomClickAction(Player player, boolean isLogin, boolean close, String password, String confirm) {
        if (close) {
            handleCloseAction(player, isLogin);
        } else {
            if (isLogin) {
                onPlayerSubmitLogin(player, password);
            } else {
                onPlayerSubmitRegister(player, password, confirm);
            }
        }
    }

    public static void handleCaptchaCustomClickAction(Player player, boolean isLogin, boolean close, String code) {
        if (close) {
            handleCloseAction(player, isLogin);
        } else {
            if (isLogin) {
                onPlayerVerifyLogCaptcha(player, code);
            } else {
                onPlayerVerifyRegCaptcha(player, code);
            }
        }
    }

    private static void handleCloseAction(Player player, boolean isLogin) {
        if (DialogUtil.allowClose()) {
            sendPlayerReopen(player, isLogin);
        } else {
            disallowCloseKick(player, isLogin);
        }
    }

    public static <W extends WrapperCommonServerShowDialog<W>> boolean isAuthMeDialog(W wrapper) {
        boolean isAuthMe = false;
        if (wrapper.getDialog() instanceof MultiActionDialog dialog) {
            List<ActionButton> buttons = dialog.getActions();
            if (!buttons.isEmpty()) {
                CommonDialogData commonDialogData = dialog.getCommon();
                if (!commonDialogData.getBody().isEmpty() && !commonDialogData.getInputs().isEmpty()) {
                    for (ActionButton button : buttons) {
                        Action action = button.getAction();
                        if (action instanceof DynamicRunCommandAction commandAction) {
                            String template = commandAction.getTemplate().getRaw();
                            if (template.startsWith("login ") || template.startsWith("register ")) {
                                isAuthMe = true;
                                break;
                            }
                        }
                        if (action instanceof DynamicCustomAction customAction) {
                            if (customAction.getId().getNamespace().equals("authme")) {
                                isAuthMe = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return isAuthMe;
    }

    public static <W extends WrapperCommonServerShowDialog<W>> boolean isShouldCancelAuthMeDialog(Player player, W wrapper) {
        if (isAuthMeDialog(wrapper)) {
            return ViaVersionUtil.isLowVersion(player) || GeyserUtil.isBedrock(player);
        }
        return false;
    }

    @Override
    public void onPacketSend(PacketSendEvent e) {
        Player player = e.getPlayer();

        PacketTypeCommon packetType = e.getPacketType();
        // player is null
        if (packetType instanceof PacketType.Configuration.Server configServerType) {
            User user = e.getUser();
            if (configServerType.equals(PacketType.Configuration.Server.SHOW_DIALOG)) {
                if (isAuthMeDialog(new WrapperConfigServerShowDialog(e))) {
                    if (user.getClientVersion().isOlderThan(ClientVersion.V_1_21_6)) {
                        e.setCancelled(true);
                    }
                }
            }
        }

        if (player == null) return;

        if (packetType instanceof PacketType.Play.Server serverType) {
            switch (serverType) {
                case SHOW_DIALOG: {
                    if (isShouldCancelAuthMeDialog(player, new WrapperPlayServerShowDialog(e))) {
                        e.setCancelled(true);
                    }
                    break;
                }
                default:
                    break;
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        if (player != null) {
            AnvilUtil.closeAnvilPage(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        handlePlayerUI(e.getPlayer(), 5L);// 延迟 5 tick
    }

    @EventHandler
    public void onPlayerSpawn(PlayerRespawnEvent e) {
        handlePlayerUI(e.getPlayer(), 1L);
    }

    public static void handlePlayerCaptcha(Player player, String msg, long delay) {
        String name = player.getName();
        Bukkit.getScheduler().runTaskLater(FlexLoginUI.instance, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!authMeApi.isUnrestricted(player) && !authMeApi.isAuthenticated(player)) {
                if (ViaVersionUtil.isLowVersion(player) || GeyserUtil.isBedrock(player)) {
                    boolean isLogin = authMeApi.isRegistered(name);
                    boolean isBedrock = GeyserUtil.isBedrock(player);
                    if (isBedrock && GeyserUtil.hasOpenForm(player)) {
                        return;
                    }
                    if (isLogin) {
                        if (AuthMeUtil.isLoginCaptchaRequired(name)) {
                            String tip = AuthMeUtil.getLogCaptchaTip(player, msg);
                            if (isBedrock) {
                                GeyserUtil.sendLogCaptchaForm(player, tip);
                            } else {
                                AnvilUtil.openLogCaptchaAnvil(player, tip);
                            }
                        }
                    } else {
                        if (AuthMeUtil.isRegisterCaptchaRequired(name)) {
                            String tip = AuthMeUtil.getRegCaptchaTip(player, msg);
                            if (isBedrock) {
                                GeyserUtil.sendRegCaptchaForm(player, tip);
                            } else {
                                AnvilUtil.openRegCaptchaAnvil(player, tip);
                            }
                        }
                    }
                } else if (DialogUtil.isHighServerVersion() || ViaVersionUtil.enabled) {
                    handlePlayerCaptchaDialog(player, msg);
                }
            }
        }, delay);
    }

    public static void handlePlayerCaptcha(Player player, String msg) {
        handlePlayerCaptcha(player, msg, 0L);
    }

    public static void handlePlayerCaptcha(Player player, long delay) {
        handlePlayerCaptcha(player, null, delay);
    }


    public static void handlePlayerCaptcha(Player player) {
        handlePlayerCaptcha(player, 0L);
    }

    public static void handlePlayerUI(Player player, long delay) {
        Bukkit.getScheduler().runTaskLater(FlexLoginUI.instance, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (AuthMeUtil.isCaptchaRequired(player)) {
                handlePlayerCaptcha(player);
                return;
            }
            if (!authMeApi.isUnrestricted(player) && !authMeApi.isAuthenticated(player)) {
                if (ViaVersionUtil.isLowVersion(player) || GeyserUtil.isBedrock(player)) {
                    boolean isLogin = authMeApi.isRegistered(player.getName());
                    if (GeyserUtil.isBedrock(player)) {
                        if (!GeyserUtil.hasOpenForm(player)) {
                            if (isLogin) {
                                GeyserUtil.sendLoginForm(player);
                            } else {
                                GeyserUtil.sendRegisterForm(player);
                            }
                        }
                    } else {
                        if (isLogin) {
                            AnvilUtil.openLoginAnvil(player);
                        } else {
                            AnvilUtil.openRegisterAnvil(player);
                        }
                    }
                } else if (DialogUtil.isHighServerVersion() || ViaVersionUtil.enabled) {
                    handlePlayerDialog(player);
                }
            }
        }, delay);
    }

    public static void handlePlayerUI(Player player) {
        handlePlayerUI(player, 0L);
    }

    // 低版本服务器如果在 Join 事件马上发包, dialog 会马上关闭
    public static void handlePlayerDialog(Player player) {
        if (AuthMeUtil.isCaptchaRequired(player)) {
            handlePlayerCaptchaDialog(player);
            return;
        }
        if (authMeApi.isUnrestricted(player) ||
                authMeApi.isAuthenticated(player) ||
                GeyserUtil.isBedrock(player) ||
                AuthMeUtil.isEnabledAuthMeDialog()) {
            return;
        }

        if (authMeApi.isRegistered(player.getName())) {
            if (!authMeApi.isAuthenticated(player)) {
                DialogUtil.sendLoginDialog(player);
            }
        } else {
            DialogUtil.sendRegisterDialog(player);
        }
    }

    public static void handlePlayerCaptchaDialog(Player player, String msg) {
        if (authMeApi.isUnrestricted(player) ||
                authMeApi.isAuthenticated(player) ||
                GeyserUtil.isBedrock(player) ||
                AuthMeUtil.isEnabledAuthMeDialog()) {
            return;
        }

        String name = player.getName();
        if (authMeApi.isRegistered(name)) {
            if (!authMeApi.isAuthenticated(player)) {
                if (AuthMeUtil.isLoginCaptchaRequired(name)) {
                    DialogUtil.sendLogCaptchaDialog(player, AuthMeUtil.getLogCaptchaTip(player, msg));
                }
            }
        } else {
            if (AuthMeUtil.isRegisterCaptchaRequired(name)) {
                DialogUtil.sendRegCaptchaDialog(player, AuthMeUtil.getRegCaptchaTip(player, msg));
            }
        }
    }

    public static void handlePlayerCaptchaDialog(Player player) {
        handlePlayerCaptchaDialog(player, null);
    }

    public static void openMessageUI(Player player, boolean isLogin, String msg) {
        if (AuthMeUtil.isCaptchaRequired(player)) {
            handlePlayerCaptcha(player, msg);
            return;
        }
        if (GeyserUtil.isBedrock(player)) {
            if (GeyserUtil.hasOpenForm(player)) {
                return;
            }
            if (isLogin) {
                GeyserUtil.sendLoginForm(player, msg);
            } else {
                GeyserUtil.sendRegisterForm(player, msg);
            }
            return;
        }

        if (ViaVersionUtil.isLowVersion(player)) {
            if (isLogin) {
                AnvilUtil.openLoginAnvil(player, msg, false);
            } else {
                AnvilUtil.openRegisterAnvil(player, DialogUtil.registerText("please_reset_password") + msg, false);
            }
            player.sendMessage(msg);
        } else {
            if (isLogin) {
                DialogUtil.sendLoginDialog(player, msg);
            } else {
                DialogUtil.sendRegisterDialog(player, msg);
            }
        }
    }

    public static void onPlayerSubmitLogin(Player player, String password) {
        String name = player.getName();
        if (authMeApi.isRegistered(name)) {
            if (!authMeApi.isAuthenticated(player)) {
                if (authMeApi.checkPassword(name, password)) {
                    authMeApi.forceLogin(player);
                    if (ViaVersionUtil.isLowVersion(player)) {
                        AnvilUtil.closeAnvilPage(player);
                        User user = getUser(player);
                        if (user != null) {
                            user.closeInventory();
                        }
                    }
                } else {
                    if (AuthMeUtil.kickOnWrongPassword()) {
                        kickPlayer(player, DialogUtil.loginText("password_error"));
                        return;
                    }
                    if (AuthMeUtil.useTempban()) {
                        AuthMeUtil.increaseTempbanCount(player);
                        if (AuthMeUtil.shouldTempban(player)) {
                            AuthMeUtil.tempbanPlayer(player);
                            return;
                        }
                    }
                    if (AuthMeUtil.useLoginFailureCaptcha()) {
                        AuthMeUtil.increaseLoginFailureCount(name);
                        if (AuthMeUtil.isLoginCaptchaRequired(name)) {
                            AuthMeUtil.muteMessageTask(player);
                            AuthMeUtil.sendMessage(player, MessageKey.USAGE_CAPTCHA,
                                    AuthMeUtil.getLoginCaptcha(name));
                        }
                    }
                    openMessageUI(player, true, DialogUtil.loginText("password_error"));
                }
            }
        }
    }

    public static void onPlayerSubmitRegister(Player player, String password, String confirm) {
        String name = player.getName();
        if (!authMeApi.isRegistered(name)) {
            AnvilUtil.getAnvilPage(player).clearConfirm();
            if (password.equals(confirm)) {
                ValidationService validationService = AuthMeUtil.validationService;
                fr.xephi.authme.message.Messages messages = AuthMeUtil.messages;
                ValidationService.ValidationResult validationResult = validationService.validatePassword(password, player.getName());
                if (password.isEmpty()) {
                    openMessageUI(player, false, messages.retrieveSingle(player, MessageKey.PASSWORD_UNSAFE_ERROR));
                } else if (validationResult.hasError()) {
                    MessageKey errorKey = validationResult.getMessageKey();
                    String singleMessage = messages.retrieveSingle(player, errorKey, validationResult.getArgs());
                    openMessageUI(player, false, singleMessage);
                } else {
                    if (authMeApi.registerPlayer(name, password)) {
                        authMeApi.forceLogin(player);
                        player.sendMessage(DialogUtil.registerText("registration_successful"));
                        if (ViaVersionUtil.isLowVersion(player)) {
                            User user = getUser(player);
                            if (user != null) {
                                user.closeInventory();
                            }
                            AnvilUtil.closeAnvilPage(player);
                        }
                    } else {
                        openMessageUI(player, false, DialogUtil.registerText("registration_failed"));
                    }
                }
            } else {
                openMessageUI(player, false, DialogUtil.registerText("password_not_match"));
            }
        }
    }

    public static void onPlayerVerifyLogCaptcha(Player player, String code) {
        String name = player.getName();
        if (AuthMeUtil.isLoginCaptchaRequired(name)) {
            if (AuthMeUtil.checkLoginCaptcha(player, code)) {
                AuthMeUtil.sendMessage(player, MessageKey.CAPTCHA_SUCCESS);
                AuthMeUtil.sendMessage(player, MessageKey.LOGIN_MESSAGE);
                AuthMeUtil.unmuteMessageTask(player);
                handlePlayerUI(player);
            } else {
                AuthMeUtil.sendMessage(player, MessageKey.CAPTCHA_WRONG_ERROR, AuthMeUtil.getLoginCaptcha(name));
                handlePlayerCaptcha(player, DialogUtil.logCaptchaText("invalid"));
            }
        }
    }


    public static void onPlayerVerifyRegCaptcha(Player player, String code) {
        String name = player.getName();
        if (AuthMeUtil.isRegisterCaptchaRequired(name)) {
            if (AuthMeUtil.checkRegisterCaptcha(player, code)) {
                AuthMeUtil.sendMessage(player, MessageKey.REGISTER_CAPTCHA_SUCCESS);
                AuthMeUtil.sendMessage(player, MessageKey.REGISTER_MESSAGE);
                handlePlayerUI(player);
            } else {
                AuthMeUtil.sendMessage(player, MessageKey.CAPTCHA_WRONG_ERROR, AuthMeUtil.getRegisterCaptcha(name));
                handlePlayerCaptcha(player, DialogUtil.regCaptchaText("invalid"));
            }
            AuthMeUtil.resetMessageTask(player, LimboMessageType.REGISTER);
        }
    }

    @SuppressWarnings("DataFlowIssue")
    public static @Nullable User getUser(Player player) {
        return PacketEvents.getAPI().getPlayerManager().getUser(player);
    }
}
