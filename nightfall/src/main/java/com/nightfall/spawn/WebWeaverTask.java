package com.nightfall.spawn;

import com.nightfall.NightfallConfig;
import com.nightfall.time.TimeController;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Spider;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Makes Web Weaver spiders periodically shoot cobwebs at nearby players.
 *
 * Runs every 30 ticks (1.5 s). When a web weaver has a player within
 * 14 blocks, it places a cobweb at the player's feet (or in their path).
 */
public final class WebWeaverTask extends BukkitRunnable {

    private static final long TICK_PERIOD = 30L;
    private static final double WEB_RADIUS = 14.0;
    private static final int COOLDOWN_TICKS = 60;

    private final Plugin plugin;
    private final NightfallConfig config;
    private final NamespacedKey webWeaverKey;
    private final Map<UUID, Long> lastWebTick = new ConcurrentHashMap<>();
    private long currentTick = 0;

    public WebWeaverTask(Plugin plugin, NightfallConfig config, NamespacedKey webWeaverKey) {
        this.plugin = plugin;
        this.config = config;
        this.webWeaverKey = webWeaverKey;
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

            for (Entity e : w.getEntitiesByClass(Spider.class)) {
                if (!(e instanceof Spider spider)) continue;
                if (!spider.getPersistentDataContainer().has(webWeaverKey, PersistentDataType.BYTE)) continue;
                if (spider.isDead()) continue;

                Long last = lastWebTick.get(spider.getUniqueId());
                if (last != null && currentTick - last < COOLDOWN_TICKS) continue;

                Player target = findNearestPlayer(spider);
                if (target == null) continue;
                if (target.getGameMode() == org.bukkit.GameMode.CREATIVE
                        || target.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;

                if (placeWeb(target.getLocation())) {
                    lastWebTick.put(spider.getUniqueId(), currentTick);
                    w.playSound(spider.getLocation(), Sound.BLOCK_SLIME_BLOCK_PLACE, 1.0f, 0.6f);
                }
            }
        }

        if (currentTick % 600L == 0L) {
            lastWebTick.entrySet().removeIf(entry -> currentTick - entry.getValue() > COOLDOWN_TICKS * 4);
        }
    }

    private Player findNearestPlayer(LivingEntity spider) {
        Player best = null;
        double bestDist = WEB_RADIUS * WEB_RADIUS;
        for (Player p : spider.getWorld().getPlayers()) {
            if (!p.isOnline()) continue;
            double d = p.getLocation().distanceSquared(spider.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private boolean placeWeb(Location loc) {
        // Try the block at player's feet first
        Block feet = loc.getBlock();
        if (feet.getType().isAir()) {
            feet.setType(Material.COBWEB);
            return true;
        }
        // Try one block in a random horizontal direction
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int dx = rng.nextInt(-1, 2);
        int dz = rng.nextInt(-1, 2);
        Block nearby = feet.getRelative(dx, 0, dz);
        if (nearby.getType().isAir()) {
            nearby.setType(Material.COBWEB);
            return true;
        }
        // Try one block above
        Block above = feet.getRelative(0, 1, 0);
        if (above.getType().isAir()) {
            above.setType(Material.COBWEB);
            return true;
        }
        return false;
    }
}
