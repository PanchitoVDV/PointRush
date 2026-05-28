package be.panchito.pointRush.minigame.bingo;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gedeelde bingo-kaart voor alle teams.
 */
public final class BingoCard {

    private final Material[] tiles;

    private BingoCard(Material[] tiles) {
        this.tiles = tiles;
    }

    public static BingoCard generate(List<Material> pool) {
        if (pool.size() < BingoGrid.RANDOM_SLOTS) {
            throw new IllegalArgumentException("Pool te klein voor bingo (min "
                    + BingoGrid.RANDOM_SLOTS + ").");
        }
        List<Material> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());

        Material[] tiles = new Material[BingoGrid.TOTAL];
        int pick = 0;
        for (int i = 0; i < BingoGrid.TOTAL; i++) {
            if (i == BingoGrid.FREE_INDEX) {
                tiles[i] = Material.LIME_WOOL;
            } else {
                tiles[i] = shuffled.get(pick++);
            }
        }
        return new BingoCard(tiles);
    }

    public Material[] copyTiles() {
        Material[] copy = new Material[BingoGrid.TOTAL];
        System.arraycopy(tiles, 0, copy, 0, BingoGrid.TOTAL);
        return copy;
    }

    public Material get(int index) {
        return tiles[index];
    }
}
