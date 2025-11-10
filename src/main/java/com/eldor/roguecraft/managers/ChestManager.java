package com.eldor.roguecraft.managers;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.Arena;
import com.eldor.roguecraft.models.GachaChest;
import org.bukkit.Location;

import java.util.*;

/**
 * Manages gacha chest spawning and cleanup
 */
public class ChestManager {
    private final RoguecraftPlugin plugin;
    private final Map<UUID, List<GachaChest>> arenaChests; // TeamRun ID -> Chests
    private final Random random;
    
    public ChestManager(RoguecraftPlugin plugin) {
        this.plugin = plugin;
        this.arenaChests = new HashMap<>();
        this.random = new Random();
    }
    
    /**
     * Spawn chests for a team run
     * Spawns 8-10 chests randomly around the arena
     */
    public void spawnChestsForRun(UUID teamId, Arena arena) {
        // Remove any existing chests first (safety check to prevent duplicates)
        removeChestsForRun(teamId);
        
        List<GachaChest> chests = new ArrayList<>();
        
        // Spawn 8-10 random chests around the arena
        int chestCount = 8 + random.nextInt(3); // 8, 9, or 10 chests
        
        plugin.getLogger().info("[Chest] Attempting to spawn " + chestCount + " chests for team " + teamId);
        
        int attempts = 0;
        int maxAttempts = chestCount * 5; // Try up to 5x the desired count to find valid locations
        
        // Get existing shrines to check spacing
        List<com.eldor.roguecraft.models.Shrine> existingShrines = plugin.getShrineManager().getShrinesForRun(teamId);
        double minDistance = 8.0; // Minimum distance between chests and shrines (8 blocks)
        
        while (chests.size() < chestCount && attempts < maxAttempts) {
            attempts++;
            Location spawnLoc = getRandomChestLocation(arena);
            if (spawnLoc != null) {
                // Check if we're too close to another chest
                boolean tooClose = false;
                for (GachaChest existingChest : chests) {
                    if (spawnLoc.distance(existingChest.getLocation()) < minDistance) {
                        tooClose = true;
                        break;
                    }
                }
                
                // Check if we're too close to any shrine
                if (!tooClose && existingShrines != null) {
                    for (com.eldor.roguecraft.models.Shrine shrine : existingShrines) {
                        if (spawnLoc.distance(shrine.getLocation()) < minDistance) {
                            tooClose = true;
                            break;
                        }
                    }
                }
                
                if (!tooClose) {
                    GachaChest chest = new GachaChest(spawnLoc);
                    chest.spawn();
                    chests.add(chest);
                    plugin.getLogger().info("[Chest] Spawned gacha chest #" + chests.size() + " at " + 
                        String.format("%.1f, %.1f, %.1f", spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ()));
                }
            }
        }
        
        arenaChests.put(teamId, chests);
        plugin.getLogger().info("[Chest] Successfully spawned " + chests.size() + " out of " + chestCount + " chests for team " + teamId);
        
        if (chests.isEmpty()) {
            plugin.getLogger().warning("[Chest] WARNING: No chests were spawned! Check arena configuration and ground blocks.");
        }
    }
    
    /**
     * Get a random location for a chest within the arena
     * Follows the same pattern as shrine spawning - just uses arena center Y
     */
    private Location getRandomChestLocation(Arena arena) {
        if (arena.getCenter() == null) {
            plugin.getLogger().warning("[Chest] Cannot spawn chest: Arena center is null!");
            return null;
        }
        
        // Spawn chests around the arena (similar to shrines)
        Random random = new Random();
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = arena.getRadius() * 0.7; // 70% of radius
        
        double x = arena.getCenter().getX() + Math.cos(angle) * distance;
        double z = arena.getCenter().getZ() + Math.sin(angle) * distance;
        double y = arena.getCenter().getY(); // Use arena center Y directly, like shrines
        
        Location chestLoc = new Location(arena.getCenter().getWorld(), x, y, z);
        
        return chestLoc;
    }
    
    /**
     * Remove all chests for a run
     */
    public void removeChestsForRun(UUID teamId) {
        List<GachaChest> chests = arenaChests.remove(teamId);
        if (chests != null && !chests.isEmpty()) {
            plugin.getLogger().info("[Chest] Removing " + chests.size() + " chests for team " + teamId);
            int removed = 0;
            for (GachaChest chest : chests) {
                if (chest != null) {
                    try {
                        chest.remove();
                        removed++;
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Chest] Failed to remove chest at " + chest.getLocation() + ": " + e.getMessage());
                    }
                }
            }
            plugin.getLogger().info("[Chest] Successfully removed " + removed + " out of " + chests.size() + " chests");
        } else {
            plugin.getLogger().fine("[Chest] No chests found to remove for team " + teamId);
        }
    }
    
    /**
     * Get chest near a player (within 3 blocks)
     * Only returns chests that haven't been opened yet
     */
    public GachaChest getChestNearPlayer(UUID teamId, org.bukkit.entity.Player player) {
        List<GachaChest> chests = arenaChests.get(teamId);
        if (chests == null) {
            return null;
        }
        
        Location playerLoc = player.getLocation();
        for (GachaChest chest : chests) {
            if (!chest.isActive() || chest.isOpened()) {
                continue;
            }
            
            Location chestLoc = chest.getLocation();
            double distance = playerLoc.distance(chestLoc);
            
            if (distance <= 3.0) {
                return chest;
            }
        }
        
        return null;
    }
    
    /**
     * Get all chests for a team
     */
    public List<GachaChest> getChestsForRun(UUID teamId) {
        return new ArrayList<>(arenaChests.getOrDefault(teamId, new ArrayList<>()));
    }
    
    /**
     * Add a chest dynamically (e.g., from legendary mob drop)
     */
    public void addChestForRun(UUID teamId, GachaChest chest) {
        arenaChests.computeIfAbsent(teamId, k -> new ArrayList<>()).add(chest);
    }
    
    /**
     * Remove all chests from all runs (safety cleanup method)
     */
    public void removeAllChests() {
        int totalChests = 0;
        for (List<GachaChest> chests : arenaChests.values()) {
            totalChests += chests.size();
        }
        
        plugin.getLogger().info("[Chest] Removing all chests (" + totalChests + " total)");
        
        for (Map.Entry<UUID, List<GachaChest>> entry : arenaChests.entrySet()) {
            for (GachaChest chest : entry.getValue()) {
                if (chest != null) {
                    try {
                        chest.remove();
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Chest] Failed to remove chest: " + e.getMessage());
                    }
                }
            }
        }
        
        arenaChests.clear();
        plugin.getLogger().info("[Chest] All chests removed");
    }
}

