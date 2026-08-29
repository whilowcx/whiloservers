# WhiloServers

A tiny proxy plugin that registers a `/<server>` command for every backend server, with optional command aliases and per-server permissions. Available for both **Velocity** and **BungeeCord**.

## Features

- One command per server (e.g. `/lobby` connects the player to the `lobby` server)
- Extra aliases per server (e.g. `/hub` as an alias for `/lobby`)
- Optional permission per server, so you can restrict access to staff-only servers
- Config is generated automatically on first start, no manual setup required

## Installation

1. Download the jar for your proxy from [Releases](../../releases):
   - `WhiloServers-Velocity-v1.jar` for Velocity
   - `WhiloServers-Bungee-v1.jar` for BungeeCord/Waterfall
2. Drop it into your proxy's `plugins/` folder
3. Start the proxy once to generate `plugins/whiloservers/config.yml` and `plugins/whiloservers/lang.yml`
4. Edit the config to match your servers (and the messages in `lang.yml` if you want), then restart or reload the proxy

## Configuration

Same format on both platforms:

```yaml
servers:
  lobby:
    aliases:
      - hub
  backrooms:
    permission: 'whiloservers.backrooms'
```

- the key (e.g. `lobby`) is the exact name of the server as registered in `velocity.toml` / BungeeCord's `config.yml`
- `permission`: optional, omit it or leave empty (`''`) to allow everyone, or set a permission node to restrict the command (and its aliases) to players who have it
- `aliases`: optional, extra command names that behave exactly like `/<server>`

### Messages (`lang.yml`)

```yaml
players-only: '&cOnly players can use this command.'
no-permission: '&cYou don''t have permission to use this command.'
server-not-configured: '&cServer %server% is not configured.'
connect-failed: '&cUnable to connect to %server%.'
already-connected: '&cYou are already on this server.'
connected: '&aConnected to %server%.'
```

`&` color codes and the `%server%` placeholder are supported.

## Building from source

Requires JDK 17+ and Maven. This is a multi-module project (`velocity/` and `bungee/`), building from the root compiles both:

```
mvn clean package
```

- `velocity/target/WhiloServers-Velocity-v1.jar`
- `bungee/target/WhiloServers-Bungee-v1.jar`

To build only one platform, run the same command inside `velocity/` or `bungee/`.

## License

MIT
