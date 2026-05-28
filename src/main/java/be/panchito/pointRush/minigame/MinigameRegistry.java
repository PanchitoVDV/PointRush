package be.panchito.pointRush.minigame;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.minigame.bingo.BingoGame;
import be.panchito.pointRush.minigame.boatrace.BoatRaceGame;
import be.panchito.pointRush.minigame.ctf.CtfGame;
import be.panchito.pointRush.minigame.floorislava.FloorIsLavaGame;
import be.panchito.pointRush.minigame.goldrush.GoldRushGame;
import be.panchito.pointRush.minigame.hiddentarget.HiddenTargetGame;
import be.panchito.pointRush.minigame.koth.KothGame;
import be.panchito.pointRush.minigame.parkour.ParkourGame;
import be.panchito.pointRush.minigame.race.RaceGame;
import be.panchito.pointRush.minigame.tntrun.TntRunGame;
import be.panchito.pointRush.minigame.tnttag.TntTagGame;
import be.panchito.pointRush.minigame.treasurehunt.TreasureHuntGame;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Single source of truth for all PointRush minigame ids, display names and
 * cross-cutting checks (active state, lobby scoreboard exclusion, random wheel).
 */
public final class MinigameRegistry {

    public record EventDef(String id, String displayName) {}

    private static final Map<String, String> EVENTS = new LinkedHashMap<>();

    static {
        EVENTS.put("parkour", "Parkour");
        EVENTS.put("tnttag", "TNT Tag");
        EVENTS.put("tntrun", "TNT Run");
        EVENTS.put("race", "Race");
        EVENTS.put("boatrace", "Bootrace");
        EVENTS.put("bingo", "Bingo");
        EVENTS.put("koth", "King of the Hill");
        EVENTS.put("floorislava", "Floor is Lava");
        EVENTS.put("treasurehunt", "Treasure Hunt");
        EVENTS.put("goldrush", "Gold Rush");
        EVENTS.put("hiddentarget", "Hidden Target");
        EVENTS.put("ctf", "Capture the Flag");
    }

    private MinigameRegistry() {
    }

    /** Immutable map of event id → display label (insertion order preserved). */
    public static Map<String, String> events() {
        return Collections.unmodifiableMap(EVENTS);
    }

    public static String displayName(String eventId) {
        return EVENTS.getOrDefault(eventId, eventId);
    }

    /**
     * True when at least one minigame is not {@code IDLE} (starting, running or intermission).
     */
    public static boolean anyActive(PointRush plugin) {
        return isActive(plugin.getParkourGame(), ParkourGame.State.IDLE)
                || isActive(plugin.getTntTagGame(), TntTagGame.State.IDLE)
                || isActive(plugin.getTntRunGame(), TntRunGame.State.IDLE)
                || isActive(plugin.getRaceGame(), RaceGame.State.IDLE)
                || isActive(plugin.getBoatRaceGame(), BoatRaceGame.State.IDLE)
                || isActive(plugin.getBingoGame(), BingoGame.State.IDLE)
                || isActive(plugin.getKothGame(), KothGame.State.IDLE)
                || isActive(plugin.getFloorIsLavaGame(), FloorIsLavaGame.State.IDLE)
                || isActive(plugin.getTreasureHuntGame(), TreasureHuntGame.State.IDLE)
                || isActive(plugin.getGoldRushGame(), GoldRushGame.State.IDLE)
                || isActive(plugin.getHiddenTargetGame(), HiddenTargetGame.State.IDLE)
                || isActive(plugin.getCtfGame(), CtfGame.State.IDLE);
    }

    /**
     * True when the player is inside an arena minigame that owns its own scoreboard.
     */
    public static boolean isPlayerInActiveEvent(PointRush plugin, UUID playerId) {
        return participant(plugin.getParkourGame(), playerId)
                || participant(plugin.getTntTagGame(), playerId)
                || participant(plugin.getTntRunGame(), playerId)
                || participant(plugin.getRaceGame(), playerId)
                || participant(plugin.getBoatRaceGame(), playerId)
                || participant(plugin.getBingoGame(), playerId)
                || participant(plugin.getKothGame(), playerId)
                || participant(plugin.getFloorIsLavaGame(), playerId)
                || participant(plugin.getHiddenTargetGame(), playerId)
                || participant(plugin.getCtfGame(), playerId);
    }

