package com.eldor.roguecraft.util;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Utility class for WorldGuard integration
 * Handles bypassing WorldGuard flags for plugin-spawned entities
 */
public class WorldGuardUtil {
    private static boolean worldGuardAvailable = false;
    
    /**
     * Initialize WorldGuard integration
     */
    public static void initialize() {
        worldGuardAvailable = DepCheck.has("WorldGuard");
    }
    
    /**
     * Check if WorldGuard is available
     */
    public static boolean isAvailable() {
        return worldGuardAvailable;
    }
}

