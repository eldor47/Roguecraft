package com.eldor.roguecraft.managers;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.Arena;
import com.eldor.roguecraft.models.Shrine;
import com.eldor.roguecraft.models.TeamRun;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages physical shrines in the arena
 */
public class ShrineManager {
    private final RoguecraftPlugin plugin;
    private final Map<UUID, List<Shrine>> arenaShrines; // TeamRun ID -> Shrines
    private final java.util.concurrent.ConcurrentHashMap<UUID, ShrineChanneling> activeChanneling; // Player ID -> Channeling info (thread-safe)
    private final Map<UUID, Map<Shrine.ShrineType, Long>> playerCooldowns; // Player ID -> Shrine Type -> Last use time
    private final Map<UUID, Long> recentlyUsed; // Player ID -> Last GUI close time (prevents immediate re-channel)
    private final Set<UUID> playersInShrineGUI; // Players currently viewing shrine GUI
    
    public ShrineManager(RoguecraftPlugin plugin) {
        this.plugin = plugin;
        this.arenaShrines = new HashMap<>();
        this.activeChanneling = new java.util.concurrent.ConcurrentHashMap<>(); // Thread-safe map
        this.playerCooldowns = new HashMap<>();
        this.recentlyUsed = new HashMap<>();
        this.playersInShrineGUI = java.util.concurrent.ConcurrentHashMap.newKeySet(); // Thread-safe set
    }
    
    /**
     * Spawn shrines for a team run
     */
    public void spawnShrinesForRun(UUID teamId, Arena arena) {
        // Remove any existing shrines first (safety check to prevent duplicates)
        removeShrinesForRun(teamId);
        
        List<Shrine> shrines = new ArrayList<>();
        Random random = new Random();
        double minDistance = 8.0; // Minimum distance between shrines and chests (8 blocks)
        
        // Get existing chests to check spacing
        List<com.eldor.roguecraft.models.GachaChest> existingChests = plugin.getChestManager().getChestsForRun(teamId);
        
        // Spawn 5-7 difficulty shrines
        int difficultyCount = 5 + random.nextInt(3); // 5, 6, or 7 shrines
        int difficultyAttempts = 0;
        int maxDifficultyAttempts = difficultyCount * 10;
        while (shrines.size() < difficultyCount && difficultyAttempts < maxDifficultyAttempts) {
            difficultyAttempts++;
            Location spawnLoc = getRandomShrineLocation(arena);
            if (spawnLoc != null && isLocationValid(spawnLoc, shrines, existingChests, minDistance)) {
                Shrine shrine = new Shrine(spawnLoc, Shrine.ShrineType.DIFFICULTY);
                shrine.build();
                shrine.setActive(true);
                shrines.add(shrine);
                plugin.getLogger().info("[Shrine] Spawned " + shrine.getType().getName() + " at " + spawnLoc);
            }
        }
        
        // Spawn 2-3 boss shrines
        int bossCount = 2 + random.nextInt(2); // 2 or 3 shrines
        int bossAttempts = 0;
        int maxBossAttempts = bossCount * 10;
        while (shrines.size() < (difficultyCount + bossCount) && bossAttempts < maxBossAttempts) {
            bossAttempts++;
            Location spawnLoc = getRandomShrineLocation(arena);
            if (spawnLoc != null && isLocationValid(spawnLoc, shrines, existingChests, minDistance)) {
                Shrine shrine = new Shrine(spawnLoc, Shrine.ShrineType.BOSS);
                shrine.build();
                shrine.setActive(true);
                shrines.add(shrine);
                plugin.getLogger().info("[Shrine] Spawned " + shrine.getType().getName() + " at " + spawnLoc);
            }
        }
        
        // Spawn 6-8 regular power shrines (the ones that use channeling)
        int powerCount = 6 + random.nextInt(3); // 6, 7, or 8 shrines
        int powerAttempts = 0;
        int maxPowerAttempts = powerCount * 10;
        while (shrines.size() < (difficultyCount + bossCount + powerCount) && powerAttempts < maxPowerAttempts) {
            powerAttempts++;
            Location spawnLoc = getRandomShrineLocation(arena);
            if (spawnLoc != null && isLocationValid(spawnLoc, shrines, existingChests, minDistance)) {
                Shrine shrine = new Shrine(spawnLoc, Shrine.ShrineType.POWER);
                shrine.build();
                shrine.setActive(true);
                shrines.add(shrine);
                plugin.getLogger().info("[Shrine] Spawned " + shrine.getType().getName() + " at " + spawnLoc);
            }
        }
        
        arenaShrines.put(teamId, shrines);
        plugin.getLogger().info("[Shrine] Spawned " + shrines.size() + " shrines for team " + teamId + " (" + difficultyCount + " difficulty, " + bossCount + " boss, " + powerCount + " power)");
    }
    
