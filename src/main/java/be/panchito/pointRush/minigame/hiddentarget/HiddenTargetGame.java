package be.panchito.pointRush.minigame.hiddentarget;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.history.EventHistoryEntry;
import be.panchito.pointRush.history.EventHistoryManager;
import be.panchito.pointRush.storage.DataManager;
import be.panchito.pointRush.team.Team;
import be.panchito.pointRush.team.TeamManager;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.MinigameText;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hidden Target: elke speler krijgt een geheim target (niet van je team) met kompas.
 * Kill zoveel mogelijk targets in de tijd; als jouw hunter je killt verlies je een punt.
 */
public final class HiddenTargetGame {

    public enum State { IDLE, STARTING, RUNNING }

    public static final int COUNTDOWN_SECONDS = 10;
    public static final int WIN_BONUS_POINTS = 75;
    public static final int COMPASS_SLOT = 8;

    private final PointRush plugin;
    private final HiddenTargetConfig config;
    private final TeamManager teamManager;
    private final DataManager dataManager;
    private final EventHistoryManager historyManager;
    private final HiddenTargetScoreboard scoreboard;

    private State state = State.IDLE;
    private final Map<UUID, HiddenTargetPlayerState> players = new HashMap<>();
    /** Laatste geldige aanvaller per slachtoffer (fallback als getKiller() null is). */
    private final Map<UUID, UUID> lastDamagers = new HashMap<>();

    private long countdownEndsMs = 0L;
    private long eventStartedAtMs = 0L;
    private long runEndsAtMs = 0L;
    private long lastTickMs = 0L;
    private boolean historyRecorded = false;

    private BukkitTask tickTask;

    public HiddenTargetGame(PointRush plugin, HiddenTargetConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.teamManager = plugin.getTeamManager();
        this.dataManager = plugin.getDataManager();
        this.historyManager = plugin.getEventHistoryManager();
        this.scoreboard = new HiddenTargetScoreboard(plugin, this);
    }

    public State getState() {
        return state;
    }

    public HiddenTargetConfig getConfig() {
        return config;
    }

    public PointRush getPlugin() {
        return plugin;
    }

    public Collection<HiddenTargetPlayerState> getAllPlayerStates() {
        return Collections.unmodifiableCollection(players.values());
    }

    public boolean isParticipant(UUID id) {
        return players.containsKey(id);
    }

    public HiddenTargetPlayerState getPlayerState(UUID id) {
        return players.get(id);
    }

    public long getCountdownTimeLeftMs() {
        if (state != State.STARTING) return 0L;
        return Math.max(0L, countdownEndsMs - System.currentTimeMillis());
    }

    public long getRunTimeLeftMs() {
        if (state != State.RUNNING) return 0L;
        return Math.max(0L, runEndsAtMs - System.currentTimeMillis());
    }

    public String formatTime(long ms) {
        long totalSec = Math.max(0L, ms / 1000L);
        long min = totalSec / 60L;
        long sec = totalSec % 60L;
        return min + ":" + (sec < 10 ? "0" : "") + sec;
    }

