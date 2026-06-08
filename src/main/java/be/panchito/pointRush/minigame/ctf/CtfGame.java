package be.panchito.pointRush.minigame.ctf;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.minigame.MinigameStartEffects;
import be.panchito.pointRush.history.EventHistoryEntry;
import be.panchito.pointRush.history.EventHistoryManager;
import be.panchito.pointRush.storage.DataManager;
import be.panchito.pointRush.team.Team;
import be.panchito.pointRush.team.TeamManager;
import be.panchito.pointRush.util.LobbyWorld;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.PlayerRespawnUtil;
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
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Capture the Flag: verstop/zoek rondes — het ene team plant de vlag,
 * het andere zoekt en brengt hem naar eigen spawn.
 */
public final class CtfGame {

    public enum State { IDLE, STARTING, RUNNING, INTERMISSION }

    public enum RoundPhase { HIDING, ACTIVE }

    public static final int COUNTDOWN_SECONDS = 10;
    public static final int INTERMISSION_SECONDS = 8;
    public static final int WIN_BONUS_POINTS = 150;
    public static final long SPECTATOR_RESPAWN_MS = 30L * 1000L;
    public static final double FLAG_INTERACT_RADIUS = 2.5;

    private final PointRush plugin;
    private final CtfConfig config;
    private final TeamManager teamManager;
    private final DataManager dataManager;
    private final EventHistoryManager historyManager;
    private final CtfScoreboard scoreboard;

    private State state = State.IDLE;
    private RoundPhase roundPhase = RoundPhase.HIDING;
    private final Map<UUID, CtfPlayerState> players = new HashMap<>();
    private final Map<CtfSide, Integer> roundWins = new EnumMap<>(CtfSide.class);

    private int roundNumber = 0;
    private CtfSide hidingSide = CtfSide.RED;
    private CtfSide seekingSide = CtfSide.BLUE;

    private boolean flagPlanted = false;
    private Location flagPlantedLocation = null;
    private UUID flagCarrier = null;
    private UUID flagMarkerId = null;

    private long countdownEndsMs = 0L;
    private long hidePhaseEndsMs = 0L;
    private long roundEndsMs = 0L;
    private long intermissionEndsMs = 0L;
    private long eventStartedAtMs = 0L;
    private boolean historyRecorded = false;

    private BukkitTask tickTask;

    public CtfGame(PointRush plugin, CtfConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.teamManager = plugin.getTeamManager();
        this.dataManager = plugin.getDataManager();
        this.historyManager = plugin.getEventHistoryManager();
        this.scoreboard = new CtfScoreboard(plugin, this);
        for (CtfSide side : CtfSide.values()) {
            roundWins.put(side, 0);
        }
    }

    public State getState() {
        return state;
    }

    public RoundPhase getRoundPhase() {
        return roundPhase;
    }

    public CtfConfig getConfig() {
        return config;
    }

    public PointRush getPlugin() {
        return plugin;
    }

    public Collection<CtfPlayerState> getAllPlayerStates() {
        return Collections.unmodifiableCollection(players.values());
    }

    public boolean isParticipant(UUID id) {
        return players.containsKey(id);
    }

    public CtfPlayerState getPlayerState(UUID id) {
        return players.get(id);
    }

    /**
     * Teleport-doel tijdens een actieve ronde: verstop-team → kasteel, zoek-team → wacht beneden.
     * Buiten {@link State#RUNNING} valt terug op teamkleur-spawn.
     */
    public Location getParticipantRoundSpawn(UUID playerId) {
        CtfPlayerState ps = players.get(playerId);
        if (ps == null) {
            return null;
        }
        int round = Math.max(1, roundNumber);
        if (state != State.RUNNING && state != State.INTERMISSION) {
            Location team = config.getRoundTeamSpawn(ps.getSide(), round);
            return team != null ? team : null;
        }
        boolean hiding = ps.getSide() == hidingSide;
        return config.getRoundSpawn(ps.getSide(), hiding, hidingSide, round);
    }

    public CtfSide getHidingSide() {
        return hidingSide;
    }

    public CtfSide getSeekingSide() {
        return seekingSide;
    }

