package com.nightfall.dummy;

import com.nightfall.NightfallConfig;
import com.nightfall.time.TimeController;
import com.nightfall.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CombatLogX-style logout dummy. If a player disconnects during a
 * managed-world night, a placeholder zombie wearing their gear stays in
 * the world. If it's killed before they rejoin, they're "killed on
 * rejoin": teleported to the death spot, inventory dropped, XP cleared,
 * health set to 0 to trigger the normal death pipeline.
 *
 * Persistence:
 *   - Live dummy ↔ player mapping is in-memory (worth a restart loss
 *     since dummies are PERSISTENT and can be re-discovered by scanning
 *     entities for the owner PDC tag at join time).
 *   - Pending kill marks survive restart in pending-kills.yml.
 */
public final class LogoutDummyManager {

    private final Plugin plugin;
    private final NightfallConfig config;
    /** Shared with NightMobListener — pre-tagged on dummies to skip night buffs. */
    private final NamespacedKey buffedKey;
    /** Tag on the dummy entity carrying the owner player's UUID as a string. */
    private final NamespacedKey dummyKey;

    private final File dataFile;
    private FileConfiguration data;

    /** Player UUID → dummy entity UUID (for fast cleanup on rejoin). */
    private final Map<UUID, UUID> liveDummyByPlayer = new HashMap<>();

    public LogoutDummyManager(Plugin plugin, NightfallConfig config, NamespacedKey buffedKey) {
        this.plugin = plugin;
        this.config = config;
        this.buffedKey = buffedKey;
        this.dummyKey = new NamespacedKey(plugin, "logout_dummy_owner");
        this.dataFile = new File(plugin.getDataFolder(), "pending-kills.yml");
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        this.data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public NamespacedKey dummyKey() { return dummyKey; }

    /** True if {@code entity} is one of our logout dummies. */
    public boolean isDummy(Entity entity) {
        return entity.getPersistentDataContainer().has(dummyKey, PersistentDataType.STRING);
    }

    /** Spawn a dummy if all conditions are met. No-op otherwise. */
    public void onQuit(Player p) {
        if (!config.dummyEnabled()) return;
        if (!config.managesWorld(p.getWorld().getName())) return;
        if (!TimeController.isNight(p.getWorld())) return;
        if (p.isDead()) return;
        if (p.isSleeping()) return; // Sleeping in a bed exempts from the combat-log penalty.
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;

        // Defense in depth: if a stale dummy is already tracked for this
        // player (server restart left an orphan, or a previous quit's
        // dummy never died), purge it before spawning a new one.
        // Otherwise their gear from the prior dummy is silently lost.
        removeExistingDummy(p.getUniqueId());

        World w = p.getWorld();
        Location loc = p.getLocation();
        UUID ownerId = p.getUniqueId();
        String ownerIdStr = ownerId.toString();
        String ownerName = p.getName();

        // Capture state synchronously — we can't read off the Player after
        // the quit event completes.
        AttributeInstance pHp = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHp     = pHp != null ? pHp.getValue() : 20.0;
        double currentHp = Math.max(1.0, p.getHealth());
        ItemStack helm  = clone(p.getInventory().getHelmet());
        ItemStack chest = clone(p.getInventory().getChestplate());
        ItemStack legs  = clone(p.getInventory().getLeggings());
        ItemStack boots = clone(p.getInventory().getBoots());
        ItemStack mh    = clone(p.getInventory().getItemInMainHand());
        ItemStack oh    = clone(p.getInventory().getItemInOffHand());

        Zombie dummy = w.spawn(loc, Zombie.class, CreatureSpawnEvent.SpawnReason.CUSTOM, z -> {
            // Pre-tagging buffedKey makes NightMobListener early-return,
            // so the dummy doesn't accidentally get the runner buff or
            // the night attribute multipliers.
            z.getPersistentDataContainer().set(buffedKey, PersistentDataType.BYTE, (byte) 1);
            z.getPersistentDataContainer().set(dummyKey, PersistentDataType.STRING, ownerIdStr);

            z.setPersistent(true);
            z.setRemoveWhenFarAway(false);
            z.setShouldBurnInDay(false);
            z.addPotionEffect(new PotionEffect(
                    PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false, false));

            String tmpl = config.dummyName();
            if (tmpl != null && !tmpl.isEmpty()) {
                z.customName(Component.text(Text.color(tmpl.replace("{player}", ownerName))));
                z.setCustomNameVisible(true);
            }

            AttributeInstance hp = z.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hp != null) {
                hp.setBaseValue(Math.max(1.0, maxHp));
                z.setHealth(Math.min(currentHp, hp.getValue()));
            } else {
                z.setHealth(Math.min(currentHp, 20.0));
            }

            EntityEquipment eq = z.getEquipment();
            if (eq != null) {
                eq.setHelmet(helm);
                eq.setChestplate(chest);
                eq.setLeggings(legs);
                eq.setBoots(boots);
                eq.setItemInMainHand(mh);
                eq.setItemInOffHand(oh);
                // No drops from the dummy itself — death drops are handled
                // through the rejoin penalty, not by the zombie loot table.
                eq.setHelmetDropChance(0f);
                eq.setChestplateDropChance(0f);
                eq.setLeggingsDropChance(0f);
                eq.setBootsDropChance(0f);
                eq.setItemInMainHandDropChance(0f);
                eq.setItemInOffHandDropChance(0f);
            }
        });

        if (dummy != null) {
            liveDummyByPlayer.put(ownerId, dummy.getUniqueId());
        }
    }

