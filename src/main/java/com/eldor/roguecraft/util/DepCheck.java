package com.eldor.roguecraft.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

public final class DepCheck {
    private DepCheck() {}

    public static boolean has(String pluginName) {
        PluginManager pm = Bukkit.getPluginManager();
        Plugin p = pm.getPlugin(pluginName);
        return p != null && p.isEnabled();
    }
}



