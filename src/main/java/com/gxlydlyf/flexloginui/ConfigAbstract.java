package com.gxlydlyf.flexloginui;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * 基于 BoostedYAML 的配置文件工具类
 * 执行顺序：加载 → 自定义版本迁移 → 自动结构更新 → 保存
 */
public abstract class ConfigAbstract {

    protected final Plugin plugin;
    protected File configFile;
    protected final String configVersionKey;
    protected final int latestVersion;
    protected YamlDocument config;
    protected String configFileName;
    protected String resourceFilePath;

    // 自定义迁移步骤
    protected final Map<Integer, VersionStep> versionSteps = new LinkedHashMap<>();

    // ==================== 构造方法 ====================

    /**
     * @param plugin               插件
     * @param externalConfigPath   【输出到数据文件夹的路径】你想要的文件名/路径
     * @param internalResourcePath 【jar 内的资源路径】源文件路径
     * @param configVersionKey     版本键
     */
    public ConfigAbstract(Plugin plugin, String externalConfigPath, String internalResourcePath, String configVersionKey) {
        if (plugin == null) plugin = FlexLoginUI.instance;
        this.plugin = plugin;
        this.configFileName = externalConfigPath;  // 外部目标路径
        this.configFile = new File(plugin.getDataFolder(), externalConfigPath); // 最终文件
        this.resourceFilePath = internalResourcePath; // jar 内源资源路径
        this.configVersionKey = configVersionKey;
        this.latestVersion = getLatestVersion();
        registerMigrations();
        loadConfig();
    }

    public ConfigAbstract(String externalPath, String internalPath, String configVersionKey) {
        this(null, externalPath, internalPath, configVersionKey);
    }

    public ConfigAbstract(String externalPath, String internalPath) {
        this(null, externalPath, internalPath, "file-version");
    }


    // ==================== 子类实现 ====================
    protected String getResourceName() {
        return this.resourceFilePath;
    }

    protected abstract int getLatestVersion();

    protected abstract void registerMigrations();

    // ==================== 注册迁移步骤（兼容你原有写法） ====================
    protected void addVersionStep(int fromVersion, Consumer<YamlDocument> upgradeAction, Consumer<YamlDocument> downgradeAction) {
        versionSteps.put(fromVersion, new VersionStep(fromVersion, fromVersion + 1, upgradeAction, downgradeAction));
    }

    protected void addUpgradeStep(int fromVersion, Consumer<YamlDocument> upgradeAction) {
        addVersionStep(fromVersion, upgradeAction, null);
    }

    // ==================== 核心加载逻辑（顺序正确） ====================
    public void loadConfig() {
        try {
            configFile.getParentFile().mkdirs();

            // 1. 文件不存在 → 手动释放（支持自定义输出路径！）
            if (!configFile.exists()) {
                configFile.getParentFile().mkdirs();
                try (InputStream in = plugin.getResource(this.resourceFilePath);
                     OutputStream out = new FileOutputStream(configFile)) {
                    if (in == null) throw new FileNotFoundException("缺失内置资源: " + this.resourceFilePath);
                    in.transferTo(out); // JDK 8+ 简洁写法
                } catch (Exception e) {
                    plugin.getLogger().severe("释放默认配置失败: " + e.getMessage());
                }
            }

            // ==================== 关键：先加载配置（不自动更新） ====================
            LoaderSettings loaderSettings = LoaderSettings.builder()
                    .setAutoUpdate(false) // 关闭自动更新！
                    .build();

            config = YamlDocument.create(
                    configFile,
                    plugin.getResource(getResourceName()),
                    loaderSettings,
                    DumperSettings.DEFAULT,
                    UpdaterSettings.DEFAULT
            );

            // ==================== 2. 先执行你的自定义版本迁移 ====================
            int currentVersion = config.getInt(configVersionKey, 1);
            if (currentVersion < latestVersion) {
                upgradeConfig(currentVersion, latestVersion);
            } else if (currentVersion > latestVersion) {
                downgradeConfig(currentVersion, latestVersion);
            }

            // ==================== 3. 最后执行 BoostedYAML 自动结构同步 ====================
            UpdaterSettings updaterSettings = UpdaterSettings.builder()
                    .setVersioning(new BasicVersioning(configVersionKey))
                    .build();

            config.update(updaterSettings);

            // 保存最终结果
            saveConfig();

        } catch (IOException e) {
            plugin.getLogger().severe("加载配置文件失败 " + configFile.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== 保存 / 重载 ====================
    public void saveConfig() {
        try {
            config.save();
        } catch (IOException e) {
            plugin.getLogger().severe("保存配置失败 " + configFile.getName() + ": " + e.getMessage());
        }
    }

    public void reloadConfig() {
        loadConfig();
    }

    public YamlDocument getConfig() {
        return config;
    }

    // ==================== 自定义迁移逻辑 ====================
    protected void upgradeConfig(int currentVer, int targetVer) {
        plugin.getLogger().info("升级配置 " + configFile.getName() + " 从 v" + currentVer + " → v" + targetVer);
        for (int ver = currentVer; ver < targetVer; ver++) {
            VersionStep step = versionSteps.get(ver);
            if (step == null || step.upgradeAction == null) {
                throw new IllegalStateException("缺少版本 " + ver + " → " + (ver + 1) + " 升级逻辑！");
            }
            step.upgradeAction.accept(config);
            config.set(configVersionKey, ver + 1);
        }
        config.set(configVersionKey, targetVer);
    }

    protected void downgradeConfig(int currentVer, int targetVer) {
        plugin.getLogger().warning("降级配置 " + configFile.getName() + " 从 v" + currentVer + " → v" + targetVer);
        for (int ver = currentVer; ver > targetVer; ver--) {
            VersionStep step = versionSteps.get(ver - 1);
            if (step == null || step.downgradeAction == null) {
                throw new UnsupportedOperationException("不支持版本 " + ver + " → " + (ver - 1) + " 降级！");
            }
            step.downgradeAction.accept(config);
            config.set(configVersionKey, ver - 1);
        }
        config.set(configVersionKey, targetVer);
    }

    // ==================== 便捷方法 ====================
    public String getString(String key) {
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

    // ==================== 内部类 ====================
    protected static class VersionStep {
        final int fromVersion;
        final int toVersion;
        final Consumer<YamlDocument> upgradeAction;
        final Consumer<YamlDocument> downgradeAction;

        public VersionStep(int fromVersion, int toVersion,
                           Consumer<YamlDocument> upgradeAction,
                           Consumer<YamlDocument> downgradeAction) {
            this.fromVersion = fromVersion;
            this.toVersion = toVersion;
            this.upgradeAction = upgradeAction;
            this.downgradeAction = downgradeAction;
        }
    }
}