    /** @deprecated gebruik {@link #getSeekingSide()} */
    public CtfSide getAttackingSide() {
        return seekingSide;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public UUID getFlagCarrier() {
        return flagCarrier;
    }

    public boolean isFlagPlanted() {
        return flagPlanted;
    }

    public boolean isFlagMarker(Entity entity) {
        return entity != null && flagMarkerId != null && flagMarkerId.equals(entity.getUniqueId());
    }

    public Map<CtfSide, Integer> getRoundWins() {
        return Collections.unmodifiableMap(roundWins);
    }

    public long getCountdownTimeLeftMs() {
        if (state != State.STARTING) return 0L;
        return Math.max(0L, countdownEndsMs - System.currentTimeMillis());
    }

    public long getRoundTimeLeftMs() {
        long now = System.currentTimeMillis();
        return switch (state) {
            case STARTING -> Math.max(0L, countdownEndsMs - now);
            case RUNNING -> Math.max(0L, roundEndsMs - now);
            case INTERMISSION -> Math.max(0L, intermissionEndsMs - now);
            default -> 0L;
        };
    }

    public long getHidePhaseTimeLeftMs() {
        if (state != State.RUNNING || roundPhase != RoundPhase.HIDING) return 0L;
        return Math.max(0L, hidePhaseEndsMs - System.currentTimeMillis());
    }

    public boolean isFlagItem(ItemStack item) {
        return CtfFlagItem.isFlag(item);
    }

    public boolean start() {
        if (state != State.IDLE) return false;
        if (!config.isReady()) return false;
        if (!CtfFlagItem.isAvailable()) {
            plugin.getLogger().warning("CTF: Nexo item '" + CtfFlagItem.NEXO_ID
                    + "' niet gevonden — fallback PAPER-vlag wordt gebruikt.");
        }

        state = State.STARTING;
        roundNumber = 0;
        countdownEndsMs = System.currentTimeMillis() + COUNTDOWN_SECONDS * 1000L;
        eventStartedAtMs = System.currentTimeMillis();
        historyRecorded = false;
        resetFlagState();
        for (CtfSide side : CtfSide.values()) {
            roundWins.put(side, 0);
        }

        List<Player> eligible = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getGameMode() == GameMode.CREATIVE || online.getGameMode() == GameMode.SPECTATOR) {
                online.sendMessage(Messages.warn("Je doet niet mee aan CTF (creative/spectator)."));
                continue;
            }
            if (!LobbyWorld.contains(plugin, online)) {
                continue;
            }
            eligible.add(online);
        }

        if (eligible.size() < 2) {
            Bukkit.broadcast(Messages.error("CTF heeft minstens 2 spelers nodig."));
            cleanupAfterStop();
            return false;
        }

        assignSides(eligible);
        for (Player player : eligible) {
            joinPlayer(player);
        }

