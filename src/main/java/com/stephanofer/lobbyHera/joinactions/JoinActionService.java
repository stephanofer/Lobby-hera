package com.stephanofer.lobbyHera.joinactions;

import com.stephanofer.lobbyHera.config.PluginConfigService;
import com.stephanofer.lobbyHera.itemjoin.ItemJoinService;
import com.stephanofer.lobbyHera.lobby.LobbyLocationService;
import com.stephanofer.lobbyHera.staff.StaffModeService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

public final class JoinActionService {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final PluginConfigService configService;
    private final LobbyLocationService lobbyLocationService;
    private final ItemJoinService itemJoinService;
    private final StaffModeService staffModeService;

    public JoinActionService(JavaPlugin plugin, PluginConfigService configService, LobbyLocationService lobbyLocationService, ItemJoinService itemJoinService, StaffModeService staffModeService) {
        this.plugin = plugin;
        this.configService = configService;
        this.lobbyLocationService = lobbyLocationService;
        this.itemJoinService = itemJoinService;
        this.staffModeService = staffModeService;
    }

    public void run(Player player) {
        PluginConfigService.JoinActionsSnapshot settings = this.configService.snapshot().joinActions();
        if (!settings.enabled()) {
            return;
        }

        if (settings.teleportSpawn()) {
            teleportSpawn(player);
        }
        if (settings.clearInventory() && !this.staffModeService.isActive(player.getUniqueId())) {
            player.getInventory().clear();
            player.getInventory().setItemInOffHand(null);
        }
        if (settings.removeEffects()) {
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        }
        for (String action : settings.actions()) {
            runAction(player, action);
        }
        if (settings.giveLobbyItems() && !this.staffModeService.isActive(player.getUniqueId())) {
            this.itemJoinService.giveLobbyItems(player);
        }
    }

    private void teleportSpawn(Player player) {
        Optional<org.bukkit.Location> lobby = this.lobbyLocationService.getLobbyLocation();
        if (lobby.isEmpty()) {
            return;
        }
        player.teleportAsync(lobby.get()).thenAccept(success -> {
            if (!success && player.isOnline()) {
                this.plugin.getLogger().warning("Could not teleport " + player.getName() + " to lobby during join actions.");
            }
        });
    }

    private void runAction(Player player, String rawAction) {
        if (rawAction == null || rawAction.isBlank()) {
            return;
        }

        String action = rawAction.trim();
        int end = action.indexOf(']');
        if (!action.startsWith("[") || end <= 1 || end + 1 >= action.length()) {
            this.plugin.getLogger().warning("Invalid join action: " + rawAction);
            return;
        }

        String type = action.substring(1, end).trim().toUpperCase(Locale.ROOT);
        String payload = action.substring(end + 1).trim().replace("<player>", player.getName());
        switch (type) {
            case "GAMEMODE" -> applyGameMode(player, payload);
            case "SOUND" -> playSound(player, payload);
            case "TITLE" -> showTitle(player, payload);
            case "MESSAGE" -> player.sendMessage(MINI.deserialize(payload));
            case "POTION_EFFECT" -> applyPotionEffect(player, payload);
            default -> this.plugin.getLogger().warning("Unknown join action type: " + type);
        }
    }

    private void applyGameMode(Player player, String payload) {
        try {
            player.setGameMode(GameMode.valueOf(payload.trim().replace('-', '_').toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            this.plugin.getLogger().warning("Invalid join action gamemode: " + payload);
        }
    }

    private void playSound(Player player, String payload) {
        NamespacedKey key = parseKey(payload, true);
        Sound sound = key == null ? null : Registry.SOUNDS.get(key);
        if (sound == null) {
            this.plugin.getLogger().warning("Invalid join action sound: " + payload);
            return;
        }
        player.playSound(player.getLocation(), sound, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private void showTitle(Player player, String payload) {
        String[] split = payload.split(";", 2);
        Component title = MINI.deserialize(split[0].trim());
        Component subtitle = split.length > 1 ? MINI.deserialize(split[1].trim()) : Component.empty();
        player.showTitle(Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))));
    }

    private void applyPotionEffect(Player player, String payload) {
        String[] split = payload.split(";");
        if (split.length < 3) {
            this.plugin.getLogger().warning("Invalid join action potion effect: " + payload);
            return;
        }

        PotionEffectType type = parsePotionEffect(split[0]);
        if (type == null) {
            this.plugin.getLogger().warning("Invalid join action potion effect type: " + split[0]);
            return;
        }

        try {
            int seconds = Integer.parseInt(split[1].trim());
            int amplifier = Math.max(0, Integer.parseInt(split[2].trim()));
            int durationTicks = seconds < 0 ? -1 : seconds * 20;
            player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, false, false, true));
        } catch (NumberFormatException exception) {
            this.plugin.getLogger().warning("Invalid join action potion effect numbers: " + payload);
        }
    }

    private static PotionEffectType parsePotionEffect(String raw) {
        NamespacedKey key = parseKey(raw, false);
        return key == null ? null : Registry.POTION_EFFECT_TYPE.get(key);
    }

    private static NamespacedKey parseKey(String raw, boolean soundPath) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        int separator = normalized.indexOf(':');
        String namespace = separator >= 0 ? normalized.substring(0, separator) : NamespacedKey.MINECRAFT;
        String path = separator >= 0 ? normalized.substring(separator + 1) : normalized;
        if (soundPath) {
            path = path.replace('_', '.');
        } else {
            path = path.replace('.', '_');
        }
        return namespace.isBlank() || path.isBlank() ? null : NamespacedKey.fromString(namespace + ":" + path);
    }
}
