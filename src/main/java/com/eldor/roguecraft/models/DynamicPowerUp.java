package com.eldor.roguecraft.models;

import org.bukkit.Material;

import java.util.Random;

/**
 * Factory for generating dynamic power-ups that scale with player stats
 */
public class DynamicPowerUp {
    private static final Random RANDOM = new Random();
    
    /**
     * Generate a random stat boost power-up scaled by luck
     * @param excludeRegeneration If true, regeneration stat will not be generated
     */
    public static PowerUp generateStatBoost(int playerLevel, double luck, boolean excludeRegeneration) {
        // Choose a random stat with weighted selection
        // Higher weight for damage and crit_chance to help with scaling
        String stat;
        int attempts = 0;
        int maxAttempts = 50; // Prevent infinite loop
        
        do {
            attempts++;
            double roll = RANDOM.nextDouble();
            
            // Adjust roll ranges if regeneration is excluded
            double regenStart = excludeRegeneration ? 0.97 : 0.92;
            double regenEnd = excludeRegeneration ? 0.995 : 0.97;
            
            if (roll < 0.12) {
                // 12% chance for difficulty
                stat = "difficulty";
            } else if (roll < 0.32) {
                // 20% chance for damage (increased from ~12.5%)
                stat = "damage";
            } else if (roll < 0.50) {
                // 18% chance for crit_chance (increased from ~12.5%)
                stat = "crit_chance";
            } else if (roll < 0.60) {
                // 10% chance for crit_damage
                stat = "crit_damage";
            } else if (roll < 0.70) {
                // 10% chance for health
                stat = "health";
            } else if (roll < 0.78) {
                // 8% chance for armor
                stat = "armor";
            } else if (roll < 0.85) {
                // 7% chance for speed
                stat = "speed";
            } else if (roll < regenStart) {
                // 7% chance for luck (or more if regen excluded)
                stat = "luck";
            } else if (roll < regenEnd) {
                // 5% chance for regeneration (only if not excluded)
                stat = "regeneration";
            } else if (roll < 0.992) {
                // 2.5% chance for drop_rate
                stat = "drop_rate";
            } else if (roll < 0.994) {
                // 0.5% chance for pickup_range
                stat = "pickup_range";
            } else if (roll < 0.997) {
                // 0.3% chance for jump_height
                stat = "jump_height";
            } else {
                // 0.3% chance for xp_multiplier
                stat = "xp_multiplier";
            }
        } while (excludeRegeneration && stat.equals("regeneration") && attempts < maxAttempts);
        
        // Fallback if we hit max attempts (shouldn't happen, but safety check)
        if (excludeRegeneration && stat.equals("regeneration") && attempts >= maxAttempts) {
            stat = "damage"; // Default to damage if regeneration keeps getting selected
        }
        
        // Determine rarity based on luck
        PowerUp.Rarity rarity = determineRarity(luck);
        
        // Calculate value based on rarity and luck
        double baseValue = getBaseValueForStat(stat, playerLevel);
        double value = baseValue * getRarityMultiplier(rarity) * (0.8 + luck * 0.4); // Luck adds 0-40% bonus
        
        // For difficulty, the value should be additive to the base multiplier (1.0)
        // So if baseValue is 0.1, and rarity is 1.5, value = 0.15, which means difficulty goes from 1.0 to 1.15
        // This is correct - we add it to the stat, not multiply
        
        // Create description
        String description;
        if (stat.equals("difficulty")) {
            // For difficulty, show the multiplier increase clearly
            description = "Increases " + formatStatName(stat) + " by " + String.format("+%.2fx", value) + " (enemies harder, better rewards)";
        } else {
            description = "Increases " + formatStatName(stat) + " by " + formatValue(stat, value);
        }
        
        return new PowerUp(
            "dynamic_" + stat + "_" + RANDOM.nextInt(10000),
            formatStatName(stat) + " Boost",
            description,
            rarity,
            PowerUp.PowerUpType.STAT_BOOST,
            getIconForStat(stat),
            value,
            new String[0]
        );
    }
    