    /**
     * On join: remove any leftover dummy for this player, then if a kill
     * mark is pending, run the death pipeline a tick later (so the
     * client has finished loading).
     */
    public void onJoin(Player p) {
        UUID id = p.getUniqueId();
        removeExistingDummy(id);

        ConfigurationSection sec = data.getConfigurationSection(id.toString());
        if (sec == null) return;

        String worldName = sec.getString("world");
        double x = sec.getDouble("x");
        double y = sec.getDouble("y");
        double z = sec.getDouble("z");
        String markKey = id.toString();

        // Crash-safety contract: keep the mark on disk until *after* the
        // kill is fully applied. If the server crashes mid-kill, the
        // worst case is a re-run on rejoin, which is idempotent — the
        // inventory drop loop iterates an already-empty inventory, and
        // setHealth(0) on a player that's currently respawning is a
        // no-op on Paper. Inverting the order would risk losing the
        // penalty entirely if a crash hits during the 1-tick delay.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            if (p.isDead()) return; // already dying from another source — bail rather than double-penalize

            // try/finally wraps the entire penalty pipeline. If any step
            // throws, we still clear the mark — better than re-running a
            // partially-applied penalty on every future join. If the
            // pipeline completes cleanly the mark is also cleared.
            World w = worldName != null ? Bukkit.getWorld(worldName) : null;
            Location killAt = w != null ? new Location(w, x, y, z) : p.getLocation();
            try {
                if (w != null) {
                    p.teleport(killAt);
                }

                if (config.dummyDropInventory()) {
                    Location dropAt = p.getLocation();
                    World dropWorld = dropAt.getWorld();
                    if (dropWorld != null) {
                        for (ItemStack item : p.getInventory().getContents()) {
                            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                                dropWorld.dropItemNaturally(dropAt, item);
                            }
                        }
                        for (ItemStack item : p.getInventory().getArmorContents()) {
                            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                                dropWorld.dropItemNaturally(dropAt, item);
                            }
                        }
                    }
                    p.getInventory().clear();
                }

                if (config.dummyClearXp()) {
                    p.setLevel(0);
                    p.setExp(0f);
                    p.setTotalExperience(0);
                }

                // Kill via setHealth — fires PlayerDeathEvent and respawn flow.
                p.setHealth(0.0);
            } finally {
                data.set(markKey, null);
                saveData();
            }
        }, 1L);
    }

    /** Called from the listener when an entity dies that {@link #isDummy} returned true for. */
    public void onDummyDeath(Entity dummy) {
        String ownerStr = dummy.getPersistentDataContainer().get(dummyKey, PersistentDataType.STRING);
        if (ownerStr == null) return;
        UUID owner;
        try {
            owner = UUID.fromString(ownerStr);
        } catch (IllegalArgumentException ex) {
            return;
        }
        liveDummyByPlayer.remove(owner);

        // If the owner is online when their dummy dies, skip the disk
        // mark — running the rejoin penalty on someone who never logged
        // out would feel like an unrelated PvP kill. The dummy lifetime
        // matched the offline window; if they're online, that window
        // closed.
        if (Bukkit.getPlayer(owner) != null) return;

        Location loc = dummy.getLocation();
        World w = loc.getWorld();
        String key = owner.toString();
        data.set(key + ".world", w != null ? w.getName() : null);
        data.set(key + ".x", loc.getX());
        data.set(key + ".y", loc.getY());
        data.set(key + ".z", loc.getZ());
        data.set(key + ".killedAt", System.currentTimeMillis());
        saveData();
    }

    /** Force-save on shutdown. */
    public void shutdown() {
        saveData();
    }

    /** Re-read pending kills from disk (used by /nightfall reload). */
    public void reloadData() {
        this.data = YamlConfiguration.loadConfiguration(dataFile);
    }

    /**
     * Walk every managed world once at startup and rebuild the in-memory
     * player→dummy index. Avoids an O(n) world scan inside every join
     * event when the in-memory map is otherwise empty post-restart.
     */
    public void reindexLiveDummies() {
        liveDummyByPlayer.clear();
        for (String worldName : config.worlds()) {
            World w = Bukkit.getWorld(worldName);
            if (w == null) continue;
            indexWorld(w);
        }
    }

    /**
     * Single-world reindex — used by the WorldLoadEvent handler so that
     * worlds loaded after server start (Multiverse, etc.) still feed
     * their existing dummies into the in-memory map.
     */
    public void indexWorld(World w) {
        if (!config.managesWorld(w.getName())) return;
        for (Entity e : w.getEntities()) {
            String owner = e.getPersistentDataContainer().get(dummyKey, PersistentDataType.STRING);
            if (owner == null) continue;
            try {
                liveDummyByPlayer.put(UUID.fromString(owner), e.getUniqueId());
            } catch (IllegalArgumentException ignored) {
                // Malformed tag — leave entity alone, admin can /kill it.
            }
        }
    }

    private void removeExistingDummy(UUID playerId) {
        UUID dummyId = liveDummyByPlayer.remove(playerId);
        if (dummyId == null) return; // Map is reindexed at startup, so absence here means truly absent.
        Entity e = Bukkit.getEntity(dummyId);
        if (e != null && !e.isDead()) e.remove();
    }

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save pending-kills.yml: " + ex.getMessage());
        }
    }

    private static ItemStack clone(ItemStack src) {
        return src == null ? null : src.clone();
    }
}
