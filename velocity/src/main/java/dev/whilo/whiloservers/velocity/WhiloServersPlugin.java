package dev.whilo.whiloservers.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Plugin(id = "whiloservers", name = "WhiloServers", version = "v1", authors = {"whilo"})
public class WhiloServersPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private Lang lang = Lang.defaults();

    @Inject
    public WhiloServersPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        lang = loadLang();
        List<ServerEntry> entries = loadConfig();
        for (ServerEntry entry : entries) {
            registerCommand(entry.server, entry.server, entry.permission);
            for (String alias : entry.aliases) {
                registerCommand(alias, entry.server, entry.permission);
            }
        }
        logger.info("========================================");
        logger.info(" WhiloServers v1 enabled");
        logger.info(" Loaded {} server entr{}.", entries.size(), entries.size() == 1 ? "y" : "ies");
        logger.info("========================================");
    }

    private void registerCommand(String commandName, String targetServer, String permission) {
        CommandMeta meta = proxy.getCommandManager().metaBuilder(commandName).plugin(this).build();
        proxy.getCommandManager().register(meta, new SlashCommand(targetServer, permission));
    }

    private static Component legacy(String message) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(message);
    }

    private final class SlashCommand implements SimpleCommand {
        private final String targetServer;
        private final String permission;

        private SlashCommand(String targetServer, String permission) {
            this.targetServer = targetServer;
            this.permission = permission;
        }

        @Override
        public void execute(Invocation invocation) {
            if (!(invocation.source() instanceof Player player)) {
                invocation.source().sendMessage(legacy(lang.playersOnly));
                return;
            }
            if (!hasPermission(invocation)) {
                player.sendMessage(legacy(lang.noPermission));
                return;
            }
            boolean alreadyThere = player.getCurrentServer()
                .map(conn -> conn.getServerInfo().getName().equalsIgnoreCase(targetServer))
                .orElse(false);
            if (alreadyThere) {
                player.sendMessage(legacy(lang.alreadyConnected));
                return;
            }
            proxy.getServer(targetServer).ifPresentOrElse(
                server -> player.createConnectionRequest(server).connect().whenComplete((result, throwable) -> {
                    if (throwable != null || result == null || !result.isSuccessful()) {
                        player.sendMessage(legacy(lang.connectFailed.replace("%server%", targetServer)));
                    } else {
                        player.sendMessage(legacy(lang.connected.replace("%server%", targetServer)));
                    }
                }),
                () -> player.sendMessage(legacy(lang.serverNotConfigured.replace("%server%", targetServer)))
            );
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return permission == null || permission.isEmpty() || invocation.source().hasPermission(permission);
        }
    }

    private Map<?, ?> loadYamlResource(String fileName) {
        try {
            Files.createDirectories(dataDirectory);
            Path path = dataDirectory.resolve(fileName);
            if (!Files.exists(path)) {
                try (InputStream in = getClass().getResourceAsStream("/" + fileName)) {
                    if (in != null) {
                        Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            try (InputStream in = Files.newInputStream(path)) {
                Map<?, ?> root = new Yaml().load(in);
                return root == null ? Map.of() : root;
            }
        } catch (IOException e) {
            logger.error("Failed to load WhiloServers {}", fileName, e);
            return Map.of();
        }
    }

    private List<ServerEntry> loadConfig() {
        List<ServerEntry> result = new ArrayList<>();
        Map<?, ?> root = loadYamlResource("config.yml");
        Object serversObj = root.get("servers");
        if (serversObj instanceof Map<?, ?> serversMap) {
            for (Map.Entry<?, ?> entry : serversMap.entrySet()) {
                String serverName = String.valueOf(entry.getKey());
                List<String> aliases = new ArrayList<>();
                String permission = "";
                if (entry.getValue() instanceof Map<?, ?> details) {
                    Object aliasesObj = details.get("aliases");
                    if (aliasesObj instanceof List<?> aliasList) {
                        for (Object a : aliasList) {
                            aliases.add(String.valueOf(a));
                        }
                    }
                    Object permissionObj = details.get("permission");
                    if (permissionObj != null) {
                        permission = String.valueOf(permissionObj);
                    }
                }
                result.add(new ServerEntry(serverName, permission, aliases));
            }
        }
        return result;
    }

    private Lang loadLang() {
        Map<?, ?> root = loadYamlResource("lang.yml");
        Lang defaults = Lang.defaults();
        return new Lang(
            stringOrDefault(root, "players-only", defaults.playersOnly),
            stringOrDefault(root, "no-permission", defaults.noPermission),
            stringOrDefault(root, "server-not-configured", defaults.serverNotConfigured),
            stringOrDefault(root, "connect-failed", defaults.connectFailed),
            stringOrDefault(root, "already-connected", defaults.alreadyConnected),
            stringOrDefault(root, "connected", defaults.connected)
        );
    }

    private static String stringOrDefault(Map<?, ?> root, String key, String defaultValue) {
        Object value = root.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static final class ServerEntry {
        final String server;
        final String permission;
        final List<String> aliases;

        ServerEntry(String server, String permission, List<String> aliases) {
            this.server = server;
            this.permission = permission;
            this.aliases = aliases;
        }
    }

    private static final class Lang {
        final String playersOnly;
        final String noPermission;
        final String serverNotConfigured;
        final String connectFailed;
        final String alreadyConnected;
        final String connected;

        Lang(String playersOnly, String noPermission, String serverNotConfigured, String connectFailed,
             String alreadyConnected, String connected) {
            this.playersOnly = playersOnly;
            this.noPermission = noPermission;
            this.serverNotConfigured = serverNotConfigured;
            this.connectFailed = connectFailed;
            this.alreadyConnected = alreadyConnected;
            this.connected = connected;
        }

        static Lang defaults() {
            return new Lang(
                "&cOnly players can use this command.",
                "&cYou don't have permission to use this command.",
                "&cServer %server% is not configured.",
                "&cUnable to connect to %server%.",
                "&cYou are already on this server.",
                "&aConnected to %server%."
            );
        }
    }
}
