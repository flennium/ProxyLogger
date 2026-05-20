package org.flennn;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.logging.Level;
import java.util.logging.Logger;

@Plugin(id = "proxylogger", name = "ProxyLogger", version = "1.1.0", authors = {"flennn"})
public class ProxyLogger {
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final ConfigManager configManager;

    private DiscordLogger discordLogger;
    private JDA jda;

    @Inject
    public ProxyLogger(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataFolder) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.configManager = new ConfigManager(logger, dataFolder);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.logger.info("Starting ProxyLogger...");
        startDiscord();
        registerListeners();
        registerCommands();
        this.logger.info("ProxyLogger is ready.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        shutdownDiscord();
    }

    public ConfigManager getConfigManager() {
        return this.configManager;
    }

    public DiscordLogger getDiscordLogger() {
        return this.discordLogger;
    }

    public Logger getLogger() {
        return this.logger;
    }

    private void registerListeners() {
        this.proxyServer.getEventManager().register(this, new ActivityListeners(this));
    }

    private void registerCommands() {
        CommandManager commandManager = this.proxyServer.getCommandManager();
        new ReloadConfigCommand(this, this.configManager).register(commandManager, this);
    }

    public synchronized void reload() {
        this.logger.info("Reloading ProxyLogger...");
        this.configManager.reload();
        shutdownDiscord();
        startDiscord();
        this.logger.info("ProxyLogger reloaded.");
    }

    private synchronized void startDiscord() {
        if (!this.configManager.isDiscordEnabled()) {
            this.logger.info("Discord logging is disabled in config.yml.");
            return;
        }

        if (this.configManager.getBotToken().isBlank() || this.configManager.getGuildId().isBlank()) {
            this.logger.warning("Discord logging is enabled, but the bot token or guild ID is missing.");
            return;
        }

        try {
            this.jda = JDABuilder.create(this.configManager.getBotToken(), EnumSet.of(GatewayIntent.GUILD_MESSAGES))
                    .disableCache(CacheFlag.ACTIVITY, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS)
                    .build()
                    .awaitReady();

            this.discordLogger = new DiscordLogger(this.proxyServer, this.jda, this.configManager.getGuildId(), this.logger, this.configManager);
            this.logger.info("Connected to Discord.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            this.logger.warning("Discord startup was interrupted.");
        } catch (Exception e) {
            this.logger.log(Level.SEVERE, "Failed to start Discord logging.", e);
        }
    }

    private synchronized void shutdownDiscord() {
        if (this.discordLogger != null) {
            this.discordLogger.close();
            this.discordLogger = null;
        }

        if (this.jda != null) {
            this.jda.shutdown();
            this.jda = null;
        }
    }
}
