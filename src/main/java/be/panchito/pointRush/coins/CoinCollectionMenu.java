package be.panchito.pointRush.coins;

import be.panchito.pointRush.storage.DataManager;
import be.panchito.pointRush.storage.mongo.MongoPlayerCoinRepository;
import be.panchito.pointRush.util.Messages;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GUI met alle Rush-munttypes en je verzamelde aantallen (CoreSMP / PointRush).
 */
public final class CoinCollectionMenu {

    /** Binnenste grid: twee rijen van 6 voor max. 12 verschillende coins. */
    private static final int[] COIN_SLOTS = {
            10, 11, 12, 13, 14, 15,
            19, 20, 21, 22, 23, 24
    };

    private CoinCollectionMenu() {
    }

    public static void open(Player player, CoinSpawnConfig config, DataManager dataManager) {
        MongoPlayerCoinRepository repo = dataManager.getPlayerCoinRepository();
        if (repo == null) {
            player.sendMessage(Messages.error("Rush-munten zijn nu niet beschikbaar."));
            return;
        }
        List<String> pool = List.copyOf(config.getResolvedPoolIds());
        Map<String, Integer> totals = repo.getTotals(player.getUniqueId(), pool);

        CoinMenuHolder holder = new CoinMenuHolder();
        Component title = Component.text()
                .append(Component.text(SmallText.of("CoreSMP "), NamedTextColor.DARK_PURPLE))
                .append(Component.text(SmallText.of("- Rush-munten"), NamedTextColor.GOLD, TextDecoration.BOLD))
                .build();
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.attach(inv);

        fillBorder(inv);

        int grandTotal = pool.stream().mapToInt(id -> totals.getOrDefault(id, 0)).sum();
        inv.setItem(4, summaryStack(grandTotal, pool, totals));

        int n = Math.min(pool.size(), COIN_SLOTS.length);
        for (int i = 0; i < n; i++) {
            String id = pool.get(i);
            int count = totals.getOrDefault(id, 0);
            inv.setItem(COIN_SLOTS[i], displayStackForCoin(id, count));
        }

        player.openInventory(inv);
    }

    private static void fillBorder(Inventory inv) {
        ItemStack pane = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inv.getSize(); slot++) {
            int row = slot / 9;
            int col = slot % 9;
            boolean edge = row == 0 || row == 5 || col == 0 || col == 8;
            if (edge && slot != 4) {
                inv.setItem(slot, pane.clone());
            }
        }
        ItemStack accent = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int c = 1; c <= 7; c++) {
            if (c == 4) {
                continue;
            }
            inv.setItem(c, accent.clone());
            inv.setItem(45 + c, accent.clone());
        }
        inv.setItem(0, accent.clone());
        inv.setItem(8, accent.clone());
        inv.setItem(45, accent.clone());
        inv.setItem(53, accent.clone());
    }

    private static ItemStack pane(Material material) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack summaryStack(int grandTotal, List<String> pool, Map<String, Integer> totals) {
        ItemStack stack = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(SmallText.of("Overzicht"), NamedTextColor.GOLD, TextDecoration.BOLD));
            long foundTypes = pool.stream().filter(id -> totals.getOrDefault(id, 0) > 0).count();
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text()
                    .append(Component.text(SmallText.of("Totaal Rush-munten: "), NamedTextColor.GRAY))
                    .append(Component.text(String.valueOf(grandTotal), NamedTextColor.YELLOW))
                    .build());
            lore.add(Component.text()
                    .append(Component.text(SmallText.of("Soorten: "), NamedTextColor.GRAY))
                    .append(Component.text(foundTypes + " / " + pool.size(), NamedTextColor.AQUA))
                    .build());
            lore.add(Component.empty());
            lore.add(Component.text(SmallText.of("PointRush - alleen je teller; je inventaris blijft zoals hij is."),
                    NamedTextColor.DARK_GRAY, TextDecoration.ITALIC));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack displayStackForCoin(String nexoId, int count) {
        ItemStack icon = NexoCoinFactory.stackForId(nexoId);
        if (icon == null || icon.isEmpty()) {
            icon = new ItemStack(Material.GOLD_NUGGET);
        } else {
            icon = icon.clone();
        }
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            String name = CoinDisplayNames.friendlyName(nexoId);
            NamedTextColor nameColor = count > 0 ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY;
            meta.displayName(Component.text(SmallText.of(name), nameColor, TextDecoration.BOLD));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            if (count > 0) {
                lore.add(Component.text()
                        .append(Component.text(SmallText.of("Verzameld: "), NamedTextColor.GRAY))
                        .append(Component.text(String.valueOf(count), NamedTextColor.YELLOW))
                        .build());
            } else {
                lore.add(Component.text(SmallText.of("Nog niet gevonden"), NamedTextColor.DARK_GRAY, TextDecoration.ITALIC));
            }
            lore.add(Component.empty());
            lore.add(Component.text(SmallText.of("Verschillende designs, zelfde Rush-munt."), NamedTextColor.DARK_GRAY, TextDecoration.ITALIC));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stripTooltipNoise(meta, count > 0);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private static void stripTooltipNoise(ItemMeta meta, boolean collected) {
        try {
            if (collected) {
                meta.setEnchantmentGlintOverride(Boolean.TRUE);
            }
        } catch (Throwable ignored) {
            // Oudere Paper / Spigot builds zonder deze API
        }
        try {
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        } catch (Throwable ignored) {
        }
    }

    private static final class CoinMenuHolder implements InventoryHolder {

        private @Nullable Inventory inventory;

        private void attach(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Voorkomt dat spelers items uit het overzicht trekken. */
    public static final class CoinCollectionMenuListener implements Listener {

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder() instanceof CoinMenuHolder)) {
                return;
            }
            event.setCancelled(true);
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            if (!(event.getInventory().getHolder() instanceof CoinMenuHolder)) {
                return;
            }
            event.setCancelled(true);
        }
    }
}
