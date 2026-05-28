package be.panchito.pointRush.minigame.floorislava;

import be.panchito.pointRush.PointRush;
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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Floor is Lava: bouw torens met random items (elke 30s), knock elkaar eraf,
 * lava stijgt elke minuut 1 blok — laatste speler/team wint.
 */
public final class FloorIsLavaGame {

    public enum State { IDLE, STARTING, RUNNING }

    public static final int[] PLACEMENT_POINTS = { 100, 80, 60, 50, 40, 30, 25, 20, 15, 10 };

    public static final int COUNTDOWN_SECONDS = 10;
    public static final long EVENT_TIMEOUT_TICKS = 45L * 60L * 20L;

    private final PointRush plugin;
    private final FloorIsLavaConfig config;
    private final TeamManager teamManager;
    private final DataManager dataManager;
    private final EventHistoryManager historyManager;
    private final FloorIsLavaScoreboard scoreboard;

    private State state = State.IDLE;
    private final Map<UUID, FloorIsLavaPlayerState> players = new HashMap<>();
    /** Lava-lagen geplaatst door de plugin (restore via block data). */
    private final Map<Block, BlockData> lavaBlocks = new LinkedHashMap<>();
    /** Door spelers geplaatste blokken (restore = air). */
    private final Set<Block> playerPlacedBlocks = new HashSet<>();

    private long eventStartedAtMs = 0L;
    private int nextEliminationPlacement = 0;
    private int currentLavaY = Integer.MIN_VALUE;
    private long nextLavaRiseMs = 0L;
    private long nextItemDropMs = 0L;
    private boolean historyRecorded = false;

    private BukkitTask countdownTask;
    private BukkitTask lavaRiseTask;
    private BukkitTask itemDropTask;
    private BukkitTask lavaCheckTask;
    private BukkitTask timeoutTask;

    public FloorIsLavaGame(PointRush plugin, FloorIsLavaConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.teamManager = plugin.getTeamManager();
        this.dataManager = plugin.getDataManager();
        this.historyManager = plugin.getEventHistoryManager();
        this.scoreboard = new FloorIsLavaScoreboard(plugin, this);
    }

    public State getState() {
        return state;
    }

    public PointRush getPlugin() {
        return plugin;
    }

    public FloorIsLavaConfig getConfig() {
        return config;
    }

    public boolean isParticipant(UUID id) {
        return players.containsKey(id);
    }

    public FloorIsLavaPlayerState getPlayerState(UUID id) {
        return players.get(id);
    }

    public Collection<FloorIsLavaPlayerState> getAllPlayerStates() {
        return Collections.unmodifiableCollection(players.values());
    }

    public int aliveCount() {
        int n = 0;
        for (FloorIsLavaPlayerState ps : players.values()) {
            if (ps.isAlive()) n++;
        }
        return n;
    }

    public int getCurrentLavaY() {
        return currentLavaY;
    }

    public long getNextLavaRiseMs() {
        return nextLavaRiseMs;
    }

    public long getNextItemDropMs() {
        return nextItemDropMs;
    }

    public boolean canBuildAt(Location loc) {
        return config.canBuildAt(loc);
    }

    public void trackPlacedBlock(Block block) {
        playerPlacedBlocks.add(block);
    }

