package be.panchito.pointRush.minigame.koth;

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
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
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

/**
 * Wipeout-style KOTH: 30 minuten punten scoren op de hill (1 pt / minuut uncontested),
 * trident + knock items, power-spots met eenmalige troll-trucs.
 */
public final class KothGame {

    public enum State { IDLE, STARTING, RUNNING }

    public static final int COUNTDOWN_SECONDS = 10;
    public static final long SPECTATOR_RESPAWN_MS = 3L * 60L * 1000L;
    public static final int WIN_BONUS_POINTS = 150;
    public static final double SPOT_PICKUP_RADIUS = 1.8;

    private final PointRush plugin;
    private final KothConfig config;
    private final TeamManager teamManager;
    private final DataManager dataManager;
    private final EventHistoryManager historyManager;
    private final KothScoreboard scoreboard;

    private State state = State.IDLE;
    private final Map<UUID, KothPlayerState> players = new HashMap<>();
    /** Hill-punten per team-bucket tijdens dit event. */
    private final Map<UUID, Integer> hillScores = new HashMap<>();
    private final Map<UUID, Long> gadgetCooldownMap = new HashMap<>();
    private final Map<String, Long> spotCooldownUntil = new HashMap<>();
    private final Map<String, UUID> spotMarkerIds = new HashMap<>();

    private UUID controllingBucket = null;
    private long hillProgressMs = 0L;
    private long countdownEndsMs = 0L;
    private long eventStartedAtMs = 0L;
    private long runEndsAtMs = 0L;
    private long lastTickMs = 0L;
    private boolean historyRecorded = false;

    private BukkitTask tickTask;

    public KothGame(PointRush plugin, KothConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.teamManager = plugin.getTeamManager();
        this.dataManager = plugin.getDataManager();
        this.historyManager = plugin.getEventHistoryManager();
        this.scoreboard = new KothScoreboard(plugin, this);
    }

    public State getState() {
        return state;
    }

    public KothConfig getConfig() {
        return config;
    }

    public PointRush getPlugin() {
        return plugin;
    }

    public Collection<KothPlayerState> getAllPlayerStates() {
        return Collections.unmodifiableCollection(players.values());
    }

    public boolean isParticipant(UUID id) {
        return players.containsKey(id);
    }

    public KothPlayerState getPlayerState(UUID id) {
        return players.get(id);
    }

    public Map<UUID, Integer> getHillScores() {
        return Collections.unmodifiableMap(hillScores);
    }

    public Map<UUID, Long> getGadgetCooldownMap() {
        return gadgetCooldownMap;
    }

    public UUID getControllingBucket() {
        return controllingBucket;
    }

    public long getHillProgressMs() {
        return hillProgressMs;
    }

    public long getCountdownTimeLeftMs() {
        if (state != State.STARTING) return 0L;
        return Math.max(0L, countdownEndsMs - System.currentTimeMillis());
    }

    public long getRunTimeLeftMs() {
        if (state != State.RUNNING) return 0L;
        return Math.max(0L, runEndsAtMs - System.currentTimeMillis());
    }

    public UUID bucketFor(Player player) {
        return bucketFor(player.getUniqueId());
    }

    public UUID bucketFor(UUID playerId) {
        Team t = teamManager.getTeamOfPlayer(playerId);
        return t != null ? t.getId() : playerId;
    }

    public String bucketName(UUID bucketId) {
        Team t = teamManager.getTeam(bucketId);
        if (t != null) return t.getName();
        Player p = Bukkit.getPlayer(bucketId);
        return p != null ? p.getName() : bucketId.toString().substring(0, 8);
    }

