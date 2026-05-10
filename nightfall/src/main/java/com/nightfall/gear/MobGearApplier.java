package com.nightfall.gear;

import com.nightfall.NightfallConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Equips night mobs with armor and weapons that scale in tier and
 * enchantment level with distance from spawn. Enchantments can exceed
 * vanilla limits (e.g. Sharpness X, Protection XV) depending on config.
 */
public final class MobGearApplier {

    private static final String[] ARMOR_TIERS = {
            "LEATHER", "CHAINMAIL", "IRON", "GOLDEN", "DIAMOND", "NETHERITE"
    };
    private static final String[] WEAPON_TIERS = {
            "WOODEN", "STONE", "IRON", "GOLDEN", "DIAMOND", "NETHERITE"
    };

    private final NightfallConfig config;

    public MobGearApplier(NightfallConfig config) {
        this.config = config;
    }

    public void apply(Monster monster, double distance) {
        if (!config.mobGearEnabled()) return;

        double chance = config.mobGearBaseChance()
                + (distance / 1000.0) * config.mobGearChancePer1000();
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;

        int tier = pickTier(distance);
        int enchantLevel = Math.max(1, (int) (distance / 2000.0))
                + ThreadLocalRandom.current().nextInt(0, 3);

        EntityEquipment eq = monster.getEquipment();
        if (eq == null) return;

        float dropChance = (float) config.mobGearDropChance();

        // Helmet
        if (ThreadLocalRandom.current().nextDouble() < 0.80) {
            ItemStack item = makeArmor(tier, "HELMET", enchantLevel);
            eq.setHelmet(item);
            eq.setHelmetDropChance(dropChance);
        }
        // Chestplate
        if (ThreadLocalRandom.current().nextDouble() < 0.70) {
            ItemStack item = makeArmor(tier, "CHESTPLATE", enchantLevel);
            eq.setChestplate(item);
            eq.setChestplateDropChance(dropChance);
        }
        // Leggings
        if (ThreadLocalRandom.current().nextDouble() < 0.60) {
            ItemStack item = makeArmor(tier, "LEGGINGS", enchantLevel);
            eq.setLeggings(item);
            eq.setLeggingsDropChance(dropChance);
        }
        // Boots
        if (ThreadLocalRandom.current().nextDouble() < 0.80) {
            ItemStack item = makeArmor(tier, "BOOTS", enchantLevel);
            eq.setBoots(item);
            eq.setBootsDropChance(dropChance);
        }

        // Weapon
        ItemStack weapon = makeWeapon(monster, tier, enchantLevel);
        if (weapon != null) {
            eq.setItemInMainHand(weapon);
            eq.setItemInMainHandDropChance(dropChance);
        }
    }

    private int pickTier(double distance) {
        double roll = ThreadLocalRandom.current().nextDouble() + (distance / 4000.0);
        if (roll < 1.5) return 0;
        if (roll < 2.5) return 1;
        if (roll < 3.5) return 2;
        if (roll < 4.5) return 3;
        if (roll < 5.5) return 4;
        return 5;
    }

    private ItemStack makeArmor(int tier, String slot, int enchantLevel) {
        String base = ARMOR_TIERS[Math.min(tier, ARMOR_TIERS.length - 1)];
        Material mat = Material.valueOf(base + "_" + slot);
        ItemStack item = new ItemStack(mat);
        enchantArmor(item, enchantLevel);
        return item;
    }

    private ItemStack makeWeapon(Monster monster, int tier, int enchantLevel) {
        if (monster instanceof Skeleton) {
            ItemStack bow = new ItemStack(Material.BOW);
            enchantBow(bow, enchantLevel);
            return bow;
        }
        if (monster instanceof Zombie) {
            String base = WEAPON_TIERS[Math.min(tier, WEAPON_TIERS.length - 1)];
            Material mat = Material.valueOf(base + "_SWORD");
            ItemStack sword = new ItemStack(mat);
            enchantSword(sword, enchantLevel);
            return sword;
        }
        return null;
    }

    private void enchantArmor(ItemStack item, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        boolean overlevel = config.mobGearAllowOverlevel();
        meta.addEnchant(Enchantment.PROTECTION, level, overlevel);
        meta.addEnchant(Enchantment.UNBREAKING, Math.max(1, level / 2), overlevel);
        if (ThreadLocalRandom.current().nextDouble() < 0.30) {
            meta.addEnchant(Enchantment.THORNS, Math.max(1, level / 3), overlevel);
        }
        item.setItemMeta(meta);
    }

    private void enchantSword(ItemStack item, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        boolean overlevel = config.mobGearAllowOverlevel();
        meta.addEnchant(Enchantment.SHARPNESS, level, overlevel);
        meta.addEnchant(Enchantment.UNBREAKING, Math.max(1, level / 2), overlevel);
        if (ThreadLocalRandom.current().nextDouble() < 0.30) {
            meta.addEnchant(Enchantment.FIRE_ASPECT, Math.max(1, level / 3), overlevel);
        }
        if (ThreadLocalRandom.current().nextDouble() < 0.20) {
            meta.addEnchant(Enchantment.KNOCKBACK, Math.max(1, level / 4), overlevel);
        }
        if (ThreadLocalRandom.current().nextDouble() < 0.20) {
            meta.addEnchant(Enchantment.LOOTING, Math.max(1, level / 3), overlevel);
        }
        item.setItemMeta(meta);
    }

    private void enchantBow(ItemStack item, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        boolean overlevel = config.mobGearAllowOverlevel();
        meta.addEnchant(Enchantment.POWER, level, overlevel);
        meta.addEnchant(Enchantment.UNBREAKING, Math.max(1, level / 2), overlevel);
        if (ThreadLocalRandom.current().nextDouble() < 0.30) {
            meta.addEnchant(Enchantment.PUNCH, Math.max(1, level / 4), overlevel);
        }
        if (ThreadLocalRandom.current().nextDouble() < 0.25) {
            meta.addEnchant(Enchantment.FLAME, 1, overlevel);
        }
        if (ThreadLocalRandom.current().nextDouble() < 0.15) {
            meta.addEnchant(Enchantment.INFINITY, 1, overlevel);
        }
        item.setItemMeta(meta);
    }
}
