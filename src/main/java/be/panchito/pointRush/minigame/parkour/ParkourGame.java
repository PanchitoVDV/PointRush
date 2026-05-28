package be.panchito.pointRush.minigame.parkour;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.minigame.gadgets.MinigameGadgetItems;
import be.panchito.pointRush.minigame.gadgets.MinigameGadgetMode;
import be.panchito.pointRush.shop.MinigameShopHook;
import be.panchito.pointRush.history.EventHistoryEntry;
import be.panchito.pointRush.history.EventHistoryManager;
import be.panchito.pointRush.storage.DataManager;
import be.panchito.pointRush.team.Team;
import be.panchito.pointRush.team.TeamManager;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.MinigameText;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core parkour minigame engine.
 *
 * <p>Lifecycle:
 * <ul>
 *     <li>{@link State#IDLE} - no event running.</li>
 *     <li>{@link State#STARTING} - players are frozen at spawn, countdown is ticking.</li>
 *     <li>{@link State#RUNNING} - players are racing; finishes award placements + points.</li>
 * </ul>
 */
public final class ParkourGame {

    public enum State { IDLE, STARTING, RUNNING }

    /** Points awarded for placements 1..10. */
    public static final int[] PLACEMENT_POINTS = { 100, 80, 60, 50, 40, 30, 25, 20, 15, 10 };

    public static final int COUNTDOWN_SECONDS = 10;
    /** Hard cap so an event never hangs forever (10 minutes). */
    public static final long EVENT_TIMEOUT_TICKS = 10L * 60L * 20L;
    /** Squared distance at which a player is considered to have touched a checkpoint/finish. */
    public static final double TOUCH_RADIUS_SQ = 4.0;

    public static final String ITEM_CHECKPOINT = "checkpoint";
    public static final String ITEM_VISIBILITY = "visibility";

    private final PointRush plugin;
    private final ParkourConfig config;
    private final TeamManager teamManager;
    private final DataManager dataManager;
    private final EventHistoryManager historyManager;
    private final NamespacedKey itemKey;
    private final ParkourScoreboard scoreboard;

    private State state = State.IDLE;
    private final Map<UUID, ParkourPlayerState> players = new HashMap<>();
    private final Map<UUID, Long> gadgetCooldownUntilMs = new ConcurrentHashMap<>();
    private int nextPlacement = 1;
    private long eventStartedAtMs = 0L;
    private BukkitTask countdownTask;
    private BukkitTask timeoutTask;

    public ParkourGame(PointRush plugin, ParkourConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.teamManager = plugin.getTeamManager();
        this.dataManager = plugin.getDataManager();
        this.historyManager = plugin.getEventHistoryManager();
        this.itemKey = new NamespacedKey(plugin, "parkour_item");
        this.scoreboard = new ParkourScoreboard(plugin, this);
    }

    /** Read-only view of all player states - used by the scoreboard. */
    public Collection<ParkourPlayerState> getAllPlayerStates() {
        return Collections.unmodifiableCollection(players.values());
    }

    public State getState() {
        return state;
    }

    public ParkourConfig getConfig() {
        return config;
    }

    public boolean isParticipant(UUID id) {
        return players.containsKey(id);
    }

    public ParkourPlayerState getPlayerState(UUID id) {
        return players.get(id);
    }

    public NamespacedKey getItemKey() {
        return itemKey;
    }

    public PointRush getPlugin() {
        return plugin;
    }

    /** Milliseconds epoch when the player may use another gadget (shared cooldown). */
    public Map<UUID, Long> getGadgetCooldownMap() {
        return gadgetCooldownUntilMs;
    }

    /** Reads the parkour-item tag from an item, or {@code null} if it isn't one of ours. */
    public String getItemTag(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    public boolean start() {
        if (state != State.IDLE) return false;
        if (!config.isReady()) return false;

        state = State.STARTING;
        nextPlacement = 1;
        eventStartedAtMs = System.currentTimeMillis();

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getGameMode() == GameMode.CREATIVE || online.getGameMode() == GameMode.SPECTATOR) {
                online.sendMessage(Messages.warn("Je doet niet mee aan parkour (creative/spectator)."));
                continue;
            }
            joinPlayer(online);
        }

        if (players.isEmpty()) {
            Bukkit.broadcast(Messages.error("Parkour heeft minstens 1 speler nodig."));
            state = State.IDLE;
            scoreboard.stop();
            return false;
        }

        broadcastTitle("Parkour", "start in " + COUNTDOWN_SECONDS + " seconden");
        startCountdown();
        scoreboard.start();
        return true;
    }

    public boolean stop() {
        if (state == State.IDLE) return false;
        recordHistoryEntry();
        state = State.IDLE;

        cancelTask(countdownTask);
        countdownTask = null;
        cancelTask(timeoutTask);
        timeoutTask = null;
        scoreboard.stop();

        Title endTitle = Title.title(
                Component.text(SmallText.of("event afgelopen"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("teleport terug naar je startlocatie"), NamedTextColor.GRAY)
        );
        for (Map.Entry<UUID, ParkourPlayerState> entry : new ArrayList<>(players.entrySet())) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                p.showTitle(endTitle);
                restorePlayer(p, entry.getValue(), true);
            }
        }
        players.clear();
        gadgetCooldownUntilMs.clear();

        Bukkit.broadcast(Messages.info("Parkour event afgelopen."));
        return true;
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            try { task.cancel(); } catch (IllegalStateException ignored) {}
        }
    }

    /**
     * Builds a history entry for the current event and hands it to the history
     * manager. Called from {@link #stop()} before state is cleared so the
     * {@code players} map is still populated. Events without finishers are
     * skipped to avoid cluttering the history.
     */
    private void recordHistoryEntry() {
        if (historyManager == null || players.isEmpty()) return;

        List<ParkourPlayerState> sorted = new ArrayList<>(players.values());
        sorted.sort(Comparator
                .comparing(ParkourPlayerState::isFinished).reversed()
                .thenComparingInt(ParkourPlayerState::getPlacement));

        List<EventHistoryEntry.Placement> placements = new ArrayList<>();
        for (ParkourPlayerState ps : sorted) {
            if (!ps.isFinished()) continue;
            Player p = Bukkit.getPlayer(ps.getUuid());
            String name = p != null ? p.getName() : ps.getUuid().toString().substring(0, 8);
            Team team = teamManager.getTeamOfPlayer(ps.getUuid());
            int score = ps.getPlacement() >= 1 && ps.getPlacement() <= PLACEMENT_POINTS.length
                    ? PLACEMENT_POINTS[ps.getPlacement() - 1]
                    : 0;
            placements.add(new EventHistoryEntry.Placement(
                    ps.getPlacement(),
                    ps.getUuid(),
                    name,
                    team != null ? team.getId() : null,
                    team != null ? team.getName() : null,
                    team != null ? team.getColor().toString() : null,
                    score,
                    null
            ));
        }
        if (placements.isEmpty()) return;

        long started = eventStartedAtMs > 0 ? eventStartedAtMs : System.currentTimeMillis();
        historyManager.record(new EventHistoryEntry(
                UUID.randomUUID().toString(),
                "parkour",
                started,
                System.currentTimeMillis(),
                placements
        ));
    }

    private void joinPlayer(Player player) {
        ItemStack[] inv = player.getInventory().getContents();
        ItemStack[] saved = new ItemStack[inv.length];
        for (int i = 0; i < inv.length; i++) {
            saved[i] = inv[i] != null ? inv[i].clone() : null;
        }

        ParkourPlayerState ps = new ParkourPlayerState(
                player.getUniqueId(),
                player.getLocation().clone(),
                player.getGameMode(),
                saved
        );
        players.put(player.getUniqueId(), ps);
        MinigameShopHook.applyParkourJoin(plugin, player, ps);

        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFallDistance(0f);

        Location spawn = config.getSpawn();
        if (spawn != null) {
            player.teleport(spawn);
        }
        giveItems(player);
        scoreboard.attach(player);

        player.sendMessage(Messages.info("Parkour event start binnenkort. Maak je klaar!"));
    }

    private void giveItems(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.setItem(0, createItem(Material.FEATHER, "Laatste checkpoint", ITEM_CHECKPOINT,
                "Rechtermuisklik om naar je laatste checkpoint te gaan."));
        inv.setItem(8, createItem(Material.ENDER_EYE, visibilityLabel(ParkourPlayerState.VisibilityMode.ALL), ITEM_VISIBILITY,
                "Rechtermuisklik om visibility te wisselen."));
        MinigameGadgetItems.giveGadgetRow(plugin, inv, MinigameGadgetMode.PARKOUR);
        player.getInventory().setHeldItemSlot(0);
    }

    private ItemStack createItem(Material material, String name, String tag, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(SmallText.of(name), NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(SmallText.of(lore), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, tag);
        item.setItemMeta(meta);
        return item;
    }

    private void startCountdown() {
        countdownTask = new BukkitRunnable() {
            int remaining = COUNTDOWN_SECONDS;

            @Override
            public void run() {
                if (state != State.STARTING) {
                    cancel();
                    return;
                }
                if (remaining > 0) {
                    broadcastActionBar("start in " + remaining + "s");
                    if (remaining <= 5) {
                        playSoundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                    }
                    remaining--;
                } else {
                    beginRace();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void beginRace() {
        state = State.RUNNING;
        scoreboard.markRaceStart();
        int dur = (int) Math.min((long) Integer.MAX_VALUE / 16, EVENT_TIMEOUT_TICKS);
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            ParkourPlayerState ps = players.get(id);
            if (p != null && ps != null) {
                if (ps.hasShopParkourSpeed()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, dur, 0, false, false, true));
                }
                if (ps.hasShopParkourJump()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, dur, 0, false, false, true));
                }
                if (ps.hasShopParkourCloud()) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 240, 0, false, false, true));
                }
                p.showTitle(Title.title(
                        MinigameText.goTitle(),
                        Component.text(SmallText.of("rush voor de finish"), NamedTextColor.GRAY)
                ));
            }
        }
        playSoundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.6f);
        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, this::stop, EVENT_TIMEOUT_TICKS);
    }

    public void onCheckpointReached(Player player, int index) {
        ParkourPlayerState ps = players.get(player.getUniqueId());
        if (ps == null || ps.isFinished() || ps.getCheckpointIndex() >= index) return;
        ps.setCheckpointIndex(index);
        ps.setLastProgressTimeMs(System.currentTimeMillis());
        player.sendActionBar(Messages.success("Checkpoint " + (index + 1) + " / " + config.getCheckpoints().size()));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
    }

    public void onFinishReached(Player player) {
        ParkourPlayerState ps = players.get(player.getUniqueId());
        if (ps == null || ps.isFinished()) return;

        int placement = nextPlacement++;
        ps.setPlacement(placement);
        ps.setLastProgressTimeMs(System.currentTimeMillis());

        int points = placement <= PLACEMENT_POINTS.length ? PLACEMENT_POINTS[placement - 1] : 0;
        Team team = teamManager.getTeamOfPlayer(player.getUniqueId());
        if (team != null && points > 0) {
            team.addPoints(points);
            dataManager.save();
        }

        Component prefix = Messages.PREFIX;
        Component msg;
        if (team != null) {
            msg = prefix
                    .append(Component.text(SmallText.of(player.getName()), NamedTextColor.WHITE))
                    .append(Component.text(SmallText.of(" finishte als #" + placement
                            + " (+" + points + " pts) - team "), NamedTextColor.GRAY))
                    .append(Component.text(SmallText.of(team.getName()), team.getColor()));
        } else {
            msg = prefix
                    .append(Component.text(SmallText.of(player.getName()), NamedTextColor.WHITE))
                    .append(Component.text(SmallText.of(" finishte als #" + placement
                            + " (geen team, geen punten)"), NamedTextColor.GRAY));
        }
        broadcastMessage(msg);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        player.setGameMode(GameMode.SPECTATOR);
        if (config.getSpawn() != null) {
            player.teleport(config.getSpawn());
        }

        boolean anyRunning = false;
        for (ParkourPlayerState s : players.values()) {
            if (!s.isFinished()) {
                anyRunning = true;
                break;
            }
        }
        if (!anyRunning) {
            stop();
        }
    }

    public void teleportToCheckpoint(Player player) {
        ParkourPlayerState ps = players.get(player.getUniqueId());
        if (ps == null || ps.isFinished()) return;
        Location target;
        int idx = ps.getCheckpointIndex();
        if (idx >= 0 && idx < config.getCheckpoints().size()) {
            target = config.getCheckpoints().get(idx);
        } else {
            target = config.getSpawn();
        }
        if (target == null) return;
        player.setFallDistance(0f);
        player.setFireTicks(0);
        player.teleport(target);
        player.sendActionBar(Messages.info("Terug naar checkpoint."));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
    }

    public void cycleVisibility(Player player) {
        ParkourPlayerState ps = players.get(player.getUniqueId());
        if (ps == null) return;
        ParkourPlayerState.VisibilityMode next = switch (ps.getVisibilityMode()) {
            case ALL -> ParkourPlayerState.VisibilityMode.TEAM;
            case TEAM -> ParkourPlayerState.VisibilityMode.NONE;
            case NONE -> ParkourPlayerState.VisibilityMode.ALL;
        };
        ps.setVisibilityMode(next);
        applyVisibility(player, ps);

        ItemStack eye = player.getInventory().getItem(8);
        if (eye != null && ITEM_VISIBILITY.equals(getItemTag(eye))) {
            ItemMeta meta = eye.getItemMeta();
            meta.displayName(Component.text(SmallText.of(visibilityLabel(next)),
                            NamedTextColor.GOLD, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            eye.setItemMeta(meta);
        }

        player.sendActionBar(Messages.info("Zichtbaarheid: " + visibilityLabel(next)));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private String visibilityLabel(ParkourPlayerState.VisibilityMode mode) {
        return switch (mode) {
            case ALL -> "zicht: iedereen";
            case TEAM -> "zicht: team";
            case NONE -> "zicht: niemand";
        };
    }

    private void applyVisibility(Player viewer, ParkourPlayerState ps) {
        Team viewerTeam = teamManager.getTeamOfPlayer(viewer.getUniqueId());
        for (UUID otherId : players.keySet()) {
            if (otherId.equals(viewer.getUniqueId())) continue;
            Player other = Bukkit.getPlayer(otherId);
            if (other == null) continue;
            boolean show = switch (ps.getVisibilityMode()) {
                case ALL -> true;
                case TEAM -> viewerTeam != null && viewerTeam.isMember(otherId);
                case NONE -> false;
            };
            if (show) {
                viewer.showPlayer(plugin, other);
            } else {
                viewer.hidePlayer(plugin, other);
            }
        }
    }

    /** Called by the listener on quit OR by /parkour leave. Restores the player without teleport when offline. */
    public void removeParticipant(Player player, boolean teleport) {
        gadgetCooldownUntilMs.remove(player.getUniqueId());
        ParkourPlayerState ps = players.remove(player.getUniqueId());
        if (ps == null) return;
        scoreboard.detach(player);
        restorePlayer(player, ps, teleport);

        if (players.isEmpty() && state != State.IDLE) {
            stop();
        }
    }

    private void restorePlayer(Player player, ParkourPlayerState ps, boolean teleport) {
        // Spectator camera kan een tp negeren als hij vastzit aan een ander entity.
        if (player.getGameMode() == GameMode.SPECTATOR) {
            try { player.setSpectatorTarget(null); } catch (Throwable ignored) { }
        }

        for (Player other : Bukkit.getOnlinePlayers()) {
            player.showPlayer(plugin, other);
        }

        // Gamemode eerst zodat een eventuele spectator-state geen invloed heeft op de tp.
        if (ps.getSavedGameMode() != null) {
            player.setGameMode(ps.getSavedGameMode());
        }

        player.getInventory().clear();
        if (ps.getSavedInventory() != null) {
            player.getInventory().setContents(ps.getSavedInventory());
        }
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.SLOW_FALLING);

        if (!teleport || ps.getSavedLocation() == null || ps.getSavedLocation().getWorld() == null) {
            return;
        }

        Location target = ps.getSavedLocation();
        try {
            player.teleport(target);
            player.setFallDistance(0f);
            player.sendActionBar(Messages.info("Terug naar je startlocatie."));
            player.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.0f);
        } catch (Exception ex) {
            plugin.getLogger().warning("Kon speler " + player.getName()
                    + " niet terugteleporteren: " + ex.getMessage());
        }
    }

    private void broadcastMessage(Component component) {
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(component);
        }
    }

    private void broadcastTitle(String title, String subtitle) {
        Component titleC = Component.text(SmallText.of(title), NamedTextColor.GOLD, TextDecoration.BOLD);
        Component subC = Component.text(SmallText.of(subtitle), NamedTextColor.GRAY);
        Title t = Title.title(titleC, subC);
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.showTitle(t);
        }
    }

    private void broadcastActionBar(String text) {
        Component c = Component.text(SmallText.of(text), NamedTextColor.GOLD);
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendActionBar(c);
        }
    }

    private void playSoundAll(Sound sound, float volume, float pitch) {
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }
}
