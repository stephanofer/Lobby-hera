package com.stephanofer.lobbyHera.command;

import com.stephanofer.lobbyHera.itemjoin.ItemJoinService;
import com.stephanofer.lobbyHera.lobby.LobbyLocationService;
import com.stephanofer.lobbyHera.message.MessageService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.exception.CommandExecutionException;
import org.incendo.cloud.exception.InvalidCommandSenderException;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.minecraft.extras.MinecraftExceptionHandler;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PaperSimpleSenderMapper;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

import java.util.Optional;
import java.util.logging.Level;

import static org.incendo.cloud.bukkit.parser.PlayerParser.playerParser;

public final class LobbyCommandRegistrar {

    private final JavaPlugin plugin;
    private final LobbyLocationService lobbyLocationService;
    private final ItemJoinService itemJoinService;
    private final MessageService messageService;
    private PaperCommandManager<Source> commandManager;

    public LobbyCommandRegistrar(JavaPlugin plugin, LobbyLocationService lobbyLocationService, ItemJoinService itemJoinService, MessageService messageService) {
        this.plugin = plugin;
        this.lobbyLocationService = lobbyLocationService;
        this.itemJoinService = itemJoinService;
        this.messageService = messageService;
    }

    public void register() {
        this.commandManager = PaperCommandManager.builder(PaperSimpleSenderMapper.simpleSenderMapper())
                .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
                .buildOnEnable(this.plugin);

        registerExceptionHandlers();
        registerLobbyCommand("lobby");
        registerLobbyCommand("spawn");
        registerSetLobbyCommand();
        registerStaffModeCommand();
    }

    private void registerExceptionHandlers() {
        MinecraftExceptionHandler.<Source>create(source -> source.source())
                .handler(NoPermissionException.class, (formatter, context) -> this.messageService.component(languageSource(context.context().sender()), "command.no-permission"))
                .handler(InvalidCommandSenderException.class, (formatter, context) -> this.messageService.component(languageSource(context.context().sender()), "command.invalid-sender"))
                .handler(InvalidSyntaxException.class, (formatter, context) -> this.messageService.component(
                        languageSource(context.context().sender()),
                        "command.invalid-syntax",
                        Placeholder.unparsed("syntax", context.exception().correctSyntax())
                ))
                .handler(CommandExecutionException.class, (formatter, context) -> {
                    this.plugin.getLogger().log(Level.WARNING, "Command execution failed.", context.exception().getCause());
                    return this.messageService.component(languageSource(context.context().sender()), "command.execution-error");
                })
                .registerTo(this.commandManager);
    }

    private void registerLobbyCommand(String name) {
        this.commandManager.command(this.commandManager.commandBuilder(name)
                .permission("lobbyhera.lobby.use")
                .senderType(PlayerSource.class)
                .handler(context -> {
                    Player player = context.sender().source();
                    Optional<org.bukkit.Location> lobby = this.lobbyLocationService.getLobbyLocation();
                    if (lobby.isEmpty()) {
                        this.messageService.send(player, "lobby.not-set");
                        return;
                    }

                    player.teleportAsync(lobby.get()).thenAccept(success -> {
                        if (!success && player.isOnline()) {
                            Bukkit.getScheduler().runTask(this.plugin, () -> {
                                if (player.isOnline()) {
                                    this.messageService.send(player, "lobby.teleport-failed");
                                }
                            });
                        }
                    });
                })
        );
    }

    private void registerSetLobbyCommand() {
        this.commandManager.command(this.commandManager.commandBuilder("setlobby")
                .permission("lobbyhera.lobby.set")
                .senderType(PlayerSource.class)
                .handler(context -> {
                    Player player = context.sender().source();
                    boolean saved = this.lobbyLocationService.setLobbyLocation(player.getLocation());
                    this.messageService.send(player, saved ? "lobby.set-success" : "lobby.set-failed");
                })
        );
    }

    private void registerStaffModeCommand() {
        this.commandManager.command(this.commandManager.commandBuilder("staffmode")
                .permission("lobbyhera.staffmode")
                .handler(context -> {
                    CommandSender sender = context.sender().source();
                    if (!(sender instanceof Player player)) {
                        this.messageService.send(sender, "usage.staffmode-console");
                        return;
                    }

                    boolean enabled = this.itemJoinService.toggleStaffBypass(player.getUniqueId());
                    this.messageService.send(player, enabled ? "staffmode.enabled.self" : "staffmode.disabled.self");
                })
        );

        this.commandManager.command(this.commandManager.commandBuilder("staffmode")
                .permission("lobbyhera.staffmode")
                .required("player", playerParser())
                .handler(context -> {
                    Source source = context.sender();
                    CommandSender sender = source.source();
                    if (!sender.hasPermission("lobbyhera.staffmode.others")) {
                        this.messageService.send(sender, "staffmode.no-permission-others");
                        return;
                    }

                    Player target = context.get("player");

                    boolean enabled = this.itemJoinService.toggleStaffBypass(target.getUniqueId());
                    this.messageService.send(
                            sender,
                            enabled ? "staffmode.enabled.other" : "staffmode.disabled.other",
                            Placeholder.unparsed("player", target.getName())
                    );

                    if (!sender.equals(target)) {
                        this.messageService.send(target, enabled ? "staffmode.enabled.target" : "staffmode.disabled.target");
                    }
                })
        );
    }

    private String languageSource(Source source) {
        return this.messageService.language(source.source());
    }
}
