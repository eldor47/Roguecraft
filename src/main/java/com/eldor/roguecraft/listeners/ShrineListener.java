package com.eldor.roguecraft.listeners;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.Shrine;
import com.eldor.roguecraft.models.TeamRun;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Handles shrine interactions
 */
public class ShrineListener implements Listener {
    private final RoguecraftPlugin plugin;
    
    public ShrineListener(RoguecraftPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check if player actually moved blocks (not just looked around)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return; // Player didn't move, just rotated
        }
        
        Player player = event.getPlayer();
        
        // Check if player is in a run
        TeamRun teamRun = plugin.getRunManager().getTeamRun(player.getUniqueId());
        if (teamRun == null || !teamRun.isActive()) return;
        
        // Get team ID (use first player's UUID)
        UUID teamId = getTeamRunId(teamRun);
        if (teamId == null) return;
        
        // Check if player is already channeling - don't try to start again
        if (plugin.getShrineManager().isPlayerChanneling(player.getUniqueId())) {
            // Player is channeling, just check if they're still near
            return; // Let the channeling task handle distance checks
        }
        
        // Check if there's a shrine near the player
        Shrine shrine = plugin.getShrineManager().getShrineNearPlayer(teamId, player);
        
        if (shrine != null) {
            // Only start channeling if not on cooldown and not already channeling
            if (plugin.getShrineManager().canPlayerUseShrine(player.getUniqueId(), shrine.getType())) {
                plugin.getShrineManager().startChanneling(player, shrine, teamId);
            }
        } else {
            // Cancel channeling if they moved away (only if actually channeling)
            if (plugin.getShrineManager().isPlayerChanneling(player.getUniqueId())) {
                plugin.getShrineManager().cancelChanneling(player);
            }
        }
    }
    
    /**
     * Get team ID from TeamRun (uses first player's UUID)
     */
    private UUID getTeamRunId(TeamRun teamRun) {
        if (teamRun == null || teamRun.getPlayers().isEmpty()) {
            return null;
        }
        return teamRun.getPlayers().get(0).getUniqueId();
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Cancel any active channeling
        plugin.getShrineManager().cancelChanneling(event.getPlayer());
    }
}

