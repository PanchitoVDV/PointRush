package be.panchito.pointRush.random;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.minigame.MinigameRegistry;
import be.panchito.pointRush.storage.mongo.MongoScheduledEventRepository;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plant random events voor de volgende dag via {@code /randomevent} en start ze
 * pas met {@code /event start}. Synchroniseert spin-state naar MongoDB voor het website-rad.
 */
public final class RandomEventService {

    private static final ZoneId SCHEDULE_ZONE = ZoneId.of("Europe/Amsterdam");

    private final PointRush plugin;
    private final MongoScheduledEventRepository scheduleRepo;
    private EventScheduleState scheduleState;

    private volatile boolean spinning;
    private BukkitTask spinTask;
    private BossBar wheelBar;

    public RandomEventService(PointRush plugin, MongoScheduledEventRepository scheduleRepo) {
        this.plugin = plugin;
        this.scheduleRepo = scheduleRepo;
        this.scheduleState = new EventScheduleState(defaultPoolIds(), null, SpinState.idle());
    }

    public void loadSchedule() {
        if (scheduleRepo == null) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                EventScheduleState loaded = scheduleRepo.loadOrDefault(defaultPoolIds());
                Bukkit.getScheduler().runTask(plugin, () -> scheduleState = loaded);
            } catch (Exception ex) {
                plugin.getLogger().warning("Kon event schedule niet laden: " + ex.getMessage());
            }
        });
    }

    public boolean isSpinning() {
        return spinning;
    }

    public UpcomingEvent upcoming() {
        UpcomingEvent u = scheduleState.upcoming();
        if (u == null || u.status() != UpcomingEvent.UpcomingStatus.SCHEDULED) {
            return null;
        }
        return u;
    }

    public List<String> poolEventIds() {
        return scheduleState.pool();
    }

    public List<String> listReadyEventNames() {
        List<String> names = new ArrayList<>();
        for (MinigameRegistry.RandomCandidate c : MinigameRegistry.randomCandidates(plugin)) {
            if (c.ready()) {
                names.add(c.displayName());
            }
        }
        return names;
    }

    public List<String> listPoolDisplayNames() {
        List<String> names = new ArrayList<>();
        for (String id : scheduleState.pool()) {
            names.add(MinigameRegistry.displayName(id));
        }
        return names;
    }

    /**
     * Draait het rad, kiest een event uit de pool voor morgen en synchroniseert naar de website.
     */
    public boolean spin(CommandSender initiator) {
        return spinInternal(initiator, false);
    }

    /**
     * Draait het rad opnieuw, ook als er al een event gepland staat (overschrijft de planning).
     */
    public boolean forceSpin(CommandSender initiator) {
        return spinInternal(initiator, true);
    }

    private boolean spinInternal(CommandSender initiator, boolean force) {
        if (spinning) {
            initiator.sendMessage(Messages.error("Er wordt al een random event-rad gedraaid."));
            return false;
        }
        if (isAnyMinigameActive()) {
            initiator.sendMessage(Messages.error("Er loopt al een minigame — stop die eerst."));
            return false;
        }

        UpcomingEvent existing = upcoming();
        if (existing != null && !force) {
            initiator.sendMessage(Messages.error("Er staat al een event gepland ("
                    + existing.displayName() + " op " + existing.scheduledFor()
                    + "). Gebruik /randomevent forcespin om opnieuw te kiezen."));
            return false;
        }
        if (existing != null) {
            scheduleState.setUpcoming(null);
            initiator.sendMessage(Messages.warn("Vorige planning (" + existing.displayName()
                    + ") wordt overschreven."));
        }

        List<MinigameRegistry.RandomCandidate> eligible = candidatesInPool();
        if (eligible.isEmpty()) {
            refillPoolIfEmpty();
            eligible = candidatesInPool();
        }
        if (eligible.isEmpty()) {
            initiator.sendMessage(Messages.error("Geen minigame is klaar in de pool. Stel minstens één arena in."));
            return false;
        }

        MinigameRegistry.RandomCandidate winner = eligible.get(
                ThreadLocalRandom.current().nextInt(eligible.size()));
        spinning = true;
        runWheelAnimation(eligible, winner, initiator);
        return true;
    }

    /**
     * Start het geplande event en haalt het uit de pool.
     */
    public boolean startScheduled(CommandSender initiator) {
        UpcomingEvent scheduled = upcoming();
        if (scheduled == null) {
            initiator.sendMessage(Messages.error("Geen event gepland. Gebruik eerst /randomevent."));
            return false;
        }
        if (isAnyMinigameActive()) {
            initiator.sendMessage(Messages.error("Er loopt al een minigame — stop die eerst."));
            return false;
        }

        MinigameRegistry.RandomCandidate candidate = findCandidate(scheduled.eventId());
        if (candidate == null) {
            initiator.sendMessage(Messages.error("Onbekend event: " + scheduled.eventId()));
            return false;
        }
        if (!candidate.ready()) {
            initiator.sendMessage(Messages.error(scheduled.displayName()
                    + " is niet klaar om te starten (arena ok? geen andere game actief?)."));
            return false;
        }

        boolean started = candidate.start();
        if (!started) {
            initiator.sendMessage(Messages.error(scheduled.displayName()
                    + " kon niet starten (genoeg spelers? arena ok?)."));
            return false;
        }

        scheduleState.removeFromPool(scheduled.eventId());
        scheduleState.setUpcoming(null);
        persistScheduleAsync();
        refillPoolIfEmpty();

        Bukkit.broadcast(Messages.success(SmallText.of(scheduled.displayName()) + " is gestart!"));
        initiator.sendMessage(Messages.success("Gepland event gestart: " + scheduled.displayName()
                + " (uit pool gehaald)."));
        return true;
    }

    public void shutdown() {
        cancelSpinTask();
        hideWheelBar();
        spinning = false;
        persistScheduleAsync();
    }

    private void runWheelAnimation(List<MinigameRegistry.RandomCandidate> eligible,
                                   MinigameRegistry.RandomCandidate winner,
                                   CommandSender initiator) {
        int extraSpins = 42 + ThreadLocalRandom.current().nextInt(14);
        MinigameRegistry.RandomCandidate[] sequence = new MinigameRegistry.RandomCandidate[extraSpins + 1];
        List<String> sequenceIds = new ArrayList<>(extraSpins + 1);
        for (int i = 0; i < extraSpins; i++) {
            MinigameRegistry.RandomCandidate pick = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
            sequence[i] = pick;
            sequenceIds.add(pick.id());
        }
        sequence[extraSpins] = winner;
        sequenceIds.add(winner.id());

        List<String> candidateIds = new ArrayList<>(scheduleState.pool());
        List<String> candidateNames = new ArrayList<>(candidateIds.size());
        for (String id : candidateIds) {
            candidateNames.add(MinigameRegistry.displayName(id));
        }

        long spinStart = System.currentTimeMillis();
        scheduleState.setSpin(new SpinState(
                true,
                spinStart,
                candidateIds,
                candidateNames,
                sequenceIds,
                winner.id()
        ));
        persistScheduleAsync();

        wheelBar = BossBar.bossBar(
                Component.text(SmallText.of("het rad draait..."), NamedTextColor.GOLD),
                0.05f,
                BossBar.Color.YELLOW,
                BossBar.Overlay.NOTCHED_20
        );
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showBossBar(wheelBar);
        }

        broadcastTitle(
                Component.text(SmallText.of("RANDOM EVENT"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("het rad draait..."), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.2f);

        scheduleWheelStep(sequence, 0, winner, initiator);
    }

    private void scheduleWheelStep(MinigameRegistry.RandomCandidate[] sequence, int step,
                                   MinigameRegistry.RandomCandidate winner,
                                   CommandSender initiator) {
        if (step >= sequence.length) {
            finishSpin(winner, initiator);
            return;
        }

        MinigameRegistry.RandomCandidate current = sequence[step];
        float progress = (step + 1) / (float) sequence.length;
        wheelBar.name(Component.text()
                .append(Component.text("▶ ", NamedTextColor.GOLD))
                .append(Component.text(SmallText.of(current.displayName()), NamedTextColor.WHITE, TextDecoration.BOLD))
                .build());
        wheelBar.progress(Math.max(0.05f, Math.min(1f, progress)));

        int remaining = sequence.length - step - 1;
        float pitch = 0.85f + (step / (float) sequence.length) * 0.9f;
        playSoundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.55f, pitch);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendActionBar(Component.text()
                    .append(Component.text(SmallText.of("random event · "), NamedTextColor.GRAY))
                    .append(Component.text(SmallText.of(current.displayName()), NamedTextColor.YELLOW))
                    .build());
        }

        long delayTicks = remaining > 14 ? 3L : remaining > 8 ? 6L : remaining > 3 ? 12L : remaining > 1 ? 18L : 28L;
        spinTask = Bukkit.getScheduler().runTaskLater(plugin, () ->
                scheduleWheelStep(sequence, step + 1, winner, initiator), delayTicks);
    }

    private void finishSpin(MinigameRegistry.RandomCandidate winner, CommandSender initiator) {
        cancelSpinTask();
        hideWheelBar();

        LocalDate tomorrow = LocalDate.now(SCHEDULE_ZONE).plusDays(1);
        long now = System.currentTimeMillis();

        scheduleState.setUpcoming(new UpcomingEvent(
                winner.id(),
                winner.displayName(),
                tomorrow,
                now,
                UpcomingEvent.UpcomingStatus.SCHEDULED
        ));
        scheduleState.setSpin(new SpinState(
                false,
                scheduleState.spin().startedAtMillis(),
                scheduleState.spin().candidateIds(),
                scheduleState.spin().candidateNames(),
                scheduleState.spin().sequence(),
                winner.id()
        ));
        persistScheduleAsync();

        Component winLine = Component.text()
                .append(Component.text(SmallText.of("morgen wordt het "), NamedTextColor.GRAY))
                .append(Component.text(SmallText.of(winner.displayName()), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("!", NamedTextColor.GRAY))
                .build();

        broadcastTitle(
                Component.text(SmallText.of(winner.displayName()), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("morgen op de planning"), NamedTextColor.GREEN)
        );
        Bukkit.broadcast(Messages.PREFIX.append(winLine));
        playSoundAll(Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.0f);
        playSoundAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);

        spinning = false;
        initiator.sendMessage(Messages.success("Event gepland voor " + tomorrow + ": "
                + winner.displayName() + ". Start met /event start."));
        initiator.sendMessage(Messages.info("Het resultaat staat op de website onder Upcoming events."));
    }

    private List<MinigameRegistry.RandomCandidate> candidatesInPool() {
        List<MinigameRegistry.RandomCandidate> out = new ArrayList<>();
        for (MinigameRegistry.RandomCandidate c : MinigameRegistry.randomCandidates(plugin)) {
            if (scheduleState.pool().contains(c.id()) && c.ready()) {
                out.add(c);
            }
        }
        return out;
    }

    private void refillPoolIfEmpty() {
        if (!scheduleState.pool().isEmpty()) {
            return;
        }
        scheduleState.resetPool(defaultPoolIds());
        persistScheduleAsync();
        plugin.getLogger().info("Event-pool was leeg — opnieuw gevuld met alle minigames.");
    }

    private MinigameRegistry.RandomCandidate findCandidate(String id) {
        for (MinigameRegistry.RandomCandidate c : MinigameRegistry.randomCandidates(plugin)) {
            if (c.id().equals(id)) {
                return c;
            }
        }
        return null;
    }

    private List<String> defaultPoolIds() {
        return new ArrayList<>(MinigameRegistry.events().keySet());
    }

    private void persistScheduleAsync() {
        if (scheduleRepo == null) {
            return;
        }
        EventScheduleState snapshot = scheduleState;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                scheduleRepo.save(snapshot);
            } catch (Exception ex) {
                plugin.getLogger().warning("Kon event schedule niet opslaan: " + ex.getMessage());
            }
        });
    }

    private void cancelSpinTask() {
        if (spinTask != null) {
            try {
                spinTask.cancel();
            } catch (IllegalStateException ignored) {
            }
            spinTask = null;
        }
    }

    private void hideWheelBar() {
        if (wheelBar == null) return;
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.hideBossBar(wheelBar);
        }
        wheelBar = null;
    }

    private boolean isAnyMinigameActive() {
        return MinigameRegistry.anyActive(plugin) || spinning;
    }

    private void broadcastTitle(Component title, Component subtitle) {
        Title t = Title.title(
                title,
                subtitle,
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1600), Duration.ofMillis(300))
        );
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showTitle(t);
        }
    }

    private void playSoundAll(Sound sound, float volume, float pitch) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), sound, volume, pitch);
        }
    }
}
