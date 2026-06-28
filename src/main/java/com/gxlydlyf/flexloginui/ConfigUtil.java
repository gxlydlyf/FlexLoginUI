package com.gxlydlyf.flexloginui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ConfigUtil extends ConfigAbstract {
    public static LangUtil lang;
    private static final Plugin PLUGIN = FlexLoginUI.instance;

    public ConfigUtil() {
        super("config.yml", "configs/en.yml", "config-version");
    }

    private static final Locale DEFAULT_LOCALE = Locale.getDefault();

    public static Locale toLocale(String fileName) {
        try {
            // 1. 空值判断
            if (fileName == null || fileName.isBlank()) {
                return DEFAULT_LOCALE;
            }

            // 2. 去掉后缀 .yml / .yaml
            String langCode = fileName
                    .trim()
                    .replace(".yml", "")
                    .replace(".yaml", "");

            // 3. 把 zh_CN → zh-CN（forLanguageTag 只认横杠，不认下划线）
            String languageTag = langCode.replace("_", "-");

            // 4. 现代方式：Java 推荐、无弃用
            Locale locale = Locale.forLanguageTag(languageTag);

            // 5. 如果解析后是 root 空语言，返回默认
            if (locale.equals(Locale.ROOT)) {
                return DEFAULT_LOCALE;
            }

            return locale;
        } catch (Exception e) {
            // 任何异常都兜底
            return DEFAULT_LOCALE;
        }
    }

    public static String buildLangResourceName(Locale locale, String configLocale) {
        if (configLocale != null) {
            locale = toLocale(configLocale);
        }
        if (locale.getLanguage().equals("zh")) {
            return "zh_CN.yml";
        } else {
            return "en.yml";
        }
    }

    public static String buildLangResourceName(Locale locale) {
        return buildLangResourceName(locale, YamlConfiguration.loadConfiguration(new File(FlexLoginUI.instance.getDataFolder(), "config.yml")).getString("language", null));
    }

    @Override
    public void loadConfig() {
        this.resourceFilePath = "configs/" + buildLangResourceName(DEFAULT_LOCALE);
        super.loadConfig();
        String langFileName = this.config.getString("language", buildLangResourceName(DEFAULT_LOCALE, null));
        String langFilePath = "langs/" + langFileName;
        String internalPath = "langs/" + buildLangResourceName(DEFAULT_LOCALE, langFileName);
        if (lang == null) {
            lang = new LangUtil(langFilePath, internalPath);
        }
        lang.configFileName = langFilePath;  // 外部目标路径
        lang.configFile = new File(plugin.getDataFolder(), langFilePath); // 最终文件
        lang.resourceFilePath = internalPath;
        lang.reloadConfig();
    }

    /**
     * 初始化语言文件：不存在才生成，不覆盖
     *
     * @param langFileList 语言文件名列表
     */
    public static void initLangFiles(List<String> langFileList) {
        File langsDir = new File(PLUGIN.getDataFolder(), "langs");
        if (!langsDir.exists()) langsDir.mkdirs();

        for (String fileName : langFileList) {
            File target = new File(langsDir, fileName);
            if (target.exists()) continue;

            try (InputStream in = PLUGIN.getResource("langs/" + fileName);
                 OutputStream out = new FileOutputStream(target)) {
                if (in == null) {
                    PLUGIN.getLogger().warning("缺失内置语言资源：" + fileName);
                    continue;
                }
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
//                PLUGIN.getLogger().info("生成语言文件：" + fileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 初始化默认配置文件：强制覆盖写入
     * 资源路径：configs/文件名.yml
     * 输出目录：default_configs
     *
     * @param configNameList 纯文件名（不带路径，如 main.yml）
     */
    public static void initDefaultConfigs(List<String> configNameList) {
        File defDir = new File(PLUGIN.getDataFolder(), "default_configs");
        if (!defDir.exists()) defDir.mkdirs();

        for (String fileName : configNameList) {
            // 内置资源路径
            String resPath = "configs/" + fileName;
            File outFile = new File(defDir, fileName);

            try (InputStream in = PLUGIN.getResource(resPath);
                 OutputStream out = new FileOutputStream(outFile)) {
                if (in == null) {
                    PLUGIN.getLogger().severe("内置配置资源不存在：" + resPath);
                    continue;
                }
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
//                PLUGIN.getLogger().info("已覆盖写入默认配置：" + fileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void initLangFiles() {
        initLangFiles(Arrays.asList(
                "zh_CN.yml",
                "en.yml"
        ));
    }


    public static void initDefaultConfigs() {
        initDefaultConfigs(Arrays.asList(
                "zh_CN.yml",
                "en.yml"
        ));
    }

    public static String getLangText(String path) {
        if (lang != null && path != null) {
            return lang.getString(path);
        }
        return "";
    }


    public static String getPrefixMsg(String path) {
        return getLangText("prefix") + " " + getLangText(path);
    }

    public boolean isDebug() {
        return this.config.getBoolean("debug", false);
    }

    public String getLoginText(String key) {
        return getString("text.login." + key);
    }

    public String getRegisterText(String key) {
        return getString("text.register." + key);
    }

    public String getCaptchaText(String key) {
        return getString("text.captcha." + key);
    }


    public String getRegCaptchaText(String key) {
        return getCaptchaText("register." + key);
    }

    public String getLogCaptchaText(String key) {
        return getCaptchaText("login." + key);
    }

    @Override
    protected int getLatestVersion() {
        return 2;
    }
}