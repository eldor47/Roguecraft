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
    private org.bukkit.plugin.Plugin plugin; // Plugin instance for metadata
    
    public Shrine(Location location, ShrineType type) {
        this.id = UUID.randomUUID();
        this.location = location.clone();
        this.type = type;
        this.blocks = new ArrayList<>();
        this.isActive = true;
        this.lastUsedTime = 0;
        // Get plugin instance
        this.plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("Roguecraft");
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
        
        if (type == ShrineType.DIFFICULTY) {
            buildDifficultyShrine();
        } else if (type == ShrineType.BOSS) {
            buildBossShrine();
        } else {
            buildStandardShrine();
        }
    }
    
    private void buildDifficultyShrine() {
        if (location == null || location.getWorld() == null) {
            return; // Can't build if location is invalid
        }
        
        // Ensure chunk is loaded
        if (!location.getChunk().isLoaded()) {
            location.getChunk().load();
        }
        
        // Just a dark block
        Block darkBlock = location.clone().add(0, 0, 0).getBlock();
        if (darkBlock != null) {
            darkBlock.setType(Material.BLACK_CONCRETE);
            blocks.add(darkBlock);
        }
        
        // Place skeleton skull block on top
        Block skullBlock = location.clone().add(0, 1, 0).getBlock();
        if (skullBlock != null) {
            skullBlock.setType(Material.SKELETON_SKULL);
            blocks.add(skullBlock);
        }
    }
    
    private void buildBossShrine() {
        if (location == null || location.getWorld() == null) {
            return; // Can't build if location is invalid
        }
        
        // Ensure chunk is loaded
        if (!location.getChunk().isLoaded()) {
            location.getChunk().load();
        }
        
        // Epic base: 5x5 platform with decorative corners
        Material baseMaterial = type.getBaseMaterial();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Block block = location.clone().add(x, 0, z).getBlock();
                if (block == null) continue; // Skip if block is null
                
                // Decorative corners with different material
                if ((x == -2 || x == 2) && (z == -2 || z == 2)) {
                    block.setType(Material.OBSIDIAN); // Obsidian corners
                } else {
                    block.setType(baseMaterial);
                }
                blocks.add(block);
            }
        }
        
        // Taller pillar (3 blocks high instead of 2)
        Material pillarMaterial = type.getPillarMaterial();
        for (int y = 1; y <= 3; y++) {
            Block block = location.clone().add(0, y, 0).getBlock();
            if (block == null) continue; // Skip if block is null
            block.setType(pillarMaterial);
            blocks.add(block);
        }
        
        // Decorative rings around pillar
        for (int y = 1; y <= 3; y++) {
            // Add decorative blocks around pillar at each level
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && z == 0) continue; // Skip center (pillar)
                    Block block = location.clone().add(x, y, z).getBlock();
                    if (block == null) continue; // Skip if block is null
                    
                    if (y == 2) {
                        // Middle ring: use soul torch
                        block.setType(Material.SOUL_TORCH);
                    } else {
                        // Top and bottom rings: use dark material
                        block.setType(Material.BLACKSTONE);
                    }
                    blocks.add(block);
                }
            }
        }
        
        // Epic light on top (beacon-like structure)
        Block lightBlock = location.clone().add(0, 4, 0).getBlock();
        if (lightBlock != null) {
            lightBlock.setType(type.getLightMaterial());
            blocks.add(lightBlock);
        }
        
        // Additional decorative block above light (use END_ROD instead of NETHER_STAR - it's a valid block)
        Block topBlock = location.clone().add(0, 5, 0).getBlock();
        if (topBlock != null) {
            topBlock.setType(Material.END_ROD); // End rod is a valid decorative block
            blocks.add(topBlock);
        }
    }
    
    private void buildStandardShrine() {
        if (location == null || location.getWorld() == null) {
            return; // Can't build if location is invalid
        }
        
        // Ensure chunk is loaded
        if (!location.getChunk().isLoaded()) {
            location.getChunk().load();
        }
        
        // Base (3x3 platform)
        Material baseMaterial = type.getBaseMaterial();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block block = location.clone().add(x, 0, z).getBlock();
                if (block == null) continue; // Skip if block is null
                block.setType(baseMaterial);
                blocks.add(block);
            }
        }
        
        // Pillar (center column)
        Material pillarMaterial = type.getPillarMaterial();
        for (int y = 1; y <= 2; y++) {
            Block block = location.clone().add(0, y, 0).getBlock();
            if (block == null) continue; // Skip if block is null
            block.setType(pillarMaterial);
            blocks.add(block);
        }
        
        // Light on top
        Block lightBlock = location.clone().add(0, 3, 0).getBlock();
        if (lightBlock != null) {
            lightBlock.setType(type.getLightMaterial());
            blocks.add(lightBlock);
        }
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
            // Difficulty shrine: only has a dark block at y=0 and skull at y=1
            if (type == ShrineType.DIFFICULTY) {
                // Remove the dark block
                Block darkBlock = shrineLoc.clone().add(0, 0, 0).getBlock();
                if (darkBlock != null && (darkBlock.getType() == Material.BLACK_CONCRETE || darkBlock.getType() == Material.BLACK_TERRACOTTA)) {
                    darkBlock.setType(Material.AIR);
                }
                // Remove the skull block
                Block skullBlock = shrineLoc.clone().add(0, 1, 0).getBlock();
                if (skullBlock != null && skullBlock.getType() == Material.SKELETON_SKULL) {
                    skullBlock.setType(Material.AIR);
                }
            } else {
                // Boss and Power shrines: determine cleanup area based on shrine type
                int baseRadius = (type == ShrineType.BOSS) ? 2 : 1; // Boss shrine has 5x5 base, others have 3x3
                int maxHeight = (type == ShrineType.BOSS) ? 5 : 3; // Boss shrine is taller
                
                // Check base area (3x3 for power, 5x5 for boss)
                for (int x = -baseRadius; x <= baseRadius; x++) {
                    for (int z = -baseRadius; z <= baseRadius; z++) {
                        Block block = shrineLoc.clone().add(x, 0, z).getBlock();
                        if (block == null) continue; // Skip if block is null
                        Material mat = block.getType();
                        // Check if it's a shrine material (concrete, terracotta, obsidian, blackstone, fence, etc.)
                        if (isShrineMaterial(mat) || mat == Material.OBSIDIAN || mat == Material.BLACKSTONE || 
                            mat == Material.DARK_OAK_FENCE || mat == Material.SOUL_TORCH || mat == Material.END_ROD) {
                            block.setType(Material.AIR);
                        }
                    }
                }
                
                // Check pillar and decorative area (y=1 to maxHeight)
                for (int y = 1; y <= maxHeight; y++) {
                    // Check center pillar
                    Block block = shrineLoc.clone().add(0, y, 0).getBlock();
                    if (block == null) continue; // Skip if block is null
                    Material mat = block.getType();
                    if (isShrineMaterial(mat) || isLightMaterial(mat) || mat == Material.END_ROD) {
                        block.setType(Material.AIR);
                    }
                    
                    // For boss shrine, also check decorative rings
                    if (type == ShrineType.BOSS && y <= 3) {
                        for (int x = -1; x <= 1; x++) {
                            for (int z = -1; z <= 1; z++) {
                                if (x == 0 && z == 0) continue; // Skip center (pillar)
                                Block ringBlock = shrineLoc.clone().add(x, y, z).getBlock();
                                if (ringBlock == null) continue; // Skip if block is null
                                Material ringMat = ringBlock.getType();
                                if (ringMat == Material.SOUL_TORCH || ringMat == Material.BLACKSTONE) {
                                    ringBlock.setType(Material.AIR);
                                }
                            }
                        }
                    }
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
        POWER("Shrine of Power", "Choose from 3 random power-ups", 0,
              Material.GOLD_BLOCK, Material.GOLD_BLOCK, Material.BEACON),
        DIFFICULTY("Shrine of Challenge", "Right-click to increase difficulty by 5%", 0,
                   Material.RED_CONCRETE, Material.RED_TERRACOTTA, Material.REDSTONE_LAMP),
        BOSS("Shrine of the Wither", "Right-click during boss phase to spawn an additional boss", 0,
             Material.BLACK_CONCRETE, Material.BLACK_TERRACOTTA, Material.SOUL_LANTERN);
        
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

