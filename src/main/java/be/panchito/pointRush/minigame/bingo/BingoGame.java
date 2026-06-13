package be.panchito.pointRush.minigame.bingo;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.minigame.MinigameStartEffects;
import be.panchito.pointRush.history.EventHistoryEntry;
import be.panchito.pointRush.history.EventHistoryManager;
import be.panchito.pointRush.storage.DataManager;
import be.panchito.pointRush.team.Team;
import be.panchito.pointRush.team.TeamManager;
import be.panchito.pointRush.util.LobbyWorld;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Team bingo: gedeelde kaart, map-GUI, inventory-sync tussen teammates.
 */
public final class BingoGame {

    public enum State { IDLE, STARTING, RUNNING }

    public static final int[] TOP_TEAM_POINTS = { 100, 80, 60, 40, 25 };

    private final PointRush plugin;
    private final BingoConfig config;
    private final TeamManager teamManager;
    private final DataManager dataManager;
    private final EventHistoryManager historyManager;
    private final BingoScoreboard scoreboard;

    private State state = State.IDLE;
    private Material[] sharedCardTiles = new Material[0];
    private final Map<UUID, BingoTeamProgress> teamProgress = new LinkedHashMap<>();
    private final Set<UUID> participants = new HashSet<>();
    private final Map<UUID, Inventory> openGuis = new HashMap<>();
    /** Locatie waar elke deelnemer stond vóór de start, om na afloop terug te teleporteren. */
    private final Map<UUID, Location> savedLocations = new HashMap<>();

    private long eventStartedAtMs = 0L;
    private long runEndsAtMs = 0L;
    private boolean historyRecorded = false;

    /** Buckets in volgorde waarin ze hun kaart compleet maakten. */
    private final List<UUID> finishedBuckets = new ArrayList<>();
    /** Werkelijk toegekende punten per bucket (voor history). */
    private final Map<UUID, Integer> awardedPoints = new HashMap<>();

    private BukkitTask tickTask;
    /** Fallback-timer die het event toch start mocht een teleport-callback uitblijven. */
    private BukkitTask startGuardTask;

    public BingoGame(PointRush plugin, BingoConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.teamManager = plugin.getTeamManager();
        this.dataManager = plugin.getDataManager();
        this.historyManager = plugin.getEventHistoryManager();
        this.scoreboard = new BingoScoreboard(plugin, this);
    }

    public State getState() {
        return state;
    }

    public PointRush getPlugin() {
        return plugin;
    }

    public BingoConfig getConfig() {
        return config;
    }

    public boolean isParticipant(UUID id) {
        return participants.contains(id);
    }

    public Material[] getSharedCardTiles() {
        Material[] copy = new Material[sharedCardTiles.length];
        System.arraycopy(sharedCardTiles, 0, copy, 0, sharedCardTiles.length);
        return copy;
    }

    public BingoTeamProgress getTeamProgress(UUID bucketId) {
        return teamProgress.get(bucketId);
    }

    public CollectionView teams() {
        return new CollectionView(teamProgress);
    }

    public long getRunTimeLeftMs() {
        if (state != State.RUNNING) return 0L;
        return Math.max(0L, runEndsAtMs - System.currentTimeMillis());
    }

    public UUID bucketFor(UUID playerId) {
        Team team = teamManager.getTeamOfPlayer(playerId);
        return team != null ? team.getId() : playerId;
    }

    public String bucketLabel(UUID bucketId) {
        Team team = teamManager.getTeam(bucketId);
        if (team != null) return team.getName();
        Player p = Bukkit.getPlayer(bucketId);
        return p != null ? p.getName() : bucketId.toString().substring(0, 8);
    }

    public void trackOpenGui(UUID playerId, Inventory inv) {
        openGuis.put(playerId, inv);
    }

    public void untrackOpenGui(UUID playerId) {
        openGuis.remove(playerId);
    }

    public boolean start() {
        if (state != State.IDLE) return false;
        if (!config.isReady()) return false;

        BingoCard card = BingoCard.generate(config.getMaterialPool());
        sharedCardTiles = card.copyTiles();
        teamProgress.clear();
        participants.clear();
        openGuis.clear();
        savedLocations.clear();
        finishedBuckets.clear();
        awardedPoints.clear();
        historyRecorded = false;

        List<Player> eligible = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getGameMode() == GameMode.CREATIVE || online.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (!LobbyWorld.contains(plugin, online)) {
                continue;
            }
            eligible.add(online);
        }

