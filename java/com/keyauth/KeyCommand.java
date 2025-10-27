package com.keyauth;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class KeyCommand implements CommandExecutor {

    private final KeyAuthPlugin plugin;

    public KeyCommand(KeyAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以使用这个命令！");
            return true;
        }

        Player player = (Player) sender;

        // 检查玩家是否已经验证
        if (plugin.isAuthenticated(player)) {
            player.sendMessage(ChatColor.WHITE + "你已经通过验证了！");
            return true;
        }

        // 检查参数
        if (args.length != 1) {
            player.sendMessage(ChatColor.BLUE + "使用方法: /key <密码>");
            return true;
        }

        String inputKey = args[0];
        plugin.authenticatePlayer(player, inputKey);

        return true;
    }
}