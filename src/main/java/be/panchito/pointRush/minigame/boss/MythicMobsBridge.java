package be.panchito.pointRush.minigame.boss;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Dunne wrapper rond de MythicMobs API voor boss-spawns en cleanup.
 * Beschikbaarheid wordt runtime gecontroleerd — PointRush laadt op STARTUP,
 * MythicMobs meestal pas daarna.
 */
public final class MythicMobsBridge implements Listener {

    private static final String[] PLUGIN_NAMES = { "MythicMobs", "MythicMobsPremium" };

    private final JavaPlugin plugin;
    private boolean missingWarningLogged;

    public MythicMobsBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::warnIfStillMissing, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        if (isMythicPluginName(event.getPlugin().getName())) {
            missingWarningLogged = false;
        }
    }

    public boolean isAvailable() {
        return resolveMythic().isPresent();
    }

    public boolean mobExists(String mobId) {
        if (mobId == null || mobId.isBlank()) {
            return false;
        }
        return resolveMythic()
                .map(mythic -> {
                    try {
                        return mythic.getMobManager().getMythicMob(mobId).isPresent();
                    } catch (Throwable ex) {
                        plugin.getLogger().log(Level.WARNING, "Kon MythicMob '" + mobId + "' niet opzoeken.", ex);
                        return false;
                    }
                })
                .orElse(false);
    }

    /** Alle geladen MythicMob internal names (voor tab-completion). */
    public List<String> listMobIds() {
        return resolveMythic()
                .map(mythic -> {
                    try {
                        List<String> names = new ArrayList<>(mythic.getMobManager().getMobNames());
                        names.sort(String.CASE_INSENSITIVE_ORDER);
                        return Collections.unmodifiableList(names);
                    } catch (Throwable ex) {
                        plugin.getLogger().log(Level.WARNING, "Kon MythicMob-namen niet ophalen.", ex);
                        return List.<String>of();
                    }
                })
                .orElse(List.of());
    }

    public Optional<UUID> spawnBoss(String mobId, Location location) {
        if (mobId == null || mobId.isBlank() || location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        Optional<MythicBukkit> mythicOpt = resolveMythic();
        if (mythicOpt.isEmpty()) {
            return Optional.empty();
        }
        try {
            ActiveMob active = mythicOpt.get().getMobManager().spawnMob(mobId, location);
            if (active == null || active.getEntity() == null) {
                return Optional.empty();
            }
            Entity entity = active.getEntity().getBukkitEntity();
            return entity != null ? Optional.of(entity.getUniqueId()) : Optional.empty();
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.SEVERE, "Kon MythicMob '" + mobId + "' niet spawnen.", ex);
            return Optional.empty();
        }
    }

    public void removeBoss(UUID entityId) {
        if (entityId == null) {
            return;
        }
        resolveMythic().ifPresent(mythic -> {
            try {
                mythic.getMobManager().getActiveMob(entityId).ifPresent(active -> {
                    Entity entity = active.getEntity().getBukkitEntity();
                    if (entity != null && !entity.isDead()) {
                        entity.remove();
                    }
                });
            } catch (Throwable ex) {
                plugin.getLogger().log(Level.WARNING, "Kon MythicMob " + entityId + " niet verwijderen.", ex);
            }
        });
    }

    public boolean isTrackedBoss(UUID entityId) {
        if (entityId == null) {
            return false;
        }
        return resolveMythic()
                .map(mythic -> {
                    try {
                        return mythic.getMobManager().getActiveMob(entityId).isPresent();
                    } catch (Throwable ignored) {
                        return false;
                    }
                })
                .orElse(false);
    }

    /**
     * Boss-HP als fractie tussen 0 en 1, of {@code -1} als de entity onbekend/dood is.
     * Gebruikt voor de live boss-health-bar.
     */
    public double getBossHealthFraction(UUID entityId) {
        if (entityId == null) {
            return -1;
        }
        Entity entity = Bukkit.getEntity(entityId);
        if (!(entity instanceof LivingEntity living) || living.isDead() || !living.isValid()) {
            return -1;
        }
        var attr = living.getAttribute(Attribute.MAX_HEALTH);
        double max = attr != null ? attr.getValue() : living.getHealth();
        if (max <= 0) {
            return -1;
        }
        return Math.max(0.0, Math.min(1.0, living.getHealth() / max));
    }

    /** True when the spawned boss entity is gone or no longer alive (Mythic cleanup). */
    public boolean isBossGone(UUID entityId) {
        if (entityId == null) {
            return false;
        }
        Entity entity = Bukkit.getEntity(entityId);
        if (entity != null) {
            return !entity.isValid() || entity.isDead();
        }
        return !isTrackedBoss(entityId);
    }

    private Optional<MythicBukkit> resolveMythic() {
        if (!isMythicPluginLoaded()) {
            return Optional.empty();
        }
        try {
            MythicBukkit mythic = MythicBukkit.inst();
            if (mythic == null || mythic.getMobManager() == null) {
                return Optional.empty();
            }
            return Optional.of(mythic);
        } catch (Throwable ex) {
            return Optional.empty();
        }
    }

    private boolean isMythicPluginLoaded() {
        for (String name : PLUGIN_NAMES) {
            Plugin mythic = Bukkit.getPluginManager().getPlugin(name);
            if (mythic != null && mythic.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMythicPluginName(String name) {
        for (String candidate : PLUGIN_NAMES) {
            if (candidate.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private void warnIfStillMissing() {
        if (!isAvailable() && !missingWarningLogged) {
            missingWarningLogged = true;
            plugin.getLogger().warning("MythicMobs niet gevonden — Boss Event kan geen bosses spawnen.");
        }
    }
}
