package com.stephanofer.lobbyHera;

import com.stephanofer.lobbyHera.itemjoin.ItemJoinListener;
import com.stephanofer.lobbyHera.itemjoin.ItemJoinService;
import com.stephanofer.lobbyHera.itemjoin.StaffModeCommand;
import com.stephanofer.lobbyHera.lobby.LobbyCommand;
import com.stephanofer.lobbyHera.lobby.LobbyJoinListener;
import com.stephanofer.lobbyHera.lobby.LobbyLocationService;
import com.stephanofer.lobbyHera.lobby.SetLobbyCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class LobbyHera extends JavaPlugin {

    private ItemJoinService itemJoinService;
    private LobbyLocationService lobbyLocationService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.lobbyLocationService = new LobbyLocationService(this);
        this.lobbyLocationService.load();
        registerCommand("setlobby", new SetLobbyCommand(this.lobbyLocationService));
        registerCommand("lobby", new LobbyCommand(this.lobbyLocationService));
        registerCommand("spawn", new LobbyCommand(this.lobbyLocationService));

        this.itemJoinService = new ItemJoinService(this);
        this.itemJoinService.load();
        registerCommand("staffmode", new StaffModeCommand(this.itemJoinService));

        getServer().getPluginManager().registerEvents(new LobbyJoinListener(this.lobbyLocationService), this);
        getServer().getPluginManager().registerEvents(new ItemJoinListener(this, this.itemJoinService), this);
        getLogger().info("Lobby + ItemJoin core loaded.");
    }

    @Override
    public void onDisable() {
        if (this.itemJoinService != null) {
            this.itemJoinService.clearRuntimeState();
        }
    }
}
