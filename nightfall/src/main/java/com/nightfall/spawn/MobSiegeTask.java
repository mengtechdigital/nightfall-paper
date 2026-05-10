package com.nightfall.spawn;

import com.nightfall.NightfallConfig;
import com.nightfall.time.TimeController;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Allows designated "siege" mobs to break through blocks to reach players,
 * including digging downward when players hide underground.
 *
 * Runs every 20 ticks (1 s). Each siege mob can break at most 1 block
 * per tick cycle. Iron blocks, bedrock, obsidian, and other critical
 * blocks are immune.
 */
public final class MobSiegeTask extends BukkitRunnable {

    private static final long TICK_PERIOD = 20L;
    private static final double SEARCH_RADIUS = 24.0;
    private static final double VERTICAL_DIG_THRESHOLD = 2.0;
    private static final int COOLDOWN_TICKS = 20;

    private static final Set<Material> UNBREAKABLE = Set.of(
            Material.BEDROCK,
            Material.IRON_BLOCK,
            Material.OBSIDIAN,
            Material.CRYING_OBSIDIAN,
            Material.BARRIER,
            Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.STRUCTURE_BLOCK,
            Material.JIGSAW,
            Material.END_PORTAL_FRAME,
            Material.END_PORTAL,
            Material.NETHER_PORTAL,
            Material.LIGHT,
            Material.SPAWNER,
            Material.AIR,
            Material.CAVE_AIR,
            Material.VOID_AIR
    );

    private final Plugin plugin;
    private final NightfallConfig config;
    private final NamespacedKey siegeKey;
    private final Map<UUID, Long> lastBreakTick = new ConcurrentHashMap<>();
    private long currentTick = 0;

    public MobSiegeTask(Plugin plugin, NightfallConfig config, NamespacedKey siegeKey) {
        this.plugin = plugin;
        this.config = config;
        this.siegeKey = siegeKey;
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

            for (Entity e : w.getEntities()) {
                if (!(e instanceof LivingEntity mob)) continue;
                if (!mob.getPersistentDataContainer().has(siegeKey, PersistentDataType.BYTE)) continue;
                if (mob.isDead()) continue;

                Long last = lastBreakTick.get(mob.getUniqueId());
                if (last != null && currentTick - last < COOLDOWN_TICKS) continue;

                Player target = findNearestPlayer(mob);
                if (target == null) continue;
                if (target.getGameMode() == org.bukkit.GameMode.CREATIVE
                        || target.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;

                if (tryBreakToward(mob, target)) {
                    lastBreakTick.put(mob.getUniqueId(), currentTick);
                }
            }
        }

        // Prune stale entries every ~30 s
        if (currentTick % 600L == 0L) {
            lastBreakTick.entrySet().removeIf(entry -> currentTick - entry.getValue() > COOLDOWN_TICKS * 4);
        }
    }

    private Player findNearestPlayer(LivingEntity mob) {
        Player best = null;
        double bestDist = SEARCH_RADIUS * SEARCH_RADIUS;
        for (Player p : mob.getWorld().getPlayers()) {
            if (!p.isOnline()) continue;
            double d = p.getLocation().distanceSquared(mob.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private boolean tryBreakToward(LivingEntity mob, Player target) {
        Location mobLoc = mob.getLocation();
        Location targetLoc = target.getLocation();

        double dy = targetLoc.getY() - mobLoc.getY();

        // Priority 1: dig down if player is below us
        if (dy < -VERTICAL_DIG_THRESHOLD) {
            Block under = mobLoc.clone().add(0, -0.5, 0).getBlock();
            if (isBreakable(under)) {
                breakBlock(under);
                return true;
            }
        }

        // Priority 2: break horizontal wall
        Vector toTarget = targetLoc.toVector().subtract(mobLoc.toVector());
        toTarget.setY(0);
        if (toTarget.lengthSquared() < 0.01) return false;
        toTarget.normalize();

        // Check at foot level and head level
        for (double yOffset : new double[]{0.0, 1.0}) {
            Location checkLoc = mobLoc.clone().add(toTarget.getX() * 1.2, yOffset, toTarget.getZ() * 1.2);
            Block block = checkLoc.getBlock();
            if (isBreakable(block)) {
                breakBlock(block);
                return true;
            }
        }

        // Priority 3: if player is above, break ceiling above mob or floor above target
        if (dy > VERTICAL_DIG_THRESHOLD) {
            Block above = mobLoc.clone().add(0, 2.0, 0).getBlock();
            if (isBreakable(above)) {
                breakBlock(above);
                return true;
            }
        }

        return false;
    }

    private boolean isBreakable(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        return !type.isAir() && !UNBREAKABLE.contains(type);
    }

    private void breakBlock(Block block) {
        World w = block.getWorld();
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        w.playSound(center, Sound.BLOCK_STONE_BREAK, 1.0f, 0.8f);
        w.spawnParticle(Particle.BLOCK, center, 20, 0.3, 0.3, 0.3, block.getBlockData());
        block.breakNaturally();
    }
}
