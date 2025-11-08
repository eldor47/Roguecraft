package com.eldor.roguecraft.managers;

import com.eldor.roguecraft.RoguecraftPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.*;

public class SpawnManager {
    private final RoguecraftPlugin plugin;
    private final Map<Integer, List<SpawnEntry>> spawnsByWave;
    private final Map<String, EntityType> eliteTypes;

    public SpawnManager(RoguecraftPlugin plugin) {
        this.plugin = plugin;
        this.spawnsByWave = new HashMap<>();
        this.eliteTypes = new HashMap<>();
        loadSpawns();
    }

    private void loadSpawns() {
        ConfigurationSection spawnsSection = plugin.getConfigManager().getSpawnsConfig().getConfigurationSection("spawns");
        if (spawnsSection == null) {
            plugin.getLogger().warning("No spawns section found in spawns.yml");
            return;
        }

        for (String key : spawnsSection.getKeys(false)) {
            ConfigurationSection waveSection = spawnsSection.getConfigurationSection(key);
            if (waveSection == null) continue;

            try {
                int wave = Integer.parseInt(key);
                List<SpawnEntry> entries = new ArrayList<>();

                for (String entryKey : waveSection.getKeys(false)) {
                    ConfigurationSection entrySection = waveSection.getConfigurationSection(entryKey);
                    if (entrySection == null) continue;

                    String typeStr = entrySection.getString("type", "ZOMBIE").toUpperCase();
                    int count = entrySection.getInt("count", 1);
                    double weight = entrySection.getDouble("weight", 1.0);
                    boolean isElite = entrySection.getBoolean("elite", false);

                    EntityType type;
                    try {
                        type = EntityType.valueOf(typeStr);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid entity type: " + typeStr);
                        continue;
                    }

                    entries.add(new SpawnEntry(type, count, weight, isElite));
                }

                spawnsByWave.put(wave, entries);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid wave number: " + key);
            }
        }

        // Load elite types
        ConfigurationSection elitesSection = plugin.getConfigManager().getSpawnsConfig().getConfigurationSection("elites");
        if (elitesSection != null) {
            for (String key : elitesSection.getKeys(false)) {
                String typeStr = elitesSection.getString(key).toUpperCase();
                try {
                    eliteTypes.put(key, EntityType.valueOf(typeStr));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid elite entity type: " + typeStr);
                }
            }
        }

        plugin.getLogger().info("Loaded spawns for " + spawnsByWave.size() + " wave(s)");
    }

    public List<SpawnEntry> getSpawnsForWave(int wave) {
        // Check if this is infinite mode
        int maxWave = plugin.getConfigManager().getBalanceConfig().getInt("waves.max-wave", 20);
        boolean isInfiniteMode = maxWave > 0 && wave > maxWave;
        
        if (isInfiniteMode) {
            return getInfiniteWaveSpawns(wave, maxWave);
        }
        
        // Get exact wave if it exists
        List<SpawnEntry> spawns = spawnsByWave.get(wave);
        if (spawns != null) {
            return new ArrayList<>(spawns);
        }

        // Find closest lower wave to use as base
        int baseWave = 0;
        for (int w : spawnsByWave.keySet()) {
            if (w <= wave && w > baseWave) {
                baseWave = w;
            }
        }

        if (baseWave > 0) {
            // Dynamically scale up the base wave spawns
            List<SpawnEntry> baseSpawns = spawnsByWave.get(baseWave);
            List<SpawnEntry> scaledSpawns = new ArrayList<>();
            
            // Calculate scaling factor: wave / baseWave
            double scaleFactor = (double) wave / baseWave;
            
            // Track which mob types we already have from config
            Set<EntityType> existingTypes = new HashSet<>();
            for (SpawnEntry entry : baseSpawns) {
                existingTypes.add(entry.getType());
            }
            
            // Also increase variety as waves progress
            int varietyBonus = (wave - baseWave) / 5; // Add more variety every 5 waves
            
            for (SpawnEntry entry : baseSpawns) {
                // Scale count and potentially add more mobs
                int scaledCount = (int) Math.max(1, entry.getCount() * scaleFactor);
                scaledCount += varietyBonus; // Add bonus mobs for variety
                
                // Make elites more common at higher waves (wave 15+) but with lower chance
                // Use spawn-chance from config instead of making everything elite
                double eliteChance = plugin.getConfigManager().getBalanceConfig().getDouble("elites.spawn-chance", 0.05);
                boolean shouldBeElite = entry.isElite();
                
                // At wave 15+, increase chance slightly but still use random chance
                if (!shouldBeElite && wave >= 15) {
                    eliteChance *= 1.5; // 1.5x chance at wave 15+
                }
                if (!shouldBeElite && Math.random() < eliteChance) {
                    shouldBeElite = true;
                }
                
                scaledSpawns.add(new SpawnEntry(
                    entry.getType(),
                    scaledCount,
                    entry.getWeight(),
                    shouldBeElite
                ));
            }
            
            // Only add additional mob types if they're not already in the config
            // This allows config to override defaults
            if (wave >= 3 && baseWave < 3 && !existingTypes.contains(EntityType.SPIDER)) {
                // Add spiders starting at wave 3
                scaledSpawns.add(new SpawnEntry(EntityType.SPIDER, (int)(3 * scaleFactor), 0.7, false));
            }
            if (wave >= 5 && baseWave < 5 && !existingTypes.contains(EntityType.CREEPER)) {
                // Add creepers starting at wave 5
                scaledSpawns.add(new SpawnEntry(EntityType.CREEPER, (int)(2 * scaleFactor), 0.4, false));
            }
            if (wave >= 8 && baseWave < 8 && !existingTypes.contains(EntityType.ENDERMAN)) {
                // Add endermen starting at wave 8
                scaledSpawns.add(new SpawnEntry(EntityType.ENDERMAN, (int)(2 * scaleFactor), 0.3, false));
            }
            if (wave >= 12 && baseWave < 12 && !existingTypes.contains(EntityType.WITCH)) {
                // Add witches starting at wave 12
                scaledSpawns.add(new SpawnEntry(EntityType.WITCH, (int)(1 * scaleFactor), 0.2, false));
            }
            if (wave >= 15 && baseWave < 15 && !existingTypes.contains(EntityType.WITHER_SKELETON)) {
                // Add wither skeletons starting at wave 15
                scaledSpawns.add(new SpawnEntry(EntityType.WITHER_SKELETON, (int)(2 * scaleFactor), 0.3, true));
            }
            
            return scaledSpawns;
        }

        // Fallback: Create basic spawns if no config exists
        List<SpawnEntry> fallbackSpawns = new ArrayList<>();
        fallbackSpawns.add(new SpawnEntry(EntityType.ZOMBIE, wave * 3, 1.0, wave >= 10));
        fallbackSpawns.add(new SpawnEntry(EntityType.SKELETON, wave * 2, 1.0, wave >= 10));
        if (wave >= 3) {
            fallbackSpawns.add(new SpawnEntry(EntityType.SPIDER, wave, 0.7, false));
        }
        return fallbackSpawns;
    }
    
