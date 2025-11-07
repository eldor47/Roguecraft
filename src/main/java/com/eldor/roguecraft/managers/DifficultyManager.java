package com.eldor.roguecraft.managers;

import com.eldor.roguecraft.RoguecraftPlugin;
import org.bukkit.configuration.ConfigurationSection;

public class DifficultyManager {
    private final RoguecraftPlugin plugin;
    private double baseDifficulty;
    private double difficultyPerWave;
    private double difficultyPerMinute;
    private double difficultyPerLevel;

    public DifficultyManager(RoguecraftPlugin plugin) {
        this.plugin = plugin;
        loadSettings();
    }

    private void loadSettings() {
        ConfigurationSection balanceSection = plugin.getConfigManager().getBalanceConfig().getConfigurationSection("difficulty");
        if (balanceSection == null) {
            plugin.getLogger().warning("No difficulty section found in balance.yml, using defaults");
            baseDifficulty = 1.0;
            difficultyPerWave = 0.1;
            difficultyPerMinute = 0.05;
            difficultyPerLevel = 0.02;
            return;
        }

        baseDifficulty = balanceSection.getDouble("base", 1.0);
        difficultyPerWave = balanceSection.getDouble("per_wave", 0.1);
        difficultyPerMinute = balanceSection.getDouble("per_minute", 0.05);
        difficultyPerLevel = balanceSection.getDouble("per_level", 0.02);
    }

    public double calculateDifficulty(int wave, int level, long elapsedMinutes) {
        double difficulty = baseDifficulty;
        difficulty += wave * difficultyPerWave;
        difficulty += elapsedMinutes * difficultyPerMinute;
        difficulty += level * difficultyPerLevel;
        return difficulty;
    }

    public double getMobHealthMultiplier(double difficulty) {
        // Balanced HP scaling - moderate quadratic scaling
        // At difficulty 1.0: 1.0x HP
        // At difficulty 2.0: 1.8x HP
        // At difficulty 5.0: 6.0x HP
        // At difficulty 10.0: 15.0x HP
        return 1.0 + (difficulty - 1.0) * (1.0 + (difficulty - 1.0) * 0.3); // Increased from 0.2 to 0.3
    }

    public double getMobDamageMultiplier(double difficulty) {
        return 1.0 + (difficulty - 1.0) * 0.3;
    }

    public double getSpawnRateMultiplier(double difficulty) {
        return 1.0 + (difficulty - 1.0) * 0.2;
    }

    public void reload() {
        loadSettings();
    }
}



