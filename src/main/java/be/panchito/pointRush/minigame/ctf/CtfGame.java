package be.panchito.pointRush.minigame.ctf;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.history.EventHistoryEntry;
import be.panchito.pointRush.history.EventHistoryManager;
import be.panchito.pointRush.storage.DataManager;
import be.panchito.pointRush.team.Team;
import be.panchito.pointRush.team.TeamManager;
import be.panchito.pointRush.util.Messages;
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
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

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

/**
 * Capture the Flag: twee kanten (rood/blauw), wisselende aanval/verdediging rondes,
 * vlag in het midden, lever af bij je delivery point.
 */
public final class CtfGame {

    public enum State { IDLE, STARTING, RUNNING, INTERMISSION }

    public static final int COUNTDOWN_SECONDS = 10;
    public static final int INTERMISSION_SECONDS = 8;
    public static final int WIN_BONUS_POINTS = 150;
    public static final double FLAG_PICKUP_RADIUS = 2.5;

    private static final String FLAG_NAME = "Vlag";

    private final PointRush plugin;
    private final CtfConfig config;
    private final TeamManager teamManager;
    private final DataManager dataManager;
    private final EventHistoryManager historyManager;
    private final CtfScoreboard scoreboard;

    private State state = State.IDLE;
    private final Map<UUID, CtfPlayerState> players = new HashMap<>();
    private final Map<CtfSide, Integer> roundWins = new EnumMap<>(CtfSide.class);
    private final Map<CtfSide, Integer> sidePopulation = new EnumMap<>(CtfSide.class);

    private int roundNumber = 0;
    /** Welke kant aanvalt in de huidige ronde. */
    private CtfSide attackingSide = CtfSide.RED;
    private UUID flagCarrier = null;
    private UUID flagMarkerId = null;

