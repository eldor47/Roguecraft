package com.eldor.roguecraft.listeners;

import com.eldor.roguecraft.RoguecraftPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntitySpawnEvent;

/**
 * WorldGuard compatibility listener
 * Allows plugin-spawned mobs to spawn even if WorldGuard flags deny mob spawning
 */
public class WorldGuardListener implements Listener {
    private final RoguecraftPlugin plugin;
    
    public WorldGuardListener(RoguecraftPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Allow plugin-spawned mobs to spawn even if WorldGuard denies mob spawning
     * This runs at HIGHEST priority to override WorldGuard's spawn cancellation
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Check if this entity was spawned by Roguecraft
        boolean isRoguecraftSpawn = event.getEntity().hasMetadata("roguecraft_spawned") || 
                                     event.getEntity().hasMetadata("roguecraft_mob") ||
                                     event.getEntity().hasMetadata("roguecraft_decoy") ||
                                     event.getEntity().hasMetadata("roguecraft_summon");
        
        // Also check if spawn reason is CUSTOM (which spawnEntity uses) and location matches
        if (!isRoguecraftSpawn && event.getSpawnReason() == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM) {
            // Check if location is a Roguecraft spawn location
            isRoguecraftSpawn = plugin.getGameManager().isRoguecraftSpawnLocation(event.getLocation());
        }
        
        // Allow the spawn even if WorldGuard cancelled it
        if (isRoguecraftSpawn && event.isCancelled()) {
            event.setCancelled(false);
            // Use fine level logging to reduce log spam (only logs if fine logging is enabled)
            plugin.getLogger().fine("Allowed Roguecraft mob spawn despite WorldGuard cancellation");
        }
    }
    
    /**
     * Prevent WorldGuard from removing/killing decoy villagers and summons
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        // Prevent WorldGuard from damaging/removing decoy villagers
        if (event.getEntity().hasMetadata("roguecraft_decoy")) {
            event.setCancelled(true);
            return;
        }
        
        // Prevent WorldGuard from damaging/removing summons (but allow mob damage)
        if (event.getEntity().hasMetadata("roguecraft_summon")) {
            // Only cancel if damage is from WorldGuard or environment (not from mobs)
            if (!(event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent)) {
                event.setCancelled(true);
            }
        }
    }
    
    /**
     * Handle entity spawn events to protect decoy villagers and summons immediately
     * This runs at HIGHEST priority to catch it before WorldGuard can interfere
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntitySpawn(EntitySpawnEvent event) {
        // Check if it's in an arena location (likely a plugin-spawned entity)
        if (plugin.getGameManager().isRoguecraftSpawnLocation(event.getLocation())) {
            if (event.getEntity() instanceof org.bukkit.entity.Villager) {
                org.bukkit.entity.Villager villager = (org.bukkit.entity.Villager) event.getEntity();
                // Mark it as plugin-spawned to protect it from WorldGuard
                villager.setMetadata("roguecraft_spawned", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                villager.setMetadata("roguecraft_mob", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                villager.setMetadata("roguecraft_decoy", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
            } else if (event.getEntity() instanceof org.bukkit.entity.LivingEntity) {
                // Mark summons and other plugin-spawned entities
                org.bukkit.entity.LivingEntity entity = (org.bukkit.entity.LivingEntity) event.getEntity();
                if (!entity.hasMetadata("roguecraft_summon")) {
                    entity.setMetadata("roguecraft_spawned", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                    entity.setMetadata("roguecraft_mob", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                }
            }
        }
    }
}

