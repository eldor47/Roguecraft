package com.eldor.roguecraft.managers;

import com.eldor.roguecraft.RoguecraftPlugin;
import com.eldor.roguecraft.models.GachaItem;
import org.bukkit.Material;

import java.util.*;

/**
 * Manages gacha items and roll logic
 */
public class GachaManager {
    private final RoguecraftPlugin plugin;
    private final Map<String, GachaItem> items;
    private final Map<GachaItem.ItemRarity, List<GachaItem>> itemsByRarity;
    
    // Roll probabilities (inspired by Megabonk)
    private static final double COMMON_CHANCE = 0.50;      // 50%
    private static final double UNCOMMON_CHANCE = 0.30;     // 30%
    private static final double RARE_CHANCE = 0.15;         // 15%
    private static final double LEGENDARY_CHANCE = 0.05;   // 5%
    
    public GachaManager(RoguecraftPlugin plugin) {
        this.plugin = plugin;
        this.items = new HashMap<>();
        this.itemsByRarity = new HashMap<>();
        
        // Initialize rarity lists
        for (GachaItem.ItemRarity rarity : GachaItem.ItemRarity.values()) {
            itemsByRarity.put(rarity, new ArrayList<>());
        }
        
        registerItems();
    }
    
    /**
     * Register all gacha items
     * Starting with ~15 items inspired by Megabonk
     */
    private void registerItems() {
        // ===== COMMON ITEMS (Green) =====
        registerItem(new GachaItem(
            "moldy_cheese",
            "Moldy Cheese",
            "§7+40% chance to poison enemies on hit",
            GachaItem.ItemRarity.COMMON,
            Material.ROTTEN_FLESH,
            GachaItem.ItemEffect.ON_HIT_EFFECT,
            0.40
        ));
        
        registerItem(new GachaItem(
            "clover",
            "Clover",
            "§7Increase Luck by +7.5%",
            GachaItem.ItemRarity.COMMON,
            Material.FERN,
            GachaItem.ItemEffect.STAT_BOOST,
            0.075
        ));
        
        registerItem(new GachaItem(
            "time_bracelet",
            "Time Bracelet",
            "§7+8% XP Gain",
            GachaItem.ItemRarity.COMMON,
            Material.CLOCK,
            GachaItem.ItemEffect.STAT_BOOST,
            0.08
        ));
        
        registerItem(new GachaItem(
            "gym_sauce",
            "Gym Sauce",
            "§7Increase Damage by +10%",
            GachaItem.ItemRarity.COMMON,
            Material.POTION,
            GachaItem.ItemEffect.STAT_BOOST,
            0.10
        ));
        
        registerItem(new GachaItem(
            "oats",
            "Oats",
            "§7Increase Max HP by +10",
            GachaItem.ItemRarity.COMMON,
            Material.WHEAT,
            GachaItem.ItemEffect.STAT_BOOST,
            10.0
        ));
        
        registerItem(new GachaItem(
            "medkit",
            "Medkit",
            "§7Increase HP Regen by +2",
            GachaItem.ItemRarity.COMMON,
            Material.GOLDEN_APPLE,
            GachaItem.ItemEffect.PASSIVE_EFFECT,
            2.0
        ));
        
        // ===== UNCOMMON ITEMS (Blue) =====
        registerItem(new GachaItem(
            "turbo_socks",
            "Turbo Socks",
            "§9Increase Movement Speed by +15%",
            GachaItem.ItemRarity.UNCOMMON,
            Material.LEATHER_BOOTS,
            GachaItem.ItemEffect.STAT_BOOST,
            0.15
        ));
        
        registerItem(new GachaItem(
            "battery",
            "Battery",
            "§9Increase Attack Speed by +8%",
            GachaItem.ItemRarity.UNCOMMON,
            Material.REDSTONE,
            GachaItem.ItemEffect.STAT_BOOST,
            0.08
        ));
        
        registerItem(new GachaItem(
            "forbidden_juice",
            "Forbidden Juice",
            "§9Increase Crit Chance by +10%",
            GachaItem.ItemRarity.UNCOMMON,
            Material.HONEY_BOTTLE,
            GachaItem.ItemEffect.STAT_BOOST,
            0.10
        ));
        
        registerItem(new GachaItem(
            "ice_crystal",
            "Ice Crystal",
            "§9+7.5% chance to freeze enemies on hit",
            GachaItem.ItemRarity.UNCOMMON,
            Material.ICE,
            GachaItem.ItemEffect.ON_HIT_EFFECT,
            0.075
        ));
        
        // ===== RARE ITEMS (Magenta) =====
        registerItem(new GachaItem(
            "boss_buster",
            "Boss Buster",
            "§d+15% more damage to Elites and Bosses",
            GachaItem.ItemRarity.RARE,
            Material.DIAMOND_SWORD,
            GachaItem.ItemEffect.SPECIAL_ABILITY,
            0.15
        ));
        
        registerItem(new GachaItem(
            "cursed_doll",
            "Cursed Doll",
            "§dCurse an enemy, dealing 30% of their Max HP every second",
            GachaItem.ItemRarity.RARE,
            Material.PLAYER_HEAD,
            GachaItem.ItemEffect.ON_HIT_EFFECT,
            0.30
        ));
        
        registerItem(new GachaItem(
            "golden_glove",
            "Golden Glove",
            "§dEarn +15% more Gold from killing enemies",
            GachaItem.ItemRarity.RARE,
            Material.GOLDEN_APPLE,
            GachaItem.ItemEffect.PASSIVE_EFFECT,
            0.15
        ));
        
        // ===== LEGENDARY ITEMS (Yellow) =====
        registerItem(new GachaItem(
            "big_bonk",
            "Big Bonk",
            "§e2% chance to BONK an enemy, dealing 20x damage",
            GachaItem.ItemRarity.LEGENDARY,
            Material.ANVIL,
            GachaItem.ItemEffect.ON_HIT_EFFECT,
            0.02
        ));
        
        registerItem(new GachaItem(
            "spicy_meatball",
            "Spicy Meatball",
            "§eAttacks have a 25% chance to explode, dealing 65% Damage to surrounding enemies",
            GachaItem.ItemRarity.LEGENDARY,
            Material.FIRE_CHARGE,
            GachaItem.ItemEffect.ON_HIT_EFFECT,
            0.25
        ));
        
        registerItem(new GachaItem(
            "power_gloves",
            "Power Gloves",
            "§e8% chance upon hitting an enemy to create a giant blast, damaging and knocking away enemies",
            GachaItem.ItemRarity.LEGENDARY,
            Material.NETHERITE_INGOT,
            GachaItem.ItemEffect.ON_HIT_EFFECT,
            0.08
        ));
        
        plugin.getLogger().info("[Gacha] Registered " + items.size() + " gacha items");
    }
    
