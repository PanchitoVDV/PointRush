package be.panchito.pointRush.commands;

import be.panchito.pointRush.history.EventHistoryEntry;
import be.panchito.pointRush.history.EventHistoryManager;
import be.panchito.pointRush.minigame.MinigameRegistry;
import be.panchito.pointRush.util.Commands;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /pointrush command — the public-facing entrypoint for browsing events and
 * their history. Pure chat-based UI built on clickable Adventure components.
 *
 * <p>Subcommands:
 * <ul>
 *     <li>{@code /pointrush} — overview with clickable event buttons.</li>
 *     <li>{@code /pointrush history &lt;event&gt;} — recent finished events.</li>
 *     <li>{@code /pointrush event &lt;id&gt;} — placements of a single past event.</li>
 * </ul>
 */
public final class PointRushCommand implements CommandExecutor, TabCompleter {

    private static final int RECENT_LIMIT = 15;

    /** Registered events — ids and display labels from {@link MinigameRegistry}. */
    private static final Map<String, String> EVENTS = MinigameRegistry.events();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final EventHistoryManager historyManager;

    public PointRushCommand(EventHistoryManager historyManager) {
        this.historyManager = historyManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendOverview(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "history" -> handleHistory(sender, args);
            case "event" -> handleEvent(sender, args);
            case "help" -> sendOverview(sender);
            default -> {
                if (EVENTS.containsKey(sub)) {
                    sendEventHistory(sender, sub);
                } else {
                    sendOverview(sender);
                }
            }
        }
        return true;
    }

    private void sendOverview(CommandSender sender) {
        sender.sendMessage(Component.text(SmallText.of("--- PointRush Events ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text(SmallText.of(
                "klik op een event om de geschiedenis te bekijken."), NamedTextColor.GRAY));
        sender.sendMessage(Component.empty());

        for (Map.Entry<String, String> e : EVENTS.entrySet()) {
            String key = e.getKey();
            String label = e.getValue();
            int count = historyManager.ofType(key).size();

            Component button = Component.text()
                    .append(Component.text(SmallText.of("[ " + label + " ]"),
                            NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(SmallText.of("  " + count + " event(s)"),
                            NamedTextColor.GRAY))
                    .build()
                    .clickEvent(ClickEvent.runCommand("/pointrush history " + key))
                    .hoverEvent(HoverEvent.showText(Component.text(
                            SmallText.of("klik om de geschiedenis te bekijken"),
                            NamedTextColor.GRAY)));
            sender.sendMessage(button);
        }
    }

    private void handleHistory(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /pointrush history <event>"));
            return;
        }
        String type = args[1].toLowerCase(Locale.ROOT);
        if (!EVENTS.containsKey(type)) {
            sender.sendMessage(Messages.error("Onbekend event. Gebruik: "
                    + String.join(", ", EVENTS.keySet())));
            return;
        }
        sendEventHistory(sender, type);
    }

