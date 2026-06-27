package com.stephanofer.lobbyHera.worldprotection;

import com.stephanofer.lobbyHera.config.PluginConfigService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class WorldProtectionListener implements Listener {

    private final WorldProtectionService worldProtectionService;

    public WorldProtectionListener(WorldProtectionService worldProtectionService) {
        this.worldProtectionService = worldProtectionService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || !activeFor(player)) {
            return;
        }
        if (settings().disableHungerLoss() && event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (settings().protectItemFrames()
                && event.getEntity() instanceof ItemFrame
                && (!(event instanceof EntityDamageByEntityEvent damageByEntityEvent) || !isBypassingDamager(damageByEntityEvent.getDamager()))) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player player) || !activeFor(player)) {
            return;
        }

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (settings().disableVoidDeath() && cause == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            this.worldProtectionService.rescueFromVoid(player);
            return;
        }
        if (settings().disableFallDamage() && cause == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            return;
        }
        if (settings().disableDrowning() && cause == EntityDamageEvent.DamageCause.DROWNING) {
            event.setCancelled(true);
            return;
        }
        if (settings().disableFireDamage() && isFireDamage(cause)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (settings().protectItemFrames() && event.getEntity() instanceof Hanging && !isBypassingDamager(event.getDamager())) {
            event.setCancelled(true);
            return;
        }
        if (!settings().disablePlayerPvp() || !(event.getEntity() instanceof Player victim) || !activeFor(victim)) {
            return;
        }
        if (isPlayerAttack(event.getDamager())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (activeFor(event.getPlayer()) && settings().disableOffHandSwap()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (this.worldProtectionService.enabled() && settings().disableWeatherChange() && event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (!activeFor(player)) {
            return;
        }
        if (settings().disableDeathMessage()) {
            event.setShowDeathMessages(false);
            event.deathMessage(null);
        }
        if (settings().clearDeathDrops()) {
            event.getDrops().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!this.worldProtectionService.enabled() || !settings().disableMobSpawning()) {
            return;
        }
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (activeFor(event.getPlayer()) && settings().disableItemDrop()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && activeFor(player) && settings().disableItemPickup()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (activeFor(event.getPlayer()) && settings().disableBlockBreak()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (activeFor(event.getPlayer()) && settings().disableBlockPlace()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!activeFor(player)) {
            return;
        }
        if (settings().disableBlockInteract() && isBlockInteraction(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (settings().protectItemFrames() && event.getRightClicked() instanceof ItemFrame && activeFor(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (settings().protectItemFrames() && event.getEntity() instanceof ItemFrame && !isBypassingDamager(event.getRemover())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (this.worldProtectionService.enabled() && settings().disableBlockBurn()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (this.worldProtectionService.enabled() && settings().disableBlockFireSpread() && event.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (this.worldProtectionService.enabled() && settings().disableBlockFireSpread() && event.getNewState().getType() == Material.FIRE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (this.worldProtectionService.enabled() && settings().disableBlockLeafDecay()) {
            event.setCancelled(true);
        }
    }

    private boolean activeFor(Player player) {
        return this.worldProtectionService.enabled() && !this.worldProtectionService.bypasses(player);
    }

    private boolean isBypassingDamager(Entity entity) {
        Player player = playerFromDamager(entity);
        return player != null && this.worldProtectionService.bypasses(player);
    }

    private static boolean isPlayerAttack(Entity damager) {
        return playerFromDamager(damager) != null;
    }

    private static Player playerFromDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            return source instanceof Player player ? player : null;
        }
        return null;
    }

    private static boolean isFireDamage(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR;
    }

    private static boolean isBlockInteraction(PlayerInteractEvent event) {
        Action action = event.getAction();
        Block clickedBlock = event.getClickedBlock();
        return clickedBlock != null && (action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_BLOCK || action == Action.PHYSICAL);
    }

    private PluginConfigService.WorldProtectionSnapshot settings() {
        return this.worldProtectionService.settings();
    }
}
