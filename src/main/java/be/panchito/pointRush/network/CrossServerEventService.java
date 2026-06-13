package be.panchito.pointRush.network;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.minigame.MinigameRegistry;
import be.panchito.pointRush.storage.mongo.MongoLiveEventRepository;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Cross-server orkestratie van events via het gedeelde {@link MongoLiveEventRepository live_event} doc.
 *
 * <p>Rollen (uit {@link NetworkSettings}):
 * <ul>
 *   <li><b>survival</b> (orchestrator): schrijft bij een event-start een PENDING-doc en stuurt de
 *       deelnemers naar de events-server. Draait de game zelf niet.</li>
 *   <li><b>events</b> (host): ziet het PENDING-doc, start na een grace-periode de echte minigame lokaal,
 *       markeert RUNNING, en stuurt de spelers terug zodra de game (via {@link MinigameRegistry#anyActive})
 *       weer idle is.</li>
 *   <li><b>standalone</b>: service is inert; alles draait lokaal zoals voorheen.</li>
 * </ul>
 *
 * <p>De poll-lus leest het doc async en verwerkt transitions op de hoofd-thread, zodat dit ook werkt op
 * een standalone Mongo (geen change streams nodig).
 */
public final class CrossServerEventService {

    private static final long POLL_INTERVAL_TICKS = 20L;

    private final PointRush plugin;
    private final NetworkSettings settings;
    private final ProxyTransport transport;
    private final MongoLiveEventRepository repo;

    private BukkitTask pollTask;
    private volatile LiveEventState cached;

    // --- events-host state machine (alleen hoofd-thread) ---
    private String hostingEventId;
    private boolean gameStarted;
    private boolean cleaningUp;
    private BukkitTask graceTask;

    public CrossServerEventService(PointRush plugin, NetworkSettings settings,
                                   ProxyTransport transport, MongoLiveEventRepository repo) {
        this.plugin = plugin;
        this.settings = settings;
        this.transport = transport;
        this.repo = repo;
    }

    /** Start de poll-lus (alleen wanneer netwerk-modus aan staat). */
    public void start() {
        if (!settings.isEnabled() || repo == null) {
            return;
        }
        pollTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::poll, 40L, POLL_INTERVAL_TICKS);
        plugin.getLogger().info("Cross-server event-service actief (rol " + settings.getRole() + ").");
    }

    public void shutdown() {
        cancel(pollTask);
        pollTask = null;
        cancel(graceTask);
        graceTask = null;
    }

    /** True wanneer er ergens in het netwerk een event loopt (gedeeld doc bestaat). */
    public boolean isRemoteLive() {
        return settings.isEnabled() && cached != null;
    }

    /** Het lopende live event, of {@code null}. */
    public LiveEventState current() {
        return cached;
    }

    /**
     * Vangnet op de events-server: als een speler hier binnenkomt terwijl er geen event loopt (bv. na een
     * disconnect/rejoin of een verdwaalde connect), wordt die na een korte vertraging teruggestuurd naar
     * survival. Tijdens een lopend/aankomend event blijft iedereen staan. No-op buiten de events-rol.
     */
    public void handleJoin(Player player) {
        if (!settings.isEventsHost()) {
            return;
        }
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline() || p.getGameMode() == GameMode.CREATIVE) {
                return; // offline of staff in creative → laat staan
            }
            if (cached != null || MinigameRegistry.anyActive(plugin)) {
                return; // event loopt of komt eraan → speler hoort hier
            }
            p.sendMessage(Messages.info("Geen event actief — je gaat terug naar de hoofdserver."));
            transport.sendToSurvival(p);
        }, 60L);
    }

    /**
     * Vraagt (vanaf de survival-server) een event aan: schrijft het PENDING-doc en stuurt de deelnemers
     * naar de events-server. De game wordt daar gestart. Returnt {@code false} als deze server niet de
     * survival-orchestrator is.
     */
    public boolean requestStart(String minigameId) {
        if (!settings.isSurvivalHost() || repo == null) {
            return false;
        }
        if (cached != null) {
            return false; // er loopt al een event — niet overschrijven
        }
        String eventId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        String host = settings.getEventsServer();

        // Optimistisch lokaal markeren zodat isRemoteLive() meteen blokkeert (geen dubbele start).
        this.cached = new LiveEventState(eventId, minigameId, LiveEventState.Phase.PENDING, host, now);
        runAsync(() -> repo.setPending(eventId, minigameId, host, now),
                "Kon live_event PENDING niet schrijven");

        String display = MinigameRegistry.displayName(minigameId);
        Bukkit.broadcast(Messages.PREFIX.append(Component.text()
                .append(Component.text(SmallText.of(display + " start! "), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(SmallText.of("je wordt naar de event-server gestuurd..."),
                        NamedTextColor.GRAY))
                .build()));

        int sent = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (transport.sendToEvents(p)) {
                sent++;
            }
        }
        plugin.getLogger().info("Cross-server event aangevraagd: " + minigameId
                + " (" + sent + " spelers naar " + host + ").");
        return true;
    }

    // ---- poll-lus ----

    private void poll() {
        LiveEventState state;
        try {
            state = repo.get();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Kon live_event niet lezen.", ex);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> onPoll(state));
    }

    private void onPoll(LiveEventState state) {
        if (!plugin.isEnabled()) {
            return;
        }
        this.cached = state;
        if (settings.isEventsHost()) {
            handleEventsHost(state);
        }
    }

    private void handleEventsHost(LiveEventState state) {
        if (cleaningUp) {
            return;
        }
        String myServer = settings.getEventsServer();

        if (hostingEventId == null) {
            if (state != null && state.phase() == LiveEventState.Phase.PENDING
                    && myServer.equals(state.hostServer())) {
                beginHosting(state);
            } else if (state != null && state.phase() == LiveEventState.Phase.RUNNING
                    && myServer.equals(state.hostServer()) && !MinigameRegistry.anyActive(plugin)) {
                // RUNNING-doc zonder lokale game = stale (host gecrasht tijdens event) → opruimen.
                plugin.getLogger().warning("Stale live_event (" + state.minigame()
                        + ") gevonden zonder lopende game — opruimen.");
                finishHosting("Vorig event opgeruimd.");
            }
            return;
        }

        // We hosten momenteel hostingEventId.
        if (state == null || !hostingEventId.equals(state.eventId())) {
            // Doc verdween of werd vervangen; laat tracking los als er lokaal niets meer draait.
            if (!MinigameRegistry.anyActive(plugin)) {
                resetHosting();
            }
            return;
        }
        if (gameStarted && !MinigameRegistry.anyActive(plugin)) {
            finishHosting("Event afgelopen — terug naar de hoofdserver.");
        }
    }

    private void beginHosting(LiveEventState state) {
        hostingEventId = state.eventId();
        gameStarted = false;
        int grace = settings.getJoinGraceSeconds();

        Bukkit.broadcast(Messages.PREFIX.append(Component.text()
                .append(Component.text(SmallText.of(MinigameRegistry.displayName(state.minigame()) + " "),
                        NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(SmallText.of("start over " + grace + "s — maak je klaar!"),
                        NamedTextColor.GRAY))
                .build()));

        graceTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            graceTask = null;
            if (!state.eventId().equals(hostingEventId)) {
                return; // ondertussen afgebroken
            }
            boolean ok = MinigameRegistry.startMinigame(plugin, state.minigame());
            if (ok) {
                gameStarted = true;
                runAsync(() -> repo.markRunning(state.eventId()), "Kon live_event RUNNING niet markeren");
                plugin.getLogger().info("Cross-server event gestart: " + state.minigame() + ".");
            } else {
                plugin.getLogger().warning("Kon cross-server event niet starten: " + state.minigame()
                        + " (arena klaar op deze server?).");
                finishHosting("Event kon niet starten — terug naar de hoofdserver.");
            }
        }, Math.max(1L, grace * 20L));
    }

    private void finishHosting(String message) {
        if (cleaningUp) {
            return;
        }
        cleaningUp = true;
        cancel(graceTask);
        graceTask = null;
        gameStarted = false;
        hostingEventId = null;

        runAsync(repo::clear, "Kon live_event niet wissen");

        long delay = Math.max(0L, settings.getReturnDelaySeconds() * 20L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() == GameMode.CREATIVE) {
                    continue; // staff in creative blijft
                }
                p.sendMessage(Messages.info(message));
                transport.sendToSurvival(p);
            }
            cleaningUp = false;
        }, delay);
    }

    private void resetHosting() {
        hostingEventId = null;
        gameStarted = false;
        cancel(graceTask);
        graceTask = null;
    }

    private void runAsync(Runnable task, String errorMessage) {
        if (!plugin.isEnabled()) {
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

    private void cancel(BukkitTask task) {
        if (task != null) {
            try {
                task.cancel();
            } catch (IllegalStateException ignored) {
            }
        }
    }
}
