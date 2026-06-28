package com.gxlydlyf.flexloginui;

import fr.xephi.authme.api.v3.AuthMeApi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.event.EventRegistrar;

import java.util.UUID;

import static com.gxlydlyf.flexloginui.DialogUtil.*;

public class GeyserUtil {
    public static boolean enabled = false;
    public static final ConfigUtil config = FlexLoginUI.config;
    public static GeyserListeners geyserListeners;

    public static String bedrockPlaceholder(String key) {
        return config.getString("text.bedrock_placeholder." + key);
    }

    public static boolean allowClose() {
        return config.getBoolean("pages.bedrock.allow_close");
    }

    public static boolean isBedrock(UUID uuid) {
        if (!enabled) return false;
        return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
    }

    public static boolean isBedrock(Player player) {
        return isBedrock(player.getUniqueId());
    }

    public static void registerEventListener() {
        geyserListeners = new GeyserListeners();
        geyserListeners.register();
    }

    public static void unregisterEventListener() {
        GeyserApi.api().eventBus().unregisterAll((EventRegistrar) FlexLoginUI.instance);
    }

    public static GeyserConnection getConnection(UUID uuid) {
        return GeyserApi.api().connectionByUuid(uuid);
    }

    public static boolean hasOpenForm(Player player) {
        return getConnection(player.getUniqueId()).hasFormOpen();
    }

    public static void sendForm(Player player, CustomForm.Builder formBuilder) {
        FloodgatePlayer floodgatePlayer = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
        Bukkit.getServer().getScheduler().runTaskLater(
                FlexLoginUI.instance,
                () -> {
                    if (!AuthMeApi.getInstance().isAuthenticated(player)) {
                        floodgatePlayer.sendForm(formBuilder.build());
                        if (FlexLoginUI.config.isDebug()) {
                            FlexLoginUI.logger.info("Send Geyser form");
                        }
                    }
                },
                2L
        );
    }

    public static void sendLoginForm(Player player, String tip) {
        sendForm(player, buildLoginForm(player, tip));
    }

    public static void sendRegisterForm(Player player, String tip) {
        sendForm(player, buildRegisterForm(player, tip));
    }

    public static void sendLoginForm(Player player) {
        sendLoginForm(player, loginText("tip"));
    }

    public static void sendRegisterForm(Player player) {
        sendRegisterForm(player, registerText("tip"));
    }

    public static void sendLogCaptchaForm(Player player, String tip) {
        sendForm(player, buildLogCaptchaForm(player, tip));
    }

    public static void sendRegCaptchaForm(Player player, String tip) {
        sendForm(player, buildRegCaptchaForm(player, tip));
    }

    public static void handleClose(Player player, boolean isLogin) {
        if (player.isDead()) {
            return;
        }
        if (allowClose()) {
            PacketListeners.sendPlayerReopen(player, isLogin);
        } else {
            PacketListeners.disallowCloseKick(player, isLogin);
        }
    }

    static CustomForm.Builder buildLoginForm(Player player, String tip) {
        return CustomForm.builder()
                .title(loginText("title"))
                .label(tip)
                .input(loginText("password_label"), bedrockPlaceholder("login.password"))
                .validResultHandler(response ->
                        PacketListeners.onPlayerSubmitLogin(player, response.asInput()))
                .closedResultHandler(response -> handleClose(player, true))
                .invalidResultHandler(response -> PacketListeners.openMessageUI(player, true, response.errorMessage()));
    }

    static CustomForm.Builder buildRegisterForm(Player player, String tip) {
        return CustomForm.builder()
                .title(registerText("title"))
                .label(tip)
                .input(registerText("password_label"), bedrockPlaceholder("register.password"))
                .input(registerText("confirm_label"), bedrockPlaceholder("register.confirm"))
                .validResultHandler(response ->
                        PacketListeners.onPlayerSubmitRegister(player, response.next(), response.next()))
                .closedResultHandler(response -> handleClose(player, false))
                .invalidResultHandler(response -> PacketListeners.openMessageUI(player, false, response.errorMessage()));
    }

    public static CustomForm.Builder buildLogCaptchaForm(Player player, String tip) {
        return CustomForm.builder()
                .title(logCaptchaText("title"))
                .label(tip)
                .input(logCaptchaText("label"), "")
                .validResultHandler(response -> PacketListeners.onPlayerVerifyLogCaptcha(player, response.asInput()))
                .closedResultHandler(response -> handleClose(player, true))
                .invalidResultHandler(response ->
                        PacketListeners.handlePlayerCaptcha(player, response.errorMessage()));
    }

    public static CustomForm.Builder buildRegCaptchaForm(Player player, String tip) {
        return CustomForm.builder()
                .title(regCaptchaText("title"))
                .label(tip)
                .input(regCaptchaText("label"), "")
                .validResultHandler(response ->
                        PacketListeners.onPlayerVerifyRegCaptcha(player, response.asInput()))
                .closedResultHandler(response -> handleClose(player, false))
                .invalidResultHandler(response ->
                        PacketListeners.handlePlayerCaptcha(player, response.errorMessage()));
    }
}
