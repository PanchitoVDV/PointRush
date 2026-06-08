package be.panchito.pointRush.minigame.holdthecrown;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.minigame.MinigameStartEffects;
import be.panchito.pointRush.history.EventHistoryEntry;
import be.panchito.pointRush.history.EventHistoryManager;
import be.panchito.pointRush.minigame.ctf.CtfSide;
import be.panchito.pointRush.minigame.ctf.CtfSideAssigner;
import be.panchito.pointRush.storage.DataManager;
import be.panchito.pointRush.team.Team;
import be.panchito.pointRush.team.TeamManager;
import be.panchito.pointRush.util.LobbyWorld;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.MinigameText;
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
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hold the Crown: rood vs blauw, pak de kroon in het midden, 1 punt per minuut voor je team.
 */
public final class HoldTheCrownGame {

    public enum State { IDLE, STARTING, RUNNING }

    public static final int COUNTDOWN_SECONDS = 10;
    public static final long SPECTATOR_RESPAWN_MS = 3L * 60L * 1000L;
    public static final int WIN_TEAM_POINTS = 50;

    private final PointRush plugin;
    private final HoldTheCrownConfig config;
    private final TeamManager teamManager;
    private final DataManager dataManager;
    private final EventHistoryManager historyManager;
    private final HoldTheCrownScoreboard scoreboard;

    private State state = State.IDLE;
    private final Map<UUID, HoldTheCrownPlayerState> players = new HashMap<>();
    private final Map<CtfSide, Integer> sidePoints = new EnumMap<>(CtfSide.class);
    private final Map<CtfSide, Long> sideHoldTimeMs = new EnumMap<>(CtfSide.class);

    private UUID crownCarrier = null;
    private boolean crownAtCenter = true;
    private UUID centerMarkerId = null;
    private long crownProgressMs = 0L;

    private long countdownEndsMs = 0L;
    private long eventStartedAtMs = 0L;
    private long runEndsAtMs = 0L;
    private long lastTickMs = 0L;
    private long lastCrownParticleMs = 0L;
    private long lastCenterParticleMs = 0L;
    private boolean historyRecorded = false;

    private BukkitTask tickTask;

    public HoldTheCrownGame(PointRush plugin, HoldTheCrownConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.teamManager = plugin.getTeamManager();
        this.dataManager = plugin.getDataManager();
        this.historyManager = plugin.getEventHistoryManager();
        this.scoreboard = new HoldTheCrownScoreboard(plugin, this);
        for (CtfSide side : CtfSide.values()) {
            sidePoints.put(side, 0);
            sideHoldTimeMs.put(side, 0L);
        }
    }

    public State getState() {
        return state;
    }

    public HoldTheCrownConfig getConfig() {
        return config;
    }

    public PointRush getPlugin() {
        return plugin;
    }

    public Collection<HoldTheCrownPlayerState> getAllPlayerStates() {
        return Collections.unmodifiableCollection(players.values());
    }

    public boolean isParticipant(UUID id) {
        return players.containsKey(id);
    }

    public HoldTheCrownPlayerState getPlayerState(UUID id) {
        return players.get(id);
    }

    public Map<CtfSide, Integer> getSidePoints() {
        return Collections.unmodifiableMap(sidePoints);
    }

    public Map<CtfSide, Long> getSideHoldTimeMs() {
        return Collections.unmodifiableMap(sideHoldTimeMs);
    }

    public UUID getCrownCarrier() {
        return crownCarrier;
    }

    public boolean isCrownAtCenter() {
        return crownAtCenter;
    }

    public long getCrownProgressMs() {
        return crownProgressMs;
    }

    public long getCountdownTimeLeftMs() {
        if (state != State.STARTING) return 0L;
        return Math.max(0L, countdownEndsMs - System.currentTimeMillis());
    }

    public long getRunTimeLeftMs() {
        if (state != State.RUNNING) return 0L;
        return Math.max(0L, runEndsAtMs - System.currentTimeMillis());
    }

    public boolean isCrownItem(ItemStack item) {
        return CrownItem.isCrown(item);
    }