    /**
     * Generate a weapon upgrade power-up
     */
    public static PowerUp generateWeaponUpgrade(int playerLevel, double luck) {
        PowerUp.Rarity rarity = determineRarity(luck);
        
        // Reduced upgrade levels to prevent power spikes
        // Higher rarity = more upgrade levels, but capped at reasonable amounts
        int levels = 1;
        if (rarity == PowerUp.Rarity.RARE) levels = 1; // Rare = 1 level (same as common)
        else if (rarity == PowerUp.Rarity.EPIC) levels = 2; // Epic = 2 levels
        else if (rarity == PowerUp.Rarity.LEGENDARY) levels = 3; // Legendary = 3 levels (reduced from 5)
        
        return new PowerUp(
            "dynamic_weapon_upgrade_" + RANDOM.nextInt(10000),
            "Weapon Enhancement",
            "Upgrades your weapon by " + levels + " level" + (levels > 1 ? "s" : ""),
            rarity,
            PowerUp.PowerUpType.WEAPON_UPGRADE,
            Material.ENCHANTED_BOOK,
            levels,
            new String[0]
        );
    }
    
    /**
     * Generate a weapon mod power-up
     */
    public static PowerUp generateWeaponMod(int playerLevel, double luck) {
        PowerUp.Rarity rarity = determineRarity(luck);
        
        String[] mods = {
            "Piercing Shot", "Explosive Rounds", "Chain Lightning", "Frost Nova",
            "Rapid Fire", "Homing Projectiles", "Multi-Shot", "Burn Effect"
        };
        String modName = mods[RANDOM.nextInt(mods.length)];
        
        double value = 1.0 + (playerLevel * 0.1) * getRarityMultiplier(rarity) * luck;
        
        return new PowerUp(
            "dynamic_mod_" + RANDOM.nextInt(10000),
            modName,
            "Enhances your weapon with " + modName,
            rarity,
            PowerUp.PowerUpType.WEAPON_MOD,
            Material.BLAZE_POWDER,
            value,
            new String[0]
        );
    }
    
    /**
     * Generate an aura power-up (passive effects)
     * @param excludeVampireAura If true, Vampire Aura will not be generated
     */
    public static PowerUp generateAura(int playerLevel, double luck, boolean excludeVampireAura) {
        PowerUp.Rarity rarity = determineRarity(luck);
        
        // Weighted selection - Vampire Aura appears more often (lifesteal is important)
        String auraName;
        int attempts = 0;
        int maxAttempts = 50; // Prevent infinite loop
        
        do {
            attempts++;
            double roll = RANDOM.nextDouble();
            
            // Adjust roll ranges if Vampire Aura is excluded
            if (excludeVampireAura) {
                // Redistribute Vampire Aura's 25% to other auras proportionally
                if (roll < 0.10) {
                    // 10% chance for Thorns Aura (was 10%)
                    auraName = "Thorns Aura";
                } else if (roll < 0.20) {
                    // 10% chance for Regeneration Aura (was 10%)
                    auraName = "Regeneration Aura";
                } else if (roll < 0.30) {
                    // 10% chance for Fire Aura (was 10%)
                    auraName = "Fire Aura";
                } else if (roll < 0.40) {
                    // 10% chance for Ice Aura (was 10%)
                    auraName = "Ice Aura";
                } else if (roll < 0.50) {
                    // 10% chance for Lightning Aura (was 10%)
                    auraName = "Lightning Aura";
                } else if (roll < 0.60) {
                    // 10% chance for Poison Aura (was 10%)
                    auraName = "Poison Aura";
                } else {
                    // 40% chance for Shield Aura (was 15%, now gets Vampire's 25%)
                    auraName = "Shield Aura";
                }
            } else {
                // Normal distribution
                if (roll < 0.25) {
                    // 25% chance for Vampire Aura (increased from 12.5%)
                    auraName = "Vampire Aura";
                } else if (roll < 0.35) {
                    // 10% chance for Thorns Aura
                    auraName = "Thorns Aura";
                } else if (roll < 0.45) {
                    // 10% chance for Regeneration Aura
                    auraName = "Regeneration Aura";
                } else if (roll < 0.55) {
                    // 10% chance for Fire Aura
                    auraName = "Fire Aura";
                } else if (roll < 0.65) {
                    // 10% chance for Ice Aura
                    auraName = "Ice Aura";
                } else if (roll < 0.75) {
                    // 10% chance for Lightning Aura
                    auraName = "Lightning Aura";
                } else if (roll < 0.85) {
                    // 10% chance for Poison Aura
                    auraName = "Poison Aura";
                } else {
                    // 15% chance for Shield Aura
                    auraName = "Shield Aura";
                }
            }
        } while (excludeVampireAura && auraName.equals("Vampire Aura") && attempts < maxAttempts);
        
        // Fallback if we hit max attempts (shouldn't happen, but safety check)
        if (excludeVampireAura && auraName.equals("Vampire Aura") && attempts >= maxAttempts) {
            auraName = "Shield Aura"; // Default to Shield Aura if Vampire keeps getting selected
        }
        
        // Value represents strength of aura
        // Reduced scaling for auras to prevent overpowered values (especially lifesteal)
        double baseValue = 0.5 + (playerLevel * 0.05); // Much lower base scaling
        double value = baseValue * getRarityMultiplier(rarity) * (0.8 + luck * 0.4);
        
        // Cap value for auras to prevent extreme scaling
        value = Math.min(value, 10.0); // Cap at 10.0 for all auras
        
        String description = getAuraDescription(auraName, value);
        
        return new PowerUp(
            "dynamic_aura_" + RANDOM.nextInt(10000),
            auraName,
            description,
            rarity,
            PowerUp.PowerUpType.AURA,
            Material.TOTEM_OF_UNDYING,
            value,
            new String[0]
        );
    }
    
