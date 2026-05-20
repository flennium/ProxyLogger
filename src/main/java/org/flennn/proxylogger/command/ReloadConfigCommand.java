package org.flennn.proxylogger.command;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.flennn.proxylogger.ProxyLogger;
import org.flennn.proxylogger.config.ConfigManager;

import java.util.List;

public class ReloadConfigCommand implements SimpleCommand {
    private final ProxyLogger plugin;
    private final ConfigManager config;

    public ReloadConfigCommand(ProxyLogger plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] arguments = invocation.arguments();

        if (arguments.length == 0 || !arguments[0].equalsIgnoreCase("reload")) {
            source.sendMessage(Component.text("Usage: /" + this.config.getReloadCommandName() + " reload", NamedTextColor.YELLOW));
            return;
        }

        if (!source.hasPermission(this.config.getReloadPermission())) {
            source.sendMessage(Component.text("You do not have permission to reload ProxyLogger.", NamedTextColor.RED));
            return;
        }

        this.plugin.reload();
        source.sendMessage(Component.text("ProxyLogger reloaded.", NamedTextColor.GREEN));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length <= 1) {
            return List.of("reload");
        }
        return List.of();
    }

    public void register(CommandManager commandManager, Object plugin) {
        CommandMeta.Builder meta = commandManager.metaBuilder(this.config.getReloadCommandName())
                .plugin(plugin);

        List<String> aliases = this.config.getReloadCommandAliases();
        if (!aliases.isEmpty()) {
            meta.aliases(aliases.toArray(String[]::new));
        }

        commandManager.register(meta.build(), this);
    }
}
