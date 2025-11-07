# Quick Start Guide

Get started with Roguecraft in 5 minutes!

## Step 1: Set Up an Arena

1. Choose a location in your world for the arena
2. Note the coordinates (X, Y, Z)
3. Edit `plugins/Roguecraft/config.yml`:

```yaml
arenas:
  default:
    name: "Default Arena"
    default: true
    spawn: world,100,64,200,0,0
    center: world,100,64,200
    radius: 50.0

visualization:
  enabled: true
  method: "WORLD_BORDER"
```

Replace `world,100,64,200,0,0` with your coordinates:
- Format: `worldname,x,y,z,yaw,pitch`
- `spawn` is where players teleport when entering
- `center` is the center of the arena
- `radius` is the arena size in blocks (world border will visualize this!)

## Step 2: Start Your First Run

1. Join your server
2. Run `/rc start`
3. You'll be teleported to the arena
4. **Choose your weapon** from the GUI - 7 unique types available!
5. A **world border appears** showing your arena boundaries
6. Your weapon **auto-attacks** enemies - no aiming required!

## Step 3: Understand the HUD

You'll see several displays during your run:

### XP Boss Bar (Top of Screen)
- Shows: `Level X | XP: current/required`
- Color changes based on progress: Red → Yellow → Green
- Flashes gold when you level up!

### Health Action Bar (Above Hotbar)
- Shows: `❤ 25.0/40.0` (current/max)
- Updates in real-time
- No more guessing your health!

### Your Weapon
- Auto-targets nearest enemy
- Each weapon has unique stats:
  - Damage, Range, Attack Speed
  - Projectile Count, AOE
- Check stats in power-up GUI

## Step 4: Level Up & Choose Power-Ups

When you level up:

1. **Everything freezes** - Mobs and projectiles pause
2. **GUI appears** with 3 unique power-ups
3. **View your stats** - Book icon shows all current stats
4. **Choose wisely** - Each affects your build differently

### Power-Up Types

**Stat Boosts (50% chance)**
- Health ❤️ - Increases max health (applies immediately!)
- Damage ⚔️ - Weapon damage multiplier
- Speed ☄️ - Movement speed (applies immediately!)
- Defense ♦️ - Damage reduction
- Crit Chance ◈ - Chance for critical hits
- Crit Damage ★ - Critical hit multiplier
- **Luck ☘️ - Increases rarity & bonus values!**
- **XP Multiplier 🧪 - Gain more XP from kills!**
- **Difficulty ☠️ - Makes enemies harder (15% chance to appear) - Risk/Reward!**

**Weapon Upgrades (25% chance)**
- Upgrades your weapon by 1-5 levels
- Increases ALL weapon stats
- Higher rarity = more levels

**Weapon Mods (25% chance)**
- Special modifiers (future implementation)
- Piercing, Explosive, Chain Lightning, etc.

### Rarity Colors
- White = **Common** (1× multiplier)
- Blue = **Rare** (1.5× multiplier)
- Purple = **Epic** (2.25× multiplier)
- Gold = **Legendary** (3.5× multiplier)

**Important:** Higher **Luck** = better rarities! Stack Luck for legendary runs!

## Step 5: Find and Use Shrines

During your run, **2-3 shrines** spawn randomly around the arena:

1. **Look for glowing structures** - Shrines are visible structures with light sources
2. **Get close** - Stand within 3 blocks
3. **Channel** - Wait 4 seconds (you can attack during this!)
   - Progress shown: "Channeling... 2/4"
4. **Choose buff** - GUI opens with 3 powerful variants
5. **Activate** - Buff applies immediately
6. **One-time use** - Each shrine can only be used once

**Shrine Types:**
- **Power** - Massive damage boosts
- **Swiftness** - Speed and teleportation
- **Vitality** - Healing and regeneration
- **Fortune** - XP multipliers
- **Fury** - Critical hit bonuses
- **Protection** - Damage reduction and invulnerability
- **Chaos** - Random powerful effects
- **Time** - Slow or freeze enemies

