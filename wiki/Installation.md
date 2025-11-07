# Installation Guide

This guide will help you install and set up Roguecraft on your Minecraft server.

## Requirements

- **Minecraft:** 1.21.10+
- **Java:** 21 (LTS)
- **Server:** Spigot, Paper, Purpur, or any Bukkit-based server

### Optional Soft Dependencies

- **ProtocolLib** - Custom visual effects
- **PlaceholderAPI** - Live stats display
- **WorldGuard** - Arena region protection
- **Vault** - Economy integration

## Installation Steps

### 1. Download

Download the latest `roguecraft.jar` from the releases page.

### 2. Install

Place the `roguecraft.jar` file in your server's `plugins` folder.

### 3. Start Server

Start your server to generate configuration files. The plugin will create:
- `plugins/Roguecraft/config.yml`
- `plugins/Roguecraft/configs/balance.yml`
- `plugins/Roguecraft/configs/cards.yml`
- `plugins/Roguecraft/configs/spawns.yml`

### 4. Stop Server

Stop your server to configure the plugin.

### 5. Configure Arena

Edit `plugins/Roguecraft/config.yml` and set up at least one arena:

```yaml
arenas:
  default:
    name: "Default Arena"
    default: true
    spawn: world,0,100,0,0,0
    center: world,0,100,0
    radius: 50.0
```

**Important:** Replace `world,0,100,0,0,0` with actual coordinates in your world.

### 6. Start Server

Start your server again. The plugin should load successfully.

### 7. Test

In-game, run `/rc start` to test if everything works.

## Post-Installation

### Optional: Install Soft Dependencies

If you want to use optional features:

1. **PlaceholderAPI** - Download and install PlaceholderAPI
2. **ProtocolLib** - Download and install ProtocolLib for visual effects
3. **WorldGuard** - Install WorldGuard for region-based arena protection
4. **Vault** - Install Vault for economy integration

The plugin will automatically detect and enable these features if present.

### Optional: Configure Power-Ups

Edit `plugins/Roguecraft/configs/cards.yml` to customize or add power-ups.

### Optional: Configure Spawns

Edit `plugins/Roguecraft/configs/spawns.yml` to customize mob spawns per wave.

## Verification

To verify the installation:

1. Check server console for: `[Roguecraft] Roguecraft has been enabled!`
2. In-game, run `/rc` - you should see a message or start a run
3. Check that configuration files were created

## Troubleshooting

### Plugin won't load

- Check that you're using Java 21+
- Verify you're using Spigot 1.21.10+ or compatible
- Check server logs for error messages

### Configuration errors

- Make sure YAML files are properly formatted
- Check for syntax errors (spaces, indentation)
- Verify all required sections exist

### Arena not working

- Make sure arena coordinates are valid
- Check that the world exists
- Verify arena radius is reasonable

See [Troubleshooting](Troubleshooting.md) for more help.



