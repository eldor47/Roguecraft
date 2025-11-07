package com.eldor.roguecraft.managers;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.PowerUp;
import com.eldor.roguecraft.models.Run;
import com.eldor.roguecraft.models.TeamRun;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class AuraManager implements Listener {
    private final RoguecraftPlugin plugin;
    private final Map<UUID, BukkitTask> auraTasks; // Track aura tasks per team/player
    
    public AuraManager(RoguecraftPlugin plugin) {
        this.plugin = plugin;
        this.auraTasks = new HashMap<>();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    /**
     * Start aura effects for a run
     */
    public void startAuras(Object run) {
        UUID runId = getRunId(run);
        if (runId == null) return;
        
        // Cancel existing task if any
        stopAuras(runId);
        
        // Start periodic aura task (every 20 ticks = 1 second)
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isRunActive(run)) {
                stopAuras(runId);
                return;
            }
            
            // Process all active auras
            processAuras(run);
        }, 20L, 20L); // Every second
        
        auraTasks.put(runId, task);
    }
    
    /**
     * Stop aura effects for a run
     */
    public void stopAuras(UUID runId) {
        BukkitTask task = auraTasks.remove(runId);
        if (task != null) {
            task.cancel();
        }
    }
    
    /**
     * Process all active auras for a run
     */
    private void processAuras(Object run) {
        List<Player> players = getPlayers(run);
        if (players.isEmpty()) return;
        
        for (Player player : players) {
            if (player == null || !player.isOnline() || player.isDead()) continue;
            
            // Skip if player is in GUI (pause auras while GUI is open)
            if (plugin.getGuiManager().isPlayerInGUI(player.getUniqueId()) || 
                plugin.getShrineManager().isPlayerInShrineGUI(player.getUniqueId())) {
                continue;
            }
            
            // Also check TeamRun's GUI tracking
            if (run instanceof com.eldor.roguecraft.models.TeamRun) {
                com.eldor.roguecraft.models.TeamRun teamRun = (com.eldor.roguecraft.models.TeamRun) run;
                if (teamRun.isPlayerInGUI(player.getUniqueId())) {
                    continue;
                }
            }
            
            List<PowerUp> auras = getActiveAuras(run);
            
            for (PowerUp aura : auras) {
                String auraName = aura.getName();
                double value = aura.getValue();
                
                switch (auraName) {
                    case "Regeneration Aura":
                        applyRegenerationAura(player, value);
                        break;
                    case "Fire Aura":
                        applyFireAura(player, value);
                        break;
                    case "Ice Aura":
                        applyIceAura(player, value);
                        break;
                    case "Lightning Aura":
                        applyLightningAura(player, value);
                        break;
                    case "Poison Aura":
                        applyPoisonAura(player, value);
                        break;
                    case "Shield Aura":
                        // Shield is handled on damage event
                        break;
                }
            }
        }
    }
    
    /**
     * Apply Thorns Aura - reflect damage to attackers
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        
        Player player = (Player) event.getEntity();
        Object run = plugin.getRunManager().getTeamRun(player);
        if (run == null) {
            run = plugin.getRunManager().getRun(player);
        }
        if (run == null || !isRunActive(run)) return;
        
        // Check for Thorns Aura
        PowerUp thornsAura = getAuraByName(run, "Thorns Aura");
        if (thornsAura != null && event.getDamager() instanceof LivingEntity) {
            double reflectPercent = thornsAura.getValue() * 10.0; // 10% per value point
            reflectPercent = Math.min(50.0, reflectPercent); // Cap at 50%
            
            double reflectDamage = event.getFinalDamage() * (reflectPercent / 100.0);
            LivingEntity attacker = (LivingEntity) event.getDamager();
            
            // Reflect damage
            attacker.damage(reflectDamage, player);
            
            // Visual feedback
            player.getWorld().spawnParticle(Particle.CRIT, attacker.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);
            player.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 0.3f, 1.5f);
        }
        
        // Check for Shield Aura - absorb damage
        PowerUp shieldAura = getAuraByName(run, "Shield Aura");
        if (shieldAura != null) {
            double shieldAmount = shieldAura.getValue() * 5.0; // 5 HP per value point
            
            // Check if player has shield metadata
            if (player.hasMetadata("shield_remaining")) {
                double remaining = player.getMetadata("shield_remaining").get(0).asDouble();
                if (remaining > 0) {
                    double damage = event.getFinalDamage();
                    double absorbed = Math.min(remaining, damage);
                    double newRemaining = remaining - absorbed;
                    
                    if (newRemaining > 0) {
                        player.setMetadata("shield_remaining", new FixedMetadataValue(plugin, newRemaining));
                        event.setDamage(event.getDamage() - absorbed);
                        
                        // Visual feedback
                        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation(), 5, 0.5, 1.0, 0.5, 0.1);
                    } else {
                        player.removeMetadata("shield_remaining", plugin);
                        event.setDamage(event.getDamage() - remaining);
                    }
                }
            } else {
                // Initialize shield
                player.setMetadata("shield_remaining", new FixedMetadataValue(plugin, shieldAmount));
                double damage = event.getFinalDamage();
                double absorbed = Math.min(shieldAmount, damage);
                event.setDamage(event.getDamage() - absorbed);
                
                // Visual feedback
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation(), 10, 0.5, 1.0, 0.5, 0.1);
            }
        }
    }
    
    private void applyRegenerationAura(Player player, double value) {
        // Heal every 5 seconds (check if 5 seconds passed)
        if (!player.hasMetadata("regen_aura_last")) {
            player.setMetadata("regen_aura_last", new FixedMetadataValue(plugin, System.currentTimeMillis()));
            return;
        }
        
        long lastHeal = player.getMetadata("regen_aura_last").get(0).asLong();
        long now = System.currentTimeMillis();
        
        if (now - lastHeal >= 5000) { // 5 seconds
            double healAmount = value * 0.5; // 0.5 HP per value point
            double currentHealth = player.getHealth();
            double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            double newHealth = Math.min(maxHealth, currentHealth + healAmount);
            
            player.setHealth(newHealth);
            player.setMetadata("regen_aura_last", new FixedMetadataValue(plugin, now));
            
            // Visual feedback
            player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0);
        }
    }
    
    private void applyFireAura(Player player, double value) {
        double radius = 8.0; // 8 block radius
        double damagePerSecond = value * 2.0; // 2 damage per value point per second
        
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                LivingEntity mob = (LivingEntity) entity;
                
                // Set fire ticks (20 ticks = 1 second, so damagePerSecond = 20 ticks)
                mob.setFireTicks(Math.max(mob.getFireTicks(), 20));
                
                // Apply direct damage
                mob.damage(damagePerSecond);
                
                // Visual feedback
                player.getWorld().spawnParticle(Particle.FLAME, mob.getLocation(), 5, 0.3, 0.5, 0.3, 0.05);
            }
        }
    }
    
    private void applyIceAura(Player player, double value) {
        double radius = 8.0;
        double slowPercent = value * 15.0; // 15% slow per value point
        slowPercent = Math.min(60.0, slowPercent); // Cap at 60% slow
        
        int slowLevel = (int) (slowPercent / 20.0); // Convert to potion level
        slowLevel = Math.min(4, slowLevel); // Max level 4
        
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                LivingEntity mob = (LivingEntity) entity;
                mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, slowLevel, false, false));
                
                // Visual feedback
                player.getWorld().spawnParticle(Particle.SNOWFLAKE, mob.getLocation(), 5, 0.3, 0.5, 0.3, 0.05);
            }
        }
    }
    
    private void applyLightningAura(Player player, double value) {
        // Chain lightning every 3 seconds
        if (!player.hasMetadata("lightning_aura_last")) {
            player.setMetadata("lightning_aura_last", new FixedMetadataValue(plugin, System.currentTimeMillis()));
            return;
        }
        
        long lastLightning = player.getMetadata("lightning_aura_last").get(0).asLong();
        long now = System.currentTimeMillis();
        
        if (now - lastLightning >= 3000) { // 3 seconds
            double radius = 10.0;
            double damage = value * 3.0; // 3 damage per value point
            
            LivingEntity nearest = null;
            double nearestDist = Double.MAX_VALUE;
            
            for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    double dist = player.getLocation().distance(entity.getLocation());
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearest = (LivingEntity) entity;
                    }
                }
            }
            
            if (nearest != null) {
                // Strike lightning
                player.getWorld().strikeLightningEffect(nearest.getLocation());
                nearest.damage(damage);
                
                // Visual feedback
                player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, nearest.getLocation(), 20, 0.5, 1.0, 0.5, 0.1);
                player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.3f, 1.0f);
                
                player.setMetadata("lightning_aura_last", new FixedMetadataValue(plugin, now));
            }
        }
    }
    
    private void applyPoisonAura(Player player, double value) {
        double radius = 8.0;
        double damagePerSecond = value; // 1 damage per value point per second
        
        int poisonLevel = (int) Math.min(4, value / 2.0); // Scale poison level
        
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                LivingEntity mob = (LivingEntity) entity;
                
                // Apply poison effect
                mob.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, poisonLevel, false, false));
                
                // Apply direct damage
                mob.damage(damagePerSecond);
                
                // Visual feedback - use a simpler particle that doesn't require extra data
                player.getWorld().spawnParticle(Particle.SMOKE, mob.getLocation(), 5, 0.3, 0.5, 0.3, 0.05);
            }
        }
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
    
    private List<Player> getPlayers(Object run) {
        if (run instanceof TeamRun) {
            return new ArrayList<>(((TeamRun) run).getPlayers());
        } else if (run instanceof Run) {
            Player player = Bukkit.getPlayer(((Run) run).getPlayerId());
            return player != null ? Collections.singletonList(player) : Collections.emptyList();
        }
        return Collections.emptyList();
    }
    
    private List<PowerUp> getActiveAuras(Object run) {
        List<PowerUp> auras = new ArrayList<>();
        
        if (run instanceof TeamRun) {
            TeamRun teamRun = (TeamRun) run;
            for (PowerUp powerUp : teamRun.getCollectedPowerUps()) {
                if (powerUp.getType() == PowerUp.PowerUpType.AURA) {
                    auras.add(powerUp);
                }
            }
        } else if (run instanceof Run) {
            Run singleRun = (Run) run;
            for (PowerUp powerUp : singleRun.getCollectedPowerUps()) {
                if (powerUp.getType() == PowerUp.PowerUpType.AURA) {
                    auras.add(powerUp);
                }
            }
        }
        
        return auras;
    }
    
    private PowerUp getAuraByName(Object run, String name) {
        for (PowerUp aura : getActiveAuras(run)) {
            if (aura.getName().equals(name)) {
                return aura;
            }
        }
        return null;
    }
    
    /**
     * Cleanup all aura tasks
     */
    public void cleanup() {
        for (BukkitTask task : auraTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        auraTasks.clear();
    }
}

