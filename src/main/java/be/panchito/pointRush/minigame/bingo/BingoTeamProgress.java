package be.panchito.pointRush.minigame.bingo;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Voortgang van één team (of solo-speler) op de gedeelde kaart.
 */
public final class BingoTeamProgress {

    private final UUID bucketId;
    private final String label;
    private final boolean[] checked = new boolean[BingoGrid.TOTAL];
    /**
     * Aantal van elk materiaal dat het team al bezat bij de start. Een vak telt pas af zodra het team
     * méér van dat materiaal heeft dan dit basisaantal — zo telt loot van vóór het event niet mee.
     */
    private final Map<Material, Integer> baselineCounts = new EnumMap<>(Material.class);
    private long completedAtMs = 0L;
    private boolean completionAnnounced = false;

    public BingoTeamProgress(UUID bucketId, String label) {
        this.bucketId = bucketId;
        this.label = label;
        if (BingoGrid.FREE_INDEX >= 0) {
            checked[BingoGrid.FREE_INDEX] = true;
        }
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
        return BingoGrid.TOTAL - countFound();
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
     * Legt vast hoeveel van elk materiaal deze speler al bezit; opgeteld vormt dit het basisaantal van
     * het team. Aanroepen bij het toetreden, vóór er gesynchroniseerd wordt — zo telt bestaande loot
     * niet mee op de kaart.
     */
    public void addBaseline(Player player) {
        if (player == null) return;
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        ingest(player.getInventory(), counts);
        for (Map.Entry<Material, Integer> e : counts.entrySet()) {
            baselineCounts.merge(e.getKey(), e.getValue(), Integer::sum);
        }
    }

    /**
     * Scant team-inventories en vinkt nieuwe vakken af. Een vak telt pas wanneer het team méér van het
     * materiaal heeft dan bij de start (basisaantal). Returns true als er voortgang was.
     */
    public boolean syncFromPlayers(Iterable<Player> members, Material[] cardTiles) {
        Map<Material, Integer> current = new EnumMap<>(Material.class);
        for (Player player : members) {
            if (player == null) continue;
            ingest(player.getInventory(), current);
        }

        boolean advanced = false;
        for (int i = 0; i < BingoGrid.TOTAL; i++) {
            if (i == BingoGrid.FREE_INDEX || checked[i]) continue;
            Material need = cardTiles[i];
            int have = current.getOrDefault(need, 0);
            int baseline = baselineCounts.getOrDefault(need, 0);
            if (have > baseline) {
                checked[i] = true;
                advanced = true;
            }
        }
        return advanced;
    }

    private static void ingest(PlayerInventory inv, Map<Material, Integer> acc) {
        for (ItemStack stack : inv.getStorageContents()) {
            add(acc, stack);
        }
        for (ItemStack stack : inv.getArmorContents()) {
            add(acc, stack);
        }
        add(acc, inv.getItemInOffHand());
    }

    private static void add(Map<Material, Integer> acc, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return;
        acc.merge(stack.getType(), stack.getAmount(), Integer::sum);
    }
}
