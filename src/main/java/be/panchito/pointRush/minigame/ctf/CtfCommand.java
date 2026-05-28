package be.panchito.pointRush.minigame.ctf;

import be.panchito.pointRush.util.Commands;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.MinigameText;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
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

/**
 * {@code /ctf} — setup + control voor Capture the Flag.
 */
public final class CtfCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.ctf.admin";

    private static final List<String> SUBCOMMANDS = List.of(
            "start", "stop", "info", "setflagspawn",
            "setredspawn", "setbluespawn", "setreddelivery", "setbluedelivery",
            "setrounds", "setroundduration", "setcapturepoints", "setdeliveryradius",
            "reload", "leave", "help"
    );

    private final CtfGame game;
    private final CtfConfig config;

    public CtfCommand(CtfGame game, CtfConfig config) {
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
        if (sub.equals("leave")) {
            handleLeave(sender);
            return true;
        }

        if (!Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(Messages.error("Geen permissie."));
            return true;
        }

        switch (sub) {
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "setflagspawn" -> handleSetLocation(sender, "flagspawn");
            case "setredspawn" -> handleSetLocation(sender, "redspawn");
            case "setbluespawn" -> handleSetLocation(sender, "bluespawn");
            case "setreddelivery" -> handleSetLocation(sender, "reddelivery");
            case "setbluedelivery" -> handleSetLocation(sender, "bluedelivery");
            case "setrounds" -> handleSetRounds(sender, args);
            case "setroundduration" -> handleSetRoundDuration(sender, args);
            case "setcapturepoints" -> handleSetCapturePoints(sender, args);
            case "setdeliveryradius" -> handleSetDeliveryRadius(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }


    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Capture the Flag ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(line("/ctf info", "Bekijk setup en status"));
        sender.sendMessage(line("/ctf leave", "Verlaat het lopende event"));
        if (Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(line("/ctf setflagspawn", "Vlag spawn (midden) op je locatie"));
            sender.sendMessage(line("/ctf setredspawn", "Rood team spawn"));
            sender.sendMessage(line("/ctf setbluespawn", "Blauw team spawn"));
            sender.sendMessage(line("/ctf setreddelivery", "Rood delivery point"));
            sender.sendMessage(line("/ctf setbluedelivery", "Blauw delivery point"));
            sender.sendMessage(line("/ctf setrounds <aantal>", "Aantal rondes (even, default 2)"));
            sender.sendMessage(line("/ctf setroundduration <min>", "Duur per ronde (default 5)"));
            sender.sendMessage(line("/ctf setcapturepoints <pts>", "Punten per ronde winst (default 75)"));
            sender.sendMessage(line("/ctf setdeliveryradius <blok>", "Delivery radius (default 3)"));
            sender.sendMessage(line("/ctf start", "Start het event"));
            sender.sendMessage(line("/ctf stop", "Stop het event"));
            sender.sendMessage(line("/ctf reload", "Herlaad config"));
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
        sender.sendMessage(Component.text(SmallText.of("--- PointRush CTF ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Messages.info("Status: " + MinigameText.stateLabel(game.getState())));
        sender.sendMessage(Messages.info("Vlag spawn: " + locText(config.getFlagSpawn())));
        sender.sendMessage(Messages.info("Rood spawn: " + locText(config.getRedSpawn())));
        sender.sendMessage(Messages.info("Blauw spawn: " + locText(config.getBlueSpawn())));
        sender.sendMessage(Messages.info("Rood delivery: " + locText(config.getRedDelivery())));
        sender.sendMessage(Messages.info("Blauw delivery: " + locText(config.getBlueDelivery())));
        sender.sendMessage(Messages.info("Rondes: " + config.getRounds()));
        sender.sendMessage(Messages.info("Ronde duur: " + config.getRoundMinutes() + " min"));
        sender.sendMessage(Messages.info("Punten per capture: " + config.getPointsPerCapture()));
        sender.sendMessage(Messages.info("Delivery radius: " + config.getDeliveryRadius() + " blok"));
        if (game.getState() != CtfGame.State.IDLE) {
            sender.sendMessage(Messages.info("Spelers: " + game.getAllPlayerStates().size()));
            if (game.getState() == CtfGame.State.RUNNING) {
                sender.sendMessage(Messages.info("Huidige ronde: " + game.getRoundNumber()));
                sender.sendMessage(Messages.info("Aanvaller: " + game.getAttackingSide().getDisplayName()));
            }
        }
    }

    private String locText(Location loc) {
        if (loc == null || loc.getWorld() == null) return "(niet ingesteld)";
        return String.format("%s %.1f / %.1f / %.1f",
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    private void handleStart(CommandSender sender) {
        if (game.getState() != CtfGame.State.IDLE) {
            sender.sendMessage(Messages.error("Er loopt al een CTF event."));
            return;
        }
        if (!config.isReady()) {
            sender.sendMessage(Messages.error(
                    "CTF setup niet compleet. Stel flag spawn, team spawns en delivery points in."));
            return;
        }
        if (!game.start()) {
            sender.sendMessage(Messages.error("Kon CTF niet starten (minstens 2 spelers nodig?)."));
            return;
        }
        sender.sendMessage(Messages.success("Capture the Flag gestart!"));
    }

    private void handleStop(CommandSender sender) {
        if (!game.stop()) {
            sender.sendMessage(Messages.error("Er loopt geen CTF event."));
            return;
        }
        sender.sendMessage(Messages.success("CTF event gestopt."));
    }

    private void handleSetLocation(CommandSender sender, String type) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        Location loc = player.getLocation().clone();
        switch (type) {
            case "flagspawn" -> {
                config.setFlagSpawn(loc);
                player.sendMessage(Messages.success("Vlag spawn (midden) ingesteld."));
            }
            case "redspawn" -> {
                config.setRedSpawn(loc);
                player.sendMessage(Messages.success("Rood spawn ingesteld."));
            }
            case "bluespawn" -> {
                config.setBlueSpawn(loc);
                player.sendMessage(Messages.success("Blauw spawn ingesteld."));
            }
            case "reddelivery" -> {
                config.setRedDelivery(loc);
                player.sendMessage(Messages.success("Rood delivery point ingesteld."));
            }
            case "bluedelivery" -> {
                config.setBlueDelivery(loc);
                player.sendMessage(Messages.success("Blauw delivery point ingesteld."));
            }
            default -> player.sendMessage(Messages.error("Onbekend locatie type."));
        }
    }

    private void handleSetRounds(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /ctf setrounds <aantal>"));
            return;
        }
        int rounds = parseInt(args[1], sender);
        if (rounds < 0) return;
        if (rounds < 2 || rounds > 20) {
            sender.sendMessage(Messages.error("Waarde moet tussen 2 en 20 zijn (even aantal)."));
            return;
        }
        config.setRounds(rounds);
        sender.sendMessage(Messages.success("Aantal rondes: " + config.getRounds()));
    }

    private void handleSetRoundDuration(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /ctf setroundduration <minuten>"));
            return;
        }
        int min = parseInt(args[1], sender);
        if (min < 0) return;
        if (min < 2 || min > 30) {
            sender.sendMessage(Messages.error("Waarde moet tussen 2 en 30 minuten zijn."));
            return;
        }
        config.setRoundMinutes(min);
        sender.sendMessage(Messages.success("Ronde duur: " + min + " minuten."));
    }

    private void handleSetCapturePoints(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /ctf setcapturepoints <punten>"));
            return;
        }
        int pts = parseInt(args[1], sender);
        if (pts < 0) return;
        if (pts < 10 || pts > 500) {
            sender.sendMessage(Messages.error("Waarde moet tussen 10 en 500 zijn."));
            return;
        }
        config.setPointsPerCapture(pts);
        sender.sendMessage(Messages.success("Punten per ronde winst: " + pts));
    }

    private void handleSetDeliveryRadius(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /ctf setdeliveryradius <blokken>"));
            return;
        }
        try {
            double radius = Double.parseDouble(args[1]);
            if (radius < 1.0 || radius > 15.0) {
                sender.sendMessage(Messages.error("Waarde moet tussen 1 en 15 zijn."));
                return;
            }
            config.setDeliveryRadius(radius);
            sender.sendMessage(Messages.success("Delivery radius: " + radius + " blok."));
        } catch (NumberFormatException ex) {
            sender.sendMessage(Messages.error("Ongeldig getal."));
        }
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
        sender.sendMessage(Messages.success("CTF config herladen."));
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers."));
            return;
        }
        if (!game.isParticipant(player.getUniqueId())) {
            sender.sendMessage(Messages.error("Je doet niet mee aan het CTF event."));
            return;
        }
        game.removeParticipant(player, true);
        player.sendMessage(Messages.info("Je hebt het CTF event verlaten."));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Commands.filterPrefix(SUBCOMMANDS, args[0]);
        }
        return List.of();
    }

}
