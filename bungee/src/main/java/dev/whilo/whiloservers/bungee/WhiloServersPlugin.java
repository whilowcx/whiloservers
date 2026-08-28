package dev.whilo.whiloservers.bungee;

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

    @Override
    public void onEnable() {
        List<ServerEntry> entries = loadConfig();
        for (ServerEntry entry : entries) {
            getProxy().getPluginManager().registerCommand(this, new SlashCommand(entry.server, entry.server, entry.permission));
            for (String alias : entry.commands) {
                getProxy().getPluginManager().registerCommand(this, new SlashCommand(alias, entry.server, entry.permission));
            }
        }
        getLogger().info("========================================");
        getLogger().info(" WhiloServers v1 enabled");
        getLogger().info(" Loaded " + entries.size() + " server entries.");
        getLogger().info("========================================");
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
                sender.sendMessage(new TextComponent("Only players can use this command."));
                return;
            }
            if (permission != null && !permission.isEmpty() && !player.hasPermission(permission)) {
                player.sendMessage(new TextComponent("You don't have permission to use this command."));
                return;
            }
            ServerInfo server = getProxy().getServerInfo(targetServer);
            if (server == null) {
                player.sendMessage(new TextComponent("Server " + targetServer + " is not configured."));
                return;
            }
            player.connect(server);
        }
    }

    private List<ServerEntry> loadConfig() {
        List<ServerEntry> result = new ArrayList<>();
        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }
            File configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                try (InputStream in = getResourceAsStream("config.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile.toPath());
                    }
                }
            }
            Yaml yaml = new Yaml();
            try (InputStream in = Files.newInputStream(configFile.toPath())) {
                Map<?, ?> root = yaml.load(in);
                if (root == null) {
                    return result;
                }
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
            }
        } catch (IOException e) {
            getLogger().severe("Failed to load WhiloServers config.yml: " + e.getMessage());
        }
        return result;
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
}
