package com.eldor.roguecraft.managers;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.PowerUp;
import com.eldor.roguecraft.models.Run;
import com.eldor.roguecraft.models.TeamRun;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class SynergyManager implements Listener {
    private final RoguecraftPlugin plugin;
    private final Map<UUID, Integer> killCounts; // Track kills per run for Rapid Escalation and Lucky Streak
    private final Map<UUID, Long> lastImmortalUse; // Track Immortal Build cooldown
    
    public SynergyManager(RoguecraftPlugin plugin) {
        this.plugin = plugin;
        this.killCounts = new HashMap<>();
        this.lastImmortalUse = new HashMap<>();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * Initialize synergy tracking for a run
     */
    public void startSynergies(Object run) {
        UUID runId = getRunId(run);
        if (runId != null) {
            killCounts.put(runId, 0);
        }
    }
    
    /**
     * Cleanup synergy tracking for a run
     */
    public void stopSynergies(UUID runId) {
        killCounts.remove(runId);
        lastImmortalUse.remove(runId);
    }
    
    /**
     * Handle kill events for synergies
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return;
        
        Player killer = entity.getKiller();
        if (killer == null) return;
        
        // Skip if killer is in GUI (pause synergies while GUI is open)
        if (plugin.getGuiManager().isPlayerInGUI(killer.getUniqueId()) || 
            plugin.getShrineManager().isPlayerInShrineGUI(killer.getUniqueId())) {
            return;
        }
        
        Object run = plugin.getRunManager().getTeamRun(killer);
        if (run == null) {
            run = plugin.getRunManager().getRun(killer);
        }
        if (run == null || !isRunActive(run)) return;
        
        // Also check TeamRun's GUI tracking
        if (run instanceof com.eldor.roguecraft.models.TeamRun) {
            com.eldor.roguecraft.models.TeamRun teamRun = (com.eldor.roguecraft.models.TeamRun) run;
            if (teamRun.isPlayerInGUI(killer.getUniqueId())) {
                return;
            }
        }
        
        UUID runId = getRunId(run);
        
        // Increment kill count
        int kills = killCounts.getOrDefault(runId, 0) + 1;
        killCounts.put(runId, kills);
        
        // Check for synergies
        List<PowerUp> synergies = getActiveSynergies(run);
        
        for (PowerUp synergy : synergies) {
            String synergyName = synergy.getName();
            double value = synergy.getValue();
            
            switch (synergyName) {
                case "Rapid Escalation":
                    applyRapidEscalation(run, value);
                    break;
                case "Chain Reaction":
                    if (shouldTriggerChainReaction(value)) {
                        triggerChainReaction(killer, entity.getLocation());
                    }
                    break;
                case "Lucky Streak":
                    checkLuckyStreak(run, kills, value);
                    break;
            }
        }
    }
    
    /**
     * Handle critical hits for Critical Mass synergy
     */
    public void onCriticalHit(Player player, LivingEntity target, double damage) {
        // Skip if player is in GUI
        if (plugin.getGuiManager().isPlayerInGUI(player.getUniqueId()) || 
            plugin.getShrineManager().isPlayerInShrineGUI(player.getUniqueId())) {
            return;
        }
        
        Object run = plugin.getRunManager().getTeamRun(player);
        if (run == null) {
            run = plugin.getRunManager().getRun(player);
        }
        if (run == null || !isRunActive(run)) return;
        
        // Also check TeamRun's GUI tracking
        if (run instanceof com.eldor.roguecraft.models.TeamRun) {
            com.eldor.roguecraft.models.TeamRun teamRun = (com.eldor.roguecraft.models.TeamRun) run;
            if (teamRun.isPlayerInGUI(player.getUniqueId())) {
                return;
            }
        }
        
        PowerUp criticalMass = getSynergyByName(run, "Critical Mass");
        if (criticalMass != null) {
            // Get player's weapon to check if it's Lightning Strike
            com.eldor.roguecraft.models.Weapon weapon = null;
            if (run instanceof com.eldor.roguecraft.models.TeamRun) {
                weapon = ((com.eldor.roguecraft.models.TeamRun) run).getWeapon();
            } else if (run instanceof com.eldor.roguecraft.models.Run) {
                weapon = ((com.eldor.roguecraft.models.Run) run).getWeapon();
            }
            
            // Lightning Strike: Reduced Critical Mass AOE (25% per value point instead of 50%)
            // Other weapons: Normal Critical Mass AOE (50% per value point)
            boolean isLightningStrike = weapon != null && weapon.getType() == com.eldor.roguecraft.models.Weapon.WeaponType.LIGHTNING_STRIKE;
            double aoePercentMultiplier = isLightningStrike ? 25.0 : 50.0; // Reduced for Lightning Strike
            double aoePercent = criticalMass.getValue() * aoePercentMultiplier;
            double aoeDamage = damage * (aoePercent / 100.0);
            
            // Boss-specific: Further reduce Critical Mass AOE damage against bosses
            boolean isBoss = target.hasMetadata("roguecraft_boss") || target.hasMetadata("roguecraft_elite_boss");
            if (isBoss) {
                aoeDamage *= 0.5; // 50% reduction for bosses
            }
            
            // Explode at target location
            Location loc = target.getLocation();
            double radius = 4.0;
            
            for (Entity nearby : target.getNearbyEntities(radius, radius, radius)) {
                if (nearby instanceof LivingEntity && !(nearby instanceof Player) && nearby != target) {
                    LivingEntity mob = (LivingEntity) nearby;
                    mob.damage(aoeDamage);
                    
                    // Visual feedback
                    player.getWorld().spawnParticle(Particle.EXPLOSION, mob.getLocation(), 1, 0.3, 0.3, 0.3, 0);
                }
            }
            
            // Visual feedback at explosion center
            player.getWorld().spawnParticle(Particle.EXPLOSION, loc, 3, 0.5, 0.5, 0.5, 0.1);
            player.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.2f);
        }
    }
    
    /**
     * Get damage multiplier from synergies
     */
    public double getDamageMultiplier(Object run, Player player) {
        // Skip synergy damage bonuses if player is in GUI
        if (plugin.getGuiManager().isPlayerInGUI(player.getUniqueId()) || 
            plugin.getShrineManager().isPlayerInShrineGUI(player.getUniqueId())) {
            return 1.0;
        }
        
        // Also check TeamRun's GUI tracking
        if (run instanceof com.eldor.roguecraft.models.TeamRun) {
            com.eldor.roguecraft.models.TeamRun teamRun = (com.eldor.roguecraft.models.TeamRun) run;
            if (teamRun.isPlayerInGUI(player.getUniqueId())) {
                return 1.0;
            }
        }
        
        double multiplier = 1.0;
        
        // Rapid Escalation - damage per kill (capped to prevent overpowered scaling)
        PowerUp rapidEscalation = getSynergyByName(run, "Rapid Escalation");
        if (rapidEscalation != null) {
            UUID runId = getRunId(run);
            int kills = killCounts.getOrDefault(runId, 0);
            // Reduced from 5% to 2% per value point per kill, with diminishing returns
            double damagePerKill = rapidEscalation.getValue() * 2.0; // 2% per value point per kill (reduced from 5%)
            
            // Apply diminishing returns: first 20 kills at full value, then 50% effectiveness
            double effectiveKills;
            if (kills <= 20) {
                effectiveKills = kills;
            } else {
                // After 20 kills: 20 full kills + (remaining kills * 0.5)
                effectiveKills = 20 + ((kills - 20) * 0.5);
            }
            
            double bonusDamage = (effectiveKills * damagePerKill) / 100.0;
            
            // Cap total bonus at 3x damage (2x multiplier = +100% = 200% total damage)
            // This means max Rapid Escalation bonus is +200% damage = 3x total
            multiplier += Math.min(bonusDamage, 2.0);
        }
        
        // Berserker Mode - damage when below 30% HP
        PowerUp berserker = getSynergyByName(run, "Berserker Mode");
        if (berserker != null) {
            double healthPercent = (player.getHealth() / player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()) * 100.0;
            if (healthPercent < 30.0) {
                double berserkerBonus = berserker.getValue() * 30.0; // 30% per value point
                multiplier += berserkerBonus / 100.0;
            }
        }
        
        // Elemental Fusion - multiplier for weapon effects
        // Further reduced scaling to prevent over-tuning (especially for potion throwing)
        // Scale: 1.0x base + 0.02x per value point, capped at 1.15x max
        PowerUp elementalFusion = getSynergyByName(run, "Elemental Fusion");
        if (elementalFusion != null) {
            double fusionValue = elementalFusion.getValue();
            // Convert value to a reasonable multiplier: 1.0 + (value * 0.02), capped at 1.15x
            // Example: value 3.0 = 1.06x multiplier, value 7.5 = 1.15x (capped), value 10.0 = 1.15x (capped)
            double fusionMultiplier = 1.0 + (fusionValue * 0.02);
            fusionMultiplier = Math.min(1.15, fusionMultiplier); // Cap at 1.15x max (15% bonus, reduced from 30%)
            multiplier *= fusionMultiplier;
        }
        
        return multiplier;
    }
    
    /**
     * Handle Immortal Build - prevent death
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        
        Player player = (Player) event.getEntity();
        Object run = plugin.getRunManager().getTeamRun(player);
        if (run == null) {
            run = plugin.getRunManager().getRun(player);
        }
        if (run == null || !isRunActive(run)) return;
        
        PowerUp immortal = getSynergyByName(run, "Immortal Build");
        if (immortal != null) {
            UUID runId = getRunId(run);
            
            // Check cooldown (30 seconds)
            long now = System.currentTimeMillis();
            long lastUse = lastImmortalUse.getOrDefault(runId, 0L);
            if (now - lastUse < 30000) {
                return; // On cooldown
            }
            
            // Check if this would kill the player
            double finalDamage = event.getFinalDamage();
            double currentHealth = player.getHealth();
            
            if (currentHealth - finalDamage <= 0) {
                // Prevent death
                event.setCancelled(true);
                
                // Set health to 1 HP
                player.setHealth(1.0);
                
                // Grant invulnerability period
                double invulnSeconds = immortal.getValue();
                long invulnTicks = (long) (invulnSeconds * 20);
                
                player.setMetadata("immortal_invuln", new FixedMetadataValue(plugin, System.currentTimeMillis() + (invulnSeconds * 1000)));
                
                // Cancel damage for invulnerability period
                BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (!player.isOnline() || player.isDead()) {
                        return;
                    }
                    
                    if (player.hasMetadata("immortal_invuln")) {
                        long endTime = player.getMetadata("immortal_invuln").get(0).asLong();
                        if (System.currentTimeMillis() > endTime) {
                            player.removeMetadata("immortal_invuln", plugin);
                            return;
                        }
                    } else {
                        return;
                    }
                }, 0L, 1L);
                
                // Auto-cancel after invulnerability period
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    task.cancel();
                    if (player.hasMetadata("immortal_invuln")) {
                        player.removeMetadata("immortal_invuln", plugin);
                    }
                }, invulnTicks);
                
                // Visual feedback
                player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation(), 30, 0.5, 1.0, 0.5, 0.2);
                player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
                player.sendMessage("§6§lIMMORTAL BUILD! §eYou cannot die for " + String.format("%.1f", invulnSeconds) + " seconds!");
                
                // Update cooldown
                lastImmortalUse.put(runId, now);
            }
        }
    }
    
    /**
     * Check if player is invulnerable from Immortal Build
     */
    public boolean isInvulnerable(Player player) {
        if (!player.hasMetadata("immortal_invuln")) {
            return false;
        }
        
        long endTime = player.getMetadata("immortal_invuln").get(0).asLong();
        if (System.currentTimeMillis() > endTime) {
            player.removeMetadata("immortal_invuln", plugin);
            return false;
        }
        
        return true;
    }
    
    private void applyRapidEscalation(Object run, double value) {
        // Damage multiplier is applied in getDamageMultiplier()
        // This is just a placeholder for any additional effects
    }
    
    private boolean shouldTriggerChainReaction(double value) {
        double chance = value * 20.0; // 20% per value point
        chance = Math.min(75.0, chance); // Cap at 75%
        return Math.random() * 100.0 < chance;
    }
    
    private void triggerChainReaction(Player player, Location location) {
        // Find nearest enemy and trigger a free weapon attack
        double radius = 20.0;
        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        
        for (Entity entity : location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            if (entity instanceof LivingEntity && !(entity instanceof Player) && !entity.isDead()) {
                double dist = location.distance(entity.getLocation());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = (LivingEntity) entity;
                }
            }
        }
        
        if (nearest != null) {
            // Trigger a free attack via WeaponManager
            // We'll need to expose a method for this
            Object run = plugin.getRunManager().getTeamRun(player);
            if (run == null) {
                run = plugin.getRunManager().getRun(player);
            }
            if (run != null && run instanceof TeamRun) {
                TeamRun teamRun = (TeamRun) run;
                if (teamRun.getWeapon() != null) {
                    plugin.getWeaponManager().attackWithWeapon(player, nearest, teamRun.getWeapon());
                }
            } else if (run instanceof Run) {
                Run singleRun = (Run) run;
                if (singleRun.getWeapon() != null) {
                    plugin.getWeaponManager().attackWithWeapon(player, nearest, singleRun.getWeapon());
                }
            }
            
            // Visual feedback
            player.getWorld().spawnParticle(Particle.CRIT, nearest.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);
            player.playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.3f, 2.0f);
        }
    }
    
    private void checkLuckyStreak(Object run, int kills, double value) {
        // Base kills needed is 20, reduced by value (higher value = fewer kills needed)
        // Value typically ranges from 1.0 to 3.0+, so:
        // value 1.0 = 20 kills needed
        // value 2.0 = 10 kills needed  
        // value 3.0 = ~7 kills needed
        int killsNeeded = (int) Math.max(5, 20 / value); // Minimum 5 kills, base 20 kills
        
        if (kills % killsNeeded == 0) {
            // Grant random power-up effect
            Player player = getFirstPlayer(run);
            if (player != null) {
                grantRandomPowerUpEffect(player, run);
            }
        }
    }
    
    private void grantRandomPowerUpEffect(Player player, Object run) {
        // Random stat boost (small temporary boost)
        String[] stats = {"damage", "speed", "crit_chance", "crit_damage", "armor"};
        String randomStat = stats[new Random().nextInt(stats.length)];
        double boost = 0.1 + (Math.random() * 0.2); // 10-30% boost
        
        // Apply as temporary metadata
        String metadataKey = "lucky_streak_" + randomStat;
        player.setMetadata(metadataKey, new FixedMetadataValue(plugin, boost));
        
        // Remove after 10 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.hasMetadata(metadataKey)) {
                player.removeMetadata(metadataKey, plugin);
            }
        }, 200L); // 10 seconds
        
        // Visual feedback
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation(), 20, 0.5, 1.0, 0.5, 0.1);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        player.sendMessage("§6§lLUCKY STREAK! §e+" + String.format("%.0f%%", boost * 100) + " " + randomStat + " for 10 seconds!");
    }
    
    // Helper methods
    private UUID getRunId(Object run) {
        if (run instanceof TeamRun) {
            TeamRun teamRun = (TeamRun) run;
            if (!teamRun.getPlayers().isEmpty()) {
                return teamRun.getPlayers().get(0).getUniqueId();
            }
        } else if (run instanceof Run) {
            return ((Run) run).getPlayerId();
        }
        return null;
    }
    
    private boolean isRunActive(Object run) {
        if (run instanceof TeamRun) {
            return ((TeamRun) run).isActive();
        } else if (run instanceof Run) {
            return ((Run) run).isActive();
        }
        return false;
    }
    
    private List<PowerUp> getActiveSynergies(Object run) {
        List<PowerUp> synergies = new ArrayList<>();
        
        if (run instanceof TeamRun) {
            TeamRun teamRun = (TeamRun) run;
            for (PowerUp powerUp : teamRun.getCollectedPowerUps()) {
                if (powerUp.getType() == PowerUp.PowerUpType.SYNERGY) {
                    synergies.add(powerUp);
                }
            }
        } else if (run instanceof Run) {
            Run singleRun = (Run) run;
            for (PowerUp powerUp : singleRun.getCollectedPowerUps()) {
                if (powerUp.getType() == PowerUp.PowerUpType.SYNERGY) {
                    synergies.add(powerUp);
                }
            }
        }
        
        return synergies;
    }
    
    private PowerUp getSynergyByName(Object run, String name) {
        for (PowerUp synergy : getActiveSynergies(run)) {
            if (synergy.getName().equals(name)) {
                return synergy;
            }
        }
        return null;
    }
    
    private Player getFirstPlayer(Object run) {
        if (run instanceof TeamRun) {
            TeamRun teamRun = (TeamRun) run;
            if (!teamRun.getPlayers().isEmpty()) {
                return teamRun.getPlayers().get(0);
            }
        } else if (run instanceof Run) {
            return Bukkit.getPlayer(((Run) run).getPlayerId());
        }
        return null;
    }
    
    /**
     * Cleanup all synergy tracking
     */
    public void cleanup() {
        killCounts.clear();
        lastImmortalUse.clear();
    }
}


