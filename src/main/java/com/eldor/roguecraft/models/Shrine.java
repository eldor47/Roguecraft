package com.eldor.roguecraft.models;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.*;

/**
 * Represents a physical shrine structure in the arena
 */
public class Shrine {
    private final UUID id;
    private final Location location;
    private final ShrineType type;
    private final List<Block> blocks;
    private boolean isActive;
    private long lastUsedTime;
    private boolean hasBeenUsed; // Track if this shrine instance has been used
    
    public Shrine(Location location, ShrineType type) {
        this.id = UUID.randomUUID();
        this.location = location.clone();
        this.type = type;
        this.blocks = new ArrayList<>();
        this.isActive = true;
        this.lastUsedTime = 0;
    }
    
    public UUID getId() {
        return id;
    }
    
    public Location getLocation() {
        return location.clone();
    }
    
    public ShrineType getType() {
        return type;
    }
    
    public List<Block> getBlocks() {
        return blocks;
    }
    
    public void addBlock(Block block) {
        blocks.add(block);
    }
    
    public boolean isActive() {
        return isActive && !hasBeenUsed; // Inactive if disabled OR already used
    }
    
    public void setActive(boolean active) {
        this.isActive = active;
    }
    
    public boolean hasBeenUsed() {
        return hasBeenUsed;
    }
    
    public void markAsUsed() {
        this.hasBeenUsed = true;
        // Visual feedback - maybe make the light dim or change color
        if (!blocks.isEmpty()) {
            Block lightBlock = location.clone().add(0, 3, 0).getBlock();
            if (lightBlock.getType() == type.getLightMaterial()) {
                // Change to a dimmed light source
                lightBlock.setType(Material.REDSTONE_LAMP); // All used shrines get a dimmed lamp
            }
        }
    }
    
    public long getLastUsedTime() {
        return lastUsedTime;
    }
    
    public void setLastUsedTime(long time) {
        this.lastUsedTime = time;
    }
    
    public boolean isOnCooldown() {
        long cooldown = type.getCooldown() * 1000L; // Convert to milliseconds
        return System.currentTimeMillis() - lastUsedTime < cooldown;
    }
    
    public long getRemainingCooldown() {
        long cooldown = type.getCooldown() * 1000L;
        long remaining = cooldown - (System.currentTimeMillis() - lastUsedTime);
        return Math.max(0, remaining / 1000L); // Return in seconds
    }
    
