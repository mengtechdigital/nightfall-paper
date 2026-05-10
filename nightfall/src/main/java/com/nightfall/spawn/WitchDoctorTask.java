package com.nightfall.spawn;

import com.nightfall.NightfallConfig;
import com.nightfall.time.TimeController;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Witch;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Makes Witch Doctors periodically grant Regeneration to nearby hostile mobs.
 *
 * Runs every 40 ticks (2 s). Each witch doctor gives Regeneration I for 5s
 * to all Monsters within 10 blocks (excluding itself).
 */
public final class WitchDoctorTask extends BukkitRunnable {

    private static final long TICK_PERIOD = 40L;
    private static final double HEAL_RADIUS = 10.0;
    private static final int HEAL_DURATION_TICKS = 100; // 5s

    private final Plugin plugin;
    private final NightfallConfig config;
    private final NamespacedKey witchDoctorKey;

    public WitchDoctorTask(Plugin plugin, NightfallConfig config, NamespacedKey witchDoctorKey) {
        this.plugin = plugin;
        this.config = config;
        this.witchDoctorKey = witchDoctorKey;
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

            for (Entity e : w.getEntitiesByClass(Witch.class)) {
                if (!(e instanceof Witch witch)) continue;
                if (!witch.getPersistentDataContainer().has(witchDoctorKey, PersistentDataType.BYTE)) continue;
                if (witch.isDead()) continue;

                PotionEffect regen = new PotionEffect(PotionEffectType.REGENERATION, HEAL_DURATION_TICKS, 0, false, false, false);
                for (Entity nearby : witch.getNearbyEntities(HEAL_RADIUS, HEAL_RADIUS, HEAL_RADIUS)) {
                    if (nearby instanceof Monster m && !m.equals(witch)) {
                        m.addPotionEffect(regen);
                    }
                }
            }
        }
    }
}
