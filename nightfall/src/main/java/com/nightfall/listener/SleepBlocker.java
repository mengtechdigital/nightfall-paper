package com.nightfall.listener;

import com.nightfall.NightfallConfig;
import com.nightfall.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.TimeSkipEvent;

/**
 * Cancels NIGHT_SKIP. Bed-enter still sets respawn point — we just
 * don't let the world fast-forward to morning.
 *
 * Defense in depth: TimeController also pins playersSleepingPercentage
 * to 101, which prevents this event from firing in the first place.
 * This listener catches anything that bypasses the gamerule (other
 * plugins forcing a skip, /time set day-via-event, etc.).
 */
public final class SleepBlocker implements Listener {

    private final NightfallConfig config;

    public SleepBlocker(NightfallConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTimeSkip(TimeSkipEvent event) {
        if (!config.preventNightSkip()) return;
        if (event.getSkipReason() != TimeSkipEvent.SkipReason.NIGHT_SKIP) return;

        World w = event.getWorld();
        if (!config.managesWorld(w.getName())) return;

        event.setCancelled(true);

        String raw = config.sleepSkipBlockedMessage();
        if (raw == null || raw.isEmpty()) return;
        Component msg = Component.text(Text.color(raw));
        for (Player p : w.getPlayers()) {
            p.sendMessage(msg);
        }
    }
}
