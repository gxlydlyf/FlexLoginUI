package com.gxlydlyf.flexloginui;

import org.bukkit.Bukkit;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.bedrock.SessionJoinEvent;

public class GeyserListeners implements EventRegistrar {
    @Subscribe
    public void onPlayerJoin(SessionJoinEvent event) {
        PacketListeners.handlePlayerUI(Bukkit.getPlayer(event.connection().playerUuid()), 5L);
    }
}
