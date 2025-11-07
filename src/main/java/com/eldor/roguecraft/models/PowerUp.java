package com.eldor.roguecraft.models;

import org.bukkit.Material;

public class PowerUp {
    private final String id;
    private final String name;
    private final String description;
    private final Rarity rarity;
    private final PowerUpType type;
    private final Material icon;
    private final double value;
    private final String[] synergies;

    public PowerUp(String id, String name, String description, Rarity rarity, 
                   PowerUpType type, Material icon, double value, String[] synergies) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.rarity = rarity;
        this.type = type;
        this.icon = icon;
        this.value = value;
        this.synergies = synergies != null ? synergies : new String[0];
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

    public Rarity getRarity() {
        return rarity;
    }

    public PowerUpType getType() {
        return type;
    }

    public Material getIcon() {
        return icon;
    }

    public double getValue() {
        return value;
    }

    public String[] getSynergies() {
        return synergies;
    }

    public enum Rarity {
        COMMON,
        RARE,
        EPIC,
        LEGENDARY
    }

    public enum PowerUpType {
        STAT_BOOST,
        WEAPON_MOD,
        WEAPON_UPGRADE,
        AURA,
        SHRINE,
        SYNERGY
    }
}



