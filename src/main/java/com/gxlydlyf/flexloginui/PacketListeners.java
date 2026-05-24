package com.gxlydlyf.flexloginui;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.nbt.NBT;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCloseWindow;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCustomClickAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientNameItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.ValidationService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PacketListeners implements PacketListener, Listener {
    private static final HashMap<UUID, String> ANVIL_INPUT = new HashMap<>();
    private static final HashMap<UUID, Integer> ACTIVE_CUSTOM_ANVIL = new HashMap<>();
    private static final HashMap<UUID, String> ANVIL_CONFIRM_PASSWORD = new HashMap<>();
    protected static final HashSet<UUID> ANVIL_MANUALLY_CLOSE = new HashSet<>();
    private static final AuthMeApi authMeApi = AuthMeApi.getInstance();

    public void addActiveCustomAnvil(UUID uuid, int windowId) {
        ACTIVE_CUSTOM_ANVIL.put(uuid, windowId);
    }

    public void removeActiveCustomAnvil(UUID uuid) {
        ACTIVE_CUSTOM_ANVIL.remove(uuid);
    }

    public Integer getActiveCustomAnvil(UUID uuid) {
        return ACTIVE_CUSTOM_ANVIL.getOrDefault(uuid, null);
    }

    public boolean isActiveCustomLogin(UUID uuid) {
        return getActiveCustomAnvil(uuid) == AnvilUtil.WINDOW_ID_LOGIN;
    }

    public boolean isActiveCustomRegister(UUID uuid) {
        return getActiveCustomAnvil(uuid) == AnvilUtil.WINDOW_ID_REGISTER;
    }

    public boolean isActiveCustomAnvil(UUID uuid) {
        return getActiveCustomAnvil(uuid) != null;
    }

    public void refreshCustomAnvil(Player player) {
        UUID uuid = player.getUniqueId();
        if (isActiveCustomAnvil(uuid)) {
            if (isActiveCustomLogin(uuid)) {
                AnvilUtil.openLoginAnvil(player, true);
            } else if (isActiveCustomRegister(uuid)) {
                AnvilUtil.openRegisterAnvil(player, true);
            }
        }
    }

    public boolean isCustomAnvil(int windowId) {
        return windowId == AnvilUtil.WINDOW_ID_LOGIN || windowId == AnvilUtil.WINDOW_ID_REGISTER;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent e) {
        Player player = e.getPlayer();
        if (player == null) {
            return;
        }
        User user = e.getUser();
        UUID uuid = player.getUniqueId();

//        int packetId = e.getPacketId();

        switch (e.getPacketType()) {
            case PacketType.Play.Client.NAME_ITEM:
//            Object buffer = e.getByteBuf();
//
//            // 1. 读取字符串长度（VarInt）
//            int length = ByteBufHelper.readVarInt(buffer);
//
//            // 2. 读取字节数组
//            byte[] bytes = new byte[length];
//            ByteBufHelper.readBytes(buffer, bytes);
//
//            // 3. 转字符串
//            String inputText = new String(bytes, StandardCharsets.UTF_8);
                if (isActiveCustomAnvil(uuid)) {
                    ANVIL_INPUT.put(
                            player.getUniqueId(),
                            new WrapperPlayClientNameItem(e).getItemName()
                    );
                    refreshCustomAnvil(player);
                }
                break;
            case PacketType.Play.Client.CLICK_WINDOW: {
                WrapperPlayClientClickWindow packet = new WrapperPlayClientClickWindow(e);
                int windowId = packet.getWindowId();
                // 只处理我们的铁砧窗口
                if (!isCustomAnvil(windowId)) return;
                e.setCancelled(true);
                // TODO: 刷新时, slot 1 物品 Lore 恢复默认
                refreshCustomAnvil(player);

                boolean isLogin = windowId == AnvilUtil.WINDOW_ID_LOGIN;
                boolean isRegister = windowId == AnvilUtil.WINDOW_ID_REGISTER;
                int slot = packet.getSlot();
                if (slot == 0) {
                    if (FlexLoginUI.config.getBoolean("pages.anvil.allow_close")) {
                        ANVIL_MANUALLY_CLOSE.add(player.getUniqueId());
                        user.closeInventory();
                        sendPlayerReopen(player, isLogin);
                    } else {
                        Bukkit.getScheduler().runTask(FlexLoginUI.instance, () -> player.kickPlayer(
                                isLogin ? DialogUtil.loginText("exit_message") : DialogUtil.registerText("exit_message")
                        ));
                    }

                }
                // 点击输出槽 2
                else if (slot == 2) {
                    user.closeInventory();
                    removeActiveCustomAnvil(uuid);
                    String inputText = ANVIL_INPUT.get(uuid);
                    if (inputText != null) {
                        onPlayerSubmitLogin(player, inputText);
                    }
                    if (isRegister) {
                        if (ANVIL_CONFIRM_PASSWORD.containsKey(uuid)) {
                            onPlayerSubmitRegister(player, ANVIL_CONFIRM_PASSWORD.getOrDefault(uuid, ""), inputText);
                            ANVIL_CONFIRM_PASSWORD.remove(uuid);
                        } else {
                            ANVIL_CONFIRM_PASSWORD.put(uuid, inputText);
                            AnvilUtil.openRegisterAnvil(player, DialogUtil.registerText("tip_confirm"), false);
                        }
                    }
                }
                Bukkit.getScheduler().runTask(FlexLoginUI.instance, player::updateInventory);
                break;
            }
            case PacketType.Play.Client.CLOSE_WINDOW:
                int windowId = new WrapperPlayClientCloseWindow(e).getWindowId();
                if (isCustomAnvil(windowId)) {
                    if (!authMeApi.isUnrestricted(player) && !authMeApi.isAuthenticated(player)) {
                        if (!ANVIL_MANUALLY_CLOSE.contains(player.getUniqueId())) {
                            if (windowId == AnvilUtil.WINDOW_ID_LOGIN) {
                                AnvilUtil.openLoginAnvil(player);
                            } else if (windowId == AnvilUtil.WINDOW_ID_REGISTER) {
                                if (ANVIL_CONFIRM_PASSWORD.containsKey(player.getUniqueId())) {
                                    AnvilUtil.openRegisterAnvil(player, DialogUtil.registerText("tip_confirm"), false);
                                } else {
                                    AnvilUtil.openRegisterAnvil(player);
                                }
                            }
                            break;
                        }
                    }
                    removeActiveCustomAnvil(uuid);
                    ANVIL_MANUALLY_CLOSE.remove(player.getUniqueId());
                }
                break;
            case PacketType.Play.Client.CUSTOM_CLICK_ACTION: {
                WrapperPlayClientCustomClickAction packet = new WrapperPlayClientCustomClickAction(e);
                String id = packet.getId().toString();
                boolean isLogin = id.equals(DialogUtil.LOGIN_DIALOG_ID);
                boolean isRegister = id.equals(DialogUtil.REGISTER_DIALOG_ID);
                if (isLogin || isRegister) {
                    NBT nbt = packet.getPayload();
                    if (nbt instanceof NBTCompound payload) {
                        handleCustomClickAction(
                                player,
                                isLogin,
                                payload.getBooleanOr("close", false),
                                payload.getStringTagValueOrDefault("password", ""),
                                payload.getStringTagValueOrDefault("confirm", "")
                        );
                    }
                }
                break;
            }
            default:
                break;
        }
    }

    public static void sendPlayerReopen(Player player, boolean isLogin) {
        player.sendMessage(isLogin ? DialogUtil.loginText("reopen") : DialogUtil.registerText("reopen"));
    }

    public static void handleCustomClickAction(Player player, boolean isLogin, boolean close, String password, String confirm) {
        if (close) {
            if (DialogUtil.allowClose()) {
                sendPlayerReopen(player, isLogin);
            } else {
                Bukkit.getScheduler().runTask(FlexLoginUI.instance, () -> player.kickPlayer(
                        isLogin ? DialogUtil.loginText("exit_message") : DialogUtil.registerText("exit_message")
                ));
            }
        } else {
            if (isLogin) {
                PacketListeners.onPlayerSubmitLogin(player, password);
            } else {
                PacketListeners.onPlayerSubmitRegister(player, password, confirm);
            }
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent e) {
        Player player = e.getPlayer();
        if (player == null) return;
        PacketTypeCommon packetType = e.getPacketType();
        if (packetType == PacketType.Play.Server.OPEN_WINDOW) {
            WrapperPlayServerOpenWindow wrapper = new WrapperPlayServerOpenWindow(e);
            int windowId = wrapper.getContainerId();
            if (isCustomAnvil(windowId)) {
                addActiveCustomAnvil(player.getUniqueId(), windowId);
            }
        }
//        Bukkit.getLogger().info("Packet send: " + e.getPacketType());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        if (player != null) {
            removeActiveCustomAnvil(player.getUniqueId());
            ANVIL_INPUT.remove(player.getUniqueId());
            ACTIVE_CUSTOM_ANVIL.remove(player.getUniqueId());
            ANVIL_CONFIRM_PASSWORD.remove(player.getUniqueId());
            ANVIL_MANUALLY_CLOSE.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        handlePlayerUI(e.getPlayer(), 5L);// 延迟 5 tick
    }

    public static void handlePlayerUI(Player player, long delay) {
        if (!authMeApi.isUnrestricted(player)) {
            if (ViaVersionUtil.shouldSendAnvil(player) || GeyserUtil.isBedrock(player)) {
                Bukkit.getScheduler().runTaskLater(FlexLoginUI.instance, () -> {
                    if (player.isOnline()) {
                        if (!authMeApi.isAuthenticated(player)) {
                            boolean isReg = authMeApi.isRegistered(player.getName());
                            if (GeyserUtil.isBedrock(player)) {
                                if (isReg) {
                                    GeyserUtil.sendLoginForm(player);
                                } else {
                                    GeyserUtil.sendRegisterForm(player);
                                }
                            } else {
                                if (isReg) {
                                    AnvilUtil.openLoginAnvil(player);
                                } else {
                                    AnvilUtil.openRegisterAnvil(player);
                                }
                            }
                        }
                    }
                }, delay);
            } else if (DialogUtil.isHighServerVersion() || ViaVersionUtil.enabled) {
                handlePlayerDialog(player);
            }
        }
    }

    public static void handlePlayerUI(Player player) {
        handlePlayerUI(player, 0L);
    }

    // 低版本服务器如果 Join 事件发包, dialog 会马上关闭
    public static void handlePlayerDialog(Player player) {
        if (authMeApi.isUnrestricted(player)) {
            return;
        }
        if (GeyserUtil.isBedrock(player)) {
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

    public static void openMessageUI(Player player, String type, String msg) {
        boolean isLogin = Objects.equals(type, "log");
        boolean isReg = Objects.equals(type, "reg");

        if (GeyserUtil.isBedrock(player)) {
            if (isLogin) {
                GeyserUtil.sendLoginForm(player, msg);
            } else if (isReg) {
                GeyserUtil.sendRegisterForm(player, msg);
            }
            return;
        }

        if (ViaVersionUtil.shouldSendAnvil(player)) {
            if (isLogin) {
                AnvilUtil.openLoginAnvil(player, msg, false);
            } else if (isReg) {
                AnvilUtil.openRegisterAnvil(player, DialogUtil.registerText("please_reset_password") + msg, false);
            }
            player.sendMessage(msg);
        } else {
            if (isLogin) {
                DialogUtil.sendLoginDialog(player, msg);
            } else if (isReg) {
                DialogUtil.sendRegisterDialog(player, msg);
            }
        }
    }

    public static void onPlayerSubmitLogin(Player player, String password) {
        if (authMeApi.isRegistered(player.getName())) {
            if (!authMeApi.isAuthenticated(player)) {
                if (authMeApi.checkPassword(player.getName(), password)) {
                    authMeApi.forceLogin(player);
                } else {
                    openMessageUI(player, "log", DialogUtil.loginText("password_error"));
                }
            }
        }
    }

    public static void onPlayerSubmitRegister(Player player, String password, String confirm) {
        if (!authMeApi.isRegistered(player.getName())) {
            if (password.equals(confirm)) {
                ValidationService validationService = AuthMeUtil.validationService;
                fr.xephi.authme.message.Messages messages = AuthMeUtil.messages;
                ValidationService.ValidationResult validationResult = validationService.validatePassword(password, player.getName());
                if (validationResult.hasError()) {
                    MessageKey errorKey = validationResult.getMessageKey();
                    String singleMessage = messages.retrieveSingle(player, errorKey, validationResult.getArgs());
                    openMessageUI(player, "reg", singleMessage);
                } else {
                    if (authMeApi.registerPlayer(player.getName(), password)) {
                        authMeApi.forceLogin(player);
                        player.sendMessage(DialogUtil.registerText("registration_successful"));
                        User user = getUser(player);
                        if (ViaVersionUtil.shouldSendAnvil(player)) {
                            if (user != null) {
                                user.closeInventory();
                            }
                        }
                    } else {
                        openMessageUI(player, "reg", DialogUtil.registerText("registration_failed"));
                    }
                }
            } else {
                openMessageUI(player, "reg", DialogUtil.registerText("password_not_match"));
            }
        }
    }

    @SuppressWarnings("DataFlowIssue")
    public static @Nullable User getUser(Player player) {
        return PacketEvents.getAPI().getPlayerManager().getUser(player);
    }
}
