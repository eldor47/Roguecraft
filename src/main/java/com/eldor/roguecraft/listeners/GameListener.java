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
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameListener implements Listener {
    private final RoguecraftPlugin plugin;

    public GameListener(RoguecraftPlugin plugin) {
        this.plugin = plugin;
    }

    private static final Random RANDOM = new Random();
    
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
        
        // Handle XP and experience
        if (teamRun != null && teamRun.isActive()) {
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
                    com.eldor.roguecraft.util.XPBar.updateXPBar(
                        player,
                        teamRun.getExperience(),
                        teamRun.getExperienceToNextLevel(),
                        teamRun.getLevel(),
                        teamRun.getWave()
                    );
                }
            }
        } else if (run != null && run.isActive()) {
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
            com.eldor.roguecraft.util.XPBar.updateXPBar(
                killer,
                run.getExperience(),
                run.getExperienceToNextLevel(),
                run.getLevel(),
                run.getWave()
            );
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
            dropCustomItems(entity, isElite, teamRun);
        } else if (run != null && run.isActive()) {
            dropCustomItems(entity, isElite, run);
        } else {
            dropCustomItems(entity, isElite, null);
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
    
    private void dropCustomItems(LivingEntity entity, boolean isElite, Object run) {
        org.bukkit.Location loc = entity.getLocation();
        
        // Get drop_rate stat multiplier
        double dropRate = 1.0;
        if (run instanceof com.eldor.roguecraft.models.TeamRun) {
            dropRate = ((com.eldor.roguecraft.models.TeamRun) run).getStat("drop_rate");
        } else if (run instanceof com.eldor.roguecraft.models.Run) {
            dropRate = ((com.eldor.roguecraft.models.Run) run).getStat("drop_rate");
        }
        
        // XP Token drop
        if (plugin.getConfigManager().getMainConfig().getBoolean("drops.xp-token.enabled", true)) {
            double xpChance = plugin.getConfigManager().getMainConfig().getDouble("drops.xp-token.base-chance", 0.05);
            if (isElite) {
                double eliteBonus = plugin.getConfigManager().getMainConfig().getDouble("drops.xp-token.elite-bonus", 0.15);
                xpChance += eliteBonus;
            }
            // Apply drop_rate multiplier
            xpChance *= dropRate;
            xpChance = Math.min(1.0, xpChance); // Cap at 100%
            
            if (RANDOM.nextDouble() < xpChance) {
                ItemStack xpToken = createXPToken();
                Item item = loc.getWorld().dropItem(loc, xpToken);
                item.setVelocity(new Vector(
                    (RANDOM.nextDouble() - 0.5) * 0.3,
                    0.2 + RANDOM.nextDouble() * 0.2,
                    (RANDOM.nextDouble() - 0.5) * 0.3
                ));
                item.setCustomName("XP_TOKEN");
                item.setCustomNameVisible(false);
                // Visual effect on drop
                loc.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, loc, 5, 0.3, 0.5, 0.3, 0.1);
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
        
        // Unique Power-Up drops (very rare drops - Movement Speed Boost and Time Freeze)
        if (plugin.getConfigManager().getMainConfig().getBoolean("drops.powerup.enabled", true)) {
            // Base chance from config (default 0.25%) - only increases with drop_rate stat
            double powerupChance = plugin.getConfigManager().getMainConfig().getDouble("drops.powerup.base-chance", 0.0025);
            // Apply drop_rate multiplier (no elite bonus, no other scaling)
            powerupChance *= dropRate;
            powerupChance = Math.min(1.0, powerupChance); // Cap at 100%
            
            if (RANDOM.nextDouble() < powerupChance && run != null) {
                // Choose between unique power-ups (50/50 chance)
                boolean isSpeedBoost = RANDOM.nextBoolean();
                String powerUpType = isSpeedBoost ? "SPEED_BOOST" : "TIME_FREEZE";
                
                ItemStack powerupItem = createUniquePowerUpItem(powerUpType);
                Item item = loc.getWorld().dropItem(loc, powerupItem);
                item.setVelocity(new Vector(
                    (RANDOM.nextDouble() - 0.5) * 0.3,
                    0.3 + RANDOM.nextDouble() * 0.3,
                    (RANDOM.nextDouble() - 0.5) * 0.3
                ));
                item.setCustomName("POWERUP_ITEM_" + powerUpType);
                item.setCustomNameVisible(false);
                
                // Unique visual and sound effects for power-up drop
                if (isSpeedBoost) {
                    loc.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, loc, 20, 0.5, 0.5, 0.5, 0.3);
                    loc.getWorld().spawnParticle(org.bukkit.Particle.CRIT, loc, 15, 0.5, 0.5, 0.5, 0.2);
                    loc.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_HORSE_GALLOP, 0.5f, 1.5f);
                } else {
                    loc.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, loc, 15, 0.5, 0.5, 0.5, 0.2);
                    loc.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, loc, 20, 0.5, 0.5, 0.5, 0.3);
                    loc.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    loc.getWorld().playSound(loc, org.bukkit.Sound.ITEM_TOTEM_USE, 0.5f, 1.2f);
                }
            }
        }
    }
    
    private ItemStack createXPToken() {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "✨ XP Token");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Pick up to gain bonus XP!");
        int xpAmount = plugin.getConfigManager().getMainConfig().getInt("drops.xp-token.xp-amount", 50);
        lore.add(ChatColor.GREEN + "+" + xpAmount + " XP");
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
        
        // Check for XP Token
        if (item.getCustomName() != null && item.getCustomName().equals("XP_TOKEN")) {
            event.setCancelled(true);
            item.remove();
            
            int xpAmount = plugin.getConfigManager().getMainConfig().getInt("drops.xp-token.xp-amount", 50);
            
            if (teamRun != null && teamRun.isActive()) {
                // Apply XP multiplier
                double multiplier = teamRun.getStat("xp_multiplier");
                int finalXp = (int) (xpAmount * multiplier);
                teamRun.addExperience(finalXp);
                
                // Update XP bar for all team members
                for (Player p : teamRun.getPlayers()) {
                    if (p != null && p.isOnline()) {
                        com.eldor.roguecraft.util.XPBar.updateXPBar(
                            p,
                            teamRun.getExperience(),
                            teamRun.getExperienceToNextLevel(),
                            teamRun.getLevel(),
                            teamRun.getWave()
                        );
                    }
                }
                player.sendMessage(ChatColor.GREEN + "✨ +" + finalXp + " XP from token!");
            } else if (run != null && run.isActive()) {
                // Apply XP multiplier
                double multiplier = run.getStat("xp_multiplier");
                int finalXp = (int) (xpAmount * multiplier);
                run.addExperience(finalXp);
                
                com.eldor.roguecraft.util.XPBar.updateXPBar(
                    player,
                    run.getExperience(),
                    run.getExperienceToNextLevel(),
                    run.getLevel(),
                    run.getWave()
                );
                player.sendMessage(ChatColor.GREEN + "✨ +" + finalXp + " XP from token!");
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
            item.remove();
            
            String powerUpType = item.getCustomName().replace("POWERUP_ITEM_", "");
            
            if (powerUpType.equals("SPEED_BOOST")) {
                // Apply Movement Speed Boost (temporary speed increase)
                applySpeedBoost(player, teamRun, run);
            } else if (powerUpType.equals("TIME_FREEZE")) {
                // Apply Time Freeze (freeze all mobs temporarily)
                applyTimeFreeze(player, teamRun, run);
            }
            
            return;
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
        } else { // TIME_FREEZE
            item = new ItemStack(Material.CLOCK);
            name = "Time Freeze";
            description = "Freeze all enemies for 8 seconds";
            color = ChatColor.LIGHT_PURPLE;
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
}
