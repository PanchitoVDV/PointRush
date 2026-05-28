package be.panchito.pointRush.coins;

import be.panchito.pointRush.storage.DataManager;
import be.panchito.pointRush.storage.mongo.MongoPlayerCoinRepository;
import be.panchito.pointRush.util.Messages;

import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Raapt alleen tijdens PointRush-events gedropte Rush-munten; schrijft naar je profiel.
 */
public final class CoinPickupListener implements Listener {

    private final JavaPlugin plugin;
    private final CoinSpawnConfig coinConfig;
    private final DataManager dataManager;
    private final NexoCoinSpawner spawner;

    public CoinPickupListener(JavaPlugin plugin, CoinSpawnConfig coinConfig,
                             DataManager dataManager, NexoCoinSpawner spawner) {
        this.plugin = plugin;
        this.coinConfig = coinConfig;
        this.dataManager = dataManager;
        this.spawner = spawner;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!coinConfig.isEnabled()) {
            return;
        }
        Item entity = event.getItem();
        ItemStack stack = entity.getItemStack();
        if (!CoinItemMarker.isStamped(plugin, stack)) {
            return;
        }
        String nexoId = NexoCoinFactory.idFromStack(stack);
        if (nexoId == null || !coinConfig.isPoolIdConfigured(nexoId)) {
            return;
        }
        Location dropLoc = entity.getLocation().clone();
        event.setCancelled(true);
        entity.remove();

        MongoPlayerCoinRepository repo = dataManager.getPlayerCoinRepository();
        if (repo == null) {
            player.sendMessage(Messages.error("Rush-munten zijn nu niet beschikbaar."));
            return;
        }
        repo.incrementCollected(player.getUniqueId(), nexoId, stack.getAmount());

        int slot = CoinItemMarker.readSpawnSlot(plugin, stack);
        if (slot < 0) {
            slot = coinConfig.nearestSpawnSlot(dropLoc);
        }
        spawner.registerPickupCooldown(slot);

        String name = CoinDisplayNames.friendlyName(nexoId);
        String label = stack.getAmount() > 1 ? stack.getAmount() + " x " + name : name;
        player.sendMessage(Messages.success("Rush-munt bijgeschreven: " + label));
    }
}
