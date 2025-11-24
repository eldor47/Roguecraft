package com.eldor.roguecraft.managers;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.Run;
import com.eldor.roguecraft.models.TeamRun;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.*;
import java.util.*;

public class DatabaseManager {
    private final RoguecraftPlugin plugin;
    private Connection connection;
    private boolean enabled;
    
    public DatabaseManager(RoguecraftPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfigManager().getMainConfig().getBoolean("database.enabled", true);
        
        if (enabled) {
            initializeDatabase();
        } else {
            plugin.getLogger().info("Database storage is disabled in config.yml");
        }
    }
    
    /**
     * Check if database is enabled
     */
    public boolean isEnabled() {
        return enabled && connection != null;
    }
    
    /**
     * Initialize SQLite database and create tables
     */
    private void initializeDatabase() {
        if (!enabled) {
            return;
        }
        
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            
            File dbFile = new File(dataFolder, "runs.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            
            connection = DriverManager.getConnection(url);
            
            // Create tables
            createTables();
            
            plugin.getLogger().info("Database initialized successfully!");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            enabled = false; // Disable database if initialization fails
        }
    }
    
    /**
     * Create all necessary tables
     */
    private void createTables() throws SQLException {
        // Runs table - stores run information
        String runsTable = "CREATE TABLE IF NOT EXISTS runs (" +
                "run_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "run_uuid TEXT UNIQUE NOT NULL," +
                "is_team_run INTEGER NOT NULL," +
                "start_time INTEGER NOT NULL," +
                "end_time INTEGER," +
                "duration_ms INTEGER," +
                "wave INTEGER NOT NULL," +
                "difficulty_multiplier REAL NOT NULL," +
                "total_score INTEGER," +
                "created_at INTEGER NOT NULL" +
                ")";
        
        // Players in runs table - links players to runs
        String playersTable = "CREATE TABLE IF NOT EXISTS run_players (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "run_id INTEGER NOT NULL," +
                "player_uuid TEXT NOT NULL," +
                "player_name TEXT NOT NULL," +
                "level INTEGER NOT NULL," +
                "experience INTEGER NOT NULL," +
                "kills INTEGER NOT NULL," +
                "gold_collected INTEGER NOT NULL," +
                "player_score INTEGER NOT NULL," +
                "weapon_type TEXT," +
                "weapon_level INTEGER," +
                "FOREIGN KEY (run_id) REFERENCES runs(run_id) ON DELETE CASCADE" +
                ")";
        
        // Player stats table - stores individual player stats at end of run
        String statsTable = "CREATE TABLE IF NOT EXISTS run_player_stats (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "run_id INTEGER NOT NULL," +
                "player_uuid TEXT NOT NULL," +
                "stat_name TEXT NOT NULL," +
                "stat_value REAL NOT NULL," +
                "FOREIGN KEY (run_id) REFERENCES runs(run_id) ON DELETE CASCADE" +
                ")";
        
        // Power-ups collected table
        String powerUpsTable = "CREATE TABLE IF NOT EXISTS run_powerups (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "run_id INTEGER NOT NULL," +
                "player_uuid TEXT NOT NULL," +
                "powerup_id TEXT NOT NULL," +
                "powerup_name TEXT NOT NULL," +
                "powerup_type TEXT NOT NULL," +
                "powerup_rarity TEXT," +
                "FOREIGN KEY (run_id) REFERENCES runs(run_id) ON DELETE CASCADE" +
                ")";
        
        // Gacha items collected table
        String itemsTable = "CREATE TABLE IF NOT EXISTS run_items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "run_id INTEGER NOT NULL," +
                "player_uuid TEXT NOT NULL," +
                "item_id TEXT NOT NULL," +
                "item_name TEXT NOT NULL," +
                "item_rarity TEXT," +
                "FOREIGN KEY (run_id) REFERENCES runs(run_id) ON DELETE CASCADE" +
                ")";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(runsTable);
            stmt.execute(playersTable);
            stmt.execute(statsTable);
            stmt.execute(powerUpsTable);
            stmt.execute(itemsTable);
        }
    }
    
    /**
     * Calculate score based on level, xp, kills, gold, and time
     */
    public int calculateScore(int level, int experience, int kills, int goldCollected, long durationMs) {
        // Score formula:
        // Base: level * 1000
        // XP: experience * 10
        // Kills: kills * 50
        // Gold: goldCollected * 5
        // Time bonus: (duration in seconds) * 2 (longer runs = more score)
        // Total = (level * 1000) + (experience * 10) + (kills * 50) + (goldCollected * 5) + (durationSeconds * 2)
        
        long durationSeconds = durationMs / 1000;
        
        int score = (level * 1000) + 
                   (experience * 10) + 
                   (kills * 50) + 
                   (goldCollected * 5) + 
                   ((int) durationSeconds * 2);
        
        return score;
    }
    
    /**
     * Save a solo run to database
     */
    public void saveRun(Run run, int kills) {
        if (!isEnabled() || run == null) return;
        
        try {
            connection.setAutoCommit(false);
            
            // Generate unique run UUID
            UUID runUuid = UUID.randomUUID();
            long endTime = System.currentTimeMillis();
            long duration = run.getElapsedTime();
            int totalScore = calculateScore(
                run.getLevel(),
                run.getExperience(),
                kills,
                run.getTotalGoldCollected(),
                duration
            );
            
            // Insert run
            String insertRun = "INSERT INTO runs (run_uuid, is_team_run, start_time, end_time, duration_ms, wave, difficulty_multiplier, total_score, created_at) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            int runId;
            try (PreparedStatement stmt = connection.prepareStatement(insertRun, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, runUuid.toString());
                stmt.setInt(2, 0); // Not a team run
                stmt.setLong(3, run.getStartTime());
                stmt.setLong(4, endTime);
                stmt.setLong(5, duration);
                stmt.setInt(6, run.getWave());
                stmt.setDouble(7, run.getDifficultyMultiplier());
                stmt.setInt(8, totalScore);
                stmt.setLong(9, System.currentTimeMillis());
                stmt.executeUpdate();
                
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    runId = rs.getInt(1);
                } else {
                    throw new SQLException("Failed to get run ID");
                }
            }
            
            // Insert player data
            Player player = run.getPlayer();
            String playerName = player != null ? player.getName() : "Unknown";
            int playerScore = calculateScore(
                run.getLevel(),
                run.getExperience(),
                kills,
                run.getTotalGoldCollected(),
                duration
            );
            
            String insertPlayer = "INSERT INTO run_players (run_id, player_uuid, player_name, level, experience, kills, gold_collected, player_score, weapon_type, weapon_level) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            try (PreparedStatement stmt = connection.prepareStatement(insertPlayer)) {
                stmt.setInt(1, runId);
                stmt.setString(2, run.getPlayerId().toString());
                stmt.setString(3, playerName);
                stmt.setInt(4, run.getLevel());
                stmt.setInt(5, run.getExperience());
                stmt.setInt(6, kills);
                stmt.setInt(7, run.getTotalGoldCollected());
                stmt.setInt(8, playerScore);
                stmt.setString(9, run.getWeapon() != null ? run.getWeapon().getType().name() : null);
                stmt.setInt(10, run.getWeapon() != null ? run.getWeapon().getLevel() : 0);
                stmt.executeUpdate();
            }
            
            // Insert stats
            for (Map.Entry<String, Double> stat : run.getStats().entrySet()) {
                String insertStat = "INSERT INTO run_player_stats (run_id, player_uuid, stat_name, stat_value) VALUES (?, ?, ?, ?)";
                try (PreparedStatement stmt = connection.prepareStatement(insertStat)) {
                    stmt.setInt(1, runId);
                    stmt.setString(2, run.getPlayerId().toString());
                    stmt.setString(3, stat.getKey());
                    stmt.setDouble(4, stat.getValue());
                    stmt.executeUpdate();
                }
            }
            
            // Insert power-ups
            for (com.eldor.roguecraft.models.PowerUp powerUp : run.getCollectedPowerUps()) {
                String insertPowerUp = "INSERT INTO run_powerups (run_id, player_uuid, powerup_id, powerup_name, powerup_type, powerup_rarity) " +
                                     "VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = connection.prepareStatement(insertPowerUp)) {
                    stmt.setInt(1, runId);
                    stmt.setString(2, run.getPlayerId().toString());
                    stmt.setString(3, powerUp.getId());
                    stmt.setString(4, powerUp.getName());
                    stmt.setString(5, powerUp.getType().name());
                    stmt.setString(6, powerUp.getRarity() != null ? powerUp.getRarity().name() : null);
                    stmt.executeUpdate();
                }
            }
            
            // Insert gacha items
            for (com.eldor.roguecraft.models.GachaItem item : run.getCollectedGachaItems()) {
                String insertItem = "INSERT INTO run_items (run_id, player_uuid, item_id, item_name, item_rarity) " +
                                  "VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = connection.prepareStatement(insertItem)) {
                    stmt.setInt(1, runId);
                    stmt.setString(2, run.getPlayerId().toString());
                    stmt.setString(3, item.getId());
                    stmt.setString(4, item.getName());
                    stmt.setString(5, item.getRarity() != null ? item.getRarity().name() : null);
                    stmt.executeUpdate();
                }
            }
            
            connection.commit();
            connection.setAutoCommit(true);
            
            plugin.getLogger().info("Saved run to database: " + playerName + " - Score: " + playerScore);
        } catch (SQLException e) {
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException ex) {
                plugin.getLogger().severe("Failed to rollback transaction: " + ex.getMessage());
            }
            plugin.getLogger().severe("Failed to save run to database: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Save a team run to database
     */
    public void saveTeamRun(TeamRun teamRun, Map<UUID, Integer> playerKills) {
        if (!isEnabled() || teamRun == null) return;
        
        try {
            connection.setAutoCommit(false);
            
            // Generate unique run UUID
            UUID runUuid = UUID.randomUUID();
            long endTime = System.currentTimeMillis();
            long duration = teamRun.getElapsedTime();
            
            // Calculate total team score (sum of all player scores)
            int totalTeamScore = 0;
            for (UUID playerId : teamRun.getPlayerIds()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) continue;
                
                int kills = playerKills.getOrDefault(playerId, 0);
                int gold = teamRun.getTotalGoldCollected(); // Shared gold
                int level = teamRun.getLevel();
                int xp = teamRun.getExperience();
                
                totalTeamScore += calculateScore(level, xp, kills, gold, duration);
            }
            
            // Insert run
            String insertRun = "INSERT INTO runs (run_uuid, is_team_run, start_time, end_time, duration_ms, wave, difficulty_multiplier, total_score, created_at) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            
            int runId;
            try (PreparedStatement stmt = connection.prepareStatement(insertRun, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, runUuid.toString());
                stmt.setInt(2, 1); // Is a team run
                stmt.setLong(3, teamRun.getStartTime());
                stmt.setLong(4, endTime);
                stmt.setLong(5, duration);
                stmt.setInt(6, teamRun.getWave());
                stmt.setDouble(7, teamRun.getDifficultyMultiplier());
                stmt.setInt(8, totalTeamScore);
                stmt.setLong(9, System.currentTimeMillis());
                stmt.executeUpdate();
                
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    runId = rs.getInt(1);
                } else {
                    throw new SQLException("Failed to get run ID");
                }
            }
            
            // Insert each player's data
            for (UUID playerId : teamRun.getPlayerIds()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) continue;
                
                int kills = playerKills.getOrDefault(playerId, 0);
                int gold = teamRun.getTotalGoldCollected(); // Shared gold
                int level = teamRun.getLevel();
                int xp = teamRun.getExperience();
                int playerScore = calculateScore(level, xp, kills, gold, duration);
                
                String insertPlayer = "INSERT INTO run_players (run_id, player_uuid, player_name, level, experience, kills, gold_collected, player_score, weapon_type, weapon_level) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                
                try (PreparedStatement stmt = connection.prepareStatement(insertPlayer)) {
                    stmt.setInt(1, runId);
                    stmt.setString(2, playerId.toString());
                    stmt.setString(3, player.getName());
                    stmt.setInt(4, level);
                    stmt.setInt(5, xp);
                    stmt.setInt(6, kills);
                    stmt.setInt(7, gold);
                    stmt.setInt(8, playerScore);
                    stmt.setString(9, teamRun.getWeapon(player) != null ? teamRun.getWeapon(player).getType().name() : null);
                    stmt.setInt(10, teamRun.getWeapon(player) != null ? teamRun.getWeapon(player).getLevel() : 0);
                    stmt.executeUpdate();
                }
                
                // Insert stats for this player
                Map<String, Double> playerStats = teamRun.getStats(playerId);
                for (Map.Entry<String, Double> stat : playerStats.entrySet()) {
                    String insertStat = "INSERT INTO run_player_stats (run_id, player_uuid, stat_name, stat_value) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement stmt = connection.prepareStatement(insertStat)) {
                        stmt.setInt(1, runId);
                        stmt.setString(2, playerId.toString());
                        stmt.setString(3, stat.getKey());
                        stmt.setDouble(4, stat.getValue());
                        stmt.executeUpdate();
                    }
                }
                
                // Insert power-ups for this player
                for (com.eldor.roguecraft.models.PowerUp powerUp : teamRun.getCollectedPowerUps(playerId)) {
                    String insertPowerUp = "INSERT INTO run_powerups (run_id, player_uuid, powerup_id, powerup_name, powerup_type, powerup_rarity) " +
                                         "VALUES (?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement stmt = connection.prepareStatement(insertPowerUp)) {
                        stmt.setInt(1, runId);
                        stmt.setString(2, playerId.toString());
                        stmt.setString(3, powerUp.getId());
                        stmt.setString(4, powerUp.getName());
                        stmt.setString(5, powerUp.getType().name());
                        stmt.setString(6, powerUp.getRarity() != null ? powerUp.getRarity().name() : null);
                        stmt.executeUpdate();
                    }
                }
                
                // Insert gacha items for this player
                for (com.eldor.roguecraft.models.GachaItem item : teamRun.getCollectedGachaItems(playerId)) {
                    String insertItem = "INSERT INTO run_items (run_id, player_uuid, item_id, item_name, item_rarity) " +
                                      "VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement stmt = connection.prepareStatement(insertItem)) {
                        stmt.setInt(1, runId);
                        stmt.setString(2, playerId.toString());
                        stmt.setString(3, item.getId());
                        stmt.setString(4, item.getName());
                        stmt.setString(5, item.getRarity() != null ? item.getRarity().name() : null);
                        stmt.executeUpdate();
                    }
                }
            }
            
            connection.commit();
            connection.setAutoCommit(true);
            
            plugin.getLogger().info("Saved team run to database: " + teamRun.getPlayerCount() + " players - Total Score: " + totalTeamScore);
        } catch (SQLException e) {
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException ex) {
                plugin.getLogger().severe("Failed to rollback transaction: " + ex.getMessage());
            }
            plugin.getLogger().severe("Failed to save team run to database: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Get run history for a player
     */
    public List<RunHistory> getPlayerRunHistory(UUID playerUuid, int limit) {
        List<RunHistory> history = new ArrayList<>();
        
        if (!isEnabled()) {
            return history;
        }
        
        try {
            String query = "SELECT r.run_id, r.run_uuid, r.is_team_run, r.start_time, r.end_time, r.duration_ms, " +
                         "r.wave, r.difficulty_multiplier, r.total_score, " +
                         "rp.player_score, rp.level, rp.experience, rp.kills, rp.gold_collected, rp.weapon_type, rp.weapon_level " +
                         "FROM runs r " +
                         "INNER JOIN run_players rp ON r.run_id = rp.run_id " +
                         "WHERE rp.player_uuid = ? " +
                         "ORDER BY r.end_time DESC " +
                         "LIMIT ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerUuid.toString());
                stmt.setInt(2, limit);
                
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    RunHistory runHistory = new RunHistory();
                    runHistory.runId = rs.getInt("run_id");
                    runHistory.runUuid = UUID.fromString(rs.getString("run_uuid"));
                    runHistory.isTeamRun = rs.getInt("is_team_run") == 1;
                    runHistory.startTime = rs.getLong("start_time");
                    runHistory.endTime = rs.getLong("end_time");
                    runHistory.duration = rs.getLong("duration_ms");
                    runHistory.wave = rs.getInt("wave");
                    runHistory.difficultyMultiplier = rs.getDouble("difficulty_multiplier");
                    runHistory.totalScore = rs.getInt("total_score");
                    runHistory.playerScore = rs.getInt("player_score");
                    runHistory.level = rs.getInt("level");
                    runHistory.experience = rs.getInt("experience");
                    runHistory.kills = rs.getInt("kills");
                    runHistory.goldCollected = rs.getInt("gold_collected");
                    runHistory.weaponType = rs.getString("weapon_type");
                    runHistory.weaponLevel = rs.getInt("weapon_level");
                    
                    history.add(runHistory);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get player run history: " + e.getMessage());
            e.printStackTrace();
        }
        
        return history;
    }
    
    /**
     * Get detailed run information by run ID
     */
    public RunDetails getRunDetails(int runId) {
        if (!isEnabled()) {
            return null;
        }
        
        try {
            // Get run info
            String runQuery = "SELECT * FROM runs WHERE run_id = ?";
            RunDetails details = new RunDetails();
            
            try (PreparedStatement stmt = connection.prepareStatement(runQuery)) {
                stmt.setInt(1, runId);
                ResultSet rs = stmt.executeQuery();
                
                if (!rs.next()) {
                    return null;
                }
                
                details.runId = runId;
                details.runUuid = UUID.fromString(rs.getString("run_uuid"));
                details.isTeamRun = rs.getInt("is_team_run") == 1;
                details.startTime = rs.getLong("start_time");
                details.endTime = rs.getLong("end_time");
                details.duration = rs.getLong("duration_ms");
                details.wave = rs.getInt("wave");
                details.difficultyMultiplier = rs.getDouble("difficulty_multiplier");
                details.totalScore = rs.getInt("total_score");
            }
            
            // Get players
            String playersQuery = "SELECT * FROM run_players WHERE run_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(playersQuery)) {
                stmt.setInt(1, runId);
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    PlayerInfo playerInfo = new PlayerInfo();
                    playerInfo.playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    playerInfo.playerName = rs.getString("player_name");
                    playerInfo.level = rs.getInt("level");
                    playerInfo.experience = rs.getInt("experience");
                    playerInfo.kills = rs.getInt("kills");
                    playerInfo.goldCollected = rs.getInt("gold_collected");
                    playerInfo.playerScore = rs.getInt("player_score");
                    playerInfo.weaponType = rs.getString("weapon_type");
                    playerInfo.weaponLevel = rs.getInt("weapon_level");
                    
                    details.players.add(playerInfo);
                }
            }
            
            // Get stats for all players
            String statsQuery = "SELECT * FROM run_player_stats WHERE run_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(statsQuery)) {
                stmt.setInt(1, runId);
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    String statName = rs.getString("stat_name");
                    double statValue = rs.getDouble("stat_value");
                    
                    details.playerStats.computeIfAbsent(playerUuid, k -> new HashMap<>())
                                      .put(statName, statValue);
                }
            }
            
            // Get power-ups for all players
            String powerUpsQuery = "SELECT * FROM run_powerups WHERE run_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(powerUpsQuery)) {
                stmt.setInt(1, runId);
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    PowerUpInfo powerUp = new PowerUpInfo();
                    powerUp.id = rs.getString("powerup_id");
                    powerUp.name = rs.getString("powerup_name");
                    powerUp.type = rs.getString("powerup_type");
                    powerUp.rarity = rs.getString("powerup_rarity");
                    
                    details.powerUps.computeIfAbsent(playerUuid, k -> new ArrayList<>())
                                   .add(powerUp);
                }
            }
            
            // Get items for all players
            String itemsQuery = "SELECT * FROM run_items WHERE run_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(itemsQuery)) {
                stmt.setInt(1, runId);
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    ItemInfo item = new ItemInfo();
                    item.id = rs.getString("item_id");
                    item.name = rs.getString("item_name");
                    item.rarity = rs.getString("item_rarity");
                    
                    details.items.computeIfAbsent(playerUuid, k -> new ArrayList<>())
                                 .add(item);
                }
            }
            
            return details;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get run details: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Get top runs leaderboard
     */
    public List<RunHistory> getTopRuns(int limit) {
        List<RunHistory> topRuns = new ArrayList<>();
        
        if (!isEnabled()) {
            return topRuns;
        }
        
        try {
            String query = "SELECT r.run_id, r.run_uuid, r.is_team_run, r.start_time, r.end_time, r.duration_ms, " +
                         "r.wave, r.difficulty_multiplier, r.total_score, " +
                         "rp.player_uuid, rp.player_name, rp.player_score, rp.level, rp.experience, rp.kills, rp.gold_collected, rp.weapon_type, rp.weapon_level " +
                         "FROM runs r " +
                         "INNER JOIN run_players rp ON r.run_id = rp.run_id " +
                         "ORDER BY rp.player_score DESC " +
                         "LIMIT ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setInt(1, limit);
                
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    RunHistory runHistory = new RunHistory();
                    runHistory.runId = rs.getInt("run_id");
                    runHistory.runUuid = UUID.fromString(rs.getString("run_uuid"));
                    runHistory.isTeamRun = rs.getInt("is_team_run") == 1;
                    runHistory.startTime = rs.getLong("start_time");
                    runHistory.endTime = rs.getLong("end_time");
                    runHistory.duration = rs.getLong("duration_ms");
                    runHistory.wave = rs.getInt("wave");
                    runHistory.difficultyMultiplier = rs.getDouble("difficulty_multiplier");
                    runHistory.totalScore = rs.getInt("total_score");
                    runHistory.playerScore = rs.getInt("player_score");
                    runHistory.level = rs.getInt("level");
                    runHistory.experience = rs.getInt("experience");
                    runHistory.kills = rs.getInt("kills");
                    runHistory.goldCollected = rs.getInt("gold_collected");
                    runHistory.weaponType = rs.getString("weapon_type");
                    runHistory.weaponLevel = rs.getInt("weapon_level");
                    runHistory.playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    runHistory.playerName = rs.getString("player_name");
                    
                    topRuns.add(runHistory);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get top runs: " + e.getMessage());
            e.printStackTrace();
        }
        
        return topRuns;
    }
    
    /**
     * Close database connection
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close database: " + e.getMessage());
        }
    }
    
    // Data classes for run history
    public static class RunHistory {
        public int runId;
        public UUID runUuid;
        public boolean isTeamRun;
        public long startTime;
        public long endTime;
        public long duration;
        public int wave;
        public double difficultyMultiplier;
        public int totalScore;
        public int playerScore;
        public int level;
        public int experience;
        public int kills;
        public int goldCollected;
        public String weaponType;
        public int weaponLevel;
        public UUID playerUuid;
        public String playerName;
    }
    
    public static class RunDetails {
        public int runId;
        public UUID runUuid;
        public boolean isTeamRun;
        public long startTime;
        public long endTime;
        public long duration;
        public int wave;
        public double difficultyMultiplier;
        public int totalScore;
        public List<PlayerInfo> players = new ArrayList<>();
        public Map<UUID, Map<String, Double>> playerStats = new HashMap<>();
        public Map<UUID, List<PowerUpInfo>> powerUps = new HashMap<>();
        public Map<UUID, List<ItemInfo>> items = new HashMap<>();
    }
    
    public static class PlayerInfo {
        public UUID playerUuid;
        public String playerName;
        public int level;
        public int experience;
        public int kills;
        public int goldCollected;
        public int playerScore;
        public String weaponType;
        public int weaponLevel;
    }
    
    public static class PowerUpInfo {
        public String id;
        public String name;
        public String type;
        public String rarity;
    }
    
    public static class ItemInfo {
        public String id;
        public String name;
        public String rarity;
    }
}

