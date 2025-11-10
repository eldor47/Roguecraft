package com.eldor.roguecraft.listeners;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.Run;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class GameListener implements Listener {
    private final RoguecraftPlugin plugin;
    private final Map<UUID, Integer> accumulatedGold; // Track accumulated gold per player
    private org.bukkit.scheduler.BukkitTask goldDisplayTask; // Task to periodically display accumulated gold

    public GameListener(RoguecraftPlugin plugin) {
        this.plugin = plugin;
        this.accumulatedGold = new HashMap<>();
        startGoldDisplayTask();
    }

    private static final Random RANDOM = new Random();
    
    /**
     * Start a task that periodically displays accumulated gold and resets it
     */
    private void startGoldDisplayTask() {
        // Run every 1 second (20 ticks) to display accumulated gold
        goldDisplayTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, Integer> entry : new HashMap<>(accumulatedGold).entrySet()) {
                UUID playerId = entry.getKey();
                int gold = entry.getValue();
                
                if (gold > 0) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        // Display the accumulated gold
                        showGoldTextDisplayNow(player, gold);
                        // Reset accumulated gold
                        accumulatedGold.put(playerId, 0);
                    } else {
                        // Player offline, remove from map
                        accumulatedGold.remove(playerId);
                    }
                }
            }
        }, 20L, 20L); // Every 1 second
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        EntityType type = entity.getType();
        
        // Only handle mobs in arenas (not players)
        if (entity instanceof Player) {
            return;
        }
        
        // Clear custom name and glowing effect IMMEDIATELY at LOWEST priority
        // This prevents death message logs for named entities
        // This is needed because we add custom names to all mobs for health display
        boolean isElite = false;
        boolean isLegendary = false;
        try {
            // Check if elite/legendary before clearing (for drop bonuses and XP)
            isElite = entity.isGlowing() || (entity.getCustomName() != null && entity.getCustomName().contains("ELITE"));
            isLegendary = entity.hasMetadata("is_legendary");
            
            // Clear custom name and glowing for all mobs IMMEDIATELY
            if (entity.getCustomName() != null) {
                entity.setCustomName(null);
                entity.setCustomNameVisible(false);
            }
            if (entity.isGlowing()) {
                entity.setGlowing(false);
            }
            // Remove from legendary team if applicable
            if (isLegendary) {
                try {
                    org.bukkit.scoreboard.Scoreboard scoreboard = org.bukkit.Bukkit.getScoreboardManager().getMainScoreboard();
                    org.bukkit.scoreboard.Team team = scoreboard.getTeam("roguecraft_legendary_gold");
                    if (team != null && team.hasEntry(entity.getUniqueId().toString())) {
                        team.removeEntry(entity.getUniqueId().toString());
                    }
                } catch (Exception e) {
                    // Silently ignore - entity might already be removed
                }
            }
        } catch (Exception e) {
            // Silently ignore - entity might already be dead/removed
        }
        
        // Check if killed by a player
        Player killer = entity.getKiller();
        
        // If no direct killer, check if killed by TNT explosion from a player's weapon
        // This is the PRIMARY method - check metadata first (most reliable)
        if (killer == null && entity.hasMetadata("roguecraft_tnt_damaged")) {
            String playerUuidStr = entity.getMetadata("roguecraft_tnt_damaged").get(0).asString();
            Player tntOwner = Bukkit.getPlayer(java.util.UUID.fromString(playerUuidStr));
            if (tntOwner != null && tntOwner.isOnline()) {
                Location deathLoc = entity.getLocation();
                
                // Check if damage was recent (within last 5 seconds) to avoid stale metadata
                long damageTime = 0;
                if (entity.hasMetadata("roguecraft_tnt_damage_time")) {
                    damageTime = entity.getMetadata("roguecraft_tnt_damage_time").get(0).asLong();
                }
                long timeSinceDamage = System.currentTimeMillis() - damageTime;
                
                // Only attribute if damage was recent (within 5 seconds)
                if (timeSinceDamage < 5000 || damageTime == 0) {
                    // Verify entity was within explosion radius
                    if (entity.hasMetadata("roguecraft_tnt_explosion_loc")) {
                        Location explosionLoc = (Location) entity.getMetadata("roguecraft_tnt_explosion_loc").get(0).value();
                        if (explosionLoc != null && deathLoc.distanceSquared(explosionLoc) <= 400) { // 20 block radius
                            killer = tntOwner;
                        }
                    } else {
                        // No explosion loc metadata, but has tnt_damaged - assume valid (within reasonable range)
                        killer = tntOwner;
                    }
                }
            }
        }
        
        // Fallback: Check for nearby TNT entities (in case metadata wasn't set)
        if (killer == null) {
            Location deathLoc = entity.getLocation();
            for (Entity nearbyEntity : deathLoc.getWorld().getNearbyEntities(deathLoc, 20, 20, 20)) {
                if (nearbyEntity instanceof org.bukkit.entity.TNTPrimed) {
                    org.bukkit.entity.TNTPrimed tnt = (org.bukkit.entity.TNTPrimed) nearbyEntity;
                    if (tnt.hasMetadata("roguecraft_tnt_owner")) {
                        String playerUuidStr = tnt.getMetadata("roguecraft_tnt_owner").get(0).asString();
                        Player tntOwner = Bukkit.getPlayer(java.util.UUID.fromString(playerUuidStr));
                        if (tntOwner != null && tntOwner.isOnline()) {
                            // Entity is near TNT, attribute kill
                            killer = tntOwner;
                            break;
                        }
                    }
                }
            }
        }
        
        if (killer == null) {
            // Still handle drops if no killer (but disable normal drops)
            if (plugin.getConfigManager().getMainConfig().getBoolean("drops.disable-normal-drops", true)) {
                event.getDrops().clear();
                event.setDroppedExp(0);
            }
            return;
        }
        
        // Check if player is in a run
        com.eldor.roguecraft.models.TeamRun teamRun = plugin.getRunManager().getTeamRun(killer);
        Run run = null;
        boolean inRun = false;
        
        if (teamRun != null && teamRun.isActive()) {
            inRun = true;
        } else {
            run = plugin.getRunManager().getRun(killer);
            if (run != null && run.isActive()) {
                inRun = true;
            }
        }
        
        // Disable normal drops if configured
        if (plugin.getConfigManager().getMainConfig().getBoolean("drops.disable-normal-drops", true)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
        
        // Only drop custom items if player is in a run
        if (!inRun) {
            return;
        }
        
        // Check if mob was nuked (no XP for nuked mobs)
        boolean wasNuked = entity.hasMetadata("roguecraft_nuked");
        
        // Check if it's the Wither boss - trigger large radius magnet instead of XP
        boolean isBoss = entity.hasMetadata("roguecraft_boss") || entity.hasMetadata("roguecraft_elite_boss");
        if (isBoss && !wasNuked && killer != null) {
            // Trigger magnet effect with very large radius for all players in the run
            if (teamRun != null && teamRun.isActive()) {
                for (Player p : teamRun.getPlayers()) {
                    if (p != null && p.isOnline()) {
                        applyMagnetLargeRadius(p, teamRun, null, 50.0); // 50 block radius
                    }
                }
            } else if (run != null && run.isActive()) {
                applyMagnetLargeRadius(killer, null, run, 50.0); // 50 block radius
            }
        }
        
        // AUTO XP SYSTEM DISABLED - Players must collect XP tokens to level up
        /*
        // Handle XP and experience (skip if nuked)
        if (!wasNuked && teamRun != null && teamRun.isActive()) {
            // Award experience to team (shared XP with multiplier)
            int baseXp = calculateExperience(type, teamRun.getWave(), teamRun.getStat("difficulty"));
            
            // Check if it's a boss (Wither) - apply boss XP multiplier
            boolean isBoss = entity.hasMetadata("roguecraft_boss") || entity.hasMetadata("roguecraft_elite_boss");
            if (isBoss) {
                // Boss gets 2x XP multiplier (same as elite, but bosses are special)
                baseXp = (int) (baseXp * 2.0);
            } else if (isLegendary) {
                // Legendary mobs get legendary XP multiplier from config
                double legendaryXpMultiplier = plugin.getConfigManager().getBalanceConfig().getDouble("legendary.xp-multiplier", 3.0);
                baseXp = (int) (baseXp * legendaryXpMultiplier);
            } else if (isElite) {
                // Elite mobs get XP multiplier from config
                double eliteXpMultiplier = plugin.getConfigManager().getBalanceConfig().getDouble("elites.xp-multiplier", 2.0);
                baseXp = (int) (baseXp * eliteXpMultiplier);
            }
            
            double multiplier = teamRun.getStat("xp_multiplier");
            int xp = (int) (baseXp * multiplier);
            teamRun.addExperience(xp);

            // Update XP bar for all team members instead of messages
            for (Player player : teamRun.getPlayers()) {
                if (player != null && player.isOnline()) {
                    com.eldor.roguecraft.util.XPBar.updateXPBarWithGold(
                        player,
                        teamRun.getExperience(),
                        teamRun.getExperienceToNextLevel(),
                        teamRun.getLevel(),
                        teamRun.getWave(),
                        teamRun.getCurrentGold()
                    );
                }
            }
        } else if (!wasNuked && run != null && run.isActive()) {
            // Award experience based on entity type with multiplier
            int baseXp = calculateExperience(type, run.getWave(), run.getStat("difficulty"));
            
            // Check if it's a boss (Wither) - apply boss XP multiplier
            boolean isBoss = entity.hasMetadata("roguecraft_boss") || entity.hasMetadata("roguecraft_elite_boss");
            if (isBoss) {
                // Boss gets 2x XP multiplier (same as elite, but bosses are special)
                baseXp = (int) (baseXp * 2.0);
            } else if (isLegendary) {
                // Legendary mobs get legendary XP multiplier from config
                double legendaryXpMultiplier = plugin.getConfigManager().getBalanceConfig().getDouble("legendary.xp-multiplier", 3.0);
                baseXp = (int) (baseXp * legendaryXpMultiplier);
            } else if (isElite) {
                // Elite mobs get XP multiplier from config
                double eliteXpMultiplier = plugin.getConfigManager().getBalanceConfig().getDouble("elites.xp-multiplier", 2.0);
                baseXp = (int) (baseXp * eliteXpMultiplier);
            }
            
            double multiplier = run.getStat("xp_multiplier");
            int xp = (int) (baseXp * multiplier);
            run.addExperience(xp);

            // Update XP bar instead of message
            com.eldor.roguecraft.util.XPBar.updateXPBarWithGold(
                killer,
                run.getExperience(),
                run.getExperienceToNextLevel(),
                run.getLevel(),
                run.getWave(),
                run.getCurrentGold()
            );
        }
        */
        
        // Award gold for killing mobs
        if (!wasNuked) {
            int baseGold = 5; // Base gold per mob
            int goldReward = baseGold;
            
            // Apply multipliers for elite/legendary mobs
            if (isLegendary) {
                goldReward = baseGold * 10; // Legendary mobs give 10x gold
            } else if (isElite) {
                goldReward = baseGold * 3; // Elite mobs give 3x gold
            }
            
            // Apply 2x gold power-up multiplier if active
            double goldMultiplier = 1.0;
            if (killer.hasMetadata("roguecraft_2x_gold")) {
                goldMultiplier = 2.0;
            }
            
            // Apply Golden Glove gacha item multiplier
            double gachaGoldMultiplier = plugin.getWeaponManager().getGoldMultiplier(killer);
            goldMultiplier *= gachaGoldMultiplier;
            
            goldReward = (int) (goldReward * goldMultiplier);
            
            // Award gold
            if (teamRun != null && teamRun.isActive()) {
                teamRun.addGold(goldReward);
                // Accumulate gold for all team members (will be displayed periodically)
                for (Player p : teamRun.getPlayers()) {
                    if (p != null && p.isOnline()) {
                        accumulateGold(p.getUniqueId(), goldReward);
                        // Update boss bar with new gold amount
                        updateBossBarWithGold(p, teamRun);
                    }
                }
            } else if (run != null && run.isActive()) {
                run.addGold(goldReward);
                accumulateGold(killer.getUniqueId(), goldReward);
                // Update boss bar with new gold amount
                updateBossBarWithGold(killer, run);
            }
        }
        
        // Check for rare power-up shrine buff (Treasure Hunter)
        if (killer.hasMetadata("shrine_rare_powerup")) {
            // Grant a rare power-up on kill
            grantRarePowerUp(killer, teamRun, run);
            // Remove metadata (one-time use)
            killer.removeMetadata("shrine_rare_powerup", plugin);
        }
        
        // Drop custom items (pass run/teamRun for drop_rate stat)
        if (teamRun != null && teamRun.isActive()) {
            dropCustomItems(entity, isElite, isLegendary, teamRun);
        } else if (run != null && run.isActive()) {
            dropCustomItems(entity, isElite, isLegendary, run);
        } else {
            dropCustomItems(entity, isElite, isLegendary, null);
        }
        
        // Legendary mobs have a chance to spawn a gacha chest
        if (isLegendary && (teamRun != null || run != null)) {
            spawnLegendaryChest(entity.getLocation(), teamRun != null ? teamRun : run);
        }
    }
    
    /**
     * Spawn a gacha chest when a legendary mob dies
     * 5% chance to spawn a chest (very rare drops)
     */
    private void spawnLegendaryChest(org.bukkit.Location location, Object run) {
        // 5% chance to spawn a chest (very rare drops)
        if (RANDOM.nextDouble() < 0.05) {
            // Get team ID for chest tracking
            java.util.UUID teamId = null;
            if (run instanceof com.eldor.roguecraft.models.TeamRun) {
                com.eldor.roguecraft.models.TeamRun tr = (com.eldor.roguecraft.models.TeamRun) run;
                if (!tr.getPlayers().isEmpty()) {
                    teamId = tr.getPlayers().get(0).getUniqueId();
                }
            } else if (run instanceof com.eldor.roguecraft.models.Run) {
                teamId = ((com.eldor.roguecraft.models.Run) run).getPlayerId();
            }
            
            if (teamId != null) {
                // Spawn chest at legendary mob death location (costs gold, scales exponentially)
                com.eldor.roguecraft.models.GachaChest chest = new com.eldor.roguecraft.models.GachaChest(location, false);
                chest.spawn();
                
                // Add to chest manager's tracking
                plugin.getChestManager().addChestForRun(teamId, chest);
                
                // Visual feedback
                location.getWorld().spawnParticle(
                    org.bukkit.Particle.TOTEM_OF_UNDYING,
                    location.add(0, 1, 0),
                    50,
                    0.5, 0.5, 0.5,
                    0.1
                );
                location.getWorld().playSound(
                    location,
                    org.bukkit.Sound.ENTITY_PLAYER_LEVELUP,
                    1.0f,
                    1.2f
                );
                
                // Notify players
                if (run instanceof com.eldor.roguecraft.models.TeamRun) {
                    com.eldor.roguecraft.models.TeamRun tr = (com.eldor.roguecraft.models.TeamRun) run;
                    for (Player p : tr.getPlayers()) {
                        if (p != null && p.isOnline()) {
                            p.sendMessage(org.bukkit.ChatColor.GOLD + "§l✨ LEGENDARY CHEST SPAWNED! ✨");
                        }
                    }
                } else if (run instanceof com.eldor.roguecraft.models.Run) {
                    Player p = ((com.eldor.roguecraft.models.Run) run).getPlayer();
                    if (p != null && p.isOnline()) {
                        p.sendMessage(org.bukkit.ChatColor.GOLD + "§l✨ LEGENDARY CHEST SPAWNED! ✨");
                    }
                }
            }
        }
    }
    
    /**
     * Track TNT explosions to attribute kills to players
     * This runs BEFORE the explosion to tag entities
     * Priority HIGH ensures we tag entities before they take damage
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        // Prevent Wither boss from breaking blocks
        Entity entity = event.getEntity();
        if (entity != null) {
            // Check if it's the Wither boss itself
            if (entity.hasMetadata("roguecraft_boss") || entity.hasMetadata("roguecraft_elite_boss")) {
                // Clear the block list to prevent block breaking
                event.blockList().clear();
                return;
            }
            
            // Check if it's a WitherSkull from the boss Wither
            if (entity instanceof org.bukkit.entity.WitherSkull) {
                org.bukkit.entity.WitherSkull skull = (org.bukkit.entity.WitherSkull) entity;
                // Check if the shooter is a Wither with boss metadata
                if (skull.getShooter() instanceof org.bukkit.entity.Wither) {
                    org.bukkit.entity.Wither shooter = (org.bukkit.entity.Wither) skull.getShooter();
                    if (shooter.hasMetadata("roguecraft_boss") || shooter.hasMetadata("roguecraft_elite_boss")) {
                        // Clear the block list to prevent block breaking
                        event.blockList().clear();
                        return;
                    }
                }
            }
        }
        if (event.getEntity() instanceof org.bukkit.entity.TNTPrimed) {
            org.bukkit.entity.TNTPrimed tnt = (org.bukkit.entity.TNTPrimed) event.getEntity();
            
            if (tnt.hasMetadata("roguecraft_tnt_owner")) {
                String playerUuidStr = tnt.getMetadata("roguecraft_tnt_owner").get(0).asString();
                Player tntOwner = Bukkit.getPlayer(java.util.UUID.fromString(playerUuidStr));
                
                if (tntOwner != null && tntOwner.isOnline()) {
                    Location explosionLoc = tnt.getLocation();
                    
                    // Tag all nearby entities that will be damaged by this explosion
                    // Use a larger radius to catch all potential victims (TNT yield can be up to ~8 blocks)
                    double radius = Math.max(15.0, tnt.getYield() * 3); // TNT yield * 3 for safety
                    for (Entity nearbyEntity : explosionLoc.getWorld().getNearbyEntities(explosionLoc, radius, radius, radius)) {
                        // Exclude the player who spawned the TNT
                        if (nearbyEntity instanceof LivingEntity && nearbyEntity != tntOwner) {
                            LivingEntity living = (LivingEntity) nearbyEntity;
                            // Tag entity for XP attribution (use setMetadata which will overwrite if exists)
                            living.setMetadata("roguecraft_tnt_damaged", new org.bukkit.metadata.FixedMetadataValue(plugin, tntOwner.getUniqueId().toString()));
                            // Also store explosion location for distance check
                            living.setMetadata("roguecraft_tnt_explosion_loc", new org.bukkit.metadata.FixedMetadataValue(plugin, explosionLoc.clone()));
                            // Store timestamp for cleanup
                            living.setMetadata("roguecraft_tnt_damage_time", new org.bukkit.metadata.FixedMetadataValue(plugin, System.currentTimeMillis()));
                        }
                    }
                }
            }
        }
    }
    
    private void dropCustomItems(LivingEntity entity, boolean isElite, boolean isLegendary, Object run) {
        org.bukkit.Location loc = entity.getLocation();
        
        // Get drop_rate stat multiplier
        double dropRate = 1.0;
        if (run instanceof com.eldor.roguecraft.models.TeamRun) {
            dropRate = ((com.eldor.roguecraft.models.TeamRun) run).getStat("drop_rate");
        } else if (run instanceof com.eldor.roguecraft.models.Run) {
            dropRate = ((com.eldor.roguecraft.models.Run) run).getStat("drop_rate");
        }
        
        // XP Token drop - ALWAYS drops from every mob (with reduced XP amount)
        // Elites and Legendaries drop larger XP tokens
        if (plugin.getConfigManager().getMainConfig().getBoolean("drops.xp-token.enabled", true)) {
            int xpMultiplier = 1;
            if (isLegendary) {
                xpMultiplier = 5; // Legendaries drop 5x XP tokens
            } else if (isElite) {
                xpMultiplier = 3; // Elites drop 3x XP tokens
            }
            
            ItemStack xpToken = createXPToken(xpMultiplier);
            Item item = loc.getWorld().dropItem(loc, xpToken);
            item.setVelocity(new Vector(
                (RANDOM.nextDouble() - 0.5) * 0.3,
                0.2 + RANDOM.nextDouble() * 0.2,
                (RANDOM.nextDouble() - 0.5) * 0.3
            ));
            // Store multiplier in custom name for pickup handler
            if (xpMultiplier > 1) {
                item.setCustomName("XP_TOKEN_" + xpMultiplier);
            } else {
                item.setCustomName("XP_TOKEN");
            }
            item.setCustomNameVisible(false);
            // Minimal visual effect on drop (since it's always dropping)
            if (RANDOM.nextDouble() < 0.1) { // Only show particles 10% of the time to reduce lag
                loc.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, loc, 2, 0.2, 0.3, 0.2, 0.05);
            }
        }
        
        // Heart drop
        if (plugin.getConfigManager().getMainConfig().getBoolean("drops.heart.enabled", true)) {
            double heartChance = plugin.getConfigManager().getMainConfig().getDouble("drops.heart.base-chance", 0.03);
            if (isElite) {
                double eliteBonus = plugin.getConfigManager().getMainConfig().getDouble("drops.heart.elite-bonus", 0.10);
                heartChance += eliteBonus;
            }
            // Apply drop_rate multiplier
            heartChance *= dropRate;
            heartChance = Math.min(1.0, heartChance); // Cap at 100%
            
            if (RANDOM.nextDouble() < heartChance) {
                ItemStack heart = createHeart();
                Item item = loc.getWorld().dropItem(loc, heart);
                item.setVelocity(new Vector(
                    (RANDOM.nextDouble() - 0.5) * 0.3,
                    0.2 + RANDOM.nextDouble() * 0.2,
                    (RANDOM.nextDouble() - 0.5) * 0.3
                ));
                item.setCustomName("HEART_ITEM");
                item.setCustomNameVisible(false);
                // Visual effect on drop
                loc.getWorld().spawnParticle(org.bukkit.Particle.HEART, loc, 3, 0.3, 0.5, 0.3, 0);
                loc.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.3f, 1.2f);
            }
        }
        
        // Unique Power-Up drops (rare drops - Movement Speed Boost, Time Freeze, Nuclear Strike, Magnet, Double XP)
        if (plugin.getConfigManager().getMainConfig().getBoolean("drops.powerup.enabled", true)) {
            // Base chance from config (2% default, scales with drop_rate stat)
            double powerupChance = plugin.getConfigManager().getMainConfig().getDouble("drops.powerup.base-chance", 0.02);
            // Apply drop_rate multiplier (no elite bonus, no other scaling)
            powerupChance *= dropRate;
            powerupChance = Math.min(1.0, powerupChance); // Cap at 100%
            
            // Check if run is valid (either Run or TeamRun)
            boolean isValidRun = run instanceof com.eldor.roguecraft.models.Run || run instanceof com.eldor.roguecraft.models.TeamRun;
            
            if (RANDOM.nextDouble() < powerupChance && isValidRun) {
                // Choose between unique power-ups (Magnet has higher chance: 30%, others 17.5% each)
                double powerUpRoll = RANDOM.nextDouble();
                String powerUpType;
                if (powerUpRoll < 0.25) {
                    powerUpType = "MAGNET"; // 25% chance for magnet
                } else if (powerUpRoll < 0.40) {
                    powerUpType = "SPEED_BOOST"; // 15% chance
                } else if (powerUpRoll < 0.55) {
                    powerUpType = "TIME_FREEZE"; // 15% chance
                } else if (powerUpRoll < 0.70) {
                    powerUpType = "NUCLEAR_STRIKE"; // 15% chance
                } else if (powerUpRoll < 0.85) {
                    powerUpType = "DOUBLE_XP"; // 15% chance
                } else {
                    powerUpType = "DOUBLE_GOLD"; // 15% chance
                }
                
                ItemStack powerupItem = createUniquePowerUpItem(powerUpType);
                // Prevent stacking by setting amount to 1 and making it unstackable
                powerupItem.setAmount(1);
                // Offset drop location slightly to prevent merging with XP tokens
                Location dropLoc = loc.clone().add(
                    (RANDOM.nextDouble() - 0.5) * 0.5,
                    0.2,
                    (RANDOM.nextDouble() - 0.5) * 0.5
                );
                Item item = dropLoc.getWorld().dropItem(dropLoc, powerupItem);
                // Prevent item from merging with other items
                item.setPickupDelay(0);
                // Add unique metadata to prevent merging
                item.setMetadata("roguecraft_powerup_" + System.currentTimeMillis() + "_" + RANDOM.nextInt(10000), 
                    new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                item.setVelocity(new Vector(
                    (RANDOM.nextDouble() - 0.5) * 0.3,
                    0.3 + RANDOM.nextDouble() * 0.3,
                    (RANDOM.nextDouble() - 0.5) * 0.3
                ));
                item.setCustomName("POWERUP_ITEM_" + powerUpType);
                item.setCustomNameVisible(false);
                
                // Unique visual and sound effects for power-up drop
                if (powerUpType.equals("SPEED_BOOST")) {
                    loc.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, loc, 20, 0.5, 0.5, 0.5, 0.3);
                    loc.getWorld().spawnParticle(org.bukkit.Particle.CRIT, loc, 15, 0.5, 0.5, 0.5, 0.2);
                    loc.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_HORSE_GALLOP, 0.5f, 1.5f);
                } else if (powerUpType.equals("TIME_FREEZE")) {
                    loc.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, loc, 15, 0.5, 0.5, 0.5, 0.2);
                    loc.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, loc, 20, 0.5, 0.5, 0.5, 0.3);
                    loc.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    loc.getWorld().playSound(loc, org.bukkit.Sound.ITEM_TOTEM_USE, 0.5f, 1.2f);
                } else if (powerUpType.equals("NUCLEAR_STRIKE")) {
                    loc.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, loc, 5, 0.5, 0.5, 0.5, 0.1);
                    loc.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, loc, 20, 0.5, 0.5, 0.5, 0.2);
                    loc.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.0f);
                } else if (powerUpType.equals("MAGNET")) {
                    loc.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, loc, 20, 0.5, 0.5, 0.5, 0.3);
                    loc.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, loc, 15, 0.5, 0.5, 0.5, 0.2);
                    loc.getWorld().playSound(loc, org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.2f);
                } else if (powerUpType.equals("DOUBLE_XP")) {
                    loc.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, loc, 25, 0.5, 0.5, 0.5, 0.3);
                    loc.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, loc, 20, 0.5, 0.5, 0.5, 0.2);
                    loc.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                } else { // DOUBLE_GOLD
                    loc.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, loc, 25, 0.5, 0.5, 0.5, 0.3);
                    loc.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, loc, 20, 0.5, 0.5, 0.5, 0.2);
                    loc.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                }
            }
        }
    }
    
    private ItemStack createXPToken(int multiplier) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        
        // Base XP amount per token (increased for faster leveling)
        int baseXpAmount = plugin.getConfigManager().getMainConfig().getInt("drops.xp-token.xp-amount", 7);
        int xpAmount = baseXpAmount * multiplier;
        
        // Different display based on multiplier
        if (multiplier > 1) {
            if (multiplier >= 5) {
                meta.setDisplayName(ChatColor.GOLD + "✨✨✨ Large XP Token ✨✨✨");
            } else {
                meta.setDisplayName(ChatColor.YELLOW + "✨✨ Medium XP Token ✨✨");
            }
        } else {
            meta.setDisplayName(ChatColor.YELLOW + "✨ XP Token");
        }
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Pick up to gain bonus XP!");
        lore.add(ChatColor.GREEN + "+" + xpAmount + " XP");
        if (multiplier > 1) {
            lore.add(ChatColor.GRAY + "(" + multiplier + "x value)");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createHeart() {
        ItemStack item = new ItemStack(Material.RED_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "❤ Healing Heart");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Pick up to restore health!");
        double healAmount = plugin.getConfigManager().getMainConfig().getDouble("drops.heart.heal-amount", 4.0);
        lore.add(ChatColor.GREEN + "+" + String.format("%.1f", healAmount) + " ❤");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
    
    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        Item item = event.getItem();
        
        // Check if player is in a run
        com.eldor.roguecraft.models.TeamRun teamRun = plugin.getRunManager().getTeamRun(player);
        Run run = null;
        boolean inRun = false;
        
        if (teamRun != null && teamRun.isActive()) {
            inRun = true;
        } else {
            run = plugin.getRunManager().getRun(player);
            if (run != null && run.isActive()) {
                inRun = true;
            }
        }
        
        if (!inRun) {
            return; // Don't process if not in a run
        }
        
        // Get pickup range stat
        double pickupRange = 1.0;
        if (teamRun != null) {
            pickupRange = teamRun.getStat("pickup_range");
        } else if (run != null) {
            pickupRange = run.getStat("pickup_range");
        }
        
        // Check distance - only allow pickup if within pickup range
        double distance = player.getLocation().distance(item.getLocation());
        if (distance > pickupRange) {
            event.setCancelled(true); // Too far away, cancel pickup
            return;
        }
        
        // Check for XP Token (regular, medium, or large)
        String customName = item.getCustomName();
        if (customName != null && customName.startsWith("XP_TOKEN")) {
            event.setCancelled(true);
            
            // Get stack size to calculate total XP from stacked tokens
            ItemStack itemStack = item.getItemStack();
            int stackSize = itemStack != null ? itemStack.getAmount() : 1;
            
            item.remove();
            
            // Extract multiplier from custom name (XP_TOKEN_3 or XP_TOKEN_5)
            int tokenMultiplier = 1;
            if (customName.contains("_")) {
                try {
                    tokenMultiplier = Integer.parseInt(customName.split("_")[2]);
                } catch (Exception e) {
                    tokenMultiplier = 1;
                }
            }
            
            int baseXpAmount = plugin.getConfigManager().getMainConfig().getInt("drops.xp-token.xp-amount", 7);
            int baseTokenXp = baseXpAmount * tokenMultiplier; // Token already has multiplier applied
            int totalBaseXp = baseTokenXp * stackSize; // Multiply by stack size
            
            if (teamRun != null && teamRun.isActive()) {
                // Scale XP with player level (increased scaling for later levels)
                int playerLevel = teamRun.getLevel();
                // Use exponential scaling: base + (level * 2) + (level^2 * 0.1) for better late-game scaling
                double levelScaling = (playerLevel * 2.0) + (playerLevel * playerLevel * 0.1);
                int xpAmount = (int) (totalBaseXp + (levelScaling * stackSize));
                
                // Apply XP multiplier
                double multiplier = teamRun.getStat("xp_multiplier");
                int finalXp = (int) (xpAmount * multiplier);
                teamRun.addExperience(finalXp);
                
                // Update XP bar for all team members
                for (Player p : teamRun.getPlayers()) {
                    if (p != null && p.isOnline()) {
                        com.eldor.roguecraft.util.XPBar.updateXPBarWithGold(
                            p,
                            teamRun.getExperience(),
                            teamRun.getExperienceToNextLevel(),
                            teamRun.getLevel(),
                            teamRun.getWave(),
                            teamRun.getCurrentGold()
                        );;
                    }
                }
                // Show XP gain as Text Display above player instead of chat
                showXPTextDisplay(player, finalXp);
            } else if (run != null && run.isActive()) {
                // Scale XP with player level (increased scaling for later levels)
                int playerLevel = run.getLevel();
                // Use exponential scaling: base + (level * 2) + (level^2 * 0.1) for better late-game scaling
                double levelScaling = (playerLevel * 2.0) + (playerLevel * playerLevel * 0.1);
                int xpAmount = (int) (totalBaseXp + (levelScaling * stackSize));
                
                // Apply XP multiplier
                double multiplier = run.getStat("xp_multiplier");
                int finalXp = (int) (xpAmount * multiplier);
                run.addExperience(finalXp);
                
                com.eldor.roguecraft.util.XPBar.updateXPBarWithGold(
                    player,
                    run.getExperience(),
                    run.getExperienceToNextLevel(),
                    run.getLevel(),
                    run.getWave(),
                    run.getCurrentGold()
                );
                // Show XP gain as Text Display above player instead of chat
                showXPTextDisplay(player, finalXp);
                // Sound effect for XP token pickup
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                player.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.1);
            }
            return;
        }
        
        // Check for Heart
        if (item.getCustomName() != null && item.getCustomName().equals("HEART_ITEM")) {
            event.setCancelled(true);
            item.remove();
            
            double healAmount = plugin.getConfigManager().getMainConfig().getDouble("drops.heart.heal-amount", 4.0);
            double currentHealth = player.getHealth();
            double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
            double newHealth = Math.min(maxHealth, currentHealth + healAmount);
            player.setHealth(newHealth);
            
            // Visual feedback
            player.getWorld().spawnParticle(org.bukkit.Particle.HEART, player.getEyeLocation(), 5, 0.3, 0.5, 0.3, 0);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
            player.sendMessage(ChatColor.RED + "❤ +" + String.format("%.1f", healAmount) + " Health restored!");
            return;
        }
        
        // Check for Unique Power-Up Items
        if (item.getCustomName() != null && item.getCustomName().startsWith("POWERUP_ITEM_")) {
            event.setCancelled(true);
            
            // Get stack size to apply effect multiple times if stacked
            ItemStack itemStack = item.getItemStack();
            int stackSize = itemStack != null ? itemStack.getAmount() : 1;
            
            item.remove();
            
            String powerUpType = item.getCustomName().replace("POWERUP_ITEM_", "");
            
            // Apply the effect for each item in the stack
            for (int i = 0; i < stackSize; i++) {
                if (powerUpType.equals("SPEED_BOOST")) {
                    // Apply Movement Speed Boost (temporary speed increase)
                    applySpeedBoost(player, teamRun, run);
                } else if (powerUpType.equals("TIME_FREEZE")) {
                    // Apply Time Freeze (freeze all mobs temporarily)
                    applyTimeFreeze(player, teamRun, run);
                } else if (powerUpType.equals("NUCLEAR_STRIKE")) {
                    // Apply Nuclear Strike (kill all mobs, no XP)
                    applyNuclearStrike(player, teamRun, run);
                } else if (powerUpType.equals("MAGNET")) {
                    // Apply Magnet (pull items to player)
                    applyMagnet(player, teamRun, run);
                } else if (powerUpType.equals("DOUBLE_XP")) {
                    // Apply Double XP (2x XP for 30 seconds)
                    applyDoubleXP(player, teamRun, run);
                } else if (powerUpType.equals("DOUBLE_GOLD")) {
                    // Apply Double Gold (2x gold for 30 seconds)
                    applyDoubleGold(player, teamRun, run);
                }
            }
            
            return;
        }
    }
    
    /**
     * Cancel natural TNT explosion damage to mobs (we apply custom damage instead)
     * and track damage for lifesteal
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        // Only handle damage to mobs (not players)
        if (!(event.getEntity() instanceof LivingEntity) || event.getEntity() instanceof Player) {
            return;
        }
        
        LivingEntity entity = (LivingEntity) event.getEntity();
        
        // Check if this entity was damaged by TNT explosion from our weapon
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION && 
            entity.hasMetadata("roguecraft_tnt_damaged")) {
            
            // Cancel the natural explosion damage since we apply custom damage
            event.setCancelled(true);
            
            String playerUuidStr = entity.getMetadata("roguecraft_tnt_damaged").get(0).asString();
            Player tntOwner = Bukkit.getPlayer(java.util.UUID.fromString(playerUuidStr));
            
            if (tntOwner != null && tntOwner.isOnline()) {
                // Check if player is in a run
                com.eldor.roguecraft.models.TeamRun teamRun = plugin.getRunManager().getTeamRun(tntOwner);
                com.eldor.roguecraft.models.Run run = null;
                
                if (teamRun == null || !teamRun.isActive()) {
                    run = plugin.getRunManager().getRun(tntOwner);
                }
                
                if ((teamRun != null && teamRun.isActive()) || (run != null && run.isActive())) {
                    // The custom damage from the scheduled task will apply lifesteal, so we don't need to do it here
                    // This handler just cancels the natural explosion damage
                }
            }
        }
    }
    
    private ItemStack createUniquePowerUpItem(String powerUpType) {
        ItemStack item;
        String name;
        String description;
        org.bukkit.ChatColor color;
        
        if (powerUpType.equals("SPEED_BOOST")) {
            item = new ItemStack(Material.FEATHER);
            name = "Movement Speed Boost";
            description = "Gain +100% movement speed for 15 seconds";
            color = ChatColor.AQUA;
        } else if (powerUpType.equals("TIME_FREEZE")) {
            item = new ItemStack(Material.CLOCK);
            name = "Time Freeze";
            description = "Freeze all enemies for 8 seconds";
            color = ChatColor.LIGHT_PURPLE;
        } else if (powerUpType.equals("NUCLEAR_STRIKE")) {
            item = new ItemStack(Material.TNT);
            name = "Nuclear Strike";
            description = "Instantly kills all enemies (no XP)";
            color = ChatColor.RED;
        } else if (powerUpType.equals("MAGNET")) {
            item = new ItemStack(Material.IRON_INGOT);
            name = "Magnet";
            description = "Pulls all nearby items to you for 20 seconds";
            color = ChatColor.BLUE;
        } else if (powerUpType.equals("DOUBLE_XP")) {
            item = new ItemStack(Material.EXPERIENCE_BOTTLE);
            name = "Double XP";
            description = "Gain 2x XP for 30 seconds";
            color = ChatColor.GREEN;
        } else { // DOUBLE_GOLD
            item = new ItemStack(Material.GOLD_INGOT);
            name = "Double Gold";
            description = "Gain 2x gold for 30 seconds";
            color = ChatColor.GOLD;
        }
        
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color + "✨ " + name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + description);
        lore.add("");
        lore.add(ChatColor.GRAY + "Pick up to activate!");
        meta.setLore(lore);
        
        // Add glowing effect to make the item glow
        // Use a fake enchantment that doesn't affect gameplay but shows the glow visual
        meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS); // Hide the enchantment text but keep the glow
        
        item.setItemMeta(meta);
        return item;
    }
    
    private void applySpeedBoost(Player player, com.eldor.roguecraft.models.TeamRun teamRun, Run run) {
        // Get current speed multiplier from run stats
        double speedMultiplier = 1.0;
        if (teamRun != null && teamRun.isActive()) {
            speedMultiplier = teamRun.getStat("speed");
        } else if (run != null && run.isActive()) {
            speedMultiplier = run.getStat("speed");
        }
        
        // Apply temporary speed boost (100% increase for 15 seconds)
        final double baseSpeed = 0.1;
        final double finalSpeedMultiplier = speedMultiplier; // Make final for lambda
        double boostedSpeed = baseSpeed * speedMultiplier * 2.0; // Double the current speed
        
        org.bukkit.attribute.Attribute speedAttr = org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED;
        org.bukkit.attribute.AttributeInstance speedInstance = player.getAttribute(speedAttr);
        if (speedInstance != null) {
            double currentSpeed = speedInstance.getBaseValue();
            speedInstance.setBaseValue(boostedSpeed);
            
            // Restore after 15 seconds
            final Player finalPlayer = player; // Make final for lambda
            final org.bukkit.attribute.AttributeInstance finalSpeedInstance = speedInstance; // Make final for lambda
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (finalPlayer.isOnline() && !finalPlayer.isDead()) {
                    // Restore to original speed (with stat multiplier)
                    double restoredSpeed = baseSpeed * finalSpeedMultiplier;
                    finalSpeedInstance.setBaseValue(restoredSpeed);
                    finalPlayer.sendMessage(ChatColor.GRAY + "Speed boost expired.");
                }
            }, 15 * 20L); // 15 seconds
            
            player.sendMessage(ChatColor.AQUA + "✨ Movement Speed Boost activated! +100% speed for 15 seconds!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_HORSE_GALLOP, 1.0f, 1.5f);
            player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.3);
            player.getWorld().spawnParticle(org.bukkit.Particle.CRIT, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.2);
        }
    }
    
    private void applyTimeFreeze(Player player, com.eldor.roguecraft.models.TeamRun teamRun, Run run) {
        // Freeze all mobs for 8 seconds
        if (teamRun != null && teamRun.isActive()) {
            plugin.getGameManager().freezeAllMobs(teamRun, 8);
            for (Player p : teamRun.getPlayers()) {
                if (p != null && p.isOnline()) {
                    p.sendMessage(ChatColor.LIGHT_PURPLE + "⏸ Time Freeze activated! All enemies frozen for 8 seconds!");
                    p.playSound(p.getLocation(), org.bukkit.Sound.ITEM_TOTEM_USE, 1.0f, 0.8f);
                    p.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.2);
                }
            }
        } else if (run != null && run.isActive()) {
            // For solo runs, find existing team run or use a different approach
            // Check if player has a team run (solo players might have both)
            com.eldor.roguecraft.models.TeamRun soloTeamRun = plugin.getRunManager().getTeamRun(player);
            if (soloTeamRun != null && soloTeamRun.isActive()) {
                plugin.getGameManager().freezeAllMobs(soloTeamRun, 8);
            } else {
                // For pure solo runs, freeze mobs directly in a radius
                freezeMobsInRadius(player, 100.0, 8);
            }
            player.sendMessage(ChatColor.LIGHT_PURPLE + "⏸ Time Freeze activated! All enemies frozen for 8 seconds!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_TOTEM_USE, 1.0f, 0.8f);
            player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.2);
        }
    }
    
    private void applyNuclearStrike(Player player, com.eldor.roguecraft.models.TeamRun teamRun, Run run) {
        // Get arena for the run
        com.eldor.roguecraft.models.Arena arena = plugin.getArenaManager().getDefaultArena();
        if (arena == null) {
            player.sendMessage(ChatColor.RED + "No arena found!");
            return;
        }
        
        // Execute nuke effect
        Object currentRun = teamRun != null ? teamRun : run;
        plugin.getGameManager().executeNuke(player, currentRun, arena);
        
        // Notify players
        if (teamRun != null && teamRun.isActive()) {
            for (Player p : teamRun.getPlayers()) {
                if (p != null && p.isOnline()) {
                    p.sendMessage(ChatColor.RED + "§l☢ NUCLEAR STRIKE! ☢");
                    p.sendMessage(ChatColor.YELLOW + "All enemies eliminated!");
                }
            }
        } else if (run != null && run.isActive()) {
            player.sendMessage(ChatColor.RED + "§l☢ NUCLEAR STRIKE! ☢");
            player.sendMessage(ChatColor.YELLOW + "All enemies eliminated!");
        }
    }
    
    /**
     * Apply magnet effect with large radius (used for Wither boss reward)
     */
    private void applyMagnetLargeRadius(Player player, com.eldor.roguecraft.models.TeamRun teamRun, Run run, double radius) {
        // Check if player is in a run
        if ((teamRun == null || !teamRun.isActive()) && (run == null || !run.isActive())) {
            return;
        }
        
        // Notify player
        player.sendMessage(ChatColor.BLUE + "🧲 Boss Defeated! Pulling all items from " + (int)radius + " blocks!");
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.5f, 1.0f);
        player.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, player.getLocation().add(0, 1, 0), 50, radius * 0.5, 2.0, radius * 0.5, 0.5);
        
        // Start magnet effect (pull items every tick for 10 seconds with large radius)
        final Player finalPlayer = player;
        final int magnetDuration = 10 * 20; // 10 seconds in ticks
        final double finalRadius = radius;
        final int[] ticksElapsed = {0};
        final org.bukkit.scheduler.BukkitTask[] magnetTaskRef = new org.bukkit.scheduler.BukkitTask[1];
        
        magnetTaskRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!finalPlayer.isOnline() || finalPlayer.isDead()) {
                if (magnetTaskRef[0] != null) {
                    magnetTaskRef[0].cancel();
                }
                return;
            }
            
            // Check if still in run
            boolean stillInRun = false;
            if (teamRun != null && teamRun.isActive()) {
                stillInRun = teamRun.getPlayers().contains(finalPlayer);
            } else if (run != null && run.isActive()) {
                stillInRun = run.getPlayer().equals(finalPlayer);
            }
            
            if (!stillInRun) {
                if (magnetTaskRef[0] != null) {
                    magnetTaskRef[0].cancel();
                }
                return;
            }
            
            ticksElapsed[0] += 1;
            if (ticksElapsed[0] >= magnetDuration) {
                if (magnetTaskRef[0] != null) {
                    magnetTaskRef[0].cancel();
                }
                return;
            }
            
            // Pull nearby items (XP tokens, hearts, power-ups) with large radius
            Location playerLoc = finalPlayer.getLocation();
            
            for (Entity entity : finalPlayer.getWorld().getNearbyEntities(playerLoc, finalRadius, finalRadius, finalRadius)) {
                if (entity instanceof Item) {
                    Item item = (Item) entity;
                    String customName = item.getCustomName();
                    
                    // Only pull XP tokens (magnets only pull XP tokens now)
                    if (customName != null && customName.startsWith("XP_TOKEN")) {
                        // Pull item towards player with much stronger pull
                        Vector direction = playerLoc.toVector().subtract(item.getLocation().toVector()).normalize();
                        double distance = item.getLocation().distance(playerLoc);
                        // Much faster pull speed for boss reward (increased from 1.0)
                        double pullSpeed = distance > 20 ? 2.0 : 3.5; // Faster when closer
                        item.setVelocity(direction.multiply(pullSpeed));
                        
                        // Visual effect
                        if (ticksElapsed[0] % 5 == 0) { // Only show particles every 5 ticks to reduce lag
                            finalPlayer.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, item.getLocation(), 1, 0.1, 0.1, 0.1, 0.01);
                        }
                    }
                }
            }
        }, 0L, 1L); // Run every tick
        
        // Cancel task after duration (backup cancellation)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (magnetTaskRef[0] != null && !magnetTaskRef[0].isCancelled()) {
                magnetTaskRef[0].cancel();
            }
        }, magnetDuration);
    }
    
    private void applyMagnet(Player player, com.eldor.roguecraft.models.TeamRun teamRun, Run run) {
        // Check if player is in a run
        if ((teamRun == null || !teamRun.isActive()) && (run == null || !run.isActive())) {
            return;
        }
        
        // Notify player
        player.sendMessage(ChatColor.BLUE + "🧲 Magnet activated! Pulling items for 20 seconds!");
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        player.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.3);
        
        // Start magnet effect (pull items every tick for 20 seconds)
        final Player finalPlayer = player;
        final int magnetDuration = 20 * 20; // 20 seconds in ticks
        final int[] ticksElapsed = {0};
        final org.bukkit.scheduler.BukkitTask[] magnetTaskRef = new org.bukkit.scheduler.BukkitTask[1];
        
        magnetTaskRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!finalPlayer.isOnline() || finalPlayer.isDead()) {
                if (magnetTaskRef[0] != null) {
                    magnetTaskRef[0].cancel();
                }
                return;
            }
            
            // Check if still in run
            boolean stillInRun = false;
            if (teamRun != null && teamRun.isActive()) {
                stillInRun = teamRun.getPlayers().contains(finalPlayer);
            } else if (run != null && run.isActive()) {
                stillInRun = run.getPlayer().equals(finalPlayer);
            }
            
            if (!stillInRun) {
                if (magnetTaskRef[0] != null) {
                    magnetTaskRef[0].cancel();
                }
                return;
            }
            
            ticksElapsed[0] += 1;
            if (ticksElapsed[0] >= magnetDuration) {
                finalPlayer.sendMessage(ChatColor.GRAY + "Magnet effect expired.");
                if (magnetTaskRef[0] != null) {
                    magnetTaskRef[0].cancel();
                }
                return;
            }
            
            // Pull nearby items (XP tokens, hearts, power-ups)
            Location playerLoc = finalPlayer.getLocation();
            double pullRadius = 30.0; // Increased from 15 to 30 block radius
            
            for (Entity entity : finalPlayer.getWorld().getNearbyEntities(playerLoc, pullRadius, pullRadius, pullRadius)) {
                if (entity instanceof Item) {
                    Item item = (Item) entity;
                    String customName = item.getCustomName();
                    
                    // Only pull XP tokens (magnets only pull XP tokens now)
                    if (customName != null && customName.startsWith("XP_TOKEN")) {
                        // Pull item towards player with much faster speed
                        Vector direction = playerLoc.toVector().subtract(item.getLocation().toVector()).normalize();
                        double distance = item.getLocation().distance(playerLoc);
                        // Faster pull speed (increased from 0.5 to 1.5), and even faster when close
                        double pullSpeed = distance > 10 ? 1.5 : 2.5; // Speed of pull - faster when closer
                        item.setVelocity(direction.multiply(pullSpeed));
                        
                        // Visual effect
                        finalPlayer.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, item.getLocation(), 2, 0.1, 0.1, 0.1, 0.01);
                    }
                }
            }
        }, 0L, 1L); // Run every tick
        
        // Cancel task after duration (backup cancellation)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (magnetTaskRef[0] != null && !magnetTaskRef[0].isCancelled()) {
                magnetTaskRef[0].cancel();
            }
        }, magnetDuration);
    }
    
    private void applyDoubleXP(Player player, com.eldor.roguecraft.models.TeamRun teamRun, Run run) {
        // Check if player is in a run
        if ((teamRun == null || !teamRun.isActive()) && (run == null || !run.isActive())) {
            return;
        }
        
        // Store original multiplier and double it
        double originalMultiplier;
        if (teamRun != null && teamRun.isActive()) {
            originalMultiplier = teamRun.getStat("xp_multiplier");
            teamRun.setStat("xp_multiplier", originalMultiplier * 2.0);
            
            // Notify all team members
            for (Player p : teamRun.getPlayers()) {
                if (p != null && p.isOnline()) {
                    p.sendMessage(ChatColor.GREEN + "✨ Double XP activated! 2x XP for 30 seconds!");
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    p.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.3);
                }
            }
        } else if (run != null && run.isActive()) {
            originalMultiplier = run.getStat("xp_multiplier");
            run.setStat("xp_multiplier", originalMultiplier * 2.0);
            
            player.sendMessage(ChatColor.GREEN + "✨ Double XP activated! 2x XP for 30 seconds!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            player.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.3);
        } else {
            return;
        }
        
        // Restore original multiplier after 30 seconds
        final double finalOriginalMultiplier = originalMultiplier;
        final com.eldor.roguecraft.models.TeamRun finalTeamRun = teamRun;
        final Run finalRun = run;
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (finalTeamRun != null && finalTeamRun.isActive()) {
                // Restore original multiplier
                finalTeamRun.setStat("xp_multiplier", finalOriginalMultiplier);
                
                // Notify team members
                for (Player p : finalTeamRun.getPlayers()) {
                    if (p != null && p.isOnline()) {
                        p.sendMessage(ChatColor.GRAY + "Double XP effect expired.");
                    }
                }
            } else if (finalRun != null && finalRun.isActive()) {
                // Restore original multiplier
                finalRun.setStat("xp_multiplier", finalOriginalMultiplier);
                
                Player finalPlayer = finalRun.getPlayer();
                if (finalPlayer != null && finalPlayer.isOnline()) {
                    finalPlayer.sendMessage(ChatColor.GRAY + "Double XP effect expired.");
                }
            }
        }, 30 * 20L); // 30 seconds
    }
    
    private void applyDoubleGold(Player player, com.eldor.roguecraft.models.TeamRun teamRun, Run run) {
        // Check if player is in a run
        if ((teamRun == null || !teamRun.isActive()) && (run == null || !run.isActive())) {
            return;
        }
        
        // Set metadata to indicate 2x gold is active
        player.setMetadata("roguecraft_2x_gold", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        
        // Notify players
        if (teamRun != null && teamRun.isActive()) {
            for (Player p : teamRun.getPlayers()) {
                if (p != null && p.isOnline()) {
                    p.sendMessage(ChatColor.GOLD + "✨ Double Gold activated! 2x gold for 30 seconds!");
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    p.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.3);
                }
            }
        } else if (run != null && run.isActive()) {
            player.sendMessage(ChatColor.GOLD + "✨ Double Gold activated! 2x gold for 30 seconds!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            player.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.3);
        } else {
            return;
        }
        
        // Remove metadata after 30 seconds
        final com.eldor.roguecraft.models.TeamRun finalTeamRun = teamRun;
        final Run finalRun = run;
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.removeMetadata("roguecraft_2x_gold", plugin);
            
            if (finalTeamRun != null && finalTeamRun.isActive()) {
                for (Player p : finalTeamRun.getPlayers()) {
                    if (p != null && p.isOnline()) {
                        p.sendMessage(ChatColor.GRAY + "Double Gold effect expired.");
                    }
                }
            } else if (finalRun != null && finalRun.isActive()) {
                Player finalPlayer = finalRun.getPlayer();
                if (finalPlayer != null && finalPlayer.isOnline()) {
                    finalPlayer.sendMessage(ChatColor.GRAY + "Double Gold effect expired.");
                }
            }
        }, 30 * 20L); // 30 seconds
    }
    
    /**
     * Accumulate gold for a player (will be displayed periodically)
     */
    private void accumulateGold(UUID playerId, int goldAmount) {
        accumulatedGold.put(playerId, accumulatedGold.getOrDefault(playerId, 0) + goldAmount);
    }
    
    /**
     * Show gold gain as floating text above the player using ArmorStand
     * This is called periodically to display accumulated gold
     */
    private void showGoldTextDisplayNow(Player player, int goldAmount) {
        // Lower spawn height - just above player's head
        Location loc = player.getLocation().add(0, 0.3, 0);
        
        try {
            // Spawn invisible ArmorStand with custom name
            org.bukkit.entity.ArmorStand armorStand = loc.getWorld().spawn(loc, org.bukkit.entity.ArmorStand.class, (stand) -> {
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setMarker(true);
                stand.setSmall(true);
                stand.setInvulnerable(true);
                stand.setCollidable(false);
                // Make text visible with bold and gold color
                stand.setCustomName(ChatColor.GOLD + "" + ChatColor.BOLD + "💰 +" + goldAmount + " gold");
                stand.setCustomNameVisible(true);
                // Add glowing effect to make it more visible
                stand.setGlowing(true);
                // Add metadata to prevent mob naming system from interfering
                stand.setMetadata("roguecraft_xp_display", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
            });
            
            // Add particles around the text for extra visibility
            player.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, loc, 5, 0.3, 0.3, 0.3, 0.1);
            
            // Make it move upward and fade out
            final org.bukkit.entity.ArmorStand finalStand = armorStand;
            org.bukkit.scheduler.BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!finalStand.isValid()) {
                    return;
                }
                
                Location currentLoc = finalStand.getLocation();
                currentLoc.add(0, 0.05, 0); // Move upward slowly
                finalStand.teleport(currentLoc);
            }, 0L, 1L);
            
            // Remove after 2 seconds
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (finalStand.isValid()) {
                    finalStand.remove();
                }
                if (task != null && !task.isCancelled()) {
                    task.cancel();
                }
            }, 40L); // 2 seconds
        } catch (Exception e) {
            plugin.getLogger().warning("[GameListener] Failed to show gold text display: " + e.getMessage());
        }
    }
    
    /**
     * Update boss bar to include gold information
     */
    private void updateBossBarWithGold(Player player, Object run) {
        if (run instanceof com.eldor.roguecraft.models.TeamRun) {
            com.eldor.roguecraft.models.TeamRun teamRun = (com.eldor.roguecraft.models.TeamRun) run;
            com.eldor.roguecraft.util.XPBar.updateXPBarWithGold(
                player,
                teamRun.getExperience(),
                teamRun.getExperienceToNextLevel(),
                teamRun.getLevel(),
                teamRun.getWave(),
                teamRun.getCurrentGold()
            );
        } else if (run instanceof com.eldor.roguecraft.models.Run) {
            com.eldor.roguecraft.models.Run soloRun = (com.eldor.roguecraft.models.Run) run;
            com.eldor.roguecraft.util.XPBar.updateXPBarWithGold(
                player,
                soloRun.getExperience(),
                soloRun.getExperienceToNextLevel(),
                soloRun.getLevel(),
                soloRun.getWave(),
                soloRun.getCurrentGold()
            );
        }
    }
    
    /**
     * Show XP gain as floating text above the player using ArmorStand
     */
    private void showXPTextDisplay(Player player, int xpAmount) {
        // Lower spawn height - just above player's head
        Location loc = player.getLocation().add(0, 0.3, 0);
        
        try {
            // Spawn invisible ArmorStand with custom name
            // Use spawn method that allows setting properties before entity becomes visible
            org.bukkit.entity.ArmorStand armorStand = loc.getWorld().spawn(loc, org.bukkit.entity.ArmorStand.class, (stand) -> {
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setMarker(true); // Makes it not interactable and invisible
                stand.setSmall(true);
                stand.setInvulnerable(true);
                stand.setCollidable(false);
                // Make text more visible with bold and bright green
                stand.setCustomName(ChatColor.GREEN + "" + ChatColor.BOLD + "✨ +" + xpAmount + " XP");
                stand.setCustomNameVisible(true);
                // Add glowing effect to make it more visible
                stand.setGlowing(true);
                // Add metadata to prevent mob naming system from interfering
                stand.setMetadata("roguecraft_xp_display", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
            });
            
            // Add particles around the text for extra visibility
            player.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, loc, 5, 0.3, 0.3, 0.3, 0.1);
            
            // Make it move upward and fade out
            final org.bukkit.entity.ArmorStand finalStand = armorStand;
            org.bukkit.scheduler.BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!finalStand.isValid()) {
                    return;
                }
                
                Location currentLoc = finalStand.getLocation();
                finalStand.teleport(currentLoc.add(0, 0.05, 0)); // Move up slowly
                
                // Fade out over time by making name less visible
                int age = finalStand.getTicksLived();
                if (age > 20) { // Start fading after 1 second
                    int fadeTime = 40; // 2 seconds to fade
                    int fadeProgress = Math.min(age - 20, fadeTime);
                    double opacity = 1.0 - ((double) fadeProgress / fadeTime);
                    
                    // Update name with opacity (using color codes) - keep bold for visibility
                    if (opacity > 0) {
                        String colorCode = opacity > 0.75 ? ChatColor.GREEN.toString() + ChatColor.BOLD.toString() : 
                                         opacity > 0.5 ? ChatColor.YELLOW.toString() + ChatColor.BOLD.toString() : 
                                         ChatColor.GRAY.toString() + ChatColor.BOLD.toString();
                        finalStand.setCustomName(colorCode + "✨ +" + xpAmount + " XP");
                    }
                }
            }, 0L, 1L);
            
            // Remove after 3 seconds
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (finalStand.isValid()) {
                    finalStand.remove();
                }
                if (task != null && !task.isCancelled()) {
                    task.cancel();
                }
            }, 60L); // 3 seconds
            
        } catch (Exception e) {
            // Fallback to chat message if ArmorStand is not available
            player.sendMessage(ChatColor.GREEN + "✨ +" + xpAmount + " XP from token!");
        }
    }
    
    /**
     * Grant a rare power-up to the player when they kill a mob (Treasure Hunter shrine buff)
     */
    private void grantRarePowerUp(Player player, com.eldor.roguecraft.models.TeamRun teamRun, Run run) {
        // Get player level and luck for power-up generation
        int playerLevel = 1;
        double luck = 1.0;
        
        if (teamRun != null && teamRun.isActive()) {
            playerLevel = teamRun.getLevel();
            luck = teamRun.getStat("luck");
        } else if (run != null && run.isActive()) {
            playerLevel = run.getLevel();
            luck = run.getStat("luck");
        }
        
        // Generate a rare power-up (force rare rarity)
        Object currentRun = teamRun != null ? teamRun : run;
        com.eldor.roguecraft.models.PowerUp rarePowerUp = plugin.getPowerUpManager().generateRarePowerUp(playerLevel, luck, currentRun);
        
        if (rarePowerUp != null) {
            // Apply the power-up directly using PowerUpGUI's apply method
            com.eldor.roguecraft.gui.PowerUpGUI powerUpGUI;
            
            if (teamRun != null && teamRun.isActive()) {
                powerUpGUI = new com.eldor.roguecraft.gui.PowerUpGUI(plugin, player, teamRun);
                powerUpGUI.applyPowerUpDirectly(rarePowerUp, teamRun);
            } else if (run != null && run.isActive()) {
                powerUpGUI = new com.eldor.roguecraft.gui.PowerUpGUI(plugin, player, run);
                powerUpGUI.applyPowerUpDirectly(rarePowerUp, run);
            } else {
                return; // No active run
            }
            
            player.sendMessage(ChatColor.GOLD + "✦ Rare Power-Up earned from Treasure Hunter: " + rarePowerUp.getName() + "!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.3);
        }
    }
    
    private void freezeMobsInRadius(Player player, double radius, int seconds) {
        java.util.Set<LivingEntity> frozenMobs = new java.util.HashSet<>();
        Location center = player.getLocation();
        
        // Find and freeze all mobs in radius
        for (org.bukkit.entity.Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                LivingEntity mob = (LivingEntity) entity;
                mob.setAI(false);
                frozenMobs.add(mob);
                
                // Remove nearby projectiles
                for (org.bukkit.entity.Entity nearby : mob.getNearbyEntities(5, 5, 5)) {
                    if (nearby instanceof org.bukkit.entity.Projectile) {
                        nearby.remove();
                    }
                }
            }
        }
        
        // Unfreeze after duration
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (LivingEntity mob : frozenMobs) {
                if (mob != null && !mob.isDead() && mob.isValid()) {
                    mob.setAI(true);
                }
            }
        }, seconds * 20L);
    }

    private int calculateExperience(EntityType type, int wave, double difficultyMultiplier) {
        // Try to get XP from balance.yml config, fallback to default if not found
        org.bukkit.configuration.ConfigurationSection experienceSection = 
            plugin.getConfigManager().getBalanceConfig().getConfigurationSection("experience.base");
        
        int baseXP = 15; // Default fallback
        
        if (experienceSection != null) {
            // Convert EntityType to lowercase string for config key
            String mobKey = type.name().toLowerCase();
            // Try to get XP value from config
            if (experienceSection.contains(mobKey)) {
                baseXP = experienceSection.getInt(mobKey, 15);
            } else {
                // Fallback to default if mob type not in config
                baseXP = 15;
            }
        }
        
        // Scale with wave (15% per wave, increased from 10% for faster scaling)
        double waveMultiplier = 1.0 + (wave * 0.15);
        
        // Scale with difficulty multiplier (higher difficulty = more XP)
        // Difficulty multiplier typically ranges from 1.0 to 2.0+
        // So if difficulty is 1.5, XP is 1.5x base
        double totalMultiplier = waveMultiplier * difficultyMultiplier;
        
        return (int) (baseXP * totalMultiplier);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPotionSplash(PotionSplashEvent event) {
        ThrownPotion potion = event.getEntity();
        
        // Check if this is a weapon potion (has metadata)
        if (potion.hasMetadata("weapon_potion")) {
            // Cancel the event to prevent vanilla potion effects
            event.setCancelled(true);
            
            // Get the player who threw it
            String playerUuid = potion.getMetadata("weapon_potion").get(0).asString();
            Player player = Bukkit.getPlayer(java.util.UUID.fromString(playerUuid));
            
            if (player == null || !player.isOnline()) {
                return; // Player not found
            }
            
            // Get weapon stats from metadata
            double weaponDamage = potion.getMetadata("weapon_damage").get(0).asDouble();
            double weaponAoe = potion.getMetadata("weapon_aoe").get(0).asDouble();
            
            // Location where potion splashed
            Location splashLoc = potion.getLocation();
            
            // Spawn particles once (not continuously)
            splashLoc.getWorld().spawnParticle(Particle.WITCH, splashLoc, 30, 1.5, 1, 1.5, 0);
            splashLoc.getWorld().playSound(splashLoc, Sound.ENTITY_SPLASH_POTION_BREAK, 1.0f, 1.0f);
            
            // Apply damage and effects to nearby enemies
            double totalDamageDealt = 0.0;
            for (Entity entity : splashLoc.getWorld().getNearbyEntities(splashLoc, weaponAoe, weaponAoe, weaponAoe)) {
                // Only affect enemies, not the player or other players
                if (entity instanceof LivingEntity && !(entity instanceof Player) && entity != player) {
                    LivingEntity living = (LivingEntity) entity;
                    
                    // Calculate damage
                    double distance = entity.getLocation().distance(splashLoc);
                    double distanceMultiplier = Math.max(0.1, 1.0 - (distance / weaponAoe)); // Damage falloff
                    double baseDamage = weaponDamage * distanceMultiplier;
                    
                    // Apply damage using WeaponManager
                    double finalDamage = plugin.getWeaponManager().calculateFinalDamage(player, baseDamage, living);
                    living.damage(finalDamage, player);
                    
                    // Apply potion effects only to enemies
                    living.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
                    living.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
                    
                    // Apply weapon mod effects
                    plugin.getWeaponManager().applyWeaponModEffects(player, living);
                    
                    totalDamageDealt += finalDamage;
                }
            }
            
            // Apply lifesteal if any damage was dealt
            if (totalDamageDealt > 0) {
                plugin.getWeaponManager().applyLifesteal(player, totalDamageDealt);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        // Handle ice shard weapon hits
        if (event.getEntity() instanceof Snowball) {
            Snowball snowball = (Snowball) event.getEntity();
            
            if (snowball.hasMetadata("ice_shard_weapon")) {
                // Get the player who shot it
                String playerUuid = snowball.getMetadata("ice_shard_weapon").get(0).asString();
                Player player = Bukkit.getPlayer(java.util.UUID.fromString(playerUuid));
                
                if (player == null || !player.isOnline()) {
                    return;
                }
                
                // Get weapon stats from metadata
                double weaponDamage = snowball.getMetadata("ice_shard_damage").get(0).asDouble();
                double weaponAoe = snowball.getMetadata("ice_shard_aoe").get(0).asDouble();
                
                // Location where snowball hit
                Location hitLoc = snowball.getLocation();
                
                // Spawn particles
                hitLoc.getWorld().spawnParticle(Particle.SNOWFLAKE, hitLoc, 30, 1, 1, 1, 0);
                hitLoc.getWorld().playSound(hitLoc, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
                
                // Apply damage and effects to nearby enemies
                double totalDamageDealt = 0.0;
                for (Entity entity : hitLoc.getWorld().getNearbyEntities(hitLoc, weaponAoe, weaponAoe, weaponAoe)) {
                    // Only affect enemies, not the player or other players
                    if (entity instanceof LivingEntity && !(entity instanceof Player) && entity != player) {
                        LivingEntity living = (LivingEntity) entity;
                        
                        // Calculate damage with distance falloff
                        double distance = entity.getLocation().distance(hitLoc);
                        double distanceMultiplier = Math.max(0.1, 1.0 - (distance / weaponAoe));
                        double baseDamage = weaponDamage * distanceMultiplier;
                        
                        // Apply damage
                        double finalDamage = plugin.getWeaponManager().calculateFinalDamage(player, baseDamage, living);
                        living.damage(finalDamage, player);
                        
                        // Apply weapon mod effects
                        plugin.getWeaponManager().applyWeaponModEffects(player, living);
                        
                        // Apply ice effects
                        living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1)); // Slow II for 3 seconds
                        living.setFreezeTicks(100); // Freeze effect
                        
                        totalDamageDealt += finalDamage;
                    }
                }
                
                // Apply lifesteal if any damage was dealt
                if (totalDamageDealt > 0) {
                    plugin.getWeaponManager().applyLifesteal(player, totalDamageDealt);
                }
                
                // Remove metadata so fallback task doesn't also apply damage
                snowball.removeMetadata("ice_shard_weapon", plugin);
                snowball.removeMetadata("ice_shard_damage", plugin);
                snowball.removeMetadata("ice_shard_aoe", plugin);
            }
        }
    }
    
    /**
     * Clean up accumulated gold when player quits
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        accumulatedGold.remove(event.getPlayer().getUniqueId());
    }
}
