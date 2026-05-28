package be.panchito.pointRush.random;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.minigame.MinigameRegistry;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Draait een visueel "rad" voor alle spelers en start een willekeurige,
 * klaarstaande minigame.
 */
public final class RandomEventService {

    private final PointRush plugin;
    private volatile boolean spinning;
    private BukkitTask spinTask;
    private BossBar wheelBar;

    public RandomEventService(PointRush plugin) {
        this.plugin = plugin;
    }

    public boolean isSpinning() {
        return spinning;
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

    public boolean spin(CommandSender initiator) {
        if (spinning) {
            initiator.sendMessage(Messages.error("Er wordt al een random event-rad gedraaid."));
            return false;
        }
        if (isAnyMinigameActive()) {
            initiator.sendMessage(Messages.error("Er loopt al een minigame — stop die eerst."));
            return false;
        }

        List<MinigameRegistry.RandomCandidate> ready = new ArrayList<>();
        for (MinigameRegistry.RandomCandidate c : MinigameRegistry.randomCandidates(plugin)) {
            if (c.ready()) {
                ready.add(c);
            }
        }
        if (ready.isEmpty()) {
            initiator.sendMessage(Messages.error("Geen minigame is klaar om te starten. Stel minstens één arena in."));
            return false;
        }

        MinigameRegistry.RandomCandidate winner = ready.get(ThreadLocalRandom.current().nextInt(ready.size()));
        spinning = true;
        runWheelAnimation(ready, winner, initiator);
        return true;
    }

    public void shutdown() {
        cancelSpinTask();
        hideWheelBar();
        spinning = false;
    }

    private void runWheelAnimation(List<MinigameRegistry.RandomCandidate> pool,
                                   MinigameRegistry.RandomCandidate winner,
                                   CommandSender initiator) {
        int extraSpins = 18 + ThreadLocalRandom.current().nextInt(8);
        MinigameRegistry.RandomCandidate[] sequence = new MinigameRegistry.RandomCandidate[extraSpins + 1];
        for (int i = 0; i < extraSpins; i++) {
            sequence[i] = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        }
        sequence[extraSpins] = winner;

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

        scheduleWheelStep(sequence, 0, initiator);
    }

    private void scheduleWheelStep(MinigameRegistry.RandomCandidate[] sequence, int step,
                                   CommandSender initiator) {
        if (step >= sequence.length) {
            finishSpin(sequence[sequence.length - 1], initiator);
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

        long delayTicks = remaining > 8 ? 2L : remaining > 3 ? 5L : remaining > 1 ? 10L : 18L;
        spinTask = Bukkit.getScheduler().runTaskLater(plugin, () ->
                scheduleWheelStep(sequence, step + 1, initiator), delayTicks);
    }

    private void finishSpin(MinigameRegistry.RandomCandidate winner, CommandSender initiator) {
        cancelSpinTask();
        hideWheelBar();

        Component winLine = Component.text()
                .append(Component.text(SmallText.of("het wordt "), NamedTextColor.GRAY))
                .append(Component.text(SmallText.of(winner.displayName()), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("!", NamedTextColor.GRAY))
                .build();

        broadcastTitle(
                Component.text(SmallText.of(winner.displayName()), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("start over een moment..."), NamedTextColor.GREEN)
        );
        Bukkit.broadcast(Messages.PREFIX.append(winLine));
        playSoundAll(Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.0f);
        playSoundAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boolean started = winner.start();
            spinning = false;
            if (started) {
                Bukkit.broadcast(Messages.success(SmallText.of(winner.displayName()) + " is gestart!"));
                initiator.sendMessage(Messages.success("Random event gestart: " + winner.displayName()));
            } else {
                Bukkit.broadcast(Messages.error(SmallText.of(winner.displayName())
                        + " kon niet starten (genoeg spelers? arena ok?)."));
                initiator.sendMessage(Messages.error("Start mislukt na het rad."));
            }
        }, 30L);
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
