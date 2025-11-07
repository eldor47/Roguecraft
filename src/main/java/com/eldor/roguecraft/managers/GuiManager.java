package com.eldor.roguecraft.managers;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.gui.PowerUpGUI;
import com.eldor.roguecraft.gui.ShrineGUI;
import com.eldor.roguecraft.models.Shrine;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GuiManager {
    private final RoguecraftPlugin plugin;
    private final Map<UUID, LinkedList<PendingGUI>> guiQueue; // Player -> Queue of pending GUIs
    private final Set<UUID> playersInGUI; // Players currently in ANY GUI

    public GuiManager(RoguecraftPlugin plugin) {
        this.plugin = plugin;
        this.guiQueue = new ConcurrentHashMap<>();
        this.playersInGUI = ConcurrentHashMap.newKeySet();
    }

    public void openPowerUpGUI(Player player, com.eldor.roguecraft.models.Run run) {
        UUID playerId = player.getUniqueId();
        
        // Check if player is in a GUI (including shrine GUI)
        if (playersInGUI.contains(playerId) || plugin.getShrineManager().isPlayerInShrineGUI(playerId)) {
            // Queue the power-up GUI
            queueGUI(player, new PendingGUI(GUIType.POWERUP_SOLO, run, null, null, null));
            player.sendMessage(ChatColor.YELLOW + "⏳ Power-up selection queued (shrine GUI active)");
            return;
        }
        
        // Mark player as in GUI first (so freeze check works)
        playersInGUI.add(playerId);
        
        // Freeze mobs IMMEDIATELY before opening GUI (removes projectiles and freezes mobs)
        // For solo runs, we need to check if they're in a team run context
        com.eldor.roguecraft.models.TeamRun teamRun = plugin.getRunManager().getTeamRun(player);
        if (teamRun != null) {
            teamRun.setPlayerInGUI(playerId, true);
            plugin.getGameManager().updateMobFreeze(teamRun);
        }
        
        new PowerUpGUI(plugin, player, run).open();
    }

    public void openPowerUpGUI(Player player, com.eldor.roguecraft.models.TeamRun teamRun) {
        UUID playerId = player.getUniqueId();
        
        // Check if player is in a GUI (including shrine GUI)
        if (playersInGUI.contains(playerId) || plugin.getShrineManager().isPlayerInShrineGUI(playerId)) {
            // Queue the power-up GUI
            queueGUI(player, new PendingGUI(GUIType.POWERUP_TEAM, null, teamRun, null, null));
            player.sendMessage(ChatColor.YELLOW + "⏳ Power-up selection queued (shrine GUI active)");
            return;
        }
        
        // Mark player as in GUI first (so freeze check works)
        playersInGUI.add(playerId);
        teamRun.setPlayerInGUI(playerId, true);
        
        // Freeze mobs IMMEDIATELY before opening GUI (removes projectiles and freezes mobs)
        plugin.getGameManager().updateMobFreeze(teamRun);
        
        new PowerUpGUI(plugin, player, teamRun).open();
    }

    public void openShrineGUI(Player player, Shrine shrine, UUID teamId) {
        UUID playerId = player.getUniqueId();
        
        // Check if player is in a GUI (should not happen due to channeling, but safety check)
        if (playersInGUI.contains(playerId)) {
            plugin.getLogger().warning("[GuiManager] Player " + player.getName() + " is already in GUI, queueing shrine GUI instead.");
            // Queue the shrine GUI
            queueGUI(player, new PendingGUI(GUIType.SHRINE, null, null, shrine, teamId));
            player.sendMessage(ChatColor.YELLOW + "⏳ Shrine selection queued (GUI active)");
            return;
        }
        
        // Mark player as in GUI first (so freeze check works)
        playersInGUI.add(playerId);
        
        // Freeze mobs IMMEDIATELY before opening GUI (removes projectiles and freezes mobs)
        if (teamId != null) {
            com.eldor.roguecraft.models.TeamRun teamRun = plugin.getRunManager().getTeamRun(teamId);
            if (teamRun != null) {
                teamRun.setPlayerInGUI(playerId, true);
                plugin.getGameManager().updateMobFreeze(teamRun);
            }
        }
        
        plugin.getLogger().info("[GuiManager] Opening shrine GUI for " + player.getName() + ". playersInGUI now contains: " + playersInGUI.contains(playerId));
        new ShrineGUI(plugin, player, shrine, teamId).open();
    }
    
    /**
     * Queue a GUI for later opening
     */
    private void queueGUI(Player player, PendingGUI gui) {
        guiQueue.computeIfAbsent(player.getUniqueId(), k -> new LinkedList<>()).add(gui);
    }
    
    /**
     * Called when a player closes any GUI - processes queued GUIs
     */
    public void onGUIClosed(Player player) {
        UUID playerId = player.getUniqueId();
        boolean removed = playersInGUI.remove(playerId);
        plugin.getLogger().info("[GuiManager] Player " + player.getName() + " closed GUI. Removed from playersInGUI: " + removed + ". Set now contains: " + playersInGUI.contains(playerId));
        
        // Check if there are queued GUIs
        LinkedList<PendingGUI> queue = guiQueue.get(playerId);
        if (queue != null && !queue.isEmpty()) {
            PendingGUI nextGUI = queue.poll();
            
            // Small delay to ensure current GUI is fully closed
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    openQueuedGUI(player, nextGUI);
                }
            }, 5L); // 5 ticks = 0.25 seconds
        }
    }
    
    /**
     * Open a queued GUI
     */
    private void openQueuedGUI(Player player, PendingGUI gui) {
        UUID playerId = player.getUniqueId();
        playersInGUI.add(playerId);
        
        // Freeze mobs IMMEDIATELY before opening GUI (removes projectiles and freezes mobs)
        switch (gui.type) {
            case POWERUP_SOLO:
                com.eldor.roguecraft.models.TeamRun teamRun = plugin.getRunManager().getTeamRun(player);
                if (teamRun != null) {
                    teamRun.setPlayerInGUI(playerId, true);
                    plugin.getGameManager().updateMobFreeze(teamRun);
                }
                new PowerUpGUI(plugin, player, gui.run).open();
                break;
            case POWERUP_TEAM:
                gui.teamRun.setPlayerInGUI(playerId, true);
                plugin.getGameManager().updateMobFreeze(gui.teamRun);
                new PowerUpGUI(plugin, player, gui.teamRun).open();
                break;
            case SHRINE:
                if (gui.teamId != null) {
                    com.eldor.roguecraft.models.TeamRun shrineTeamRun = plugin.getRunManager().getTeamRun(gui.teamId);
                    if (shrineTeamRun != null) {
                        shrineTeamRun.setPlayerInGUI(playerId, true);
                        plugin.getGameManager().updateMobFreeze(shrineTeamRun);
                    }
                }
                new ShrineGUI(plugin, player, gui.shrine, gui.teamId).open();
                break;
        }
    }
    
    /**
     * Check if player is currently in any GUI
     */
    public boolean isPlayerInGUI(UUID playerId) {
        return playersInGUI.contains(playerId);
    }
    
    /**
     * Clear all queued GUIs for a player (e.g., when run ends)
     */
    public void clearQueue(UUID playerId) {
        guiQueue.remove(playerId);
        playersInGUI.remove(playerId);
    }
    
    /**
     * Helper class to store pending GUI information
     */
    private static class PendingGUI {
        final GUIType type;
        final com.eldor.roguecraft.models.Run run;
        final com.eldor.roguecraft.models.TeamRun teamRun;
        final Shrine shrine;
        final UUID teamId;
        
        PendingGUI(GUIType type, com.eldor.roguecraft.models.Run run, 
                   com.eldor.roguecraft.models.TeamRun teamRun, Shrine shrine, UUID teamId) {
            this.type = type;
            this.run = run;
            this.teamRun = teamRun;
            this.shrine = shrine;
            this.teamId = teamId;
        }
    }
    
    private enum GUIType {
        POWERUP_SOLO,
        POWERUP_TEAM,
        SHRINE
    }
}
