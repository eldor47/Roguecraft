package com.eldor.roguecraft.managers;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.Arena;
import com.eldor.roguecraft.models.Run;
import com.eldor.roguecraft.models.TeamRun;
import com.eldor.roguecraft.models.Weapon;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class GameManager {
    private final RoguecraftPlugin plugin;
    private final Map<UUID, BukkitTask> runTasks;
    private final Map<UUID, BukkitTask> spawnTasks;
    private final Map<UUID, BukkitTask> regenTasks; // Regeneration tasks
    private final Map<UUID, BukkitTask> jumpHeightTasks; // Jump height (slow falling) tasks
    private final Map<UUID, BukkitTask> healthDisplayTasks; // Health display for players
    private final Set<UUID> teamsInWeaponSelection; // Track players currently in weapon selection phase
    private final Map<UUID, Set<LivingEntity>> frozenMobs; // Track frozen mobs per team run
    private final Map<UUID, Long> timeFreezeEndTime; // Track when time freeze ends for each team run (for new spawns)
    private final Map<UUID, WorldBorderSettings> originalBorders; // Store original border settings per team
    private final Set<Location> roguecraftSpawnLocations; // Track spawn locations for WorldGuard compatibility
    private final Map<UUID, Long> lastDamageTime; // Track last damage time for regeneration proc system
    private final Map<UUID, Integer> bossSpawnedWave; // Track which wave has spawned the boss for each team

    public GameManager(RoguecraftPlugin plugin) {
        this.plugin = plugin;
        this.runTasks = new HashMap<>();
        this.spawnTasks = new HashMap<>();
        this.regenTasks = new HashMap<>();
        this.jumpHeightTasks = new HashMap<>();
        this.healthDisplayTasks = new HashMap<>();
        this.teamsInWeaponSelection = new HashSet<>();
        this.frozenMobs = new HashMap<>();
        this.timeFreezeEndTime = new HashMap<>();
        this.originalBorders = new HashMap<>();
        this.roguecraftSpawnLocations = new HashSet<>();
        this.lastDamageTime = new HashMap<>();
        this.bossSpawnedWave = new HashMap<>();
    }
    
    /**
     * Check if a location is a Roguecraft spawn location (for WorldGuard compatibility)
     */
    public boolean isRoguecraftSpawnLocation(Location loc) {
        if (loc == null) return false;
        // Check if location is within 1 block of any tracked spawn location
        for (Location spawnLoc : roguecraftSpawnLocations) {
            if (spawnLoc != null && spawnLoc.getWorld().equals(loc.getWorld()) &&
                spawnLoc.distanceSquared(loc) <= 4.0) { // 2 block radius
                return true;
            }
        }
        return false;
    }
    
    /**
     * Add a spawn location to tracking (for WorldGuard compatibility)
     */
    private void addSpawnLocation(Location loc) {
        if (loc != null) {
            Location cloned = loc.clone();
            roguecraftSpawnLocations.add(cloned);
            // Remove after 2 seconds (spawn event should fire immediately, but give buffer)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                roguecraftSpawnLocations.remove(cloned);
            }, 40L); // 2 seconds
        }
    }
    
    // Helper class to store world border settings
    private static class WorldBorderSettings {
        final Location center;
        final double size;
        final int warningDistance;
        final int warningTime;
        
        WorldBorderSettings(org.bukkit.WorldBorder border) {
            this.center = border.getCenter();
            this.size = border.getSize();
            this.warningDistance = border.getWarningDistance();
            this.warningTime = border.getWarningTime();
        }
        
        void restore(org.bukkit.WorldBorder border) {
            border.setCenter(center);
            border.setSize(size);
            border.setWarningDistance(warningDistance);
            border.setWarningTime(warningTime);
        }
    }

    public boolean startRun(Player player, Arena arena) {
        if (plugin.getRunManager().hasActiveRun(player)) {
            player.sendMessage("§cYou already have an active run!");
            return false;
        }

        if (arena == null) {
            arena = plugin.getArenaManager().getDefaultArena();
            if (arena == null) {
                player.sendMessage("§cNo arena available!");
                return false;
            }
        }

        // Check if there's already a team run in this arena - join it
        TeamRun existingTeam = plugin.getRunManager().getTeamRun(player);
        if (existingTeam == null) {
            existingTeam = plugin.getRunManager().startTeamRun(player, arena);
        }

        // Teleport player to arena
        if (arena.getSpawnPoint() != null) {
            player.teleport(arena.getSpawnPoint());
        }

        arena.setActive(true);
        player.sendMessage("§aEntering the Arena Realm...");
        player.sendMessage("§eSurvive the waves and close the rift!");
        
        if (existingTeam != null && existingTeam.getPlayerCount() > 1) {
            player.sendMessage("§bJoined team of " + existingTeam.getPlayerCount() + " players!");
        }

        // Show weapon selection for each player individually
        UUID teamId = getTeamRunId(existingTeam);
        final TeamRun finalTeamRun = existingTeam;
        final Arena finalArena = arena;
        
        // Check if this player already has a weapon
        Weapon playerWeapon = finalTeamRun.getWeapon(player);
        
        if (playerWeapon == null) {
            // Player needs to select their weapon
            // Check if they're already in weapon selection
            if (!teamsInWeaponSelection.contains(player.getUniqueId())) {
                teamsInWeaponSelection.add(player.getUniqueId());
                
                // Open weapon selection GUI for this player
                openWeaponSelection(player, weapon -> {
                    // Remove from weapon selection tracking
                    teamsInWeaponSelection.remove(player.getUniqueId());
                    
                    // Set weapon for this specific player
                    finalTeamRun.setWeapon(player, new Weapon(weapon));
                    
                    // Start auto-attack for this player
                    plugin.getWeaponManager().startAutoAttack(player, finalTeamRun.getWeapon(player));
                    
                    // Initialize XP bar for this player
                    com.eldor.roguecraft.util.XPBar.updateXPBarWithGold(
                        player,
                        finalTeamRun.getExperience(),
                        finalTeamRun.getExperienceToNextLevel(),
                        finalTeamRun.getLevel(),
                        finalTeamRun.getWave(),
                        finalTeamRun.getCurrentGold()
                    );
                    
                    // Apply initial stats for this player
                    applyInitialStats(player, finalTeamRun);
                    
                    // Start health display for this player
                    startHealthDisplay(player, finalTeamRun);
                    
                    // If this is the first player to select a weapon, start the game loop
                    if (!runTasks.containsKey(teamId)) {
                        // Set up world border visualization
                        setupArenaBorder(finalTeamRun, finalArena, teamId);
                        
                        // Remove any existing shrines first (safety check)
                        plugin.getShrineManager().removeShrinesForRun(teamId);
                        
                        // Spawn physical shrines in arena
                        plugin.getShrineManager().spawnShrinesForRun(teamId, finalArena);
                        
                        // Remove any existing chests first (safety check)
                        plugin.getChestManager().removeChestsForRun(teamId);
                        
                        // Spawn gacha chests in arena
                        plugin.getChestManager().spawnChestsForRun(teamId, finalArena);
                        
                        // Start game loop
                        startGameLoop(finalTeamRun, finalArena);
                        
                        // Start aura effects
                        plugin.getAuraManager().startAuras(finalTeamRun);
                        
                        // Start synergy tracking
                        plugin.getSynergyManager().startSynergies(finalTeamRun);
                        
                        // Start mob health display updates
                        startMobHealthDisplay(finalTeamRun);
                        
                        // Initialize XP bars and apply initial stats for all players who already have weapons
                        for (Player p : finalTeamRun.getPlayers()) {
                            if (p != null && p.isOnline() && finalTeamRun.getWeapon(p) != null) {
                                // Initialize XP bar
                                com.eldor.roguecraft.util.XPBar.updateXPBarWithGold(
                                    p,
                                    finalTeamRun.getExperience(),
                                    finalTeamRun.getExperienceToNextLevel(),
                                    finalTeamRun.getLevel(),
                                    finalTeamRun.getWave(),
                                    finalTeamRun.getCurrentGold()
                                );
                                // Apply initial health
                                applyInitialStats(p, finalTeamRun);
                                // Start health display
                                startHealthDisplay(p, finalTeamRun);
                            }
                        }
                    }
                });
            }
        } else {
            // Player already has a weapon, just initialize their display
            // Initialize XP bar for joining player
            com.eldor.roguecraft.util.XPBar.updateXPBarWithGold(
                player,
                existingTeam.getExperience(),
                existingTeam.getExperienceToNextLevel(),
                existingTeam.getLevel(),
                existingTeam.getWave(),
                existingTeam.getCurrentGold()
            );
            // Apply initial health
            applyInitialStats(player, existingTeam);
            // Start health display
            startHealthDisplay(player, existingTeam);
            
            // Start auto-attack if game loop is already running
            if (runTasks.containsKey(teamId)) {
                plugin.getWeaponManager().startAutoAttack(player, playerWeapon);
            }
        }

        return true;
    }
    
    private void openWeaponSelection(Player player, java.util.function.Consumer<Weapon.WeaponType> onSelect) {
        com.eldor.roguecraft.gui.WeaponSelectionGUI gui = 
            new com.eldor.roguecraft.gui.WeaponSelectionGUI(plugin, player, onSelect);
        gui.open();
    }
    
    private void applyInitialStats(Player player, TeamRun teamRun) {
        // Apply initial health (individual per player)
        double health = teamRun.getStat(player, "health");
        org.bukkit.attribute.Attribute healthAttr = org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH;
        org.bukkit.attribute.AttributeInstance healthInstance = player.getAttribute(healthAttr);
        if (healthInstance != null) {
            healthInstance.setBaseValue(health);
            player.setHealth(health);
        }
        
        // Apply initial speed (individual per player)
        double speed = teamRun.getStat(player, "speed");
        double baseSpeed = 0.1;
        double newSpeed = Math.max(0.0, Math.min(1.0, baseSpeed * speed));
        org.bukkit.attribute.Attribute speedAttr = org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED;
        org.bukkit.attribute.AttributeInstance speedInstance = player.getAttribute(speedAttr);
        if (speedInstance != null) {
            speedInstance.setBaseValue(newSpeed);
        }
        
        // Apply initial armor (visible in HUD like hearts) (individual per player)
        double armor = teamRun.getStat(player, "armor");
        org.bukkit.attribute.Attribute armorAttr = org.bukkit.attribute.Attribute.GENERIC_ARMOR;
        org.bukkit.attribute.AttributeInstance armorInstance = player.getAttribute(armorAttr);
        if (armorInstance != null) {
            armorInstance.setBaseValue(armor);
        }
        
        // Set hunger to max and saturation (prevents hunger during runs)
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExhaustion(0.0f);
    }
    
    private void startHealthDisplay(Player player, TeamRun teamRun) {
        // Display health on action bar every 10 ticks (0.5 seconds)
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !teamRun.isActive()) {
                return;
            }
            
            double currentHealth = player.getHealth();
            // Get player-specific max health
            double maxHealth = teamRun.getStat(player, "health");
            
            // Build health bar with hearts
            String healthBar = ChatColor.RED + "❤ " + 
                              ChatColor.WHITE + String.format("%.1f", currentHealth) + 
                              ChatColor.GRAY + "/" + 
                              ChatColor.WHITE + String.format("%.1f", maxHealth);
            
            // Send action bar message
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(healthBar));
        }, 0L, 10L);
        
        // Store task so we can cancel it later
        healthDisplayTasks.put(player.getUniqueId(), task);
    }
    
    private void setupArenaBorder(TeamRun teamRun, Arena arena, UUID teamId) {
        if (arena.getCenter() == null || arena.getWorld() == null) {
            return;
        }

        // Set world border using the world's border (affects all players in that world)
        org.bukkit.WorldBorder border = arena.getWorld().getWorldBorder();
        
        // Store original settings for later restoration
        originalBorders.put(teamId, new WorldBorderSettings(border));
        
        // Set new border for arena visualization
        // Use arena spawn point (where players start) as the center for accurate positioning
        // This ensures the border is centered on where players actually are
        Location borderCenter = arena.getSpawnPoint();
        if (borderCenter == null) {
            borderCenter = arena.getCenter(); // Fallback to center if spawn not set
        }
        if (borderCenter == null) {
            return; // Can't set border without a location
        }
        
        border.setCenter(borderCenter);
        
        // World border size is diameter, but we want to ensure players can't leave
        // Set size to slightly smaller than radius*2 to account for player collision box
        // Player collision is ~0.6 blocks, so we subtract more to ensure they can't leave
        // Subtract 4 blocks total (2 blocks margin on each side) to be safe
        double borderDiameter = (arena.getRadius() * 2) - 4.0; // Subtract 4 blocks total for safety margin
        border.setSize(Math.max(1.0, borderDiameter)); // Ensure minimum size of 1.0
        
        border.setWarningDistance(0); // No warning distance
        border.setWarningTime(0);
        border.setDamageAmount(0); // Don't damage players
        border.setDamageBuffer(0);
        
        // Notify all players
        for (Player player : teamRun.getPlayers()) {
            if (player != null && player.isOnline()) {
                player.sendMessage("§bWorld border visible - this is your arena!");
                player.sendMessage("§7Border radius: " + String.format("%.1f", arena.getRadius()) + " blocks");
            }
        }
    }
    
    private void removeArenaBorder(TeamRun teamRun, Arena arena) {
        if (arena == null || arena.getWorld() == null) {
            return;
        }

        UUID teamId = getTeamRunId(teamRun);
        WorldBorderSettings original = originalBorders.remove(teamId);
        
        org.bukkit.WorldBorder border = arena.getWorld().getWorldBorder();
        
        if (original != null) {
            // Restore original world border settings
            original.restore(border);
        } else {
            // If no original settings stored, just turn off the border (set to max size)
            // This makes it effectively disappear
            border.setSize(29999984.0); // Max world border size (effectively off)
            border.setCenter(0, 0); // Reset center
            border.setWarningDistance(5);
            border.setWarningTime(15);
            border.setDamageAmount(0.2);
            border.setDamageBuffer(5.0);
        }
        
        // Notify players
        for (Player player : teamRun.getPlayers()) {
            if (player != null && player.isOnline()) {
                player.sendMessage("§7Arena border removed.");
            }
        }
    }

    private UUID getTeamRunId(TeamRun teamRun) {
        if (teamRun == null || teamRun.getPlayers().isEmpty()) {
            return null;
        }
        // Use first player's UUID as team ID
        return teamRun.getPlayers().get(0).getUniqueId();
    }
    
    /**
     * Set the last damage time for a player (used for regeneration proc system)
     */
    public void setLastDamageTime(UUID playerId, long time) {
        lastDamageTime.put(playerId, time);
    }

    private void startGameLoop(TeamRun teamRun, Arena arena) {
        UUID teamId = getTeamRunId(teamRun);

        // Jump height task - applies slow falling effect based on jump_height stat
        BukkitTask jumpHeightTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!teamRun.isActive()) {
                return;
            }
            
            // Apply jump height effect per player (each player has their own jump_height stat)
            for (Player player : teamRun.getPlayers()) {
                if (player != null && player.isOnline() && !player.isDead()) {
                    // Get player-specific jump_height stat
                    double jumpHeight = teamRun.getStat(player, "jump_height");
                    if (jumpHeight > 0) {
                        // Apply slow falling effect based on jump_height
                        // Higher jump_height = higher slow falling level (capped at level 4)
                        int slowFallingLevel = (int) Math.min(4, Math.floor(jumpHeight / 0.5)); // 0.5 jump_height per level
                        if (slowFallingLevel > 0) {
                            // Apply slow falling effect (infinite duration, refreshed every tick)
                            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.SLOW_FALLING, 
                                100, // 5 seconds duration (refreshed every tick)
                                slowFallingLevel - 1, // Level 0-3 (slow falling levels are 0-indexed)
                                false, // No ambient particles
                                false  // No icon
                            ));
                        }
                    }
                }
            }
        }, 0L, 1L); // Run every tick
        
        // Regeneration task - applies regeneration stat to all players with proc system
        // Use a counter to track visual feedback timing
        final java.util.Map<UUID, Integer> regenTickCounters = new java.util.HashMap<>();
        
        BukkitTask regenTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!teamRun.isActive()) {
                return;
            }
            
            for (Player player : teamRun.getPlayers()) {
                if (player != null && player.isOnline() && !player.isDead()) {
                    // Get player-specific regeneration stat
                    double regeneration = teamRun.getStat(player, "regeneration");
                    if (regeneration > 0) {
                        UUID playerId = player.getUniqueId();
                        long lastDamage = lastDamageTime.getOrDefault(playerId, 0L);
                        long timeSinceDamage = System.currentTimeMillis() - lastDamage;
                        
                        // Calculate proc delay based on regeneration stat
                        // Base delay: 1.0 second, reduced by 0.1s per point of regen
                        // Minimum delay: 0.2 seconds
                        double procDelaySeconds = Math.max(0.2, 1.0 - (regeneration * 0.1));
                        long procDelayMs = (long) (procDelaySeconds * 1000);
                        
                        // Only heal if enough time has passed since last damage
                        if (timeSinceDamage >= procDelayMs) {
                            double currentHealth = player.getHealth();
                            // Get player-specific max health
                            double maxHealth = teamRun.getStat(player, "health");
                            
                            // Heal based on regeneration stat (HP per second)
                            // Cap at 2 hearts (4 HP) per second - fixed value to prevent invincibility
                            double healAmount = Math.min(regeneration, 4.0); // Cap at 4 HP per second
                            double newHealth = Math.min(maxHealth, currentHealth + healAmount);
                            
                            if (newHealth > currentHealth) {
                                player.setHealth(newHealth);
                                
                                // Visual feedback every 2 seconds (40 ticks)
                                int tickCount = regenTickCounters.getOrDefault(playerId, 0) + 1;
                                regenTickCounters.put(playerId, tickCount);
                                
                                if (tickCount >= 40) { // Every 2 seconds
                                    player.getWorld().spawnParticle(org.bukkit.Particle.HEART, player.getLocation().add(0, 1, 0), 2, 0.3, 0.3, 0.3, 0);
                                    regenTickCounters.put(playerId, 0); // Reset counter
                                }
                            }
                        }
                    }
                }
            }
        }, 0L, 20L); // Every second (20 ticks)

        // Main game loop task
        BukkitTask gameTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Refresh players list
            teamRun.getPlayers().removeIf(p -> p == null || !p.isOnline());
            
            if (!teamRun.isActive() || teamRun.getPlayerCount() == 0) {
                endTeamRun(teamId, arena);
                return;
            }

            // Arena edge detection removed - world border prevents players from leaving
            // Players can only end the run by dying, using /rc stop, or leaving the server

            // Update difficulty with multiplayer scaling
            long elapsedMinutes = teamRun.getElapsedTime() / 60000;
            long elapsedSeconds = teamRun.getElapsedTime() / 1000;
            
            // Get max wave from config
            int maxWave = plugin.getConfigManager().getBalanceConfig().getInt("waves.max-wave", 20);

            // Handle players in the power-up GUI (team runs)
            // No timeout - players can keep GUI open as long as they want
            if (teamRun.hasAnyPlayerInGUI()) {
                // Check if any players are still online and in GUI
                for (UUID guiPlayerId : new HashSet<>(teamRun.getPlayersInGUI())) {
                    Player guiPlayer = Bukkit.getPlayer(guiPlayerId);
                    if (guiPlayer == null || !guiPlayer.isOnline()) {
                        // Player disconnected, remove from GUI tracking
                        teamRun.setPlayerInGUI(guiPlayerId, false);
                    }
                }
                if (teamRun.hasAnyPlayerInGUI()) {
                    return; // Still waiting on selections - pause game loop
                }
            }

            boolean isInfiniteMode = maxWave > 0 && teamRun.getWave() > maxWave;
            
            if (!isInfiniteMode) {
                // Wave progression: Advance wave every 30 seconds
                int expectedWave = (int) (elapsedSeconds / 30) + 1;
                if (expectedWave > teamRun.getWave() && expectedWave <= maxWave) {
                    int previousWave = teamRun.getWave();
                    teamRun.setWave(expectedWave);
                    // Notify players
                    for (Player player : teamRun.getPlayers()) {
                        if (player != null && player.isOnline()) {
                            player.sendMessage("§6§l⚡ Wave " + expectedWave + " has begun! §r§7Difficulty increased!");
                        }
                    }
                    // Spawn Warden boss when wave 20 is reached (only once)
                    if (expectedWave == maxWave && previousWave < maxWave) {
                        // Check if boss has already been spawned (safety check)
                        Integer lastBossWave = bossSpawnedWave.get(teamId);
                        if (lastBossWave == null || lastBossWave != expectedWave) {
                            spawnWitherBoss(teamRun, arena);
                            bossSpawnedWave.put(teamId, expectedWave);
                            
                            // Spawn additional bosses for each clicked boss shrine
                            Set<UUID> clickedBossShrines = teamRun.getClickedBossShrines();
                            if (clickedBossShrines != null && !clickedBossShrines.isEmpty()) {
                                for (UUID shrineId : clickedBossShrines) {
                                    spawnWitherBoss(teamRun, arena);
                                    
                                    // Notify all players
                                    for (Player p : teamRun.getPlayers()) {
                                        if (p != null && p.isOnline()) {
                                            p.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "☠ ADDITIONAL WITHER BOSS SPAWNED! ☠");
                                            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.7f);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (expectedWave > maxWave && teamRun.getWave() == maxWave) {
                    // Transition to infinite mode
                    teamRun.setWave(maxWave + 1);
                    for (Player player : teamRun.getPlayers()) {
                        if (player != null && player.isOnline()) {
                            player.sendMessage("§c§l☠ INFINITE MODE ACTIVATED! ☠");
                            player.sendMessage("§7The waves are now endless and progressively harder!");
                        }
                    }
                }
            } else {
                // Infinite mode: Progressively faster wave progression
                // Wave interval decreases as infinite waves progress
                // Start at 10 seconds for wave 21, decrease by 0.5 seconds per infinite wave
                // Minimum interval: 1 second (caps at wave 19+)
                int infiniteWaveNumber = teamRun.getWave() - maxWave;
                double waveInterval = Math.max(1.0, 10.0 - (infiniteWaveNumber * 0.5)); // Decrease by 0.5s per wave, min 1s
                
                // Calculate time since infinite mode started
                // Find when infinite mode began (when wave exceeded maxWave)
                long infiniteModeStartTime = teamRun.getElapsedTime() - ((elapsedSeconds - (maxWave * 30)) * 1000);
                long infiniteModeElapsedSeconds = (teamRun.getElapsedTime() - infiniteModeStartTime) / 1000;
                
                // Calculate expected wave based on progressive timing
                int expectedWave = maxWave + 1;
                double accumulatedTime = 0.0;
                for (int w = 1; w <= 100; w++) { // Check up to 100 infinite waves
                    double intervalForWave = Math.max(1.0, 10.0 - ((w - 1) * 0.5));
                    accumulatedTime += intervalForWave;
                    if (accumulatedTime <= infiniteModeElapsedSeconds) {
                        expectedWave = maxWave + w;
                    } else {
                        break;
                    }
                }
                
                if (expectedWave > teamRun.getWave()) {
                    int previousWave = teamRun.getWave();
                    // Set wave directly to expected wave (allows catching up if delayed)
                    teamRun.setWave(expectedWave);
                    // Notify players every 5 waves
                    if (expectedWave % 5 == 0 || previousWave < expectedWave - 4) {
                        // Notify if it's a milestone wave, or if we skipped multiple waves
                        for (Player player : teamRun.getPlayers()) {
                            if (player != null && player.isOnline()) {
                                player.sendMessage("§c§l∞ Infinite Wave " + expectedWave + " §r§7(Getting harder and faster...)");
                            }
                        }
                    }
                }
            }
            
            double baseDifficulty = plugin.getDifficultyManager().calculateDifficulty(
                teamRun.getWave(),
                teamRun.getLevel(),
                elapsedMinutes
            );
            
            // Add infinite wave difficulty scaling
            if (isInfiniteMode) {
                int infiniteWaveNumber = teamRun.getWave() - maxWave;
                double infiniteDifficultyIncrease = plugin.getConfigManager().getBalanceConfig()
                    .getDouble("waves.infinite.difficulty-increase-per-wave", 0.15);
                baseDifficulty += infiniteWaveNumber * infiniteDifficultyIncrease;
            }
            
            // Scale difficulty based on player count
            int playerCount = teamRun.getPlayerCount();
            double multiplayerMultiplier = 1.0 + (playerCount - 1) * 0.2; // 20% per additional player
            
            // Apply difficulty stat (from power-ups) - use maximum difficulty from all players for team-wide mob scaling
            // Since difficulty affects shared mob scaling, we use the maximum so any player's difficulty increase affects the team
            double difficultyStat = 1.0;
            for (Player p : teamRun.getPlayers()) {
                if (p != null && p.isOnline()) {
                    double playerDifficulty = teamRun.getStat(p, "difficulty");
                    if (playerDifficulty > difficultyStat) {
                        difficultyStat = playerDifficulty;
                    }
                }
            }
            double difficulty = baseDifficulty * multiplayerMultiplier * difficultyStat;
            teamRun.setDifficultyMultiplier(difficulty);

            // Freeze/unfreeze mobs based on GUI state
            updateMobFreeze(teamRun);

            // Check for level up
            if (teamRun.getExperience() >= teamRun.getExperienceToNextLevel()) {
                levelUp(teamRun);
            }

        }, 0L, 20L); // Every second

        runTasks.put(teamId, gameTask);
        regenTasks.put(teamId, regenTask);
        jumpHeightTasks.put(teamId, jumpHeightTask);

        // Spawn task
        startSpawnTask(teamRun, arena);
    }

    /**
     * Freeze all mobs for a specific duration (for Time Freeze power-up)
     */
    public void freezeAllMobs(TeamRun teamRun, int seconds) {
        UUID teamId = getTeamRunId(teamRun);
        Set<LivingEntity> frozen = frozenMobs.getOrDefault(teamId, new HashSet<>());
        
        if (teamRun.getPlayers().isEmpty()) return;
        
        Player firstPlayer = teamRun.getPlayers().get(0);
        if (firstPlayer == null || !firstPlayer.isOnline()) return;
        
        Location center = firstPlayer.getLocation();
        double radius = 100.0;
        
        // Track when time freeze ends (for new spawns)
        long freezeEndTime = System.currentTimeMillis() + (seconds * 1000L);
        timeFreezeEndTime.put(teamId, freezeEndTime);
        
        // Find and freeze all mobs in arena
        for (org.bukkit.entity.Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                LivingEntity mob = (LivingEntity) entity;
                if (!frozen.contains(mob)) {
                    mob.setAI(false); // Disable AI to freeze
                    frozen.add(mob);
                    
                    // Remove all projectiles near the mob
                    for (org.bukkit.entity.Entity nearby : mob.getNearbyEntities(5, 5, 5)) {
                        if (nearby instanceof org.bukkit.entity.Projectile) {
                            nearby.remove();
                        }
                    }
                }
            }
        }
        
        frozenMobs.put(teamId, frozen);
        
        // Unfreeze after duration
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Set<LivingEntity> stillFrozen = frozenMobs.getOrDefault(teamId, new HashSet<>());
            for (LivingEntity mob : new HashSet<>(stillFrozen)) {
                if (mob != null && !mob.isDead() && mob.isValid()) {
                    mob.setAI(true); // Re-enable AI
                }
            }
            // Only remove from frozen set if not in GUI
            if (!teamRun.hasAnyPlayerInGUI()) {
                stillFrozen.clear();
            }
            // Clear time freeze tracking
            timeFreezeEndTime.remove(teamId);
        }, seconds * 20L);
    }
    
    /**
     * Check if time freeze is currently active for a team run
     */
    public boolean isTimeFreezeActive(UUID teamId) {
        Long endTime = timeFreezeEndTime.get(teamId);
        if (endTime == null) {
            return false;
        }
        // Check if freeze time has expired
        if (System.currentTimeMillis() >= endTime) {
            timeFreezeEndTime.remove(teamId);
            return false;
        }
        return true;
    }
    
    public void updateMobFreeze(TeamRun teamRun) {
        UUID teamId = getTeamRunId(teamRun);
        Set<LivingEntity> frozen = frozenMobs.getOrDefault(teamId, new HashSet<>());
        
        // Don't interfere with time freeze power-up - if time freeze is active, don't unfreeze
        boolean timeFreezeActive = isTimeFreezeActive(teamId);
        
        // If any player is in GUI, freeze mobs
        if (teamRun.hasAnyPlayerInGUI()) {
            // Find and freeze all mobs in arena
            if (teamRun.getPlayers().isEmpty()) return;
            
            Player firstPlayer = teamRun.getPlayers().get(0);
            if (firstPlayer == null || !firstPlayer.isOnline()) return;
            
            Location center = firstPlayer.getLocation();
            double radius = 100.0; // Freeze radius
            
            for (org.bukkit.entity.Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    LivingEntity mob = (LivingEntity) entity;
                    if (!frozen.contains(mob)) {
                        mob.setAI(false); // Disable AI to freeze
                        frozen.add(mob);
                    }
                } else if (entity instanceof org.bukkit.entity.Projectile) {
                    // Remove any projectiles (arrows, etc.) from mobs when freezing
                    // This prevents damage from arrows shot right before freeze
                    org.bukkit.entity.Projectile proj = (org.bukkit.entity.Projectile) entity;
                    if (proj.getShooter() instanceof LivingEntity && !(proj.getShooter() instanceof Player)) {
                        proj.remove();
                    }
                }
            }
        } else if (!timeFreezeActive) {
            // Only unfreeze if time freeze is NOT active (don't interfere with time freeze power-up)
            for (LivingEntity mob : frozen) {
                if (mob != null && !mob.isDead()) {
                    mob.setAI(true);
                }
            }
            frozen.clear();
        }
        // If time freeze is active, keep mobs frozen regardless of GUI state
        
        frozenMobs.put(teamId, frozen);
    }

    private void startSpawnTask(TeamRun teamRun, Arena arena) {
        UUID teamId = getTeamRunId(teamRun);
        
        // Track last spawn time to prevent accumulation when GUI is open
        final long[] lastSpawnTime = {System.currentTimeMillis()};

        BukkitTask spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!teamRun.isActive()) {
                return;
            }
            
            // If someone is in GUI, pause spawning but don't accumulate time
            if (teamRun.hasAnyPlayerInGUI()) {
                // Reset last spawn time when GUI opens so we don't accumulate spawns
                // This ensures when GUI closes, we wait the full interval before next spawn
                return;
            }
            
            // Check if enough time has passed since last spawn (10 seconds = 10000ms)
            long timeSinceLastSpawn = System.currentTimeMillis() - lastSpawnTime[0];
            if (timeSinceLastSpawn >= 10000) {
                // Spawn mobs for current wave
                spawnWaveMobs(teamRun, arena);
                lastSpawnTime[0] = System.currentTimeMillis(); // Update last spawn time
            }

        }, 100L, 20L); // Check every second instead of every 10 seconds for more responsive pausing

        spawnTasks.put(teamId, spawnTask);
    }

    private void spawnWaveMobs(TeamRun teamRun, Arena arena) {
        if (arena.getCenter() == null) return;

        List<com.eldor.roguecraft.managers.SpawnManager.SpawnEntry> spawns = 
            plugin.getSpawnManager().getSpawnsForWave(teamRun.getWave());

        int playerCount = teamRun.getPlayerCount();
        
        for (com.eldor.roguecraft.managers.SpawnManager.SpawnEntry entry : spawns) {
            // Scale spawn count with player count
            int spawnCount = (int) (entry.getCount() * (1.0 + (playerCount - 1) * 0.5));
            
                for (int i = 0; i < spawnCount; i++) {
                Location spawnLoc = getRandomSpawnLocation(arena);
                if (spawnLoc != null) {
                    // Mark location BEFORE spawning so WorldGuardListener can detect it
                    // Use a slight delay to ensure the event fires before cleanup
                    Location spawnLocClone = spawnLoc.clone();
                    addSpawnLocation(spawnLocClone);
                    
                    org.bukkit.entity.Entity entity = null;
                    try {
                        entity = spawnLoc.getWorld().spawnEntity(spawnLoc, entry.getType());
                        
                        // Mark entity as plugin-spawned for WorldGuard compatibility
                        if (entity != null) {
                            entity.setMetadata("roguecraft_spawned", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                        } else {
                            plugin.getLogger().warning("Failed to spawn entity at " + spawnLoc + " - spawnEntity returned null");
                            continue; // Skip to next spawn attempt
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Exception spawning mob at " + spawnLoc + ": " + e.getMessage());
                        e.printStackTrace();
                        continue; // Skip to next spawn attempt
                    }
                    
                    if (entity instanceof LivingEntity) {
                        LivingEntity mob = (LivingEntity) entity;
                        
                        // Check if time freeze is active OR if any player is in GUI - freeze new spawns
                        UUID teamId = getTeamRunId(teamRun);
                        if (isTimeFreezeActive(teamId) || teamRun.hasAnyPlayerInGUI()) {
                            mob.setAI(false); // Freeze immediately
                            Set<LivingEntity> frozen = frozenMobs.getOrDefault(teamId, new HashSet<>());
                            frozen.add(mob);
                            frozenMobs.put(teamId, frozen);
                        }
                        
                        // Check if this is an elite mob
                        boolean isElite = entry.isElite();
                        
                        // Check if elite should become legendary (rarer than elite)
                        boolean isLegendary = false;
                        if (isElite) {
                            double legendaryChance = plugin.getConfigManager().getBalanceConfig().getDouble("legendary.spawn-chance", 0.15);
                            if (Math.random() < legendaryChance) {
                                isLegendary = true;
                            }
                        }
                        
                        // Scale mob HP based on player count, difficulty, and wave
                        double hpMultiplier = plugin.getDifficultyManager().getMobHealthMultiplier(teamRun.getDifficultyMultiplier());
                        hpMultiplier *= (1.0 + (playerCount - 1) * 0.3); // 30% HP per additional player
                        // Add wave-based scaling (enemies get tankier each wave)
                        // Slower scaling for early waves (waves 1-5: 5% per wave, waves 6-10: 10% per wave, 11+: 15% per wave)
                        int wave = teamRun.getWave();
                        double waveMultiplier;
                        if (wave <= 5) {
                            // First 5 waves: 5% per wave (much slower)
                            waveMultiplier = 1.0 + (wave * 0.05);
                        } else if (wave <= 10) {
                            // Waves 6-10: 10% per wave (moderate)
                            waveMultiplier = 1.0 + (5 * 0.05) + ((wave - 5) * 0.10); // 1.25 base from first 5 waves
                        } else {
                            // Waves 11+: 15% per wave (normal scaling)
                            waveMultiplier = 1.0 + (5 * 0.05) + (5 * 0.10) + ((wave - 10) * 0.15); // 1.75 base from first 10 waves
                        }
                        hpMultiplier *= waveMultiplier;
                        
                        // Apply elite bonuses (reduced HP multiplier to balance with armor)
                        if (isElite) {
                            double eliteHpMultiplier = plugin.getConfigManager().getBalanceConfig().getDouble("elites.hp-multiplier", 2.0);
                            // Reduce HP multiplier for later waves (wave 10+) since they get armor
                            if (teamRun.getWave() >= 10) {
                                eliteHpMultiplier *= 0.7; // 30% reduction when armor is present
                            }
                            hpMultiplier *= eliteHpMultiplier;
                            
                            // Apply legendary HP multiplier on top of elite
                            if (isLegendary) {
                                double legendaryHpMultiplier = plugin.getConfigManager().getBalanceConfig().getDouble("legendary.hp-multiplier", 1.5);
                                hpMultiplier *= legendaryHpMultiplier;
                            }
                        }
                        
                        // Cap health at Minecraft's maximum (2048.0) to prevent errors
                        double newMaxHealth = mob.getMaxHealth() * hpMultiplier;
                        double finalMaxHealth = Math.min(2048.0, newMaxHealth);
                        mob.setMaxHealth(finalMaxHealth);
                        mob.setHealth(finalMaxHealth);
                        
                        // Apply elite/legendary bonuses
                        if (isLegendary) {
                            applyLegendaryScaling(mob);
                            // Legendary damage resistance is handled in PlayerListener.onEntityDamage
                        } else if (isElite) {
                            applyEliteScaling(mob);
                            // Elite damage resistance is now handled in PlayerListener.onEntityDamage
                            // (replaced armor system with scaling resistance modifier)
                        }
                        
                        // Tag undead mobs so we can prevent sunlight damage
                        if (mob instanceof org.bukkit.entity.Zombie || 
                            mob instanceof org.bukkit.entity.Skeleton ||
                            mob instanceof org.bukkit.entity.Stray ||
                            mob instanceof org.bukkit.entity.Husk ||
                            mob instanceof org.bukkit.entity.Drowned ||
                            mob instanceof org.bukkit.entity.WitherSkeleton ||
                            mob instanceof org.bukkit.entity.Phantom) {
                            // Tag as roguecraft mob so we can prevent sunlight damage
                            mob.setMetadata("roguecraft_mob", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                        }
                        
                        // Make creepers explode faster (reduced fuse time)
                        if (mob instanceof org.bukkit.entity.Creeper) {
                            org.bukkit.entity.Creeper creeper = (org.bukkit.entity.Creeper) mob;
                            // Set max fuse ticks to 20 (1 second) instead of default 30 (1.5 seconds)
                            // This makes creepers explode faster and more dangerous
                            creeper.setMaxFuseTicks(10);
                            // Tag as roguecraft mob for tracking
                            mob.setMetadata("roguecraft_mob", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                        }
                        
                        // Set up health display for all mobs
                        updateMobHealthDisplay(mob);
                        
                        // Scale mob movement speed with wave number and difficulty
                        applyMobSpeedScaling(mob, teamRun.getWave(), teamRun.getDifficultyMultiplier());
                        
                        // Set mob to target nearest player for better pathfinding
                        if (!teamRun.getPlayers().isEmpty()) {
                            Player nearestPlayer = null;
                            double nearestDistance = Double.MAX_VALUE;
                            for (Player player : teamRun.getPlayers()) {
                                if (player != null && player.isOnline() && !player.isDead()) {
                                    double dist = player.getLocation().distance(mob.getLocation());
                                    if (dist < nearestDistance) {
                                        nearestDistance = dist;
                                        nearestPlayer = player;
                                    }
                                }
                            }
                            
                            // Set target if within reasonable range (mob will pathfind naturally after)
                            if (nearestPlayer != null && nearestDistance < 100) {
                                // For mobs that can have targets (like Zombie, Skeleton, etc.)
                                if (mob instanceof org.bukkit.entity.Mob) {
                                    ((org.bukkit.entity.Mob) mob).setTarget(nearestPlayer);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Spawn Wither boss for solo run (used by boss shrine)
     */
    public void spawnWitherBossForSoloRun(Run run, Arena arena) {
        if (arena.getCenter() == null) return;
        
        // Spawn Wither at arena center (or slightly offset)
        Location spawnLoc = arena.getCenter().clone();
        if (spawnLoc.getWorld() == null) return;
        
        // Find safe Y position (Wither needs space above)
        org.bukkit.World world = spawnLoc.getWorld();
        org.bukkit.block.Block block = world.getBlockAt(spawnLoc);
        while (block.getType().isSolid() && spawnLoc.getY() < world.getMaxHeight()) {
            spawnLoc.add(0, 1, 0);
            block = world.getBlockAt(spawnLoc);
        }
        // Ensure we're on solid ground
        while (!block.getType().isSolid() && spawnLoc.getY() > world.getMinHeight()) {
            spawnLoc.subtract(0, 1, 0);
            block = world.getBlockAt(spawnLoc);
        }
        spawnLoc.add(0, 2, 0); // Spawn 2 blocks above ground (Wither needs space)
        
        // Mark location BEFORE spawning so WorldGuardListener can detect it
        addSpawnLocation(spawnLoc);
        
        try {
            org.bukkit.entity.Entity entity = spawnLoc.getWorld().spawnEntity(spawnLoc, org.bukkit.entity.EntityType.WITHER);
            
            if (entity instanceof org.bukkit.entity.LivingEntity) {
                org.bukkit.entity.LivingEntity wither = (org.bukkit.entity.LivingEntity) entity;
                
                // Mark as plugin-spawned and boss
                wither.setMetadata("roguecraft_spawned", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                wither.setMetadata("roguecraft_boss", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                wither.setMetadata("roguecraft_mob", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                wither.setMetadata("roguecraft_elite_boss", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                
                // Calculate scaled health based on level and difficulty
                int playerLevel = run.getLevel();
                double difficulty = run.getDifficultyMultiplier();
                
                // Base health: 600 HP
                double baseHealth = 600.0;
                
                // Scale with level (25 HP per level)
                double levelHealth = baseHealth + (playerLevel * 25.0);
                
                // Scale with difficulty (0.3 multiplier)
                double difficultyHealth = levelHealth * (1.0 + (difficulty - 1.0) * 0.3);
                
                // Solo run - no multiplayer scaling
                double multiplayerHealth = difficultyHealth;
                
                // Apply wave-based scaling
                int wave = run.getWave();
                double waveMultiplier;
                if (wave <= 5) {
                    waveMultiplier = 1.0 + (wave * 0.05);
                } else if (wave <= 10) {
                    waveMultiplier = 1.0 + (5 * 0.05) + ((wave - 5) * 0.10);
                } else {
                    waveMultiplier = 1.0 + (5 * 0.05) + (5 * 0.10) + ((wave - 10) * 0.15);
                }
                double waveHealth = multiplayerHealth * waveMultiplier;
                
                // Cap health at Minecraft's maximum (2048.0)
                double finalHealth = Math.min(2048.0, waveHealth);
                
                wither.setMaxHealth(finalHealth);
                wither.setHealth(finalHealth);
                
                // Apply elite boss scaling
                applyEliteBossScaling(wither);
                
                // Set custom name
                wither.setCustomName(org.bukkit.ChatColor.DARK_RED + "" + org.bukkit.ChatColor.BOLD + "☠ BOSS: WITHER ☠");
                wither.setCustomNameVisible(true);
                
                // Update health display
                updateMobHealthDisplay(wither);
                
                // Make Wither target player
                Player player = run.getPlayer();
                if (player != null && player.isOnline() && !player.isDead()) {
                    double dist = player.getLocation().distance(wither.getLocation());
                    if (dist < 100) {
                        if (wither instanceof org.bukkit.entity.Mob) {
                            ((org.bukkit.entity.Mob) wither).setTarget(player);
                        } else if (wither instanceof org.bukkit.entity.Wither) {
                            ((org.bukkit.entity.Wither) wither).setTarget(player);
                        }
                    }
                }
                
                // Start red particle effect task (create a wrapper for solo runs)
                startBossParticleEffectForSolo(wither, run);
                
                // Start boss targeting task
                startBossTargetingTaskForSolo(wither, run);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn Wither boss for solo run: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void spawnWitherBoss(TeamRun teamRun, Arena arena) {
        if (arena.getCenter() == null) return;
        
        // Spawn Wither at arena center (or slightly offset)
        Location spawnLoc = arena.getCenter().clone();
        if (spawnLoc.getWorld() == null) return;
        
        // Find safe Y position (Wither needs space above)
        org.bukkit.World world = spawnLoc.getWorld();
        org.bukkit.block.Block block = world.getBlockAt(spawnLoc);
        while (block.getType().isSolid() && spawnLoc.getY() < world.getMaxHeight()) {
            spawnLoc.add(0, 1, 0);
            block = world.getBlockAt(spawnLoc);
        }
        // Ensure we're on solid ground
        while (!block.getType().isSolid() && spawnLoc.getY() > world.getMinHeight()) {
            spawnLoc.subtract(0, 1, 0);
            block = world.getBlockAt(spawnLoc);
        }
        spawnLoc.add(0, 2, 0); // Spawn 2 blocks above ground (Wither needs space)
        
        // Mark location BEFORE spawning so WorldGuardListener can detect it
        addSpawnLocation(spawnLoc);
        
        try {
            org.bukkit.entity.Entity entity = spawnLoc.getWorld().spawnEntity(spawnLoc, org.bukkit.entity.EntityType.WITHER);
            
            if (entity instanceof org.bukkit.entity.LivingEntity) {
                org.bukkit.entity.LivingEntity wither = (org.bukkit.entity.LivingEntity) entity;
                
                // Mark as plugin-spawned and boss
                wither.setMetadata("roguecraft_spawned", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                wither.setMetadata("roguecraft_boss", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                wither.setMetadata("roguecraft_mob", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                wither.setMetadata("roguecraft_elite_boss", new org.bukkit.metadata.FixedMetadataValue(plugin, true)); // Special elite boss tag
                
                // Calculate scaled health based on level and difficulty
                int playerLevel = teamRun.getLevel();
                double difficulty = teamRun.getDifficultyMultiplier();
                int playerCount = teamRun.getPlayerCount();
                
                // Base health: 600 HP (Wither's default is 600)
                double baseHealth = 600.0;
                
                // Scale with level (reduced from 50 to 25 HP per level)
                double levelHealth = baseHealth + (playerLevel * 25.0);
                
                // Scale with difficulty (reduced from 0.5 to 0.3 multiplier)
                double difficultyHealth = levelHealth * (1.0 + (difficulty - 1.0) * 0.3);
                
                // Scale with player count (reduced from 20% to 15% per additional player)
                double multiplayerHealth = difficultyHealth * (1.0 + (playerCount - 1) * 0.15);
                
                // Apply wave-based scaling (slower for early waves, same as regular mobs)
                int wave = teamRun.getWave();
                double waveMultiplier;
                if (wave <= 5) {
                    // First 5 waves: 5% per wave
                    waveMultiplier = 1.0 + (wave * 0.05);
                } else if (wave <= 10) {
                    // Waves 6-10: 10% per wave
                    waveMultiplier = 1.0 + (5 * 0.05) + ((wave - 5) * 0.10);
                } else {
                    // Waves 11+: 15% per wave
                    waveMultiplier = 1.0 + (5 * 0.05) + (5 * 0.10) + ((wave - 10) * 0.15);
                }
                double waveHealth = multiplayerHealth * waveMultiplier;
                
                // Cap health at Minecraft's maximum (2048.0)
                double finalHealth = Math.min(2048.0, waveHealth);
                
                wither.setMaxHealth(finalHealth);
                wither.setHealth(finalHealth);
                
                // Apply elite boss scaling (2.5x size, red visual effects)
                applyEliteBossScaling(wither);
                
                // Set custom name with red color
                wither.setCustomName(org.bukkit.ChatColor.DARK_RED + "" + org.bukkit.ChatColor.BOLD + "☠ BOSS: WITHER ☠");
                wither.setCustomNameVisible(true);
                
                // Update health display
                updateMobHealthDisplay(wither);
                
                // Make Wither target nearest player
                if (!teamRun.getPlayers().isEmpty()) {
                    Player nearestPlayer = null;
                    double nearestDistance = Double.MAX_VALUE;
                    for (Player player : teamRun.getPlayers()) {
                        if (player != null && player.isOnline() && !player.isDead()) {
                            double dist = player.getLocation().distance(wither.getLocation());
                            if (dist < nearestDistance) {
                                nearestDistance = dist;
                                nearestPlayer = player;
                            }
                        }
                    }
                    
                    // Set target if within reasonable range
                    if (nearestPlayer != null && nearestDistance < 100) {
                        if (wither instanceof org.bukkit.entity.Mob) {
                            ((org.bukkit.entity.Mob) wither).setTarget(nearestPlayer);
                        } else if (wither instanceof org.bukkit.entity.Wither) {
                            // Wither has special targeting - set target using reflection if needed
                            ((org.bukkit.entity.Wither) wither).setTarget(nearestPlayer);
                        }
                    }
                }
                
                // Start red particle effect task
                startBossParticleEffect(wither, teamRun);
                
                // Start boss targeting task (keeps Wither aggro on players)
                startBossTargetingTask(wither, teamRun);
                
                // Notify all players
                for (org.bukkit.entity.Player player : teamRun.getPlayers()) {
                    if (player != null && player.isOnline()) {
                        player.sendMessage(org.bukkit.ChatColor.DARK_RED + "" + org.bukkit.ChatColor.BOLD + "☠ THE WITHER HAS AWAKENED! ☠");
                        player.sendMessage(org.bukkit.ChatColor.GRAY + "The final boss has spawned!");
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn Wither boss: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Apply elite boss scaling (2.5x size, red visual effects)
     */
    private void applyEliteBossScaling(LivingEntity mob) {
        try {
            // 2.5x size multiplier for boss
            double sizeMultiplier = 2.5;
            
            // Visual indicators - red glowing effect (using red particles instead of white glow)
            mob.setGlowing(true); // Still use glowing for visibility, but we'll add red particles
            
            // Store original name for health display
            String originalName = mob.getType().name().replace("_", " ");
            mob.setMetadata("original_name", new org.bukkit.metadata.FixedMetadataValue(plugin, originalName));
            mob.setMetadata("is_elite", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
            mob.setMetadata("is_elite_boss", new org.bukkit.metadata.FixedMetadataValue(plugin, true)); // Special tag for red effects
            
            // Try to scale entity size using Bukkit Attribute API
            try {
                org.bukkit.attribute.Attribute scaleAttr = org.bukkit.attribute.Attribute.GENERIC_SCALE;
                org.bukkit.attribute.AttributeInstance scaleInstance = mob.getAttribute(scaleAttr);
                
                if (scaleInstance != null) {
                    scaleInstance.setBaseValue(sizeMultiplier);
                    plugin.getLogger().fine("Applied elite boss size scaling via SCALE attribute: " + sizeMultiplier);
                    return;
                }
            } catch (Exception e) {
                plugin.getLogger().fine("SCALE attribute not available, trying NMS reflection: " + e.getMessage());
            }
            
            // Fallback: Try to scale entity size using NMS reflection (same as applyEliteScaling)
            try {
                Object craftEntity = mob.getClass().getMethod("getHandle").invoke(mob);
                Class<?> entityClass = craftEntity.getClass().getSuperclass();
                
                try {
                    java.lang.reflect.Method setScaleMethod = null;
                    try {
                        setScaleMethod = entityClass.getMethod("setScale", float.class);
                    } catch (NoSuchMethodException e) {
                        try {
                            setScaleMethod = craftEntity.getClass().getMethod("setScale", float.class);
                        } catch (NoSuchMethodException e2) {
                            try {
                                Class<?> entityClass2 = craftEntity.getClass();
                                setScaleMethod = entityClass2.getMethod("a", float.class);
                            } catch (NoSuchMethodException e3) {}
                        }
                    }
                    
                    if (setScaleMethod != null) {
                        setScaleMethod.invoke(craftEntity, (float) sizeMultiplier);
                        plugin.getLogger().fine("Applied elite boss size scaling via setScale method");
                        return;
                    }
                    
                    // Try field access
                    try {
                        java.lang.reflect.Field scaleField = entityClass.getDeclaredField("scale");
                        scaleField.setAccessible(true);
                        scaleField.set(craftEntity, (float) sizeMultiplier);
                        plugin.getLogger().fine("Applied elite boss size scaling via scale field");
                        return;
                    } catch (NoSuchFieldException e) {
                        try {
                            java.lang.reflect.Field scaleField = entityClass.getDeclaredField("dataScale");
                            scaleField.setAccessible(true);
                            scaleField.set(craftEntity, (float) sizeMultiplier);
                            plugin.getLogger().fine("Applied elite boss size scaling via dataScale field");
                            return;
                        } catch (NoSuchFieldException e2) {
                            // Try obfuscated field names
                            for (java.lang.reflect.Field field : entityClass.getDeclaredFields()) {
                                if (field.getType() == float.class || field.getType() == Float.class) {
                                    try {
                                        field.setAccessible(true);
                                        float currentValue = field.getFloat(craftEntity);
                                        if (currentValue >= 0.5f && currentValue <= 2.0f) {
                                            field.set(craftEntity, (float) sizeMultiplier);
                                            plugin.getLogger().fine("Applied elite boss size scaling via detected scale field");
                                            return;
                                        }
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().fine("NMS size scaling failed: " + e.getMessage());
                }
            } catch (Exception e) {
                plugin.getLogger().fine("Reflection access failed for elite boss size scaling: " + e.getMessage());
            }
            
        } catch (Exception e) {
            plugin.getLogger().fine("Failed to apply elite boss scaling: " + e.getMessage());
        }
    }
    
    /**
     * Start red particle effect around boss
     */
    private void startBossParticleEffect(LivingEntity boss, TeamRun teamRun) {
        if (boss == null || boss.isDead()) return;
        
        // Create task to spawn red particles around boss every 0.5 seconds
        final int[] taskId = new int[1];
        taskId[0] = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (boss.isDead() || !boss.isValid()) {
                    plugin.getServer().getScheduler().cancelTask(taskId[0]);
                    return;
                }
                
                // Spawn red particles around the boss
                Location loc = boss.getLocation();
                for (int i = 0; i < 8; i++) {
                    double angle = (i * Math.PI * 2) / 8;
                    double radius = 1.5;
                    double x = loc.getX() + Math.cos(angle) * radius;
                    double y = loc.getY() + 1.0;
                    double z = loc.getZ() + Math.sin(angle) * radius;
                    
                    Location particleLoc = new Location(loc.getWorld(), x, y, z);
                    loc.getWorld().spawnParticle(org.bukkit.Particle.DUST, particleLoc, 1, 
                        new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.0f));
                }
            }
        }, 0L, 10L); // Every 0.5 seconds (10 ticks)
        
        // Store task ID for cleanup
        boss.setMetadata("boss_particle_task", new org.bukkit.metadata.FixedMetadataValue(plugin, taskId[0]));
    }
    
    /**
     * Start boss targeting task to keep Wither aggro on players
     */
    private void startBossTargetingTask(LivingEntity boss, TeamRun teamRun) {
        if (boss == null || boss.isDead()) return;
        
        // Create task to update boss target every 2 seconds
        final int[] taskId = new int[1];
        taskId[0] = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (boss.isDead() || !boss.isValid()) {
                    plugin.getServer().getScheduler().cancelTask(taskId[0]);
                    return;
                }
                
                // Find nearest player and set as target
                if (!teamRun.getPlayers().isEmpty()) {
                    Player nearestPlayer = null;
                    double nearestDistance = Double.MAX_VALUE;
                    for (Player player : teamRun.getPlayers()) {
                        if (player != null && player.isOnline() && !player.isDead()) {
                            double dist = player.getLocation().distance(boss.getLocation());
                            if (dist < nearestDistance) {
                                nearestDistance = dist;
                                nearestPlayer = player;
                            }
                        }
                    }
                    
                    // Set target if within reasonable range
                    if (nearestPlayer != null && nearestDistance < 100) {
                        if (boss instanceof org.bukkit.entity.Mob) {
                            ((org.bukkit.entity.Mob) boss).setTarget(nearestPlayer);
                        } else if (boss instanceof org.bukkit.entity.Wither) {
                            ((org.bukkit.entity.Wither) boss).setTarget(nearestPlayer);
                        }
                    }
                }
            }
        }, 0L, 40L); // Every 2 seconds (40 ticks)
        
        // Store task ID for cleanup
        boss.setMetadata("boss_targeting_task", new org.bukkit.metadata.FixedMetadataValue(plugin, taskId[0]));
    }
    
    /**
     * Start boss particle effect for solo run
     */
    private void startBossParticleEffectForSolo(LivingEntity boss, Run run) {
        if (boss == null || boss.isDead()) return;
        
        // Create task to spawn red particles around boss every 0.5 seconds
        final int[] taskId = new int[1];
        taskId[0] = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (boss.isDead() || !boss.isValid()) {
                    plugin.getServer().getScheduler().cancelTask(taskId[0]);
                    return;
                }
                
                // Spawn red particles around the boss
                Location loc = boss.getLocation();
                for (int i = 0; i < 8; i++) {
                    double angle = (i * Math.PI * 2) / 8;
                    double radius = 1.5;
                    double x = loc.getX() + Math.cos(angle) * radius;
                    double y = loc.getY() + 1.0;
                    double z = loc.getZ() + Math.sin(angle) * radius;
                    
                    Location particleLoc = new Location(loc.getWorld(), x, y, z);
                    loc.getWorld().spawnParticle(org.bukkit.Particle.DUST, particleLoc, 1, 
                        new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.0f));
                }
            }
        }, 0L, 10L); // Every 0.5 seconds (10 ticks)
        
        // Store task ID for cleanup
        boss.setMetadata("boss_particle_task", new org.bukkit.metadata.FixedMetadataValue(plugin, taskId[0]));
    }
    
    /**
     * Start boss targeting task for solo run
     */
    private void startBossTargetingTaskForSolo(LivingEntity boss, Run run) {
        if (boss == null || boss.isDead()) return;
        
        // Create task to update boss target every 2 seconds
        final int[] taskId = new int[1];
        taskId[0] = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (boss.isDead() || !boss.isValid()) {
                    plugin.getServer().getScheduler().cancelTask(taskId[0]);
                    return;
                }
                
                // Find player and set as target
                Player player = run.getPlayer();
                if (player != null && player.isOnline() && !player.isDead()) {
                    double dist = player.getLocation().distance(boss.getLocation());
                    if (dist < 100) {
                        if (boss instanceof org.bukkit.entity.Mob) {
                            ((org.bukkit.entity.Mob) boss).setTarget(player);
                        } else if (boss instanceof org.bukkit.entity.Wither) {
                            ((org.bukkit.entity.Wither) boss).setTarget(player);
                        }
                    }
                }
            }
        }, 0L, 40L); // Every 2 seconds (40 ticks)
        
        // Store task ID for cleanup
        boss.setMetadata("boss_targeting_task", new org.bukkit.metadata.FixedMetadataValue(plugin, taskId[0]));
    }
    
    /**
     * Start legendary particle effect around legendary mob (golden glow)
     */
    private void startLegendaryParticleEffect(LivingEntity legendary) {
        if (legendary == null || legendary.isDead()) return;
        
        // Create task to spawn golden particles around legendary mob every 0.3 seconds for a golden glow effect
        final int[] taskId = new int[1];
        taskId[0] = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (legendary.isDead() || !legendary.isValid()) {
                    plugin.getServer().getScheduler().cancelTask(taskId[0]);
                    return;
                }
                
                // Spawn golden particles around the legendary mob for a golden glow
                Location loc = legendary.getLocation();
                // Create a golden aura with more particles
                for (int i = 0; i < 12; i++) {
                    double angle = (i * Math.PI * 2) / 12;
                    double radius = 1.0 + (Math.random() * 0.4); // Slight variation in radius
                    double x = loc.getX() + Math.cos(angle) * radius;
                    double y = loc.getY() + 0.5 + (Math.random() * 1.0); // Vary height
                    double z = loc.getZ() + Math.sin(angle) * radius;
                    
                    Location particleLoc = new Location(loc.getWorld(), x, y, z);
                    // Use golden/yellow particles for a gold glow effect
                    // Mix of bright gold and yellow for a glowing effect
                    org.bukkit.Color goldColor = Math.random() < 0.7 ? 
                        org.bukkit.Color.fromRGB(255, 215, 0) : // Gold
                        org.bukkit.Color.fromRGB(255, 255, 0); // Bright yellow
                    loc.getWorld().spawnParticle(org.bukkit.Particle.DUST, particleLoc, 1, 
                        new org.bukkit.Particle.DustOptions(goldColor, 1.2f));
                }
                
                // Also spawn some particles above the mob for a golden aura
                Location topLoc = loc.clone().add(0, 1.5, 0);
                for (int i = 0; i < 4; i++) {
                    double offsetX = (Math.random() - 0.5) * 0.8;
                    double offsetZ = (Math.random() - 0.5) * 0.8;
                    Location auraLoc = topLoc.clone().add(offsetX, 0, offsetZ);
                    topLoc.getWorld().spawnParticle(org.bukkit.Particle.DUST, auraLoc, 1, 
                        new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255, 215, 0), 1.5f)); // Gold
                }
            }
        }, 0L, 6L); // Every 0.3 seconds (6 ticks) for smoother glow
        
        // Store task ID for cleanup
        legendary.setMetadata("legendary_particle_task", new org.bukkit.metadata.FixedMetadataValue(plugin, taskId[0]));
    }

    private Location getRandomSpawnLocation(Arena arena) {
        if (arena.getCenter() == null) return null;

        Random random = new Random();
        
        // Spawn mobs closer to players, but still around the arena edge
        // Use 60-90% of radius to ensure they're within pathfinding range (16 blocks default, but can be extended)
        // This ensures mobs spawn within reasonable pathfinding distance
        double minDistance = arena.getRadius() * 0.6;
        double maxDistance = arena.getRadius() * 0.9;
        double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
        
        double angle = random.nextDouble() * 2 * Math.PI;
        double x = arena.getCenter().getX() + Math.cos(angle) * distance;
        double z = arena.getCenter().getZ() + Math.sin(angle) * distance;
        double y = arena.getCenter().getY();

        Location spawnLoc = new Location(arena.getCenter().getWorld(), x, y, z);
        
        // Find a safe Y position (not in air or solid block)
        org.bukkit.World world = spawnLoc.getWorld();
        if (world != null) {
            org.bukkit.block.Block block = world.getBlockAt(spawnLoc);
            org.bukkit.block.Block below = world.getBlockAt(spawnLoc.clone().add(0, -1, 0));
            
            // If spawn location is in air, find ground
            if (block.getType() == org.bukkit.Material.AIR && below.getType() != org.bukkit.Material.AIR) {
                // Already on ground
            } else if (block.getType() == org.bukkit.Material.AIR) {
                // Find ground below
                for (int i = 1; i <= 10; i++) {
                    org.bukkit.block.Block check = world.getBlockAt(spawnLoc.clone().add(0, -i, 0));
                    if (check.getType() != org.bukkit.Material.AIR) {
                        spawnLoc.setY(spawnLoc.getY() - i + 1);
                        break;
                    }
                }
            } else if (block.getType().isSolid()) {
                // Find air above
                for (int i = 1; i <= 10; i++) {
                    org.bukkit.block.Block check = world.getBlockAt(spawnLoc.clone().add(0, i, 0));
                    if (check.getType() == org.bukkit.Material.AIR) {
                        spawnLoc.setY(spawnLoc.getY() + i);
                        break;
                    }
                }
            }
        }
        
        return spawnLoc;
    }
    
    /**
     * Apply elite mob scaling (size, visual effects)
     */
    private void applyEliteScaling(LivingEntity mob) {
        try {
            // Get size multiplier from config
            double sizeMultiplier = plugin.getConfigManager().getBalanceConfig().getDouble("elites.size-multiplier", 4.0);
            
            // Visual indicators
            mob.setGlowing(true);
            // Store original name for health display
            String originalName = mob.getType().name().replace("_", " ");
            mob.setMetadata("original_name", new org.bukkit.metadata.FixedMetadataValue(plugin, originalName));
            mob.setMetadata("is_elite", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
            // Health display will be updated by the periodic task
            
            // Try to scale entity size using Bukkit Attribute API (available in 1.20.5+)
            try {
                // Try using SCALE attribute if available
                org.bukkit.attribute.Attribute scaleAttr = org.bukkit.attribute.Attribute.GENERIC_SCALE;
                org.bukkit.attribute.AttributeInstance scaleInstance = mob.getAttribute(scaleAttr);
                
                if (scaleInstance != null) {
                    // Set scale using attribute (base scale is 1.0, so multiply by sizeMultiplier)
                    scaleInstance.setBaseValue(sizeMultiplier);
                    plugin.getLogger().fine("Applied elite size scaling via SCALE attribute: " + sizeMultiplier);
                    return;
                }
            } catch (Exception e) {
                // SCALE attribute not available, try NMS reflection
                plugin.getLogger().fine("SCALE attribute not available, trying NMS reflection: " + e.getMessage());
            }
            
            // Fallback: Try to scale entity size using NMS reflection
            try {
                // Get NMS entity
                Object craftEntity = mob.getClass().getMethod("getHandle").invoke(mob);
                Class<?> entityClass = craftEntity.getClass().getSuperclass();
                
                // Try to find and set the scale field/method
                try {
                    // Method 1: Try to find setScale method (if available in newer versions)
                    java.lang.reflect.Method setScaleMethod = null;
                    try {
                        setScaleMethod = entityClass.getMethod("setScale", float.class);
                    } catch (NoSuchMethodException e) {
                        // Try alternative method names
                        try {
                            setScaleMethod = craftEntity.getClass().getMethod("setScale", float.class);
                        } catch (NoSuchMethodException e2) {
                            // Try EntityData API (1.20.5+)
                            try {
                                Class<?> entityClass2 = craftEntity.getClass();
                                setScaleMethod = entityClass2.getMethod("a", float.class); // setScale in obfuscated
                            } catch (NoSuchMethodException e3) {
                                // Method doesn't exist, try field access
                            }
                        }
                    }
                    
                    if (setScaleMethod != null) {
                        setScaleMethod.invoke(craftEntity, (float) sizeMultiplier);
                        plugin.getLogger().fine("Applied elite size scaling via setScale method");
                        return;
                    }
                    
                    // Method 2: Try to access scale field directly
                    try {
                        java.lang.reflect.Field scaleField = entityClass.getDeclaredField("scale");
                        scaleField.setAccessible(true);
                        scaleField.set(craftEntity, (float) sizeMultiplier);
                        plugin.getLogger().fine("Applied elite size scaling via scale field");
                        return;
                    } catch (NoSuchFieldException e) {
                        // Try alternative field names
                        try {
                            java.lang.reflect.Field scaleField = entityClass.getDeclaredField("dataScale");
                            scaleField.setAccessible(true);
                            scaleField.set(craftEntity, (float) sizeMultiplier);
                            plugin.getLogger().fine("Applied elite size scaling via dataScale field");
                            return;
                        } catch (NoSuchFieldException e2) {
                            // Try obfuscated field names
                            for (java.lang.reflect.Field field : entityClass.getDeclaredFields()) {
                                if (field.getType() == float.class || field.getType() == Float.class) {
                                    try {
                                        field.setAccessible(true);
                                        float currentValue = field.getFloat(craftEntity);
                                        if (currentValue >= 0.5f && currentValue <= 2.0f) { // Likely scale field
                                            field.set(craftEntity, (float) sizeMultiplier);
                                            plugin.getLogger().fine("Applied elite size scaling via detected scale field");
                                            return;
                                        }
                                    } catch (Exception ignored) {}
                                }
                            }
                            plugin.getLogger().fine("Entity size scaling not available - using visual indicators only");
                        }
                    }
                } catch (Exception e) {
                    // NMS access failed, use visual indicators only
                    plugin.getLogger().fine("NMS size scaling failed: " + e.getMessage());
                }
            } catch (Exception e) {
                // Reflection failed completely, use visual indicators only
                plugin.getLogger().fine("Reflection access failed for elite size scaling: " + e.getMessage());
            }
            
        } catch (Exception e) {
            // Only log at fine level to avoid spam - elite scaling failures are not critical
            plugin.getLogger().fine("Failed to apply elite scaling: " + e.getMessage());
        }
    }
    
    /**
     * Apply legendary mob scaling (size, visual effects)
     * Legendary mobs are rarer and stronger than elites
     */
    private void applyLegendaryScaling(LivingEntity mob) {
        try {
            // Get size multiplier from config (2.1x - 0.4x bigger than elite)
            double sizeMultiplier = plugin.getConfigManager().getBalanceConfig().getDouble("legendary.size-multiplier", 2.1);
            
            // Visual indicators - legendary gets golden glow effect using team color
            mob.setGlowing(true);
            // Set gold glow color using scoreboard team
            setGoldGlowColor(mob);
            // Store original name for health display
            String originalName = mob.getType().name().replace("_", " ");
            mob.setMetadata("original_name", new org.bukkit.metadata.FixedMetadataValue(plugin, originalName));
            mob.setMetadata("is_elite", new org.bukkit.metadata.FixedMetadataValue(plugin, true)); // Legendary is also elite
            mob.setMetadata("is_legendary", new org.bukkit.metadata.FixedMetadataValue(plugin, true)); // Special legendary tag
            mob.setMetadata("roguecraft_legendary", new org.bukkit.metadata.FixedMetadataValue(plugin, true)); // For easy checking
            // Health display will be updated by the periodic task
            
            // Start legendary particle effect (golden/purple particles)
            startLegendaryParticleEffect(mob);
            
            // Try to scale entity size using Bukkit Attribute API (available in 1.20.5+)
            try {
                // Try using SCALE attribute if available
                org.bukkit.attribute.Attribute scaleAttr = org.bukkit.attribute.Attribute.GENERIC_SCALE;
                org.bukkit.attribute.AttributeInstance scaleInstance = mob.getAttribute(scaleAttr);
                
                if (scaleInstance != null) {
                    // Set scale using attribute (base scale is 1.0, so multiply by sizeMultiplier)
                    scaleInstance.setBaseValue(sizeMultiplier);
                    plugin.getLogger().fine("Applied legendary size scaling via SCALE attribute: " + sizeMultiplier);
                    return;
                }
            } catch (Exception e) {
                // SCALE attribute not available, try NMS reflection
                plugin.getLogger().fine("SCALE attribute not available, trying NMS reflection: " + e.getMessage());
            }
            
            // Fallback: Try to scale entity size using NMS reflection (same as applyEliteScaling)
            try {
                // Get NMS entity
                Object craftEntity = mob.getClass().getMethod("getHandle").invoke(mob);
                Class<?> entityClass = craftEntity.getClass().getSuperclass();
                
                // Try to find and set the scale field/method
                try {
                    // Method 1: Try to find setScale method (if available in newer versions)
                    java.lang.reflect.Method setScaleMethod = null;
                    try {
                        setScaleMethod = entityClass.getMethod("setScale", float.class);
                    } catch (NoSuchMethodException e) {
                        // Try alternative method names
                        try {
                            setScaleMethod = craftEntity.getClass().getMethod("setScale", float.class);
                        } catch (NoSuchMethodException e2) {
                            // Try EntityData API (1.20.5+)
                            try {
                                Class<?> entityClass2 = craftEntity.getClass();
                                setScaleMethod = entityClass2.getMethod("a", float.class); // setScale in obfuscated
                            } catch (NoSuchMethodException e3) {
                                // Method doesn't exist, try field access
                            }
                        }
                    }
                    
                    if (setScaleMethod != null) {
                        setScaleMethod.invoke(craftEntity, (float) sizeMultiplier);
                        plugin.getLogger().fine("Applied legendary size scaling via setScale method");
                        return;
                    }
                    
                    // Method 2: Try to access scale field directly
                    try {
                        java.lang.reflect.Field scaleField = entityClass.getDeclaredField("scale");
                        scaleField.setAccessible(true);
                        scaleField.set(craftEntity, (float) sizeMultiplier);
                        plugin.getLogger().fine("Applied legendary size scaling via scale field");
                        return;
                    } catch (NoSuchFieldException e) {
                        // Try alternative field names
                        try {
                            java.lang.reflect.Field scaleField = entityClass.getDeclaredField("dataScale");
                            scaleField.setAccessible(true);
                            scaleField.set(craftEntity, (float) sizeMultiplier);
                            plugin.getLogger().fine("Applied legendary size scaling via dataScale field");
                            return;
                        } catch (NoSuchFieldException e2) {
                            // Try obfuscated field names
                            for (java.lang.reflect.Field field : entityClass.getDeclaredFields()) {
                                if (field.getType() == float.class || field.getType() == Float.class) {
                                    try {
                                        field.setAccessible(true);
                                        float currentValue = field.getFloat(craftEntity);
                                        if (currentValue >= 0.5f && currentValue <= 2.0f) { // Likely scale field
                                            field.set(craftEntity, (float) sizeMultiplier);
                                            plugin.getLogger().fine("Applied legendary size scaling via detected scale field");
                                            return;
                                        }
                                    } catch (Exception ignored) {}
                                }
                            }
                            plugin.getLogger().fine("Entity size scaling not available - using visual indicators only");
                        }
                    }
                } catch (Exception e) {
                    // NMS access failed, use visual indicators only
                    plugin.getLogger().fine("NMS size scaling failed: " + e.getMessage());
                }
            } catch (Exception e) {
                // Reflection failed completely, use visual indicators only
                plugin.getLogger().fine("Reflection access failed for legendary size scaling: " + e.getMessage());
            }
            
        } catch (Exception e) {
            // Only log at fine level to avoid spam - legendary scaling failures are not critical
            plugin.getLogger().fine("Failed to apply legendary scaling: " + e.getMessage());
        }
    }
    
    /**
     * Set gold glow color for legendary mobs using scoreboard team
     */
    private void setGoldGlowColor(LivingEntity mob) {
        try {
            // Get or create the main scoreboard
            org.bukkit.scoreboard.Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            org.bukkit.scoreboard.Team team = scoreboard.getTeam("roguecraft_legendary_gold");
            
            // Create team if it doesn't exist
            if (team == null) {
                team = scoreboard.registerNewTeam("roguecraft_legendary_gold");
                team.setColor(org.bukkit.ChatColor.GOLD); // Set team color to gold for gold glow
            }
            
            // Add entity to team (this changes the glow color to gold)
            if (!team.hasEntry(mob.getUniqueId().toString())) {
                team.addEntry(mob.getUniqueId().toString());
            }
        } catch (Exception e) {
            // Fallback: if team system fails, just use regular glowing
            plugin.getLogger().fine("Failed to set gold glow color: " + e.getMessage());
        }
    }
    
    /**
     * Update mob custom name to show health
     */
    private void updateMobHealthDisplay(LivingEntity mob) {
        // Store original name if not already stored
        if (!mob.hasMetadata("original_name")) {
            String originalName = mob.getCustomName();
            if (originalName == null) {
                originalName = mob.getType().name().replace("_", " ");
            }
            mob.setMetadata("original_name", new org.bukkit.metadata.FixedMetadataValue(plugin, originalName));
        }
        
        // Update health display
        updateMobNameWithHealth(mob);
    }
    
    /**
     * Update a mob's name to show current health
     */
    private void updateMobNameWithHealth(LivingEntity mob) {
        if (mob.isDead() || !mob.isValid()) return;
        // Skip XP display ArmorStands
        if (mob.hasMetadata("roguecraft_xp_display")) return;
        
        String originalName = mob.getType().name().replace("_", " ");
        if (mob.hasMetadata("original_name")) {
            originalName = mob.getMetadata("original_name").get(0).asString();
        }
        
        double currentHealth = mob.getHealth();
        double maxHealth = mob.getMaxHealth();
        double healthPercent = (currentHealth / maxHealth) * 100.0;
        
        // Choose color based on health percentage
        ChatColor healthColor;
        if (healthPercent > 75) {
            healthColor = ChatColor.GREEN;
        } else if (healthPercent > 50) {
            healthColor = ChatColor.YELLOW;
        } else if (healthPercent > 25) {
            healthColor = ChatColor.GOLD;
        } else {
            healthColor = ChatColor.RED;
        }
        
        // Build health bar (10 hearts)
        int fullHearts = (int) (healthPercent / 10);
        StringBuilder healthBar = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i < fullHearts) {
                healthBar.append(healthColor).append("❤");
            } else {
                healthBar.append(ChatColor.GRAY).append("❤");
            }
        }
        
        // Format health display
        String healthDisplay = String.format("%.1f/%.1f", currentHealth, maxHealth);
        String name;
        
        // Check if elite boss (Wither)
        boolean isEliteBoss = mob.hasMetadata("roguecraft_elite_boss") || mob.hasMetadata("is_elite_boss");
        // Check if legendary
        boolean isLegendary = mob.hasMetadata("is_legendary");
        // Check if elite (glowing + has elite metadata or original name contains ELITE)
        boolean isElite = mob.isGlowing() && (mob.hasMetadata("is_elite") || 
                          (mob.getCustomName() != null && mob.getCustomName().contains("ELITE")));
        
        if (isEliteBoss) {
            // Elite boss (Wither) - red name
            name = "§4§l☠ BOSS: WITHER ☠ §r§7[" + healthColor + healthDisplay + "§7]";
        } else if (isLegendary) {
            // Legendary mob - golden/purple name
            name = "§6§l★ LEGENDARY ★ §r" + originalName + " §7[" + healthColor + healthDisplay + "§7]";
        } else if (isElite) {
            name = "§c§l⚡ ELITE §r" + originalName + " §7[" + healthColor + healthDisplay + "§7]";
        } else {
            name = originalName + " §7[" + healthColor + healthDisplay + "§7]";
        }
        
        mob.setCustomName(name);
        mob.setCustomNameVisible(true);
    }
    
    /**
     * Start periodic task to update mob health displays for all players in a run
     */
    private void startMobHealthDisplay(TeamRun teamRun) {
        UUID runId = teamRun.getPlayers().get(0).getUniqueId();
        
        // Cancel existing task if any
        BukkitTask existingTask = runTasks.get(runId);
        if (existingTask != null && existingTask.getTaskId() != -1) {
            // Don't cancel the main game loop, just add health display to it
        }
        
        // Update mob health displays every second (20 ticks)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!teamRun.isActive()) return;
            
            // Update health for all nearby mobs for all players
            for (Player player : teamRun.getPlayers()) {
                if (player == null || !player.isOnline()) continue;
                
                // Update health for mobs within 30 blocks
                for (org.bukkit.entity.Entity entity : player.getNearbyEntities(30, 30, 30)) {
                    if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                        LivingEntity mob = (LivingEntity) entity;
                        // Skip XP display ArmorStands
                        if (!mob.hasMetadata("roguecraft_xp_display")) {
                            updateMobNameWithHealth(mob);
                        }
                    }
                }
            }
        }, 20L, 20L); // Every second
    }
    
    private void applyMobSpeedScaling(LivingEntity mob, int wave, double difficultyMultiplier) {
        try {
            org.bukkit.attribute.Attribute speedAttr = org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED;
            org.bukkit.attribute.AttributeInstance speedInstance = mob.getAttribute(speedAttr);
            
            if (speedInstance != null) {
                double baseSpeed = speedInstance.getBaseValue();
                
                // Get max wave from config
                int maxWave = plugin.getConfigManager().getBalanceConfig().getInt("waves.max-wave", 20);
                boolean isInfiniteWave = wave > maxWave;
                
                double waveSpeedMultiplier;
                if (isInfiniteWave) {
                    // Infinite waves: Horrific speed scaling
                    // Start at +50% for wave 21, then add 5% per infinite wave (uncapped)
                    // Wave 30 = +95%, Wave 40 = +145%, Wave 50 = +195%, etc.
                    int infiniteWaveNumber = wave - maxWave;
                    double infiniteSpeedBonus = 0.50 + (infiniteWaveNumber * 0.05); // 5% per infinite wave
                    waveSpeedMultiplier = 1.0 + infiniteSpeedBonus; // No cap - let it scale horrifically
                } else {
                    // Regular waves: Increase speed by 0.5% per wave, capped at +50%
                    waveSpeedMultiplier = 1.0 + Math.min(0.5, wave * 0.005);
                }
                
                // Additional scaling from difficulty (time-based) - reduced
                double difficultySpeedBonus = Math.min(0.25, (difficultyMultiplier - 1.0) * 0.25);
                
                double newSpeed = baseSpeed * waveSpeedMultiplier * (1.0 + difficultySpeedBonus);
                
                speedInstance.setBaseValue(newSpeed);
            }
        } catch (Exception e) {
            // Some mobs might not have speed attribute, ignore
        }
    }
    
    /**
     * Apply armor to elite mobs in later waves (wave 10+)
     * Armor increases with wave number to make elites more tanky
     * 
     * Note: Not all mobs can wear armor. Only mobs with equipment slots can:
     * - Zombies, Skeletons, Husks, Strays, Drowned: Can wear armor
     * - Wither Skeletons, Vindicators, Pillagers: Can wear armor
     * - Ravagers, Blazes, Endermen: Cannot wear armor (no equipment slots)
     */
    private void applyEliteArmor(LivingEntity mob, int wave) {
        // Only mobs with equipment slots can wear armor
        // Check if mob has equipment - if null, this mob type cannot wear armor
        if (!(mob instanceof org.bukkit.entity.Mob)) {
            return;
        }
        
        try {
            org.bukkit.entity.Mob mobEntity = (org.bukkit.entity.Mob) mob;
            org.bukkit.inventory.EntityEquipment equipment = mobEntity.getEquipment();
            if (equipment == null) {
                // This mob type doesn't support equipment (e.g., Ravager, Blaze, Enderman)
                return;
            }
            
            // Calculate armor tier based on wave
            // Wave 10-15: Iron armor
            // Wave 16-20: Diamond armor
            // Wave 21+: Netherite armor
            org.bukkit.Material helmetMaterial;
            org.bukkit.Material chestplateMaterial;
            org.bukkit.Material leggingsMaterial;
            org.bukkit.Material bootsMaterial;
            int armorLevel = 0;
            
            if (wave >= 21) {
                // Netherite armor (best protection)
                helmetMaterial = org.bukkit.Material.NETHERITE_HELMET;
                chestplateMaterial = org.bukkit.Material.NETHERITE_CHESTPLATE;
                leggingsMaterial = org.bukkit.Material.NETHERITE_LEGGINGS;
                bootsMaterial = org.bukkit.Material.NETHERITE_BOOTS;
                armorLevel = 3; // Protection III
            } else if (wave >= 16) {
                // Diamond armor
                helmetMaterial = org.bukkit.Material.DIAMOND_HELMET;
                chestplateMaterial = org.bukkit.Material.DIAMOND_CHESTPLATE;
                leggingsMaterial = org.bukkit.Material.DIAMOND_LEGGINGS;
                bootsMaterial = org.bukkit.Material.DIAMOND_BOOTS;
                armorLevel = 2; // Protection II
            } else {
                // Iron armor (wave 10-15)
                helmetMaterial = org.bukkit.Material.IRON_HELMET;
                chestplateMaterial = org.bukkit.Material.IRON_CHESTPLATE;
                leggingsMaterial = org.bukkit.Material.IRON_LEGGINGS;
                bootsMaterial = org.bukkit.Material.IRON_BOOTS;
                armorLevel = 1; // Protection I
            }
            
            // Create armor pieces with enchantments
            org.bukkit.inventory.ItemStack helmet = new org.bukkit.inventory.ItemStack(helmetMaterial);
            org.bukkit.inventory.ItemStack chestplate = new org.bukkit.inventory.ItemStack(chestplateMaterial);
            org.bukkit.inventory.ItemStack leggings = new org.bukkit.inventory.ItemStack(leggingsMaterial);
            org.bukkit.inventory.ItemStack boots = new org.bukkit.inventory.ItemStack(bootsMaterial);
            
            // Add Protection enchantment to all pieces
            org.bukkit.enchantments.Enchantment protection = org.bukkit.enchantments.Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft("protection"));
            if (protection != null) {
                helmet.addEnchantment(protection, armorLevel);
                chestplate.addEnchantment(protection, armorLevel);
                leggings.addEnchantment(protection, armorLevel);
                boots.addEnchantment(protection, armorLevel);
            }
            
            // Make armor unbreakable and undroppable
            org.bukkit.inventory.meta.ItemMeta helmetMeta = helmet.getItemMeta();
            if (helmetMeta != null) {
                helmetMeta.setUnbreakable(true);
                helmet.setItemMeta(helmetMeta);
            }
            org.bukkit.inventory.meta.ItemMeta chestplateMeta = chestplate.getItemMeta();
            if (chestplateMeta != null) {
                chestplateMeta.setUnbreakable(true);
                chestplate.setItemMeta(chestplateMeta);
            }
            org.bukkit.inventory.meta.ItemMeta leggingsMeta = leggings.getItemMeta();
            if (leggingsMeta != null) {
                leggingsMeta.setUnbreakable(true);
                leggings.setItemMeta(leggingsMeta);
            }
            org.bukkit.inventory.meta.ItemMeta bootsMeta = boots.getItemMeta();
            if (bootsMeta != null) {
                bootsMeta.setUnbreakable(true);
                boots.setItemMeta(bootsMeta);
            }
            
            // Equip the armor
            equipment.setHelmet(helmet);
            equipment.setChestplate(chestplate);
            equipment.setLeggings(leggings);
            equipment.setBoots(boots);
            
            // Make armor not drop on death
            equipment.setHelmetDropChance(0.0f);
            equipment.setChestplateDropChance(0.0f);
            equipment.setLeggingsDropChance(0.0f);
            equipment.setBootsDropChance(0.0f);
        } catch (Exception e) {
            // Some mobs might not support equipment, ignore
            plugin.getLogger().fine("Failed to apply elite armor: " + e.getMessage());
        }
    }

    private void levelUp(TeamRun teamRun) {
        levelUpRun(teamRun);
    }
    
    private void levelUp(Run run) {
        levelUpRun(run);
    }
    
    /**
     * Generic level up method that works for both Run and TeamRun
     */
    private void levelUpRun(Object run) {
        if (run instanceof TeamRun) {
            TeamRun teamRun = (TeamRun) run;
            // Store old required XP before modifying
            int oldRequiredXP = teamRun.getExperienceToNextLevel();
            teamRun.setLevel(teamRun.getLevel() + 1);
            
            // Subtract old required XP and ensure experience doesn't go negative
            int newExperience = Math.max(0, teamRun.getExperience() - oldRequiredXP);
            teamRun.addExperience(-teamRun.getExperience()); // Reset to 0 first
            teamRun.addExperience(newExperience); // Set to remainder (handles overflow)
            
            // Less aggressive scaling for faster progression
            int currentLevel = teamRun.getLevel();
            double scaleMultiplier;
            if (currentLevel <= 5) {
                // Early levels: 1.2x scaling (faster progression)
                scaleMultiplier = 1.2;
            } else {
                // Later levels: 1.35x scaling (faster than before)
                scaleMultiplier = 1.35;
            }
            teamRun.setExperienceToNextLevel((int) (oldRequiredXP * scaleMultiplier));

            // Flash XP bar and notify all players
            for (Player player : teamRun.getPlayers()) {
                if (player != null && player.isOnline()) {
                    player.sendMessage("§6§lLEVEL UP! §eLevel " + teamRun.getLevel());
                    player.sendMessage("§aChoose your power-up!");
                    
                    // Flash XP bar for level up
                    com.eldor.roguecraft.util.XPBar.flashLevelUp(player, teamRun.getLevel());
                    
                    // Update XP bar with gold after level up
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
            
            // Open power-up GUI for all players
            for (Player player : teamRun.getPlayers()) {
                if (player != null && player.isOnline()) {
                    plugin.getGuiManager().openPowerUpGUI(player, teamRun);
                }
            }
        } else if (run instanceof Run) {
            Run soloRun = (Run) run;
            // Store old required XP before modifying
            int oldRequiredXP = soloRun.getExperienceToNextLevel();
            soloRun.setLevel(soloRun.getLevel() + 1);
            
            // Subtract old required XP and ensure experience doesn't go negative
            int newExperience = Math.max(0, soloRun.getExperience() - oldRequiredXP);
            soloRun.addExperience(-soloRun.getExperience()); // Reset to 0 first
            soloRun.addExperience(newExperience); // Set to remainder (handles overflow)
            
            // Less aggressive scaling for faster progression
            int currentLevel = soloRun.getLevel();
            double scaleMultiplier;
            if (currentLevel <= 5) {
                // Early levels: 1.2x scaling (faster progression)
                scaleMultiplier = 1.2;
            } else {
                // Later levels: 1.35x scaling (faster than before)
                scaleMultiplier = 1.35;
            }
            soloRun.setExperienceToNextLevel((int) (oldRequiredXP * scaleMultiplier));
            
            Player player = soloRun.getPlayer();
            if (player != null && player.isOnline()) {
                player.sendMessage("§6§lLEVEL UP! §eLevel " + soloRun.getLevel());
                player.sendMessage("§aChoose your power-up!");
                
                // Flash XP bar for level up
                com.eldor.roguecraft.util.XPBar.flashLevelUp(player, soloRun.getLevel());
                
                // Update XP bar with gold after level up
                com.eldor.roguecraft.util.XPBar.updateXPBarWithGold(
                    player,
                    soloRun.getExperience(),
                    soloRun.getExperienceToNextLevel(),
                    soloRun.getLevel(),
                    soloRun.getWave(),
                    soloRun.getCurrentGold()
                );
                
                // Open power-up GUI
                plugin.getGuiManager().openPowerUpGUI(player, soloRun);
            }
        }
    }

    public void endTeamRun(UUID teamId, Arena arena) {
        TeamRun teamRun = plugin.getRunManager().getAllActiveTeamRuns().stream()
            .filter(tr -> getTeamRunId(tr) != null && getTeamRunId(tr).equals(teamId))
            .findFirst().orElse(null);
            
        if (teamRun != null) {
            // Comprehensive cleanup
            cleanupRun(teamRun, teamId, arena, true);
            
            // Notify all players
            for (Player player : teamRun.getPlayers()) {
                if (player != null && player.isOnline()) {
                    logRunStats(player, teamRun);
                }
            }

            plugin.getRunManager().endTeamRun(teamId);
        } else {
            // If teamRun is null, still try to clean up chests for this teamId
            plugin.getLogger().warning("[GameManager] TeamRun not found for teamId " + teamId + ", attempting chest cleanup anyway");
            plugin.getChestManager().removeChestsForRun(teamId);
        }

        // Cancel tasks
        BukkitTask gameTask = runTasks.remove(teamId);
        if (gameTask != null) {
            gameTask.cancel();
        }

        BukkitTask spawnTask = spawnTasks.remove(teamId);
        if (spawnTask != null) {
            spawnTask.cancel();
        }
        
        BukkitTask regenTask = regenTasks.remove(teamId);
        if (regenTask != null) {
            regenTask.cancel();
        }
        
        BukkitTask jumpHeightTask = jumpHeightTasks.remove(teamId);
        if (jumpHeightTask != null) {
            jumpHeightTask.cancel();
        }
        
        // Clean up weapon selection tracking (remove all players from this team)
        if (teamRun != null) {
            for (UUID playerId : teamRun.getPlayerIds()) {
                teamsInWeaponSelection.remove(playerId);
            }
        } else {
            teamsInWeaponSelection.remove(teamId);
        }
        
        // Clean up last damage time tracking for all players in the team
        if (teamRun != null) {
            for (UUID playerId : teamRun.getPlayerIds()) {
                lastDamageTime.remove(playerId);
            }
        }

        if (arena != null) {
            arena.setActive(false);
        }
    }

    public void endRun(UUID playerId, Arena arena) {
        // Check for team run first
        TeamRun teamRun = plugin.getRunManager().getTeamRun(playerId);
        if (teamRun != null) {
            UUID teamId = getTeamRunId(teamRun);
            if (teamId != null && teamRun.getPlayerCount() <= 1) {
                endTeamRun(teamId, arena);
            } else {
                // Just remove this player from team - still need full cleanup for this player
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    // Stop weapon auto-attack
                    plugin.getWeaponManager().stopAutoAttack(player);
                    // Stop health display
                    BukkitTask healthTask = healthDisplayTasks.remove(playerId);
                    if (healthTask != null) {
                        healthTask.cancel();
                    }
                    // Remove XP bar
                    com.eldor.roguecraft.util.XPBar.removeXPBar(player);
                    // Reset attributes
                    resetPlayerAttributes(player);
                    // Clean up shrine channeling
                    plugin.getShrineManager().cleanupPlayerChanneling(player);
                    // Clear GUI queue
                    plugin.getGuiManager().clearQueue(playerId);
                }
                plugin.getRunManager().endRun(playerId);
            }
            return;
        }

        // Single player run
        Run run = plugin.getRunManager().getRun(playerId);
        if (run != null) {
            // Comprehensive cleanup
            cleanupRun(run, playerId, arena, false);
            
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                logRunStats(player, run);
            }
            
            plugin.getRunManager().endRun(playerId);
        }

        // Cancel tasks
        BukkitTask gameTask = runTasks.remove(playerId);
        if (gameTask != null) {
            gameTask.cancel();
        }

        BukkitTask spawnTask = spawnTasks.remove(playerId);
        if (spawnTask != null) {
            spawnTask.cancel();
        }
        
        // Clean up time freeze tracking
        timeFreezeEndTime.remove(playerId);

        if (arena != null) {
            arena.setActive(false);
        }
    }
    
    /**
     * Comprehensive cleanup function for runs
     * Stops all active game mechanics, removes entities, and resets arena state
     */
    private void cleanupRun(Object run, UUID runId, Arena arena, boolean isTeamRun) {
        // Mark run as inactive
        if (run instanceof Run) {
            ((Run) run).setActive(false);
        } else if (run instanceof TeamRun) {
            ((TeamRun) run).setActive(false);
        }
        
        // 1. Stop all weapon auto-attacks, remove XP bars, stop health display, and clean up shrine channeling
        if (run instanceof TeamRun) {
            TeamRun teamRun = (TeamRun) run;
            for (Player player : teamRun.getPlayers()) {
                if (player != null && player.isOnline()) {
                    plugin.getWeaponManager().stopAutoAttack(player);
                    com.eldor.roguecraft.util.XPBar.removeXPBar(player);
                    
                    // Stop health display
                    BukkitTask healthTask = healthDisplayTasks.remove(player.getUniqueId());
                    if (healthTask != null) {
                        healthTask.cancel();
                    }
                    
                    // Clean up any active shrine channeling/GUI tasks
                    plugin.getShrineManager().cleanupPlayerChanneling(player);
                    
                    // Reset health and speed to default
                    resetPlayerAttributes(player);
                    
                    // Reset hunger to normal
                    player.setFoodLevel(20);
                    player.setSaturation(20.0f);
                    player.setExhaustion(0.0f);
                }
            }
        } else if (run instanceof Run) {
            Player player = Bukkit.getPlayer(runId);
            if (player != null) {
                plugin.getWeaponManager().stopAutoAttack(player);
                com.eldor.roguecraft.util.XPBar.removeXPBar(player);
                
                // Stop health display
                BukkitTask healthTask = healthDisplayTasks.remove(player.getUniqueId());
                if (healthTask != null) {
                    healthTask.cancel();
                }
                
                // Clean up any active shrine channeling/GUI tasks
                plugin.getShrineManager().cleanupPlayerChanneling(player);
                
                // Reset health and speed to default
                resetPlayerAttributes(player);
            }
        }
        
        // 2. Remove/restore world border
        if (run instanceof TeamRun && arena != null) {
            removeArenaBorder((TeamRun) run, arena);
        }
        
        // 3. Unfreeze all mobs
        Set<LivingEntity> frozen = frozenMobs.remove(runId);
        if (frozen != null) {
            for (LivingEntity mob : frozen) {
                if (mob != null && !mob.isDead()) {
                    mob.setAI(true);
                    mob.setGravity(true);
                }
            }
        }
        
        // 4. Remove all spawned mobs and plugin entities in arena
        if (arena != null && arena.getCenter() != null) {
            double radius = arena.getRadius();
            org.bukkit.World world = arena.getCenter().getWorld();
            int removedMobs = 0;
            
            for (org.bukkit.entity.Entity entity : world.getNearbyEntities(arena.getCenter(), radius, radius, radius)) {
                if (entity == null || entity.isDead()) continue;
                
                // Remove all mobs (except players) - be more aggressive
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    // Remove if it has plugin metadata OR if it's a hostile mob in the arena
                    boolean shouldRemove = false;
                    if (entity.hasMetadata("roguecraft_mob") || 
                        entity.hasMetadata("roguecraft_boss") || 
                        entity.hasMetadata("roguecraft_elite") ||
                        entity.hasMetadata("roguecraft_elite_boss") ||
                        entity.hasMetadata("is_legendary") ||
                        entity.hasMetadata("is_elite")) {
                        shouldRemove = true;
                    } else if (entity instanceof org.bukkit.entity.Monster) {
                        // Also remove any hostile monsters in the arena (they shouldn't be there)
                        shouldRemove = true;
                    }
                    
                    if (shouldRemove) {
                        entity.remove();
                        removedMobs++;
                    }
                }
            }
            
            if (removedMobs > 0) {
                plugin.getLogger().info("[GameManager] Removed " + removedMobs + " mobs from arena during cleanup");
            }
        }
        
        // 5. Clean up boss spawn tracking
        bossSpawnedWave.remove(runId);
        timeFreezeEndTime.remove(runId);
        lastDamageTime.remove(runId);
        
        // 5. Close any open GUIs for players
        if (run instanceof TeamRun) {
            TeamRun teamRun = (TeamRun) run;
            for (Player player : teamRun.getPlayers()) {
                if (player != null && player.isOnline()) {
                    if (player.getOpenInventory().getTopInventory().getHolder() == null) {
                        player.closeInventory();
                    }
                    teamRun.setPlayerInGUI(player.getUniqueId(), false);
                }
            }
        } else if (run instanceof Run) {
            Player player = Bukkit.getPlayer(runId);
            if (player != null && player.isOnline()) {
                if (player.getOpenInventory().getTopInventory().getHolder() == null) {
                    player.closeInventory();
                }
            }
        }
        
        // 6. Remove physical shrines (for both team and solo runs)
        plugin.getShrineManager().removeShrinesForRun(runId);
        
        // 6b. Remove gacha chests (for both team and solo runs)
        plugin.getChestManager().removeChestsForRun(runId);
        
        // 6c. Double-check all shrines are removed and clean up any remaining entities
        if (arena != null && arena.getCenter() != null) {
            double radius = arena.getRadius();
            org.bukkit.World world = arena.getCenter().getWorld();
            
            // Remove all plugin-spawned entities in the arena
            int removedEntities = 0;
            for (org.bukkit.entity.Entity entity : world.getNearbyEntities(arena.getCenter(), radius, radius, radius)) {
                if (entity == null || entity.isDead()) continue;
                
                // Remove dropped items (XP tokens, heart items, power-up items)
                if (entity instanceof org.bukkit.entity.Item) {
                    org.bukkit.entity.Item item = (org.bukkit.entity.Item) entity;
                    String customName = item.getCustomName();
                    if (customName != null && (
                        customName.startsWith("XP_TOKEN") ||
                        customName.equals("HEART_ITEM") ||
                        customName.startsWith("POWERUP_ITEM_"))) {
                        entity.remove();
                        removedEntities++;
                        continue;
                    }
                }
                
                // Remove XP display ArmorStands
                if (entity instanceof org.bukkit.entity.ArmorStand && entity.hasMetadata("roguecraft_xp_display")) {
                    entity.remove();
                    removedEntities++;
                    continue;
                }
                
                // Remove ItemFrames (from chest gacha animations)
                if (entity instanceof org.bukkit.entity.ItemFrame) {
                    // Check if it's from our plugin (chest gacha animations use fixed/invulnerable ItemFrames)
                    org.bukkit.entity.ItemFrame frame = (org.bukkit.entity.ItemFrame) entity;
                    if (frame.isFixed() && frame.isInvulnerable()) {
                        entity.remove();
                        removedEntities++;
                        continue;
                    }
                }
                
                // Remove any other ArmorStands that might be from our plugin
                if (entity instanceof org.bukkit.entity.ArmorStand) {
                    org.bukkit.entity.ArmorStand stand = (org.bukkit.entity.ArmorStand) entity;
                    // Check if it's invisible and marker (typical of our plugin entities)
                    if (!stand.isVisible() && stand.isMarker()) {
                        entity.remove();
                        removedEntities++;
                        continue;
                    }
                }
            }
            
            if (removedEntities > 0) {
                plugin.getLogger().info("[GameManager] Removed " + removedEntities + " plugin entities (items, ArmorStands, ItemFrames) from arena during cleanup");
            }
            
            // Double-check shrines are removed by scanning for shrine blocks
            List<com.eldor.roguecraft.models.Shrine> remainingShrines = plugin.getShrineManager().getShrinesForRun(runId);
            if (remainingShrines != null && !remainingShrines.isEmpty()) {
                plugin.getLogger().warning("[GameManager] Found " + remainingShrines.size() + " remaining shrines after cleanup, forcing removal");
                for (com.eldor.roguecraft.models.Shrine shrine : remainingShrines) {
                    if (shrine != null) {
                        try {
                            shrine.remove();
                        } catch (Exception e) {
                            plugin.getLogger().warning("[GameManager] Error removing remaining shrine: " + e.getMessage());
                        }
                    }
                }
                // Clear the list
                remainingShrines.clear();
            }
            
            // 6d. Aggressive block cleanup - scan entire arena for any remaining shrine blocks
            int removedBlocks = 0;
            org.bukkit.Location center = arena.getCenter();
            double arenaRadius = arena.getRadius();
            int centerX = center.getBlockX();
            int centerY = center.getBlockY();
            int centerZ = center.getBlockZ();
            
            // Scan a larger area to catch any missed blocks
            int scanRadius = (int) Math.ceil(arenaRadius) + 5; // Add 5 blocks buffer
            int minY = centerY - 5; // Check below arena
            int maxY = centerY + 10; // Check above arena
            
            for (int x = centerX - scanRadius; x <= centerX + scanRadius; x++) {
                for (int z = centerZ - scanRadius; z <= centerZ + scanRadius; z++) {
                    // Check distance from center
                    double distance = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(z - centerZ, 2));
                    if (distance > arenaRadius + 10) continue; // Skip if too far
                    
                    for (int y = minY; y <= maxY; y++) {
                        org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                        if (block == null) continue;
                        
                        org.bukkit.Material mat = block.getType();
                        
                        // Remove shrine-related blocks
                        if (mat == org.bukkit.Material.SKELETON_SKULL ||
                            mat == org.bukkit.Material.BLACK_CONCRETE ||
                            mat == org.bukkit.Material.BLACK_TERRACOTTA ||
                            mat == org.bukkit.Material.OBSIDIAN ||
                            mat == org.bukkit.Material.BLACKSTONE ||
                            mat == org.bukkit.Material.SOUL_TORCH ||
                            mat == org.bukkit.Material.END_ROD ||
                            mat == org.bukkit.Material.DARK_OAK_FENCE ||
                            (mat.name().contains("CONCRETE") && (mat.name().contains("BLACK") || mat.name().contains("GRAY"))) ||
                            (mat.name().contains("TERRACOTTA") && (mat.name().contains("BLACK") || mat.name().contains("GRAY")))) {
                            // Ensure chunk is loaded
                            if (!block.getChunk().isLoaded()) {
                                block.getChunk().load();
                            }
                            block.setType(org.bukkit.Material.AIR);
                            removedBlocks++;
                        }
                    }
                }
            }
            
            if (removedBlocks > 0) {
                plugin.getLogger().info("[GameManager] Removed " + removedBlocks + " shrine blocks during aggressive cleanup");
            }
        }
        
        // 7. Clear GUI queue for all players in the run
        if (run instanceof TeamRun) {
            TeamRun teamRun = (TeamRun) run;
            for (Player player : teamRun.getPlayers()) {
                if (player != null) {
                    plugin.getGuiManager().clearQueue(player.getUniqueId());
                }
            }
        } else if (run instanceof Run) {
            plugin.getGuiManager().clearQueue(runId);
        }
        
        // 8. Stop aura effects
        plugin.getAuraManager().stopAuras(runId);
        
        // 9. Stop synergy tracking
        plugin.getSynergyManager().stopSynergies(runId);
        
        // 10. Clear original border settings
        originalBorders.remove(runId);
    }

    public void stopAllRuns() {
        // Stop all team runs
        for (UUID teamId : new ArrayList<>(runTasks.keySet())) {
            Arena arena = plugin.getArenaManager().getDefaultArena();
            endTeamRun(teamId, arena);
        }
        
        // Stop all solo runs
        for (UUID playerId : new ArrayList<>(runTasks.keySet())) {
            Arena arena = plugin.getArenaManager().getDefaultArena();
            endRun(playerId, arena);
        }
        
        // Final cleanup - ensure everything is stopped
        plugin.getWeaponManager().stopAllAutoAttacks();
        
        // Cancel all health display tasks
        for (BukkitTask task : healthDisplayTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        
        runTasks.clear();
        spawnTasks.clear();
        healthDisplayTasks.clear();
        frozenMobs.clear();
        originalBorders.clear();
    }
    
    /**
     * Execute Nuclear Strike - kills all mobs in arena without XP, with explosion effects
     */
    public void executeNuke(Player player, Object run, Arena arena) {
        if (arena == null || arena.getCenter() == null) {
            return;
        }
        
        Location center = arena.getCenter();
        double radius = arena.getRadius();
        org.bukkit.World world = center.getWorld();
        
        if (world == null) {
            return;
        }
        
        // Find all mobs in arena (exclude Wither boss)
        java.util.List<LivingEntity> mobsToKill = new java.util.ArrayList<>();
        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                LivingEntity mob = (LivingEntity) entity;
                // Skip Wither boss (has roguecraft_boss or roguecraft_elite_boss metadata)
                if (mob.hasMetadata("roguecraft_boss") || mob.hasMetadata("roguecraft_elite_boss")) {
                    continue; // Don't kill the boss
                }
                if (!mob.isDead()) {
                    mobsToKill.add(mob);
                }
            }
        }
        
        if (mobsToKill.isEmpty()) {
            return; // No mobs to kill
        }
        
        // Mark all mobs as nuked (prevents XP gain)
        for (LivingEntity mob : mobsToKill) {
            mob.setMetadata("roguecraft_nuked", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        }
        
        // Create explosion particles at each mob location
        for (LivingEntity mob : mobsToKill) {
            Location mobLoc = mob.getLocation();
            
            // Spawn explosion particles
            world.spawnParticle(org.bukkit.Particle.EXPLOSION, mobLoc, 3, 0.5, 0.5, 0.5, 0.1);
            world.spawnParticle(org.bukkit.Particle.EXPLOSION, mobLoc, 10, 1.0, 1.0, 1.0, 0.05);
            world.spawnParticle(org.bukkit.Particle.SMOKE, mobLoc, 15, 1.0, 1.0, 1.0, 0.1);
            
            // Play explosion sound
            world.playSound(mobLoc, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.8f);
        }
        
        // Kill all mobs (slight delay for visual effect)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (LivingEntity mob : mobsToKill) {
                if (mob != null && !mob.isDead() && mob.isValid()) {
                    mob.setHealth(0); // Kill the mob
                }
            }
        }, 5L); // 0.25 second delay for visual feedback
        
        // Additional large explosion effect at arena center
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, center, 1, 0, 0, 0, 0);
            world.playSound(center, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
        }, 10L); // 0.5 second delay
    }
    
    private void resetPlayerAttributes(Player player) {
        // Reset health to default 20
        org.bukkit.attribute.Attribute healthAttr = org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH;
        org.bukkit.attribute.AttributeInstance healthInstance = player.getAttribute(healthAttr);
        if (healthInstance != null) {
            healthInstance.setBaseValue(20.0);
            if (player.getHealth() > 20.0) {
                player.setHealth(20.0);
            }
        }
        
        // Reset speed to default 0.1
        org.bukkit.attribute.Attribute speedAttr = org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED;
        org.bukkit.attribute.AttributeInstance speedInstance = player.getAttribute(speedAttr);
        if (speedInstance != null) {
            speedInstance.setBaseValue(0.1);
        }
        
        // Reset armor to default 0
        org.bukkit.attribute.Attribute armorAttr = org.bukkit.attribute.Attribute.GENERIC_ARMOR;
        org.bukkit.attribute.AttributeInstance armorInstance = player.getAttribute(armorAttr);
        if (armorInstance != null) {
            armorInstance.setBaseValue(0.0);
        }
    }
    
    /**
     * Log comprehensive run statistics to chat when run ends
     */
    private void logRunStats(Player player, Object run) {
        if (run == null || player == null) return;
        
        long elapsed = 0;
        int wave = 0;
        int level = 0;
        int powerUpCount = 0;
        Weapon weapon = null;
        double health = 0;
        double damage = 0;
        double speed = 0;
        double armor = 0;
        double critChance = 0;
        double critDamage = 0;
        double lifesteal = 0;
        double regeneration = 0;
        double xpMultiplier = 0;
        double dropRate = 0;
        double difficulty = 0;
        int teamSize = 1;
        
        if (run instanceof Run) {
            Run r = (Run) run;
            elapsed = r.getElapsedTime() / 1000;
            wave = r.getWave();
            level = r.getLevel();
            powerUpCount = r.getCollectedPowerUps().size();
            weapon = r.getWeapon();
            health = r.getStat("health");
            damage = r.getStat("damage");
            speed = r.getStat("speed");
            armor = r.getStat("armor");
            critChance = r.getStat("crit_chance");
            critDamage = r.getStat("crit_damage");
            regeneration = r.getStat("regeneration");
            xpMultiplier = r.getStat("xp_multiplier");
            dropRate = r.getStat("drop_rate");
            difficulty = r.getStat("difficulty");
            
            // Calculate lifesteal from Vampire Aura
            for (com.eldor.roguecraft.models.PowerUp powerUp : r.getCollectedPowerUps()) {
                if (powerUp.getType() == com.eldor.roguecraft.models.PowerUp.PowerUpType.AURA) {
                    String name = powerUp.getName().toLowerCase();
                    if (name.contains("vampire") || name.contains("lifesteal")) {
                        lifesteal += powerUp.getValue() * 2.0;
                    }
                }
            }
        } else if (run instanceof TeamRun) {
            TeamRun tr = (TeamRun) run;
            elapsed = tr.getElapsedTime() / 1000;
            wave = tr.getWave();
            level = tr.getLevel();
            powerUpCount = tr.getCollectedPowerUps().size();
            weapon = tr.getWeapon();
            health = tr.getStat("health");
            damage = tr.getStat("damage");
            speed = tr.getStat("speed");
            armor = tr.getStat("armor");
            critChance = tr.getStat("crit_chance");
            critDamage = tr.getStat("crit_damage");
            regeneration = tr.getStat("regeneration");
            xpMultiplier = tr.getStat("xp_multiplier");
            dropRate = tr.getStat("drop_rate");
            difficulty = tr.getStat("difficulty");
            teamSize = tr.getPlayerCount();
            
            // Calculate lifesteal from Vampire Aura
            for (com.eldor.roguecraft.models.PowerUp powerUp : tr.getCollectedPowerUps()) {
                if (powerUp.getType() == com.eldor.roguecraft.models.PowerUp.PowerUpType.AURA) {
                    String name = powerUp.getName().toLowerCase();
                    if (name.contains("vampire") || name.contains("lifesteal")) {
                        lifesteal += powerUp.getValue() * 2.0;
                    }
                }
            }
        }
        
        // Format time
        long minutes = elapsed / 60;
        long seconds = elapsed % 60;
        String timeStr = minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s";
        
        // Send formatted stats
        player.sendMessage("");
        player.sendMessage("§6╔════════════════════════════════════╗");
        player.sendMessage("§6║        §c§lRUN STATISTICS§6        ║");
        player.sendMessage("§6╠════════════════════════════════════╣");
        player.sendMessage("§eTime Survived: §f" + timeStr);
        player.sendMessage("§eWave Reached: §f" + wave);
        player.sendMessage("§eLevel Reached: §f" + level);
        if (teamSize > 1) {
            player.sendMessage("§eTeam Size: §f" + teamSize);
        }
        player.sendMessage("§ePower-Ups Collected: §f" + powerUpCount);
        player.sendMessage("");
        player.sendMessage("§6╠════════════════════════════════════╣");
        player.sendMessage("§6║          §a§lPLAYER STATS§6          ║");
        player.sendMessage("§6╠════════════════════════════════════╣");
        player.sendMessage("§aHealth: §f" + String.format("%.1f", health));
        player.sendMessage("§cDamage: §f" + String.format("%.1f", damage));
        player.sendMessage("§bSpeed: §f" + String.format("%.1f", speed));
        player.sendMessage("§9Armor: §f" + String.format("%.1f", armor));
        player.sendMessage("§5Crit Chance: §f" + String.format("%.1f%%", critChance * 100));
        player.sendMessage("§dCrit Damage: §f" + String.format("%.2fx", critDamage));
        if (lifesteal > 0) {
            player.sendMessage("§cLifesteal: §f" + String.format("%.1f%%", lifesteal));
        }
        if (regeneration > 0) {
            player.sendMessage("§aRegeneration: §f" + String.format("%.2f HP/s", regeneration));
        }
        player.sendMessage("§eXP Multiplier: §f" + String.format("%.2fx", xpMultiplier));
        player.sendMessage("§bDrop Rate: §f" + String.format("%.1f%%", dropRate * 100));
        player.sendMessage("§4Difficulty: §f" + String.format("%.2fx", difficulty));
        
        if (weapon != null) {
            player.sendMessage("");
            player.sendMessage("§6╠════════════════════════════════════╣");
            player.sendMessage("§6║          §c§lWEAPON STATS§6          ║");
            player.sendMessage("§6╠════════════════════════════════════╣");
            player.sendMessage("§eType: §f" + weapon.getType().getDisplayName());
            player.sendMessage("§eLevel: §f" + weapon.getLevel());
            player.sendMessage("§cDamage: §f" + String.format("%.1f", weapon.getDamage()));
            player.sendMessage("§bRange: §f" + String.format("%.1f", weapon.getRange()) + " blocks");
            player.sendMessage("§aAttack Speed: §f" + String.format("%.2f", weapon.getAttackSpeed()) + "/s");
            if (weapon.getProjectileCount() > 1) {
                player.sendMessage("§dProjectiles: §f" + weapon.getProjectileCount());
            }
            if (weapon.getAreaOfEffect() > 0) {
                player.sendMessage("§6AOE: §f" + String.format("%.1f", weapon.getAreaOfEffect()) + " blocks");
            }
        }
        
        player.sendMessage("§6╚════════════════════════════════════╝");
        player.sendMessage("");
    }
}