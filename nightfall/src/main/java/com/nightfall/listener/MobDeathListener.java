package com.nightfall.listener;

import com.nightfall.NightfallConfig;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Rewards players for killing buffed night mobs with bonus XP and a
 * chance at extra loot. Only mobs that carry the Nightfall buffed tag
 * (i.e. natural night spawns and extra spawns) are eligible — spawner
 * farm mobs are excluded.
 */
public final class MobDeathListener implements Listener {

    private final NightfallConfig config;
    private final NamespacedKey buffedKey;

    public MobDeathListener(NightfallConfig config, NamespacedKey buffedKey) {
        this.config = config;
        this.buffedKey = buffedKey;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Monster monster)) return;
        if (!config.managesWorld(monster.getWorld().getName())) return;

        // Only reward for Nightfall-buffed mobs (natural / extra spawns).
        if (!monster.getPersistentDataContainer().has(buffedKey, PersistentDataType.BYTE)) return;

        // Must have been killed by a player (or a pet/projectile owned by a player).
        if (monster.getKiller() == null) return;

        // ---- Bonus XP ----
        double xpMult = config.lootXpMultiplier();
        if (xpMult > 1.0) {
            int baseXp = event.getDroppedExp();
            event.setDroppedExp((int) Math.round(baseXp * xpMult));
        }

        // ---- Extra loot ----
        if (!config.extraLootEnabled() || config.lootTotalWeight() <= 0) return;

        boolean isStormy = monster.getWorld().hasStorm();
        double rollChance = config.extraLootChance();
        if (isStormy) {
            rollChance += config.extraLootStormBonus();
        }

        if (ThreadLocalRandom.current().nextDouble() >= rollChance) return;

        NightfallConfig.LootEntry entry = pickWeightedDrop();
        if (entry == null) return;

        int amount = entry.minAmount;
        if (entry.maxAmount > entry.minAmount) {
            amount += ThreadLocalRandom.current().nextInt(entry.maxAmount - entry.minAmount + 1);
        }

        event.getDrops().add(new ItemStack(entry.material, amount));
    }

    private NightfallConfig.LootEntry pickWeightedDrop() {
        int total = config.lootTotalWeight();
        if (total <= 0) return null;
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (NightfallConfig.LootEntry e : config.lootEntries()) {
            roll -= e.weight;
            if (roll < 0) return e;
        }
        return null; // unreachable given total > 0
    }
}
