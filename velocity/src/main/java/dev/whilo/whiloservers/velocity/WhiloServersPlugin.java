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
import net.kyori.adventure.text.format.NamedTextColor;
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

    @Inject
    public WhiloServersPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
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
                invocation.source().sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
                return;
            }
            if (!hasPermission(invocation)) {
                player.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
                return;
            }
            proxy.getServer(targetServer).ifPresentOrElse(
                server -> player.createConnectionRequest(server).connect().whenComplete((result, throwable) -> {
                    if (throwable != null || result == null || !result.isSuccessful()) {
                        player.sendMessage(Component.text("Unable to connect to " + targetServer + ".", NamedTextColor.RED));
                    }
                }),
                () -> player.sendMessage(Component.text("Server " + targetServer + " is not configured.", NamedTextColor.RED))
            );
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return permission == null || permission.isEmpty() || invocation.source().hasPermission(permission);
        }
    }

    private List<ServerEntry> loadConfig() {
        List<ServerEntry> result = new ArrayList<>();
        try {
            Files.createDirectories(dataDirectory);
            Path configPath = dataDirectory.resolve("config.yml");
            if (!Files.exists(configPath)) {
                try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                    if (in != null) {
                        Files.copy(in, configPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            Yaml yaml = new Yaml();
            try (InputStream in = Files.newInputStream(configPath)) {
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
            logger.error("Failed to load WhiloServers config.yml", e);
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
