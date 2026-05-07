package com.nightfall.command;

import com.nightfall.NightfallConfig;
import com.nightfall.NightfallPlugin;
import com.nightfall.spawn.ExtraSpawnTask;
import com.nightfall.time.TimeController;
import com.nightfall.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /nightfall reload | status | day [world] | night [world]
 *
 * All subcommands gate on the nightfall.admin permission.
 */
public final class NightfallCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("reload", "status", "day", "night");

    private final NightfallPlugin plugin;
    private final NightfallConfig config;
    private final TimeController timeController;
    private final ExtraSpawnTask extraSpawnTask;

    public NightfallCommand(NightfallPlugin plugin,
                            NightfallConfig config,
                            TimeController timeController,
                            ExtraSpawnTask extraSpawnTask) {
        this.plugin = plugin;
        this.config = config;
        this.timeController = timeController;
        this.extraSpawnTask = extraSpawnTask;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nightfall.admin")) {
            send(sender, "no-permission", "&cYou don't have permission to do that.");
            return true;
        }
        if (args.length == 0) {
            send(sender, null, "&7Usage: /nightfall <reload|status|day|night>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> runReload(sender);
            case "status" -> runStatus(sender, args);
            case "day"    -> runForce(sender, args, /* isNight */ false);
            case "night"  -> runForce(sender, args, /* isNight */ true);
            default       -> send(sender, null, "&7Usage: /nightfall <reload|status|day|night>");
        }
        return true;
    }

    private void runReload(CommandSender sender) {
        config.load(plugin);
        timeController.applyWorldRules();
        timeController.resetAccumulators();
        plugin.rescheduleExtraSpawnTask();
        send(sender, "reloaded", "&aNightfall config reloaded.");
    }

    private void runStatus(CommandSender sender, String[] args) {
        World w = resolveWorld(sender, args);
        if (w == null) return;
        long tod = w.getTime();
        boolean night = TimeController.isNight(w);
        String tmpl = config.message("status",
                "&7World: &f{world}\n&7Phase: &f{phase}  &8(time {time}/24000)");
        String formatted = Text.color(tmpl)
                .replace("{world}", w.getName())
                .replace("{phase}", night ? "night" : "day")
                .replace("{time}", Long.toString(tod))
                .replace("{day}", trim(config.dayMinutes()))
                .replace("{night}", trim(config.nightMinutes()))
                .replace("{sleep-skip}", config.preventNightSkip() ? "blocked" : "vanilla")
                .replace("{mob-buff}", config.mobBuffEnabled() ? "on" : "off")
                .replace("{extras}", Integer.toString(extraSpawnTask.countExtras(w)));
        // Intentionally avoid prefixing — status is multi-line.
        for (String line : formatted.split("\n", -1)) {
            sender.sendMessage(Component.text(line));
        }
    }

    private void runForce(CommandSender sender, String[] args, boolean isNight) {
        World w = resolveWorld(sender, args);
        if (w == null) return;
        // 1000 = mid-morning, 14000 = solid night.
        w.setTime(isNight ? 14000L : 1000L);
        send(sender, isNight ? "forced-night" : "forced-day",
                isNight ? "&eForced night in &f{world}&e." : "&eForced day in &f{world}&e.",
                "{world}", w.getName());
    }

    /**
     * Resolve a world from optional args[1], else the player's current
     * world. Returns null and notifies the sender if no world can be
     * resolved.
     */
    private World resolveWorld(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            World w = Bukkit.getWorld(args[1]);
            if (w == null) {
                send(sender, "unknown-world", "&cThat world isn't loaded.");
                return null;
            }
            return w;
        }
        if (sender instanceof Player p) return p.getWorld();
        send(sender, null, "&7Console must specify a world: /nightfall " + args[0] + " <world>");
        return null;
    }

    private void send(CommandSender to, String key, String fallback, String... replacements) {
        String prefix = Text.color(config.message("prefix", "&8[&5Nightfall&8] &r"));
        String tmpl = key == null ? fallback : config.message(key, fallback);
        String body = Text.color(tmpl);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            body = body.replace(replacements[i], replacements[i + 1]);
        }
        to.sendMessage(Component.text(prefix + body));
    }

    private static String trim(double v) {
        if (v == Math.floor(v)) return Long.toString((long) v);
        return String.format(Locale.ROOT, "%.1f", v);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : SUBS) if (s.startsWith(prefix)) out.add(s);
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("status")
                || args[0].equalsIgnoreCase("day")
                || args[0].equalsIgnoreCase("night"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (World w : Bukkit.getWorlds()) {
                if (w.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(w.getName());
            }
            return out;
        }
        return List.of();
    }
}
