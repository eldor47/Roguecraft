package com.eldor.roguecraft.managers;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.Run;
import com.eldor.roguecraft.models.TeamRun;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class RunManager {
    private final RoguecraftPlugin plugin;
    private final Map<UUID, Run> activeRuns;
    private final Map<UUID, TeamRun> activeTeamRuns;
    private final Map<UUID, UUID> playerToTeamRun; // Maps player UUID to team run ID (uses first player's UUID as team ID)

    public RunManager(RoguecraftPlugin plugin) {
        this.plugin = plugin;
        this.activeRuns = new HashMap<>();
        this.activeTeamRuns = new HashMap<>();
        this.playerToTeamRun = new HashMap<>();
    }

    public Run startRun(Player player) {
        if (activeRuns.containsKey(player.getUniqueId()) || playerToTeamRun.containsKey(player.getUniqueId())) {
            return null; // Already has an active run
        }

        Run run = new Run(player);
        activeRuns.put(player.getUniqueId(), run);
        return run;
    }

    public TeamRun startTeamRun(Player player, com.eldor.roguecraft.models.Arena arena) {
        if (activeRuns.containsKey(player.getUniqueId()) || playerToTeamRun.containsKey(player.getUniqueId())) {
            return null; // Already has an active run
        }

        // Check if there's already a team run in this arena
        TeamRun existingTeam = findTeamRunInArena(arena);
        if (existingTeam != null) {
            // Join existing team
            existingTeam.addPlayer(player);
            playerToTeamRun.put(player.getUniqueId(), getTeamRunId(existingTeam));
            return existingTeam;
        }

        // Create new team run
        TeamRun teamRun = new TeamRun(player);
        UUID teamId = player.getUniqueId(); // Use first player's UUID as team ID
        activeTeamRuns.put(teamId, teamRun);
        playerToTeamRun.put(player.getUniqueId(), teamId);
        return teamRun;
    }

    private TeamRun findTeamRunInArena(com.eldor.roguecraft.models.Arena arena) {
        for (TeamRun teamRun : activeTeamRuns.values()) {
            if (!teamRun.isActive()) continue;
            
            // Check if any player in the team is in this arena
            for (Player p : teamRun.getPlayers()) {
                if (p != null && p.isOnline() && arena.isInArena(p.getLocation())) {
                    return teamRun;
                }
            }
        }
        return null;
    }

    private UUID getTeamRunId(TeamRun teamRun) {
        for (Map.Entry<UUID, TeamRun> entry : activeTeamRuns.entrySet()) {
            if (entry.getValue() == teamRun) {
                return entry.getKey();
            }
        }
        return null;
    }

    public Run getRun(Player player) {
        return activeRuns.get(player.getUniqueId());
    }

    public Run getRun(UUID playerId) {
        return activeRuns.get(playerId);
    }

    public TeamRun getTeamRun(Player player) {
        UUID teamId = playerToTeamRun.get(player.getUniqueId());
        if (teamId != null) {
            return activeTeamRuns.get(teamId);
        }
        return null;
    }

    public TeamRun getTeamRun(UUID playerId) {
        UUID teamId = playerToTeamRun.get(playerId);
        if (teamId != null) {
            return activeTeamRuns.get(teamId);
        }
        return null;
    }

    public void endRun(Player player) {
        Run run = activeRuns.remove(player.getUniqueId());
        if (run != null) {
            run.setActive(false);
        }
        
        // Also check team runs
        UUID teamId = playerToTeamRun.remove(player.getUniqueId());
        if (teamId != null) {
            TeamRun teamRun = activeTeamRuns.get(teamId);
            if (teamRun != null) {
                teamRun.removePlayer(player.getUniqueId());
                if (teamRun.getPlayerCount() == 0) {
                    teamRun.setActive(false);
                    activeTeamRuns.remove(teamId);
                }
            }
        }
    }

    public void endRun(UUID playerId) {
        Run run = activeRuns.remove(playerId);
        if (run != null) {
            run.setActive(false);
        }
        
        UUID teamId = playerToTeamRun.remove(playerId);
        if (teamId != null) {
            TeamRun teamRun = activeTeamRuns.get(teamId);
            if (teamRun != null) {
                teamRun.removePlayer(playerId);
                if (teamRun.getPlayerCount() == 0) {
                    teamRun.setActive(false);
                    activeTeamRuns.remove(teamId);
                }
            }
        }
    }

    public void endTeamRun(UUID teamId) {
        TeamRun teamRun = activeTeamRuns.remove(teamId);
        if (teamRun != null) {
            teamRun.setActive(false);
            for (UUID playerId : teamRun.getPlayerIds()) {
                playerToTeamRun.remove(playerId);
            }
        }
    }

    public boolean hasActiveRun(Player player) {
        return activeRuns.containsKey(player.getUniqueId()) || playerToTeamRun.containsKey(player.getUniqueId());
    }

    public Collection<Run> getAllActiveRuns() {
        return new ArrayList<>(activeRuns.values());
    }

    public Collection<TeamRun> getAllActiveTeamRuns() {
        return new ArrayList<>(activeTeamRuns.values());
    }

    public void stopAllRuns() {
        for (Run run : activeRuns.values()) {
            run.setActive(false);
        }
        activeRuns.clear();
        
        for (TeamRun teamRun : activeTeamRuns.values()) {
            teamRun.setActive(false);
        }
        activeTeamRuns.clear();
        playerToTeamRun.clear();
    }
}
