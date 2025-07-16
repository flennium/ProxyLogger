package org.flennn;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.server.ServerRegisteredEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class DiscordLogger {
    private final ProxyServer proxy;
    private final Guild guild;
    private final Map<String, ServerChannels> serverChannels = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Logger logger;
    private final ConfigManager configManager;
    private final Set<String> serversBeingCreated = ConcurrentHashMap.newKeySet();
    private final Set<String> initializedServers = ConcurrentHashMap.newKeySet();
    private final long lastCheckedTimestamp = System.currentTimeMillis();

    public DiscordLogger(ProxyServer proxy, JDA jda, String guildId, Logger logger, ConfigManager configManager) {
        this.proxy = proxy;
        this.logger = logger;
        this.guild = jda.getGuildById(guildId);
        this.configManager = configManager;


        if (guild == null) {
            Utils.Log.severe("Invalid logger guild ID provided");
        }

        initialize();
    }

    private void initialize() {
        proxy.getAllServers().forEach(this::setupServerChannels);
        scheduler.scheduleAtFixedRate(this::verifyChannels, 0, 2, TimeUnit.MINUTES);
    }

    @Subscribe
    public void onServerRegistered(ServerRegisteredEvent event) {
        scheduler.execute(() -> setupServerChannels(event.registeredServer()));
    }

    private void verifyChannels() {
        proxy.getAllServers().forEach(this::setupServerChannels);
    }

    private void setupServerChannels(RegisteredServer server) {
        if (server == null || guild == null) return;

        final String serverName = server.getServerInfo().getName();

        if (initializedServers.contains(serverName) || serversBeingCreated.contains(serverName)) {
            return;
        }

        List<Category> categories = guild.getCategoriesByName(serverName, true);

        Category existingCategory = categories.stream()
                .filter(category -> category.getName().equalsIgnoreCase(serverName))
                .findFirst()
                .orElse(null);

        if (existingCategory == null) {
            serversBeingCreated.add(serverName);
            createNewCategory(serverName);
        } else {
            updateExistingCategory(existingCategory, serverName);
            initializedServers.add(serverName);
        }
    }

    private void createNewCategory(String serverName) {
        guild.createCategory(serverName).queue(category -> {
            try {
                TextChannel chatLogs = getOrCreateChannel(category, "chat-logs");
                TextChannel commands = getOrCreateChannel(category, "commands");
                TextChannel joinLeave = getOrCreateChannel(category, "join-leave");

                serverChannels.put(serverName, new ServerChannels(chatLogs, commands, joinLeave));

                // Mark as completed
                serversBeingCreated.remove(serverName);
                initializedServers.add(serverName);

                Utils.Log.info("Successfully created category and channels for server: " + serverName);
            } catch (Exception e) {
                serversBeingCreated.remove(serverName);
                Utils.Log.warning("Failed to create channels for " + serverName + ": " + e.getMessage());
            }
        }, error -> {
            serversBeingCreated.remove(serverName);
            Utils.Log.warning("Failed to create category for " + serverName + ": " + error.getMessage());
        });
    }

    private void updateExistingCategory(Category category, String serverName) {
        try {
            TextChannel chatLogs = getOrCreateChannel(category, "chat-logs");
            TextChannel commands = getOrCreateChannel(category, "commands");
            TextChannel joinLeave = getOrCreateChannel(category, "join-leave");

            serverChannels.put(serverName, new ServerChannels(chatLogs, commands, joinLeave));

            Utils.Log.info("Successfully updated channels for existing server: " + serverName);
        } catch (Exception e) {
            Utils.Log.warning("Failed to update channels for " + serverName + ": " + e.getMessage());
        }
    }


    private TextChannel getOrCreateChannel(Category category, String channelName) {
        return category.getTextChannels().stream()
                .filter(channel -> channel.getName().equalsIgnoreCase(channelName))
                .findFirst()
                .orElseGet(() -> createChannel(category, channelName));
    }

    private TextChannel createChannel(Category category, String name) {
        try {
            String topic = switch (name) {
                case "chat-logs" -> "Player chat logs - Automatically created by flennn Logger";
                case "commands" -> "Player command logs - Automatically created by flennn Logger";
                case "join-leave" -> "Player join/leave logs - Automatically created by flennn Logger";
                default -> "Automatically created by flennn Logger";
            };

            return category.createTextChannel(name)
                    .setTopic(topic)
                    .complete();
        } catch (Exception e) {
            Utils.Log.warning("Failed to create channel " + name);
            return null;
        }
    }

    public void log(String serverName, LogType type, String message) {
        ServerChannels channels = serverChannels.get(serverName);
        if (channels == null) {
            return;
        }

        try {
            TextChannel target = switch (type) {
                case CHAT -> channels.chat;
                case COMMAND -> channels.commands;
                case JOIN_LEAVE -> channels.joinLeave;
            };

            if (target != null) {
                MessageEmbed embed = createEmbed(type, message, serverName);
                target.sendMessageEmbeds(embed).queue();
            }
        } catch (Exception ignored) {
        }
    }


    private MessageEmbed createEmbed(LogType type, String message, String serverName) {
        EmbedBuilder builder = new EmbedBuilder();
        String[] parts = message.split("\n", 2);
        String title = parts[0];
        String description = parts.length > 1 ? parts[1] : "";

        String decoratedDescription = "\n✨ " + description.replace("```diff\n", "📜 **Log Details:**\n```diff\n")
                + "\n" + getRandomCreativeSymbol();

        switch (type) {
            case CHAT:
                builder.setTitle("💬 " + title)
                        .setColor(Color.BLUE)
                        .setDescription(decoratedDescription);
                break;

            case COMMAND:
                builder.setTitle("⚡ " + title)
                        .setColor(Color.ORANGE)
                        .setDescription("🔧 Command Executed:\n" + decoratedDescription);
                break;

            case JOIN_LEAVE:
                boolean isJoin = title.contains("JOINED") || title.contains("SERVER JOIN");
                builder.setTitle(isJoin ? "🚪 " + title : "🚶 " + title)
                        .setColor(isJoin ? Color.GREEN : Color.RED)
                        .setDescription(decoratedDescription);
                break;
        }
        builder.setFooter("🏰 Server: " + serverName + " • ⏰ " +
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd yyyy HH:mm")),
                guild.getIconUrl());

        return builder.build();
    }

    private String getRandomCreativeSymbol() {
        String[] symbols = {
                "✧･ﾟ: *✧･ﾟ:* *:･ﾟ✧*:･ﾟ✧･ﾟ:* *:･ﾟ✧*:･ﾟ✧",
                "♡ ♡ ♡ ♡ ♡ ♡ ♡ ♡ ♡ ♡ ♡ ♡ ♡ ♡ ♡ ♡ ♡ ♡",
                "✿｡.:* ☆:**:.｡✿｡.:* ☆:**:.｡✿｡.:* ☆:**:.｡",
                "•̩̩͙✩•̩̩͙˚ ｡･:*˚:✧｡･:*:･ﾟ✧*:･ﾟ✧*:･ﾟ✧*:･ﾟ✧",
                "｡ﾟ•┈୨♡୧┈•ﾟ｡ﾟ•┈୨♡୧┈•ﾟ｡ﾟ•┈୨♡୧┈•ﾟ｡ﾟ•",
                "⋆｡°✩⋆｡˚✩⋆｡°✩⋆｡˚✩⋆｡°✩⋆｡˚✩⋆｡°✩⋆｡˚✩",
                "༺♡༻༺♡༻༺♡༻༺♡༻༺♡༻༺♡༻༺♡༻",
                "╰┈➤ ❝ ｡.｡.｡.｡.｡.｡.｡.｡.｡ ❞ ➤┈╯",
                "✦───✿✿✿───✦───✿✿✿───✦───✿✿✿───✦",
                "╔══ஓ๑♡๑ஓ══╗╔══ஓ๑♡๑ஓ══╗╔══ஓ๑♡๑ஓ══╗",
                "༉‧₊˚✧༚˚₊‧༉‧₊˚✧༚˚₊‧༉‧₊˚✧༚˚₊‧༉‧₊˚✧༚˚₊‧༉"
        };

        return symbols[new Random().nextInt(symbols.length)];
    }

    public enum LogType {
        CHAT, COMMAND, JOIN_LEAVE
    }

    private record ServerChannels(
            TextChannel chat,
            TextChannel commands,
            TextChannel joinLeave
    ) {
    }
}