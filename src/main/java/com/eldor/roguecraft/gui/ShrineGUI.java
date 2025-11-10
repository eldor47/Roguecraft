package com.eldor.roguecraft.gui;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.Shrine;
import com.eldor.roguecraft.models.TeamRun;
import com.eldor.roguecraft.models.Run;
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

import java.util.*;

/**
 * GUI for shrine buff selection
 */
public class ShrineGUI implements Listener {
    private final RoguecraftPlugin plugin;
    private final Player player;
    private final Shrine shrine;
    private final UUID teamId;
    private final Inventory inventory;
    private List<ShrineBuff> generatedBuffs; // Store generated buffs
    
    public ShrineGUI(RoguecraftPlugin plugin, Player player, Shrine shrine, UUID teamId) {
        this.plugin = plugin;
        this.player = player;
        this.shrine = shrine;
        this.teamId = teamId;
        this.inventory = Bukkit.createInventory(null, 27, ChatColor.GOLD + "⚡ " + shrine.getType().getName());
        
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        // Game is already frozen from channeling, just maintain the state
        // (TeamRun.setPlayerInGUI was already called during channeling)
        
        setupGUI();
    }
    
    private void setupGUI() {
        // Border
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.setDisplayName(" ");
        border.setItemMeta(borderMeta);
        
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) {
                inventory.setItem(i, border);
            }
        }
        
        // Shrine info
        ItemStack info = new ItemStack(shrine.getType().getLightMaterial());
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + shrine.getType().getName());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + shrine.getType().getDescription());
        lore.add("");
        lore.add(ChatColor.YELLOW + "Choose a buff variant:");
        infoMeta.setLore(lore);
        info.setItemMeta(infoMeta);
        inventory.setItem(4, info);
        
        // Generate 3 variants of the shrine buff
        generatedBuffs = generateBuffVariants();
        
        inventory.setItem(10, createBuffItem(generatedBuffs.get(0), 0));
        inventory.setItem(12, createBuffItem(generatedBuffs.get(1), 1));
        inventory.setItem(14, createBuffItem(generatedBuffs.get(2), 2));
    }
    
    private List<ShrineBuff> generateBuffVariants() {
        List<ShrineBuff> buffs = new ArrayList<>();
        Random random = new Random();
        
        // Get player's run info for level and luck
        TeamRun teamRun = plugin.getRunManager().getTeamRun(player.getUniqueId());
        Run run = null;
        if (teamRun == null) {
            run = plugin.getRunManager().getRun(player.getUniqueId());
        }
        
        int playerLevel = 1;
        double luck = 1.0;
        if (teamRun != null) {
            playerLevel = teamRun.getLevel();
            luck = teamRun.getStat("luck");
        } else if (run != null) {
            playerLevel = run.getLevel();
            luck = run.getStat("luck");
        }
        
        // Generate 3 unique power-ups (excluding weapon-related ones)
        Set<String> usedTypes = new HashSet<>();
        int maxAttempts = 50;
        int attempts = 0;
        
        while (buffs.size() < 3 && attempts < maxAttempts) {
            attempts++;
            double roll = random.nextDouble();
            com.eldor.roguecraft.models.PowerUp powerUp = null;
            String uniqueKey = null;
            
            // 8% chance for jump height (rarer pull)
            if (roll < 0.08) {
                powerUp = createJumpHeightPowerUp(playerLevel, luck);
                uniqueKey = "jump_height";
            } else {
                // 92% chance for stat boost (damage, regen, crit, speed, health, etc.)
                // Exclude regeneration if capped, but allow it for shrines
                boolean excludeRegen = false;
                if (teamRun != null) {
                    excludeRegen = teamRun.getStat("regeneration") >= 4.0;
                } else if (run != null) {
                    excludeRegen = run.getStat("regeneration") >= 4.0;
                }
                
                powerUp = com.eldor.roguecraft.models.DynamicPowerUp.generateStatBoost(playerLevel, luck, excludeRegen);
                // Extract stat name for uniqueness
                String statName = powerUp.getId().replaceAll("dynamic_", "").replaceAll("_\\d+", "");
                uniqueKey = "stat_" + statName;
            }
            
            // Only add if not already present and not null
            if (powerUp != null && !usedTypes.contains(uniqueKey)) {
                // Convert PowerUp to ShrineBuff
                ChatColor color = getColorForRarity(powerUp.getRarity());
                Material icon = powerUp.getIcon();
                ShrineBuff buff = new ShrineBuff(
                    powerUp.getName(),
                    powerUp.getDescription(),
                    color,
                    icon,
                    powerUp.getId(), // Store power-up ID as effectType
                    powerUp // Store the actual power-up object
                );
                buffs.add(buff);
                usedTypes.add(uniqueKey);
            }
        }
        
        // If we couldn't generate 3, fill with random stat boosts
        while (buffs.size() < 3) {
            com.eldor.roguecraft.models.PowerUp powerUp = com.eldor.roguecraft.models.DynamicPowerUp.generateStatBoost(playerLevel, luck, false);
            ChatColor color = getColorForRarity(powerUp.getRarity());
            ShrineBuff buff = new ShrineBuff(
                powerUp.getName(),
                powerUp.getDescription(),
                color,
                powerUp.getIcon(),
                powerUp.getId()
            );
            buffs.add(buff);
        }
        
        return buffs;
    }
    
    private com.eldor.roguecraft.models.PowerUp createJumpHeightPowerUp(int playerLevel, double luck) {
        // Jump height power-up (rarer)
        // Determine rarity based on luck (similar to DynamicPowerUp logic)
        com.eldor.roguecraft.models.PowerUp.Rarity rarity;
        double roll = new Random().nextDouble() * luck;
        if (roll < 0.5) {
            rarity = com.eldor.roguecraft.models.PowerUp.Rarity.COMMON;
        } else if (roll < 0.8) {
            rarity = com.eldor.roguecraft.models.PowerUp.Rarity.RARE;
        } else if (roll < 0.95) {
            rarity = com.eldor.roguecraft.models.PowerUp.Rarity.EPIC;
        } else {
            rarity = com.eldor.roguecraft.models.PowerUp.Rarity.LEGENDARY;
        }
        
        // Jump height values: 0.5, 1.0, 1.5, 2.0 (provides slow falling effect)
        double jumpHeight = 0.5; // Base: 0.5 jump height
        if (rarity == com.eldor.roguecraft.models.PowerUp.Rarity.COMMON) {
            jumpHeight = 0.5; // Level 1 slow falling
        } else if (rarity == com.eldor.roguecraft.models.PowerUp.Rarity.RARE) {
            jumpHeight = 1.0; // Level 2 slow falling
        } else if (rarity == com.eldor.roguecraft.models.PowerUp.Rarity.EPIC) {
            jumpHeight = 1.5; // Level 3 slow falling
        } else if (rarity == com.eldor.roguecraft.models.PowerUp.Rarity.LEGENDARY) {
            jumpHeight = 2.0; // Level 4 slow falling (max)
        }
        
        // Calculate slow falling level for description
        int slowFallingLevel = (int) Math.min(4, Math.floor(jumpHeight / 0.5));
        
        return new com.eldor.roguecraft.models.PowerUp(
            "shrine_jump_height_" + System.currentTimeMillis(),
            "Jump Height",
            "Increase jump height by +" + String.format("%.1f", jumpHeight) + " (Slow Falling " + slowFallingLevel + ")",
            rarity,
            com.eldor.roguecraft.models.PowerUp.PowerUpType.STAT_BOOST,
            org.bukkit.Material.FEATHER,
            jumpHeight,
            new String[0]
        );
    }
    
    private ChatColor getColorForRarity(com.eldor.roguecraft.models.PowerUp.Rarity rarity) {
        switch (rarity) {
            case COMMON: return ChatColor.GREEN;
            case RARE: return ChatColor.BLUE;
            case EPIC: return ChatColor.LIGHT_PURPLE;
            case LEGENDARY: return ChatColor.GOLD;
            default: return ChatColor.WHITE;
        }
    }
    
    private ItemStack createBuffItem(ShrineBuff buff, int slot) {
        ItemStack item = new ItemStack(buff.icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(buff.color + "" + ChatColor.BOLD + buff.name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + buff.description);
        lore.add("");
        lore.add(ChatColor.GREEN + "Click to activate!");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
    
    public void open() {
        // Close player's inventory if it's open (prevents conflicts)
        if (player.getOpenInventory() != null && player.getOpenInventory().getTopInventory() != null) {
            // Only close if it's not already our GUI
            if (!player.getOpenInventory().getTopInventory().equals(inventory)) {
                player.closeInventory();
                // Small delay to ensure inventory is fully closed before opening new one
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.openInventory(inventory);
                    }
                }, 1L);
                return;
            }
        }
        
        player.openInventory(inventory);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);
        
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player clicker = (Player) event.getWhoClicked();
        if (!clicker.equals(player)) return;
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        // Check if it's one of the buff options (slots 10, 12, 14)
        int slot = event.getSlot();
        if (slot == 10 || slot == 12 || slot == 14) {
            // Get which buff variant was selected
            int buffIndex = (slot == 10) ? 0 : (slot == 12) ? 1 : 2;
            ShrineBuff selectedBuff = generatedBuffs.get(buffIndex);
            
            // Unregister this listener FIRST to prevent any re-triggering
            InventoryClickEvent.getHandlerList().unregister(this);
            InventoryCloseEvent.getHandlerList().unregister(this);
            
            // Mark player as no longer in GUI and unfreeze IMMEDIATELY (BEFORE closing)
            TeamRun teamRun = plugin.getRunManager().getTeamRun(player.getUniqueId());
            if (teamRun != null) {
                teamRun.setPlayerInGUI(player.getUniqueId(), false);
            }
            
            // Close inventory FIRST (this will trigger onInventoryClose)
            player.closeInventory();
            
            // Notify shrine manager that GUI is closing (AFTER closing to ensure state is cleared)
            plugin.getShrineManager().onShrineGUIClosed(player);
            
            // Also notify GUI manager to clear its tracking
            plugin.getGuiManager().onGUIClosed(player);
            
            // Apply the buff and start cooldown (with variant info)
            plugin.getShrineManager().applyShrineBuffAndStartCooldown(player, shrine, shrine.getType(), selectedBuff);
            
            // Restart weapon auto-attack
            if (teamRun != null && teamRun.getWeapon() != null) {
                plugin.getWeaponManager().startAutoAttack(player, teamRun.getWeapon());
            }
            
            player.sendMessage(ChatColor.GREEN + "✦ " + selectedBuff.name + " activated!");
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!(event.getPlayer() instanceof Player)) return;
        Player clicker = (Player) event.getPlayer();
        if (!clicker.equals(player)) return;
        
        // Prevent duplicate processing - use a flag to track if we've already processed
        // Check if listeners are still registered (if not, we've already processed)
        boolean clickListenerRegistered = InventoryClickEvent.getHandlerList().getRegisteredListeners(plugin).contains(this);
        boolean closeListenerRegistered = InventoryCloseEvent.getHandlerList().getRegisteredListeners(plugin).contains(this);
        
        if (!clickListenerRegistered && !closeListenerRegistered) {
            // Already processed, skip
            return;
        }
        
        // Unregister this listener FIRST to prevent re-triggering
        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryCloseEvent.getHandlerList().unregister(this);
        
        // Mark player as no longer in GUI and unfreeze
        TeamRun teamRun = plugin.getRunManager().getTeamRun(player.getUniqueId());
        if (teamRun != null) {
            teamRun.setPlayerInGUI(player.getUniqueId(), false);
            
            // Restart weapon auto-attack for all players if no one else is in GUI
            if (!teamRun.hasAnyPlayerInGUI() && teamRun.getWeapon() != null) {
                for (Player teamPlayer : teamRun.getPlayers()) {
                    if (teamPlayer != null && teamPlayer.isOnline()) {
                        plugin.getWeaponManager().startAutoAttack(teamPlayer, teamRun.getWeapon());
                    }
                }
            }
        } else {
            // Solo run - check if there's a Run object
            Run run = plugin.getRunManager().getRun(player.getUniqueId());
            if (run != null && run.getWeapon() != null) {
                plugin.getWeaponManager().startAutoAttack(player, run.getWeapon());
            }
        }
        
        // Notify shrine manager that GUI is closing
        plugin.getShrineManager().onShrineGUIClosed(player);
        
        // Notify GUI manager that GUI is closing (process queue) - THIS MUST BE CALLED
        plugin.getGuiManager().onGUIClosed(player);
    }
    
    public static class ShrineBuff {
        public String name;
        public String description;
        public ChatColor color;
        public Material icon;
        public String effectType; // For applying the actual effect
        public com.eldor.roguecraft.models.PowerUp powerUp; // Store the actual power-up object
        
        ShrineBuff(String name, String description, ChatColor color, Material icon, String effectType) {
            this.name = name;
            this.description = description;
            this.color = color;
            this.icon = icon;
            this.effectType = effectType;
            this.powerUp = null;
        }
        
        ShrineBuff(String name, String description, ChatColor color, Material icon, String effectType, com.eldor.roguecraft.models.PowerUp powerUp) {
            this.name = name;
            this.description = description;
            this.color = color;
            this.icon = icon;
            this.effectType = effectType;
            this.powerUp = powerUp;
        }
    }
}

