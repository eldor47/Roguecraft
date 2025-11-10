package com.eldor.roguecraft.models;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;

import java.util.UUID;

/**
 * Represents a gacha chest that can be opened for items
 */
public class GachaChest {
    private final UUID id;
    private final Location location;
    private final Block chestBlock;
    private boolean isOpened;
    private boolean isActive;
    private boolean isFree; // True if chest is free (e.g., from legendary mob drop)
    
    public GachaChest(Location location) {
        this.id = UUID.randomUUID();
        this.location = location.clone();
        this.chestBlock = location.getBlock();
        this.isOpened = false;
        this.isActive = true;
        this.isFree = false; // Default: chests cost money
    }
    
    public GachaChest(Location location, boolean isFree) {
        this.id = UUID.randomUUID();
        this.location = location.clone();
        this.chestBlock = location.getBlock();
        this.isOpened = false;
        this.isActive = true;
        this.isFree = isFree;
    }
    
    public UUID getId() {
        return id;
    }
    
    public Location getLocation() {
        return location.clone();
    }
    
    public Block getChestBlock() {
        return chestBlock;
    }
    
    public boolean isOpened() {
        return isOpened;
    }
    
    public void setOpened(boolean opened) {
        this.isOpened = opened;
        if (opened && chestBlock.getState() instanceof Chest) {
            // Visual feedback - chest is now opened
            // Could add particles or change block type here if needed
        }
    }
    
    public boolean isActive() {
        return isActive && !isOpened;
    }
    
    public void setActive(boolean active) {
        this.isActive = active;
    }
    
    public boolean isFree() {
        return isFree;
    }
    
    public void setFree(boolean free) {
        this.isFree = free;
    }
    
    /**
     * Spawn the chest at the location
     */
    public void spawn() {
        if (chestBlock.getType() != Material.CHEST) {
            chestBlock.setType(Material.CHEST);
        }
    }
    
    /**
     * Remove the chest
     */
    public void remove() {
        if (location != null && location.getWorld() != null) {
            // Get fresh block reference from location to avoid stale references
            Block block = location.getBlock();
            if (block != null && block.getType() == Material.CHEST) {
                block.setType(Material.AIR);
            }
        }
    }
}