    /**
     * Get spawns for infinite wave mode
     * Only spawns Wither Skeletons to prevent mob infighting
     */
    private List<SpawnEntry> getInfiniteWaveSpawns(int wave, int maxWave) {
        List<SpawnEntry> spawns = new ArrayList<>();
        
        // Get infinite wave config
        org.bukkit.configuration.ConfigurationSection infiniteSection = 
            plugin.getConfigManager().getBalanceConfig().getConfigurationSection("waves.infinite");
        
        List<EntityType> mobTypes;
        if (infiniteSection != null && infiniteSection.contains("mob-types")) {
            // Load from config
            List<String> typeStrings = infiniteSection.getStringList("mob-types");
            mobTypes = new ArrayList<>();
            for (String typeStr : typeStrings) {
                try {
                    mobTypes.add(EntityType.valueOf(typeStr.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid infinite wave mob type: " + typeStr);
                }
            }
        } else {
            // Default: Only Wither Skeletons
            mobTypes = new ArrayList<>();
            mobTypes.add(EntityType.WITHER_SKELETON);
        }
        
        // Ensure we have at least one type
        if (mobTypes.isEmpty()) {
            // Fallback
            mobTypes.add(EntityType.WITHER_SKELETON);
        }
        
        // Calculate base count and scaling
        int baseCount = infiniteSection != null ? infiniteSection.getInt("base-count", 5) : 5;
        int countIncrease = infiniteSection != null ? infiniteSection.getInt("count-increase-per-wave", 2) : 2;
        
        // Calculate how many infinite waves have passed
        int infiniteWaveNumber = wave - maxWave;
        
        // Calculate spawn count (progressive increase, more aggressive)
        int spawnCount = baseCount + (infiniteWaveNumber * countIncrease);
        
        // Elite spawn chance scales dramatically with infinite wave number
        // Use config values for elite spawn scaling in infinite waves
        double eliteBaseChance = infiniteSection != null ? infiniteSection.getDouble("elite-base-chance", 0.20) : 0.20;
        double eliteIncreasePerWave = infiniteSection != null ? infiniteSection.getDouble("elite-increase-per-wave", 0.025) : 0.025;
        double eliteChance = Math.min(0.85, eliteBaseChance + (infiniteWaveNumber * eliteIncreasePerWave)); // Scales from base, caps at 85%
        
        // Use all available types (typically just Wither Skeletons to prevent mob infighting)
        List<EntityType> selectedTypes = new ArrayList<>(mobTypes);
        
        // Distribute spawn count across selected types
        int spawnsPerType = (int) Math.ceil(spawnCount / (double) selectedTypes.size());
        
        // Spawn selected types
        for (EntityType type : selectedTypes) {
            // All mob types in infinite waves are always elite
            // This makes infinite waves significantly more challenging
            spawns.add(new SpawnEntry(type, spawnsPerType, 1.0, true));
        }
        
        return spawns;
    }

    public void reload() {
        spawnsByWave.clear();
        eliteTypes.clear();
        loadSpawns();
    }

    public static class SpawnEntry {
        private final EntityType type;
        private final int count;
        private final double weight;
        private final boolean isElite;

        public SpawnEntry(EntityType type, int count, double weight, boolean isElite) {
            this.type = type;
            this.count = count;
            this.weight = weight;
            this.isElite = isElite;
        }

        public EntityType getType() {
            return type;
        }

        public int getCount() {
            return count;
        }

        public double getWeight() {
            return weight;
        }

        public boolean isElite() {
            return isElite;
        }
    }
}



