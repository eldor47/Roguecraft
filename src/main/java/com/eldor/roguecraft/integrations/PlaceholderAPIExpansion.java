package com.eldor.roguecraft.integrations;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.Run;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PlaceholderAPIExpansion extends PlaceholderExpansion {
    private final RoguecraftPlugin plugin;

    public PlaceholderAPIExpansion(RoguecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "roguecraft";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Eldor";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        Run run = plugin.getRunManager().getRun(player);
        if (run == null || !run.isActive()) {
            return "0";
        }

        switch (params.toLowerCase()) {
            case "level":
                return String.valueOf(run.getLevel());
            case "wave":
                return String.valueOf(run.getWave());
            case "experience":
                return String.valueOf(run.getExperience());
            case "experience_next":
                return String.valueOf(run.getExperienceToNextLevel());
            case "time":
                return String.valueOf(run.getElapsedTime() / 1000);
            case "difficulty":
                return String.format("%.2f", run.getDifficultyMultiplier());
            case "powerups":
                return String.valueOf(run.getCollectedPowerUps().size());
            case "rerolls":
                return String.valueOf(run.getRerollsRemaining());
            case "health":
                return String.format("%.1f", run.getStat("health"));
            case "damage":
                return String.format("%.1f", run.getStat("damage"));
            case "speed":
                return String.format("%.1f", run.getStat("speed"));
            default:
                return null;
        }
    }
}



