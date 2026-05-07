package com.nightfall;

import com.nightfall.command.NightfallCommand;
import com.nightfall.dummy.LogoutDummyManager;
import com.nightfall.listener.LogoutDummyListener;
import com.nightfall.listener.NightMobListener;
import com.nightfall.listener.SleepBlocker;
import com.nightfall.spawn.ExtraSpawnTask;
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

        getServer().getPluginManager().registerEvents(nightMobListener, this);
        getServer().getPluginManager().registerEvents(new SleepBlocker(config), this);
        getServer().getPluginManager().registerEvents(new LogoutDummyListener(dummyManager), this);

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
