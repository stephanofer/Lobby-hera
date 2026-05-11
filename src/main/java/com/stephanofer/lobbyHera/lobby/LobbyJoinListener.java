package com.stephanofer.lobbyHera.lobby;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Optional;

public final class LobbyJoinListener implements Listener {

    private final LobbyLocationService lobbyLocationService;

    public LobbyJoinListener(LobbyLocationService lobbyLocationService) {
        this.lobbyLocationService = lobbyLocationService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Optional<Location> lobby = this.lobbyLocationService.getLobbyLocation();
        lobby.ifPresent(location -> event.getPlayer().teleportAsync(location));
    }
}
