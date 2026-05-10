package com.nightfall.listener;

import com.nightfall.NightfallConfig;
import com.nightfall.time.TimeController;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

/**
 * Transforms skeleton projectiles for specialized variants:
 *   - Pyro Skeleton: shoots small fireballs instead of arrows.
 *   - Blaze Archer: shoots faster small fireballs.
 *   - Bomber Skeleton: arrows spawn primed TNT on impact.
 */
public final class SkeletonProjectileListener implements Listener {

    private final NightfallConfig config;
    private final NamespacedKey pyroKey;
    private final NamespacedKey bomberKey;
    private final NamespacedKey blazeArcherKey;

    public SkeletonProjectileListener(NightfallConfig config, NightMobListener nightMobListener) {
        this.config = config;
        this.pyroKey = nightMobListener.pyroSkeletonKey();
        this.bomberKey = nightMobListener.bomberSkeletonKey();
        this.blazeArcherKey = nightMobListener.blazeArcherKey();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSkeletonShoot(EntityShootBowEvent event) {
        if (!config.mobVariantsEnabled()) return;
        if (!(event.getEntity() instanceof Skeleton skeleton)) return;
        if (!config.managesWorld(skeleton.getWorld().getName())) return;
        if (!TimeController.isNight(skeleton.getWorld())) return;

        var pdc = skeleton.getPersistentDataContainer();

        // Pyro Skeleton — small fireball
        if (pdc.has(pyroKey, PersistentDataType.BYTE)) {
            event.setConsumeArrow(false);
            launchFireball(skeleton, 1.2);
            // Remove the arrow that was spawned by vanilla logic
            if (event.getProjectile() instanceof AbstractArrow arrow) {
                arrow.remove();
            }
            event.setCancelled(true);
            return;
        }

        // Blaze Archer — faster small fireball
        if (pdc.has(blazeArcherKey, PersistentDataType.BYTE)) {
            event.setConsumeArrow(false);
            launchFireball(skeleton, 1.8);
            if (event.getProjectile() instanceof AbstractArrow arrow) {
                arrow.remove();
            }
            event.setCancelled(true);
            return;
        }

        // Bomber Skeleton — arrows are left alone, handled on impact
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!config.mobVariantsEnabled()) return;
        Projectile projectile = event.getEntity();
        if (!(projectile instanceof AbstractArrow arrow)) return;

        ProjectileSource shooter = arrow.getShooter();
        if (!(shooter instanceof Skeleton skeleton)) return;
        if (!config.managesWorld(skeleton.getWorld().getName())) return;

        var pdc = skeleton.getPersistentDataContainer();

        // Bomber Skeleton — spawn primed TNT at hit location
        if (pdc.has(bomberKey, PersistentDataType.BYTE)) {
            Location hitLoc = arrow.getLocation();
            World w = hitLoc.getWorld();
            if (w != null) {
                TNTPrimed tnt = w.spawn(hitLoc, TNTPrimed.class, t -> {
                    t.setFuseTicks(30); // 1.5s fuse
                    t.setSource(skeleton);
                });
            }
            arrow.remove();
        }
    }

    private void launchFireball(Skeleton skeleton, double speed) {
        Vector dir = skeleton.getLocation().getDirection().normalize();
        Location spawnLoc = skeleton.getEyeLocation().add(dir.clone().multiply(0.5));
        SmallFireball fireball = skeleton.getWorld().spawn(spawnLoc, SmallFireball.class, fb -> {
            fb.setVelocity(dir.multiply(speed));
            fb.setShooter(skeleton);
        });
    }
}
