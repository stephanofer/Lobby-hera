package com.stephanofer.lobbyHera.worldprotection;

import com.stephanofer.lobbyHera.config.PluginConfigService;
import com.stephanofer.lobbyHera.lobby.LobbyLocationService;
import com.stephanofer.lobbyHera.staff.StaffModeService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Optional;

public final class WorldProtectionService {

    private final JavaPlugin plugin;
    private final PluginConfigService configService;
    private final LobbyLocationService lobbyLocationService;
    private final StaffModeService staffModeService;
    private BukkitTask alwaysDayTask;

    public WorldProtectionService(JavaPlugin plugin, PluginConfigService configService, LobbyLocationService lobbyLocationService, StaffModeService staffModeService) {
        this.plugin = plugin;
        this.configService = configService;
        this.lobbyLocationService = lobbyLocationService;
        this.staffModeService = staffModeService;
    }

    public void start() {
        stop();
        if (!settings().enabled() || !settings().alwaysDayEnabled()) {
            return;
        }
        applyAlwaysDay();
        this.alwaysDayTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::applyAlwaysDay, 600L, 600L);
    }

    public void stop() {
        if (this.alwaysDayTask != null) {
            this.alwaysDayTask.cancel();
            this.alwaysDayTask = null;
        }
    }

    public boolean enabled() {
        return settings().enabled();
    }

    public boolean bypasses(Player player) {
        PluginConfigService.WorldProtectionSnapshot settings = settings();
        return this.staffModeService.bypassesProtection(player)
                || (!settings.bypassPermission().isBlank() && player.hasPermission(settings.bypassPermission()));
    }

    public void rescueFromVoid(Player player) {
        Optional<Location> lobby = this.lobbyLocationService.getLobbyLocation();
        if (lobby.isEmpty()) {
            this.plugin.getLogger().warning("Void protection is enabled but lobby spawn is not configured.");
            return;
        }
        player.setFallDistance(0.0F);
        player.teleportAsync(lobby.get()).thenAccept(success -> {
            if (!success && player.isOnline()) {
                this.plugin.getLogger().warning("Could not rescue " + player.getName() + " from void.");
            }
        });
    }

    public PluginConfigService.WorldProtectionSnapshot settings() {
        return this.configService.snapshot().worldProtection();
    }

    private void applyAlwaysDay() {
        PluginConfigService.WorldProtectionSnapshot settings = settings();
        Optional<Location> lobby = this.lobbyLocationService.getLobbyLocation();
        if (lobby.isEmpty()) {
            return;
        }

        World world = lobby.get().getWorld();
        if (world == null) {
            return;
        }

        if (world.hasSkyLight() && !world.isFixedTime()) {
            world.setTime(settings.alwaysDayTime());
        } else {
            this.plugin.getLogger().warning("Skipping always-day for world '" + world.getName() + "' because it does not support a mutable day/night clock.");
        }

        if (settings.disableWeatherChange()) {
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(0);
            world.setThunderDuration(0);
        }
    }
}
