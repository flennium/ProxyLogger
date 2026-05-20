package org.flennn.proxylogger.listener;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import org.flennn.proxylogger.ProxyLogger;
import org.flennn.proxylogger.config.ConfigManager;
import org.flennn.proxylogger.discord.DiscordLogger;
import org.flennn.proxylogger.util.Utils;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ActivityListeners {
    private final ProxyLogger plugin;
    private final Map<UUID, Instant> joinTimes = new ConcurrentHashMap<>();

    public ActivityListeners(ProxyLogger plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        ConfigManager config = this.plugin.getConfigManager();
        DiscordLogger logger = this.plugin.getDiscordLogger();
        if (logger == null) {
            return;
        }

        Player player = event.getPlayer();
        String serverName = event.getServer().getServerInfo().getName();
        if (isIgnoredServer(config, serverName)) {
            return;
        }

        boolean firstServer = event.getPreviousServer().isEmpty();
        if (firstServer) {
            this.joinTimes.put(player.getUniqueId(), Instant.now());
            if (config.isJoinLoggingEnabled()) {
                logger.log(serverName, DiscordLogger.LogType.JOIN, player.getUsername(), buildDetails(player, serverName, null));
            }
            return;
        }

        if (config.isServerSwitchLoggingEnabled()) {
            String previousServer = event.getPreviousServer().get().getServerInfo().getName();
            logger.log(serverName, DiscordLogger.LogType.SERVER_SWITCH, player.getUsername(), buildDetails(player, serverName, previousServer));
        }
    }

    @Subscribe
    public void onCommandExecute(CommandExecuteEvent event) {
        ConfigManager config = this.plugin.getConfigManager();
        DiscordLogger logger = this.plugin.getDiscordLogger();
        if (logger == null || !config.isCommandLoggingEnabled()) {
            return;
        }

        CommandSource source = event.getCommandSource();
        String command = event.getCommand();
        if (isIgnoredCommand(config, command)) {
            return;
        }

        if (source instanceof Player player) {
            player.getCurrentServer().ifPresent(serverConnection -> {
                String serverName = serverConnection.getServerInfo().getName();
                if (isIgnoredServer(config, serverName)) {
                    return;
                }

                List<String> details = buildDetails(player, serverName, null);
                details.add("Command: /" + Utils.escapeMarkdown(command));
                logger.log(serverName, DiscordLogger.LogType.COMMAND, player.getUsername(), details);
            });
            return;
        }

        if (config.shouldLogConsoleCommands()) {
            List<String> details = new ArrayList<>();
            details.add("Command: /" + Utils.escapeMarkdown(command));
            logger.log("proxy", DiscordLogger.LogType.COMMAND, "Console", details);
        }
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        ConfigManager config = this.plugin.getConfigManager();
        DiscordLogger logger = this.plugin.getDiscordLogger();
        if (logger == null || !config.isChatLoggingEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        player.getCurrentServer().ifPresent(serverConnection -> {
            String serverName = serverConnection.getServerInfo().getName();
            if (isIgnoredServer(config, serverName)) {
                return;
            }

            List<String> details = buildDetails(player, serverName, null);
            details.add("Message: " + Utils.escapeMarkdown(event.getMessage()));
            logger.log(serverName, DiscordLogger.LogType.CHAT, player.getUsername(), details);
        });
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        ConfigManager config = this.plugin.getConfigManager();
        DiscordLogger logger = this.plugin.getDiscordLogger();
        if (logger == null || !config.isLeaveLoggingEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        player.getCurrentServer().ifPresent(serverConnection -> {
            String serverName = serverConnection.getServerInfo().getName();
            if (isIgnoredServer(config, serverName)) {
                return;
            }

            Duration duration = Duration.between(
                    this.joinTimes.getOrDefault(player.getUniqueId(), Instant.now()),
                    Instant.now()
            );

            List<String> details = buildDetails(player, serverName, null);
            details.add("Connected: " + formatDuration(duration));
            logger.log(serverName, DiscordLogger.LogType.LEAVE, player.getUsername(), details);
            this.joinTimes.remove(player.getUniqueId());
        });
    }

    private List<String> buildDetails(Player player, String serverName, String previousServer) {
        ConfigManager config = this.plugin.getConfigManager();
        List<String> details = new ArrayList<>();

        if (config.includeServer()) {
            details.add("Server: " + serverName);
        }
        if (previousServer != null && config.includeServer()) {
            details.add("Previous server: " + previousServer);
        }
        if (config.includeUuid()) {
            details.add("UUID: " + player.getUniqueId());
        }
        if (config.includeIp()) {
            details.add("IP: " + getIp(player));
        }
        if (config.includeClient()) {
            details.add("Client: " + getClientVersion(player));
        }

        return details;
    }

    private boolean isIgnoredServer(ConfigManager config, String serverName) {
        return config.getIgnoredServers().contains(serverName.toLowerCase(Locale.ROOT));
    }

    private boolean isIgnoredCommand(ConfigManager config, String command) {
        String rootCommand = command.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        return config.getIgnoredCommands().contains(rootCommand);
    }

    private String getIp(Player player) {
        InetAddress address = player.getRemoteAddress().getAddress();
        return address == null ? "Unknown" : address.getHostAddress();
    }

    private String getClientVersion(Player player) {
        String version = player.getProtocolVersion().getMostRecentSupportedVersion();
        String brand = Objects.requireNonNullElse(player.getClientBrand(), "Unknown");
        return (version == null ? "Unknown" : version) + " / " + brand;
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        return hours + "h " + minutes + "m " + seconds + "s";
    }
}
