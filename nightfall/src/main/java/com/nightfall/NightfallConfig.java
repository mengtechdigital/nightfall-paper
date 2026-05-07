package com.nightfall;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Snapshot of config.yml. Re-read on /nightfall reload. All consumers
 * hold a reference and pick up new values on the next tick/event.
 *
 * Defensive note: every getter returns a sane fallback so a malformed
 * config never NPEs the plugin. Out-of-range numbers are clamped where
 * they could otherwise break the time controller (e.g. zero minutes).
 */
public final class NightfallConfig {

    private List<String> worlds;

    private double dayMinutes;
    private double nightMinutes;

    private boolean preventNightSkip;
    private String sleepSkipBlockedMessage;

    private double runnerChance;
    private int runnerSpeedAmp;
    private String runnerName;

    private boolean forceZombieDoorBreak;

    private boolean mobBuffEnabled;
    private double mobHealthMultiplier;
    private double mobAttackMultiplier;

    private boolean dummyEnabled;
    private String dummyName;
    private boolean dummyDropInventory;
    private boolean dummyClearXp;

    private boolean extraSpawnEnabled;
    private long extraIntervalSeconds;
    private int extraPerPlayerPerCycle;
    private int extraMaxPerWorld;
    private int extraMinDistance;
    private int extraMaxDistance;
    private boolean extraRequireDarkness;
    private LinkedHashMap<EntityType, Integer> extraMobWeights;

    private FileConfiguration root;

    public void load(Plugin plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        this.root = c;

        List<String> rawWorlds = c.getStringList("worlds");
        this.worlds = rawWorlds.isEmpty()
                ? List.of("world")
                : Collections.unmodifiableList(new ArrayList<>(rawWorlds));

        // Clamp to a sane minimum so we never divide by ~zero.
        this.dayMinutes   = Math.max(0.5, c.getDouble("day-length-minutes",   30.0));
        this.nightMinutes = Math.max(0.5, c.getDouble("night-length-minutes",  3.0));

        this.preventNightSkip = c.getBoolean("prevent-night-skip", true);
        this.sleepSkipBlockedMessage = c.getString("sleep-skip-blocked-message",
                "&8&oThe night refuses to end.");

        this.runnerChance    = clamp(c.getDouble("night.runner-chance", 0.30), 0.0, 1.0);
        this.runnerSpeedAmp  = Math.max(0, c.getInt("night.runner-speed-amplifier", 1));
        this.runnerName      = c.getString("night.runner-name", "&cRunner");

        this.forceZombieDoorBreak = c.getBoolean("night.force-zombie-door-break", true);

        this.mobBuffEnabled       = c.getBoolean("night.mob-buff.enabled", true);
        this.mobHealthMultiplier  = Math.max(0.1, c.getDouble("night.mob-buff.max-health-multiplier", 1.5));
        this.mobAttackMultiplier  = Math.max(0.1, c.getDouble("night.mob-buff.attack-damage-multiplier", 1.4));

        this.dummyEnabled        = c.getBoolean("night.logout-dummy.enabled", true);
        this.dummyName           = c.getString("night.logout-dummy.name", "&7&o{player} (sleeping)");
        this.dummyDropInventory  = c.getBoolean("night.logout-dummy.drop-inventory-on-kill", true);
        this.dummyClearXp        = c.getBoolean("night.logout-dummy.clear-xp-on-kill", true);

        ConfigurationSection extra = c.getConfigurationSection("night.extra-spawn");
        this.extraSpawnEnabled       = extra != null && extra.getBoolean("enabled", true);
        this.extraIntervalSeconds    = Math.max(5, extra != null ? extra.getLong("interval-seconds", 25) : 25);
        this.extraPerPlayerPerCycle  = Math.max(0, extra != null ? extra.getInt("per-player-per-cycle", 2) : 2);
        this.extraMaxPerWorld        = Math.max(0, extra != null ? extra.getInt("max-per-world", 80) : 80);
        this.extraMinDistance        = Math.max(8, extra != null ? extra.getInt("min-distance", 24) : 24);
        this.extraMaxDistance        = Math.max(this.extraMinDistance + 4,
                                                extra != null ? extra.getInt("max-distance", 40) : 40);
        this.extraRequireDarkness    = extra != null && extra.getBoolean("require-darkness", true);

        this.extraMobWeights = new LinkedHashMap<>();
        ConfigurationSection weights = extra != null ? extra.getConfigurationSection("mob-weights") : null;
        if (weights != null) {
            for (String key : weights.getKeys(false)) {
                int weight = weights.getInt(key, 0);
                if (weight <= 0) continue;
                EntityType type = parseEntityType(key);
                if (type == null) {
                    plugin.getLogger().warning("Unknown EntityType in night.extra-spawn.mob-weights: " + key);
                    continue;
                }
                this.extraMobWeights.put(type, weight);
            }
        }
        if (this.extraMobWeights.isEmpty()) {
            // Sensible default so a misconfigured weights map doesn't silently
            // disable extra spawns. Mirrors the bundled config defaults.
            this.extraMobWeights.put(EntityType.ZOMBIE, 5);
            this.extraMobWeights.put(EntityType.SKELETON, 3);
            this.extraMobWeights.put(EntityType.SPIDER, 2);
            this.extraMobWeights.put(EntityType.CREEPER, 1);
        }
    }

    private static EntityType parseEntityType(String name) {
        try {
            return EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public List<String> worlds()             { return worlds; }
    public boolean managesWorld(String name) { return worlds.contains(name); }

    public double dayMinutes()   { return dayMinutes; }
    public double nightMinutes() { return nightMinutes; }

    public boolean preventNightSkip()        { return preventNightSkip; }
    public String sleepSkipBlockedMessage()  { return sleepSkipBlockedMessage; }

    public double runnerChance()    { return runnerChance; }
    public int runnerSpeedAmp()     { return runnerSpeedAmp; }
    public String runnerName()      { return runnerName; }

    public boolean forceZombieDoorBreak() { return forceZombieDoorBreak; }

    public boolean mobBuffEnabled()        { return mobBuffEnabled; }
    public double mobHealthMultiplier()    { return mobHealthMultiplier; }
    public double mobAttackMultiplier()    { return mobAttackMultiplier; }

    public boolean dummyEnabled()       { return dummyEnabled; }
    public String dummyName()           { return dummyName; }
    public boolean dummyDropInventory() { return dummyDropInventory; }
    public boolean dummyClearXp()       { return dummyClearXp; }

    public boolean extraSpawnEnabled()                 { return extraSpawnEnabled; }
    public long extraIntervalSeconds()                 { return extraIntervalSeconds; }
    public int extraPerPlayerPerCycle()                { return extraPerPlayerPerCycle; }
    public int extraMaxPerWorld()                      { return extraMaxPerWorld; }
    public int extraMinDistance()                      { return extraMinDistance; }
    public int extraMaxDistance()                      { return extraMaxDistance; }
    public boolean extraRequireDarkness()              { return extraRequireDarkness; }
    public Map<EntityType, Integer> extraMobWeights()  { return extraMobWeights; }

    public String message(String key, String fallback) {
        return root.getString("messages." + key, fallback);
    }
}
