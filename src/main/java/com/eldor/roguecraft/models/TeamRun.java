package com.eldor.roguecraft.models;

import org.bukkit.entity.Player;

import java.util.*;

public class TeamRun {
    private final Set<UUID> playerIds;
    private final List<Player> players;
    private int level;
    private int experience;
    private int experienceToNextLevel;
    private long startTime;
    private int wave;
    private double difficultyMultiplier;
    // Individual per-player data
    private final Map<UUID, Weapon> playerWeapons; // Individual weapon per player
    private final Map<UUID, Map<String, Double>> playerStats; // Individual stats per player
    private final Map<UUID, List<PowerUp>> playerPowerUps; // Individual power-ups per player
    private final Map<UUID, List<com.eldor.roguecraft.models.GachaItem>> playerGachaItems; // Individual gacha items per player
    private final Map<UUID, Integer> playerRerolls; // Individual rerolls per player
    
    // Shared team data
    private final List<PowerUp> collectedPowerUps; // Legacy - kept for compatibility, but individual power-ups are stored per player
    private final List<com.eldor.roguecraft.models.GachaItem> collectedGachaItems; // Legacy - kept for compatibility
    private final Map<String, Double> stats; // Legacy - kept for compatibility, but individual stats are stored per player
    private int rerollsRemaining; // Legacy - kept for compatibility
    private boolean isActive;
    private final Set<UUID> playersInGUI;
    private final Map<UUID, Long> guiOpenTimestamps;
    private int currentGold; // Current gold in this run
    private int totalGoldCollected; // Total gold collected in this run
    private int chestCost; // Current cost to open a chest
    private final Set<UUID> clickedBossShrines; // Track boss shrines that have been clicked

    public TeamRun(Player initialPlayer) {
        this.playerIds = new HashSet<>();
        this.players = new ArrayList<>();
        this.playerIds.add(initialPlayer.getUniqueId());
        this.players.add(initialPlayer);
        this.level = 1;
        this.experience = 0;
        this.experienceToNextLevel = 50; // Lower base for faster early leveling
        this.startTime = System.currentTimeMillis();
        this.wave = 1;
        this.difficultyMultiplier = 1.0;
        // Initialize individual player data maps
        this.playerWeapons = new HashMap<>();
        this.playerStats = new HashMap<>();
        this.playerPowerUps = new HashMap<>();
        this.playerGachaItems = new HashMap<>();
        this.playerRerolls = new HashMap<>();
        
        // Legacy shared data (kept for compatibility)
        this.collectedPowerUps = new ArrayList<>();
        this.collectedGachaItems = new ArrayList<>();
        this.stats = new HashMap<>();
        this.rerollsRemaining = 2;
        this.isActive = true;
        this.playersInGUI = new HashSet<>();
        this.guiOpenTimestamps = new HashMap<>();
        this.currentGold = 0; // Start with 0 gold
        this.totalGoldCollected = 0; // Track total gold collected
        this.chestCost = 50; // Initial chest cost
        this.clickedBossShrines = new HashSet<>(); // Track clicked boss shrines
        
        // Initialize base stats for legacy compatibility
        stats.put("health", 20.0);
        stats.put("damage", 1.0);
        stats.put("speed", 1.0);
        stats.put("armor", 0.0);
        stats.put("crit_chance", 0.05);
        stats.put("crit_damage", 1.5);
        stats.put("luck", 1.0); // Base luck stat
        stats.put("xp_multiplier", 1.0); // XP gain multiplier
        stats.put("difficulty", 1.0); // Base difficulty multiplier (affects enemy HP/damage/spawns)
        stats.put("regeneration", 0.01); // Health regeneration per second
        stats.put("drop_rate", 1.0); // Drop rate multiplier (1.0 = 100% of base chance)
        stats.put("pickup_range", 1.0); // Pickup range in blocks (default 1 block)
        stats.put("jump_height", 0.0); // Jump height stat (applies slow falling effect)
        
        // Initialize stats for initial player
        initializePlayerData(initialPlayer.getUniqueId());
    }
    
    /**
     * Initialize player-specific data when a player joins
     */
    private void initializePlayerData(UUID playerId) {
        // Initialize player stats
        Map<String, Double> playerStatMap = new HashMap<>();
        playerStatMap.put("health", 20.0);
        playerStatMap.put("damage", 1.0);
        playerStatMap.put("speed", 1.0);
        playerStatMap.put("armor", 0.0);
        playerStatMap.put("crit_chance", 0.05);
        playerStatMap.put("crit_damage", 1.5);
        playerStatMap.put("luck", 1.0);
        playerStatMap.put("xp_multiplier", 1.0);
        playerStatMap.put("difficulty", 1.0);
        playerStatMap.put("regeneration", 0.01);
        playerStatMap.put("drop_rate", 1.0);
        playerStatMap.put("pickup_range", 1.0);
        playerStatMap.put("jump_height", 0.0);
        playerStats.put(playerId, playerStatMap);
        
        // Initialize empty lists
        playerPowerUps.put(playerId, new ArrayList<>());
        playerGachaItems.put(playerId, new ArrayList<>());
        playerRerolls.put(playerId, 2); // 2 rerolls per player
    }

