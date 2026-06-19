package com.stephanofer.lobbyHera.config;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public final class PluginConfigService {

    private final JavaPlugin plugin;
    private YamlDocument config;
    private YamlDocument items;
    private YamlDocument messages;
    private ConfigSnapshot snapshot;

    public PluginConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() throws IOException {
        this.config = loadDocument("config.yml");
        this.items = loadDocument("items.yml");
        this.messages = loadDocument("messages.yml");
        this.snapshot = readSnapshot();
    }

    public void saveConfig() throws IOException {
        this.config.save();
    }

    public YamlDocument config() {
        return this.config;
    }

    public YamlDocument items() {
        return this.items;
    }

    public YamlDocument messages() {
        return this.messages;
    }

    public ConfigSnapshot snapshot() {
        return this.snapshot;
    }

    private YamlDocument loadDocument(String resourceName) throws IOException {
        File file = new File(this.plugin.getDataFolder(), resourceName);
        return YamlDocument.create(
                file,
                this.plugin.getResource(resourceName),
                LoaderSettings.builder().setAutoUpdate(true).build(),
                UpdaterSettings.builder()
                        .setVersioning(new BasicVersioning("config-version"))
                        .setKeepAll(true)
                        .build()
        );
    }

    private ConfigSnapshot readSnapshot() {
        int forcedSlot = this.config.getInt("settings.forced-join-held-slot", -1);
        if (forcedSlot < -1 || forcedSlot > 8) {
            this.plugin.getLogger().warning("Invalid settings.forced-join-held-slot=" + forcedSlot + ". Must be between 0 and 8, or -1 to disable. Falling back to -1.");
            forcedSlot = -1;
        }

        return new ConfigSnapshot(
                normalizeLanguage(this.config.getString("settings.default-language", "en")),
                this.config.getBoolean("settings.block-all-item-drops", false),
                this.config.getBoolean("settings.block-managed-item-drops", true),
                this.config.getBoolean("settings.lock-managed-items-in-inventory", true),
                this.config.getBoolean("settings.clear-cooldowns-on-quit", false),
                forcedSlot
        );
    }

    private static String normalizeLanguage(String raw) {
        String normalized = raw == null ? "en" : raw.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("es") ? "es" : "en";
    }

    public record ConfigSnapshot(
            String defaultLanguage,
            boolean blockAllItemDrops,
            boolean blockManagedItemDrops,
            boolean lockManagedItemsInInventory,
            boolean clearCooldownsOnQuit,
            int forcedJoinHeldSlot
    ) {
    }
}
