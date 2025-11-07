package com.eldor.roguecraft.models;

import org.bukkit.entity.Player;

import java.util.*;

public class Run {
    private final UUID playerId;
    private final Player player;
    private int level;
    private int experience;
    private int experienceToNextLevel;
    private long startTime;
    private long elapsedTime;
    private int wave;
    private double difficultyMultiplier;
    private final List<PowerUp> collectedPowerUps;
    private final Map<String, Double> stats;
    private int rerollsRemaining;
    private boolean isActive;
    private Weapon weapon; // Player's equipped weapon

    public Run(Player player) {
        this.playerId = player.getUniqueId();
        this.player = player;
        this.level = 1;
        this.experience = 0;
        this.experienceToNextLevel = 75; // Reduced from 100 for faster early leveling
        this.startTime = System.currentTimeMillis();
        this.wave = 1;
        this.difficultyMultiplier = 1.0;
        this.collectedPowerUps = new ArrayList<>();
        this.stats = new HashMap<>();
        this.rerollsRemaining = 2; // Default rerolls
        this.isActive = true;
        this.weapon = null; // Weapon selected at start
        
        // Initialize base stats
        stats.put("health", 20.0);
        stats.put("damage", 1.0);
        stats.put("speed", 1.0);
        stats.put("armor", 0.0);
        stats.put("crit_chance", 0.0);
        stats.put("crit_damage", 1.5);
        stats.put("luck", 1.0); // Base luck stat
        stats.put("xp_multiplier", 1.0); // XP gain multiplier
        stats.put("difficulty", 1.0); // Base difficulty multiplier (affects enemy HP/damage/spawns)
        stats.put("regeneration", 0.0); // Health regeneration per second
        stats.put("drop_rate", 1.0); // Drop rate multiplier (1.0 = 100% of base chance)
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Player getPlayer() {
        return player;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getExperience() {
        return experience;
    }

    public void addExperience(int amount) {
        this.experience += amount;
    }

    public int getExperienceToNextLevel() {
        return experienceToNextLevel;
    }

    public void setExperienceToNextLevel(int experienceToNextLevel) {
        this.experienceToNextLevel = experienceToNextLevel;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - startTime;
    }

    public int getWave() {
        return wave;
    }

    public void setWave(int wave) {
        this.wave = wave;
    }

    public void incrementWave() {
        this.wave++;
    }

    public double getDifficultyMultiplier() {
        return difficultyMultiplier;
    }

    public void setDifficultyMultiplier(double difficultyMultiplier) {
        this.difficultyMultiplier = difficultyMultiplier;
    }

    public List<PowerUp> getCollectedPowerUps() {
        return new ArrayList<>(collectedPowerUps);
    }

    public void addPowerUp(PowerUp powerUp) {
        this.collectedPowerUps.add(powerUp);
    }

    public Map<String, Double> getStats() {
        return new HashMap<>(stats);
    }

    public double getStat(String key) {
        return stats.getOrDefault(key, 0.0);
    }

    public void setStat(String key, double value) {
        stats.put(key, value);
    }

    public void addStat(String key, double value) {
        stats.put(key, stats.getOrDefault(key, 0.0) + value);
    }

    public int getRerollsRemaining() {
        return rerollsRemaining;
    }

    public void useReroll() {
        if (rerollsRemaining > 0) {
            rerollsRemaining--;
        }
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
    
    public Weapon getWeapon() {
        return weapon;
    }
    
    public void setWeapon(Weapon weapon) {
        this.weapon = weapon;
    }
}



