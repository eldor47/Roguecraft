package com.eldor.roguecraft.managers;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.Arena;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public class ArenaManager {
    private final RoguecraftPlugin plugin;
    private final Map<String, Arena> arenas;
    private Arena defaultArena;

    public ArenaManager(RoguecraftPlugin plugin) {
        this.plugin = plugin;
        this.arenas = new HashMap<>();
        loadArenas();
    }

    private void loadArenas() {
        ConfigurationSection arenasSection = plugin.getConfigManager().getMainConfig().getConfigurationSection("arenas");
        if (arenasSection == null) {
            plugin.getLogger().warning("No arenas section found in config.yml");
            return;
        }

        for (String key : arenasSection.getKeys(false)) {
            ConfigurationSection arenaSection = arenasSection.getConfigurationSection(key);
            if (arenaSection == null) continue;

            Arena arena = new Arena(key, arenaSection.getString("name", key));
            
            // Load spawn point
            Location spawn = parseLocation(arenaSection.getString("spawn"));
            if (spawn != null) {
                arena.setSpawnPoint(spawn);
            }
            
            // Load center and radius
            Location center = parseLocation(arenaSection.getString("center"));
            if (center != null) {
                arena.setCenter(center);
                arena.setRadius(arenaSection.getDouble("radius", 50.0));
            }

            arenas.put(key, arena);
            
            if (arenaSection.getBoolean("default", false)) {
                defaultArena = arena;
            }
        }

        if (defaultArena == null && !arenas.isEmpty()) {
            defaultArena = arenas.values().iterator().next();
        }

        plugin.getLogger().info("Loaded " + arenas.size() + " arena(s)");
    }

    private Location parseLocation(String locationString) {
        if (locationString == null || locationString.isEmpty()) {
            return null;
        }

        try {
            // Format: worldname,x,y,z,yaw,pitch
            // or: worldname,x,y,z
            String[] parts = locationString.split(",");
            if (parts.length < 4) {
                plugin.getLogger().warning("Invalid location format: " + locationString + " (expected: world,x,y,z[,yaw,pitch])");
                return null;
            }

            String worldName = parts[0].trim();
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("World not found: " + worldName);
                return null;
            }

            double x = Double.parseDouble(parts[1].trim());
            double y = Double.parseDouble(parts[2].trim());
            double z = Double.parseDouble(parts[3].trim());
            
            float yaw = 0.0f;
            float pitch = 0.0f;
            
            if (parts.length >= 5) {
                yaw = Float.parseFloat(parts[4].trim());
            }
            if (parts.length >= 6) {
                pitch = Float.parseFloat(parts[5].trim());
            }

            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid number in location: " + locationString + " - " + e.getMessage());
            return null;
        }
    }

    public Arena getArena(String id) {
        return arenas.get(id);
    }

    public Arena getDefaultArena() {
        return defaultArena;
    }

    public Collection<Arena> getAllArenas() {
        return new ArrayList<>(arenas.values());
    }

    public void addArena(Arena arena) {
        arenas.put(arena.getId(), arena);
    }

    public void removeArena(String id) {
        arenas.remove(id);
        if (defaultArena != null && defaultArena.getId().equals(id)) {
            defaultArena = arenas.isEmpty() ? null : arenas.values().iterator().next();
        }
    }

    public void reload() {
        arenas.clear();
        defaultArena = null;
        loadArenas();
    }
    
    /**
     * Save an arena's configuration to config.yml
     */
    public void saveArena(Arena arena) {
        if (arena == null) return;
        
        ConfigurationSection arenasSection = plugin.getConfigManager().getMainConfig().getConfigurationSection("arenas");
        if (arenasSection == null) {
            arenasSection = plugin.getConfigManager().getMainConfig().createSection("arenas");
        }
        
        ConfigurationSection arenaSection = arenasSection.getConfigurationSection(arena.getId());
        if (arenaSection == null) {
            arenaSection = arenasSection.createSection(arena.getId());
        }
        
        // Save arena name
        arenaSection.set("name", arena.getName());
        
        // Save spawn point
        if (arena.getSpawnPoint() != null) {
            arenaSection.set("spawn", formatLocation(arena.getSpawnPoint()));
        }
        
        // Save center and radius
        if (arena.getCenter() != null) {
            arenaSection.set("center", formatLocation(arena.getCenter()));
            arenaSection.set("radius", arena.getRadius());
        }
        
        // Save default flag
        arenaSection.set("default", (defaultArena != null && defaultArena.getId().equals(arena.getId())));
        
        // Save to file
        plugin.saveConfig();
    }
    
    /**
     * Format a location as a string for config
     */
    private String formatLocation(Location loc) {
        if (loc == null) return null;
        return loc.getWorld().getName() + "," + 
               loc.getX() + "," + loc.getY() + "," + loc.getZ() + "," +
               loc.getYaw() + "," + loc.getPitch();
    }
    
}
