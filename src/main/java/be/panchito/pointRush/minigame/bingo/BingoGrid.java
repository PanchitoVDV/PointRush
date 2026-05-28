package be.panchito.pointRush.minigame.bingo;

/**
 * 5×5 bingo-rooster; middenvak is gratis.
 */
public final class BingoGrid {

    public static final int DIM = 5;
    public static final int TOTAL = DIM * DIM;
    public static final int FREE_INDEX = 12;
    public static final int RANDOM_SLOTS = TOTAL - 1;

    /** GUI-slots (54-slot inventory) voor het 5×5 rooster. */
    public static final int[] GUI_SLOTS = {
            10, 11, 12, 13, 14,
            19, 20, 21, 22, 23,
            28, 29, 30, 31, 32,
            37, 38, 39, 40, 41,
            46, 47, 48, 49, 50
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
