package com.stephanofer.lobbyHera.lobby;

import com.stephanofer.lobbyHera.config.PluginConfigService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Optional;

public final class LobbyLocationService {

    private static final String PATH = "lobby";

    private final JavaPlugin plugin;
    private final PluginConfigService configService;
    private volatile Location lobbyLocation;

    public LobbyLocationService(JavaPlugin plugin, PluginConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
    }

    public void load() {
        this.lobbyLocation = readFromConfig();
    }

    public Optional<Location> getLobbyLocation() {
        return Optional.ofNullable(this.lobbyLocation).map(Location::clone);
    }

    public boolean setLobbyLocation(Location location) {
        Location safeLocation = sanitize(location);
        if (safeLocation == null) {
            return false;
        }

        this.lobbyLocation = safeLocation;
        writeToConfig(safeLocation);
        return true;
    }

    private Location readFromConfig() {
        String worldName = this.configService.config().getString(PATH + ".world", "").trim();
        if (worldName.isBlank()) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            this.plugin.getLogger().warning("Lobby world '" + worldName + "' is not loaded or does not exist. Lobby spawn disabled until fixed.");
            return null;
        }

        double x = this.configService.config().getDouble(PATH + ".x");
        double y = this.configService.config().getDouble(PATH + ".y");
        double z = this.configService.config().getDouble(PATH + ".z");
        float yaw = this.configService.config().getDouble(PATH + ".yaw").floatValue();
        float pitch = this.configService.config().getDouble(PATH + ".pitch").floatValue();

        if (!isFinite(x) || !isFinite(y) || !isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            this.plugin.getLogger().warning("Lobby coordinates in config are invalid (NaN/Infinite). Lobby spawn disabled until fixed.");
            return null;
        }

        return new Location(world, x, y, z, yaw, pitch);
    }

    private void writeToConfig(Location location) {
        this.configService.config().set(PATH + ".world", Objects.requireNonNull(location.getWorld()).getName());
        this.configService.config().set(PATH + ".x", location.getX());
        this.configService.config().set(PATH + ".y", location.getY());
        this.configService.config().set(PATH + ".z", location.getZ());
        this.configService.config().set(PATH + ".yaw", location.getYaw());
        this.configService.config().set(PATH + ".pitch", location.getPitch());
        try {
            this.configService.saveConfig();
        } catch (Exception exception) {
            this.plugin.getLogger().warning("Could not save lobby location: " + exception.getMessage());
        }
    }

    private static Location sanitize(Location input) {
        if (input == null || input.getWorld() == null) {
            return null;
        }
        if (!isFinite(input.getX()) || !isFinite(input.getY()) || !isFinite(input.getZ())
                || !Float.isFinite(input.getYaw()) || !Float.isFinite(input.getPitch())) {
            return null;
        }
        return input.clone();
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
