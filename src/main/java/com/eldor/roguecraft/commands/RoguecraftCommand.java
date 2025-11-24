package com.eldor.roguecraft.commands;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.Arena;
import com.eldor.roguecraft.models.Run;
import com.eldor.roguecraft.models.Weapon;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RoguecraftCommand implements CommandExecutor, TabCompleter {
    private final RoguecraftPlugin plugin;

    public RoguecraftCommand(RoguecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // Open GUI or start run
            if (plugin.getRunManager().hasActiveRun(player)) {
                player.sendMessage("§cYou already have an active run! Use /rc stop to end it.");
            } else {
                plugin.getGameManager().startRun(player, null);
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start":
                if (!player.hasPermission("roguecraft.play")) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                
                if (plugin.getRunManager().hasActiveRun(player)) {
                    player.sendMessage("§cYou already have an active run!");
                } else {
                    // Check if player is in a team lobby
                    com.eldor.roguecraft.managers.TeamLobbyManager.TeamLobby lobby = plugin.getTeamLobbyManager().getLobby(player);
                    if (lobby != null) {
                        // Team start - check if all ready
                        if (!lobby.allReady()) {
                            player.sendMessage("§cNot all players are ready! Use /rc ready to mark yourself as ready.");
                            lobby.broadcast("§e" + player.getName() + " tried to start, but not all players are ready.");
                            return true;
                        }
                        
                        // Start team run with all lobby members
                        Arena arena = null;
                        if (args.length > 1) {
                            arena = plugin.getArenaManager().getArena(args[1]);
                            if (arena == null) {
                                player.sendMessage("§cArena not found: " + args[1]);
                                return true;
                            }
                        }
                        
                        // Start run for all team members
                        Player firstPlayer = lobby.getPlayers().get(0);
                        plugin.getGameManager().startRun(firstPlayer, arena);
                        
                        // Add other players to the team
                        for (Player teamPlayer : lobby.getPlayers()) {
                            if (teamPlayer != null && teamPlayer.isOnline() && !teamPlayer.getUniqueId().equals(firstPlayer.getUniqueId())) {
                                // They will join the existing team run
                                plugin.getGameManager().startRun(teamPlayer, arena);
                            }
                        }
                        
                        // Remove lobby
                        plugin.getTeamLobbyManager().removeLobby(lobby);
                    } else {
                        // Solo start
                        Arena arena = null;
                        if (args.length > 1) {
                            arena = plugin.getArenaManager().getArena(args[1]);
                            if (arena == null) {
                                player.sendMessage("§cArena not found: " + args[1]);
                                return true;
                            }
                        }
                        plugin.getGameManager().startRun(player, arena);
                    }
                }
                break;
            
            case "invite":
                if (!player.hasPermission("roguecraft.play")) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /rc invite <player>");
                    return true;
                }
                
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage("§cPlayer not found: " + args[1]);
                    return true;
                }
                
                // Create lobby if doesn't exist
                com.eldor.roguecraft.managers.TeamLobbyManager.TeamLobby lobby = plugin.getTeamLobbyManager().getLobby(player);
                if (lobby == null) {
                    lobby = plugin.getTeamLobbyManager().createLobby(player);
                }
                
                plugin.getTeamLobbyManager().invitePlayer(player, target);
                break;
            
            case "accept":
                if (!player.hasPermission("roguecraft.play")) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                
                plugin.getTeamLobbyManager().acceptInvite(player);
                break;
            
            case "decline":
                if (!player.hasPermission("roguecraft.play")) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                
                plugin.getTeamLobbyManager().declineInvite(player);
                break;
            
            case "leave":
                if (!player.hasPermission("roguecraft.play")) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                
                plugin.getTeamLobbyManager().leaveLobby(player);
                break;
            
            case "ready":
                if (!player.hasPermission("roguecraft.play")) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                
                plugin.getTeamLobbyManager().toggleReady(player);
                break;
            
            case "team":
                if (!player.hasPermission("roguecraft.play")) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                
                if (args.length < 2) {
                    // Show team status
                    lobby = plugin.getTeamLobbyManager().getLobby(player);
                    if (lobby == null) {
                        player.sendMessage("§cYou're not in a team! Use /rc invite <player> to create a team.");
                    } else {
                        player.sendMessage("§6=== Team Status ===");
                        player.sendMessage("§bLeader: §f" + lobby.getLeader().getName());
                        player.sendMessage("§bMembers (" + lobby.getPlayerCount() + "/4):");
                        for (Player p : lobby.getPlayers()) {
                            if (p != null && p.isOnline()) {
                                String readyStatus = lobby.isReady(p) ? "§a✓ Ready" : "§c✗ Not Ready";
                                player.sendMessage("§7  - §f" + p.getName() + " " + readyStatus);
                            }
                        }
                        if (lobby.allReady()) {
                            player.sendMessage("§aAll players are ready! Use /rc start to begin.");
                        } else {
                            player.sendMessage("§eUse /rc ready to mark yourself as ready.");
                        }
                    }
                    return true;
                }
                
                switch (args[1].toLowerCase()) {
                    case "create":
                        plugin.getTeamLobbyManager().createLobby(player);
                        break;
                    case "leave":
                        plugin.getTeamLobbyManager().leaveLobby(player);
                        break;
                    default:
                        player.sendMessage("§cUsage: /rc team [create|leave]");
                        break;
                }
                break;

            case "stop":
                if (!player.hasPermission("roguecraft.play")) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                
                if (!plugin.getRunManager().hasActiveRun(player)) {
                    player.sendMessage("§cYou don't have an active run!");
                } else {
                    plugin.getGameManager().endRun(player.getUniqueId(), plugin.getArenaManager().getDefaultArena());
                    player.sendMessage("§aRun ended!");
                }
                break;

            case "stats":
                if (!player.hasPermission("roguecraft.play")) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                
                // Check for team run first
                com.eldor.roguecraft.models.TeamRun teamRun = plugin.getRunManager().getTeamRun(player);
                if (teamRun != null && teamRun.isActive()) {
                    displayTeamStats(player, teamRun);
                } else {
                    Run run = plugin.getRunManager().getRun(player);
                    if (run == null || !run.isActive()) {
                        player.sendMessage("§cYou don't have an active run!");
                    } else {
                        displayStats(player, run);
                    }
                }
                break;

            case "gui":
                if (!player.hasPermission("roguecraft.play")) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                
                // Check for team run first
                teamRun = plugin.getRunManager().getTeamRun(player);
                if (teamRun != null && teamRun.isActive()) {
                    plugin.getGuiManager().openPowerUpGUI(player, teamRun);
                } else {
                    Run run = plugin.getRunManager().getRun(player);
                    if (run == null || !run.isActive()) {
                        player.sendMessage("§cYou don't have an active run!");
                    } else {
                        plugin.getGuiManager().openPowerUpGUI(player, run);
                    }
                }
                break;

            case "reload":
                if (!player.hasPermission("roguecraft.admin.reload")) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                
                plugin.reload();
                player.sendMessage("§aConfiguration reloaded!");
                break;

            case "give":
                if (!player.hasPermission("roguecraft.admin.give")) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /rc give <item_id>");
                    player.sendMessage("§7Use /rc listitems to see all available items");
                    return true;
                }
                
                // Check if player has an active run
                if (!plugin.getRunManager().hasActiveRun(player)) {
                    player.sendMessage("§cYou must have an active run to receive items!");
                    return true;
                }
                
                String itemId = args[1].toLowerCase();
                com.eldor.roguecraft.models.GachaItem item = plugin.getGachaManager().getItem(itemId);
                
                if (item == null) {
                    player.sendMessage("§cItem not found: " + itemId);
                    player.sendMessage("§7Use /rc listitems to see all available items");
                    return true;
                }
                
                // Get the player's run
                com.eldor.roguecraft.models.TeamRun playerTeamRun = plugin.getRunManager().getTeamRun(player);
                com.eldor.roguecraft.models.Run playerRun = null;
                if (playerTeamRun == null) {
                    playerRun = plugin.getRunManager().getRun(player);
                }
                
                // Add item to run
                if (playerTeamRun != null) {
                    playerTeamRun.addGachaItem(player, item);
                    plugin.getChestListener().applyItemEffect(player, item, playerTeamRun, null);
                } else if (playerRun != null) {
                    playerRun.addGachaItem(item);
                    plugin.getChestListener().applyItemEffect(player, item, null, playerRun);
                }
                
                String itemRarityColor = item.getRarity().getColorCode();
                player.sendMessage("§aYou received: " + itemRarityColor + item.getName());
                player.sendMessage("§7" + item.getDescription());
                break;

            case "listitems":
                if (!player.hasPermission("roguecraft.admin.give")) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                
                player.sendMessage("§6=== Available Gacha Items ===");
                for (com.eldor.roguecraft.models.GachaItem gachaItem : plugin.getGachaManager().getAllItems()) {
                    String itemColor = gachaItem.getRarity().getColorCode();
                    player.sendMessage(itemColor + gachaItem.getId() + " §7- " + gachaItem.getName());
                }
                player.sendMessage("§7Use /rc give <item_id> to give yourself an item");
                break;

            case "arena":
                if (!player.hasPermission("roguecraft.admin.arena")) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /rc arena <setstart|setcenter|setradius|fromwg|list> [arena_id]");
                    player.sendMessage("§7  setstart - Set spawn point to your current location");
                    player.sendMessage("§7  setcenter - Set center to your current location");
                    player.sendMessage("§7  setradius <radius> - Set arena radius");
                    player.sendMessage("§7  fromwg - Use WorldGuard selection to set center and radius");
                    player.sendMessage("§7  list - List all arenas");
                    return true;
                }
                
                // Parse arguments based on subcommand
                String arenaId = "default";
                String subcommand = args[1].toLowerCase();
                
                // For setradius, radius comes first, then optional arena_id
                if (subcommand.equals("setradius")) {
                    if (args.length < 3) {
                        player.sendMessage("§cUsage: /rc arena setradius <radius> [arena_id]");
                        return true;
                    }
                    try {
                        double radius = Double.parseDouble(args[2]);
                        if (radius <= 0) {
                            player.sendMessage("§cRadius must be greater than 0!");
                            return true;
                        }
                        if (args.length >= 4) {
                            arenaId = args[3];
                        }
                        Arena arena = plugin.getArenaManager().getArena(arenaId);
                        if (arena == null) {
                            arena = new Arena(arenaId, arenaId);
                            plugin.getArenaManager().addArena(arena);
                            player.sendMessage("§aCreated new arena: " + arenaId);
                        }
                        arena.setRadius(radius);
                        plugin.getArenaManager().saveArena(arena);
                        player.sendMessage("§aSet radius for arena '" + arenaId + "' to " + radius + " blocks!");
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cInvalid radius: " + args[2]);
                    }
                    return true;
                }
                
                // For other commands, arena_id is optional third argument
                if (args.length >= 3) {
                    arenaId = args[2];
                }
                
                Arena arena = plugin.getArenaManager().getArena(arenaId);
                if (arena == null) {
                    // Create new arena if it doesn't exist
                    arena = new Arena(arenaId, arenaId);
                    plugin.getArenaManager().addArena(arena);
                    player.sendMessage("§aCreated new arena: " + arenaId);
                }
                
                switch (subcommand) {
                    case "setstart":
                        arena.setSpawnPoint(player.getLocation());
                        plugin.getArenaManager().saveArena(arena);
                        player.sendMessage("§aSet spawn point for arena '" + arenaId + "' to your location!");
                        player.sendMessage("§7Location: " + formatLocation(player.getLocation()));
                        break;
                    
                    case "setcenter":
                        arena.setCenter(player.getLocation());
                        plugin.getArenaManager().saveArena(arena);
                        player.sendMessage("§aSet center for arena '" + arenaId + "' to your location!");
                        player.sendMessage("§7Location: " + formatLocation(player.getLocation()));
                        break;
                    
                    case "fromwg":
                        // Try to get WorldGuard/WorldEdit selection
                        if (!com.eldor.roguecraft.util.WorldGuardUtil.isAvailable()) {
                            player.sendMessage("§cWorldGuard/WorldEdit is not available!");
                            player.sendMessage("§7Please install WorldEdit or WorldGuard to use this feature.");
                            return true;
                        }
                        
                        try {
                            // Try to get selection from WorldEdit API (compatible with multiple versions)
                            org.bukkit.plugin.Plugin worldEditPlugin = 
                                org.bukkit.Bukkit.getPluginManager().getPlugin("WorldEdit");
                            
                            if (worldEditPlugin == null) {
                                player.sendMessage("§cWorldEdit is not installed!");
                                return true;
                            }
                            
                            // Use reflection to access WorldEdit API (works across versions)
                            java.lang.reflect.Method getSessionMethod = worldEditPlugin.getClass()
                                .getMethod("getSession", org.bukkit.entity.Player.class);
                            Object session = getSessionMethod.invoke(worldEditPlugin, player);
                            
                            // Get WorldEdit world adapter
                            java.lang.reflect.Method getWorldMethod = worldEditPlugin.getClass()
                                .getMethod("getWorld", org.bukkit.World.class);
                            Object weWorld = getWorldMethod.invoke(worldEditPlugin, player.getWorld());
                            
                            // Get selection
                            java.lang.reflect.Method getSelectionMethod = session.getClass()
                                .getMethod("getSelection", weWorld.getClass());
                            Object selection = getSelectionMethod.invoke(session, weWorld);
                            
                            if (selection == null) {
                                player.sendMessage("§cYou don't have a WorldEdit selection!");
                                player.sendMessage("§7Use WorldEdit's selection tool (//wand) to select an area first.");
                                return true;
                            }
                            
                            // Get minimum and maximum points
                            java.lang.reflect.Method getMinMethod = selection.getClass()
                                .getMethod("getMinimumPoint");
                            java.lang.reflect.Method getMaxMethod = selection.getClass()
                                .getMethod("getMaximumPoint");
                            
                            Object minPoint = getMinMethod.invoke(selection);
                            Object maxPoint = getMaxMethod.invoke(selection);
                            
                            // Extract coordinates (works with Vector3 or Vector)
                            double minX = getCoordinate(minPoint, "getX");
                            double minY = getCoordinate(minPoint, "getY");
                            double minZ = getCoordinate(minPoint, "getZ");
                            double maxX = getCoordinate(maxPoint, "getX");
                            double maxY = getCoordinate(maxPoint, "getY");
                            double maxZ = getCoordinate(maxPoint, "getZ");
                            
                            // Calculate center
                            double centerX = (minX + maxX) / 2.0;
                            double centerY = (minY + maxY) / 2.0;
                            double centerZ = (minZ + maxZ) / 2.0;
                            
                            org.bukkit.Location center = new org.bukkit.Location(player.getWorld(), centerX, centerY, centerZ);
                            arena.setCenter(center);
                            
                            // Calculate radius (use the largest dimension)
                            double width = maxX - minX;
                            double height = maxY - minY;
                            double depth = maxZ - minZ;
                            double radius = Math.max(Math.max(width, height), depth) / 2.0;
                            
                            arena.setRadius(radius);
                            
                            // Set spawn point to center if not set
                            if (arena.getSpawnPoint() == null) {
                                arena.setSpawnPoint(center);
                            }
                            
                            plugin.getArenaManager().saveArena(arena);
                            
                            player.sendMessage("§aSet arena '" + arenaId + "' from WorldEdit selection!");
                            player.sendMessage("§7Center: " + formatLocation(center));
                            player.sendMessage("§7Radius: " + String.format("%.1f", radius) + " blocks");
                            player.sendMessage("§7Selection size: " + String.format("%.1f", width) + " x " + 
                                            String.format("%.1f", height) + " x " + 
                                            String.format("%.1f", depth));
                        } catch (Exception e) {
                            player.sendMessage("§cError getting WorldEdit selection: " + e.getMessage());
                            plugin.getLogger().warning("Error getting WorldEdit selection: " + e.getMessage());
                            e.printStackTrace();
                        }
                        break;
                    
                    case "list":
                        player.sendMessage("§6=== Available Arenas ===");
                        for (Arena a : plugin.getArenaManager().getAllArenas()) {
                            String info = "§7- §f" + a.getId() + " §7(" + a.getName() + ")";
                            if (a.getCenter() != null) {
                                info += " §7- Center: " + formatLocation(a.getCenter());
                                info += " §7- Radius: " + String.format("%.1f", a.getRadius());
                            }
                            if (plugin.getArenaManager().getDefaultArena() != null && 
                                plugin.getArenaManager().getDefaultArena().getId().equals(a.getId())) {
                                info += " §a[Default]";
                            }
                            player.sendMessage(info);
                        }
                        break;
                    
                    default:
                        player.sendMessage("§cUnknown subcommand: " + args[1]);
                        player.sendMessage("§7Use: /rc arena <setstart|setcenter|setradius|fromwg|list>");
                        break;
                }
                break;

            case "history":
                if (!player.hasPermission("roguecraft.play")) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                
                int limit = 10;
                if (args.length > 1) {
                    try {
                        limit = Integer.parseInt(args[1]);
                        limit = Math.max(1, Math.min(50, limit)); // Clamp between 1 and 50
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cInvalid number: " + args[1]);
                        return true;
                    }
                }
                
                displayRunHistory(player, limit);
                break;
            
            case "leaderboard":
            case "lb":
                if (!player.hasPermission("roguecraft.play")) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                
                int lbLimit = 10;
                if (args.length > 1) {
                    try {
                        lbLimit = Integer.parseInt(args[1]);
                        lbLimit = Math.max(1, Math.min(50, lbLimit)); // Clamp between 1 and 50
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cInvalid number: " + args[1]);
                        return true;
                    }
                }
                
                displayLeaderboard(player, lbLimit);
                break;
            
            case "run":
                if (!player.hasPermission("roguecraft.play")) {
                    player.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /rc run <run_id>");
                    player.sendMessage("§7View detailed information about a specific run");
                    return true;
                }
                
                try {
                    int runId = Integer.parseInt(args[1]);
                    displayRunDetails(player, runId);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid run ID: " + args[1]);
                }
                break;

            default:
                player.sendMessage("§cUnknown command! Use /rc <start|stop|stats|gui|reload|history|leaderboard>");
                if (player.hasPermission("roguecraft.admin.give")) {
                    player.sendMessage("§7Admin: /rc give <item_id> | /rc listitems | /rc arena");
                }
                break;
        }

        return true;
    }

    private void displayStats(Player player, Run run) {
        long elapsed = run.getElapsedTime() / 1000;
        player.sendMessage("§6=== Run Statistics ===");
        player.sendMessage("§eLevel: §f" + run.getLevel());
        player.sendMessage("§eWave: §f" + run.getWave());
        player.sendMessage("§eExperience: §f" + run.getExperience() + " / " + run.getExperienceToNextLevel());
        player.sendMessage("§eTime: §f" + elapsed + " seconds");
        player.sendMessage("§eDifficulty: §f" + String.format("%.2f", run.getDifficultyMultiplier()));
        player.sendMessage("§ePower-Ups: §f" + run.getCollectedPowerUps().size());
        
        // Display weapon info
        if (run.getWeapon() != null) {
            player.sendMessage("§eWeapon: §f" + run.getWeapon().getType().getDisplayName() + " §7(Level " + run.getWeapon().getLevel() + ")");
        }
    }
    
    private void displayTeamStats(Player player, com.eldor.roguecraft.models.TeamRun teamRun) {
        long elapsed = teamRun.getElapsedTime() / 1000;
        player.sendMessage("§6=== Team Run Statistics ===");
        player.sendMessage("§bTeam Size: §f" + teamRun.getPlayerCount());
        player.sendMessage("§eLevel: §f" + teamRun.getLevel());
        player.sendMessage("§eWave: §f" + teamRun.getWave());
        player.sendMessage("§eExperience: §f" + teamRun.getExperience() + " / " + teamRun.getExperienceToNextLevel());
        player.sendMessage("§eTime: §f" + elapsed + " seconds");
        player.sendMessage("§eDifficulty: §f" + String.format("%.2f", teamRun.getDifficultyMultiplier()));
        player.sendMessage("§ePower-Ups: §f" + teamRun.getCollectedPowerUps().size());
        
        // Display weapon info (player-specific)
        Weapon playerWeapon = teamRun.getWeapon(player);
        if (playerWeapon != null) {
            player.sendMessage("§eWeapon: §f" + playerWeapon.getType().getDisplayName() + " §7(Level " + playerWeapon.getLevel() + ")");
        }
        
        // Display team members
        player.sendMessage("§bTeam Members:");
        for (org.bukkit.entity.Player p : teamRun.getPlayers()) {
            if (p != null && p.isOnline()) {
                player.sendMessage("§7  - §f" + p.getName());
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("start", "stop", "stats", "gui", "invite", "accept", "decline", "leave", "ready", "team", "history", "leaderboard", "lb", "run"));
            if (sender.hasPermission("roguecraft.admin.reload")) {
                completions.add("reload");
            }
            if (sender.hasPermission("roguecraft.admin.give")) {
                completions.add("give");
                completions.add("listitems");
            }
            if (sender.hasPermission("roguecraft.admin.arena")) {
                completions.add("arena");
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("start")) {
                // Suggest arena names
                for (Arena arena : plugin.getArenaManager().getAllArenas()) {
                    completions.add(arena.getId());
                }
            } else if (args[0].equalsIgnoreCase("invite")) {
                // Suggest online player names
                for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                    if (p != sender) { // Don't suggest self
                        completions.add(p.getName());
                    }
                }
            } else if (args[0].equalsIgnoreCase("team")) {
                // Suggest team subcommands
                completions.addAll(Arrays.asList("create", "leave"));
            } else if (args[0].equalsIgnoreCase("give")) {
                // Suggest gacha item IDs
                if (sender.hasPermission("roguecraft.admin.give")) {
                    for (com.eldor.roguecraft.models.GachaItem item : plugin.getGachaManager().getAllItems()) {
                        completions.add(item.getId());
                    }
                }
            } else if (args[0].equalsIgnoreCase("arena")) {
                // Suggest arena subcommands
                if (sender.hasPermission("roguecraft.admin.arena")) {
                    completions.addAll(Arrays.asList("setstart", "setcenter", "setradius", "fromwg", "list"));
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("arena")) {
                if (args[1].equalsIgnoreCase("setradius")) {
                    // Suggest radius values
                    completions.addAll(Arrays.asList("25", "50", "75", "100", "150", "200"));
                } else if (args[1].equalsIgnoreCase("setstart") || 
                          args[1].equalsIgnoreCase("setcenter") || 
                          args[1].equalsIgnoreCase("fromwg")) {
                    // Suggest arena IDs
                    for (Arena arena : plugin.getArenaManager().getAllArenas()) {
                        completions.add(arena.getId());
                    }
                }
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("arena") && args[1].equalsIgnoreCase("setradius")) {
                // Suggest arena IDs after radius
                for (Arena arena : plugin.getArenaManager().getAllArenas()) {
                    completions.add(arena.getId());
                }
            }
        }

        // Filter based on what's typed
        String current = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(current));

        return completions;
    }
    
    /**
     * Format a location as a readable string
     */
    private String formatLocation(org.bukkit.Location loc) {
        if (loc == null) return "null";
        return loc.getWorld().getName() + " @ " + 
               String.format("%.1f", loc.getX()) + ", " + 
               String.format("%.1f", loc.getY()) + ", " + 
               String.format("%.1f", loc.getZ());
    }
    
    /**
     * Helper method to extract coordinate from WorldEdit Vector using reflection
     */
    private double getCoordinate(Object vector, String methodName) throws Exception {
        java.lang.reflect.Method method = vector.getClass().getMethod(methodName);
        Object result = method.invoke(vector);
        if (result instanceof Number) {
            return ((Number) result).doubleValue();
        }
        throw new IllegalArgumentException("Could not extract coordinate from " + vector.getClass().getName());
    }
    
    /**
     * Display run history for a player
     */
    private void displayRunHistory(Player player, int limit) {
        if (!plugin.getDatabaseManager().isEnabled()) {
            player.sendMessage("§cDatabase storage is disabled on this server!");
            player.sendMessage("§7Contact an administrator to enable run history tracking.");
            return;
        }
        
        java.util.List<com.eldor.roguecraft.managers.DatabaseManager.RunHistory> history = 
            plugin.getDatabaseManager().getPlayerRunHistory(player.getUniqueId(), limit);
        
        if (history.isEmpty()) {
            player.sendMessage("§cYou don't have any run history yet!");
            player.sendMessage("§7Start a run with /rc start to begin tracking your progress!");
            return;
        }
        
        player.sendMessage("§6=== Your Run History ===");
        player.sendMessage("§7Showing last " + history.size() + " run(s)");
        player.sendMessage("");
        
        for (int i = 0; i < history.size(); i++) {
            com.eldor.roguecraft.managers.DatabaseManager.RunHistory run = history.get(i);
            String runType = run.isTeamRun ? "§b[Team]" : "§7[Solo]";
            long durationSeconds = run.duration / 1000;
            String durationStr = formatDuration(durationSeconds);
            
            player.sendMessage("§e#" + (i + 1) + " " + runType + " §7- Run ID: §f" + run.runId);
            player.sendMessage("§7  Score: §f" + run.playerScore + " §7| Level: §f" + run.level + 
                             " §7| Wave: §f" + run.wave + " §7| Time: §f" + durationStr);
            player.sendMessage("§7  Kills: §f" + run.kills + " §7| XP: §f" + run.experience + 
                             " §7| Gold: §f" + run.goldCollected);
            if (run.weaponType != null) {
                player.sendMessage("§7  Weapon: §f" + run.weaponType + " §7(Level " + run.weaponLevel + ")");
            }
            player.sendMessage("");
        }
        
        player.sendMessage("§7Use §e/rc run <run_id> §7to view detailed information about a specific run");
    }
    
    /**
     * Display leaderboard
     */
    private void displayLeaderboard(Player player, int limit) {
        if (!plugin.getDatabaseManager().isEnabled()) {
            player.sendMessage("§cDatabase storage is disabled on this server!");
            player.sendMessage("§7Contact an administrator to enable leaderboards.");
            return;
        }
        
        java.util.List<com.eldor.roguecraft.managers.DatabaseManager.RunHistory> topRuns = 
            plugin.getDatabaseManager().getTopRuns(limit);
        
        if (topRuns.isEmpty()) {
            player.sendMessage("§cNo runs found in the leaderboard!");
            return;
        }
        
        player.sendMessage("§6=== Top Runs Leaderboard ===");
        player.sendMessage("");
        
        for (int i = 0; i < topRuns.size(); i++) {
            com.eldor.roguecraft.managers.DatabaseManager.RunHistory run = topRuns.get(i);
            String medal = "";
            if (i == 0) medal = "§6🥇 ";
            else if (i == 1) medal = "§7🥈 ";
            else if (i == 2) medal = "§c🥉 ";
            else medal = "§7#" + (i + 1) + " ";
            
            String runType = run.isTeamRun ? "§b[Team]" : "§7[Solo]";
            long durationSeconds = run.duration / 1000;
            String durationStr = formatDuration(durationSeconds);
            
            player.sendMessage(medal + "§f" + (run.playerName != null ? run.playerName : "Unknown") + 
                             " §7- Score: §f" + run.playerScore);
            player.sendMessage("§7  " + runType + " §7| Level: §f" + run.level + 
                             " §7| Wave: §f" + run.wave + " §7| Time: §f" + durationStr);
            player.sendMessage("§7  Kills: §f" + run.kills + " §7| XP: §f" + run.experience + 
                             " §7| Gold: §f" + run.goldCollected + " §7| Run ID: §f" + run.runId);
            player.sendMessage("");
        }
    }
    
    /**
     * Display detailed run information
     */
    private void displayRunDetails(Player player, int runId) {
        if (!plugin.getDatabaseManager().isEnabled()) {
            player.sendMessage("§cDatabase storage is disabled on this server!");
            player.sendMessage("§7Contact an administrator to enable run history tracking.");
            return;
        }
        
        com.eldor.roguecraft.managers.DatabaseManager.RunDetails details = 
            plugin.getDatabaseManager().getRunDetails(runId);
        
        if (details == null) {
            player.sendMessage("§cRun not found: " + runId);
            return;
        }
        
        String runType = details.isTeamRun ? "§bTeam Run" : "§7Solo Run";
        long durationSeconds = details.duration / 1000;
        String durationStr = formatDuration(durationSeconds);
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String startDate = dateFormat.format(new java.util.Date(details.startTime));
        String endDate = dateFormat.format(new java.util.Date(details.endTime));
        
        player.sendMessage("§6=== Run Details #" + runId + " ===");
        player.sendMessage("§7Type: " + runType);
        player.sendMessage("§7Start: §f" + startDate);
        player.sendMessage("§7End: §f" + endDate);
        player.sendMessage("§7Duration: §f" + durationStr);
        player.sendMessage("§7Wave: §f" + details.wave);
        player.sendMessage("§7Difficulty: §f" + String.format("%.2fx", details.difficultyMultiplier));
        player.sendMessage("§7Total Score: §f" + details.totalScore);
        player.sendMessage("");
        
        player.sendMessage("§6Players (" + details.players.size() + "):");
        for (com.eldor.roguecraft.managers.DatabaseManager.PlayerInfo playerInfo : details.players) {
            player.sendMessage("§7  - §f" + playerInfo.playerName);
            player.sendMessage("§7    Score: §f" + playerInfo.playerScore + 
                             " §7| Level: §f" + playerInfo.level + 
                             " §7| XP: §f" + playerInfo.experience);
            player.sendMessage("§7    Kills: §f" + playerInfo.kills + 
                             " §7| Gold: §f" + playerInfo.goldCollected);
            if (playerInfo.weaponType != null) {
                player.sendMessage("§7    Weapon: §f" + playerInfo.weaponType + " §7(Level " + playerInfo.weaponLevel + ")");
            }
            
            // Show stats
            java.util.Map<String, Double> stats = details.playerStats.get(playerInfo.playerUuid);
            if (stats != null && !stats.isEmpty()) {
                player.sendMessage("§7    Stats: §fHealth: " + String.format("%.1f", stats.getOrDefault("health", 0.0)) +
                                 " §7| Damage: " + String.format("%.1f", stats.getOrDefault("damage", 0.0)) +
                                 " §7| Speed: " + String.format("%.2f", stats.getOrDefault("speed", 0.0)));
            }
            
            // Show power-ups
            java.util.List<com.eldor.roguecraft.managers.DatabaseManager.PowerUpInfo> powerUps = 
                details.powerUps.get(playerInfo.playerUuid);
            if (powerUps != null && !powerUps.isEmpty()) {
                player.sendMessage("§7    Power-Ups (" + powerUps.size() + "): §f" + 
                    powerUps.stream().map(p -> p.name).limit(5).collect(java.util.stream.Collectors.joining(", ")) +
                    (powerUps.size() > 5 ? "..." : ""));
            }
            
            // Show items
            java.util.List<com.eldor.roguecraft.managers.DatabaseManager.ItemInfo> items = 
                details.items.get(playerInfo.playerUuid);
            if (items != null && !items.isEmpty()) {
                player.sendMessage("§7    Items (" + items.size() + "): §f" + 
                    items.stream().map(i -> i.name).limit(5).collect(java.util.stream.Collectors.joining(", ")) +
                    (items.size() > 5 ? "..." : ""));
            }
            
            player.sendMessage("");
        }
    }
    
    /**
     * Format duration in seconds to readable string
     */
    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long secs = seconds % 60;
            return minutes + "m " + secs + "s";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            long secs = seconds % 60;
            return hours + "h " + minutes + "m " + secs + "s";
        }
    }
}



