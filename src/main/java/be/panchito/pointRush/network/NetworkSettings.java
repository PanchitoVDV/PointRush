package be.panchito.pointRush.network;

import be.panchito.pointRush.config.UnifiedSettings;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Locale;

/**
 * Netwerk-instellingen onder {@code network} in {@code settings.yml}. Bepaalt of PointRush in een
 * multi-server (Velocity) opstelling draait en welke rol deze server speelt.
 *
 * <p>Standaard staat dit uit ({@code enabled: false}) zodat een losse server zich exact gedraagt als
 * voorheen. Zet {@code enabled: true}, de juiste {@code role} en de servernamen (zoals in
 * {@code velocity.toml}) om events op een aparte events-server te isoleren.
 */
public final class NetworkSettings {

    /** Rol van deze server binnen het netwerk. */
    public enum Role {
        /** Losse server: alles draait lokaal (default, geen cross-server gedrag). */
        STANDALONE,
        /** Hoofdserver waar spelers normaal zitten; stuurt deelnemers naar de events-server. */
        SURVIVAL,
        /** Aparte server die events host. */
        EVENTS;

        static Role parse(String raw) {
            if (raw == null) {
                return STANDALONE;
            }
            try {
                return Role.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return STANDALONE;
            }
        }
    }

    private static final String KEY = "network";

    private final UnifiedSettings unified;

    private boolean enabled;
    private Role role = Role.STANDALONE;
    private String eventsServer = "events";
    private String survivalServer = "survival";
    private String transferCommand = "";
    private int joinGraceSeconds = 6;
    private int returnDelaySeconds = 6;

    public NetworkSettings(UnifiedSettings unified) {
        this.unified = unified;
    }

    public void load() {
        enabled = false;
        role = Role.STANDALONE;
        eventsServer = "events";
        survivalServer = "survival";
        transferCommand = "";
        joinGraceSeconds = 6;
        returnDelaySeconds = 6;

        YamlConfiguration cfg = unified.yaml();
        if (cfg.getConfigurationSection(KEY) == null) {
            return;
        }
        enabled = cfg.getBoolean(KEY + ".enabled", false);
        role = Role.parse(cfg.getString(KEY + ".role", "standalone"));
        eventsServer = cfg.getString(KEY + ".events-server", "events");
        survivalServer = cfg.getString(KEY + ".survival-server", "survival");
        transferCommand = cfg.getString(KEY + ".transfer-command", "");
        joinGraceSeconds = Math.max(0, cfg.getInt(KEY + ".join-grace-seconds", 6));
        returnDelaySeconds = Math.max(0, cfg.getInt(KEY + ".return-delay-seconds", 6));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Role getRole() {
        return role;
    }

    public String getEventsServer() {
        return eventsServer;
    }

    public String getSurvivalServer() {
        return survivalServer;
    }

    /** Eigen transfer-commando ({@code {server}} = doelserver), of leeg voor BungeeCord/Velocity Connect. */
    public String getTransferCommand() {
        return transferCommand;
    }

    public int getJoinGraceSeconds() {
        return joinGraceSeconds;
    }

    public int getReturnDelaySeconds() {
        return returnDelaySeconds;
    }

    /** True wanneer cross-server routing actief is én deze server de survival-rol heeft. */
    public boolean isSurvivalHost() {
        return enabled && role == Role.SURVIVAL;
    }

    /** True wanneer cross-server routing actief is én deze server de events-rol heeft. */
    public boolean isEventsHost() {
        return enabled && role == Role.EVENTS;
    }
}