    /**
     * Generate a shrine power-up (temporary powerful buffs)
     */
    public static PowerUp generateShrine(int playerLevel, double luck) {
        PowerUp.Rarity rarity = determineRarity(luck);
        
        String[] shrines = {
            "Shrine of Power", "Shrine of Swiftness", "Shrine of Vitality", "Shrine of Fortune",
            "Shrine of Fury", "Shrine of Protection", "Shrine of Chaos", "Shrine of Time"
        };
        String shrineName = shrines[RANDOM.nextInt(shrines.length)];
        
        // Value represents cooldown reduction
        double baseValue = 30.0 - (playerLevel * 0.5); // Lower = better
        double value = Math.max(10.0, baseValue / getRarityMultiplier(rarity));
        
        String description = getShrineDescription(shrineName, value);
        
        return new PowerUp(
            "dynamic_shrine_" + RANDOM.nextInt(10000),
            shrineName,
            description,
            rarity,
            PowerUp.PowerUpType.SHRINE,
            Material.BEACON,
            value,
            new String[0]
        );
    }
    
    /**
     * Generate a synergy power-up (combo effects)
     */
    public static PowerUp generateSynergy(int playerLevel, double luck) {
        PowerUp.Rarity rarity = determineRarity(luck);
        
        String[] synergies = {
            "Critical Mass", "Elemental Fusion", "Rapid Escalation", "Chain Reaction",
            "Berserker Mode", "Glass Cannon", "Immortal Build", "Lucky Streak"
        };
        String synergyName = synergies[RANDOM.nextInt(synergies.length)];
        
        double baseValue = 1.5 + (playerLevel * 0.1);
        double value = baseValue * getRarityMultiplier(rarity) * luck;
        
        String description = getSynergyDescription(synergyName, value);
        
        return new PowerUp(
            "dynamic_synergy_" + RANDOM.nextInt(10000),
            synergyName,
            description,
            rarity,
            PowerUp.PowerUpType.SYNERGY,
            Material.NETHER_STAR,
            value,
            new String[0]
        );
    }
    
    private static String getAuraDescription(String auraName, double value) {
        switch (auraName) {
            case "Thorns Aura":
                return "Reflect " + String.format("%.1f%%", value * 10) + " damage to attackers";
            case "Regeneration Aura":
                return "Heal " + String.format("%.1f", value * 0.5) + " HP every 5 seconds";
            case "Vampire Aura":
                return "Lifesteal " + String.format("%.1f%%", value * 2.0) + " of damage dealt";
            case "Fire Aura":
                return "Nearby enemies burn for " + String.format("%.1f", value * 2) + " damage/sec";
            case "Ice Aura":
                return "Slow nearby enemies by " + String.format("%.1f%%", value * 15);
            case "Lightning Aura":
                return "Chain lightning every 3 seconds for " + String.format("%.1f", value * 3) + " damage";
            case "Poison Aura":
                return "Poison nearby enemies for " + String.format("%.1f", value) + " damage/sec";
            case "Shield Aura":
                return "Absorb " + String.format("%.1f", value * 5) + " damage before taking HP loss";
            default:
                return "Passive effect that scales with " + String.format("%.1f", value);
        }
    }
    
