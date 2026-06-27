package com.stephanofer.lobbyHera;

import com.stephanofer.lobbyHera.command.LobbyCommandRegistrar;
import com.stephanofer.lobbyHera.config.PluginConfigService;
import com.stephanofer.lobbyHera.itemjoin.ItemJoinListener;
import com.stephanofer.lobbyHera.itemjoin.ItemJoinService;
import com.stephanofer.lobbyHera.joinactions.JoinActionListener;
import com.stephanofer.lobbyHera.joinactions.JoinActionService;
import com.stephanofer.lobbyHera.lobby.LobbyLocationService;
import com.stephanofer.lobbyHera.message.LocalizationService;
import com.stephanofer.lobbyHera.message.MessageService;
import com.stephanofer.lobbyHera.staff.StaffModeService;
import com.stephanofer.lobbyHera.worldprotection.WorldProtectionListener;
import com.stephanofer.lobbyHera.worldprotection.WorldProtectionService;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

public final class LobbyHera extends JavaPlugin {

    private PluginConfigService configService;
    private ItemJoinService itemJoinService;
    private LobbyLocationService lobbyLocationService;
    private StaffModeService staffModeService;
    private WorldProtectionService worldProtectionService;

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

        this.staffModeService = new StaffModeService(this.configService, this.itemJoinService);
        this.worldProtectionService = new WorldProtectionService(this, this.configService, this.lobbyLocationService, this.staffModeService);
        JoinActionService joinActionService = new JoinActionService(this, this.configService, this.lobbyLocationService, this.itemJoinService, this.staffModeService);

        new LobbyCommandRegistrar(this, this.lobbyLocationService, this.staffModeService, messageService).register();

        getServer().getPluginManager().registerEvents(new JoinActionListener(joinActionService), this);
        getServer().getPluginManager().registerEvents(new ItemJoinListener(this, this.itemJoinService, this.staffModeService), this);
        getServer().getPluginManager().registerEvents(new WorldProtectionListener(this.worldProtectionService), this);
        this.worldProtectionService.start();
        getLogger().info("LobbyHera features loaded.");
    }

    @Override
    public void onDisable() {
        if (this.itemJoinService != null) {
            this.itemJoinService.clearRuntimeState();
        }
        if (this.staffModeService != null) {
            this.staffModeService.clearAll();
        }
        if (this.worldProtectionService != null) {
            this.worldProtectionService.stop();
        }
    }
}
