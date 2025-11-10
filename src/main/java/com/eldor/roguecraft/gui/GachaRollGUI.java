package com.eldor.roguecraft.gui;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.GachaItem;
import com.eldor.roguecraft.models.Run;
import com.eldor.roguecraft.models.TeamRun;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * GUI for gacha roll animation that freezes the game
 */
public class GachaRollGUI implements Listener {
    private final RoguecraftPlugin plugin;
    private final Player player;
    private final Run run;
    private final TeamRun teamRun;
    private final Inventory inventory;
    private final GachaItem finalItem;
    private final double luck;
    private BukkitTask animationTask;
    private boolean isRevealed = false;
    private int rollCount = 0;
    private final int totalRolls = 25; // Fast rolls
    private final int slowRolls = 8; // Slower final rolls
    
    public GachaRollGUI(RoguecraftPlugin plugin, Player player, GachaItem finalItem, double luck, Run run) {
        this.plugin = plugin;
        this.player = player;
        this.run = run;
        this.teamRun = null;
        this.finalItem = finalItem;
        this.luck = luck;
        this.inventory = Bukkit.createInventory(null, 27, "§6§l✨ GACHA ROLL ✨");
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        // Stop weapon auto-attack while in GUI
        plugin.getWeaponManager().stopAutoAttack(player);
    }
    
    public GachaRollGUI(RoguecraftPlugin plugin, Player player, GachaItem finalItem, double luck, TeamRun teamRun) {
        this.plugin = plugin;
        this.player = player;
        this.run = null;
        this.teamRun = teamRun;
        this.finalItem = finalItem;
        this.luck = luck;
        this.inventory = Bukkit.createInventory(null, 27, "§6§l✨ GACHA ROLL ✨");
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
        teamRun.setPlayerInGUI(player.getUniqueId(), true);
        
        // Stop ALL players' weapon auto-attacks when ANY player opens GUI (team-wide pause)
        if (teamRun.getWeapon() != null) {
            for (Player teamPlayer : teamRun.getPlayers()) {
                if (teamPlayer != null && teamPlayer.isOnline()) {
                    plugin.getWeaponManager().stopAutoAttack(teamPlayer);
                }
            }
        }
    }
    
