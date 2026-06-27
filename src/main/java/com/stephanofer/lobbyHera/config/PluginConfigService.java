package com.stephanofer.lobbyHera.config;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import org.bukkit.GameMode;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
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
                forcedSlot,
                readWorldProtectionSnapshot(),
                readJoinActionsSnapshot(),
                readStaffModeSnapshot()
        );
    }

    private WorldProtectionSnapshot readWorldProtectionSnapshot() {
        String path = "world-protection.";
        long alwaysDayTime = this.config.getLong(path + "always-day.time", 6000L);
        if (alwaysDayTime < 0L || alwaysDayTime > 24000L) {
            this.plugin.getLogger().warning("Invalid world-protection.always-day.time=" + alwaysDayTime + ". Must be between 0 and 24000. Falling back to 6000.");
            alwaysDayTime = 6000L;
        }

        return new WorldProtectionSnapshot(
                this.config.getBoolean(path + "enabled", true),
                this.config.getString(path + "bypass-permission", "lobbyhera.protection.bypass"),
                this.config.getBoolean(path + "disable-hunger-loss", true),
                this.config.getBoolean(path + "disable-player-pvp", true),
                this.config.getBoolean(path + "disable-fall-damage", true),
                this.config.getBoolean(path + "disable-fire-damage", true),
                this.config.getBoolean(path + "disable-drowning", true),
                this.config.getBoolean(path + "disable-void-death", true),
                this.config.getBoolean(path + "disable-off-hand-swap", true),
                this.config.getBoolean(path + "disable-weather-change", true),
                this.config.getBoolean(path + "disable-death-message", true),
                this.config.getBoolean(path + "clear-death-drops", true),
                this.config.getBoolean(path + "disable-mob-spawning", true),
                this.config.getBoolean(path + "disable-item-drop", true),
                this.config.getBoolean(path + "disable-item-pickup", false),
                this.config.getBoolean(path + "disable-block-break", true),
                this.config.getBoolean(path + "disable-block-place", true),
                this.config.getBoolean(path + "disable-block-interact", true),
                this.config.getBoolean(path + "disable-block-burn", true),
                this.config.getBoolean(path + "disable-block-fire-spread", true),
                this.config.getBoolean(path + "disable-block-leaf-decay", true),
                this.config.getBoolean(path + "protect-item-frames", true),
                this.config.getBoolean(path + "always-day.enabled", true),
                alwaysDayTime
        );
    }

    private JoinActionsSnapshot readJoinActionsSnapshot() {
        String path = "join-actions.";
        return new JoinActionsSnapshot(
                this.config.getBoolean(path + "enabled", true),
                this.config.getBoolean(path + "teleport-spawn", true),
                this.config.getBoolean(path + "clear-inventory", true),
                this.config.getBoolean(path + "remove-effects", true),
                this.config.getBoolean(path + "give-lobby-items", true),
                this.config.getStringList(path + "actions", List.of())
        );
    }

    private StaffModeSnapshot readStaffModeSnapshot() {
        String path = "staff-mode.";
        return new StaffModeSnapshot(
                this.config.getBoolean(path + "enabled", true),
                this.config.getString(path + "permission", "lobbyhera.staffmode"),
                this.config.getString(path + "others-permission", "lobbyhera.staffmode.others"),
                this.config.getBoolean(path + "protection-bypass", true),
                this.config.getBoolean(path + "clear-lobby-items-on-enable", true),
                this.config.getBoolean(path + "restore-lobby-items-on-disable", true),
                parseGameMode(this.config.getString(path + "gamemode-on-enable", "creative"), GameMode.CREATIVE, path + "gamemode-on-enable"),
                parseGameMode(this.config.getString(path + "gamemode-on-disable", "adventure"), GameMode.ADVENTURE, path + "gamemode-on-disable")
        );
    }

    private GameMode parseGameMode(String raw, GameMode fallback, String path) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return GameMode.valueOf(raw.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            this.plugin.getLogger().warning("Invalid " + path + "='" + raw + "'. Falling back to " + fallback.name().toLowerCase(Locale.ROOT) + ".");
            return fallback;
        }
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
            int forcedJoinHeldSlot,
            WorldProtectionSnapshot worldProtection,
            JoinActionsSnapshot joinActions,
            StaffModeSnapshot staffMode
    ) {
    }

    public record WorldProtectionSnapshot(
            boolean enabled,
            String bypassPermission,
            boolean disableHungerLoss,
            boolean disablePlayerPvp,
            boolean disableFallDamage,
            boolean disableFireDamage,
            boolean disableDrowning,
            boolean disableVoidDeath,
            boolean disableOffHandSwap,
            boolean disableWeatherChange,
            boolean disableDeathMessage,
            boolean clearDeathDrops,
            boolean disableMobSpawning,
            boolean disableItemDrop,
            boolean disableItemPickup,
            boolean disableBlockBreak,
            boolean disableBlockPlace,
            boolean disableBlockInteract,
            boolean disableBlockBurn,
            boolean disableBlockFireSpread,
            boolean disableBlockLeafDecay,
            boolean protectItemFrames,
            boolean alwaysDayEnabled,
            long alwaysDayTime
    ) {
    }

    public record JoinActionsSnapshot(
            boolean enabled,
            boolean teleportSpawn,
            boolean clearInventory,
            boolean removeEffects,
            boolean giveLobbyItems,
            List<String> actions
    ) {
    }

    public record StaffModeSnapshot(
            boolean enabled,
            String permission,
            String othersPermission,
            boolean protectionBypass,
            boolean clearLobbyItemsOnEnable,
            boolean restoreLobbyItemsOnDisable,
            GameMode gamemodeOnEnable,
            GameMode gamemodeOnDisable
    ) {
    }
}
