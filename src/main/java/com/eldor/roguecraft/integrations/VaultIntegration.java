package com.eldor.roguecraft.integrations;

import com.eldor.roguecraft.RoguecraftPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault integration for economy support
 */
public class VaultIntegration {
    private final RoguecraftPlugin plugin;
    private Economy economy;
    private boolean enabled = false;
    
    public VaultIntegration(RoguecraftPlugin plugin) {
        this.plugin = plugin;
        if (setupEconomy()) {
            enabled = true;
            plugin.getLogger().info("Vault economy integration enabled!");
        } else {
            plugin.getLogger().warning("Vault detected but no economy plugin found!");
        }
    }
    
    /**
     * Set up Vault economy
     */
    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        
        economy = rsp.getProvider();
        return economy != null;
    }
    
    /**
     * Check if Vault economy is enabled
     */
    public boolean isEnabled() {
        return enabled && economy != null;
    }
    
    /**
     * Get player's balance
     */
    public double getBalance(Player player) {
        if (!isEnabled()) {
            return 0.0;
        }
        return economy.getBalance(player);
    }
    
    /**
     * Check if player has enough money
     */
    public boolean hasEnough(Player player, double amount) {
        if (!isEnabled()) {
            return false;
        }
        return economy.has(player, amount);
    }
    
    /**
     * Withdraw money from player
     */
    public boolean withdraw(Player player, double amount) {
        if (!isEnabled()) {
            return false;
        }
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }
    
    /**
     * Deposit money to player
     */
    public boolean deposit(Player player, double amount) {
        if (!isEnabled()) {
            return false;
        }
        return economy.depositPlayer(player, amount).transactionSuccess();
    }
    
    /**
     * Format money amount
     */
    public String format(double amount) {
        if (!isEnabled()) {
            return String.format("%.2f", amount);
        }
        return economy.format(amount);
    }
    
    /**
     * Get economy name
     */
    public String getEconomyName() {
        if (!isEnabled()) {
            return "None";
        }
        return economy.getName();
    }
}

