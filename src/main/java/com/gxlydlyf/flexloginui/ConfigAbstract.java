package com.gxlydlyf.flexloginui;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 BoostedYAML 的配置文件工具类
 * 执行顺序：加载 → 版本检测（备份旧版）→ 自动结构更新 → 保存
 */
public abstract class ConfigAbstract {

    protected final Plugin plugin;
    protected File configFile;
    protected final String configVersionKey;
    protected final int latestVersion;
    protected YamlDocument config;
    protected String configFileName;
    protected String resourceFilePath;

    // ==================== 构造方法 ====================

    /**
     * @param plugin               插件
     * @param externalConfigPath   输出到数据文件夹的路径
     * @param internalResourcePath jar内的资源路径
     * @param configVersionKey     版本键
     */
    public ConfigAbstract(Plugin plugin, String externalConfigPath, String internalResourcePath, String configVersionKey) {
        if (plugin == null) plugin = FlexLoginUI.instance;
        this.plugin = plugin;
        this.configFileName = externalConfigPath;
        this.configFile = new File(plugin.getDataFolder(), externalConfigPath);
        this.resourceFilePath = internalResourcePath;
        this.configVersionKey = configVersionKey;
        this.latestVersion = getLatestVersion();
        loadConfig();
    }

    public ConfigAbstract(String externalPath, String internalPath, String configVersionKey) {
        this(null, externalPath, internalPath, configVersionKey);
    }

    public ConfigAbstract(String externalPath, String internalPath) {
        this(null, externalPath, internalPath, "file-version");
    }

    // ==================== 子类实现 ====================
    protected abstract int getLatestVersion();

    // ==================== 核心加载逻辑 ====================
    public void loadConfig() {
        try {
            configFile.getParentFile().mkdirs();

            // 1. 文件不存在 → 从资源释放默认配置
            if (!configFile.exists()) {
                releaseDefaultConfig();
            }

            // 2. 加载配置（不自动更新）
            LoaderSettings loaderSettings = LoaderSettings.builder()
                    .setAutoUpdate(false)
                    .build();

            config = YamlDocument.create(
                    configFile,
                    plugin.getResource(getResourceName()),
                    loaderSettings,
                    DumperSettings.DEFAULT,
                    UpdaterSettings.DEFAULT
            );

            // 3. 检测版本差异，如果版本不同则备份旧配置
            int currentVersion = config.getInt(configVersionKey, 1);
            if (currentVersion != latestVersion) {
                backupOldConfig(currentVersion);
            }

            // 4. 执行 BoostedYAML 自动结构同步
            UpdaterSettings updaterSettings = UpdaterSettings.builder()
                    .setVersioning(new BasicVersioning(configVersionKey))
                    .build();

            config.update(updaterSettings);

            // 5. 保存最终结果
            saveConfig();

            // 6. 输出版本更新日志
            if (currentVersion != latestVersion) {
                plugin.getLogger().info("Config file " + configFile.getName() + " has been updated from v" + currentVersion + " to v" + latestVersion);
            }

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load config file " + configFile.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 释放默认配置文件
     */
    protected void releaseDefaultConfig() {
        try (InputStream in = plugin.getResource(this.resourceFilePath);
             OutputStream out = new FileOutputStream(configFile)) {
            if (in == null) {
                throw new FileNotFoundException("缺失内置资源: " + this.resourceFilePath);
            }
            in.transferTo(out);
            plugin.getLogger().info("Default config file has been released: " + configFile.getName());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to release default config: " + e.getMessage());
        }
    }

    /**
     * 备份旧版本配置文件
     *
     * @param oldVersion 旧版本号
     */
    protected void backupOldConfig(int oldVersion) {
        try {
            // 生成时间戳：yyyyMMdd_HHmmss
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    .format(java.time.LocalDateTime.now());

            // 备份文件名：配置文件名.v旧版本号.时间戳.bak
            String backupFileName = String.format("%s.v%d.%s.bak",
                    configFile.getName(),
                    oldVersion,
                    timestamp);

            File backupFile = new File(configFile.getParentFile(), backupFileName);

            // 复制当前配置文件到备份文件
            try (InputStream in = new FileInputStream(configFile);
                 OutputStream out = new FileOutputStream(backupFile)) {
                in.transferTo(out);
            }

            plugin.getLogger().info("Old config file has been backed up: " + backupFileName);

        } catch (IOException e) {
            plugin.getLogger().warning("Failed to back up old config file: " + e.getMessage());
        }
    }

    protected String getResourceName() {
        return this.resourceFilePath;
    }

    // ==================== 保存 / 重载 ====================
    public void saveConfig() {
        try {
            config.save();
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save config file " + configFile.getName() + ": " + e.getMessage());
        }
    }

    public void reloadConfig() {
        loadConfig();
    }

    public YamlDocument getConfig() {
        return config;
    }

    // ==================== 便捷方法 ====================
    public String getString(String key) {
        if (config.isList(key)) {
            return String.join("\n", config.getStringList(key, new ArrayList<>()));
        }
        return config.getString(key);
    }

    public boolean getBoolean(String key) {
        return config.getBoolean(key);
    }

    public int getInt(String key) {
        return config.getInt(key);
    }

    public List<String> getStringList(String key) {
        return config.getStringList(key);
    }
}