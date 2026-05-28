package be.panchito.pointRush.history;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Persists finished events to {@code events.yml} and exposes simple queries.
 *
 * <p>Entries are appended on every event end. Reads return entries in
 * newest-first order so command output is naturally sorted by recency.
 * Persistence is intentionally synchronous and small — there should never be
 * more than a few hundred events per server.
 */
public final class EventHistoryManager {

    /** Maximum entries kept on disk; oldest are dropped when exceeded. */
    public static final int MAX_ENTRIES = 500;

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, EventHistoryEntry> entries = new LinkedHashMap<>();
    private Consumer<EventHistoryEntry> onRecord;

    public EventHistoryManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "events.yml");
    }

    public void load() {
        entries.clear();
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Kon plugin data folder niet aanmaken.");
        }
        if (!file.exists()) {
            return;
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("events");
        if (root == null) {
            return;
        }

        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) continue;
            try {
                String type = sec.getString("type", "unknown").toLowerCase(Locale.ROOT);
                long startedAt = sec.getLong("started", 0L);
                long endedAt = sec.getLong("ended", startedAt);

                List<EventHistoryEntry.Placement> placements = new ArrayList<>();
                List<Map<?, ?>> rawPlacements = sec.getMapList("placements");
                for (Map<?, ?> raw : rawPlacements) {
                    placements.add(parsePlacement(raw));
                }

                entries.put(key, new EventHistoryEntry(key, type, startedAt, endedAt, placements));
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Kon event " + key + " niet laden", ex);
            }
        }
        plugin.getLogger().info("Geladen: " + entries.size() + " event(s) uit history.");
    }

    private EventHistoryEntry.Placement parsePlacement(Map<?, ?> raw) {
        int rank = toInt(raw.get("rank"));
        UUID playerId = toUuid(raw.get("playerId"));
        Object rawName = raw.get("playerName");
        String playerName = rawName == null ? "?" : String.valueOf(rawName);
        UUID teamId = toUuid(raw.get("teamId"));
        String teamName = raw.get("teamName") == null ? null : String.valueOf(raw.get("teamName"));
        String teamColor = raw.get("teamColor") == null ? null : String.valueOf(raw.get("teamColor"));
        int score = toInt(raw.get("score"));
        String detail = raw.get("detail") == null ? null : String.valueOf(raw.get("detail"));
        return new EventHistoryEntry.Placement(
                rank, playerId, playerName, teamId, teamName, teamColor, score, detail
        );
    }

    private int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return 0;
        try { return Integer.parseInt(o.toString()); } catch (NumberFormatException ex) { return 0; }
    }

    private UUID toUuid(Object o) {
        if (o == null) return null;
        try { return UUID.fromString(o.toString()); } catch (IllegalArgumentException ex) { return null; }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, EventHistoryEntry> e : entries.entrySet()) {
            EventHistoryEntry entry = e.getValue();
            String base = "events." + entry.id();
            cfg.set(base + ".type", entry.eventType());
            cfg.set(base + ".started", entry.startedAt());
            cfg.set(base + ".ended", entry.endedAt());

            List<Map<String, Object>> raw = new ArrayList<>();
            for (EventHistoryEntry.Placement p : entry.placements()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("rank", p.rank());
                m.put("playerId", p.playerId() == null ? null : p.playerId().toString());
                m.put("playerName", p.playerName());
                m.put("teamId", p.teamId() == null ? null : p.teamId().toString());
                m.put("teamName", p.teamName());
                m.put("teamColor", p.teamColor());
                m.put("score", p.score());
                m.put("detail", p.detail());
                raw.add(m);
            }
            cfg.set(base + ".placements", raw);
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Kon events.yml niet opslaan!", ex);
        }
    }

    /** Optional hook (e.g. Mongo sync for the stats website). */
    public void setOnRecord(Consumer<EventHistoryEntry> onRecord) {
        this.onRecord = onRecord;
    }

    public void record(EventHistoryEntry entry) {
        if (entry == null) return;
        entries.put(entry.id(), entry);
        trimOldEntries();
        save();
        if (onRecord != null) {
            try {
                onRecord.accept(entry);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Event onRecord hook mislukt voor " + entry.id(), ex);
            }
        }
    }

    private void trimOldEntries() {
        if (entries.size() <= MAX_ENTRIES) return;
        List<EventHistoryEntry> sorted = new ArrayList<>(entries.values());
        sorted.sort((a, b) -> Long.compare(a.endedAt(), b.endedAt()));
        int toRemove = entries.size() - MAX_ENTRIES;
        for (int i = 0; i < toRemove; i++) {
            entries.remove(sorted.get(i).id());
        }
    }

    /** All entries, newest first. */
    public List<EventHistoryEntry> all() {
        List<EventHistoryEntry> list = new ArrayList<>(entries.values());
        list.sort((a, b) -> Long.compare(b.endedAt(), a.endedAt()));
        return Collections.unmodifiableList(list);
    }

    /** Entries for a given event type, newest first. */
    public List<EventHistoryEntry> ofType(String type) {
        if (type == null) return all();
        String norm = type.toLowerCase(Locale.ROOT);
        List<EventHistoryEntry> list = new ArrayList<>();
        for (EventHistoryEntry entry : entries.values()) {
            if (entry.eventType().equalsIgnoreCase(norm)) {
                list.add(entry);
            }
        }
        list.sort((a, b) -> Long.compare(b.endedAt(), a.endedAt()));
        return Collections.unmodifiableList(list);
    }

    public EventHistoryEntry get(String id) {
        return id == null ? null : entries.get(id);
    }
}