        scoreboard.start();
        scoreboard.updateBossBar("countdown", 1.0f, BossBar.Color.YELLOW);
        broadcastTitle(
                Component.text(SmallText.of("CAPTURE THE FLAG"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("verstop · zoek · breng naar spawn"), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.4f);

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        MinigameStartEffects.onStarted(plugin);
        return true;
    }

    public boolean stop() {
        if (state == State.IDLE) return false;
        endEvent(false);
        return true;
    }

    private void assignSides(List<Player> eligible) {
        Map<UUID, CtfSide> sides = CtfSideAssigner.assignSides(teamManager, eligible);

        for (Player p : eligible) {
            CtfSide side = sides.get(p.getUniqueId());
            CtfPlayerState ps = new CtfPlayerState(
                    p.getUniqueId(),
                    p.getLocation().clone(),
                    p.getGameMode()
            );
            ps.setSide(side);
            players.put(p.getUniqueId(), ps);
        }
    }

    private void joinPlayer(Player player) {
        CtfPlayerState ps = players.get(player.getUniqueId());
        if (ps == null) return;

        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        clearPotionEffects(player);

        Location spawn = config.getRoundTeamSpawn(ps.getSide(), 1);
        if (spawn != null) {
            plugin.getTeleporter().teleport(player, spawn);
        }
        giveKit(player, ps.getSide());
        scoreboard.attach(player);
        player.sendMessage(Messages.info("Je zit in team "
                + ps.getSide().getDisplayName() + " — wisselend verstoppen en zoeken!"));
    }

    /** Called from {@link CtfListener} after the player has actually respawned. */
    public void applyRespawnKit(Player player) {
        CtfPlayerState ps = players.get(player.getUniqueId());
        if (ps == null) return;
        clearPotionEffects(player);
        giveKit(player, ps.getSide());
    }

    public void giveKit(Player player, CtfSide side) {
        player.getInventory().clear();
        player.getInventory().setHelmet(side.leatherPiece(Material.LEATHER_HELMET));
        player.getInventory().setChestplate(side.leatherPiece(Material.LEATHER_CHESTPLATE));
        player.getInventory().setLeggings(side.leatherPiece(Material.LEATHER_LEGGINGS));
        player.getInventory().setBoots(side.leatherPiece(Material.LEATHER_BOOTS));

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.editMeta(meta -> {
            meta.displayName(Component.text("Zwaard", side.getTextColor(), TextDecoration.BOLD));
            meta.addEnchant(Enchantment.SHARPNESS, 1, true);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        });

        ItemStack bow = new ItemStack(Material.BOW);
        bow.editMeta(meta -> {
            meta.displayName(Component.text("Boog", side.getTextColor(), TextDecoration.BOLD));
            meta.addEnchant(Enchantment.POWER, 1, true);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        });

        player.getInventory().setItem(0, sword);
        player.getInventory().setItem(1, bow);
        player.getInventory().setItem(2, new ItemStack(Material.ARROW, 32));
        player.getInventory().setItem(3, new ItemStack(Material.GOLDEN_APPLE, 8));
        player.getInventory().setItem(4, new ItemStack(Material.ENDER_PEARL, 16));
        player.getInventory().setItem(5, new ItemStack(Material.COOKED_BEEF, 16));

        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
    }

    public ItemStack createFlagItem() {
        return CtfFlagItem.create();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        switch (state) {
            case STARTING -> tickStarting(now);
            case RUNNING -> tickRunning(now);
            case INTERMISSION -> tickIntermission(now);
            default -> {
            }
        }
    }

    private void tickStarting(long now) {
        long left = Math.max(0L, countdownEndsMs - now);
        float progress = left / (float) (COUNTDOWN_SECONDS * 1000L);
        scoreboard.updateBossBar("start in " + formatTime(left), progress, BossBar.Color.YELLOW);
        if (left <= 0) {
            beginRound(1);
        }
    }

    private void beginRound(int round) {
        state = State.RUNNING;
        roundNumber = round;
        hidingSide = (round % 2 == 1) ? CtfSide.RED : CtfSide.BLUE;
        seekingSide = hidingSide.opposite();
        roundPhase = RoundPhase.HIDING;

        long now = System.currentTimeMillis();
        hidePhaseEndsMs = now + config.getHidePhaseMs();
        roundEndsMs = now + config.getRoundDurationMs();
        resetFlagState();
        respawnAllForRound();
        giveFlagToRandomHider(null);

        broadcastTitle(
                Component.text(SmallText.of("RONDE " + round), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text()
                        .append(Component.text(hidingSide.getDisplayName(), hidingSide.getTextColor(), TextDecoration.BOLD))
                        .append(Component.text(SmallText.of(" verstopt · "), NamedTextColor.GRAY))
                        .append(Component.text(seekingSide.getDisplayName(), seekingSide.getTextColor(), TextDecoration.BOLD))
                        .append(Component.text(SmallText.of(" zoekt"), NamedTextColor.GRAY))
                        .build()
        );
        playSoundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.4f);
        launchFirework(config.resolveHideSpawn(hidingSide, roundNumber), FireworkEffect.Type.BURST,
                hidingSide == CtfSide.RED ? Color.RED : Color.BLUE, Color.WHITE);

        for (CtfPlayerState ps : players.values()) {
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p == null) continue;
            if (ps.getSide() == hidingSide) {
                p.sendMessage(Messages.info("Plant de vlag ergens! Rechtsklik met de vlag in je offhand — daarna mag het zoek-team meteen zoeken."));
            } else {
                p.sendMessage(Messages.info("Wacht tot de vlag geplant is of de verstopfase voorbij is, daarna mag je zoeken!"));
            }
        }
    }

    private void giveFlagToRandomHider(UUID exclude) {
        List<Player> hiders = new ArrayList<>();
        for (CtfPlayerState ps : players.values()) {
            if (ps.getSide() != hidingSide) continue;
            if (!ps.isAlive()) continue;
            if (exclude != null && ps.getUuid().equals(exclude)) continue;
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p != null) hiders.add(p);
        }
        if (hiders.isEmpty()) return;

