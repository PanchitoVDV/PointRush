package be.panchito.pointRush.coins;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.storage.mongo.MongoPlayerCoinRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

/**
 * Niet-blokkerende cache van het totale Rush-munten saldo per speler.
 *
 * <p>De PlaceholderAPI-expansie ({@code %pointrush_coins%}) wordt op de hoofd-thread aangeroepen,
 * vaak elke scoreboard/HUD-render. Voorheen deed elke aanroep een blokkerende MongoDB-query op de
 * hoofd-thread (10-50ms serverstilstand per render). Nu leest de placeholder uit deze in-memory
 * cache; alle Mongo-I/O loopt op virtuele threads buiten de hoofd-thread.
 */
public final class CoinTotalCache {

    /** Hoe vaak de saldo's van online spelers met MongoDB gesynchroniseerd worden. */
    private static final long REFRESH_INTERVAL_TICKS = 200L; // ~10s

    private final PointRush plugin;
    private final ExecutorService io;
    private final ConcurrentHashMap<UUID, Integer> totals = new ConcurrentHashMap<>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();

    private BukkitTask refreshTask;

    public CoinTotalCache(PointRush plugin, ExecutorService io) {
        this.plugin = plugin;
        this.io = io;
    }

    /** Start de periodieke (async) hersynchronisatie voor online spelers. */
    public void start() {
        if (refreshTask != null) {
            return;
        }
        // Hoofd-thread leest alleen de online spelerslijst (Bukkit-API), dispatch daarna async.
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                refresh(p.getUniqueId());
            }
        }, REFRESH_INTERVAL_TICKS, REFRESH_INTERVAL_TICKS);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    /**
     * Laatst bekende saldo (niet-blokkerend). Bij een cache-miss wordt een async load gestart en
     * tijdelijk 0 teruggegeven; de volgende render toont de echte waarde.
     */
    public int get(UUID playerId) {
        Integer cached = totals.get(playerId);
        if (cached == null) {
            refresh(playerId);
            return 0;
        }
        return cached;
    }

    /**
     * Verwerkt een muntpickup: optimistische directe bijwerking voor instant feedback, plus een
     * async Mongo-write gevolgd door een autoritatieve herlees zodat de cache exact klopt.
     */
    public void recordPickup(UUID playerId, String nexoItemId, int amount) {
        if (amount != 0) {
            totals.merge(playerId, amount, Integer::sum);
        }
        io.execute(() -> {
            try {
                MongoPlayerCoinRepository repo = plugin.getDataManager().getPlayerCoinRepository();
                if (repo == null) {
                    return;
                }
                repo.incrementCollected(playerId, nexoItemId, amount);
                totals.put(playerId, repo.getTotalCoins(playerId));
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Kon muntsaldo niet bijwerken voor " + playerId, ex);
            }
        });
    }

    /** Forceert een async herlees uit MongoDB (samengevoegd per speler zodat queries niet stapelen). */
    public void refresh(UUID playerId) {
        if (!loading.add(playerId)) {
            return;
        }
        io.execute(() -> {
            try {
                MongoPlayerCoinRepository repo = plugin.getDataManager().getPlayerCoinRepository();
                if (repo != null) {
                    totals.put(playerId, repo.getTotalCoins(playerId));
                }
            } catch (Exception ex) {
                plugin.getLogger().log(Level.FINE, "Coin-cache refresh mislukt voor " + playerId, ex);
            } finally {
                loading.remove(playerId);
            }
        });
    }

    public void invalidate(UUID playerId) {
        totals.remove(playerId);
    }

    public void invalidateAll() {
        totals.clear();
    }
}
