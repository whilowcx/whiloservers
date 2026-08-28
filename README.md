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
3. Start the proxy once to generate `plugins/whiloservers/config.yml`
4. Edit the config to match your servers, then restart or reload the proxy

## Configuration

Same format on both platforms:

```yaml
servers:
-   server: lobby
    permission: ''
    # Aliases to /lobby command - you can use listed command instead of /lobby
    commands:
    - hub
-   server: backrooms
    permission: 'whiloservers.backrooms'
    commands: []
```

- `server`: the exact name of the server as registered in `velocity.toml` / BungeeCord's `config.yml`
- `permission`: leave empty (`''`) to allow everyone, or set a permission node to restrict the command (and its aliases) to players who have it
- `commands`: extra command names that behave exactly like `/<server>`

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