    private static String getShrineDescription(String shrineName, double cooldown) {
        String effect = "";
        switch (shrineName) {
            case "Shrine of Power":
                effect = "Triple damage for 10 seconds";
                break;
            case "Shrine of Swiftness":
                effect = "Double speed for 15 seconds";
                break;
            case "Shrine of Vitality":
                effect = "Full heal + 50% max HP for 20 seconds";
                break;
            case "Shrine of Fortune":
                effect = "Quadruple XP for 30 seconds";
                break;
            case "Shrine of Fury":
                effect = "100% crit chance for 8 seconds";
                break;
            case "Shrine of Protection":
                effect = "Invulnerability for 5 seconds";
                break;
            case "Shrine of Chaos":
                effect = "Random powerful effect for 12 seconds";
                break;
            case "Shrine of Time":
                effect = "Slow all enemies by 80% for 15 seconds";
                break;
            default:
                effect = "Powerful temporary buff";
        }
        return effect + " (Cooldown: " + String.format("%.0fs", cooldown) + ")";
    }
    
    private static String getSynergyDescription(String synergyName, double value) {
        switch (synergyName) {
            case "Critical Mass":
                return "Crits explode for " + String.format("%.0f%%", value * 50) + " AOE damage";
            case "Elemental Fusion":
                return "Weapon effects stack and multiply by " + String.format("%.1fx", value);
            case "Rapid Escalation":
                return "Gain " + String.format("%.1f%%", value * 2) + " damage per kill (stacks, max +200% bonus)";
            case "Chain Reaction":
                return "Kills have " + String.format("%.0f%%", value * 20) + " chance to trigger free attack";
            case "Berserker Mode":
                return "Gain " + String.format("%.0f%%", value * 30) + " damage when below 30% HP";
            case "Glass Cannon":
                return "+" + String.format("%.0f%%", value * 100) + " damage, -50% max HP";
            case "Immortal Build":
                return "Cannot die for " + String.format("%.1f", value) + " seconds after fatal damage (30s cooldown)";
            case "Lucky Streak":
                int killsNeeded = (int) Math.max(5, 20 / value);
                return "Every " + killsNeeded + " kills grants random power-up effect";
            default:
                return "Powerful combo effect (x" + String.format("%.1f", value) + ")";
        }
    }
    
    /**
     * Determine rarity based on luck stat
     * Legendary is now much rarer - requires very high roll + luck bonus
     */
    private static PowerUp.Rarity determineRarity(double luck) {
        // Base roll from 0.0 to 1.0
        double roll = RANDOM.nextDouble();
        
        // Luck adds a small bonus to the roll (max 5% bonus from very high luck)
        // Formula: luck * 0.005, capped at 0.05 (so luck 10.0+ gives max bonus)
        double luckBonus = Math.min(0.05, luck * 0.005);
        double effectiveRoll = Math.min(1.0, roll + luckBonus);
        
        // Much rarer thresholds:
        // Legendary: 0.99+ (1% base chance, up to 6% with max luck bonus)
        // Epic: 0.85+ (15% base chance, up to 20% with max luck)
        // Rare: 0.60+ (40% base chance, up to 45% with max luck)
        // Common: everything else
        if (effectiveRoll > 0.99) {
            return PowerUp.Rarity.LEGENDARY;
        } else if (effectiveRoll > 0.85) {
            return PowerUp.Rarity.EPIC;
        } else if (effectiveRoll > 0.60) {
            return PowerUp.Rarity.RARE;
        } else {
            return PowerUp.Rarity.COMMON;
        }
    }
    
