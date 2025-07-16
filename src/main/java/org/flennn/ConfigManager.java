package org.flennn;

import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ConfigManager {
    private final Logger logger;
    private final Path configPath;
    private final Yaml yaml;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private Map<String, Object> config;

    @Inject
    public ConfigManager(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataFolder) {
        this.logger = logger;
        this.configPath = dataFolder.resolve("config.yml");

        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        this.yaml = new Yaml(options);
        initialize();
    }

    private void initialize() {
        try {
            loadConfig();
            startAutoReload();
        } catch (Exception e) {
            Utils.Log.severe("❌ Failed to initialize configuration: " + e.getMessage());

            handleCriticalError();
        }
    }

    private synchronized void loadConfig() throws IOException {
        Files.createDirectories(configPath.getParent());

        if (!Files.exists(configPath)) {
            try (InputStream is = getClass().getResourceAsStream("/config.yml")) {
                if (is != null) {
                    Files.copy(is, configPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        try (InputStream inputStream = Files.newInputStream(configPath)) {
            config = yaml.load(inputStream);
        }

        if (config == null) {
            config = new HashMap<>();
        }

        validateConfig();
        Utils.Log.info("✅ Configuration loaded successfully.");
    }

    public synchronized void saveConfig() {
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            yaml.dump(config, writer);
            Utils.Log.info("✅ Configuration saved successfully.");
        } catch (IOException e) {
            Utils.Log.severe("❌ Failed to save config: " + e.getMessage());
        }
    }


    public synchronized void reloadConfig() {
        try {
            loadConfig();
            Utils.Log.info("✅ Configuration reloaded");
        } catch (IOException e) {
            Utils.Log.severe("❌ Config Reload failed: " + e.getMessage());
        }
    }

    private void validateConfig() {
        if (config == null || config.isEmpty()) {
            Utils.Log.severe("❌ Configuration is empty or null");
            return;
        }

        Map<String, Object> discord = getSection("discord");
        if (discord == null || !discord.containsKey("bot-token") || discord.get("bot-token").toString().isEmpty()) {
            Utils.Log.severe("❌ Discord bot token is required");
        }
    }

    public void startAutoReload() {
        scheduler.scheduleAtFixedRate(this::reloadConfig, 30, 30, TimeUnit.MINUTES);
    }

    // =======================
    // Configuration Getters
    // =======================

    public String getBotToken() {
        return getString("discord.bot-token", "");
    }

    public String getLogsGuildID() {
        return getString("discord.logger-guildid", "");
    }


    public Boolean IsLogger() {
        return getBoolean("discord.logger", false);
    }


    // =======================
    // Helper Methods
    // =======================

    @SuppressWarnings("unchecked")
    public Map<String, Object> getSection(String key) {
        Object value = getValue(key);
        return (value instanceof Map) ? (Map<String, Object>) value : new HashMap<>();
    }

    private int getInt(String key, int defaultValue) {
        Object value = getValue(key);
        return value != null ? Integer.parseInt(value.toString()) : defaultValue;
    }

    private String getString(String key, String defaultValue) {
        Object value = getValue(key);
        return value != null ? value.toString() : defaultValue;
    }

    private boolean getBoolean(String key, boolean defaultValue) {
        Object value = getValue(key);
        return value != null ? Boolean.parseBoolean(value.toString()) : defaultValue;
    }

    private List<String> getList(String key) {
        Object value = getValue(key);
        return (value instanceof List) ? (List<String>) value : new ArrayList<>();
    }

    private void set(String key, Object value) {
        setValue(key, value);
        saveConfig();
    }

    private Object getValue(String key) {
        String[] keys = key.split("\\.");
        Map<String, Object> section = config;
        for (int i = 0; i < keys.length - 1; i++) {
            section = (Map<String, Object>) section.computeIfAbsent(keys[i], k -> new HashMap<>());
        }
        return section.get(keys[keys.length - 1]);
    }

    private void setValue(String key, Object value) {
        String[] keys = key.split("\\.");
        Map<String, Object> section = config;
        for (int i = 0; i < keys.length - 1; i++) {
            section = (Map<String, Object>) section.computeIfAbsent(keys[i], k -> new HashMap<>());
        }
        section.put(keys[keys.length - 1], value);
    }

    private void handleCriticalError() {
        logger.severe("CRITICAL CONFIG ERROR - Plugin may not function properly");
    }
}