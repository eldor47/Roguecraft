# Roguecraft - Minecraft Roguelike Plugin

[![Spigot](https://img.shields.io/badge/Spigot-1.21.10-blue.svg)](https://www.spigotmc.org/)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A Minecraft server plugin that delivers a simple, high-replayability roguelike/roguelite experience inspired by **Megabonk** (3D auto-attack horde survival). Built on **Spigot API 1.21.10** for maximum compatibility with Bukkit, Paper, and Purpur servers.

## Features

### Core Gameplay
- **Arena Realm System** - Enter corrupted rifts with world border visualization and fight escalating swarms of mobs
- **Wave Progression** - Waves advance every 30 seconds automatically, with difficulty scaling each wave
- **Infinite Wave Mode** - After max wave (default: 20), endless waves spawn difficult mobs (Ravager, Blaze, Vindicator, Skeleton, Zombie, Wither Skeleton, Enderman) with progressive difficulty. All infinite wave mobs are elite, and a Wither boss spawns on wave 20
- **Auto-Attack Weapon System** - Choose from 7 unique weapon types with auto-targeting projectiles
- **Dynamic Power-Up System** - Procedurally generated upgrades scaled by your Luck stat (no manual configuration required!)
- **Intelligent Level-Up GUI** - Chest-style GUI showing 3 unique randomized upgrades with rarity colors and current stats display
- **Physical Shrine System** - 2-3 shrines spawn in arena, channel for 4 seconds to activate powerful temporary buffs
- **Custom Drop System** - Mobs drop XP tokens (5% base, most common), healing hearts (0.5% base, rare), and power-ups (0.25% base, very rare) instead of normal drops
- **XP Boss Bar** - Live XP progress displayed at the top of your screen with color-coded progress
- **Health Action Bar** - Real-time health display above your hotbar (❤ 25.0/40.0)
- **Rarity System** - Common → Rare → Epic → Legendary power-ups, influenced by Luck
- **Difficulty Scaling** - Automatically scales with time survived, wave number, player count, player level, and player-selected difficulty stat
- **Elite Mobs** - Elite enemies are physically larger (1.5x size), glowing, have custom names, 2.5x HP, deal 1.75x damage (scales in infinite waves), and have a 5% base spawn chance
- **Legendary Mobs** - Rare elite variant (0.4x bigger than elite, 2.1x total size) with golden glow, increased resistance, damage, and XP rewards
- **Visual Mob Health Display** - Mobs show their current/max health in their name tags, color-coded by health percentage
- **Automatic Sunlight Protection** - Barrier blocks are automatically placed above the arena at world height to prevent undead mobs from burning, then cleaned up when the run ends
- **Multiplayer Support** - Solo or co-op with scaled enemy HP/spawn counts and shared team progression

### Advanced Systems
- **9 Dynamic Stats** - Health, Damage, Speed, Armor (visible in HUD), Crit Chance, Crit Damage, Luck, XP Multiplier, **Difficulty**
- **Difficulty Stat** - Players can increase difficulty via power-ups for harder enemies and better rewards
- **Luck-Based Progression** - Higher Luck = better rarity rolls and stronger stat bonuses
- **Weapon Upgrades** - Power-up cards can upgrade your weapon (1-5 levels based on rarity)
- **Movement Speed** - Speed stat directly affects player movement via attributes
- **Mob Freezing** - All mobs and projectiles pause immediately when GUI opens (prevents damage during selection)
- **GUI Queue System** - Multiple GUIs (level-up, shrine) queue properly to prevent conflicts
- **Comprehensive Cleanup** - Proper reset of health, speed, mobs, borders, shrines, and tasks on run end/quit/death

### Technical Features
- **Configurable Progression** - YAML tables for game balance and mob spawns
- **Soft Dependencies** - Optional integrations with ProtocolLib, PlaceholderAPI, WorldGuard, and Vault
- **Team Synchronization** - Shared XP, levels, and weapon selection for co-op teams
- **Attribute-Based Stats** - Uses Minecraft's native attribute system for health, speed, and armor (visible in HUD like hearts)

## Requirements

- **Minecraft:** 1.21.10+
- **Java:** 21 (LTS)
- **Server:** Spigot, Paper, Purpur, or any Bukkit-based server
- **API:** Spigot API 1.21.10

### Optional Soft Dependencies

- **ProtocolLib** - Custom visual effects and packet-based telegraphs
- **PlaceholderAPI** - Display live stats on HUDs and scoreboards
- **WorldGuard** - Arena region protection and event triggers
- **Vault** - Economy and cosmetic purchases

## Installation

1. **Download** the latest `roguecraft.jar` from releases
2. **Place** the jar in your server's `plugins` folder
3. **Start** your server to generate configuration files
4. **Stop** your server
5. **Edit** `plugins/Roguecraft/config.yml` and configure arenas
6. **Edit** `plugins/Roguecraft/configs/balance.yml` and `spawns.yml` as needed
7. **Start** your server again

## Quick Start

1. **Set up an arena** in `config.yml`:
   ```yaml
   arenas:
     default:
       name: "Default Arena"
       default: true
       spawn: world,0,100,0,0,0
       center: world,0,100,0
       radius: 50.0
   ```

2. **Start a run**:
   ```
   /rc start
   ```

3. **Choose your weapon** from 7 unique types (Fireball, Arrow Storm, Lightning Strike, TNT Spawner, Potion Thrower, Ice Shard, Magic Missile)

4. **Fight mobs** - Your weapon auto-attacks nearby enemies, collect XP

5. **Level up** - XP bar fills at top of screen, choose power-ups from GUI

6. **Check your stats** - Health displayed on action bar, view full stats in power-up GUI

7. **Survive** as long as possible!

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/roguecraft` or `/rc` | Start a run or open GUI | `roguecraft.play` |
| `/rc start [arena]` | Start a new run | `roguecraft.play` |
| `/rc stop` | End your current run | `roguecraft.play` |
| `/rc stats` | View your current run statistics | `roguecraft.play` |
| `/rc gui` | Open power-up selection GUI | `roguecraft.play` |
| `/rc reload` | Reload configuration | `roguecraft.admin.reload` |

**Aliases:** `/roguecraft`, `/rc`, `/roguelike`

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `roguecraft.play` | Allows players to play Roguecraft | `true` |
| `roguecraft.admin` | All admin commands | `op` |
| `roguecraft.admin.reload` | Reload configuration | `op` |
| `roguecraft.admin.arena` | Manage arenas | `op` |
| `roguecraft.admin.setup` | Set up arena regions | `op` |
| `roguecraft.*` | All permissions | `op` |

## Weapon System

### 7 Unique Auto-Attack Weapons

Each weapon has distinct stats that scale with upgrades:

1. **Fireball Launcher** 🔥
   - Shoots auto-targeting fireballs at enemies
   - Medium damage, medium range, AOE explosion
   - Base: 8 damage, 20 range, 1/s, 2 AOE

2. **Arrow Storm** 🏹
   - Rapid-fire arrows at nearby enemies
   - Low damage, high speed, long range
   - Base: 4 damage, 25 range, 3/s, no AOE

3. **Lightning Strike** ⚡
   - Summons lightning bolts on enemies
   - High damage, slow speed, large AOE
   - Base: 15 damage, 30 range, 0.5/s, 3 AOE

4. **TNT Spawner** 💣
   - Spawns primed TNT near enemies
   - Very high damage, very slow, massive AOE
   - Base: 20 damage, 15 range, 0.33/s, 5 AOE

5. **Potion Thrower** 🧪
   - Throws harmful splash potions at enemies
   - Medium damage, applies poison & weakness to enemies only
   - Base: 6 damage, 18 range, 1.5/s, 4 AOE
   - **Note:** Potions do not affect the player

6. **Ice Shard** ❄️
   - Launches ice shards that slow and freeze enemies
   - Low damage, fast speed, applies slow and freeze effects
   - Base: 5 damage, 22 range, 2/s, 1.5 AOE
   - **Note:** Damage applied on impact with AOE falloff

7. **Magic Missile** ✨
   - Homing magical projectiles
   - Medium damage, fast speed, slight AOE
   - Base: 7 damage, 28 range, 2.5/s, 0.5 AOE

**Weapon Upgrades:**
- Appear as power-up cards during level-up
- Rarity determines upgrade levels: Common (+1), Rare (+1), Epic (+2), Legendary (+3)
- Scales damage, range, attack speed, projectile count, and AOE
- Range and AOE are capped to prevent exponential growth

## Dynamic Power-Up System

### No Configuration Required!
Power-ups are **procedurally generated** each level-up based on your current stats and Luck value. No manual card configuration needed!

### Stat System

All stats are tracked and applied in real-time:

| Stat | Description | Display |
|------|-------------|---------|
| **Health** ❤️ | Maximum health (applies via attributes) | Action bar + Hearts |
| **Damage** ⚔️ | Base weapon damage multiplier | Power-up GUI |
| **Speed** ☄️ | Movement speed (applies via attributes) | Visible movement |
| **Armor** 🛡️ | Damage reduction (visible in HUD as armor icons) | Power-up GUI + HUD |
| **Crit Chance** ◈ | Chance to deal critical hits | Power-up GUI |
| **Crit Damage** ★ | Critical hit damage multiplier | Power-up GUI |
| **Luck** ☘️ | **Affects rarity rolls & bonus values** | Power-up GUI |
| **XP Multiplier** 🧪 | Increases XP gained from kills | Boss bar progress |
| **Difficulty** ☠️ | **Multiplies enemy difficulty (HP, damage, spawns)** | Power-up GUI |

### How Luck Works

**Luck is the meta-progression stat:**
- Base luck: 1.0
- Higher luck = increased chance for Rare/Epic/Legendary power-ups
- Higher luck = 0-40% bonus to all stat values
- Luck stacks additively (+0.15 per Common boost, more for higher rarities)
- **Example:** With 2.5 Luck, you're much more likely to see Legendary cards!

### Power-Up Types

Power-ups are generated on-the-fly in 6 categories:

1. **Stat Boosts** (50% chance) ✅ **FULLY IMPLEMENTED**
   - Randomly selects one of 9 stats to boost
   - **Difficulty stat** has 15% chance to appear (vs 85% for other stats)
   - Value scales with: player level × rarity × luck
   - Always unique - won't see duplicate stats in same selection
   - Icon and color match the stat type
   - **Difficulty boosts** make enemies harder but don't affect XP rewards
   - **All stats apply correctly**: Health, Speed (via attributes), Damage, Armor (visible in HUD), Crit Chance, Crit Damage, Luck, XP Multiplier, Difficulty

2. **Weapon Upgrades** (25% chance) ✅ **FULLY IMPLEMENTED**
   - Upgrades your weapon by 1-3 levels
   - Common/Rare: +1 level, Epic: +2 levels, Legendary: +3 levels
   - Scales all weapon stats simultaneously (damage, range, attack speed, AOE)
   - Range and AOE are capped to prevent exponential growth
   - Icon: Enchanted Book

3. **Weapon Mods** (25% chance) ✅ **FULLY IMPLEMENTED**
   - Special modifiers that enhance weapon attacks
   - **Rapid Fire** - Increases weapon attack speed
   - **Homing Projectiles** - Arrows and fireballs track nearby enemies
   - **Explosive Rounds** - Fireballs create larger explosions
   - **Piercing Shot** - Arrows pass through multiple enemies
   - **Chain Lightning** - Lightning strikes chain to nearby enemies
   - **Frost Nova** - Ice shards create area slow/freeze effects
   - **Burn Effect** - Fire attacks set enemies on fire
   - Icon: Blaze Powder

4. **AURA Power-Ups** (can appear in power-up pool) ✅ **FULLY IMPLEMENTED**
   - **Vampire Aura** ✅ - Lifesteal % of damage dealt (heals on every attack, capped at 2 hearts/4 HP per second, 25% spawn chance)
   - **Thorns Aura** ✅ - Reflect % damage to attackers
   - **Regeneration Aura** ✅ - Heal HP every 5 seconds
   - **Fire Aura** ✅ - Nearby enemies burn for damage/sec
   - **Ice Aura** ✅ - Slow nearby enemies by %
   - **Lightning Aura** ✅ - Chain lightning every 3 seconds
   - **Poison Aura** ✅ - Poison nearby enemies for damage/sec
   - **Shield Aura** ✅ - Absorb damage before taking HP loss
   - **Note:** Auras pause when player is in GUI
   - Icon: Totem of Undying

5. **SHRINE Power-Ups** (physical shrines in arena) ✅ **FULLY IMPLEMENTED**
   - Physical shrines spawn in arena (2-3 per run)
   - Channel for 4 seconds to activate
   - 8 shrine types with 3 buff variants each
   - See [Physical Shrine System](#physical-shrine-system) section for details
   - Icon: Beacon

6. **SYNERGY Power-Ups** (can appear in power-up pool) ✅ **FULLY IMPLEMENTED**
   - **Critical Mass** ✅ - Crits explode for AOE damage
   - **Elemental Fusion** ✅ - Weapon effects stack and multiply (capped at 1.15x max, reduced scaling)
   - **Rapid Escalation** ✅ - Gain damage per kill (stacks)
   - **Chain Reaction** ✅ - Kills have chance to trigger free attack
   - **Berserker Mode** ✅ - Gain damage when below 30% HP
   - **Glass Cannon** ✅ - +damage, -50% max HP (applied immediately on selection)
   - **Immortal Build** ✅ - Cannot die for X seconds after fatal damage (30s cooldown)
   - **Lucky Streak** ✅ - Every 20 kills (scales with value, min 5) grants random power-up effect
   - **Note:** Synergies pause when player is in GUI
   - Icon: Nether Star

### Implementation Status

**✅ Fully Working:**
- All 9 stat boosts (Health, Speed, Damage, Armor, Crit Chance, Crit Damage, Luck, XP Multiplier, Difficulty)
- Weapon Upgrades (1-3 levels based on rarity)
- Physical Shrines (8 types, 3 variants each)
- All AURA effects (Vampire, Thorns, Regeneration, Fire, Ice, Lightning, Poison, Shield)
- All SYNERGY effects (Critical Mass, Elemental Fusion, Rapid Escalation, Chain Reaction, Berserker Mode, Glass Cannon, Immortal Build, Lucky Streak)
- All WEAPON_MOD effects (Rapid Fire, Homing Projectiles, Explosive Rounds, Piercing Shot, Chain Lightning, Frost Nova, Burn Effect)

### Rarity Tiers

Rarity is determined **dynamically** by your Luck stat:

- **Common** (White) - Base tier, always available
- **Rare** (Blue) - 1.5× stat multiplier
- **Epic** (Purple) - 2.25× stat multiplier
- **Legendary** (Gold) - 3.5× stat multiplier

Higher luck shifts the probability distribution toward better rarities!

### Level-Up GUI Features

When you level up:
- **3 Unique Power-Ups** - No duplicates in a single selection
- **Current Stats Display** - Book icon shows all your stats
- **Rarity Color Coding** - Instant visual feedback
- **Stat Preview** - See exact values before selecting
- **Limited Rerolls** - Default 2 rerolls per run
- **Mob Freezing** - All enemies freeze immediately when GUI opens
- **Projectile Removal** - All enemy projectiles removed when GUI opens
- **Damage Cancellation** - All damage canceled while GUI is open
- **Weapon Pause** - Auto-attacks stop during selection
- **Inventory Auto-Close** - Player's inventory automatically closes if open
- **Increased Damage/Crit Spawn** - Damage and crit chance power-ups have higher spawn rates (20% and 18% respectively)

## Visual Feedback Systems

### XP Boss Bar
- Displayed at top of screen
- Shows: `Wave X | Level Y | XP: current/required` (wave only shown during active runs)
- Color-coded progress: Red (0-33%), Yellow (33-66%), Green (66-100%)
- Flashes gold on level-up with "★ LEVEL UP! ★" message
- No more XP spam in chat!

### Health Action Bar
- Constant display above hotbar
- Format: `❤ 25.0/40.0` (current/max)
- Updates every 0.5 seconds
- Color-coded: Red heart, white numbers, gray separator
- Shows immediately when health changes

### Mob Health Display
- Mobs show their current/max health in their name tags
- Format: `[12.5/25.9]` color-coded by health percentage
- Elite mobs show: `⚡ ELITE [12.5/25.9]`
- Health colors: Green (>75%), Yellow (>50%), Gold (>25%), Red (≤25%)
- Updates every second for nearby mobs
- Cleared on death to prevent log spam

### World Border Visualization
- Blue barrier appears around arena when run starts
- Shows exact arena radius boundaries
- Restored to original settings on run end
- No damage to players

### Custom Drop System
Mobs in arenas drop custom items instead of normal Minecraft drops:

**XP Tokens** ✨
- 5% base drop chance (20% for elite mobs)
- Grants bonus XP (default: 50 XP, configurable)
- XP multiplier stat applies to token XP
- Pick up to instantly gain XP
- Icon: Experience Bottle

**Healing Hearts** ❤
- 3% base drop chance (13% for elite mobs)
- Restores health (default: 4.0 HP / 2 hearts, configurable)
- Pick up to instantly heal
- Visual feedback: Heart particles + sound
- Icon: Red Dye

**Power-Up Drops** ⭐
- 0.25% base drop chance (very rare, fixed, no elite bonus)
- Only increases with player's `drop_rate` stat
- Grants temporary power-up effects (Speed Boost, Time Freeze) or permanent stat increases
- Icon: Nether Star

**Drop Rates:**
- **XP Tokens**: 5% base chance (most common, +10% for elites = 15% total)
- **Hearts**: 0.5% base chance (rare, +1.5% for elites = 2% total)
- **Power-Ups**: 0.25% base chance (very rare, only scales with drop_rate stat)

**Features:**
- Normal mob drops are disabled (configurable)
- Elite mobs have higher drop rates for XP tokens and hearts (not power-ups)
- Items are automatically picked up when touched
- Drops only work during active runs
- Fully configurable drop rates and values
- **Capped Stat Exclusion** - Regeneration and Vampire Aura power-ups won't appear if you're already at the healing cap (4 HP/second)

## Configuration

### Main Config (`config.yml`)

```yaml
# Arena Settings
arenas:
  default:
    name: "Default Arena"
    default: true
    spawn: world,0,100,0,0,0    # Format: world,x,y,z,yaw,pitch
    center: world,0,100,0         # Arena center for mob spawning
    radius: 50.0                  # Arena radius in blocks

# Game Settings
game:
  base-xp: 100
  xp-multiplier: 1.5
  default-rerolls: 2
  night-duration: 900
  wave-interval: 10
  sunlight-protection: true  # Automatically place barrier blocks above arena to prevent sunlight damage

# Multiplayer Settings
multiplayer:
  enabled: true
  hp-multiplier-per-player: 1.5       # Each player adds 50% more mob HP
  spawn-multiplier-per-player: 1.3    # Each player adds 30% more mob spawns

# Visualization Settings
visualization:
  enabled: true
  method: "WORLD_BORDER"    # Arena boundary visualization

# Drop System
drops:
  disable-normal-drops: true  # Disable vanilla mob drops
  xp-token:
    enabled: true
    base-chance: 0.05         # 5% base chance
    elite-bonus: 0.15         # +15% for elites (total 20%)
    xp-amount: 50             # XP per token
  heart:
    enabled: true
    base-chance: 0.03         # 3% base chance
    elite-bonus: 0.10         # +10% for elites (total 13%)
    heal-amount: 4.0          # Health restored
```

### Balance Config (`configs/balance.yml`)

Defines difficulty scaling, experience values, elite mobs, and wave settings:

```yaml
# Elite Mobs
elites:
  hp-multiplier: 2.5        # Elite mobs have 2.5x HP
  damage-multiplier: 1.5    # Elite mobs deal 1.5x damage (base, scales in infinite waves)
  size-multiplier: 1.5      # Elite mobs are physically 1.5x larger (using GENERIC_SCALE attribute)
  spawn-chance: 0.05        # 5% base chance for any mob to be elite
  xp-multiplier: 2.0        # Elite mobs give 2x XP

# Wave Settings
waves:
  max-wave: 20              # Maximum wave before infinite mode (Wither boss spawns on this wave)
  infinite:
    mob-types:              # 2-3 mob types randomly selected per wave (all spawn as elite)
      - RAVAGER
      - BLAZE
      - VINDICATOR
      - SKELETON
      - ZOMBIE
    base-count: 15          # Starting spawn count (increased difficulty)
    count-increase-per-wave: 5    # How many more mobs per wave (increased difficulty)
    difficulty-increase-per-wave: 0.15  # Difficulty scaling per wave

# Experience Values
experience:
  base:
    # XP values for each mob type (configurable)
    zombie: 10
    skeleton: 12
    spider: 8
    # ... (see balance.yml for full list)
```

### Spawns Config (`configs/spawns.yml`)

Defines mob spawns for each wave:
- Entity types including: ZOMBIE, SKELETON, SPIDER, CREEPER, ENDERMAN, WITCH, WITHER_SKELETON, HUSK, STRAY, CAVE_SPIDER, DROWNED, PILLAGER, VINDICATOR, PHANTOM, SLIME, SILVERFISH, RAVAGER
- Spawn counts
- Weights (spawn probability)
- Elite status

**Features:**
- **Default waves** - Pre-configured for waves 1-20 with progressive difficulty
- **Dynamic scaling** - Missing waves automatically scale from nearest lower wave
- **Variety progression** - New mob types appear at specific waves:
  - Wave 4+: Husk, Stray
  - Wave 6+: Cave Spider, Slime
  - Wave 8+: Drowned
  - Wave 12+: Pillager, Phantom
  - Wave 15+: Vindicator, Silverfish
  - Wave 20+: Ravager
- **Elite spawn chance** - Elites use base spawn chance (5% by default) from balance.yml, not forced at higher waves

**Example:**
```yaml
spawns:
  "1":
    zombie_1:
      type: ZOMBIE
      count: 5
      weight: 1.0
      elite: false
  "4":
    husk_1:
      type: HUSK
      count: 2
      weight: 0.6
      elite: false
  "12":
    pillager_1:
      type: PILLAGER
      count: 2
      weight: 0.4
      elite: false
```

**Note:** Power-up configuration (`cards.yml`) is no longer used - all power-ups are generated dynamically!

## Gameplay Loop

1. **Enter Arena** - Use `/rc start` to enter the Arena Realm
   - World border appears showing arena boundaries
   - Choose your starting weapon from 7 types

2. **Auto-Combat** - Your weapon automatically targets and attacks enemies
   - No manual aiming required
   - XP boss bar shows your progress
   - Health action bar displays current HP

3. **Collect XP** - Defeat mobs to gain experience
   - XP scales with your XP Multiplier stat
   - Shared across all team members

4. **Level Up** - XP bar fills and you gain a level
   - Boss bar flashes gold with celebration
   - Mobs and projectiles freeze
   - Choose from 3 unique power-ups

5. **Power Up** - Select your upgrade
   - Stat boosts apply instantly (health, speed)
   - Weapon upgrades make your attacks stronger
   - GUI shows your current stats

6. **Escalating Difficulty** - Waves get harder over time
   - Waves advance every 30 seconds automatically
   - More mobs spawn, new types appear at higher waves
   - Elite enemies appear more frequently (glowing, larger, named)
   - Mob HP, damage, and movement speed increase with wave
   - Mob speed scales: +2% per wave (capped at +100%)
   - Elite mobs gain damage resistance starting at wave 10 (10% → 20% → 30% → scales in infinite waves)
   - Elite mobs deal progressively more damage in infinite waves (1.5x base + 0.1x per infinite wave)
   - Player difficulty stat multiplies all enemy stats

7. **Physical Shrines** - Power-up stations in the arena
   - 2-3 shrines spawn randomly around the arena
   - Stand near a shrine for 4 seconds to channel
   - Choose from 3 powerful buff variants (damage, speed, healing, XP, crit, invulnerability, etc.)
   - Each shrine can only be used once per run
   - Shrines visually dim after use

8. **Wither Boss** - Final challenge on wave 20
   - A single Wither boss spawns at the start of wave 20
   - Health scales with player level and difficulty stat
   - Red elite outline, 2.5x size, high resistance, and aggro on players
   - Defeat the Wither to complete the regular waves
   - After wave 20, infinite mode begins

9. **Infinite Wave Mode** - After wave 20 (configurable)
   - Randomly selects 2-3 mob types from: Ravager, Blaze, Vindicator, Skeleton, Zombie, Wither Skeleton, Enderman
   - All infinite mobs are guaranteed elite
   - Spawn count increases: Wave 21 = 40 mobs (base), increases by 8 per wave
   - Difficulty increases: +0.15 per wave
   - Elite damage resistance scales: 30% + 3% per infinite wave (60% at wave 30, caps at 98%)
   - Elite damage scales: 1.75x base + 0.1x per infinite wave (uncapped)
   - Endless challenge!

10. **Survive or Die** - Last as long as possible
   - Health displayed constantly
   - Proper cleanup on death/quit (shrines, channeling, tasks)
   - Stats reset after run ends
   - Challenge yourself: Increase difficulty stat for harder enemies!

## Multiplayer

### Team Features
- **Shared Progression** - All players share XP, level, and weapon
- **Scaled Difficulty** - Enemy HP: +50% per player, Spawns: +30% per player
- **Individual GUIs** - Each player gets their own power-up selection
- **Synchronized Levels** - Team levels up together
- **Co-op Weapon** - Team chooses one weapon together at start
- **Shared Stats** - Speed and health buffs apply to entire team

### Joining Mid-Run
- Players can join active runs
- Inherit team's current level, XP, and weapon
- Immediately get buffs applied
- Health and XP displays start instantly

## PlaceholderAPI Integration

If PlaceholderAPI is installed, you can use these placeholders:

- `%roguecraft_level%` - Current level
- `%roguecraft_wave%` - Current wave
- `%roguecraft_experience%` - Current experience
- `%roguecraft_experience_next%` - Experience needed for next level
- `%roguecraft_time%` - Time survived (seconds)
- `%roguecraft_difficulty%` - Current difficulty multiplier
- `%roguecraft_powerups%` - Number of collected power-ups
- `%roguecraft_rerolls%` - Rerolls remaining
- `%roguecraft_health%` - Current health stat
- `%roguecraft_damage%` - Current damage stat
- `%roguecraft_speed%` - Current speed stat
- `%roguecraft_luck%` - Current luck stat
- `%roguecraft_xp_multiplier%` - Current XP multiplier
- `%roguecraft_difficulty_stat%` - Current difficulty stat (player-selected)

## Story Premise

**The world is glitching.** Chunks "de-render" into a monochrome **Arena Realm** where mobs spawn from corrupted rifts. Players slip into the Realm during nightly rifts, fight escalating swarms with auto-attacking weapons, and return at dawn—if they survive.

**Goal:** Close rifts by surviving a timed "night" (e.g., 15 minutes) or defeating the Realm Boss. Each successful night strengthens the next rift.

**Tone:** Arcadey, tongue-in-cheek: over-the-top power spikes ("bonk builds"), ridiculous synergies, and luck-based progression that rewards risk-taking.

## Building from Source

Requirements:
- Maven 3.6+
- Java 21+

```bash
# Clone the repository
git clone https://github.com/yourusername/roguecraft.git
cd roguecraft

# Build with Maven
mvn clean package

# Find the jar in target/roguecraft-0.1.0.jar
```

## Server Stack

Built directly on **Spigot API 1.21.10** to ensure maximum Bukkit compatibility. Uses native Minecraft attributes for health and speed. Optional integrations (ProtocolLib, PlaceholderAPI, WorldGuard, Vault) enhance visuals, stats, and economy systems but are not required. Fully compatible with Bukkit, Spigot, Paper, and Purpur.

## WorldGuard Setup

**Note:** WorldGuard is **optional**. The plugin works perfectly fine without it. WorldGuard integration only activates if WorldGuard is installed.

If you're using **WorldGuard** to protect your arena region, follow these steps to ensure Roguecraft works properly:

### 1. Create Your Arena Region

```
/rg define <region-name>
/rg addmember <region-name> <players>
```

### 2. Configure WorldGuard Flags

**Important flags to set:**

```bash
# Allow plugin mobs to spawn (Roguecraft handles this automatically)
/rg flag <region-name> mob-spawning deny

# Allow players to interact (for XP and items)
/rg flag <region-name> block-break deny
/rg flag <region-name> block-place deny

# Allow PvP if needed
/rg flag <region-name> pvp deny

# Allow item pickup (for XP tokens and hearts)
/rg flag <region-name> item-pickup allow

# Allow experience (vanilla XP - Roguecraft uses plugin XP, so this is optional)
/rg flag <region-name> exp-drop allow

# Disable explosion grief (prevents creeper and TNT from destroying blocks)
/rg flag <region-name> creeper-explosion deny
/rg flag <region-name> tnt deny
/rg flag <region-name> block-break deny  # Prevents all block breaking (including explosions)

# Prevent mobs from burning in sunlight (undead mobs like zombies/skeletons)
# NOTE: The plugin automatically places barrier blocks above the arena at world height
# to prevent sunlight damage. This is enabled by default (config: game.sunlight-protection: true).
# To disable this feature, set game.sunlight-protection: false in config.yml

# If you disable automatic protection, you can manually prevent sunlight damage:
# Option 1: Lock time to night
/gamerule doDaylightCycle false
/time set night

# Option 2: Build arena underground or with roof to block sunlight
```

**Note:** The plugin automatically bypasses `mob-spawning` restrictions for Roguecraft-spawned mobs. The `mob-spawning=deny` flag will prevent passive mobs from spawning naturally, but Roguecraft's game mobs will still spawn correctly.

Roguecraft-spawned mobs will still spawn because they're marked with special metadata and bypass WorldGuard restrictions.

### 3. XP System

Roguecraft uses **plugin XP** (not vanilla Minecraft experience), so WorldGuard's `exp-drop` flag does **not** affect it. Players will gain XP from kills regardless of WorldGuard settings.

### Troubleshooting WorldGuard Issues

**Problem:** Mobs aren't spawning
- **Solution:** Make sure WorldGuard is installed and the plugin detected it on startup. Check server logs for "WorldGuard detected - enabling region support."

**Problem:** Mobs burning in sunlight
- **Solution:** The plugin automatically places invisible barrier blocks above the arena at world height to prevent sunlight damage (enabled by default). To disable this, set `game.sunlight-protection: false` in `config.yml`. If disabled, use `/gamerule doDaylightCycle false` and `/time set night`, or build your arena underground/with a roof.

## Troubleshooting

### Arena not working
- Make sure you've configured an arena in `config.yml`
- Check that spawn and center locations are valid
- Verify the arena radius is appropriate
- Format: `world,x,y,z,yaw,pitch` for spawn, `world,x,y,z` for center

### World border not appearing
- Check `visualization.enabled: true` in config
- Ensure arena has valid center coordinates
- Verify radius is reasonable (10-100 blocks)

### Weapons not attacking
- Ensure mobs are spawning within weapon range
- Check weapon stats in GUI (damage, range, attack speed)
- Weapon auto-attacks nearest enemy - stay close to mobs
- Weapons pause during GUI screens (intentional)

### Health not displaying
- Action bar health appears automatically during runs
- Check if other plugins are using action bar
- Health resets to 20 after run ends (normal behavior)

### XP not showing
- Boss bar appears at top of screen during runs
- Check if other plugins are using boss bars
- XP is shared among team members in co-op

### Performance issues
- Reduce spawn rates in `configs/spawns.yml`
- Lower wave intervals in `config.yml`
- Reduce arena radius if too large
- Limit player count per arena

## Wave System

### Wave Progression
- **Automatic progression** - Waves advance every 30 seconds
- **Wave notifications** - Players notified when new wave begins
- **Difficulty scaling** - Each wave increases enemy HP, damage, and spawn rates
- **Mob speed scaling** - Enemies move faster each wave (+2% per wave, capped at +100%)
- **Variety scaling** - New mob types appear at specific waves:
  - Wave 3+: Spiders
  - Wave 4+: Husk, Stray
  - Wave 5+: Creepers
  - Wave 6+: Cave Spider, Slime
  - Wave 8+: Endermen, Drowned
  - Wave 12+: Witches, Pillager, Phantom
  - Wave 15+: Wither Skeletons, Vindicator, Silverfish
  - Wave 20+: Ravager

### Infinite Wave Mode
After reaching the maximum wave (default: 20), **Infinite Mode** activates:
- **Notification**: "☠ INFINITE MODE ACTIVATED! ☠"
- **Wither Boss**: A single Wither boss spawns on wave 20 (last regular wave) with health scaling based on level and difficulty, red elite outline, 2.5x size, high resistance, and aggro on players
- **Mob types**: Randomly selects 2-3 mob types from: Ravager, Blaze, Vindicator, Skeleton, Zombie, Wither Skeleton, Enderman
- **All elites**: All infinite wave mobs are guaranteed to be elite
- **Progressive difficulty**: Each wave gets harder (HP, damage, spawns)
- **Progressive spawns**: Wave 21 = 40 mobs (base), increases by 8 per wave
- **Elite damage scaling**: Elite damage multiplier increases continuously (1.75x base + 0.1x per infinite wave, uncapped)
- **Elite damage resistance**: Elite mobs take reduced damage:
  - Wave 10-15: 10% resistance
  - Wave 16-20: 20% resistance
  - Wave 21: 30% resistance
  - Infinite waves: 30% + 3% per infinite wave (60% at wave 30, caps at 98%)
- **Wave notifications**: Every 5 waves players are notified
- **No limit**: Waves continue indefinitely until you die

## Physical Shrine System

### How Shrines Work
1. **Spawn**: 2-3 shrines spawn randomly around the arena when run starts
2. **Channeling**: Stand within 3 blocks of a shrine for 4 seconds
   - Progress shown on action bar: "Channeling... 1/4"
   - You can move and attack during channeling (enemies still move)
   - Moving too far cancels channeling
3. **Selection**: GUI opens with 3 buff variants to choose from
   - Mobs and projectiles freeze during GUI selection
4. **Activation**: Buff applies immediately after selection
5. **One-time use**: Each shrine can only be used once per run
6. **Visual feedback**: Used shrines dim (light changes to redstone lamp)

### Shrine Types & Buffs

**8 Shrine Types:**
1. **Shrine of Power** - Damage multipliers (4x for 8s, 2.5x for 15s, 3x + AOE)
2. **Shrine of Swiftness** - Speed boosts (3x for 10s, 2x for 20s, Blink teleport)
3. **Shrine of Vitality** - Healing (Full heal + regen, Overheal, Regen over time)
4. **Shrine of Fortune (Treasure Hunter)** - XP multipliers:
   - 5x XP for 25s (temporary)
   - 3x XP + luck boost (temporary)
   - 4x XP permanent + rare power-up on next kill (grants a rare stat boost power-up directly to your run)
5. **Shrine of Fury** - Critical hits (100% crit, 75% + attack speed, 80% + precision)
6. **Shrine of Protection** - Defense (Invulnerability, 75% reduction, Shield bubble)
7. **Shrine of Chaos** - Random effects (Wild magic, Chaos storm, Gambler's dream)
8. **Shrine of Time** - Enemy control (Time stop, Slow time, Haste)

**Shrine Buffs:**
- Applied via player metadata and attributes
- Temporary effects (5-30 seconds depending on variant)
- Stacks with other buffs (e.g., shrine damage + power-up damage)
- Team members can use different shrines independently

## Version History

### Latest Updates (Today)
- **Legendary Mob Tier** - New rare mob tier (0.4x bigger than elite, 2.1x total size) with golden glow effect using scoreboard teams, increased resistance, damage, and XP rewards
- **Armor System Overhaul** - Renamed "defense" to "armor" and made it visible in HUD using Minecraft's GENERIC_ARMOR attribute (shows as armor icons like hearts)
- **Power-Up GUI Stats** - Stats panel now displays lifesteal percentage and active auras list
- **Drop Rate Stat Nerf** - Reduced drop_rate stat values from 0.1 + level*0.05 to 0.02 + level*0.005 to prevent excessive values
- **Power-Up Drop Rates** - Scaled back drop rates: XP tokens 5% base (most common), hearts 0.5% base (rare), power-ups 0.25% base (very rare, reduced from 1%)
- **Healing Caps** - Lifesteal and regeneration both capped at 2 hearts (4 HP) per second (fixed value, not percentage-based) to prevent invincibility
- **Capped Stat Exclusion** - Regeneration and Vampire Aura power-ups won't appear in selections if you're already at the healing cap (4 HP/second)
- **Elemental Fusion Nerf** - Reduced scaling from 1.3x max to 1.15x max, and from 0.05x per value to 0.02x per value
- **Weapon Balance** - Potion Thrower upgrades scaled back (reduced attack speed, AOE, and damage scaling), TNT and Fireball buffed (increased attack speed, AOE, and damage scaling)

### Previous Updates
- **Wave Display on Boss Bar** - XP bar now shows current wave number: "Wave X | Level Y | XP: Z / W"
- **Elite Size Adjustment** - Elite mobs are now 1.5x size (reduced from 2x for better gameplay balance)
- **Elite Damage Resistance** - Progressive damage resistance scaling:
  - Wave 10-15: 10% resistance
  - Wave 16-20: 20% resistance
  - Wave 21: 30% resistance
  - Infinite waves: 30% + 3% per infinite wave (60% at wave 30, caps at 98%)
- **Elite Damage Scaling** - Elite damage scales continuously in infinite waves (1.5x base + 0.1x per infinite wave, uncapped)
- **Wither Boss** - Single Wither boss spawns on wave 20 (last regular wave) with health scaling based on level and difficulty, red elite outline, 2.5x size, high resistance, and aggro on players
- **Infinite Wave Overhaul** - Increased difficulty and variety:
  - Added Ravager, Blaze, Vindicator, Skeleton, and Zombie to infinite waves
  - All infinite wave mobs are guaranteed elite
  - Increased spawn counts: Wave 21 = 40 mobs (base), increases by 8 per wave
- **Power-Up Drop Rate** - Fixed 0.25% base chance (reduced from 1%), no elite bonus, only scales with `drop_rate` stat
- **XP Configuration** - XP values now read from `balance.yml` instead of hardcoded values
- **Treasure Hunter Shrine** - "xp_4x_rare" variant grants permanent 4x XP and a rare power-up on next kill
- **Time Freeze Fix** - Time freeze power-up now properly tracks duration and freezes newly spawned mobs
- **Enhanced Mob Diversity** - Added 10+ new mob types (Husk, Stray, Cave Spider, Drowned, Pillager, Vindicator, Phantom, Slime, Silverfish, Ravager)
- **Visual Mob Health** - Mobs display health bars in name tags with color coding
- **Improved GUI Safety** - Mobs freeze, projectiles removed, and damage canceled immediately when GUI opens
- **Fixed Potion Thrower** - Potions no longer affect the player, only enemies
- **Fixed Ice Shard** - Damage now applies correctly on impact with AOE falloff
- **Inventory Auto-Close** - Player inventory automatically closes when power-up GUI opens
- **Increased Damage/Crit Spawn** - Damage and crit chance power-ups appear more frequently
- **Fixed Death Logging** - Named entities no longer spam server logs on death
- **All Power-Ups Implemented** - All AURA, SYNERGY, and WEAPON_MOD effects are now fully functional
- **Synergy Improvements** - Lucky Streak now requires 20 kills base (scales down with value, minimum 5)
- **XP Scaling** - XP rewards now scale with difficulty stat

### v0.1.0 - Initial Release
- Dynamic power-up generation system with Luck scaling
- 7 unique auto-attack weapon types
- Boss bar XP display with color-coding
- Action bar health display
- Movement speed attribute application
- World border arena visualization
- Mob and projectile freezing during GUI
- Comprehensive cleanup system
- Team-based multiplayer with shared progression
- 9-stat system including Luck, XP Multiplier, and Difficulty
- Weapon upgrade power-up cards
- No duplicate power-ups in selections
- Attribute-based health and speed
- **Wave progression system** - Automatic wave advancement every 30 seconds
- **Dynamic spawn generation** - Missing waves auto-scale from config
- **Infinite wave mode** - Endless challenge after max wave with progressive difficulty
- **Physical shrine system** - Channel shrines for powerful temporary buffs
- **Elite mob scaling** - Physical size scaling (1.5x), visual indicators (glowing, names), 2.5x HP, 1.5x damage
- **Difficulty stat** - Players can increase difficulty for harder enemies
- **GUI queue system** - Multiple GUIs queue properly (level-up, shrine)
- **Improved spawn locations** - Mobs spawn within pathfinding range
- **Mob speed scaling** - Enemies move faster each wave
- **Better mob targeting** - Mobs auto-target nearest player on spawn
- **Shrine cleanup** - Comprehensive cleanup prevents dark spots and orphaned tasks

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Credits

- Built for Spigot/Paper Minecraft servers
- Inspired by Megabonk (3D auto-attack horde survival)
- Following established plugin patterns from ChestLockLite, GrokChat, and QuestLogs

## Support

- **Issues**: [GitHub Issues](https://github.com/yourusername/roguecraft/issues)
- **Wiki**: Check the `wiki/` folder for detailed documentation

---

*Not affiliated with Mojang or Microsoft*
