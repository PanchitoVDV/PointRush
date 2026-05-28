package be.panchito.pointRush.commands;

import be.panchito.pointRush.PointRush;
import be.panchito.pointRush.shop.CoinShopService;
import be.panchito.pointRush.shop.ShopMenus;
import be.panchito.pointRush.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Coin-shop GUI voor minigame boosts.
 */
public final class ShopCommand implements CommandExecutor {

    private final PointRush plugin;
    private final CoinShopService shopService;

    public ShopCommand(PointRush plugin, CoinShopService shopService) {
        this.plugin = plugin;
        this.shopService = shopService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Alleen spelers kunnen /shop gebruiken."));
            return true;
        }
        if (!player.hasPermission("pointrush.shop.use")) {
            player.sendMessage(Messages.error("Geen permissie."));
            return true;
        }
        if (plugin.getDataManager().getPlayerCoinRepository() == null) {
            player.sendMessage(Messages.error("Rush-munten zijn nu niet beschikbaar."));
            return true;
        }
        ShopMenus.openHub(plugin, player, shopService);
        return true;
    }
}
