package be.panchito.pointRush.storage;

import be.panchito.pointRush.config.UnifiedSettings;
import be.panchito.pointRush.history.EventHistoryEntry;
import be.panchito.pointRush.history.EventHistoryManager;
import be.panchito.pointRush.storage.mongo.MongoEventRepository;
import be.panchito.pointRush.storage.mongo.MongoLiveEventRepository;
import be.panchito.pointRush.storage.mongo.MongoLiveStreamRepository;
import be.panchito.pointRush.storage.mongo.MongoPlayerCoinRepository;
import be.panchito.pointRush.storage.mongo.MongoScheduledEventRepository;
import be.panchito.pointRush.storage.mongo.MongoTeamRepository;
import be.panchito.pointRush.team.LeaderboardCache;
import be.panchito.pointRush.team.Team;
import be.panchito.pointRush.team.TeamManager;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * MongoDB-laag voor teams (met punten en leden), plus gedebounced/async writes en
 * leaderboard-cache invalidatie.
 */
public final class DataManager {

    private final JavaPlugin plugin;
    private final TeamManager teamManager;
    private final UnifiedSettings unifiedSettings;
    private final LeaderboardCache leaderboardCache;

    private MongoClient mongoClient;
    private MongoTeamRepository teamRepo;
    private MongoPlayerCoinRepository playerCoinRepo;
    private MongoEventRepository eventRepo;
    private MongoLiveStreamRepository liveStreamRepo;
    private MongoScheduledEventRepository scheduledEventRepo;
    private MongoLiveEventRepository liveEventRepo;

    private final Object flushLock = new Object();
    private volatile BukkitTask pendingFlush;