**Pro Tip:** Use shrines strategically - they're one-time use, so save them for tough moments!

## Step 6: Master the Weapons

### 7 Unique Weapon Types

Each has different playstyles:

1. **Fireball Launcher** 🔥 - Medium damage, AOE explosions
2. **Arrow Storm** 🏹 - Fast attacks, long range, precision
3. **Lightning Strike** ⚡ - High damage, slow, huge AOE
4. **TNT Spawner** 💣 - Massive damage, very slow, area denial
5. **Potion Thrower** 🧪 - Poison & weakness, good AOE
6. **Ice Shard** ❄️ - Fast attacks, slows enemies
7. **Magic Missile** ✨ - Homing projectiles, balanced

**Pro Tip:** Choose based on your playstyle:
- Aggressive? Arrow Storm or Magic Missile
- Tank? TNT Spawner or Lightning Strike
- Support? Potion Thrower or Ice Shard

## Step 7: Understand Waves & Difficulty

### Wave System
- **Waves advance automatically** every 30 seconds
- **Each wave** increases enemy HP, damage, and spawn rates
- **New mob types** appear at specific waves:
  - Wave 3+: Spiders
  - Wave 5+: Creepers
  - Wave 8+: Endermen
  - Wave 12+: Witches
  - Wave 15+: Wither Skeletons
- **Elite mobs** appear more frequently at wave 10+
  - Elite mobs: Glowing, larger, named "⚡ ELITE", 2x HP, 1.5x damage

### Infinite Wave Mode
After **Wave 20** (configurable):
- **Only difficult mobs** spawn (Wither Skeletons, Endermen)
- **All mobs are elite** - Every single one!
- **Progressive difficulty** - Gets harder each wave
- **No limit** - Survive as long as possible!

### Difficulty Stat
- **Increase difficulty** via power-ups for harder enemies
- **Risk/Reward** - Enemies are tougher, but you get better challenges
- **Multiplies** all enemy stats (HP, damage, spawns)
- **Viewable** in Power-Up GUI stats display

## Step 8: Build Your Strategy

### Early Game (Levels 1-5)
- **Focus on Health** - Survivability is key
- **Get Speed** - Dodging is easier
- **Upgrade Weapon** - Better DPS early helps
- **Don't ignore Luck** - Pay dividends later!

### Mid Game (Levels 6-12)
- **Balance Stats** - Round out weaknesses
- **Stack Luck** - Start seeing Epics/Legendaries
- **Get XP Multiplier** - Level faster
- **Weapon Upgrades** - Keep weapon competitive

### Late Game (Levels 13+)
- **Stack Luck** - Fish for Legendaries
- **Maximize DPS** - Crit builds shine
- **More Health** - Enemies hit harder
- **Weapon Upgrades** - Keep scaling

## Step 9: Survive!

- **Watch your health** - Action bar shows real-time HP
- **Monitor XP progress** - Boss bar shows next level
- **Use terrain** - Arena border shows boundaries
- **Keep moving** - Speed stat helps dodge
- **Fight strategically** - Let weapon auto-target

## Multiplayer Tips

### Playing with Friends
- **Team shares everything** - XP, levels, weapon
- **Difficulty scales** - More players = harder enemies
- **Everyone chooses** - Individual power-up GUIs
- **Coordinate builds** - Discuss strategy!

### Team Strategies
- One player focuses **Tank** (Health/Defense)
- One player focuses **DPS** (Damage/Crit)
- One player focuses **Support** (XP Multi/Luck)
- All benefit from shared bonuses!

## Commands

### Player Commands
- `/rc` or `/rc start` - Start a run
- `/rc stop` - End your current run
- `/rc stats` - View your run statistics

### Admin Commands
- `/rc reload` - Reload configuration
- More in [Commands & Permissions](Commands-Permissions.md)

## Tips & Tricks

