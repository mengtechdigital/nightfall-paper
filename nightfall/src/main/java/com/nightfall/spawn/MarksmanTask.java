package com.nightfall.spawn;

import com.nightfall.NightfallConfig;
import com.nightfall.time.TimeController;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Makes Skeleton Marksmen reset their arrow cooldown frequently so they
 * shoot at roughly twice the normal rate.
 *
 * Runs every 10 ticks (0.5 s). A marksman skeleton that has a target
 * within 24 blocks gets its arrow cooldown forced to 0 so it fires
 * again immediately.
 */
public final class MarksmanTask extends BukkitRunnable {

    private static final long TICK_PERIOD = 10L;
    private static final double TARGET_RADIUS = 24.0;

    private final Plugin plugin;
    private final NightfallConfig config;
    private final NamespacedKey marksmanKey;

    public MarksmanTask(Plugin plugin, NightfallConfig config, NamespacedKey marksmanKey) {
        this.plugin = plugin;
        this.config = config;
        this.marksmanKey = marksmanKey;
    }

    public void start() {
        runTaskTimer(plugin, TICK_PERIOD, TICK_PERIOD);
    }

    @Override
    public void run() {
        if (!config.mobVariantsEnabled()) return;

        for (String name : config.worlds()) {
            World w = Bukkit.getWorld(name);
            if (w == null) continue;
            if (!TimeController.isNight(w)) continue;

            for (Entity e : w.getEntitiesByClass(Skeleton.class)) {
                if (!(e instanceof Skeleton skeleton)) continue;
                if (!skeleton.getPersistentDataContainer().has(marksmanKey, PersistentDataType.BYTE)) continue;
                if (skeleton.isDead()) continue;

                Player target = findNearestPlayer(skeleton);
                if (target == null) continue;
                if (target.getGameMode() == org.bukkit.GameMode.CREATIVE
                        || target.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;

                // Force immediate readiness to fire.
                skeleton.setArrowCooldown(0);
            }
        }
    }

    private Player findNearestPlayer(LivingEntity entity) {
        Player nearest = null;
        double best = TARGET_RADIUS * TARGET_RADIUS;
        for (Player p : entity.getWorld().getPlayers()) {
            if (!p.isOnline()) continue;
            double distSq = p.getLocation().distanceSquared(entity.getLocation());
            if (distSq < best) {
                best = distSq;
                nearest = p;
            }
        }
        return nearest;
    }
}
