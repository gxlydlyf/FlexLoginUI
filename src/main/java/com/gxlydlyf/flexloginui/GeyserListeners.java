package com.gxlydlyf.flexloginui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventBus;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.bedrock.SessionJoinEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundShowDialogConfigurationPacket;

import java.util.UUID;

import static com.gxlydlyf.flexloginui.CommandExecutors.authMeApi;

public class GeyserListeners implements EventRegistrar {
    public void register() {
        EventBus<EventRegistrar> bus = GeyserApi.api().eventBus();
        bus.subscribe(this, GeyserPostInitializeEvent.class, this::onGeyserPostInitializeEvent);
        bus.subscribe(this, SessionJoinEvent.class, this::onPlayerJoin);
    }

    @Subscribe
    public void onPlayerJoin(SessionJoinEvent event) {
        if (event.connection().hasFormOpen()) {
            return;
        }
        UUID uuid = event.connection().playerUuid();
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            if (!authMeApi.isUnrestricted(player) && !authMeApi.isAuthenticated(player)) {
                boolean isLogin = authMeApi.isRegistered(event.connection().name());
                if (isLogin) {
                    GeyserUtil.sendLoginForm(player);
                } else {
                    GeyserUtil.sendRegisterForm(player);
                }
            }
        }
    }

    @Subscribe
    public void onGeyserPostInitializeEvent(GeyserPostInitializeEvent event) {
        // 1. 从注册表取出已经注册好的原生翻译器
        PacketTranslator<ClientboundShowDialogConfigurationPacket> origin =
                (PacketTranslator<ClientboundShowDialogConfigurationPacket>) Registries.JAVA_PACKET_TRANSLATORS.get(ClientboundShowDialogConfigurationPacket.class);

        if (origin != null) {
            // 2. 用自定义Hook类覆盖注册表
            Registries.JAVA_PACKET_TRANSLATORS.register(
                    ClientboundShowDialogConfigurationPacket.class,
                    new HookShowDialogTranslator(origin)
            );

            FlexLoginUI.logger.info("Patch geyser ClientboundShowDialogConfigurationPacket successfully!");
        }
    }
}
