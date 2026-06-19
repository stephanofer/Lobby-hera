package com.stephanofer.lobbyHera;

import com.stephanofer.lobbyHera.command.LobbyCommandRegistrar;
import com.stephanofer.lobbyHera.config.PluginConfigService;
import com.stephanofer.lobbyHera.itemjoin.ItemJoinListener;
import com.stephanofer.lobbyHera.itemjoin.ItemJoinService;
import com.stephanofer.lobbyHera.lobby.LobbyJoinListener;
import com.stephanofer.lobbyHera.lobby.LobbyLocationService;
import com.stephanofer.lobbyHera.message.LocalizationService;
import com.stephanofer.lobbyHera.message.MessageService;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

public final class LobbyHera extends JavaPlugin {

    private PluginConfigService configService;
    private ItemJoinService itemJoinService;
    private LobbyLocationService lobbyLocationService;

    @Override
    public void onEnable() {
        this.configService = new PluginConfigService(this);
        try {
            this.configService.load();
        } catch (IOException exception) {
            getLogger().severe("Could not load YAML configuration: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        LocalizationService localizationService;
        try {
            localizationService = new LocalizationService(this.configService);
        } catch (IllegalStateException exception) {
            getLogger().severe(exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        MessageService messageService = new MessageService(this.configService, localizationService);

        this.lobbyLocationService = new LobbyLocationService(this, this.configService);
        this.lobbyLocationService.load();

        this.itemJoinService = new ItemJoinService(this, this.configService, localizationService, messageService);
        this.itemJoinService.load();

        new LobbyCommandRegistrar(this, this.lobbyLocationService, this.itemJoinService, messageService).register();

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
