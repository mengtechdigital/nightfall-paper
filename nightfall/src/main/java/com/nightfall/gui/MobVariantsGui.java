package com.nightfall.gui;

import com.nightfall.NightfallConfig;
import com.nightfall.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI that displays all Nightfall mob variants with descriptions.
 */
public final class MobVariantsGui implements InventoryHolder {

    private static final String GUI_TITLE = Text.color("&8[&5Nightfall&8] &dMob Variants");
    private static final int SIZE = 54;

    private final Inventory inventory;

    public MobVariantsGui(NightfallConfig config) {
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text(GUI_TITLE));
        populate(config);
    }

    private void populate(NightfallConfig config) {
        int slot = 0;

        slot = addVariant(slot, Material.BONE, config.variant("skeleton-marksman"),
                "&bSkeleton Marksman",
                "&7Spawns during thunderstorms.",
                "&7Shoots arrows at twice the normal",
                "&7speed and tracks players from",
                "&7much further away.");

        slot = addVariant(slot, Material.SAND, config.variant("desert-zombie"),
                "&6Desert Zombie",
                "&7A scorched husk with fire resistance.",
                "&7Hits ignite the target for 4s and",
                "&7inflict Hunger III.");

        slot = addVariant(slot, Material.SPIDER_EYE, config.variant("venomous-spider"),
                "&2Venomous Spider",
                "&7A spider dripping with venom.",
                "&7Bites apply Poison II for 5s.");

        slot = addVariant(slot, Material.BREWING_STAND, config.variant("witch-doctor"),
                "&5Witch Doctor",
                "&7A swift witch that radiates healing.",
                "&7Grants Regeneration to nearby",
                "&7hostile mobs every 2 seconds.");

        slot = addVariant(slot, Material.GUNPOWDER, config.variant("brute-creeper"),
                "&4Brute Creeper",
                "&7A faster creeper with a short fuse.",
                "&7Explosion radius is 5 blocks and",
                "&7fuse is only 1 second.");

        slot = addVariant(slot, Material.WITHER_SKELETON_SKULL, config.variant("wither-reaper"),
                "&8Wither Reaper",
                "&7A wither skeleton of immense damage.",
                "&7Hits apply Wither III for 6s and",
                "&7deal +40% attack damage.");

        slot = addVariant(slot, Material.SNOWBALL, config.variant("frozen-stray"),
                "&3Frozen Stray",
                "&7A hardy stray from the frozen wastes.",
                "&7Has +50% max health, Resistance,",
                "&7and arrows inflict Slowness II.");

        slot = addVariant(slot, Material.ENDER_PEARL, config.variant("ender-stalker"),
                "&5Ender Stalker",
                "&7An enderman that never lets go.",
                "&7Teleports aggressively toward players",
                "&7and tracks them from 60 blocks away.");

        slot = addVariant(slot, Material.POISONOUS_POTATO, config.variant("plague-zombie"),
                "&aPlague Zombie",
                "&7A diseased carrier of the plague.",
                "&7Hits inflict Weakness II and Nausea.",
                "&7Moves slower than normal zombies.");

        slot = addVariant(slot, Material.IRON_AXE, config.variant("vindicator-berserker"),
                "&cVindicator Berserker",
                "&7A vindicator that rages when wounded.",
                "&7Below 50% health it gains Strength II",
                "&7and Speed II until death.");

        slot = addVariant(slot, Material.PHANTOM_MEMBRANE, config.variant("phantom-diver"),
                "&9Phantom Diver",
                "&7A swift phantom that strikes from above.",
                "&7Dives faster and blinds targets for 3s.");

        slot = addVariant(slot, Material.CROSSBOW, config.variant("pillager-sniper"),
                "&7Pillager Sniper",
                "&7A pillager armed with a tuned crossbow.",
                "&7Tracks players from 80 blocks away.",
                "&7Arrows pierce armor and deal heavy",
                "&7knockback.");

        slot = addVariant(slot, Material.IRON_BLOCK, config.variant("siege-zombie"),
                "&4Siege Zombie",
                "&7A zombie that breaks through walls",
                "&7and digs underground to find players.",
                "&7Cannot break iron blocks.");

        slot = addVariant(slot, Material.TNT, config.variant("flash-creeper"),
                "&cFlash Creeper",
                "&7Explodes in 0.25 seconds — impossible",
                "&7to kite. Moves at extreme speed.",
                "&7Also breaks blocks.");

        slot = addVariant(slot, Material.CREEPER_SPAWN_EGG, config.variant("splitter-creeper"),
                "&eSplitter Creeper",
                "&7Spawns 3 baby creepers on death.",
                "&7Each baby has a short fuse and",
                "&7small explosion radius.");

        slot = addVariant(slot, Material.CREEPER_HEAD, config.variant("volatile-creeper"),
                "&8Volatile Creeper",
                "&7Explodes on death even if killed",
                "&7before detonating. Breaks blocks.",
                "&7Be careful when engaging up close.");

        slot = addVariant(slot, Material.COBWEB, config.variant("web-weaver"),
                "&8Web Weaver",
                "&7A spider that shoots cobwebs at",
                "&7players, trapping them in place.",
                "&7Moves faster than normal spiders.");

        slot = addVariant(slot, Material.FIRE_CHARGE, config.variant("pyro-skeleton"),
                "&6Pyro Skeleton",
                "&7Shoots small fireballs instead of",
                "&7arrows. Sets terrain and players",
                "&7on fire.");

        slot = addVariant(slot, Material.TNT_MINECART, config.variant("bomber-skeleton"),
                "&cBomber Skeleton",
                "&7Arrows spawn primed TNT on impact.",
                "&7Great for destroying fortifications",
                "&7and flushing players out.");

        slot = addVariant(slot, Material.BLAZE_POWDER, config.variant("blaze-archer"),
                "&eBlaze Archer",
                "&7Shoots fast blaze fireballs that",
                "&7deal heavy fire damage and set",
                "&7blocks ablaze.");

        slot = addVariant(slot, Material.ZOMBIE_HEAD, config.variant("swarm-zombie"),
                "&2Swarm Zombie",
                "&7Spawns as a group of 3-5 zombies",
                "&7instead of a single mob.",
                "&7Overwhelms lone players quickly.");

        // Fill empty slots with glass panes
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) {
            fm.displayName(Component.text(Text.color(" ")));
            fm.setHideTooltip(true);
            filler.setItemMeta(fm);
        }
        for (int i = 0; i < SIZE; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    private int addVariant(int slot, Material icon, NightfallConfig.VariantEntry entry,
                           String title, String... loreLines) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            boolean enabled = entry != null && entry.enabled;
            String status = enabled ? "&a&lENABLED" : "&c&lDISABLED";
            meta.displayName(Component.text(Text.color(title)));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(Text.color("&7Status: " + status)));
            if (entry != null) {
                lore.add(Component.text(Text.color("&7Chance: &f" + Math.round(entry.chance * 100) + "%")));
            }
            lore.add(Component.text(Text.color(" ")));
            for (String line : loreLines) {
                lore.add(Component.text(Text.color(line)));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        inventory.setItem(slot, item);
        return slot + 1;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
