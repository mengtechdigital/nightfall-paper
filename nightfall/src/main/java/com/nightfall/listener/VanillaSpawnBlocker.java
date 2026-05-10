package com.nightfall.listener;

import com.nightfall.NightfallConfig;
import com.nightfall.time.TimeController;
import org.bukkit.World;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Cancels vanilla NATURAL hostile mob spawns at night in managed worlds.
 *
 * Why this kills AFK farms:
 *   Vanilla spawning cares about spawn chunks, light levels, and opaque
 *   blocks. AFK farms exploit this by building large dark platforms in
 *   spawn chunks and funnelling mobs to a kill zone. By cancelling
 *   NATURAL spawns and routing all night hostile spawning through our
 *   {@link com.nightfall.spawn.ExtraSpawnTask} (which spawns directly
 *   near players in a ring), dark rooms far from the player produce
 *   absolutely nothing.
 *
 * Only {@code SpawnReason.NATURAL} is blocked. Spawners, spawn eggs,
 * plugins, and our own CUSTOM spawns are unaffected.
 */
public final class VanillaSpawnBlocker implements Listener {

    private final NightfallConfig config;

    public VanillaSpawnBlocker(NightfallConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        if (!(event.getEntity() instanceof Monster)) return;

        World w = event.getLocation().getWorld();
        if (w == null) return;
        if (!config.managesWorld(w.getName())) return;
        if (!config.disableVanillaNightSpawns()) return;
        if (!TimeController.isNight(w)) return;

        event.setCancelled(true);
    }
}
