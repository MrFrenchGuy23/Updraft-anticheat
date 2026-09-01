package com.updraft.anticheat.command;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.data.PlayerData;
import com.updraft.anticheat.data.storage.LogDao;
import com.updraft.anticheat.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Root command: {@code /updraft} (alias {@code /ac}). Subcommands:
 * help, alerts, info, violations, logs, reload, checks, exempt.
 */
public final class UpdraftCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList(
            "help", "alerts", "info", "violations", "logs", "commands", "reload", "checks", "exempt");

    private final UpdraftAC plugin;
    private final LogDao logDao;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public UpdraftCommand(UpdraftAC plugin) {
        this.plugin = plugin;
        this.logDao = new LogDao(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> sendHelp(sender, label);
            case "reload" -> handleReload(sender);
            case "alerts" -> handleAlerts(sender);
            case "checks" -> handleChecks(sender);
            case "info" -> handleInfo(sender, args);
            case "violations" -> handleViolations(sender, args);
            case "logs" -> handleLogs(sender, args);
            case "commands" -> handleCommands(sender, args);
            case "exempt" -> handleExempt(sender, args);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void sendHelp(CommandSender s, String label) {
        s.sendMessage(plugin.messages().raw("commands.help-header"));
        line(s, "/" + label + " help", "Show this help");
        line(s, "/" + label + " reload", "Reload all configuration files");
        line(s, "/" + label + " alerts", "Toggle staff alerts for yourself");
        line(s, "/" + label + " checks", "List all registered checks");
        line(s, "/" + label + " info <player>", "Show live player info");
        line(s, "/" + label + " violations <player>", "Show active violation levels");
        line(s, "/" + label + " logs <player>", "Show recent DB-stored logs");
        line(s, "/" + label + " commands <player>", "Show recent executed commands");
        line(s, "/" + label + " exempt <player> <check>", "Toggle exemption (permission based)");
        s.sendMessage(plugin.messages().raw("commands.help-footer"));
    }

    private void line(CommandSender s, String usage, String desc) {
        s.sendMessage(plugin.messages().raw("commands.help-line", "usage", usage, "description", desc));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("updraft.command.reload")) {
            sender.sendMessage(plugin.messages().noPermission());
            return;
        }
        long start = System.currentTimeMillis();
        plugin.reload();
        sender.sendMessage(plugin.messages().reloadSuccess(System.currentTimeMillis() - start));
    }

    private void handleAlerts(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.messages().playerOnly());
            return;
        }
        if (!sender.hasPermission("updraft.alerts")) {
            sender.sendMessage(plugin.messages().noPermission());
            return;
        }
        Player p = (Player) sender;
        boolean now = plugin.alerts().toggle(p.getUniqueId());
        sender.sendMessage(now
                ? plugin.messages().raw("commands.alerts-toggled-on")
                : plugin.messages().raw("commands.alerts-toggled-off"));
    }

    private void handleChecks(CommandSender sender) {
        if (!sender.hasPermission("updraft.command.checks")) {
            sender.sendMessage(plugin.messages().noPermission());
            return;
        }
        sender.sendMessage(plugin.messages().raw("commands.checks-header"));
        for (Check c : plugin.checks().all()) {
            sender.sendMessage(plugin.messages().raw("commands.checks-line",
                    "category", c.category().display(),
                    "id", c.shortId(),
                    "enabled", String.valueOf(c.enabled())));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("updraft.command.info")) {
            sender.sendMessage(plugin.messages().noPermission());
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.messages().raw("commands.player-not-found", "target", ""));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        PlayerData pd = target == null ? null : plugin.playerData().get(target);
        if (pd == null) {
            sender.sendMessage(plugin.messages().playerNotFound(args[1]));
            return;
        }
        sender.sendMessage(plugin.messages().raw("commands.info-header", "player", pd.name()));
        kv(sender, "Client", pd.clientType());
        kv(sender, "Brand", pd.clientBrand());
        kv(sender, "Version", pd.clientVersion());
        kv(sender, "Ping", pd.ping() + "ms");
        kv(sender, "On ground", String.valueOf(pd.onGround()));
        kv(sender, "Mods", String.join(", ", pd.detectedModIds()));
        kv(sender, "Channels", String.join(", ", pd.pluginChannels()));
        kv(sender, "Total VL", String.format(Locale.ROOT, "%.1f", pd.totalVl()));
        kv(sender, "Game mode", target.getGameMode().name());
        sender.sendMessage(plugin.messages().raw("commands.info-footer"));
    }

    private void handleViolations(CommandSender sender, String[] args) {
        if (!sender.hasPermission("updraft.command.violations")) {
            sender.sendMessage(plugin.messages().noPermission());
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.messages().playerNotFound(""));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        PlayerData pd = target == null ? null : plugin.playerData().get(target);
        if (pd == null) {
            sender.sendMessage(plugin.messages().playerNotFound(args[1]));
            return;
        }
        if (pd.violations().isEmpty()) {
            sender.sendMessage(plugin.messages().raw("commands.violations-empty", "player", pd.name()));
            return;
        }
        sender.sendMessage(plugin.messages().raw("commands.violations-header", "player", pd.name()));
        pd.violations().forEach((check, vl) -> {
            Check c = plugin.checks().get(check);
            int maxVl = c == null || c.settings() == null ? 100 : c.settings().maxVl();
            sender.sendMessage(plugin.messages().raw("commands.violations-line",
                    "check", check, "vl", String.format(Locale.ROOT, "%.1f", vl),
                    "maxvl", Integer.toString(maxVl)));
        });
    }

    private void handleLogs(CommandSender sender, String[] args) {
        if (!sender.hasPermission("updraft.command.logs")) {
            sender.sendMessage(plugin.messages().noPermission());
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.messages().playerNotFound(""));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]); // supports offline lookups
        UUID uuid = target.getUniqueId();
        sender.sendMessage(plugin.messages().raw("commands.logs-header", "player", args[1]));
        CompletableFuture.supplyAsync(() -> logDao.recentViolations(uuid, 10))
                .thenAccept(rows -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (rows.isEmpty()) {
                        sender.sendMessage(plugin.messages().raw("commands.logs-empty", "player", args[1]));
                        return;
                    }
                    for (var r : rows) {
                        sender.sendMessage(plugin.messages().raw("commands.logs-line",
                                "time", dateFormat.format(new Date(r.timeMs())),
                                "check", r.checkId(),
                                "vl", String.format(Locale.ROOT, "%.1f", r.vl()),
                                "action", ""));
                    }
                    sender.sendMessage(plugin.messages().raw("commands.logs-footer"));
                }));
    }

    private void handleCommands(CommandSender sender, String[] args) {
        if (!sender.hasPermission("updraft.command.commands")) {
            sender.sendMessage(plugin.messages().noPermission());
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.messages().playerNotFound(""));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        UUID uuid = target.getUniqueId();
        sender.sendMessage(plugin.messages().raw("commands.cmdlogs-header", "player", args[1]));
        CompletableFuture.supplyAsync(() -> logDao.recentCommands(uuid, 10))
                .thenAccept(rows -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (rows.isEmpty()) {
                        sender.sendMessage(plugin.messages().raw("commands.cmdlogs-empty", "player", args[1]));
                        return;
                    }
                    for (var r : rows) {
                        sender.sendMessage(plugin.messages().raw("commands.cmdlogs-line",
                                "time", dateFormat.format(new Date(r.timeMs())),
                                "command", r.command(),
                                "world", r.world() == null ? "" : r.world()));
                    }
                    sender.sendMessage(plugin.messages().raw("commands.cmdlogs-footer"));
                }));
    }

    private void handleExempt(CommandSender sender, String[] args) {
        if (!sender.hasPermission("updraft.command.exempt")) {
            sender.sendMessage(plugin.messages().noPermission());
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatUtil.color(plugin.messages().prefix() + "&cUsage: /updraft exempt <player> <check|*>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.messages().playerNotFound(args[1]));
            return;
        }
        String check = args[2].toLowerCase(Locale.ROOT);
        sender.sendMessage(ChatUtil.color(plugin.messages().prefix() + "&7Exemptions are permission-based — grant &fupdraft.exempt." + check
                + " &7to &f" + target.getName() + "&7 via your permission manager."));
    }

    private void kv(CommandSender s, String key, String value) {
        s.sendMessage(plugin.messages().raw("commands.info-line", "key", key, "value", value));
    }

    // ----- tab completion -----

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) if (startsWith(s, args[0])) out.add(s);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("info") || sub.equals("violations") || sub.equals("logs")
                    || sub.equals("commands") || sub.equals("exempt")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (startsWith(p.getName(), args[1])) out.add(p.getName());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("exempt")) {
            for (Check c : plugin.checks().all()) {
                if (startsWith(c.shortId(), args[2])) out.add(c.shortId());
            }
            if (startsWith("*", args[2])) out.add("*");
        }
        return out;
    }

    private static boolean startsWith(String candidate, String prefix) {
        return candidate.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
    }
}