    private static double getBaseValueForStat(String stat, int playerLevel) {
        if (stat.equals("regeneration")) {
            // Regeneration: base value of 1.0 HP per second, scales with level
            // Increased from 0.5 to make it more noticeable and effective
            return 1.0 + (playerLevel * 0.15);
        }
        if (stat.equals("drop_rate")) {
            // Drop rate: base value of 0.02 (2% increase), scales with level (reduced to prevent excessive values)
            return 0.02 + (playerLevel * 0.005);
        }
        if (stat.equals("pickup_range")) {
            // Pickup range: base value of 0.5 blocks, scales with level
            return 0.5 + (playerLevel * 0.1);
        }
        if (stat.equals("jump_height")) {
            // Jump height: base value of 0.3, scales with level
            return 0.3 + (playerLevel * 0.05);
        }
        double levelScaling = 1.0 + (playerLevel * 0.15);
        
        switch (stat) {
            case "health":
                return 2.0 * levelScaling; // Reduced from 5.0 to 2.0 to prevent excessive health gains
            case "damage":
                return 0.5 * levelScaling;
            case "speed":
                return 0.1 * levelScaling;
            case "armor":
                return 1.0 * levelScaling;
            case "crit_chance":
                return 0.05 * levelScaling;
            case "crit_damage":
                return 0.2 * levelScaling;
            case "luck":
                return 0.15 * levelScaling;
            case "xp_multiplier":
                return 0.1 * levelScaling; // 10% XP boost per level
            case "difficulty":
                return 0.1 * levelScaling; // 10% difficulty increase per level (makes enemies harder)
            default:
                return 1.0 * levelScaling;
        }
    }
    
    private static double getRarityMultiplier(PowerUp.Rarity rarity) {
        switch (rarity) {
            case COMMON:
                return 1.0;
            case RARE:
                return 1.5;
            case EPIC:
                return 2.25;
            case LEGENDARY:
                return 3.5;
            default:
                return 1.0;
        }
    }
    
    private static String formatStatName(String stat) {
        switch (stat) {
            case "health":
                return "Health";
            case "damage":
                return "Damage";
            case "speed":
                return "Speed";
            case "armor":
                return "Armor";
            case "crit_chance":
                return "Critical Chance";
            case "crit_damage":
                return "Critical Damage";
            case "luck":
                return "Luck";
            case "xp_multiplier":
                return "XP Multiplier";
            case "difficulty":
                return "Difficulty";
            case "regeneration":
                return "Regeneration";
            case "drop_rate":
                return "Drop Rate";
            case "pickup_range":
                return "Pickup Range";
            case "jump_height":
                return "Jump Height";
            default:
                return stat;
        }
    }
    
    private static String formatValue(String stat, double value) {
        if (stat.equals("crit_chance")) {
            return String.format("%.1f%%", value * 100);
        } else if (stat.equals("crit_damage")) {
            return String.format("%.1fx", value);
        } else if (stat.equals("xp_multiplier")) {
            return String.format("+%.1f%%", value * 100);
        } else if (stat.equals("difficulty")) {
            return String.format("+%.2fx", value); // Show as additive multiplier (e.g., +0.15x)
        } else if (stat.equals("regeneration")) {
            return String.format("%.2f HP/s", value); // Show as HP per second
        } else if (stat.equals("drop_rate")) {
            return String.format("+%.1f%%", value * 100); // Show as percentage increase
        } else if (stat.equals("pickup_range")) {
            return String.format("+%.1f blocks", value); // Show as blocks
        } else if (stat.equals("jump_height")) {
            // Calculate slow falling level for display
            int slowFallingLevel = (int) Math.min(4, Math.floor(value / 0.5));
            return String.format("+%.1f (Slow Falling %d)", value, slowFallingLevel);
        } else {
            return String.format("%.1f", value);
        }
    }
    
    private static Material getIconForStat(String stat) {
        switch (stat) {
            case "health":
                return Material.RED_DYE;
            case "damage":
                return Material.IRON_SWORD;
            case "speed":
                return Material.SUGAR;
            case "armor":
                return Material.IRON_CHESTPLATE;
            case "crit_chance":
                return Material.ARROW;
            case "crit_damage":
                return Material.DIAMOND_SWORD;
            case "luck":
                return Material.RABBIT_FOOT;
            case "xp_multiplier":
                return Material.EXPERIENCE_BOTTLE;
            case "difficulty":
                return Material.WITHER_SKELETON_SKULL; // Skull icon for difficulty
            case "regeneration":
                return Material.GOLDEN_APPLE; // Golden apple for regeneration
            case "drop_rate":
                return Material.EMERALD; // Emerald for drop rate
            case "pickup_range":
                return Material.LEAD; // Lead for pickup range (magnet-like)
            case "jump_height":
                return Material.FEATHER; // Feather for jump height
            default:
                return Material.PAPER;
        }
    }
}

