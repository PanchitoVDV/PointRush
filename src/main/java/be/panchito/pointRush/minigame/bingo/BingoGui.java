package be.panchito.pointRush.minigame.bingo;

import be.panchito.pointRush.team.Team;
import be.panchito.pointRush.util.SmallText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GUI met de team-bingo kaart (klik map in inventory).
 */
public final class BingoGui {

    private static final int SIZE = 54;

    private BingoGui() {
    }

    public static void open(Player viewer, BingoGame game) {
        Team team = game.getPlugin().getTeamManager().getTeamOfPlayer(viewer.getUniqueId());
        UUID bucket = game.bucketFor(viewer.getUniqueId());
        BingoTeamProgress progress = game.getTeamProgress(bucket);
        if (progress == null) {
            viewer.sendMessage(net.kyori.adventure.text.Component.text(
                    SmallText.of("Je doet niet mee aan Bingo."), NamedTextColor.RED));
            return;
        }

        game.syncTeam(bucket);

        BingoGuiHolder holder = new BingoGuiHolder(viewer.getUniqueId());
        Component title = Component.text()
                .append(Component.text(SmallText.of("Bingo · "), NamedTextColor.GOLD))
                .append(Component.text(SmallText.of(progress.getLabel()),
                        team != null ? team.getColor() : NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(progress.countFound() + "/" + BingoGrid.TOTAL,
                        NamedTextColor.GREEN))
                .build();

        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.attach(inv);
        paint(inv, game, progress);
        game.trackOpenGui(viewer.getUniqueId(), inv);
        viewer.openInventory(inv);
    }

    public static void refresh(Inventory inv, BingoGame game, BingoTeamProgress progress) {
        if (!(inv.getHolder() instanceof BingoGuiHolder)) return;
        paint(inv, game, progress);
    }

    private static void paint(Inventory inv, BingoGame game, BingoTeamProgress progress) {
        Material[] card = game.getSharedCardTiles();
        boolean[] checked = progress.copyChecked();

        fillBorder(inv);

        for (int i = 0; i < BingoGrid.TOTAL; i++) {
            int slot = BingoGrid.GUI_SLOTS[i];
            Material need = card[i];
            boolean done = checked[i];
            inv.setItem(slot, cellStack(need, done, i == BingoGrid.FREE_INDEX));
        }

        inv.setItem(4, headerStack(progress, game));
    }

    private static ItemStack headerStack(BingoTeamProgress progress, BingoGame game) {
        ItemStack star = new ItemStack(Material.NETHER_STAR);
        star.editMeta(meta -> {
            meta.displayName(Component.text(SmallText.of("Team voortgang"),
                    NamedTextColor.GOLD, TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text()
                    .append(Component.text(SmallText.of("Gevonden: "), NamedTextColor.GRAY))
                    .append(Component.text(progress.countFound() + " / " + BingoGrid.TOTAL,
                            NamedTextColor.GREEN))
                    .build());
            lore.add(Component.text()
                    .append(Component.text(SmallText.of("Nog nodig: "), NamedTextColor.GRAY))
                    .append(Component.text(Math.max(0, progress.countNeeded()), NamedTextColor.WHITE))
                    .build());
            lore.add(Component.text()
                    .append(Component.text(SmallText.of("Resttijd: "), NamedTextColor.GRAY))
                    .append(Component.text(game.formatTime(game.getRunTimeLeftMs()), NamedTextColor.AQUA))
                    .build());
            if (progress.isComplete()) {
                lore.add(Component.text(SmallText.of("BINGO COMPLEET!"), NamedTextColor.GREEN, TextDecoration.BOLD));
            }
            meta.lore(lore);
        });
        return star;
    }

    private static ItemStack cellStack(Material material, boolean done, boolean free) {
        if (free) {
            ItemStack freeStack = new ItemStack(Material.LIME_WOOL);
            freeStack.editMeta(meta -> {
                meta.displayName(Component.text(SmallText.of("GRATIS"), NamedTextColor.GREEN, TextDecoration.BOLD));
                meta.lore(List.of(Component.text(SmallText.of("Vrij vak"), NamedTextColor.GRAY)));
            });
            return freeStack;
        }

        Material display = done ? Material.LIME_STAINED_GLASS_PANE : material;
        ItemStack stack = new ItemStack(display);
        stack.editMeta(meta -> {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            if (done) {
                meta.displayName(Component.text()
                        .append(Component.text("✓ ", NamedTextColor.GREEN, TextDecoration.BOLD))
                        .append(Component.text(formatMaterial(material), NamedTextColor.WHITE,
                                TextDecoration.STRIKETHROUGH))
                        .build());
                meta.lore(List.of(Component.text(SmallText.of("Gevonden door team"), NamedTextColor.GREEN)));
            } else {
                meta.displayName(Component.text(formatMaterial(material), NamedTextColor.YELLOW));
                meta.lore(List.of(Component.text(SmallText.of("Nog zoeken..."), NamedTextColor.GRAY)));
            }
        });
        return stack;
    }

    private static String formatMaterial(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
    }

    private static void fillBorder(Inventory inv) {
        ItemStack pane = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inv.getSize(); slot++) {
            boolean inGrid = false;
            for (int g : BingoGrid.GUI_SLOTS) {
                if (g == slot) {
                    inGrid = true;
                    break;
                }
            }
            if (!inGrid && slot != 4) {
                inv.setItem(slot, pane.clone());
            }
        }
    }

    private static ItemStack pane(Material material) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(ItemMeta.class, meta -> {
            meta.displayName(Component.text(" "));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
        return stack;
    }

    public static final class BingoGuiHolder implements InventoryHolder {

        private final java.util.UUID viewerId;
        private Inventory inventory;

        BingoGuiHolder(java.util.UUID viewerId) {
            this.viewerId = viewerId;
        }

        void attach(Inventory inventory) {
            this.inventory = inventory;
        }

        public java.util.UUID getViewerId() {
            return viewerId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
