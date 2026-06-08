package be.panchito.pointRush.placeholder;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.profile.PlayerProfileService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Placeholders voor Ultimate UI profielmenu ({@code %pointrush_...%}).
 */
public final class PointRushExpansion extends PlaceholderExpansion {

    private final PointRush plugin;
    private final PlayerProfileService profiles;

    public PointRushExpansion(PointRush plugin, PlayerProfileService profiles) {
        this.plugin = plugin;
        this.profiles = profiles;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "pointrush";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        String value = profiles.resolve(player.getUniqueId(), params);
        return value != null ? value : "";
    }
}
