package be.panchito.pointRush.minigame.finale;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;

/**
 * Een opgeslagen uitrusting (inventory-snapshot) voor het Finale-event.
 *
 * <p>Een admin stelt de kit in met {@code /finale setkit}: zijn volledige inventory
 * — hotbar, opslag, harnas en off-hand — wordt vastgelegd en in {@code settings.yml}
 * bewaard. Bij de start van het event krijgt iedere deelnemer exact deze items.</p>
 *
 * <p>Items worden als Bukkit-{@link ItemStack} (ConfigurationSerializable) per slot
 * opgeslagen, zodat enchantments, namen en custom meta volledig behouden blijven.</p>
 */
public final class FinaleKit {

    /** 36 slots: hotbar (0-8) + hoofdopslag (9-35). */
    private final ItemStack[] contents;
    /** 4 harnas-slots: boots, leggings, chestplate, helmet. */
    private final ItemStack[] armor;
    private final ItemStack offhand;

    public FinaleKit(ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
        this.contents = normalize(contents, 36);
        this.armor = normalize(armor, 4);
        this.offhand = isReal(offhand) ? offhand.clone() : null;
    }

    /** Legt de huidige inventory van een speler vast als kit. */
    public static FinaleKit capture(Player player) {
        PlayerInventory inv = player.getInventory();
        return new FinaleKit(
                inv.getStorageContents(),
                inv.getArmorContents(),
                inv.getItemInOffHand()
        );
    }

    /** Geeft deze kit aan een speler (oude inventory wordt eerst geleegd). */
    public void apply(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setStorageContents(cloneArray(contents));
        inv.setArmorContents(cloneArray(armor));
        inv.setItemInOffHand(offhand == null ? null : offhand.clone());
        // Forceer resync zodat harnas dat in dezelfde tick als een (cross-world) teleport
        // wordt aangetrokken niet als "ghost armor" blijft hangen.
        player.updateInventory();
    }

    public boolean isEmpty() {
        return itemCount() == 0;
    }

    public int itemCount() {
        int n = 0;
        for (ItemStack i : contents) if (isReal(i)) n++;
        for (ItemStack i : armor) if (isReal(i)) n++;
        if (isReal(offhand)) n++;
        return n;
    }

    /** Schrijft de kit naar een config-sectie (bestaande inhoud wordt gewist). */
    public void save(ConfigurationSection sec) {
        for (String key : new ArrayList<>(sec.getKeys(false))) {
            sec.set(key, null);
        }
        for (int i = 0; i < contents.length; i++) {
            if (isReal(contents[i])) sec.set("contents." + i, contents[i]);
        }
        for (int i = 0; i < armor.length; i++) {
            if (isReal(armor[i])) sec.set("armor." + i, armor[i]);
        }
        if (isReal(offhand)) sec.set("offhand", offhand);
    }

    /** Leest een kit terug uit config; {@code null} als er niets (geldigs) staat. */
    public static FinaleKit load(ConfigurationSection sec) {
        if (sec == null) return null;
        ItemStack[] contents = readIndexed(sec.getConfigurationSection("contents"), 36);
        ItemStack[] armor = readIndexed(sec.getConfigurationSection("armor"), 4);
        ItemStack offhand = sec.getItemStack("offhand");
        FinaleKit kit = new FinaleKit(contents, armor, offhand);
        return kit.isEmpty() ? null : kit;
    }

    private static ItemStack[] readIndexed(ConfigurationSection sec, int size) {
        ItemStack[] out = new ItemStack[size];
        if (sec == null) return out;
        for (String key : sec.getKeys(false)) {
            int idx;
            try {
                idx = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                continue;
            }
            if (idx < 0 || idx >= size) continue;
            ItemStack item = sec.getItemStack(key);
            if (isReal(item)) out[idx] = item;
        }
        return out;
    }

    private static ItemStack[] normalize(ItemStack[] src, int size) {
        ItemStack[] out = new ItemStack[size];
        if (src == null) return out;
        for (int i = 0; i < size && i < src.length; i++) {
            out[i] = isReal(src[i]) ? src[i].clone() : null;
        }
        return out;
    }

    private static ItemStack[] cloneArray(ItemStack[] src) {
        ItemStack[] out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            out[i] = src[i] == null ? null : src[i].clone();
        }
        return out;
    }

    private static boolean isReal(ItemStack item) {
        return item != null && !item.getType().isAir();
    }
}
