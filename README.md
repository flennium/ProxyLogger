# ProxyLogger

ProxyLogger is a configurable Discord audit logger for Velocity networks. It tracks player chat, commands, joins, leaves, and optional server switches, then organizes the logs into clean per-server Discord channels.

It is built for server owners who want readable staff logs, simple privacy controls, and a release jar that is ready to drop into production.

## Features

- Discord logs for chat, commands, joins, leaves, and server switches
- Per-server Discord categories and configurable channel names
- Optional automatic channel/category creation
- Privacy controls for IP address, UUID, client brand, and server name
- Ignored servers and ignored commands
- Configurable embed colors, footer text, timestamps, and message length
- `/proxylogger reload` command with configurable aliases and permission
- Shaded release jar, ready to drop into Velocity

## Requirements

- Velocity 4.1 or newer
- Java 25 or newer
- A Discord bot token
- A Discord server ID

## Installation

1. Download the latest `ProxyLogger-<version>.jar` from the releases page.
2. Put the jar in your Velocity `plugins` folder.
3. Start the proxy once to generate `plugins/proxylogger/config.yml`.
4. Stop the proxy.
5. Add your Discord bot token and guild ID to the config.
6. Start the proxy again.

## Discord Bot Setup

Create a bot in the Discord Developer Portal, then invite it to your Discord server.

The bot needs these permissions:

- View Channels
- Manage Channels, if `channels.auto-create` is enabled
- Send Messages
- Embed Links

The bot does not need administrator permission.

## Configuration

Default config:

```yaml
discord:
  enabled: true
  bot-token: ""
  guild-id: ""

channels:
  auto-create: true
  verify-interval-seconds: 120
  category-format: "{server}"
  names:
    chat: "chat-logs"
    commands: "commands"
    join-leave: "join-leave"

events:
  chat:
    enabled: true
  commands:
    enabled: true
    log-console: false
  join:
    enabled: true
  leave:
    enabled: true
  server-switch:
    enabled: false

privacy:
  include-ip: false
  include-uuid: true
  include-client: true
  include-server: true
```

The full generated config includes channel topics, embed colors, filters, and command settings.

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/proxylogger reload` | `proxylogger.admin` | Reloads the config and reconnects the Discord bot |

Aliases are configurable. By default, `plogger` and `plreloadconfig` are also registered.

## Building

```bash
mvn clean package
```

The release jar is written to:

```text
target/ProxyLogger-<version>.jar
```

## Notes

Keep the bot token private. Anyone with the token can control the bot.

If automatic channel creation is disabled, create categories and channels manually using the names from `config.yml`.

## License

ProxyLogger is released under the MIT License. See [LICENSE](LICENSE).
