package org.flennn.proxylogger.config;

import com.velocitypowered.api.plugin.annotation.DataDirectory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.flennn.proxylogger.util.Console;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public class ConfigManager {
    private final Logger logger;
    private final Path configPath;
    private final Yaml yaml;
    private Map<String, Object> config = new HashMap<>();

    @Inject
    public ConfigManager(Logger logger, @DataDirectory Path dataFolder) {
        this.logger = logger;
        this.configPath = dataFolder.resolve("config.yml");

        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        this.yaml = new Yaml(options);

        reload();
    }

    public synchronized void reload() {
        try {
            Files.createDirectories(this.configPath.getParent());
            if (!Files.exists(this.configPath)) {
                copyDefaultConfig();
            }

            try (InputStream inputStream = Files.newInputStream(this.configPath)) {
                Object loaded = this.yaml.load(inputStream);
                this.config = loaded instanceof Map<?, ?> map ? normalizeMap(map) : new HashMap<>();
            }

            validate();
        } catch (IOException e) {
            Console.error(this.logger, "Failed to load config.yml: " + e.getMessage());
            this.config = new HashMap<>();
        }
    }

    private void copyDefaultConfig() throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream("/config.yml")) {
            if (inputStream == null) {
                Files.createFile(this.configPath);
                return;
            }
            Files.copy(inputStream, this.configPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void validate() {
        if (!isDiscordEnabled()) {
            return;
        }

        if (getBotToken().isBlank()) {
            Console.warn(this.logger, "Discord logging is enabled, but discord.bot-token is empty.");
        }

        if (getGuildId().isBlank()) {
            Console.warn(this.logger, "Discord logging is enabled, but discord.guild-id is empty.");
        }
    }

    public boolean isDiscordEnabled() {
        return getBoolean("discord.enabled", true);
    }

    public String getBotToken() {
        return getString("discord.bot-token", "");
    }

    public String getGuildId() {
        return getString("discord.guild-id", "");
    }

    public boolean shouldAutoCreateChannels() {
        return getBoolean("channels.auto-create", true);
    }

    public long getChannelVerifyIntervalSeconds() {
        return Math.max(30, getLong("channels.verify-interval-seconds", 120));
    }

    public String getCategoryFormat() {
        return getString("channels.category-format", "{server}");
    }

    public String getChatChannelName() {
        return normalizeChannelName(getString("channels.names.chat", "chat-logs"));
    }

    public String getCommandChannelName() {
        return normalizeChannelName(getString("channels.names.commands", "commands"));
    }

    public String getJoinLeaveChannelName() {
        return normalizeChannelName(getString("channels.names.join-leave", "join-leave"));
    }

    public String getChatChannelTopic() {
        return getString("channels.topics.chat", "Player chat logs");
    }

    public String getCommandChannelTopic() {
        return getString("channels.topics.commands", "Player command logs");
    }

    public String getJoinLeaveChannelTopic() {
        return getString("channels.topics.join-leave", "Player join and leave logs");
    }

    public boolean isChatLoggingEnabled() {
        return getBoolean("events.chat.enabled", true);
    }

    public boolean isCommandLoggingEnabled() {
        return getBoolean("events.commands.enabled", true);
    }

    public boolean isJoinLoggingEnabled() {
        return getBoolean("events.join.enabled", true);
    }

    public boolean isLeaveLoggingEnabled() {
        return getBoolean("events.leave.enabled", true);
    }

    public boolean isServerSwitchLoggingEnabled() {
        return getBoolean("events.server-switch.enabled", false);
    }

    public boolean shouldLogConsoleCommands() {
        return getBoolean("events.commands.log-console", false);
    }

    public boolean includeIp() {
        return getBoolean("privacy.include-ip", false);
    }

    public boolean includeUuid() {
        return getBoolean("privacy.include-uuid", true);
    }

    public boolean includeClient() {
        return getBoolean("privacy.include-client", true);
    }

    public boolean includeServer() {
        return getBoolean("privacy.include-server", true);
    }

    public List<String> getIgnoredServers() {
        return getLowercaseList("filters.ignored-servers");
    }

    public List<String> getIgnoredCommands() {
        return getLowercaseList("filters.ignored-commands");
    }

    public int getMaxMessageLength() {
        return Math.max(100, getInt("format.max-message-length", 1800));
    }

    public boolean useEmbeds() {
        return getBoolean("format.embeds.enabled", true);
    }

    public boolean showTimestamp() {
        return getBoolean("format.embeds.timestamp", true);
    }

    public String getFooterText() {
        return getString("format.embeds.footer", "ProxyLogger - {server}");
    }

    public int getChatColor() {
        return parseColor(getString("format.embeds.colors.chat", "#3498db"), 0x3498db);
    }

    public int getCommandColor() {
        return parseColor(getString("format.embeds.colors.commands", "#f39c12"), 0xf39c12);
    }

    public int getJoinColor() {
        return parseColor(getString("format.embeds.colors.join", "#2ecc71"), 0x2ecc71);
    }

    public int getLeaveColor() {
        return parseColor(getString("format.embeds.colors.leave", "#e74c3c"), 0xe74c3c);
    }

    public int getSwitchColor() {
        return parseColor(getString("format.embeds.colors.server-switch", "#9b59b6"), 0x9b59b6);
    }

    public String getReloadCommandName() {
        return getString("commands.reload.name", "proxylogger");
    }

    public List<String> getReloadCommandAliases() {
        return getList("commands.reload.aliases");
    }

    public String getReloadPermission() {
        return getString("commands.reload.permission", "proxylogger.admin");
    }

    public String getString(String key, String defaultValue) {
        Object value = getValue(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = getValue(key);
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    public int getInt(String key, int defaultValue) {
        Object value = getValue(key);
        if (value == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public long getLong(String key, long defaultValue) {
        Object value = getValue(key);
        if (value == null) {
            return defaultValue;
        }

        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> getList(String key) {
        Object value = getValue(key);
        if (!(value instanceof List<?> list)) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object getValue(String key) {
        String[] parts = key.split("\\.");
        Map<String, Object> current = this.config;

        for (int i = 0; i < parts.length - 1; i++) {
            Object child = current.get(parts[i]);
            if (!(child instanceof Map<?, ?>)) {
                return null;
            }
            current = (Map<String, Object>) child;
        }

        return current.get(parts[parts.length - 1]);
    }

    private List<String> getLowercaseList(String key) {
        return getList(key).stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
    }

    private String normalizeChannelName(String name) {
        return name.toLowerCase(Locale.ROOT)
                .replace(' ', '-')
                .replaceAll("[^a-z0-9-_]", "");
    }

    private int parseColor(String value, int defaultValue) {
        try {
            String color = value.startsWith("#") ? value.substring(1) : value;
            return Integer.parseInt(color, 16);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Map<String, Object> normalizeMap(Map<?, ?> input) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }

            Object value = entry.getValue();
            if (value instanceof Map<?, ?> map) {
                value = normalizeMap(map);
            }
            result.put(String.valueOf(entry.getKey()), value);
        }
        return result;
    }
}
