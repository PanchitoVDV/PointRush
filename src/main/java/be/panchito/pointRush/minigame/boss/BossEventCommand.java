package be.panchito.pointRush.minigame.boss;

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
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * {@code /bossevent} — setup + control voor Boss Event (MythicMobs).
 */
public final class BossEventCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.bossevent.admin";

    private static final List<String> SUBCOMMANDS = List.of(
            "start", "stop", "info", "leave", "reload", "help",
            "addarena", "removearena", "listarenas",
            "setarenaspawn", "setarenabossspawn", "setarenaboss",
            "setfinalspawn", "setfinalbossspawn", "setfinalboss",
            "setplayersperarena", "setroundtimeout", "setintermission",
            "setsurvivorpoints", "setfinalbonus", "setfinalconsolation"
    );

    private final BossEventGame game;
    private final BossEventConfig config;

    public BossEventCommand(BossEventGame game, BossEventConfig config) {
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
            case "reload" -> handleReload(sender);
            case "addarena" -> handleAddArena(sender, args);
            case "removearena" -> handleRemoveArena(sender, args);
            case "listarenas" -> handleListArenas(sender);
            case "setarenaspawn" -> handleSetArenaSpawn(sender, args, true);
            case "setarenabossspawn" -> handleSetArenaSpawn(sender, args, false);
            case "setarenaboss" -> handleSetArenaBoss(sender, args);
            case "setfinalspawn" -> handleSetFinalSpawn(sender);
            case "setfinalbossspawn" -> handleSetFinalBossSpawn(sender);
            case "setfinalboss" -> handleSetFinalBoss(sender, args);
            case "setplayersperarena" -> handleSetInt(sender, args, config::setPlayersPerArena, "spelers per arena");
            case "setroundtimeout" -> handleSetInt(sender, args, config::setRoundTimeoutMinutes, "ronde timeout (min)");
            case "setintermission" -> handleSetInt(sender, args, config::setIntermissionSeconds, "pauze (sec)");
            case "setsurvivorpoints" -> handleSetInt(sender, args, config::setSurvivorPoints, "arena-overlever punten");
            case "setfinalbonus" -> handleSetInt(sender, args, config::setFinalBonusPoints, "finaal bonus");
            case "setfinalconsolation" -> handleSetInt(sender, args, config::setFinalConsolationPoints, "finaal troostprijs");
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Boss Event ---"),
                NamedTextColor.DARK_RED, TextDecoration.BOLD));
        sender.sendMessage(line("/bossevent info", "Bekijk setup en status"));
        sender.sendMessage(line("/bossevent leave", "Verlaat het lopende event"));
        if (Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(line("/bossevent addarena <id>", "Nieuwe arena toevoegen"));
            sender.sendMessage(line("/bossevent removearena <id>", "Arena verwijderen"));
            sender.sendMessage(line("/bossevent listarenas", "Alle arenas tonen"));
            sender.sendMessage(line("/bossevent setarenaspawn <id>", "Speler-spawn voor arena"));
            sender.sendMessage(line("/bossevent setarenabossspawn <id>", "Boss-spawn voor arena"));
            sender.sendMessage(line("/bossevent setarenaboss <id> <1|2|3> <MythicMob>", "Boss per ronde"));
            sender.sendMessage(line("/bossevent setfinalspawn", "Finaal speler-spawn"));
            sender.sendMessage(line("/bossevent setfinalbossspawn", "Finaal boss-spawn"));
            sender.sendMessage(line("/bossevent setfinalboss <MythicMob>", "Finaal boss"));
            sender.sendMessage(line("/bossevent setplayersperarena <n>", "Max spelers per arena (default 20)"));
            sender.sendMessage(line("/bossevent setroundtimeout <min>", "Timeout per ronde"));
            sender.sendMessage(line("/bossevent setintermission <sec>", "Pauze tussen rondes"));
            sender.sendMessage(line("/bossevent setsurvivorpoints <pts>", "Punten na 3 rondes"));
            sender.sendMessage(line("/bossevent setfinalbonus <pts>", "Bonus finaal (arena-overlevers)"));
            sender.sendMessage(line("/bossevent setfinalconsolation <pts>", "Troostprijs finaal"));
            sender.sendMessage(line("/bossevent start", "Start het event"));
            sender.sendMessage(line("/bossevent stop", "Stop het event"));
            sender.sendMessage(line("/bossevent reload", "Herlaad config"));
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
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Boss Event ---"),
                NamedTextColor.DARK_RED, TextDecoration.BOLD));
        sender.sendMessage(Messages.info("Status: " + MinigameText.stateLabel(game.getState())));
        sender.sendMessage(Messages.info("MythicMobs: "
                + (config.getMythic().isAvailable()
                ? "geladen (" + config.getMythic().listMobIds().size() + " mobs)"
                : "NIET gevonden")));
        sender.sendMessage(Messages.info("Spelers per arena: " + config.getPlayersPerArena()));
        sender.sendMessage(Messages.info("Arena-rondes: " + config.getArenaRounds()));
        sender.sendMessage(Messages.info("Ronde timeout: " + config.getRoundTimeoutMinutes() + " min"));
        sender.sendMessage(Messages.info("Pauze: " + config.getIntermissionSeconds() + " sec"));
        sender.sendMessage(Messages.info("Overlever punten: " + config.getSurvivorPoints()));
        sender.sendMessage(Messages.info("Finaal bonus: " + config.getFinalBonusPoints()));
        sender.sendMessage(Messages.info("Finaal troost: " + config.getFinalConsolationPoints()));
        sender.sendMessage(Messages.info("Arenas: " + config.getArenas().size()));
        for (BossEventArenaConfig arena : config.getArenas()) {
            sender.sendMessage(Messages.info("  · " + arena.getId() + " ready=" + arena.isReady()
                    + " bosses=[" + arena.getRoundBoss(1) + ", "
                    + arena.getRoundBoss(2) + ", " + arena.getRoundBoss(3) + "]"));
        }
        sender.sendMessage(Messages.info("Finaal spawn: " + locText(config.getFinalPlayerSpawn())));
        sender.sendMessage(Messages.info("Finaal boss spawn: " + locText(config.getFinalBossSpawn())));
        sender.sendMessage(Messages.info("Finaal boss: " + nullText(config.getFinalBoss())));
        sender.sendMessage(Messages.info("Setup klaar: " + config.isReady()));
        sendSetupIssues(sender);
        if (game.getState() != BossEventGame.State.IDLE) {
            sender.sendMessage(Messages.info("Deelnemers: " + game.getAllPlayerStates().size()));
            sender.sendMessage(Messages.info("Fase: " + game.getPhase() + " · ronde " + game.getCurrentRound()));
            sender.sendMessage(Messages.info("Actieve arenas: " + game.getActiveArenas().size()));
        }
    }

    private String locText(org.bukkit.Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return "(niet ingesteld)";
        }
        return String.format("%s %.1f / %.1f / %.1f",
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    private String nullText(String value) {
        return value == null || value.isBlank() ? "(niet ingesteld)" : value;
    }

    private void sendSetupIssues(CommandSender sender) {
        List<String> issues = config.getSetupIssues();
        if (issues.isEmpty()) {
            return;
        }
        sender.sendMessage(Messages.warn("Ontbrekend (" + issues.size() + "):"));
        for (String issue : issues) {
            sender.sendMessage(Messages.info("  · " + issue));
        }
    }

    private void handleStart(CommandSender sender) {
        if (game.getState() != BossEventGame.State.IDLE) {
            sender.sendMessage(Messages.error("Er loopt al een Boss Event."));
            return;
        }
        if (!config.getMythic().isAvailable()) {
            sender.sendMessage(Messages.error("MythicMobs is niet geladen."));
            return;
        }
        if (!config.isReady()) {
            sender.sendMessage(Messages.error("Boss Event setup niet compleet:"));
            sendSetupIssues(sender);
            return;
        }
        if (game.start()) {
            sender.sendMessage(Messages.success("Boss Event gestart!"));
        } else {
            sender.sendMessage(Messages.error("Kon Boss Event niet starten."));
        }
    }

    private void handleStop(CommandSender sender) {
        if (game.stop()) {
            sender.sendMessage(Messages.success("Boss Event gestopt."));
        } else {
            sender.sendMessage(Messages.error("Geen actief Boss Event."));
        }
    }

    private void handleReload(CommandSender sender) {
        config.load();
        sender.sendMessage(Messages.success("Boss Event config herladen."));
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen leave gebruiken."));
            return;
        }
        if (!game.isParticipant(player.getUniqueId())) {
            sender.sendMessage(Messages.error("Je doet niet mee aan Boss Event."));
            return;
        }
        game.leave(player);
    }

    private void handleAddArena(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /bossevent addarena <id>"));
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (config.getArena(id) != null) {
            sender.sendMessage(Messages.error("Arena '" + id + "' bestaat al."));
            return;
        }
        config.addArena(id);
        sender.sendMessage(Messages.success("Arena '" + id + "' toegevoegd."));
    }

    private void handleRemoveArena(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /bossevent removearena <id>"));
            return;
        }
        if (config.removeArena(args[1])) {
            sender.sendMessage(Messages.success("Arena verwijderd."));
        } else {
            sender.sendMessage(Messages.error("Arena niet gevonden."));
        }
    }

    private void handleListArenas(CommandSender sender) {
        if (config.getArenas().isEmpty()) {
            sender.sendMessage(Messages.info("Geen arenas geconfigureerd."));
            return;
        }
        for (BossEventArenaConfig arena : config.getArenas()) {
            sender.sendMessage(Messages.info(arena.getId() + " — ready=" + arena.isReady()));
        }
    }

    private void handleSetArenaSpawn(CommandSender sender, String[] args, boolean playerSpawn) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties instellen."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /bossevent "
                    + (playerSpawn ? "setarenaspawn" : "setarenabossspawn") + " <id>"));
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (config.getArena(id) == null) {
            sender.sendMessage(Messages.error("Arena '" + id + "' bestaat niet."));
            return;
        }
        if (playerSpawn) {
            config.setArenaPlayerSpawn(id, player.getLocation());
            sender.sendMessage(Messages.success("Speler-spawn voor arena '" + id + "' ingesteld."));
        } else {
            config.setArenaBossSpawn(id, player.getLocation());
            sender.sendMessage(Messages.success("Boss-spawn voor arena '" + id + "' ingesteld."));
        }
    }

    private void handleSetArenaBoss(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Messages.error("Gebruik: /bossevent setarenaboss <id> <1|2|3> <MythicMob>"));
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (config.getArena(id) == null) {
            sender.sendMessage(Messages.error("Arena '" + id + "' bestaat niet."));
            return;
        }
        int round;
        try {
            round = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Messages.error("Ronde moet 1, 2 of 3 zijn."));
            return;
        }
        if (round < 1 || round > 3) {
            sender.sendMessage(Messages.error("Ronde moet 1, 2 of 3 zijn."));
            return;
        }
        String mobId = args[3];
        if (!config.getMythic().mobExists(mobId)) {
            sender.sendMessage(Messages.warn("MythicMob '" + mobId + "' niet gevonden — opgeslagen anyway."));
        }
        config.setArenaRoundBoss(id, round, mobId);
        sender.sendMessage(Messages.success("Arena '" + id + "' ronde " + round + " boss: " + mobId));
    }

    private void handleSetFinalSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties instellen."));
            return;
        }
        config.setFinalPlayerSpawn(player.getLocation());
        sender.sendMessage(Messages.success("Finaal speler-spawn ingesteld."));
    }

    private void handleSetFinalBossSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties instellen."));
            return;
        }
        config.setFinalBossSpawn(player.getLocation());
        sender.sendMessage(Messages.success("Finaal boss-spawn ingesteld."));
    }

    private void handleSetFinalBoss(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /bossevent setfinalboss <MythicMob>"));
            return;
        }
        String mobId = args[1];
        if (!config.getMythic().mobExists(mobId)) {
            sender.sendMessage(Messages.warn("MythicMob '" + mobId + "' niet gevonden — opgeslagen anyway."));
        }
        config.setFinalBoss(mobId);
        sender.sendMessage(Messages.success("Finaal boss: " + mobId));
    }

    private void handleSetInt(CommandSender sender, String[] args, IntSetter setter, String label) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /bossevent " + args[0] + " <getal>"));
            return;
        }
        try {
            int value = Integer.parseInt(args[1]);
            setter.set(value);
            sender.sendMessage(Messages.success(label + " ingesteld op " + value + "."));
        } catch (NumberFormatException ex) {
            sender.sendMessage(Messages.error("Ongeldig getal."));
        }
    }

    @FunctionalInterface
    private interface IntSetter {
        void set(int value);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("leave")
                    || args[0].equalsIgnoreCase("help")) {
                return filter(SUBCOMMANDS, args[0]);
            }
            if (!Commands.isAdmin(sender, PERMISSION)) {
                return List.of();
            }
            return filter(SUBCOMMANDS, args[0]);
        }

        if (!Commands.isAdmin(sender, PERMISSION)) {
            return List.of();
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            if (sub.equals("removearena") || sub.equals("setarenaspawn")
                    || sub.equals("setarenabossspawn") || sub.equals("setarenaboss")) {
                return filter(config.getArenas().stream()
                        .map(BossEventArenaConfig::getId)
                        .collect(Collectors.toList()), args[1]);
            }
            if (sub.equals("setfinalboss")) {
                return filterMythicMobs(args[1]);
            }
        }
        if (args.length == 3 && sub.equals("setarenaboss")) {
            return filter(List.of("1", "2", "3"), args[2]);
        }
        if (args.length >= 4 && sub.equals("setarenaboss")) {
            return filterMythicMobs(args[3]);
        }
        return List.of();
    }

    private List<String> filterMythicMobs(String prefix) {
        if (!config.getMythic().isAvailable()) {
            return List.of();
        }
        return filter(config.getMythic().listMobIds(), prefix != null ? prefix : "");
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(opt);
            }
        }
        return out;
    }
}
