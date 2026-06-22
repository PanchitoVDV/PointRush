package be.panchito.pointRush.commands;

import be.panchito.pointRush.history.EventHistoryEntry;
import be.panchito.pointRush.history.EventHistoryManager;
import be.panchito.pointRush.minigame.MinigameRegistry;
import be.panchito.pointRush.storage.DataManager;
import be.panchito.pointRush.team.Team;
import be.panchito.pointRush.team.TeamManager;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin-GUI achter {@code /pointrush admin}: een menu om afgeronde events te verwijderen, bv. wanneer
 * een event niet mag meetellen. Bij het verwijderen worden de toegekende punten teruggedraaid (per
 * placement met een team), wordt het event uit {@code events.yml} gehaald en uit MongoDB (stats site).
 */
public final class AdminEventMenu {

    private static final int LIST_SIZE = 54;
    private static final int EVENTS_PER_PAGE = 45;
    static final int SLOT_PREV = 45;
    static final int SLOT_CLOSE = 49;
    static final int SLOT_NEXT = 53;

    private static final int CONFIRM_SIZE = 27;
    static final int SLOT_CONFIRM_YES = 11;
    private static final int SLOT_CONFIRM_INFO = 13;
    static final int SLOT_CONFIRM_NO = 15;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final EventHistoryManager history;
    private final TeamManager teamManager;
    private final DataManager dataManager;

    public AdminEventMenu(EventHistoryManager history, TeamManager teamManager, DataManager dataManager) {
        this.history = history;
        this.teamManager = teamManager;
        this.dataManager = dataManager;
    }

    // ----------------------------------------------------------------- list view

    public void openList(Player player) {
        openList(player, 0);
    }

