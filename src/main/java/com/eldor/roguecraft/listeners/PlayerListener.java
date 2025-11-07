package com.eldor.roguecraft.listeners;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.Arena;
import com.eldor.roguecraft.models.Run;
import com.eldor.roguecraft.models.TeamRun;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerListener implements Listener {
    private final RoguecraftPlugin plugin;

    public PlayerListener(RoguecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        
        // Clean up any active shrine channeling/GUI tasks for this player
        plugin.getShrineManager().cleanupPlayerChanneling(player);
        
        // Check if player has an active run (solo or team)
        if (plugin.getRunManager().hasActiveRun(player)) {
            // End the run
            Arena arena = plugin.getArenaManager().getDefaultArena();
            plugin.getGameManager().endRun(player.getUniqueId(), arena);
            
            // Stop weapon auto-attack
            plugin.getWeaponManager().stopAutoAttack(player);
            
            player.sendMessage("§cYou died! Run ended.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Clean up any active shrine channeling/GUI tasks for this player
        plugin.getShrineManager().cleanupPlayerChanneling(player);
        
        // Check if player has an active run (solo or team)
        if (plugin.getRunManager().hasActiveRun(player)) {
            // End the run
            Arena arena = plugin.getArenaManager().getDefaultArena();
            plugin.getGameManager().endRun(player.getUniqueId(), arena);
            
            // Stop weapon auto-attack
            plugin.getWeaponManager().stopAutoAttack(player);
        }
    }
    
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // Ensure weapon is fully stopped after respawn
        plugin.getWeaponManager().stopAutoAttack(event.getPlayer());
    }
    
    /**
     * Disable vanilla health regeneration during runs (only allow custom regeneration stat)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        
        // Check if player is in an active run
        com.eldor.roguecraft.models.TeamRun teamRun = plugin.getRunManager().getTeamRun(player);
        com.eldor.roguecraft.models.Run run = null;
        boolean inRun = false;
        
        if (teamRun != null && teamRun.isActive()) {
            inRun = true;
        } else {
            run = plugin.getRunManager().getRun(player);
            if (run != null && run.isActive()) {
                inRun = true;
            }
        }
        
        // If player is in a run, disable vanilla regeneration (SATIATED, EATING, etc.)
        // Only allow REGEN effect (potion) or CUSTOM (from our system)
        if (inRun) {
            if (event.getRegainReason() == org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.SATIATED ||
                event.getRegainReason() == org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.EATING ||
                event.getRegainReason() == org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.REGEN) {
                // Cancel vanilla regeneration (SATIATED, EATING, REGEN)
                // But allow REGEN potion effect if it's a custom effect
                // We'll check if it's from a potion - if so, allow it
                if (event.getRegainReason() == org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.REGEN) {
                    // Check if player has regeneration potion effect
                    if (!player.hasPotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION)) {
                        event.setCancelled(true);
                    }
                } else {
                    // Cancel SATIATED and EATING regeneration
                    event.setCancelled(true);
                }
            }
        }
    }
    
    /**
     * Prevent hunger from decreasing during active runs
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        
        // Check if player is in an active run
        com.eldor.roguecraft.models.TeamRun teamRun = plugin.getRunManager().getTeamRun(player);
        com.eldor.roguecraft.models.Run run = null;
        boolean inRun = false;
        
        if (teamRun != null && teamRun.isActive()) {
            inRun = true;
        } else {
            run = plugin.getRunManager().getRun(player);
            if (run != null && run.isActive()) {
                inRun = true;
            }
        }
        
        // If player is in a run, maintain hunger at max (if enabled in config)
        if (inRun && plugin.getConfigManager().getMainConfig().getBoolean("game.disable-hunger", true)) {
            // If hunger is decreasing, prevent it
            if (event.getFoodLevel() < player.getFoodLevel()) {
                event.setCancelled(true);
                // Keep hunger and saturation at max
                player.setFoodLevel(20);
                player.setSaturation(20.0f);
                player.setExhaustion(0.0f);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        // Prevent sunlight damage to plugin-spawned undead mobs
        if (event.getEntity() instanceof LivingEntity && !(event.getEntity() instanceof Player)) {
            LivingEntity entity = (LivingEntity) event.getEntity();
            
            // Check if it's a plugin-spawned undead mob
            if (entity.hasMetadata("roguecraft_mob")) {
                // Check if damage is from fire (sunlight damage)
                if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FIRE_TICK ||
                    event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FIRE) {
                    // Check if it's day time (sunlight)
                    long worldTime = entity.getWorld().getTime();
                    if (worldTime >= 0 && worldTime < 13000) { // Day time (0-13000 ticks)
                        // Cancel sunlight/fire damage
                        event.setCancelled(true);
                        // Also clear fire ticks
                        entity.setFireTicks(0);
                        return; // Don't process further
                    }
                }
                
                // Apply elite/legendary damage resistance based on wave number (replaces armor system)
                boolean isLegendary = entity.hasMetadata("is_legendary");
                boolean isElite = entity.hasMetadata("roguecraft_elite") || entity.hasMetadata("is_elite");
                boolean isEliteBoss = entity.hasMetadata("roguecraft_elite_boss");
                
                if (isElite || isEliteBoss || isLegendary) {
                    // Elite boss (Wither) gets very high resistance (90%)
                    if (isEliteBoss) {
                        double originalDamage = event.getDamage();
                        double resistedDamage = originalDamage * 0.10; // 90% damage reduction
                        event.setDamage(resistedDamage);
                        return; // Skip normal elite resistance calculation
                    }
                    
                    // Get the wave number from the run
                    int wave = 1;
                    TeamRun teamRun = null;
                    Run run = null;
                    
                    // Try to find the active run by checking nearby players
                    for (Player player : entity.getWorld().getPlayers()) {
                        if (entity.getLocation().distance(player.getLocation()) < 100) {
                            teamRun = plugin.getRunManager().getTeamRun(player);
                            if (teamRun != null && teamRun.isActive()) {
                                wave = teamRun.getWave();
                                break;
                            } else {
                                run = plugin.getRunManager().getRun(player);
                                if (run != null && run.isActive()) {
                                    wave = run.getWave();
                                    break;
                                }
                            }
                        }
                    }
                    
                    // Only apply resistance for wave 10+ (when armor would have been applied)
                    if (wave >= 10) {
                        // Calculate resistance based on wave
                        // Wave 10-15: 10% resistance (like Iron armor)
                        // Wave 16-20: 20% resistance (like Diamond armor)
                        // Wave 21+: Start at 30%, then scale continuously for infinite waves
                        // Infinite waves: +3% resistance per wave beyond 20 (60% at wave 30, caps at 98% max)
                        double resistancePercent = 0.0;
                        
                        // Get max wave from config
                        int maxWave = plugin.getConfigManager().getBalanceConfig().getInt("waves.max-wave", 20);
                        boolean isInfiniteWave = wave > maxWave;
                        
                        if (isInfiniteWave) {
                            // Infinite wave scaling: 30% base + 3% per wave beyond maxWave
                            // Wave 30 = 60% resistance, caps at 98% max
                            int infiniteWaveNumber = wave - maxWave;
                            resistancePercent = 0.30 + (infiniteWaveNumber * 0.03); // 3% per infinite wave
                            resistancePercent = Math.min(0.98, resistancePercent); // Cap at 98% max
                        } else if (wave >= 21) {
                            // Wave 21 is the last regular wave
                            resistancePercent = 0.30; // 30% damage reduction
                        } else if (wave >= 16) {
                            resistancePercent = 0.20; // 20% damage reduction
                        } else {
                            resistancePercent = 0.10; // 10% damage reduction
                        }
                        
                        // Legendary mobs get additional resistance bonus on top of elite resistance
                        if (isLegendary) {
                            double legendaryResistanceBonus = plugin.getConfigManager().getBalanceConfig().getDouble("legendary.resistance-bonus", 0.20);
                            resistancePercent += legendaryResistanceBonus;
                            resistancePercent = Math.min(0.98, resistancePercent); // Cap at 98% max
                        }
                        
                        // Apply resistance to damage
                        double originalDamage = event.getDamage();
                        double resistedDamage = originalDamage * (1.0 - resistancePercent);
                        event.setDamage(resistedDamage);
                    }
                }
            }
            
            // Clear custom name when entity is about to die (health drops to 0 or below)
            // This prevents death logs from appearing for named entities
            if (entity.getHealth() - event.getFinalDamage() <= 0) {
                try {
                    if (entity.getCustomName() != null) {
                        entity.setCustomName(null);
                        entity.setCustomNameVisible(false);
                    }
                } catch (Exception e) {
                    // Ignore - entity might be invalid
                }
            }
        }
        
        // Apply defense stat to reduce incoming damage for players
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        
        // CANCEL all damage if player is in GUI (prevents damage from projectiles shot before freeze)
        if (plugin.getGuiManager().isPlayerInGUI(player.getUniqueId()) || 
            plugin.getShrineManager().isPlayerInShrineGUI(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        
        // Also check TeamRun's GUI tracking
        TeamRun teamRun = plugin.getRunManager().getTeamRun(player);
        if (teamRun != null && teamRun.isPlayerInGUI(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        
        // Check if player has an active run
        Run run = null;
        double armor = 0.0;
        
        if (teamRun != null && teamRun.isActive()) {
            armor = teamRun.getStat("armor");
        } else {
            run = plugin.getRunManager().getRun(player);
            if (run != null && run.isActive()) {
                armor = run.getStat("armor");
            } else {
                return; // Not in a run, don't modify damage
            }
        }
        
        // Armor reduces damage: 1 armor = 1% damage reduction (capped at 75%)
        // Note: Armor attribute is also set for visual display in HUD
        if (armor > 0) {
            double damageReduction = Math.min(0.75, armor / 100.0); // Max 75% reduction
            double originalDamage = event.getDamage();
            double newDamage = originalDamage * (1.0 - damageReduction);
            event.setDamage(newDamage);
        }
        
        // Track last damage time for regeneration proc system
        if (event.getFinalDamage() > 0) {
            plugin.getGameManager().setLastDamageTime(player.getUniqueId(), System.currentTimeMillis());
        }
    }
    
    /**
     * Prevent players from being damaged by their own arrows and TNT
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        
        // Check if player is in an active run
        TeamRun teamRun = plugin.getRunManager().getTeamRun(player);
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
            return; // Not in a run, don't modify
        }
        
        // Prevent damage from own arrows
        if (event.getDamager() instanceof org.bukkit.entity.Arrow) {
            org.bukkit.entity.Arrow arrow = (org.bukkit.entity.Arrow) event.getDamager();
            if (arrow.getShooter() == player) {
                event.setCancelled(true);
                return;
            }
        }
        
        // Prevent damage from own TNT explosions
        if (event.getDamager() instanceof org.bukkit.entity.TNTPrimed) {
            org.bukkit.entity.TNTPrimed tnt = (org.bukkit.entity.TNTPrimed) event.getDamager();
            if (tnt.hasMetadata("roguecraft_tnt_owner")) {
                String ownerUuid = tnt.getMetadata("roguecraft_tnt_owner").get(0).asString();
                if (ownerUuid.equals(player.getUniqueId().toString())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        
        // Also check for explosion damage from TNT (EntityExplodeEvent might cause damage)
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            // Check if there's a nearby TNT that belongs to this player
            for (org.bukkit.entity.Entity nearby : player.getNearbyEntities(20, 20, 20)) {
                if (nearby instanceof org.bukkit.entity.TNTPrimed) {
                    org.bukkit.entity.TNTPrimed tnt = (org.bukkit.entity.TNTPrimed) nearby;
                    if (tnt.hasMetadata("roguecraft_tnt_owner")) {
                        String ownerUuid = tnt.getMetadata("roguecraft_tnt_owner").get(0).asString();
                        if (ownerUuid.equals(player.getUniqueId().toString())) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
            }
        }
        
        // Apply mob damage scaling based on wave and difficulty
        if (event.getDamager() instanceof LivingEntity && !(event.getDamager() instanceof Player)) {
            LivingEntity attacker = (LivingEntity) event.getDamager();
            
            // Check if it's a plugin-spawned mob
            if (attacker.hasMetadata("roguecraft_mob")) {
                double difficultyMultiplier = 1.0;
                int wave = 1;
                
                if (teamRun != null && teamRun.isActive()) {
                    difficultyMultiplier = teamRun.getDifficultyMultiplier();
                    wave = teamRun.getWave();
                } else if (run != null && run.isActive()) {
                    difficultyMultiplier = run.getDifficultyMultiplier();
                    wave = run.getWave();
                }
                
                // Get damage multiplier from DifficultyManager
                double mobDamageMultiplier = plugin.getDifficultyManager().getMobDamageMultiplier(difficultyMultiplier);
                
                // Apply wave-based damage scaling (additional 2% per wave)
                double waveDamageMultiplier = 1.0 + (wave * 0.02);
                
                // Check if it's a boss (Wither) - bosses get level-based damage scaling
                boolean isBoss = attacker.hasMetadata("roguecraft_boss") || attacker.hasMetadata("roguecraft_elite_boss");
                boolean isLegendary = attacker.hasMetadata("is_legendary");
                boolean isElite = attacker.hasMetadata("roguecraft_elite") || attacker.hasMetadata("is_elite");
                
                double eliteDamageMultiplier = 1.0;
                double levelDamageMultiplier = 1.0;
                
                if (isBoss) {
                    // Boss damage scales with player level (5% per level)
                    int playerLevel = 1;
                    if (teamRun != null && teamRun.isActive()) {
                        playerLevel = teamRun.getLevel();
                    } else if (run != null && run.isActive()) {
                        playerLevel = run.getLevel();
                    }
                    // Boss damage increases by 5% per player level (level 10 = 1.5x, level 20 = 2.0x, etc.)
                    levelDamageMultiplier = 1.0 + (playerLevel * 0.05);
                    
                    // Boss also gets elite damage multiplier
                    double baseEliteMultiplier = plugin.getConfigManager().getBalanceConfig().getDouble("elites.damage-multiplier", 1.5);
                    eliteDamageMultiplier = baseEliteMultiplier;
                } else if (isLegendary) {
                    // Legendary mobs get elite damage multiplier + legendary multiplier
                    double baseEliteMultiplier = plugin.getConfigManager().getBalanceConfig().getDouble("elites.damage-multiplier", 1.75);
                    double legendaryMultiplier = plugin.getConfigManager().getBalanceConfig().getDouble("legendary.damage-multiplier", 1.5);
                    
                    // Get max wave from config
                    int maxWave = plugin.getConfigManager().getBalanceConfig().getInt("waves.max-wave", 20);
                    boolean isInfiniteWave = wave > maxWave;
                    
                    if (isInfiniteWave) {
                        // Infinite wave scaling: base multiplier + 0.1x per infinite wave (scales continuously)
                        int infiniteWaveNumber = wave - maxWave;
                        double scaledEliteMultiplier = baseEliteMultiplier + (infiniteWaveNumber * 0.1);
                        eliteDamageMultiplier = scaledEliteMultiplier * legendaryMultiplier;
                    } else {
                        eliteDamageMultiplier = baseEliteMultiplier * legendaryMultiplier;
                    }
                } else if (isElite) {
                    // Base elite damage multiplier from config
                    double baseEliteMultiplier = plugin.getConfigManager().getBalanceConfig().getDouble("elites.damage-multiplier", 1.75);
                    
                    // Get max wave from config
                    int maxWave = plugin.getConfigManager().getBalanceConfig().getInt("waves.max-wave", 20);
                    boolean isInfiniteWave = wave > maxWave;
                    
                    if (isInfiniteWave) {
                        // Infinite wave scaling: base multiplier + 0.1x per infinite wave (scales continuously)
                        // Example: Wave 21 = 1.6x, Wave 30 = 2.5x, Wave 50 = 4.5x, etc.
                        int infiniteWaveNumber = wave - maxWave;
                        eliteDamageMultiplier = baseEliteMultiplier + (infiniteWaveNumber * 0.1);
                        // No cap - let it scale infinitely to ensure players eventually die
                    } else {
                        eliteDamageMultiplier = baseEliteMultiplier;
                    }
                }
                
                // Apply all multipliers
                double originalDamage = event.getDamage();
                double scaledDamage = originalDamage * mobDamageMultiplier * waveDamageMultiplier * eliteDamageMultiplier * levelDamageMultiplier;
                event.setDamage(scaledDamage);
            }
        }
    }
}



