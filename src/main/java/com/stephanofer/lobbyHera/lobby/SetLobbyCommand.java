package com.stephanofer.lobbyHera.lobby;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public final class SetLobbyCommand implements BasicCommand {

    private final LobbyLocationService lobbyLocationService;

    public SetLobbyCommand(LobbyLocationService lobbyLocationService) {
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
            sender.sendRichMessage("<red>Uso: /setlobby");
            return;
        }

        boolean saved = this.lobbyLocationService.setLobbyLocation(player.getLocation());
        if (!saved) {
            sender.sendRichMessage("<red>No se pudo guardar el lobby. Revisá el mundo/ubicación.");
            return;
        }

        sender.sendRichMessage("<green>Lobby seteado correctamente en tu posición actual.");
    }

    @Override
    public @Nullable String permission() {
        return "lobbyhera.lobby.set";
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("lobbyhera.lobby.set");
    }
}