    /**
     * Build the shrine structure at the location
     */
    public void build() {
        blocks.clear();
        
        // Base (3x3 platform)
        Material baseMaterial = type.getBaseMaterial();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block block = location.clone().add(x, 0, z).getBlock();
                block.setType(baseMaterial);
                blocks.add(block);
            }
        }
        
        // Pillar (center column)
        Material pillarMaterial = type.getPillarMaterial();
        for (int y = 1; y <= 2; y++) {
            Block block = location.clone().add(0, y, 0).getBlock();
            block.setType(pillarMaterial);
            blocks.add(block);
        }
        
        // Light on top
        Block lightBlock = location.clone().add(0, 3, 0).getBlock();
        lightBlock.setType(type.getLightMaterial());
        blocks.add(lightBlock);
    }
    
    /**
     * Remove the shrine structure
     * Ensures all blocks are set to AIR and light is properly updated
     */
    public void remove() {
        if (location == null || location.getWorld() == null) {
            return; // Can't remove if location is invalid
        }
        
        // Remove all tracked blocks
        for (Block block : blocks) {
            if (block != null && block.getWorld() != null) {
                block.setType(Material.AIR);
                // Force light update to remove dark spots
                block.getWorld().refreshChunk(block.getChunk().getX(), block.getChunk().getZ());
            }
        }
        
        // Also check the area around the shrine location to catch any missed blocks
        // Sometimes blocks might not be in the list if build() failed partially
        Location shrineLoc = getLocation();
        if (shrineLoc != null && shrineLoc.getWorld() != null) {
            // Check 3x3 base area
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    Block block = shrineLoc.clone().add(x, 0, z).getBlock();
                    Material mat = block.getType();
                    // Check if it's a shrine material (concrete or terracotta)
                    if (isShrineMaterial(mat)) {
                        block.setType(Material.AIR);
                    }
                }
            }
            
            // Check pillar area (y=1 to y=3)
            for (int y = 1; y <= 3; y++) {
                Block block = shrineLoc.clone().add(0, y, 0).getBlock();
                Material mat = block.getType();
                if (isShrineMaterial(mat) || isLightMaterial(mat)) {
                    block.setType(Material.AIR);
                }
            }
            
            // Force chunk refresh for the entire shrine area
            shrineLoc.getWorld().refreshChunk(shrineLoc.getChunk().getX(), shrineLoc.getChunk().getZ());
        }
        
        blocks.clear();
    }
    
    /**
     * Check if a material is a shrine building material
     */
    private boolean isShrineMaterial(Material mat) {
        return mat.name().contains("CONCRETE") || mat.name().contains("TERRACOTTA");
    }
    
    /**
     * Check if a material is a light source material
     */
    private boolean isLightMaterial(Material mat) {
        return mat == Material.REDSTONE_LAMP || mat == Material.SEA_LANTERN || 
               mat == Material.GLOWSTONE || mat == Material.BEACON || 
               mat == Material.JACK_O_LANTERN || mat == Material.END_ROD;
    }
    
    public enum ShrineType {
        POWER("Shrine of Power", "Triple damage for 10 seconds", 30,
              Material.RED_CONCRETE, Material.RED_TERRACOTTA, Material.REDSTONE_LAMP),
        SWIFTNESS("Shrine of Swiftness", "Double speed for 15 seconds", 25,
                  Material.CYAN_CONCRETE, Material.CYAN_TERRACOTTA, Material.SEA_LANTERN),
        VITALITY("Shrine of Vitality", "Full heal + 50% max HP for 20 seconds", 40,
                 Material.GREEN_CONCRETE, Material.GREEN_TERRACOTTA, Material.GLOWSTONE),
        FORTUNE("Shrine of Fortune", "Quadruple XP for 30 seconds", 35,
                Material.YELLOW_CONCRETE, Material.YELLOW_TERRACOTTA, Material.GLOWSTONE),
        FURY("Shrine of Fury", "100% crit chance for 8 seconds", 20,
             Material.ORANGE_CONCRETE, Material.ORANGE_TERRACOTTA, Material.JACK_O_LANTERN),
        PROTECTION("Shrine of Protection", "Invulnerability for 5 seconds", 60,
                   Material.LIGHT_BLUE_CONCRETE, Material.LIGHT_BLUE_TERRACOTTA, Material.SEA_LANTERN),
        CHAOS("Shrine of Chaos", "Random powerful effect for 12 seconds", 45,
              Material.PURPLE_CONCRETE, Material.PURPLE_TERRACOTTA, Material.END_ROD),
        TIME("Shrine of Time", "Slow all enemies by 80% for 15 seconds", 50,
             Material.WHITE_CONCRETE, Material.WHITE_TERRACOTTA, Material.BEACON);
        
        private final String name;
        private final String description;
        private final int cooldown; // In seconds
        private final Material baseMaterial;
        private final Material pillarMaterial;
        private final Material lightMaterial;
        
        ShrineType(String name, String description, int cooldown,
                   Material baseMaterial, Material pillarMaterial, Material lightMaterial) {
            this.name = name;
            this.description = description;
            this.cooldown = cooldown;
            this.baseMaterial = baseMaterial;
            this.pillarMaterial = pillarMaterial;
            this.lightMaterial = lightMaterial;
        }
        
        public String getName() {
            return name;
        }
        
        public String getDescription() {
            return description;
        }
        
        public int getCooldown() {
            return cooldown;
        }
        
        public Material getBaseMaterial() {
            return baseMaterial;
        }
        
        public Material getPillarMaterial() {
            return pillarMaterial;
        }
        
        public Material getLightMaterial() {
            return lightMaterial;
        }
    }
}

