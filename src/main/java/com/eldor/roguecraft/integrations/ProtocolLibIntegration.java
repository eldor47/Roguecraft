package com.eldor.roguecraft.integrations;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.Weapon;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Color;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ProtocolLib integration for advanced entity manipulation and visual effects
 */
public class ProtocolLibIntegration {
    private final RoguecraftPlugin plugin;
    private ProtocolManager protocolManager;
    private boolean enabled = false;
    
    // Track fake entities (markers)
    private final Map<UUID, Integer> fakeEntityIds = new ConcurrentHashMap<>();
    private final Map<Integer, Location> fakeEntityLocations = new ConcurrentHashMap<>();
    private int nextFakeEntityId = Integer.MAX_VALUE - 10000; // Start from high ID to avoid conflicts
    
    // Track boss bars per player (using Bukkit's BossBar API)
    private final Map<UUID, Map<Integer, BossBar>> playerBossBars = new ConcurrentHashMap<>();
    
    // Track projectiles for trail effects
    private final Map<UUID, ProjectileInfo> trackedProjectiles = new ConcurrentHashMap<>();
    private org.bukkit.scheduler.BukkitTask trailTask = null;
    
    // Projectile info for trail tracking
    private static class ProjectileInfo {
        Projectile projectile;
        Particle trailParticle;
        int trailCount;
        double trailSpeed;
        Location lastLocation;
        long spawnTime;
        
        ProjectileInfo(Projectile proj, Particle particle, int count, double speed) {
            this.projectile = proj;
            this.trailParticle = particle;
            this.trailCount = count;
            this.trailSpeed = speed;
            this.lastLocation = proj.getLocation().clone();
            this.spawnTime = System.currentTimeMillis();
        }
    }
    
    public ProtocolLibIntegration(RoguecraftPlugin plugin) {
        this.plugin = plugin;
        if (setupProtocolLib()) {
            enabled = true;
            plugin.getLogger().info("ProtocolLib integration enabled!");
            startTrailTask();
        }
    }
    
