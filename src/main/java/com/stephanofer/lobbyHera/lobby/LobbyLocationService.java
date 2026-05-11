package com.stephanofer.lobbyHera.lobby;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Optional;

public final class LobbyLocationService {

    private static final String PATH = "settings.lobby";

    private final JavaPlugin plugin;
    private volatile Location lobbyLocation;

    public LobbyLocationService(JavaPlugin plugin) {
        this.plugin = plugin;
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
        FileConfiguration config = this.plugin.getConfig();

        String worldName = config.getString(PATH + ".world", "").trim();
        if (worldName.isBlank()) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            this.plugin.getLogger().warning("Lobby world '" + worldName + "' is not loaded or does not exist. Lobby spawn disabled until fixed.");
            return null;
        }

        double x = config.getDouble(PATH + ".x");
        double y = config.getDouble(PATH + ".y");
        double z = config.getDouble(PATH + ".z");
        float yaw = (float) config.getDouble(PATH + ".yaw");
        float pitch = (float) config.getDouble(PATH + ".pitch");

        if (!isFinite(x) || !isFinite(y) || !isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            this.plugin.getLogger().warning("Lobby coordinates in config are invalid (NaN/Infinite). Lobby spawn disabled until fixed.");
            return null;
        }

        return new Location(world, x, y, z, yaw, pitch);
    }

    private void writeToConfig(Location location) {
        FileConfiguration config = this.plugin.getConfig();
        config.set(PATH + ".world", Objects.requireNonNull(location.getWorld()).getName());
        config.set(PATH + ".x", location.getX());
        config.set(PATH + ".y", location.getY());
        config.set(PATH + ".z", location.getZ());
        config.set(PATH + ".yaw", location.getYaw());
        config.set(PATH + ".pitch", location.getPitch());
        this.plugin.saveConfig();
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