    private void registerItem(GachaItem item) {
        items.put(item.getId(), item);
        itemsByRarity.get(item.getRarity()).add(item);
    }
    
    /**
     * Perform a gacha roll and return a random item based on rarity probabilities
     * Luck stat affects the roll - higher luck increases chances for better rarities
     * 
     * @param luck The player's luck stat (1.0 = base, higher = better chances)
     * @return A random gacha item
     */
    public GachaItem roll(double luck) {
        Random random = new Random();
        double roll = random.nextDouble();
        
        // Calculate luck-adjusted probabilities
        // Luck acts as a multiplier that shifts probability from common to higher rarities
        // Base luck is 1.0, so at 1.0 luck we use base probabilities
        // At 2.0 luck, we roughly double the chances for rare/legendary
        // Cap luck effect at 3.0x to prevent it from being too overpowered
        
        double effectiveLuck = Math.min(luck, 3.0); // Cap at 3x
        double luckMultiplier = (effectiveLuck - 1.0) * 0.5 + 1.0; // Scale from 1.0 to 2.0
        
        // Adjust probabilities based on luck
        // Higher luck reduces common chance and increases higher rarity chances
        double commonChance = COMMON_CHANCE / luckMultiplier;
        double uncommonChance = UNCOMMON_CHANCE * luckMultiplier;
        double rareChance = RARE_CHANCE * luckMultiplier;
        double legendaryChance = LEGENDARY_CHANCE * luckMultiplier;
        
        // Normalize probabilities to ensure they sum to 1.0
        double total = commonChance + uncommonChance + rareChance + legendaryChance;
        commonChance /= total;
        uncommonChance /= total;
        rareChance /= total;
        legendaryChance /= total;
        
        // Determine rarity based on adjusted probabilities
        GachaItem.ItemRarity rarity;
        if (roll < legendaryChance) {
            rarity = GachaItem.ItemRarity.LEGENDARY;
        } else if (roll < legendaryChance + rareChance) {
            rarity = GachaItem.ItemRarity.RARE;
        } else if (roll < legendaryChance + rareChance + uncommonChance) {
            rarity = GachaItem.ItemRarity.UNCOMMON;
        } else {
            rarity = GachaItem.ItemRarity.COMMON;
        }
        
        // Get random item from that rarity
        List<GachaItem> rarityItems = itemsByRarity.get(rarity);
        if (rarityItems.isEmpty()) {
            // Fallback to common if rarity is empty
            rarityItems = itemsByRarity.get(GachaItem.ItemRarity.COMMON);
        }
        
        return rarityItems.get(random.nextInt(rarityItems.size()));
    }
    
    /**
     * Perform a gacha roll with base luck (1.0) - for backwards compatibility
     */
    public GachaItem roll() {
        return roll(1.0);
    }
    
    /**
     * Get item by ID
     */
    public GachaItem getItem(String id) {
        return items.get(id);
    }
    
    /**
     * Get all items
     */
    public Collection<GachaItem> getAllItems() {
        return items.values();
    }
    
    /**
     * Get items by rarity
     */
    public List<GachaItem> getItemsByRarity(GachaItem.ItemRarity rarity) {
        return new ArrayList<>(itemsByRarity.get(rarity));
    }
}

