package com.eldor.roguecraft.gui;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.Shrine;
import com.eldor.roguecraft.models.TeamRun;
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
        List<ShrineBuff> buffs = generateBuffVariants();
        
        inventory.setItem(10, createBuffItem(buffs.get(0), 0));
        inventory.setItem(12, createBuffItem(buffs.get(1), 1));
        inventory.setItem(14, createBuffItem(buffs.get(2), 2));
    }
    
    private List<ShrineBuff> generateBuffVariants() {
        List<ShrineBuff> buffs = new ArrayList<>();
        Random random = new Random();
        
        // Each shrine type has 3 variants with different tradeoffs
        switch (shrine.getType()) {
            case POWER:
                buffs.add(new ShrineBuff("Overwhelming Power", "4x damage for 8 seconds", ChatColor.RED, Material.DIAMOND_SWORD, "power_4x_8s"));
                buffs.add(new ShrineBuff("Sustained Power", "2.5x damage for 15 seconds", ChatColor.RED, Material.IRON_SWORD, "power_2.5x_15s"));
                buffs.add(new ShrineBuff("Explosive Power", "3x damage + 50% AOE for 10 seconds", ChatColor.RED, Material.TNT, "power_3x_10s_aoe"));
                break;
            case SWIFTNESS:
                buffs.add(new ShrineBuff("Lightning Speed", "3x speed for 10 seconds", ChatColor.AQUA, Material.FEATHER, "speed_3x_10s"));
                buffs.add(new ShrineBuff("Marathon Runner", "2x speed for 20 seconds", ChatColor.AQUA, Material.LEATHER_BOOTS, "speed_2x_20s"));
                buffs.add(new ShrineBuff("Blink", "Teleport to target + 2x speed for 12s", ChatColor.AQUA, Material.ENDER_PEARL, "blink_2x_12s"));
                break;
            case VITALITY:
                buffs.add(new ShrineBuff("Full Restoration", "Instant full heal + regen III for 15s", ChatColor.GREEN, Material.GOLDEN_APPLE, "heal_full_regen_15s"));
                buffs.add(new ShrineBuff("Overheal", "+100% max HP for 25 seconds", ChatColor.GREEN, Material.ENCHANTED_GOLDEN_APPLE, "overheal_25s"));
                buffs.add(new ShrineBuff("Regeneration", "Heal 2 HP/sec for 30 seconds", ChatColor.GREEN, Material.GLISTERING_MELON_SLICE, "regen_30s"));
                break;
            case FORTUNE:
                buffs.add(new ShrineBuff("Fortune's Favor", "5x XP for 25 seconds", ChatColor.YELLOW, Material.EXPERIENCE_BOTTLE, "xp_5x_25s"));
                buffs.add(new ShrineBuff("Lucky Streak", "3x XP + double luck for 40s", ChatColor.YELLOW, Material.RABBIT_FOOT, "xp_3x_luck_40s"));
                buffs.add(new ShrineBuff("Treasure Hunter", "4x XP + rare power-up on kill", ChatColor.YELLOW, Material.EMERALD, "xp_4x_rare"));
                break;
            case FURY:
                buffs.add(new ShrineBuff("Critical Rage", "100% crit + 2x crit damage for 8s", ChatColor.GOLD, Material.BLAZE_POWDER, "crit_100_2x_8s"));
                buffs.add(new ShrineBuff("Berserker", "75% crit + 50% attack speed for 12s", ChatColor.GOLD, Material.FIRE_CHARGE, "crit_75_atkspd_12s"));
                buffs.add(new ShrineBuff("Precision", "80% crit + crits never miss for 10s", ChatColor.GOLD, Material.ARROW, "crit_80_10s"));
                break;
            case PROTECTION:
                buffs.add(new ShrineBuff("Invulnerability", "Immune to all damage for 5 seconds", ChatColor.LIGHT_PURPLE, Material.TOTEM_OF_UNDYING, "invuln_5s"));
                buffs.add(new ShrineBuff("Iron Skin", "75% damage reduction for 15 seconds", ChatColor.LIGHT_PURPLE, Material.IRON_CHESTPLATE, "reduce_75_15s"));
                buffs.add(new ShrineBuff("Shield Bubble", "Absorb 50 damage over 20 seconds", ChatColor.LIGHT_PURPLE, Material.SHIELD, "shield_50_20s"));
                break;
            case CHAOS:
                buffs.add(new ShrineBuff("Wild Magic", "3 random powerful buffs for 12s", ChatColor.DARK_PURPLE, Material.NETHER_STAR, "wild_magic_12s"));
                buffs.add(new ShrineBuff("Chaos Storm", "Enemies take random debuffs for 15s", ChatColor.DARK_PURPLE, Material.DRAGON_BREATH, "chaos_storm_15s"));
                buffs.add(new ShrineBuff("Gambler's Dream", "Roll 1-10x power for 10 seconds", ChatColor.DARK_PURPLE, Material.ENCHANTED_BOOK, "gambler_10s"));
                break;
            case TIME:
                buffs.add(new ShrineBuff("Time Stop", "Freeze all enemies for 10 seconds", ChatColor.WHITE, Material.CLOCK, "timestop_10s"));
                buffs.add(new ShrineBuff("Slow Time", "Enemies 90% slower for 20 seconds", ChatColor.WHITE, Material.COBWEB, "slow_90_20s"));
                buffs.add(new ShrineBuff("Haste", "You move 50% faster + enemies 50% slower for 15s", ChatColor.WHITE, Material.SUGAR, "haste_50_15s"));
                break;
        }
        
        return buffs;
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
            List<ShrineBuff> buffs = generateBuffVariants();
            ShrineBuff selectedBuff = buffs.get(buffIndex);
            
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
        
        // Prevent duplicate processing - check if already unregistered
        if (!InventoryClickEvent.getHandlerList().getRegisteredListeners(plugin).contains(this)) {
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
            
            // Restart weapon auto-attack (only if not already running)
            // WeaponManager.startAutoAttack already stops any existing task first
            if (teamRun.getWeapon() != null) {
                plugin.getWeaponManager().startAutoAttack(player, teamRun.getWeapon());
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
        
        ShrineBuff(String name, String description, ChatColor color, Material icon, String effectType) {
            this.name = name;
            this.description = description;
            this.color = color;
            this.icon = icon;
            this.effectType = effectType;
        }
    }
}

