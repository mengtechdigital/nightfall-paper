package com.nightfall.spawn;

import com.nightfall.NightfallConfig;
import com.nightfall.time.TimeController;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Makes Ender Stalkers periodically teleport closer to nearby players.
 *
 * Runs every 30 ticks (1.5 s). If an ender stalker has a player within
 * 32 blocks but further than 4 blocks, it attempts to teleport to a
 * random spot near that player (like an enderman aggressive teleport).
 */
public final class EnderStalkerTask extends BukkitRunnable {

    private static final long TICK_PERIOD = 30L;
    private static final double TELEPORT_MIN = 4.0;
    private static final double TELEPORT_MAX = 32.0;
    private static final int COOLDOWN_TICKS = 60;

    private final Plugin plugin;
    private final NightfallConfig config;
    private final NamespacedKey enderStalkerKey;
    private long currentTick = 0;

    public EnderStalkerTask(Plugin plugin, NightfallConfig config, NamespacedKey enderStalkerKey) {
        this.plugin = plugin;
        this.config = config;
        this.enderStalkerKey = enderStalkerKey;
    }

    public void start() {
        runTaskTimer(plugin, TICK_PERIOD, TICK_PERIOD);
    }

    @Override
    public void run() {
        currentTick += TICK_PERIOD;
        if (!config.mobVariantsEnabled()) return;

        for (String name : config.worlds()) {
            World w = Bukkit.getWorld(name);
            if (w == null) continue;
            if (!TimeController.isNight(w)) continue;

            for (Entity e : w.getEntitiesByClass(Enderman.class)) {
                if (!(e instanceof Enderman ender)) continue;
                if (!ender.getPersistentDataContainer().has(enderStalkerKey, PersistentDataType.BYTE)) continue;
                if (ender.isDead()) continue;
                // Respect enderman teleport cooldown ( Paper tracks this internally )
                if (ender.getRemainingAir() < 0) continue; // dummy check; we rely on teleport() success

                Player target = findEligiblePlayer(ender);
                if (target == null) continue;
                if (target.getGameMode() == org.bukkit.GameMode.CREATIVE
                        || target.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;

                Location dest = findTeleportDest(target.getLocation());
                if (dest != null) {
                    ender.teleport(dest);
                }
            }
        }
    }

    private Player findEligiblePlayer(Enderman ender) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        Location eloc = ender.getLocation();
        for (Player p : ender.getWorld().getPlayers()) {
            if (!p.isOnline()) continue;
            double d = p.getLocation().distanceSquared(eloc);
            double minSq = TELEPORT_MIN * TELEPORT_MIN;
            double maxSq = TELEPORT_MAX * TELEPORT_MAX;
            if (d >= minSq && d <= maxSq && d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private Location findTeleportDest(Location center) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 16; i++) {
            double dx = (rng.nextDouble() - 0.5) * 6.0;
            double dz = (rng.nextDouble() - 0.5) * 6.0;
            Location dest = center.clone().add(dx, 0, dz);
            dest.setY(center.getY() + rng.nextDouble() * 2.0);
            if (dest.getBlock().isPassable() && dest.clone().add(0, 1, 0).getBlock().isPassable()) {
                return dest;
            }
        }
        return null;
    }
}