    /**
     * Minigames eligible for the random event wheel: arena ready and idle.
     */
    public static java.util.List<RandomCandidate> randomCandidates(PointRush plugin) {
        java.util.List<RandomCandidate> list = new java.util.ArrayList<>();
        list.add(candidate("parkour", plugin.getParkourConfig().isReady(),
                () -> plugin.getParkourGame().getState() == ParkourGame.State.IDLE,
                () -> plugin.getParkourGame().start()));
        list.add(candidate("tnttag", plugin.getTntTagConfig().isReady(),
                () -> plugin.getTntTagGame().getState() == TntTagGame.State.IDLE,
                () -> plugin.getTntTagGame().start()));
        list.add(candidate("tntrun", plugin.getTntRunConfig().isReady(),
                () -> plugin.getTntRunGame().getState() == TntRunGame.State.IDLE,
                () -> plugin.getTntRunGame().start()));
        list.add(candidate("race", plugin.getRaceConfig().isReady(),
                () -> plugin.getRaceGame().getState() == RaceGame.State.IDLE,
                () -> plugin.getRaceGame().start()));
        list.add(candidate("boatrace", plugin.getBoatRaceConfig().isReady(),
                () -> plugin.getBoatRaceGame().getState() == BoatRaceGame.State.IDLE,
                () -> plugin.getBoatRaceGame().start()));
        list.add(candidate("bingo", plugin.getBingoConfig().isReady(),
                () -> plugin.getBingoGame().getState() == BingoGame.State.IDLE,
                () -> plugin.getBingoGame().start()));
        list.add(candidate("koth", plugin.getKothConfig().isReady(),
                () -> plugin.getKothGame().getState() == KothGame.State.IDLE,
                () -> plugin.getKothGame().start()));
        list.add(candidate("floorislava", plugin.getFloorIsLavaConfig().isReady(),
                () -> plugin.getFloorIsLavaGame().getState() == FloorIsLavaGame.State.IDLE,
                () -> plugin.getFloorIsLavaGame().start()));
        list.add(candidate("treasurehunt", plugin.getTreasureHuntConfig().isReady(),
                () -> plugin.getTreasureHuntGame().getState() == TreasureHuntGame.State.IDLE,
                () -> plugin.getTreasureHuntGame().start()));
        list.add(candidate("goldrush", plugin.getGoldRushConfig().isReady(),
                () -> plugin.getGoldRushGame().getState() == GoldRushGame.State.IDLE,
                () -> plugin.getGoldRushGame().start()));
        list.add(candidate("hiddentarget", plugin.getHiddenTargetConfig().isReady(),
                () -> plugin.getHiddenTargetGame().getState() == HiddenTargetGame.State.IDLE,
                () -> plugin.getHiddenTargetGame().start()));
        list.add(candidate("ctf", plugin.getCtfConfig().isReady(),
                () -> plugin.getCtfGame().getState() == CtfGame.State.IDLE,
                () -> plugin.getCtfGame().start()));
        return list;
    }

    public record RandomCandidate(
            String id,
            String displayName,
            BooleanSupplier readyCheck,
            BooleanSupplier idleCheck,
            BooleanSupplier startAction
    ) {
        public boolean ready() {
            return readyCheck.getAsBoolean() && idleCheck.getAsBoolean();
        }

        public boolean start() {
            return startAction.getAsBoolean();
        }
    }

    private static RandomCandidate candidate(
            String id,
            boolean configReady,
            BooleanSupplier idleCheck,
            BooleanSupplier startAction
    ) {
        return new RandomCandidate(
                id,
                displayName(id),
                () -> configReady,
                idleCheck,
                startAction
        );
    }

    private static <S extends Enum<S>> boolean isActive(Object game, S idleState) {
        if (game == null) return false;
        @SuppressWarnings("unchecked")
        S state = (S) switch (game) {
            case ParkourGame g -> g.getState();
            case TntTagGame g -> g.getState();
            case TntRunGame g -> g.getState();
            case RaceGame g -> g.getState();
            case BoatRaceGame g -> g.getState();
            case BingoGame g -> g.getState();
            case KothGame g -> g.getState();
            case FloorIsLavaGame g -> g.getState();
            case TreasureHuntGame g -> g.getState();
            case GoldRushGame g -> g.getState();
            case HiddenTargetGame g -> g.getState();
            case CtfGame g -> g.getState();
            default -> null;
        };
        return state != idleState;
    }

    private static boolean participant(Object game, UUID playerId) {
        if (game == null) return false;
        return switch (game) {
            case ParkourGame g -> g.isParticipant(playerId);
            case TntTagGame g -> g.isParticipant(playerId);
            case TntRunGame g -> g.isParticipant(playerId);
            case RaceGame g -> g.isParticipant(playerId);
            case BoatRaceGame g -> g.isParticipant(playerId);
            case BingoGame g -> g.isParticipant(playerId);
            case KothGame g -> g.isParticipant(playerId);
            case FloorIsLavaGame g -> g.isParticipant(playerId);
            case HiddenTargetGame g -> g.isParticipant(playerId);
            case CtfGame g -> g.isParticipant(playerId);
            default -> false;
        };
    }
}
