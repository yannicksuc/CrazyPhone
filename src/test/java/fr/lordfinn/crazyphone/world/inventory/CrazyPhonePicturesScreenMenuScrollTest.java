package fr.lordfinn.crazyphone.world.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers clampRowOffset in isolation - the real scrollBy()/AlbumInventoryItemHandler it backs needs a full
 * live menu (real player Inventory, real album ItemStack) to construct, which this deliberately avoids.
 */
class CrazyPhonePicturesScreenMenuScrollTest {

    // 3 columns, 3 visible rows throughout, matching the real ALBUM_COLUMNS/VISIBLE_ROWS values.

    @Test
    void scrollingUp_pastZero_clampsToZero() {
        int result = CrazyPhonePicturesScreenMenu.clampRowOffset(0, -1, 54, 3, 3);
        assertEquals(0, result);
    }

    @Test
    void scrollingDown_pastTheLastRow_clampsToMaxOffset() {
        // 54 slots / 3 columns = 18 total rows; 18 - 3 visible = 15 max offset.
        int result = CrazyPhonePicturesScreenMenu.clampRowOffset(15, 5, 54, 3, 3);
        assertEquals(15, result);
    }

    @Test
    void scrollingDown_oneRowAtATime_reachesExactlyTheMax() {
        int offset = 0;
        for (int i = 0; i < 20; i++) // deliberately over-scroll past the real max
            offset = CrazyPhonePicturesScreenMenu.clampRowOffset(offset, 1, 54, 3, 3);
        assertEquals(15, offset);
    }

    @Test
    void containerSizeNotEvenlyDivisibleByColumns_stillCountsThePartialLastRow() {
        // 10 slots / 3 columns = 4 rows (3+3+3+1), not 3 - the partial last row must still be reachable.
        // 4 total rows - 3 visible = 1 max offset.
        assertEquals(1, CrazyPhonePicturesScreenMenu.clampRowOffset(0, 100, 10, 3, 3));
    }

    @Test
    void contentThatFitsEntirelyWithinTheVisibleGrid_cannotScrollAtAll() {
        // 6 slots / 3 columns = 2 rows, which already fits inside 3 visible rows - max offset must be 0,
        // not negative (the Math.max(0, ...) clamp this test specifically exercises).
        assertEquals(0, CrazyPhonePicturesScreenMenu.clampRowOffset(0, 5, 6, 3, 3));
        assertEquals(0, CrazyPhonePicturesScreenMenu.clampRowOffset(0, -5, 6, 3, 3));
    }

    @Test
    void emptyAlbum_neverScrolls() {
        assertEquals(0, CrazyPhonePicturesScreenMenu.clampRowOffset(0, 1, 0, 3, 3));
    }

    @Test
    void scrollDelta_movesByExactlyTheRequestedAmountWhenWithinBounds() {
        int result = CrazyPhonePicturesScreenMenu.clampRowOffset(5, 2, 54, 3, 3);
        assertEquals(7, result);
    }

    @Test
    void negativeDelta_movesBackByExactlyTheRequestedAmountWhenWithinBounds() {
        int result = CrazyPhonePicturesScreenMenu.clampRowOffset(5, -2, 54, 3, 3);
        assertEquals(3, result);
    }
}
