package com.nightfall.listener;

import com.nightfall.NightfallConfig;
import com.nightfall.time.TimeController;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Handles on-hit and on-damaged effects for mob variants.
 */
public final class MobVariantHitListener implements Listener {

    private final NightfallConfig config;
    private final NamespacedKey desertKey;
    private final NamespacedKey venomousKey;
    private final NamespacedKey witherReaperKey;
    private final NamespacedKey frozenKey;
    private final NamespacedKey plagueKey;
    private final NamespacedKey berserkerKey;
    private final NamespacedKey phantomDiverKey;
    private final NamespacedKey pillagerSniperKey;

    public MobVariantHitListener(NightfallConfig config, NightMobListener nightMobListener) {
        this.config = config;
        this.desertKey = nightMobListener.desertKey();
        this.venomousKey = nightMobListener.venomousKey();
        this.witherReaperKey = nightMobListener.witherReaperKey();
        this.frozenKey = nightMobListener.frozenKey();
        this.plagueKey = nightMobListener.plagueKey();
        this.berserkerKey = nightMobListener.berserkerKey();
        this.phantomDiverKey = nightMobListener.phantomDiverKey();
        this.pillagerSniperKey = nightMobListener.pillagerSniperKey();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMobHit(EntityDamageByEntityEvent event) {
        if (!config.mobVariantsEnabled()) return;

        Entity damager = event.getDamager();
        Entity victim = event.getEntity();
        if (!(victim instanceof Player player)) return;

        // Unwrap projectiles to the shooter
        LivingEntity attacker = null;
        if (damager instanceof LivingEntity le) {
            attacker = le;
        } else if (damager instanceof Projectile proj && proj.getShooter() instanceof LivingEntity le) {
            attacker = le;
        }
        if (attacker == null) return;

        World w = attacker.getWorld();
        if (!config.managesWorld(w.getName())) return;
        if (!TimeController.isNight(w)) return;

        var pdc = attacker.getPersistentDataContainer();

        // Desert Zombie — fire + hunger
        if (pdc.has(desertKey, PersistentDataType.BYTE)) {
            player.setFireTicks(Math.max(player.getFireTicks(), 80)); // 4s
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 160, 2, false, false, false));
        }

        // Venomous Spider — poison
        if (pdc.has(venomousKey, PersistentDataType.BYTE)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1, false, false, false));
        }

        // Wither Reaper — wither
        if (pdc.has(witherReaperKey, PersistentDataType.BYTE)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 120, 2, false, false, false));
        }

        // Plague Zombie — weakness + nausea
        if (pdc.has(plagueKey, PersistentDataType.BYTE)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1, false, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0, false, false, false));
        }

        // Phantom Diver — blindness
        if (pdc.has(phantomDiverKey, PersistentDataType.BYTE)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, false));
        }

        // Frozen Stray — slowness on hit (arrows or melee)
        if (pdc.has(frozenKey, PersistentDataType.BYTE)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1, false, false, false));
        }

        // Pillager Sniper — extra knockback on arrow hits (handled by Punch enchant on crossbow)
        // We add a small velocity push for extra feel.
        if (pdc.has(pillagerSniperKey, PersistentDataType.BYTE) && damager instanceof Projectile) {
            org.bukkit.util.Vector push = player.getLocation().toVector()
                    .subtract(attacker.getLocation().toVector()).normalize().setY(0.3).multiply(0.6);
            player.setVelocity(player.getVelocity().add(push));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMobDamaged(EntityDamageEvent event) {
        if (!config.mobVariantsEnabled()) return;
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!(entity instanceof Monster)) return;

        World w = entity.getWorld();
        if (!config.managesWorld(w.getName())) return;
        if (!TimeController.isNight(w)) return;

        // Vindicator Berserker — rage mode below 50% HP
        if (entity.getPersistentDataContainer().has(berserkerKey, PersistentDataType.BYTE)) {
            double maxHp = entity.getMaxHealth();
            double current = entity.getHealth() - event.getFinalDamage();
            double threshold = maxHp * 0.50;
            if (current <= threshold && current > 0) {
                // Only apply if not already raging (check via a temporary PDC flag or just re-apply)
                // Re-applying every hit is fine; potion effects overwrite with same/longer duration.
                entity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1, false, false, false));
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false, false));
            }
        }
    }
}
