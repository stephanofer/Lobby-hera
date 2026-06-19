package com.stephanofer.lobbyHera.itemjoin;

import com.stephanofer.networkplayersettings.api.SettingKey;
import com.stephanofer.networkplayersettings.event.PlayerSettingChangeEvent;
import com.stephanofer.networkplayersettings.event.PlayerSettingsReadyEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemJoinListener implements Listener {

    private final JavaPlugin plugin;
    private final ItemJoinService itemJoinService;

    public ItemJoinListener(JavaPlugin plugin, ItemJoinService itemJoinService) {
        this.plugin = plugin;
        this.itemJoinService = itemJoinService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSettingsReady(PlayerSettingsReadyEvent event) {
        Player player = event.player();
        this.itemJoinService.giveItems(player, ItemJoinService.TriggerType.JOIN);
        this.itemJoinService.applyJoinHeldSlot(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLanguageChange(PlayerSettingChangeEvent event) {
        if (event.settingKey() != SettingKey.LANGUAGE) {
            return;
        }

        Bukkit.getScheduler().runTask(this.plugin, () -> {
            Player player = Bukkit.getPlayer(event.playerId());
            if (player != null) {
                this.itemJoinService.refreshManagedItems(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(this.plugin, () -> this.itemJoinService.giveItems(player, ItemJoinService.TriggerType.RESPAWN));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldSwitch(PlayerChangedWorldEvent event) {
        this.itemJoinService.giveItems(event.getPlayer(), ItemJoinService.TriggerType.WORLD_SWITCH);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (this.itemJoinService.isStaffBypass(player.getUniqueId())) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack itemStack = player.getInventory().getItemInMainHand();
        if (!this.itemJoinService.isManagedItem(itemStack)) {
            return;
        }

        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
        this.itemJoinService.executeInteract(player, itemStack);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (this.itemJoinService.isStaffBypass(player.getUniqueId())) {
            return;
        }

        ItemStack itemStack = player.getInventory().getItemInMainHand();
        if (!this.itemJoinService.isManagedItem(itemStack)) {
            return;
        }

        event.setCancelled(true);
        this.itemJoinService.executeInteract(player, itemStack);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack itemStack = event.getItemDrop().getItemStack();
        if (this.itemJoinService.shouldBlockDrop(event.getPlayer(), itemStack)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!this.itemJoinService.shouldLockManagedItemsInInventory(player)) {
            return;
        }

        PlayerInventory playerInventory = player.getInventory();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        ItemStack hotbarSwapItem = null;
        if (event.getHotbarButton() >= 0 && event.getHotbarButton() < 9) {
            hotbarSwapItem = playerInventory.getItem(event.getHotbarButton());
        }

        if (this.itemJoinService.isManagedItem(current)
                || this.itemJoinService.isManagedItem(cursor)
                || this.itemJoinService.isManagedItem(hotbarSwapItem)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!this.itemJoinService.shouldLockManagedItemsInInventory(player)) {
            return;
        }

        if (this.itemJoinService.isManagedItem(event.getOldCursor()) || this.itemJoinService.isManagedItem(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (!this.itemJoinService.shouldLockManagedItemsInInventory(event.getPlayer())) {
            return;
        }

        if (this.itemJoinService.isManagedItem(event.getMainHandItem()) || this.itemJoinService.isManagedItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(PlayerDeathEvent event) {
        if (this.itemJoinService.isStaffBypass(event.getPlayer().getUniqueId())) {
            return;
        }
        event.getDrops().removeIf(itemStack -> this.itemJoinService.isManagedItem(itemStack) && !this.itemJoinService.canDeathDrop(itemStack));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        this.itemJoinService.handlePlayerQuit(player.getUniqueId());
        this.itemJoinService.clearStaffBypass(player.getUniqueId());
    }
}
