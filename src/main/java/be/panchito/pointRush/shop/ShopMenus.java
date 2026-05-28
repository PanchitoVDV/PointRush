package be.panchito.pointRush.shop;

import be.panchito.pointRush.PointRush;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ShopMenus {

    private ShopMenus() {
    }

    public static void openHub(PointRush plugin, Player player, CoinShopService service) {
        ShopGuiHolder holder = new ShopGuiHolder(ShopGuiHolder.Kind.HUB, null);
        Component title = Component.text()
                .append(Component.text(SmallText.of("CoreSMP "), NamedTextColor.DARK_PURPLE))
                .append(Component.text(SmallText.of("- Rush-winkel"), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .build();
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.attach(inv);

        fillBorder(inv);
        inv.setItem(13, balanceStack(plugin, player, service));

        putCategory(inv, holder, 29, ShopCategory.PARKOUR);
        putCategory(inv, holder, 31, ShopCategory.TNT_TAG);
        putCategory(inv, holder, 33, ShopCategory.TNT_RUN);
        putCategory(inv, holder, 35, ShopCategory.RACE);

        player.openInventory(inv);
    }

    public static void openCategory(PointRush plugin, Player player, CoinShopService service, ShopCategory cat) {
        ShopGuiHolder holder = new ShopGuiHolder(ShopGuiHolder.Kind.CATEGORY, cat);
        Component title = Component.text()
                .append(Component.text(SmallText.of(cat.title() + " "), cat.accent()))
                .append(Component.text(SmallText.of("- voordelen"), NamedTextColor.GRAY))
                .build();
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.attach(inv);
        fillBorder(inv);

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta bm = back.getItemMeta();
        if (bm != null) {
            bm.displayName(Component.text(SmallText.of("Terug"), NamedTextColor.RED, TextDecoration.BOLD));
            bm.lore(List.of(Component.text(SmallText.of("Terug naar Rush-winkel"), NamedTextColor.DARK_GRAY)));
            back.setItemMeta(bm);
        }
        inv.setItem(45, back);

        inv.setItem(4, balanceStack(plugin, player, service));

        List<ShopOfferView> offers = ShopCatalog.forCategory(cat);
        int[] slots = {20, 22, 24};
        for (int i = 0; i < Math.min(slots.length, offers.size()); i++) {
            ShopOfferView o = offers.get(i);
            holder.registerOfferSlot(slots[i], o.perkId());
            inv.setItem(slots[i], offerStack(o));
        }

        player.openInventory(inv);
    }

    private static void putCategory(Inventory inv, ShopGuiHolder holder, int slot, ShopCategory cat) {
        holder.registerCategorySlot(slot, cat);
        ItemStack stack = new ItemStack(cat.icon());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(SmallText.of(cat.title() + " - Rush-voordelen"), cat.accent(), TextDecoration.BOLD));
            meta.lore(List.of(
                    Component.text(SmallText.of("Klik - perks voor deze PointRush-modus"), NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text(SmallText.of("Betaal met Rush-tegoed (munten worden automatisch verrekend)"), NamedTextColor.DARK_GRAY, TextDecoration.ITALIC)
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        inv.setItem(slot, stack);
    }

    private static ItemStack balanceStack(PointRush plugin, Player player, CoinShopService service) {
        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = star.getItemMeta();
        if (meta != null) {
            int bal = service.balanceCredits(player.getUniqueId());
            meta.displayName(Component.text(SmallText.of("Jouw Rush-tegoed"), NamedTextColor.GOLD, TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text()
                    .append(Component.text(SmallText.of("Saldo Rush-tegoed: "), NamedTextColor.GRAY))
                    .append(Component.text(String.valueOf(bal), NamedTextColor.YELLOW))
                    .build());
            lore.add(Component.empty());
            var repo = plugin.getDataManager().getPlayerCoinRepository();
            if (repo != null) {
                Map<String, Integer> pending = repo.getShopCharges(player.getUniqueId());
                if (!pending.isEmpty()) {
                    lore.add(Component.text(SmallText.of("Klaar voor je volgende event:"), NamedTextColor.AQUA));
                    for (Map.Entry<String, Integer> e : pending.entrySet()) {
                        String label = e.getKey();
                        for (ShopOfferView o : ShopCatalog.allOffers()) {
                            if (o.perkId().equals(e.getKey())) {
                                label = o.displayName();
                                break;
                            }
                        }
                        lore.add(Component.text(SmallText.of("- " + label + " x" + e.getValue()), NamedTextColor.GRAY));
                    }
                } else {
                    lore.add(Component.text(SmallText.of("Nog geen perks gekocht - pak er hieronder een."), NamedTextColor.DARK_GRAY));
                }
            }
            lore.add(Component.empty());
            lore.add(Component.text(SmallText.of("Tip: gouden diamant levert het meeste tegoed op."), NamedTextColor.DARK_GRAY, TextDecoration.ITALIC));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            star.setItemMeta(meta);
        }
        return star;
    }

    private static ItemStack offerStack(ShopOfferView o) {
        ItemStack stack = new ItemStack(o.icon());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(SmallText.of(o.displayName()), NamedTextColor.GREEN, TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            for (String line : o.descriptionLines()) {
                lore.add(Component.text(SmallText.of(line), NamedTextColor.GRAY));
            }
            lore.add(Component.empty());
            lore.add(Component.text()
                    .append(Component.text(SmallText.of("Prijs: "), NamedTextColor.GOLD))
                    .append(Component.text(String.valueOf(o.priceCredits()), NamedTextColor.YELLOW))
                    .append(Component.text(SmallText.of(" tegoed"), NamedTextColor.GRAY))
                    .build());
            lore.add(Component.text(SmallText.of("Linksklik om te kopen"), NamedTextColor.DARK_AQUA, TextDecoration.ITALIC));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static void fillBorder(Inventory inv) {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        if (pm != null) {
            pm.displayName(Component.text(" "));
            pane.setItemMeta(pm);
        }
        int rows = inv.getSize() / 9;
        for (int slot = 0; slot < inv.getSize(); slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                inv.setItem(slot, pane.clone());
            }
        }
    }

    public static final class ShopGuiListener implements Listener {

        private final PointRush plugin;
        private final CoinShopService service;

        public ShopGuiListener(PointRush plugin, CoinShopService service) {
            this.plugin = plugin;
            this.service = service;
        }

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder() instanceof ShopGuiHolder holder)) {
                return;
            }
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= event.getInventory().getSize()) {
                return;
            }

            if (holder.kind == ShopGuiHolder.Kind.HUB) {
                ShopCategory cat = holder.categoryAt(slot);
                if (cat != null) {
                    openCategory(plugin, player, service, cat);
                }
                return;
            }

            if (holder.kind == ShopGuiHolder.Kind.CATEGORY) {
                if (slot == 45 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.BARRIER) {
                    openHub(plugin, player, service);
                    return;
                }
                String perkId = holder.perkAt(slot);
                if (perkId != null) {
                    ShopOfferView offer = service.findOffer(perkId);
                    if (offer == null) {
                        player.sendMessage(Messages.error("Dat item staat niet in de winkel."));
                        return;
                    }
                    String err = service.tryPurchase(player, offer);
                    if (err != null) {
                        player.sendMessage(Messages.error(err));
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 0.8f, 0.7f);
                        return;
                    }
                    player.sendMessage(Messages.success("Gekocht: " + SmallText.of(offer.displayName())
                            + " - staat klaar bij je volgende PointRush-event."));
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.3f);
                    ShopCategory cat = holder.targetCategory();
                    if (cat != null) {
                        openCategory(plugin, player, service, cat);
                    } else {
                        openHub(plugin, player, service);
                    }
                }
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            if (event.getInventory().getHolder() instanceof ShopGuiHolder) {
                event.setCancelled(true);
            }
        }
    }

    private static final class ShopGuiHolder implements InventoryHolder {

        enum Kind { HUB, CATEGORY }

        private final Kind kind;
        private final @Nullable ShopCategory category;
        private final Map<Integer, ShopCategory> categorySlots = new HashMap<>();
        private final Map<Integer, String> offerSlots = new HashMap<>();
        private @Nullable Inventory inventory;

        ShopGuiHolder(Kind kind, @Nullable ShopCategory category) {
            this.kind = kind;
            this.category = category;
        }

        void attach(Inventory inventory) {
            this.inventory = inventory;
        }

        void registerCategorySlot(int slot, ShopCategory cat) {
            categorySlots.put(slot, cat);
        }

        void registerOfferSlot(int slot, String perkId) {
            offerSlots.put(slot, perkId);
        }

        @Nullable ShopCategory categoryAt(int slot) {
            return categorySlots.get(slot);
        }

        @Nullable String perkAt(int slot) {
            return offerSlots.get(slot);
        }

        @Nullable ShopCategory targetCategory() {
            return category;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