    public void open() {
        // Close player's inventory if it's open (prevents conflicts)
        if (player.getOpenInventory() != null && player.getOpenInventory().getTopInventory() != null) {
            if (!player.getOpenInventory().getTopInventory().equals(inventory)) {
                player.closeInventory();
                // Schedule opening and animation after inventory closes
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    setupGUI();
                    player.openInventory(inventory);
                    player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                    startRollingAnimation();
                }, 2L);
                return;
            }
        }
        
        // Setup initial GUI with glass panes
        setupGUI();
        
        // Open inventory
        player.openInventory(inventory);
        
        // Play opening sound
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        
        // Start rolling animation (with small delay to ensure GUI is open)
        // Also show an initial item immediately for visual feedback
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Show initial item immediately
            List<GachaItem> allItems = new ArrayList<>(plugin.getGachaManager().getAllItems());
            if (!allItems.isEmpty()) {
                Random random = new Random();
                GachaItem initialItem = allItems.get(random.nextInt(allItems.size()));
                updateRollDisplay(initialItem, false);
            }
            // Then start the animation
            startRollingAnimation();
        }, 1L);
    }
    
    private void setupGUI() {
        // Fill with glass panes
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, glass);
        }
        
        // Center slot (slot 13) will show the rolling item
    }
    
    private void startRollingAnimation() {
        List<GachaItem> allItems = new ArrayList<>(plugin.getGachaManager().getAllItems());
        
        // Check if we have items
        if (allItems.isEmpty()) {
            plugin.getLogger().warning("[GachaRollGUI] No gacha items available! Cannot start animation.");
            // Show final item immediately if no items available
            revealFinalItem();
            return;
        }
        
        Random random = new Random();
        
        animationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Check if player is still online and GUI is still open
            if (!player.isOnline() || !player.getOpenInventory().getTopInventory().equals(inventory)) {
                if (animationTask != null) {
                    animationTask.cancel();
                }
                return;
            }
            
            if (rollCount < totalRolls) {
                // Fast rolling phase
                GachaItem randomItem = allItems.get(random.nextInt(allItems.size()));
                updateRollDisplay(randomItem, false);
                
                // Play rolling sound
                if (rollCount % 3 == 0) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.5f + (rollCount * 0.05f));
                }
                
                rollCount++;
            } else if (rollCount < totalRolls + slowRolls) {
                // Slow rolling phase
                GachaItem randomItem = allItems.get(random.nextInt(allItems.size()));
                updateRollDisplay(randomItem, false);
                
                // Play slower rolling sound
                if (rollCount % 2 == 0) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
                }
                
                rollCount++;
            } else {
                // Final reveal
                if (animationTask != null) {
                    animationTask.cancel();
                }
                
                revealFinalItem();
            }
        }, 0L, 2L); // Fast ticks (every 2 ticks = 0.1 seconds)
    }
    
    private void updateRollDisplay(GachaItem item, boolean isFinal) {
        if (item == null) {
            plugin.getLogger().warning("[GachaRollGUI] Cannot update display: item is null");
            return;
        }
        
        ItemStack displayItem = new ItemStack(item.getIcon());
        ItemMeta meta = displayItem.getItemMeta();
        
        if (meta == null) {
            plugin.getLogger().warning("[GachaRollGUI] Cannot update display: ItemMeta is null");
            return;
        }
        
        String rarityColor = item.getRarity().getColorCode().toString();
        String displayName;
        
        if (isFinal) {
            displayName = rarityColor + "§l✨ " + item.getName() + " ✨";
        } else {
            displayName = ChatColor.GRAY + "Rolling... " + rarityColor + item.getName();
        }
        
        meta.setDisplayName(displayName);
        
        List<String> lore = new ArrayList<>();
        if (isFinal) {
            lore.add("");
            lore.add(rarityColor + item.getDescription());
            lore.add("");
            if (luck > 1.0) {
                lore.add(ChatColor.GRAY + "Luck: " + ChatColor.GREEN + String.format("%.1fx", luck));
            }
            // Add glowing effect for final item
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        } else {
            lore.add(ChatColor.GRAY + "Rolling...");
        }
        
        meta.setLore(lore);
        displayItem.setItemMeta(meta);
        
        // Update center slot (slot 13) - ensure player's GUI is still open
        if (player.isOnline() && player.getOpenInventory().getTopInventory().equals(inventory)) {
            inventory.setItem(13, displayItem);
        }
    }
    
    private void revealFinalItem() {
        isRevealed = true;
        updateRollDisplay(finalItem, true);
        
        // Final sounds and effects
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.2f);
        
        // Track collected item
        if (teamRun != null) {
            teamRun.addGachaItem(finalItem);
        } else if (run != null) {
            run.addGachaItem(finalItem);
        }
        
        // Apply item effect (this would need to be called from ChestListener)
        // For now, we'll let ChestListener handle it after GUI closes
        
        // Send final message
        String rarityColor = finalItem.getRarity().getColorCode().toString();
        String message = rarityColor + "✨ " + ChatColor.BOLD + finalItem.getName() + ChatColor.RESET + " " + rarityColor + "✨";
        if (luck > 1.0) {
            message += ChatColor.GRAY + " (Luck: " + ChatColor.GREEN + String.format("%.1fx", luck) + ChatColor.GRAY + ")";
        }
        player.sendMessage(message);
        player.sendMessage(ChatColor.GRAY + finalItem.getDescription());
        
        // Auto-close after 3 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory().equals(inventory)) {
                player.closeInventory();
            }
        }, 60L); // 3 seconds
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().equals(inventory) && event.getWhoClicked() == player) {
            // Prevent all clicks during animation
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory) && event.getPlayer() == player) {
            // Cancel animation task if still running
            if (animationTask != null && !animationTask.isCancelled()) {
                animationTask.cancel();
            }
            
            // Mark player as no longer in GUI
            if (teamRun != null) {
                teamRun.setPlayerInGUI(player.getUniqueId(), false);
                
                // Only restart ALL players' weapon auto-attacks if NO other players are in GUI
                if (!teamRun.hasAnyPlayerInGUI() && teamRun.getWeapon() != null) {
                    for (Player teamPlayer : teamRun.getPlayers()) {
                        if (teamPlayer != null && teamPlayer.isOnline()) {
                            plugin.getWeaponManager().startAutoAttack(teamPlayer, teamRun.getWeapon());
                        }
                    }
                }
            } else if (run != null) {
                // Restart weapon auto-attack for solo runs
                if (run.getWeapon() != null) {
                    plugin.getWeaponManager().startAutoAttack(player, run.getWeapon());
                }
            }
            
            // Notify GUI manager that GUI is closing
            plugin.getGuiManager().onGUIClosed(player);
            
            // Unregister this listener
            InventoryClickEvent.getHandlerList().unregister(this);
            InventoryCloseEvent.getHandlerList().unregister(this);
        }
    }
}

