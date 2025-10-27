package com.keyauth;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class KeyAuthPlugin extends JavaPlugin implements Listener {

    private Set<UUID> authenticatedPlayers;
    private Map<UUID, Long> joinTimeMap;
    private List<Long> verificationTimes;
    private String currentKey;
    private int kickDelay;
    private boolean autoUpdate;
    private String webPath;
    private int updateHour;
    private long nextUpdateTime;

    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();

        // 初始化变量
        authenticatedPlayers = ConcurrentHashMap.newKeySet();
        joinTimeMap = new ConcurrentHashMap<>();
        verificationTimes = Collections.synchronizedList(new ArrayList<>());

        // 加载配置
        reloadPluginConfig();

        // 计算下次更新时间
        calculateNextUpdateTime();

        // 注册事件
        getServer().getPluginManager().registerEvents(this, this);

        // 注册命令
        this.getCommand("key").setExecutor(new KeyCommand(this));
        this.getCommand("keyreload").setExecutor(new KeyReloadCommand(this));
        this.getCommand("keystats").setExecutor(new KeyStatsCommand(this));
        this.getCommand("keyinfo").setExecutor(new KeyInfoCommand(this));

        // 启动定时任务
        startScheduledTasks();

        getLogger().info("密钥认证插件已启用！");
        getLogger().info("当前密钥: " + currentKey);
        getLogger().info("下次更新时间: " + new Date(nextUpdateTime));
    }

    @Override
    public void onDisable() {
        authenticatedPlayers.clear();
        joinTimeMap.clear();
        getLogger().info("密钥认证插件已禁用！");
    }

    public void reloadPluginConfig() {
        this.reloadConfig();
        this.currentKey = this.getConfig().getString("key", "default123");
        this.kickDelay = this.getConfig().getInt("kick-delay", 60);
        this.autoUpdate = this.getConfig().getBoolean("auto-update.enabled", true);
        this.webPath = this.getConfig().getString("auto-update.web-path", "plugins/KeyAuth/web/key.txt");
        this.updateHour = this.getConfig().getInt("auto-update.update-hour", 12);

        // 保存当前密钥到网站文件
        saveKeyToWebFile();
    }

    private void calculateNextUpdateTime() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, updateHour);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // 如果今天已经过了更新时间，就设置为明天
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        nextUpdateTime = calendar.getTimeInMillis();
    }

    private void startScheduledTasks() {
        // 每分钟检查一次是否需要更新密钥
        new BukkitRunnable() {
            @Override
            public void run() {
                checkAndUpdateKey();
            }
        }.runTaskTimer(this, 60 * 20L, 60 * 20L); // 每分钟检查一次

        // 每5分钟更新一次网站文件（确保文件存在）
        new BukkitRunnable() {
            @Override
            public void run() {
                saveKeyToWebFile();
            }
        }.runTaskTimer(this, 5 * 60 * 20L, 5 * 60 * 20L);
    }

    private void checkAndUpdateKey() {
        if (!autoUpdate) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime >= nextUpdateTime) {
            updateKey();
            calculateNextUpdateTime();

            // 通知在线玩家
            Bukkit.broadcastMessage(ChatColor.YELLOW + "⚠ 服务器密码已自动更新！");
            Bukkit.broadcastMessage(ChatColor.GREEN + "请查看QQ群获取新密码。");

            getLogger().info("密钥已自动更新为: " + currentKey);
            getLogger().info("下次更新时间: " + new Date(nextUpdateTime));
        }
    }

    private void updateKey() {
        // 生成6位随机密码（字母+数字）
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder newKey = new StringBuilder(6);

        for (int i = 0; i < 6; i++) {
            newKey.append(chars.charAt(random.nextInt(chars.length())));
        }

        currentKey = newKey.toString();

        // 更新配置
        getConfig().set("key", currentKey);
        saveConfig();

        // 清除所有玩家的验证状态（除了OP）
        authenticatedPlayers.clear();

        // 通知在线非OP玩家需要重新验证
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOp() && authenticatedPlayers.contains(player.getUniqueId())) {
                authenticatedPlayers.remove(player.getUniqueId());
                player.sendMessage(ChatColor.RED + "⚠ 密码已更新，请重新验证！");
                sendVerificationPrompt(player);
            }
        }

        // 保存到网站文件
        saveKeyToWebFile();
    }

    private void saveKeyToWebFile() {
        try {
            File webDir = new File(webPath).getParentFile();
            if (!webDir.exists()) {
                webDir.mkdirs();
            }

            // 创建包含密钥和更新时间的JSON文件
            String jsonContent = String.format(
                    "{\"key\": \"%s\", \"nextUpdate\": %d, \"updateTime\": \"%s\"}",
                    currentKey, nextUpdateTime, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(nextUpdateTime))
            );

            try (FileWriter writer = new FileWriter(webPath)) {
                writer.write(jsonContent);
            }

            // 同时创建一个简单的文本文件只包含密钥（用于兼容性）
            String txtPath = webPath.replace(".txt", "_simple.txt");
            try (FileWriter writer = new FileWriter(txtPath)) {
                writer.write(currentKey);
            }

            // 生成网页文件
            generateWebPage();

        } catch (IOException e) {
            getLogger().warning("无法保存密钥到网站文件: " + e.getMessage());
        }
    }

    private void generateWebPage() {
        try {
            String htmlPath = new File(webPath).getParent() + "/key.html";
            try (FileWriter writer = new FileWriter(htmlPath)) {
                writer.write(getWebPageContent());
            }
        } catch (IOException e) {
            getLogger().warning("无法生成网页文件: " + e.getMessage());
        }
    }

    private String getWebPageContent() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"zh-CN\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>服务器每日密钥</title>\n" +
                "    <link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">\n" +
                "    <style>\n" +
                "        * {\n" +
                "            margin: 0;\n" +
                "            padding: 0;\n" +
                "            box-sizing: border-box;\n" +
                "            font-family: 'Segoe UI', 'Microsoft YaHei', sans-serif;\n" +
                "        }\n" +
                "        \n" +
                "        :root {\n" +
                "            --navy-blue: #1e3a8a;\n" +
                "            --navy-dark: #0f1e4d;\n" +
                "            --navy-light: #3b5fc5;\n" +
                "            --white: #ffffff;\n" +
                "            --light-gray: #f8fafc;\n" +
                "            --medium-gray: #e2e8f0;\n" +
                "            --dark-gray: #64748b;\n" +
                "            --success: #10b981;\n" +
                "            --warning: #f59e0b;\n" +
                "            --danger: #ef4444;\n" +
                "        }\n" +
                "        \n" +
                "        body {\n" +
                "            background: linear-gradient(135deg, var(--navy-blue) 0%, var(--navy-dark) 100%);\n" +
                "            min-height: 100vh;\n" +
                "            display: flex;\n" +
                "            justify-content: center;\n" +
                "            align-items: center;\n" +
                "            padding: 20px;\n" +
                "            color: #333;\n" +
                "        }\n" +
                "        \n" +
                "        .container {\n" +
                "            width: 100%;\n" +
                "            max-width: 480px;\n" +
                "        }\n" +
                "        \n" +
                "        .header-card {\n" +
                "            background: var(--white);\n" +
                "            border-radius: 16px;\n" +
                "            padding: 30px;\n" +
                "            margin-bottom: 20px;\n" +
                "            box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);\n" +
                "            text-align: center;\n" +
                "        }\n" +
                "        \n" +
                "        .logo {\n" +
                "            display: flex;\n" +
                "            justify-content: center;\n" +
                "            align-items: center;\n" +
                "            margin-bottom: 20px;\n" +
                "        }\n" +
                "        \n" +
                "        .logo-icon {\n" +
                "            background: var(--navy-blue);\n" +
                "            color: white;\n" +
                "            width: 50px;\n" +
                "            height: 50px;\n" +
                "            border-radius: 12px;\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            justify-content: center;\n" +
                "            font-size: 24px;\n" +
                "            margin-right: 12px;\n" +
                "        }\n" +
                "        \n" +
                "        .logo-text {\n" +
                "            font-size: 24px;\n" +
                "            font-weight: 700;\n" +
                "            color: var(--navy-blue);\n" +
                "        }\n" +
                "        \n" +
                "        .header-card h1 {\n" +
                "            font-size: 22px;\n" +
                "            color: var(--navy-dark);\n" +
                "            margin-bottom: 8px;\n" +
                "        }\n" +
                "        \n" +
                "        .header-card p {\n" +
                "            color: var(--dark-gray);\n" +
                "            font-size: 15px;\n" +
                "        }\n" +
                "        \n" +
                "        .key-card {\n" +
                "            background: var(--white);\n" +
                "            border-radius: 16px;\n" +
                "            padding: 25px;\n" +
                "            margin-bottom: 20px;\n" +
                "            box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);\n" +
                "            text-align: center;\n" +
                "        }\n" +
                "        \n" +
                "        .card-title {\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            justify-content: center;\n" +
                "            margin-bottom: 20px;\n" +
                "        }\n" +
                "        \n" +
                "        .card-title i {\n" +
                "            color: var(--navy-blue);\n" +
                "            margin-right: 10px;\n" +
                "            font-size: 18px;\n" +
                "        }\n" +
                "        \n" +
                "        .card-title h2 {\n" +
                "            font-size: 18px;\n" +
                "            color: var(--navy-dark);\n" +
                "        }\n" +
                "        \n" +
                "        .key-display {\n" +
                "            background: var(--light-gray);\n" +
                "            border: 2px dashed var(--medium-gray);\n" +
                "            border-radius: 12px;\n" +
                "            padding: 20px;\n" +
                "            margin-bottom: 20px;\n" +
                "            position: relative;\n" +
                "        }\n" +
                "        \n" +
                "        .key-label {\n" +
                "            font-size: 14px;\n" +
                "            color: var(--dark-gray);\n" +
                "            margin-bottom: 10px;\n" +
                "        }\n" +
                "        \n" +
                "        .key-value {\n" +
                "            font-size: 32px;\n" +
                "            font-weight: 700;\n" +
                "            color: var(--navy-blue);\n" +
                "            letter-spacing: 3px;\n" +
                "            margin: 10px 0;\n" +
                "            font-family: 'Courier New', monospace;\n" +
                "        }\n" +
                "        \n" +
                "        .copy-btn {\n" +
                "            background: var(--navy-blue);\n" +
                "            color: white;\n" +
                "            border: none;\n" +
                "            padding: 14px 28px;\n" +
                "            border-radius: 50px;\n" +
                "            font-size: 16px;\n" +
                "            font-weight: 600;\n" +
                "            cursor: pointer;\n" +
                "            transition: all 0.3s ease;\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            justify-content: center;\n" +
                "            width: 100%;\n" +
                "            margin-top: 10px;\n" +
                "        }\n" +
                "        \n" +
                "        .copy-btn i {\n" +
                "            margin-right: 8px;\n" +
                "        }\n" +
                "        \n" +
                "        .copy-btn:hover {\n" +
                "            background: var(--navy-dark);\n" +
                "            transform: translateY(-2px);\n" +
                "            box-shadow: 0 5px 15px rgba(30, 58, 138, 0.3);\n" +
                "        }\n" +
                "        \n" +
                "        .copy-btn.copied {\n" +
                "            background: var(--success);\n" +
                "        }\n" +
                "        \n" +
                "        .countdown-card {\n" +
                "            background: var(--white);\n" +
                "            border-radius: 16px;\n" +
                "            padding: 25px;\n" +
                "            margin-bottom: 20px;\n" +
                "            box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);\n" +
                "        }\n" +
                "        \n" +
                "        .time-display {\n" +
                "            font-size: 28px;\n" +
                "            font-weight: 700;\n" +
                "            color: var(--navy-blue);\n" +
                "            text-align: center;\n" +
                "            margin: 15px 0;\n" +
                "            font-family: 'Courier New', monospace;\n" +
                "        }\n" +
                "        \n" +
                "        .progress-container {\n" +
                "            margin: 20px 0;\n" +
                "        }\n" +
                "        \n" +
                "        .progress-bar {\n" +
                "            width: 100%;\n" +
                "            height: 8px;\n" +
                "            background: var(--medium-gray);\n" +
                "            border-radius: 4px;\n" +
                "            overflow: hidden;\n" +
                "        }\n" +
                "        \n" +
                "        .progress {\n" +
                "            height: 100%;\n" +
                "            background: linear-gradient(90deg, var(--navy-light), var(--navy-blue));\n" +
                "            width: 75%;\n" +
                "            transition: width 1s linear;\n" +
                "        }\n" +
                "        \n" +
                "        .progress-labels {\n" +
                "            display: flex;\n" +
                "            justify-content: space-between;\n" +
                "            margin-top: 8px;\n" +
                "            font-size: 13px;\n" +
                "            color: var(--dark-gray);\n" +
                "        }\n" +
                "        \n" +
                "        .info-card {\n" +
                "            background: var(--white);\n" +
                "            border-radius: 16px;\n" +
                "            padding: 25px;\n" +
                "            margin-bottom: 20px;\n" +
                "            box-shadow: 0 10px 25px rgba(0, 0, 0, 0.1);\n" +
                "        }\n" +
                "        \n" +
                "        .info-item {\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            margin-bottom: 15px;\n" +
                "        }\n" +
                "        \n" +
                "        .info-item:last-child {\n" +
                "            margin-bottom: 0;\n" +
                "        }\n" +
                "        \n" +
                "        .info-icon {\n" +
                "            width: 36px;\n" +
                "            height: 36px;\n" +
                "            background: var(--light-gray);\n" +
                "            border-radius: 10px;\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            justify-content: center;\n" +
                "            margin-right: 15px;\n" +
                "            color: var(--navy-blue);\n" +
                "        }\n" +
                "        \n" +
                "        .info-text {\n" +
                "            flex: 1;\n" +
                "        }\n" +
                "        \n" +
                "        .info-title {\n" +
                "            font-weight: 600;\n" +
                "            color: var(--navy-dark);\n" +
                "            margin-bottom: 3px;\n" +
                "        }\n" +
                "        \n" +
                "        .info-desc {\n" +
                "            font-size: 14px;\n" +
                "            color: var(--dark-gray);\n" +
                "        }\n" +
                "        \n" +
                "        .footer {\n" +
                "            text-align: center;\n" +
                "            color: rgba(255, 255, 255, 0.7);\n" +
                "            font-size: 14px;\n" +
                "            margin-top: 20px;\n" +
                "            padding: 15px;\n" +
                "        }\n" +
                "        \n" +
                "        .footer a {\n" +
                "            color: rgba(255, 255, 255, 0.9);\n" +
                "            text-decoration: none;\n" +
                "        }\n" +
                "        \n" +
                "        .footer a:hover {\n" +
                "            text-decoration: underline;\n" +
                "        }\n" +
                "        \n" +
                "        .notification {\n" +
                "            position: fixed;\n" +
                "            top: 20px;\n" +
                "            right: 20px;\n" +
                "            background: var(--success);\n" +
                "            color: white;\n" +
                "            padding: 15px 25px;\n" +
                "            border-radius: 10px;\n" +
                "            box-shadow: 0 5px 15px rgba(0, 0, 0, 0.2);\n" +
                "            transform: translateX(150%);\n" +
                "            transition: transform 0.3s ease;\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "        }\n" +
                "        \n" +
                "        .notification i {\n" +
                "            margin-right: 10px;\n" +
                "        }\n" +
                "        \n" +
                "        .notification.show {\n" +
                "            transform: translateX(0);\n" +
                "        }\n" +
                "        \n" +
                "        @media (max-width: 480px) {\n" +
                "            .container {\n" +
                "                padding: 10px;\n" +
                "            }\n" +
                "            \n" +
                "            .header-card, .key-card, .countdown-card, .info-card {\n" +
                "                padding: 20px;\n" +
                "            }\n" +
                "            \n" +
                "            .key-value {\n" +
                "                font-size: 26px;\n" +
                "                letter-spacing: 2px;\n" +
                "            }\n" +
                "            \n" +
                "            .time-display {\n" +
                "                font-size: 24px;\n" +
                "            }\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"header-card\">\n" +
                "            <div class=\"logo\">\n" +
                "                <div class=\"logo-icon\">\n" +
                "                    <i class=\"fas fa-key\"></i>\n" +
                "                </div>\n" +
                "                <div class=\"logo-text\">KeyAuth</div>\n" +
                "            </div>\n" +
                "            <h1>服务器每日密钥</h1>\n" +
                "            <p>安全验证 · 每日更新 · 请勿外泄</p>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"key-card\">\n" +
                "            <div class=\"card-title\">\n" +
                "                <i class=\"fas fa-lock\"></i>\n" +
                "                <h2>今日密钥</h2>\n" +
                "            </div>\n" +
                "            \n" +
                "            <div class=\"key-display\">\n" +
                "                <div class=\"key-label\">当前有效密钥</div>\n" +
                "                <div class=\"key-value\" id=\"keyValue\">加载中...</div>\n" +
                "                <div class=\"key-label\">点击下方按钮复制</div>\n" +
                "            </div>\n" +
                "            \n" +
                "            <button class=\"copy-btn\" onclick=\"copyKey()\" id=\"copyBtn\">\n" +
                "                <i class=\"far fa-copy\"></i> 复制密钥\n" +
                "            </button>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"countdown-card\">\n" +
                "            <div class=\"card-title\">\n" +
                "                <i class=\"far fa-clock\"></i>\n" +
                "                <h2>下次更新倒计时</h2>\n" +
                "            </div>\n" +
                "            \n" +
                "            <div class=\"time-display\" id=\"countdown\">--:--:--</div>\n" +
                "            \n" +
                "            <div class=\"progress-container\">\n" +
                "                <div class=\"progress-bar\">\n" +
                "                    <div class=\"progress\" id=\"progressBar\"></div>\n" +
                "                </div>\n" +
                "                <div class=\"progress-labels\">\n" +
                "                    <span>上次更新</span>\n" +
                "                    <span>下次更新</span>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "            \n" +
                "            <div class=\"info-item\">\n" +
                "                <div class=\"info-icon\">\n" +
                "                    <i class=\"fas fa-sync-alt\"></i>\n" +
                "                </div>\n" +
                "                <div class=\"info-text\">\n" +
                "                    <div class=\"info-title\">自动更新</div>\n" +
                "                    <div class=\"info-desc\">每天中午12:00自动生成新密钥</div>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"info-card\">\n" +
                "            <div class=\"card-title\">\n" +
                "                <i class=\"fas fa-info-circle\"></i>\n" +
                "                <h2>使用说明</h2>\n" +
                "            </div>\n" +
                "            \n" +
                "            <div class=\"info-item\">\n" +
                "                <div class=\"info-icon\">\n" +
                "                    <i class=\"fas fa-gamepad\"></i>\n" +
                "                </div>\n" +
                "                <div class=\"info-text\">\n" +
                "                    <div class=\"info-title\">游戏内验证</div>\n" +
                "                    <div class=\"info-desc\">在服务器内输入 <code>/key 密钥</code> 完成验证</div>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "            \n" +
                "            <div class=\"info-item\">\n" +
                "                <div class=\"info-icon\">\n" +
                "                    <i class=\"fas fa-shield-alt\"></i>\n" +
                "                </div>\n" +
                "                <div class=\"info-text\">\n" +
                "                    <div class=\"info-title\">安全提示</div>\n" +
                "                    <div class=\"info-desc\">请勿在游戏公屏或公开场合分享密钥</div>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "            \n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"footer\">\n" +
                "            <p>Powered by <a href=\"#\" target=\"_blank\">HotWindLibs</a> & <a href=\"#\" target=\"_blank\">KeyAuth</a></p>\n" +
                "            <p>© 2023 服务器密钥管理系统 - 保留所有权利</p>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "    \n" +
                "    <div class=\"notification\" id=\"notification\">\n" +
                "        <i class=\"fas fa-check-circle\"></i> 密钥已复制到剪贴板！\n" +
                "    </div>\n" +
                "\n" +
                "    <script>\n" +
                "        // 加载密钥数据\n" +
                "        async function loadKeyData() {\n" +
                "            try {\n" +
                "                // 尝试从JSON文件加载\n" +
                "                const response = await fetch('key.txt?' + new Date().getTime());\n" +
                "                const data = await response.json();\n" +
                "                \n" +
                "                document.getElementById('keyValue').textContent = data.key;\n" +
                "                \n" +
                "                // 设置下次更新时间\n" +
                "                nextUpdateTime = data.nextUpdate;\n" +
                "                \n" +
                "                startCountdown();\n" +
                "                \n" +
                "            } catch (error) {\n" +
                "                // 如果JSON加载失败，尝试从简单文本文件加载\n" +
                "                try {\n" +
                "                    const response = await fetch('key_simple.txt?' + new Date().getTime());\n" +
                "                    const key = await response.text();\n" +
                "                    document.getElementById('keyValue').textContent = key;\n" +
                "                    \n" +
                "                    // 设置默认的更新时间（今天中午12点）\n" +
                "                    const now = new Date();\n" +
                "                    const nextUpdate = new Date();\n" +
                "                    nextUpdate.setHours(12, 0, 0, 0);\n" +
                "                    if (now > nextUpdate) {\n" +
                "                        nextUpdate.setDate(nextUpdate.getDate() + 1);\n" +
                "                    }\n" +
                "                    nextUpdateTime = nextUpdate.getTime();\n" +
                "                    \n" +
                "                    startCountdown();\n" +
                "                    \n" +
                "                } catch (fallbackError) {\n" +
                "                    document.getElementById('keyValue').textContent = '加载失败';\n" +
                "                    console.error('无法加载密钥:', fallbackError);\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "        \n" +
                "        // 开始倒计时\n" +
                "        function startCountdown() {\n" +
                "            updateCountdown(); // 立即更新一次\n" +
                "            updateInterval = setInterval(updateCountdown, 1000);\n" +
                "        }\n" +
                "        \n" +
                "        // 更新倒计时显示\n" +
                "        function updateCountdown() {\n" +
                "            const now = Date.now();\n" +
                "            const timeLeft = nextUpdateTime - now;\n" +
                "            \n" +
                "            if (timeLeft <= 0) {\n" +
                "                // 时间到，重新加载数据\n" +
                "                clearInterval(updateInterval);\n" +
                "                document.getElementById('countdown').textContent = '更新中...';\n" +
                "                document.getElementById('progressBar').style.width = '0%';\n" +
                "                loadKeyData();\n" +
                "                return;\n" +
                "            }\n" +
                "            \n" +
                "            // 计算时分秒\n" +
                "            const hours = Math.floor(timeLeft / (1000 * 60 * 60));\n" +
                "            const minutes = Math.floor((timeLeft % (1000 * 60 * 60)) / (1000 * 60));\n" +
                "            const seconds = Math.floor((timeLeft % (1000 * 60)) / 1000);\n" +
                "            \n" +
                "            // 更新显示\n" +
                "            document.getElementById('countdown').textContent = \n" +
                "                `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;\n" +
                "            \n" +
                "            // 更新进度条（一天的总毫秒数）\n" +
                "            const totalDayTime = 24 * 60 * 60 * 1000;\n" +
                "            const progress = ((totalDayTime - timeLeft) / totalDayTime) * 100;\n" +
                "            document.getElementById('progressBar').style.width = progress + '%';\n" +
                "        }\n" +
                "        \n" +
                "        // 复制密钥到剪贴板\n" +
                "        function copyKey() {\n" +
                "            const keyValue = document.getElementById('keyValue').textContent;\n" +
                "            if (!keyValue || keyValue === '加载中...' || keyValue === '加载失败') return;\n" +
                "            \n" +
                "            navigator.clipboard.writeText(keyValue).then(() => {\n" +
                "                const btn = document.getElementById('copyBtn');\n" +
                "                const notification = document.getElementById('notification');\n" +
                "                \n" +
                "                // 显示复制成功提示\n" +
                "                btn.innerHTML = '<i class=\"fas fa-check\"></i> 已复制';\n" +
                "                btn.classList.add('copied');\n" +
                "                \n" +
                "                notification.classList.add('show');\n" +
                "                \n" +
                "                setTimeout(() => {\n" +
                "                    btn.innerHTML = '<i class=\"far fa-copy\"></i> 复制密钥';\n" +
                "                    btn.classList.remove('copied');\n" +
                "                    notification.classList.remove('show');\n" +
                "                }, 2000);\n" +
                "            }).catch(err => {\n" +
                "                console.error('复制失败:', err);\n" +
                "                // 降级方案\n" +
                "                const textArea = document.createElement(\"textarea\");\n" +
                "                textArea.value = keyValue;\n" +
                "                document.body.appendChild(textArea);\n" +
                "                textArea.select();\n" +
                "                document.execCommand('copy');\n" +
                "                document.body.removeChild(textArea);\n" +
                "                \n" +
                "                const notification = document.getElementById('notification');\n" +
                "                notification.innerHTML = '<i class=\"fas fa-check-circle\"></i> 密钥已复制！';\n" +
                "                notification.classList.add('show');\n" +
                "                setTimeout(() => {\n" +
                "                    notification.classList.remove('show');\n" +
                "                }, 2000);\n" +
                "            });\n" +
                "        }\n" +
                "        \n" +
                "        // 页面加载时初始化\n" +
                "        document.addEventListener('DOMContentLoaded', function() {\n" +
                "            loadKeyData();\n" +
                "            \n" +
                "            // 每5分钟自动刷新数据\n" +
                "            setInterval(loadKeyData, 5 * 60 * 1000);\n" +
                "        });\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 记录玩家加入时间
        joinTimeMap.put(player.getUniqueId(), System.currentTimeMillis());

        // 发送验证提示
        sendVerificationPrompt(player);

        // 延迟踢出未验证玩家
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && !isAuthenticated(player)) {
                    player.kickPlayer(ChatColor.RED + "验证超时！\n请获取正确密码后重新进入服务器。\n密码请在QQ群中获取。");
                }
            }
        }.runTaskLater(this, kickDelay * 20L);
    }

    private void sendVerificationPrompt(Player player) {
        player.sendMessage(" ");
        player.sendMessage(ChatColor.GRAY + "系统 >> " + ChatColor.BLUE + "欢迎你!");
        player.sendMessage(ChatColor.GRAY + "系统 >> " + ChatColor.BLUE + "请使用 " + ChatColor.WHITE + "/key <密码>" + ChatColor.BLUE + " 进行验证");
        player.sendMessage(" ");
        player.sendMessage(ChatColor.GRAY + "系统 >> " + ChatColor.BLUE + "你有 " + ChatColor.RED + kickDelay + ChatColor.BLUE + " 秒时间输入密码");
        player.sendMessage(" ");

        // 发送标题提示
        player.sendTitle(
                ChatColor.BLUE + "⚠ 你当前需要验证才可移动",
                ChatColor.WHITE + "使用 /key <密码> 进行验证",
                10, 60, 10
        );
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // 如果玩家未验证，阻止移动
        if (!isAuthenticated(player) && !player.isOp()) {
            // 检查是否真的移动了位置
            if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                    event.getFrom().getBlockY() != event.getTo().getBlockY() ||
                    event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
                event.setTo(event.getFrom());
                player.sendMessage(ChatColor.GRAY + "系统 >> " + ChatColor.BLUE + "请先使用 " + ChatColor.WHITE + "/key <密码>" + ChatColor.BLUE + " 进行验证!");
            }
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().toLowerCase();

        // 允许验证命令和退出命令
        if (message.startsWith("/key ") || message.equals("/key") ||
                message.startsWith("/quit") || message.equals("/quit") ||
                message.startsWith("/exit") || message.equals("/exit") ||
                message.startsWith("/keystats") || message.equals("/keystats") ||
                message.startsWith("/keyinfo") || message.equals("/keyinfo")) {
            return;
        }

        // 阻止未验证玩家使用其他命令
        if (!isAuthenticated(player) && !player.isOp()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.GRAY + "系统 >> " + ChatColor.BLUE + "请先使用 " + ChatColor.WHITE + "/key <密码>" + ChatColor.BLUE + " 进行验证!");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        // 玩家退出时移除验证状态和加入时间
        authenticatedPlayers.remove(playerId);
        joinTimeMap.remove(playerId);
    }

    public boolean authenticatePlayer(Player player, String inputKey) {
        if (inputKey.equals(currentKey)) {
            long joinTime = joinTimeMap.getOrDefault(player.getUniqueId(), System.currentTimeMillis());
            long verificationTime = System.currentTimeMillis() - joinTime;
            double seconds = verificationTime / 1000.0;

            // 记录验证时间
            verificationTimes.add(verificationTime);

            // 计算超越百分比
            double percentile = calculatePercentile(verificationTime);

            authenticatedPlayers.add(player.getUniqueId());

            // 发送成功标题
            sendSuccessTitle(player, seconds, percentile);

            player.sendMessage(ChatColor.GRAY + "系统 >> " + ChatColor.BLUE + "✅ 验证成功！欢迎来到服务器!");
            player.sendMessage(ChatColor.GRAY + "系统 >> " +ChatColor.BLUE + "现在你可以正常游戏了");

            return true;
        } else {
            player.sendMessage(ChatColor.GRAY + "系统 >> " + ChatColor.RED + "❌ 密码错误!");
            player.sendMessage(ChatColor.GRAY + "系统 >> " + ChatColor.BLUE + "请检查密码是否正确");
            return false;
        }
    }

    private void sendSuccessTitle(Player player, double seconds, double percentile) {
        String timeString = String.format("%.2f", seconds);
        String percentileString = String.format("%.1f", percentile);

        // 主标题
        player.sendTitle(
                ChatColor.BLUE + "✅ 验证成功！",
                ChatColor.WHITE + "输入时间 " + timeString + " 秒 | 你当前超越了 " + percentileString + "% 玩家",
                10, 70, 20
        );

        // 3秒后显示欢迎信息
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.sendTitle(
                            ChatColor.BLUE + "欢迎，" + player.getName() + "!",
                            ChatColor.WHITE + "Enjoy This Game ♡ ",
                            10, 40, 10
                    );
                }
            }
        }.runTaskLater(this, 60L); // 3秒后执行 (60 ticks = 3 seconds)
    }

    private double calculatePercentile(long verificationTime) {
        if (verificationTimes.size() <= 1) {
            return 100.0; // 第一个玩家，超越100%
        }

        int fasterCount = 0;
        synchronized (verificationTimes) {
            for (Long time : verificationTimes) {
                if (time < verificationTime) {
                    fasterCount++;
                }
            }
        }

        double percentile = (1.0 - (double) fasterCount / verificationTimes.size()) * 100;
        return Math.max(0, Math.min(100, percentile)); // 确保在0-100范围内
    }

    public boolean isAuthenticated(Player player) {
        return authenticatedPlayers.contains(player.getUniqueId()) || player.isOp();
    }

    public String getCurrentKey() {
        return currentKey;
    }

    public long getNextUpdateTime() {
        return nextUpdateTime;
    }

    public int getUpdateHour() {
        return updateHour;
    }

    public List<Long> getVerificationTimes() {
        return new ArrayList<>(verificationTimes);
    }

    public void clearStatistics() {
        verificationTimes.clear();
    }
}