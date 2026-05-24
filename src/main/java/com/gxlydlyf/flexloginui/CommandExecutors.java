package com.gxlydlyf.flexloginui;

import fr.xephi.authme.api.v3.AuthMeApi;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.gxlydlyf.flexloginui.ConfigUtil.getLangText;
import static com.gxlydlyf.flexloginui.ConfigUtil.getPrefixMsg;

public class CommandExecutors implements CommandExecutor, TabCompleter {
    public static final AuthMeApi authMeApi = AuthMeApi.getInstance();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // 统一转小写
        String cmdName = command.getName().toLowerCase();

        if (!(sender instanceof Player player)) {
            if (sender instanceof ConsoleCommandSender consoleCommandSender) {
                if (cmdName.equals("flexloginui") && args.length > 0 && args[0].equals("reload")) {
                    FlexLoginUI.config.reloadConfig();
                    FlexLoginUI.logger.info(getLangText("plugin_reload_success"));
                    return true;
                }
            }
            sender.sendMessage(getLangText("command_only_for_players"));
            return true;
        }

        // switch 判断命令
        switch (cmdName) {
            case "logui" -> {
                if (authMeApi.isUnrestricted(player)) {
                    player.sendMessage(getLangText("no_need_to_login"));
                    return true;
                }
                if (authMeApi.isAuthenticated(player)) {
                    player.sendMessage(getLangText("already_logged_in"));
                    return true;
                }
                // 登录UI逻辑
                PacketListeners.handlePlayerUI(player);
            }
            case "regui" -> {
                if (authMeApi.isUnrestricted(player)) {
                    player.sendMessage(getLangText("no_need_to_register"));
                    return true;
                }
                if (authMeApi.isRegistered(player.getName())) {
                    player.sendMessage(getLangText("already_registered"));
                    return true;
                }
                // 注册UI逻辑
                PacketListeners.handlePlayerUI(player);
            }
            case "flexloginui" -> {
                if (!authMeApi.isUnrestricted(player) && !authMeApi.isAuthenticated(player)) {
                    player.sendMessage(getLangText("not_logged_in"));
                    return true;
                }
                // 管理主命令
                if (args.length == 0) {
                    player.sendMessage("FlexLoginUI " + getLangText("plugin_help.title"));
                    player.sendMessage("§f/flui reload " + getLangText("plugin_help.reload"));
                } else if ("reload".equalsIgnoreCase(args[0])) {
                    FlexLoginUI.config.reloadConfig();
                    player.sendMessage(getPrefixMsg("plugin_reload_success"));
                }
            }
            default -> player.sendMessage(getPrefixMsg("unknown_command"));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> tips = new ArrayList<>();
        if ("flexloginui".equalsIgnoreCase(command.getName()) && args.length == 1) {
            tips.add("reload");
        }
        return tips;
    }
}