    public void openList(Player player, int page) {
        List<EventHistoryEntry> all = history.all();
        int pageCount = Math.max(1, (all.size() + EVENTS_PER_PAGE - 1) / EVENTS_PER_PAGE);
        int safePage = Math.max(0, Math.min(page, pageCount - 1));

        Holder holder = new Holder(Holder.View.LIST, safePage, null);
        Component title = Component.text()
                .append(Component.text(SmallText.of("Events beheren"), NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(SmallText.of("  (pagina " + (safePage + 1) + "/" + pageCount + ")"),
                        NamedTextColor.DARK_GRAY))
                .build();
        Inventory inv = Bukkit.createInventory(holder, LIST_SIZE, title);
        holder.attach(inv);

        if (all.isEmpty()) {
            inv.setItem(22, simpleItem(Material.BARRIER,
                    Component.text(SmallText.of("Nog geen afgeronde events"), NamedTextColor.GRAY),
                    List.of()));
        } else {
            int from = safePage * EVENTS_PER_PAGE;
            int to = Math.min(all.size(), from + EVENTS_PER_PAGE);
            int slot = 0;
            for (int i = from; i < to; i++) {
                EventHistoryEntry entry = all.get(i);
                inv.setItem(slot, eventItem(entry));
                holder.slotEvents.put(slot, entry.id());
                slot++;
            }
        }

        fillRow(inv);
        if (safePage > 0) {
            inv.setItem(SLOT_PREV, simpleItem(Material.ARROW,
                    Component.text(SmallText.of("« Vorige pagina"), NamedTextColor.YELLOW), List.of()));
        }
        if (safePage < pageCount - 1) {
            inv.setItem(SLOT_NEXT, simpleItem(Material.ARROW,
                    Component.text(SmallText.of("Volgende pagina »"), NamedTextColor.YELLOW), List.of()));
        }
        inv.setItem(SLOT_CLOSE, simpleItem(Material.BARRIER,
                Component.text(SmallText.of("Sluiten"), NamedTextColor.RED), List.of()));

        player.openInventory(inv);
    }

    private ItemStack eventItem(EventHistoryEntry entry) {
        String label = MinigameRegistry.displayName(entry.eventType());
        String when = DATE_FMT.format(Instant.ofEpochMilli(entry.endedAt()));
        String winner = entry.placements().isEmpty() ? "(geen finishers)"
                : entry.placements().get(0).playerName();
        long totalPoints = totalAwardedPoints(entry);

        List<Component> lore = new ArrayList<>();
        lore.add(line("Wanneer", when));
        lore.add(line("Winnaar", winner));
        lore.add(line("Toegekende punten", String.valueOf(totalPoints)));
        lore.add(line("Placements", String.valueOf(entry.placements().size())));
        lore.add(Component.empty());
        lore.add(Component.text(SmallText.of("» klik om te verwijderen"),
                NamedTextColor.RED, TextDecoration.BOLD));
        lore.add(Component.text(SmallText.of("  (punten worden teruggedraaid)"), NamedTextColor.DARK_GRAY));

        return simpleItem(iconFor(entry.eventType()),
                Component.text(SmallText.of(label), NamedTextColor.GOLD, TextDecoration.BOLD), lore);
    }

    // -------------------------------------------------------------- confirm view

    public void openConfirm(Player player, String eventId) {
        EventHistoryEntry entry = history.get(eventId);
        if (entry == null) {
            openList(player, 0);
            return;
        }

        Holder holder = new Holder(Holder.View.CONFIRM, 0, eventId);
        Component title = Component.text(SmallText.of("Event verwijderen?"),
                NamedTextColor.RED, TextDecoration.BOLD);
        Inventory inv = Bukkit.createInventory(holder, CONFIRM_SIZE, title);
        holder.attach(inv);

        for (int slot = 0; slot < CONFIRM_SIZE; slot++) {
            inv.setItem(slot, pane());
        }

        String label = MinigameRegistry.displayName(entry.eventType());
        String when = DATE_FMT.format(Instant.ofEpochMilli(entry.endedAt()));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(line("Wanneer", when));
        infoLore.add(line("Toegekende punten", String.valueOf(totalAwardedPoints(entry))));
        infoLore.add(Component.empty());
        for (EventHistoryEntry.Placement p : entry.placements()) {
            if (p.teamId() == null || p.score() <= 0) continue;
            infoLore.add(Component.text(SmallText.of("  -" + p.score() + " pts · "), NamedTextColor.RED)
                    .append(Component.text(SmallText.of(p.teamName() != null ? p.teamName() : p.playerName()),
                            NamedTextColor.WHITE)));
        }
        inv.setItem(SLOT_CONFIRM_INFO, simpleItem(iconFor(entry.eventType()),
                Component.text(SmallText.of(label), NamedTextColor.GOLD, TextDecoration.BOLD), infoLore));

        inv.setItem(SLOT_CONFIRM_YES, simpleItem(Material.LIME_WOOL,
                Component.text(SmallText.of("Verwijderen"), NamedTextColor.GREEN, TextDecoration.BOLD),
                List.of(Component.text(SmallText.of("event wissen + punten terugdraaien"),
                        NamedTextColor.GRAY))));
        inv.setItem(SLOT_CONFIRM_NO, simpleItem(Material.RED_WOOL,
                Component.text(SmallText.of("Annuleren"), NamedTextColor.RED, TextDecoration.BOLD),
                List.of(Component.text(SmallText.of("terug naar de lijst"), NamedTextColor.GRAY))));

        player.openInventory(inv);
    }

    // ------------------------------------------------------------------- delete

    /**
     * Verwijdert het event en draait de toegekende punten terug. Geeft een resultaat terug voor
     * feedback, of {@code null} als het event niet (meer) bestond.
     */
    public DeleteResult deleteEvent(String eventId) {
        EventHistoryEntry entry = history.get(eventId);
        if (entry == null) {
            return null;
        }

        int teamsAffected = 0;
        long pointsReversed = 0L;
        for (EventHistoryEntry.Placement p : entry.placements()) {
            if (p.teamId() == null || p.score() <= 0) continue;
            Team team = teamManager.getTeam(p.teamId());
            if (team == null) continue;
            dataManager.removeTeamPoints(team, p.score());
            teamsAffected++;
            pointsReversed += p.score();
        }

        history.remove(eventId);
        dataManager.deleteEventHistoryEntry(eventId);

        return new DeleteResult(MinigameRegistry.displayName(entry.eventType()), teamsAffected, pointsReversed);
    }

    private long totalAwardedPoints(EventHistoryEntry entry) {
        long total = 0L;
        for (EventHistoryEntry.Placement p : entry.placements()) {
            if (p.score() > 0) total += p.score();
        }
        return total;
    }

    // -------------------------------------------------------------- item helpers

    private Component line(String key, String value) {
        return Component.text()
                .append(Component.text(SmallText.of(key + ": "), NamedTextColor.GRAY))
                .append(Component.text(SmallText.of(value), NamedTextColor.WHITE))
                .build();
    }

    private ItemStack simpleItem(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            if (!lore.isEmpty()) {
                List<Component> clean = new ArrayList<>();
                for (Component c : lore) {
                    clean.add(c.decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(clean);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        });
        return stack;
    }

    private ItemStack pane() {
        return simpleItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
    }

    private void fillRow(Inventory inv) {
        ItemStack pane = pane();
        for (int slot = LIST_SIZE - 9; slot < LIST_SIZE; slot++) {
            inv.setItem(slot, pane.clone());
        }
    }

    private static Material iconFor(String type) {
        return switch (type) {
            case "parkour" -> Material.FEATHER;
            case "tnttag", "tntrun" -> Material.TNT;
            case "dropper" -> Material.WATER_BUCKET;
            case "race" -> Material.MINECART;
            case "boatrace" -> Material.OAK_BOAT;
            case "bingo" -> Material.FILLED_MAP;
            case "koth" -> Material.GOLDEN_HELMET;
            case "floorislava" -> Material.MAGMA_BLOCK;
            case "treasurehunt" -> Material.CHEST;
            case "goldrush" -> Material.GOLD_INGOT;
            case "hiddentarget" -> Material.TARGET;
            case "ctf" -> Material.WHITE_BANNER;
            case "holdthecrown" -> Material.GOLDEN_APPLE;
            case "bossevent" -> Material.DRAGON_HEAD;
            case "football" -> Material.SLIME_BALL;
            case "finale" -> Material.NETHER_STAR;
            default -> Material.PAPER;
        };
    }

    /** Resultaat van een verwijderactie, voor de chat-feedback. */
    public record DeleteResult(String eventLabel, int teamsAffected, long pointsReversed) {
    }

    /**
     * Inventory-holder die de staat van het admin-menu draagt: welke weergave, welke pagina, het
     * geselecteerde event en (in de lijst) de mapping van slot → event-id voor kliks.
     */
    public static final class Holder implements InventoryHolder {

        public enum View { LIST, CONFIRM }

        private final View view;
        private final int page;
        private final String eventId;
        private final Map<Integer, String> slotEvents = new HashMap<>();
        private Inventory inventory;

        Holder(View view, int page, String eventId) {
            this.view = view;
            this.page = page;
            this.eventId = eventId;
        }

        void attach(Inventory inventory) {
            this.inventory = inventory;
        }

        public View getView() {
            return view;
        }

        public int getPage() {
            return page;
        }

        public String getEventId() {
            return eventId;
        }

        public String eventAtSlot(int slot) {
            return slotEvents.get(slot);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
