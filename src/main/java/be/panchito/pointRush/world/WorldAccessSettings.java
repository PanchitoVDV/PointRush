package be.panchito.pointRush.world;

import be.panchito.pointRush.config.UnifiedSettings;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Toggleable access to the Nether and the End, persisted in {@code settings.yml}
 * under the {@code dimensions} section. Defaults to enabled so existing servers
 * keep their normal behaviour until an admin flips a switch.
 */
public final class WorldAccessSettings {

    private static final String KEY_NETHER = "dimensions.nether-enabled";
    private static final String KEY_END = "dimensions.end-enabled";

    private final UnifiedSettings settings;
    private final Logger logger;

    private boolean netherEnabled = true;
    private boolean endEnabled = true;

    public WorldAccessSettings(UnifiedSettings settings, Logger logger) {
        this.settings = settings;
        this.logger = logger;
    }

    public void load() {
        this.netherEnabled = settings.yaml().getBoolean(KEY_NETHER, true);
        this.endEnabled = settings.yaml().getBoolean(KEY_END, true);
    }

    public boolean isNetherEnabled() {
        return netherEnabled;
    }

    public boolean isEndEnabled() {
        return endEnabled;
    }

    public void setNetherEnabled(boolean enabled) {
        this.netherEnabled = enabled;
        settings.yaml().set(KEY_NETHER, enabled);
        persist();
    }

    public void setEndEnabled(boolean enabled) {
        this.endEnabled = enabled;
        settings.yaml().set(KEY_END, enabled);
        persist();
    }

    private void persist() {
        try {
            settings.save();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Kon dimensie-instellingen niet opslaan in settings.yml.", ex);
        }
    }
}
