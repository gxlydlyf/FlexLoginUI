package com.gxlydlyf.flexloginui;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.EventManager;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.logging.Logger;

public final class FlexLoginUI extends JavaPlugin {
    public static JavaPlugin instance;
    public static PacketListenerCommon listenersCommon;
    public static ConfigUtil config;
    public static CommandExecutors commandExecutors;
    public static Logger logger;

    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;
        logger = getLogger();

        ConfigUtil.initLangFiles();
        ConfigUtil.initDefaultConfigs();
        config = new ConfigUtil();

        AnvilUtil.setAnvilWindowType();

        // 判断 Geyser 是否加载
        boolean hasGeyser = Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot");
        // 判断 Floodgate 是否加载
        boolean hasFloodgate = Bukkit.getPluginManager().isPluginEnabled("floodgate");
        GeyserUtil.enabled = hasGeyser && hasFloodgate;
        if (GeyserUtil.enabled) {
            getLogger().info("Found Geyser & Floodgate!");
            GeyserUtil.registerEventListener();
        }

        AuthMeUtil.getAuthMe();

        EventManager events = PacketEvents.getAPI().getEventManager();
        PacketListeners listeners = new PacketListeners();
        listenersCommon = events.registerListener(listeners, PacketListenerPriority.NORMAL);
        Bukkit.getPluginManager().registerEvents(listeners, this);
        commandExecutors = new CommandExecutors();
        this.getCommand("flexloginui").setExecutor(commandExecutors);
        this.getCommand("logui").setExecutor(commandExecutors);
        this.getCommand("regui").setExecutor(commandExecutors);

        ViaVersionUtil.enabled = Bukkit.getPluginManager().isPluginEnabled("ViaVersion");
        if (ViaVersionUtil.enabled) {
            getLogger().info("Found ViaVersion!");
        }
        ViaVersionUtil.tryLoadHook();

        getLogger().info("FlexLoginUI " + getDescription().getVersion() + " has been enabled!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        HandlerList.unregisterAll(this);

        EventManager events = PacketEvents.getAPI().getEventManager();
        events.unregisterListener(listenersCommon);

        if (GeyserUtil.enabled) {
            GeyserUtil.unregisterEventListener();
        }

        // 2. 取消本插件所有调度任务
        Bukkit.getScheduler().cancelTasks(this);
        getServer().getScheduler().cancelTasks(this);
        getServer().getServicesManager().unregisterAll(this);

        getLogger().info("FlexLoginUI has been disabled!");
    }
}