    /**
     * Start the trail effect task
     */
    private void startTrailTask() {
        if (trailTask != null) {
            trailTask.cancel();
        }
        trailTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            updateProjectileTrails();
        }, 0L, 1L); // Every tick
    }
    
    /**
     * Update all projectile trails
     */
    private void updateProjectileTrails() {
        if (!isEnabled()) {
            return;
        }
        
        Iterator<Map.Entry<UUID, ProjectileInfo>> iterator = trackedProjectiles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ProjectileInfo> entry = iterator.next();
            ProjectileInfo info = entry.getValue();
            
            if (info.projectile == null || !info.projectile.isValid() || info.projectile.isDead()) {
                iterator.remove();
                continue;
            }
            
            Location currentLoc = info.projectile.getLocation();
            if (currentLoc.getWorld() == null) {
                iterator.remove();
                continue;
            }
            
            // Spawn trail particles
            if (info.lastLocation != null && currentLoc.distanceSquared(info.lastLocation) > 0.04) { // Only if moved significantly
                // Spawn particles for all nearby players
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (player.getWorld().equals(currentLoc.getWorld())) {
                        double distance = player.getLocation().distance(currentLoc);
                        if (distance <= 64.0) { // Only show to players within 64 blocks
                            spawnParticle(player, currentLoc, info.trailParticle, info.trailCount, 0.1f, 0.1f, 0.1f, (float) info.trailSpeed);
                        }
                    }
                }
                info.lastLocation = currentLoc.clone();
            }
            
            // Remove old projectiles (5 second timeout)
            if (System.currentTimeMillis() - info.spawnTime > 5000) {
                iterator.remove();
            }
        }
    }
    
    /**
     * Set up ProtocolLib
     */
    private boolean setupProtocolLib() {
        try {
            if (plugin.getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
                return false;
            }
            protocolManager = ProtocolLibrary.getProtocolManager();
            return protocolManager != null;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize ProtocolLib: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if ProtocolLib is enabled
     */
    public boolean isEnabled() {
        return enabled && protocolManager != null;
    }
    
    /**
     * Make an entity glow for a specific player (client-side only)
     */
    public void setEntityGlowing(Player player, Entity entity, boolean glowing) {
        if (!isEnabled()) {
            return;
        }
        
        try {
            PacketContainer packet = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, entity.getEntityId());
            
            WrappedDataWatcher watcher = new WrappedDataWatcher();
            WrappedDataWatcher.Serializer serializer = WrappedDataWatcher.Registry.get(Byte.class);
            byte flags = 0;
            
            // Get current entity metadata
            if (entity instanceof org.bukkit.entity.LivingEntity) {
                org.bukkit.entity.LivingEntity living = (org.bukkit.entity.LivingEntity) entity;
                // Preserve existing flags
                flags = (byte) (living.getFireTicks() > 0 ? 0x01 : 0);
            }
            
            // Set glowing flag (bit 6)
            if (glowing) {
                flags |= 0x40; // Glowing bit
            } else {
                flags &= ~0x40; // Clear glowing bit
            }
            
            watcher.setObject(0, serializer, flags);
            
            List<WrappedDataValue> dataValues = new ArrayList<>();
            dataValues.add(new WrappedDataValue(0, serializer, flags));
            packet.getDataValueCollectionModifier().write(0, dataValues);
            
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception e) {
            plugin.getLogger().fine("Failed to set entity glowing via ProtocolLib: " + e.getMessage());
        }
    }
    
    /**
     * Make an entity glow for all players
     */
    public void setEntityGlowing(Entity entity, boolean glowing) {
        if (!isEnabled()) {
            return;
        }
        
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getWorld().equals(entity.getWorld())) {
                setEntityGlowing(player, entity, glowing);
            }
        }
    }
    
    /**
     * Update entity name tag for a specific player (client-side only)
     */
    public void updateEntityName(Player player, Entity entity, String name) {
        if (!isEnabled()) {
            return;
        }
        
        try {
            PacketContainer packet = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, entity.getEntityId());
            
            WrappedDataWatcher watcher = new WrappedDataWatcher();
            WrappedDataWatcher.Serializer nameSerializer = WrappedDataWatcher.Registry.getChatComponentSerializer(true);
            
            // Set custom name
            watcher.setObject(2, nameSerializer, com.comphenix.protocol.wrappers.WrappedChatComponent.fromText(name));
            watcher.setObject(3, WrappedDataWatcher.Registry.get(Boolean.class), true); // Custom name visible
            
            List<WrappedDataValue> dataValues = new ArrayList<>();
            dataValues.add(new WrappedDataValue(2, nameSerializer, com.comphenix.protocol.wrappers.WrappedChatComponent.fromText(name)));
            dataValues.add(new WrappedDataValue(3, WrappedDataWatcher.Registry.get(Boolean.class), true));
            packet.getDataValueCollectionModifier().write(0, dataValues);
            
            protocolManager.sendServerPacket(player, packet);
        } catch (Exception e) {
            plugin.getLogger().fine("Failed to update entity name via ProtocolLib: " + e.getMessage());
        }
    }
    
    /**
     * Make elite mobs glow for players within range (client-side only)
     */
    public void updateEliteGlowingForNearbyPlayers(Entity eliteEntity, double range) {
        if (!isEnabled() || eliteEntity == null || eliteEntity.isDead()) {
            return;
        }
        
        Location entityLoc = eliteEntity.getLocation();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getWorld().equals(entityLoc.getWorld())) {
                double distance = player.getLocation().distance(entityLoc);
                boolean shouldGlow = distance <= range;
                setEntityGlowing(player, eliteEntity, shouldGlow);
            }
        }
    }
    
    /**
     * Create visual telegraph for weapon attack (custom particle effects)
     */
    public void createAttackTelegraph(Player player, Location targetLoc, Weapon.WeaponType weaponType, double damage) {
        if (!isEnabled()) {
            return;
        }
        
        try {
            // Create custom particle effect based on weapon type
            Particle particle = Particle.CRIT;
            int count = 10;
            double offsetX = 0.3, offsetY = 0.5, offsetZ = 0.3;
            double speed = 0.1;
            
            switch (weaponType) {
                case FIREBALL:
                    particle = Particle.FLAME;
                    count = 15;
                    speed = 0.2;
                    break;
                case LIGHTNING_STRIKE:
                    particle = Particle.ELECTRIC_SPARK;
                    count = 20;
                    offsetX = offsetY = offsetZ = 0.5;
                    break;
                case ICE_SHARD:
                    particle = Particle.SNOWFLAKE;
                    count = 12;
                    break;
                case MAGIC_MISSILE:
                    particle = Particle.ENCHANT;
                    count = 8;
                    break;
                case TNT_SPAWNER:
                    particle = Particle.EXPLOSION;
                    count = 3;
                    offsetX = offsetY = offsetZ = 0.8;
                    break;
                case POTION_THROWER:
                    particle = Particle.ITEM_SLIME;
                    count = 10;
                    break;
                case ARROW_STORM:
                    particle = Particle.CRIT;
                    count = 8;
                    break;
            }
            
            // Use Bukkit's particle API instead (more reliable)
            player.spawnParticle(particle, targetLoc, count, offsetX, offsetY, offsetZ, speed);
        } catch (Exception e) {
            plugin.getLogger().fine("Failed to create attack telegraph: " + e.getMessage());
        }
    }
    
    /**
     * Create a fake entity marker at a location (client-side only)
     */
    public UUID createFakeEntityMarker(Player player, Location location, EntityType displayType, String name) {
        if (!isEnabled()) {
            return null;
        }
        
        try {
            int entityId = nextFakeEntityId--;
            UUID entityUuid = UUID.randomUUID();
            fakeEntityIds.put(entityUuid, entityId);
            fakeEntityLocations.put(entityId, location.clone());
            
            // Spawn fake entity packet
            PacketContainer spawnPacket = protocolManager.createPacket(com.comphenix.protocol.PacketType.Play.Server.SPAWN_ENTITY);
            spawnPacket.getIntegers().write(0, entityId);
            spawnPacket.getUUIDs().write(0, entityUuid);
            spawnPacket.getDoubles().write(0, location.getX());
            spawnPacket.getDoubles().write(1, location.getY());
            spawnPacket.getDoubles().write(2, location.getZ());
            spawnPacket.getIntegers().write(1, 0); // Type (0 = marker)
            spawnPacket.getIntegers().write(2, 0); // Data
            spawnPacket.getIntegers().write(3, 0); // Velocity X
            spawnPacket.getIntegers().write(4, 0); // Velocity Y
            spawnPacket.getIntegers().write(5, 0); // Velocity Z
            
            protocolManager.sendServerPacket(player, spawnPacket);
            
            // Set custom name if provided
            if (name != null && !name.isEmpty()) {
                updateEntityName(player, null, name); // We'll need to handle this differently
            }
            
            return entityUuid;
        } catch (Exception e) {
            plugin.getLogger().fine("Failed to create fake entity marker: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Remove a fake entity marker
     */
    public void removeFakeEntityMarker(Player player, UUID markerUuid) {
        if (!isEnabled() || markerUuid == null) {
            return;
        }
        
        Integer entityId = fakeEntityIds.remove(markerUuid);
        if (entityId != null) {
            fakeEntityLocations.remove(entityId);
            
            // Find and remove the actual entity
            for (org.bukkit.entity.Entity entity : player.getWorld().getEntities()) {
                if (entity.getUniqueId().equals(markerUuid) && entity instanceof ArmorStand) {
                    entity.remove();
                    break;
                }
            }
        }
    }
    
    /**
     * Create or update a custom boss bar for a specific player
     */
    public void setBossBar(Player player, int barId, String title, float health) {
        if (!isEnabled()) {
            return;
        }
        
        try {
            health = Math.max(0.0f, Math.min(1.0f, health)); // Clamp between 0 and 1
            
            // Remove existing boss bar if it exists
            removeBossBar(player, barId);
            
            // Create new boss bar using Bukkit's API
            BossBar bossBar = Bukkit.createBossBar(title, BarColor.PINK, BarStyle.SOLID);
            bossBar.setProgress(health);
            bossBar.addPlayer(player);
            
            // Track boss bar
            playerBossBars.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(barId, bossBar);
        } catch (Exception e) {
            plugin.getLogger().fine("Failed to set boss bar: " + e.getMessage());
        }
    }
    
    /**
     * Update boss bar health
     */
    public void updateBossBarHealth(Player player, int barId, float health) {
        if (!isEnabled()) {
            return;
        }
        
        try {
            health = Math.max(0.0f, Math.min(1.0f, health));
            
            Map<Integer, BossBar> bars = playerBossBars.get(player.getUniqueId());
            if (bars == null) {
                return;
            }
            
            BossBar bossBar = bars.get(barId);
            if (bossBar != null) {
                bossBar.setProgress(health);
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Failed to update boss bar health: " + e.getMessage());
        }
    }
    
    /**
     * Update boss bar title
     */
    public void updateBossBarTitle(Player player, int barId, String title) {
        if (!isEnabled()) {
            return;
        }
        
        try {
            Map<Integer, BossBar> bars = playerBossBars.get(player.getUniqueId());
            if (bars == null) {
                return;
            }
            
            BossBar bossBar = bars.get(barId);
            if (bossBar != null) {
                bossBar.setTitle(title);
            }
            // If boss bar doesn't exist, it will be created by setBossBar or updateBossBarHealth
        } catch (Exception e) {
            plugin.getLogger().fine("Failed to update boss bar title: " + e.getMessage());
        }
    }
    
    /**
     * Check if a boss bar exists for a player
     */
    public boolean hasBossBar(Player player, int barId) {
        if (!isEnabled()) {
            return false;
        }
        
        Map<Integer, BossBar> bars = playerBossBars.get(player.getUniqueId());
        if (bars == null) {
            return false;
        }
        
        return bars.containsKey(barId);
    }
    
    /**
     * Remove a boss bar
     */
    public void removeBossBar(Player player, int barId) {
        if (!isEnabled()) {
            return;
        }
        
        Map<Integer, BossBar> bars = playerBossBars.get(player.getUniqueId());
        if (bars == null) {
            return;
        }
        
        BossBar bossBar = bars.remove(barId);
        if (bossBar != null) {
            bossBar.removePlayer(player);
            bossBar.removeAll();
        }
    }
    
    /**
     * Remove boss bar for all players (when boss dies)
     */
    public void removeBossBarForAll(int barId) {
        if (!isEnabled()) {
            return;
        }
        
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            removeBossBar(player, barId);
        }
    }
    
    /**
     * Clean up all fake entities and boss bars for a player
     */
    public void cleanupPlayer(Player player) {
        if (!isEnabled()) {
            return;
        }
        
        UUID playerUuid = player.getUniqueId();
        
        // Remove all fake entities
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : fakeEntityIds.entrySet()) {
            toRemove.add(entry.getKey());
        }
        for (UUID uuid : toRemove) {
            removeFakeEntityMarker(player, uuid);
        }
        
        // Remove all boss bars
        Map<Integer, BossBar> bars = playerBossBars.remove(playerUuid);
        if (bars != null) {
            for (Integer barId : bars.keySet()) {
                removeBossBar(player, barId);
            }
        }
    }
    
    // ========== ADVANCED PARTICLE EFFECTS ==========
    
    /**
     * Start tracking a projectile for trail effects
     */
    public void startProjectileTrail(Projectile projectile, Weapon.WeaponType weaponType) {
        if (!isEnabled() || projectile == null) {
            return;
        }
        
        Particle trailParticle = Particle.CRIT;
        int count = 2;
        double speed = 0.05;
        
        // Set trail based on weapon type
        switch (weaponType) {
            case FIREBALL:
                trailParticle = Particle.FLAME;
                count = 3;
                speed = 0.1;
                break;
            case ARROW_STORM:
                trailParticle = Particle.CRIT;
                count = 2;
                speed = 0.05;
                break;
            case ICE_SHARD:
                trailParticle = Particle.SNOWFLAKE;
                count = 2;
                speed = 0.05;
                break;
            case MAGIC_MISSILE:
                trailParticle = Particle.ENCHANT;
                count = 2;
                speed = 0.08;
                break;
            case POTION_THROWER:
                trailParticle = Particle.ITEM_SLIME;
                count = 2;
                speed = 0.05;
                break;
            case LIGHTNING_STRIKE:
                trailParticle = Particle.ELECTRIC_SPARK;
                count = 3;
                speed = 0.1;
                break;
            case TNT_SPAWNER:
                trailParticle = Particle.SMOKE;
                count = 2;
                speed = 0.05;
                break;
        }
        
        trackedProjectiles.put(projectile.getUniqueId(), new ProjectileInfo(projectile, trailParticle, count, speed));
    }
    
    /**
     * Stop tracking a projectile (when it hits or despawns)
     */
    public void stopProjectileTrail(Projectile projectile) {
        if (projectile != null) {
            trackedProjectiles.remove(projectile.getUniqueId());
        }
    }
    
    /**
     * Show AOE damage indicator (ring of particles at explosion location)
     */
    public void showAOEDamageIndicator(Location center, double radius, Weapon.WeaponType weaponType) {
        if (!isEnabled() || center == null) {
            return;
        }
        
        Particle particle = Particle.EXPLOSION;
        int particleCount = (int) (radius * 8); // Scale particles with radius
        
        // Set particle based on weapon type
        switch (weaponType) {
            case FIREBALL:
            case TNT_SPAWNER:
                particle = Particle.EXPLOSION;
                break;
            case ICE_SHARD:
                particle = Particle.SNOWFLAKE;
                break;
            case LIGHTNING_STRIKE:
                particle = Particle.ELECTRIC_SPARK;
                break;
            case POTION_THROWER:
                particle = Particle.ITEM_SLIME;
                break;
            default:
                particle = Particle.EXPLOSION;
        }
        
        // Show to all nearby players
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getWorld().equals(center.getWorld())) {
                double distance = player.getLocation().distance(center);
                if (distance <= 64.0) {
                    // Create expanding ring effect
                    for (int i = 0; i < particleCount; i++) {
                        double angle = (i * 2 * Math.PI) / particleCount;
                        double x = center.getX() + Math.cos(angle) * radius;
                        double y = center.getY() + 0.5;
                        double z = center.getZ() + Math.sin(angle) * radius;
                        Location particleLoc = new Location(center.getWorld(), x, y, z);
                        spawnParticle(player, particleLoc, particle, 1, 0.0f, 0.0f, 0.0f, 0.0f);
                    }
                    // Also spawn particles above and below
                    spawnParticle(player, center.clone().add(0, radius, 0), particle, particleCount / 4, 0.2f, 0.2f, 0.2f, 0.1f);
                    spawnParticle(player, center.clone().add(0, -radius, 0), particle, particleCount / 4, 0.2f, 0.2f, 0.2f, 0.1f);
                }
            }
        }
    }
    
    /**
     * Show enhanced critical hit effect
     */
    public void showCriticalHitEffect(Player player, LivingEntity target, double damage) {
        if (!isEnabled() || target == null) {
            return;
        }
        
        Location targetLoc = target.getEyeLocation();
        
        // Enhanced crit particles - multiple types for dramatic effect
        // Golden crit particles
        spawnParticle(player, targetLoc, Particle.CRIT, 30, 0.5f, 0.5f, 0.5f, 0.2f);
        // Electric sparks
        spawnParticle(player, targetLoc, Particle.ELECTRIC_SPARK, 15, 0.4f, 0.4f, 0.4f, 0.1f);
        // Explosion particles for impact
        spawnParticle(player, targetLoc, Particle.EXPLOSION, 5, 0.3f, 0.3f, 0.3f, 0.0f);
        
        // Burst effect - particles shooting upward
        for (int i = 0; i < 20; i++) {
            double angle = (i * 2 * Math.PI) / 20;
            double x = targetLoc.getX() + Math.cos(angle) * 0.3;
            double y = targetLoc.getY() + 0.5;
            double z = targetLoc.getZ() + Math.sin(angle) * 0.3;
            Location burstLoc = new Location(targetLoc.getWorld(), x, y, z);
            spawnParticle(player, burstLoc, Particle.ENCHANT, 1, 0.0f, 0.2f, 0.0f, 0.1f);
        }
    }
    
    /**
     * Show status effect visual indicator
     */
    public void showStatusEffectVisual(Player player, LivingEntity target, PotionEffectType effectType, int amplifier) {
        if (!isEnabled() || target == null) {
            return;
        }
        
        Location targetLoc = target.getLocation().add(0, target.getHeight() / 2, 0);
        Particle particle = Particle.HEART;
        int count = 5 + amplifier * 2;
        Color color = null;
        
        // Set particle and color based on effect type
        String effectName = effectType.getKey().getKey().toUpperCase();
        switch (effectName) {
            case "POISON":
                particle = Particle.ITEM_SLIME;
                color = Color.fromRGB(0, 255, 0); // Green
                break;
            case "WEAKNESS":
                particle = Particle.ENCHANT;
                color = Color.fromRGB(128, 128, 128); // Gray
                break;
            case "SLOWNESS":
                particle = Particle.SNOWFLAKE;
                color = Color.fromRGB(100, 149, 237); // Blue
                break;
            case "WITHER":
                particle = Particle.SMOKE;
                color = Color.fromRGB(64, 64, 64); // Dark gray
                break;
            case "FIRE_RESISTANCE":
                particle = Particle.FLAME;
                color = Color.fromRGB(255, 100, 0); // Orange
                break;
            case "REGENERATION":
                particle = Particle.HEART;
                color = Color.fromRGB(255, 0, 0); // Red
                break;
            case "STRENGTH":
                particle = Particle.CRIT;
                color = Color.fromRGB(255, 0, 0); // Red
                break;
            default:
                particle = Particle.HEART;
        }
        
        // Spawn particles in a ring around the entity
        for (int i = 0; i < count; i++) {
            double angle = (i * 2 * Math.PI) / count;
            double x = targetLoc.getX() + Math.cos(angle) * 0.5;
            double y = targetLoc.getY() + (i % 2) * 0.3;
            double z = targetLoc.getZ() + Math.sin(angle) * 0.5;
            Location particleLoc = new Location(targetLoc.getWorld(), x, y, z);
            
            if (color != null && particle == Particle.DUST) {
                spawnParticle(player, particleLoc, Particle.DUST, 1, 0.0f, 0.0f, 0.0f, 1.0f);
            } else {
                spawnParticle(player, particleLoc, particle, 1, 0.0f, 0.0f, 0.0f, 0.0f);
            }
        }
    }
    
    /**
     * Spawn a particle for a specific player (client-side only)
     */
    private void spawnParticle(Player player, Location location, Particle particle, int count, float offsetX, float offsetY, float offsetZ, float speed) {
        if (!isEnabled() || player == null || location == null) {
            return;
        }
        
        try {
            // Use Bukkit's particle API (more reliable than ProtocolLib packets for particles)
            player.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed);
        } catch (Exception e) {
            plugin.getLogger().fine("Failed to spawn particle: " + e.getMessage());
        }
    }
}

