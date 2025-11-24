package com.eldor.roguecraft;

import com.eldor.roguecraft.commands.RoguecraftCommand;
import com.eldor.roguecraft.integrations.PlaceholderAPIExpansion;
import com.eldor.roguecraft.integrations.VaultIntegration;
import com.eldor.roguecraft.integrations.ProtocolLibIntegration;
import com.eldor.roguecraft.managers.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class RoguecraftPlugin extends JavaPlugin {

    private static RoguecraftPlugin instance;
    
    private ConfigManager configManager;
    private GameManager gameManager;
    private RunManager runManager;
    private PowerUpManager powerUpManager;
    private DifficultyManager difficultyManager;
    private ArenaManager arenaManager;
    private SpawnManager spawnManager;
    private GuiManager guiManager;
    private WeaponManager weaponManager;
    private ShrineManager shrineManager;
    private AuraManager auraManager;
    private SynergyManager synergyManager;
    private GachaManager gachaManager;
    private ChestManager chestManager;
    private TeamLobbyManager teamLobbyManager;
    private DatabaseManager databaseManager;
    private com.eldor.roguecraft.listeners.ChestListener chestListener;
    private VaultIntegration vaultIntegration;
    private ProtocolLibIntegration protocolLibIntegration;

    @Override
    public void onEnable() {
        instance = this;
        
        // Save default configs
        saveDefaultConfig();
        saveResource("configs/balance.yml", false);
        saveResource("configs/cards.yml", false);
        saveResource("configs/spawns.yml", false);
        
        // Initialize managers
        this.configManager = new ConfigManager(this);
        
        try {
            this.arenaManager = new ArenaManager(this);
            this.difficultyManager = new DifficultyManager(this);
            this.powerUpManager = new PowerUpManager(this);
            this.spawnManager = new SpawnManager(this);
            this.runManager = new RunManager(this);
            this.guiManager = new GuiManager(this);
            this.weaponManager = new WeaponManager(this);
            this.shrineManager = new ShrineManager(this);
            this.auraManager = new AuraManager(this);
            this.synergyManager = new SynergyManager(this);
            this.gachaManager = new GachaManager(this);
            this.chestManager = new ChestManager(this);
            this.teamLobbyManager = new TeamLobbyManager(this);
            this.databaseManager = new DatabaseManager(this);
            this.gameManager = new GameManager(this);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize managers", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new com.eldor.roguecraft.listeners.PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new com.eldor.roguecraft.listeners.GameListener(this), this);
        getServer().getPluginManager().registerEvents(new com.eldor.roguecraft.listeners.ShrineListener(this), this);
        this.chestListener = new com.eldor.roguecraft.listeners.ChestListener(this);
        getServer().getPluginManager().registerEvents(chestListener, this);
        
        // Register commands
        RoguecraftCommand commandHandler = new RoguecraftCommand(this);
        getCommand("roguecraft").setExecutor(commandHandler);
        getCommand("roguecraft").setTabCompleter(commandHandler);
        
        // Setup soft dependencies
        setupSoftDependencies();
        
        getLogger().info("Roguecraft has been enabled!");
        getLogger().info("Welcome to the Arena Realm!");
    }

    @Override
    public void onDisable() {
        // Stop all active runs
        if (gameManager != null) {
            gameManager.stopAllRuns();
        }
        
        // Stop all weapon auto-attacks
        if (weaponManager != null) {
            weaponManager.stopAllAutoAttacks();
        }
        
        // Cleanup shrines
        if (shrineManager != null) {
            shrineManager.cleanup();
        }
        
        // Cleanup auras
        if (auraManager != null) {
            auraManager.cleanup();
        }
        
        // Cleanup synergies
        if (synergyManager != null) {
            synergyManager.cleanup();
        }
        
        // Cleanup chests
        if (chestManager != null) {
            // Chest cleanup handled per-run in GameManager
        }
        
        // Close database connection
        if (databaseManager != null) {
            databaseManager.close();
        }
        
        getLogger().info("Roguecraft has been disabled!");
    }

    private void setupSoftDependencies() {
        // PlaceholderAPI
        if (com.eldor.roguecraft.util.DepCheck.has("PlaceholderAPI")) {
            new PlaceholderAPIExpansion(this).register();
            getLogger().info("PlaceholderAPI detected - enabling placeholders.");
        }
        
        // ProtocolLib
        if (com.eldor.roguecraft.util.DepCheck.has("ProtocolLib")) {
            this.protocolLibIntegration = new ProtocolLibIntegration(this);
            if (protocolLibIntegration.isEnabled()) {
                getLogger().info("ProtocolLib integration enabled - advanced visual effects available.");
            }
        }
        
        // WorldGuard
        if (com.eldor.roguecraft.util.DepCheck.has("WorldGuard")) {
            getLogger().info("WorldGuard detected - enabling region support.");
            com.eldor.roguecraft.util.WorldGuardUtil.initialize();
            // Register WorldGuard event handlers
            getServer().getPluginManager().registerEvents(new com.eldor.roguecraft.listeners.WorldGuardListener(this), this);
        }
        
        // Vault
        if (com.eldor.roguecraft.util.DepCheck.has("Vault")) {
            this.vaultIntegration = new VaultIntegration(this);
            if (vaultIntegration.isEnabled()) {
                getLogger().info("Vault economy integration enabled - economy: " + vaultIntegration.getEconomyName());
            }
        }
    }

    public void reload() {
        reloadConfig();
        configManager.reload();
        
        // Reload other managers
        difficultyManager.reload();
        powerUpManager.reload();
        spawnManager.reload();
        
        getLogger().info("Roguecraft configuration reloaded!");
    }

    public static RoguecraftPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public RunManager getRunManager() {
        return runManager;
    }

    public PowerUpManager getPowerUpManager() {
        return powerUpManager;
    }

    public DifficultyManager getDifficultyManager() {
        return difficultyManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }
    
    public WeaponManager getWeaponManager() {
        return weaponManager;
    }
    
    public ShrineManager getShrineManager() {
        return shrineManager;
    }
    
    public AuraManager getAuraManager() {
        return auraManager;
    }
    
    public SynergyManager getSynergyManager() {
        return synergyManager;
    }
    
    public GachaManager getGachaManager() {
        return gachaManager;
    }
    
    public ChestManager getChestManager() {
        return chestManager;
    }
    
    public TeamLobbyManager getTeamLobbyManager() {
        return teamLobbyManager;
    }
    
    public com.eldor.roguecraft.listeners.ChestListener getChestListener() {
        return chestListener;
    }
    
    public VaultIntegration getVaultIntegration() {
        return vaultIntegration;
    }
    
    public ProtocolLibIntegration getProtocolLibIntegration() {
        return protocolLibIntegration;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
