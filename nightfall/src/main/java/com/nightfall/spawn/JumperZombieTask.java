package com.nightfall.spawn;

import com.nightfall.NightfallConfig;
import com.nightfall.time.TimeController;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Makes jumper zombies periodically leap toward nearby players.
 *
 * Runs every 10 ticks (0.5 s). A jumper zombie checks for the nearest
 * player within 12 blocks. If found, and its per-entity cooldown has
 * expired, it launches itself in an arc toward the player. The leap
 * applies a brief velocity impulse; mob AI will take over again once
 * the entity lands.
 */
public final class JumperZombieTask extends BukkitRunnable {

    /** How often the task scans (ticks). */
    private static final long TICK_PERIOD = 10L;
    /** Minimum ticks between leaps for a single zombie. */
    private static final int LEAP_COOLDOWN_TICKS = 40;
    /** Horizontal search radius for targets. */
    private static final double TARGET_RADIUS = 12.0;
    /** Leap forward impulse. */
    private static final double LEAP_HORIZONTAL = 0.55;
    /** Leap upward impulse. */
    private static final double LEAP_VERTICAL = 0.45;

    private final Plugin plugin;
    private final NightfallConfig config;
    private final NamespacedKey jumperKey;
    private final Map<UUID, Long> lastLeapTick = new HashMap<>();
    private long currentTick = 0;

    public JumperZombieTask(Plugin plugin, NightfallConfig config, NamespacedKey jumperKey) {
        this.plugin = plugin;
        this.config = config;
        this.jumperKey = jumperKey;
    }

    public void start() {
        runTaskTimer(plugin, TICK_PERIOD, TICK_PERIOD);
    }

    @Override
    public void run() {
        currentTick += TICK_PERIOD;
        if (!config.mobBuffEnabled()) return;

        for (String name : config.worlds()) {
            World w = Bukkit.getWorld(name);
            if (w == null) continue;
            if (!TimeController.isNight(w)) continue;

            for (Entity e : w.getEntitiesByClass(Zombie.class)) {
                if (!(e instanceof LivingEntity zombie)) continue;
                if (!zombie.getPersistentDataContainer().has(jumperKey, PersistentDataType.BYTE)) continue;
                if (zombie.isDead()) continue;

                Player target = findNearestPlayer(zombie);
                if (target == null) continue;
                if (target.getGameMode() == org.bukkit.GameMode.CREATIVE
                        || target.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;

                UUID id = zombie.getUniqueId();
                Long last = lastLeapTick.get(id);
                if (last != null && currentTick - last < LEAP_COOLDOWN_TICKS) continue;

                // Small randomization so all jumpers don't leap in perfect sync.
                if (ThreadLocalRandom.current().nextDouble() < 0.3) continue;

                leap(zombie, target);
                lastLeapTick.put(id, currentTick);
            }
        }

        // Prune old entries every ~20 s to prevent unbounded growth.
        if (currentTick % 400L == 0L) {
            lastLeapTick.entrySet().removeIf(e -> currentTick - e.getValue() > LEAP_COOLDOWN_TICKS * 4);
        }
    }

    private Player findNearestPlayer(LivingEntity zombie) {
        Player nearest = null;
        double best = TARGET_RADIUS * TARGET_RADIUS;
        for (Player p : zombie.getWorld().getPlayers()) {
            if (!p.isOnline()) continue;
            double distSq = p.getLocation().distanceSquared(zombie.getLocation());
            if (distSq < best) {
                best = distSq;
                nearest = p;
            }
        }
        return nearest;
    }

    private void leap(LivingEntity zombie, Player target) {
        Vector to = target.getLocation().toVector().subtract(zombie.getLocation().toVector());
        to.setY(0);
        double len = to.length();
        if (len < 0.01) {
            // Target is directly above/below — pick a random horizontal direction.
            to = new Vector(ThreadLocalRandom.current().nextDouble() - 0.5, 0,
                    ThreadLocalRandom.current().nextDouble() - 0.5);
            len = to.length();
        }
        to.normalize();
        to.multiply(LEAP_HORIZONTAL);
        to.setY(LEAP_VERTICAL);

        zombie.setVelocity(to);
    }
}
