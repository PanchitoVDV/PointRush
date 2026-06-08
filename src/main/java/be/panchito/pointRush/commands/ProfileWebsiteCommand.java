package be.panchito.pointRush.commands;

import be.panchito.pointRush.profile.PlayerProfileService;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Opent de PointRush-website via een klikbare chatlink (Ultimate UI profielknop).
 */
public final class ProfileWebsiteCommand implements CommandExecutor {

    private final PlayerProfileService profiles;

    public ProfileWebsiteCommand(PlayerProfileService profiles) {
        this.profiles = profiles;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen de website openen."));
            return true;
        }
        String url = profiles.websiteUrl();
        if (url == null || url.isBlank()) {
            player.sendMessage(Messages.error("Geen website-URL geconfigureerd in settings.yml."));
            return true;
        }
        Component link = Component.text(SmallText.of("Klik hier voor de PointRush website"),
                        NamedTextColor.GOLD, TextDecoration.BOLD)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text(url, NamedTextColor.GRAY)));
        player.sendMessage(Component.empty());
        player.sendMessage(link);
        player.sendMessage(Component.text(SmallText.of(url), NamedTextColor.DARK_GRAY));
        return true;
    }
}