### The Luck Meta
- **Luck is king** - It affects BOTH rarity AND values
- **Stack early** - Better cards throughout run
- **Multiplies with itself** - 2.5 Luck is game-changing
- **Legendaries everywhere** - High Luck = consistent Legendaries

### Difficulty Strategy
- **Risk/Reward** - Increase difficulty for harder challenge
- **Scales enemy stats** - HP, damage, spawn rates all increase
- **Visible in GUI** - See your difficulty multiplier in stats
- **15% chance** - Less common than other stats, choose wisely
- **Late game** - Consider stacking difficulty for maximum challenge

### Weapon Scaling
- Weapons scale exponentially with upgrades
- **Don't ignore weapon upgrades** - They're very strong
- Legendary weapon upgrade = +5 levels instantly!
- Balance weapon upgrades with stat boosts

### XP Multiplier Strategy
- **Get one early** - Speeds up entire run
- **Stacks additively** - 1.1 + 0.2 = 1.3× total
- **Pairs with Luck** - More levels = more Luck choices
- **Late game MVP** - Levels fly by

### Movement Speed
- **Applies immediately** - Feel the difference
- **Helps dodge** - Easier to avoid attacks
- **Kiting builds** - Stay ahead of mobs
- **Synergizes with range** - Fast + long range = safe

## Troubleshooting

**Q: My weapon isn't attacking!**
A: Weapons auto-attack nearest enemy. Make sure mobs are within range!

**Q: I can't see my health!**
A: Look above your hotbar - action bar shows `❤ 25.0/40.0`

**Q: XP bar disappeared!**
A: It only shows during active runs. Start a new run to see it again.

**Q: World border is gone!**
A: Restored after run ends. It's intentional to show arena boundaries.

**Q: Mobs aren't moving!**
A: During power-up selection, everything freezes. Normal behavior!

**Q: Health didn't increase after buff!**
A: Check your heart containers - stat applies via attributes, not base health.

## Next Steps

- Read [Configuration](Configuration.md) to customize your experience
- Check [Power-Ups](Power-Ups.md) to understand the system deeply
- See [Multiplayer](Multiplayer.md) for team strategies
- Explore [Weapons Guide](Weapons.md) for detailed weapon mechanics
- Review [Commands & Permissions](Commands-Permissions.md) for admin features

## Common Questions

**Q: How do I stop a run?**
A: Run `/rc stop` or die. Everything resets cleanly.

**Q: Can I pause a run?**
A: No, but power-up GUI pauses mobs temporarily.

**Q: Do I lose progress when I stop?**
A: Yes, each run is independent. Progress resets.

**Q: Can I play with friends?**
A: Yes! Multiple players share one run with scaled difficulty.

**Q: How do I get better power-ups?**
A: **Increase your Luck stat!** It's the key to better loot.

**Q: What's the best weapon?**
A: Depends on playstyle. Try them all!

**Q: Should I upgrade my weapon or stats?**
A: Both! Balance is key, but weapon upgrades are very strong.

**Q: How does Luck actually work?**
A: Increases rarity chance AND bonus values. Stack it for OP runs!

**Q: Can I change weapons mid-run?**
A: No, weapon chosen at start. Choose wisely!

**Q: Why did my speed/health reset?**
A: After run ends, all stats reset to default. Fresh start!

**Q: How do waves work?**
A: Waves advance every 30 seconds automatically. After wave 20, infinite mode starts with only difficult mobs.

**Q: What are shrines?**
A: Physical structures in the arena. Channel for 4 seconds to get powerful temporary buffs. Each shrine used once.

**Q: Should I increase difficulty?**
A: It's a risk/reward choice! Enemies get harder but you challenge yourself. Great for experienced players.

**Q: Why are some mobs glowing?**
A: Those are elite mobs! They have 2x HP, 1.5x damage, and are larger. They appear more at higher waves.

**Q: Can I use a shrine twice?**
A: No, each shrine can only be used once per run. Look for other shrines in the arena!

## Ready to Play?

Run `/rc start` and begin your roguelike adventure! 🎮

Good luck, and may the RNG be in your favor! ☘️
