package be.panchito.pointRush.minigame.bingo;

/**
 * 4×4 bingo-rooster met 16 vakken (geen gratis vak).
 */
public final class BingoGrid {

    public static final int DIM = 4;
    public static final int TOTAL = DIM * DIM;
    /** Geen gratis vak in het 4×4 rooster. */
    public static final int FREE_INDEX = -1;
    public static final int RANDOM_SLOTS = TOTAL;

    /** GUI-slots (54-slot inventory) voor het 4×4 rooster. */
    public static final int[] GUI_SLOTS = {
            11, 12, 13, 14,
            20, 21, 22, 23,
            29, 30, 31, 32,
            38, 39, 40, 41
    };

    private BingoGrid() {
    }

    public static int row(int index) {
        return index / DIM;
    }

    public static int col(int index) {
        return index % DIM;
    }
}