    private void sendEventHistory(CommandSender sender, String type) {
        String label = EVENTS.get(type);
        List<EventHistoryEntry> recent = historyManager.ofType(type);

        sender.sendMessage(Component.text(SmallText.of("--- " + label + " History ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        if (recent.isEmpty()) {
            sender.sendMessage(Component.text(SmallText.of(
                    "nog geen events afgerond."), NamedTextColor.GRAY));
            sender.sendMessage(backLink());
            return;
        }

        int shown = Math.min(recent.size(), RECENT_LIMIT);
        for (int i = 0; i < shown; i++) {
            EventHistoryEntry entry = recent.get(i);
            sender.sendMessage(historyLine(entry, i + 1));
        }
        if (recent.size() > shown) {
            sender.sendMessage(Component.text(SmallText.of(
                    "... en nog " + (recent.size() - shown) + " ouder(e) event(s)."),
                    NamedTextColor.DARK_GRAY));
        }
        sender.sendMessage(backLink());
    }

    private Component backLink() {
        return Component.text()
                .append(Component.text(SmallText.of("« terug naar overzicht"), NamedTextColor.GRAY))
                .build()
                .clickEvent(ClickEvent.runCommand("/pointrush"))
                .hoverEvent(HoverEvent.showText(Component.text(
                        SmallText.of("klik om naar het overzicht te gaan"),
                        NamedTextColor.GRAY)));
    }

    private Component historyLine(EventHistoryEntry entry, int index) {
        String when = DATE_FMT.format(Instant.ofEpochMilli(entry.endedAt()));
        String duration = formatDuration(entry.endedAt() - entry.startedAt());
        String winner = entry.placements().isEmpty()
                ? "(geen finishers)"
                : entry.placements().get(0).playerName();
        NamedTextColor winnerColor = NamedTextColor.GOLD;

        Component line = Component.text()
                .append(Component.text(SmallText.of("#" + index + " "), NamedTextColor.DARK_GRAY))
                .append(Component.text(when, NamedTextColor.GRAY))
                .append(Component.text(SmallText.of("  · "), NamedTextColor.DARK_GRAY))
                .append(Component.text(SmallText.of(duration), NamedTextColor.WHITE))
                .append(Component.text(SmallText.of("  · winnaar "), NamedTextColor.DARK_GRAY))
                .append(Component.text(winner, winnerColor, TextDecoration.BOLD))
                .build();

        return line
                .clickEvent(ClickEvent.runCommand("/pointrush event " + entry.id()))
                .hoverEvent(HoverEvent.showText(Component.text(
                        SmallText.of("klik om de placements te bekijken"),
                        NamedTextColor.GRAY)));
    }

    private void handleEvent(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.error("Gebruik: /pointrush event <id>"));
            return;
        }
        EventHistoryEntry entry = historyManager.get(args[1]);
        if (entry == null) {
            sender.sendMessage(Messages.error("Geen event met die id."));
            return;
        }

        String label = EVENTS.getOrDefault(entry.eventType(), entry.eventType());
        String when = DATE_FMT.format(Instant.ofEpochMilli(entry.endedAt()));
        String duration = formatDuration(entry.endedAt() - entry.startedAt());

        sender.sendMessage(Component.text(SmallText.of("--- " + label + " Event ---"),
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text()
                .append(Component.text(SmallText.of("wanneer "), NamedTextColor.GRAY))
                .append(Component.text(when, NamedTextColor.WHITE))
                .build());
        sender.sendMessage(Component.text()
                .append(Component.text(SmallText.of("duur "), NamedTextColor.GRAY))
                .append(Component.text(duration, NamedTextColor.WHITE))
                .build());
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text(SmallText.of("placements"),
                NamedTextColor.GOLD, TextDecoration.BOLD));

        if (entry.placements().isEmpty()) {
            sender.sendMessage(Component.text(SmallText.of("(geen placements)"),
                    NamedTextColor.DARK_GRAY));
        } else {
            for (EventHistoryEntry.Placement p : entry.placements()) {
                sender.sendMessage(placementLine(p));
            }
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text()
                .append(Component.text(SmallText.of("« terug naar " + label), NamedTextColor.GRAY))
                .build()
                .clickEvent(ClickEvent.runCommand("/pointrush history " + entry.eventType())));
    }

    private Component placementLine(EventHistoryEntry.Placement p) {
        TextColor rankColor = switch (p.rank()) {
            case 1 -> NamedTextColor.GOLD;
            case 2 -> NamedTextColor.GRAY;
            case 3 -> NamedTextColor.DARK_RED;
            default -> NamedTextColor.DARK_GRAY;
        };
        TextColor nameColor = parseColor(p.teamColor());
        if (nameColor == null) nameColor = NamedTextColor.WHITE;

        var builder = Component.text()
                .append(Component.text("#" + p.rank() + " ", rankColor))
                .append(Component.text(p.playerName(), nameColor, TextDecoration.BOLD));

        if (p.teamName() != null && !p.teamName().isBlank()) {
            builder.append(Component.text(SmallText.of("  (team "), NamedTextColor.DARK_GRAY))
                    .append(Component.text(SmallText.of(p.teamName()), nameColor))
                    .append(Component.text(")", NamedTextColor.DARK_GRAY));
        }

        builder.append(Component.text(SmallText.of("  +" + p.score() + " pts"),
                NamedTextColor.GOLD));

        if (p.detail() != null && !p.detail().isBlank()) {
            builder.append(Component.text(SmallText.of("  · " + p.detail()),
                    NamedTextColor.DARK_GRAY));
        }
        return builder.build();
    }

    private TextColor parseColor(String name) {
        if (name == null) return null;
        return NamedTextColor.NAMES.value(name.toLowerCase(Locale.ROOT));
    }

    private String formatDuration(long ms) {
        if (ms <= 0) return "0s";
        Duration d = Duration.ofMillis(ms);
        long minutes = d.toMinutes();
        long seconds = d.minusMinutes(minutes).toSeconds();
        if (minutes <= 0) return seconds + "s";
        return minutes + "m " + seconds + "s";
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("history", "event", "help"));
            options.addAll(EVENTS.keySet());
            return Commands.filterPrefix(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("history")) {
            return Commands.filterPrefix(new ArrayList<>(EVENTS.keySet()), args[1]);
        }
        return List.of();
    }

}
