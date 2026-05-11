package com.nightfall.spawn;

import com.nightfall.NightfallConfig;
import com.nightfall.time.TimeController;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Periodic spawner that adds extra hostile mobs near each player during
 * the (now brief) night. Caps total nightfall-tagged mobs per world to
 * avoid runaway spawns.
 *
 * When {@code night.disable-vanilla-night-spawns} is true, this task
 * becomes the PRIMARY source of hostile mobs at night. It spawns mobs
 * directly near players in a horizontal ring, which makes AFK dark-room
 * farms completely ineffective.
 *
 * Spawn-loc selection: pick a random offset in a horizontal ring around
 * the player ({@code minDistance}..{@code maxDistance}), drop the y
 * coordinate to the highest non-air solid block, then verify there's a
 * 2-block air gap above and (optionally) light level <= 7.
 *
 * Flying mobs (Blaze, Ghast, Phantom, Vex) spawn in the air above the
 * ground instead of on it, and skip the darkness check.
 *
 * Tagged mobs flow through NightMobListener like any other CUSTOM spawn
 * — they get the runner roll, attribute buffs, door-break, gear, etc.
 */
public final class ExtraSpawnTask implements Runnable {

    /** Tries per spawn slot before we give up and move to the next slot. */
    private static final int LOC_TRIES = 8;

    /** Mobs that spawn in the air rather than on the ground. */
    private static final Set<EntityType> FLYING_MOBS = Set.of(
            EntityType.BLAZE,
            EntityType.GHAST,
            EntityType.PHANTOM,
            EntityType.VEX
    );

    private final Plugin plugin;
    private final NightfallConfig config;
    private final NamespacedKey extraTag;

    public ExtraSpawnTask(Plugin plugin, NightfallConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.extraTag = new NamespacedKey(plugin, "extra_spawn");
    }

    public NamespacedKey extraTag() { return extraTag; }

    @Override
    public void run() {
        if (!config.extraSpawnEnabled()) return;

        for (String name : config.worlds()) {
            World w = Bukkit.getWorld(name);
            if (w == null) continue;
            if (!TimeController.isNight(w)) continue;

            int existing = countTagged(w);
            int budget = config.extraMaxPerWorld() - existing;
            if (budget <= 0) continue;

            for (Player p : w.getPlayers()) {
                if (budget <= 0) break;
                if (p.getGameMode() == org.bukkit.GameMode.CREATIVE
                        || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;

                int spawns = Math.min(config.extraPerPlayerPerCycle(), budget);
                for (int i = 0; i < spawns; i++) {
                    if (spawnNearPlayer(p)) {
                        budget--;
                    }
                }
            }
        }
    }

    /** @return true if a mob was actually spawned. */
    private boolean spawnNearPlayer(Player player) {
        World w = player.getWorld();
        EntityType type = pickWeightedType(config.extraMobWeights());
        if (type == null) return false;
        boolean isFlying = FLYING_MOBS.contains(type);

        for (int attempt = 0; attempt < LOC_TRIES; attempt++) {
            Location loc = pickRingLocation(player);
            if (loc == null) continue;

            if (isFlying) {
                // Spawn in air 5–15 blocks above the ground.
                double airY = loc.getY() + 5.0 + ThreadLocalRandom.current().nextDouble() * 10.0;
                loc.setY(airY);
                // Ensure two air blocks.
                if (!loc.getBlock().getType().isAir()) continue;
                if (!loc.getBlock().getRelative(0, 1, 0).getType().isAir()) continue;
            } else {
                if (!isSpawnable(loc)) continue;
                if (config.extraRequireDarkness() && loc.getBlock().getLightLevel() > 7) continue;
            }

            Entity spawned = w.spawnEntity(loc, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
            if (spawned == null || !spawned.isValid()) return false;
            spawned.getPersistentDataContainer().set(extraTag, PersistentDataType.BYTE, (byte) 1);
            return true;
        }
        return false;
    }

    private Location pickRingLocation(Player player) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double dist = config.extraMinDistance()
                + rng.nextDouble() * (config.extraMaxDistance() - config.extraMinDistance());
        double angle = rng.nextDouble() * Math.PI * 2.0;
        Location base = player.getLocation();

        int x = base.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
        int z = base.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);

        World w = base.getWorld();
        if (w == null) return null;

        // Critical: getHighestBlockYAt synchronously generates the chunk
        // if it isn't already loaded, which can stall the main thread for
        // seconds on a large map. Skip if the chunk is not currently
        // loaded — the next cycle's roll will pick a different ring spot.
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return null;

        int y = w.getHighestBlockYAt(x, z);
        // Clamp to world build limits.
        if (y <= w.getMinHeight() || y >= w.getMaxHeight() - 2) return null;
        return new Location(w, x + 0.5, y + 1, z + 0.5);
    }

    /** Two air blocks above a non-air, non-liquid floor. */
    private boolean isSpawnable(Location loc) {
        Block feet = loc.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block floor = feet.getRelative(0, -1, 0);
        if (!feet.getType().isAir() || !head.getType().isAir()) return false;
        Material floorMat = floor.getType();
        if (floorMat.isAir() || !floor.getType().isSolid()) return false;
        if (floorMat == Material.LAVA || floorMat == Material.WATER) return false;
        return true;
    }

    private int countTagged(World w) {
        // Restrict the scan to Monster instances — we never tag anything
        // else, and this typically reduces the iteration size by 10x
        // versus w.getEntities() (which includes items, projectiles, etc).
        int n = 0;
        for (Monster m : w.getEntitiesByClass(Monster.class)) {
            if (m.getPersistentDataContainer().has(extraTag, PersistentDataType.BYTE)) n++;
        }
        return n;
    }

    private static EntityType pickWeightedType(Map<EntityType, Integer> weights) {
        if (weights.isEmpty()) return null;
        int total = 0;
        for (Integer w : weights.values()) total += w;
        if (total <= 0) return null;
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (Map.Entry<EntityType, Integer> e : weights.entrySet()) {
            roll -= e.getValue();
            if (roll < 0) return e.getKey();
        }
        return null; // unreachable given total > 0
    }

    /** Convenience for /nightfall status. */
    public int countExtras(World w) {
        return countTagged(w);
    }

    public List<String> managedWorlds() {
        return config.worlds();
    }
}
