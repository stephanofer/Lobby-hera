package com.stephanofer.lobbyHera.staff;

import com.stephanofer.lobbyHera.config.PluginConfigService;
import com.stephanofer.lobbyHera.itemjoin.ItemJoinService;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class StaffModeService {

    private final PluginConfigService configService;
    private final ItemJoinService itemJoinService;
    private final Set<UUID> activeUsers = new HashSet<>();

    public StaffModeService(PluginConfigService configService, ItemJoinService itemJoinService) {
        this.configService = configService;
        this.itemJoinService = itemJoinService;
    }

    public boolean toggle(Player player) {
        if (isActive(player.getUniqueId())) {
            disable(player);
            return false;
        }
        enable(player);
        return true;
    }

    public void enable(Player player) {
        PluginConfigService.StaffModeSnapshot settings = settings();
        if (!settings.enabled()) {
            return;
        }

        this.activeUsers.add(player.getUniqueId());
        if (settings.clearLobbyItemsOnEnable()) {
            this.itemJoinService.removeManagedItems(player);
        }
        applyGameMode(player, settings.gamemodeOnEnable());
    }

    public void disable(Player player) {
        PluginConfigService.StaffModeSnapshot settings = settings();
        this.activeUsers.remove(player.getUniqueId());
        applyGameMode(player, settings.gamemodeOnDisable());
        if (settings.restoreLobbyItemsOnDisable()) {
            this.itemJoinService.giveLobbyItems(player);
        }
    }

    public void clear(UUID uuid) {
        this.activeUsers.remove(uuid);
    }

    public void clearAll() {
        this.activeUsers.clear();
    }

    public boolean isActive(UUID uuid) {
        return this.activeUsers.contains(uuid);
    }

    public boolean bypassesProtection(Player player) {
        PluginConfigService.StaffModeSnapshot settings = settings();
        return settings.protectionBypass() && isActive(player.getUniqueId());
    }

    public PluginConfigService.StaffModeSnapshot settings() {
        return this.configService.snapshot().staffMode();
    }

    private static void applyGameMode(Player player, GameMode gameMode) {
        if (gameMode != null && player.getGameMode() != gameMode) {
            player.setGameMode(gameMode);
        }
    }
}
