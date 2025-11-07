package com.eldor.roguecraft.util;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manager for displaying XP progress using boss bars
 */
public class XPBar {
    private static final Map<UUID, BossBar> activeBars = new HashMap<>();
    
    /**
     * Create or update XP bar for a player
     */
    public static void updateXPBar(Player player, int currentXP, int requiredXP, int level) {
        updateXPBar(player, currentXP, requiredXP, level, 0);
    }
    
    /**
     * Create or update XP bar for a player with wave number
     */
    public static void updateXPBar(Player player, int currentXP, int requiredXP, int level, int wave) {
        BossBar bar = activeBars.get(player.getUniqueId());
        
        if (bar == null) {
            String title = wave > 0 ? 
                "Wave " + wave + " | Level " + level + " | XP: " + currentXP + " / " + requiredXP :
                "Level " + level + " | XP: " + currentXP + " / " + requiredXP;
            bar = Bukkit.createBossBar(
                title,
                BarColor.GREEN,
                BarStyle.SEGMENTED_10
            );
            bar.addPlayer(player);
            activeBars.put(player.getUniqueId(), bar);
        }
        
        // Update bar
        double progress = Math.min(1.0, (double) currentXP / requiredXP);
        bar.setProgress(progress);
        String title = wave > 0 ?
            "§bWave §f" + wave + " §7| §6Level " + level + " §7| §eXP: §f" + currentXP + " §7/ §f" + requiredXP :
            "§6Level " + level + " §7| §eXP: §f" + currentXP + " §7/ §f" + requiredXP;
        bar.setTitle(title);
        
        // Change color based on progress
        if (progress < 0.33) {
            bar.setColor(BarColor.RED);
        } else if (progress < 0.66) {
            bar.setColor(BarColor.YELLOW);
        } else {
            bar.setColor(BarColor.GREEN);
        }
    }
    
    /**
     * Remove XP bar for a player
     */
    public static void removeXPBar(Player player) {
        BossBar bar = activeBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removePlayer(player);
            bar.removeAll();
        }
    }
    
    /**
     * Remove all XP bars
     */
    public static void removeAllBars() {
        for (BossBar bar : activeBars.values()) {
            if (bar != null) {
                bar.removeAll();
            }
        }
        activeBars.clear();
    }
    
    /**
     * Flash XP bar for level up
     */
    public static void flashLevelUp(Player player, int newLevel) {
        BossBar bar = activeBars.get(player.getUniqueId());
        if (bar != null) {
            // Temporarily change to gold/legendary color
            bar.setColor(BarColor.YELLOW);
            bar.setTitle("§6§l★ LEVEL UP! ★ §eLevel " + newLevel);
            
            // Reset after 2 seconds
            Bukkit.getScheduler().runTaskLater(
                org.bukkit.Bukkit.getPluginManager().getPlugin("Roguecraft"),
                () -> bar.setColor(BarColor.RED),
                40L
            );
        }
    }
}

