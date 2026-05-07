package com.nightfall.time;

import com.nightfall.NightfallConfig;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Drives the day/night clock manually.
 *
 * Vanilla Minecraft advances world.getFullTime() by 1 game-tick per
 * server tick. We disable doDaylightCycle on managed worlds and add our
 * own delta each tick so day takes {@code dayMinutes} real minutes and
 * night takes {@code nightMinutes} real minutes. The delta is fractional
 * (e.g. ~0.33 game-ticks/server-tick during day) so we keep a per-world
 * accumulator and only advance the world when whole ticks have built up.
 *
 * Phase boundaries (12000 / 24000 in time-of-day) cause a small delta
 * discontinuity but never a jump — the accumulator carries fractional
 * residue across ticks.
 */
public final class TimeController extends BukkitRunnable {

    /** Ticks per minecraft day phase (0..12000) and night phase (12000..24000). */
    private static final long PHASE_TICKS = 12000L;
    /** Server ticks per second. */
    private static final double SERVER_TPS = 20.0;

    private final NightfallConfig config;
    private final Map<UUID, Double> accumulators = new HashMap<>();

    public TimeController(NightfallConfig config) {
        this.config = config;
    }

    /**
     * Apply gamerules to all configured, currently-loaded worlds. Safe to
     * call multiple times; idempotent.
     */
    public void applyWorldRules() {
        for (String name : config.worlds()) {
            World w = Bukkit.getWorld(name);
            if (w == null) continue;
            applyRules(w);
        }
    }

    private void applyRules(World w) {
        Boolean dayCycle = w.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE);
        if (Boolean.TRUE.equals(dayCycle)) {
            w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        }
        if (config.preventNightSkip()) {
            // Setting this to >100 means TimeSkipEvent will never fire from
            // sleeping. We also defensively cancel TimeSkipEvent in
            // SleepBlocker, so this is belt-and-braces.
            Integer pct = w.getGameRuleValue(GameRule.PLAYERS_SLEEPING_PERCENTAGE);
            if (pct == null || pct <= 100) {
                w.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, 101);
            }
        }
    }

    @Override
    public void run() {
        for (String name : config.worlds()) {
            World w = Bukkit.getWorld(name);
            if (w == null) continue;
            applyRules(w);
            advance(w);
        }
    }

    private void advance(World w) {
        long tod = w.getTime(); // 0..23999
        boolean isNight = tod >= PHASE_TICKS && tod < PHASE_TICKS * 2;
        double minutes = isNight ? config.nightMinutes() : config.dayMinutes();
        double delta = PHASE_TICKS / (minutes * 60.0 * SERVER_TPS);

        UUID id = w.getUID();
        double acc = accumulators.getOrDefault(id, 0.0) + delta;
        long whole = (long) Math.floor(acc);
        if (whole > 0) {
            w.setFullTime(w.getFullTime() + whole);
            acc -= whole;
        }
        accumulators.put(id, acc);
    }

    /** Drop accumulator state — called on /nightfall reload to avoid drift if config changed. */
    public void resetAccumulators() {
        accumulators.clear();
    }

    /** True if the world's time-of-day is in the night band. */
    public static boolean isNight(World w) {
        long tod = w.getTime();
        return tod >= PHASE_TICKS && tod < PHASE_TICKS * 2;
    }
}
