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
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * {@code /ctf} — setup + control voor Capture the Flag.
 */
public final class CtfCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.ctf.admin";

    private static final List<String> SUBCOMMANDS = List.of(
            "start", "stop", "info", "setredspawn", "setbluespawn",
            "setrounds", "setroundduration", "sethidephase", "setcapturepoints", "setcaptureradius",
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
            case "setredspawn" -> handleSetSpawn(sender, CtfSide.RED);
            case "setbluespawn" -> handleSetSpawn(sender, CtfSide.BLUE);
            case "setrounds" -> handleSetRounds(sender, args);
            case "setroundduration" -> handleSetRoundDuration(sender, args);
            case "sethidephase" -> handleSetHidePhase(sender, args);
            case "setcapturepoints" -> handleSetCapturePoints(sender, args);
            case "setcaptureradius" -> handleSetCaptureRadius(sender, args);
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
            sender.sendMessage(line("/ctf setredspawn", "Rood team spawn (ook afleverpunt voor zoekers)"));
            sender.sendMessage(line("/ctf setbluespawn", "Blauw team spawn (ook afleverpunt voor zoekers)"));
            sender.sendMessage(line("/ctf setrounds <aantal>", "Aantal rondes (default 3)"));
            sender.sendMessage(line("/ctf setroundduration <min>", "Duur per ronde (default 5)"));
            sender.sendMessage(line("/ctf sethidephase <sec>", "Verstopfase in seconden (default 60)"));
            sender.sendMessage(line("/ctf setcapturepoints <pts>", "Punten per ronde winst (default 75)"));
            sender.sendMessage(line("/ctf setcaptureradius <blok>", "Radius bij spawn voor afleveren (default 4)"));
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
        sender.sendMessage(Messages.info("Rood spawn: " + locText(config.getRedSpawn())));
        sender.sendMessage(Messages.info("Blauw spawn: " + locText(config.getBlueSpawn())));
        sender.sendMessage(Messages.info("Rondes: " + config.getRounds()));
        sender.sendMessage(Messages.info("Ronde duur: " + config.getRoundMinutes() + " min"));
        sender.sendMessage(Messages.info("Verstopfase: " + config.getHidePhaseSeconds() + " sec"));
        sender.sendMessage(Messages.info("Punten per winst: " + config.getPointsPerCapture()));
        sender.sendMessage(Messages.info("Capture radius: " + config.getCaptureRadius() + " blok"));
        if (game.getState() != CtfGame.State.IDLE) {
            sender.sendMessage(Messages.info("Spelers: " + game.getAllPlayerStates().size()));
            if (game.getState() == CtfGame.State.RUNNING) {
                sender.sendMessage(Messages.info("Huidige ronde: " + game.getRoundNumber()));
                sender.sendMessage(Messages.info("Verstopt: " + game.getHidingSide().getDisplayName()));
                sender.sendMessage(Messages.info("Zoekt: " + game.getSeekingSide().getDisplayName()));
                sender.sendMessage(Messages.info("Fase: " + game.getRoundPhase().name()));
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
            sender.sendMessage(Messages.error("CTF setup niet compleet. Stel rood en blauw spawn in."));
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

    private void handleSetSpawn(CommandSender sender, CtfSide side) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        Location loc = player.getLocation().clone();
        if (side == CtfSide.RED) {
            config.setRedSpawn(loc);
        } else {
            config.setBlueSpawn(loc);
        }
        player.sendMessage(Messages.success(side.getDisplayName() + " spawn ingesteld."));
    }

    private void handleSetRounds(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /ctf setrounds <aantal>"));
            return;
        }
        int rounds = parseInt(args[1], sender);
        if (rounds < 0) return;
        if (rounds < 1 || rounds > 20) {
            sender.sendMessage(Messages.error("Waarde moet tussen 1 en 20 zijn."));
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

    private void handleSetHidePhase(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /ctf sethidephase <seconden>"));
            return;
        }
        int sec = parseInt(args[1], sender);
        if (sec < 0) return;
        if (sec < 15 || sec > 300) {
            sender.sendMessage(Messages.error("Waarde moet tussen 15 en 300 seconden zijn."));
            return;
        }
        config.setHidePhaseSeconds(sec);
        sender.sendMessage(Messages.success("Verstopfase: " + sec + " seconden."));
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

    private void handleSetCaptureRadius(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /ctf setcaptureradius <blokken>"));
            return;
        }
        try {
            double radius = Double.parseDouble(args[1]);
            if (radius < 1.0 || radius > 15.0) {
                sender.sendMessage(Messages.error("Waarde moet tussen 1 en 15 zijn."));
                return;
            }
            config.setCaptureRadius(radius);
            sender.sendMessage(Messages.success("Capture radius: " + radius + " blok."));
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
