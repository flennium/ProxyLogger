package org.flennn;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class ReloadConfigCommand implements SimpleCommand {
    private final ProxyLogger plugin;
    private final ConfigManager configManager;
    private final ProxyServer proxyServer;

    public ReloadConfigCommand(ProxyLogger plugin, ConfigManager configManager, ProxyServer proxyServer) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.proxyServer = proxyServer;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!source.hasPermission("logger.admin")) {
            source.sendMessage(Component.text(" ❌ You do not have permission to use this command.", NamedTextColor.RED));
            return;
        }

        source.sendMessage(Component.text(" 🔄 Reloading Logger...", NamedTextColor.YELLOW));

        plugin.reload();

        source.sendMessage(Component.text(" ✅ Logger reloaded successfully!", NamedTextColor.GREEN));
    }

    public void register(CommandManager commandManager, Object plugin) {
        commandManager.register(
                commandManager.metaBuilder("reloadconfig")
                        .plugin(plugin)
                        .build(),
                this
        );
    }
}