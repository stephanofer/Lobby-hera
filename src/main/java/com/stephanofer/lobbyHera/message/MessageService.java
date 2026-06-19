package com.stephanofer.lobbyHera.message;

import com.stephanofer.lobbyHera.config.PluginConfigService;
import dev.dejvokep.boostedyaml.YamlDocument;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MessageService {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final PluginConfigService configService;
    private final LocalizationService localizationService;

    public MessageService(PluginConfigService configService, LocalizationService localizationService) {
        this.configService = configService;
        this.localizationService = localizationService;
    }

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        sender.sendMessage(component(this.localizationService.language(sender), key, resolvers));
    }

    public void send(Player player, String key, TagResolver... resolvers) {
        player.sendMessage(component(this.localizationService.language(player), key, resolvers));
    }

    public String language(CommandSender sender) {
        return this.localizationService.language(sender);
    }

    public Component component(String language, String key, TagResolver... resolvers) {
        return MINI.deserialize(raw(language, key), resolvers);
    }

    public String raw(String language, String key) {
        YamlDocument messages = this.configService.messages();
        String lang = LocalizationService.normalize(language);
        String value = messages.getString("messages." + key + "." + lang, null);
        if (value != null && !value.isBlank()) {
            return value;
        }

        String fallback = messages.getString("messages." + key + "." + this.localizationService.defaultLanguage(), null);
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }

        return messages.getString("messages." + key + ".en", "<red>Missing message: " + key);
    }
}
