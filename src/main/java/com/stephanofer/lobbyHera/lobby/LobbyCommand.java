package com.stephanofer.lobbyHera.lobby;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class LobbyCommand implements BasicCommand {

    private final LobbyLocationService lobbyLocationService;

    public LobbyCommand(LobbyLocationService lobbyLocationService) {
        this.lobbyLocationService = lobbyLocationService;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();

        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Este comando solo puede usarlo un jugador.");
            return;
        }

        if (args.length != 0) {
            sender.sendRichMessage("<red>Uso: /lobby");
            return;
        }

        Optional<org.bukkit.Location> lobby = this.lobbyLocationService.getLobbyLocation();
        if (lobby.isEmpty()) {
            sender.sendRichMessage("<red>El lobby todavía no está seteado. Usá /setlobby.");
            return;
        }

        player.teleportAsync(lobby.get()).thenAccept(success -> {
            if (!success) {
                player.sendRichMessage("<red>No se pudo teletransportar al lobby. Intentá de nuevo.");
            }
        });
    }

    @Override
    public @Nullable String permission() {
        return "lobbyhera.lobby.use";
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("lobbyhera.lobby.use");
    }
}
