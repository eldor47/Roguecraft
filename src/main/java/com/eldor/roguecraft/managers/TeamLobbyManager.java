package com.eldor.roguecraft.managers;

import com.eldor.roguecraft.RoguecraftPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Manages team lobbies - players can invite others and form teams before starting a run
 */
public class TeamLobbyManager {
    private final RoguecraftPlugin plugin;
    
    // Map of player UUID -> pending invite from UUID
    private final Map<UUID, UUID> pendingInvites; // invited player -> inviter player
    
    // Map of team leader UUID -> TeamLobby
    private final Map<UUID, TeamLobby> teamLobbies;
    
    public TeamLobbyManager(RoguecraftPlugin plugin) {
        this.plugin = plugin;
        this.pendingInvites = new HashMap<>();
        this.teamLobbies = new HashMap<>();
    }
    
    /**
     * Create a new team lobby (player becomes leader)
     */
    public TeamLobby createLobby(Player leader) {
        // Check if player is already in a lobby
        if (isInLobby(leader)) {
            return getLobby(leader);
        }
        
        TeamLobby lobby = new TeamLobby(leader);
        teamLobbies.put(leader.getUniqueId(), lobby);
        leader.sendMessage(ChatColor.GREEN + "Team lobby created! Use /rc invite <player> to invite others.");
        return lobby;
    }
    
