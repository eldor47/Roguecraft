package com.eldor.roguecraft.listeners;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.GachaChest;
import com.eldor.roguecraft.models.GachaItem;
import com.eldor.roguecraft.models.Run;
import com.eldor.roguecraft.models.TeamRun;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.UUID;

/**
 * Handles gacha chest interactions
 */
public class ChestListener implements Listener {
    private final RoguecraftPlugin plugin;
    
    public ChestListener(RoguecraftPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Check if player is in a run
        TeamRun teamRun = plugin.getRunManager().getTeamRun(player.getUniqueId());
        Run run = plugin.getRunManager().getRun(player.getUniqueId());
        
        if ((teamRun == null || !teamRun.isActive()) && (run == null || !run.isActive())) {
            return;
        }
        
        // Get team ID
        UUID teamId = null;
        if (teamRun != null) {
            teamId = getTeamRunId(teamRun);
        } else if (run != null) {
            teamId = run.getPlayerId();
        }
        
        if (teamId == null) {
            return;
        }
        
        // Check if clicked block is a chest
        if (event.getClickedBlock() == null || 
            event.getClickedBlock().getType() != org.bukkit.Material.CHEST) {
            return;
        }
        
        // Check if player clicked on a gacha chest
        GachaChest chest = plugin.getChestManager().getChestNearPlayer(teamId, player);
        if (chest == null) {
            // Debug: log if chest not found
            plugin.getLogger().fine("[Chest] No gacha chest found near player " + player.getName() + " at " + event.getClickedBlock().getLocation());
            return;
        }
        
        // Check if chest is at the clicked location (use block coordinates for comparison)
        org.bukkit.Location clickedLoc = event.getClickedBlock().getLocation();
        org.bukkit.Location chestLoc = chest.getLocation();
        
        // Compare block coordinates (ignore Y precision issues)
        if (clickedLoc.getBlockX() != chestLoc.getBlockX() ||
            clickedLoc.getBlockY() != chestLoc.getBlockY() ||
            clickedLoc.getBlockZ() != chestLoc.getBlockZ()) {
            return;
        }
        
        event.setCancelled(true);
        
        // Check if chest is already opened
        if (chest.isOpened()) {
            player.sendMessage(ChatColor.RED + "This chest has already been opened!");
            return;
        }
        
        // Check currency (unless chest is free from legendary mob)
        if (!chest.isFree()) {
            int chestCost = 50;
            if (teamRun != null) {
                chestCost = teamRun.getChestCost();
            } else if (run != null) {
                chestCost = run.getChestCost();
            }
            
            int currentGold = 0;
            if (teamRun != null) {
                currentGold = teamRun.getCurrentGold();
            } else if (run != null) {
                currentGold = run.getCurrentGold();
            }
            
            if (currentGold < chestCost) {
                player.sendMessage(ChatColor.RED + "Not enough gold! You need " + ChatColor.GOLD + chestCost + 
                    ChatColor.RED + " gold (you have " + ChatColor.GOLD + currentGold + ChatColor.RED + ").");
                return;
            }
            
            // Deduct gold and increase cost
            if (teamRun != null) {
                teamRun.spendGold(chestCost);
                teamRun.increaseChestCost();
            } else if (run != null) {
                run.spendGold(chestCost);
                run.increaseChestCost();
            }
            
            player.sendMessage(ChatColor.GOLD + "Spent " + chestCost + " gold to open chest. Next chest costs " + 
                (teamRun != null ? teamRun.getChestCost() : run.getChestCost()) + " gold.");
            
            // Update boss bar with new gold amount
            if (teamRun != null) {
                for (Player p : teamRun.getPlayers()) {
                    if (p != null && p.isOnline()) {
                        com.eldor.roguecraft.util.XPBar.updateXPBarWithGold(
                            p,
                            teamRun.getExperience(),
                            teamRun.getExperienceToNextLevel(),
                            teamRun.getLevel(),
                            teamRun.getWave(),
                            teamRun.getCurrentGold()
                        );
                    }
                }
            } else if (run != null) {
                com.eldor.roguecraft.util.XPBar.updateXPBarWithGold(
                    player,
                    run.getExperience(),
                    run.getExperienceToNextLevel(),
                    run.getLevel(),
                    run.getWave(),
                    run.getCurrentGold()
                );
            }
        } else {
            // This shouldn't happen anymore since legendary chests cost gold
            // But keep this for backwards compatibility
        }
        
        // Get player's luck stat
        double luck = 1.0;
        if (teamRun != null) {
            luck = teamRun.getStat("luck");
        } else if (run != null) {
            luck = run.getStat("luck");
        }
        
        // Perform gacha roll with luck stat
        GachaItem item = plugin.getGachaManager().roll(luck);
        
        // Mark chest as opened
        chest.setOpened(true);
        
        // Remove chest immediately (before opening GUI)
        chest.remove();
        
        // Open gacha roll GUI (this will freeze the game)
        if (teamRun != null) {
            plugin.getGuiManager().openGachaRollGUI(player, item, luck, teamRun);
        } else if (run != null) {
            plugin.getGuiManager().openGachaRollGUI(player, item, luck, run);
        }
        
        // Apply item effect after a short delay (to ensure GUI is open)
        // We'll apply it when GUI closes, but store it for now
        final GachaItem finalItem = item;
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Apply item effect
            applyItemEffect(player, finalItem, teamRun, run);
        }, 5L); // Small delay to ensure GUI is open
    }
    
    /**
     * Apply the effect of a gacha item to the player
     */
    private void applyItemEffect(Player player, GachaItem item, TeamRun teamRun, Run run) {
        Object runObj = teamRun != null ? teamRun : run;
        if (runObj == null) {
            return;
        }
        
        switch (item.getEffect()) {
            case STAT_BOOST:
                applyStatBoost(player, item, runObj);
                break;
            case ON_HIT_EFFECT:
                // Store item ID in player metadata for on-hit effects
                player.setMetadata("gacha_item_" + item.getId(), 
                    new org.bukkit.metadata.FixedMetadataValue(plugin, item));
                break;
            case ON_KILL_EFFECT:
                // Store item ID in player metadata for on-kill effects
                player.setMetadata("gacha_item_" + item.getId(), 
                    new org.bukkit.metadata.FixedMetadataValue(plugin, item));
                break;
            case PASSIVE_EFFECT:
                applyPassiveEffect(player, item, runObj);
                break;
            case SPECIAL_ABILITY:
                // Store item ID in player metadata for special abilities
                player.setMetadata("gacha_item_" + item.getId(), 
                    new org.bukkit.metadata.FixedMetadataValue(plugin, item));
                break;
        }
    }
    
    /**
     * Apply stat boost from item
     */
    private void applyStatBoost(Player player, GachaItem item, Object run) {
        String itemId = item.getId();
        double value = item.getValue();
        
        // Determine which stat to boost based on item ID
        if (itemId.contains("clover") || itemId.contains("luck")) {
            addStat(run, "luck", value);
        } else if (itemId.contains("time_bracelet") || itemId.contains("xp")) {
            addStat(run, "xp_multiplier", value);
        } else if (itemId.contains("gym_sauce") || itemId.contains("damage")) {
            addStat(run, "damage", value);
        } else if (itemId.contains("oats") || itemId.contains("hp") || itemId.contains("health")) {
            addStat(run, "health", value);
            // Apply health immediately via attributes
            if (run instanceof TeamRun) {
                TeamRun tr = (TeamRun) run;
                for (Player p : tr.getPlayers()) {
                    if (p != null && p.isOnline()) {
                        double health = tr.getStat("health");
                        org.bukkit.attribute.Attribute healthAttr = org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH;
                        org.bukkit.attribute.AttributeInstance healthInstance = p.getAttribute(healthAttr);
                        if (healthInstance != null) {
                            healthInstance.setBaseValue(health);
                            p.setHealth(Math.min(health, p.getHealth()));
                        }
                    }
                }
            } else if (run instanceof Run) {
                Run r = (Run) run;
                double health = r.getStat("health");
                org.bukkit.attribute.Attribute healthAttr = org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH;
                org.bukkit.attribute.AttributeInstance healthInstance = player.getAttribute(healthAttr);
                if (healthInstance != null) {
                    healthInstance.setBaseValue(health);
                    player.setHealth(Math.min(health, player.getHealth()));
                }
            }
        } else if (itemId.contains("turbo_socks") || itemId.contains("speed")) {
            addStat(run, "speed", value);
            // Apply speed immediately via attributes
            if (run instanceof TeamRun) {
                TeamRun tr = (TeamRun) run;
                for (Player p : tr.getPlayers()) {
                    if (p != null && p.isOnline()) {
                        double speed = tr.getStat("speed");
                        double baseSpeed = 0.1;
                        double newSpeed = Math.max(0.0, Math.min(1.0, baseSpeed * speed));
                        org.bukkit.attribute.Attribute speedAttr = org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED;
                        org.bukkit.attribute.AttributeInstance speedInstance = p.getAttribute(speedAttr);
                        if (speedInstance != null) {
                            speedInstance.setBaseValue(newSpeed);
                        }
                    }
                }
            } else if (run instanceof Run) {
                Run r = (Run) run;
                double speed = r.getStat("speed");
                double baseSpeed = 0.1;
                double newSpeed = Math.max(0.0, Math.min(1.0, baseSpeed * speed));
                org.bukkit.attribute.Attribute speedAttr = org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED;
                org.bukkit.attribute.AttributeInstance speedInstance = player.getAttribute(speedAttr);
                if (speedInstance != null) {
                    speedInstance.setBaseValue(newSpeed);
                }
            }
        } else if (itemId.contains("battery") || itemId.contains("attack_speed")) {
            // Attack speed affects weapon - store with consistent key
            player.setMetadata("gacha_attack_speed_battery", 
                new org.bukkit.metadata.FixedMetadataValue(plugin, value));
        } else if (itemId.contains("forbidden_juice") || itemId.contains("crit")) {
            addStat(run, "crit_chance", value);
        }
    }
    
    /**
     * Apply passive effect from item
     */
    private void applyPassiveEffect(Player player, GachaItem item, Object run) {
        String itemId = item.getId();
        double value = item.getValue();
        
        if (itemId.contains("medkit") || itemId.contains("regen")) {
            addStat(run, "regeneration", value);
        } else if (itemId.contains("golden_glove")) {
            // Store for currency multiplier (to be implemented)
            player.setMetadata("gacha_gold_multiplier", 
                new org.bukkit.metadata.FixedMetadataValue(plugin, value));
        }
    }
    
    /**
     * Helper to add stat value
     */
    private void addStat(Object run, String statKey, double value) {
        if (run instanceof TeamRun) {
            TeamRun tr = (TeamRun) run;
            tr.addStat(statKey, value);
        } else if (run instanceof Run) {
            Run r = (Run) run;
            r.addStat(statKey, value);
        }
    }
    
    /**
     * Get team run ID (use first player's UUID)
     */
    private UUID getTeamRunId(TeamRun teamRun) {
        if (teamRun == null || teamRun.getPlayers().isEmpty()) {
            return null;
        }
        return teamRun.getPlayers().get(0).getUniqueId();
    }
    
    /**
     * Start the rolling animation for a gacha roll
     */
    private void startRollingAnimation(Player player, GachaChest chest, GachaItem finalItem, double luck, TeamRun teamRun, Run run) {
        // Position on top of chest (centered on top face)
        final org.bukkit.Location chestLoc = chest.getLocation().add(0.5, 1.0, 0.5);
        
        // Spawn item frame to show item icons (on top of chest)
        final org.bukkit.entity.ItemFrame[] itemFrameRef = new org.bukkit.entity.ItemFrame[1];
        try {
            // Place item frame on top face of chest
            final org.bukkit.block.BlockFace face = org.bukkit.block.BlockFace.UP;
            final org.bukkit.Location frameLoc = chestLoc.clone().add(0, 0.1, 0);
            
            // Spawn item frame
            itemFrameRef[0] = chestLoc.getWorld().spawn(frameLoc, org.bukkit.entity.ItemFrame.class, (frame) -> {
                frame.setFacingDirection(face);
                frame.setFixed(true);
                frame.setInvulnerable(true);
                frame.setVisible(false);
                frame.setGlowing(true); // Make item frame glow
            });
        } catch (Exception e) {
            plugin.getLogger().warning("[Chest] Could not spawn item frame: " + e.getMessage());
        }
        
        // Spawn text display (ArmorStand) for rolling text (on top of chest, fixed position)
        final org.bukkit.entity.ArmorStand textDisplay = chestLoc.getWorld().spawn(chestLoc.clone().add(0, 0.5, 0), 
            org.bukkit.entity.ArmorStand.class, (stand) -> {
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setMarker(true);
                stand.setSmall(true);
                stand.setInvulnerable(true);
                stand.setCollidable(false);
                stand.setCustomNameVisible(true);
                stand.setGlowing(false); // Don't glow to avoid health display issues
                // Set initial name immediately to prevent "ARMOR STAND" from showing
                stand.setCustomName(ChatColor.GRAY + "Rolling...");
                // Prevent health display system from updating this
                stand.setMetadata("roguecraft_gacha_roll", 
                    new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                stand.setMetadata("roguecraft_xp_display", 
                    new org.bukkit.metadata.FixedMetadataValue(plugin, true)); // Also mark as XP display to skip health updates
                // Set health to 0 to prevent health bar from showing
                stand.setHealth(0.0);
            });
        
        // Get all items for rolling animation
        java.util.List<GachaItem> allItems = new java.util.ArrayList<>(plugin.getGachaManager().getAllItems());
        java.util.Random random = new java.util.Random();
        
        // Animation variables
        final int[] rollCount = {0};
        final int totalRolls = 20; // Fast rolls
        final int slowRolls = 5; // Slower final rolls
        
        // Play opening sound
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        
        // Start glowing particle effect around chest
        final org.bukkit.scheduler.BukkitTask[] particleTaskRef = new org.bukkit.scheduler.BukkitTask[1];
        particleTaskRef[0] = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (chest.isOpened() && !chest.isActive()) {
                // Stop particles when chest is removed
                if (particleTaskRef[0] != null) {
                    particleTaskRef[0].cancel();
                }
                return;
            }
            
            // Spawn glowing particles around the chest
            org.bukkit.Location particleLoc = chest.getLocation().add(0.5, 0.5, 0.5);
            
            // Create a glowing aura with particles in a circle around the chest
            for (int i = 0; i < 8; i++) {
                double angle = (i * Math.PI * 2) / 8;
                double radius = 0.8 + (Math.random() * 0.3); // Slight variation
                double x = particleLoc.getX() + Math.cos(angle) * radius;
                double y = particleLoc.getY() + (Math.random() * 0.5);
                double z = particleLoc.getZ() + Math.sin(angle) * radius;
                
                org.bukkit.Location glowLoc = new org.bukkit.Location(particleLoc.getWorld(), x, y, z);
                // Use golden/yellow glowing particles
                org.bukkit.Color glowColor = Math.random() < 0.5 ? 
                    org.bukkit.Color.fromRGB(255, 215, 0) : // Gold
                    org.bukkit.Color.fromRGB(255, 255, 100); // Bright yellow
                particleLoc.getWorld().spawnParticle(org.bukkit.Particle.DUST, glowLoc, 1, 
                    new org.bukkit.Particle.DustOptions(glowColor, 1.5f));
            }
            
            // Also spawn some particles above the chest
            org.bukkit.Location topLoc = particleLoc.clone().add(0, 0.5, 0);
            for (int i = 0; i < 3; i++) {
                double offsetX = (Math.random() - 0.5) * 0.6;
                double offsetZ = (Math.random() - 0.5) * 0.6;
                org.bukkit.Location auraLoc = topLoc.clone().add(offsetX, 0, offsetZ);
                topLoc.getWorld().spawnParticle(org.bukkit.Particle.DUST, auraLoc, 1, 
                    new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255, 215, 0), 1.8f));
            }
        }, 0L, 3L); // Every 3 ticks (0.15 seconds) for smooth glow
        
        // Start rolling animation - fast phase
        org.bukkit.scheduler.BukkitTask[] rollTaskRef = new org.bukkit.scheduler.BukkitTask[1];
        rollTaskRef[0] = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (rollCount[0] < totalRolls) {
                // Fast rolling - show random items
                GachaItem randomItem = allItems.get(random.nextInt(allItems.size()));
                updateRollDisplay(textDisplay, itemFrameRef[0], randomItem, false);
                
                // Play rolling sound
                if (rollCount[0] % 3 == 0) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.5f + (rollCount[0] * 0.1f));
                }
                
                rollCount[0]++;
            } else if (rollCount[0] < totalRolls + slowRolls) {
                // Slow rolling - gradually slow down
                GachaItem randomItem = allItems.get(random.nextInt(allItems.size()));
                updateRollDisplay(textDisplay, itemFrameRef[0], randomItem, false);
                
                // Play slower rolling sound
                if (rollCount[0] % 2 == 0) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
                }
                
                rollCount[0]++;
            } else {
                // Cancel fast task and show final item
                if (rollTaskRef[0] != null) {
                    rollTaskRef[0].cancel();
                }
                
                // Show final item
                updateRollDisplay(textDisplay, itemFrameRef[0], finalItem, true);
                
                // Final sounds and effects
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.2f);
                
                // Spawn particles
                chestLoc.getWorld().spawnParticle(
                    org.bukkit.Particle.TOTEM_OF_UNDYING,
                    chestLoc,
                    50,
                    0.5, 0.5, 0.5,
                    0.1
                );
                
                // Rarity-specific particles
                String rarityColor = finalItem.getRarity().getColorCode();
                org.bukkit.Particle particle = org.bukkit.Particle.HAPPY_VILLAGER;
                if (finalItem.getRarity() == GachaItem.ItemRarity.LEGENDARY) {
                    particle = org.bukkit.Particle.END_ROD;
                } else if (finalItem.getRarity() == GachaItem.ItemRarity.RARE) {
                    particle = org.bukkit.Particle.ENCHANT;
                }
                
                chestLoc.getWorld().spawnParticle(
                    particle,
                    chestLoc,
                    30,
                    0.5, 0.5, 0.5,
                    0.05
                );
                
                // Track collected item
                if (teamRun != null) {
                    teamRun.addGachaItem(finalItem);
                } else if (run != null) {
                    run.addGachaItem(finalItem);
                }
                
                // Apply item effect
                applyItemEffect(player, finalItem, teamRun, run);
                
                // Send final message
                String message = rarityColor + "✨ " + ChatColor.BOLD + finalItem.getName() + ChatColor.RESET + " " + rarityColor + "✨";
                if (luck > 1.0) {
                    message += ChatColor.GRAY + " (Luck: " + ChatColor.GREEN + String.format("%.1fx", luck) + ChatColor.GRAY + ")";
                }
                player.sendMessage(message);
                player.sendMessage(ChatColor.GRAY + finalItem.getDescription());
                
                        // Keep display for 3 seconds, then remove
                        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            // Stop particle effect
                            if (particleTaskRef[0] != null && !particleTaskRef[0].isCancelled()) {
                                particleTaskRef[0].cancel();
                            }
                            if (textDisplay != null && !textDisplay.isDead()) {
                                textDisplay.remove();
                            }
                            if (itemFrameRef[0] != null && !itemFrameRef[0].isDead()) {
                                itemFrameRef[0].remove();
                            }
                        }, 60L); // 3 seconds
            }
        }, 0L, 2L); // Fast ticks (every 2 ticks = 0.1 seconds)
        
        // Switch to slow phase after fast rolls
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (rollTaskRef[0] != null && !rollTaskRef[0].isCancelled()) {
                rollTaskRef[0].cancel();
                // Start slow roll phase
                rollTaskRef[0] = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (rollCount[0] < totalRolls + slowRolls) {
                        GachaItem randomItem = allItems.get(random.nextInt(allItems.size()));
                        updateRollDisplay(textDisplay, itemFrameRef[0], randomItem, false);
                        
                        if (rollCount[0] % 2 == 0) {
                            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
                        }
                        
                        rollCount[0]++;
                    } else {
                        // Show final item
                        if (rollTaskRef[0] != null) {
                            rollTaskRef[0].cancel();
                        }
                        
                        updateRollDisplay(textDisplay, itemFrameRef[0], finalItem, true);
                        
                        // Final sounds and effects
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.2f);
                        
                        // Spawn particles
                        chestLoc.getWorld().spawnParticle(
                            org.bukkit.Particle.TOTEM_OF_UNDYING,
                            chestLoc,
                            50,
                            0.5, 0.5, 0.5,
                            0.1
                        );
                        
                        // Rarity-specific particles
                        String rarityColor = finalItem.getRarity().getColorCode();
                        org.bukkit.Particle particle = org.bukkit.Particle.HAPPY_VILLAGER;
                        if (finalItem.getRarity() == GachaItem.ItemRarity.LEGENDARY) {
                            particle = org.bukkit.Particle.END_ROD;
                        } else if (finalItem.getRarity() == GachaItem.ItemRarity.RARE) {
                            particle = org.bukkit.Particle.ENCHANT;
                        }
                        
                        chestLoc.getWorld().spawnParticle(
                            particle,
                            chestLoc,
                            30,
                            0.5, 0.5, 0.5,
                            0.05
                        );
                        
                        // Track collected item
                        if (teamRun != null) {
                            teamRun.addGachaItem(finalItem);
                        } else if (run != null) {
                            run.addGachaItem(finalItem);
                        }
                        
                        // Apply item effect
                        applyItemEffect(player, finalItem, teamRun, run);
                        
                        // Send final message
                        player.sendMessage("");
                        player.sendMessage(rarityColor + "╔════════════════════════════════╗");
                        player.sendMessage(rarityColor + "║   " + ChatColor.BOLD + "GACHA ROLL!" + rarityColor + "   ║");
                        player.sendMessage(rarityColor + "╠════════════════════════════════╣");
                        if (luck > 1.0) {
                            player.sendMessage(rarityColor + "║ " + ChatColor.GRAY + "Luck: " + ChatColor.GREEN + String.format("%.1fx", luck) + rarityColor + " ║");
                        }
                        player.sendMessage(rarityColor + "║ " + ChatColor.WHITE + finalItem.getName() + " " + rarityColor + "║");
                        player.sendMessage(rarityColor + "║ " + ChatColor.GRAY + finalItem.getDescription() + rarityColor + " ║");
                        player.sendMessage(rarityColor + "╚════════════════════════════════╝");
                        player.sendMessage("");
                        
                        // Keep display for 3 seconds, then remove
                        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            // Stop particle effect
                            if (particleTaskRef[0] != null && !particleTaskRef[0].isCancelled()) {
                                particleTaskRef[0].cancel();
                            }
                            if (textDisplay != null && !textDisplay.isDead()) {
                                textDisplay.remove();
                            }
                            if (itemFrameRef[0] != null && !itemFrameRef[0].isDead()) {
                                itemFrameRef[0].remove();
                            }
                        }, 60L); // 3 seconds
                    }
                }, 0L, 5L); // Slower ticks (every 5 ticks = 0.25 seconds)
            }
        }, totalRolls * 2L);
    }
    
    /**
     * Update the roll display with an item
     */
    private void updateRollDisplay(org.bukkit.entity.ArmorStand textDisplay, org.bukkit.entity.ItemFrame itemFrame, 
                                   GachaItem item, boolean isFinal) {
        String rarityColor = item.getRarity().getColorCode();
        String displayText;
        
        if (isFinal) {
            displayText = rarityColor + "" + ChatColor.BOLD + "✨ " + item.getName() + " ✨\n" +
                         ChatColor.GRAY + item.getDescription();
        } else {
            displayText = ChatColor.GRAY + "Rolling... " + rarityColor + item.getName();
        }
        
        if (textDisplay != null && !textDisplay.isDead()) {
            textDisplay.setCustomName(displayText);
        }
        
        // Update item frame with item icon
        if (itemFrame != null && !itemFrame.isDead()) {
            org.bukkit.inventory.ItemStack itemStack = new org.bukkit.inventory.ItemStack(item.getIcon());
            org.bukkit.inventory.meta.ItemMeta meta = itemStack.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(rarityColor + item.getName());
                itemStack.setItemMeta(meta);
            }
            itemFrame.setItem(itemStack);
        }
    }
}

