package com.nightfall.listener;

import com.nightfall.NightfallConfig;
import com.nightfall.time.TimeController;
import com.nightfall.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Zombie;
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
 *   - Runner: ~runnerChance of zombies get Speed II + custom name.
 *   - Mob buff: hostile mobs get scaled max-health and attack-damage.
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
    /** Marks an entity as already buffed so we never double-stack modifiers. */
    private final NamespacedKey buffedKey;
    /** Marks runner zombies (used by status counts and future tooling). */
    private final NamespacedKey runnerKey;
    /** Fixed modifier keys — see applyAttributeBoost for why these are stable. */
    private final NamespacedKey hpModKey;
    private final NamespacedKey atkModKey;

    public NightMobListener(Plugin plugin, NightfallConfig config) {
        this.config = config;
        this.buffedKey = new NamespacedKey(plugin, "night_buffed");
        this.runnerKey = new NamespacedKey(plugin, "runner");
        this.hpModKey  = new NamespacedKey(plugin, "nf_hp");
        this.atkModKey = new NamespacedKey(plugin, "nf_atk");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!ELIGIBLE_REASONS.contains(event.getSpawnReason())) return;

        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Monster)) return;
        if (!config.managesWorld(entity.getWorld().getName())) return;
        if (!TimeController.isNight(entity.getWorld())) return;

        // Idempotency guard: PDC tag means we've buffed this mob already.
        if (entity.getPersistentDataContainer().has(buffedKey, PersistentDataType.BYTE)) return;
        entity.getPersistentDataContainer().set(buffedKey, PersistentDataType.BYTE, (byte) 1);

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

        // Stat buffs apply to all hostile Monsters.
        if (config.mobBuffEnabled()) {
            applyAttributeBoost(entity, Attribute.GENERIC_MAX_HEALTH,
                    config.mobHealthMultiplier() - 1.0, hpModKey);
            applyAttributeBoost(entity, Attribute.GENERIC_ATTACK_DAMAGE,
                    config.mobAttackMultiplier() - 1.0, atkModKey);

            // Top up to the new max — without this, a 20-HP zombie stays
            // at 20 even though its cap just rose to 30.
            AttributeInstance hp = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hp != null) {
                entity.setHealth(Math.min(entity.getHealth() * config.mobHealthMultiplier(), hp.getValue()));
            }
        }
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
            zombie.setCustomNameVisible(false); // off by default; admins can flip via /summon if they want
        }
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
    public NamespacedKey buffedKey() { return buffedKey; }
}
