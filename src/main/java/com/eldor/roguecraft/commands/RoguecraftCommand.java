package com.eldor.roguecraft.commands;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.Arena;
import com.eldor.roguecraft.models.Run;
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

            default:
                player.sendMessage("§cUnknown command! Use /rc <start|stop|stats|gui|reload>");
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
        
        // Display weapon info
        if (teamRun.getWeapon() != null) {
            player.sendMessage("§eWeapon: §f" + teamRun.getWeapon().getType().getDisplayName() + " §7(Level " + teamRun.getWeapon().getLevel() + ")");
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
            completions.addAll(Arrays.asList("start", "stop", "stats", "gui"));
            if (sender.hasPermission("roguecraft.admin.reload")) {
                completions.add("reload");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            // Suggest arena names
            for (Arena arena : plugin.getArenaManager().getAllArenas()) {
                completions.add(arena.getId());
            }
        }

        // Filter based on what's typed
        String current = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(current));

        return completions;
    }
}



