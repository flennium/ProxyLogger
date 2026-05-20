package org.flennn.proxylogger.discord;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.server.ServerRegisteredEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.flennn.proxylogger.config.ConfigManager;
import org.flennn.proxylogger.util.Console;
import org.flennn.proxylogger.util.Utils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class DiscordLogger implements AutoCloseable {
    private final ProxyServer proxy;
    private final Guild guild;
    private final Logger logger;
    private final ConfigManager config;
    private final Map<String, ServerChannels> serverChannels = new ConcurrentHashMap<>();
    private final Set<String> setupInProgress = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ProxyLogger Channel Sync");
        thread.setDaemon(true);
        return thread;
    });

    public DiscordLogger(ProxyServer proxy, JDA jda, String guildId, Logger logger, ConfigManager config) {
        this.proxy = proxy;
        this.guild = jda.getGuildById(guildId);
        this.logger = logger;
        this.config = config;

        if (this.guild == null) {
            throw new IllegalArgumentException("Discord guild was not found. Check discord.guild-id and invite the bot to that server.");
        }

        initialize();
    }

    private void initialize() {
        this.proxy.getEventManager().register(this, this);
        this.proxy.getAllServers().forEach(server -> setupServer(server.getServerInfo().getName()));
        setupServer("proxy");
        this.scheduler.scheduleAtFixedRate(
                () -> this.proxy.getAllServers().forEach(server -> setupServer(server.getServerInfo().getName())),
                this.config.getChannelVerifyIntervalSeconds(),
                this.config.getChannelVerifyIntervalSeconds(),
                TimeUnit.SECONDS
        );
    }

    @Subscribe
    public void onServerRegistered(ServerRegisteredEvent event) {
        setupServer(event.registeredServer().getServerInfo().getName());
    }

    public void log(String serverName, LogType type, String actor, List<String> details) {
        log(serverName, type, actor, details, true);
    }

    private void log(String serverName, LogType type, String actor, List<String> details, boolean retry) {
        String key = normalizeKey(serverName);
        ServerChannels channels = this.serverChannels.get(key);
        if (channels == null) {
            setupServer(serverName);
            channels = this.serverChannels.get(key);
        }
        if (channels == null) {
            if (retry && this.config.shouldAutoCreateChannels()) {
                this.scheduler.schedule(() -> log(serverName, type, actor, details, false), 3, TimeUnit.SECONDS);
            }
            this.logger.fine("Skipping Discord log because no channel exists for " + serverName);
            return;
        }

        TextChannel target = channels.forType(type);
        if (target == null) {
            return;
        }

        if (this.config.useEmbeds()) {
            target.sendMessageEmbeds(createEmbed(serverName, type, actor, details)).queue(null,
                    error -> this.logger.warning("Failed to send Discord log: " + error.getMessage()));
            return;
        }

        target.sendMessage(createPlainMessage(serverName, type, actor, details)).queue(null,
                error -> this.logger.warning("Failed to send Discord log: " + error.getMessage()));
    }

    private void setupServer(String serverName) {
        String key = normalizeKey(serverName);
        if (!this.setupInProgress.add(key)) {
            return;
        }

        this.scheduler.execute(() -> {
            try {
                Category category = findCategory(serverName);
                if (category == null && this.config.shouldAutoCreateChannels()) {
                    category = this.guild.createCategory(formatCategory(serverName)).complete();
                }

                if (category == null) {
                    return;
                }

                TextChannel chat = getOrCreateChannel(category, this.config.getChatChannelName(), this.config.getChatChannelTopic());
                TextChannel commands = getOrCreateChannel(category, this.config.getCommandChannelName(), this.config.getCommandChannelTopic());
                TextChannel joinLeave = getOrCreateChannel(category, this.config.getJoinLeaveChannelName(), this.config.getJoinLeaveChannelTopic());

                this.serverChannels.put(key, new ServerChannels(chat, commands, joinLeave));
            } catch (Exception e) {
                Console.warn(this.logger, "Failed to prepare Discord channels for " + serverName + ": " + e.getMessage());
            } finally {
                this.setupInProgress.remove(key);
            }
        });
    }

    private Category findCategory(String serverName) {
        String expected = formatCategory(serverName);
        return this.guild.getCategories().stream()
                .filter(category -> category.getName().equalsIgnoreCase(expected))
                .findFirst()
                .orElse(null);
    }

    private TextChannel getOrCreateChannel(Category category, String name, String topic) {
        TextChannel existing = category.getTextChannels().stream()
                .filter(channel -> channel.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);

        if (existing != null || !this.config.shouldAutoCreateChannels()) {
            return existing;
        }

        return category.createTextChannel(name)
                .setTopic(topic)
                .complete();
    }

    private MessageEmbed createEmbed(String serverName, LogType type, String actor, List<String> details) {
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle(titleFor(type, actor))
                .setColor(colorFor(type))
                .setDescription(Utils.limit(String.join("\n", details), this.config.getMaxMessageLength()));

        if (this.config.showTimestamp()) {
            builder.setTimestamp(Instant.now());
        }

        String footer = this.config.getFooterText().replace("{server}", serverName);
        if (!footer.isBlank()) {
            builder.setFooter(footer, this.guild.getIconUrl());
        }

        return builder.build();
    }

    private String createPlainMessage(String serverName, LogType type, String actor, List<String> details) {
        String body = "**" + titleFor(type, actor) + "**\n" + String.join("\n", details);
        return Utils.limit(body + "\n" + this.config.getFooterText().replace("{server}", serverName), this.config.getMaxMessageLength());
    }

    private String titleFor(LogType type, String actor) {
        return switch (type) {
            case CHAT -> actor + " sent a chat message";
            case COMMAND -> actor + " ran a command";
            case JOIN -> actor + " joined";
            case LEAVE -> actor + " left";
            case SERVER_SWITCH -> actor + " changed servers";
        };
    }

    private int colorFor(LogType type) {
        return switch (type) {
            case CHAT -> this.config.getChatColor();
            case COMMAND -> this.config.getCommandColor();
            case JOIN -> this.config.getJoinColor();
            case LEAVE -> this.config.getLeaveColor();
            case SERVER_SWITCH -> this.config.getSwitchColor();
        };
    }

    private String formatCategory(String serverName) {
        return this.config.getCategoryFormat().replace("{server}", serverName);
    }

    private String normalizeKey(String serverName) {
        return serverName.toLowerCase(Locale.ROOT);
    }

    @Override
    public void close() {
        this.proxy.getEventManager().unregisterListeners(this);
        this.scheduler.shutdownNow();
    }

    public enum LogType {
        CHAT,
        COMMAND,
        JOIN,
        LEAVE,
        SERVER_SWITCH
    }

    private record ServerChannels(TextChannel chat, TextChannel commands, TextChannel joinLeave) {
        private TextChannel forType(LogType type) {
            return switch (type) {
                case CHAT -> this.chat;
                case COMMAND -> this.commands;
                case JOIN, LEAVE, SERVER_SWITCH -> this.joinLeave;
            };
        }
    }
}
