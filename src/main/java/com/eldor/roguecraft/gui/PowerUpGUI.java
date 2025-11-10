package com.eldor.roguecraft.gui;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.PowerUp;
import com.eldor.roguecraft.models.Run;
import com.eldor.roguecraft.models.TeamRun;
import com.eldor.roguecraft.models.Weapon;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PowerUpGUI implements Listener {
    private final RoguecraftPlugin plugin;
    private final Player player;
    private final Run run;
    private final TeamRun teamRun;
    private final Inventory inventory;
    private final List<PowerUp> powerUps;
    private Inventory itemsGUI; // Track items view GUI

    public PowerUpGUI(RoguecraftPlugin plugin, Player player, Run run) {
        this.plugin = plugin;
        this.player = player;
        this.run = run;
        this.teamRun = null;
        // Use dynamic power-up generation with luck scaling
        this.powerUps = plugin.getPowerUpManager().generateDynamicPowerUps(run.getLevel(), run.getStat("luck"), run);
        this.inventory = Bukkit.createInventory(null, 36, "§6Choose Your Power-Up");
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        // Stop weapon auto-attack while in GUI
        plugin.getWeaponManager().stopAutoAttack(player);
    }

    public PowerUpGUI(RoguecraftPlugin plugin, Player player, TeamRun teamRun) {
        this.plugin = plugin;
        this.player = player;
        this.run = null;
        this.teamRun = teamRun;
        // Use dynamic power-up generation with luck scaling
        this.powerUps = plugin.getPowerUpManager().generateDynamicPowerUps(teamRun.getLevel(), teamRun.getStat("luck"), teamRun);
        this.inventory = Bukkit.createInventory(null, 36, "§6Choose Your Power-Up");
        
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
    
    private Run getEffectiveRun() {
        return run != null ? run : null;
    }
    
    private TeamRun getEffectiveTeamRun() {
        return teamRun != null ? teamRun : null;
    }
    
    private int getLevel() {
        return run != null ? run.getLevel() : (teamRun != null ? teamRun.getLevel() : 1);
    }
    
    private int getRerollsRemaining() {
        return run != null ? run.getRerollsRemaining() : (teamRun != null ? teamRun.getRerollsRemaining() : 0);
    }
    
    private void useReroll() {
        if (run != null) {
            run.useReroll();
        } else if (teamRun != null) {
            teamRun.useReroll();
        }
    }

    public void open() {
        // Close player's inventory if it's open (prevents conflicts)
        if (player.getOpenInventory() != null && player.getOpenInventory().getTopInventory() != null) {
            // Only close if it's not already our GUI
            if (!player.getOpenInventory().getTopInventory().equals(inventory)) {
                player.closeInventory();
                // Small delay to ensure inventory is fully closed before opening new one
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        setupGUI();
                        player.openInventory(inventory);
                    }
                }, 1L);
                return;
            }
        }
        
        setupGUI();
        player.openInventory(inventory);
    }

    private void setupGUI() {
        // Clear inventory
        inventory.clear();

        // Add stats display at top row (slots 0-8)
        addStatsDisplay();
        
        // Add collected items view button
        addCollectedItemsButton();

        // Add power-up options at slots 10, 13, 16
        if (powerUps.size() > 0) {
            addPowerUpItem(10, powerUps.get(0));
        }
        if (powerUps.size() > 1) {
            addPowerUpItem(13, powerUps.get(1));
        }
        if (powerUps.size() > 2) {
            addPowerUpItem(16, powerUps.get(2));
        }

        // Add reroll button if rerolls available
        if (getRerollsRemaining() > 0) {
            ItemStack rerollItem = new ItemStack(Material.EMERALD);
            ItemMeta rerollMeta = rerollItem.getItemMeta();
            rerollMeta.setDisplayName(ChatColor.GREEN + "Reroll Power-Ups");
            List<String> rerollLore = new ArrayList<>();
            rerollLore.add(ChatColor.GRAY + "Rerolls remaining: " + ChatColor.YELLOW + getRerollsRemaining());
            rerollMeta.setLore(rerollLore);
            rerollItem.setItemMeta(rerollMeta);
            inventory.setItem(31, rerollItem);
        }

        // Close button
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Close");
        closeItem.setItemMeta(closeMeta);
        inventory.setItem(35, closeItem);
    }
    
    private void addStatsDisplay() {
        // Current Stats Item
        ItemStack statsItem = new ItemStack(Material.BOOK);
        ItemMeta statsMeta = statsItem.getItemMeta();
        statsMeta.setDisplayName(ChatColor.GOLD + "Current Stats");
        
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        if (run != null) {
            lore.add(ChatColor.GREEN + "Health: " + ChatColor.WHITE + String.format("%.1f", run.getStat("health")));
            lore.add(ChatColor.RED + "Damage: " + ChatColor.WHITE + String.format("%.1f", run.getStat("damage")));
            lore.add(ChatColor.AQUA + "Speed: " + ChatColor.WHITE + String.format("%.1f", run.getStat("speed")));
            lore.add(ChatColor.BLUE + "Armor: " + ChatColor.WHITE + String.format("%.1f", run.getStat("armor")));
            lore.add(ChatColor.DARK_PURPLE + "Crit Chance: " + ChatColor.WHITE + String.format("%.1f%%", run.getStat("crit_chance") * 100));
            lore.add(ChatColor.LIGHT_PURPLE + "Crit Damage: " + ChatColor.WHITE + String.format("%.1fx", run.getStat("crit_damage")));
            lore.add(ChatColor.GOLD + "Luck: " + ChatColor.WHITE + String.format("%.2f", run.getStat("luck")));
            lore.add(ChatColor.YELLOW + "XP Multi: " + ChatColor.WHITE + String.format("%.2fx", run.getStat("xp_multiplier")));
            lore.add(ChatColor.GREEN + "Regeneration: " + ChatColor.WHITE + String.format("%.2f HP/s", run.getStat("regeneration")));
            lore.add(ChatColor.AQUA + "Drop Rate: " + ChatColor.WHITE + String.format("%.1f%%", run.getStat("drop_rate") * 100));
            lore.add(ChatColor.DARK_RED + "Difficulty: " + ChatColor.WHITE + String.format("%.2fx", run.getStat("difficulty")));
            
            // Calculate and display lifesteal from Vampire Aura
            double lifesteal = calculateLifesteal(run);
            if (lifesteal > 0) {
                lore.add(ChatColor.RED + "Lifesteal: " + ChatColor.WHITE + String.format("%.1f%%", lifesteal) + " of damage");
            }
            
            // Show active auras
            List<String> activeAuras = getActiveAuraNames(run);
            if (!activeAuras.isEmpty()) {
                lore.add("");
                lore.add(ChatColor.GOLD + "Active Auras:");
                for (String aura : activeAuras) {
                    lore.add(ChatColor.YELLOW + "  • " + aura);
                }
            }
            
            // Show weapon stats
            Weapon weapon = run.getWeapon();
            if (weapon != null) {
                lore.add("");
                lore.add(ChatColor.GOLD + "Weapon Stats:");
                lore.add(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + weapon.getType().getDisplayName());
                lore.add(ChatColor.YELLOW + "Level: " + ChatColor.WHITE + weapon.getLevel());
                lore.add(ChatColor.RED + "Damage: " + ChatColor.WHITE + String.format("%.1f", weapon.getDamage()));
                lore.add(ChatColor.AQUA + "Range: " + ChatColor.WHITE + String.format("%.1f", weapon.getRange()) + " blocks");
                lore.add(ChatColor.GREEN + "Attack Speed: " + ChatColor.WHITE + String.format("%.2f", weapon.getAttackSpeed()) + "/s");
                if (weapon.getProjectileCount() > 1) {
                    lore.add(ChatColor.LIGHT_PURPLE + "Projectiles: " + ChatColor.WHITE + weapon.getProjectileCount());
                }
                if (weapon.getAreaOfEffect() > 0) {
                    lore.add(ChatColor.GOLD + "AOE: " + ChatColor.WHITE + String.format("%.1f", weapon.getAreaOfEffect()) + " blocks");
                }
            }
        } else if (teamRun != null) {
            lore.add(ChatColor.GREEN + "Health: " + ChatColor.WHITE + String.format("%.1f", teamRun.getStat("health")));
            lore.add(ChatColor.RED + "Damage: " + ChatColor.WHITE + String.format("%.1f", teamRun.getStat("damage")));
            lore.add(ChatColor.AQUA + "Speed: " + ChatColor.WHITE + String.format("%.1f", teamRun.getStat("speed")));
            lore.add(ChatColor.BLUE + "Armor: " + ChatColor.WHITE + String.format("%.1f", teamRun.getStat("armor")));
            lore.add(ChatColor.DARK_PURPLE + "Crit Chance: " + ChatColor.WHITE + String.format("%.1f%%", teamRun.getStat("crit_chance") * 100));
            lore.add(ChatColor.LIGHT_PURPLE + "Crit Damage: " + ChatColor.WHITE + String.format("%.1fx", teamRun.getStat("crit_damage")));
            lore.add(ChatColor.GOLD + "Luck: " + ChatColor.WHITE + String.format("%.2f", teamRun.getStat("luck")));
            lore.add(ChatColor.YELLOW + "XP Multi: " + ChatColor.WHITE + String.format("%.2fx", teamRun.getStat("xp_multiplier")));
            lore.add(ChatColor.GREEN + "Regeneration: " + ChatColor.WHITE + String.format("%.2f HP/s", teamRun.getStat("regeneration")));
            lore.add(ChatColor.AQUA + "Drop Rate: " + ChatColor.WHITE + String.format("%.1f%%", teamRun.getStat("drop_rate") * 100));
            lore.add(ChatColor.DARK_RED + "Difficulty: " + ChatColor.WHITE + String.format("%.2fx", teamRun.getStat("difficulty")));
            
            // Calculate and display lifesteal from Vampire Aura
            double lifesteal = calculateLifesteal(teamRun);
            if (lifesteal > 0) {
                lore.add(ChatColor.RED + "Lifesteal: " + ChatColor.WHITE + String.format("%.1f%%", lifesteal) + " of damage");
            }
            
            // Show active auras
            List<String> activeAuras = getActiveAuraNames(teamRun);
            if (!activeAuras.isEmpty()) {
                lore.add("");
                lore.add(ChatColor.GOLD + "Active Auras:");
                for (String aura : activeAuras) {
                    lore.add(ChatColor.YELLOW + "  • " + aura);
                }
            }
            
            // Show weapon stats
            Weapon weapon = teamRun.getWeapon();
            if (weapon != null) {
                lore.add("");
                lore.add(ChatColor.GOLD + "Weapon Stats:");
                lore.add(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + weapon.getType().getDisplayName());
                lore.add(ChatColor.YELLOW + "Level: " + ChatColor.WHITE + weapon.getLevel());
                lore.add(ChatColor.RED + "Damage: " + ChatColor.WHITE + String.format("%.1f", weapon.getDamage()));
                lore.add(ChatColor.AQUA + "Range: " + ChatColor.WHITE + String.format("%.1f", weapon.getRange()) + " blocks");
                lore.add(ChatColor.GREEN + "Attack Speed: " + ChatColor.WHITE + String.format("%.2f", weapon.getAttackSpeed()) + "/s");
                if (weapon.getProjectileCount() > 1) {
                    lore.add(ChatColor.LIGHT_PURPLE + "Projectiles: " + ChatColor.WHITE + weapon.getProjectileCount());
                }
                if (weapon.getAreaOfEffect() > 0) {
                    lore.add(ChatColor.GOLD + "AOE: " + ChatColor.WHITE + String.format("%.1f", weapon.getAreaOfEffect()) + " blocks");
                }
            }
        }
        
        statsMeta.setLore(lore);
        statsItem.setItemMeta(statsMeta);
        inventory.setItem(4, statsItem);
    }
    
    private void addCollectedItemsButton() {
        // Collected Items Button (next to stats)
        ItemStack itemsItem = new ItemStack(Material.CHEST);
        ItemMeta itemsMeta = itemsItem.getItemMeta();
        itemsMeta.setDisplayName(ChatColor.GOLD + "Collected Items");
        
        List<String> lore = new ArrayList<>();
        lore.add("");
        
        List<com.eldor.roguecraft.models.GachaItem> collectedItems;
        if (run != null) {
            collectedItems = run.getCollectedGachaItems();
        } else if (teamRun != null) {
            collectedItems = teamRun.getCollectedGachaItems();
        } else {
            collectedItems = new ArrayList<>();
        }
        
        if (collectedItems.isEmpty()) {
            lore.add(ChatColor.GRAY + "No items collected yet.");
            lore.add(ChatColor.GRAY + "Open chests to collect items!");
        } else {
            lore.add(ChatColor.YELLOW + "Items Collected: " + ChatColor.WHITE + collectedItems.size());
            lore.add("");
            
            // Group items by rarity
            Map<com.eldor.roguecraft.models.GachaItem.ItemRarity, Integer> rarityCounts = new LinkedHashMap<>();
            for (com.eldor.roguecraft.models.GachaItem.ItemRarity rarity : com.eldor.roguecraft.models.GachaItem.ItemRarity.values()) {
                rarityCounts.put(rarity, 0);
            }
            
            for (com.eldor.roguecraft.models.GachaItem item : collectedItems) {
                rarityCounts.put(item.getRarity(), rarityCounts.get(item.getRarity()) + 1);
            }
            
            for (Map.Entry<com.eldor.roguecraft.models.GachaItem.ItemRarity, Integer> entry : rarityCounts.entrySet()) {
                if (entry.getValue() > 0) {
                    String rarityColor = entry.getKey().getColorCode();
                    lore.add(rarityColor + entry.getKey().getDisplayName() + ": " + ChatColor.WHITE + entry.getValue());
                }
            }
            
            lore.add("");
            lore.add(ChatColor.GRAY + "Click to view all items");
        }
        
        itemsMeta.setLore(lore);
        itemsItem.setItemMeta(itemsMeta);
        inventory.setItem(8, itemsItem);
    }
    
    private void showCollectedItemsGUI() {
        List<com.eldor.roguecraft.models.GachaItem> collectedItems;
        if (run != null) {
            collectedItems = run.getCollectedGachaItems();
        } else if (teamRun != null) {
            collectedItems = teamRun.getCollectedGachaItems();
        } else {
            collectedItems = new ArrayList<>();
        }
        
        if (collectedItems.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "You haven't collected any items yet!");
            return;
        }
        
        // Create items view GUI
        Inventory itemsGUI = Bukkit.createInventory(null, 54, "§6Collected Items (" + collectedItems.size() + ")");
        
        int slot = 0;
        for (com.eldor.roguecraft.models.GachaItem item : collectedItems) {
            if (slot >= 54) break; // Prevent overflow
            
            ItemStack itemStack = new ItemStack(item.getIcon());
            ItemMeta meta = itemStack.getItemMeta();
            
            String rarityColor = item.getRarity().getColorCode();
            meta.setDisplayName(rarityColor + item.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + item.getDescription());
            lore.add("");
            lore.add(ChatColor.YELLOW + "Rarity: " + rarityColor + item.getRarity().getDisplayName());
            lore.add(ChatColor.YELLOW + "Effect: " + ChatColor.WHITE + item.getEffect().name());
            
            meta.setLore(lore);
            itemStack.setItemMeta(meta);
            itemsGUI.setItem(slot, itemStack);
            slot++;
        }
        
        // Add back button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Back");
        backItem.setItemMeta(backMeta);
        itemsGUI.setItem(49, backItem);
        
        this.itemsGUI = itemsGUI;
        player.openInventory(itemsGUI);
    }

    private void addPowerUpItem(int slot, PowerUp powerUp) {
        ItemStack item = new ItemStack(powerUp.getIcon());
        ItemMeta meta = item.getItemMeta();
        
        // Set display name with rarity color
        ChatColor rarityColor = getRarityColor(powerUp.getRarity());
        meta.setDisplayName(rarityColor + powerUp.getName());
        
        // Build lore
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + powerUp.getDescription());
        lore.add("");
        lore.add(ChatColor.YELLOW + "Rarity: " + rarityColor + powerUp.getRarity().name());
        lore.add(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + powerUp.getType().name());
        
        // Add predicted weapon stats for weapon upgrades
        if (powerUp.getType() == PowerUp.PowerUpType.WEAPON_UPGRADE) {
            Weapon weapon = run != null ? run.getWeapon() : (teamRun != null ? teamRun.getWeapon() : null);
            if (weapon != null) {
                int levels = (int) powerUp.getValue();
                PredictedWeaponStats predicted = calculatePredictedWeaponStats(weapon, levels);
                
                lore.add("");
                lore.add(ChatColor.GOLD + "⚔ Weapon Upgrade Preview:");
                lore.add(ChatColor.GRAY + "Level: " + ChatColor.WHITE + weapon.getLevel() + 
                         ChatColor.GRAY + " → " + ChatColor.GREEN + predicted.newLevel);
                
                if (predicted.damageChange > 0) {
                    lore.add(ChatColor.RED + "  Damage: " + ChatColor.WHITE + String.format("%.1f", weapon.getDamage()) + 
                             ChatColor.GRAY + " → " + ChatColor.GREEN + String.format("%.1f", predicted.newDamage) + 
                             ChatColor.GRAY + " (+" + String.format("%.1f", predicted.damageChange) + ")");
                }
                if (predicted.rangeChange > 0) {
                    lore.add(ChatColor.AQUA + "  Range: " + ChatColor.WHITE + String.format("%.1f", weapon.getRange()) + 
                             ChatColor.GRAY + " → " + ChatColor.GREEN + String.format("%.1f", predicted.newRange) + 
                             ChatColor.GRAY + " (+" + String.format("%.1f", predicted.rangeChange) + " blocks)");
                }
                if (predicted.speedChange > 0) {
                    lore.add(ChatColor.GREEN + "  Attack Speed: " + ChatColor.WHITE + String.format("%.2f", weapon.getAttackSpeed()) + 
                             ChatColor.GRAY + " → " + ChatColor.GREEN + String.format("%.2f", predicted.newAttackSpeed) + 
                             ChatColor.GRAY + " (+" + String.format("%.2f", predicted.speedChange) + "/s)");
                }
                if (predicted.projectileChange > 0) {
                    lore.add(ChatColor.LIGHT_PURPLE + "  Projectiles: " + ChatColor.WHITE + weapon.getProjectileCount() + 
                             ChatColor.GRAY + " → " + ChatColor.GREEN + predicted.newProjectiles + 
                             ChatColor.GRAY + " (+" + predicted.projectileChange + ")");
                }
                if (predicted.aoeChange > 0) {
                    lore.add(ChatColor.GOLD + "  AOE: " + ChatColor.WHITE + String.format("%.1f", weapon.getAreaOfEffect()) + 
                             ChatColor.GRAY + " → " + ChatColor.GREEN + String.format("%.1f", predicted.newAOE) + 
                             ChatColor.GRAY + " (+" + String.format("%.1f", predicted.aoeChange) + " blocks)");
                }
            }
        }
        
        if (powerUp.getSynergies().length > 0) {
            lore.add("");
            lore.add(ChatColor.AQUA + "Synergies:");
            for (String synergy : powerUp.getSynergies()) {
                lore.add(ChatColor.GRAY + "  - " + synergy);
            }
        }
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }
    
    /**
     * Calculate predicted weapon stats after upgrade without modifying the weapon
     */
    private static class PredictedWeaponStats {
        int newLevel;
        double newDamage;
        double newRange;
        double newAttackSpeed;
        int newProjectiles;
        double newAOE;
        double damageChange;
        double rangeChange;
        double speedChange;
        int projectileChange;
        double aoeChange;
    }
    
    private PredictedWeaponStats calculatePredictedWeaponStats(Weapon weapon, int levels) {
        PredictedWeaponStats stats = new PredictedWeaponStats();
        
        // Start with current stats
        int currentLevel = weapon.getLevel();
        double currentDamage = weapon.getDamage();
        double currentRange = weapon.getRange();
        double currentAttackSpeed = weapon.getAttackSpeed();
        int currentProjectiles = weapon.getProjectileCount();
        double currentAOE = weapon.getAreaOfEffect();
        
        // Simulate upgrades
        int newLevel = currentLevel;
        double newDamage = currentDamage;
        double newRange = currentRange;
        double newAttackSpeed = currentAttackSpeed;
        int newProjectiles = currentProjectiles;
        double newAOE = currentAOE;
        
        Weapon.WeaponType type = weapon.getType();
        
        for (int i = 0; i < levels; i++) {
            newLevel++;
            
            // Damage scaling - weapon-specific
            if (type == Weapon.WeaponType.TNT_SPAWNER || type == Weapon.WeaponType.FIREBALL) {
                newDamage *= 1.18;
            } else if (type == Weapon.WeaponType.POTION_THROWER) {
                newDamage *= 1.12;
            } else if (type == Weapon.WeaponType.LIGHTNING_STRIKE) {
                newDamage *= 1.10;
            } else {
                newDamage *= 1.15;
            }
            
            // Range scaling - only increase every 2 levels
            if (newLevel % 2 == 0) {
                double maxRange = type.getBaseRange() * 2.0;
                newRange = Math.min(newRange * 1.05, maxRange);
            }
            
            // Attack speed scaling - weapon-specific
            if (type == Weapon.WeaponType.ARROW_STORM) {
                double maxAttackSpeed = type.getBaseAttackSpeed() * 2.0;
                newAttackSpeed = Math.min(newAttackSpeed * 1.05, maxAttackSpeed);
            } else if (type == Weapon.WeaponType.POTION_THROWER) {
                double maxAttackSpeed = type.getBaseAttackSpeed() * 1.5;
                newAttackSpeed = Math.min(newAttackSpeed * 1.05, maxAttackSpeed);
            } else if (type == Weapon.WeaponType.LIGHTNING_STRIKE) {
                double maxAttackSpeed = type.getBaseAttackSpeed() * 1.5;
                newAttackSpeed = Math.min(newAttackSpeed * 1.05, maxAttackSpeed);
            } else if (type == Weapon.WeaponType.TNT_SPAWNER || type == Weapon.WeaponType.FIREBALL) {
                newAttackSpeed *= 1.12;
            } else {
                newAttackSpeed *= 1.1;
            }
            
            // Projectile count scaling
            if (type == Weapon.WeaponType.ARROW_STORM) {
                if (newLevel % 5 == 0 && newProjectiles < 3) {
                    newProjectiles++;
                }
            } else {
                if (newLevel % 3 == 0) {
                    newProjectiles++;
                }
            }
            
            // AOE scaling - weapon-specific
            if (type == Weapon.WeaponType.POTION_THROWER) {
                double maxAOE = type.getBaseAOE() * 1.3;
                newAOE = Math.min(newAOE * 1.03, maxAOE);
            } else if (type == Weapon.WeaponType.TNT_SPAWNER || type == Weapon.WeaponType.FIREBALL) {
                double maxAOE = type.getBaseAOE() * 2.0;
                newAOE = Math.min(newAOE * 1.07, maxAOE);
            } else {
                double maxAOE = type.getBaseAOE() * 1.5;
                newAOE = Math.min(newAOE * 1.05, maxAOE);
            }
        }
        
        // Store results
        stats.newLevel = newLevel;
        stats.newDamage = newDamage;
        stats.newRange = newRange;
        stats.newAttackSpeed = newAttackSpeed;
        stats.newProjectiles = newProjectiles;
        stats.newAOE = newAOE;
        
        // Calculate changes
        stats.damageChange = newDamage - currentDamage;
        stats.rangeChange = newRange - currentRange;
        stats.speedChange = newAttackSpeed - currentAttackSpeed;
        stats.projectileChange = newProjectiles - currentProjectiles;
        stats.aoeChange = newAOE - currentAOE;
        
        return stats;
    }

    private ChatColor getRarityColor(PowerUp.Rarity rarity) {
        switch (rarity) {
            case COMMON:
                return ChatColor.WHITE;
            case RARE:
                return ChatColor.BLUE;
            case EPIC:
                return ChatColor.DARK_PURPLE;
            case LEGENDARY:
                return ChatColor.GOLD;
            default:
                return ChatColor.GRAY;
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() != player) return;
        
        // Handle items GUI clicks
        if (itemsGUI != null && event.getInventory().equals(itemsGUI)) {
            event.setCancelled(true);
            if (event.getSlot() == 49) {
                // Back button clicked - reopen power-up GUI
                player.closeInventory();
                itemsGUI = null;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    open();
                }, 1L);
            }
            return;
        }
        
        // Handle main power-up GUI clicks
        if (!event.getInventory().equals(inventory)) return;
        
        event.setCancelled(true);

        if (event.getSlot() == 10 && powerUps.size() > 0) {
            selectPowerUp(powerUps.get(0));
        } else if (event.getSlot() == 13 && powerUps.size() > 1) {
            selectPowerUp(powerUps.get(1));
        } else if (event.getSlot() == 16 && powerUps.size() > 2) {
            selectPowerUp(powerUps.get(2));
        } else if (event.getSlot() == 31 && getRerollsRemaining() > 0) {
            reroll();
        } else if (event.getSlot() == 8) {
            // Collected items button clicked
            showCollectedItemsGUI();
        } else if (event.getSlot() == 35) {
            player.closeInventory();
        }
    }

    private void selectPowerUp(PowerUp powerUp) {
        if (run != null) {
            run.addPowerUp(powerUp);
            applyPowerUp(powerUp, run);
        } else if (teamRun != null) {
            teamRun.addPowerUp(powerUp);
            applyPowerUp(powerUp, teamRun);
            
            // Notify team members
            for (Player p : teamRun.getPlayers()) {
                if (p != null && p.isOnline() && !p.getUniqueId().equals(player.getUniqueId())) {
                    p.sendMessage(ChatColor.GREEN + player.getName() + " selected: " + getRarityColor(powerUp.getRarity()) + powerUp.getName());
                }
            }
        }
        
        player.sendMessage(ChatColor.GREEN + "Selected: " + getRarityColor(powerUp.getRarity()) + powerUp.getName());
        player.closeInventory();
    }
    
    // Public method to apply power-up directly (for dropped power-ups)
    public void applyPowerUpDirectly(PowerUp powerUp, Run run) {
        applyPowerUp(powerUp, run);
    }
    
    public void applyPowerUpDirectly(PowerUp powerUp, TeamRun teamRun) {
        applyPowerUp(powerUp, teamRun);
    }
    
    private void applyPowerUp(PowerUp powerUp, Run run) {
        // Apply power-up effects based on type
        switch (powerUp.getType()) {
            case STAT_BOOST:
                applyStatBoost(powerUp, run);
                break;
            case WEAPON_UPGRADE:
                // Upgrade weapon and show stat changes
                if (run.getWeapon() != null) {
                    Weapon weapon = run.getWeapon();
                    int levels = (int) powerUp.getValue();
                    
                    // Store stats before upgrade
                    int oldLevel = weapon.getLevel();
                    double oldDamage = weapon.getDamage();
                    double oldRange = weapon.getRange();
                    double oldAttackSpeed = weapon.getAttackSpeed();
                    int oldProjectiles = weapon.getProjectileCount();
                    double oldAOE = weapon.getAreaOfEffect();
                    
                    // Apply upgrades
                    for (int i = 0; i < levels; i++) {
                        weapon.upgrade();
                    }
                    
                    // Calculate changes
                    double damageChange = weapon.getDamage() - oldDamage;
                    double rangeChange = weapon.getRange() - oldRange;
                    double speedChange = weapon.getAttackSpeed() - oldAttackSpeed;
                    int projectileChange = weapon.getProjectileCount() - oldProjectiles;
                    double aoeChange = weapon.getAreaOfEffect() - oldAOE;
                    
                    // Show upgrade message with stat changes
                    player.sendMessage(ChatColor.GREEN + "⚔ Weapon upgraded to level " + weapon.getLevel() + "!");
                    player.sendMessage(ChatColor.GRAY + "Stat Changes:");
                    if (damageChange > 0) {
                        player.sendMessage(ChatColor.RED + "  Damage: " + ChatColor.WHITE + String.format("%.1f", oldDamage) + 
                                         ChatColor.GRAY + " → " + ChatColor.GREEN + String.format("%.1f", weapon.getDamage()) + 
                                         ChatColor.GRAY + " (+" + String.format("%.1f", damageChange) + ")");
                    }
                    if (rangeChange > 0) {
                        player.sendMessage(ChatColor.AQUA + "  Range: " + ChatColor.WHITE + String.format("%.1f", oldRange) + 
                                         ChatColor.GRAY + " → " + ChatColor.GREEN + String.format("%.1f", weapon.getRange()) + 
                                         ChatColor.GRAY + " (+" + String.format("%.1f", rangeChange) + " blocks)");
                    }
                    if (speedChange > 0) {
                        player.sendMessage(ChatColor.GREEN + "  Attack Speed: " + ChatColor.WHITE + String.format("%.2f", oldAttackSpeed) + 
                                         ChatColor.GRAY + " → " + ChatColor.GREEN + String.format("%.2f", weapon.getAttackSpeed()) + 
                                         ChatColor.GRAY + " (+" + String.format("%.2f", speedChange) + "/s)");
                    }
                    if (projectileChange > 0) {
                        player.sendMessage(ChatColor.LIGHT_PURPLE + "  Projectiles: " + ChatColor.WHITE + oldProjectiles + 
                                         ChatColor.GRAY + " → " + ChatColor.GREEN + weapon.getProjectileCount() + 
                                         ChatColor.GRAY + " (+" + projectileChange + ")");
                    }
                    if (aoeChange > 0) {
                        player.sendMessage(ChatColor.GOLD + "  AOE: " + ChatColor.WHITE + String.format("%.1f", oldAOE) + 
                                         ChatColor.GRAY + " → " + ChatColor.GREEN + String.format("%.1f", weapon.getAreaOfEffect()) + 
                                         ChatColor.GRAY + " (+" + String.format("%.1f", aoeChange) + " blocks)");
                    }
                }
                break;
            case WEAPON_MOD:
                run.addPowerUp(powerUp); // Track for weapon mod effects
                player.sendMessage(ChatColor.YELLOW + "Applied " + powerUp.getName() + "!");
                break;
            case AURA:
                run.addPowerUp(powerUp); // Track for aura effects
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Activated " + powerUp.getName() + "!");
                break;
            case SHRINE:
                run.addPowerUp(powerUp); // Track for shrine cooldowns
                player.sendMessage(ChatColor.GOLD + "Unlocked " + powerUp.getName() + "!");
                break;
            case SYNERGY:
                run.addPowerUp(powerUp); // Track for synergy effects
                // Apply Glass Cannon immediately if selected
                if (powerUp.getName().equals("Glass Cannon")) {
                    applyGlassCannon(powerUp, run, player);
                } else {
                    player.sendMessage(ChatColor.DARK_PURPLE + "Gained " + powerUp.getName() + "!");
                }
                break;
        }
    }
    
    private void applyPowerUp(PowerUp powerUp, TeamRun teamRun) {
        // Apply power-up effects based on type
        switch (powerUp.getType()) {
            case STAT_BOOST:
                applyStatBoost(powerUp, teamRun);
                break;
            case WEAPON_UPGRADE:
                // Upgrade weapon and show stat changes
                if (teamRun.getWeapon() != null) {
                    Weapon weapon = teamRun.getWeapon();
                    int levels = (int) powerUp.getValue();
                    
                    // Store stats before upgrade
                    int oldLevel = weapon.getLevel();
                    double oldDamage = weapon.getDamage();
                    double oldRange = weapon.getRange();
                    double oldAttackSpeed = weapon.getAttackSpeed();
                    int oldProjectiles = weapon.getProjectileCount();
                    double oldAOE = weapon.getAreaOfEffect();
                    
                    // Apply upgrades
                    for (int i = 0; i < levels; i++) {
                        weapon.upgrade();
                    }
                    
                    // Calculate changes
                    double damageChange = weapon.getDamage() - oldDamage;
                    double rangeChange = weapon.getRange() - oldRange;
                    double speedChange = weapon.getAttackSpeed() - oldAttackSpeed;
                    int projectileChange = weapon.getProjectileCount() - oldProjectiles;
                    double aoeChange = weapon.getAreaOfEffect() - oldAOE;
                    
                    // Show upgrade message with stat changes to all team members
                    for (Player p : teamRun.getPlayers()) {
                        if (p != null && p.isOnline()) {
                            p.sendMessage(ChatColor.GREEN + "⚔ Weapon upgraded to level " + weapon.getLevel() + "!");
                            p.sendMessage(ChatColor.GRAY + "Stat Changes:");
                            if (damageChange > 0) {
                                p.sendMessage(ChatColor.RED + "  Damage: " + ChatColor.WHITE + String.format("%.1f", oldDamage) + 
                                             ChatColor.GRAY + " → " + ChatColor.GREEN + String.format("%.1f", weapon.getDamage()) + 
                                             ChatColor.GRAY + " (+" + String.format("%.1f", damageChange) + ")");
                            }
                            if (rangeChange > 0) {
                                p.sendMessage(ChatColor.AQUA + "  Range: " + ChatColor.WHITE + String.format("%.1f", oldRange) + 
                                             ChatColor.GRAY + " → " + ChatColor.GREEN + String.format("%.1f", weapon.getRange()) + 
                                             ChatColor.GRAY + " (+" + String.format("%.1f", rangeChange) + " blocks)");
                            }
                            if (speedChange > 0) {
                                p.sendMessage(ChatColor.GREEN + "  Attack Speed: " + ChatColor.WHITE + String.format("%.2f", oldAttackSpeed) + 
                                             ChatColor.GRAY + " → " + ChatColor.GREEN + String.format("%.2f", weapon.getAttackSpeed()) + 
                                             ChatColor.GRAY + " (+" + String.format("%.2f", speedChange) + "/s)");
                            }
                            if (projectileChange > 0) {
                                p.sendMessage(ChatColor.LIGHT_PURPLE + "  Projectiles: " + ChatColor.WHITE + oldProjectiles + 
                                             ChatColor.GRAY + " → " + ChatColor.GREEN + weapon.getProjectileCount() + 
                                             ChatColor.GRAY + " (+" + projectileChange + ")");
                            }
                            if (aoeChange > 0) {
                                p.sendMessage(ChatColor.GOLD + "  AOE: " + ChatColor.WHITE + String.format("%.1f", oldAOE) + 
                                             ChatColor.GRAY + " → " + ChatColor.GREEN + String.format("%.1f", weapon.getAreaOfEffect()) + 
                                             ChatColor.GRAY + " (+" + String.format("%.1f", aoeChange) + " blocks)");
                            }
                        }
                    }
                }
                break;
            case WEAPON_MOD:
                teamRun.addPowerUp(powerUp); // Track for weapon mod effects
                player.sendMessage(ChatColor.YELLOW + "Applied " + powerUp.getName() + "!");
                break;
            case AURA:
                teamRun.addPowerUp(powerUp); // Track for aura effects
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Activated " + powerUp.getName() + "!");
                break;
            case SHRINE:
                teamRun.addPowerUp(powerUp); // Track for shrine cooldowns
                player.sendMessage(ChatColor.GOLD + "Unlocked " + powerUp.getName() + "!");
                break;
            case SYNERGY:
                teamRun.addPowerUp(powerUp); // Track for synergy effects
                // Apply Glass Cannon immediately if selected
                if (powerUp.getName().equals("Glass Cannon")) {
                    applyGlassCannon(powerUp, teamRun, player);
                } else {
                    player.sendMessage(ChatColor.DARK_PURPLE + "Gained " + powerUp.getName() + "!");
                }
                break;
        }
    }
    
    private void applyStatBoost(PowerUp powerUp, Run run) {
        String id = powerUp.getId().toLowerCase();
        if (id.contains("health")) {
            run.addStat("health", powerUp.getValue());
            applyHealthAttribute(player, run.getStat("health"));
            player.sendMessage(ChatColor.GREEN + "+" + String.format("%.1f", powerUp.getValue()) + " Health!");
        } else if (id.contains("damage")) {
            run.addStat("damage", powerUp.getValue());
            player.sendMessage(ChatColor.RED + "+" + String.format("%.1f", powerUp.getValue()) + " Damage!");
        } else if (id.contains("speed")) {
            run.addStat("speed", powerUp.getValue());
            applySpeedAttribute(player, run.getStat("speed"));
            player.sendMessage(ChatColor.AQUA + "+" + String.format("%.2f", powerUp.getValue()) + " Speed!");
        } else if (id.contains("armor")) {
            run.addStat("armor", powerUp.getValue());
            applyArmorAttribute(player, run.getStat("armor"));
            player.sendMessage(ChatColor.BLUE + "+" + String.format("%.1f", powerUp.getValue()) + " Armor!");
        } else if (id.contains("crit_chance")) {
            run.addStat("crit_chance", powerUp.getValue());
            player.sendMessage(ChatColor.DARK_PURPLE + "+" + String.format("%.1f%%", powerUp.getValue() * 100) + " Crit Chance!");
        } else if (id.contains("crit_damage")) {
            run.addStat("crit_damage", powerUp.getValue());
            player.sendMessage(ChatColor.LIGHT_PURPLE + "+" + String.format("%.2fx", powerUp.getValue()) + " Crit Damage!");
        } else if (id.contains("luck")) {
            run.addStat("luck", powerUp.getValue());
            player.sendMessage(ChatColor.GOLD + "+" + String.format("%.2f", powerUp.getValue()) + " Luck!");
        } else if (id.contains("xp_multiplier")) {
            run.addStat("xp_multiplier", powerUp.getValue());
            player.sendMessage(ChatColor.YELLOW + "+" + String.format("%.1f%%", powerUp.getValue() * 100) + " XP Gain!");
        } else if (id.contains("regeneration") || id.contains("regen")) {
            run.addStat("regeneration", powerUp.getValue());
            player.sendMessage(ChatColor.GREEN + "+" + String.format("%.2f", powerUp.getValue()) + " HP/s Regeneration!");
        } else if (id.contains("drop_rate") || id.contains("droprate") || id.contains("drop")) {
            run.addStat("drop_rate", powerUp.getValue());
            player.sendMessage(ChatColor.AQUA + "+" + String.format("%.1f%%", powerUp.getValue() * 100) + " Drop Rate!");
        } else if (id.contains("difficulty")) {
            run.addStat("difficulty", powerUp.getValue());
            player.sendMessage(ChatColor.DARK_RED + "+" + String.format("%.2fx", powerUp.getValue()) + " Difficulty!");
            player.sendMessage(ChatColor.GRAY + "Enemies will be harder, but rewards are greater!");
        }
    }
    
    private void applyStatBoost(PowerUp powerUp, TeamRun teamRun) {
        String id = powerUp.getId().toLowerCase();
        if (id.contains("health")) {
            teamRun.addStat("health", powerUp.getValue());
            // Apply health to all team members
            for (Player p : teamRun.getPlayers()) {
                if (p != null && p.isOnline()) {
                    applyHealthAttribute(p, teamRun.getStat("health"));
                }
            }
            player.sendMessage(ChatColor.GREEN + "+" + String.format("%.1f", powerUp.getValue()) + " Health!");
        } else if (id.contains("damage")) {
            teamRun.addStat("damage", powerUp.getValue());
            player.sendMessage(ChatColor.RED + "+" + String.format("%.1f", powerUp.getValue()) + " Damage!");
        } else if (id.contains("speed")) {
            teamRun.addStat("speed", powerUp.getValue());
            // Apply speed to all team members
            for (Player p : teamRun.getPlayers()) {
                if (p != null && p.isOnline()) {
                    applySpeedAttribute(p, teamRun.getStat("speed"));
                }
            }
            player.sendMessage(ChatColor.AQUA + "+" + String.format("%.2f", powerUp.getValue()) + " Speed!");
        } else if (id.contains("armor")) {
            teamRun.addStat("armor", powerUp.getValue());
            // Apply armor to all team members
            for (Player p : teamRun.getPlayers()) {
                if (p != null && p.isOnline()) {
                    applyArmorAttribute(p, teamRun.getStat("armor"));
                }
            }
            player.sendMessage(ChatColor.BLUE + "+" + String.format("%.1f", powerUp.getValue()) + " Armor!");
        } else if (id.contains("crit_chance")) {
            teamRun.addStat("crit_chance", powerUp.getValue());
            player.sendMessage(ChatColor.DARK_PURPLE + "+" + String.format("%.1f%%", powerUp.getValue() * 100) + " Crit Chance!");
        } else if (id.contains("crit_damage")) {
            teamRun.addStat("crit_damage", powerUp.getValue());
            player.sendMessage(ChatColor.LIGHT_PURPLE + "+" + String.format("%.2fx", powerUp.getValue()) + " Crit Damage!");
        } else if (id.contains("luck")) {
            teamRun.addStat("luck", powerUp.getValue());
            player.sendMessage(ChatColor.GOLD + "+" + String.format("%.2f", powerUp.getValue()) + " Luck!");
        } else if (id.contains("xp_multiplier")) {
            teamRun.addStat("xp_multiplier", powerUp.getValue());
            player.sendMessage(ChatColor.YELLOW + "+" + String.format("%.1f%%", powerUp.getValue() * 100) + " XP Gain!");
        } else if (id.contains("regeneration") || id.contains("regen")) {
            teamRun.addStat("regeneration", powerUp.getValue());
            player.sendMessage(ChatColor.GREEN + "+" + String.format("%.2f", powerUp.getValue()) + " HP/s Regeneration!");
        } else if (id.contains("drop_rate") || id.contains("droprate") || id.contains("drop")) {
            teamRun.addStat("drop_rate", powerUp.getValue());
            player.sendMessage(ChatColor.AQUA + "+" + String.format("%.1f%%", powerUp.getValue() * 100) + " Drop Rate!");
        } else if (id.contains("difficulty")) {
            teamRun.addStat("difficulty", powerUp.getValue());
            // Notify all team members
            for (Player p : teamRun.getPlayers()) {
                if (p != null && p.isOnline()) {
                    p.sendMessage(ChatColor.DARK_RED + "+" + String.format("%.2fx", powerUp.getValue()) + " Difficulty!");
                    p.sendMessage(ChatColor.GRAY + "Enemies will be harder, but rewards are greater!");
                }
            }
        }
    }
    
    private void applyHealthAttribute(Player player, double health) {
        // Apply health as max health attribute
        org.bukkit.attribute.Attribute healthAttr = org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH;
        org.bukkit.attribute.AttributeInstance healthInstance = player.getAttribute(healthAttr);
        if (healthInstance != null) {
            healthInstance.setBaseValue(health);
            // Heal player to max health when increasing
            if (player.getHealth() < health) {
                player.setHealth(health);
            }
        }
    }
    
    private void applySpeedAttribute(Player player, double speed) {
        // Apply speed as movement speed attribute
        // Base speed is 0.1, we scale from there
        double baseSpeed = 0.1;
        double newSpeed = baseSpeed * speed;
        // Clamp between 0 and 1 (Minecraft limits)
        newSpeed = Math.max(0.0, Math.min(1.0, newSpeed));
        
        org.bukkit.attribute.Attribute speedAttr = org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED;
        org.bukkit.attribute.AttributeInstance speedInstance = player.getAttribute(speedAttr);
        if (speedInstance != null) {
            speedInstance.setBaseValue(newSpeed);
        }
    }
    
    private void applyArmorAttribute(Player player, double armor) {
        // Apply armor as GENERIC_ARMOR attribute (visible in HUD like hearts)
        org.bukkit.attribute.Attribute armorAttr = org.bukkit.attribute.Attribute.GENERIC_ARMOR;
        org.bukkit.attribute.AttributeInstance armorInstance = player.getAttribute(armorAttr);
        if (armorInstance != null) {
            armorInstance.setBaseValue(armor);
        }
    }
    
    /**
     * Calculate lifesteal percentage from Vampire Aura power-ups
     */
    private double calculateLifesteal(Object run) {
        if (run == null) return 0.0;
        
        List<com.eldor.roguecraft.models.PowerUp> powerUps;
        if (run instanceof com.eldor.roguecraft.models.TeamRun) {
            powerUps = ((com.eldor.roguecraft.models.TeamRun) run).getCollectedPowerUps();
        } else if (run instanceof com.eldor.roguecraft.models.Run) {
            powerUps = ((com.eldor.roguecraft.models.Run) run).getCollectedPowerUps();
        } else {
            return 0.0;
        }
        
        double totalLifesteal = 0.0;
        for (com.eldor.roguecraft.models.PowerUp powerUp : powerUps) {
            if (powerUp.getType() == com.eldor.roguecraft.models.PowerUp.PowerUpType.AURA) {
                String name = powerUp.getName().toLowerCase();
                if (name.contains("vampire") || name.contains("lifesteal")) {
                    // Vampire aura value represents lifesteal percentage (e.g., value 1.0 = 2% lifesteal)
                    totalLifesteal += powerUp.getValue() * 2.0; // Convert to percentage
                }
            }
        }
        
        return totalLifesteal;
    }
    
    /**
     * Get list of active aura names
     */
    private List<String> getActiveAuraNames(Object run) {
        List<String> formattedAuras = new ArrayList<>();
        if (run == null) return formattedAuras;

        List<com.eldor.roguecraft.models.PowerUp> powerUps;
        if (run instanceof com.eldor.roguecraft.models.TeamRun) {
            powerUps = ((com.eldor.roguecraft.models.TeamRun) run).getCollectedPowerUps();
        } else if (run instanceof com.eldor.roguecraft.models.Run) {
            powerUps = ((com.eldor.roguecraft.models.Run) run).getCollectedPowerUps();
        } else {
            return formattedAuras;
        }

        Map<String, Integer> auraCounts = new LinkedHashMap<>();
        for (com.eldor.roguecraft.models.PowerUp powerUp : powerUps) {
            if (powerUp.getType() == com.eldor.roguecraft.models.PowerUp.PowerUpType.AURA) {
                String name = powerUp.getName();
                auraCounts.put(name, auraCounts.getOrDefault(name, 0) + 1);
            }
        }

        for (Map.Entry<String, Integer> entry : auraCounts.entrySet()) {
            String name = entry.getKey();
            int count = entry.getValue();
            if (count > 1) {
                formattedAuras.add(name + " x" + count);
            } else {
                formattedAuras.add(name);
            }
        }

        return formattedAuras;
    }

    private void reroll() {
        useReroll();
        powerUps.clear();
        // Use dynamic generation with current luck
        double luck = run != null ? run.getStat("luck") : (teamRun != null ? teamRun.getStat("luck") : 1.0);
        Object currentRun = run != null ? run : teamRun;
        powerUps.addAll(plugin.getPowerUpManager().generateDynamicPowerUps(getLevel(), luck, currentRun));
        setupGUI();
        player.sendMessage(ChatColor.YELLOW + "Power-ups rerolled! " + getRerollsRemaining() + " rerolls remaining.");
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory) && event.getPlayer() == player) {
            // Mark player as no longer in GUI
            if (teamRun != null) {
                teamRun.setPlayerInGUI(player.getUniqueId(), false);
                
                // Only restart ALL players' weapon auto-attacks if NO other players are in GUI
                // This ensures the game stays paused while any player is selecting a power-up
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
    
    private void applyGlassCannon(PowerUp powerUp, Run run, Player player) {
        // Glass Cannon: +damage, -50% max HP
        double damageBoost = powerUp.getValue() * 100.0; // 100% per value point
        run.addStat("damage", powerUp.getValue());
        
        // Reduce max HP by 50%
        double currentHealth = run.getStat("health");
        double newHealth = currentHealth * 0.5; // 50% reduction
        run.setStat("health", newHealth);
        
        // Apply new health attribute
        applyHealthAttribute(player, newHealth);
        
        player.sendMessage(ChatColor.RED + "§lGLASS CANNON! §r§c+" + String.format("%.0f%%", damageBoost) + " Damage, -50% Max HP!");
        player.getWorld().spawnParticle(org.bukkit.Particle.CRIT, player.getLocation(), 30, 0.5, 1.0, 0.5, 0.1);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 0.8f);
    }
    
    private void applyGlassCannon(PowerUp powerUp, TeamRun teamRun, Player player) {
        // Glass Cannon: +damage, -50% max HP
        double damageBoost = powerUp.getValue() * 100.0; // 100% per value point
        teamRun.addStat("damage", powerUp.getValue());
        
        // Reduce max HP by 50%
        double currentHealth = teamRun.getStat("health");
        double newHealth = currentHealth * 0.5; // 50% reduction
        teamRun.setStat("health", newHealth);
        
        // Apply new health attribute to all team members
        for (Player p : teamRun.getPlayers()) {
            if (p != null && p.isOnline()) {
                applyHealthAttribute(p, newHealth);
            }
        }
        
        // Notify all team members
        for (Player p : teamRun.getPlayers()) {
            if (p != null && p.isOnline()) {
                p.sendMessage(ChatColor.RED + "§lGLASS CANNON! §r§c+" + String.format("%.0f%%", damageBoost) + " Damage, -50% Max HP!");
                p.getWorld().spawnParticle(org.bukkit.Particle.CRIT, p.getLocation(), 30, 0.5, 1.0, 0.5, 0.1);
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 0.8f);
            }
        }
    }
    
}
