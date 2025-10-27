package com.keyauth;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class KeyStatsCommand implements CommandExecutor {

    private final KeyAuthPlugin plugin;

    public KeyStatsCommand(KeyAuthPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("keyauth.stats")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用这个命令！");
            return true;
        }

        List<Long> times = plugin.getVerificationTimes();

        if (times.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "系统 >> " + ChatColor.BLUE + "暂无验证统计数据。");
            return true;
        }

        // 计算统计数据
        long total = 0;
        long fastest = Long.MAX_VALUE;
        long slowest = 0;

        for (Long time : times) {
            total += time;
            if (time < fastest) fastest = time;
            if (time > slowest) slowest = time;
        }

        double avg = total / (double) times.size() / 1000.0;
        double fastestSec = fastest / 1000.0;
        double slowestSec = slowest / 1000.0;

        sender.sendMessage(ChatColor.GRAY + "系统 >> " + ChatColor.BLUE + "总验证次数: " + ChatColor.GREEN + times.size());
        sender.sendMessage(ChatColor.GRAY + "系统 >> " + ChatColor.BLUE + "最快验证: " + ChatColor.GREEN + String.format("%.2f", fastestSec) + "秒");
        sender.sendMessage(ChatColor.GRAY + "系统 >> " + ChatColor.BLUE + "最慢验证: " + ChatColor.GREEN + String.format("%.2f", slowestSec) + "秒");
        sender.sendMessage(ChatColor.GRAY + "系统 >> " + ChatColor.BLUE + "平均验证: " + ChatColor.GREEN + String.format("%.2f", avg) + "秒");

        if (args.length > 0 && args[0].equalsIgnoreCase("clear") && sender.hasPermission("keyauth.stats.clear")) {
            plugin.clearStatistics();
            sender.sendMessage(ChatColor.GRAY + "系统 >> " + ChatColor.BLUE + "验证统计已清空！");
        }

        return true;
    }
}