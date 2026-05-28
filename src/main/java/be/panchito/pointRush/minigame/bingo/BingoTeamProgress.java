package be.panchito.pointRush.minigame.bingo;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Voortgang van één team (of solo-speler) op de gedeelde kaart.
 */
public final class BingoTeamProgress {

    private final UUID bucketId;
    private final String label;
    private final boolean[] checked = new boolean[BingoGrid.TOTAL];
    private long completedAtMs = 0L;
    private boolean completionAnnounced = false;

    public BingoTeamProgress(UUID bucketId, String label) {
        this.bucketId = bucketId;
        this.label = label;
        checked[BingoGrid.FREE_INDEX] = true;
    }

    public UUID getBucketId() {
        return bucketId;
    }

    public String getLabel() {
        return label;
    }

    public boolean isChecked(int index) {
        return checked[index];
    }

    public boolean[] copyChecked() {
        boolean[] copy = new boolean[BingoGrid.TOTAL];
        System.arraycopy(checked, 0, copy, 0, BingoGrid.TOTAL);
        return copy;
    }

    public int countFound() {
        int n = 0;
        for (boolean b : checked) {
            if (b) n++;
        }
        return n;
    }

    public int countNeeded() {
        return BingoGrid.RANDOM_SLOTS - (countFound() - 1);
    }

    public boolean isComplete() {
        return countFound() >= BingoGrid.TOTAL;
    }

    public long getCompletedAtMs() {
        return completedAtMs;
    }

    public void setCompletedAtMs(long completedAtMs) {
        this.completedAtMs = completedAtMs;
    }

    public boolean isCompletionAnnounced() {
        return completionAnnounced;
    }

    public void setCompletionAnnounced(boolean completionAnnounced) {
        this.completionAnnounced = completionAnnounced;
    }

    /**
     * Scant team-inventories en vinkt nieuwe vakken af. Returns true als er voortgang was.
     */
    public boolean syncFromPlayers(Iterable<Player> members, Material[] cardTiles) {
        EnumSet<Material> union = EnumSet.noneOf(Material.class);
        for (Player player : members) {
            if (player == null) continue;
            ingest(player.getInventory(), union);
        }

        boolean advanced = false;
        for (int i = 0; i < BingoGrid.TOTAL; i++) {
            if (i == BingoGrid.FREE_INDEX || checked[i]) continue;
            if (union.contains(cardTiles[i])) {
                checked[i] = true;
                advanced = true;
            }
        }
        return advanced;
    }

    private static void ingest(PlayerInventory inv, EnumSet<Material> acc) {
        for (ItemStack stack : inv.getStorageContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            acc.add(stack.getType());
        }
        for (ItemStack stack : inv.getArmorContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            acc.add(stack.getType());
        }
        ItemStack off = inv.getItemInOffHand();
        if (off != null && !off.getType().isAir()) {
            acc.add(off.getType());
        }
    }
}
