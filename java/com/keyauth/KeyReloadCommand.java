package com.keyauth;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class KeyReloadCommand implements CommandExecutor {

    private final KeyAuthPlugin plugin;

    public KeyReloadCommand(KeyAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("keyauth.reload")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用这个命令！");
            return true;
        }

        plugin.reloadPluginConfig();
        sender.sendMessage(ChatColor.GRAY + "系统 >> " + ChatColor.BLUE + "密钥认证插件配置已重载！");
        sender.sendMessage(ChatColor.GRAY + "系统 >> " + ChatColor.GREEN + "当前密钥: " + plugin.getCurrentKey());

        return true;
    }
}