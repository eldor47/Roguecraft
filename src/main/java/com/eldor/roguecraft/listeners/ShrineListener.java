package com.eldor.roguecraft.listeners;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.Shrine;
import com.eldor.roguecraft.models.TeamRun;
import com.eldor.roguecraft.models.Run;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
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
        
        // Check if player is in a run (team or solo)
        TeamRun teamRun = plugin.getRunManager().getTeamRun(player.getUniqueId());
        Run run = null;
        boolean inRun = false;
        UUID teamId = null;
        
        if (teamRun != null && teamRun.isActive()) {
            inRun = true;
            // Get team ID (use first player's UUID)
            teamId = getTeamRunId(teamRun);
            if (teamId == null) return;
        } else {
            run = plugin.getRunManager().getRun(player.getUniqueId());
            if (run != null && run.isActive()) {
                inRun = true;
                teamId = run.getPlayerId();
            }
        }
        
        if (!inRun || teamId == null) {
            return;
        }
        
        // Check if player is already channeling - don't try to start again
        if (plugin.getShrineManager().isPlayerChanneling(player.getUniqueId())) {
            // Player is channeling, just check if they're still near
            return; // Let the channeling task handle distance checks
        }
        
        // Check if there's a shrine near the player
        Shrine shrine = plugin.getShrineManager().getShrineNearPlayer(teamId, player);
        
        if (shrine != null) {
            // Only start channeling for POWER shrines (DIFFICULTY and BOSS shrines use right-click)
            if (shrine.getType() == Shrine.ShrineType.POWER) {
                // Only start channeling if not on cooldown and not already channeling
                if (plugin.getShrineManager().canPlayerUseShrine(player.getUniqueId(), shrine.getType())) {
                    plugin.getShrineManager().startChanneling(player, shrine, teamId);
                }
            }
            // For DIFFICULTY and BOSS shrines, do nothing on move - they use right-click only
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
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        if (event.getClickedBlock() == null) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Check if player is in a run
        TeamRun teamRun = plugin.getRunManager().getTeamRun(player.getUniqueId());
        Run run = null;
        boolean inRun = false;
        
        if (teamRun != null && teamRun.isActive()) {
            inRun = true;
        } else {
            run = plugin.getRunManager().getRun(player.getUniqueId());
            if (run != null && run.isActive()) {
                inRun = true;
            }
        }
        
        if (!inRun) {
            return;
        }
        
        // Get team ID
        UUID teamId = null;
        if (teamRun != null) {
            teamId = getTeamRunId(teamRun);
        } else if (run != null) {
            teamId = run.getPlayerId();
        }
        
        if (teamId == null) {
            return;
        }
        
        // Check if clicked block is part of a shrine
        Shrine shrine = plugin.getShrineManager().getShrineAtLocation(teamId, event.getClickedBlock().getLocation());
        if (shrine == null) {
            return;
        }
        
        // Only handle DIFFICULTY and BOSS shrines (POWER shrines use channeling)
        if (shrine.getType() == Shrine.ShrineType.POWER) {
            return; // Let the channeling system handle it
        }
        
        // For difficulty shrines, check if they clicked the block or the skull
        if (shrine.getType() == Shrine.ShrineType.DIFFICULTY) {
            // Check if clicked block is the shrine block (dark block) or skull block
            Location shrineLoc = shrine.getLocation();
            Location clickedLoc = event.getClickedBlock().getLocation();
            int dx = clickedLoc.getBlockX() - shrineLoc.getBlockX();
            int dy = clickedLoc.getBlockY() - shrineLoc.getBlockY();
            int dz = clickedLoc.getBlockZ() - shrineLoc.getBlockZ();
            
            // Check if clicked the dark block (y=0) or skull block (y=1)
            if (dx == 0 && dz == 0 && (dy == 0 || dy == 1)) {
                // They clicked the block or skull, handle it
                if (shrine.hasBeenUsed()) {
                    player.sendMessage(ChatColor.RED + "This shrine has already been used!");
                    return;
                }
                event.setCancelled(true);
                handleDifficultyShrine(player, shrine, teamRun, run);
            }
            return; // Don't handle if they didn't click the block or skull
        }
        
        // Check if shrine has been used
        if (shrine.hasBeenUsed()) {
            player.sendMessage(ChatColor.RED + "This shrine has already been used!");
            return;
        }
        
        event.setCancelled(true);
        
        // Handle BOSS shrine
        if (shrine.getType() == Shrine.ShrineType.BOSS) {
            handleBossShrine(player, shrine, teamRun, run);
        }
    }
    
    
    private void handleDifficultyShrine(Player player, Shrine shrine, TeamRun teamRun, Run run) {
        // Increase difficulty by 5%
        if (teamRun != null) {
            double currentDifficulty = teamRun.getStat("difficulty");
            double newDifficulty = currentDifficulty * 1.05; // 5% increase
            teamRun.setStat("difficulty", newDifficulty);
            
            // Notify all players in team
            for (Player p : teamRun.getPlayers()) {
                if (p != null && p.isOnline()) {
                    p.sendMessage(ChatColor.RED + "⚔ " + ChatColor.BOLD + "Difficulty increased by 5%!" + ChatColor.RESET + ChatColor.GRAY + " (Now: " + String.format("%.2fx", newDifficulty) + ")");
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.8f);
                }
            }
        } else if (run != null) {
            double currentDifficulty = run.getStat("difficulty");
            double newDifficulty = currentDifficulty * 1.05; // 5% increase
            run.setStat("difficulty", newDifficulty);
            
            player.sendMessage(ChatColor.RED + "⚔ " + ChatColor.BOLD + "Difficulty increased by 5%!" + ChatColor.RESET + ChatColor.GRAY + " (Now: " + String.format("%.2fx", newDifficulty) + ")");
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.8f);
        }
        
        // Mark shrine as used
        shrine.markAsUsed();
        
        // Visual effects
        org.bukkit.Location loc = shrine.getLocation();
        loc.getWorld().spawnParticle(org.bukkit.Particle.DUST, loc.add(0, 2, 0), 30, 0.5, 0.5, 0.5, 0.1, new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.0f));
        loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
    }
    
    private void handleBossShrine(Player player, Shrine shrine, TeamRun teamRun, Run run) {
        // Mark this boss shrine as clicked (will spawn extra boss during wave 20)
        if (teamRun != null) {
            teamRun.markBossShrineClicked(shrine.getId());
            
            // Notify all players in team
            for (Player p : teamRun.getPlayers()) {
                if (p != null && p.isOnline()) {
                    p.sendMessage(ChatColor.DARK_PURPLE + "☠ " + ChatColor.BOLD + "Boss Shrine Activated!" + ChatColor.RESET + ChatColor.GRAY + " An additional boss will spawn during Wave 20!");
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.8f);
                }
            }
        } else if (run != null) {
            run.markBossShrineClicked(shrine.getId());
            
            player.sendMessage(ChatColor.DARK_PURPLE + "☠ " + ChatColor.BOLD + "Boss Shrine Activated!" + ChatColor.RESET + ChatColor.GRAY + " An additional boss will spawn during Wave 20!");
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.8f);
        }
        
        // Mark shrine as used (prevents multiple clicks)
        shrine.markAsUsed();
        
        // Visual effects
        org.bukkit.Location loc = shrine.getLocation();
        loc.getWorld().spawnParticle(org.bukkit.Particle.DUST, loc.add(0, 2, 0), 30, 0.5, 0.5, 0.5, 0.1, new org.bukkit.Particle.DustOptions(org.bukkit.Color.PURPLE, 1.0f));
        loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
    }
}

