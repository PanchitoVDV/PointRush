package be.panchito.pointRush.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

/**
 * Single {@code settings.yml} under the plugin data folder: Mongo connectivity,
 * cache tuning and nested sections for each minigame module.
 *
 * <p>On first run after upgrading from older builds, legacy {@code *.yml} arena
 * files are merged into this file once so operators keep their setups.
 *
 * <p>Loads YAML from raw bytes so UTF-16 saves (Windows tools) do not break SnakeYAML.
 */
public final class UnifiedSettings {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    public UnifiedSettings(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "settings.yml");
    }

    public YamlConfiguration yaml() {
        return yaml;
    }

    public void load() throws IOException, InvalidConfigurationException {
        ensureFolder();
        if (!file.exists()) {
            plugin.saveResource("settings.yml", false);
        }
        YamlTextDecoder.Result decoded = YamlTextDecoder.decode(Files.readAllBytes(file.toPath()));
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(decoded.text());
        this.yaml = cfg;
        if (decoded.needsRewriteAsUtf8()) {
            plugin.getLogger().warning(
                    "settings.yml was niet UTF-8 (vaak UTF-16); wordt nu opnieuw opgeslagen als UTF-8.");
            saveQuiet();
        }
        migrateLegacyIfNeeded();
        saveQuiet();
    }

    /** Atomisch naar schijf — altijd UTF-8 zonder platform-default encoding. */
    public void save() throws IOException {
        ensureFolder();
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        Files.writeString(tmp.toPath(), yaml.saveToString(), StandardCharsets.UTF_8);
        try {
            Files.move(tmp.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception atomicFail) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void saveQuiet() {
        try {
            save();
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Kon settings.yml niet opslaan.", ex);
        }
    }

    private void ensureFolder() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Kon plugin data folder niet aanmaken.");
        }
    }

    private void migrateLegacyIfNeeded() {
        migrateLegacyRootFile("parkour.yml", "parkour");
        migrateLegacyRootFile("tnttag.yml", "tnttag");
        migrateLegacyRootFile("tntrun.yml", "tntrun");
        migrateLegacyRootFile("race.yml", "race");
    }

    private void migrateLegacyRootFile(String legacyName, String targetKey) {
        File legacy = new File(plugin.getDataFolder(), legacyName);
        if (!legacy.exists()) {
            return;
        }
        if (!sectionMissingOrEmpty(targetKey)) {
            return;
        }
        YamlConfiguration leg;
        try {
            YamlTextDecoder.Result dec = YamlTextDecoder.decode(Files.readAllBytes(legacy.toPath()));
            leg = new YamlConfiguration();
            leg.loadFromString(dec.text());
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().log(Level.WARNING, "Kon legacy " + legacyName + " niet lezen voor migratie.", ex);
            return;
        }
        if (leg.getKeys(false).isEmpty()) {
            return;
        }
        for (String k : leg.getKeys(false)) {
            yaml.set(targetKey + "." + k, leg.get(k));
        }
        plugin.getLogger().info("Migratie: " + legacyName + " -> settings.yml sectie '" + targetKey + "'.");
    }

    private boolean sectionMissingOrEmpty(String key) {
        var sec = yaml.getConfigurationSection(key);
        return sec == null || sec.getKeys(false).isEmpty();
    }
}