        Player chosen = hiders.get(ThreadLocalRandom.current().nextInt(hiders.size()));
        assignFlagCarrier(chosen);
        chosen.sendMessage(Messages.success("Jij hebt de vlag — plant hem ergens veilig!"));
    }

    private void assignFlagCarrier(Player player) {
        if (flagPlanted && flagCarrier == null) {
            removeFlagMarker();
        }
        flagCarrier = player.getUniqueId();
        player.getInventory().setItemInOffHand(createFlagItem());
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 999999, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 999999, 0, false, false, true));
    }

    private void clearFlagCarrierEffects(Player player) {
        if (player == null) return;
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.GLOWING);
    }

    private void respawnAllForRound() {
        for (CtfPlayerState ps : players.values()) {
            ps.setAlive(true);
            ps.setRespawnAtMs(0L);
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p == null) continue;
            PlayerRespawnUtil.clearSpectatorState(p);
            clearPotionEffects(p);
            Location spawn = getParticipantRoundSpawn(ps.getUuid());
            if (spawn != null) {
                plugin.getTeleporter().teleport(p, spawn);
            }
            giveKit(p, ps.getSide());
        }
    }

    private void tickRunning(long now) {
        if (roundPhase == RoundPhase.HIDING && now >= hidePhaseEndsMs) {
            beginSeekPhase();
        }

        if (now >= roundEndsMs) {
            endRound(hidingSide, false);
            return;
        }

        maintainFlagCarrierEffects();
        enforceFlagInOffhand();
        processRespawns(now);
        broadcastSpectatorActionBars(now);

        long left = Math.max(0L, roundEndsMs - now);
        float progress = Math.min(1f, left / (float) config.getRoundDurationMs());
        String phaseLabel;
        if (roundPhase == RoundPhase.HIDING) {
            phaseLabel = hidingSide.getDisplayName() + " verstopt · zoeken over "
                    + formatTime(getHidePhaseTimeLeftMs());
        } else {
            phaseLabel = seekingSide.getDisplayName() + " zoekt · "
                    + (flagCarrier != null ? "vlag onderweg!" : flagPlanted ? "vlag gevonden?" : "vlag nog verborgen");
        }
        scoreboard.updateBossBar(phaseLabel + " · " + formatTime(left), progress, BossBar.Color.GREEN);
    }

    private void beginSeekPhase() {
        if (roundPhase != RoundPhase.HIDING) return;
        roundPhase = RoundPhase.ACTIVE;
        broadcastTitle(
                Component.text(SmallText.of("ZOEKEN!"), seekingSide.getTextColor(), TextDecoration.BOLD),
                Component.text(SmallText.of(seekingSide.getDisplayName() + " mag nu zoeken"), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.6f);
    }

    private void tickIntermission(long now) {
        long left = Math.max(0L, intermissionEndsMs - now);
        float progress = left / (float) (INTERMISSION_SECONDS * 1000L);
        scoreboard.updateBossBar("volgende ronde · " + formatTime(left), progress, BossBar.Color.BLUE);
        if (left <= 0) {
            if (roundNumber >= config.getRounds()) {
                endEvent(true);
            } else {
                beginRound(roundNumber + 1);
            }
        }
    }

    private void maintainFlagCarrierEffects() {
        if (flagCarrier == null) return;
        Player carrier = Bukkit.getPlayer(flagCarrier);
        if (carrier == null || !carrier.isOnline()) {
            returnFlagToPlantedLocation();
            return;
        }
        CtfPlayerState ps = players.get(flagCarrier);
        if (ps == null || !ps.isAlive()) {
            return;
        }
        carrier.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, false, true));
        carrier.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false, true));
    }

    private void enforceFlagInOffhand() {
        if (flagCarrier == null) return;
        Player carrier = Bukkit.getPlayer(flagCarrier);
        if (carrier == null) return;

        ItemStack offhand = carrier.getInventory().getItemInOffHand();
        if (!isFlagItem(offhand)) {
            carrier.getInventory().setItemInOffHand(createFlagItem());
        }
    }

    /** Verstop-team plant de vlag op huidige locatie (rechtsklik met vlag in offhand). */
    public void tryPlantFlag(Player player) {
        if (state != State.RUNNING) return;
        if (flagPlanted) return;

        CtfPlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isAlive() || ps.getSide() != hidingSide) return;
        if (!isFlagItem(player.getInventory().getItemInOffHand())) return;

        flagPlanted = true;
        flagPlantedLocation = player.getLocation().clone();
        flagCarrier = null;
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        clearPotionEffects(player);
        spawnFlagMarker(flagPlantedLocation);

        Component msg = Component.text()
                .append(Component.text(hidingSide.getDisplayName(), hidingSide.getTextColor(), TextDecoration.BOLD))
                .append(Component.text(SmallText.of(" heeft de vlag geplant!"), NamedTextColor.GOLD))
                .build();
        Bukkit.broadcast(Messages.PREFIX.append(msg));
        playSoundAll(Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.2f);

        if (roundPhase == RoundPhase.HIDING) {
            beginSeekPhase();
        }
    }

    /** Zoek-team pakt geplante vlag op. */
    public void tryPickupFlag(Player player) {
        if (state != State.RUNNING || roundPhase != RoundPhase.ACTIVE) return;
        if (flagCarrier != null || !flagPlanted) return;

        CtfPlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isAlive() || ps.getSide() != seekingSide) return;

        Location flagLoc = flagPlantedLocation;
        if (flagLoc == null || player.getWorld() != flagLoc.getWorld()) return;
        if (player.getLocation().distanceSquared(flagLoc) > FLAG_INTERACT_RADIUS * FLAG_INTERACT_RADIUS) return;

        assignFlagCarrier(player);

        Component msg = Component.text()
                .append(Component.text(player.getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(SmallText.of(" heeft de vlag gestolen! Breng hem naar base camp!"), NamedTextColor.GOLD))
                .build();
        Bukkit.broadcast(Messages.PREFIX.append(msg));
        playSoundAll(Sound.ITEM_TOTEM_USE, 0.7f, 1.2f);
    }

    public void tryDeliverFlag(Player player) {
        if (state != State.RUNNING || roundPhase != RoundPhase.ACTIVE) return;
        if (flagCarrier == null || !flagCarrier.equals(player.getUniqueId())) return;

        CtfPlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isAlive() || ps.getSide() != seekingSide) return;
        if (!config.isNearSpawn(seekingSide, player.getLocation(), roundNumber)) return;

        ps.incrementCaptures();
        endRound(seekingSide, true);
    }

    private void endRound(CtfSide winner, boolean captured) {
        state = State.INTERMISSION;
        resetFlagState();

        int wins = roundWins.getOrDefault(winner, 0) + 1;
        roundWins.put(winner, wins);
        awardRoundPoints(winner);

        String reason = captured
                ? "vlag teruggebracht naar base camp!"
                : "tijd verstreken — vlag niet gevonden";
        broadcastTitle(
                Component.text(winner.getDisplayName(), winner.getTextColor(), TextDecoration.BOLD),
                Component.text(SmallText.of(reason), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        launchFirework(config.getRoundTeamSpawn(winner, roundNumber), FireworkEffect.Type.STAR,
                winner == CtfSide.RED ? Color.RED : Color.BLUE, Color.WHITE);

        intermissionEndsMs = System.currentTimeMillis() + INTERMISSION_SECONDS * 1000L;
    }

    private void awardRoundPoints(CtfSide winner) {
        int points = config.getPointsPerCapture();
        Set<UUID> awardedTeams = new HashSet<>();

        for (CtfPlayerState ps : players.values()) {
            if (ps.getSide() != winner) continue;
            ps.addPointsEarned(points);
            Team team = teamManager.getTeamOfPlayer(ps.getUuid());
            if (team != null && awardedTeams.add(team.getId())) {
                team.addPoints(points);
            }
        }
        dataManager.save();

        Component msg = Component.text()
                .append(Component.text(winner.getDisplayName(), winner.getTextColor(), TextDecoration.BOLD))
                .append(Component.text(SmallText.of(" wint ronde " + roundNumber + " · +"), NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(points), NamedTextColor.GREEN))
                .append(Component.text(SmallText.of(" pts"), NamedTextColor.GRAY))
                .build();
        Bukkit.broadcast(Messages.PREFIX.append(msg));
    }

    private void endEvent(boolean natural) {
        if (state == State.IDLE) return;

        CtfSide overallWinner = determineOverallWinner();
        if (natural && overallWinner != null) {
            awardWinBonus(overallWinner);
            broadcastTitle(
                    Component.text(SmallText.of("CTF AFGELOPEN"), NamedTextColor.GOLD, TextDecoration.BOLD),
                    Component.text(overallWinner.getDisplayName(), overallWinner.getTextColor(), TextDecoration.BOLD)
            );
        }

        List<UUID> winners = new ArrayList<>();
        if (overallWinner != null) {
            for (CtfPlayerState ps : players.values()) {
                if (ps.getSide() == overallWinner) {
                    winners.add(ps.getUuid());
                }
            }
        }
        recordHistoryEntry(winners);
        cleanupAfterStop();
        Bukkit.broadcast(Messages.info("Capture the Flag event afgelopen."));
    }

    private CtfSide determineOverallWinner() {
        int red = roundWins.getOrDefault(CtfSide.RED, 0);
        int blue = roundWins.getOrDefault(CtfSide.BLUE, 0);
        if (red > blue) return CtfSide.RED;
        if (blue > red) return CtfSide.BLUE;
        return null;
    }

    private void awardWinBonus(CtfSide winner) {
        Set<UUID> awardedTeams = new HashSet<>();
        for (CtfPlayerState ps : players.values()) {
            if (ps.getSide() != winner) continue;
            ps.addPointsEarned(WIN_BONUS_POINTS);
            Team team = teamManager.getTeamOfPlayer(ps.getUuid());
            if (team != null && awardedTeams.add(team.getId())) {
                team.addPoints(WIN_BONUS_POINTS);
            }
        }
        dataManager.save();
    }

    /**
     * Zoeker doodt vlagdrager tijdens actieve fase — vlag gaat naar de killer.
     *
     * @return {@code true} als de vlag is overgenomen
     */
    private boolean tryStealFlagFromCarrier(Player victim) {
        if (state != State.RUNNING || roundPhase != RoundPhase.ACTIVE || !flagPlanted) {
            return false;
        }
        Player killer = victim.getKiller();
        if (killer == null || !killer.isOnline()) {
            return false;
        }
        CtfPlayerState killerPs = players.get(killer.getUniqueId());
        if (killerPs == null || !killerPs.isAlive() || killerPs.getSide() != seekingSide) {
            return false;
        }

        clearFlagCarrierEffects(victim);
        assignFlagCarrier(killer);

        Component msg = Component.text()
                .append(Component.text(killer.getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(SmallText.of(" heeft de vlag gestolen! Breng hem naar base camp!"), NamedTextColor.GOLD))
                .build();
        Bukkit.broadcast(Messages.PREFIX.append(msg));
        playSoundAll(Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.9f);
        return true;
    }

    public void returnFlagToPlantedLocation() {
        if (flagCarrier != null) {
            clearFlagCarrierEffects(Bukkit.getPlayer(flagCarrier));
        }
        flagCarrier = null;

        if (state == State.RUNNING && flagPlanted && flagPlantedLocation != null) {
            spawnFlagMarker(flagPlantedLocation);
            Location loc = flagPlantedLocation;
            if (loc.getWorld() != null) {
                loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.02);
                loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.2f);
            }
            Bukkit.broadcast(Messages.info("De vlag ligt weer op de geplante plek!"));
        }
    }

    /** Hider die de vlag nog niet geplant had — geef aan andere hider. */
    private void returnUnplantedFlag(UUID exclude) {
        if (flagCarrier != null) {
            clearFlagCarrierEffects(Bukkit.getPlayer(flagCarrier));
        }
        flagCarrier = null;
        if (state == State.RUNNING && !flagPlanted) {
            giveFlagToRandomHider(exclude);
            Bukkit.broadcast(Messages.info("De vlag is aan een andere verstopper gegeven!"));
        }
    }

    private void resetFlagState() {
        removeFlagMarker();
        flagPlanted = false;
        flagPlantedLocation = null;
        flagCarrier = null;
    }

    private void spawnFlagMarker(Location loc) {
        removeFlagMarker();
        if (loc == null || loc.getWorld() == null) return;

        Location spawnLoc = loc.clone().add(0, 0.15, 0);
        ItemDisplay display = spawnLoc.getWorld().spawn(spawnLoc, ItemDisplay.class, d -> {
            d.setItemStack(createFlagItem());
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setPersistent(false);
            d.setInvulnerable(true);
            d.setGravity(false);
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0.35f, 0f),
                    new AxisAngle4f(0f, 0f, 1f, 0f),
                    new Vector3f(1.4f, 1.4f, 1.4f),
                    new AxisAngle4f(0f, 0f, 1f, 0f)
            ));
        });
        flagMarkerId = display.getUniqueId();
    }

    private void removeFlagMarker() {
        if (flagMarkerId == null) return;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(flagMarkerId);
            if (entity != null) {
                entity.remove();
                break;
            }
        }
        flagMarkerId = null;
    }

    private void processRespawns(long now) {
        for (CtfPlayerState ps : players.values()) {
            if (ps.isAlive() || ps.getRespawnAtMs() <= 0L) continue;
            if (now < ps.getRespawnAtMs()) continue;

            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p == null) continue;

            ps.setAlive(true);
            ps.setRespawnAtMs(0L);

            PlayerRespawnUtil.forceRespawnIfDead(p);
            PlayerRespawnUtil.clearSpectatorState(p);
            for (Player other : Bukkit.getOnlinePlayers()) {
                p.showPlayer(plugin, other);
                other.showPlayer(plugin, p);
            }

            Location spawn = getParticipantRoundSpawn(ps.getUuid());
            if (spawn != null) {
                plugin.getTeleporter().teleport(p, spawn);
            }
            giveKit(p, ps.getSide());

            p.showTitle(Title.title(
                    Component.text(SmallText.of("RESPAWN!"), NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text(SmallText.of("team " + ps.getSide().getDisplayName()), ps.getSide().getTextColor()),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(200))
            ));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        }
    }

    private void broadcastSpectatorActionBars(long now) {
        for (CtfPlayerState ps : players.values()) {
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
        CtfPlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isAlive()) return;

        ps.incrementDeaths();
        boolean hadFlag = flagCarrier != null && flagCarrier.equals(player.getUniqueId());

        if (hadFlag) {
            if (tryStealFlagFromCarrier(player)) {
                // vlag overgenomen door killer
            } else if (flagPlanted) {
                returnFlagToPlantedLocation();
            } else {
                returnUnplantedFlag(player.getUniqueId());
            }
        }

        ps.setAlive(false);
        ps.setRespawnAtMs(System.currentTimeMillis() + SPECTATOR_RESPAWN_MS);

        PlayerRespawnUtil.forceRespawnIfDead(player);

        Location loc = player.getLocation();
        if (loc.getWorld() != null) {
            loc.getWorld().spawnParticle(Particle.SMOKE, loc, 20, 0.4, 0.5, 0.4, 0.02);
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_DEATH, 1.0f, 1.0f);
        }

        Component announce = Component.text()
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .append(Component.text(SmallText.of(" is gevallen ("), NamedTextColor.GRAY))
                .append(Component.text(ps.getSide().getDisplayName(), ps.getSide().getTextColor()))
                .append(Component.text(SmallText.of(hadFlag ? ") · vlag terug! · respawn 30s" : ") · respawn 30s"),
                        NamedTextColor.GRAY))
                .build();
        Bukkit.broadcast(Messages.PREFIX.append(announce));

        player.showTitle(Title.title(
                Component.text(SmallText.of("DOOD"), NamedTextColor.RED, TextDecoration.BOLD),
                Component.text(SmallText.of("spectator 30 seconden"), NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1500), Duration.ofMillis(300))
        ));
    }

    public boolean sameSide(UUID a, UUID b) {
        CtfPlayerState psA = players.get(a);
        CtfPlayerState psB = players.get(b);
        if (psA == null || psB == null) return false;
        return psA.getSide() == psB.getSide();
    }

    public boolean isSeekerFrozen(Player player) {
        if (state != State.RUNNING || roundPhase != RoundPhase.HIDING) return false;
        CtfPlayerState ps = players.get(player.getUniqueId());
        return ps != null && ps.getSide() == seekingSide;
    }

    public boolean isCombatAllowed() {
        return state == State.RUNNING && roundPhase == RoundPhase.ACTIVE;
    }

    public void removeParticipant(Player player, boolean teleport) {
        if (flagCarrier != null && flagCarrier.equals(player.getUniqueId())) {
            if (flagPlanted) {
                returnFlagToPlantedLocation();
            } else {
                returnUnplantedFlag(player.getUniqueId());
            }
        }
        CtfPlayerState ps = players.remove(player.getUniqueId());
        if (ps == null) return;
        scoreboard.detach(player);
        restorePlayer(player, ps, teleport);

        if (players.isEmpty() && state != State.IDLE) {
            recordHistoryEntry(null);
            cleanupAfterStop();
        }
    }

    private void cleanupAfterStop() {
        state = State.IDLE;
        roundPhase = RoundPhase.HIDING;
        cancelTask(tickTask);
        tickTask = null;
        scoreboard.stop();
        resetFlagState();

        Title endTitle = Title.title(
                Component.text(SmallText.of("event afgelopen"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("teleport terug naar je startlocatie"), NamedTextColor.GRAY)
        );
        for (Map.Entry<UUID, CtfPlayerState> entry : new ArrayList<>(players.entrySet())) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                p.showTitle(endTitle);
                restorePlayer(p, entry.getValue(), true);
            }
        }
        players.clear();
        for (CtfSide side : CtfSide.values()) {
            roundWins.put(side, 0);
        }
        roundNumber = 0;
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            try {
                task.cancel();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    private void restorePlayer(Player player, CtfPlayerState ps, boolean teleport) {
        PlayerRespawnUtil.prepareForRestore(player);
        clearPotionEffects(player);
        if (ps.getSavedGameMode() != null) {
            player.setGameMode(ps.getSavedGameMode());
        }
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setHealth(20.0);

        if (teleport && ps.getSavedLocation() != null && ps.getSavedLocation().getWorld() != null) {
            try {
                plugin.getTeleporter().teleport(player, ps.getSavedLocation());
                player.setFallDistance(0f);
                player.sendActionBar(Messages.info("Terug naar je startlocatie."));
                player.playSound(ps.getSavedLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.0f);
            } catch (Exception ex) {
                plugin.getLogger().warning("Kon speler " + player.getName()
                        + " niet terugteleporteren: " + ex.getMessage());
            }
        }
    }

    private void clearPotionEffects(Player player) {
        for (PotionEffect eff : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(eff.getType());
        }
    }

    private void recordHistoryEntry(List<UUID> winners) {
        if (historyManager == null || historyRecorded || players.isEmpty()) return;
        historyRecorded = true;

        Set<UUID> winnerSet = winners != null ? new HashSet<>(winners) : Set.of();
        List<CtfPlayerState> sorted = new ArrayList<>(players.values());
        sorted.sort((a, b) -> {
            int winsA = roundWins.getOrDefault(a.getSide(), 0);
            int winsB = roundWins.getOrDefault(b.getSide(), 0);
            if (winsA != winsB) return Integer.compare(winsB, winsA);
            if (a.getCaptures() != b.getCaptures()) return Integer.compare(b.getCaptures(), a.getCaptures());
            boolean aw = winnerSet.contains(a.getUuid());
            boolean bw = winnerSet.contains(b.getUuid());
            if (aw != bw) return aw ? -1 : 1;
            return Integer.compare(a.getDeaths(), b.getDeaths());
        });

        List<EventHistoryEntry.Placement> placements = new ArrayList<>();
        int rank = 1;
        for (CtfPlayerState ps : sorted) {
            Player p = Bukkit.getPlayer(ps.getUuid());
            String name = p != null ? p.getName() : ps.getUuid().toString().substring(0, 8);
            Team team = teamManager.getTeamOfPlayer(ps.getUuid());
            int sideWins = roundWins.getOrDefault(ps.getSide(), 0);
            String detail = ps.getSide().getDisplayName() + " · " + sideWins + " ronde-winsten · "
                    + ps.getCaptures() + " captures · " + ps.getDeaths() + " deaths";
            placements.add(new EventHistoryEntry.Placement(
                    rank++,
                    ps.getUuid(),
                    name,
                    team != null ? team.getId() : null,
                    team != null ? team.getName() : null,
                    team != null ? team.getColor().toString() : ps.getSide().getTextColor().toString(),
                    ps.getPointsEarned(),
                    detail
            ));
        }
        if (placements.isEmpty()) return;

        long started = eventStartedAtMs > 0 ? eventStartedAtMs : System.currentTimeMillis();
        historyManager.record(new EventHistoryEntry(
                UUID.randomUUID().toString(),
                "ctf",
                started,
                System.currentTimeMillis(),
                placements
        ));
    }

    private void launchFirework(Location loc, FireworkEffect.Type type, Color a, Color b) {
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