    private long countdownEndsMs = 0L;
    private long roundStartMs = 0L;
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
            sidePopulation.put(side, 0);
        }
    }

    public State getState() {
        return state;
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

    public CtfSide getAttackingSide() {
        return attackingSide;
    }

    public CtfSide getDefendingSide() {
        return attackingSide.opposite();
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public UUID getFlagCarrier() {
        return flagCarrier;
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

    public boolean isFlagItem(ItemStack item) {
        if (item == null || item.getType() != Material.WHITE_BANNER) return false;
        if (!item.hasItemMeta() || item.getItemMeta().displayName() == null) return false;
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
        return FLAG_NAME.equalsIgnoreCase(plain);
    }

    public boolean start() {
        if (state != State.IDLE) return false;
        if (!config.isReady()) return false;

        state = State.STARTING;
        roundNumber = 0;
        countdownEndsMs = System.currentTimeMillis() + COUNTDOWN_SECONDS * 1000L;
        eventStartedAtMs = System.currentTimeMillis();
        historyRecorded = false;
        flagCarrier = null;
        for (CtfSide side : CtfSide.values()) {
            roundWins.put(side, 0);
            sidePopulation.put(side, 0);
        }

        List<Player> eligible = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getGameMode() == GameMode.CREATIVE || online.getGameMode() == GameMode.SPECTATOR) {
                online.sendMessage(Messages.warn("Je doet niet mee aan CTF (creative/spectator)."));
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
                Component.text(SmallText.of("pak de vlag · lever af · bescherm je base"), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.4f);

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        return true;
    }

    public boolean stop() {
        if (state == State.IDLE) return false;
        endEvent(false);
        return true;
    }

    /**
     * Split PointRush-teams over rood/blauw; hele teams blijven bij elkaar (greedy balance).
     */
    private void assignSides(List<Player> eligible) {
        Map<UUID, Set<UUID>> teamBuckets = new HashMap<>();
        for (Player p : eligible) {
            Team t = teamManager.getTeamOfPlayer(p.getUniqueId());
            UUID bucket = t != null ? t.getId() : p.getUniqueId();
            teamBuckets.computeIfAbsent(bucket, k -> new HashSet<>()).add(p.getUniqueId());
        }

        List<Map.Entry<UUID, Set<UUID>>> sorted = new ArrayList<>(teamBuckets.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));

        Map<CtfSide, Set<UUID>> sideMembers = new EnumMap<>(CtfSide.class);
        sideMembers.put(CtfSide.RED, new HashSet<>());
        sideMembers.put(CtfSide.BLUE, new HashSet<>());

        for (Map.Entry<UUID, Set<UUID>> entry : sorted) {
            CtfSide assign = sideMembers.get(CtfSide.RED).size() <= sideMembers.get(CtfSide.BLUE).size()
                    ? CtfSide.RED : CtfSide.BLUE;
            sideMembers.get(assign).addAll(entry.getValue());
        }

        sidePopulation.put(CtfSide.RED, sideMembers.get(CtfSide.RED).size());
        sidePopulation.put(CtfSide.BLUE, sideMembers.get(CtfSide.BLUE).size());

        for (Player p : eligible) {
            CtfSide side = sideMembers.get(CtfSide.RED).contains(p.getUniqueId())
                    ? CtfSide.RED : CtfSide.BLUE;
            CtfPlayerState ps = new CtfPlayerState(
                    p.getUniqueId(),
                    p.getLocation().clone(),
                    p.getGameMode(),
                    cloneInventory(p.getInventory().getContents())
            );
            ps.setSide(side);
            players.put(p.getUniqueId(), ps);
        }
    }

    private ItemStack[] cloneInventory(ItemStack[] inv) {
        ItemStack[] saved = new ItemStack[inv.length];
        for (int i = 0; i < inv.length; i++) {
            saved[i] = inv[i] != null ? inv[i].clone() : null;
        }
        return saved;
    }

    private void joinPlayer(Player player) {
        CtfPlayerState ps = players.get(player.getUniqueId());
        if (ps == null) return;

        player.getInventory().clear();
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        clearPotionEffects(player);

        Location spawn = config.getSpawn(ps.getSide());
        if (spawn != null) {
            player.teleport(spawn);
        }
        giveKit(player, ps.getSide());
        scoreboard.attach(player);
        player.sendMessage(Messages.info("Je zit in team "
                + ps.getSide().getDisplayName() + " — wisselende aanval/verdediging rondes!"));
    }

    /** Vanilla PVP kit met gekleurde leather armour. */
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
        ItemStack flag = new ItemStack(Material.WHITE_BANNER);
        flag.editMeta(meta -> {
            meta.displayName(Component.text(FLAG_NAME, NamedTextColor.GOLD, TextDecoration.BOLD));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        });
        return flag;
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
            beginRound(1, CtfSide.RED);
        }
    }

    private void beginRound(int round, CtfSide attacker) {
        state = State.RUNNING;
        roundNumber = round;
        attackingSide = attacker;
        roundStartMs = System.currentTimeMillis();
        roundEndsMs = roundStartMs + config.getRoundDurationMs();
        flagCarrier = null;

        removeFlagMarker();
        spawnFlagMarker();
        respawnAllForRound();

        CtfSide defender = attacker.opposite();
        broadcastTitle(
                Component.text(SmallText.of("RONDE " + round), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text()
                        .append(Component.text(attacker.getDisplayName(), attacker.getTextColor(), TextDecoration.BOLD))
                        .append(Component.text(SmallText.of(" aanvalt · "), NamedTextColor.GRAY))
                        .append(Component.text(defender.getDisplayName(), defender.getTextColor(), TextDecoration.BOLD))
                        .append(Component.text(SmallText.of(" verdedigt"), NamedTextColor.GRAY))
                        .build()
        );
        playSoundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.4f);
        launchFirework(config.getFlagSpawn(), FireworkEffect.Type.BURST, Color.WHITE, Color.YELLOW);
    }

    private void respawnAllForRound() {
        for (CtfPlayerState ps : players.values()) {
            ps.setAlive(true);
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p == null) continue;
            clearPotionEffects(p);
            Location spawn = config.getSpawn(ps.getSide());
            if (spawn != null) {
                p.teleport(spawn);
            }
            giveKit(p, ps.getSide());
        }
    }

    private void tickRunning(long now) {
        if (now >= roundEndsMs) {
            endRound(getDefendingSide(), false);
            return;
        }

        maintainFlagCarrierEffects();
        enforceFlagInOffhand();

        long left = Math.max(0L, roundEndsMs - now);
        float progress = Math.min(1f, left / (float) config.getRoundDurationMs());
        String role = attackingSide.getDisplayName() + " aanvalt · "
                + (flagCarrier != null ? "vlag onderweg!" : "vlag op spawn");
        scoreboard.updateBossBar(role + " · " + formatTime(left), progress, BossBar.Color.GREEN);
    }

    private void tickIntermission(long now) {
        long left = Math.max(0L, intermissionEndsMs - now);
        float progress = left / (float) (INTERMISSION_SECONDS * 1000L);
        scoreboard.updateBossBar("volgende ronde · " + formatTime(left), progress, BossBar.Color.BLUE);
        if (left <= 0) {
            if (roundNumber >= config.getRounds()) {
                endEvent(true);
            } else {
                CtfSide nextAttacker = roundNumber % 2 == 1 ? CtfSide.BLUE : CtfSide.RED;
                beginRound(roundNumber + 1, nextAttacker);
            }
        }
    }

    private void maintainFlagCarrierEffects() {
        if (flagCarrier == null) return;
        Player carrier = Bukkit.getPlayer(flagCarrier);
        if (carrier == null || !carrier.isOnline()) {
            returnFlagToSpawn();
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

    public void tryPickupFlag(Player player) {
        if (state != State.RUNNING) return;
        if (flagCarrier != null) return;

        CtfPlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isAlive()) return;
        if (ps.getSide() != attackingSide) {
            player.sendActionBar(Component.text(SmallText.of("alleen het aanvallende team mag de vlag pakken"),
                    NamedTextColor.RED));
            return;
        }

        Location flagLoc = config.getFlagSpawn();
        if (flagLoc == null || player.getWorld() != flagLoc.getWorld()) return;
        if (player.getLocation().distanceSquared(flagLoc) > FLAG_PICKUP_RADIUS * FLAG_PICKUP_RADIUS) return;

        flagCarrier = player.getUniqueId();
        removeFlagMarker();
        player.getInventory().setItemInOffHand(createFlagItem());
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 999999, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 999999, 0, false, false, true));

        Component msg = Component.text()
                .append(Component.text(player.getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(SmallText.of(" heeft de vlag!"), NamedTextColor.GOLD))
                .build();
        Bukkit.broadcast(Messages.PREFIX.append(msg));
        playSoundAll(Sound.ITEM_TOTEM_USE, 0.7f, 1.2f);
    }

    public void tryDeliverFlag(Player player) {
        if (state != State.RUNNING || flagCarrier == null) return;
        if (!flagCarrier.equals(player.getUniqueId())) return;

        CtfPlayerState ps = players.get(player.getUniqueId());
        if (ps == null || ps.getSide() != attackingSide) return;
        if (!config.isNearDelivery(attackingSide, player.getLocation())) return;

        ps.incrementCaptures();
        endRound(attackingSide, true);
    }

    private void endRound(CtfSide winner, boolean captured) {
        state = State.INTERMISSION;
        flagCarrier = null;
        removeFlagMarker();

        int wins = roundWins.getOrDefault(winner, 0) + 1;
        roundWins.put(winner, wins);
        awardRoundPoints(winner);

        String reason = captured ? "vlag afgeleverd!" : "tijd verstreken — verdediging hield stand";
        broadcastTitle(
                Component.text(winner.getDisplayName(), winner.getTextColor(), TextDecoration.BOLD),
                Component.text(SmallText.of(reason), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        launchFirework(config.getDelivery(winner), FireworkEffect.Type.STAR,
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

    public void returnFlagToSpawn() {
        if (flagCarrier != null) {
            Player carrier = Bukkit.getPlayer(flagCarrier);
            if (carrier != null) {
                carrier.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                carrier.removePotionEffect(PotionEffectType.SLOWNESS);
                carrier.removePotionEffect(PotionEffectType.GLOWING);
            }
        }
        flagCarrier = null;
        if (state == State.RUNNING) {
            spawnFlagMarker();
            Location loc = config.getFlagSpawn();
            if (loc != null && loc.getWorld() != null) {
                loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.02);
                loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.2f);
            }
            Bukkit.broadcast(Messages.info("De vlag is terug op spawn!"));
        }
    }

    private void spawnFlagMarker() {
        removeFlagMarker();
        Location loc = config.getFlagSpawn();
        if (loc == null || loc.getWorld() == null) return;

        ArmorStand stand = loc.getWorld().spawn(loc.clone().add(0, -0.4, 0), ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setInvulnerable(true);
            as.setCustomNameVisible(false);
            as.setSmall(true);
            as.getEquipment().setHelmet(createFlagItem());
        });
        flagMarkerId = stand.getUniqueId();
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

    public void handleDeath(Player player) {
        if (state != State.RUNNING && state != State.STARTING) return;
        CtfPlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isAlive()) return;

        ps.incrementDeaths();
        boolean hadFlag = flagCarrier != null && flagCarrier.equals(player.getUniqueId());

        if (hadFlag) {
            returnFlagToSpawn();
        }

        player.getInventory().clear();
        player.setHealth(20.0);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        clearPotionEffects(player);

        Location spawn = config.getSpawn(ps.getSide());
        if (spawn != null) {
            player.teleport(spawn);
        }
        giveKit(player, ps.getSide());

        Component announce = Component.text()
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .append(Component.text(SmallText.of(" is gevallen ("), NamedTextColor.GRAY))
                .append(Component.text(ps.getSide().getDisplayName(), ps.getSide().getTextColor()))
                .append(Component.text(hadFlag ? SmallText.of(") · vlag terug!") : SmallText.of(")"), NamedTextColor.GRAY))
                .build();
        Bukkit.broadcast(Messages.PREFIX.append(announce));
    }

    public boolean sameSide(UUID a, UUID b) {
        CtfPlayerState psA = players.get(a);
        CtfPlayerState psB = players.get(b);
        if (psA == null || psB == null) return false;
        return psA.getSide() == psB.getSide();
    }

    public void removeParticipant(Player player, boolean teleport) {
        if (flagCarrier != null && flagCarrier.equals(player.getUniqueId())) {
            returnFlagToSpawn();
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
        cancelTask(tickTask);
        tickTask = null;
        scoreboard.stop();
        removeFlagMarker();
        flagCarrier = null;

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
            sidePopulation.put(side, 0);
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
        clearPotionEffects(player);
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

        if (teleport && ps.getSavedLocation() != null && ps.getSavedLocation().getWorld() != null) {
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
