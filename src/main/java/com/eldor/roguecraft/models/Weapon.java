package com.eldor.roguecraft.models;

import org.bukkit.Material;

public class Weapon {
    private final WeaponType type;
    private int level;
    private double damage;
    private double range;
    private double attackSpeed; // Attacks per second
    private int projectileCount; // For multi-shot weapons
    private double areaOfEffect; // For AOE weapons
    
    public Weapon(WeaponType type) {
        this.type = type;
        this.level = 1;
        this.damage = type.getBaseDamage();
        this.range = type.getBaseRange();
        this.attackSpeed = type.getBaseAttackSpeed();
        this.projectileCount = type.getBaseProjectileCount();
        this.areaOfEffect = type.getBaseAOE();
    }
    
    public void upgrade() {
        this.level++;
        // Reduced scaling to prevent exponential power growth
        this.damage *= 1.15; // 15% damage increase per level (reduced from 20%)
        // Cap range scaling - only increase range every 2 levels, and cap at 2x base
        if (level % 2 == 0) {
            double maxRange = this.type.getBaseRange() * 2.0; // Cap at 2x base range
            this.range = Math.min(this.range * 1.05, maxRange); // 5% every 2 levels, capped
        }
        
        // Attack speed scaling - weapon-specific scaling
        if (this.type == WeaponType.ARROW_STORM) {
            // Arrow Storm: 5% per level (much slower) instead of 10%, capped at 2x base
            double maxAttackSpeed = this.type.getBaseAttackSpeed() * 2.0; // Cap at 2x base (5.0 for arrows)
            this.attackSpeed = Math.min(this.attackSpeed * 1.05, maxAttackSpeed);
        } else if (this.type == WeaponType.POTION_THROWER) {
            // Potion Thrower: Reduced scaling (5% per level instead of 10%), capped at 1.5x base
            double maxAttackSpeed = this.type.getBaseAttackSpeed() * 1.5;
            this.attackSpeed = Math.min(this.attackSpeed * 1.05, maxAttackSpeed);
        } else if (this.type == WeaponType.LIGHTNING_STRIKE) {
            // Lightning Strike: Reduced attack speed scaling (5% per level), capped at 1.5x base to prevent excessive fire rate
            double maxAttackSpeed = this.type.getBaseAttackSpeed() * 1.5;
            this.attackSpeed = Math.min(this.attackSpeed * 1.05, maxAttackSpeed);
        } else if (this.type == WeaponType.TNT_SPAWNER || this.type == WeaponType.FIREBALL) {
            // TNT and Fireball: Increased scaling (12% per level) to buff them
            this.attackSpeed *= 1.12;
        } else {
            // Other weapons: 10% per level
            this.attackSpeed *= 1.1;
        }
        
        // Projectile count scaling - slower and capped for Arrow Storm
        if (this.type == WeaponType.ARROW_STORM) {
            // Arrow Storm: Extra projectile every 5 levels instead of 3, capped at 3 total
            if (level % 5 == 0 && this.projectileCount < 3) {
                this.projectileCount++; // Extra projectile every 5 levels, max 3
            }
        } else {
            // Other weapons: Extra projectile every 3 levels
            if (level % 3 == 0) {
                this.projectileCount++;
            }
        }
        
        // AOE scaling - weapon-specific
        if (this.type == WeaponType.POTION_THROWER) {
            // Potion Thrower: Reduced AOE scaling (3% per level instead of 5%), capped at 1.3x base
            double maxAOE = this.type.getBaseAOE() * 1.3;
            this.areaOfEffect = Math.min(this.areaOfEffect * 1.03, maxAOE);
        } else if (this.type == WeaponType.TNT_SPAWNER || this.type == WeaponType.FIREBALL) {
            // TNT and Fireball: Increased AOE scaling (7% per level), capped at 2.0x base
            double maxAOE = this.type.getBaseAOE() * 2.0;
            this.areaOfEffect = Math.min(this.areaOfEffect * 1.07, maxAOE);
        } else {
            // Other weapons: 5% per level, capped at 1.5x base
            double maxAOE = this.type.getBaseAOE() * 1.5;
            this.areaOfEffect = Math.min(this.areaOfEffect * 1.05, maxAOE);
        }
        
        // Damage scaling - buff TNT and Fireball damage more, nerf Lightning Strike
        if (this.type == WeaponType.TNT_SPAWNER || this.type == WeaponType.FIREBALL) {
            // TNT and Fireball: Increased damage scaling (18% per level instead of 15%)
            this.damage *= 1.18;
        } else if (this.type == WeaponType.POTION_THROWER) {
            // Potion Thrower: Reduced damage scaling (12% per level instead of 15%)
            this.damage *= 1.12;
        } else if (this.type == WeaponType.LIGHTNING_STRIKE) {
            // Lightning Strike: Further reduced damage scaling (10% per level) to prevent one-shotting bosses
            this.damage *= 1.10;
        } else {
            // Other weapons: 15% damage increase per level
            this.damage *= 1.15;
        }
    }
    
