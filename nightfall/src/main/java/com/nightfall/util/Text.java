package com.nightfall.util;

import org.bukkit.ChatColor;

/**
 * Tiny color/format helper. Keeps the legacy &-code translation in one
 * place so we don't sprinkle ChatColor.translateAlternateColorCodes calls
 * across the plugin.
 */
public final class Text {

    private Text() {}

    public static String color(String s) {
        if (s == null || s.isEmpty()) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
