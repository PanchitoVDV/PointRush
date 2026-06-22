package be.panchito.pointRush.commands;

import be.panchito.pointRush.team.Team;
import be.panchito.pointRush.team.TeamManager;
import be.panchito.pointRush.util.Commands;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /camper <speler> [seconden]} — anti-camp tool.
 *
 * <p>De doelspeler gaat {@link PotionEffectType#GLOWING glowen} (door muren heen zichtbaar voor
 * iedereen) en zijn exacte locatie wordt server-breed in de chat gezet. Zo worden campers eruit
 * gerookt en gedwongen het gevecht aan te gaan.</p>
 */
public final class CamperCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "pointrush.camper";
    private static final int DEFAULT_SECONDS = 30;
    private static final int MIN_SECONDS = 5;
    private static final int MAX_SECONDS = 300;

    private final TeamManager teamManager;

    public CamperCommand(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!Commands.isAdmin(sender, PERMISSION)) {
            sender.sendMessage(Messages.error("Je hebt geen permissie voor dit commando."));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(Messages.error("Gebruik: /camper <speler> [seconden]"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Messages.error("Speler '" + args[0] + "' is niet online."));
            return true;
        }

        int seconds = DEFAULT_SECONDS;
        if (args.length >= 2) {
            try {
                seconds = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(Messages.error("Tijd moet een geheel getal (seconden) zijn."));
                return true;
            }
            seconds = Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, seconds));
        }

        // Glow toepassen - ambient/geen partikels, wel zichtbaar door muren.
        target.removePotionEffect(PotionEffectType.GLOWING);
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.GLOWING, seconds * 20, 0, false, false, true));

        broadcastCamper(target, seconds);

        target.showTitle(Title.title(
                Component.text(SmallText.of("JE BENT GESPOT"), NamedTextColor.RED, TextDecoration.BOLD),
                Component.text(SmallText.of("stop met campen - je locatie is gedeeld!"), NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2400), Duration.ofMillis(400))
        ));
        target.playSound(target.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 1.0f);

        sender.sendMessage(Messages.success(target.getName() + " is gemarkeerd als camper ("
                + seconds + "s glow + locatie gedeeld)."));
        return true;
    }

    private void broadcastCamper(Player target, int seconds) {
        Location loc = target.getLocation();
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "?";
        Team team = teamManager.getTeamOfPlayer(target.getUniqueId());

        Component line = Component.text()
                .append(Component.text(SmallText.of("CAMPER GESPOT! "), NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(target.getName(),
                        team != null ? team.getColor() : NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(SmallText.of(" zit bij "), NamedTextColor.GRAY))
                .append(Component.text(SmallText.of(world + " " + loc.getBlockX() + " / "
                        + loc.getBlockY() + " / " + loc.getBlockZ()), NamedTextColor.YELLOW))
                .append(Component.text(SmallText.of("  - hij glowt " + seconds + "s. Erop af!"),
                        NamedTextColor.GRAY))
                .build();

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(Messages.PREFIX.append(line));
            online.playSound(online.getLocation(), Sound.BLOCK_BELL_USE, 0.8f, 1.4f);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!Commands.isAdmin(sender, PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(p.getName());
                }
            }
            return names;
        }
        if (args.length == 2) {
            return Commands.filterPrefix(List.of("15", "30", "60", "120"), args[1]);
        }
        return List.of();
    }
}
