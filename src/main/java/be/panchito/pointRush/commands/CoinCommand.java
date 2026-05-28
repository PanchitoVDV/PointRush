package be.panchito.pointRush.commands;

import be.panchito.pointRush.coins.CoinCollectionMenu;
import be.panchito.pointRush.coins.CoinDisplayNames;
import be.panchito.pointRush.coins.CoinSpawnConfig;
import be.panchito.pointRush.coins.NexoCoinSpawner;
import be.panchito.pointRush.storage.DataManager;
import be.panchito.pointRush.util.Commands;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Beheer en lees Nexo collectible coins (spawnplekken + profiel in MongoDB).
 */
public final class CoinCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "pointrush.coins.admin";
    private static final String USE = "pointrush.coins.use";

    private static final List<String> SUBS = List.of(
            "help", "menu", "info", "status", "addspawn", "clearspawns", "reload", "refill", "toggle"
    );

    private final CoinSpawnConfig config;
    private final NexoCoinSpawner spawner;
    private final DataManager dataManager;

    public CoinCommand(CoinSpawnConfig config, NexoCoinSpawner spawner, DataManager dataManager) {
        this.config = config;
        this.spawner = spawner;
        this.dataManager = dataManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player && sender.hasPermission(USE)) {
                CoinCollectionMenu.open(player, config, dataManager);
                return true;
            }
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("help")) {
            sendHelp(sender);
            return true;
        }

        if (sub.equals("menu")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Messages.error("Alleen spelers kunnen het Rush-muntmenu openen."));
                return true;
            }
            if (!sender.hasPermission(USE)) {
                sender.sendMessage(Messages.error("Geen permissie."));
                return true;
            }
            CoinCollectionMenu.open(player, config, dataManager);
            return true;
        }

        if (sub.equals("info")) {
            if (!sender.hasPermission(USE)) {
                sender.sendMessage(Messages.error("Geen permissie."));
                return true;
            }
            handleInfo(sender, args);
            return true;
        }

        if (!Commands.isAdmin(sender, ADMIN)) {
            sender.sendMessage(Messages.error("Geen permissie."));
            return true;
        }

        switch (sub) {
            case "status" -> handleStatus(sender);
            case "addspawn" -> handleAddSpawn(sender);
            case "clearspawns" -> handleClearSpawns(sender);
            case "reload" -> handleReload(sender);
            case "refill" -> handleRefill(sender);
            case "toggle" -> handleToggle(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("CoreSMP - Rush-munten"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        if (sender.hasPermission(USE)) {
            sender.sendMessage(line("/coins", "Je Rush-muntmenu (GUI)"));
            sender.sendMessage(line("/coins menu", "Zelfde menu"));
            sender.sendMessage(line("/coins info [speler]", "Lijstje met tellers"));
        }
        if (Commands.isAdmin(sender, ADMIN)) {
            sender.sendMessage(line("/coins status", "Spawner-status en yaml-samenvatting"));
            sender.sendMessage(line("/coins addspawn", "Voeg spawnplek toe (je positie)"));
            sender.sendMessage(line("/coins clearspawns", "Wis alle spawnplekken"));
            sender.sendMessage(line("/coins reload", "Herlaad coins uit settings.yml"));
            sender.sendMessage(line("/coins refill", "Probeer ontbrekende coins te plaatsen"));
            sender.sendMessage(line("/coins toggle", "Zet enabled in yaml aan/uit"));
        }
    }

    private Component line(String usage, String desc) {
        return Component.text()
                .append(Component.text(SmallText.of(usage), NamedTextColor.GOLD))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(SmallText.of(desc), NamedTextColor.GRAY))
                .build();
    }

    private void handleInfo(CommandSender sender, String[] args) {
        UUID targetUuid;
        String label;
        if (args.length >= 2 && Commands.isAdmin(sender, ADMIN)) {
            OfflinePlayer off = resolvePlayer(args[1]);
            if (off == null) {
                sender.sendMessage(Messages.error("Speler niet gevonden."));
                return;
            }
            targetUuid = off.getUniqueId();
            label = off.getName() != null ? off.getName() : targetUuid.toString();
        } else if (sender instanceof Player self) {
            targetUuid = self.getUniqueId();
            label = self.getName() != null ? self.getName() : "you";
        } else {
            sender.sendMessage(Messages.error("Console: gebruik /coins info <speler>"));
            return;
        }

        var repo = dataManager.getPlayerCoinRepository();
        if (repo == null) {
            sender.sendMessage(Messages.error("Profielopslag staat niet aan."));
            return;
        }
        Map<String, Integer> totals = repo.getTotals(targetUuid, config.getResolvedPoolIds());
        int sum = totals.values().stream().mapToInt(Integer::intValue).sum();

        sender.sendMessage(Component.text(SmallText.of("Rush-munten — " + label),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Messages.info("Totaal op je profiel: " + sum));

        List<Map.Entry<String, Integer>> positives = totals.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(48)
                .toList();
        if (positives.isEmpty()) {
            sender.sendMessage(Messages.info("Nog niks verzameld (alle tellers op 0)."));
            return;
        }
        for (Map.Entry<String, Integer> e : positives) {
            String coinLabel = CoinDisplayNames.friendlyName(e.getKey());
            sender.sendMessage(Messages.plain(SmallText.of("  "
                    + coinLabel + ": " + e.getValue()), NamedTextColor.GRAY));
        }
    }

    private OfflinePlayer resolvePlayer(String nameFragment) {
        Player online = Bukkit.getPlayerExact(nameFragment);
        if (online != null) {
            return online;
        }
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.getName() != null && o.getName().equalsIgnoreCase(nameFragment)) {
                return o;
            }
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer offline = Bukkit.getOfflinePlayer(nameFragment);
        if (!offline.hasPlayedBefore() && !offline.isOnline()) {
            return null;
        }
        return offline;
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("Rush-munt spawner"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Messages.info("Enabled: " + config.isEnabled()));
        sender.sendMessage(Messages.info("Minigame nu actief (spawn gate): " + spawner.isMinigameGateOpen()));
        sender.sendMessage(Messages.info("Nexo items geladen (API): " + spawner.isNexoItemsReady()));
        sender.sendMessage(Messages.info("Spawnplekken: " + config.getSpawns().size()));
        sender.sendMessage(Messages.info("Pool: " + config.poolSummary()));
        long respawnSec = config.getPickupRespawnDelayTicks() / 20L;
        sender.sendMessage(Messages.info("Respawn na pickup per plek: " + respawnSec + " s"));
        sender.sendMessage(Messages.info(
                "Refill om de " + config.getRefillIntervalTicks()
                        + " ticks (radius=" + config.getProximityRadius() + ")"));
        sender.sendMessage(Messages.info(
                "Loop-fase klaar: " + config.isRunnable() + "."));
    }

    private void handleAddSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen een spawn zetten."));
            return;
        }
        Location loc = player.getLocation().getBlock().getLocation().clone();
        config.addSpawn(loc);
        player.sendMessage(Messages.success("Spawn #" + config.getSpawns().size()
                + " opgeslagen in settings.yml (blok voet: "
                + loc.getWorld().getName() + " " + loc.getBlockX()
                + " " + loc.getBlockY() + " " + loc.getBlockZ() + ")."));
    }

    private void handleClearSpawns(CommandSender sender) {
        config.clearSpawns();
        sender.sendMessage(Messages.success("Alle Rush-munt spawnpunten gewist."));
    }

    private void handleReload(CommandSender sender) {
        config.load();
        spawner.reloadConfig();
        sender.sendMessage(Messages.success("coins-sectie herladen en spawner-interval vernieuwd."));
    }

    private void handleRefill(CommandSender sender) {
        spawner.refillEmptySpawns();
        sender.sendMessage(Messages.success("Refill-tick gedraaid (lege plekken vullen waar mogelijk)."));
    }

    private void handleToggle(CommandSender sender) {
        boolean next = !config.isEnabled();
        config.setEnabled(next);
        sender.sendMessage(Messages.success("coins.enabled staat nu op " + next + "."));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> all = new ArrayList<>();
            if (sender.hasPermission(USE)) {
                all.addAll(List.of("help", "menu", "info"));
            }
            if (Commands.isAdmin(sender, ADMIN)) {
                all.addAll(List.of(
                        "status", "addspawn", "clearspawns", "reload", "refill", "toggle"));
            }
            return Commands.filterPrefix(all, args[0]);
        }
        return List.of();
    }

}
