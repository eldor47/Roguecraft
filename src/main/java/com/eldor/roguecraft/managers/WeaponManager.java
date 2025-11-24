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
    private final Map<UUID, Long> lifestealLastHeal; // Player UUID -> Last heal time (for minimum interval)
    
    public WeaponManager(RoguecraftPlugin plugin) {
        this.plugin = plugin;
        this.weaponTasks = new HashMap<>();
        this.lifestealHealingTracker = new HashMap<>();
        this.lifestealLastReset = new HashMap<>();
        this.lifestealLastHeal = new HashMap<>();
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
        lifestealLastHeal.remove(playerId);
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
        lifestealLastHeal.clear();
    }
    
    private LivingEntity findNearestEnemy(Player player, double range) {
        Location playerLoc = player.getLocation();
        LivingEntity nearest = null;
        double nearestDistance = range * range; // Use squared distance for efficiency
        
        for (Entity entity : player.getWorld().getNearbyEntities(playerLoc, range, range, range)) {
            // Ignore Text Display entities (used for XP display)
            if (entity instanceof org.bukkit.entity.TextDisplay || 
                entity instanceof org.bukkit.entity.Display) {
                continue;
            }
            
            // Ignore ArmorStands used for XP display (they have metadata "roguecraft_xp_display")
            if (entity instanceof org.bukkit.entity.ArmorStand && 
                entity.hasMetadata("roguecraft_xp_display")) {
                continue;
            }
            
            if (entity instanceof LivingEntity && !(entity instanceof Player) && !entity.isDead()) {
                // Exclude decoy villagers and summons from weapon targeting
                if (entity.hasMetadata("roguecraft_decoy") || entity.hasMetadata("roguecraft_summon")) {
                    continue;
                }
                
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
        // ProtocolLib: Create visual telegraph for attack
        if (plugin.getProtocolLibIntegration() != null && plugin.getProtocolLibIntegration().isEnabled()) {
            double damage = calculateFinalDamage(player, weapon.getDamage(), target);
            plugin.getProtocolLibIntegration().createAttackTelegraph(player, target.getLocation(), weapon.getType(), damage);
        }
        
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
            // Use player-specific stats
            damageMultiplier = teamRun.getStat(player, "damage");
            critChance = teamRun.getStat(player, "crit_chance");
            critDamage = teamRun.getStat(player, "crit_damage");
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
        
        // Apply Big Bonk - 2% chance for 20x damage
        if (target != null) {
            for (org.bukkit.metadata.MetadataValue meta : player.getMetadata("gacha_item_big_bonk")) {
                if (meta.value() instanceof com.eldor.roguecraft.models.GachaItem) {
                    com.eldor.roguecraft.models.GachaItem item = (com.eldor.roguecraft.models.GachaItem) meta.value();
                    java.util.Random random = new java.util.Random();
                    if (random.nextDouble() < item.getValue()) {
                        // Big Bonk - 20x damage!
                        finalDamage *= 20.0;
                        // Visual and audio feedback
                        target.getWorld().spawnParticle(Particle.EXPLOSION, target.getEyeLocation(), 20, 0.5, 0.5, 0.5, 0.1);
                        target.getWorld().playSound(target.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);
                        player.sendMessage(org.bukkit.ChatColor.YELLOW + "💥 BIG BONK! 💥");
                    }
                }
            }
            
            // Apply Boss Buster - 15% more damage to elites/bosses
            boolean isElite = target.hasMetadata("is_elite") || target.hasMetadata("is_legendary") || 
                             target.hasMetadata("is_elite_boss") || target.hasMetadata("roguecraft_elite_boss");
            if (isElite) {
                for (org.bukkit.metadata.MetadataValue meta : player.getMetadata("gacha_item_boss_buster")) {
                    if (meta.value() instanceof com.eldor.roguecraft.models.GachaItem) {
                        com.eldor.roguecraft.models.GachaItem item = (com.eldor.roguecraft.models.GachaItem) meta.value();
                        finalDamage *= (1.0 + item.getValue()); // Add 15% damage
                    }
                }
            }
        }
        
        // Apply synergy damage multipliers
        Object synergyRun = teamRun != null ? teamRun : run;
        if (synergyRun != null) {
            double synergyMultiplier = plugin.getSynergyManager().getDamageMultiplier(synergyRun, player);
            finalDamage *= synergyMultiplier;
        }
        
        // Check for crit
        boolean isCrit = Math.random() < critChance;
        if (isCrit) {
            // Tiered crit damage reduction based on mob type:
            // - Regular mobs: Full crit damage
            // - Elite mobs: 25% crit damage reduction (75% of crit damage)
            // - Legendary mobs: 50% crit damage reduction (50% of crit damage)
            // - Bosses: 50% crit damage reduction (50% of crit damage)
            boolean isBoss = target != null && (target.hasMetadata("roguecraft_boss") || target.hasMetadata("roguecraft_elite_boss"));
            boolean isLegendary = target != null && target.hasMetadata("is_legendary");
            boolean isElite = target != null && (target.hasMetadata("roguecraft_elite") || target.hasMetadata("is_elite"));
            
            double effectiveCritDamage;
            if (isBoss || isLegendary) {
                effectiveCritDamage = critDamage * 0.5; // 50% crit damage reduction for bosses and legendaries
            } else if (isElite) {
                effectiveCritDamage = critDamage * 0.75; // 25% crit damage reduction for elites (less than legendary)
            } else {
                effectiveCritDamage = critDamage; // Full crit damage for regular mobs
            }
            
            finalDamage *= effectiveCritDamage;
            // Enhanced visual feedback for crit using ProtocolLib
            if (target != null) {
                if (plugin.getProtocolLibIntegration() != null && plugin.getProtocolLibIntegration().isEnabled()) {
                    plugin.getProtocolLibIntegration().showCriticalHitEffect(player, target, finalDamage);
                } else {
                    // Fallback to basic particles
                    player.getWorld().spawnParticle(Particle.CRIT, target.getEyeLocation(), 20, 0.5, 0.5, 0.5, 0.1);
                }
            }
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.5f, 1.5f);
            
            // Trigger Critical Mass synergy
            if (synergyRun != null && target != null) {
                plugin.getSynergyManager().onCriticalHit(player, target, finalDamage);
            }
        }
        
        // Boss damage cap: Maximum 12% of boss's max health per hit (reduced from 20% for harder difficulty)
        // This prevents one-shotting bosses but makes them more challenging
        if (target != null) {
            boolean isBoss = target.hasMetadata("roguecraft_boss") || target.hasMetadata("roguecraft_elite_boss");
            if (isBoss) {
                double maxHealth = target.getMaxHealth();
                double maxDamagePerHit = maxHealth * 0.12; // 12% of max health (reduced from 20%)
                finalDamage = Math.min(finalDamage, maxDamagePerHit);
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
            // Use player-specific power-ups
            for (com.eldor.roguecraft.models.PowerUp powerUp : teamRun.getCollectedPowerUps(player)) {
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
        
        // Apply lifesteal with strict healing rate cap (max 1 heart = 2 HP per second)
        // Also enforce minimum time between heals to prevent rapid stacking
        if (hasVampireAura && lifestealPercent > 0) {
            UUID playerId = player.getUniqueId();
            long currentTime = System.currentTimeMillis();
            long lastReset = lifestealLastReset.getOrDefault(playerId, 0L);
            long lastHeal = lifestealLastHeal.getOrDefault(playerId, 0L);
            
            // Reset tracker every second
            if (currentTime - lastReset >= 1000) {
                lifestealHealingTracker.put(playerId, 0.0);
                lifestealLastReset.put(playerId, currentTime);
            }
            
            // Enforce minimum 200ms (5 heals per second max) between individual heal applications
            // This prevents rapid stacking from multiple AOE hits
            if (currentTime - lastHeal < 200) {
                return; // Too soon since last heal, skip this application
            }
            
            double maxHealingPerSecond = 2.0; // Cap at 1 heart (2 HP) per second - reduced to prevent invincibility
            double currentHealingThisSecond = lifestealHealingTracker.getOrDefault(playerId, 0.0);
            
            double healAmount = damageDealt * (lifestealPercent / 100.0);
            double remainingHealingBudget = Math.max(0, maxHealingPerSecond - currentHealingThisSecond);
            double actualHealAmount = Math.min(healAmount, remainingHealingBudget);
            
            if (actualHealAmount > 0) {
                double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
                double currentHealth = player.getHealth();
                double newHealth = Math.min(maxHealth, currentHealth + actualHealAmount);
                player.setHealth(newHealth);
                
                // Update trackers
                lifestealHealingTracker.put(playerId, currentHealingThisSecond + actualHealAmount);
                lifestealLastHeal.put(playerId, currentTime);
                
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
            // Use player-specific power-ups
            for (com.eldor.roguecraft.models.PowerUp powerUp : teamRun.getCollectedPowerUps(player)) {
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
            // Use Math.max to ensure fire ticks don't decrease if already on fire
            int currentFireTicks = target.getFireTicks();
            int newFireTicks = Math.max(currentFireTicks, 100); // 5 seconds of fire (100 ticks)
            target.setFireTicks(newFireTicks);
            target.getWorld().spawnParticle(Particle.FLAME, target.getLocation(), 10, 0.3, 0.5, 0.3, 0.01);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 0.3f, 1.5f);
        }
        
        // Frost Nova - slow/freeze enemies
        if (hasWeaponMod(player, "Frost Nova")) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2)); // Slow III for 4 seconds
            target.setFreezeTicks(100);
            target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation(), 20, 0.5, 1.0, 0.5, 0.1);
        }
        
        // Apply gacha on-hit effects
        applyGachaOnHitEffects(player, target);
    }
    
    /**
     * Apply all gacha item on-hit effects
     */
    private void applyGachaOnHitEffects(Player player, LivingEntity target) {
        java.util.Random random = new java.util.Random();
        
        // Get player's run to check gacha items (supports stacking)
        com.eldor.roguecraft.models.TeamRun teamRun = plugin.getRunManager().getTeamRun(player);
        com.eldor.roguecraft.models.Run run = null;
        if (teamRun == null) {
            run = plugin.getRunManager().getRun(player);
        }
        
        // Check all moldy cheese items the player has (supports stacking - each item has its own proc chance)
        int moldyCheeseCount = 0;
        if (teamRun != null) {
            for (com.eldor.roguecraft.models.GachaItem item : teamRun.getCollectedGachaItems(player)) {
                if (item.getId().equals("moldy_cheese")) {
                    moldyCheeseCount++;
                    if (random.nextDouble() < item.getValue()) {
                        // Moldy Cheese - poison effect (stacks - each item procs independently)
                        int poisonDuration = 100; // 5 seconds base
                        int poisonLevel = Math.min(1, moldyCheeseCount - 1); // Level 0 for 1 item, Level 1 for 2+ items
                        target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.POISON, poisonDuration, poisonLevel, false, true));
                        
                        // Enhanced visual feedback
                        target.getWorld().spawnParticle(Particle.ITEM_SLIME, target.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0.02);
                        target.getWorld().spawnParticle(Particle.SMOKE, target.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.05);
                        // Play sound effect
                        target.getWorld().playSound(target.getLocation(), org.bukkit.Sound.ENTITY_SLIME_SQUISH, 0.5f, 0.8f);
                    }
                }
            }
        } else if (run != null) {
            for (com.eldor.roguecraft.models.GachaItem item : run.getCollectedGachaItems()) {
                if (item.getId().equals("moldy_cheese")) {
                    moldyCheeseCount++;
                    if (random.nextDouble() < item.getValue()) {
                        // Moldy Cheese - poison effect (stacks - each item procs independently)
                        int poisonDuration = 100; // 5 seconds base
                        int poisonLevel = Math.min(1, moldyCheeseCount - 1); // Level 0 for 1 item, Level 1 for 2+ items
                        target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.POISON, poisonDuration, poisonLevel, false, true));
                        
                        // Enhanced visual feedback
                        target.getWorld().spawnParticle(Particle.ITEM_SLIME, target.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0.02);
                        target.getWorld().spawnParticle(Particle.SMOKE, target.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.05);
                        // Play sound effect
                        target.getWorld().playSound(target.getLocation(), org.bukkit.Sound.ENTITY_SLIME_SQUISH, 0.5f, 0.8f);
                    }
                }
            }
        }
        
        // Also check metadata for backwards compatibility (legacy system)
        for (org.bukkit.metadata.MetadataValue meta : player.getMetadata("gacha_item_moldy_cheese")) {
            if (meta.value() instanceof com.eldor.roguecraft.models.GachaItem) {
                com.eldor.roguecraft.models.GachaItem item = (com.eldor.roguecraft.models.GachaItem) meta.value();
                if (random.nextDouble() < item.getValue()) {
                    // Moldy Cheese - poison effect
                    target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.POISON, 100, 0, false, true)); // 5 seconds
                    target.getWorld().spawnParticle(Particle.ITEM_SLIME, target.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0.02);
                    target.getWorld().spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, target.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.05);
                    target.getWorld().playSound(target.getLocation(), org.bukkit.Sound.ENTITY_SLIME_SQUISH, 0.5f, 0.8f);
                }
            }
        }
        
        // Check all ice crystal items the player has (supports stacking - each item has its own proc chance)
        if (teamRun != null) {
            for (com.eldor.roguecraft.models.GachaItem item : teamRun.getCollectedGachaItems(player)) {
                if (item.getId().equals("ice_crystal")) {
                    if (random.nextDouble() < item.getValue()) {
                        // Ice Crystal - freeze effect + damage (stacks - each item procs independently)
                        // Deal ice damage (50% of base weapon damage)
                        double iceDamage = 0.0;
                        Weapon playerWeapon = teamRun.getWeapon(player);
                        if (playerWeapon != null) {
                            iceDamage = playerWeapon.getDamage() * 0.5; // 50% of weapon damage
                        }
                        if (iceDamage > 0) {
                            double finalIceDamage = calculateFinalDamage(player, iceDamage, target);
                            target.damage(finalIceDamage, player);
                        }
                        target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SLOWNESS, 100, 1, false, true)); // Slow II for 5 seconds
                        target.setFreezeTicks(100);
                        // Enhanced visual feedback
                        target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.02);
                        target.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, target.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.01);
                        target.getWorld().playSound(target.getLocation(), org.bukkit.Sound.BLOCK_GLASS_BREAK, 0.6f, 1.2f);
                    }
                }
            }
        } else if (run != null) {
            for (com.eldor.roguecraft.models.GachaItem item : run.getCollectedGachaItems()) {
                if (item.getId().equals("ice_crystal")) {
                    if (random.nextDouble() < item.getValue()) {
                        // Ice Crystal - freeze effect + damage (stacks - each item procs independently)
                        // Deal ice damage (50% of base weapon damage)
                        double iceDamage = 0.0;
                        Weapon playerWeapon = run.getWeapon();
                        if (playerWeapon != null) {
                            iceDamage = playerWeapon.getDamage() * 0.5; // 50% of weapon damage
                        }
                        if (iceDamage > 0) {
                            double finalIceDamage = calculateFinalDamage(player, iceDamage, target);
                            target.damage(finalIceDamage, player);
                        }
                        target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SLOWNESS, 100, 1, false, true)); // Slow II for 5 seconds
                        target.setFreezeTicks(100);
                        // Enhanced visual feedback
                        target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.02);
                        target.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, target.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.01);
                        target.getWorld().playSound(target.getLocation(), org.bukkit.Sound.BLOCK_GLASS_BREAK, 0.6f, 1.2f);
                    }
                }
            }
        }
        
        // Also check metadata for backwards compatibility (legacy system)
        for (org.bukkit.metadata.MetadataValue meta : player.getMetadata("gacha_item_ice_crystal")) {
            if (meta.value() instanceof com.eldor.roguecraft.models.GachaItem) {
                com.eldor.roguecraft.models.GachaItem item = (com.eldor.roguecraft.models.GachaItem) meta.value();
                if (random.nextDouble() < item.getValue()) {
                    // Ice Crystal - freeze effect + damage
                    // Deal ice damage (50% of base weapon damage)
                    double iceDamage = 0.0;
                    Weapon playerWeapon = null;
                    TeamRun tr = plugin.getRunManager().getTeamRun(player);
                    Run r = null;
                    if (tr != null) {
                        playerWeapon = tr.getWeapon(player);
                    } else {
                        r = plugin.getRunManager().getRun(player);
                        if (r != null) {
                            playerWeapon = r.getWeapon();
                        }
                    }
                    if (playerWeapon != null) {
                        iceDamage = playerWeapon.getDamage() * 0.5; // 50% of weapon damage
                    }
                    if (iceDamage > 0) {
                        double finalIceDamage = calculateFinalDamage(player, iceDamage, target);
                        target.damage(finalIceDamage, player);
                    }
                    target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SLOWNESS, 100, 1, false, true)); // Slow II for 5 seconds
                    target.setFreezeTicks(100);
                    target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.02);
                    target.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, target.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.01);
                    target.getWorld().playSound(target.getLocation(), org.bukkit.Sound.BLOCK_GLASS_BREAK, 0.6f, 1.2f);
                }
            }
        }
        
        // Check all cursed doll items the player has (supports stacking)
        if (teamRun != null) {
            for (com.eldor.roguecraft.models.GachaItem item : teamRun.getCollectedGachaItems(player)) {
                if (item.getId().equals("cursed_doll")) {
                    if (random.nextDouble() < item.getValue()) {
                        // Cursed Doll - curse effect (30% max HP per second for 3 seconds)
                        applyCurseEffect(player, target);
                    }
                }
            }
        } else if (run != null) {
            for (com.eldor.roguecraft.models.GachaItem item : run.getCollectedGachaItems()) {
                if (item.getId().equals("cursed_doll")) {
                    if (random.nextDouble() < item.getValue()) {
                        // Cursed Doll - curse effect (30% max HP per second for 3 seconds)
                        applyCurseEffect(player, target);
                    }
                }
            }
        }
        
        // Check all spicy meatball items the player has (supports stacking)
        if (teamRun != null) {
            for (com.eldor.roguecraft.models.GachaItem item : teamRun.getCollectedGachaItems(player)) {
                if (item.getId().equals("spicy_meatball")) {
                    if (random.nextDouble() < item.getValue()) {
                        // Spicy Meatball - explosion effect
                        createExplosionEffect(player, target, item.getValue());
                    }
                }
            }
        } else if (run != null) {
            for (com.eldor.roguecraft.models.GachaItem item : run.getCollectedGachaItems()) {
                if (item.getId().equals("spicy_meatball")) {
                    if (random.nextDouble() < item.getValue()) {
                        // Spicy Meatball - explosion effect
                        createExplosionEffect(player, target, item.getValue());
                    }
                }
            }
        }
        
        // Check all power gloves items the player has (supports stacking)
        if (teamRun != null) {
            for (com.eldor.roguecraft.models.GachaItem item : teamRun.getCollectedGachaItems(player)) {
                if (item.getId().equals("power_gloves")) {
                    if (random.nextDouble() < item.getValue()) {
                        // Power Gloves - giant blast effect
                        createBlastEffect(player, target);
                    }
                }
            }
        } else if (run != null) {
            for (com.eldor.roguecraft.models.GachaItem item : run.getCollectedGachaItems()) {
                if (item.getId().equals("power_gloves")) {
                    if (random.nextDouble() < item.getValue()) {
                        // Power Gloves - giant blast effect
                        createBlastEffect(player, target);
                    }
                }
            }
        }
        
        // Also check metadata for backwards compatibility (legacy system)
        for (org.bukkit.metadata.MetadataValue meta : player.getMetadata("gacha_item_cursed_doll")) {
            if (meta.value() instanceof com.eldor.roguecraft.models.GachaItem) {
                com.eldor.roguecraft.models.GachaItem item = (com.eldor.roguecraft.models.GachaItem) meta.value();
                if (random.nextDouble() < item.getValue()) {
                    // Cursed Doll - curse effect (30% max HP per second for 3 seconds)
                    applyCurseEffect(player, target);
                }
            }
        }
        
        for (org.bukkit.metadata.MetadataValue meta : player.getMetadata("gacha_item_spicy_meatball")) {
            if (meta.value() instanceof com.eldor.roguecraft.models.GachaItem) {
                com.eldor.roguecraft.models.GachaItem item = (com.eldor.roguecraft.models.GachaItem) meta.value();
                if (random.nextDouble() < item.getValue()) {
                    // Spicy Meatball - explosion effect
                    createExplosionEffect(player, target, item.getValue());
                }
            }
        }
        
        for (org.bukkit.metadata.MetadataValue meta : player.getMetadata("gacha_item_power_gloves")) {
            if (meta.value() instanceof com.eldor.roguecraft.models.GachaItem) {
                com.eldor.roguecraft.models.GachaItem item = (com.eldor.roguecraft.models.GachaItem) meta.value();
                if (random.nextDouble() < item.getValue()) {
                    // Power Gloves - giant blast effect
                    createBlastEffect(player, target);
                }
            }
        }
    }
    
    /**
     * Apply curse effect - deals 30% of max HP per second for 3 seconds
     */
    private void applyCurseEffect(Player player, LivingEntity target) {
        if (target.isDead() || !target.isValid()) return;
        
        double maxHP = target.getMaxHealth();
        double curseDamage = maxHP * 0.30; // 30% of max HP
        
        // Visual effect
        target.getWorld().spawnParticle(Particle.SMOKE, target.getEyeLocation(), 20, 0.3, 0.5, 0.3, 0.05);
        target.getWorld().playSound(target.getLocation(), org.bukkit.Sound.ENTITY_WITHER_HURT, 0.5f, 1.5f);
        
        // Apply curse damage over 3 seconds (60 ticks, every 20 ticks = 1 second)
        final LivingEntity finalTarget = target;
        final int[] ticksElapsed = {0};
        org.bukkit.scheduler.BukkitTask curseTask = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (finalTarget.isDead() || !finalTarget.isValid() || ticksElapsed[0] >= 60) {
                return;
            }
            
            if (ticksElapsed[0] % 20 == 0) { // Every second
                finalTarget.damage(curseDamage, player);
                finalTarget.getWorld().spawnParticle(Particle.SMOKE, finalTarget.getEyeLocation(), 10, 0.2, 0.3, 0.2, 0.02);
            }
            
            ticksElapsed[0]++;
        }, 0L, 1L);
        
        // Cancel task after 3 seconds
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (curseTask != null && !curseTask.isCancelled()) {
                curseTask.cancel();
            }
        }, 60L);
    }
    
    /**
     * Create explosion effect - deals 65% damage to nearby enemies
     */
    private void createExplosionEffect(Player player, LivingEntity target, double damagePercent) {
        org.bukkit.Location explodeLoc = target.getLocation();
        double explosionRadius = 3.0; // 3 block radius
        
        // Visual and audio
        target.getWorld().spawnParticle(Particle.EXPLOSION, explodeLoc, 10, 0.5, 0.5, 0.5, 0.1);
        target.getWorld().playSound(explodeLoc, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
        
        // Get base damage from player's weapon
        double baseDamage = 0.0;
        TeamRun teamRun = plugin.getRunManager().getTeamRun(player);
        Run run = null;
        if (teamRun != null && teamRun.isActive()) {
            // Use player-specific weapon
            Weapon playerWeapon = teamRun.getWeapon(player);
            if (playerWeapon != null) {
                baseDamage = playerWeapon.getDamage();
            }
        } else {
            run = plugin.getRunManager().getRun(player);
            if (run != null && run.isActive()) {
                if (run.getWeapon() != null) {
                    baseDamage = run.getWeapon().getDamage();
                }
            }
        }
        
        double baseExplosionDamage = baseDamage * damagePercent; // 65% of base damage
        
        // Get team members to exclude from damage
        Set<UUID> teamMemberIds = new HashSet<>();
        if (teamRun != null && teamRun.isActive()) {
            teamMemberIds.addAll(teamRun.getPlayerIds());
        }
        
        // Apply damage to nearby enemies
        for (org.bukkit.entity.Entity entity : explodeLoc.getWorld().getNearbyEntities(explodeLoc, explosionRadius, explosionRadius, explosionRadius)) {
            if (entity instanceof LivingEntity && entity != target) {
                // Exclude team members
                if (entity instanceof org.bukkit.entity.Player) {
                    org.bukkit.entity.Player targetPlayer = (org.bukkit.entity.Player) entity;
                    if (teamMemberIds.contains(targetPlayer.getUniqueId())) {
                        continue; // Skip team members
                    }
                }
                
                LivingEntity living = (LivingEntity) entity;
                
                // Check if it's a boss - apply additional damage reduction for explosion effects
                boolean isBoss = living.hasMetadata("roguecraft_boss") || living.hasMetadata("roguecraft_elite_boss");
                double effectiveExplosionDamage = baseExplosionDamage;
                if (isBoss) {
                    // Explosion effects deal reduced damage to bosses (50% of normal damage)
                    effectiveExplosionDamage *= 0.5;
                }
                
                double distance = entity.getLocation().distance(explodeLoc);
                double distanceMultiplier = Math.max(0.1, 1.0 - (distance / explosionRadius));
                double finalDamage = calculateFinalDamage(player, effectiveExplosionDamage * distanceMultiplier, living);
                living.damage(finalDamage, player);
                living.getWorld().spawnParticle(Particle.EXPLOSION, living.getLocation(), 3, 0.2, 0.2, 0.2, 0.05);
            }
        }
    }
    
    /**
     * Create giant blast effect - damages and knocks away nearby enemies
     */
    private void createBlastEffect(Player player, LivingEntity target) {
        // Validate inputs
        if (player == null || !player.isOnline() || target == null || !target.isValid()) {
            return;
        }
        
        org.bukkit.Location blastLoc = target.getLocation();
        if (blastLoc == null || blastLoc.getWorld() == null) {
            return;
        }
        
        double blastRadius = 5.0; // 5 block radius
        
        // Visual and audio
        target.getWorld().spawnParticle(Particle.EXPLOSION, blastLoc, 5, 1.0, 1.0, 1.0, 0.1);
        target.getWorld().spawnParticle(Particle.CLOUD, blastLoc, 30, 1.0, 1.0, 1.0, 0.1);
        target.getWorld().playSound(blastLoc, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        
        // Get base damage
        double baseDamage = 0.0;
        TeamRun teamRun = plugin.getRunManager().getTeamRun(player);
        Run run = null;
        if (teamRun != null && teamRun.isActive()) {
            // Use player-specific weapon
            Weapon playerWeapon = teamRun.getWeapon(player);
            if (playerWeapon != null) {
                baseDamage = playerWeapon.getDamage();
            }
        } else {
            run = plugin.getRunManager().getRun(player);
            if (run != null && run.isActive()) {
                if (run.getWeapon() != null) {
                    baseDamage = run.getWeapon().getDamage();
                }
            }
        }
        
        // Get team members to exclude from damage
        Set<UUID> teamMemberIds = new HashSet<>();
        if (teamRun != null && teamRun.isActive()) {
            teamMemberIds.addAll(teamRun.getPlayerIds());
        }
        
        // Apply damage and knockback to nearby enemies
        for (org.bukkit.entity.Entity entity : blastLoc.getWorld().getNearbyEntities(blastLoc, blastRadius, blastRadius, blastRadius)) {
            if (!(entity instanceof LivingEntity) || !entity.isValid()) {
                continue;
            }
            
            LivingEntity living = (LivingEntity) entity;
            
            // Exclude team members
            if (entity instanceof org.bukkit.entity.Player) {
                org.bukkit.entity.Player targetPlayer = (org.bukkit.entity.Player) entity;
                if (teamMemberIds.contains(targetPlayer.getUniqueId())) {
                    continue; // Skip team members
                }
            }
            
            // Check if it's a boss - apply additional damage reduction for blast effects
            boolean isBoss = living.hasMetadata("roguecraft_boss") || living.hasMetadata("roguecraft_elite_boss");
            double effectiveBaseDamage = baseDamage;
            if (isBoss) {
                // Blast effects deal reduced damage to bosses (50% of normal damage)
                effectiveBaseDamage *= 0.5;
            }
            
            org.bukkit.Location entityLoc = living.getLocation();
            if (entityLoc == null || entityLoc.getWorld() == null) {
                continue;
            }
            
            double distance = entityLoc.distance(blastLoc);
            if (!Double.isFinite(distance) || distance < 0) {
                continue; // Skip if distance is invalid
            }
            
            double distanceMultiplier = Math.max(0.1, 1.0 - (distance / blastRadius));
            double finalDamage = calculateFinalDamage(player, effectiveBaseDamage * distanceMultiplier, living);
            living.damage(finalDamage, player);
            
            // Knockback - only if distance is greater than a small epsilon
            if (distance > 0.01) {
                org.bukkit.util.Vector knockback = entityLoc.toVector().subtract(blastLoc.toVector());
                double length = knockback.length();
                
                // Only normalize if length is valid and greater than epsilon
                if (Double.isFinite(length) && length > 0.01) {
                    knockback.normalize();
                    knockback.multiply(1.5); // Knockback strength
                    knockback.setY(0.5); // Add upward component
                    
                    // Validate vector components before setting velocity
                    if (Double.isFinite(knockback.getX()) && Double.isFinite(knockback.getY()) && Double.isFinite(knockback.getZ())) {
                        living.setVelocity(knockback);
                    }
                } else {
                    // Fallback: use a simple upward knockback if vectors are too close
                    living.setVelocity(new org.bukkit.util.Vector(0, 0.5, 0));
                }
            } else {
                // Entities at same location get simple upward knockback
                living.setVelocity(new org.bukkit.util.Vector(0, 0.5, 0));
            }
            
            living.getWorld().spawnParticle(Particle.EXPLOSION, entityLoc, 2, 0.3, 0.3, 0.3, 0.05);
        }
    }
    
    /**
     * Get gold multiplier from Golden Glove gacha item
     * Returns 1.0 + multiplier (e.g., 1.15 for 15% boost)
     * Ready for use when currency system is implemented
     */
    public double getGoldMultiplier(Player player) {
        double multiplier = 1.0;
        for (org.bukkit.metadata.MetadataValue meta : player.getMetadata("gacha_gold_multiplier")) {
            if (meta.value() instanceof Double) {
                multiplier += (Double) meta.value(); // Add percentage boost (0.15 = 15%)
            }
        }
        return multiplier;
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
    public double getModifiedAttackSpeed(Player player, Weapon weapon) {
        double baseSpeed = weapon.getAttackSpeed();
        if (hasWeaponMod(player, "Rapid Fire")) {
            double rapidFireValue = getWeaponModValue(player, "Rapid Fire");
            baseSpeed *= (1.0 + rapidFireValue * 0.3); // 30% per value point
        }
        
        // Apply Battery gacha item - attack speed boost
        for (org.bukkit.metadata.MetadataValue meta : player.getMetadata("gacha_attack_speed_battery")) {
            if (meta.value() instanceof Double) {
                double attackSpeedBoost = (Double) meta.value();
                baseSpeed *= (1.0 + attackSpeedBoost); // Add percentage boost
            }
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
            // Add spread to prevent fireballs from colliding with each other
            final Vector spreadDirection;
            if (projectileCount > 1) {
                // Add slight spread: 0.1 radians (~5.7 degrees) per projectile
                double spreadAngle = (i - (projectileCount - 1) / 2.0) * 0.1;
                // Rotate direction vector slightly
                org.bukkit.util.Vector right = direction.clone().crossProduct(new org.bukkit.util.Vector(0, 1, 0)).normalize();
                if (right.lengthSquared() > 0.01) {
                    spreadDirection = direction.clone().rotateAroundAxis(right, spreadAngle);
                } else {
                    spreadDirection = direction.clone();
                }
            } else {
                spreadDirection = direction.clone();
            }
            
            // Small delay between projectiles to prevent collision
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                Fireball fireball = player.getWorld().spawn(eyeLoc, Fireball.class);
                fireball.setDirection(spreadDirection);
                fireball.setYield(0); // No terrain damage
                fireball.setIsIncendiary(false);
                fireball.setShooter(player);
                
                // ProtocolLib: Start projectile trail
                if (plugin.getProtocolLibIntegration() != null && plugin.getProtocolLibIntegration().isEnabled()) {
                    plugin.getProtocolLibIntegration().startProjectileTrail(fireball, weapon.getType());
                }
                
                // Set initial velocity for faster projectiles (increased from default)
                // Validate spreadDirection before using it
                double length = spreadDirection.length();
                if (Double.isFinite(length) && length > 0.01) {
                    Vector velocity = spreadDirection.clone().normalize().multiply(1.2);
                    if (Double.isFinite(velocity.getX()) && Double.isFinite(velocity.getY()) && Double.isFinite(velocity.getZ())) {
                        fireball.setVelocity(velocity); // Faster default speed (1.2 instead of default ~0.5)
                    }
                }
            
                // Homing effect
                if (isHoming) {
                    BukkitTask homingTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                        if (!fireball.isValid() || !player.isOnline()) return;
                        LivingEntity nearest = findNearestEnemy(player, weapon.getRange() * 1.5);
                        if (nearest != null && nearest.isValid()) {
                            Vector newDir = nearest.getEyeLocation().subtract(fireball.getLocation()).toVector();
                            double dirLength = newDir.length();
                            if (Double.isFinite(dirLength) && dirLength > 0.01) {
                                newDir.normalize();
                                if (Double.isFinite(newDir.getX()) && Double.isFinite(newDir.getY()) && Double.isFinite(newDir.getZ())) {
                                    fireball.setDirection(newDir);
                                    fireball.setVelocity(newDir.multiply(1.2)); // Increased from 0.5 to 1.2 for faster homing
                                    fireball.getWorld().spawnParticle(Particle.ENCHANT, fireball.getLocation(), 1, 0.1, 0.1, 0.1, 0);
                                }
                            }
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
            }, i * 2L); // 2 tick delay between each projectile (0.1 seconds)
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
            
            // ProtocolLib: Start projectile trail
            if (plugin.getProtocolLibIntegration() != null && plugin.getProtocolLibIntegration().isEnabled()) {
                plugin.getProtocolLibIntegration().startProjectileTrail(arrow, weapon.getType());
            }
            
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
                    if (!arrow.isValid() || arrow.isDead() || !player.isOnline()) return;
                    LivingEntity nearest = findNearestEnemy(player, weapon.getRange() * 1.5);
                    if (nearest != null && nearest.isValid()) {
                        Vector newDir = nearest.getEyeLocation().subtract(arrow.getLocation()).toVector();
                        double dirLength = newDir.length();
                        if (Double.isFinite(dirLength) && dirLength > 0.01) {
                            newDir.normalize();
                            if (Double.isFinite(newDir.getX()) && Double.isFinite(newDir.getY()) && Double.isFinite(newDir.getZ())) {
                                arrow.setVelocity(newDir.multiply(2.0));
                                arrow.getWorld().spawnParticle(Particle.ENCHANT, arrow.getLocation(), 1, 0.1, 0.1, 0.1, 0);
                            }
                        }
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
                Vector finalDir = direction.clone().add(spread);
                double finalDirLength = finalDir.length();
                if (Double.isFinite(finalDirLength) && finalDirLength > 0.01) {
                    finalDir.normalize();
                    if (Double.isFinite(finalDir.getX()) && Double.isFinite(finalDir.getY()) && Double.isFinite(finalDir.getZ())) {
                        arrow.setVelocity(finalDir.multiply(2.0));
                    }
                }
            }
        }
        
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.3f, 1.2f);
    }
    
    private void strikeLightning(Player player, LivingEntity target, Weapon weapon) {
        Location targetLoc = target.getLocation();
        boolean isChainLightning = hasWeaponMod(player, "Chain Lightning");
        
        // Cap the effective range - use weapon range but don't let it exceed reasonable bounds
        // Lightning Strike: Reduced range cap to prevent excessive range
        double effectiveRange = Math.min(weapon.getRange(), 25.0); // Cap at 25 blocks max (reduced from 40)
        
        // Get team members to exclude from damage
        com.eldor.roguecraft.models.TeamRun teamRun = plugin.getRunManager().getTeamRun(player);
        Set<UUID> teamMemberIds = new HashSet<>();
        if (teamRun != null && teamRun.isActive()) {
            teamMemberIds.addAll(teamRun.getPlayerIds());
        }
        
        // Damage nearby enemies within effective range (not unlimited range)
        double totalDamageDealt = 0.0;
        double aoe = weapon.getAreaOfEffect();
        // Cap AOE to prevent it from becoming too large
        double effectiveAOE = Math.min(aoe, 8.0); // Cap AOE at 8 blocks
        
        // ProtocolLib: Show AOE damage indicator
        if (plugin.getProtocolLibIntegration() != null && plugin.getProtocolLibIntegration().isEnabled()) {
            plugin.getProtocolLibIntegration().showAOEDamageIndicator(targetLoc, effectiveAOE, weapon.getType());
        }
        
        // Visual lightning effect
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, targetLoc.clone().add(0, 1, 0), 50, 0.5, 2, 0.5, 0.1);
        player.getWorld().playSound(targetLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.0f);
        
        Set<LivingEntity> hitEntities = new HashSet<>();
        
        for (Entity entity : targetLoc.getWorld().getNearbyEntities(targetLoc, effectiveAOE, effectiveAOE, effectiveAOE)) {
            if (entity instanceof LivingEntity) {
                // Exclude team members
                if (entity instanceof Player) {
                    Player targetPlayer = (Player) entity;
                    if (teamMemberIds.contains(targetPlayer.getUniqueId())) {
                        continue; // Skip team members
                    }
                }
                
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
                        // Exclude team members
                        if (entity instanceof Player) {
                            Player targetPlayer = (Player) entity;
                            if (teamMemberIds.contains(targetPlayer.getUniqueId())) {
                                continue; // Skip team members
                            }
                        }
                        
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
        tnt.setYield((float) weapon.getAreaOfEffect()); // Restore yield for explosion effects
        tnt.setIsIncendiary(false);
        
        // Tag TNT with player UUID for XP attribution
        tnt.setMetadata("roguecraft_tnt_owner", new org.bukkit.metadata.FixedMetadataValue(plugin, player.getUniqueId().toString()));
        
        // Apply custom damage when TNT explodes (scheduled to run right before explosion)
        // We run this at 29 ticks to apply damage just before the natural explosion
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (tnt.isValid() && !tnt.isDead()) {
                Location explodeLoc = tnt.getLocation();
                
                // ProtocolLib: Show AOE damage indicator
                if (plugin.getProtocolLibIntegration() != null && plugin.getProtocolLibIntegration().isEnabled()) {
                    plugin.getProtocolLibIntegration().showAOEDamageIndicator(explodeLoc, weapon.getAreaOfEffect(), weapon.getType());
                }
                
                // Store actual explosion location for XP attribution
                tnt.setMetadata("roguecraft_tnt_explosion_loc", new org.bukkit.metadata.FixedMetadataValue(plugin, explodeLoc.clone()));
                
                // Get team members to exclude from damage
                com.eldor.roguecraft.models.TeamRun teamRun = plugin.getRunManager().getTeamRun(player);
                Set<UUID> teamMemberIds = new HashSet<>();
                if (teamRun != null && teamRun.isActive()) {
                    teamMemberIds.addAll(teamRun.getPlayerIds());
                }
                
                double totalDamageDealt = 0.0;
                for (Entity entity : explodeLoc.getWorld().getNearbyEntities(explodeLoc, weapon.getAreaOfEffect(), weapon.getAreaOfEffect(), weapon.getAreaOfEffect())) {
                    if (entity instanceof LivingEntity && entity != player) {
                        // Exclude team members from damage
                        if (entity instanceof Player) {
                            Player targetPlayer = (Player) entity;
                            if (teamMemberIds.contains(targetPlayer.getUniqueId())) {
                                continue; // Skip team members
                            }
                        }
                        
                        double distance = entity.getLocation().distance(explodeLoc);
                        double distanceMultiplier = Math.max(0.1, 1.0 - (distance / weapon.getAreaOfEffect()));
                        double baseDamage = weapon.getDamage() * distanceMultiplier;
                        double finalDamage = calculateFinalDamage(player, baseDamage, (LivingEntity) entity);
                        LivingEntity living = (LivingEntity) entity;
                        
                        // Tag entity with TNT owner for XP attribution if killed by explosion
                        living.setMetadata("roguecraft_tnt_damaged", new org.bukkit.metadata.FixedMetadataValue(plugin, player.getUniqueId().toString()));
                        living.setMetadata("roguecraft_tnt_damage_time", new org.bukkit.metadata.FixedMetadataValue(plugin, System.currentTimeMillis()));
                        
                        // Apply damage
                        living.damage(finalDamage, player);
                        applyWeaponModEffects(player, living);
                        totalDamageDealt += finalDamage;
                    }
                }
                if (totalDamageDealt > 0) {
                    applyLifesteal(player, totalDamageDealt);
                }
            }
        }, 29L); // Run 1 tick before explosion to apply damage first
        
        player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 0.5f, 1.0f);
    }
    
    private void throwPotion(Player player, LivingEntity target, Weapon weapon) {
        if (target == null || !target.isValid()) return;
        Vector direction = target.getEyeLocation().subtract(player.getEyeLocation()).toVector();
        double dirLength = direction.length();
        if (!Double.isFinite(dirLength) || dirLength < 0.01) return;
        direction.normalize();
        if (!Double.isFinite(direction.getX()) || !Double.isFinite(direction.getY()) || !Double.isFinite(direction.getZ())) return;
        
        // Launch potion from player with increased velocity for better range
        ThrownPotion potion = player.launchProjectile(ThrownPotion.class);
        Vector velocity = direction.multiply(1.2);
        if (Double.isFinite(velocity.getX()) && Double.isFinite(velocity.getY()) && Double.isFinite(velocity.getZ())) {
            potion.setVelocity(velocity); // Increased from 0.75 to 1.2 for better range
        }
        
        // ProtocolLib: Start projectile trail
        if (plugin.getProtocolLibIntegration() != null && plugin.getProtocolLibIntegration().isEnabled()) {
            plugin.getProtocolLibIntegration().startProjectileTrail(potion, weapon.getType());
        }
        
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
        if (target == null || !target.isValid()) return;
        Location eyeLoc = player.getEyeLocation();
        Vector direction = target.getEyeLocation().subtract(eyeLoc).toVector();
        double dirLength = direction.length();
        if (!Double.isFinite(dirLength) || dirLength < 0.01) return;
        direction.normalize();
        if (!Double.isFinite(direction.getX()) || !Double.isFinite(direction.getY()) || !Double.isFinite(direction.getZ())) return;
        
        // Use snowball as projectile
        Snowball snowball = player.getWorld().spawn(eyeLoc, Snowball.class);
        Vector velocity = direction.multiply(2.0);
        if (Double.isFinite(velocity.getX()) && Double.isFinite(velocity.getY()) && Double.isFinite(velocity.getZ())) {
            snowball.setVelocity(velocity);
        }
        snowball.setShooter(player);
        
        // ProtocolLib: Start projectile trail (replaces the manual particle task)
        if (plugin.getProtocolLibIntegration() != null && plugin.getProtocolLibIntegration().isEnabled()) {
            plugin.getProtocolLibIntegration().startProjectileTrail(snowball, weapon.getType());
        }
        
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
                        // Apply full damage at center, with reduced falloff (minimum 50% damage)
                        double distance = entity.getLocation().distance(loc);
                        double distanceMultiplier = Math.max(0.5, 1.0 - (distance / (weaponAoe * 2.0)));
                        double baseDamage = weaponDamage * distanceMultiplier;
                        double finalDamage = calculateFinalDamage(player, baseDamage, living);
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
        
        // Increased AOE for magic missile (from 0.5 to 2.0)
        double missileAOE = Math.max(2.0, weapon.getAreaOfEffect());
        
        for (int i = 0; i < weapon.getProjectileCount(); i++) {
            // Use small fireball for magic missile
            SmallFireball missile = player.getWorld().spawn(eyeLoc, SmallFireball.class);
            missile.setDirection(direction);
            missile.setYield(0);
            missile.setIsIncendiary(false);
            missile.setShooter(player);
            
            // ProtocolLib: Start projectile trail
            if (plugin.getProtocolLibIntegration() != null && plugin.getProtocolLibIntegration().isEnabled()) {
                plugin.getProtocolLibIntegration().startProjectileTrail(missile, weapon.getType());
            }
            
            // Store metadata for hit detection
            missile.setMetadata("magic_missile_weapon", new org.bukkit.metadata.FixedMetadataValue(plugin, player.getUniqueId().toString()));
            missile.setMetadata("magic_missile_damage", new org.bukkit.metadata.FixedMetadataValue(plugin, weapon.getDamage()));
            missile.setMetadata("magic_missile_aoe", new org.bukkit.metadata.FixedMetadataValue(plugin, missileAOE));
            
            // Homing effect - update direction periodically
            BukkitTask homingTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!missile.isValid()) {
                    return;
                }
                
                // Find nearest enemy for homing
                LivingEntity nearestTarget = target;
                if (!target.isValid() || target.isDead()) {
                    nearestTarget = findNearestEnemy(player, weapon.getRange() * 1.5);
                }
                
                if (nearestTarget != null && nearestTarget.isValid() && !nearestTarget.isDead()) {
                    Vector newDirection = nearestTarget.getEyeLocation().subtract(missile.getLocation()).toVector();
                    double dirLength = newDirection.length();
                    if (Double.isFinite(dirLength) && dirLength > 0.01) {
                        newDirection.normalize();
                        if (Double.isFinite(newDirection.getX()) && Double.isFinite(newDirection.getY()) && Double.isFinite(newDirection.getZ())) {
                            missile.setDirection(newDirection);
                            missile.setVelocity(newDirection.multiply(1.0)); // Increased speed from 0.5 to 1.0
                        }
                    }
                }
                
                // Particle trail (reduced from 3 to 1 particle, every 3 ticks instead of every tick)
                if (missile.getTicksLived() % 3 == 0) {
                    missile.getWorld().spawnParticle(Particle.ENCHANT, missile.getLocation(), 1, 0.1, 0.1, 0.1, 0);
                }
            }, 0L, 1L); // Update every tick for better homing
            
            // Check for hits every tick (more responsive than waiting 3 seconds)
            final BukkitTask[] hitCheckTaskRef = new BukkitTask[1];
            BukkitTask hitCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!missile.isValid() || !missile.hasMetadata("magic_missile_weapon")) {
                    return;
                }
                
                Location loc = missile.getLocation();
                double missileAOEValue = missile.getMetadata("magic_missile_aoe").get(0).asDouble();
                double missileDamage = missile.getMetadata("magic_missile_damage").get(0).asDouble();
                
                // Check for nearby enemies
                for (Entity entity : loc.getWorld().getNearbyEntities(loc, missileAOEValue, missileAOEValue, missileAOEValue)) {
                    if (entity instanceof LivingEntity && !(entity instanceof Player) && entity != player) {
                        LivingEntity living = (LivingEntity) entity;
                        
                        // Hit detected - deal damage immediately
                        double finalDamage = calculateFinalDamage(player, missileDamage, living);
                        living.damage(finalDamage, player);
                        applyWeaponModEffects(player, living);
                        
                        // Visual effect on hit (reduced from 20 to 5 particles)
                        loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 5, 0.5, 0.5, 0.5, 0);
                        loc.getWorld().playSound(loc, Sound.ENTITY_EVOKER_CAST_SPELL, 0.7f, 1.5f);
                        
                        // Remove missile after hit
                        homingTask.cancel();
                        if (hitCheckTaskRef[0] != null) {
                            hitCheckTaskRef[0].cancel();
                        }
                        missile.removeMetadata("magic_missile_weapon", plugin);
                        missile.removeMetadata("magic_missile_damage", plugin);
                        missile.removeMetadata("magic_missile_aoe", plugin);
                        missile.remove();
                        return;
                    }
                }
            }, 1L, 1L); // Check every tick
            hitCheckTaskRef[0] = hitCheckTask;
            
            // Fallback: remove missile after 3 seconds if it didn't hit anything
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                homingTask.cancel();
                if (hitCheckTaskRef[0] != null) {
                    hitCheckTaskRef[0].cancel();
                }
                if (missile.isValid() && missile.hasMetadata("magic_missile_weapon")) {
                    Location loc = missile.getLocation();
                    loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 20, 0.5, 0.5, 0.5, 0);
                    
                    // Apply damage at final location as fallback
                    double missileAOEValue = missile.getMetadata("magic_missile_aoe").get(0).asDouble();
                    double missileDamage = missile.getMetadata("magic_missile_damage").get(0).asDouble();
                    double totalDamageDealt = 0.0;
                    
                    for (Entity entity : loc.getWorld().getNearbyEntities(loc, missileAOEValue, missileAOEValue, missileAOEValue)) {
                        if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                            LivingEntity living = (LivingEntity) entity;
                            double finalDamage = calculateFinalDamage(player, missileDamage, living);
                            living.damage(finalDamage, player);
                            applyWeaponModEffects(player, living);
                            totalDamageDealt += finalDamage;
                        }
                    }
                    if (totalDamageDealt > 0) {
                        applyLifesteal(player, totalDamageDealt);
                    }
                    missile.removeMetadata("magic_missile_weapon", plugin);
                    missile.removeMetadata("magic_missile_damage", plugin);
                    missile.removeMetadata("magic_missile_aoe", plugin);
                    missile.remove();
                }
            }, 60L);
        }
        
        player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.5f, 1.5f);
    }
}


