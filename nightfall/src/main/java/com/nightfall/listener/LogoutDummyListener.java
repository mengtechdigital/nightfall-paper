package com.nightfall.listener;

import com.nightfall.dummy.LogoutDummyManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;

/** Bridges Bukkit player/entity events into LogoutDummyManager. */
public final class LogoutDummyListener implements Listener {

    private final LogoutDummyManager manager;

    public LogoutDummyListener(LogoutDummyManager manager) {
        this.manager = manager;
    }

    // HIGH priority (not MONITOR) for quit: we read player state and spawn
    // an entity, both of which are unsafe after Paper has finished its
    // session-teardown pass at MONITOR.
    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        manager.onQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        manager.onJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!manager.isDummy(event.getEntity())) return;
        manager.onDummyDeath(event.getEntity());
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    /**
     * Plugins like Multiverse can load managed worlds after onEnable. If
     * we don't reindex here, dummies in those worlds never enter the
     * in-memory map and the next quit silently orphans the prior dummy.
     */
    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        manager.indexWorld(event.getWorld());
    }
}
