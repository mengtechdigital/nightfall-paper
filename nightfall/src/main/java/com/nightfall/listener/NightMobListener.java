package com.nightfall.listener;

import com.nightfall.NightfallConfig;
import com.nightfall.gear.MobGearApplier;
import com.nightfall.time.TimeController;
import com.nightfall.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Stray;
import org.bukkit.entity.Vindicator;
import org.bukkit.entity.Witch;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Applies nighttime threats to hostile mobs as they spawn:
 *   - Runner: ~runnerChance of zombies get Speed + custom name.
 *   - Jumper: ~jumperChance of zombies get Jump Boost + custom name.
 *   - Charged creepers: during thunderstorms, creepers spawn powered.
 *   - Storm buff: during rain/thunder, all hostiles get extra HP/damage.
 *   - Distance scaling: stats scale infinitely with distance from spawn.
 *   - Mob gear: enchanted armor/weapons that scale with distance.
 *   - Mob buff: hostile mobs get scaled max-health and attack-damage.
 *   - Spider speed: spiders get a small speed boost.
 *   - Skeleton accuracy: skeletons get increased follow range.
 *   - Door break: zombies/zombie-villagers break doors regardless of difficulty.
 *
 * Triggers off CreatureSpawnEvent so it covers both vanilla NATURAL spawns
 * and our own ExtraSpawnTask (which fires CUSTOM-reason spawns). Other
 * spawn reasons (eggs, breeding, mob spawners) are excluded so we don't
 * mess with builder-placed mobs.
 */
public final class NightMobListener implements Listener {

    /** Spawn reasons we transform. NATURAL = normal night spawns; CUSTOM = our extras. */
    private static final Set<CreatureSpawnEvent.SpawnReason> ELIGIBLE_REASONS = Set.of(
            CreatureSpawnEvent.SpawnReason.NATURAL,
            CreatureSpawnEvent.SpawnReason.CUSTOM,
            CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION,
            CreatureSpawnEvent.SpawnReason.PATROL,
            CreatureSpawnEvent.SpawnReason.RAID,
            CreatureSpawnEvent.SpawnReason.REINFORCEMENTS
    );

    private final NightfallConfig config;
    private final MobGearApplier gearApplier;
    /** Marks an entity as already buffed so we never double-stack modifiers. */
    private final NamespacedKey buffedKey;
    /** Marks runner zombies (used by status counts and future tooling). */
    private final NamespacedKey runnerKey;
    /** Marks jumper zombies. */
    private final NamespacedKey jumperKey;
    /** Fixed modifier keys — see applyAttributeBoost for why these are stable. */
    private final NamespacedKey hpModKey;
    private final NamespacedKey atkModKey;
    private final NamespacedKey stormHpModKey;
    private final NamespacedKey stormAtkModKey;
    private final NamespacedKey distHpModKey;
    private final NamespacedKey distAtkModKey;
    /** Variant tracking keys. */
    private final NamespacedKey marksmanKey;
    private final NamespacedKey desertKey;
    private final NamespacedKey venomousKey;
    private final NamespacedKey witchDoctorKey;
    private final NamespacedKey bruteKey;
    private final NamespacedKey witherReaperKey;
    private final NamespacedKey frozenKey;
    private final NamespacedKey enderStalkerKey;
    private final NamespacedKey plagueKey;
    private final NamespacedKey berserkerKey;
    private final NamespacedKey phantomDiverKey;
    private final NamespacedKey pillagerSniperKey;
    private final NamespacedKey siegeKey;
    private final NamespacedKey flashCreeperKey;
    private final NamespacedKey splitterCreeperKey;
    private final NamespacedKey volatileCreeperKey;
    private final NamespacedKey webWeaverKey;
    private final NamespacedKey pyroSkeletonKey;
    private final NamespacedKey bomberSkeletonKey;
    private final NamespacedKey blazeArcherKey;
    private final NamespacedKey swarmZombieKey;