        if (eligible.isEmpty()) {
            Bukkit.broadcast(Messages.error("Bingo heeft minstens 1 speler nodig."));
            sharedCardTiles = new Material[0];
            return false;
        }

        // Fase 1: iedereen eerst (async, cross-world) naar de bingo-wereld teleporteren. Pas wanneer
        // ze er allemaal zijn vangt fase 2 (beginEvent) aan — zo wordt het basisaantal opgenomen in de
        // bingo-wereld en telt loot van vóór het event niet mee.
        state = State.STARTING;
        int[] pending = { eligible.size() };
        for (Player player : eligible) {
            admit(player, () -> {
                if (--pending[0] <= 0) {
                    beginEvent();
                }
            });
        }
        // Veiligheidsnet: mocht een teleport-callback uitblijven, start het event toch.
        startGuardTask = Bukkit.getScheduler().runTaskLater(plugin, this::beginEvent, 100L);
        return true;
    }

    /**
     * Fase 2: alle spelers zijn in de bingo-wereld; nu pas begint het event en het tellen. Idempotent
     * via de {@link State#STARTING}-check, want zowel de teleport-latch als de fallback kan dit triggeren.
     */
    private void beginEvent() {
        if (state != State.STARTING) {
            return;
        }
        cancelTask(startGuardTask);
        startGuardTask = null;

        boolean anyOnline = false;
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                anyOnline = true;
                break;
            }
        }
        if (!anyOnline) {
            Bukkit.broadcast(Messages.error("Bingo afgebroken: geen spelers meer aanwezig."));
            cleanup();
            return;
        }

        state = State.RUNNING;
        eventStartedAtMs = System.currentTimeMillis();
        runEndsAtMs = eventStartedAtMs + config.getDurationMs();

        scoreboard.start();
        broadcastStart();
        syncAllTeams();

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        MinigameStartEffects.onStarted(plugin);
    }

    public boolean stop() {
        if (state == State.IDLE) return false;
        if (state == State.STARTING) {
            // Start was nog bezig (spelers aan het teleporteren): netjes afbreken.
            cleanup();
            Bukkit.broadcast(Messages.info("Bingo geannuleerd tijdens de start."));
            return true;
        }
        endEvent(true);
        return true;
    }

    /**
     * Laat een speler toe tot het event: registreer + teleporteer naar de bingo-wereld. Het basisaantal
     * en de kaart worden pas in {@link #onPlayerArrived} (ná de teleport) toegekend. {@code afterArrival}
     * draait nadat de speler is aangekomen (gebruikt door de start-latch).
     */
    private void admit(Player player, Runnable afterArrival) {
        participants.add(player.getUniqueId());
        savedLocations.put(player.getUniqueId(), player.getLocation().clone());
        UUID bucket = bucketFor(player.getUniqueId());
        teamProgress.computeIfAbsent(bucket, id -> new BingoTeamProgress(id, bucketLabel(id)));

        Runnable onArrive = () -> {
            onPlayerArrived(player);
            if (afterArrival != null) {
                afterArrival.run();
            }
        };

        Location spawn = config.getSpawn();
        if (spawn != null && spawn.getWorld() != null) {
            plugin.getTeleporter().teleport(player, spawn, false, onArrive);
        } else {
            onArrive.run();
        }
    }

    /**
     * Afgehandeld zodra de speler in de bingo-wereld is aangekomen: leg het basisaantal vast (zodat
     * bestaande loot niet meetelt), geef de kaart en koppel het scorebord. Bij een laat-toegetreden
     * speler tijdens een lopend event wordt meteen gesynchroniseerd.
     */
    private void onPlayerArrived(Player player) {
        if (!player.isOnline() || !participants.contains(player.getUniqueId())) {
            return;
        }
        // Begin met een lege inventory zodat er geen items van een vorig event achterblijven. Veilig:
        // de bingo-wereld heeft een eigen Multiverse-Inventories-profiel, dus dit raakt survival niet.
        // Draait ná de teleport (onArrive), dus ná de per-wereld inventory-swap. De baseline wordt
        // hierna op een schone inventory vastgelegd.
        player.getInventory().clear();

        UUID bucket = bucketFor(player.getUniqueId());
        BingoTeamProgress progress = teamProgress.get(bucket);
        if (progress != null) {
            progress.addBaseline(player);
        }
        giveMap(player);
        scoreboard.attach(player);
        if (state == State.RUNNING) {
            syncTeam(bucket);
        }
    }

    private void giveMap(Player player) {
        ItemStack map = BingoItems.createMap(plugin);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(map);
        if (!leftover.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(), map);
        }
    }

    /** True als de speler al een bingo-kaart in zijn inventory heeft. */
    private boolean hasBingoMap(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (BingoItems.isBingoMap(plugin, item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Geeft de speler zijn bingo-kaart terug als die ontbreekt — bv. na dood/respawn. Doet niets als
     * er geen event loopt, de speler niet meedoet of hij de kaart nog heeft (geen duplicaten).
     */
    public void giveMapIfMissing(Player player) {
        if (state != State.RUNNING || !participants.contains(player.getUniqueId())) {
            return;
        }
        if (!hasBingoMap(player)) {
            giveMap(player);
        }
    }

    private void broadcastStart() {
        Title title = Title.title(
                Component.text(SmallText.of("BINGO"), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                Component.text(SmallText.of("zelfde kaart · team sync · 1 uur"), NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(400))
        );
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.showTitle(title);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            }
        }
        Bukkit.broadcast(Messages.PREFIX.append(Component.text()
                .append(Component.text(SmallText.of("Bingo gestart! "), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .append(Component.text(SmallText.of("Klik je bingo-kaart (map) · verzamel met je team · "
                        + config.getDurationMinutes() + " min"), NamedTextColor.GRAY))
                .build()));
    }

    private void tick() {
        if (state != State.RUNNING) return;
        if (System.currentTimeMillis() >= runEndsAtMs) {
            endEvent(false);
            return;
        }
        syncAllTeams();
        scoreboard.updateBossBar();
    }

    public void syncAllTeams() {
        for (UUID bucketId : new ArrayList<>(teamProgress.keySet())) {
            syncTeam(bucketId);
        }
    }

    public void syncTeam(UUID bucketId) {
        if (state != State.RUNNING) return;
        BingoTeamProgress progress = teamProgress.get(bucketId);
        if (progress == null) return;

        List<Player> members = onlineMembers(bucketId);
        boolean advanced = progress.syncFromPlayers(members, sharedCardTiles);

        if (advanced) {
            for (Player member : members) {
                member.sendActionBar(Component.text()
                        .append(Component.text(SmallText.of("bingo voortgang "), NamedTextColor.LIGHT_PURPLE))
                        .append(Component.text(progress.countFound() + "/" + BingoGrid.TOTAL,
                                NamedTextColor.GREEN, TextDecoration.BOLD))
                        .build());
                member.playSound(member.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.3f);
            }
            refreshTeamGuis(bucketId, progress);
        }

        if (progress.isComplete() && progress.getCompletedAtMs() == 0L) {
            progress.setCompletedAtMs(System.currentTimeMillis());
            onTeamComplete(bucketId, progress);
        }
    }

    private List<Player> onlineMembers(UUID bucketId) {
        List<Player> out = new ArrayList<>();
        Team team = teamManager.getTeam(bucketId);
        if (team != null) {
            for (UUID memberId : team.getMembers()) {
                if (!participants.contains(memberId)) continue;
                Player p = Bukkit.getPlayer(memberId);
                if (p != null) out.add(p);
            }
        } else if (participants.contains(bucketId)) {
            Player solo = Bukkit.getPlayer(bucketId);
            if (solo != null) out.add(solo);
        }
        return out;
    }

    private void refreshTeamGuis(UUID bucketId, BingoTeamProgress progress) {
        Team team = teamManager.getTeam(bucketId);
        Set<UUID> viewers = new HashSet<>();
        if (team != null) {
            viewers.addAll(team.getMembers());
        } else {
            viewers.add(bucketId);
        }
        for (UUID viewerId : viewers) {
            Inventory inv = openGuis.get(viewerId);
            if (inv == null) continue;
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) continue;
            BingoGui.refresh(inv, this, progress);
        }
    }

    private void onTeamComplete(UUID bucketId, BingoTeamProgress progress) {
        if (progress.isCompletionAnnounced()) return;
        progress.setCompletionAnnounced(true);

        Team team = teamManager.getTeam(bucketId);
        int placementIndex = finishedBuckets.size();
        finishedBuckets.add(bucketId);

        int pts = 0;
        if (team != null) {
            if (placementIndex < TOP_TEAM_POINTS.length) {
                pts += TOP_TEAM_POINTS[placementIndex];
            }
            if (pts > 0) {
                dataManager.addTeamPoints(team, pts);
            }
        }
        awardedPoints.put(bucketId, pts);

        var builder = Component.text()
                .append(Component.text(SmallText.of("BINGO! "), NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(bucketLabel(bucketId),
                        team != null ? team.getColor() : NamedTextColor.GOLD, TextDecoration.BOLD));
        if (team != null && pts > 0) {
            builder.append(Component.text(SmallText.of(" · plek " + (placementIndex + 1) + " · +"
                    + pts + " pts"), NamedTextColor.GRAY));
        } else {
            builder.append(Component.text(SmallText.of(" · kaart compleet!"), NamedTextColor.GRAY));
        }
        Bukkit.broadcast(Messages.PREFIX.append(builder.build()));

        for (Player member : onlineMembers(bucketId)) {
            member.showTitle(Title.title(
                    Component.text(SmallText.of("BINGO!"), NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text(SmallText.of("kaart compleet"), NamedTextColor.GOLD)
            ));
            member.playSound(member.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
        refreshTeamGuis(bucketId, progress);

        finishAndTeleportTeam(bucketId);
    }

    /**
     * Brengt de teamleden terug naar hun opgeslagen startlocatie (de lobby) en haalt ze uit het
     * lopende event. De voortgang blijft bewaard voor de eindstand/history.
     */
    private void finishAndTeleportTeam(UUID bucketId) {
        for (Player member : onlineMembers(bucketId)) {
            Inventory open = openGuis.get(member.getUniqueId());
            if (open != null && member.getOpenInventory().getTopInventory().equals(open)) {
                member.closeInventory();
            }
            removeParticipant(member, true);
        }
    }

    /** Teleporteert {@code player} terug naar de locatie waar hij stond vóór de bingo-start. */
    private void returnToSaved(Player player) {
        Location saved = savedLocations.get(player.getUniqueId());
        if (saved == null || saved.getWorld() == null) {
            return;
        }
        try {
            plugin.getTeleporter().teleport(player, saved);
            player.setFallDistance(0f);
        } catch (Exception ignored) {
        }
    }

    private void endEvent(boolean forced) {
        syncAllTeams();

        List<Map.Entry<UUID, BingoTeamProgress>> remaining = remainingRanked();
        awardRemaining(remaining);
        recordHistory(remaining);

        Bukkit.broadcast(Messages.PREFIX.append(Component.text()
                .append(Component.text(SmallText.of(forced ? "Bingo gestopt." : "Bingo afgelopen! "),
                        NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .append(Component.text(SmallText.of("Top teams kregen punten."), NamedTextColor.GRAY))
                .build()));

        cleanup();
    }

    /** Teams die NIET compleet maakten, gesorteerd op gevonden vakken. */
    private List<Map.Entry<UUID, BingoTeamProgress>> remainingRanked() {
        List<Map.Entry<UUID, BingoTeamProgress>> remaining = new ArrayList<>();
        for (Map.Entry<UUID, BingoTeamProgress> e : teamProgress.entrySet()) {
            if (!finishedBuckets.contains(e.getKey())) {
                remaining.add(e);
            }
        }
        remaining.sort(Comparator.<Map.Entry<UUID, BingoTeamProgress>>comparingInt(
                e -> e.getValue().countFound()).reversed());
        return remaining;
    }

    private void awardRemaining(List<Map.Entry<UUID, BingoTeamProgress>> remaining) {
        int placed = finishedBuckets.size();
        for (Map.Entry<UUID, BingoTeamProgress> entry : remaining) {
            if (placed >= TOP_TEAM_POINTS.length) break;
            if (entry.getValue().countFound() <= 0) break;

            Team team = teamManager.getTeam(entry.getKey());
            if (team == null) continue;

            int pts = TOP_TEAM_POINTS[placed];
            dataManager.addTeamPoints(team, pts);
            awardedPoints.put(entry.getKey(), pts);
            placed++;

            Bukkit.broadcast(Messages.info("Top " + placed + ": team "
                    + team.getName() + " · " + entry.getValue().countFound()
                    + " vakken · +" + pts + " pts"));
        }
    }

    private void recordHistory(List<Map.Entry<UUID, BingoTeamProgress>> remaining) {
        if (historyManager == null || historyRecorded) return;
        historyRecorded = true;

        List<UUID> order = new ArrayList<>(finishedBuckets);
        for (Map.Entry<UUID, BingoTeamProgress> entry : remaining) {
            order.add(entry.getKey());
        }

        List<EventHistoryEntry.Placement> placements = new ArrayList<>();
        int rank = 1;
        for (UUID bucketId : order) {
            BingoTeamProgress prog = teamProgress.get(bucketId);
            if (prog == null) continue;
            Team team = teamManager.getTeam(bucketId);
            int pts = awardedPoints.getOrDefault(bucketId, 0);

            placements.add(new EventHistoryEntry.Placement(
                    rank++,
                    bucketId,
                    bucketLabel(bucketId),
                    team != null ? team.getId() : null,
                    team != null ? team.getName() : null,
                    team != null ? team.getColor().toString() : null,
                    pts,
                    prog.countFound() + "/" + BingoGrid.TOTAL + " vakken"
            ));
        }
        if (placements.isEmpty()) return;

        historyManager.record(new EventHistoryEntry(
                UUID.randomUUID().toString(),
                "bingo",
                eventStartedAtMs,
                System.currentTimeMillis(),
                placements
        ));
    }

    private void cleanup() {
        state = State.IDLE;
        cancelTask(tickTask);
        tickTask = null;
        cancelTask(startGuardTask);
        startGuardTask = null;
        scoreboard.stop();

        for (UUID id : new ArrayList<>(participants)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                returnToSaved(p);
                removeBingoMaps(p);
                scoreboard.detach(p);
            }
        }

        for (Inventory inv : openGuis.values()) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getOpenInventory().getTopInventory() == inv) {
                    online.closeInventory();
                }
            }
        }

        participants.clear();
        teamProgress.clear();
        openGuis.clear();
        savedLocations.clear();
        sharedCardTiles = new Material[0];
    }

    private void removeBingoMaps(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (BingoItems.isBingoMap(plugin, contents[i])) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    public void removeParticipant(Player player) {
        removeParticipant(player, false);
    }

    /**
     * Haalt {@code player} uit het event. Met {@code returnToLobby} wordt hij eerst teruggeteleporteerd
     * naar zijn opgeslagen startlocatie; bij een server-quit hoeft dat niet (speler is offline).
     */
    public void removeParticipant(Player player, boolean returnToLobby) {
        if (returnToLobby) {
            returnToSaved(player);
        }
        participants.remove(player.getUniqueId());
        savedLocations.remove(player.getUniqueId());
        untrackOpenGui(player.getUniqueId());
        removeBingoMaps(player);
        scoreboard.detach(player);
    }

    public void onPlayerJoin(Player player) {
        if (state != State.RUNNING) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (participants.contains(player.getUniqueId())) {
            giveMap(player);
            return;
        }
        if (finishedBuckets.contains(bucketFor(player.getUniqueId()))) {
            return;
        }
        // admit() teleporteert en synct ná aankomst (onPlayerArrived).
        admit(player, null);
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            try {
                task.cancel();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    public String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = (ms + 999) / 1000;
        long minutes = totalSec / 60;
        long seconds = totalSec % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /** Read-only view voor scoreboard. */
    public static final class CollectionView {
        private final Map<UUID, BingoTeamProgress> map;

        CollectionView(Map<UUID, BingoTeamProgress> map) {
            this.map = map;
        }

        public List<Map.Entry<UUID, BingoTeamProgress>> sortedByFound() {
            List<Map.Entry<UUID, BingoTeamProgress>> list = new ArrayList<>(map.entrySet());
            list.sort(Comparator.<Map.Entry<UUID, BingoTeamProgress>>comparingInt(
                    e -> e.getValue().countFound()).reversed());
            return list;
        }
    }
}
