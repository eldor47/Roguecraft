package com.eldor.roguecraft.gui;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.managers.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * GUI that shows detailed stats for a saved run.
 */
public class RunDetailsGUI implements Listener {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final RoguecraftPlugin plugin;
    private final Player viewer;
    private final int runId;
    private final UUID highlightPlayerUuid;

    private Inventory inventory;
    private DatabaseManager.RunDetails details;
    private DatabaseManager.PlayerInfo highlightPlayer;
    private Map<String, Double> highlightStats = Collections.emptyMap();
    private List<DatabaseManager.PowerUpInfo> highlightPowerUps = Collections.emptyList();
    private List<DatabaseManager.ItemInfo> highlightItems = Collections.emptyList();

    public RunDetailsGUI(RoguecraftPlugin plugin, Player viewer, int runId, UUID highlightPlayerUuid) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.runId = runId;
        this.highlightPlayerUuid = highlightPlayerUuid;
    }

    /**
     * Load run data and open the GUI. Returns false if the run could not be loaded.
     */
    public boolean open() {
        if (!plugin.getDatabaseManager().isEnabled()) {
            viewer.sendMessage(ChatColor.RED + "Database storage is disabled on this server.");
            return false;
        }

        this.details = plugin.getDatabaseManager().getRunDetails(runId);
        if (details == null) {
            viewer.sendMessage(ChatColor.RED + "Unable to load run #" + runId + ".");
            return false;
        }

        this.highlightPlayer = resolveHighlightedPlayer();
        if (highlightPlayer == null) {
            viewer.sendMessage(ChatColor.RED + "No player data found for run #" + runId + ".");
            return false;
        }

        this.highlightStats = details.playerStats.getOrDefault(highlightPlayer.playerUuid, Collections.emptyMap());
        this.highlightPowerUps = details.powerUps.getOrDefault(highlightPlayer.playerUuid, Collections.emptyList());
        this.highlightItems = details.items.getOrDefault(highlightPlayer.playerUuid, Collections.emptyList());

        this.inventory = Bukkit.createInventory(null, 54, ChatColor.DARK_AQUA + "Run #" + runId + " Details");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        setupInventory();
        viewer.openInventory(inventory);
        return true;
    }

    private DatabaseManager.PlayerInfo resolveHighlightedPlayer() {
        Optional<DatabaseManager.PlayerInfo> exactMatch = details.players.stream()
            .filter(info -> info.playerUuid.equals(highlightPlayerUuid))
            .findFirst();

        return exactMatch.orElseGet(() -> details.players.isEmpty() ? null : details.players.get(0));
    }

    private void setupInventory() {
        inventory.clear();
        inventory.setItem(4, createOverviewItem());
        inventory.setItem(13, createPlayerItem());
        inventory.setItem(21, createStatsItem());
        inventory.setItem(23, createCombatItem());
        inventory.setItem(30, createPowerUpsItem());
        inventory.setItem(32, createItemsItem());
        inventory.setItem(49, createCloseButton());
    }

    private ItemStack createOverviewItem() {
        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = star.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Run Overview");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Type: " + (details.isTeamRun ? ChatColor.AQUA + "Team" : ChatColor.YELLOW + "Solo"));
        lore.add(ChatColor.GRAY + "Wave: " + ChatColor.WHITE + details.wave);
        lore.add(ChatColor.GRAY + "Difficulty: " + ChatColor.WHITE + String.format("%.2fx", details.difficultyMultiplier));
        lore.add(ChatColor.GRAY + "Duration: " + ChatColor.WHITE + formatDuration(details.duration / 1000));
        lore.add(ChatColor.GRAY + "Score: " + ChatColor.WHITE + details.totalScore);
        lore.add("");
        lore.add(ChatColor.GRAY + "Started: " + ChatColor.WHITE + DATE_FORMAT.format(new Date(details.startTime)));
        lore.add(ChatColor.GRAY + "Ended: " + ChatColor.WHITE + DATE_FORMAT.format(new Date(details.endTime)));
        lore.add("");
        lore.add(ChatColor.GRAY + "Players (" + details.players.size() + "):");

        int count = 0;
        for (DatabaseManager.PlayerInfo info : details.players) {
            ChatColor nameColor = info.playerUuid.equals(highlightPlayer.playerUuid) ? ChatColor.GOLD : ChatColor.WHITE;
            lore.add(ChatColor.DARK_GRAY + " • " + nameColor + info.playerName +
                ChatColor.GRAY + " [" + info.playerScore + "]");
            if (++count >= 5) {
                lore.add(ChatColor.DARK_GRAY + " • " + ChatColor.GRAY + "...");
                break;
            }
        }

        meta.setLore(lore);
        star.setItemMeta(meta);
        return star;
    }

    private ItemStack createPlayerItem() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta baseMeta = head.getItemMeta();

        if (baseMeta instanceof SkullMeta skullMeta) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(highlightPlayer.playerUuid);
            skullMeta.setOwningPlayer(offlinePlayer);
            baseMeta = skullMeta;
        }

        baseMeta.setDisplayName(ChatColor.AQUA + "Player Summary");
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Name: " + ChatColor.WHITE + highlightPlayer.playerName);
        lore.add(ChatColor.GRAY + "Level: " + ChatColor.WHITE + highlightPlayer.level);
        lore.add(ChatColor.GRAY + "XP: " + ChatColor.WHITE + highlightPlayer.experience);
        lore.add(ChatColor.GRAY + "Kills: " + ChatColor.WHITE + highlightPlayer.kills);
        lore.add(ChatColor.GRAY + "Gold: " + ChatColor.WHITE + highlightPlayer.goldCollected);
        lore.add(ChatColor.GRAY + "Score: " + ChatColor.WHITE + highlightPlayer.playerScore);
        lore.add("");
        if (highlightPlayer.weaponType != null) {
            lore.add(ChatColor.GRAY + "Weapon: " + ChatColor.WHITE + highlightPlayer.weaponType +
                ChatColor.GRAY + " (Lv." + highlightPlayer.weaponLevel + ")");
        } else {
            lore.add(ChatColor.GRAY + "Weapon: " + ChatColor.WHITE + "Unknown");
        }
        baseMeta.setLore(lore);
        head.setItemMeta(baseMeta);
        return head;
    }

    private ItemStack createStatsItem() {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Final Stats");

        List<String> lore = new ArrayList<>();
        lore.add("");
        addStatLine(lore, ChatColor.GREEN + "Health", formatStat("health"));
        addStatLine(lore, ChatColor.RED + "Damage", formatStat("damage"));
        addStatLine(lore, ChatColor.AQUA + "Speed", formatStat("speed"));
        addStatLine(lore, ChatColor.BLUE + "Armor", formatStat("armor"));
        addStatLine(lore, ChatColor.DARK_PURPLE + "Crit Chance", formatPercentStat("crit_chance"));
        addStatLine(lore, ChatColor.LIGHT_PURPLE + "Crit Damage", formatMultiplierStat("crit_damage"));
        addStatLine(lore, ChatColor.GOLD + "Luck", formatStat("luck"));
        addStatLine(lore, ChatColor.YELLOW + "XP Multi", formatMultiplierStat("xp_multiplier"));
        addStatLine(lore, ChatColor.GREEN + "Regeneration", formatRegeneration());
        addStatLine(lore, ChatColor.AQUA + "Drop Rate", formatPercentStat("drop_rate"));
        addStatLine(lore, ChatColor.DARK_RED + "Difficulty", formatMultiplierStat("difficulty"));

        if (highlightStats.getOrDefault("jump_height", 0.0) > 0) {
            addStatLine(lore, ChatColor.LIGHT_PURPLE + "Jump Height", String.format("%.1f", highlightStats.get("jump_height")));
        }
        if (highlightStats.getOrDefault("pickup_range", 0.0) > 0) {
            addStatLine(lore, ChatColor.GRAY + "Pickup Range", String.format("%.1f", highlightStats.get("pickup_range")));
        }

        if (lore.size() == 1) {
            lore.add(ChatColor.GRAY + "No stats recorded.");
        }

        meta.setLore(lore);
        book.setItemMeta(meta);
        return book;
    }

    private ItemStack createCombatItem() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "Combat Summary");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Wave Reached: " + ChatColor.WHITE + details.wave);
        lore.add(ChatColor.GRAY + "Duration: " + ChatColor.WHITE + formatDuration(details.duration / 1000));
        lore.add(ChatColor.GRAY + "Kills: " + ChatColor.WHITE + highlightPlayer.kills);
        lore.add(ChatColor.GRAY + "Gold Collected: " + ChatColor.WHITE + highlightPlayer.goldCollected);
        lore.add(ChatColor.GRAY + "Score Contribution: " + ChatColor.WHITE + highlightPlayer.playerScore);
        lore.add("");
        lore.add(ChatColor.GRAY + "Team Difficulty: " + ChatColor.WHITE + String.format("%.2fx", details.difficultyMultiplier));

        meta.setLore(lore);
        sword.setItemMeta(meta);
        return sword;
    }

    private ItemStack createPowerUpsItem() {
        ItemStack beacon = new ItemStack(Material.BEACON);
        ItemMeta meta = beacon.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Power-Ups");

        List<String> lore = new ArrayList<>();
        lore.add("");

        if (highlightPowerUps.isEmpty()) {
            lore.add(ChatColor.GRAY + "No power-ups recorded.");
        } else {
            int count = 0;
            for (DatabaseManager.PowerUpInfo powerUp : highlightPowerUps) {
                lore.add(formatPowerUpLine(powerUp));
                if (++count >= 20) {
                    lore.add(ChatColor.DARK_GRAY + " ...");
                    break;
                }
            }
        }

        meta.setLore(lore);
        beacon.setItemMeta(meta);
        return beacon;
    }

    private ItemStack createItemsItem() {
        ItemStack chest = new ItemStack(Material.CHEST);
        ItemMeta meta = chest.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "Items Collected");

        List<String> lore = new ArrayList<>();
        lore.add("");

        if (highlightItems.isEmpty()) {
            lore.add(ChatColor.GRAY + "No items collected.");
        } else {
            int count = 0;
            for (DatabaseManager.ItemInfo item : highlightItems) {
                lore.add(formatItemLine(item));
                if (++count >= 20) {
                    lore.add(ChatColor.DARK_GRAY + " ...");
                    break;
                }
            }
        }

        meta.setLore(lore);
        chest.setItemMeta(meta);
        return chest;
    }

    private ItemStack createCloseButton() {
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta meta = barrier.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "Close");
        barrier.setItemMeta(meta);
        return barrier;
    }

    private void addStatLine(List<String> lore, String label, String value) {
        if (value != null) {
            lore.add(label + ChatColor.WHITE + ": " + value);
        }
    }

    private String formatStat(String key) {
        if (!highlightStats.containsKey(key)) {
            return null;
        }
        return String.format("%.1f", highlightStats.getOrDefault(key, 0.0));
    }

    private String formatPercentStat(String key) {
        if (!highlightStats.containsKey(key)) {
            return null;
        }
        return String.format("%.1f%%", highlightStats.getOrDefault(key, 0.0) * 100);
    }

    private String formatMultiplierStat(String key) {
        if (!highlightStats.containsKey(key)) {
            return null;
        }
        return String.format("%.2fx", highlightStats.getOrDefault(key, 0.0));
    }

    private String formatRegeneration() {
        if (!highlightStats.containsKey("regeneration")) {
            return null;
        }
        return String.format("%.2f HP/s", highlightStats.getOrDefault("regeneration", 0.0));
    }

    private String formatPowerUpLine(DatabaseManager.PowerUpInfo powerUp) {
        return ChatColor.GRAY + "- " + rarityColor(powerUp.rarity) + powerUp.name +
            ChatColor.DARK_GRAY + " [" + powerUp.type + "]";
    }

    private String formatItemLine(DatabaseManager.ItemInfo item) {
        return ChatColor.GRAY + "- " + rarityColor(item.rarity) + item.name;
    }

    private ChatColor rarityColor(String rarity) {
        if (rarity == null) {
            return ChatColor.WHITE;
        }
        return switch (rarity.toLowerCase()) {
            case "common" -> ChatColor.WHITE;
            case "uncommon" -> ChatColor.GREEN;
            case "rare" -> ChatColor.BLUE;
            case "epic" -> ChatColor.DARK_PURPLE;
            case "legendary" -> ChatColor.GOLD;
            case "mythic" -> ChatColor.LIGHT_PURPLE;
            default -> ChatColor.WHITE;
        };
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format("%d:%02d", minutes, secs);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (inventory == null || event.getInventory() != inventory) return;
        if (!event.getWhoClicked().equals(viewer)) return;

        event.setCancelled(true);
        if (event.getSlot() == 49) {
            viewer.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (inventory == null || event.getInventory() != inventory) return;
        if (!event.getPlayer().equals(viewer)) return;

        HandlerList.unregisterAll(this);
    }
}

