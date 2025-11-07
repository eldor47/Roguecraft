package com.eldor.roguecraft.gui;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.Weapon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import java.util.function.Consumer;

public class WeaponSelectionGUI implements Listener {
    private final RoguecraftPlugin plugin;
    private final Player player;
    private final Inventory inventory;
    private final Consumer<Weapon.WeaponType> onSelect;
    
    public WeaponSelectionGUI(RoguecraftPlugin plugin, Player player, Consumer<Weapon.WeaponType> onSelect) {
        this.plugin = plugin;
        this.player = player;
        this.onSelect = onSelect;
        this.inventory = Bukkit.createInventory(null, 27, ChatColor.GOLD + "Choose Your Weapon");
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    public void open() {
        setupGUI();
        player.openInventory(inventory);
    }
    
    private void setupGUI() {
        inventory.clear();
        
        // Add all weapon types
        Weapon.WeaponType[] types = Weapon.WeaponType.values();
        int[] slots = {10, 11, 12, 13, 14, 15, 16}; // Center slots
        
        for (int i = 0; i < types.length && i < slots.length; i++) {
            addWeaponItem(slots[i], types[i]);
        }
    }
    
    private void addWeaponItem(int slot, Weapon.WeaponType type) {
        ItemStack item = new ItemStack(type.getIcon());
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + type.getDisplayName());
        
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + type.getDescription());
        lore.add("");
        lore.add(ChatColor.YELLOW + "Base Stats:");
        lore.add(ChatColor.WHITE + "  ⚔ Damage: " + ChatColor.RED + String.format("%.1f", type.getBaseDamage()));
        lore.add(ChatColor.WHITE + "  ◈ Range: " + ChatColor.GREEN + String.format("%.1f", type.getBaseRange()) + " blocks");
        lore.add(ChatColor.WHITE + "  ⚡ Attack Speed: " + ChatColor.GOLD + String.format("%.1f", type.getBaseAttackSpeed()) + "/s");
        
        if (type.getBaseProjectileCount() > 1) {
            lore.add(ChatColor.WHITE + "  ※ Projectiles: " + ChatColor.LIGHT_PURPLE + type.getBaseProjectileCount());
        }
        
        if (type.getBaseAOE() > 0) {
            lore.add(ChatColor.WHITE + "  ◉ AOE: " + ChatColor.BLUE + String.format("%.1f", type.getBaseAOE()) + " blocks");
        }
        
        lore.add("");
        lore.add(ChatColor.GREEN + "Click to select!");
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        inventory.setItem(slot, item);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (event.getWhoClicked() != player) return;
        
        event.setCancelled(true);
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        
        // Find which weapon was clicked
        for (Weapon.WeaponType type : Weapon.WeaponType.values()) {
            if (clicked.getType() == type.getIcon()) {
                onSelect.accept(type);
                player.sendMessage(ChatColor.GREEN + "Selected: " + ChatColor.AQUA + type.getDisplayName());
                player.closeInventory();
                unregister();
                return;
            }
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory) && event.getPlayer() == player) {
            unregister();
        }
    }
    
    private void unregister() {
        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryCloseEvent.getHandlerList().unregister(this);
    }
}