    public boolean start() {
        if (state != State.IDLE) return false;
        if (!config.isReady()) return false;

        state = State.STARTING;
        countdownEndsMs = System.currentTimeMillis() + COUNTDOWN_SECONDS * 1000L;
        eventStartedAtMs = System.currentTimeMillis();
        lastTickMs = eventStartedAtMs;
        historyRecorded = false;
        lastDamagers.clear();

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getGameMode() == GameMode.CREATIVE || online.getGameMode() == GameMode.SPECTATOR) {
                online.sendMessage(Messages.warn("Je doet niet mee aan Hidden Target (creative/spectator)."));
                continue;
            }
            joinPlayer(online);
        }

        if (players.size() < 2) {
            Bukkit.broadcast(Messages.error("Hidden Target heeft minstens 2 spelers nodig."));
            cleanupAfterStop();
            return false;
        }

        if (!assignInitialTargets()) {
            Bukkit.broadcast(Messages.error("Kon geen geldige targets toewijzen (te weinig spelers buiten je team?)."));
            cleanupAfterStop();
            return false;
        }

        scoreboard.start();
        scoreboard.updateBossBar("start in " + formatTime(getCountdownTimeLeftMs()), 1.0f, BossBar.Color.YELLOW);
        broadcastTitle(
                Component.text(SmallText.of("HIDDEN TARGET"), NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Component.text(SmallText.of("volg je kompas · kill je target · geen teamgenoten"), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f);

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        return true;
    }

    public boolean stop() {
        if (state == State.IDLE) return false;
        if (state == State.RUNNING) {
            endEventWithTimeUp(true);
            return true;
        }
        recordHistoryEntry(null);
        cleanupAfterStop();
        Bukkit.broadcast(Messages.info("Hidden Target event afgelopen."));
        return true;
    }

    private void cleanupAfterStop() {
        state = State.IDLE;
        cancelTask(tickTask);
        tickTask = null;
        scoreboard.stop();

        Title endTitle = Title.title(
                Component.text(SmallText.of("event afgelopen"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("teleport terug naar je startlocatie"), NamedTextColor.GRAY)
        );
        for (Map.Entry<UUID, HiddenTargetPlayerState> entry : new ArrayList<>(players.entrySet())) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                p.showTitle(endTitle);
                restorePlayer(p, entry.getValue(), true);
            }
        }
        players.clear();
        lastDamagers.clear();
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            try {
                task.cancel();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    private void joinPlayer(Player player) {
        ItemStack[] inv = player.getInventory().getContents();
        ItemStack[] saved = new ItemStack[inv.length];
        for (int i = 0; i < inv.length; i++) {
            saved[i] = inv[i] != null ? inv[i].clone() : null;
        }
        HiddenTargetPlayerState ps = new HiddenTargetPlayerState(
                player.getUniqueId(),
                player.getLocation().clone(),
                player.getGameMode(),
                saved
        );
        players.put(player.getUniqueId(), ps);

        player.getInventory().clear();
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFallDistance(0f);

        giveKit(player);
        scoreboard.attach(player);
        player.sendMessage(Messages.info("Hidden Target start binnenkort — volg je kompas naar je geheime target!"));
    }

    public void giveKit(Player player) {
        player.getInventory().clear();

        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        sword.editMeta(meta -> {
            meta.displayName(Component.text(SmallText.of("Jager zwaard"), NamedTextColor.RED, TextDecoration.BOLD));
            meta.addEnchant(Enchantment.SHARPNESS, 1, true);
            meta.setUnbreakable(true);
        });

        ItemStack bow = new ItemStack(Material.BOW);
        bow.editMeta(meta -> {
            meta.displayName(Component.text(SmallText.of("Jager boog"), NamedTextColor.GOLD, TextDecoration.BOLD));
            meta.addEnchant(Enchantment.POWER, 1, true);
            meta.setUnbreakable(true);
        });

        ItemStack compass = new ItemStack(Material.COMPASS);
        compass.editMeta(meta -> {
            meta.displayName(Component.text(SmallText.of("Doel kompas"), NamedTextColor.DARK_RED, TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text(SmallText.of("wijst naar je geheime target"), NamedTextColor.GRAY)
            ));
            meta.setUnbreakable(true);
        });

        player.getInventory().setItem(0, sword);
        player.getInventory().setItem(1, bow);
        player.getInventory().setItem(2, new ItemStack(Material.ARROW, 32));
        player.getInventory().setItem(3, new ItemStack(Material.GOLDEN_APPLE, 3));
        player.getInventory().setItem(4, new ItemStack(Material.COOKED_BEEF, 16));
        player.getInventory().setItem(COMPASS_SLOT, compass);

        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        switch (state) {
            case STARTING -> tickStarting(now);
            case RUNNING -> tickRunning(now);
            default -> {
            }
        }
        lastTickMs = now;
    }

    private void tickStarting(long now) {
        long left = Math.max(0L, countdownEndsMs - now);
        float progress = left / (float) (COUNTDOWN_SECONDS * 1000L);
        scoreboard.updateBossBar("start in " + formatTime(left), progress, BossBar.Color.YELLOW);
        if (left <= 0) {
            beginRun();
        }
    }

    private void beginRun() {
        state = State.RUNNING;
        lastTickMs = System.currentTimeMillis();
        runEndsAtMs = lastTickMs + config.getDurationMs();

        broadcastTitle(
                MinigameText.goTitle(),
                Component.text(SmallText.of("jacht geopend · " + config.getDurationMinutes() + " min"), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.6f);
        launchFireworkAtSpawn(FireworkEffect.Type.BALL_LARGE, Color.RED, Color.MAROON);
    }

    private void tickRunning(long now) {
        if (now >= runEndsAtMs) {
            endEventWithTimeUp(false);
            return;
        }

        processRespawns(now);
        updateCompasses();
        updateBossBar(now);
        broadcastSpectatorActionBars(now);
    }

    private void updateCompasses() {
        for (HiddenTargetPlayerState ps : players.values()) {
            if (!ps.isAlive()) continue;
            UUID targetId = ps.getTargetId();
            if (targetId == null) continue;

            Player hunter = Bukkit.getPlayer(ps.getUuid());
            Player target = Bukkit.getPlayer(targetId);
            if (hunter == null || target == null || !target.isOnline()) continue;
            if (!players.containsKey(targetId)) continue;

            HiddenTargetPlayerState targetPs = players.get(targetId);
            if (targetPs == null || !targetPs.isAlive()) {
                assignTarget(ps.getUuid());
                continue;
            }

            hunter.setCompassTarget(target.getLocation());

            double dist = hunter.getLocation().distance(target.getLocation());
            hunter.sendActionBar(Component.text()
                    .append(Component.text(SmallText.of("target "), NamedTextColor.DARK_RED, TextDecoration.BOLD))
                    .append(Component.text(formatDistance(dist), NamedTextColor.GOLD))
                    .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(SmallText.of("kills " + ps.getTargetKills()), NamedTextColor.GREEN))
                    .append(Component.text("  ·  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(formatTime(getRunTimeLeftMs()), NamedTextColor.GRAY))
                    .build());
        }
    }

    private String formatDistance(double blocks) {
        if (blocks >= 1000) {
            return String.format("%.1fkm", blocks / 1000.0);
        }
        return (int) Math.round(blocks) + "m";
    }

    private void updateBossBar(long now) {
        long left = Math.max(0L, runEndsAtMs - now);
        float progress = Math.min(1f, left / (float) config.getDurationMs());
        scoreboard.updateBossBar("jacht · rest " + formatTime(left), progress, BossBar.Color.RED);
    }

    private void processRespawns(long now) {
        for (HiddenTargetPlayerState ps : players.values()) {
            if (ps.isAlive() || ps.getRespawnAtMs() <= 0L) continue;
            if (now < ps.getRespawnAtMs()) continue;

            Player player = Bukkit.getPlayer(ps.getUuid());
            if (player == null) continue;

            ps.setAlive(true);
            ps.setRespawnAtMs(0L);

            Location respawn = config.getSpawn();
            if (respawn == null) {
                respawn = ps.getSavedLocation();
            }
            if (respawn != null && respawn.getWorld() != null) {
                player.teleport(respawn);
            }
            giveKit(player);
            updateCompassFor(player, ps.getTargetId());

            player.showTitle(Title.title(
                    Component.text(SmallText.of("RESPAWN"), NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text(SmallText.of("jacht je target!"), NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(800), Duration.ofMillis(200))
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        }
    }

    private void broadcastSpectatorActionBars(long now) {
        for (HiddenTargetPlayerState ps : players.values()) {
            if (ps.isAlive() || ps.getRespawnAtMs() <= 0L) continue;
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p == null) continue;
            long left = Math.max(0L, ps.getRespawnAtMs() - now);
            p.sendActionBar(Component.text()
                    .append(Component.text(SmallText.of("dood · respawn in "), NamedTextColor.GRAY))
                    .append(Component.text(formatTime(left), NamedTextColor.GOLD, TextDecoration.BOLD))
                    .build());
        }
    }

    public void recordDamager(UUID victimId, UUID attackerId) {
        if (state != State.RUNNING) return;
        if (!players.containsKey(victimId) || !players.containsKey(attackerId)) return;
        if (victimId.equals(attackerId)) return;
        if (sameTeam(victimId, attackerId)) return;
        lastDamagers.put(victimId, attackerId);
    }

    /**
     * Called when a participant dies. Resolves target-kill scoring and respawn.
     */
    public void handleDeath(Player victim, Player killer) {
        if (state != State.RUNNING) return;

        HiddenTargetPlayerState victimPs = players.get(victim.getUniqueId());
        if (victimPs == null || !victimPs.isAlive()) return;

        UUID victimId = victim.getUniqueId();
        killer = resolveKiller(victim, killer);

        victimPs.setAlive(false);
        victimPs.incrementDeaths();
        victimPs.setRespawnAtMs(System.currentTimeMillis() + config.getRespawnMs());

        UUID reassignedHunter = null;
        boolean hunted = false;
        if (killer != null) {
            HiddenTargetPlayerState killerPs = players.get(killer.getUniqueId());
            if (killerPs != null && killerPs.isAlive()) {
                UUID killerTarget = killerPs.getTargetId();
                if (killerTarget != null && killerTarget.equals(victimId)) {
                    hunted = true;
                    onTargetKill(killer, killerPs, victim, victimPs);
                    reassignedHunter = killer.getUniqueId();
                }
            }
        }

        reassignHuntersOfVictim(victimId, reassignedHunter);

        victim.getInventory().clear();
        victim.setHealth(20.0);
        victim.setGameMode(GameMode.SPECTATOR);
        victim.setFireTicks(0);
        victim.setFallDistance(0f);

        Location loc = victim.getLocation();
        loc.getWorld().spawnParticle(Particle.SMOKE, loc, 20, 0.4, 0.5, 0.4, 0.02);
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_DEATH, 1.0f, 1.0f);

        if (hunted) {
            victim.showTitle(Title.title(
                    Component.text(SmallText.of("GEJAAGD!"), NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text(SmallText.of("-" + config.getHuntedPenalty() + " pt · respawn "
                            + config.getRespawnSeconds() + "s"), NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1500), Duration.ofMillis(300))
            ));
        } else {
            victim.showTitle(Title.title(
                    Component.text(SmallText.of("DOOD"), NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text(SmallText.of("respawn " + config.getRespawnSeconds() + "s"), NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1200), Duration.ofMillis(300))
            ));
        }
    }

    private void onTargetKill(Player killer, HiddenTargetPlayerState killerPs,
                              Player victim, HiddenTargetPlayerState victimPs) {
        int killPts = config.getKillPoints();
        int penalty = config.getHuntedPenalty();

        killerPs.incrementTargetKills();
        killerPs.addPointsEarned(killPts);
        awardPoints(killer, killPts);

        victimPs.incrementHuntedDeaths();
        victimPs.addPointsLost(penalty);
        deductPoints(victim, penalty);

        killer.showTitle(Title.title(
                Component.text(SmallText.of("DOEL NEER!"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("+" + killPts + " pt · nieuwe target"), NamedTextColor.GREEN),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1400), Duration.ofMillis(300))
        ));
        killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);

        Component msg = Component.text()
                .append(Component.text(killer.getName(), NamedTextColor.WHITE))
                .append(Component.text(SmallText.of(" heeft een target geëlimineerd! "), NamedTextColor.GRAY))
                .append(Component.text("+" + killPts + " pt", NamedTextColor.GREEN))
                .build();
        Bukkit.broadcast(Messages.PREFIX.append(msg));

        assignTarget(killer.getUniqueId());
    }

    private void awardPoints(Player player, int amount) {
        Team team = teamManager.getTeamOfPlayer(player.getUniqueId());
        if (team != null) {
            team.addPoints(amount);
            dataManager.save();
        }
    }

    private void deductPoints(Player player, int amount) {
        Team team = teamManager.getTeamOfPlayer(player.getUniqueId());
        if (team != null) {
            team.removePoints(amount);
            dataManager.save();
        }
    }

    private boolean assignInitialTargets() {
        List<UUID> ids = new ArrayList<>(players.keySet());
        if (ids.size() < 2) return false;

        for (int attempt = 0; attempt < 200; attempt++) {
            Collections.shuffle(ids);
            Map<UUID, UUID> assignment = new HashMap<>();
            boolean valid = true;

            for (int i = 0; i < ids.size(); i++) {
                UUID hunter = ids.get(i);
                UUID target = ids.get((i + 1) % ids.size());
                if (hunter.equals(target) || sameTeam(hunter, target)) {
                    valid = false;
                    break;
                }
                assignment.put(hunter, target);
            }

            if (valid) {
                applyAssignments(assignment);
                return true;
            }
        }

        Map<UUID, UUID> fallback = new HashMap<>();
        for (UUID hunter : ids) {
            UUID target = pickRandomTarget(hunter, fallback.values());
            if (target == null) {
                return false;
            }
            fallback.put(hunter, target);
        }
        applyAssignments(fallback);
        return true;
    }

    private void applyAssignments(Map<UUID, UUID> assignment) {
        for (Map.Entry<UUID, UUID> e : assignment.entrySet()) {
            HiddenTargetPlayerState ps = players.get(e.getKey());
            if (ps != null) {
                ps.setTargetId(e.getValue());
                Player hunter = Bukkit.getPlayer(e.getKey());
                if (hunter != null) {
                    updateCompassFor(hunter, e.getValue());
                }
            }
        }
    }

    private Player resolveKiller(Player victim, Player killerFromEvent) {
        UUID victimId = victim.getUniqueId();
        if (killerFromEvent != null && players.containsKey(killerFromEvent.getUniqueId())) {
            lastDamagers.remove(victimId);
            return killerFromEvent;
        }
        UUID damagerId = lastDamagers.remove(victimId);
        if (damagerId == null || !players.containsKey(damagerId)) {
            return null;
        }
        Player damager = Bukkit.getPlayer(damagerId);
        return damager != null && damager.isOnline() ? damager : null;
    }

    private void reassignHuntersOfVictim(UUID victimId, UUID alreadyReassigned) {
        for (HiddenTargetPlayerState ps : players.values()) {
            if (!ps.isAlive()) continue;
            if (ps.getUuid().equals(alreadyReassigned)) continue;
            UUID targetId = ps.getTargetId();
            if (targetId != null && targetId.equals(victimId)) {
                assignTarget(ps.getUuid());
            }
        }
    }

    public void assignTarget(UUID hunterId) {
        HiddenTargetPlayerState hunter = players.get(hunterId);
        if (hunter == null || !hunter.isAlive()) return;

        UUID target = pickRandomTarget(hunterId, Set.of());
        hunter.setTargetId(target);

        Player hunterPlayer = Bukkit.getPlayer(hunterId);
        if (hunterPlayer != null) {
            if (target != null) {
                hunterPlayer.sendMessage(Messages.success("Nieuwe target — volg je kompas!"));
                updateCompassFor(hunterPlayer, target);
            } else {
                hunterPlayer.sendMessage(Messages.warn("Geen geldige targets meer — wacht op respawns."));
            }
        }
    }

    private UUID pickRandomTarget(UUID hunterId, Collection<UUID> avoidDuplicates) {
        Set<UUID> taken = new HashSet<>(avoidDuplicates);
        List<UUID> candidates = new ArrayList<>();

        for (HiddenTargetPlayerState other : players.values()) {
            if (!other.isAlive()) continue;
            if (other.getUuid().equals(hunterId)) continue;
            if (sameTeam(hunterId, other.getUuid())) continue;
            if (taken.contains(other.getUuid())) continue;
            candidates.add(other.getUuid());
        }

        if (candidates.isEmpty()) {
            for (HiddenTargetPlayerState other : players.values()) {
                if (!other.isAlive()) continue;
                if (other.getUuid().equals(hunterId)) continue;
                if (sameTeam(hunterId, other.getUuid())) continue;
                candidates.add(other.getUuid());
            }
        }

        if (candidates.isEmpty()) return null;
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private boolean sameTeam(UUID a, UUID b) {
        Team teamA = teamManager.getTeamOfPlayer(a);
        Team teamB = teamManager.getTeamOfPlayer(b);
        return teamA != null && teamB != null && teamA.getId().equals(teamB.getId());
    }

    private void updateCompassFor(Player hunter, UUID targetId) {
        if (targetId == null) return;
        Player target = Bukkit.getPlayer(targetId);
        if (target != null && target.isOnline()) {
            hunter.setCompassTarget(target.getLocation());
        }
    }

    private void endEventWithTimeUp(boolean forcedStop) {
        if (state != State.RUNNING) {
            if (forcedStop) {
                recordHistoryEntry(null);
                cleanupAfterStop();
            }
            return;
        }

        List<HiddenTargetPlayerState> ranking = new ArrayList<>(players.values());
        ranking.sort(Comparator.comparingInt(HiddenTargetPlayerState::getTargetKills).reversed()
                .thenComparingInt(HiddenTargetPlayerState::netScore).reversed());

        HiddenTargetPlayerState top = ranking.isEmpty() ? null : ranking.get(0);
        int topKills = top != null ? top.getTargetKills() : 0;

        List<UUID> winners = new ArrayList<>();
        if (topKills > 0) {
            for (HiddenTargetPlayerState ps : ranking) {
                if (ps.getTargetKills() != topKills) {
                    break;
                }
                winners.add(ps.getUuid());
                awardWinBonus(ps.getUuid());
            }
        }

        Component winnerLine = buildWinnerLine(top, topKills, ranking, winners.size() > 1);
        launchFireworkAtSpawn(FireworkEffect.Type.BALL_LARGE, Color.fromRGB(255, 215, 0), Color.RED);
        broadcastTitle(
                Component.text(SmallText.of("TIJD OP!"), NamedTextColor.GOLD, TextDecoration.BOLD),
                winnerLine
        );
        Bukkit.broadcast(Messages.PREFIX.append(winnerLine));

        List<UUID> winnerIds = winners;
        recordHistoryEntry(winnerIds);
        state = State.STARTING;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != State.IDLE) {
                cleanupAfterStop();
                Bukkit.broadcast(Messages.info("Hidden Target event afgelopen."));
            }
        }, 80L);
    }

    private void awardWinBonus(UUID playerId) {
        Team team = teamManager.getTeamOfPlayer(playerId);
        if (team != null) {
            team.addPoints(WIN_BONUS_POINTS);
            dataManager.save();
        }
        HiddenTargetPlayerState ps = players.get(playerId);
        if (ps != null) {
            ps.addPointsEarned(WIN_BONUS_POINTS);
        }
    }

    private Component buildWinnerLine(HiddenTargetPlayerState top, int topKills,
                                      List<HiddenTargetPlayerState> ranking, boolean tied) {
        if (top == null || topKills <= 0) {
            return Component.text(SmallText.of("geen targets geëlimineerd"), NamedTextColor.GRAY);
        }
        if (tied) {
            var builder = Component.text()
                    .append(Component.text(SmallText.of("gelijkspel · "), NamedTextColor.GOLD, TextDecoration.BOLD));
            boolean first = true;
            for (HiddenTargetPlayerState ps : ranking) {
                if (ps.getTargetKills() != topKills) {
                    break;
                }
                if (!first) {
                    builder.append(Component.text(SmallText.of(" & "), NamedTextColor.DARK_GRAY));
                }
                first = false;
                Player p = Bukkit.getPlayer(ps.getUuid());
                String name = p != null ? p.getName() : ps.getUuid().toString().substring(0, 8);
                Team team = teamManager.getTeamOfPlayer(ps.getUuid());
                builder.append(Component.text(name, team != null ? team.getColor() : NamedTextColor.WHITE, TextDecoration.BOLD));
            }
            builder.append(Component.text(SmallText.of(" · " + topKills + " kills"), NamedTextColor.GOLD));
            return builder.build();
        }

        Player p = Bukkit.getPlayer(top.getUuid());
        String name = p != null ? p.getName() : top.getUuid().toString().substring(0, 8);
        Team team = teamManager.getTeamOfPlayer(top.getUuid());

        var builder = Component.text()
                .append(Component.text(SmallText.of("top hunter: "), NamedTextColor.GRAY))
                .append(Component.text(name, team != null ? team.getColor() : NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(SmallText.of(" · " + topKills + " kills"), NamedTextColor.GOLD));
        if (team != null) {
            builder.append(Component.text(SmallText.of(" · +" + WIN_BONUS_POINTS + " bonus"), NamedTextColor.GREEN));
        }
        if (ranking.size() > 1) {
            HiddenTargetPlayerState second = ranking.get(1);
            Player p2 = Bukkit.getPlayer(second.getUuid());
            String name2 = p2 != null ? p2.getName() : second.getUuid().toString().substring(0, 8);
            builder.append(Component.text(SmallText.of(" · #2 "), NamedTextColor.DARK_GRAY))
                    .append(Component.text(name2, NamedTextColor.WHITE))
                    .append(Component.text(" " + second.getTargetKills(), NamedTextColor.GRAY));
        }
        return builder.build();
    }

    private void recordHistoryEntry(List<UUID> winners) {
        if (historyManager == null || historyRecorded || players.isEmpty()) return;
        historyRecorded = true;

        Set<UUID> winnerSet = winners != null ? new HashSet<>(winners) : Set.of();

        List<HiddenTargetPlayerState> sorted = new ArrayList<>(players.values());
        sorted.sort(Comparator.comparingInt(HiddenTargetPlayerState::getTargetKills).reversed()
                .thenComparingInt(HiddenTargetPlayerState::netScore).reversed()
                .thenComparingInt(HiddenTargetPlayerState::getDeaths));

        List<EventHistoryEntry.Placement> placements = new ArrayList<>();
        int rank = 1;
        for (HiddenTargetPlayerState ps : sorted) {
            Player p = Bukkit.getPlayer(ps.getUuid());
            String name = p != null ? p.getName() : ps.getUuid().toString().substring(0, 8);
            Team team = teamManager.getTeamOfPlayer(ps.getUuid());
            String detail = ps.getTargetKills() + " kills · " + ps.getHuntedDeaths() + " gejaagd · "
                    + ps.getDeaths() + " deaths";
            if (winnerSet.contains(ps.getUuid())) {
                detail = "winnaar · " + detail;
            }
            placements.add(new EventHistoryEntry.Placement(
                    rank++,
                    ps.getUuid(),
                    name,
                    team != null ? team.getId() : null,
                    team != null ? team.getName() : null,
                    team != null ? team.getColor().toString() : null,
                    ps.getPointsEarned(),
                    detail
            ));
        }
        if (placements.isEmpty()) return;

        long started = eventStartedAtMs > 0 ? eventStartedAtMs : System.currentTimeMillis();
        historyManager.record(new EventHistoryEntry(
                UUID.randomUUID().toString(),
                "hiddentarget",
                started,
                System.currentTimeMillis(),
                placements
        ));
    }

    public void removeParticipant(Player player, boolean teleport) {
        HiddenTargetPlayerState ps = players.remove(player.getUniqueId());
        if (ps == null) return;
        scoreboard.detach(player);
        restorePlayer(player, ps, teleport);

        if (players.isEmpty() && state != State.IDLE) {
            stop();
        }
    }

    private void restorePlayer(Player player, HiddenTargetPlayerState ps, boolean teleport) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            try {
                player.setSpectatorTarget(null);
            } catch (Throwable ignored) {
            }
        }
        player.getInventory().clear();
        player.setGameMode(ps.getSavedGameMode());
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFallDistance(0f);

        ItemStack[] saved = ps.getSavedInventory();
        if (saved != null) {
            player.getInventory().setContents(saved);
        }

        if (teleport) {
            Location loc = ps.getSavedLocation();
            if (loc != null && loc.getWorld() != null) {
                player.teleport(loc);
            }
        }
    }

    private void launchFireworkAtSpawn(FireworkEffect.Type type, Color primary, Color fade) {
        Location spawn = config.getSpawn();
        if (spawn == null || spawn.getWorld() == null) return;
        Firework fw = spawn.getWorld().spawn(spawn, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .with(type)
                .withColor(primary)
                .withFade(fade)
                .build());
        meta.setPower(1);
        fw.setFireworkMeta(meta);
        Bukkit.getScheduler().runTaskLater(plugin, fw::detonate, 2L);
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
