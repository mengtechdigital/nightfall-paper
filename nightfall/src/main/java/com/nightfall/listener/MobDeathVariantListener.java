package com.nightfall.listener;

import com.nightfall.NightfallConfig;
import com.nightfall.time.TimeController;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles special death effects for mob variants:
 *   - Splitter Creeper: spawns 3 baby creepers on death.
 *   - Volatile Creeper: explodes on death.
 */
public final class MobDeathVariantListener implements Listener {

    private final NightfallConfig config;
    private final NamespacedKey splitterKey;
    private final NamespacedKey volatileKey;

    public MobDeathVariantListener(NightfallConfig config, NightMobListener nightMobListener) {
        this.config = config;
        this.splitterKey = nightMobListener.splitterCreeperKey();
        this.volatileKey = nightMobListener.volatileCreeperKey();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        if (!config.mobVariantsEnabled()) return;
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!(entity instanceof Creeper creeper)) return;

        World w = entity.getWorld();
        if (!config.managesWorld(w.getName())) return;
        if (!TimeController.isNight(w)) return;

        var pdc = entity.getPersistentDataContainer();

        // Splitter Creeper — spawn 3 baby creepers
        if (pdc.has(splitterKey, PersistentDataType.BYTE)) {
            Location loc = entity.getLocation();
            for (int i = 0; i < 3; i++) {
                double ox = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.0;
                double oz = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.0;
                Location spawnLoc = loc.clone().add(ox, 0, oz);
                // Use CUSTOM reason so NightMobListener buffs them if night
                Entity spawned = w.spawnEntity(spawnLoc, EntityType.CREEPER);
                if (!(spawned instanceof Creeper baby)) continue;
                // Make them smaller / faster by giving them a flash-creeper-like profile
                baby.setExplosionRadius(2);
                baby.setFuseTicks(15);
            }
        }

        // Volatile Creeper — explode on death
        if (pdc.has(volatileKey, PersistentDataType.BYTE)) {
            Location loc = entity.getLocation();
            w.createExplosion(loc, 3.0f, false, true, entity);
        }
    }
}