    public NightMobListener(Plugin plugin, NightfallConfig config) {
        this.config = config;
        this.gearApplier = new MobGearApplier(config);
        this.buffedKey = new NamespacedKey(plugin, "night_buffed");
        this.runnerKey = new NamespacedKey(plugin, "runner");
        this.jumperKey = new NamespacedKey(plugin, "jumper");
        this.hpModKey  = new NamespacedKey(plugin, "nf_hp");
        this.atkModKey = new NamespacedKey(plugin, "nf_atk");
        this.stormHpModKey  = new NamespacedKey(plugin, "nf_storm_hp");
        this.stormAtkModKey = new NamespacedKey(plugin, "nf_storm_atk");
        this.distHpModKey   = new NamespacedKey(plugin, "nf_dist_hp");
        this.distAtkModKey  = new NamespacedKey(plugin, "nf_dist_atk");
        this.marksmanKey    = new NamespacedKey(plugin, "marksman");
        this.desertKey      = new NamespacedKey(plugin, "desert");
        this.venomousKey    = new NamespacedKey(plugin, "venomous");
        this.witchDoctorKey = new NamespacedKey(plugin, "witch_doctor");
        this.bruteKey       = new NamespacedKey(plugin, "brute");
        this.witherReaperKey = new NamespacedKey(plugin, "wither_reaper");
        this.frozenKey      = new NamespacedKey(plugin, "frozen");
        this.enderStalkerKey = new NamespacedKey(plugin, "ender_stalker");
        this.plagueKey      = new NamespacedKey(plugin, "plague");
        this.berserkerKey   = new NamespacedKey(plugin, "berserker");
        this.phantomDiverKey = new NamespacedKey(plugin, "phantom_diver");
        this.pillagerSniperKey = new NamespacedKey(plugin, "pillager_sniper");
        this.siegeKey        = new NamespacedKey(plugin, "siege");
        this.flashCreeperKey = new NamespacedKey(plugin, "flash_creeper");
        this.splitterCreeperKey = new NamespacedKey(plugin, "splitter_creeper");
        this.volatileCreeperKey = new NamespacedKey(plugin, "volatile_creeper");
        this.webWeaverKey    = new NamespacedKey(plugin, "web_weaver");
        this.pyroSkeletonKey = new NamespacedKey(plugin, "pyro_skeleton");
        this.bomberSkeletonKey = new NamespacedKey(plugin, "bomber_skeleton");
        this.blazeArcherKey  = new NamespacedKey(plugin, "blaze_archer");
        this.swarmZombieKey  = new NamespacedKey(plugin, "swarm_zombie");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!ELIGIBLE_REASONS.contains(event.getSpawnReason())) return;

        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Monster monster)) return;
        if (!config.managesWorld(entity.getWorld().getName())) return;
        if (!TimeController.isNight(entity.getWorld())) return;

        // Idempotency guard: PDC tag means we've buffed this mob already.
        if (entity.getPersistentDataContainer().has(buffedKey, PersistentDataType.BYTE)) return;
        entity.getPersistentDataContainer().set(buffedKey, PersistentDataType.BYTE, (byte) 1);

        boolean isStormy = entity.getWorld().hasStorm();
        double distance = horizontalDistanceFromSpawn(entity);
        double distFactor = computeDistanceFactor(distance);

        // Door-breaking — restrict to plain Zombies and ZombieVillagers.
        // Drowned/Husk don't have the BreakDoorGoal in vanilla and we
        // don't want to surprise admins by changing that.
        if (config.forceZombieDoorBreak()
                && entity instanceof Zombie z
                && !(entity instanceof Drowned)
                && !(entity instanceof Husk)) {
            z.setCanBreakDoors(true);
        }

        // Runner — only zombies (any subclass) become runners.
        if (entity instanceof Zombie && ThreadLocalRandom.current().nextDouble() < config.runnerChance()) {
            applyRunner(entity);
        }

        // Jumper — only plain zombies and husks become jumpers.
        if (entity instanceof Zombie && !(entity instanceof Drowned)
                && ThreadLocalRandom.current().nextDouble() < config.jumperChance()) {
            applyJumper(entity);
        }

        // Charged creepers during thunderstorms.
        if (config.chargedCreeperEnabled()
                && isStormy
                && entity instanceof Creeper creeper
                && ThreadLocalRandom.current().nextDouble() < config.chargedCreeperChance()) {
            creeper.setPowered(true);
        }

        // Spider speed boost — makes them harder to kite.
        if (entity instanceof Spider spider) {
            applySpiderBoost(spider);
        }

        // Skeleton follow range + night vision so they snipe from further.
        if (entity instanceof Skeleton skeleton) {
            applySkeletonBoost(skeleton);
        }

        // ---- New mob variants ----
        if (config.mobVariantsEnabled()) {
            applyMobVariants(entity, isStormy, distFactor);
        }

        // Stat buffs apply to all hostile Monsters.
        if (config.mobBuffEnabled()) {
            applyAttributeBoost(entity, Attribute.GENERIC_MAX_HEALTH,
                    config.mobHealthMultiplier() - 1.0, hpModKey);
            applyAttributeBoost(entity, Attribute.GENERIC_ATTACK_DAMAGE,
                    config.mobAttackMultiplier() - 1.0, atkModKey);

            // Storm buff stacks on top of the base buff.
            if (config.stormBuffEnabled() && isStormy) {
                applyAttributeBoost(entity, Attribute.GENERIC_MAX_HEALTH,
                        config.stormHealthMultiplier() - 1.0, stormHpModKey);
                applyAttributeBoost(entity, Attribute.GENERIC_ATTACK_DAMAGE,
                        config.stormAttackMultiplier() - 1.0, stormAtkModKey);
            }

            // Distance scaling stacks infinitely on top of everything.
            if (config.distanceScalingEnabled() && distFactor > 0.0) {
                applyAttributeBoost(entity, Attribute.GENERIC_MAX_HEALTH,
                        distFactor * (config.distanceScalingHealthMultiplier() - 1.0), distHpModKey);
                applyAttributeBoost(entity, Attribute.GENERIC_ATTACK_DAMAGE,
                        distFactor * (config.distanceScalingAttackMultiplier() - 1.0), distAtkModKey);
            }

            // Top up to the new max — without this, a 20-HP zombie stays
            // at 20 even though its cap just rose to 30.
            AttributeInstance hp = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hp != null) {
                double totalHealthMult = config.mobHealthMultiplier();
                if (config.stormBuffEnabled() && isStormy) {
                    totalHealthMult *= config.stormHealthMultiplier();
                }
                if (distFactor > 0.0) {
                    totalHealthMult *= (1.0 + distFactor * (config.distanceScalingHealthMultiplier() - 1.0));
                }
                entity.setHealth(Math.min(entity.getHealth() * totalHealthMult, hp.getValue()));
            }
        }

        // Gear — armor and weapons with distance-scaled enchantments.
        gearApplier.apply(monster, distance);
    }

    private double horizontalDistanceFromSpawn(LivingEntity entity) {
        World w = entity.getWorld();
        Location spawn = config.distanceScalingUseWorldSpawn()
                ? w.getSpawnLocation()
                : new Location(w, 0.0, 64.0, 0.0);
        double dx = entity.getLocation().getX() - spawn.getX();
        double dz = entity.getLocation().getZ() - spawn.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double computeDistanceFactor(double distance) {
        if (!config.distanceScalingEnabled()) return 0.0;
        double start = config.distanceScalingStart();
        double max   = config.distanceScalingMax();
        if (distance <= start) return 0.0;
        double range = max - start;
        if (range <= 0) return 0.0;
        // No clamp — factor grows linearly forever.
        return (distance - start) / range;
    }

    private void applyRunner(LivingEntity zombie) {
        zombie.getPersistentDataContainer().set(runnerKey, PersistentDataType.BYTE, (byte) 1);

        PotionEffect speed = new PotionEffect(
                PotionEffectType.SPEED,
                Integer.MAX_VALUE,
                config.runnerSpeedAmp(),
                /* ambient */ false,
                /* particles */ false,
                /* icon */ false);
        zombie.addPotionEffect(speed);

        String name = config.runnerName();
        if (name != null && !name.isEmpty()) {
            zombie.customName(Component.text(Text.color(name)));
            zombie.setCustomNameVisible(false);
        }
    }

    private void applyJumper(LivingEntity zombie) {
        zombie.getPersistentDataContainer().set(jumperKey, PersistentDataType.BYTE, (byte) 1);

        PotionEffect jump = new PotionEffect(
                PotionEffectType.JUMP_BOOST,
                Integer.MAX_VALUE,
                config.jumperJumpAmp(),
                /* ambient */ false,
                /* particles */ false,
                /* icon */ false);
        zombie.addPotionEffect(jump);

        String name = config.jumperName();
        if (name != null && !name.isEmpty()) {
            // If already named (runner), append the jumper name.
            Component existing = zombie.customName();
            if (existing != null) {
                zombie.customName(existing.append(Component.text(Text.color(" &2Jumper"))));
            } else {
                zombie.customName(Component.text(Text.color(name)));
            }
            zombie.setCustomNameVisible(false);
        }
    }

    private void applySpiderBoost(Spider spider) {
        // Small speed boost so spiders close distance faster.
        PotionEffect speed = new PotionEffect(
                PotionEffectType.SPEED,
                Integer.MAX_VALUE,
                0,
                false, false, false);
        spider.addPotionEffect(speed);

        // Increase follow range so they don't give up the chase.
        AttributeInstance follow = spider.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
        if (follow != null) {
            double base = follow.getBaseValue();
            if (base < 48.0) {
                follow.setBaseValue(48.0);
            }
        }
    }

    private void applySkeletonBoost(Skeleton skeleton) {
        // Skeletons track players from further away.
        AttributeInstance follow = skeleton.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
        if (follow != null) {
            double base = follow.getBaseValue();
            if (base < 40.0) {
                follow.setBaseValue(40.0);
            }
        }
    }

    private void applyMobVariants(LivingEntity entity, boolean isStormy, double distFactor) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double chanceMult = 1.0;
        if (config.variantDistanceScalingEnabled() && distFactor > 0.0) {
            chanceMult = 1.0 + distFactor * (config.variantDistanceChanceMultiplier() - 1.0);
        }

        // 1. Skeleton Marksman — during thunderstorms
        if (isStormy && entity instanceof Skeleton s) {
            NightfallConfig.VariantEntry v = config.variant("skeleton-marksman");
            if (v != null && v.enabled && rng.nextDouble() < scaledChance(v.chance, chanceMult)) {
                entity.getPersistentDataContainer().set(marksmanKey, PersistentDataType.BYTE, (byte) 1);
                PotionEffect speed = new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false, false);
                s.addPotionEffect(speed);
                AttributeInstance follow = s.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
                if (follow != null && follow.getBaseValue() < 64.0) follow.setBaseValue(64.0);
                name(entity, v.name);
            }
        }

        // 2. Desert Zombie — Husks only
        if (entity instanceof Husk h) {
            NightfallConfig.VariantEntry v = config.variant("desert-zombie");
            if (v != null && v.enabled && rng.nextDouble() < scaledChance(v.chance, chanceMult)) {
                entity.getPersistentDataContainer().set(desertKey, PersistentDataType.BYTE, (byte) 1);
                PotionEffect fireRes = new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false, false);
                h.addPotionEffect(fireRes);
                name(entity, v.name);
            }
        }

        // 3. Venomous Spider
        if (entity instanceof Spider s) {
            NightfallConfig.VariantEntry v = config.variant("venomous-spider");
            if (v != null && v.enabled && rng.nextDouble() < scaledChance(v.chance, chanceMult)) {
                entity.getPersistentDataContainer().set(venomousKey, PersistentDataType.BYTE, (byte) 1);
                PotionEffect speed = new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false, false);
                s.addPotionEffect(speed);
                name(entity, v.name);
            }
        }

        // 4. Witch Doctor
        if (entity instanceof Witch w) {
            NightfallConfig.VariantEntry v = config.variant("witch-doctor");
            if (v != null && v.enabled && rng.nextDouble() < scaledChance(v.chance, chanceMult)) {
                entity.getPersistentDataContainer().set(witchDoctorKey, PersistentDataType.BYTE, (byte) 1);
                PotionEffect speed = new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false, false);
                w.addPotionEffect(speed);
                name(entity, v.name);
            }
        }

        // 5. Brute Creeper
        if (entity instanceof Creeper c) {
            NightfallConfig.VariantEntry v = config.variant("brute-creeper");
            if (v != null && v.enabled && rng.nextDouble() < scaledChance(v.chance, chanceMult)) {
                entity.getPersistentDataContainer().set(bruteKey, PersistentDataType.BYTE, (byte) 1);
                entity.getPersistentDataContainer().set(siegeKey, PersistentDataType.BYTE, (byte) 1);
                c.setExplosionRadius(5);
                c.setFuseTicks(20);
                PotionEffect speed = new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false, false);
                c.addPotionEffect(speed);
                name(entity, v.name);
            }
        }

        // 6. Wither Reaper
        if (entity instanceof WitherSkeleton ws) {
            NightfallConfig.VariantEntry v = config.variant("wither-reaper");
            if (v != null && v.enabled && rng.nextDouble() < scaledChance(v.chance, chanceMult)) {
                entity.getPersistentDataContainer().set(witherReaperKey, PersistentDataType.BYTE, (byte) 1);
                entity.getPersistentDataContainer().set(siegeKey, PersistentDataType.BYTE, (byte) 1);
                applyAttributeBoost(ws, Attribute.GENERIC_ATTACK_DAMAGE, 0.40, witherReaperKey);
                name(entity, v.name);
            }
        }

        // 7. Frozen Stray
        if (entity instanceof Stray st) {
            NightfallConfig.VariantEntry v = config.variant("frozen-stray");
            if (v != null && v.enabled && rng.nextDouble() < scaledChance(v.chance, chanceMult)) {
                entity.getPersistentDataContainer().set(frozenKey, PersistentDataType.BYTE, (byte) 1);
                PotionEffect resist = new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, false, false);
                st.addPotionEffect(resist);
                applyAttributeBoost(st, Attribute.GENERIC_MAX_HEALTH, 0.50, frozenKey);
                AttributeInstance hp = st.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (hp != null) st.setHealth(hp.getValue());
                name(entity, v.name);
            }
        }

        // 8. Ender Stalker
        if (entity instanceof Enderman em) {
            NightfallConfig.VariantEntry v = config.variant("ender-stalker");
            if (v != null && v.enabled && rng.nextDouble() < scaledChance(v.chance, chanceMult)) {
                entity.getPersistentDataContainer().set(enderStalkerKey, PersistentDataType.BYTE, (byte) 1);
                PotionEffect speed = new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false, false);
                em.addPotionEffect(speed);
                AttributeInstance follow = em.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
                if (follow != null && follow.getBaseValue() < 60.0) follow.setBaseValue(60.0);
                name(entity, v.name);
            }
        }

        // 9. Plague Zombie — plain zombies only (no husk/drowned)
        if (entity instanceof Zombie z && !(z instanceof Husk) && !(z instanceof Drowned)) {
            NightfallConfig.VariantEntry v = config.variant("plague-zombie");
            if (v != null && v.enabled && rng.nextDouble() < scaledChance(v.chance, chanceMult)) {
                entity.getPersistentDataContainer().set(plagueKey, PersistentDataType.BYTE, (byte) 1);
                // Slow themselves down but infect on hit
                PotionEffect slow = new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 0, false, false, false);
                z.addPotionEffect(slow);
                name(entity, v.name);
            }
        }

        // 10. Vindicator Berserker
        if (entity instanceof Vindicator vin) {
            NightfallConfig.VariantEntry v = config.variant("vindicator-berserker");
            if (v != null && v.enabled && rng.nextDouble() < scaledChance(v.chance, chanceMult)) {
                entity.getPersistentDataContainer().set(berserkerKey, PersistentDataType.BYTE, (byte) 1);
                entity.getPersistentDataContainer().set(siegeKey, PersistentDataType.BYTE, (byte) 1);
                name(entity, v.name);
            }
        }

        // 11. Phantom Diver
        if (entity instanceof Phantom ph) {
            NightfallConfig.VariantEntry v = config.variant("phantom-diver");
            if (v != null && v.enabled && rng.nextDouble() < scaledChance(v.chance, chanceMult)) {
                entity.getPersistentDataContainer().set(phantomDiverKey, PersistentDataType.BYTE, (byte) 1);
                PotionEffect speed = new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false, false);
                ph.addPotionEffect(speed);
                name(entity, v.name);
            }
        }

        // 12. Pillager Sniper
        if (entity instanceof Pillager p) {
            NightfallConfig.VariantEntry v = config.variant("pillager-sniper");
            if (v != null && v.enabled && rng.nextDouble() < scaledChance(v.chance, chanceMult)) {
                entity.getPersistentDataContainer().set(pillagerSniperKey, PersistentDataType.BYTE, (byte) 1);
                AttributeInstance follow = p.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
                if (follow != null && follow.getBaseValue() < 80.0) follow.setBaseValue(80.0);
                EntityEquipment eq = p.getEquipment();
                if (eq != null) {
                    ItemStack crossbow = new ItemStack(org.bukkit.Material.CROSSBOW);
                    ItemMeta meta = crossbow.getItemMeta();
                    if (meta != null) {
                        meta.addEnchant(org.bukkit.enchantments.Enchantment.QUICK_CHARGE, 3, true);
                        meta.addEnchant(org.bukkit.enchantments.Enchantment.PIERCING, 2, true);
                        crossbow.setItemMeta(meta);
                    }
                    eq.setItemInMainHand(crossbow);
                    eq.setItemInMainHandDropChance(0.0f);
                }
                name(entity, v.name);
            }
        }

        // 13. Siege Zombie — plain zombies only
        if (entity instanceof Zombie z && !(z instanceof Husk) && !(z instanceof Drowned)) {
            NightfallConfig.VariantEntry v = config.variant("siege-zombie");
            if (v != null && v.enabled && rng.nextDouble() < scaledChance(v.chance, chanceMult)) {
                entity.getPersistentDataContainer().set(siegeKey, PersistentDataType.BYTE, (byte) 1);
                PotionEffect speed = new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false, false);
                z.addPotionEffect(speed);
                applyAttributeBoost(z, Attribute.GENERIC_ATTACK_DAMAGE, 0.25, siegeKey);
                name(entity, v.name);
            }
        }

        // 14. Flash Creeper — nearly instant boom
        if (entity instanceof Creeper c) {
            NightfallConfig.VariantEntry v = config.variant("flash-creeper");
            if (v != null && v.enabled && rng.nextDouble() < scaledChance(v.chance, chanceMult)) {
                entity.getPersistentDataContainer().set(flashCreeperKey, PersistentDataType.BYTE, (byte) 1);
                entity.getPersistentDataContainer().set(siegeKey, PersistentDataType.BYTE, (byte) 1);
                c.setFuseTicks(5);   // 0.25 s — impossible to kite
                c.setExplosionRadius(3);
                PotionEffect speed = new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2, false, false, false);
                c.addPotionEffect(speed);
                name(entity, v.name);
            }
        }

        // 15. Splitter Creeper
        if (entity instanceof Creeper c) {
            NightfallConfig.VariantEntry v = config.variant("splitter-creeper");
            if (v != null && v.enabled && rng.nextDouble() < scaledChance(v.chance, chanceMult)) {
                entity.getPersistentDataContainer().set(splitterCreeperKey, PersistentDataType.BYTE, (byte) 1);
                name(entity, v.name);
            }
        }

        // 16. Volatile Creeper — explodes on death
        if (entity instanceof Creeper c) {
            NightfallConfig.VariantEntry v = config.variant("volatile-creeper");
            if (v != null && v.enabled && rng.nextDouble() < scaledChance(v.chance, chanceMult)) {
                entity.getPersistentDataContainer().set(volatileCreeperKey, PersistentDataType.BYTE, (byte) 1);
                entity.getPersistentDataContainer().set(siegeKey, PersistentDataType.BYTE, (byte) 1);
                c.setExplosionRadius(4);
                name(entity, v.name);
            }
        }

        // 17. Web Weaver — spider variant
        if (entity instanceof Spider s) {
            NightfallConfig.VariantEntry v = config.variant("web-weaver");
            if (v != null && v.enabled && rng.nextDouble() < scaledChance(v.chance, chanceMult)) {
                entity.getPersistentDataContainer().set(webWeaverKey, PersistentDataType.BYTE, (byte) 1);
                PotionEffect speed = new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false, false);
                s.addPotionEffect(speed);
                name(entity, v.name);
            }
        }

        // 18. Pyro Skeleton
        if (entity instanceof Skeleton s) {
            NightfallConfig.VariantEntry v = config.variant("pyro-skeleton");
            if (v != null && v.enabled && rng.nextDouble() < scaledChance(v.chance, chanceMult)) {
                entity.getPersistentDataContainer().set(pyroSkeletonKey, PersistentDataType.BYTE, (byte) 1);
                name(entity, v.name);
            }
        }

        // 19. Bomber Skeleton
        if (entity instanceof Skeleton s) {
            NightfallConfig.VariantEntry v = config.variant("bomber-skeleton");
            if (v != null && v.enabled && rng.nextDouble() < scaledChance(v.chance, chanceMult)) {
                entity.getPersistentDataContainer().set(bomberSkeletonKey, PersistentDataType.BYTE, (byte) 1);
                name(entity, v.name);
            }
        }

        // 20. Blaze Archer
        if (entity instanceof Skeleton s) {
            NightfallConfig.VariantEntry v = config.variant("blaze-archer");
            if (v != null && v.enabled && rng.nextDouble() < scaledChance(v.chance, chanceMult)) {
                entity.getPersistentDataContainer().set(blazeArcherKey, PersistentDataType.BYTE, (byte) 1);
                name(entity, v.name);
            }
        }

        // 21. Swarm Zombie — spawns multiple zombies at once
        if (entity instanceof Zombie z && !(z instanceof Husk) && !(z instanceof Drowned)) {
            NightfallConfig.VariantEntry v = config.variant("swarm-zombie");
            if (v != null && v.enabled && rng.nextDouble() < scaledChance(v.chance, chanceMult)) {
                entity.getPersistentDataContainer().set(swarmZombieKey, PersistentDataType.BYTE, (byte) 1);
                name(entity, v.name);
                // Spawn 2-4 additional zombies nearby (they will trigger CreatureSpawnEvent with CUSTOM reason)
                World w = z.getWorld();
                Location origin = z.getLocation();
                int extras = 2 + rng.nextInt(3); // 2 to 4
                for (int i = 0; i < extras; i++) {
                    double ox = (rng.nextDouble() - 0.5) * 3.0;
                    double oz = (rng.nextDouble() - 0.5) * 3.0;
                    Location spawnLoc = origin.clone().add(ox, 0.5, oz);
                    w.spawn(spawnLoc, Zombie.class, CreatureSpawnEvent.SpawnReason.CUSTOM, null);
                }
            }
        }
    }

    private static double scaledChance(double base, double mult) {
        return Math.min(1.0, base * mult);
    }

    private void name(LivingEntity entity, String name) {
        if (name == null || name.isEmpty()) return;
        Component existing = entity.customName();
        if (existing != null) {
            entity.customName(existing.append(Component.text(Text.color(" &r" + name))));
        } else {
            entity.customName(Component.text(Text.color(name)));
        }
        entity.setCustomNameVisible(false);
    }

    /**
     * Adds a MULTIPLY_SCALAR_1 modifier to the given attribute. Operation
     * semantics: {@code final = base * (1 + sum(modifiers))}. So passing
     * {@code amount = 0.5} produces +50%.
     *
     * Uses a fixed NamespacedKey per modifier type. AttributeModifiers
     * are persisted in entity NBT, so a unique-per-spawn key would
     * silently bloat NBT over thousands of mob spawns. The
     * {@code buffedKey} PDC tag (set by {@link #onSpawn}) prevents
     * double-application within a single entity's lifetime; if a future
     * Paper version still ends up duplicating, we remove the existing
     * modifier with the same key first to stay defensive.
     */
    private void applyAttributeBoost(LivingEntity entity, Attribute attribute, double amount, NamespacedKey key) {
        if (amount == 0.0) return;
        AttributeInstance inst = entity.getAttribute(attribute);
        if (inst == null) return;

        // Defensive: if a modifier with the same key is already present
        // (chunk-reload weirdness, or a foreign plugin colliding on the
        // namespace), drop it before adding ours. Iterate a snapshot —
        // AttributeInstance.getModifiers() returns the live backing
        // collection on Paper, and removeModifier mutates it.
        List<AttributeModifier> snapshot = new ArrayList<>(inst.getModifiers());
        for (AttributeModifier existing : snapshot) {
            if (key.equals(existing.getKey())) {
                inst.removeModifier(existing);
            }
        }

        AttributeModifier mod = new AttributeModifier(
                key, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        inst.addModifier(mod);
    }

    public NamespacedKey runnerKey() { return runnerKey; }
    public NamespacedKey jumperKey() { return jumperKey; }
    public NamespacedKey buffedKey() { return buffedKey; }

    public NamespacedKey marksmanKey()    { return marksmanKey; }
    public NamespacedKey desertKey()      { return desertKey; }
    public NamespacedKey venomousKey()    { return venomousKey; }
    public NamespacedKey witchDoctorKey() { return witchDoctorKey; }
    public NamespacedKey bruteKey()       { return bruteKey; }
    public NamespacedKey witherReaperKey(){ return witherReaperKey; }
    public NamespacedKey frozenKey()      { return frozenKey; }
    public NamespacedKey enderStalkerKey(){ return enderStalkerKey; }
    public NamespacedKey plagueKey()      { return plagueKey; }
    public NamespacedKey berserkerKey()   { return berserkerKey; }
    public NamespacedKey phantomDiverKey(){ return phantomDiverKey; }
    public NamespacedKey pillagerSniperKey(){ return pillagerSniperKey; }
    public NamespacedKey siegeKey()         { return siegeKey; }
    public NamespacedKey flashCreeperKey()  { return flashCreeperKey; }
    public NamespacedKey splitterCreeperKey(){ return splitterCreeperKey; }
    public NamespacedKey volatileCreeperKey(){ return volatileCreeperKey; }
    public NamespacedKey webWeaverKey()     { return webWeaverKey; }
    public NamespacedKey pyroSkeletonKey()  { return pyroSkeletonKey; }
    public NamespacedKey bomberSkeletonKey(){ return bomberSkeletonKey; }
    public NamespacedKey blazeArcherKey()   { return blazeArcherKey; }
    public NamespacedKey swarmZombieKey()   { return swarmZombieKey; }
}
