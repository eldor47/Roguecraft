package com.eldor.roguecraft.gui;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.managers.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI for displaying leaderboard
 */
public class LeaderboardGUI implements Listener {
    private final RoguecraftPlugin plugin;
    private final Player player;
    private final Inventory inventory;
    private final List<DatabaseManager.RunHistory> topRuns;
    
    public LeaderboardGUI(RoguecraftPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(null, 54, ChatColor.GOLD + "🏆 Top Runs Leaderboard");
        
        // Get top 10 runs
        this.topRuns = plugin.getDatabaseManager().getTopRuns(10);
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        setupGUI();
    }
    
    private void setupGUI() {
        inventory.clear();
        
        // Add border
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.setDisplayName(" ");
        border.setItemMeta(borderMeta);
        
        // Top and bottom borders
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, border);
            inventory.setItem(i + 45, border);
        }
        
        // Side borders
        for (int i = 9; i < 45; i += 9) {
            inventory.setItem(i, border);
            inventory.setItem(i + 8, border);
        }
        
        // Title item in center top
        ItemStack title = new ItemStack(Material.GOLDEN_APPLE);
        ItemMeta titleMeta = title.getItemMeta();
        titleMeta.setDisplayName(ChatColor.GOLD + "🏆 Top Runs Leaderboard");
        List<String> titleLore = new ArrayList<>();
        titleLore.add(ChatColor.GRAY + "Best runs by score");
        titleLore.add(ChatColor.GRAY + "Click a run to view details");
        titleMeta.setLore(titleLore);
        title.setItemMeta(titleMeta);
        inventory.setItem(4, title);
        
        // Display top runs
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int i = 0; i < topRuns.size() && i < slots.length; i++) {
            DatabaseManager.RunHistory run = topRuns.get(i);
            addRunItem(slots[i], run, i + 1);
        }
        
        // Close button
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Close");
        closeButton.setItemMeta(closeMeta);
        inventory.setItem(49, closeButton);
    }
    
    private void addRunItem(int slot, DatabaseManager.RunHistory run, int rank) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        
        // Medal/rank display
        String medal = "";
        ChatColor rankColor = ChatColor.WHITE;
        if (rank == 1) {
            medal = "🥇 ";
            rankColor = ChatColor.GOLD;
        } else if (rank == 2) {
            medal = "🥈 ";
            rankColor = ChatColor.GRAY;
        } else if (rank == 3) {
            medal = "🥉 ";
            rankColor = ChatColor.RED;
        }
        
        String playerName = run.playerName != null ? run.playerName : "Unknown";
        meta.setDisplayName(rankColor + medal + "#" + rank + " " + ChatColor.WHITE + playerName);
        
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GOLD + "Score: " + ChatColor.YELLOW + run.playerScore);
        lore.add("");
        lore.add(ChatColor.GRAY + "Level: " + ChatColor.WHITE + run.level);
        lore.add(ChatColor.GRAY + "Wave: " + ChatColor.WHITE + run.wave);
        lore.add(ChatColor.GRAY + "Time: " + ChatColor.WHITE + formatDuration(run.duration / 1000));
        lore.add("");
        lore.add(ChatColor.GRAY + "Kills: " + ChatColor.WHITE + run.kills);
        lore.add(ChatColor.GRAY + "XP: " + ChatColor.WHITE + run.experience);
        lore.add(ChatColor.GRAY + "Gold: " + ChatColor.WHITE + run.goldCollected);
        lore.add("");
        if (run.isTeamRun) {
            lore.add(ChatColor.BLUE + "Team Run");
        } else {
            lore.add(ChatColor.GRAY + "Solo Run");
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to view details");
        lore.add(ChatColor.GRAY + "Run ID: " + run.runId);
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }
    
    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%d:%02d", minutes, secs);
        }
    }
    
    public void open() {
        player.openInventory(inventory);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() != inventory) return;
        if (event.getWhoClicked() != player) return;
        
        event.setCancelled(true);
        
        int slot = event.getSlot();
        
        // Close button
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        
        // Check if clicked on a run item
        for (int i = 0; i < topRuns.size(); i++) {
            DatabaseManager.RunHistory run = topRuns.get(i);
            int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
            if (i < slots.length && slot == slots[i]) {
                // Open run details
                player.performCommand("rc run " + run.runId);
                player.closeInventory();
                return;
            }
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory() == inventory && event.getPlayer() == player) {
            // Unregister listener when GUI closes
            org.bukkit.event.HandlerList.unregisterAll(this);
        }
    }
}


