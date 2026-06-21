package com.stephanofer.lobbyHera.message;

import com.stephanofer.lobbyHera.config.PluginConfigService;
import com.stephanofer.networkplayersettings.settings.api.PlayerSettingsService;
import com.stephanofer.networkplayersettings.settings.language.Language;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class LocalizationService {

    private final PluginConfigService configService;
    private final PlayerSettingsService playerSettingsService;

    public LocalizationService(PluginConfigService configService) {
        this.configService = configService;
        this.playerSettingsService = Bukkit.getServicesManager().load(
            PlayerSettingsService.class
        );
        if (this.playerSettingsService == null) {
            throw new IllegalStateException(
                "NetworkPlayerSettings PlayerSettingsService is not available"
            );
        }
    }

    public PlayerSettingsService playerSettingsService() {
        return this.playerSettingsService;
    }

    public String language(CommandSender sender) {
        if (sender instanceof Player player) {
            return language(player);
        }
        return this.configService.snapshot().defaultLanguage();
    }

    public String language(Player player) {
        Language resolved = this.playerSettingsService.resolvedLanguage(player);
        return normalize(resolved.code());
    }

    public String defaultLanguage() {
        return this.configService.snapshot().defaultLanguage();
    }

    public static String normalize(String raw) {
        String normalized =
            raw == null ? "en" : raw.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("es") ? "es" : "en";
    }
}
