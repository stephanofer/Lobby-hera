package com.stephanofer.lobbyHera.command;

import com.stephanofer.lobbyHera.lobby.LobbyLocationService;
import com.stephanofer.lobbyHera.message.MessageService;
import com.stephanofer.lobbyHera.staff.StaffModeService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
    private final StaffModeService staffModeService;
    private final MessageService messageService;
    private PaperCommandManager<Source> commandManager;

    public LobbyCommandRegistrar(JavaPlugin plugin, LobbyLocationService lobbyLocationService, StaffModeService staffModeService, MessageService messageService) {
        this.plugin = plugin;
        this.lobbyLocationService = lobbyLocationService;
        this.staffModeService = staffModeService;
        this.messageService = messageService;
    }

    public void register() {
        this.commandManager = PaperCommandManager.builder(PaperSimpleSenderMapper.simpleSenderMapper())
                .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
                .buildOnEnable(this.plugin);

        registerExceptionHandlers();
        registerLobbyCommand("lobby");
        registerLobbyCommand("spawn");
        registerSetLobbyCommand("setlobby");
        registerSetLobbyCommand("setspawn");
        registerStaffModeCommand();
        registerGameModeCommands();
        registerFlyCommand();
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

    private void registerSetLobbyCommand(String name) {
        this.commandManager.command(this.commandManager.commandBuilder(name)
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
        registerStaffModeCommand("staffmode");
        registerStaffModeCommand("staff");
    }

    private void registerStaffModeCommand(String name) {
        this.commandManager.command(this.commandManager.commandBuilder(name)
                .permission(this.staffModeService.settings().permission())
                .handler(context -> {
                    CommandSender sender = context.sender().source();
                    if (!(sender instanceof Player player)) {
                        this.messageService.send(sender, "usage.staffmode-console");
                        return;
                    }

                    boolean enabled = this.staffModeService.toggle(player);
                    this.messageService.send(player, enabled ? "staffmode.enabled.self" : "staffmode.disabled.self");
                })
        );

        this.commandManager.command(this.commandManager.commandBuilder(name)
                .permission(this.staffModeService.settings().permission())
                .required("player", playerParser())
                .handler(context -> {
                    Source source = context.sender();
                    CommandSender sender = source.source();
                    if (!sender.hasPermission(this.staffModeService.settings().othersPermission())) {
                        this.messageService.send(sender, "staffmode.no-permission-others");
                        return;
                    }

                    Player target = context.get("player");

                    boolean enabled = this.staffModeService.toggle(target);
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

    private void registerGameModeCommands() {
        registerGameModeCommand("gmc", GameMode.CREATIVE);
        registerGameModeCommand("gms", GameMode.SURVIVAL);
        registerGameModeCommand("gma", GameMode.ADVENTURE);
        registerGameModeCommand("gmsp", GameMode.SPECTATOR);
        registerGameModeLiteral("creative", GameMode.CREATIVE);
        registerGameModeLiteral("survival", GameMode.SURVIVAL);
        registerGameModeLiteral("adventure", GameMode.ADVENTURE);
        registerGameModeLiteral("spectator", GameMode.SPECTATOR);
    }

    private void registerGameModeCommand(String name, GameMode gameMode) {
        this.commandManager.command(this.commandManager.commandBuilder(name)
                .permission("lobbyhera.command.gamemode")
                .senderType(PlayerSource.class)
                .handler(context -> setGameMode(context.sender().source(), gameMode))
        );
    }

    private void registerGameModeLiteral(String literal, GameMode gameMode) {
        this.commandManager.command(this.commandManager.commandBuilder("gm")
                .permission("lobbyhera.command.gamemode")
                .senderType(PlayerSource.class)
                .literal(literal)
                .handler(context -> setGameMode(context.sender().source(), gameMode))
        );
    }

    private void setGameMode(Player player, GameMode gameMode) {
        player.setGameMode(gameMode);
        this.messageService.send(player, "command.gamemode-changed", Placeholder.unparsed("gamemode", gameMode.name().toLowerCase(java.util.Locale.ROOT)));
    }

    private void registerFlyCommand() {
        this.commandManager.command(this.commandManager.commandBuilder("fly")
                .permission("lobbyhera.command.fly")
                .senderType(PlayerSource.class)
                .handler(context -> {
                    Player player = context.sender().source();
                    boolean enabled = !player.getAllowFlight();
                    player.setAllowFlight(enabled);
                    if (!enabled) {
                        player.setFlying(false);
                    }
                    this.messageService.send(player, enabled ? "command.fly-enabled" : "command.fly-disabled");
                })
        );
    }

    private String languageSource(Source source) {
        return this.messageService.language(source.source());
    }
}
