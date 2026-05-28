package be.panchito.pointRush.commands;

import be.panchito.pointRush.live.LivePlatform;
import be.panchito.pointRush.live.LiveStreamEntry;
import be.panchito.pointRush.live.LiveStreamUrls;
import be.panchito.pointRush.storage.mongo.MongoLiveStreamRepository;
import be.panchito.pointRush.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public final class LiveCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLATFORMS = List.of("twitch", "youtube", "tiktok", "off");

    private final JavaPlugin plugin;
    private final MongoLiveStreamRepository liveRepo;

    public LiveCommand(JavaPlugin plugin, MongoLiveStreamRepository liveRepo) {
        this.plugin = plugin;
        this.liveRepo = liveRepo;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen dit gebruiken."));
            return true;
        }
        if (!player.hasPermission("pointrush.streamer")) {
            player.sendMessage(Messages.error("Geen streamer permissie (pointrush.streamer)."));
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("off".equals(sub) || "stop".equals(sub)) {
            if (liveRepo == null) {
                player.sendMessage(Messages.error("Live streams zijn niet beschikbaar (MongoDB niet verbonden)."));
                return true;
            }
            clearLive(player, false);
            return true;
        }

        Optional<LivePlatform> platform = LivePlatform.parse(sub);
        if (platform.isEmpty()) {
            sendHelp(player);
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(Messages.error("Gebruik: /live " + sub + " <link>"));
            return true;
        }

        if (liveRepo == null) {
            player.sendMessage(Messages.error("Live streams zijn niet beschikbaar (MongoDB niet verbonden)."));
            return true;
        }

        String rawUrl = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        Optional<String> normalized = LiveStreamUrls.normalize(platform.get(), rawUrl);
        if (normalized.isEmpty()) {
            player.sendMessage(Messages.error("Ongeldige " + sub + " link. Plak de volledige live URL."));
            return true;
        }

        LiveStreamEntry entry = new LiveStreamEntry(
                player.getUniqueId(),
                player.getName(),
                platform.get(),
                normalized.get(),
                System.currentTimeMillis());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                liveRepo.upsert(entry);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Messages.success("Je bent nu LIVE op pointrush.coresmp.nl!"));
                    player.sendMessage(Messages.info("Platform: " + platform.get().id() + " · " + normalized.get()));
                    player.sendMessage(Messages.info("Stop met /live off"));
                });
            } catch (Exception ex) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(Messages.error("Kon live status niet opslaan. Probeer opnieuw.")));
            }
        });
        return true;
    }

    public void clearLive(Player player) {
        clearLive(player, false);
    }

    public void clearLive(Player player, boolean silent) {
        if (liveRepo == null) {
            if (!silent) {
                player.sendMessage(Messages.error("Live streams zijn niet beschikbaar (MongoDB niet verbonden)."));
            }
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                liveRepo.remove(player.getUniqueId());
                if (!silent) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage(Messages.success("Live stream verwijderd van de website.")));
                }
            } catch (Exception ex) {
                if (!silent) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage(Messages.error("Kon live status niet verwijderen.")));
                }
            }
        });
    }

    private void sendHelp(Player player) {
        player.sendMessage(Messages.accent("--- Streamer /live ---"));
        player.sendMessage(Messages.info("/live twitch <link>"));
        player.sendMessage(Messages.info("/live youtube <link>"));
        player.sendMessage(Messages.info("/live tiktok <link>"));
        player.sendMessage(Messages.info("/live off — stop live op website"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return PLATFORMS.stream()
                    .filter(p -> p.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
