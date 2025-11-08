package com.eldor.roguecraft.managers;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.PowerUp;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.stream.Collectors;

public class PowerUpManager {
    private final RoguecraftPlugin plugin;
    private final Map<String, PowerUp> powerUps;
    private final Map<PowerUp.Rarity, List<PowerUp>> powerUpsByRarity;

    public PowerUpManager(RoguecraftPlugin plugin) {
        this.plugin = plugin;
        this.powerUps = new HashMap<>();
        this.powerUpsByRarity = new HashMap<>();
        
        for (PowerUp.Rarity rarity : PowerUp.Rarity.values()) {
            powerUpsByRarity.put(rarity, new ArrayList<>());
        }
        
        loadPowerUps();
    }

    private void loadPowerUps() {
        ConfigurationSection cardsSection = plugin.getConfigManager().getCardsConfig().getConfigurationSection("powerups");
        if (cardsSection == null) {
            plugin.getLogger().warning("No powerups section found in cards.yml");
            return;
        }

        for (String key : cardsSection.getKeys(false)) {
            ConfigurationSection powerUpSection = cardsSection.getConfigurationSection(key);
            if (powerUpSection == null) continue;

            String id = key;
            String name = powerUpSection.getString("name", key);
            String description = powerUpSection.getString("description", "");
            String rarityStr = powerUpSection.getString("rarity", "COMMON").toUpperCase();
            String typeStr = powerUpSection.getString("type", "STAT_BOOST").toUpperCase();
            String iconStr = powerUpSection.getString("icon", "PAPER");
            double value = powerUpSection.getDouble("value", 1.0);
            List<String> synergies = powerUpSection.getStringList("synergies");

            PowerUp.Rarity rarity;
            try {
                rarity = PowerUp.Rarity.valueOf(rarityStr);
            } catch (IllegalArgumentException e) {
                rarity = PowerUp.Rarity.COMMON;
            }

            PowerUp.PowerUpType type;
            try {
                type = PowerUp.PowerUpType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                type = PowerUp.PowerUpType.STAT_BOOST;
            }

            Material icon;
            try {
                icon = Material.valueOf(iconStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                icon = Material.PAPER;
            }

            PowerUp powerUp = new PowerUp(
                id,
                name,
                description,
                rarity,
                type,
                icon,
                value,
                synergies.toArray(new String[0])
            );

            powerUps.put(id, powerUp);
            powerUpsByRarity.get(rarity).add(powerUp);
        }

        plugin.getLogger().info("Loaded " + powerUps.size() + " power-ups");
    }

    public PowerUp getPowerUp(String id) {
        return powerUps.get(id);
    }

    public List<PowerUp> getRandomPowerUps(int count, PowerUp.Rarity minRarity) {
        List<PowerUp> available = new ArrayList<>();
        
        // Collect all power-ups of minimum rarity or higher
        for (PowerUp.Rarity rarity : PowerUp.Rarity.values()) {
            if (rarity.ordinal() >= minRarity.ordinal()) {
                available.addAll(powerUpsByRarity.get(rarity));
            }
        }
        
        Collections.shuffle(available);
        return available.stream().limit(count).collect(Collectors.toList());
    }

    public List<PowerUp> getRandomPowerUpsForLevel(int level) {
        PowerUp.Rarity minRarity = determineRarityForLevel(level);
        return getRandomPowerUps(3, minRarity);
    }
    
    /**
     * Generate dynamic power-ups based on player stats
     * Ensures no duplicate power-ups in selection
     * Excludes capped stats (regeneration >= 4.0, lifesteal >= 4.0 HP/second)
     */
    public List<PowerUp> generateDynamicPowerUps(int playerLevel, double luck, Object run) {
        // Check if regeneration is capped
        double currentRegen = 0.0;
        double currentLifesteal = 0.0;
        if (run != null) {
            if (run instanceof com.eldor.roguecraft.models.TeamRun) {
                currentRegen = ((com.eldor.roguecraft.models.TeamRun) run).getStat("regeneration");
                currentLifesteal = calculateLifesteal(run);
            } else if (run instanceof com.eldor.roguecraft.models.Run) {
                currentRegen = ((com.eldor.roguecraft.models.Run) run).getStat("regeneration");
                currentLifesteal = calculateLifesteal(run);
            }
        }
        
        boolean excludeRegeneration = currentRegen >= 4.0;
        boolean excludeVampireAura = currentLifesteal >= 4.0;
        
        List<PowerUp> powerUps = new ArrayList<>();
        Set<String> usedTypes = new HashSet<>();
        Random random = new Random();
        int maxAttempts = 30; // Increased from 20 to account for exclusions
        
        while (powerUps.size() < 3 && maxAttempts > 0) {
            maxAttempts--;
            double roll = random.nextDouble();
            PowerUp powerUp = null;
            String uniqueKey = null;
            
            if (roll < 0.22) {
                // 22% chance for weapon upgrade
                powerUp = com.eldor.roguecraft.models.DynamicPowerUp.generateWeaponUpgrade(playerLevel, luck);
                uniqueKey = "weapon_upgrade";
            } else if (roll < 0.42) {
                // 18% chance for weapon mod
                powerUp = com.eldor.roguecraft.models.DynamicPowerUp.generateWeaponMod(playerLevel, luck);
                uniqueKey = "weapon_mod_" + powerUp.getName();
            } else if (roll < 0.62) {
                // 20% chance for aura
                powerUp = com.eldor.roguecraft.models.DynamicPowerUp.generateAura(playerLevel, luck, excludeVampireAura);
                uniqueKey = "aura_" + powerUp.getName();
            } else if (roll < 0.72) {
                // 10% chance for synergy
                powerUp = com.eldor.roguecraft.models.DynamicPowerUp.generateSynergy(playerLevel, luck);
                uniqueKey = "synergy_" + powerUp.getName();
            } else {
                // 30% chance for stat boost (SHRINES REMOVED - now physical in arena)
                powerUp = com.eldor.roguecraft.models.DynamicPowerUp.generateStatBoost(playerLevel, luck, excludeRegeneration);
                // Extract stat name from ID for uniqueness
                String statName = powerUp.getId().replaceAll("dynamic_", "").replaceAll("_\\d+", "");
                uniqueKey = "stat_" + statName;
            }
            
            // Only add if not already present and not null
            if (powerUp != null && !usedTypes.contains(uniqueKey)) {
                powerUps.add(powerUp);
                usedTypes.add(uniqueKey);
            }
        }
        
        // If we couldn't generate 3 unique ones, fill remaining with random stats
        while (powerUps.size() < 3) {
            PowerUp fallback = com.eldor.roguecraft.models.DynamicPowerUp.generateStatBoost(playerLevel, luck, excludeRegeneration);
            if (fallback != null) {
                String statName = fallback.getId().replaceAll("dynamic_", "").replaceAll("_\\d+", "");
                String uniqueKey = "stat_" + statName;
                if (!usedTypes.contains(uniqueKey)) {
                    powerUps.add(fallback);
                    usedTypes.add(uniqueKey);
                }
            }
        }
        
        return powerUps;
    }
    
    /**
     * Overload for backward compatibility
     */
    public List<PowerUp> generateDynamicPowerUps(int playerLevel, double luck) {
        return generateDynamicPowerUps(playerLevel, luck, null);
    }
    
    /**
     * Calculate current lifesteal percentage from Vampire Aura power-ups
     */
    private double calculateLifesteal(Object run) {
        if (run == null) return 0.0;
        
        List<com.eldor.roguecraft.models.PowerUp> powerUps;
        if (run instanceof com.eldor.roguecraft.models.TeamRun) {
            powerUps = ((com.eldor.roguecraft.models.TeamRun) run).getCollectedPowerUps();
        } else if (run instanceof com.eldor.roguecraft.models.Run) {
            powerUps = ((com.eldor.roguecraft.models.Run) run).getCollectedPowerUps();
        } else {
            return 0.0;
        }
        
        double totalLifesteal = 0.0;
        for (com.eldor.roguecraft.models.PowerUp powerUp : powerUps) {
            if (powerUp.getType() == com.eldor.roguecraft.models.PowerUp.PowerUpType.AURA) {
                String name = powerUp.getName().toLowerCase();
                if (name.contains("vampire") || name.contains("lifesteal")) {
                    // Vampire aura value represents lifesteal percentage (e.g., value 1.0 = 2% lifesteal)
                    totalLifesteal += powerUp.getValue() * 2.0; // Convert to percentage
                }
            }
        }
        
        return totalLifesteal;
    }

    private PowerUp.Rarity determineRarityForLevel(int level) {
        // Higher levels have better rarity chances
        Random random = new Random();
        double roll = random.nextDouble();
        
        if (level >= 20 && roll < 0.05) {
            return PowerUp.Rarity.LEGENDARY;
        } else if (level >= 15 && roll < 0.15) {
            return PowerUp.Rarity.EPIC;
        } else if (level >= 10 && roll < 0.35) {
            return PowerUp.Rarity.RARE;
        } else {
            return PowerUp.Rarity.COMMON;
        }
    }

    /**
     * Generate a single rare power-up for Treasure Hunter shrine buff
     */
    public PowerUp generateRarePowerUp(int playerLevel, double luck, Object run) {
        // Check if regeneration is capped
        boolean excludeRegeneration = false;
        if (run != null) {
            double currentRegen = 0.0;
            if (run instanceof com.eldor.roguecraft.models.TeamRun) {
                currentRegen = ((com.eldor.roguecraft.models.TeamRun) run).getStat("regeneration");
            } else if (run instanceof com.eldor.roguecraft.models.Run) {
                currentRegen = ((com.eldor.roguecraft.models.Run) run).getStat("regeneration");
            }
            excludeRegeneration = currentRegen >= 4.0;
        }
        
        // Generate a dynamic power-up and ensure it's rare rarity
        PowerUp powerUp = com.eldor.roguecraft.models.DynamicPowerUp.generateStatBoost(playerLevel, luck, excludeRegeneration);
        // Force rare rarity
        if (powerUp != null) {
            // Create a new power-up with rare rarity
            return new PowerUp(
                powerUp.getId(),
                powerUp.getName(),
                powerUp.getDescription(),
                PowerUp.Rarity.RARE,
                powerUp.getType(),
                powerUp.getIcon(),
                powerUp.getValue() * 1.5, // Boost value for rare
                powerUp.getSynergies()
            );
        }
        return null;
    }
    
    /**
     * Overload for backward compatibility
     */
    public PowerUp generateRarePowerUp(int playerLevel, double luck) {
        return generateRarePowerUp(playerLevel, luck, null);
    }

    public void reload() {
        powerUps.clear();
        for (PowerUp.Rarity rarity : PowerUp.Rarity.values()) {
            powerUpsByRarity.get(rarity).clear();
        }
        loadPowerUps();
    }
}



