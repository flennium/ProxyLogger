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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Plugin(id = "proxylogger", name = "ProxyLogger", version = "1.0.1", authors = {"flennn"})
public class ProxyLogger {

    private static ProxyLogger instance;
    private final ConfigManager configManager;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ProxyServer proxyServer;
    private final Logger logger;
    private JDA jda;
    private DiscordLogger discordLogger;


    @Inject
    public ProxyLogger(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataFolder) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.configManager = new ConfigManager(proxyServer, logger, dataFolder);
    }

    public static ProxyLogger getInstance() {
        return instance;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        Utils.Log.info(" 🔧 Starting Logger ...");
        instance = this;

        if (LaunchDiscord()) {
            registerListeners();
        }

        registerCommands();

        Utils.Log.info(" ✅ Logger successfully loaded!");
    }

    public void registerListeners() {
        discordLogger = new DiscordLogger(proxyServer, jda, configManager.getLogsGuildID(), logger, configManager);
        proxyServer.getEventManager().register(this, new ActivityListeners(discordLogger, configManager));
    }

    public void registerCommands() {
        CommandManager commandManager = proxyServer.getCommandManager();

        new ReloadConfigCommand(this, configManager, proxyServer).register(commandManager, this);
    }

    public boolean LaunchDiscord() {
        try {
            JDABuilder builder = JDABuilder.create(configManager.getBotToken(), GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS));
            builder.disableCache(CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS);

            jda = builder.build().awaitReady();

            Utils.Log.info(" ✅ Logger Discord-bot successfully loaded!");
            return true;
        } catch (InterruptedException e) {
            Utils.Log.severe(" ❌ Failed to start Discord bot");
            e.printStackTrace();
        }
        return false;
    }

    public void shutdown() {
        if (!scheduler.isShutdown()) {
            scheduler.shutdownNow();

            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    Utils.Log.warning(" DiscordLogger scheduler did not terminate in time.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Utils.Log.warning(" DiscordLogger scheduler shutdown interrupted.");
            }

            if (jda != null) {
                jda.shutdown();
            }
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        shutdown();
    }

    public void reload() {
        shutdown();
        Utils.Log.info(" 🔄 Reloading Logger...");
        configManager.reloadConfig();

        proxyServer.getEventManager().unregisterListeners(this);

        LaunchDiscord();

        registerListeners();
        registerCommands();

        Utils.Log.info(" 🔁 Logger config and listeners reloaded.");
    }
}
