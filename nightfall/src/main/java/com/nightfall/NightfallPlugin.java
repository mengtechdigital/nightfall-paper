package com.nightfall;

import com.nightfall.command.NightfallCommand;
import com.nightfall.dummy.LogoutDummyManager;
import com.nightfall.gui.MobVariantsGuiListener;
import com.nightfall.listener.LogoutDummyListener;
import com.nightfall.listener.MobDeathListener;
import com.nightfall.listener.MobDeathVariantListener;
import com.nightfall.listener.MobVariantHitListener;
import com.nightfall.listener.NightMobListener;
import com.nightfall.listener.SkeletonProjectileListener;
import com.nightfall.listener.SleepBlocker;
import com.nightfall.listener.VanillaSpawnBlocker;
import com.nightfall.spawn.EnderStalkerTask;
import com.nightfall.spawn.ExtraSpawnTask;
import com.nightfall.spawn.JumperZombieTask;
import com.nightfall.spawn.MarksmanTask;
import com.nightfall.spawn.MobSiegeTask;
import com.nightfall.spawn.WebWeaverTask;
import com.nightfall.spawn.WitchDoctorTask;
import com.nightfall.time.TimeController;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class NightfallPlugin extends JavaPlugin {

    private NightfallConfig config;
    private TimeController timeController;
    private NightMobListener nightMobListener;
    private LogoutDummyManager dummyManager;
    private ExtraSpawnTask extraSpawnTask;
    private JumperZombieTask jumperTask;
    private MarksmanTask marksmanTask;
    private WitchDoctorTask witchDoctorTask;
    private EnderStalkerTask enderStalkerTask;
    private MobSiegeTask siegeTask;
    private WebWeaverTask webWeaverTask;
    private BukkitTask extraSpawnHandle;

    @Override
    public void onEnable() {
        this.config = new NightfallConfig();
        this.config.load(this);

        this.timeController = new TimeController(config);
        this.timeController.applyWorldRules();
        this.timeController.runTaskTimer(this, 1L, 1L);
        // TimeController extends BukkitRunnable and is scheduled once
        // for the lifetime of the plugin. ExtraSpawnTask uses a plain
        // Runnable so it can be rescheduled on /nightfall reload.

        this.nightMobListener = new NightMobListener(this, config);
        this.dummyManager = new LogoutDummyManager(this, config, nightMobListener.buffedKey());
        this.dummyManager.reindexLiveDummies(); // amortize the post-restart scan to startup, not per-join
        this.extraSpawnTask = new ExtraSpawnTask(this, config);
        this.jumperTask = new JumperZombieTask(this, config, nightMobListener.jumperKey());
        this.jumperTask.start();
        this.marksmanTask = new MarksmanTask(this, config, nightMobListener.marksmanKey());
        this.marksmanTask.start();
        this.witchDoctorTask = new WitchDoctorTask(this, config, nightMobListener.witchDoctorKey());
        this.witchDoctorTask.start();
        this.enderStalkerTask = new EnderStalkerTask(this, config, nightMobListener.enderStalkerKey());
        this.enderStalkerTask.start();
        this.siegeTask = new MobSiegeTask(this, config, nightMobListener.siegeKey());
        this.siegeTask.start();
        this.webWeaverTask = new WebWeaverTask(this, config, nightMobListener.webWeaverKey());
        this.webWeaverTask.start();

        getServer().getPluginManager().registerEvents(nightMobListener, this);
        getServer().getPluginManager().registerEvents(new SleepBlocker(config), this);
        getServer().getPluginManager().registerEvents(new LogoutDummyListener(dummyManager), this);
        getServer().getPluginManager().registerEvents(new MobDeathListener(config, nightMobListener.buffedKey()), this);
        getServer().getPluginManager().registerEvents(new VanillaSpawnBlocker(config), this);
        getServer().getPluginManager().registerEvents(new MobVariantHitListener(config, nightMobListener), this);
        getServer().getPluginManager().registerEvents(new SkeletonProjectileListener(config, nightMobListener), this);
        getServer().getPluginManager().registerEvents(new MobDeathVariantListener(config, nightMobListener), this);
        getServer().getPluginManager().registerEvents(new MobVariantsGuiListener(), this);

        rescheduleExtraSpawnTask();

        NightfallCommand cmd = new NightfallCommand(this, config, timeController, extraSpawnTask);
        PluginCommand pc = getCommand("nightfall");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }

        getLogger().info("Nightfall enabled. Day: " + config.dayMinutes()
                + " min, night: " + config.nightMinutes()
                + " min, worlds: " + config.worlds());
    }

    @Override
    public void onDisable() {
        if (extraSpawnHandle != null) {
            extraSpawnHandle.cancel();
            extraSpawnHandle = null;
        }
        if (timeController != null) {
            timeController.cancel();
        }
        if (jumperTask != null) {
            jumperTask.cancel();
        }
        if (marksmanTask != null) {
            marksmanTask.cancel();
        }
        if (witchDoctorTask != null) {
            witchDoctorTask.cancel();
        }
        if (enderStalkerTask != null) {
            enderStalkerTask.cancel();
        }
        if (siegeTask != null) {
            siegeTask.cancel();
        }
        if (webWeaverTask != null) {
            webWeaverTask.cancel();
        }
        if (dummyManager != null) {
            dummyManager.shutdown();
        }
    }

    /**
     * (Re)start the extra-spawn task with the current configured period.
     * Called on enable and on /nightfall reload.
     */
    public void rescheduleExtraSpawnTask() {
        if (extraSpawnHandle != null) {
            extraSpawnHandle.cancel();
            extraSpawnHandle = null;
        }
        if (!config.extraSpawnEnabled()) return;
        long period = config.extraIntervalSeconds() * 20L;
        extraSpawnHandle = getServer().getScheduler().runTaskTimer(this, extraSpawnTask, period, period);
    }

    public NightfallConfig nightfallConfig()  { return config; }
    public TimeController timeController()    { return timeController; }
    public LogoutDummyManager dummyManager()  { return dummyManager; }
}
