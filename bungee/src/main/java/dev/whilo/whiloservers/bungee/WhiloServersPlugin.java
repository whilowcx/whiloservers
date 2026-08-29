package dev.whilo.whiloservers.bungee;

import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WhiloServersPlugin extends Plugin {

    private Lang lang = Lang.defaults();

    @Override
    public void onEnable() {
        lang = loadLang();
        List<ServerEntry> entries = loadConfig();
        for (ServerEntry entry : entries) {
            getProxy().getPluginManager().registerCommand(this, new SlashCommand(entry.server, entry.server, entry.permission));
            for (String alias : entry.aliases) {
                getProxy().getPluginManager().registerCommand(this, new SlashCommand(alias, entry.server, entry.permission));
            }
        }
        getLogger().info("========================================");
        getLogger().info(" WhiloServers v1 enabled");
        getLogger().info(" Loaded " + entries.size() + " server entries.");
        getLogger().info("========================================");
    }

    private static TextComponent legacy(String message) {
        return new TextComponent(ChatColor.translateAlternateColorCodes('&', message));
    }

    private final class SlashCommand extends Command {
        private final String targetServer;
        private final String permission;

        private SlashCommand(String name, String targetServer, String permission) {
            super(name);
            this.targetServer = targetServer;
            this.permission = permission;
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(sender instanceof ProxiedPlayer player)) {
                sender.sendMessage(legacy(lang.playersOnly));
                return;
            }
            if (permission != null && !permission.isEmpty() && !player.hasPermission(permission)) {
                player.sendMessage(legacy(lang.noPermission));
                return;
            }
            if (player.getServer() != null && player.getServer().getInfo().getName().equalsIgnoreCase(targetServer)) {
                player.sendMessage(legacy(lang.alreadyConnected));
                return;
            }
            ServerInfo server = getProxy().getServerInfo(targetServer);
            if (server == null) {
                player.sendMessage(legacy(lang.serverNotConfigured.replace("%server%", targetServer)));
                return;
            }
            player.connect(server, (Callback<Boolean>) (result, error) -> {
                if (error != null || result == null || !result) {
                    player.sendMessage(legacy(lang.connectFailed.replace("%server%", targetServer)));
                } else {
                    player.sendMessage(legacy(lang.connected.replace("%server%", targetServer)));
                }
            });
        }
    }

    private Map<?, ?> loadYamlResource(String fileName) {
        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }
            File file = new File(getDataFolder(), fileName);
            if (!file.exists()) {
                try (InputStream in = getResourceAsStream(fileName)) {
                    if (in != null) {
                        Files.copy(in, file.toPath());
                    }
                }
            }
            try (InputStream in = Files.newInputStream(file.toPath())) {
                Map<?, ?> root = new Yaml().load(in);
                return root == null ? Map.of() : root;
            }
        } catch (IOException e) {
            getLogger().severe("Failed to load WhiloServers " + fileName + ": " + e.getMessage());
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
