package be.panchito.pointRush.minigame.finale;

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
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /finale} — setup + besturing voor de Finale-minigame.
 *
 * <p>Publieke subcommando's: {@code info}, {@code leave}, {@code help}.
 * Admin: {@code addspawn}, {@code removespawn}, {@code clearspawns}, {@code setkit},
 * {@code clearkit}, {@code setspectator}, {@code clearspectator}, {@code setfreeze},
 * {@code setgrace}, {@code start}, {@code stop}, {@code reload}.
 *
 * <p>Setup: ga naar elke gewenste startplek en doe {@code /finale addspawn} (minstens 2).
 * Vul je inventory met de gewenste uitrusting en doe {@code /finale setkit}. Eventueel
 * {@code /finale setspectator} voor een vaste kijkplek. Daarna {@code /finale start}.
 */
public final class FinaleCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.finale.admin";

    private static final List<String> SUBCOMMANDS = List.of(
            "start", "stop", "info", "addspawn", "removespawn", "clearspawns",
            "setkit", "clearkit", "setspectator", "clearspectator",
            "setfreeze", "setgrace",
            "addlootspawn", "removelootspawn", "clearlootspawns",
            "setloot", "clearloot", "droploot", "setlootinterval",
            "reload", "leave", "help"
    );

    private final FinaleGame game;
    private final FinaleConfig config;

    public FinaleCommand(FinaleGame game, FinaleConfig config) {
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

        if (sub.equals("help")) { sendHelp(sender); return true; }
        if (sub.equals("info")) { showInfo(sender); return true; }
        if (sub.equals("leave")) { handleLeave(sender); return true; }

        if (!Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(Messages.error("Geen permissie."));
            return true;
        }

        switch (sub) {
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "addspawn" -> handleAddSpawn(sender);
            case "removespawn" -> handleRemoveSpawn(sender);
            case "clearspawns" -> handleClearSpawns(sender);
            case "setkit" -> handleSetKit(sender);
            case "clearkit" -> handleClearKit(sender);
            case "setspectator" -> handleSetSpectator(sender);
            case "clearspectator" -> handleClearSpectator(sender);
            case "setfreeze" -> handleSetFreeze(sender, args);
            case "setgrace" -> handleSetGrace(sender, args);
            case "addlootspawn" -> handleAddLootSpawn(sender);
            case "removelootspawn" -> handleRemoveLootSpawn(sender);
            case "clearlootspawns" -> handleClearLootSpawns(sender);
            case "setloot" -> handleSetLoot(sender);
            case "clearloot" -> handleClearLoot(sender);
            case "droploot" -> handleDropLoot(sender);
            case "setlootinterval" -> handleSetLootInterval(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Finale ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(line("/finale info", "Bekijk de setup en status"));
        sender.sendMessage(line("/finale leave", "Verlaat het lopende event"));
        if (Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(line("/finale addspawn", "Voeg je huidige plek toe als spawn"));
            sender.sendMessage(line("/finale removespawn", "Verwijder de laatst toegevoegde spawn"));
            sender.sendMessage(line("/finale clearspawns", "Verwijder alle spawns"));
            sender.sendMessage(line("/finale setkit", "Sla je huidige inventory op als kit"));
            sender.sendMessage(line("/finale clearkit", "Verwijder de opgeslagen kit"));
            sender.sendMessage(line("/finale setspectator", "Zet de spectator-locatie"));
            sender.sendMessage(line("/finale clearspectator", "Verwijder de spectator-locatie"));
            sender.sendMessage(line("/finale setfreeze <sec>", "Stilstaan-tijd na de start"));
            sender.sendMessage(line("/finale setgrace <sec>", "Grace-tijd (looten, geen PvP)"));
            sender.sendMessage(line("/finale addlootspawn", "Voeg een loot-drop locatie toe"));
            sender.sendMessage(line("/finale removelootspawn", "Verwijder de laatste loot-locatie"));
            sender.sendMessage(line("/finale clearlootspawns", "Verwijder alle loot-locaties"));
            sender.sendMessage(line("/finale setloot", "Sla je inventory op als loot-pool"));
            sender.sendMessage(line("/finale clearloot", "Verwijder de loot-pool"));
            sender.sendMessage(line("/finale droploot", "Forceer nu een loot-drop"));
            sender.sendMessage(line("/finale setlootinterval <sec>", "Tijd tussen automatische drops"));
            sender.sendMessage(line("/finale start", "Start het event"));
            sender.sendMessage(line("/finale stop", "Stop het event"));
            sender.sendMessage(line("/finale reload", "Herlaad de finale-config"));
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
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Finale ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Messages.info("Status: " + MinigameText.stateLabel(game.getState())));
        sender.sendMessage(Messages.info("Spawns: " + config.getSpawns().size() + " (min. 2)"));
        sender.sendMessage(Messages.info("Kit: " + (config.hasKit()
                ? config.getKit().itemCount() + " items" : "(niet ingesteld)")));
        sender.sendMessage(Messages.info("Spectator: " + locText(config.getSpectatorSpawn())));
        sender.sendMessage(Messages.info("Freeze-tijd: " + config.getFreezeSeconds() + "s"));
        sender.sendMessage(Messages.info("Grace-tijd: " + config.getGraceSeconds() + "s"
                + (config.getGraceSeconds() == 0 ? " (uit)" : " (geen PvP)")));
        sender.sendMessage(Messages.info("Gevecht: geen tijdslimiet (tot 1 over)"));
        sender.sendMessage(Messages.info("Loot-locaties: " + config.getLootSpawns().size()
                + ", loot-items: " + config.getLootPool().size()
                + ", interval: " + config.getLootIntervalSeconds() + "s"));
        sender.sendMessage(Messages.info("Loot actief: " + (config.hasLoot() ? "ja" : "nee (zet loot-locaties + setloot)")));
        sender.sendMessage(Messages.info("Klaar om te starten: " + (config.isReady() ? "ja" : "nee")));
        if (game.getState() != FinaleGame.State.IDLE) {
            sender.sendMessage(Messages.info("Spelers: " + game.getAllPlayerStates().size()
                    + " (in leven: " + game.aliveCount() + ")"));
        }
    }

    private String locText(Location loc) {
        if (loc == null || loc.getWorld() == null) return "(niet ingesteld)";
        return String.format("%s %.1f / %.1f / %.1f",
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    private void handleStart(CommandSender sender) {
        if (Commands.dispatchCrossServerStart(sender, "finale")) {
            return;
        }
        if (game.getState() != FinaleGame.State.IDLE) {
            sender.sendMessage(Messages.error("Er loopt al een Finale event."));
            return;
        }
        if (!config.isReady()) {
            sender.sendMessage(Messages.error("Finale setup is niet compleet. Stel minstens 2 spawns in."));
            return;
        }
        if (!config.hasKit()) {
            sender.sendMessage(Messages.warn("Let op: er is geen kit ingesteld - spelers starten met lege handen."));
        }
        if (!game.start()) {
            sender.sendMessage(Messages.error("Kon Finale niet starten (heb je minstens 2 spelers?)."));
            return;
        }
        sender.sendMessage(Messages.success("Finale event gestart!"));
    }

    private void handleStop(CommandSender sender) {
        if (!game.stop()) {
            sender.sendMessage(Messages.error("Er loopt geen Finale event."));
            return;
        }
        sender.sendMessage(Messages.success("Finale event gestopt."));
    }

    private void handleAddSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        config.addSpawn(player.getLocation().clone());
        player.sendMessage(Messages.success("Spawn toegevoegd. Totaal nu: " + config.getSpawns().size() + "."));
    }

    private void handleRemoveSpawn(CommandSender sender) {
        if (config.removeLastSpawn()) {
            sender.sendMessage(Messages.success("Laatste spawn verwijderd. Over: "
                    + config.getSpawns().size() + "."));
        } else {
            sender.sendMessage(Messages.error("Er zijn geen spawns om te verwijderen."));
        }
    }

    private void handleClearSpawns(CommandSender sender) {
        config.clearSpawns();
        sender.sendMessage(Messages.success("Alle finale-spawns verwijderd."));
    }

    private void handleSetKit(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen een kit instellen."));
            return;
        }
        FinaleKit kit = FinaleKit.capture(player);
        if (kit.isEmpty()) {
            sender.sendMessage(Messages.error("Je inventory is leeg - vul hem eerst met de gewenste uitrusting."));
            return;
        }
        config.setKit(kit);
        player.sendMessage(Messages.success("Kit opgeslagen (" + kit.itemCount()
                + " items, incl. harnas en off-hand)."));
    }

    private void handleClearKit(CommandSender sender) {
        config.clearKit();
        sender.sendMessage(Messages.success("Finale-kit verwijderd."));
    }

    private void handleSetSpectator(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        config.setSpectatorSpawn(player.getLocation().clone());
        player.sendMessage(Messages.success("Spectator-locatie ingesteld."));
    }

    private void handleClearSpectator(CommandSender sender) {
        config.setSpectatorSpawn(null);
        sender.sendMessage(Messages.success("Spectator-locatie verwijderd (spectators blijven op hun doodplek)."));
    }

    private void handleSetFreeze(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /finale setfreeze <seconden>"));
            return;
        }
        int sec;
        try {
            sec = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Messages.error("Tijd moet een geheel getal (seconden) zijn."));
            return;
        }
        config.setFreezeSeconds(sec);
        sender.sendMessage(Messages.success("Freeze-tijd ingesteld op " + config.getFreezeSeconds() + "s."));
    }

    private void handleSetGrace(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /finale setgrace <seconden> (0 = uit)"));
            return;
        }
        int sec;
        try {
            sec = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Messages.error("Tijd moet een geheel getal (seconden) zijn."));
            return;
        }
        config.setGraceSeconds(sec);
        int g = config.getGraceSeconds();
        sender.sendMessage(Messages.success("Grace-tijd ingesteld op " + g + "s"
                + (g == 0 ? " (uit - meteen PvP)." : " (looten zonder PvP).")));
    }

    private void handleAddLootSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen locaties zetten."));
            return;
        }
        config.addLootSpawn(player.getLocation().clone());
        player.sendMessage(Messages.success("Loot-drop locatie toegevoegd. Totaal nu: "
                + config.getLootSpawns().size() + "."));
    }

    private void handleRemoveLootSpawn(CommandSender sender) {
        if (config.removeLastLootSpawn()) {
            sender.sendMessage(Messages.success("Laatste loot-locatie verwijderd. Over: "
                    + config.getLootSpawns().size() + "."));
        } else {
            sender.sendMessage(Messages.error("Er zijn geen loot-locaties om te verwijderen."));
        }
    }

    private void handleClearLootSpawns(CommandSender sender) {
        config.clearLootSpawns();
        sender.sendMessage(Messages.success("Alle loot-locaties verwijderd."));
    }

    private void handleSetLoot(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen een loot-pool instellen."));
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && !item.getType().isAir()) {
                items.add(item);
            }
        }
        if (items.isEmpty()) {
            sender.sendMessage(Messages.error("Je inventory is leeg - vul hem met de gewenste loot-items."));
            return;
        }
        config.setLootPool(items);
        player.sendMessage(Messages.success("Loot-pool opgeslagen (" + items.size()
                + " items). Per drop krijgt een speler tot " + config.getLootItemsPerDrop() + " willekeurige items."));
    }

    private void handleClearLoot(CommandSender sender) {
        config.clearLootPool();
        sender.sendMessage(Messages.success("Loot-pool verwijderd."));
    }

    private void handleDropLoot(CommandSender sender) {
        if (game.getState() != FinaleGame.State.RUNNING) {
            sender.sendMessage(Messages.error("Er is geen lopend gevecht om loot in te droppen."));
            return;
        }
        if (!config.hasLoot()) {
            sender.sendMessage(Messages.error("Geen loot ingesteld (zet loot-locaties + /finale setloot)."));
            return;
        }
        if (game.spawnLootDrop()) {
            sender.sendMessage(Messages.success("Loot-drop geplaatst!"));
        } else {
            sender.sendMessage(Messages.error("Geen vrije/geladen loot-locatie beschikbaar."));
        }
    }

    private void handleSetLootInterval(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /finale setlootinterval <seconden>"));
            return;
        }
        int sec;
        try {
            sec = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Messages.error("Tijd moet een geheel getal (seconden) zijn."));
            return;
        }
        config.setLootIntervalSeconds(sec);
        sender.sendMessage(Messages.success("Loot-interval ingesteld op " + config.getLootIntervalSeconds() + "s."));
    }

    private void handleReload(CommandSender sender) {
        config.load();
        sender.sendMessage(Messages.success("Finale config herladen."));
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers."));
            return;
        }
        if (!game.isParticipant(player.getUniqueId())) {
            sender.sendMessage(Messages.error("Je doet niet mee aan het Finale event."));
            return;
        }
        game.removeParticipant(player, true);
        player.sendMessage(Messages.info("Je hebt het Finale event verlaten."));
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
