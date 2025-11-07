package com.eldor.roguecraft.listeners;

import com.eldor.roguecraft.RoguecraftPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

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
        boolean isRoguecraftSpawn = event.getEntity().hasMetadata("roguecraft_spawned");
        
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
}