    public boolean start() {
        if (state != State.IDLE) return false;
        if (!config.isReady()) return false;

        if (!CrownItem.isAvailable()) {
            plugin.getLogger().warning("Hold the Crown: Nexo item '" + CrownItem.NEXO_ID
                    + "' niet gevonden — fallback helm wordt gebruikt.");
        }

        state = State.STARTING;
        countdownEndsMs = System.currentTimeMillis() + COUNTDOWN_SECONDS * 1000L;
        eventStartedAtMs = System.currentTimeMillis();
        lastTickMs = eventStartedAtMs;
        historyRecorded = false;
        crownCarrier = null;
        crownAtCenter = true;
        crownProgressMs = 0L;
        players.clear();
        for (CtfSide side : CtfSide.values()) {
            sidePoints.put(side, 0);
            sideHoldTimeMs.put(side, 0L);
        }

        List<Player> eligible = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getGameMode() == GameMode.CREATIVE || online.getGameMode() == GameMode.SPECTATOR) {
                online.sendMessage(Messages.warn("Je doet niet mee aan Hold the Crown (creative/spectator)."));
                continue;
            }
            if (!LobbyWorld.contains(plugin, online)) {
                continue;
            }
            eligible.add(online);
        }

        if (eligible.size() < 2) {
            Bukkit.broadcast(Messages.error("Hold the Crown heeft minstens 2 spelers nodig."));
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
                Component.text(SmallText.of("HOLD THE CROWN"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("rood vs blauw · 1 pt/min kroon · diamond kit"), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.4f);

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        MinigameStartEffects.onStarted(plugin);
        return true;
    }

    public boolean stop() {
        if (state == State.IDLE) return false;
        if (state == State.RUNNING) {
            endEventWithTimeUp(true);
            return true;
        }
        recordHistoryEntry();
        cleanupAfterStop();
        Bukkit.broadcast(Messages.info("Hold the Crown event afgelopen."));
        return true;
    }

    private void assignSides(List<Player> eligible) {
        Map<UUID, CtfSide> sides = CtfSideAssigner.assignSides(teamManager, eligible);

        for (Player p : eligible) {
            CtfSide side = sides.get(p.getUniqueId());
            HoldTheCrownPlayerState ps = new HoldTheCrownPlayerState(
                    p.getUniqueId(),
                    p.getLocation().clone(),
                    p.getGameMode()
            );
            ps.setSide(side);
            players.put(p.getUniqueId(), ps);
        }
    }

    private void joinPlayer(Player player) {
        HoldTheCrownPlayerState ps = players.get(player.getUniqueId());
        if (ps == null) return;

        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        clearPotionEffects(player);
        player.setGlowing(false);

        Location spawn = config.getSpawn(ps.getSide());
        if (spawn != null) {
            plugin.getTeleporter().teleport(player, spawn);
        }
        giveKit(player, ps.getSide());
        scoreboard.attach(player);
        Team prTeam = teamManager.getTeamOfPlayer(player.getUniqueId());
        if (prTeam != null) {
            player.sendMessage(Messages.info("PointRush-team " + prTeam.getName()
                    + " speelt als " + ps.getSide().getDisplayName() + "."));
        }
        player.sendMessage(Messages.info("Je zit in team "
                + ps.getSide().getDisplayName() + " — pak de kroon in het midden!"));
    }

    public void giveKit(Player player, CtfSide side) {
        player.getInventory().clear();

        ArmorTrim trim = side == CtfSide.RED
                ? new ArmorTrim(TrimMaterial.REDSTONE, TrimPattern.VEX)
                : new ArmorTrim(TrimMaterial.LAPIS, TrimPattern.COAST);

        ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
        ItemStack chest = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemStack legs = new ItemStack(Material.DIAMOND_LEGGINGS);
        ItemStack boots = new ItemStack(Material.DIAMOND_BOOTS);
        for (ItemStack piece : List.of(helmet, chest, legs, boots)) {
            piece.editMeta(ArmorMeta.class, meta -> {
                meta.setTrim(trim);
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            });
        }
        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chest);
        player.getInventory().setLeggings(legs);
        player.getInventory().setBoots(boots);

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.editMeta(meta -> {
            meta.displayName(Component.text("Zwaard", side.getTextColor(), TextDecoration.BOLD));
            meta.addEnchant(Enchantment.SHARPNESS, 3, true);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        });

        ItemStack bow = new ItemStack(Material.BOW);
        bow.editMeta(meta -> {
            meta.displayName(Component.text("Boog", side.getTextColor(), TextDecoration.BOLD));
            meta.addEnchant(Enchantment.POWER, 2, true);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        });

        player.getInventory().setItem(0, sword);
        player.getInventory().setItem(1, bow);
        player.getInventory().setItem(2, new ItemStack(Material.ARROW, 64));
        player.getInventory().setItem(3, new ItemStack(Material.GOLDEN_APPLE, 8));
        player.getInventory().setItem(4, new ItemStack(Material.COOKED_BEEF, 16));

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
        crownCarrier = null;
        crownAtCenter = true;
        crownProgressMs = 0L;

        spawnCenterMarker();
        broadcastTitle(
                MinigameText.goTitle(),
                Component.text(SmallText.of("pak de kroon in het midden!"), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.6f);
        launchFirework(config.getCenter(), FireworkEffect.Type.BALL_LARGE, Color.YELLOW, Color.ORANGE);
    }

    private void tickRunning(long now) {
        long deltaMs = Math.max(0L, now - lastTickMs);
        if (deltaMs > 500L) deltaMs = 50L;

        if (now >= runEndsAtMs) {
            endEventWithTimeUp(false);
            return;
        }

        processRespawns(now);
        updateCrownScoring(deltaMs, now);
        maintainCrownEffects(now);
        tickCenterPickup(now);
        if (now - lastCenterParticleMs >= 200L) {
            lastCenterParticleMs = now;
            tickCenterParticles();
        }
        updateBossBar(now);
        broadcastSpectatorActionBars(now);
    }

    private void tickCenterPickup(long now) {
        if (!crownAtCenter || crownCarrier != null) return;

        for (HoldTheCrownPlayerState ps : players.values()) {
            if (!ps.isAlive()) continue;
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p == null || p.getGameMode() != GameMode.SURVIVAL) continue;
            if (!config.isNearCenter(p.getLocation())) continue;
            tryEquipCrown(p, ps, now);
            return;
        }
    }

    public void tryPickupDroppedCrown(Player player) {
        if (state != State.RUNNING) return;
        if (crownCarrier != null || crownAtCenter) return;

        HoldTheCrownPlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isAlive()) return;
        if (player.getGameMode() != GameMode.SURVIVAL) return;

        Location loc = player.getLocation();
        double radiusSq = 2.0 * 2.0;
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, 2, 2, 2)) {
            if (!(entity instanceof Item itemEntity)) continue;
            if (!isCrownItem(itemEntity.getItemStack())) continue;
            if (itemEntity.getLocation().distanceSquared(loc) > radiusSq) continue;

            itemEntity.remove();
            tryEquipCrown(player, ps, System.currentTimeMillis());
            return;
        }
    }

    private void tryEquipCrown(Player player, HoldTheCrownPlayerState ps, long now) {
        if (crownCarrier != null) return;

        crownAtCenter = false;
        crownCarrier = player.getUniqueId();
        crownProgressMs = 0L;
        removeCenterMarker();

        ItemStack savedHelmet = player.getInventory().getHelmet();
        if (savedHelmet != null && !savedHelmet.getType().isAir()) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(savedHelmet.clone());
            for (ItemStack stack : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            }
        }
        player.getInventory().setHelmet(CrownItem.create());
        ps.setCrownSessionStartMs(now);

        player.setGlowing(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 999999, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 999999, 0, false, false, true));
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GOLD, 1.0f, 1.2f);

        Component msg = Component.text()
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .append(Component.text(SmallText.of(" heeft de kroon! "), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(ps.getSide().getDisplayName(), ps.getSide().getTextColor(), TextDecoration.BOLD))
                .build();
        Bukkit.broadcast(Messages.PREFIX.append(msg));
    }

    private void stripCrown(long now) {
        if (crownCarrier == null) return;

        HoldTheCrownPlayerState ps = players.get(crownCarrier);
        Player carrier = Bukkit.getPlayer(crownCarrier);
        if (ps != null && ps.getCrownSessionStartMs() > 0L) {
            long sessionMs = now - ps.getCrownSessionStartMs();
            ps.addHoldTimeMs(sessionMs);
            sideHoldTimeMs.merge(ps.getSide(), sessionMs, Long::sum);
            ps.setCrownSessionStartMs(0L);
        }

        if (carrier != null && carrier.isOnline()) {
            carrier.setGlowing(false);
            carrier.removePotionEffect(PotionEffectType.GLOWING);
            carrier.removePotionEffect(PotionEffectType.SLOWNESS);
            ItemStack helmet = carrier.getInventory().getHelmet();
            if (isCrownItem(helmet)) {
                carrier.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
            }
        }

        crownCarrier = null;
        crownProgressMs = 0L;
    }

    public void returnCrownToCenter() {
        stripCrown(System.currentTimeMillis());
        crownAtCenter = true;
        spawnCenterMarker();
    }

    public void dropCrownAt(Location loc) {
        stripCrown(System.currentTimeMillis());
        crownAtCenter = false;
        if (loc.getWorld() != null) {
            loc.getWorld().dropItemNaturally(loc.clone().add(0, 0.5, 0), CrownItem.create());
            loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.6f);
        }
    }

    private void updateCrownScoring(long deltaMs, long now) {
        if (crownCarrier == null) return;

        HoldTheCrownPlayerState ps = players.get(crownCarrier);
        Player carrier = Bukkit.getPlayer(crownCarrier);
        if (ps == null || !ps.isAlive() || carrier == null
                || carrier.getGameMode() != GameMode.SURVIVAL) {
            return;
        }

        crownProgressMs += deltaMs;

        long interval = config.getPointIntervalMs();
        while (crownProgressMs >= interval) {
            awardCrownPoint(ps.getSide(), carrier, now);
            crownProgressMs -= interval;
        }
    }

    private void awardCrownPoint(CtfSide side, Player carrier, long now) {
        int score = sidePoints.getOrDefault(side, 0) + 1;
        sidePoints.put(side, score);

        Component msg = Component.text()
                .append(Component.text(SmallText.of("kroon punt! "), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(side.getDisplayName(), side.getTextColor(), TextDecoration.BOLD))
                .append(Component.text(SmallText.of(" · " + score + " pts · "), NamedTextColor.GRAY))
                .append(Component.text(carrier.getName(), NamedTextColor.WHITE))
                .build();
        Bukkit.broadcast(Messages.PREFIX.append(msg));
        playSoundAll(Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

        Location center = config.getCenter();
        if (center != null && center.getWorld() != null) {
            center.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, center.clone().add(0, 1, 0),
                    20, 0.8, 0.5, 0.8, 0.05);
        }
    }

    private void maintainCrownEffects(long now) {
        if (crownCarrier == null) return;
        Player carrier = Bukkit.getPlayer(crownCarrier);
        if (carrier == null || !carrier.isOnline()) {
            returnCrownToCenter();
            return;
        }

        HoldTheCrownPlayerState ps = players.get(crownCarrier);
        if (ps == null || !ps.isAlive()) {
            return;
        }

        carrier.setGlowing(true);
        carrier.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false, true));
        carrier.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, false, true));

        // Cosmetische particles ~elke 200ms i.p.v. elke tick (bespaart packets + DustOptions-allocatie).
        Location loc = carrier.getLocation();
        if (loc.getWorld() != null && now - lastCrownParticleMs >= 200L) {
            lastCrownParticleMs = now;
            loc.getWorld().spawnParticle(
                    Particle.END_ROD,
                    loc.clone().add(0, 1.0, 0),
                    3, 0.35, 0.5, 0.35, 0.01
            );
            loc.getWorld().spawnParticle(
                    Particle.DUST,
                    loc.clone().add(0, 0.2, 0),
                    6, 0.4, 0.1, 0.4,
                    new Particle.DustOptions(ps.getSide() == CtfSide.RED
                            ? Color.fromRGB(255, 60, 60) : Color.fromRGB(60, 100, 255), 1.2f)
            );
        }

        ItemStack helmet = carrier.getInventory().getHelmet();
        if (!isCrownItem(helmet)) {
            carrier.getInventory().setHelmet(CrownItem.create());
        }
    }

    private void tickCenterParticles() {
        if (!crownAtCenter) return;
        Location center = config.getCenter();
        if (center == null || center.getWorld() == null) return;

        center.getWorld().spawnParticle(
                Particle.WAX_ON,
                center.clone().add(0, 1.2, 0),
                2, 0.3, 0.2, 0.3, 0.01
        );
    }

    private void spawnCenterMarker() {
        removeCenterMarker();
        Location center = config.getCenter();
        if (center == null || center.getWorld() == null) return;

        Location loc = center.clone().add(0, -0.4, 0);
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setInvulnerable(true);
            as.setCustomNameVisible(false);
            as.setSmall(true);
            as.getEquipment().setHelmet(CrownItem.create());
        });
        centerMarkerId = stand.getUniqueId();
    }

    private void removeCenterMarker() {
        if (centerMarkerId == null) return;
        UUID id = centerMarkerId;
        centerMarkerId = null;
        for (World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(id);
            if (entity != null) {
                entity.remove();
                return;
            }
        }
    }

    private void updateBossBar(long now) {
        long left = Math.max(0L, runEndsAtMs - now);
        float timeProgress = Math.min(1f, left / (float) config.getDurationMs());

        if (crownCarrier != null) {
            HoldTheCrownPlayerState ps = players.get(crownCarrier);
            Player carrier = Bukkit.getPlayer(crownCarrier);
            long interval = config.getPointIntervalMs();
            float pointProgress = Math.min(1f, crownProgressMs / (float) interval);
            String carrierName = carrier != null ? carrier.getName() : "?";
            CtfSide side = ps != null ? ps.getSide() : CtfSide.RED;
            String label = side.getDisplayName() + " · " + carrierName
                    + " · punt in " + formatTime(Math.max(0L, interval - crownProgressMs))
                    + " · rest " + formatTime(left);
            BossBar.Color color = side == CtfSide.RED ? BossBar.Color.RED : BossBar.Color.BLUE;
            scoreboard.updateBossBar(label, pointProgress, color);
        } else {
            scoreboard.updateBossBar(
                    SmallText.of("kroon vrij · rest " + formatTime(left)),
                    timeProgress,
                    BossBar.Color.YELLOW
            );
        }
    }

    private void processRespawns(long now) {
        for (HoldTheCrownPlayerState ps : players.values()) {
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

            Location spawn = config.getSpawn(ps.getSide());
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
        for (HoldTheCrownPlayerState ps : players.values()) {
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
        HoldTheCrownPlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isAlive()) return;

        if (crownCarrier != null && crownCarrier.equals(player.getUniqueId())) {
            dropCrownAt(player.getLocation());
        }

        ps.setAlive(false);
        ps.incrementDeaths();
        ps.setRespawnAtMs(System.currentTimeMillis() + SPECTATOR_RESPAWN_MS);

        PlayerRespawnUtil.forceRespawnIfDead(player);

        Location loc = player.getLocation();
        loc.getWorld().spawnParticle(Particle.SMOKE, loc, 20, 0.4, 0.5, 0.4, 0.02);
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_DEATH, 1.0f, 1.0f);

        Component announce = Component.text()
                .append(Component.text(player.getName(), NamedTextColor.WHITE))
                .append(Component.text(SmallText.of(" is gevallen ("), NamedTextColor.GRAY))
                .append(Component.text(ps.getSide().getDisplayName(), ps.getSide().getTextColor()))
                .append(Component.text(SmallText.of(") · respawn 3m"), NamedTextColor.GRAY))
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
                recordHistoryEntry();
                cleanupAfterStop();
            }
            return;
        }

        stripCrown(System.currentTimeMillis());
        awardWinningTeamPoints();

        List<HoldTheCrownPlayerState> ranked = rankByHoldTime();
        HoldTheCrownPlayerState winner = ranked.isEmpty() ? null : ranked.get(0);

        Component winnerLine = buildWinnerLine(winner, ranked);
        launchFirework(config.getCenter(), FireworkEffect.Type.BALL_LARGE, Color.YELLOW, Color.fromRGB(255, 215, 0));
        broadcastTitle(
                Component.text(SmallText.of("TIJD OP!"), NamedTextColor.GOLD, TextDecoration.BOLD),
                winnerLine
        );
        Bukkit.broadcast(Messages.PREFIX.append(winnerLine));

        recordHistoryEntry();
        state = State.STARTING;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != State.IDLE) {
                cleanupAfterStop();
                Bukkit.broadcast(Messages.info("Hold the Crown event afgelopen."));
            }
        }, 80L);
    }

    private List<HoldTheCrownPlayerState> rankByHoldTime() {
        long now = System.currentTimeMillis();
        List<HoldTheCrownPlayerState> list = new ArrayList<>(players.values());
        list.sort(Comparator.comparingLong((HoldTheCrownPlayerState ps) -> ps.totalHoldTimeMs(now)).reversed());
        return list;
    }

    private void awardWinningTeamPoints() {
        CtfSide winningSide = determineWinningSide();
        if (winningSide == null) {
            return;
        }

        boolean awarded = false;
        for (HoldTheCrownPlayerState ps : players.values()) {
            if (ps.getSide() != winningSide) continue;
            ps.setPlacement(1);
            Team team = teamManager.getTeamOfPlayer(ps.getUuid());
            if (team != null) {
                team.addPoints(WIN_TEAM_POINTS);
                awarded = true;
            }
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p != null) {
                p.sendMessage(Messages.success("Team " + winningSide.getDisplayName()
                        + " heeft gewonnen! +" + WIN_TEAM_POINTS + " punten"
                        + (team != null ? " voor team " + team.getName() : "") + "."));
            }
        }
        if (awarded) {
            dataManager.save();
        }
    }

    private CtfSide determineWinningSide() {
        int red = sidePoints.getOrDefault(CtfSide.RED, 0);
        int blue = sidePoints.getOrDefault(CtfSide.BLUE, 0);
        if (red == blue) {
            return null;
        }
        return red > blue ? CtfSide.RED : CtfSide.BLUE;
    }

    private Component buildWinnerLine(HoldTheCrownPlayerState winner, List<HoldTheCrownPlayerState> ranked) {
        if (winner == null || winner.totalHoldTimeMs(System.currentTimeMillis()) <= 0L) {
            return Component.text(SmallText.of("niemand hield de kroon — gelijkspel"), NamedTextColor.GRAY);
        }
        Player wp = Bukkit.getPlayer(winner.getUuid());
        String name = wp != null ? wp.getName() : winner.getUuid().toString().substring(0, 8);
        long holdMs = winner.totalHoldTimeMs(System.currentTimeMillis());

        var builder = Component.text()
                .append(Component.text(SmallText.of("winnaar: "), NamedTextColor.GRAY))
                .append(Component.text(name, NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(SmallText.of(" · kroon "), NamedTextColor.GRAY))
                .append(Component.text(formatTime(holdMs), NamedTextColor.WHITE))
                .append(Component.text(SmallText.of(" · "), NamedTextColor.DARK_GRAY))
                .append(Component.text(winner.getSide().getDisplayName(), winner.getSide().getTextColor()));

        builder.append(Component.text(SmallText.of(" · R "), NamedTextColor.DARK_GRAY))
                .append(Component.text(String.valueOf(sidePoints.get(CtfSide.RED)), NamedTextColor.RED))
                .append(Component.text(SmallText.of(" · B "), NamedTextColor.DARK_GRAY))
                .append(Component.text(String.valueOf(sidePoints.get(CtfSide.BLUE)), NamedTextColor.BLUE));

        if (ranked.size() > 1 && ranked.get(1).totalHoldTimeMs(System.currentTimeMillis()) > 0L) {
            HoldTheCrownPlayerState second = ranked.get(1);
            Player sp = Bukkit.getPlayer(second.getUuid());
            String sName = sp != null ? sp.getName() : "?";
            builder.append(Component.text(SmallText.of(" · #2 "), NamedTextColor.DARK_GRAY))
                    .append(Component.text(sName, NamedTextColor.WHITE));
        }
        return builder.build();
    }

    private void recordHistoryEntry() {
        if (historyManager == null || historyRecorded || players.isEmpty()) return;
        historyRecorded = true;

        long now = System.currentTimeMillis();
        List<HoldTheCrownPlayerState> sorted = rankByHoldTime();

        List<EventHistoryEntry.Placement> placements = new ArrayList<>();
        int rank = 1;
        for (HoldTheCrownPlayerState ps : sorted) {
            long holdMs = ps.totalHoldTimeMs(now);
            if (holdMs <= 0L && ps.getPlacement() <= 0) continue;

            Player p = Bukkit.getPlayer(ps.getUuid());
            String name = p != null ? p.getName() : ps.getUuid().toString().substring(0, 8);
            Team team = teamManager.getTeamOfPlayer(ps.getUuid());
            int score = ps.getPlacement() == 1 ? WIN_TEAM_POINTS : 0;
            String detail = ps.getSide().getDisplayName() + " · kroon " + formatTime(holdMs)
                    + " · team pts R" + sidePoints.get(CtfSide.RED)
                    + "/B" + sidePoints.get(CtfSide.BLUE)
                    + " · " + ps.getDeaths() + " deaths";
            placements.add(new EventHistoryEntry.Placement(
                    ps.getPlacement() > 0 ? ps.getPlacement() : rank,
                    ps.getUuid(),
                    name,
                    team != null ? team.getId() : null,
                    team != null ? team.getName() : null,
                    team != null ? team.getColor().toString() : null,
                    score,
                    detail
            ));
            rank++;
        }
        if (placements.isEmpty()) return;

        long started = eventStartedAtMs > 0 ? eventStartedAtMs : System.currentTimeMillis();
        historyManager.record(new EventHistoryEntry(
                UUID.randomUUID().toString(),
                "holdthecrown",
                started,
                System.currentTimeMillis(),
                placements
        ));
    }

    private void cleanupAfterStop() {
        state = State.IDLE;
        cancelTask(tickTask);
        tickTask = null;
        scoreboard.stop();
        stripCrown(System.currentTimeMillis());
        removeCenterMarker();

        Title endTitle = Title.title(
                Component.text(SmallText.of("event afgelopen"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("teleport terug naar je startlocatie"), NamedTextColor.GRAY)
        );
        for (Map.Entry<UUID, HoldTheCrownPlayerState> entry : new ArrayList<>(players.entrySet())) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                p.showTitle(endTitle);
                restorePlayer(p, entry.getValue(), true);
            }
        }
        players.clear();
        for (CtfSide side : CtfSide.values()) {
            sidePoints.put(side, 0);
            sideHoldTimeMs.put(side, 0L);
        }
        crownCarrier = null;
        crownAtCenter = true;
        crownProgressMs = 0L;
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            try {
                task.cancel();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    public void removeParticipant(Player player, boolean teleport) {
        if (crownCarrier != null && crownCarrier.equals(player.getUniqueId())) {
            returnCrownToCenter();
        }
        HoldTheCrownPlayerState ps = players.remove(player.getUniqueId());
        if (ps == null) return;
        scoreboard.detach(player);
        restorePlayer(player, ps, teleport);

        if (players.isEmpty() && state != State.IDLE) {
            recordHistoryEntry();
            cleanupAfterStop();
        }
    }

    private void restorePlayer(Player player, HoldTheCrownPlayerState ps, boolean teleport) {
        PlayerRespawnUtil.prepareForRestore(player);
        player.setGlowing(false);
        player.removePotionEffect(PotionEffectType.GLOWING);
        clearPotionEffects(player);

        for (Player other : Bukkit.getOnlinePlayers()) {
            player.showPlayer(plugin, other);
            other.showPlayer(plugin, player);
        }

        if (ps.getSavedGameMode() != null) {
            player.setGameMode(ps.getSavedGameMode());
        }

        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setHealth(20.0);

        if (!teleport || ps.getSavedLocation() == null || ps.getSavedLocation().getWorld() == null) {
            return;
        }
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

    private void clearPotionEffects(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
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