    /**
     * Check if a location is valid (not too close to other shrines or chests)
     */
    private boolean isLocationValid(Location loc, List<Shrine> existingShrines, List<com.eldor.roguecraft.models.GachaChest> existingChests, double minDistance) {
        // Check distance from existing shrines
        for (Shrine shrine : existingShrines) {
            if (loc.distance(shrine.getLocation()) < minDistance) {
                return false;
            }
        }
        
        // Check distance from existing chests
        if (existingChests != null) {
            for (com.eldor.roguecraft.models.GachaChest chest : existingChests) {
                if (loc.distance(chest.getLocation()) < minDistance) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Remove all shrines for a run
     */
    public void removeShrinesForRun(UUID teamId) {
        List<Shrine> shrines = arenaShrines.remove(teamId);
        if (shrines != null && !shrines.isEmpty()) {
            plugin.getLogger().info("[Shrine] Removing " + shrines.size() + " shrines for team " + teamId);
            for (Shrine shrine : shrines) {
                if (shrine != null) {
                    try {
                        shrine.remove();
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Shrine] Error removing shrine: " + e.getMessage());
                    }
                }
            }
            shrines.clear(); // Clear the list after removal
        } else {
            // Also check if there are any shrines still in the map (cleanup any orphaned shrines)
            List<Shrine> allShrines = arenaShrines.get(teamId);
            if (allShrines != null && !allShrines.isEmpty()) {
                plugin.getLogger().warning("[Shrine] Found orphaned shrines for team " + teamId + ", removing them");
                for (Shrine shrine : allShrines) {
                    if (shrine != null) {
                        try {
                            shrine.remove();
                        } catch (Exception e) {
                            plugin.getLogger().warning("[Shrine] Error removing orphaned shrine: " + e.getMessage());
                        }
                    }
                }
                arenaShrines.remove(teamId);
            }
        }
        
        // Final verification - ensure the list is cleared
        arenaShrines.remove(teamId);
    }
    
    /**
     * Get shrine near a player (within 3 blocks)
     * Only returns shrines that haven't been used yet
     */
    public Shrine getShrineNearPlayer(UUID teamId, Player player) {
        List<Shrine> shrines = arenaShrines.get(teamId);
        if (shrines == null) {
            plugin.getLogger().fine("[Shrine] No shrines found for team " + teamId);
            return null;
        }
        
        Location playerLoc = player.getLocation();
        for (Shrine shrine : shrines) {
            // Only return active shrines that haven't been used
            if (!shrine.isActive() || shrine.hasBeenUsed()) {
                continue;
            }
            
            Location shrineLoc = shrine.getLocation();
            double distance = playerLoc.distance(shrineLoc);
            
            if (distance <= 3.0) {
                plugin.getLogger().fine("[Shrine] Found shrine near " + player.getName() + 
                    " at distance " + String.format("%.2f", distance) + "m");
                return shrine;
            }
        }
        
        return null;
    }
    
    /**
     * Get all shrines for a run
     */
    public List<Shrine> getShrinesForRun(UUID teamId) {
        return new ArrayList<>(arenaShrines.getOrDefault(teamId, new ArrayList<>()));
    }
    
    /**
     * Get shrine at a specific location (checks if location is part of shrine blocks)
     */
    public Shrine getShrineAtLocation(UUID teamId, Location location) {
        List<Shrine> shrines = arenaShrines.get(teamId);
        if (shrines == null) {
            return null;
        }
        
        for (Shrine shrine : shrines) {
            Location shrineLoc = shrine.getLocation();
            
            // Difficulty shrines: check exact block location (dark block) or skull block (1 block above)
            if (shrine.getType() == Shrine.ShrineType.DIFFICULTY) {
                int dx = location.getBlockX() - shrineLoc.getBlockX();
                int dy = location.getBlockY() - shrineLoc.getBlockY();
                int dz = location.getBlockZ() - shrineLoc.getBlockZ();
                // Check if clicked the dark block (y=0) or skull block (y=1)
                if (dx == 0 && dz == 0 && (dy == 0 || dy == 1)) {
                    return shrine;
                }
                continue;
            }
            
            // Other shrines: Check if location is within shrine area (3x3 base + pillar)
            int dx = Math.abs(location.getBlockX() - shrineLoc.getBlockX());
            int dz = Math.abs(location.getBlockZ() - shrineLoc.getBlockZ());
            int dy = location.getBlockY() - shrineLoc.getBlockY();
            
            // Check if within 3x3 base area (x/z within 1 block, y within 0-3 blocks)
            if (dx <= 1 && dz <= 1 && dy >= 0 && dy <= 3) {
                return shrine;
            }
        }
        
        return null;
    }
    
    /**
     * Start channeling a shrine
     */
    public void startChanneling(Player player, Shrine shrine, UUID teamId) {
        UUID playerId = player.getUniqueId();
        
        // Check if player is in shrine GUI
        if (playersInShrineGUI.contains(playerId)) {
            plugin.getLogger().fine("[Shrine] Cannot start channeling: Player " + player.getName() + " is in shrine GUI");
            return;
        }
        
        // Check if player already channeling - THIS IS CRITICAL TO PREVENT DUPLICATES
        if (activeChanneling.containsKey(playerId)) {
            plugin.getLogger().fine("[Shrine] Cannot start channeling: Player " + player.getName() + " is already channeling");
            return;
        }
        
        // Check if player just used a shrine (within last 3 seconds - increased)
        Long lastUsed = recentlyUsed.get(playerId);
        if (lastUsed != null && System.currentTimeMillis() - lastUsed < 3000L) {
            plugin.getLogger().fine("[Shrine] Cannot start channeling: Player " + player.getName() + " recently used a shrine");
            return; // Silently prevent immediate re-channeling
        }
        
        // Check personal cooldown
        if (isPlayerOnCooldown(playerId, shrine.getType())) {
            plugin.getLogger().fine("[Shrine] Cannot start channeling: Player " + player.getName() + " is on cooldown for " + shrine.getType());
            return; // Silently return if on cooldown
        }
        
        // CRITICAL: Double-check one more time AFTER logging (race condition protection)
        if (activeChanneling.containsKey(playerId)) {
            plugin.getLogger().warning("[Shrine] Race condition detected! Player " + player.getName() + " already has active channeling. Skipping duplicate start.");
            return;
        }
        
        player.sendMessage(ChatColor.GOLD + "Channeling " + shrine.getType().getName() + "...");
        plugin.getLogger().info("[Shrine] Player " + player.getName() + " started channeling shrine " + shrine.getType().getName() + " at " + shrine.getLocation());
        
        // DON'T freeze during channeling - only freeze when GUI opens
        // Player can still attack and enemies can move during channeling
        
        // Create channeling object and ADD TO MAP IMMEDIATELY to prevent duplicates
        ShrineChanneling channeling = new ShrineChanneling(player, shrine, teamId);
        
        // Use putIfAbsent for atomic check-and-add (thread-safe)
        ShrineChanneling existing = activeChanneling.putIfAbsent(playerId, channeling);
        if (existing != null) {
            plugin.getLogger().warning("[Shrine] Concurrent channeling detected! Player " + player.getName() + " already has active channeling. Cancelling duplicate.");
            return;
        }
        
        plugin.getLogger().info("[Shrine] Channeling task created. Active channeling count: " + activeChanneling.size());
        
        // Use AtomicBoolean to track if task is cancelled/completed
        final java.util.concurrent.atomic.AtomicBoolean isCancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        
        // Start progress task
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks = 0;
            final int requiredTicks = 20 * 2; // 2 seconds
            final UUID taskPlayerId = playerId;
            final Shrine taskShrine = shrine;
            final UUID taskTeamId = teamId;
            final Location shrineLoc = shrine.getLocation().clone(); // Capture shrine location
            
            @Override
            public void run() {
                // Check if this task was cancelled
                if (isCancelled.get()) {
                    return;
                }
                
                // Double-check: Make sure we're still in the map (another task might have completed)
                if (!activeChanneling.containsKey(taskPlayerId)) {
                    plugin.getLogger().fine("[Shrine] Task cancelled - no longer in activeChanneling map");
                    isCancelled.set(true);
                    try {
                        BukkitTask currentTask = (BukkitTask) this;
                        if (currentTask != null) currentTask.cancel();
                    } catch (Exception e) {
                        // Ignore
                    }
                    return;
                }
                
                Player taskPlayer = Bukkit.getPlayer(taskPlayerId);
                if (taskPlayer == null || !taskPlayer.isOnline() || taskPlayer.isDead()) {
                    plugin.getLogger().info("[Shrine] Channeling interrupted: Player went offline or died");
                    activeChanneling.remove(taskPlayerId);
                    isCancelled.set(true);
                    try {
                        BukkitTask currentTask = (BukkitTask) this;
                        if (currentTask != null) currentTask.cancel();
                    } catch (Exception e) {
                        // Ignore
                    }
                    return;
                }
                
                // Check distance directly instead of using channeling object
                Location playerLoc = taskPlayer.getLocation();
                double distance = playerLoc.distance(shrineLoc);
                boolean stillNear = distance <= 3.5;
                if (!stillNear) {
                    plugin.getLogger().warning("[Shrine] Channeling interrupted: Player " + taskPlayer.getName() + 
                        " moved away from shrine. Distance: " + String.format("%.2f", distance) + 
                        " (max: 3.5). Shrine at " + shrineLoc + 
                        ", Player at " + playerLoc);
                    
                    activeChanneling.remove(taskPlayerId);
                    isCancelled.set(true);
                    
                    // Only send message if it's a significant move (not just tiny movement)
                    if (distance > 4.0) {
                        taskPlayer.sendMessage(ChatColor.RED + "Channeling interrupted! Stay near the shrine.");
                    }
                    
                    try {
                        BukkitTask currentTask = (BukkitTask) this;
                        if (currentTask != null) currentTask.cancel();
                    } catch (Exception e) {
                        // Ignore
                    }
                    return;
                }
                
                ticks++;
                
                // Progress indicator
                if (ticks % 20 == 0 && !isCancelled.get()) {
                    int seconds = ticks / 20;
                    taskPlayer.spigot().sendMessage(ChatMessageType.ACTION_BAR, 
                        new TextComponent(ChatColor.YELLOW + "Channeling... " + seconds + "/2"));
                }
                
                if (ticks >= requiredTicks && !isCancelled.get()) {
                    // CRITICAL: Use synchronized block to ensure only ONE task completes
                    synchronized (ShrineManager.this) {
                        // Check again if cancelled or already completed
                        if (isCancelled.get() || !activeChanneling.containsKey(taskPlayerId)) {
                            plugin.getLogger().fine("[Shrine] Completion skipped - already cancelled or completed");
                            return;
                        }
                        
                        // Check if player is already in shrine GUI (prevent duplicate GUI opens)
                        if (playersInShrineGUI.contains(taskPlayerId)) {
                            plugin.getLogger().warning("[Shrine] Player " + taskPlayer.getName() + " already in shrine GUI, skipping duplicate open.");
                            activeChanneling.remove(taskPlayerId);
                            isCancelled.set(true);
                            try {
                                BukkitTask currentTask = (BukkitTask) this;
                                if (currentTask != null) currentTask.cancel();
                            } catch (Exception e) {
                                // Ignore
                            }
                            return;
                        }
                        
                        // Mark as cancelled IMMEDIATELY to prevent other tasks from running
                        isCancelled.set(true);
                        
                        // Remove from activeChanneling - this prevents other tasks from completing
                        ShrineChanneling completedChanneling = activeChanneling.remove(taskPlayerId);
                        if (completedChanneling == null) {
                            // This shouldn't happen, but just in case
                            plugin.getLogger().warning("[Shrine] Strange: activeChanneling was null during completion");
                            try {
                                BukkitTask currentTask = (BukkitTask) this;
                                if (currentTask != null) currentTask.cancel();
                            } catch (Exception e) {
                                // Ignore
                            }
                            return;
                        }
                        
                        plugin.getLogger().info("[Shrine] Channeling complete for " + taskPlayer.getName() + "! Opening GUI.");
                        
                        // Mark shrine as used so it can't be channeled again
                        taskShrine.markAsUsed();
                        plugin.getLogger().info("[Shrine] Shrine " + taskShrine.getType().getName() + " marked as used at " + taskShrine.getLocation());
                        
                        // NOW freeze game when GUI opens
                        TeamRun teamRun = plugin.getRunManager().getTeamRun(taskPlayerId);
                        if (teamRun != null) {
                            teamRun.setPlayerInGUI(taskPlayerId, true);
                            // Freeze mobs immediately when GUI opens (don't wait for next game loop tick)
                            plugin.getGameManager().updateMobFreeze(teamRun);
                        }
                        plugin.getWeaponManager().stopAutoAttack(taskPlayer);
                        
                        // Check if player is still in a GUI from previous shrine
                        boolean wasInGUI = playersInShrineGUI.contains(taskPlayerId);
                        if (wasInGUI) {
                            plugin.getLogger().warning("[Shrine] Player " + taskPlayer.getName() + " was still in shrine GUI set, removing before opening new GUI.");
                            playersInShrineGUI.remove(taskPlayerId);
                        }
                        
                        // Mark as in shrine GUI IMMEDIATELY to prevent duplicates
                        playersInShrineGUI.add(taskPlayerId);
                        plugin.getLogger().info("[Shrine] Opening GUI for " + taskPlayer.getName() + ". playersInShrineGUI contains: " + playersInShrineGUI.contains(taskPlayerId));
                        
                        openShrineGUI(taskPlayer, taskShrine, taskTeamId);
                        
                        // Cancel this task
                        try {
                            BukkitTask currentTask = (BukkitTask) this;
                            if (currentTask != null) currentTask.cancel();
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                }
            }
        }, 0L, 1L);
        
        channeling.setTask(task);
    }
    
    /**
     * Cancel channeling for a player
     */
    public void cancelChanneling(Player player) {
        ShrineChanneling channeling = activeChanneling.remove(player.getUniqueId());
        if (channeling != null) {
            if (channeling.getTask() != null) {
                channeling.getTask().cancel();
                plugin.getLogger().info("[Shrine] Cancelled channeling task for " + player.getName());
            }
            // No need to unfreeze - we never froze during channeling
        }
    }
    
    /**
     * Clean up all shrine-related state for a player (channeling, GUI, etc.)
     * Called when player dies, quits, or run ends
     */
    public void cleanupPlayerChanneling(Player player) {
        UUID playerId = player.getUniqueId();
        
        // Cancel any active channeling
        ShrineChanneling channeling = activeChanneling.remove(playerId);
        if (channeling != null) {
            if (channeling.getTask() != null) {
                channeling.getTask().cancel();
                plugin.getLogger().info("[Shrine] Cleaned up channeling task for " + player.getName() + " (death/quit)");
            }
        }
        
        // Remove from shrine GUI tracking
        boolean wasInGUI = playersInShrineGUI.remove(playerId);
        if (wasInGUI) {
            plugin.getLogger().info("[Shrine] Removed " + player.getName() + " from shrine GUI tracking");
        }
        
        // Note: We don't clear cooldowns or recentlyUsed - those should persist
        // We don't remove shrines from arenaShrines - those are per-team and cleaned up when run ends
    }
    
    /**
     * Open shrine buff selection GUI
     */
    private void openShrineGUI(Player player, Shrine shrine, UUID teamId) {
        plugin.getGuiManager().openShrineGUI(player, shrine, teamId);
    }
    
    /**
     * Apply shrine buff and start cooldown
     */
    public void applyShrineBuffAndStartCooldown(Player player, Shrine shrine, Shrine.ShrineType type, com.eldor.roguecraft.gui.ShrineGUI.ShrineBuff buff) {
        UUID playerId = player.getUniqueId();
        
        // Remove from shrine GUI tracking
        playersInShrineGUI.remove(playerId);
        
        // Mark as recently used to prevent immediate re-channeling
        recentlyUsed.put(playerId, System.currentTimeMillis());
        
        // Set personal cooldown
        playerCooldowns.computeIfAbsent(playerId, k -> new HashMap<>())
                .put(type, System.currentTimeMillis());
        
        // Apply the actual buff effect
        applyShrineBuffEffect(player, buff);
        
        // Visual feedback
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
    }
    
    /**
     * Apply the actual shrine buff effect based on the effect type
     * Now handles power-ups instead of temporary buffs
     */
    private void applyShrineBuffEffect(Player player, com.eldor.roguecraft.gui.ShrineGUI.ShrineBuff buff) {
        String effectType = buff.effectType;
        
        // Check if this is a power-up ID (new system)
        if (effectType.startsWith("dynamic_") || effectType.startsWith("shrine_")) {
            // Get the power-up from the buff object if available
            com.eldor.roguecraft.models.PowerUp powerUp = buff.powerUp;
            if (powerUp == null) {
                // Fallback: try to get from registry
                powerUp = plugin.getPowerUpManager().getPowerUp(effectType);
            }
            if (powerUp == null) {
                // Last resort: create from ID
                powerUp = createPowerUpFromId(effectType);
            }
            
            if (powerUp != null) {
                // Apply the power-up using the existing system
                applyPowerUpFromShrine(player, powerUp);
            }
            return;
        }
        
        // Legacy effect system (for backwards compatibility, though shouldn't be used now)
        String[] parts = effectType.split("_");
        
        if (effectType.startsWith("power_")) {
            // Power buffs: damage multiplier
            double multiplier = Double.parseDouble(parts[1].replace("x", ""));
            int duration = Integer.parseInt(parts[2].replace("s", ""));
            applyDamageMultiplier(player, multiplier, duration);
            
        } else if (effectType.startsWith("speed_")) {
            // Speed buffs
            double multiplier = Double.parseDouble(parts[1].replace("x", ""));
            int duration = Integer.parseInt(parts[2].replace("s", ""));
            applySpeedMultiplier(player, multiplier, duration);
            
        } else if (effectType.startsWith("blink_")) {
            // Blink: teleport + speed
            double speedMultiplier = Double.parseDouble(parts[1].replace("x", ""));
            int duration = Integer.parseInt(parts[2].replace("s", ""));
            applyBlinkEffect(player, speedMultiplier, duration);
            
        } else if (effectType.startsWith("heal_")) {
            // Healing buffs
            if (effectType.contains("full")) {
                player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, 15 * 20, 2));
            } else if (effectType.contains("regen")) {
                int duration = Integer.parseInt(parts[parts.length - 1].replace("s", ""));
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, duration * 20, 1));
            }
            
        } else if (effectType.startsWith("overheal_")) {
            // Overheal: increase max HP temporarily
            int duration = Integer.parseInt(parts[1].replace("s", ""));
            applyOverheal(player, duration);
            
        } else if (effectType.startsWith("xp_")) {
            // XP multiplier buffs
            if (parts.length >= 2) {
                double multiplier = Double.parseDouble(parts[1].replace("x", ""));
                
                // Check if it's "rare" (grants rare power-up on kill) or has a duration
                if (parts.length > 2 && parts[2].equals("rare")) {
                    // Special case: 4x XP + rare power-up on kill (permanent until end of run)
                    applyXPMultiplier(player, multiplier, 0); // 0 = permanent
                    // Set metadata to grant rare power-up on kill
                    player.setMetadata("shrine_rare_powerup", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                    player.sendMessage(ChatColor.YELLOW + "✦ Treasure Hunter activated! You'll receive a rare power-up on your next kill!");
                } else {
                    // Normal XP buff with duration
                    int duration = parts.length > 2 ? Integer.parseInt(parts[2].replace("s", "")) : 0;
                    applyXPMultiplier(player, multiplier, duration);
                }
            }
            
        } else if (effectType.startsWith("crit_")) {
            // Critical hit buffs
            int critChance = Integer.parseInt(parts[1]);
            int duration = Integer.parseInt(parts[parts.length - 1].replace("s", ""));
            applyCritBuff(player, critChance, duration);
            
        } else if (effectType.startsWith("invuln_")) {
            // Invulnerability
            int duration = Integer.parseInt(parts[1].replace("s", ""));
            applyInvulnerability(player, duration);
            
        } else if (effectType.startsWith("slow_") || effectType.startsWith("timestop_")) {
            // Slow/freeze enemies
            int duration = Integer.parseInt(parts[parts.length - 1].replace("s", ""));
            double slowPercent = effectType.contains("timestop") ? 1.0 : Double.parseDouble(parts[1]) / 100.0;
            // For time stop (freeze), use freezeAllMobs instead of slow effect
            if (effectType.contains("timestop")) {
                com.eldor.roguecraft.models.TeamRun teamRun = plugin.getRunManager().getTeamRun(player);
                if (teamRun != null && teamRun.isActive()) {
                    plugin.getGameManager().freezeAllMobs(teamRun, duration);
                    for (Player p : teamRun.getPlayers()) {
                        if (p != null && p.isOnline()) {
                            p.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE + "⏸ Time Stop activated! All enemies frozen for " + duration + " seconds!");
                            p.playSound(p.getLocation(), org.bukkit.Sound.ITEM_TOTEM_USE, 1.0f, 0.8f);
                            p.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.2);
                        }
                    }
                }
            } else {
                applyEnemySlow(player, slowPercent, duration);
            }
            
        } else if (effectType.startsWith("haste_")) {
            // Haste: player speed + enemy slow
            double speedBoost = Double.parseDouble(parts[1]) / 100.0;
            int duration = Integer.parseInt(parts[2].replace("s", ""));
            applyHasteEffect(player, speedBoost, duration);
        }
    }
    
    private void applyDamageMultiplier(Player player, double multiplier, int seconds) {
        // Store damage multiplier in player metadata
        player.setMetadata("shrine_damage_mult", new org.bukkit.metadata.FixedMetadataValue(plugin, multiplier));
        
        // Remove after duration
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.removeMetadata("shrine_damage_mult", plugin);
            player.sendMessage(ChatColor.GRAY + "Power buff expired.");
        }, seconds * 20L);
    }
    
    private void applySpeedMultiplier(Player player, double multiplier, int seconds) {
        org.bukkit.attribute.AttributeInstance speedAttr = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            double baseSpeed = 0.1;
            double newSpeed = Math.max(0.0, Math.min(1.0, baseSpeed * multiplier));
            speedAttr.setBaseValue(newSpeed);
            
            // Restore after duration
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                speedAttr.setBaseValue(baseSpeed);
                player.sendMessage(ChatColor.GRAY + "Speed buff expired.");
            }, seconds * 20L);
        }
    }
    
    private void applyBlinkEffect(Player player, double speedMultiplier, int seconds) {
        // Teleport to nearest enemy
        LivingEntity target = findNearestEnemyForTeleport(player);
        if (target != null) {
            Location targetLoc = target.getLocation();
            player.teleport(targetLoc.add(0, 0.5, 0));
            player.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, player.getLocation(), 30, 0.5, 1, 0.5, 0.5);
        }
        
        // Apply speed
        applySpeedMultiplier(player, speedMultiplier, seconds);
    }
    
    private void applyOverheal(Player player, int seconds) {
        org.bukkit.attribute.AttributeInstance healthAttr = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
        if (healthAttr != null) {
            double originalMax = healthAttr.getBaseValue();
            double newMax = originalMax * 2.0;
            healthAttr.setBaseValue(newMax);
            player.setHealth(newMax);
            
            // Restore after duration
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                healthAttr.setBaseValue(originalMax);
                if (player.getHealth() > originalMax) {
                    player.setHealth(originalMax);
                }
                player.sendMessage(ChatColor.GRAY + "Overheal expired.");
            }, seconds * 20L);
        }
    }
    
    private void applyXPMultiplier(Player player, double multiplier, int seconds) {
        player.setMetadata("shrine_xp_mult", new org.bukkit.metadata.FixedMetadataValue(plugin, multiplier));
        
        // Only set expiration if duration > 0 (0 = permanent until end of run)
        if (seconds > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.removeMetadata("shrine_xp_mult", plugin);
                player.sendMessage(ChatColor.GRAY + "XP multiplier expired.");
            }, seconds * 20L);
        }
    }
    
    private void applyCritBuff(Player player, int critChance, int seconds) {
        player.setMetadata("shrine_crit_chance", new org.bukkit.metadata.FixedMetadataValue(plugin, critChance / 100.0));
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.removeMetadata("shrine_crit_chance", plugin);
            player.sendMessage(ChatColor.GRAY + "Crit buff expired.");
        }, seconds * 20L);
    }
    
    private void applyInvulnerability(Player player, int seconds) {
        player.setMetadata("shrine_invulnerable", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        
        // Visual effect
        org.bukkit.scheduler.BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) return;
            player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3, 0);
        }, 0L, 5L);
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            task.cancel();
            player.removeMetadata("shrine_invulnerable", plugin);
            player.sendMessage(ChatColor.GRAY + "Invulnerability expired.");
        }, seconds * 20L);
    }
    
    private void applyEnemySlow(Player player, double slowPercent, int seconds) {
        // Apply slowness to all nearby enemies
        for (org.bukkit.entity.Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 50, 50, 50)) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                LivingEntity mob = (LivingEntity) entity;
                int slownessLevel = slowPercent >= 1.0 ? 255 : (int)(slowPercent * 10);
                mob.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, seconds * 20, slownessLevel, true, false));
            }
        }
        
        player.sendMessage(ChatColor.GRAY + "Enemies slowed for " + seconds + " seconds.");
    }
    
    private void applyHasteEffect(Player player, double speedBoost, int seconds) {
        // Apply speed to player
        applySpeedMultiplier(player, 1.0 + speedBoost, seconds);
        
        // Slow enemies
        applyEnemySlow(player, 0.5, seconds);
    }
    
    private LivingEntity findNearestEnemyForTeleport(Player player) {
        double range = 30.0;
        LivingEntity nearest = null;
        double nearestDistance = range * range;
        
        for (org.bukkit.entity.Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), range, range, range)) {
            if (entity instanceof LivingEntity && !(entity instanceof Player) && !entity.isDead()) {
                double distSq = player.getLocation().distanceSquared(entity.getLocation());
                if (distSq < nearestDistance) {
                    nearest = (LivingEntity) entity;
                    nearestDistance = distSq;
                }
            }
        }
        
        return nearest;
    }
    
    private boolean isPlayerOnCooldown(UUID playerId, Shrine.ShrineType type) {
        Map<Shrine.ShrineType, Long> cooldowns = playerCooldowns.get(playerId);
        if (cooldowns == null) return false;
        
        Long lastUsed = cooldowns.get(type);
        if (lastUsed == null) return false;
        
        long cooldown = type.getCooldown() * 1000L;
        return System.currentTimeMillis() - lastUsed < cooldown;
    }
    
    private long getPlayerCooldownRemaining(UUID playerId, Shrine.ShrineType type) {
        Map<Shrine.ShrineType, Long> cooldowns = playerCooldowns.get(playerId);
        if (cooldowns == null) return 0;
        
        Long lastUsed = cooldowns.get(type);
        if (lastUsed == null) return 0;
        
        long cooldown = type.getCooldown() * 1000L;
        long remaining = cooldown - (System.currentTimeMillis() - lastUsed);
        return Math.max(0, remaining / 1000L);
    }
    
    /**
     * Check if player can use this specific shrine instance
     */
    public boolean canPlayerUseShrine(UUID playerId, Shrine.ShrineType type) {
        // Can't use if already channeling
        if (activeChanneling.containsKey(playerId)) {
            return false;
        }
        // Can't use if in shrine GUI
        if (playersInShrineGUI.contains(playerId)) {
            return false;
        }
        return !isPlayerOnCooldown(playerId, type);
    }
    
    /**
     * Check if player is currently channeling
     */
    public boolean isPlayerChanneling(UUID playerId) {
        return activeChanneling.containsKey(playerId);
    }
    
    /**
     * Get a random location for a shrine within the arena
     * Randomly distributed throughout the region, on the surface (not in caves)
     */
    private Location getRandomShrineLocation(Arena arena) {
        if (arena.getCenter() == null) {
            plugin.getLogger().warning("[Shrine] Cannot spawn shrine: Arena center is null!");
            return null;
        }
        
        World world = arena.getCenter().getWorld();
        if (world == null) {
            plugin.getLogger().warning("[Shrine] Cannot spawn shrine: World is null!");
            return null;
        }
        
        Random random = new Random();
        double radius = arena.getRadius();
        
        // Generate random X/Z coordinates uniformly distributed within the arena radius
        // Use rejection sampling to ensure uniform distribution in a circle
        double x, z;
        do {
            x = arena.getCenter().getX() + (random.nextDouble() * 2 - 1) * radius;
            z = arena.getCenter().getZ() + (random.nextDouble() * 2 - 1) * radius;
        } while (Math.sqrt(Math.pow(x - arena.getCenter().getX(), 2) + Math.pow(z - arena.getCenter().getZ(), 2)) > radius);
        
        // Find surface Y coordinate
        Location surfaceLoc = findSurfaceLocation(world, x, z, arena.getCenter().getY());
        if (surfaceLoc == null) {
            return null; // Could not find valid surface
        }
        
        plugin.getLogger().info("[Shrine] Spawning shrine at " + surfaceLoc + " (arena center: " + arena.getCenter() + ")");
        return surfaceLoc;
    }
    
    /**
     * Find a surface location at the given X/Z coordinates
     * Returns a location on solid ground with air above (not in a cave)
     * 
     * @param world The world to search in
     * @param x X coordinate
     * @param z Z coordinate
     * @param startY Starting Y coordinate to search from (typically arena center Y)
     * @return Surface location, or null if no valid surface found
     */
    private Location findSurfaceLocation(World world, double x, double z, double startY) {
        // Ensure chunk is loaded
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        org.bukkit.Chunk chunk = world.getChunkAt(blockX >> 4, blockZ >> 4);
        if (!chunk.isLoaded()) {
            chunk.load();
        }
        
        // Start searching from a reasonable height (arena center Y + some buffer)
        // Search down first to find the ground
        int searchY = (int) Math.floor(startY);
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        
        // First, try to find solid ground below the start Y
        Block block = world.getBlockAt(blockX, searchY, blockZ);
        Block below = searchY > minY ? world.getBlockAt(blockX, searchY - 1, blockZ) : null;
        
        // If we're in air, search down for solid ground
        if (block.getType() == Material.AIR && below != null && below.getType() != Material.AIR) {
            // Already on surface, but verify it's not in a cave
            if (isOnSurface(world, x, searchY, z)) {
                return new Location(world, x, searchY, z);
            }
        }
        
        // Search downward for solid ground
        for (int y = searchY; y >= minY + 5; y--) {
            block = world.getBlockAt(blockX, y, blockZ);
            Block blockAbove = y < maxY - 1 ? world.getBlockAt(blockX, y + 1, blockZ) : null;
            
            // Found solid ground with air above
            if (block.getType().isSolid() && blockAbove != null && blockAbove.getType() == Material.AIR) {
                // Verify it's on the surface (not in a cave)
                if (isOnSurface(world, x, y + 1, z)) {
                    return new Location(world, x, y + 1, z);
                }
            }
        }
        
        // If we didn't find anything below, try searching upward (in case we started too low)
        for (int y = searchY + 1; y <= Math.min(startY + 20, maxY - 5); y++) {
            block = world.getBlockAt(blockX, y, blockZ);
            Block blockBelow = y > minY ? world.getBlockAt(blockX, y - 1, blockZ) : null;
            
            // Found air with solid ground below
            if (block.getType() == Material.AIR && blockBelow != null && blockBelow.getType().isSolid()) {
                // Verify it's on the surface (not in a cave)
                if (isOnSurface(world, x, y, z)) {
                    return new Location(world, x, y, z);
                }
            }
        }
        
        // Could not find valid surface
        return null;
    }
    
    /**
     * Check if a location is on the surface (not in a cave)
     * Verifies that there's enough air above (at least 3 blocks) to ensure it's not underground
     * 
     * @param world The world
     * @param x X coordinate
     * @param y Y coordinate (should be the air block above ground)
     * @param z Z coordinate
     * @return true if on surface, false if in a cave
     */
    private boolean isOnSurface(World world, double x, int y, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        
        // Ensure chunk is loaded
        org.bukkit.Chunk chunk = world.getChunkAt(blockX >> 4, blockZ >> 4);
        if (!chunk.isLoaded()) {
            chunk.load();
        }
        
        int maxY = world.getMaxHeight();
        
        // Check that there's at least 10 blocks of air above (ensures it's on surface, not in cave)
        int airBlocks = 0;
        for (int checkY = y; checkY < Math.min(y + 12, maxY); checkY++) {
            Block checkBlock = world.getBlockAt(blockX, checkY, blockZ);
            if (checkBlock.getType() == Material.AIR) {
                airBlocks++;
            } else {
                break; // Hit a solid block, stop counting
            }
        }
        
        // Need at least 10 blocks of air above to be considered "on surface"
        return airBlocks >= 10;
    }
    
    /**
     * Clean up all shrines and channeling
     * Called on server stop/disable to ensure all shrine blocks are removed
     */
    public void cleanup() {
        plugin.getLogger().info("[Shrine] Starting comprehensive shrine cleanup...");
        
        // Cancel all active channeling
        int channelingCount = activeChanneling.size();
        for (ShrineChanneling channeling : activeChanneling.values()) {
            if (channeling.getTask() != null) {
                channeling.getTask().cancel();
            }
        }
        activeChanneling.clear();
        plugin.getLogger().info("[Shrine] Cancelled " + channelingCount + " active channeling tasks");
        
        // Remove all shrines from all teams
        int totalShrines = 0;
        for (List<Shrine> shrines : arenaShrines.values()) {
            for (Shrine shrine : shrines) {
                if (shrine != null) {
                    shrine.remove();
                    totalShrines++;
                }
            }
        }
        arenaShrines.clear();
        plugin.getLogger().info("[Shrine] Removed " + totalShrines + " shrines and cleared all shrine blocks");
        
        // Clear cooldowns, recent use tracking, and GUI tracking
        playerCooldowns.clear();
        recentlyUsed.clear();
        playersInShrineGUI.clear();
        
        plugin.getLogger().info("[Shrine] Cleanup complete!");
    }
    
    /**
     * Called when player closes shrine GUI
     */
    public void onShrineGUIClosed(Player player) {
        UUID playerId = player.getUniqueId();
        boolean removed = playersInShrineGUI.remove(playerId);
        plugin.getLogger().info("[Shrine] Player " + player.getName() + " closed shrine GUI. Removed from playersInShrineGUI: " + removed + ". Set now contains: " + playersInShrineGUI.contains(playerId));
    }
    
    /**
     * Check if player is currently in shrine GUI
     */
    public boolean isPlayerInShrineGUI(UUID playerId) {
        return playersInShrineGUI.contains(playerId);
    }
    
    /**
     * Helper class to track active channeling
     */
    private static class ShrineChanneling {
        private final Player player;
        private final Shrine shrine;
        private final UUID teamId;
        private final Location startLocation;
        private BukkitTask task;
        private int checkCount = 0;
        
        public ShrineChanneling(Player player, Shrine shrine, UUID teamId) {
            this.player = player;
            this.shrine = shrine;
            this.teamId = teamId;
            this.startLocation = player.getLocation().clone();
        }
        
        public boolean isStillNear() {
            checkCount++;
            Location playerLoc = player.getLocation();
            Location shrineLoc = shrine.getLocation();
            
            // Check if in same world
            if (!playerLoc.getWorld().equals(shrineLoc.getWorld())) {
                if (checkCount % 20 == 0) { // Log every second to avoid spam
                    RoguecraftPlugin.getInstance().getLogger().warning("[Shrine] Player " + player.getName() + 
                        " in different world! Player: " + playerLoc.getWorld().getName() + 
                        ", Shrine: " + shrineLoc.getWorld().getName());
                }
                return false;
            }
            
            double distance = playerLoc.distance(shrineLoc);
            double maxDistance = 3.5;
            
            // Log distance check every 20 ticks (1 second) to debug
            if (checkCount % 20 == 0) {
                RoguecraftPlugin.getInstance().getLogger().info("[Shrine] Distance check #" + checkCount + 
                    ": " + String.format("%.2f", distance) + "m (max: " + maxDistance + "m). " +
                    "Shrine: " + String.format("%.1f,%.1f,%.1f", shrineLoc.getX(), shrineLoc.getY(), shrineLoc.getZ()) +
                    ", Player: " + String.format("%.1f,%.1f,%.1f", playerLoc.getX(), playerLoc.getY(), playerLoc.getZ()));
            }
            
            return distance <= maxDistance;
        }
        
        public Player getPlayer() {
            return player;
        }
        
        public Shrine getShrine() {
            return shrine;
        }
        
        public UUID getTeamId() {
            return teamId;
        }
        
        public BukkitTask getTask() {
            return task;
        }
        
        public void setTask(BukkitTask task) {
            this.task = task;
        }
    }
    
    /**
     * Create a power-up from its ID (for dynamically generated power-ups)
     */
    private com.eldor.roguecraft.models.PowerUp createPowerUpFromId(String id) {
        // This is a fallback - power-ups should be stored when generated
        // For now, we'll need to regenerate it or store it differently
        // For jump height, we can recreate it
        if (id.startsWith("shrine_jump_height_")) {
            // Extract jump height from stored metadata or recreate
            // For simplicity, we'll use a default value and track it via stat
            return null; // Will be handled via stat
        }
        return null;
    }
    
    /**
     * Apply a power-up from shrine selection
     */
    private void applyPowerUpFromShrine(Player player, com.eldor.roguecraft.models.PowerUp powerUp) {
        if (powerUp == null) return;
        
        // Get player's run
        com.eldor.roguecraft.models.TeamRun teamRun = plugin.getRunManager().getTeamRun(player.getUniqueId());
        com.eldor.roguecraft.models.Run run = null;
        if (teamRun == null) {
            run = plugin.getRunManager().getRun(player.getUniqueId());
        }
        
        Object runObj = teamRun != null ? teamRun : run;
        if (runObj == null) return;
        
        // Check if it's jump height
        if (powerUp.getId().startsWith("shrine_jump_height_") || 
            powerUp.getName().contains("Jump Height")) {
            double jumpHeightValue = powerUp.getValue();
            // Add jump height to stat
            if (teamRun != null) {
                teamRun.addStat("jump_height", jumpHeightValue);
            } else if (run != null) {
                run.addStat("jump_height", jumpHeightValue);
            }
            // Calculate slow falling level for message
            int slowFallingLevel = (int) Math.min(4, Math.floor(jumpHeightValue / 0.5));
            player.sendMessage(ChatColor.GREEN + "✦ " + powerUp.getName() + " activated! Jump height increased by +" + String.format("%.1f", jumpHeightValue) + " (Slow Falling " + slowFallingLevel + ")");
            return;
        }
        
        // For stat boosts, apply using the existing power-up system
        if (powerUp.getType() == com.eldor.roguecraft.models.PowerUp.PowerUpType.STAT_BOOST) {
            String statName = extractStatName(powerUp.getId());
            double value = powerUp.getValue();
            
            if (teamRun != null) {
                teamRun.addStat(player, statName, value);
                teamRun.addPowerUp(player, powerUp);
            } else if (run != null) {
                run.addStat(statName, value);
                run.addPowerUp(powerUp);
            }
            
            // Apply stat immediately if it's health or speed
            if (statName.equals("health")) {
                double health = teamRun != null ? teamRun.getStat(player, "health") : run.getStat("health");
                org.bukkit.attribute.Attribute healthAttr = org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH;
                org.bukkit.attribute.AttributeInstance healthInstance = player.getAttribute(healthAttr);
                if (healthInstance != null) {
                    healthInstance.setBaseValue(health);
                    player.setHealth(Math.min(health, player.getHealth()));
                }
            } else if (statName.equals("speed")) {
                double speed = teamRun != null ? teamRun.getStat(player, "speed") : run.getStat("speed");
                double baseSpeed = 0.1;
                double newSpeed = Math.max(0.0, Math.min(1.0, baseSpeed * speed));
                org.bukkit.attribute.Attribute speedAttr = org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED;
                org.bukkit.attribute.AttributeInstance speedInstance = player.getAttribute(speedAttr);
                if (speedInstance != null) {
                    speedInstance.setBaseValue(newSpeed);
                }
            }
            
            player.sendMessage(ChatColor.GREEN + "✦ " + powerUp.getName() + " activated!");
        }
    }
    
    /**
     * Extract stat name from power-up ID
     */
    private String extractStatName(String id) {
        // Remove "dynamic_" prefix and any trailing numbers
        String statName = id.replaceAll("dynamic_", "").replaceAll("_\\d+", "");
        return statName;
    }
}

