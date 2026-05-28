package be.panchito.pointRush.minigame.treasurehunt;

import be.panchito.pointRush.util.Commands;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.MinigameText;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * {@code /treasurehunt} — survival restock/gear event met hints en schatten.
 */
public final class TreasureHuntCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.treasurehunt.admin";

    private static final List<String> SUBCOMMANDS = List.of(
            "start", "stop", "info", "addtreasure", "deltreasure", "listtreasures",
            "sethint", "hint", "sethintinterval", "setduration", "setradius",
            "reload", "help"
    );

    private static final List<String> TIERS = Arrays.stream(TreasureTier.values())
            .map(TreasureTier::getConfigKey)
            .collect(Collectors.toList());

    private final TreasureHuntGame game;
    private final TreasureHuntConfig config;

    public TreasureHuntCommand(TreasureHuntGame game, TreasureHuntConfig config) {
        this.game = game;
        this.config = config;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("help")) {
            sendHelp(sender);
            return true;
        }
        if (sub.equals("info")) {
            showInfo(sender);
            return true;
        }
        if (sub.equals("listtreasures")) {
            listTreasures(sender);
            return true;
        }

        if (!Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(Messages.error("Geen permissie."));
            return true;
        }

        switch (sub) {
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "addtreasure" -> handleAddTreasure(sender, args);
            case "deltreasure" -> handleDelTreasure(sender);
            case "sethint" -> handleSetHint(sender, args);
            case "hint" -> handleHint(sender, args);
            case "sethintinterval" -> handleSetHintInterval(sender, args);
            case "setduration" -> handleSetDuration(sender, args);
            case "setradius" -> handleSetRadius(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }


    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Treasure Hunt ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(line("/treasurehunt info", "Status van het event"));
        sender.sendMessage(line("/treasurehunt listtreasures", "Alle schatlocaties"));
        if (Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(line("/treasurehunt addtreasure [tier] [hint...]",
                    "Schat op je locatie (normal/epic/legendary)"));
            sender.sendMessage(line("/treasurehunt deltreasure", "Verwijder dichtstbijzijnde schat (5 blok)"));
            sender.sendMessage(line("/treasurehunt sethint <id> <tekst>", "Hint tekst aanpassen"));
            sender.sendMessage(line("/treasurehunt hint [id]", "Hint (+ coords) in chat"));
            sender.sendMessage(line("/treasurehunt sethintinterval <sec>", "Hint interval (default 120)"));
            sender.sendMessage(line("/treasurehunt setduration <min>", "Eventduur (default 45)"));
            sender.sendMessage(line("/treasurehunt setradius <blokken>", "Claim radius (default 2.5)"));
            sender.sendMessage(line("/treasurehunt start", "Start survival treasure hunt"));
            sender.sendMessage(line("/treasurehunt stop", "Stop het event"));
            sender.sendMessage(line("/treasurehunt reload", "Herlaad config"));
        }
    }

    private Component line(String usage, String description) {
        return Component.text()
                .append(Component.text(SmallText.of(usage), NamedTextColor.GOLD))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(SmallText.of(description), NamedTextColor.GRAY))
                .build();
    }

    private void showInfo(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Treasure Hunt ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Messages.info("Status: " + MinigameText.stateLabel(game.getState())));
        sender.sendMessage(Messages.info("Schatten: " + config.getTreasures().size()));
        sender.sendMessage(Messages.info("Hint interval: " + config.getHintIntervalSeconds() + "s"));
        sender.sendMessage(Messages.info("Duur: " + config.getDurationMinutes() + " min"));
        sender.sendMessage(Messages.info("Claim radius: " + config.getClaimRadius() + " blok"));
        if (game.getState() == TreasureHuntGame.State.RUNNING) {
            sender.sendMessage(Messages.info("Resttijd: " + game.formatTime(game.getRunTimeLeftMs())));
        }
        if (sender instanceof Player player) {
            sender.sendMessage(Messages.info("Jouw schatten: " + game.getClaimCount(player.getUniqueId())));
        }
    }

    private void listTreasures(CommandSender sender) {
        List<TreasureLocation> treasures = config.getTreasures();
        if (treasures.isEmpty()) {
            sender.sendMessage(Messages.info("Geen schatten ingesteld."));
            return;
        }
        sender.sendMessage(Component.text(SmallText.of("--- Schatten ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        for (TreasureLocation treasure : treasures) {
            sender.sendMessage(Messages.info(treasure.getId() + " · "
                    + treasure.getTier().getDisplayName() + " · "
                    + treasure.formatCoords() + " · \"" + treasure.getHint() + "\""));
        }
    }

    private void handleStart(CommandSender sender) {
        if (game.getState() != TreasureHuntGame.State.IDLE) {
            sender.sendMessage(Messages.error("Er loopt al een Treasure Hunt."));
            return;
        }
        if (!config.isReady()) {
            sender.sendMessage(Messages.error("Geen schatten ingesteld. Gebruik /treasurehunt addtreasure."));
            return;
        }
        if (!game.start()) {
            sender.sendMessage(Messages.error("Kon Treasure Hunt niet starten."));
            return;
        }
        sender.sendMessage(Messages.success("Treasure Hunt gestart!"));
    }

    private void handleStop(CommandSender sender) {
        if (!game.stop()) {
            sender.sendMessage(Messages.error("Er loopt geen Treasure Hunt."));
            return;
        }
        sender.sendMessage(Messages.success("Treasure Hunt gestopt."));
    }

    private void handleAddTreasure(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        TreasureTier tier = TreasureTier.NORMAL;
        int hintStart = 1;
        if (args.length >= 2) {
            String maybeTier = args[1].toLowerCase(Locale.ROOT);
            if (maybeTier.equals("normal") || maybeTier.equals("epic") || maybeTier.equals("legendary")) {
                tier = TreasureTier.fromConfig(args[1]);
                hintStart = 2;
            }
        }
        String hint = "Zoek de schat";
        if (args.length > hintStart) {
            hint = String.join(" ", Arrays.copyOfRange(args, hintStart, args.length));
        }
        TreasureLocation treasure = config.addTreasure(player.getLocation(), tier, hint);
        player.sendMessage(Messages.success("Schat " + treasure.getId() + " ("
                + tier.getDisplayName() + ") toegevoegd."));
    }

    private void handleDelTreasure(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen schatten verwijderen."));
            return;
        }
        TreasureLocation removed = config.removeNearest(player.getLocation(), 5.0);
        if (removed == null) {
            player.sendMessage(Messages.error("Geen schat binnen 5 blokken."));
            return;
        }
        player.sendMessage(Messages.success("Schat " + removed.getId() + " verwijderd."));
    }

    private void handleSetHint(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Messages.error("Gebruik: /treasurehunt sethint <id> <hint tekst>"));
            return;
        }
        TreasureLocation treasure = config.getTreasure(args[1]);
        if (treasure == null) {
            sender.sendMessage(Messages.error("Onbekende schat: " + args[1]));
            return;
        }
        String hint = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        treasure.setHint(hint);
        config.save();
        sender.sendMessage(Messages.success("Hint voor " + treasure.getId() + " bijgewerkt."));
    }

    private void handleHint(CommandSender sender, String[] args) {
        if (game.getState() != TreasureHuntGame.State.RUNNING) {
            sender.sendMessage(Messages.warn("Event is niet actief — hint toont wel coords."));
        }
        if (args.length >= 2) {
            TreasureLocation treasure = config.getTreasure(args[1]);
            if (treasure == null) {
                sender.sendMessage(Messages.error("Onbekende schat: " + args[1]));
                return;
            }
            game.broadcastHint(treasure, false);
            return;
        }
        List<TreasureLocation> list = config.getTreasures();
        if (list.isEmpty()) {
            sender.sendMessage(Messages.error("Geen schatten ingesteld."));
            return;
        }
        for (TreasureLocation treasure : list) {
            game.broadcastHint(treasure, false);
        }
    }

    private void handleSetHintInterval(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /treasurehunt sethintinterval <seconden>"));
            return;
        }
        int sec = parseInt(args[1], sender);
        if (sec < 0) return;
        if (sec < 30 || sec > 3600) {
            sender.sendMessage(Messages.error("Waarde moet tussen 30 en 3600 seconden zijn."));
            return;
        }
        config.setHintIntervalSeconds(sec);
        sender.sendMessage(Messages.success("Hints elke " + sec + " seconden."));
    }

    private void handleSetDuration(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /treasurehunt setduration <minuten>"));
            return;
        }
        int min = parseInt(args[1], sender);
        if (min < 0) return;
        if (min < 5 || min > 240) {
            sender.sendMessage(Messages.error("Waarde moet tussen 5 en 240 minuten zijn."));
            return;
        }
        config.setDurationMinutes(min);
        sender.sendMessage(Messages.success("Eventduur: " + min + " minuten."));
    }

    private void handleSetRadius(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /treasurehunt setradius <blokken>"));
            return;
        }
        double radius;
        try {
            radius = Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Messages.error("Ongeldig getal."));
            return;
        }
        if (radius < 1.0 || radius > 10.0) {
            sender.sendMessage(Messages.error("Waarde moet tussen 1.0 en 10.0 zijn."));
            return;
        }
        config.setClaimRadius(radius);
        sender.sendMessage(Messages.success("Claim radius: " + radius + " blok."));
    }

    private int parseInt(String raw, CommandSender sender) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Messages.error("Ongeldig getal."));
            return -1;
        }
    }

    private void handleReload(CommandSender sender) {
        config.load();
        sender.sendMessage(Messages.success("Treasure Hunt config herladen."));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Commands.filterPrefix(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("addtreasure")) {
            return Commands.filterPrefix(TIERS, args[1]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("sethint") || args[0].equalsIgnoreCase("hint"))) {
            return Commands.filterPrefix(config.getTreasures().stream().map(TreasureLocation::getId).toList(), args[1]);
        }
        return List.of();
    }

}
