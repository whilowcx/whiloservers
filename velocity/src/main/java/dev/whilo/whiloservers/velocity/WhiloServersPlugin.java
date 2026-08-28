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
            for (String alias : entry.commands) {
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
            proxy.getServer(targetServer).ifPresentOrElse(
                server -> player.createConnectionRequest(server).connect().whenComplete((result, throwable) -> {
                    if (throwable != null || result == null || !result.isSuccessful()) {
                        player.sendMessage(legacy(lang.connectFailed.replace("%server%", targetServer)));
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
        if (serversObj instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> map) {
                    Object serverName = map.get("server");
                    if (serverName == null) {
                        continue;
                    }
                    String permission = map.get("permission") == null ? "" : String.valueOf(map.get("permission"));
                    List<String> commands = new ArrayList<>();
                    Object cmdsObj = map.get("commands");
                    if (cmdsObj instanceof List<?> cmdList) {
                        for (Object c : cmdList) {
                            commands.add(String.valueOf(c));
                        }
                    }
                    result.add(new ServerEntry(String.valueOf(serverName), permission, commands));
                }
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
            stringOrDefault(root, "connect-failed", defaults.connectFailed)
        );
    }

    private static String stringOrDefault(Map<?, ?> root, String key, String defaultValue) {
        Object value = root.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static final class ServerEntry {
        final String server;
        final String permission;
        final List<String> commands;

        ServerEntry(String server, String permission, List<String> commands) {
            this.server = server;
            this.permission = permission;
            this.commands = commands;
        }
    }

    private static final class Lang {
        final String playersOnly;
        final String noPermission;
        final String serverNotConfigured;
        final String connectFailed;

        Lang(String playersOnly, String noPermission, String serverNotConfigured, String connectFailed) {
            this.playersOnly = playersOnly;
            this.noPermission = noPermission;
            this.serverNotConfigured = serverNotConfigured;
            this.connectFailed = connectFailed;
        }

        static Lang defaults() {
            return new Lang(
                "&cOnly players can use this command.",
                "&cYou don't have permission to use this command.",
                "&cServer %server% is not configured.",
                "&cUnable to connect to %server%."
            );
        }
    }
}