    public boolean start() {
        if (state != State.IDLE) return false;
        if (!config.isReady()) return false;

        state = State.STARTING;
        countdownEndsMs = System.currentTimeMillis() + COUNTDOWN_SECONDS * 1000L;
        eventStartedAtMs = System.currentTimeMillis();
        lastTickMs = eventStartedAtMs;
        historyRecorded = false;
        hillScores.clear();
        gadgetCooldownMap.clear();
        spotCooldownUntil.clear();
        controllingBucket = null;
        hillProgressMs = 0L;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getGameMode() == GameMode.CREATIVE || online.getGameMode() == GameMode.SPECTATOR) {
                online.sendMessage(Messages.warn("Je doet niet mee aan KOTH (creative/spectator)."));
                continue;
            }
            joinPlayer(online);
        }

        if (players.size() < 2) {
            Bukkit.broadcast(Messages.error("KOTH heeft minstens 2 spelers nodig."));
            cleanupAfterStop();
            return false;
        }

        scoreboard.start();
        scoreboard.updateBossBar("countdown", 1.0f, BossBar.Color.YELLOW);
        broadcastTitle(
                Component.text(SmallText.of("KING OF THE HILL"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("wipeout · 1 pt per minuut op hill · 30 min"), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.4f);

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
        Bukkit.broadcast(Messages.info("KOTH event afgelopen."));
        return true;
    }

    private void cleanupAfterStop() {
        state = State.IDLE;
        cancelTask(tickTask);
        tickTask = null;
        scoreboard.stop();
        removeSpotMarkers();

        Title endTitle = Title.title(
                Component.text(SmallText.of("event afgelopen"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("teleport terug naar je startlocatie"), NamedTextColor.GRAY)
        );
        for (Map.Entry<UUID, KothPlayerState> entry : new ArrayList<>(players.entrySet())) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                p.showTitle(endTitle);
                restorePlayer(p, entry.getValue(), true);
            }
        }
        players.clear();
        hillScores.clear();
        gadgetCooldownMap.clear();
        spotCooldownUntil.clear();
        controllingBucket = null;
        hillProgressMs = 0L;
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
        KothPlayerState ps = new KothPlayerState(
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

        Location spawn = config.getSpawn();
        if (spawn != null) {
            player.teleport(spawn);
        }
        giveKit(player);
        scoreboard.attach(player);
        player.sendMessage(Messages.info("KOTH start binnenkort — cap de hill, knock elkaar eraf!"));
    }

    /** Wipeout kit: trident + knock items. */
    public void giveKit(Player player) {
        player.getInventory().clear();

        ItemStack trident = new ItemStack(Material.TRIDENT);
        trident.editMeta(meta -> {
            meta.displayName(Component.text(SmallText.of("Lanceer drietand"), NamedTextColor.AQUA, TextDecoration.BOLD));
            meta.addEnchant(Enchantment.LOYALTY, 3, true);
            meta.addEnchant(Enchantment.RIPTIDE, 2, true);
            meta.setUnbreakable(true);
        });

        ItemStack knockStick = new ItemStack(Material.STICK);
        knockStick.editMeta(meta -> {
            meta.displayName(Component.text(SmallText.of("Duw stok"), NamedTextColor.RED, TextDecoration.BOLD));
            meta.addEnchant(Enchantment.KNOCKBACK, 2, true);
            meta.setUnbreakable(true);
        });

        player.getInventory().setItem(0, trident);
        player.getInventory().setItem(1, knockStick);
        player.getInventory().setItem(2, new ItemStack(Material.WIND_CHARGE, 4));
        player.getInventory().setItem(3, new ItemStack(Material.SNOWBALL, 16));
        player.getInventory().setItem(4, new ItemStack(Material.EGG, 8));
        player.getInventory().setItem(8, new ItemStack(Material.GOLDEN_APPLE, 2));

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
        controllingBucket = null;
        hillProgressMs = 0L;

        spawnSpotMarkers();
        broadcastTitle(
                MinigameText.goTitle(),
                Component.text(SmallText.of("30 min · 1 pt/min op hill"), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.6f);
        launchFireworkAtSpawn(FireworkEffect.Type.BALL_LARGE, Color.ORANGE, Color.YELLOW);
    }

    private void tickRunning(long now) {
        long deltaMs = Math.max(0L, now - lastTickMs);
        if (deltaMs > 500L) deltaMs = 50L;

        if (now >= runEndsAtMs) {
            endEventWithTimeUp(false);
            return;
        }

        processRespawns(now);
        updateHillScoring(deltaMs);
        tickPowerSpots(now);
        updateBossBar(now);
        broadcastSpectatorActionBars(now);
    }

    private void updateHillScoring(long deltaMs) {
        Set<UUID> bucketsInHill = new HashSet<>();
        for (KothPlayerState ps : players.values()) {
            if (!ps.isAlive()) continue;
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p == null || p.getGameMode() != GameMode.SURVIVAL) continue;
            if (!config.contains(p.getLocation())) continue;
            bucketsInHill.add(bucketFor(p));
        }

        if (bucketsInHill.size() == 1) {
            UUID bucket = bucketsInHill.iterator().next();
            if (controllingBucket != null && !controllingBucket.equals(bucket)) {
                hillProgressMs = 0L;
            }
            controllingBucket = bucket;
            hillProgressMs += deltaMs;

            long interval = config.getPointIntervalMs();
            while (hillProgressMs >= interval) {
                awardHillPoint(bucket);
                hillProgressMs -= interval;
            }
        } else {
            if (controllingBucket != null) {
                hillProgressMs = 0L;
            }
            controllingBucket = null;
        }
    }

    private void awardHillPoint(UUID bucket) {
        int score = hillScores.getOrDefault(bucket, 0) + 1;
        hillScores.put(bucket, score);

        Team team = teamManager.getTeam(bucket);
        if (team != null) {
            team.addPoints(1);
            dataManager.save();
            for (KothPlayerState ps : players.values()) {
                if (!ps.isAlive()) continue;
                Team t = teamManager.getTeamOfPlayer(ps.getUuid());
                if (t != null && t.getId().equals(bucket)) {
                    ps.addPointsEarned(1);
                }
            }
        } else {
            KothPlayerState ps = players.get(bucket);
            if (ps != null) ps.addPointsEarned(1);
        }

        Component msg = Component.text()
                .append(Component.text(SmallText.of("hill punt! "), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(bucketName(bucket), teamColor(bucket), TextDecoration.BOLD))
                .append(Component.text(SmallText.of(" · " + score + " pts"), NamedTextColor.GRAY))
                .build();
        Bukkit.broadcast(Messages.PREFIX.append(msg));
        playSoundAll(Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

        Location center = hillCenter();
        if (center != null && center.getWorld() != null) {
            center.getWorld().spawnParticle(Particle.FLAME, center, 30, 1.5, 0.5, 1.5, 0.02);
            center.getWorld().playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        }
    }

    private Location hillCenter() {
        Location min = config.getRegionMin();
        Location max = config.getRegionMax();
        if (min == null || max == null || min.getWorld() == null) return config.getSpawn();
        return new Location(
                min.getWorld(),
                (min.getX() + max.getX()) / 2.0,
                (min.getY() + max.getY()) / 2.0 + 0.5,
                (min.getZ() + max.getZ()) / 2.0
        );
    }

    private NamedTextColor teamColor(UUID bucketId) {
        Team t = teamManager.getTeam(bucketId);
        return t != null ? t.getColor() : NamedTextColor.GOLD;
    }

    private void updateBossBar(long now) {
        long left = Math.max(0L, runEndsAtMs - now);
        float timeProgress = Math.min(1f, left / (float) config.getDurationMs());

        if (controllingBucket != null) {
            long interval = config.getPointIntervalMs();
            float pointProgress = Math.min(1f, hillProgressMs / (float) interval);
            String label = bucketName(controllingBucket) + " · punt in "
                    + formatTime(Math.max(0L, interval - hillProgressMs))
                    + " · rest " + formatTime(left);
            scoreboard.updateBossBar(label, pointProgress, BossBar.Color.GREEN);
        } else {
            scoreboard.updateBossBar(
                    SmallText.of("omstreden · rest " + formatTime(left)),
                    timeProgress,
                    BossBar.Color.RED
            );
        }
    }

    private void tickPowerSpots(long now) {
        for (KothPowerSpot spot : config.getPowerSpots()) {
            Long until = spotCooldownUntil.get(spot.getId());
            boolean available = until == null || now >= until;

            if (available && !spotMarkerIds.containsKey(spot.getId())) {
                spawnSpotMarker(spot);
            }

            Location loc = spot.getLocation();
            if (loc.getWorld() == null) continue;

            if (available) {
                loc.getWorld().spawnParticle(
                        Particle.HAPPY_VILLAGER,
                        loc.clone().add(0, 0.6, 0),
                        2, 0.25, 0.15, 0.25, 0.01
                );
            }
        }

        for (KothPlayerState ps : players.values()) {
            if (!ps.isAlive()) continue;
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p == null) continue;
            tryPickupSpot(p, now);
        }
    }

    public void tryPickupSpot(Player player, long now) {
        if (state != State.RUNNING) return;
        KothPlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isAlive()) return;

        Location playerLoc = player.getLocation();
        for (KothPowerSpot spot : config.getPowerSpots()) {
            Long until = spotCooldownUntil.get(spot.getId());
            if (until != null && until > now) continue;

            Location spotLoc = spot.getLocation();
            if (spotLoc.getWorld() != playerLoc.getWorld()) continue;
            if (spotLoc.distanceSquared(playerLoc) > SPOT_PICKUP_RADIUS * SPOT_PICKUP_RADIUS) continue;

            ItemStack item = spot.getType().createItem(plugin);
            giveSpotItem(player, item);

            spotCooldownUntil.put(spot.getId(), now + config.getSpotRespawnMs());
            removeSpotMarker(spot.getId());

            player.sendActionBar(Component.text()
                    .append(Component.text(SmallText.of("power spot! "), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                    .append(Component.text(SmallText.of(spot.getType().getLabel()), NamedTextColor.WHITE))
                    .build());
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.3f);
            spotLoc.getWorld().playSound(spotLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
            return;
        }
    }

    private void giveSpotItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
        if (!leftover.isEmpty()) {
            for (ItemStack stack : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            }
        }
    }

    private void spawnSpotMarkers() {
        removeSpotMarkers();
        long now = System.currentTimeMillis();
        for (KothPowerSpot spot : config.getPowerSpots()) {
            Long until = spotCooldownUntil.get(spot.getId());
            if (until != null && until > now) continue;
            spawnSpotMarker(spot);
        }
    }

    private void spawnSpotMarker(KothPowerSpot spot) {
        Location loc = spot.getLocation().clone().add(0, -0.4, 0);
        if (loc.getWorld() == null) return;

        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setInvulnerable(true);
            as.setCustomNameVisible(false);
            as.setSmall(true);
            as.getEquipment().setHelmet(new ItemStack(spot.getType().getMarkerMaterial()));
        });
        spotMarkerIds.put(spot.getId(), stand.getUniqueId());
    }

    private void removeSpotMarker(String spotId) {
        UUID entityId = spotMarkerIds.remove(spotId);
        if (entityId == null) return;
        for (World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(entityId);
            if (entity != null) {
                entity.remove();
                return;
            }
        }
    }

    private void removeSpotMarkers() {
        for (String spotId : new ArrayList<>(spotMarkerIds.keySet())) {
            removeSpotMarker(spotId);
        }
        spotMarkerIds.clear();
    }

    private void processRespawns(long now) {
        for (KothPlayerState ps : players.values()) {
            if (ps.isAlive() || ps.getRespawnAtMs() <= 0L) continue;
            if (now < ps.getRespawnAtMs()) continue;

            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p == null) continue;

            ps.setAlive(true);
            ps.setRespawnAtMs(0L);

            if (p.getGameMode() == GameMode.SPECTATOR) {
                try {
                    p.setSpectatorTarget(null);
                } catch (Throwable ignored) {
                }
            }
            for (Player other : Bukkit.getOnlinePlayers()) {
                p.showPlayer(plugin, other);
                other.showPlayer(plugin, p);
            }

            Location spawn = config.getSpawn();
            if (spawn != null) {
                p.teleport(spawn);
            }
            giveKit(p);

            Team team = teamManager.getTeamOfPlayer(p.getUniqueId());
            Component subtitle = team != null
                    ? Component.text(SmallText.of("team " + team.getName()), team.getColor())
                    : Component.text(SmallText.of("vecht opnieuw om de hill"), NamedTextColor.GRAY);
            p.showTitle(Title.title(
                    Component.text(SmallText.of("RESPAWN!"), NamedTextColor.GREEN, TextDecoration.BOLD),
                    subtitle,
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(200))
            ));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        }
    }

    private void broadcastSpectatorActionBars(long now) {
        for (KothPlayerState ps : players.values()) {
            if (ps.isAlive() || ps.getRespawnAtMs() <= 0L) continue;
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p == null) continue;
            long left = Math.max(0L, ps.getRespawnAtMs() - now);
            p.sendActionBar(Component.text()
                    .append(Component.text(SmallText.of("spectator · respawn in "), NamedTextColor.GRAY))
                    .append(Component.text(formatTime(left), NamedTextColor.GOLD, TextDecoration.BOLD))
                    .build());
        }
    }

    public void handleDeath(Player player) {
        if (state != State.RUNNING && state != State.STARTING) return;
        KothPlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isAlive()) return;

        ps.setAlive(false);
        ps.incrementDeaths();
        ps.setRespawnAtMs(System.currentTimeMillis() + SPECTATOR_RESPAWN_MS);

        player.getInventory().clear();
        player.setHealth(20.0);
        player.setGameMode(GameMode.SPECTATOR);
        player.setFireTicks(0);
        player.setFallDistance(0f);

        Location loc = player.getLocation();
        loc.getWorld().spawnParticle(Particle.SMOKE, loc, 20, 0.4, 0.5, 0.4, 0.02);
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_DEATH, 1.0f, 1.0f);

        Team team = teamManager.getTeamOfPlayer(player.getUniqueId());
        Component announce = team != null
                ? Component.text()
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .append(Component.text(SmallText.of(" is gevallen (team "), NamedTextColor.GRAY))
                .append(Component.text(SmallText.of(team.getName()), team.getColor()))
                .append(Component.text(SmallText.of(") · respawn 3m"), NamedTextColor.GRAY))
                .build()
                : Component.text()
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .append(Component.text(SmallText.of(" is gevallen · respawn 3m"), NamedTextColor.GRAY))
                .build();
        Bukkit.broadcast(Messages.PREFIX.append(announce));

        player.showTitle(Title.title(
                Component.text(SmallText.of("DOOD"), NamedTextColor.RED, TextDecoration.BOLD),
                Component.text(SmallText.of("spectator 3 minuten"), NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1500), Duration.ofMillis(300))
        ));
    }

    private void endEventWithTimeUp(boolean forcedStop) {
        if (state != State.RUNNING) {
            if (forcedStop) {
                recordHistoryEntry(null);
                cleanupAfterStop();
            }
            return;
        }

        List<Map.Entry<UUID, Integer>> ranking = new ArrayList<>(hillScores.entrySet());
        ranking.sort(Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed());

        UUID winnerBucket = ranking.isEmpty() ? null : ranking.get(0).getKey();
        int topScore = winnerBucket != null ? hillScores.get(winnerBucket) : 0;

        if (winnerBucket != null && topScore > 0) {
            Team team = teamManager.getTeam(winnerBucket);
            if (team != null) {
                team.addPoints(WIN_BONUS_POINTS);
                dataManager.save();
            }
        }

        Component winnerLine = buildWinnerLine(winnerBucket, topScore, ranking);

        launchFireworkAtSpawn(FireworkEffect.Type.BALL_LARGE, Color.fromRGB(255, 215, 0), Color.ORANGE);
        broadcastTitle(
                Component.text(SmallText.of("TIJD OP!"), NamedTextColor.GOLD, TextDecoration.BOLD),
                winnerLine
        );
        Bukkit.broadcast(Messages.PREFIX.append(winnerLine));

        List<UUID> winners = new ArrayList<>();
        if (winnerBucket != null) {
            if (teamManager.getTeam(winnerBucket) != null) {
                for (KothPlayerState ps : players.values()) {
                    Team t = teamManager.getTeamOfPlayer(ps.getUuid());
                    if (t != null && t.getId().equals(winnerBucket)) {
                        winners.add(ps.getUuid());
                    }
                }
            } else {
                winners.add(winnerBucket);
            }
        }

        recordHistoryEntry(winners);
        state = State.STARTING;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != State.IDLE) {
                cleanupAfterStop();
                Bukkit.broadcast(Messages.info("KOTH event afgelopen."));
            }
        }, 80L);
    }

    private Component buildWinnerLine(UUID winnerBucket, int topScore,
                                      List<Map.Entry<UUID, Integer>> ranking) {
        if (winnerBucket == null || topScore <= 0) {
            return Component.text(SmallText.of("geen hill punten — gelijkspel"), NamedTextColor.GRAY);
        }
        Team team = teamManager.getTeam(winnerBucket);
        var builder = Component.text()
                .append(Component.text(SmallText.of("winnaar: "), NamedTextColor.GRAY))
                .append(Component.text(bucketName(winnerBucket), teamColor(winnerBucket), TextDecoration.BOLD))
                .append(Component.text(SmallText.of(" · " + topScore + " hill pts"), NamedTextColor.GOLD));
        if (team != null) {
            builder.append(Component.text(SmallText.of(" · +" + WIN_BONUS_POINTS + " bonus"), NamedTextColor.GREEN));
        }
        if (ranking.size() > 1) {
            UUID second = ranking.get(1).getKey();
            builder.append(Component.text(SmallText.of(" · #2 "), NamedTextColor.DARK_GRAY))
                    .append(Component.text(bucketName(second), teamColor(second)))
                    .append(Component.text(" " + hillScores.get(second), NamedTextColor.GRAY));
        }
        return builder.build();
    }

    private void recordHistoryEntry(List<UUID> winners) {
        if (historyManager == null || historyRecorded || players.isEmpty()) return;
        historyRecorded = true;

        Set<UUID> winnerSet = winners != null ? new HashSet<>(winners) : Set.of();

        List<KothPlayerState> sorted = new ArrayList<>(players.values());
        sorted.sort((a, b) -> {
            int scoreA = hillScores.getOrDefault(bucketFor(a.getUuid()), 0);
            int scoreB = hillScores.getOrDefault(bucketFor(b.getUuid()), 0);
            if (scoreA != scoreB) return Integer.compare(scoreB, scoreA);
            boolean aw = winnerSet.contains(a.getUuid());
            boolean bw = winnerSet.contains(b.getUuid());
            if (aw != bw) return aw ? -1 : 1;
            return Integer.compare(a.getDeaths(), b.getDeaths());
        });

        List<EventHistoryEntry.Placement> placements = new ArrayList<>();
        int rank = 1;
        for (KothPlayerState ps : sorted) {
            Player p = Bukkit.getPlayer(ps.getUuid());
            String name = p != null ? p.getName() : ps.getUuid().toString().substring(0, 8);
            Team team = teamManager.getTeamOfPlayer(ps.getUuid());
            int teamPts = hillScores.getOrDefault(bucketFor(ps.getUuid()), 0);
            String detail = winnerSet.contains(ps.getUuid())
                    ? "winnaar · team " + teamPts + " hill pts · " + ps.getDeaths() + " deaths"
                    : teamPts + " hill pts · " + ps.getDeaths() + " deaths";
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
                "koth",
                started,
                System.currentTimeMillis(),
                placements
        ));
    }

    public void removeParticipant(Player player, boolean teleport) {
        KothPlayerState ps = players.remove(player.getUniqueId());
        if (ps == null) return;
        scoreboard.detach(player);
        restorePlayer(player, ps, teleport);

        if (players.isEmpty() && state != State.IDLE) {
            recordHistoryEntry(null);
            cleanupAfterStop();
        }
    }

    private void restorePlayer(Player player, KothPlayerState ps, boolean teleport) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            try {
                player.setSpectatorTarget(null);
            } catch (Throwable ignored) {
            }
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            player.showPlayer(plugin, other);
            other.showPlayer(plugin, player);
        }

        if (ps.getSavedGameMode() != null) {
            player.setGameMode(ps.getSavedGameMode());
        }

        player.getInventory().clear();
        if (ps.getSavedInventory() != null) {
            player.getInventory().setContents(ps.getSavedInventory());
        }

        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setHealth(20.0);

        if (!teleport || ps.getSavedLocation() == null || ps.getSavedLocation().getWorld() == null) {
            return;
        }
        try {
            player.teleport(ps.getSavedLocation());
            player.setFallDistance(0f);
            player.sendActionBar(Messages.info("Terug naar je startlocatie."));
            player.playSound(ps.getSavedLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.0f);
        } catch (Exception ex) {
            plugin.getLogger().warning("Kon speler " + player.getName()
                    + " niet terugteleporteren: " + ex.getMessage());
        }
    }

    private void launchFireworkAtSpawn(FireworkEffect.Type type, Color a, Color b) {
        Location loc = config.getSpawn();
        if (loc == null || loc.getWorld() == null) return;
        Firework firework = loc.getWorld().spawn(loc, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .with(type)
                .withColor(a)
                .withFade(b)
                .flicker(true)
                .trail(true)
                .build());
        meta.setPower(1);
        firework.setFireworkMeta(meta);
    }

    private void broadcastTitle(Component title, Component subtitle) {
        Title t = Title.title(title, subtitle);
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.showTitle(t);
        }
    }

    private void playSoundAll(Sound sound, float volume, float pitch) {
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }

    public String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = (ms + 999) / 1000;
        long minutes = totalSec / 60;
        long seconds = totalSec % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}