    /**
     * Invite a player to the team
     */
    public boolean invitePlayer(Player inviter, Player target) {
        TeamLobby lobby = getLobby(inviter);
        if (lobby == null) {
            inviter.sendMessage(ChatColor.RED + "You don't have a team lobby! Use /rc team create to create one.");
            return false;
        }
        
        if (!lobby.isLeader(inviter)) {
            inviter.sendMessage(ChatColor.RED + "Only the team leader can invite players!");
            return false;
        }
        
        if (lobby.hasPlayer(target)) {
            inviter.sendMessage(ChatColor.RED + target.getName() + " is already in your team!");
            return false;
        }
        
        if (plugin.getRunManager().hasActiveRun(target)) {
            inviter.sendMessage(ChatColor.RED + target.getName() + " is already in a run!");
            return false;
        }
        
        if (isInLobby(target)) {
            inviter.sendMessage(ChatColor.RED + target.getName() + " is already in another team lobby!");
            return false;
        }
        
        // Send invite
        pendingInvites.put(target.getUniqueId(), inviter.getUniqueId());
        inviter.sendMessage(ChatColor.GREEN + "Invited " + target.getName() + " to your team!");
        target.sendMessage(ChatColor.GOLD + inviter.getName() + " invited you to join their team!");
        target.sendMessage(ChatColor.YELLOW + "Use /rc accept to join or /rc decline to decline.");
        
        // Expire invite after 60 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingInvites.remove(target.getUniqueId()) != null) {
                target.sendMessage(ChatColor.GRAY + "Your team invite from " + inviter.getName() + " has expired.");
            }
        }, 1200L); // 60 seconds
        
        return true;
    }
    
    /**
     * Accept a pending invite
     */
    public boolean acceptInvite(Player player) {
        UUID inviterId = pendingInvites.remove(player.getUniqueId());
        if (inviterId == null) {
            player.sendMessage(ChatColor.RED + "You don't have any pending invites!");
            return false;
        }
        
        Player inviter = Bukkit.getPlayer(inviterId);
        if (inviter == null || !inviter.isOnline()) {
            player.sendMessage(ChatColor.RED + "The player who invited you is no longer online!");
            return false;
        }
        
        TeamLobby lobby = getLobby(inviter);
        if (lobby == null) {
            player.sendMessage(ChatColor.RED + "The team lobby no longer exists!");
            return false;
        }
        
        lobby.addPlayer(player);
        player.sendMessage(ChatColor.GREEN + "You joined " + inviter.getName() + "'s team!");
        lobby.broadcast(ChatColor.GREEN + player.getName() + " joined the team! (" + lobby.getPlayerCount() + "/4 players)");
        return true;
    }
    
    /**
     * Decline a pending invite
     */
    public boolean declineInvite(Player player) {
        UUID inviterId = pendingInvites.remove(player.getUniqueId());
        if (inviterId == null) {
            player.sendMessage(ChatColor.RED + "You don't have any pending invites!");
            return false;
        }
        
        Player inviter = Bukkit.getPlayer(inviterId);
        if (inviter != null && inviter.isOnline()) {
            inviter.sendMessage(ChatColor.YELLOW + player.getName() + " declined your team invite.");
        }
        
        player.sendMessage(ChatColor.GRAY + "You declined the team invite.");
        return true;
    }
    
    /**
     * Leave a team lobby
     */
    public boolean leaveLobby(Player player) {
        TeamLobby lobby = getLobby(player);
        if (lobby == null) {
            player.sendMessage(ChatColor.RED + "You're not in a team lobby!");
            return false;
        }
        
        if (lobby.isLeader(player)) {
            // Leader leaving - disband team or transfer leadership
            if (lobby.getPlayerCount() == 1) {
                // Only leader, disband
                teamLobbies.remove(player.getUniqueId());
                player.sendMessage(ChatColor.YELLOW + "Team lobby disbanded.");
            } else {
                // Transfer leadership to next player
                Player newLeader = lobby.getNextLeader();
                if (newLeader != null) {
                    teamLobbies.remove(player.getUniqueId());
                    teamLobbies.put(newLeader.getUniqueId(), lobby);
                    lobby.setLeader(newLeader);
                    lobby.removePlayer(player);
                    lobby.broadcast(ChatColor.YELLOW + player.getName() + " left the team. " + newLeader.getName() + " is now the leader.");
                    player.sendMessage(ChatColor.YELLOW + "You left the team.");
                }
            }
        } else {
            lobby.removePlayer(player);
            lobby.broadcast(ChatColor.YELLOW + player.getName() + " left the team.");
            player.sendMessage(ChatColor.YELLOW + "You left the team.");
        }
        
        return true;
    }
    
    /**
     * Toggle ready status for a player
     */
    public boolean toggleReady(Player player) {
        TeamLobby lobby = getLobby(player);
        if (lobby == null) {
            player.sendMessage(ChatColor.RED + "You're not in a team lobby!");
            return false;
        }
        
        boolean wasReady = lobby.isReady(player);
        lobby.setReady(player, !wasReady);
        
        if (!wasReady) {
            lobby.broadcast(ChatColor.GREEN + player.getName() + " is now ready!");
        } else {
            lobby.broadcast(ChatColor.YELLOW + player.getName() + " is no longer ready.");
        }
        
        // Check if all players are ready
        if (lobby.allReady()) {
            lobby.broadcast(ChatColor.GREEN + "All players are ready! Use /rc start to begin the run.");
        }
        
        return true;
    }
    
    /**
     * Get the lobby a player is in
     */
    public TeamLobby getLobby(Player player) {
        // Check if player is a leader
        TeamLobby lobby = teamLobbies.get(player.getUniqueId());
        if (lobby != null) {
            return lobby;
        }
        
        // Check if player is a member
        for (TeamLobby l : teamLobbies.values()) {
            if (l.hasPlayer(player)) {
                return l;
            }
        }
        
        return null;
    }
    
    /**
     * Check if player is in a lobby
     */
    public boolean isInLobby(Player player) {
        return getLobby(player) != null;
    }
    
    /**
     * Remove a lobby (when starting a run)
     */
    public void removeLobby(TeamLobby lobby) {
        if (lobby != null && lobby.getLeader() != null) {
            teamLobbies.remove(lobby.getLeader().getUniqueId());
        }
    }
    
    /**
     * Clean up invites when player disconnects
     */
    public void onPlayerQuit(Player player) {
        // Remove invites from this player
        pendingInvites.entrySet().removeIf(entry -> entry.getValue().equals(player.getUniqueId()));
        
        // Remove invites to this player
        pendingInvites.remove(player.getUniqueId());
        
        // Remove from lobby
        leaveLobby(player);
    }
    
    /**
     * Team lobby class
     */
    public static class TeamLobby {
        private Player leader;
        private final Set<UUID> playerIds;
        private final List<Player> players;
        private final Set<UUID> readyPlayers;
        private final int maxPlayers = 4;
        
        public TeamLobby(Player leader) {
            this.leader = leader;
            this.playerIds = new HashSet<>();
            this.players = new ArrayList<>();
            this.readyPlayers = new HashSet<>();
            this.playerIds.add(leader.getUniqueId());
            this.players.add(leader);
        }
        
        public Player getLeader() {
            return leader;
        }
        
        public void setLeader(Player newLeader) {
            this.leader = newLeader;
        }
        
        public boolean isLeader(Player player) {
            return leader != null && leader.getUniqueId().equals(player.getUniqueId());
        }
        
        public void addPlayer(Player player) {
            if (!playerIds.contains(player.getUniqueId())) {
                playerIds.add(player.getUniqueId());
                players.add(player);
            }
        }
        
        public void removePlayer(Player player) {
            playerIds.remove(player.getUniqueId());
            players.removeIf(p -> p.getUniqueId().equals(player.getUniqueId()));
            readyPlayers.remove(player.getUniqueId());
        }
        
        public boolean hasPlayer(Player player) {
            return playerIds.contains(player.getUniqueId());
        }
        
        public int getPlayerCount() {
            return playerIds.size();
        }
        
        public List<Player> getPlayers() {
            return new ArrayList<>(players);
        }
        
        public Set<UUID> getPlayerIds() {
            return new HashSet<>(playerIds);
        }
        
        public boolean isReady(Player player) {
            return readyPlayers.contains(player.getUniqueId());
        }
        
        public void setReady(Player player, boolean ready) {
            if (ready) {
                readyPlayers.add(player.getUniqueId());
            } else {
                readyPlayers.remove(player.getUniqueId());
            }
        }
        
        public boolean allReady() {
            if (players.isEmpty()) return false;
            // All players must be ready
            for (Player p : players) {
                if (p == null || !p.isOnline()) continue;
                if (!readyPlayers.contains(p.getUniqueId())) {
                    return false;
                }
            }
            return true;
        }
        
        public Player getNextLeader() {
            for (Player p : players) {
                if (p != null && p.isOnline() && !p.getUniqueId().equals(leader.getUniqueId())) {
                    return p;
                }
            }
            return null;
        }
        
        public void broadcast(String message) {
            for (Player p : players) {
                if (p != null && p.isOnline()) {
                    p.sendMessage(message);
                }
            }
        }
        
        public boolean isFull() {
            return getPlayerCount() >= maxPlayers;
        }
    }
}

