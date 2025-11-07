package com.eldor.roguecraft.managers;

import com.eldor.roguecraft.RoguecraftPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class ConfigManager {
    private final RoguecraftPlugin plugin;
    private FileConfiguration balanceConfig;
    private FileConfiguration cardsConfig;
    private FileConfiguration spawnsConfig;
    private File balanceFile;
    private File cardsFile;
    private File spawnsFile;

    public ConfigManager(RoguecraftPlugin plugin) {
        this.plugin = plugin;
        loadConfigs();
    }

    private void loadConfigs() {
        // Load main config
        plugin.saveDefaultConfig();
        
        // Ensure configs directory exists
        File configsDir = new File(plugin.getDataFolder(), "configs");
        if (!configsDir.exists()) {
            configsDir.mkdirs();
        }
        
        // Load custom configs
        balanceFile = new File(plugin.getDataFolder(), "configs/balance.yml");
        cardsFile = new File(plugin.getDataFolder(), "configs/cards.yml");
        spawnsFile = new File(plugin.getDataFolder(), "configs/spawns.yml");
        
        if (!balanceFile.exists()) {
            plugin.saveResource("configs/balance.yml", false);
        }
        if (!cardsFile.exists()) {
            plugin.saveResource("configs/cards.yml", false);
        }
        if (!spawnsFile.exists()) {
            plugin.saveResource("configs/spawns.yml", false);
        }
        
        balanceConfig = YamlConfiguration.loadConfiguration(balanceFile);
        cardsConfig = YamlConfiguration.loadConfiguration(cardsFile);
        spawnsConfig = YamlConfiguration.loadConfiguration(spawnsFile);
    }

    public void reload() {
        plugin.reloadConfig();
        loadConfigs();
    }

    public FileConfiguration getBalanceConfig() {
        return balanceConfig;
    }

    public FileConfiguration getCardsConfig() {
        return cardsConfig;
    }

    public FileConfiguration getSpawnsConfig() {
        return spawnsConfig;
    }

    public FileConfiguration getMainConfig() {
        return plugin.getConfig();
    }

    public void saveBalanceConfig() {
        try {
            balanceConfig.save(balanceFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save balance.yml: " + e.getMessage());
        }
    }

    public void saveCardsConfig() {
        try {
            cardsConfig.save(cardsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save cards.yml: " + e.getMessage());
        }
    }

    public void saveSpawnsConfig() {
        try {
            spawnsConfig.save(spawnsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save spawns.yml: " + e.getMessage());
        }
    }
}
