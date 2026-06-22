package be.panchito.pointRush.minigame.football;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Dunne reflectie-wrapper rond de BlockBall ({@code Shynixn/BlockBall}) developer-API.
 *
 * <p>BlockBall publiceert zijn API niet naar Maven Central en de jar zit niet in deze repo,
 * dus we kunnen er niet compile-time tegen linken. In plaats daarvan praten we — net als
 * {@link be.panchito.pointRush.minigame.boss.MythicMobsBridge} met MythicMobs — runtime via
 * de Bukkit {@code ServicesManager} en reflectie. Zo blijft de PointRush-build los van
 * BlockBall en degradeert alles netjes als de plugin ontbreekt.</p>
 *
 * <p>De échte BlockBall-API (Kotlin):</p>
 * <pre>
 * GameService  com.github.shynixn.blockball.contract.GameService
 *   SoccerGame getByName(String name)
 *   List&lt;SoccerGame&gt; getAll()
 * SoccerGame   com.github.shynixn.blockball.contract.SoccerGame
 *   JoinResult join(Player player, Team team)
 *   LeaveResult leave(Player player)
 *   Set&lt;Player&gt; getPlayers()
 *   int getRedScore() / int getBlueScore()
 *   SoccerArena getArena()  (arena.getName())
 *   void close()
 * Team         com.github.shynixn.blockball.enumeration.Team { RED, BLUE, REFEREE }
 * </pre>
 */
public final class BlockBallBridge {

    public static final String PLUGIN_NAME = "BlockBall";

    private static final String GAME_SERVICE_CLASS = "com.github.shynixn.blockball.contract.GameService";
    private static final String TEAM_ENUM_CLASS = "com.github.shynixn.blockball.enumeration.Team";

    private final JavaPlugin plugin;
    private final Map<String, Method> methodCache = new HashMap<>();

    private boolean missingWarningLogged;