    public boolean start() {
        if (state != State.IDLE) return false;
        if (!config.isReady()) return false;

        state = State.STARTING;
        eventStartedAtMs = System.currentTimeMillis();
        nextEliminationPlacement = 0;
        historyRecorded = false;
        currentLavaY = Integer.MIN_VALUE;
        nextLavaRiseMs = 0L;
        nextItemDropMs = 0L;
        lavaBlocks.clear();
        playerPlacedBlocks.clear();

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getGameMode() == GameMode.CREATIVE || online.getGameMode() == GameMode.SPECTATOR) {
                online.sendMessage(Messages.warn("Je doet niet mee aan Floor is Lava (creative/spectator)."));
                continue;
            }
            joinPlayer(online);
        }

        if (players.size() < 2) {
            Bukkit.broadcast(Messages.error("Floor is Lava heeft minstens 2 spelers nodig."));
            cleanupAfterStop();
            return false;
        }

        nextEliminationPlacement = players.size();
        scoreboard.start();
        broadcastTitle(
                Component.text(SmallText.of("FLOOR IS LAVA"), NamedTextColor.RED, TextDecoration.BOLD),
                Component.text(SmallText.of("bouw omhoog · knock elkaar eraf"), NamedTextColor.GRAY)
        );
        playSoundAll(Sound.BLOCK_LAVA_POP, 0.6f, 1.0f);
        startCountdown();
        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, this::stop, EVENT_TIMEOUT_TICKS);
        return true;
    }

    public boolean stop() {
        if (state == State.IDLE) return false;
        recordHistoryEntry();
        cleanupAfterStop();
        Bukkit.broadcast(Messages.info("Floor is Lava event afgelopen."));
        return true;
    }

    private void cleanupAfterStop() {
        state = State.IDLE;
        cancelTask(countdownTask);
        countdownTask = null;
        cancelTask(lavaRiseTask);
        lavaRiseTask = null;
        cancelTask(itemDropTask);
        itemDropTask = null;
        cancelTask(lavaCheckTask);
        lavaCheckTask = null;
        cancelTask(timeoutTask);
        timeoutTask = null;
        scoreboard.stop();
        restoreArena();

        Title endTitle = Title.title(
                Component.text(SmallText.of("event afgelopen"), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(SmallText.of("teleport terug naar je startlocatie"), NamedTextColor.GRAY)
        );
        for (Map.Entry<UUID, FloorIsLavaPlayerState> entry : new ArrayList<>(players.entrySet())) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                p.showTitle(endTitle);
                restorePlayer(p, entry.getValue(), true);
            }
        }
        players.clear();
        nextEliminationPlacement = 0;
        currentLavaY = Integer.MIN_VALUE;
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
        FloorIsLavaPlayerState ps = new FloorIsLavaPlayerState(
                player.getUniqueId(),
                player.getLocation().clone(),
                player.getGameMode(),
                saved
        );
        players.put(player.getUniqueId(), ps);

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
        scoreboard.attach(player);
        player.sendMessage(Messages.info("Floor is Lava start binnenkort — elke 30s krijg je een random item!"));
    }

    private void startCountdown() {
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int remaining = COUNTDOWN_SECONDS;

            @Override
            public void run() {
                if (state != State.STARTING) {
                    cancelTask(countdownTask);
                    countdownTask = null;
                    return;
                }
                if (remaining > 0) {
                    broadcastActionBar("start in " + remaining + "s");
                    if (remaining <= 5) {
                        playSoundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                    }
                    remaining--;
                } else {
                    beginRun();
                    cancelTask(countdownTask);
                    countdownTask = null;
                }
            }
        }, 0L, 20L);
    }

    private void beginRun() {
        state = State.RUNNING;
        if (config.getRegionMin() != null) {
            currentLavaY = config.getRegionMin().getBlockY() - 1;
        }
        long now = System.currentTimeMillis();
        nextLavaRiseMs = now + config.getLavaRiseIntervalTicks() * 50L;

        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.setGameMode(GameMode.SURVIVAL);
            p.showTitle(Title.title(
                    MinigameText.goTitle(),
                    Component.text(SmallText.of("bouw · knock · overleef"), NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(900), Duration.ofMillis(200))
            ));
        }
        playSoundAll(Sound.ENTITY_GHAST_SCREAM, 0.4f, 1.2f);

        dropRandomItemsToAll();
        nextItemDropMs = System.currentTimeMillis() + config.getItemDropIntervalTicks() * 50L;
        startItemDropTask();
        startLavaRiseTask();
        startLavaCheckTask();
    }

    private void startItemDropTask() {
        if (itemDropTask != null) return;
        itemDropTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != State.RUNNING) return;
            long now = System.currentTimeMillis();
            if (now < nextItemDropMs) return;
            dropRandomItemsToAll();
            nextItemDropMs = now + config.getItemDropIntervalTicks() * 50L;
        }, 20L, 20L);
    }

    private void startLavaRiseTask() {
        if (lavaRiseTask != null) return;
        lavaRiseTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != State.RUNNING) return;
            long now = System.currentTimeMillis();
            if (now < nextLavaRiseMs) return;
            riseLavaLayer();
            nextLavaRiseMs = now + config.getLavaRiseIntervalTicks() * 50L;
        }, 20L, 20L);
    }

    private void startLavaCheckTask() {
        if (lavaCheckTask != null) return;
        lavaCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != State.RUNNING) return;
            for (FloorIsLavaPlayerState ps : players.values()) {
                if (!ps.isAlive()) continue;
                Player p = Bukkit.getPlayer(ps.getUuid());
                if (p == null) continue;
                if (touchesLava(p.getLocation())) {
                    eliminate(p);
                }
            }
        }, 10L, 10L);
    }

    private void dropRandomItemsToAll() {
        for (FloorIsLavaPlayerState ps : players.values()) {
            if (!ps.isAlive()) continue;
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p == null) continue;

            FloorIsLavaKit.Roll roll = FloorIsLavaKit.randomRoll();
            giveItem(p, roll.item());

            NamedTextColor color = roll.type() == FloorIsLavaKit.Type.BUILD
                    ? NamedTextColor.GREEN : NamedTextColor.RED;
            p.sendActionBar(Component.text()
                    .append(Component.text(SmallText.of("kit: "), NamedTextColor.GRAY))
                    .append(Component.text(SmallText.of(roll.label()), color, TextDecoration.BOLD))
                    .build());
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        }
        playSoundAll(Sound.BLOCK_CHEST_OPEN, 0.5f, 1.4f);
    }

    private void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
        if (!leftover.isEmpty() && config.getSpawn() != null) {
            for (ItemStack stack : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            }
        }
    }

    private void riseLavaLayer() {
        Location min = config.getRegionMin();
        Location max = config.getRegionMax();
        if (min == null || max == null || min.getWorld() == null) return;

        currentLavaY++;
        World world = min.getWorld();
        int minX = min.getBlockX();
        int maxX = max.getBlockX();
        int minZ = min.getBlockZ();
        int maxZ = max.getBlockZ();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block block = world.getBlockAt(x, currentLavaY, z);
                if (block.getType() == Material.LAVA) continue;
                recordLavaBlock(block);
                block.setType(Material.LAVA, false);
                playerPlacedBlocks.remove(block);
            }
        }

        Location center = new Location(world,
                (minX + maxX) / 2.0 + 0.5,
                currentLavaY + 0.5,
                (minZ + maxZ) / 2.0 + 0.5);
        world.spawnParticle(Particle.LAVA, center, 40, (maxX - minX) / 2.0, 0.3, (maxZ - minZ) / 2.0, 0.02);
        world.playSound(center, Sound.BLOCK_LAVA_POP, 1.0f, 0.7f);
        broadcastActionBar("lava stijgt · y=" + currentLavaY);

        for (FloorIsLavaPlayerState ps : players.values()) {
            if (!ps.isAlive()) continue;
            Player p = Bukkit.getPlayer(ps.getUuid());
            if (p != null && touchesLava(p.getLocation())) {
                eliminate(p);
            }
        }
    }

    public void checkLava(Player player) {
        if (state != State.RUNNING) return;
        FloorIsLavaPlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isAlive()) return;
        if (touchesLava(player.getLocation())) {
            eliminate(player);
        }
    }

    private boolean touchesLava(Location loc) {
        if (loc.getWorld() == null) return false;
        if (loc.getY() < config.getDeathY()) return true;

        Material feet = loc.getBlock().getType();
        Material below = loc.clone().subtract(0, 1, 0).getBlock().getType();
        if (feet == Material.LAVA || below == Material.LAVA) return true;

        if (currentLavaY > Integer.MIN_VALUE && loc.getBlockY() <= currentLavaY) {
            Block atLevel = loc.getWorld().getBlockAt(loc.getBlockX(), currentLavaY, loc.getBlockZ());
            return atLevel.getType() == Material.LAVA;
        }
        return false;
    }

    private void recordLavaBlock(Block block) {
        if (!lavaBlocks.containsKey(block)) {
            lavaBlocks.put(block, block.getBlockData().clone());
        }
    }

    public void eliminate(Player player) {
        FloorIsLavaPlayerState ps = players.get(player.getUniqueId());
        if (ps == null || !ps.isAlive()) return;

        ps.setAlive(false);
        ps.setEliminatedAtMs(System.currentTimeMillis());
        ps.setPlacement(nextEliminationPlacement);
        if (nextEliminationPlacement > 0) nextEliminationPlacement--;

        player.getInventory().clear();
        int pts = pointsForPlacement(ps.getPlacement());
        Team team = teamManager.getTeamOfPlayer(player.getUniqueId());
        if (team != null && pts > 0) {
            team.addPoints(pts);
            dataManager.save();
        }

        player.setGameMode(GameMode.SPECTATOR);
        player.setFireTicks(0);
        player.showTitle(Title.title(
                Component.text(SmallText.of("LAVA!"), NamedTextColor.RED, TextDecoration.BOLD),
                Component.text(SmallText.of("#" + ps.getPlacement() + " · +" + pts + " pts"),
                        NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1500), Duration.ofMillis(300))
        ));
        if (config.getSpawn() != null) {
            try {
                player.teleport(config.getSpawn());
            } catch (Exception ignored) {
            }
        }

        Component msg;
        if (team != null) {
            msg = Component.text()
                    .append(Component.text(player.getName(), NamedTextColor.WHITE))
                    .append(Component.text(SmallText.of(" viel in lava als #" + ps.getPlacement()
                            + " (+" + pts + " pts) — team "), NamedTextColor.GRAY))
                    .append(Component.text(SmallText.of(team.getName()), team.getColor()))
                    .build();
        } else {
            msg = Component.text()
                    .append(Component.text(player.getName(), NamedTextColor.WHITE))
                    .append(Component.text(SmallText.of(" viel in lava als #" + ps.getPlacement()
                            + " (geen team, geen punten)"), NamedTextColor.GRAY))
                    .build();
        }
        Bukkit.broadcast(Messages.PREFIX.append(msg));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 0.5f);

        checkWinCondition();
    }

    private int pointsForPlacement(int placement) {
        if (placement < 1 || placement > PLACEMENT_POINTS.length) return 0;
        return PLACEMENT_POINTS[placement - 1];
    }

    private void checkWinCondition() {
        List<UUID> alive = new ArrayList<>();
        Set<UUID> aliveTeamIds = new HashSet<>();
        int aliveSolo = 0;
        for (FloorIsLavaPlayerState ps : players.values()) {
            if (!ps.isAlive()) continue;
            alive.add(ps.getUuid());
            Team t = teamManager.getTeamOfPlayer(ps.getUuid());
            if (t != null) aliveTeamIds.add(t.getId());
            else aliveSolo++;
        }

        boolean teamWin = aliveTeamIds.size() == 1 && aliveSolo == 0;
        boolean soloWin = alive.size() == 1;
        boolean noOne = alive.isEmpty();
        if (!noOne && !teamWin && !soloWin) {
            return;
        }

        for (UUID id : alive) {
            FloorIsLavaPlayerState ps = players.get(id);
            if (ps == null) continue;
            ps.setPlacement(1);
            Team team = teamManager.getTeamOfPlayer(id);
            if (team != null) {
                team.addPoints(pointsForPlacement(1));
            }
        }
        if (!alive.isEmpty()) {
            dataManager.save();
        }

        announceWinner(alive);
        recordHistoryEntry();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (state != State.IDLE) {
                cleanupAfterStop();
                Bukkit.broadcast(Messages.info("Floor is Lava event afgelopen."));
            }
        }, 60L);
        state = State.STARTING;
    }

    private void announceWinner(List<UUID> alive) {
        Component winnerLine;
        if (alive.isEmpty()) {
            winnerLine = Component.text(SmallText.of("iedereen viel in lava — gelijkspel"),
                    NamedTextColor.GRAY);
        } else if (alive.size() == 1) {
            UUID solo = alive.get(0);
            Player p = Bukkit.getPlayer(solo);
            String name = p != null ? p.getName() : solo.toString().substring(0, 8);
            Team t = teamManager.getTeamOfPlayer(solo);
            var b = Component.text()
                    .append(Component.text(name, t != null ? t.getColor() : NamedTextColor.GOLD,
                            TextDecoration.BOLD));
            if (t != null) {
                b.append(Component.text(SmallText.of("  (team "), NamedTextColor.GRAY))
                        .append(Component.text(SmallText.of(t.getName()), t.getColor()))
                        .append(Component.text(")", NamedTextColor.GRAY));
            }
            b.append(Component.text(SmallText.of(" wint!"), NamedTextColor.GRAY));
            winnerLine = b.build();
        } else {
            Team t = teamManager.getTeamOfPlayer(alive.get(0));
            winnerLine = Component.text()
                    .append(Component.text(SmallText.of("team "), NamedTextColor.GRAY))
                    .append(Component.text(SmallText.of(t != null ? t.getName() : "?"),
                            t != null ? t.getColor() : NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(SmallText.of(" wint! (" + alive.size()
                            + " overlevenden)"), NamedTextColor.GRAY))
                    .build();
        }

        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.showTitle(Title.title(
                    Component.text(SmallText.of("EVENT VOORBIJ"), NamedTextColor.GOLD, TextDecoration.BOLD),
                    winnerLine
            ));
        }
        Bukkit.broadcast(Messages.PREFIX.append(winnerLine));
        playSoundAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    private void recordHistoryEntry() {
        if (historyManager == null || historyRecorded || players.isEmpty()) return;
        historyRecorded = true;

        List<FloorIsLavaPlayerState> sorted = new ArrayList<>(players.values());
        sorted.sort((a, b) -> {
            int ap = a.getPlacement();
            int bp = b.getPlacement();
            if (ap == 0 && bp == 0) return 0;
            if (ap == 0) return 1;
            if (bp == 0) return -1;
            return Integer.compare(ap, bp);
        });

        List<EventHistoryEntry.Placement> placements = new ArrayList<>();
        for (FloorIsLavaPlayerState ps : sorted) {
            if (ps.getPlacement() <= 0) continue;
            Player p = Bukkit.getPlayer(ps.getUuid());
            String name = p != null ? p.getName() : ps.getUuid().toString().substring(0, 8);
            Team team = teamManager.getTeamOfPlayer(ps.getUuid());
            placements.add(new EventHistoryEntry.Placement(
                    ps.getPlacement(),
                    ps.getUuid(),
                    name,
                    team != null ? team.getId() : null,
                    team != null ? team.getName() : null,
                    team != null ? team.getColor().toString() : null,
                    pointsForPlacement(ps.getPlacement()),
                    ps.isAlive() ? "overleefd" : "lava"
            ));
        }
        if (placements.isEmpty()) return;

        long started = eventStartedAtMs > 0 ? eventStartedAtMs : System.currentTimeMillis();
        historyManager.record(new EventHistoryEntry(
                UUID.randomUUID().toString(),
                "floorislava",
                started,
                System.currentTimeMillis(),
                placements
        ));
    }

    private void restoreArena() {
        for (Block block : new ArrayList<>(playerPlacedBlocks)) {
            try {
                block.setType(Material.AIR, false);
            } catch (Exception ex) {
                plugin.getLogger().warning("Kon speler-blok niet verwijderen: " + ex.getMessage());
            }
        }
        playerPlacedBlocks.clear();

        for (Map.Entry<Block, BlockData> e : lavaBlocks.entrySet()) {
            try {
                e.getKey().setBlockData(e.getValue(), false);
            } catch (Exception ex) {
                plugin.getLogger().warning("Kon lava-blok niet restoren: " + ex.getMessage());
            }
        }
        lavaBlocks.clear();
    }

    public void removeParticipant(Player player, boolean teleport) {
        FloorIsLavaPlayerState ps = players.remove(player.getUniqueId());
        if (ps == null) return;
        scoreboard.detach(player);
        restorePlayer(player, ps, teleport);

        if (state == State.RUNNING) {
            checkWinCondition();
        }
        if (players.isEmpty() && state != State.IDLE) {
            stop();
        }
    }

    private void restorePlayer(Player player, FloorIsLavaPlayerState ps, boolean teleport) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            try {
                player.setSpectatorTarget(null);
            } catch (Throwable ignored) {
            }
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

    private void broadcastTitle(Component title, Component subtitle) {
        Title t = Title.title(title, subtitle);
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.showTitle(t);
        }
    }

    private void broadcastActionBar(String text) {
        Component c = Component.text(SmallText.of(text), NamedTextColor.RED);
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
