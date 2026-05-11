package com.nightfall;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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

    private double jumperChance;
    private int jumperJumpAmp;
    private String jumperName;

    private boolean forceZombieDoorBreak;

    private boolean mobBuffEnabled;
    private double mobHealthMultiplier;
    private double mobAttackMultiplier;

    private boolean chargedCreeperEnabled;
    private double chargedCreeperChance;

    private boolean stormBuffEnabled;
    private double stormHealthMultiplier;
    private double stormAttackMultiplier;

    private boolean distanceScalingEnabled;
    private boolean distanceScalingUseWorldSpawn;
    private double distanceScalingStart;
    private double distanceScalingMax;
    private double distanceScalingHealthMultiplier;
    private double distanceScalingAttackMultiplier;

    private boolean disableVanillaNightSpawns;

    private boolean mobGearEnabled;
    private double mobGearBaseChance;
    private double mobGearChancePer1000;
    private boolean mobGearAllowOverlevel;
    private double mobGearDropChance;

    private double lootXpMultiplier;
    private boolean extraLootEnabled;
    private double extraLootChance;
    private double extraLootStormBonus;
    private List<LootEntry> lootEntries;
    private int lootTotalWeight;

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

    private boolean mobVariantsEnabled;
    private boolean variantDistanceScalingEnabled;
    private double variantDistanceChanceMultiplier;
    private final Map<String, VariantEntry> variants = new HashMap<>();

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
        this.nightMinutes = Math.max(0.5, c.getDouble("night-length-minutes",  5.0));

        this.preventNightSkip = c.getBoolean("prevent-night-skip", true);
        this.sleepSkipBlockedMessage = c.getString("sleep-skip-blocked-message",
                "&8&oThe night refuses to end.");

        this.runnerChance    = clamp(c.getDouble("night.runner-chance", 0.35), 0.0, 1.0);
        this.runnerSpeedAmp  = Math.max(0, c.getInt("night.runner-speed-amplifier", 1));
        this.runnerName      = c.getString("night.runner-name", "&cRunner");

        this.jumperChance    = clamp(c.getDouble("night.jumper-chance", 0.25), 0.0, 1.0);
        this.jumperJumpAmp   = Math.max(0, c.getInt("night.jumper-jump-amplifier", 2));
        this.jumperName      = c.getString("night.jumper-name", "&2Jumper");

        this.forceZombieDoorBreak = c.getBoolean("night.force-zombie-door-break", true);

        this.mobBuffEnabled       = c.getBoolean("night.mob-buff.enabled", true);
        this.mobHealthMultiplier  = Math.max(0.1, c.getDouble("night.mob-buff.max-health-multiplier", 2.0));
        this.mobAttackMultiplier  = Math.max(0.1, c.getDouble("night.mob-buff.attack-damage-multiplier", 1.6));

        this.chargedCreeperEnabled = c.getBoolean("night.charged-creeper.enabled", true);
        this.chargedCreeperChance  = clamp(c.getDouble("night.charged-creeper.chance", 0.60), 0.0, 1.0);

        this.stormBuffEnabled      = c.getBoolean("night.storm-buff.enabled", true);
        this.stormHealthMultiplier = Math.max(0.1, c.getDouble("night.storm-buff.max-health-multiplier", 1.25));
        this.stormAttackMultiplier = Math.max(0.1, c.getDouble("night.storm-buff.attack-damage-multiplier", 1.15));

        ConfigurationSection dist = c.getConfigurationSection("night.distance-scaling");
        this.distanceScalingEnabled        = dist != null && dist.getBoolean("enabled", true);
        this.distanceScalingUseWorldSpawn   = dist != null && dist.getBoolean("use-world-spawn", true);
        this.distanceScalingStart           = Math.max(0, dist != null ? dist.getDouble("start-distance", 500.0) : 500.0);
        this.distanceScalingMax             = Math.max(this.distanceScalingStart + 1,
                                                        dist != null ? dist.getDouble("max-distance", 5000.0) : 5000.0);
        this.distanceScalingHealthMultiplier = Math.max(1.0, dist != null ? dist.getDouble("max-health-multiplier", 2.0) : 2.0);
        this.distanceScalingAttackMultiplier = Math.max(1.0, dist != null ? dist.getDouble("attack-damage-multiplier", 1.5) : 1.5);

        this.disableVanillaNightSpawns = c.getBoolean("night.disable-vanilla-night-spawns", true);

        ConfigurationSection gear = c.getConfigurationSection("night.mob-gear");
        this.mobGearEnabled         = gear != null && gear.getBoolean("enabled", true);
        this.mobGearBaseChance      = clamp(gear != null ? gear.getDouble("base-chance", 0.15) : 0.15, 0.0, 1.0);
        this.mobGearChancePer1000   = clamp(gear != null ? gear.getDouble("chance-per-1000-blocks", 0.08) : 0.08, 0.0, 1.0);
        this.mobGearAllowOverlevel  = gear != null && gear.getBoolean("allow-overlevel-enchants", true);
        this.mobGearDropChance      = clamp(gear != null ? gear.getDouble("drop-chance", 0.22) : 0.22, 0.0, 1.0);

        ConfigurationSection loot = c.getConfigurationSection("night.loot");
        this.lootXpMultiplier      = Math.max(1.0, loot != null ? loot.getDouble("xp-multiplier", 4.0) : 4.0);
        ConfigurationSection extraLoot = loot != null ? loot.getConfigurationSection("extra-loot") : null;
        this.extraLootEnabled       = extraLoot != null && extraLoot.getBoolean("enabled", true);
        this.extraLootChance        = clamp(extraLoot != null ? extraLoot.getDouble("base-chance", 0.45) : 0.45, 0.0, 1.0);
        this.extraLootStormBonus    = clamp(extraLoot != null ? extraLoot.getDouble("storm-bonus", 0.25) : 0.25, 0.0, 1.0);
        this.lootEntries = new ArrayList<>();
        this.lootTotalWeight = 0;
        ConfigurationSection drops = extraLoot != null ? extraLoot.getConfigurationSection("drops") : null;
        if (drops != null) {
            for (String key : drops.getKeys(false)) {
                Material mat = Material.matchMaterial(key.trim().toUpperCase(Locale.ROOT));
                if (mat == null || !mat.isItem()) {
                    plugin.getLogger().warning("Unknown or non-item Material in night.loot.extra-loot.drops: " + key);
                    continue;
                }
                ConfigurationSection itemSec = drops.getConfigurationSection(key);
                if (itemSec == null) continue;
                int weight = itemSec.getInt("weight", 0);
                if (weight <= 0) continue;
                int min = Math.max(1, itemSec.getInt("min", 1));
                int max = Math.max(min, itemSec.getInt("max", 1));
                this.lootEntries.add(new LootEntry(mat, weight, min, max));
                this.lootTotalWeight += weight;
            }
        }
        if (this.lootEntries.isEmpty()) {
            // Sensible fallback so misconfiguration doesn't silently disable drops.
            this.lootEntries.add(new LootEntry(Material.IRON_INGOT, 25, 2, 5));
            this.lootEntries.add(new LootEntry(Material.COPPER_INGOT, 20, 3, 6));
            this.lootEntries.add(new LootEntry(Material.ARROW, 20, 8, 16));
            this.lootEntries.add(new LootEntry(Material.REDSTONE, 18, 4, 8));
            this.lootEntries.add(new LootEntry(Material.GOLD_INGOT, 18, 2, 4));
            this.lootEntries.add(new LootEntry(Material.COAL, 15, 3, 6));
            this.lootEntries.add(new LootEntry(Material.LAPIS_LAZULI, 15, 3, 6));
            this.lootEntries.add(new LootEntry(Material.EXPERIENCE_BOTTLE, 12, 2, 4));
            this.lootEntries.add(new LootEntry(Material.EMERALD, 12, 1, 3));
            this.lootEntries.add(new LootEntry(Material.GLOWSTONE_DUST, 10, 3, 6));
            this.lootEntries.add(new LootEntry(Material.DIAMOND, 8, 1, 2));
            this.lootEntries.add(new LootEntry(Material.OBSIDIAN, 6, 1, 3));
            this.lootEntries.add(new LootEntry(Material.ENDER_PEARL, 6, 1, 2));
            this.lootEntries.add(new LootEntry(Material.BLAZE_ROD, 6, 1, 2));
            this.lootEntries.add(new LootEntry(Material.NAME_TAG, 3, 1, 1));
            this.lootEntries.add(new LootEntry(Material.TOTEM_OF_UNDYING, 1, 1, 1));
            this.lootTotalWeight = 195;
        }

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

        ConfigurationSection mv = c.getConfigurationSection("night.mob-variants");
        this.mobVariantsEnabled = mv != null && mv.getBoolean("enabled", true);
        this.variantDistanceScalingEnabled = mv != null && mv.getBoolean("distance-scaling-enabled", true);
        this.variantDistanceChanceMultiplier = Math.max(1.0, mv != null ? mv.getDouble("distance-chance-multiplier", 2.0) : 2.0);
        if (mv != null) {
            for (String key : mv.getKeys(false)) {
                if ("enabled".equals(key)) continue;
                ConfigurationSection vs = mv.getConfigurationSection(key);
                if (vs == null) continue;
                boolean venabled = vs.getBoolean("enabled", true);
                double vchance = clamp(vs.getDouble("chance", 0.15), 0.0, 1.0);
                String vname = vs.getString("name", "&e" + titleCase(key));
                variants.put(key, new VariantEntry(venabled, vchance, vname));
            }
        }
        ensureVariant("skeleton-marksman", 0.15, "&bSkeleton Marksman");
        ensureVariant("desert-zombie",     0.20, "&6Desert Zombie");
        ensureVariant("venomous-spider",   0.20, "&2Venomous Spider");
        ensureVariant("witch-doctor",      0.15, "&5Witch Doctor");
        ensureVariant("brute-creeper",     0.15, "&4Brute Creeper");
        ensureVariant("wither-reaper",     0.15, "&8Wither Reaper");
        ensureVariant("frozen-stray",      0.20, "&3Frozen Stray");
        ensureVariant("ender-stalker",     0.12, "&5Ender Stalker");
        ensureVariant("plague-zombie",     0.18, "&aPlague Zombie");
        ensureVariant("vindicator-berserker", 0.15, "&cVindicator Berserker");
        ensureVariant("phantom-diver",     0.20, "&9Phantom Diver");
        ensureVariant("pillager-sniper",   0.15, "&7Pillager Sniper");

        // Siege & advanced variants
        ensureVariant("siege-zombie",      0.12, "&4Siege Zombie");
        ensureVariant("flash-creeper",     0.10, "&cFlash Creeper");
        ensureVariant("splitter-creeper",  0.10, "&eSplitter Creeper");
        ensureVariant("volatile-creeper",  0.08, "&8Volatile Creeper");
        ensureVariant("web-weaver",        0.12, "&8Web Weaver");
        ensureVariant("pyro-skeleton",     0.10, "&6Pyro Skeleton");
        ensureVariant("bomber-skeleton",   0.10, "&cBomber Skeleton");
        ensureVariant("blaze-archer",      0.08, "&eBlaze Archer");
        ensureVariant("swarm-zombie",      0.10, "&2Swarm Zombie");
    }

    private void ensureVariant(String key, double defaultChance, String defaultName) {
        if (!variants.containsKey(key)) {
            variants.put(key, new VariantEntry(true, defaultChance, defaultName));
        }
    }

    private static String titleCase(String kebab) {
        StringBuilder sb = new StringBuilder();
        boolean upper = true;
        for (char c : kebab.toCharArray()) {
            if (c == '-') {
                sb.append(' ');
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
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

    public double jumperChance()    { return jumperChance; }
    public int jumperJumpAmp()      { return jumperJumpAmp; }
    public String jumperName()      { return jumperName; }

    public boolean forceZombieDoorBreak() { return forceZombieDoorBreak; }

    public boolean mobBuffEnabled()        { return mobBuffEnabled; }
    public double mobHealthMultiplier()    { return mobHealthMultiplier; }
    public double mobAttackMultiplier()    { return mobAttackMultiplier; }

    public boolean chargedCreeperEnabled() { return chargedCreeperEnabled; }
    public double chargedCreeperChance()   { return chargedCreeperChance; }

    public boolean stormBuffEnabled()        { return stormBuffEnabled; }
    public double stormHealthMultiplier()    { return stormHealthMultiplier; }
    public double stormAttackMultiplier()    { return stormAttackMultiplier; }

    public boolean distanceScalingEnabled()          { return distanceScalingEnabled; }
    public boolean distanceScalingUseWorldSpawn()    { return distanceScalingUseWorldSpawn; }
    public double distanceScalingStart()             { return distanceScalingStart; }
    public double distanceScalingMax()               { return distanceScalingMax; }
    public double distanceScalingHealthMultiplier()  { return distanceScalingHealthMultiplier; }
    public double distanceScalingAttackMultiplier()  { return distanceScalingAttackMultiplier; }

    public boolean disableVanillaNightSpawns() { return disableVanillaNightSpawns; }

    public boolean mobGearEnabled()          { return mobGearEnabled; }
    public double mobGearBaseChance()        { return mobGearBaseChance; }
    public double mobGearChancePer1000()     { return mobGearChancePer1000; }
    public boolean mobGearAllowOverlevel()   { return mobGearAllowOverlevel; }
    public double mobGearDropChance()        { return mobGearDropChance; }

    public double lootXpMultiplier()         { return lootXpMultiplier; }
    public boolean extraLootEnabled()        { return extraLootEnabled; }
    public double extraLootChance()          { return extraLootChance; }
    public double extraLootStormBonus()      { return extraLootStormBonus; }
    public List<LootEntry> lootEntries()     { return lootEntries; }
    public int lootTotalWeight()             { return lootTotalWeight; }

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

    public boolean mobVariantsEnabled() { return mobVariantsEnabled; }
    public boolean variantDistanceScalingEnabled() { return variantDistanceScalingEnabled; }
    public double variantDistanceChanceMultiplier() { return variantDistanceChanceMultiplier; }
    public VariantEntry variant(String key) { return variants.get(key); }

    public String message(String key, String fallback) {
        return root.getString("messages." + key, fallback);
    }

    public static final class LootEntry {
        public final Material material;
        public final int weight;
        public final int minAmount;
        public final int maxAmount;

        public LootEntry(Material material, int weight, int minAmount, int maxAmount) {
            this.material = material;
            this.weight = weight;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
        }
    }

    public static final class VariantEntry {
        public final boolean enabled;
        public final double chance;
        public final String name;

        public VariantEntry(boolean enabled, double chance, String name) {
            this.enabled = enabled;
            this.chance = chance;
            this.name = name;
        }
    }
}