    public BlockBallBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskLater(plugin, this::warnIfStillMissing, 60L);
    }

    /** True wanneer BlockBall geladen is én zijn {@code GameService} beschikbaar is. */
    public boolean isAvailable() {
        return resolveGameService() != null;
    }

    /**
     * Zoekt het BlockBall-spel (arena) op naam.
     *
     * @return ondoorzichtige {@code SoccerGame}-handle, of {@code null} als de arena niet bestaat.
     */
    public Object getGameByName(String arenaName) {
        if (arenaName == null || arenaName.isBlank()) {
            return null;
        }
        Object service = resolveGameService();
        if (service == null) {
            return null;
        }
        try {
            Method getByName = method(service, "getByName", 1);
            return getByName != null ? getByName.invoke(service, arenaName) : null;
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING, "BlockBall: kon arena '" + arenaName + "' niet opzoeken.", ex);
            return null;
        }
    }

    /** Alle bekende arena-namen (voor tab-completion / setup-feedback). */
    public List<String> listArenaNames() {
        Object service = resolveGameService();
        if (service == null) {
            return List.of();
        }
        try {
            Method getAll = method(service, "getAll", 0);
            if (getAll == null) {
                return List.of();
            }
            Object result = getAll.invoke(service);
            if (!(result instanceof Iterable<?> games)) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            for (Object game : games) {
                String name = arenaName(game);
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
            names.sort(String.CASE_INSENSITIVE_ORDER);
            return Collections.unmodifiableList(names);
        } catch (Throwable ex) {
            return List.of();
        }
    }

    /**
     * Laat de speler de arena joinen in het gegeven team ({@code "RED"} of {@code "BLUE"}).
     * BlockBall teleporteert de speler zelf naar de arena.
     *
     * @return {@code true} als de join-call zonder fout uitgevoerd is.
     */
    public boolean join(Object game, Player player, String teamName) {
        if (game == null || player == null) {
            return false;
        }
        try {
            Object teamValue = teamValue(teamName);
            Method join = method(game, "join", 2);
            if (join == null) {
                return false;
            }
            join.invoke(game, player, teamValue);
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING, "BlockBall: kon " + player.getName() + " niet laten joinen.", ex);
            return false;
        }
    }

    /** Laat de speler de arena verlaten (best-effort). */
    public void leave(Object game, Player player) {
        if (game == null || player == null) {
            return;
        }
        try {
            Method leave = method(game, "leave", 1);
            if (leave != null) {
                leave.invoke(game, player);
            }
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.FINE, "BlockBall: leave faalde voor " + player.getName(), ex);
        }
    }

    public int getRedScore(Object game) {
        return readInt(game, "getRedScore");
    }

    public int getBlueScore(Object game) {
        return readInt(game, "getBlueScore");
    }

    /** De spelers die BlockBall momenteel in de arena heeft. Lege set bij fouten. */
    @SuppressWarnings("unchecked")
    public Set<Player> getPlayers(Object game) {
        if (game == null) {
            return Set.of();
        }
        try {
            Method getPlayers = method(game, "getPlayers", 0);
            if (getPlayers == null) {
                return Set.of();
            }
            Object result = getPlayers.invoke(game);
            if (result instanceof Set<?> set) {
                return (Set<Player>) set;
            }
        } catch (Throwable ignored) {
        }
        return Set.of();
    }

    /** True wanneer de speler nog in de BlockBall-arena zit. */
    public boolean isPlayerInGame(Object game, Player player) {
        return player != null && getPlayers(game).contains(player);
    }

    /** Annuleert/reset de BlockBall-arena (best-effort). */
    public void closeGame(Object game) {
        if (game == null) {
            return;
        }
        try {
            Method close = method(game, "close", 0);
            if (close != null) {
                close.invoke(game);
            }
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.FINE, "BlockBall: close faalde.", ex);
        }
    }

    // --- intern -------------------------------------------------------------

    private Object resolveGameService() {
        if (!isPluginEnabled()) {
            return null;
        }
        try {
            Class<?> serviceClass = Class.forName(GAME_SERVICE_CLASS);
            return Bukkit.getServicesManager().load(serviceClass);
        } catch (Throwable ex) {
            return null;
        }
    }

    private boolean isPluginEnabled() {
        Plugin blockBall = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        return blockBall != null && blockBall.isEnabled();
    }

    private Object teamValue(String teamName) throws ReflectiveOperationException {
        Class<?> teamClass = Class.forName(TEAM_ENUM_CLASS);
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object value = Enum.valueOf((Class<Enum>) teamClass.asSubclass(Enum.class),
                teamName == null ? "BLUE" : teamName);
        return value;
    }

    private int readInt(Object game, String getter) {
        if (game == null) {
            return 0;
        }
        try {
            Method m = method(game, getter, 0);
            if (m == null) {
                return 0;
            }
            Object value = m.invoke(game);
            return value instanceof Number number ? number.intValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private String arenaName(Object game) {
        if (game == null) {
            return null;
        }
        try {
            Method getArena = method(game, "getArena", 0);
            if (getArena == null) {
                return null;
            }
            Object arena = getArena.invoke(game);
            if (arena == null) {
                return null;
            }
            Method getName = method(arena, "getName", 0);
            Object name = getName != null ? getName.invoke(arena) : null;
            return name != null ? name.toString() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Vindt en cachet een publieke methode op naam + parametertelling (negeert exacte parametertypes,
     * zodat Kotlin/Java-interface-varianten allebei matchen).
     */
    private Method method(Object target, String name, int paramCount) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        String key = type.getName() + "#" + name + "/" + paramCount;
        Method cached = methodCache.get(key);
        if (cached != null) {
            return cached;
        }
        if (methodCache.containsKey(key)) {
            return null; // negatief gecachet
        }
        Method found = null;
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                found = m;
                break;
            }
        }
        if (found != null) {
            found.setAccessible(true);
        }
        methodCache.put(key, found);
        return found;
    }

    private void warnIfStillMissing() {
        if (!isAvailable() && !missingWarningLogged) {
            missingWarningLogged = true;
            plugin.getLogger().warning("BlockBall niet gevonden — Voetbal-event kan geen arena starten. "
                    + "Installeer BlockBall en maak een arena aan.");
        }
    }
}
