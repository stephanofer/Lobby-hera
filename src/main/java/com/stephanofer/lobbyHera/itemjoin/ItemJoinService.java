package com.stephanofer.lobbyHera.itemjoin;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.format.TextDecoration;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ItemJoinService {

    private static final String SETTINGS_SECTION = "settings";
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Pattern ATTRIBUTE_ENTRY = Pattern.compile("\\{([^}]+)}");
    private static final Map<String, String> LEGACY_ENCHANT_ALIASES = Map.ofEntries(
            Map.entry("DAMAGE_ALL", "sharpness"),
            Map.entry("DAMAGE_UNDEAD", "smite"),
            Map.entry("DAMAGE_ARTHROPODS", "bane_of_arthropods"),
            Map.entry("LOOT_BONUS_MOBS", "looting"),
            Map.entry("LOOT_BONUS_BLOCKS", "fortune"),
            Map.entry("ARROW_DAMAGE", "power"),
            Map.entry("ARROW_KNOCKBACK", "punch"),
            Map.entry("ARROW_FIRE", "flame"),
            Map.entry("ARROW_INFINITE", "infinity"),
            Map.entry("DIG_SPEED", "efficiency"),
            Map.entry("DURABILITY", "unbreaking"),
            Map.entry("PROTECTION_ENVIRONMENTAL", "protection"),
            Map.entry("PROTECTION_FIRE", "fire_protection"),
            Map.entry("PROTECTION_FALL", "feather_falling"),
            Map.entry("PROTECTION_EXPLOSIONS", "blast_protection"),
            Map.entry("PROTECTION_PROJECTILE", "projectile_protection"),
            Map.entry("WATER_WORKER", "aqua_affinity"),
            Map.entry("OXYGEN", "respiration")
    );

    private final JavaPlugin plugin;
    private final NamespacedKey customItemKey;

    private final Map<String, ItemDefinition> itemsById = new HashMap<>();
    private final Map<TriggerType, List<ItemDefinition>> itemsByTrigger = new EnumMap<>(TriggerType.class);
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Set<UUID> staffBypassUsers = new HashSet<>();

    private boolean blockAllItemDrops;
    private boolean blockManagedItemDrops;
    private boolean lockManagedItemsInInventory;
    private boolean clearCooldownsOnQuit;
    private int forcedJoinHeldSlot;

    public ItemJoinService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.customItemKey = new NamespacedKey(plugin, "itemjoin-id");

        for (TriggerType triggerType : TriggerType.values()) {
            this.itemsByTrigger.put(triggerType, new ArrayList<>());
        }
    }

    public void load() {
        this.itemsById.clear();
        this.cooldowns.clear();
        this.itemsByTrigger.values().forEach(List::clear);

        this.plugin.reloadConfig();
        FileConfiguration config = this.plugin.getConfig();

        this.blockAllItemDrops = config.getBoolean(SETTINGS_SECTION + ".block-all-item-drops", false);
        this.blockManagedItemDrops = config.getBoolean(SETTINGS_SECTION + ".block-managed-item-drops", false);
        this.lockManagedItemsInInventory = config.getBoolean(SETTINGS_SECTION + ".lock-managed-items-in-inventory", false);
        this.clearCooldownsOnQuit = config.getBoolean(SETTINGS_SECTION + ".clear-cooldowns-on-quit", false);
        this.forcedJoinHeldSlot = config.getInt(SETTINGS_SECTION + ".forced-join-held-slot", -1);

        if (this.forcedJoinHeldSlot < -1 || this.forcedJoinHeldSlot > 8) {
            this.plugin.getLogger().warning("Invalid settings.forced-join-held-slot=" + this.forcedJoinHeldSlot + ". Must be between 0 and 8, or -1 to disable. Falling back to -1.");
            this.forcedJoinHeldSlot = -1;
        }

        int loaded = 0;
        for (String key : config.getKeys(false)) {
            if (key.equalsIgnoreCase(SETTINGS_SECTION)) {
                continue;
            }

            if (!config.isConfigurationSection(key)) {
                continue;
            }

            ConfigurationSection section = Objects.requireNonNull(config.getConfigurationSection(key));
            ItemDefinition definition = parseDefinition(key, section);
            if (definition == null) {
                continue;
            }

            this.itemsById.put(definition.id(), definition);
            for (TriggerType trigger : definition.triggers()) {
                this.itemsByTrigger.get(trigger).add(definition);
            }
            loaded++;
        }

        this.plugin.getLogger().info("Loaded " + loaded + " ItemJoin item(s).");
        this.plugin.getLogger().info("ItemJoin settings -> blockAllDrops=" + this.blockAllItemDrops
                + ", blockManagedDrops=" + this.blockManagedItemDrops
                + ", lockManagedInInventory=" + this.lockManagedItemsInInventory
                + ", clearCooldownsOnQuit=" + this.clearCooldownsOnQuit
                + ", forcedJoinHeldSlot=" + this.forcedJoinHeldSlot);
    }

    public void clearRuntimeState() {
        this.cooldowns.clear();
        this.staffBypassUsers.clear();
    }

    public void handlePlayerQuit(UUID uuid) {
        if (this.clearCooldownsOnQuit) {
            this.cooldowns.remove(uuid);
            return;
        }

        Map<String, Long> byItem = this.cooldowns.get(uuid);
        if (byItem == null || byItem.isEmpty()) {
            this.cooldowns.remove(uuid);
            return;
        }

        long now = System.currentTimeMillis();
        byItem.values().removeIf(availableAt -> availableAt <= now);
        if (byItem.isEmpty()) {
            this.cooldowns.remove(uuid);
        }
    }

    public boolean toggleStaffBypass(UUID uuid) {
        if (this.staffBypassUsers.contains(uuid)) {
            this.staffBypassUsers.remove(uuid);
            return false;
        }

        this.staffBypassUsers.add(uuid);
        return true;
    }

    public void clearStaffBypass(UUID uuid) {
        this.staffBypassUsers.remove(uuid);
    }

    public boolean isStaffBypass(UUID uuid) {
        return this.staffBypassUsers.contains(uuid);
    }

    public void giveItems(Player player, TriggerType triggerType) {
        List<ItemDefinition> definitions = this.itemsByTrigger.get(triggerType);
        if (definitions == null || definitions.isEmpty()) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        int size = inventory.getSize();

        for (ItemDefinition definition : definitions) {
            if (definition.slot() < 0 || definition.slot() >= size) {
                continue;
            }
            inventory.setItem(definition.slot(), definition.itemTemplate().clone());
        }
    }

    public void applyJoinHeldSlot(Player player) {
        if (this.forcedJoinHeldSlot >= 0) {
            player.getInventory().setHeldItemSlot(this.forcedJoinHeldSlot);
        }
    }

    public boolean isManagedItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return false;
        }

        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return false;
        }

        String itemId = meta.getPersistentDataContainer().get(this.customItemKey, PersistentDataType.STRING);
        return itemId != null && this.itemsById.containsKey(itemId);
    }

    public boolean canSelfDrop(ItemStack itemStack) {
        ItemDefinition definition = resolveDefinition(itemStack);
        return definition == null || definition.allowSelfDrops();
    }

    public boolean shouldBlockDrop(Player player, ItemStack itemStack) {
        if (isStaffBypass(player.getUniqueId())) {
            return false;
        }

        if (this.blockAllItemDrops) {
            return true;
        }

        if (!isManagedItem(itemStack)) {
            return false;
        }

        if (this.blockManagedItemDrops) {
            return true;
        }

        return !canSelfDrop(itemStack);
    }

    public boolean shouldLockManagedItemsInInventory(Player player) {
        return this.lockManagedItemsInInventory && !isStaffBypass(player.getUniqueId());
    }

    public boolean canDeathDrop(ItemStack itemStack) {
        ItemDefinition definition = resolveDefinition(itemStack);
        return definition == null || definition.allowDeathDrops();
    }

    public void executeInteract(Player player, ItemStack itemStack) {
        ItemDefinition definition = resolveDefinition(itemStack);
        if (definition == null || definition.commands().isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!canUse(player, definition, now)) {
            sendCooldownMessage(player, definition, now);
            return;
        }

        boolean applyCooldown = definition.cooldownMillis() > 0
                && !(definition.creativeBypass() && player.getGameMode() == GameMode.CREATIVE);
        if (applyCooldown) {
            this.cooldowns
                    .computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                    .put(definition.id(), now + definition.cooldownMillis());
        }

        List<ConfiguredCommand> commands = definition.commands();
        if (definition.commandSequence() == CommandSequence.RANDOM && commands.size() > 1) {
            ConfiguredCommand selected = commands.get(ThreadLocalRandom.current().nextInt(commands.size()));
            try {
                dispatchCommand(player, selected, definition);
            } catch (Throwable throwable) {
                this.plugin.getLogger().log(Level.WARNING,
                        "Failed to execute ItemJoin random command for item '" + definition.id() + "'.", throwable);
            }
        } else {
            for (ConfiguredCommand command : commands) {
                try {
                    dispatchCommand(player, command, definition);
                } catch (Throwable throwable) {
                    this.plugin.getLogger().log(Level.WARNING,
                            "Failed to execute ItemJoin command for item '" + definition.id() + "': " + command.command(), throwable);
                }
            }
        }

        if (definition.commandSound() != null) {
            player.playSound(player.getLocation(), definition.commandSound(), SoundCategory.PLAYERS, 1.0f, 1.0f);
        }

    }

    private boolean canUse(Player player, ItemDefinition definition, long now) {
        if (definition.cooldownMillis() <= 0) {
            return true;
        }
        if (definition.creativeBypass() && player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }

        Map<String, Long> byItem = this.cooldowns.get(player.getUniqueId());
        if (byItem == null) {
            return true;
        }

        Long availableAt = byItem.get(definition.id());
        if (availableAt == null) {
            return true;
        }

        if (availableAt <= now) {
            byItem.remove(definition.id());
            if (byItem.isEmpty()) {
                this.cooldowns.remove(player.getUniqueId());
            }
            return true;
        }

        return false;
    }

    private void sendCooldownMessage(Player player, ItemDefinition definition, long now) {
        if (definition.cooldownMessage().isBlank()) {
            return;
        }

        long availableAt = this.cooldowns
                .getOrDefault(player.getUniqueId(), Collections.emptyMap())
                .getOrDefault(definition.id(), now);

        long remainingMillis = Math.max(0L, availableAt - now);
        long seconds = (long) Math.ceil(remainingMillis / 1000.0D);

        String parsed = definition.cooldownMessage()
                .replace("%item%", definition.placeholderItemName())
                .replace("%timeleft%", String.valueOf(seconds));

        player.sendMessage(MINI.deserialize(parsed));
    }

    private void dispatchCommand(Player player, ConfiguredCommand command, ItemDefinition definition) {
        String parsed = command.command()
                .replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("%item%", definition.placeholderItemName());

        switch (command.executor()) {
            case CONSOLE -> this.plugin.getServer().dispatchCommand(this.plugin.getServer().getConsoleSender(), parsed);
            case PLAYER -> player.performCommand(stripLeadingSlash(parsed));
        }
    }

    private static String stripLeadingSlash(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
    }

    private ItemDefinition resolveDefinition(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return null;
        }

        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return null;
        }

        String itemId = meta.getPersistentDataContainer().get(this.customItemKey, PersistentDataType.STRING);
        if (itemId == null) {
            return null;
        }

        return this.itemsById.get(itemId);
    }

    private ItemDefinition parseDefinition(String id, ConfigurationSection section) {
        String materialName = section.getString("id", "");
        Material material = Material.matchMaterial(materialName);
        if (material == null || material == Material.AIR) {
            this.plugin.getLogger().warning("Skipping item '" + id + "': invalid material '" + materialName + "'.");
            return null;
        }

        int slot = section.getInt("slot", -1);
        if (slot < 0) {
            this.plugin.getLogger().warning("Skipping item '" + id + "': slot must be >= 0.");
            return null;
        }

        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();

        String configuredName = section.getString("name", "");
        if (!configuredName.isBlank()) {
            meta.displayName(MINI.deserialize(configuredName).decoration(TextDecoration.ITALIC, false));
        }

        List<String> lore = readStringList(section, "lore");
        if (!lore.isEmpty()) {
            meta.lore(lore.stream()
                    .map(line -> MINI.deserialize(line).decoration(TextDecoration.ITALIC, false))
                    .toList());
        }

        parseEnchantments(section.getString("enchantment", ""), meta, id);
        parseAttributes(section.getString("attributes", ""), meta, id);

        ParsedFlags parsedFlags = parseFlags(section.getString("itemflags", ""), meta);

        meta.getPersistentDataContainer().set(this.customItemKey, PersistentDataType.STRING, id);
        itemStack.setItemMeta(meta);

        List<ConfiguredCommand> commands = parseCommands(readStringList(section, "interact"), id);
        CommandSequence commandSequence = parseCommandSequence(section.getString("commands-sequence", "SEQUENTIAL"));

        Sound commandSound = parseSound(section.getString("commands-sound", ""));

        long cooldownMillis = Math.max(0, section.getLong("commands-cooldown", 0L)) * 1000L;
        String cooldownMessage = section.getString("cooldown-message", "");

        Set<TriggerType> triggers = parseTriggers(readStringList(section, "triggers"), id);

        String placeholderName = configuredName.isBlank()
                ? id
                : PLAIN.serialize(MINI.deserialize(configuredName));

        return new ItemDefinition(
                id,
                itemStack,
                slot,
                triggers,
                commands,
                commandSequence,
                cooldownMillis,
                cooldownMessage,
                commandSound,
                parsedFlags.allowSelfDrops,
                parsedFlags.allowDeathDrops,
                parsedFlags.creativeBypass,
                placeholderName
        );
    }

    private void parseEnchantments(String raw, ItemMeta meta, String itemId) {
        if (raw == null || raw.isBlank()) {
            return;
        }

        String[] entries = raw.split(",");
        for (String entry : entries) {
            String[] split = entry.trim().split(":", 2);
            if (split.length != 2) {
                continue;
            }

            String name = split[0].trim();
            int level;
            try {
                level = Integer.parseInt(split[1].trim());
            } catch (NumberFormatException ex) {
                this.plugin.getLogger().warning("Invalid enchantment level in '" + itemId + "': " + entry);
                continue;
            }

            Enchantment enchantment = parseEnchantment(name);

            if (enchantment == null) {
                this.plugin.getLogger().warning("Unknown enchantment in '" + itemId + "': " + split[0]);
                continue;
            }

            meta.addEnchant(enchantment, level, true);
        }
    }

    private void parseAttributes(String raw, ItemMeta meta, String itemId) {
        if (raw == null || raw.isBlank()) {
            return;
        }

        Matcher matcher = ATTRIBUTE_ENTRY.matcher(raw);
        while (matcher.find()) {
            String entry = matcher.group(1).trim();
            String[] split = entry.split(":", 2);
            if (split.length != 2) {
                continue;
            }

            String attributeRaw = split[0].trim();
            double value;
            try {
                value = Double.parseDouble(split[1].trim());
            } catch (NumberFormatException ex) {
                this.plugin.getLogger().warning("Invalid attribute value in '" + itemId + "': " + entry);
                continue;
            }

            Attribute attribute = parseAttribute(attributeRaw);
            if (attribute == null) {
                this.plugin.getLogger().warning("Unknown attribute in '" + itemId + "': " + split[0]);
                continue;
            }

            String attributePath = normalizePathForKey(attributeRaw);

            AttributeModifier modifier = new AttributeModifier(
                    new NamespacedKey(this.plugin, "itemjoin-" + attributePath + "-" + UUID.randomUUID()),
                    value,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND
            );
            meta.addAttributeModifier(attribute, modifier);
        }
    }

    private ParsedFlags parseFlags(String raw, ItemMeta meta) {
        if (raw == null || raw.isBlank()) {
            return new ParsedFlags(false, false, false);
        }

        boolean allowSelfDrops = false;
        boolean allowDeathDrops = false;
        boolean creativeBypass = false;

        for (String entry : raw.split(",")) {
            String token = entry.trim();
            if (token.isBlank()) {
                continue;
            }

            String normalized = normalizeKey(token);
            switch (normalized) {
                case "UNBREAKABLE" -> meta.setUnbreakable(true);
                case "HIDE_FLAGS" -> meta.addItemFlags(ItemFlag.values());
                case "SELF_DROPS" -> allowSelfDrops = true;
                case "NO_SELF_DROPS" -> allowSelfDrops = false;
                case "DEATH_DROPS" -> allowDeathDrops = true;
                case "NO_DEATH_DROPS" -> allowDeathDrops = false;
                case "CREATIVEBYPASS", "CREATIVE_BYPASS" -> creativeBypass = true;
                default -> {
                    try {
                        ItemFlag itemFlag = ItemFlag.valueOf(normalized);
                        meta.addItemFlags(itemFlag);
                    } catch (IllegalArgumentException ignored) {
                        // Unknown custom flag: safely ignored.
                    }
                }
            }
        }

        return new ParsedFlags(allowSelfDrops, allowDeathDrops, creativeBypass);
    }

    private List<ConfiguredCommand> parseCommands(List<String> lines, String itemId) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }

        List<ConfiguredCommand> commands = new ArrayList<>(lines.size());
        for (String raw : lines) {
            if (raw == null || raw.isBlank()) {
                continue;
            }

            String[] split = raw.split(":", 2);
            if (split.length != 2) {
                this.plugin.getLogger().warning("Invalid interact command in '" + itemId + "': " + raw);
                continue;
            }

            CommandExecutorType executorType;
            String mode = split[0].trim().toLowerCase(Locale.ROOT);
            if (mode.equals("console")) {
                executorType = CommandExecutorType.CONSOLE;
            } else if (mode.equals("player")) {
                executorType = CommandExecutorType.PLAYER;
            } else {
                this.plugin.getLogger().warning("Unsupported interact executor in '" + itemId + "': " + split[0]);
                continue;
            }

            String command = split[1].trim();
            if (command.isBlank()) {
                continue;
            }

            commands.add(new ConfiguredCommand(executorType, command));
        }

        return List.copyOf(commands);
    }

    private CommandSequence parseCommandSequence(String raw) {
        if (raw == null || raw.isBlank()) {
            return CommandSequence.SEQUENTIAL;
        }

        try {
            return CommandSequence.valueOf(normalizeKey(raw));
        } catch (IllegalArgumentException ex) {
            return CommandSequence.SEQUENTIAL;
        }
    }

    private Set<TriggerType> parseTriggers(List<String> rawTriggers, String itemId) {
        if (rawTriggers == null || rawTriggers.isEmpty()) {
            return EnumSet.of(TriggerType.JOIN);
        }

        EnumSet<TriggerType> parsed = EnumSet.noneOf(TriggerType.class);
        for (String raw : rawTriggers) {
            if (raw == null || raw.isBlank()) {
                continue;
            }

            TriggerType triggerType = TriggerType.fromConfig(raw);
            if (triggerType == null) {
                this.plugin.getLogger().warning("Unknown trigger in '" + itemId + "': " + raw + " (ignored)");
                continue;
            }
            parsed.add(triggerType);
        }

        if (parsed.isEmpty()) {
            parsed.add(TriggerType.JOIN);
        }

        return parsed;
    }

    private Sound parseSound(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        NamespacedKey key = parseKey(raw, true);
        if (key == null) {
            return null;
        }

        Sound byKey = RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT).get(key);
        if (byKey != null) {
            return byKey;
        }

        String rawFieldSource = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;
        String enumLike = normalizeKey(rawFieldSource);
        try {
            Object field = Sound.class.getField(enumLike).get(null);
            if (field instanceof Sound sound) {
                return sound;
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            // Ignore enum-like lookup failure and continue with alternate path lookup.
        }

        String path = key.getKey();
        String alternatePath = path.contains(".")
                ? path.replace('.', '_')
                : path.replace('_', '.');

        if (alternatePath.equals(path)) {
            return null;
        }

        NamespacedKey alternateKey = NamespacedKey.fromString(key.getNamespace() + ":" + alternatePath);
        if (alternateKey == null) {
            return null;
        }

        return RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT).get(alternateKey);
    }

    private Attribute parseAttribute(String raw) {
        NamespacedKey key = parseKey(raw, false);
        if (key == null) {
            return null;
        }

        Attribute byKey = Registry.ATTRIBUTE.get(key);
        if (byKey != null) {
            return byKey;
        }

        String path = key.getKey();
        if (path.startsWith("generic_")) {
            NamespacedKey fallback = NamespacedKey.fromString(key.getNamespace() + ":" + path.substring("generic_".length()));
            if (fallback != null) {
                return Registry.ATTRIBUTE.get(fallback);
            }
        }

        return null;
    }

    private static String normalizePathForKey(String raw) {
        NamespacedKey key = parseKey(raw, false);
        if (key == null) {
            return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        }
        return key.getKey().replace('.', '_');
    }

    private Enchantment parseEnchantment(String raw) {
        NamespacedKey key = parseEnchantmentKey(raw);
        if (key != null) {
            Enchantment enchantment = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
            if (enchantment != null) {
                return enchantment;
            }
        }

        String source = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;
        String fieldName = normalizeKey(source);

        String aliasPath = LEGACY_ENCHANT_ALIASES.get(fieldName);
        if (aliasPath != null) {
            NamespacedKey aliasKey = NamespacedKey.fromString(NamespacedKey.MINECRAFT + ":" + aliasPath);
            if (aliasKey != null) {
                Enchantment enchantment = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(aliasKey);
                if (enchantment != null) {
                    return enchantment;
                }
            }
        }

        try {
            Object field = Enchantment.class.getField(fieldName).get(null);
            if (field instanceof Enchantment enchantment) {
                return enchantment;
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            // No enum-like field found.
        }

        return null;
    }

    private static NamespacedKey parseEnchantmentKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String trimmed = raw.trim().toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');

        String namespace;
        String path;
        int separator = trimmed.indexOf(':');
        if (separator >= 0) {
            namespace = trimmed.substring(0, separator);
            path = trimmed.substring(separator + 1);
        } else {
            namespace = NamespacedKey.MINECRAFT;
            path = trimmed;
        }

        if (namespace.isBlank() || path.isBlank()) {
            return null;
        }

        path = path.replace('.', '_');
        return NamespacedKey.fromString(namespace + ":" + path);
    }

    private static NamespacedKey parseKey(String raw, boolean soundPath) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String trimmed = raw.trim().toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');

        String namespace;
        String path;

        int separator = trimmed.indexOf(':');
        if (separator >= 0) {
            namespace = trimmed.substring(0, separator);
            path = trimmed.substring(separator + 1);
        } else {
            namespace = NamespacedKey.MINECRAFT;
            path = trimmed;
        }

        if (namespace.isBlank() || path.isBlank()) {
            return null;
        }

        if (soundPath) {
            if (!path.contains(".")) {
                path = path.replace('_', '.');
            }
        } else {
            if (path.startsWith("generic.")) {
                path = path.substring("generic.".length());
            } else if (path.startsWith("generic_")) {
                path = path.substring("generic_".length());
            }
            path = path.replace('.', '_');
        }

        return NamespacedKey.fromString(namespace + ":" + path);
    }

    private static String normalizeKey(String raw) {
        return raw.trim()
                .replace('-', '_')
                .replace('.', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }

    private static List<String> readStringList(ConfigurationSection section, String path) {
        if (section.isList(path)) {
            return section.getStringList(path);
        }

        String raw = section.getString(path, "");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    public enum TriggerType {
        JOIN,
        RESPAWN,
        WORLD_SWITCH;

        public static TriggerType fromConfig(String raw) {
            String normalized = normalizeKey(raw);
            return switch (normalized) {
                case "JOIN" -> JOIN;
                case "RESPAWN" -> RESPAWN;
                case "WORLD_SWITCH", "WORLDSWITCH", "WORLDCHANGE", "WORLD_CHANGE" -> WORLD_SWITCH;
                default -> null;
            };
        }
    }

    private enum CommandExecutorType {
        CONSOLE,
        PLAYER
    }

    private enum CommandSequence {
        SEQUENTIAL,
        RANDOM
    }

    private record ConfiguredCommand(CommandExecutorType executor, String command) {
    }

    private record ParsedFlags(boolean allowSelfDrops, boolean allowDeathDrops, boolean creativeBypass) {
    }

    private record ItemDefinition(
            String id,
            ItemStack itemTemplate,
            int slot,
            Set<TriggerType> triggers,
            List<ConfiguredCommand> commands,
            CommandSequence commandSequence,
            long cooldownMillis,
            String cooldownMessage,
            Sound commandSound,
            boolean allowSelfDrops,
            boolean allowDeathDrops,
            boolean creativeBypass,
            String placeholderItemName
    ) {
    }
}
