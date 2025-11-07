package com.eldor.roguecraft.managers;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.Run;
import com.eldor.roguecraft.models.TeamRun;
import com.eldor.roguecraft.models.Weapon;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class WeaponManager {
    private final RoguecraftPlugin plugin;
    private final Map<UUID, BukkitTask> weaponTasks; // Player UUID -> Attack task
    private final Map<UUID, Double> lifestealHealingTracker; // Player UUID -> Healing done in last second
    private final Map<UUID, Long> lifestealLastReset; // Player UUID -> Last reset time
    
    public WeaponManager(RoguecraftPlugin plugin) {
        this.plugin = plugin;
        this.weaponTasks = new HashMap<>();
        this.lifestealHealingTracker = new HashMap<>();
        this.lifestealLastReset = new HashMap<>();
    }
    
    public void startAutoAttack(Player player, Weapon weapon) {
        UUID playerId = player.getUniqueId();
        
        // Cancel existing weapon task if any (safety check)
        stopAutoAttack(player);
        
        // Double-check: Ensure no task is already running
        if (weaponTasks.containsKey(playerId)) {
            plugin.getLogger().warning("Warning: Attempted to start auto-attack for " + player.getName() + " but task already exists! Clearing it.");
            stopAutoAttack(player);
        }
        
        // Apply Rapid Fire mod if present
        double modifiedSpeed = getModifiedAttackSpeed(player, weapon);
        long cooldownTicks = (long) (20.0 / modifiedSpeed); // Convert attacks per second to ticks
        
        // Ensure cooldown is reasonable (minimum 1 tick = 0.05 seconds)
        if (cooldownTicks < 1) {
            plugin.getLogger().warning("Warning: Weapon cooldown was " + cooldownTicks + " ticks, setting to minimum 1 tick");
            cooldownTicks = 1;
        }
        
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !player.isValid()) {
                stopAutoAttack(player);
                return;
            }
            
            // Find nearest enemy
            LivingEntity target = findNearestEnemy(player, weapon.getRange());
            
            if (target != null) {
                // Execute attack based on weapon type
                attackWithWeapon(player, target, weapon);
            }
            
        }, 0L, cooldownTicks);
        
        weaponTasks.put(playerId, task);
    }
    
    public void stopAutoAttack(Player player) {
        UUID playerId = player.getUniqueId();
        BukkitTask task = weaponTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        // Clean up lifesteal trackers
        lifestealHealingTracker.remove(playerId);
        lifestealLastReset.remove(playerId);
    }
    
    public void stopAllAutoAttacks() {
        for (BukkitTask task : weaponTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        weaponTasks.clear();
        // Clean up all lifesteal trackers
        lifestealHealingTracker.clear();
        lifestealLastReset.clear();
    }
    
    private LivingEntity findNearestEnemy(Player player, double range) {
        Location playerLoc = player.getLocation();
        LivingEntity nearest = null;
        double nearestDistance = range * range; // Use squared distance for efficiency
        
        for (Entity entity : player.getWorld().getNearbyEntities(playerLoc, range, range, range)) {
            if (entity instanceof LivingEntity && !(entity instanceof Player) && !entity.isDead()) {
                LivingEntity living = (LivingEntity) entity;
                double distSq = playerLoc.distanceSquared(living.getLocation());
                
                if (distSq < nearestDistance) {
                    nearest = living;
                    nearestDistance = distSq;
                }
            }
        }
        
        return nearest;
    }
    
    public void attackWithWeapon(Player player, LivingEntity target, Weapon weapon) {
        switch (weapon.getType()) {
            case FIREBALL:
                launchFireball(player, target, weapon);
                break;
            case ARROW_STORM:
                launchArrows(player, target, weapon);
                break;
            case LIGHTNING_STRIKE:
                strikeLightning(player, target, weapon);
                break;
            case TNT_SPAWNER:
                spawnTNT(player, target, weapon);
                break;
            case POTION_THROWER:
                throwPotion(player, target, weapon);
                break;
            case ICE_SHARD:
                launchIceShard(player, target, weapon);
                break;
            case MAGIC_MISSILE:
                launchMagicMissile(player, target, weapon);
                break;
        }
    }
    
    /**
     * Calculate final damage with all modifiers (damage multiplier, crit, etc.)
     */
    public double calculateFinalDamage(Player player, double baseDamage, LivingEntity target) {
        // Get player stats
        TeamRun teamRun = plugin.getRunManager().getTeamRun(player);
        Run run = null;
        double damageMultiplier = 1.0;
        double critChance = 0.0;
        double critDamage = 1.5;
        
        if (teamRun != null && teamRun.isActive()) {
            damageMultiplier = teamRun.getStat("damage");
            critChance = teamRun.getStat("crit_chance");
            critDamage = teamRun.getStat("crit_damage");
        } else {
            run = plugin.getRunManager().getRun(player);
            if (run != null && run.isActive()) {
                damageMultiplier = run.getStat("damage");
                critChance = run.getStat("crit_chance");
                critDamage = run.getStat("crit_damage");
            }
        }
        
        // Apply damage multiplier
        double finalDamage = baseDamage * damageMultiplier;
        
        // Apply synergy damage multipliers
        Object synergyRun = teamRun != null ? teamRun : run;
        if (synergyRun != null) {
            double synergyMultiplier = plugin.getSynergyManager().getDamageMultiplier(synergyRun, player);
            finalDamage *= synergyMultiplier;
        }
        
        // Check for crit
        boolean isCrit = Math.random() < critChance;
        if (isCrit) {
            finalDamage *= critDamage;
            // Visual feedback for crit
            if (target != null) {
                player.getWorld().spawnParticle(Particle.CRIT, target.getEyeLocation(), 20, 0.5, 0.5, 0.5, 0.1);
            }
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.5f, 1.5f);
            
            // Trigger Critical Mass synergy
            if (synergyRun != null && target != null) {
                plugin.getSynergyManager().onCriticalHit(player, target, finalDamage);
            }
        }
        
        return finalDamage;
    }
    
    /**
     * Overload without target (for cases where target might not exist)
     */
    private double calculateFinalDamage(Player player, double baseDamage) {
        return calculateFinalDamage(player, baseDamage, null);
    }
    
    /**
     * Apply lifesteal/vampire aura if player has it
     */
    public void applyLifesteal(Player player, double damageDealt) {
        TeamRun teamRun = plugin.getRunManager().getTeamRun(player);
        Run run = null;
        boolean hasVampireAura = false;
        double lifestealPercent = 0.0;
        
        // Check for Vampire Aura power-up (check both "Vampire Aura" and "Lifesteal" names)
        if (teamRun != null && teamRun.isActive()) {
            for (com.eldor.roguecraft.models.PowerUp powerUp : teamRun.getCollectedPowerUps()) {
                if (powerUp.getType() == com.eldor.roguecraft.models.PowerUp.PowerUpType.AURA) {
                    String name = powerUp.getName().toLowerCase();
                    if (name.contains("vampire") || name.contains("lifesteal")) {
                        hasVampireAura = true;
                        // Vampire aura value represents lifesteal percentage (e.g., value 1.0 = 2% lifesteal)
                        // Removed cap - lifesteal can scale higher now since it's rarer
                        lifestealPercent = powerUp.getValue() * 2.0; // Convert to percentage, no cap
                        break;
                    }
                }
            }
        } else {
            run = plugin.getRunManager().getRun(player);
            if (run != null && run.isActive()) {
                for (com.eldor.roguecraft.models.PowerUp powerUp : run.getCollectedPowerUps()) {
                    if (powerUp.getType() == com.eldor.roguecraft.models.PowerUp.PowerUpType.AURA) {
                        String name = powerUp.getName().toLowerCase();
                        if (name.contains("vampire") || name.contains("lifesteal")) {
                            hasVampireAura = true;
                            // Removed cap - lifesteal can scale higher now since it's rarer
                            lifestealPercent = powerUp.getValue() * 2.0;
                            break;
                        }
                    }
                }
            }
        }
        
        // Apply lifesteal with healing rate cap (max 2 hearts = 4 HP per second)
        if (hasVampireAura && lifestealPercent > 0) {
            UUID playerId = player.getUniqueId();
            long currentTime = System.currentTimeMillis();
            long lastReset = lifestealLastReset.getOrDefault(playerId, 0L);
            
            // Reset tracker every second
            if (currentTime - lastReset >= 1000) {
                lifestealHealingTracker.put(playerId, 0.0);
                lifestealLastReset.put(playerId, currentTime);
            }
            
            double maxHealingPerSecond = 4.0; // Cap at 2 hearts (4 HP) per second - fixed value to prevent invincibility
            double currentHealingThisSecond = lifestealHealingTracker.getOrDefault(playerId, 0.0);
            
            double healAmount = damageDealt * (lifestealPercent / 100.0);
            double remainingHealingBudget = Math.max(0, maxHealingPerSecond - currentHealingThisSecond);
            double actualHealAmount = Math.min(healAmount, remainingHealingBudget);
            
            if (actualHealAmount > 0) {
                double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
                double currentHealth = player.getHealth();
                double newHealth = Math.min(maxHealth, currentHealth + actualHealAmount);
                player.setHealth(newHealth);
                
                // Update tracker
                lifestealHealingTracker.put(playerId, currentHealingThisSecond + actualHealAmount);
                
                // Visual feedback
                player.getWorld().spawnParticle(Particle.HEART, player.getEyeLocation(), 3, 0.3, 0.5, 0.3, 0);
            }
        }
    }
    
    /**
     * Get active weapon mods for a player's run
     */
    private List<com.eldor.roguecraft.models.PowerUp> getActiveWeaponMods(Player player) {
        List<com.eldor.roguecraft.models.PowerUp> mods = new ArrayList<>();
        
        TeamRun teamRun = plugin.getRunManager().getTeamRun(player);
        if (teamRun != null && teamRun.isActive()) {
            for (com.eldor.roguecraft.models.PowerUp powerUp : teamRun.getCollectedPowerUps()) {
                if (powerUp.getType() == com.eldor.roguecraft.models.PowerUp.PowerUpType.WEAPON_MOD) {
                    mods.add(powerUp);
                }
            }
        } else {
            Run run = plugin.getRunManager().getRun(player);
            if (run != null && run.isActive()) {
                for (com.eldor.roguecraft.models.PowerUp powerUp : run.getCollectedPowerUps()) {
                    if (powerUp.getType() == com.eldor.roguecraft.models.PowerUp.PowerUpType.WEAPON_MOD) {
                        mods.add(powerUp);
                    }
                }
            }
        }
        
        return mods;
    }
    
    /**
     * Check if player has a specific weapon mod
     */
    private boolean hasWeaponMod(Player player, String modName) {
        for (com.eldor.roguecraft.models.PowerUp mod : getActiveWeaponMods(player)) {
            if (mod.getName().equals(modName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get weapon mod value (for mods with values like Multi-Shot count)
     */
    private double getWeaponModValue(Player player, String modName) {
        for (com.eldor.roguecraft.models.PowerUp mod : getActiveWeaponMods(player)) {
            if (mod.getName().equals(modName)) {
                return mod.getValue();
            }
        }
        return 0.0;
    }
    
    /**
     * Apply weapon mod effects to damage/hit
     */
    public void applyWeaponModEffects(Player player, LivingEntity target) {
        // Burn Effect - set enemies on fire
        if (hasWeaponMod(player, "Burn Effect")) {
            target.setFireTicks(100); // 5 seconds of fire
            target.getWorld().spawnParticle(Particle.FLAME, target.getLocation(), 10, 0.3, 0.5, 0.3, 0.01);
        }
        
        // Frost Nova - slow/freeze enemies
        if (hasWeaponMod(player, "Frost Nova")) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2)); // Slow III for 4 seconds
            target.setFreezeTicks(100);
            target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation(), 20, 0.5, 1.0, 0.5, 0.1);
        }
    }
    
    /**
     * Get modified projectile count with Multi-Shot mod
     * Capped to prevent overpowered arrow spam
     */
    private int getModifiedProjectileCount(Player player, Weapon weapon) {
        int baseCount = weapon.getProjectileCount();
        if (hasWeaponMod(player, "Multi-Shot")) {
            double multiShotValue = getWeaponModValue(player, "Multi-Shot");
            baseCount += (int) multiShotValue; // Add extra projectiles
        }
        
        // Cap projectile count based on weapon type to prevent overpowered scaling
        // Arrow Storm is capped at 3 projectiles max (was allowing unlimited)
        if (weapon.getType() == Weapon.WeaponType.ARROW_STORM) {
            return Math.min(baseCount, 3); // Max 3 arrows per shot
        }
        
        // Other weapons can have more projectiles, but still cap at reasonable amount
        return Math.min(baseCount, 5); // General cap of 5 projectiles
    }
    
    /**
     * Get modified attack speed with Rapid Fire mod
     * Capped to prevent overpowered arrow spam
     */
    private double getModifiedAttackSpeed(Player player, Weapon weapon) {
        double baseSpeed = weapon.getAttackSpeed();
        if (hasWeaponMod(player, "Rapid Fire")) {
            double rapidFireValue = getWeaponModValue(player, "Rapid Fire");
            baseSpeed *= (1.0 + rapidFireValue * 0.3); // 30% per value point
        }
        
        // Cap attack speed for Arrow Storm specifically to prevent overpowered scaling
        // Arrow Storm starts at 3.0 attacks/sec, cap at 5.0 max (was allowing unlimited scaling)
        if (weapon.getType() == Weapon.WeaponType.ARROW_STORM) {
            return Math.min(baseSpeed, 5.0); // Max 5 attacks per second
        }
        
        // Other weapons can have higher attack speeds, but still cap at reasonable amount
        return Math.min(baseSpeed, 8.0); // General cap of 8 attacks per second
    }
    
    private void launchFireball(Player player, LivingEntity target, Weapon weapon) {
        Location eyeLoc = player.getEyeLocation();
        Vector direction = target.getEyeLocation().subtract(eyeLoc).toVector().normalize();
        
        int projectileCount = getModifiedProjectileCount(player, weapon);
        boolean isHoming = hasWeaponMod(player, "Homing Projectiles");
        boolean isExplosive = hasWeaponMod(player, "Explosive Rounds");
        
        for (int i = 0; i < projectileCount; i++) {
            Fireball fireball = player.getWorld().spawn(eyeLoc, Fireball.class);
            fireball.setDirection(direction);
            fireball.setYield(0); // No terrain damage
            fireball.setIsIncendiary(false);
            fireball.setShooter(player);
            
            // Homing effect
            if (isHoming) {
                BukkitTask homingTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (!fireball.isValid()) return;
                    LivingEntity nearest = findNearestEnemy(player, weapon.getRange() * 1.5);
                    if (nearest != null && nearest.isValid()) {
                        Vector newDir = nearest.getEyeLocation().subtract(fireball.getLocation()).toVector().normalize();
                        fireball.setDirection(newDir);
                        fireball.setVelocity(newDir.multiply(0.5));
                        fireball.getWorld().spawnParticle(Particle.ENCHANT, fireball.getLocation(), 1, 0.1, 0.1, 0.1, 0);
                    }
                }, 0L, 2L);
                
                // Cancel homing after 3 seconds
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (homingTask != null) homingTask.cancel();
                }, 60L);
            }
            
            // Schedule damage on impact
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (fireball.isValid()) {
                    Location loc = fireball.getLocation();
                    double totalDamageDealt = 0.0;
                    double aoeRadius = isExplosive ? weapon.getAreaOfEffect() * 1.5 : weapon.getAreaOfEffect();
                    
                    for (Entity entity : loc.getWorld().getNearbyEntities(loc, aoeRadius, aoeRadius, aoeRadius)) {
                        if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                            LivingEntity living = (LivingEntity) entity;
                            double finalDamage = calculateFinalDamage(player, weapon.getDamage(), living);
                            living.damage(finalDamage, player);
                            applyWeaponModEffects(player, living);
                            totalDamageDealt += finalDamage;
                        }
                    }
                    
                    // Explosive Rounds visual effect
                    if (isExplosive && totalDamageDealt > 0) {
                        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 5, 0.5, 0.5, 0.5, 0.1);
                        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.2f);
                    }
                    
                    if (totalDamageDealt > 0) {
                        applyLifesteal(player, totalDamageDealt);
                    }
                    fireball.remove();
                }
            }, 40L); // Remove after 2 seconds if not hit anything
        }
        
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.5f, 1.0f);
    }
    
    private void launchArrows(Player player, LivingEntity target, Weapon weapon) {
        Location eyeLoc = player.getEyeLocation();
        Vector direction = target.getEyeLocation().subtract(eyeLoc).toVector().normalize();
        
        double finalDamage = calculateFinalDamage(player, weapon.getDamage(), target);
        
        // For Endermen, apply damage directly instead of using arrows (they teleport away)
        if (target.getType() == org.bukkit.entity.EntityType.ENDERMAN) {
            target.damage(finalDamage, player);
            applyLifesteal(player, finalDamage);
            // Visual feedback
            player.getWorld().spawnParticle(org.bukkit.Particle.CRIT, target.getEyeLocation(), 10, 0.3, 0.5, 0.3, 0);
            player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.3f, 1.2f);
            return;
        }
        
        int projectileCount = getModifiedProjectileCount(player, weapon);
        boolean isHoming = hasWeaponMod(player, "Homing Projectiles");
        boolean isPiercing = hasWeaponMod(player, "Piercing Shot");
        
        for (int i = 0; i < projectileCount; i++) {
            Arrow arrow = player.getWorld().spawnArrow(eyeLoc, direction, 2.0f, 2.0f);
            arrow.setShooter(player);
            arrow.setDamage(finalDamage);
            arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
            
            // Piercing Shot - arrows pass through
            if (isPiercing) {
                arrow.setPierceLevel((byte) 3); // Can hit up to 3 enemies
            }
            
            // Fast despawn timer - remove arrows after 2 seconds (40 ticks) to prevent server lag
            // This is much faster than vanilla 60 seconds, but gives enough time for arrows to hit
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (arrow.isValid() && !arrow.isDead()) {
                    arrow.remove();
                }
            }, 40L); // 2 seconds = 40 ticks
            
            // Homing effect
            if (isHoming) {
                BukkitTask homingTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (!arrow.isValid() || arrow.isDead()) return;
                    LivingEntity nearest = findNearestEnemy(player, weapon.getRange() * 1.5);
                    if (nearest != null && nearest.isValid()) {
                        Vector newDir = nearest.getEyeLocation().subtract(arrow.getLocation()).toVector().normalize();
                        arrow.setVelocity(newDir.multiply(2.0));
                        arrow.getWorld().spawnParticle(Particle.ENCHANT, arrow.getLocation(), 1, 0.1, 0.1, 0.1, 0);
                    }
                }, 0L, 2L);
                
                // Cancel homing when arrow despawns (2 seconds)
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (homingTask != null) homingTask.cancel();
                }, 40L);
            }
            
            // Apply lifesteal on arrow hit (we'll track this in a delayed task)
            final double damageForLifesteal = finalDamage;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (arrow.isDead() || !arrow.isValid()) {
                    applyLifesteal(player, damageForLifesteal);
                }
            }, 5L);
            
            // Add slight spread for multiple arrows
            if (projectileCount > 1) {
                Vector spread = new Vector(
                    (Math.random() - 0.5) * 0.2,
                    (Math.random() - 0.5) * 0.2,
                    (Math.random() - 0.5) * 0.2
                );
                arrow.setVelocity(direction.clone().add(spread).normalize().multiply(2.0));
            }
        }
        
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.3f, 1.2f);
    }
    
    private void strikeLightning(Player player, LivingEntity target, Weapon weapon) {
        Location targetLoc = target.getLocation();
        boolean isChainLightning = hasWeaponMod(player, "Chain Lightning");
        
        // Cap the effective range - use weapon range but don't let it exceed reasonable bounds
        double effectiveRange = Math.min(weapon.getRange(), 40.0); // Cap at 40 blocks max
        
        // Visual lightning effect
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, targetLoc.clone().add(0, 1, 0), 50, 0.5, 2, 0.5, 0.1);
        player.getWorld().playSound(targetLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.0f);
        
        // Damage nearby enemies within effective range (not unlimited range)
        double totalDamageDealt = 0.0;
        double aoe = weapon.getAreaOfEffect();
        // Cap AOE to prevent it from becoming too large
        double effectiveAOE = Math.min(aoe, 8.0); // Cap AOE at 8 blocks
        
        Set<LivingEntity> hitEntities = new HashSet<>();
        
        for (Entity entity : targetLoc.getWorld().getNearbyEntities(targetLoc, effectiveAOE, effectiveAOE, effectiveAOE)) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                LivingEntity living = (LivingEntity) entity;
                // Check if within effective range from player
                double distance = player.getLocation().distance(living.getLocation());
                if (distance <= effectiveRange) {
                    double finalDamage = calculateFinalDamage(player, weapon.getDamage(), living);
                    living.damage(finalDamage, player);
                    applyWeaponModEffects(player, living);
                    totalDamageDealt += finalDamage;
                    hitEntities.add(living);
                    
                    // Visual effect on hit enemy
                    living.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, living.getEyeLocation(), 20, 0.3, 0.5, 0.3, 0);
                }
            }
        }
        
        // Chain Lightning - chain to nearby enemies
        if (isChainLightning && !hitEntities.isEmpty()) {
            double chainRange = 8.0;
            double chainDamage = weapon.getDamage() * 0.6; // 60% of base damage for chained hits
            int maxChains = 3;
            int chainCount = 0;
            
            LivingEntity lastHit = hitEntities.iterator().next();
            Set<LivingEntity> chainedEntities = new HashSet<>(hitEntities);
            
            while (chainCount < maxChains && lastHit != null && lastHit.isValid()) {
                LivingEntity nextTarget = null;
                double nearestDist = chainRange * chainRange;
                
                for (Entity entity : lastHit.getNearbyEntities(chainRange, chainRange, chainRange)) {
                    if (entity instanceof LivingEntity && !(entity instanceof Player) && !chainedEntities.contains(entity)) {
                        double dist = lastHit.getLocation().distanceSquared(entity.getLocation());
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nextTarget = (LivingEntity) entity;
                        }
                    }
                }
                
                if (nextTarget != null) {
                    double finalDamage = calculateFinalDamage(player, chainDamage, nextTarget);
                    nextTarget.damage(finalDamage, player);
                    applyWeaponModEffects(player, nextTarget);
                    totalDamageDealt += finalDamage;
                    chainedEntities.add(nextTarget);
                    
                    // Visual chain effect
                    Location chainLoc = lastHit.getEyeLocation();
                    Location nextLoc = nextTarget.getEyeLocation();
                    Vector chainDir = nextLoc.subtract(chainLoc).toVector().normalize();
                    for (int i = 0; i < 10; i++) {
                        Location particleLoc = chainLoc.clone().add(chainDir.clone().multiply(i * 0.5));
                        nextTarget.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, particleLoc, 1, 0.1, 0.1, 0.1, 0);
                    }
                    
                    lastHit = nextTarget;
                    chainCount++;
                } else {
                    break;
                }
            }
        }
        
        if (totalDamageDealt > 0) {
            applyLifesteal(player, totalDamageDealt);
        }
    }
    
    private void spawnTNT(Player player, LivingEntity target, Weapon weapon) {
        Location targetLoc = target.getLocation();
        
        TNTPrimed tnt = player.getWorld().spawn(targetLoc.clone().add(0, 1, 0), TNTPrimed.class);
        tnt.setFuseTicks(30); // 1.5 seconds fuse
        tnt.setYield((float) weapon.getAreaOfEffect());
        tnt.setIsIncendiary(false);
        
        // Tag TNT with player UUID for XP attribution
        tnt.setMetadata("roguecraft_tnt_owner", new org.bukkit.metadata.FixedMetadataValue(plugin, player.getUniqueId().toString()));
        
        // Custom damage since TNT damage is weird
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (tnt.isValid()) {
                Location explodeLoc = tnt.getLocation();
                
                // Store actual explosion location for XP attribution
                tnt.setMetadata("roguecraft_tnt_explosion_loc", new org.bukkit.metadata.FixedMetadataValue(plugin, explodeLoc.clone()));
                tnt.getWorld().spawnParticle(Particle.EXPLOSION, explodeLoc, 5, 1, 1, 1, 0);
                tnt.getWorld().playSound(explodeLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                
                double totalDamageDealt = 0.0;
                for (Entity entity : explodeLoc.getWorld().getNearbyEntities(explodeLoc, weapon.getAreaOfEffect(), weapon.getAreaOfEffect(), weapon.getAreaOfEffect())) {
                    if (entity instanceof LivingEntity && entity != player) { // Exclude the player who spawned TNT
                        double distance = entity.getLocation().distance(explodeLoc);
                        double distanceMultiplier = 1.0 - (distance / weapon.getAreaOfEffect());
                        double baseDamage = weapon.getDamage() * distanceMultiplier;
                        double finalDamage = calculateFinalDamage(player, baseDamage);
                        LivingEntity living = (LivingEntity) entity;
                        
                        // Tag entity with TNT owner for XP attribution if killed by explosion
                        living.setMetadata("roguecraft_tnt_damaged", new org.bukkit.metadata.FixedMetadataValue(plugin, player.getUniqueId().toString()));
                        
                        living.damage(finalDamage, player);
                        applyWeaponModEffects(player, living);
                        totalDamageDealt += finalDamage;
                    }
                }
                if (totalDamageDealt > 0) {
                    applyLifesteal(player, totalDamageDealt);
                }
                tnt.remove();
            }
        }, 30L);
        
        player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 0.5f, 1.0f);
    }
    
    private void throwPotion(Player player, LivingEntity target, Weapon weapon) {
        Vector direction = target.getEyeLocation().subtract(player.getEyeLocation()).toVector().normalize();
        
        // Launch potion from player with increased velocity for better range
        ThrownPotion potion = player.launchProjectile(ThrownPotion.class);
        potion.setVelocity(direction.multiply(1.2)); // Increased from 0.75 to 1.2 for better range
        
        // Mark this potion as a weapon potion with player and weapon info
        // We'll handle damage/effects in the PotionSplashEvent listener
        potion.setMetadata("weapon_potion", new org.bukkit.metadata.FixedMetadataValue(plugin, player.getUniqueId().toString()));
        potion.setMetadata("weapon_damage", new org.bukkit.metadata.FixedMetadataValue(plugin, weapon.getDamage()));
        potion.setMetadata("weapon_aoe", new org.bukkit.metadata.FixedMetadataValue(plugin, weapon.getAreaOfEffect()));
        
        // Create a harmless splash potion (water splash) - damage will be handled in event
        org.bukkit.inventory.ItemStack potionItem = new org.bukkit.inventory.ItemStack(Material.SPLASH_POTION);
        org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) potionItem.getItemMeta();
        // Use water splash potion (harmless) - we'll handle effects manually
        potionItem.setItemMeta(meta);
        potion.setItem(potionItem);
        
        player.playSound(player.getLocation(), Sound.ENTITY_SPLASH_POTION_THROW, 0.5f, 0.8f);
    }
    
    private void launchIceShard(Player player, LivingEntity target, Weapon weapon) {
        Location eyeLoc = player.getEyeLocation();
        Vector direction = target.getEyeLocation().subtract(eyeLoc).toVector().normalize();
        
        // Use snowball as projectile
        Snowball snowball = player.getWorld().spawn(eyeLoc, Snowball.class);
        snowball.setVelocity(direction.multiply(2.0));
        snowball.setShooter(player);
        
        // Mark this snowball as an ice shard weapon so we can handle damage on hit
        snowball.setMetadata("ice_shard_weapon", new org.bukkit.metadata.FixedMetadataValue(plugin, player.getUniqueId().toString()));
        snowball.setMetadata("ice_shard_damage", new org.bukkit.metadata.FixedMetadataValue(plugin, weapon.getDamage()));
        snowball.setMetadata("ice_shard_aoe", new org.bukkit.metadata.FixedMetadataValue(plugin, weapon.getAreaOfEffect()));
        
        // Visual ice particles
        BukkitTask particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (snowball.isValid()) {
                snowball.getWorld().spawnParticle(Particle.SNOWFLAKE, snowball.getLocation(), 3, 0.1, 0.1, 0.1, 0);
            }
        }, 0L, 1L);
        
        // Fallback: apply damage after timeout if snowball didn't hit anything
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            particleTask.cancel();
            if (snowball.isValid() && snowball.hasMetadata("ice_shard_weapon")) {
                // Snowball didn't hit anything, apply AOE damage at current location
                Location loc = snowball.getLocation();
                loc.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 30, 1, 1, 1, 0);
                
                double weaponDamage = snowball.getMetadata("ice_shard_damage").get(0).asDouble();
                double weaponAoe = snowball.getMetadata("ice_shard_aoe").get(0).asDouble();
                
                double totalDamageDealt = 0.0;
                for (Entity entity : loc.getWorld().getNearbyEntities(loc, weaponAoe, weaponAoe, weaponAoe)) {
                    if (entity instanceof LivingEntity && !(entity instanceof Player) && entity != player) {
                        LivingEntity living = (LivingEntity) entity;
                        double finalDamage = calculateFinalDamage(player, weaponDamage, living);
                        living.damage(finalDamage, player);
                        applyWeaponModEffects(player, living);
                        totalDamageDealt += finalDamage;
                        living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1)); // Slow II for 3 seconds
                        living.setFreezeTicks(100); // Freeze effect
                    }
                }
                if (totalDamageDealt > 0) {
                    applyLifesteal(player, totalDamageDealt);
                }
                snowball.remove();
            }
        }, 60L); // 3 seconds timeout
        
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.5f, 1.5f);
    }
    
    private void launchMagicMissile(Player player, LivingEntity target, Weapon weapon) {
        Location eyeLoc = player.getEyeLocation();
        Vector direction = target.getEyeLocation().subtract(eyeLoc).toVector().normalize();
        
        for (int i = 0; i < weapon.getProjectileCount(); i++) {
            // Use small fireball for magic missile
            SmallFireball missile = player.getWorld().spawn(eyeLoc, SmallFireball.class);
            missile.setDirection(direction);
            missile.setYield(0);
            missile.setIsIncendiary(false);
            missile.setShooter(player);
            
            // Homing effect - update direction periodically
            BukkitTask homingTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!missile.isValid() || !target.isValid()) {
                    return;
                }
                
                Vector newDirection = target.getEyeLocation().subtract(missile.getLocation()).toVector().normalize();
                missile.setDirection(newDirection);
                missile.setVelocity(newDirection.multiply(0.5));
                
                // Particle trail
                missile.getWorld().spawnParticle(Particle.ENCHANT, missile.getLocation(), 2, 0.1, 0.1, 0.1, 0);
            }, 0L, 2L);
            
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                homingTask.cancel();
                if (missile.isValid()) {
                    Location loc = missile.getLocation();
                    loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 20, 0.5, 0.5, 0.5, 0);
                    
                    double totalDamageDealt = 0.0;
                    for (Entity entity : loc.getWorld().getNearbyEntities(loc, weapon.getAreaOfEffect(), weapon.getAreaOfEffect(), weapon.getAreaOfEffect())) {
                        if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                            LivingEntity living = (LivingEntity) entity;
                            double finalDamage = calculateFinalDamage(player, weapon.getDamage(), living);
                            living.damage(finalDamage, player);
                            totalDamageDealt += finalDamage;
                        }
                    }
                    if (totalDamageDealt > 0) {
                        applyLifesteal(player, totalDamageDealt);
                    }
                    missile.remove();
                }
            }, 60L);
        }
        
        player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.5f, 1.5f);
    }
}


