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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.Bukkit;

import java.util.UUID;

public class PlayerListener implements Listener {
    private final RoguecraftPlugin plugin;

    public PlayerListener(RoguecraftPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handle Totem of Undying - prevent death and revive player
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageForTotem(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        
        Player player = (Player) event.getEntity();
        
        // Check if player has an active run
        if (!plugin.getRunManager().hasActiveRun(player)) {
            return;
        }
        
        // Check for Totem of Undying gacha item
        TeamRun teamRun = plugin.getRunManager().getTeamRun(player);
        Run run = null;
        if (teamRun == null) {
            run = plugin.getRunManager().getRun(player);
        }
        
        com.eldor.roguecraft.models.GachaItem totemItem = null;
        if (teamRun != null) {
            for (com.eldor.roguecraft.models.GachaItem item : teamRun.getCollectedGachaItems(player)) {
                if (item.getId().equals("totem_of_undying")) {
                    totemItem = item;
                    break;
                }
            }
        } else if (run != null) {
            for (com.eldor.roguecraft.models.GachaItem item : run.getCollectedGachaItems()) {
                if (item.getId().equals("totem_of_undying")) {
                    totemItem = item;
                    break;
                }
            }
        }
        
        // If player has Totem of Undying and this would kill them, prevent death
        if (totemItem != null) {
            double finalDamage = event.getFinalDamage();
            double currentHealth = player.getHealth();
            
            if (currentHealth - finalDamage <= 0) {
                // Prevent death
                event.setCancelled(true);
                
                // Remove the totem item (one-time use)
                if (teamRun != null) {
                    teamRun.removeGachaItem(player, "totem_of_undying");
                } else if (run != null) {
                    run.removeGachaItem("totem_of_undying");
                }
                
                // Revive player to full health
                double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
                player.setHealth(maxHealth);
                player.setAbsorptionAmount(4.0); // Give absorption hearts like vanilla totem
                player.setFireTicks(0);
                
                // Schedule health set again after 1 tick to ensure it's fully applied
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && !player.isDead()) {
                        player.setHealth(maxHealth);
                    }
                });
                
                // Visual and audio effects
                player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, player.getLocation(), 30, 0.5, 1.0, 0.5, 0.2);
                player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
                
