package com.eldor.roguecraft.util;

import com.eldor.roguecraft.models.Arena;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class ArenaVisualizer {
    private static final Map<UUID, BukkitTask> visualizationTasks = new HashMap<>();
    
    /**
     * Visualize arena using world border (per-player)
     */
    public static void showWorldBorder(Player player, Arena arena, org.bukkit.plugin.Plugin plugin) {
        if (arena.getCenter() == null) return;
        
        World world = arena.getCenter().getWorld();
        if (world == null) return;
        
        // Create a temporary world border centered on the arena
        WorldBorder border = player.getWorldBorder();
        border.setCenter(arena.getCenter());
        border.setSize(arena.getRadius() * 2);
        border.setWarningDistance(0);
        border.setDamageAmount(0);
        border.setDamageBuffer(0);
    }
    
    /**
     * Remove world border visualization
     */
    public static void hideWorldBorder(Player player) {
        // Reset to default world border
        WorldBorder border = player.getWorldBorder();
        World world = player.getWorld();
        if (world != null) {
            border.setCenter(world.getSpawnLocation());
            border.setSize(world.getWorldBorder().getSize());
        }
    }
    
    /**
     * Visualize arena using particle outline
     */
    public static void showParticleOutline(Arena arena, Collection<Player> players, org.bukkit.plugin.Plugin plugin) {
        if (arena.getCenter() == null || arena.getRadius() <= 0) return;
        
        Location center = arena.getCenter();
        World world = center.getWorld();
        if (world == null) return;
        
        // Cancel any existing visualization
        for (BukkitTask task : visualizationTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        visualizationTasks.clear();
        
        // Create particle outline task
        BukkitTask task = new BukkitRunnable() {
            private int tickCount = 0;
            private static final int DURATION_TICKS = 200; // 10 seconds
            
            @Override
            public void run() {
                if (tickCount++ >= DURATION_TICKS) {
                    cancel();
                    visualizationTasks.clear();
                    return;
                }
                
                // Draw circular outline at ground level
                double radius = arena.getRadius();
                double y = center.getY();
                int particlesPerCircle = (int) (radius * 4); // More particles for larger arenas
                
                for (int i = 0; i < particlesPerCircle; i++) {
                    double angle = (2 * Math.PI * i) / particlesPerCircle;
                    double x = center.getX() + radius * Math.cos(angle);
                    double z = center.getZ() + radius * Math.sin(angle);
                    
                    Location particleLoc = new Location(world, x, y, z);
                    
                    // Show particles to all players in the collection
                    for (Player player : players) {
                        if (player != null && player.isOnline() && player.getWorld() == world) {
                            player.spawnParticle(
                                org.bukkit.Particle.END_ROD,
                                particleLoc,
                                1,
                                0, 0, 0,
                                0
                            );
                        }
                    }
                }
                
                // Also draw vertical lines at corners for better visibility
                if (tickCount % 20 == 0) { // Every second
                    for (int corner = 0; corner < 4; corner++) {
                        double angle = (Math.PI / 2) * corner;
                        double x = center.getX() + radius * Math.cos(angle);
                        double z = center.getZ() + radius * Math.sin(angle);
                        
                        // Draw vertical line from ground up
                        for (double yOffset = 0; yOffset < 10; yOffset += 0.5) {
                            Location particleLoc = new Location(world, x, y + yOffset, z);
                            for (Player player : players) {
                                if (player != null && player.isOnline() && player.getWorld() == world) {
                            player.spawnParticle(
                                org.bukkit.Particle.END_ROD,
                                particleLoc,
                                1,
                                0, 0, 0,
                                0
                            );
                                }
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L); // Every 5 ticks (0.25 seconds)
        
        // Store task for each player
        for (Player player : players) {
            if (player != null) {
                visualizationTasks.put(player.getUniqueId(), task);
            }
        }
    }
    
    /**
     * Stop particle visualization
     */
    public static void hideParticleOutline(Collection<Player> players) {
        for (Player player : players) {
            if (player != null) {
                BukkitTask task = visualizationTasks.remove(player.getUniqueId());
                if (task != null) {
                    task.cancel();
                }
            }
        }
    }
    
    /**
     * Stop all visualizations
     */
    public static void stopAllVisualizations() {
        for (BukkitTask task : visualizationTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        visualizationTasks.clear();
    }
}
