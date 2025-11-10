package com.eldor.roguecraft.models;

import org.bukkit.Material;

/**
 * Represents a gacha item that can be obtained from chests
 * Inspired by Megabonk's item system
 */
public class GachaItem {
    private final String id;
    private final String name;
    private final String description;
    private final ItemRarity rarity;
    private final Material icon;
    private final ItemEffect effect;
    private final double value; // Effect value (damage multiplier, stat boost, etc.)
    
    public GachaItem(String id, String name, String description, ItemRarity rarity,
                     Material icon, ItemEffect effect, double value) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.rarity = rarity;
        this.icon = icon;
        this.effect = effect;
        this.value = value;
    }
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public ItemRarity getRarity() {
        return rarity;
    }
    
    public Material getIcon() {
        return icon;
    }
    
    public ItemEffect getEffect() {
        return effect;
    }
    
    public double getValue() {
        return value;
    }
    
    /**
     * Item rarity tiers matching Megabonk
     * Common (Green), Uncommon (Blue), Rare (Magenta), Legendary (Yellow)
     */
    public enum ItemRarity {
        COMMON("Common", "§a"),      // Green
        UNCOMMON("Uncommon", "§9"),  // Blue
        RARE("Rare", "§d"),          // Magenta
        LEGENDARY("Legendary", "§e"); // Yellow
        
        private final String displayName;
        private final String colorCode;
        
        ItemRarity(String displayName, String colorCode) {
            this.displayName = displayName;
            this.colorCode = colorCode;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getColorCode() {
            return colorCode;
        }
    }
    
    /**
     * Types of effects that items can have
     */
    public enum ItemEffect {
        STAT_BOOST,           // Permanent stat increase (damage, health, speed, etc.)
        ON_HIT_EFFECT,        // Effect that triggers on hit (poison, freeze, etc.)
        ON_KILL_EFFECT,       // Effect that triggers on kill (spawn entity, etc.)
        PASSIVE_EFFECT,       // Continuous passive effect (regen, lifesteal, etc.)
        SPECIAL_ABILITY        // Unique special ability
    }
}