                // Message
                player.sendMessage("§6§lTOTEM OF UNDYING! §eYou have been revived! The run continues...");
            }
        }
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
    public void onPlayerMove(PlayerMoveEvent event) {
        // Movement is now allowed during GUI selection
        // Players can walk around, but weapons are frozen and enemies remain frozen
        // Shrine channeling movement blocking is handled separately in ShrineListener
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        
        // Check if player is in a team run
        TeamRun teamRun = plugin.getRunManager().getTeamRun(player);
        if (teamRun != null && teamRun.isActive()) {
            // Check if player was tagged by a TNT explosion from a team member
            if (player.hasMetadata("roguecraft_tnt_damaged")) {
                String ownerUuid = player.getMetadata("roguecraft_tnt_damaged").get(0).asString();
                UUID ownerId = java.util.UUID.fromString(ownerUuid);
                
                // Check if TNT belongs to this player or a team member
                if (ownerId.equals(player.getUniqueId()) || teamRun.getPlayerIds().contains(ownerId)) {
                    // Cancel velocity change from team member TNT explosion
                    event.setCancelled(true);
                    // Reset velocity to prevent any knockback
                    player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                }
            }
        } else {
            // Solo run - check if TNT belongs to this player
            if (player.hasMetadata("roguecraft_tnt_damaged")) {
                String ownerUuid = player.getMetadata("roguecraft_tnt_damaged").get(0).asString();
                UUID ownerId = java.util.UUID.fromString(ownerUuid);
                
                if (ownerId.equals(player.getUniqueId())) {
                    // Cancel velocity change from own TNT explosion
                    event.setCancelled(true);
                    player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Clean up team lobby invites/lobbies
        plugin.getTeamLobbyManager().onPlayerQuit(player);
        
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
        Player player = event.getPlayer();
        
        // Comprehensive cleanup on respawn to ensure player can interact normally
        // Stop weapon auto-attack
        plugin.getWeaponManager().stopAutoAttack(player);
        
        // Clean up any active shrine channeling/GUI tasks
        plugin.getShrineManager().cleanupPlayerChanneling(player);
        
        // Clear GUI queue
        plugin.getGuiManager().clearQueue(player.getUniqueId());
        
        // Reset player attributes to default
        plugin.getGameManager().resetPlayerAttributes(player);
        
        // Ensure player can interact with blocks and break them
        // Clear any metadata that might prevent interaction
        player.removeMetadata("roguecraft_in_run", plugin);
        player.removeMetadata("roguecraft_frozen", plugin);
        
        // Clean up all gacha item metadata
        plugin.getGameManager().cleanupGachaMetadata(player);
        
        // ProtocolLib: Clean up fake entities and boss bars
        if (plugin.getProtocolLibIntegration() != null && plugin.getProtocolLibIntegration().isEnabled()) {
            plugin.getProtocolLibIntegration().cleanupPlayer(player);
        }
        
        // Ensure run is fully ended (in case death handler didn't complete)
        if (plugin.getRunManager().hasActiveRun(player)) {
            Arena arena = plugin.getArenaManager().getDefaultArena();
            plugin.getGameManager().endRun(player.getUniqueId(), arena);
        }
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
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        // Prevent player damage to decoy villagers, but allow mob damage
        if (event.getEntity() instanceof org.bukkit.entity.Villager && event.getEntity().hasMetadata("roguecraft_decoy")) {
            // Only cancel if damage is from a player
            if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent) {
                org.bukkit.event.entity.EntityDamageByEntityEvent byEntityEvent = (org.bukkit.event.entity.EntityDamageByEntityEvent) event;
                if (byEntityEvent.getDamager() instanceof Player) {
                    event.setCancelled(true);
                    return;
                }
            }
            // Allow mob damage to pass through
        }
        
        // Prevent player damage to summons, but allow mob damage
        if (event.getEntity().hasMetadata("roguecraft_summon")) {
            // Only cancel if damage is from a player
            if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent) {
                org.bukkit.event.entity.EntityDamageByEntityEvent byEntityEvent = (org.bukkit.event.entity.EntityDamageByEntityEvent) event;
                if (byEntityEvent.getDamager() instanceof Player) {
                    event.setCancelled(true);
                    return;
                }
            }
            // Allow mob damage to pass through
        }
        
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
                    // Elite boss (Wither) gets very high resistance that scales with player level
                    if (isEliteBoss) {
                        // Get player level from the run
                        int playerLevel = 1;
                        TeamRun teamRun = null;
                        Run run = null;
                        
                        // Try to find the active run by checking nearby players
                        for (Player player : entity.getWorld().getPlayers()) {
                            if (entity.getLocation().distance(player.getLocation()) < 100) {
                                teamRun = plugin.getRunManager().getTeamRun(player);
                                if (teamRun != null && teamRun.isActive()) {
                                    playerLevel = teamRun.getLevel();
                                    break;
                                } else {
                                    run = plugin.getRunManager().getRun(player);
                                    if (run != null && run.isActive()) {
                                        playerLevel = run.getLevel();
                                        break;
                                    }
                                }
                            }
                        }
                        
                        // Base resistance: 50% (50% damage taken) - increased for harder difficulty
                        // Scale up resistance for overleveled players: +1% per level above 10
                        // Example: Level 10 = 50%, Level 20 = 60%, Level 30 = 70% (capped at 75%)
                        double baseResistance = 0.50; // 50% base (increased from 30%)
                        double levelScaling = 0.0;
                        
                        if (playerLevel > 10) {
                            // Add 1% resistance per level above 10 (increased from 0.5%)
                            levelScaling = (playerLevel - 10) * 0.01; // 1% per level
                        }
                        
                        double totalResistance = baseResistance + levelScaling;
                        totalResistance = Math.min(0.75, totalResistance); // Cap at 75% max (increased from 50%)
                        
                        // Apply resistance to damage
                        double originalDamage = event.getDamage();
                        double resistedDamage = originalDamage * (1.0 - totalResistance);
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
                        // Increased resistance for harder difficulty
                        // Wave 10-15: 10% resistance (increased from 5%)
                        // Wave 16-20: 15% resistance (increased from 10%)
                        // Wave 21: 20% resistance (increased from 15%)
                        // Infinite waves: Start at 20%, then scale +5.33% per wave beyond 20
                        // Wave 25 = 46.7%, Wave 30 = 73.3%, Wave 35 = 100% (invulnerability)
                        double resistancePercent = 0.0;
                        
                        // Get max wave from config
                        int maxWave = plugin.getConfigManager().getBalanceConfig().getInt("waves.max-wave", 20);
                        boolean isInfiniteWave = wave > maxWave;
                        
                        if (isInfiniteWave) {
                            // Infinite wave scaling: 20% base + 5.33% per wave beyond maxWave
                            // Reaches 100% resistance (impossible) at wave 35
                            // Wave 22 = 30.7%, Wave 25 = 46.7%, Wave 30 = 73.3%, Wave 35 = 100% (impossible)
                            int infiniteWaveNumber = wave - maxWave;
                            resistancePercent = 0.20 + (infiniteWaveNumber * 0.0533); // 5.33% per infinite wave (reaches 100% at wave 35)
                            resistancePercent = Math.min(1.0, resistancePercent); // Cap at 100% (invulnerability)
                        } else if (wave >= 21) {
                            // Wave 21 is the last regular wave
                            resistancePercent = 0.20; // 20% damage reduction (increased from 15%)
                        } else if (wave >= 16) {
                            resistancePercent = 0.15; // 15% damage reduction (increased from 10%)
                        } else {
                            resistancePercent = 0.10; // 10% damage reduction (increased from 5%)
                        }
                        
                        // Legendary mobs get additional resistance bonus on top of elite resistance
                        if (isLegendary) {
                            double legendaryResistanceBonus = plugin.getConfigManager().getBalanceConfig().getDouble("legendary.resistance-bonus", 0.20);
                            resistancePercent += legendaryResistanceBonus;
                            // Cap at 100% for infinite waves (allows invulnerability), 98% for regular waves
                            double maxResistance = isInfiniteWave ? 1.0 : 0.98;
                            resistancePercent = Math.min(maxResistance, resistancePercent);
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
            // MUST be done at HIGHEST priority to ensure it happens before death message is generated
            if (entity.getHealth() - event.getFinalDamage() <= 0) {
                try {
                    // Clear immediately and synchronously - this MUST happen before death message
                    if (entity.getCustomName() != null) {
                        entity.setCustomName(null);
                        entity.setCustomNameVisible(false);
                    }
                    // Also clear glowing to prevent any visual indicators
                    if (entity.isGlowing()) {
                        entity.setGlowing(false);
                    }
                    // Remove from scoreboard teams immediately
                    try {
                        org.bukkit.scoreboard.Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                        org.bukkit.scoreboard.Team legendaryTeam = scoreboard.getTeam("roguecraft_legendary_gold");
                        if (legendaryTeam != null && legendaryTeam.hasEntry(entity.getUniqueId().toString())) {
                            legendaryTeam.removeEntry(entity.getUniqueId().toString());
                        }
                        org.bukkit.scoreboard.Team decoyTeam = scoreboard.getTeam("roguecraft_decoy_purple");
                        if (decoyTeam != null && decoyTeam.hasEntry(entity.getUniqueId().toString())) {
                            decoyTeam.removeEntry(entity.getUniqueId().toString());
                        }
                    } catch (Exception e) {
                        // Ignore
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
            armor = teamRun.getStat(player, "armor");
        } else {
            run = plugin.getRunManager().getRun(player);
            if (run != null && run.isActive()) {
                armor = run.getStat("armor");
            } else {
                return; // Not in a run, don't modify damage
            }
        }
        
        // Cancel explosion damage from team member TNT
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            // Check if player was tagged by a TNT explosion from a team member
            if (player.hasMetadata("roguecraft_tnt_damaged")) {
                String ownerUuid = player.getMetadata("roguecraft_tnt_damaged").get(0).asString();
                UUID ownerId = java.util.UUID.fromString(ownerUuid);
                
                // Check if TNT belongs to this player
                if (ownerId.equals(player.getUniqueId())) {
                    event.setCancelled(true);
                    // Remove metadata after cancelling
                    player.removeMetadata("roguecraft_tnt_damaged", plugin);
                    return;
                }
                
                // Check if TNT belongs to a team member
                if (teamRun != null && teamRun.isActive()) {
                    if (teamRun.getPlayerIds().contains(ownerId)) {
                        event.setCancelled(true);
                        // Remove metadata after cancelling
                        player.removeMetadata("roguecraft_tnt_damaged", plugin);
                        return;
                    }
                }
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
        
        // Prevent damage from own TNT explosions or team member TNT
        if (event.getDamager() instanceof org.bukkit.entity.TNTPrimed) {
            org.bukkit.entity.TNTPrimed tnt = (org.bukkit.entity.TNTPrimed) event.getDamager();
            if (tnt.hasMetadata("roguecraft_tnt_owner")) {
                String ownerUuid = tnt.getMetadata("roguecraft_tnt_owner").get(0).asString();
                UUID ownerId = java.util.UUID.fromString(ownerUuid);
                
                // Check if TNT belongs to this player
                if (ownerId.equals(player.getUniqueId())) {
                    event.setCancelled(true);
                    return;
                }
                
                // Check if TNT belongs to a team member
                if (teamRun != null && teamRun.isActive()) {
                    if (teamRun.getPlayerIds().contains(ownerId)) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
        
        // Also check for explosion damage from TNT (check metadata set by explosion)
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            // Check if player was tagged by a TNT explosion from a team member
            if (player.hasMetadata("roguecraft_tnt_damaged")) {
                String ownerUuid = player.getMetadata("roguecraft_tnt_damaged").get(0).asString();
                UUID ownerId = java.util.UUID.fromString(ownerUuid);
                
                // Check if TNT belongs to this player
                if (ownerId.equals(player.getUniqueId())) {
                    event.setCancelled(true);
                    // Remove metadata after cancelling
                    player.removeMetadata("roguecraft_tnt_damaged", plugin);
                    return;
                }
                
                // Check if TNT belongs to a team member
                if (teamRun != null && teamRun.isActive()) {
                    if (teamRun.getPlayerIds().contains(ownerId)) {
                        event.setCancelled(true);
                        // Remove metadata after cancelling
                        player.removeMetadata("roguecraft_tnt_damaged", plugin);
                        return;
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
                
                // Cap skeleton damage to 50% of player's max HP to prevent one-shots
                if (attacker instanceof org.bukkit.entity.Skeleton || attacker instanceof org.bukkit.entity.WitherSkeleton || 
                    attacker instanceof org.bukkit.entity.Stray) {
                    // Get player's max HP
                    double playerMaxHP = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
                    double maxAllowedDamage = playerMaxHP * 0.5; // 50% of max HP
                    
                    // Cap the damage
                    if (scaledDamage > maxAllowedDamage) {
                        scaledDamage = maxAllowedDamage;
                    }
                }
                
                event.setDamage(scaledDamage);
            }
        }
    }
    
}



