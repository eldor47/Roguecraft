package com.eldor.roguecraft.managers;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.Arena;
import com.eldor.roguecraft.models.GachaChest;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

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
     * Spawns 12-15 chests randomly around the arena
     */
    public void spawnChestsForRun(UUID teamId, Arena arena) {
        // Remove any existing chests first (safety check to prevent duplicates)
        removeChestsForRun(teamId);
        
        List<GachaChest> chests = new ArrayList<>();
        
        // Spawn 12-15 random chests around the arena
        int chestCount = 12 + random.nextInt(4); // 12, 13, 14, or 15 chests
        
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
     * Randomly distributed throughout the region, on the surface (not in caves)
     */
    private Location getRandomChestLocation(Arena arena) {
        if (arena.getCenter() == null) {
            plugin.getLogger().warning("[Chest] Cannot spawn chest: Arena center is null!");
            return null;
        }
        
        World world = arena.getCenter().getWorld();
        if (world == null) {
            plugin.getLogger().warning("[Chest] Cannot spawn chest: World is null!");
            return null;
        }
        
        Random random = new Random();
        double radius = arena.getRadius();
        
        // Generate random X/Z coordinates uniformly distributed within the arena radius
        // Use rejection sampling to ensure uniform distribution in a circle
        double x, z;
        do {
            x = arena.getCenter().getX() + (random.nextDouble() * 2 - 1) * radius;
            z = arena.getCenter().getZ() + (random.nextDouble() * 2 - 1) * radius;
        } while (Math.sqrt(Math.pow(x - arena.getCenter().getX(), 2) + Math.pow(z - arena.getCenter().getZ(), 2)) > radius);
        
        // Find surface Y coordinate
        Location surfaceLoc = findSurfaceLocation(world, x, z, arena.getCenter().getY());
        if (surfaceLoc == null) {
            return null; // Could not find valid surface
        }
        
        return surfaceLoc;
    }
    
    /**
     * Find a surface location at the given X/Z coordinates
     * Returns a location on solid ground with air above (not in a cave)
     * 
     * @param world The world to search in
     * @param x X coordinate
     * @param z Z coordinate
     * @param startY Starting Y coordinate to search from (typically arena center Y)
     * @return Surface location, or null if no valid surface found
     */
    private Location findSurfaceLocation(World world, double x, double z, double startY) {
        // Ensure chunk is loaded
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        org.bukkit.Chunk chunk = world.getChunkAt(blockX >> 4, blockZ >> 4);
        if (!chunk.isLoaded()) {
            chunk.load();
        }
        
        // Start searching from a reasonable height (arena center Y + some buffer)
        // Search down first to find the ground
        int searchY = (int) Math.floor(startY);
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        
        // First, try to find solid ground below the start Y
        Block block = world.getBlockAt(blockX, searchY, blockZ);
        Block below = searchY > minY ? world.getBlockAt(blockX, searchY - 1, blockZ) : null;
        
        // If we're in air, search down for solid ground
        if (block.getType() == Material.AIR && below != null && below.getType() != Material.AIR) {
            // Already on surface, but verify it's not in a cave
            if (isOnSurface(world, x, searchY, z)) {
                return new Location(world, x, searchY, z);
            }
        }
        
        // Search downward for solid ground
        for (int y = searchY; y >= minY + 5; y--) {
            block = world.getBlockAt(blockX, y, blockZ);
            Block blockAbove = y < maxY - 1 ? world.getBlockAt(blockX, y + 1, blockZ) : null;
            
            // Found solid ground with air above
            if (block.getType().isSolid() && blockAbove != null && blockAbove.getType() == Material.AIR) {
                // Verify it's on the surface (not in a cave)
                if (isOnSurface(world, x, y + 1, z)) {
                    return new Location(world, x, y + 1, z);
                }
            }
        }
        
        // If we didn't find anything below, try searching upward (in case we started too low)
        for (int y = searchY + 1; y <= Math.min(startY + 20, maxY - 5); y++) {
            block = world.getBlockAt(blockX, y, blockZ);
            Block blockBelow = y > minY ? world.getBlockAt(blockX, y - 1, blockZ) : null;
            
            // Found air with solid ground below
            if (block.getType() == Material.AIR && blockBelow != null && blockBelow.getType().isSolid()) {
                // Verify it's on the surface (not in a cave)
                if (isOnSurface(world, x, y, z)) {
                    return new Location(world, x, y, z);
                }
            }
        }
        
        // Could not find valid surface
        return null;
    }
    
    /**
     * Check if a location is on the surface (not in a cave)
     * Verifies that there's enough air above (at least 3 blocks) to ensure it's not underground
     * 
     * @param world The world
     * @param x X coordinate
     * @param y Y coordinate (should be the air block above ground)
     * @param z Z coordinate
     * @return true if on surface, false if in a cave
     */
    private boolean isOnSurface(World world, double x, int y, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        
        // Ensure chunk is loaded
        org.bukkit.Chunk chunk = world.getChunkAt(blockX >> 4, blockZ >> 4);
        if (!chunk.isLoaded()) {
            chunk.load();
        }
        
        int maxY = world.getMaxHeight();
        
        // Check that there's at least 10 blocks of air above (ensures it's on surface, not in cave)
        int airBlocks = 0;
        for (int checkY = y; checkY < Math.min(y + 12, maxY); checkY++) {
            Block checkBlock = world.getBlockAt(blockX, checkY, blockZ);
            if (checkBlock.getType() == Material.AIR) {
                airBlocks++;
            } else {
                break; // Hit a solid block, stop counting
            }
        }
        
        // Need at least 10 blocks of air above to be considered "on surface"
        return airBlocks >= 10;
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

