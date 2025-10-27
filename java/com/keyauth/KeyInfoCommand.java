package com.keyauth;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;

public class KeyInfoCommand implements CommandExecutor {

    private final KeyAuthPlugin plugin;

    public KeyInfoCommand(KeyAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以使用这个命令！");
            return true;
        }

        Player player = (Player) sender;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long nextUpdate = plugin.getNextUpdateTime();
        long currentTime = System.currentTimeMillis();
        long timeLeft = nextUpdate - currentTime;

        int hours = (int) (timeLeft / (1000 * 60 * 60));
        int minutes = (int) ((timeLeft % (1000 * 60 * 60)) / (1000 * 60));

        player.sendMessage(ChatColor.GRAY + "系统 >> " + ChatColor.BLUE + "下次更新时间: " + ChatColor.WHITE + sdf.format(new Date(nextUpdate)));
        player.sendMessage(ChatColor.GRAY + "系统 >> " + ChatColor.BLUE + "剩余时间: " + ChatColor.WHITE + hours + "小时 " + minutes + "分钟");
        player.sendMessage(ChatColor.GRAY + "系统 >> " + ChatColor.BLUE + "自动更新: " + (plugin.getConfig().getBoolean("auto-update.enabled") ? ChatColor.GREEN + "开启" : ChatColor.RED + "关闭"));
        player.sendMessage(ChatColor.GRAY + "系统 >> " + ChatColor.BLUE + "查看网页获取密钥");

        return true;
    }
}