    public void addPlayer(Player player) {
        if (!playerIds.contains(player.getUniqueId())) {
            playerIds.add(player.getUniqueId());
            players.add(player);
            // Initialize player-specific data
            initializePlayerData(player.getUniqueId());
        }
    }

    public void removePlayer(UUID playerId) {
        playerIds.remove(playerId);
        players.removeIf(p -> p.getUniqueId().equals(playerId));
        // Clean up player-specific data
        playerWeapons.remove(playerId);
        playerStats.remove(playerId);
        playerPowerUps.remove(playerId);
        playerGachaItems.remove(playerId);
        playerRerolls.remove(playerId);
    }

    public Set<UUID> getPlayerIds() {
        return new HashSet<>(playerIds);
    }

    public List<Player> getPlayers() {
        return new ArrayList<>(players);
    }

    public int getPlayerCount() {
        return playerIds.size();
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

    // Individual power-up methods
    public List<PowerUp> getCollectedPowerUps(UUID playerId) {
        List<PowerUp> powerUps = playerPowerUps.get(playerId);
        if (powerUps == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(powerUps);
    }
    
    public List<PowerUp> getCollectedPowerUps(Player player) {
        return getCollectedPowerUps(player.getUniqueId());
    }
    
    public void addPowerUp(UUID playerId, PowerUp powerUp) {
        List<PowerUp> powerUps = playerPowerUps.get(playerId);
        if (powerUps == null) {
            powerUps = new ArrayList<>();
            playerPowerUps.put(playerId, powerUps);
        }
        powerUps.add(powerUp);
    }
    
    public void addPowerUp(Player player, PowerUp powerUp) {
        addPowerUp(player.getUniqueId(), powerUp);
    }
    
    // Individual gacha item methods
    public List<com.eldor.roguecraft.models.GachaItem> getCollectedGachaItems(UUID playerId) {
        List<com.eldor.roguecraft.models.GachaItem> items = playerGachaItems.get(playerId);
        if (items == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(items);
    }
    
    public List<com.eldor.roguecraft.models.GachaItem> getCollectedGachaItems(Player player) {
        return getCollectedGachaItems(player.getUniqueId());
    }
    
    public void addGachaItem(UUID playerId, com.eldor.roguecraft.models.GachaItem item) {
        List<com.eldor.roguecraft.models.GachaItem> items = playerGachaItems.get(playerId);
        if (items == null) {
            items = new ArrayList<>();
            playerGachaItems.put(playerId, items);
        }
        items.add(item);
    }
    
    public void addGachaItem(Player player, com.eldor.roguecraft.models.GachaItem item) {
        addGachaItem(player.getUniqueId(), item);
    }
    
    // Legacy methods for compatibility
    public List<PowerUp> getCollectedPowerUps() {
        // Return combined list of all players' power-ups (for compatibility)
        List<PowerUp> allPowerUps = new ArrayList<>(collectedPowerUps);
        for (List<PowerUp> playerPowerUpsList : playerPowerUps.values()) {
            allPowerUps.addAll(playerPowerUpsList);
        }
        return allPowerUps;
    }

    public void addPowerUp(PowerUp powerUp) {
        // Add to legacy list for compatibility
        this.collectedPowerUps.add(powerUp);
    }
    
    public List<com.eldor.roguecraft.models.GachaItem> getCollectedGachaItems() {
        // Return combined list of all players' gacha items (for compatibility)
        List<com.eldor.roguecraft.models.GachaItem> allItems = new ArrayList<>(collectedGachaItems);
        for (List<com.eldor.roguecraft.models.GachaItem> playerItems : playerGachaItems.values()) {
            allItems.addAll(playerItems);
        }
        return allItems;
    }
    
    public void addGachaItem(com.eldor.roguecraft.models.GachaItem item) {
        // Add to legacy list for compatibility
        this.collectedGachaItems.add(item);
    }

    // Individual stat methods
    public double getStat(UUID playerId, String key) {
        Map<String, Double> playerStatMap = playerStats.get(playerId);
        if (playerStatMap == null) {
            return 0.0;
        }
        return playerStatMap.getOrDefault(key, 0.0);
    }
    
    public double getStat(Player player, String key) {
        return getStat(player.getUniqueId(), key);
    }
    
    public void setStat(UUID playerId, String key, double value) {
        Map<String, Double> playerStatMap = playerStats.get(playerId);
        if (playerStatMap == null) {
            initializePlayerData(playerId);
            playerStatMap = playerStats.get(playerId);
        }
        playerStatMap.put(key, value);
    }
    
    public void setStat(Player player, String key, double value) {
        setStat(player.getUniqueId(), key, value);
    }
    
    public void addStat(UUID playerId, String key, double value) {
        Map<String, Double> playerStatMap = playerStats.get(playerId);
        if (playerStatMap == null) {
            initializePlayerData(playerId);
            playerStatMap = playerStats.get(playerId);
        }
        playerStatMap.put(key, playerStatMap.getOrDefault(key, 0.0) + value);
    }
    
    public void addStat(Player player, String key, double value) {
        addStat(player.getUniqueId(), key, value);
    }
    
    public Map<String, Double> getStats(UUID playerId) {
        Map<String, Double> playerStatMap = playerStats.get(playerId);
        if (playerStatMap == null) {
            initializePlayerData(playerId);
            playerStatMap = playerStats.get(playerId);
        }
        return new HashMap<>(playerStatMap);
    }
    
    public Map<String, Double> getStats(Player player) {
        return getStats(player.getUniqueId());
    }
    
    // Legacy methods for compatibility (use first player's stats)
    public Map<String, Double> getStats() {
        if (playerStats.isEmpty()) {
            return new HashMap<>(stats);
        }
        return new HashMap<>(playerStats.values().iterator().next());
    }

    public double getStat(String key) {
        if (playerStats.isEmpty()) {
            return stats.getOrDefault(key, 0.0);
        }
        return playerStats.values().iterator().next().getOrDefault(key, 0.0);
    }

    public void setStat(String key, double value) {
        // Set for all players (legacy behavior)
        for (UUID playerId : playerIds) {
            setStat(playerId, key, value);
        }
        // Also update legacy stats map
        stats.put(key, value);
    }

    public void addStat(String key, double value) {
        // Add for all players (legacy behavior)
        for (UUID playerId : playerIds) {
            addStat(playerId, key, value);
        }
        // Also update legacy stats map
        stats.put(key, stats.getOrDefault(key, 0.0) + value);
    }

    // Individual reroll methods
    public int getRerollsRemaining(UUID playerId) {
        return playerRerolls.getOrDefault(playerId, 2);
    }
    
    public int getRerollsRemaining(Player player) {
        return getRerollsRemaining(player.getUniqueId());
    }
    
    public void useReroll(UUID playerId) {
        int rerolls = playerRerolls.getOrDefault(playerId, 2);
        if (rerolls > 0) {
            playerRerolls.put(playerId, rerolls - 1);
        }
    }
    
    public void useReroll(Player player) {
        useReroll(player.getUniqueId());
    }
    
    // Legacy method for compatibility
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

    public boolean isPlayerInGUI(UUID playerId) {
        return playersInGUI.contains(playerId);
    }

    public void setPlayerInGUI(UUID playerId, boolean inGUI) {
        if (inGUI) {
            playersInGUI.add(playerId);
            guiOpenTimestamps.put(playerId, System.currentTimeMillis());
        } else {
            playersInGUI.remove(playerId);
            guiOpenTimestamps.remove(playerId);
        }
    }

    public boolean hasAnyPlayerInGUI() {
        return !playersInGUI.isEmpty();
    }

    public Set<UUID> getPlayersInGUI() {
        return new HashSet<>(playersInGUI);
    }
    
    public long getGuiOpenTimestamp(UUID playerId) {
        return guiOpenTimestamps.getOrDefault(playerId, 0L);
    }
    
    // Individual weapon methods
    public Weapon getWeapon(UUID playerId) {
        return playerWeapons.get(playerId);
    }
    
    public Weapon getWeapon(Player player) {
        return getWeapon(player.getUniqueId());
    }
    
    public void setWeapon(UUID playerId, Weapon weapon) {
        playerWeapons.put(playerId, weapon);
    }
    
    public void setWeapon(Player player, Weapon weapon) {
        setWeapon(player.getUniqueId(), weapon);
    }
    
    // Legacy method for compatibility (returns first player's weapon or null)
    public Weapon getWeapon() {
        if (playerWeapons.isEmpty()) {
            return null;
        }
        return playerWeapons.values().iterator().next();
    }
    
    // Legacy method for compatibility (sets weapon for all players - not recommended)
    public void setWeapon(Weapon weapon) {
        for (UUID playerId : playerIds) {
            playerWeapons.put(playerId, weapon);
        }
    }
    
    // Currency methods
    public int getCurrentGold() {
        return currentGold;
    }
    
    public void addGold(int amount) {
        this.currentGold += amount;
        this.totalGoldCollected += amount;
    }
    
    public boolean spendGold(int amount) {
        if (currentGold >= amount) {
            this.currentGold -= amount;
            return true;
        }
        return false;
    }
    
    public int getTotalGoldCollected() {
        return totalGoldCollected;
    }
    
    public int getChestCost() {
        return chestCost;
    }
    
    public void increaseChestCost() {
        // Exponential scaling: 1.75x multiplier for aggressive cost growth
        this.chestCost = (int) Math.ceil(chestCost * 1.75);
    }
    
    public Set<UUID> getClickedBossShrines() {
        return new HashSet<>(clickedBossShrines);
    }
    
    public void markBossShrineClicked(UUID shrineId) {
        clickedBossShrines.add(shrineId);
    }
}