    public WeaponType getType() {
        return type;
    }
    
    public int getLevel() {
        return level;
    }
    
    public double getDamage() {
        return damage;
    }
    
    public void setDamage(double damage) {
        this.damage = damage;
    }
    
    public double getRange() {
        return range;
    }
    
    public void setRange(double range) {
        this.range = range;
    }
    
    public double getAttackSpeed() {
        return attackSpeed;
    }
    
    public void setAttackSpeed(double attackSpeed) {
        this.attackSpeed = attackSpeed;
    }
    
    public int getProjectileCount() {
        return projectileCount;
    }
    
    public void setProjectileCount(int projectileCount) {
        this.projectileCount = projectileCount;
    }
    
    public double getAreaOfEffect() {
        return areaOfEffect;
    }
    
    public void setAreaOfEffect(double areaOfEffect) {
        this.areaOfEffect = areaOfEffect;
    }
    
    public long getAttackCooldownTicks() {
        // Convert attacks per second to ticks (20 ticks = 1 second)
        return (long) (20.0 / attackSpeed);
    }
    
    public enum WeaponType {
        FIREBALL(
            "Fireball Launcher",
            "Shoots auto-targeting fireballs at enemies",
            Material.FIRE_CHARGE,
            8.0,  // base damage
            20.0, // base range
            1.0,  // base attack speed (1 per second)
            1,    // base projectile count
            2.0   // base AOE
        ),
        ARROW_STORM(
            "Arrow Storm",
            "Rapid-fire arrows at nearby enemies",
            Material.ARROW,
            4.0,  // base damage
            25.0, // base range
            2.5,  // base attack speed (2.5 per second, reduced from 3.0 to prevent overpowered scaling)
            1,    // base projectile count
            0.0   // base AOE
        ),
        LIGHTNING_STRIKE(
            "Lightning Strike",
            "Summons lightning bolts on enemies",
            Material.END_ROD,
            15.0, // base damage
            15.0, // base range (reduced from 20.0 to prevent excessive range)
            0.5,  // base attack speed (0.5 per second)
            1,    // base projectile count
            3.0   // base AOE
        ),
        TNT_SPAWNER(
            "TNT Spawner",
            "Spawns primed TNT near enemies",
            Material.TNT,
            20.0, // base damage
            15.0, // base range
            0.33, // base attack speed (1 per 3 seconds)
            1,    // base projectile count
            5.0   // base AOE
        ),
        POTION_THROWER(
            "Potion Thrower",
            "Throws harmful potions at enemies",
            Material.SPLASH_POTION,
            6.0,  // base damage
            18.0, // base range
            1.5,  // base attack speed (1.5 per second)
            1,    // base projectile count
            4.0   // base AOE
        ),
        ICE_SHARD(
            "Ice Shard",
            "Launches ice shards that slow enemies",
            Material.ICE,
            5.0,  // base damage
            22.0, // base range
            2.0,  // base attack speed (2 per second)
            1,    // base projectile count
            1.5   // base AOE
        ),
        MAGIC_MISSILE(
            "Magic Missile",
            "Homing magical projectiles",
            Material.GLOWSTONE_DUST,
            12.0, // base damage (increased from 7.0)
            28.0, // base range
            2.5,  // base attack speed (2.5 per second)
            1,    // base projectile count
            0.5   // base AOE
        );
        
        private final String displayName;
        private final String description;
        private final Material icon;
        private final double baseDamage;
        private final double baseRange;
        private final double baseAttackSpeed;
        private final int baseProjectileCount;
        private final double baseAOE;
        
        WeaponType(String displayName, String description, Material icon,
                   double baseDamage, double baseRange, double baseAttackSpeed,
                   int baseProjectileCount, double baseAOE) {
            this.displayName = displayName;
            this.description = description;
            this.icon = icon;
            this.baseDamage = baseDamage;
            this.baseRange = baseRange;
            this.baseAttackSpeed = baseAttackSpeed;
            this.baseProjectileCount = baseProjectileCount;
            this.baseAOE = baseAOE;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
        
        public Material getIcon() {
            return icon;
        }
        
        public double getBaseDamage() {
            return baseDamage;
        }
        
        public double getBaseRange() {
            return baseRange;
        }
        
        public double getBaseAttackSpeed() {
            return baseAttackSpeed;
        }
        
        public int getBaseProjectileCount() {
            return baseProjectileCount;
        }
        
        public double getBaseAOE() {
            return baseAOE;
        }
    }
}