    public DataManager(JavaPlugin plugin, TeamManager teamManager,
                       UnifiedSettings unifiedSettings, LeaderboardCache leaderboardCache) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.unifiedSettings = unifiedSettings;
        this.leaderboardCache = leaderboardCache;
    }

    /** Verbindt met MongoDB en vult {@link TeamManager}. Gebruikt tijdens enable (hoofd-thread). */
    public void load() {
        YamlConfiguration yaml = unifiedSettings.yaml();
        String uri = yaml.getString("mongodb.uri", "mongodb://localhost:27017");
        String database = yaml.getString("mongodb.database", "pointrush");
        String collection = yaml.getString("mongodb.teams-collection", "teams");
        String playerCoins = yaml.getString("mongodb.player-coins-collection", "player_coins");
        String eventsCollection = yaml.getString("mongodb.events-collection", "events");
        String liveStreams = yaml.getString("mongodb.live-streams-collection", "live_streams");
        String scheduleCollection = yaml.getString("mongodb.schedule-collection", "event_schedule");
        String liveEventCollection = yaml.getString("mongodb.live-event-collection", "live_event");

        try {
            mongoClient = MongoClients.create(uri);
            teamRepo = new MongoTeamRepository(mongoClient, database, collection);
            playerCoinRepo = new MongoPlayerCoinRepository(mongoClient, database, playerCoins);
            eventRepo = new MongoEventRepository(mongoClient, database, eventsCollection);
            liveStreamRepo = new MongoLiveStreamRepository(mongoClient, database, liveStreams);
            scheduledEventRepo = new MongoScheduledEventRepository(mongoClient, database, scheduleCollection);
            liveEventRepo = new MongoLiveEventRepository(mongoClient, database, liveEventCollection);

            teamManager.clear();
            List<Team> fromMongo = teamRepo.loadAll(plugin.getLogger());
            if (fromMongo.isEmpty()) {
                List<Team> legacyTeams = loadTeamsFromLegacyYaml();
                if (!legacyTeams.isEmpty()) {
                    for (Team t : legacyTeams) {
                        teamManager.registerTeam(t);
                    }
                    teamRepo.upsertAllMetadata(new ArrayList<>(teamManager.getTeams()));
                    plugin.getLogger().warning("MongoDB-collectie was leeg: teams.yml eenmalig geïmporteerd ("
                            + legacyTeams.size() + " teams). Verwijder of hernoem teams.yml na controle.");
                }
            } else {
                for (Team team : fromMongo) {
                    teamManager.registerTeam(team);
                }
            }
            plugin.getLogger().info("MongoDB verbonden. Teams geladen: " + teamManager.getTeams().size()
                    + "; player coins-collectie: " + playerCoins
                    + "; events-collectie: " + eventsCollection
                    + "; live streams-collectie: " + liveStreams
                    + "; schedule-collectie: " + scheduleCollection);
        } catch (Exception ex) {
            shutdownMongoQuiet();
            logMongoConnectivityHints(ex);
            throw new IllegalStateException("MongoDB verbinding of laden mislukt", ex);
        }
    }

    private void logMongoConnectivityHints(Throwable ex) {
        Throwable t = ex;
        while (t != null) {
            if (t instanceof MongoTimeoutException) {
                plugin.getLogger().severe(
                        "MongoDB: timeout bij verbinden (geen bereikbare TCP-poort). Dit is vrijwel altijd firewall, "
                                + "security group, of Mongo luistert alleen op localhost (bindIp). "
                                + "Test vanaf deze machine naar jouw URI-host:poort (bv. Test-NetConnection host -Port 27017).");
                return;
            }
            String msg = t.getMessage();
            if (msg != null && (msg.contains("timed out") || msg.contains("Connection refused"))) {
                plugin.getLogger().severe(
                        "MongoDB: socket-timeout of connection refused — netwerk/route/firewall/bindIp controleren.");
                return;
            }
            t = t.getCause();
        }
    }

    /**
     * Eenmalige upgrade: als Mongo leeg is maar {@code teams.yml} nog bestaat, importeer die teams.
     */
    private List<Team> loadTeamsFromLegacyYaml() {
        File teamsFile = new File(plugin.getDataFolder(), "teams.yml");
        if (!teamsFile.exists()) {
            return List.of();
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(teamsFile);
        ConfigurationSection root = cfg.getConfigurationSection("teams");
        if (root == null) {
            return List.of();
        }
        List<Team> out = new ArrayList<>();
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                UUID id = UUID.fromString(key);
                String name = section.getString("name");
                UUID leader = UUID.fromString(section.getString("leader", ""));
                String colorName = section.getString("color", "WHITE");
                long points = section.getLong("points", 0L);
                List<String> memberStrings = section.getStringList("members");

                NamedTextColor color = NamedTextColor.NAMES.value(colorName.toLowerCase());
                if (color == null) {
                    color = NamedTextColor.WHITE;
                }

                Team team = new Team(id, name, leader, color);
                team.setPoints(points);
                for (String memberId : memberStrings) {
                    try {
                        team.addMemberRaw(UUID.fromString(memberId));
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("Ongeldig UUID in team " + name + ": " + memberId);
                    }
                }
                out.add(team);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Kon legacy team niet laden: " + key, ex);
            }
        }
        return out;
    }

    public void shutdown() {
        cancelPendingFlush();
        flushSync();
        shutdownMongoQuiet();
    }

    private void shutdownMongoQuiet() {
        if (mongoClient != null) {
            try {
                mongoClient.close();
            } catch (Exception ignored) {
            }
            mongoClient = null;
            teamRepo = null;
            playerCoinRepo = null;
            eventRepo = null;
            liveStreamRepo = null;
            scheduledEventRepo = null;
            liveEventRepo = null;
        }
    }

    public MongoLiveStreamRepository getLiveStreamRepository() {
        return liveStreamRepo;
    }

    public MongoScheduledEventRepository getScheduledEventRepository() {
        return scheduledEventRepo;
    }

    public MongoLiveEventRepository getLiveEventRepository() {
        return liveEventRepo;
    }

    /** Mongo-profiel voor verzamelde Nexo collectible coins ({@code null} als verbinding geforceerd sloot). */
    public MongoPlayerCoinRepository getPlayerCoinRepository() {
        return playerCoinRepo;
    }

    /** Schrijft een afgerond event naar MongoDB (async, voor de stats website). */
    public void syncEventHistoryEntry(EventHistoryEntry entry) {
        MongoEventRepository repo = eventRepo;
        if (repo == null || entry == null) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repo.upsert(entry);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Kon event niet naar MongoDB schrijven: " + entry.id(), ex);
            }
        });
    }

    /** Verwijdert een afgerond event uit MongoDB (async, voor de stats website). */
    public void deleteEventHistoryEntry(String id) {
        MongoEventRepository repo = eventRepo;
        if (repo == null || id == null) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repo.delete(id);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Kon event niet uit MongoDB verwijderen: " + id, ex);
            }
        });
    }

    /** Eenmalige migratie: bestaande events.yml → MongoDB. */
    public void syncAllEventHistory(Iterable<EventHistoryEntry> entries) {
        MongoEventRepository repo = eventRepo;
        if (repo == null) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repo.syncAll(entries);
                plugin.getLogger().info("Event history gesynchroniseerd naar MongoDB.");
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Kon event history niet naar MongoDB migreren.", ex);
            }
        });
    }
    /**
     * Persisteer alle teams naar MongoDB. De leaderboard-cache wordt direct gewist zodat de volgende
     * read een verse volgorde uit het geheugen bouwt; schrijven kan async en gedebounced zijn.
     */
    public void save() {
        leaderboardCache.invalidate();

        YamlConfiguration yaml = unifiedSettings.yaml();
        boolean asyncWrites = yaml.getBoolean("cache.async-writes", true);
        long debounceMs = yaml.getLong("cache.debounce-ms", 400L);

        if (!asyncWrites) {
            flushSync();
            return;
        }

        cancelPendingFlush();
        long ticks = Math.max(1L, debounceMs / 50L);
        pendingFlush = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            pendingFlush = null;
            flushSync();
        }, ticks);
    }

    private void cancelPendingFlush() {
        BukkitTask task = pendingFlush;
        if (task != null) {
            task.cancel();
            pendingFlush = null;
        }
    }

    private void flushSync() {
        MongoTeamRepository repo = teamRepo;
        if (repo == null) {
            return;
        }
        synchronized (flushLock) {
            try {
                repo.upsertAllMetadata(new ArrayList<>(teamManager.getTeams()));
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Kon teams niet naar MongoDB schrijven.", ex);
            }
        }
    }

    /**
     * Kent atomair teampunten toe ({@code $inc}). Commutatief en dus veilig wanneer meerdere servers
     * tegen dezelfde database schrijven: gelijktijdige toekenningen kunnen elkaar nooit overschrijven.
     * Werkt het in-memory team direct bij (voor live weergave) en schrijft async naar MongoDB.
     */
    public void addTeamPoints(Team team, long delta) {
        if (team == null || delta == 0L) {
            return;
        }
        team.addPoints(delta);
        leaderboardCache.invalidate();
        MongoTeamRepository repo = teamRepo;
        if (repo == null) {
            return;
        }
        runStorageTask(() -> repo.incrementPoints(team, delta),
                "Kon teampunten niet bijwerken: " + team.getName());
    }

    /**
     * Trekt teampunten af (in-memory geklemd op {@code >= 0}) en schrijft de nieuwe absolute waarde weg.
     * Voor admin-acties — bewust autoritair (last-writer-wins), in tegenstelling tot {@link #addTeamPoints}.
     */
    public void removeTeamPoints(Team team, long amount) {
        if (team == null || amount == 0L) {
            return;
        }
        team.removePoints(amount);
        persistAbsolutePoints(team);
    }

    /**
     * Zet teampunten op een vaste waarde (admin set/reset). Schrijft de absolute waarde atomair weg.
     */
    public void setTeamPoints(Team team, long value) {
        if (team == null) {
            return;
        }
        team.setPoints(value);
        persistAbsolutePoints(team);
    }

    private void persistAbsolutePoints(Team team) {
        leaderboardCache.invalidate();
        MongoTeamRepository repo = teamRepo;
        if (repo == null) {
            return;
        }
        long value = team.getPoints();
        runStorageTask(() -> repo.setPoints(team, value),
                "Kon teampunten niet bijwerken: " + team.getName());
    }

    /** Verwijdert een team uit MongoDB (expliciet bij disband; routine-saves verwijderen nooit). */
    public void deleteTeam(UUID teamId) {
        MongoTeamRepository repo = teamRepo;
        if (repo == null || teamId == null) {
            return;
        }
        runStorageTask(() -> repo.deleteTeam(teamId), "Kon team niet verwijderen: " + teamId);
    }

    /** Voert een MongoDB-schrijfactie async uit; logt bij falen met {@code errorMessage}. */
    private void runStorageTask(Runnable task, String errorMessage) {
        if (!plugin.isEnabled()) {
            // Tijdens shutdown kunnen geen async tasks meer; schrijf synchroon zodat data niet verloren gaat.
            try {
                task.run();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, errorMessage, ex);
            }
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                task.run();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, errorMessage, ex);
            }
        });
    }

    /**
     * Wist alle spelerdata: teams (in-memory + MongoDB), rush-muntprofielen en eventgeschiedenis
     * (events.yml + MongoDB, voor de stats website).
     * Hoofd-thread aanbevolen; Mongo-writes zijn synchroon.
     */
    public WipeResult wipeAllPlayerData(EventHistoryManager historyManager) {
        cancelPendingFlush();
        leaderboardCache.invalidate();

        int teamsRemoved = teamManager.getTeams().size();
        for (Team team : new ArrayList<>(teamManager.getTeams())) {
            teamManager.disbandTeam(team);
        }
        // Routine-saves verwijderen niets meer; wis de team-collectie hier expliciet.
        MongoTeamRepository teams = teamRepo;
        if (teams != null) {
            try {
                teams.deleteAll();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Kon teams niet wissen in MongoDB.", ex);
                throw new IllegalStateException("Kon teams niet wissen", ex);
            }
        }

        long coinProfilesRemoved = 0L;
        MongoPlayerCoinRepository coins = playerCoinRepo;
        if (coins != null) {
            try {
                coinProfilesRemoved = coins.deleteAll();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Kon speler-muntprofielen niet wissen.", ex);
                throw new IllegalStateException("Kon rush-muntprofielen niet wissen", ex);
            }
        }

        int eventsRemoved = 0;
        if (historyManager != null) {
            eventsRemoved = historyManager.clearAll();
        }

        long mongoEventsRemoved = 0L;
        MongoEventRepository events = eventRepo;
        if (events != null) {
            try {
                mongoEventsRemoved = events.deleteAll();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Kon eventgeschiedenis niet wissen in MongoDB.", ex);
                throw new IllegalStateException("Kon eventgeschiedenis niet wissen", ex);
            }
        }

        if (mongoEventsRemoved > 0 && eventsRemoved == 0) {
            eventsRemoved = (int) Math.min(mongoEventsRemoved, Integer.MAX_VALUE);
        }

        return new WipeResult(teamsRemoved, coinProfilesRemoved, eventsRemoved);
    }

    public record WipeResult(int teamsRemoved, long coinProfilesRemoved, int eventsRemoved) {}
}
