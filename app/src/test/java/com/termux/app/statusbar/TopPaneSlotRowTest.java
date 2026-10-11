package com.termux.app.statusbar;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Where the clock stands along the bar, alone and beside the media row or the pinned cards.
 *
 * <p>Alone, the widget centres inside its own bounds, so a centred clock is only centred on the
 * bar if the cell is — and the cell used to run from the place icon to the gutter, putting
 * "centre" 26dp toward the trailing edge. Shared, the clock used to stand at the leading edge
 * whatever the alignment said.</p>
 */
public class TopPaneSlotRowTest {

    private static final String LEFT = TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_LEFT;
    private static final String CENTER = TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_CENTER;
    private static final String RIGHT = TermuxPreferenceConstants.TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_RIGHT;

    private static final int WIDTH = 1080;
    private static final int LEADING = 192; // 64dp at 3x
    private static final int GUTTER = 36;   // 12dp at 3x
    private static final int GAP = 36;      // 12dp at 3x
    private static final int SIDE_MIN = 360; // 120dp at 3x
    private static final int CLOCK = 400;

    // ---- Alone ----------------------------------------------------------------

    @Test
    public void aloneLeftRunsFromThePlaceIconToTheGutter() {
        TopPaneSlotRow row = TopPaneSlotRow.alone(WIDTH, LEADING, GUTTER, LEFT);
        assertEquals(LEADING, row.clockStart);
        assertEquals(WIDTH - GUTTER, row.clockEnd);
        assertEquals("nothing shares the slot", 0, row.sideWidth());
    }

    @Test
    public void aloneRightRunsFromThePlaceIconToTheGutter() {
        TopPaneSlotRow row = TopPaneSlotRow.alone(WIDTH, LEADING, GUTTER, RIGHT);
        assertEquals(LEADING, row.clockStart);
        assertEquals(WIDTH - GUTTER, row.clockEnd);
    }

    @Test
    public void aloneCentreIsSymmetricAboutTheBar() {
        TopPaneSlotRow row = TopPaneSlotRow.alone(WIDTH, LEADING, GUTTER, CENTER);
        assertEquals("the cell's centre must be the bar's centre",
            WIDTH / 2f, (row.clockStart + row.clockEnd) / 2f, .5f);
        assertEquals("the cell keeps clear of the place icon", LEADING, row.clockStart);
    }

    @Test
    public void aloneCentreFollowsWiderLeadingIcons() {
        int wider = LEADING + 120;
        TopPaneSlotRow row = TopPaneSlotRow.alone(WIDTH, wider, GUTTER, CENTER);
        assertEquals(wider, row.clockStart);
        assertEquals(WIDTH - wider, row.clockEnd);
    }

    @Test
    public void unknownAndNullFallBackToLeft() {
        TopPaneSlotRow left = TopPaneSlotRow.alone(WIDTH, LEADING, GUTTER, LEFT);
        for (String alignment : new String[] {null, "middle"}) {
            TopPaneSlotRow alone = TopPaneSlotRow.alone(WIDTH, LEADING, GUTTER, alignment);
            assertEquals(left.clockStart, alone.clockStart);
            assertEquals(left.clockEnd, alone.clockEnd);
            TopPaneSlotRow shared = TopPaneSlotRow.shared(WIDTH, LEADING, GUTTER, GAP, CLOCK,
                SIDE_MIN, alignment);
            assertEquals(LEADING, shared.clockStart);
        }
    }

    @Test
    public void aBarNarrowerThanItsInsetsYieldsAnEmptyCellNotANegativeOne() {
        TopPaneSlotRow row = TopPaneSlotRow.alone(300, LEADING, GUTTER, CENTER);
        assertEquals(0, row.clockWidth());
    }

    // ---- Shared ---------------------------------------------------------------

    @Test
    public void sharedLeftIsTheLayoutTheMediaAndCardFixesWereMadeAgainst() {
        TopPaneSlotRow row = TopPaneSlotRow.shared(WIDTH, LEADING, GUTTER, GAP, CLOCK, SIDE_MIN,
            LEFT);
        assertEquals(LEADING, row.clockStart);
        assertEquals(LEADING + CLOCK, row.clockEnd);
        assertEquals(LEADING + CLOCK + GAP, row.sideStart);
        assertEquals("the side content still ends at the gutter, where the media row's "
            + "end inset is measured from", WIDTH - GUTTER, row.sideEnd);
    }

    @Test
    public void sharedRightSetsTheClockAgainstTheGutterAndTheContentBeforeIt() {
        TopPaneSlotRow row = TopPaneSlotRow.shared(WIDTH, LEADING, GUTTER, GAP, CLOCK, SIDE_MIN,
            RIGHT);
        assertEquals(WIDTH - GUTTER, row.clockEnd);
        assertEquals(CLOCK, row.clockWidth());
        assertEquals(LEADING, row.sideStart);
        assertEquals(row.clockStart - GAP, row.sideEnd);
    }

    @Test
    public void sharedCentreSitsOnTheBarsCentreWhenTheContentHasRoom() {
        int wide = 2400;
        TopPaneSlotRow row = TopPaneSlotRow.shared(wide, LEADING, GUTTER, GAP, CLOCK, SIDE_MIN,
            CENTER);
        assertEquals(wide / 2f, (row.clockStart + row.clockEnd) / 2f, .5f);
        assertEquals(row.clockEnd + GAP, row.sideStart);
        assertEquals(wide - GUTTER, row.sideEnd);
    }

    @Test
    public void sharedCentreStopsWhereTheContentWouldFallBelowItsLeast() {
        TopPaneSlotRow row = TopPaneSlotRow.shared(WIDTH, LEADING, GUTTER, GAP, CLOCK, SIDE_MIN,
            CENTER);
        assertEquals(SIDE_MIN, row.sideWidth());
        assertEquals(WIDTH - GUTTER, row.sideEnd);
        assertTrue("still nearer the middle than Left",
            row.clockStart > LEADING);
    }

    @Test
    public void sharedCentreNeverCrossesThePlaceIcon() {
        // The content's least already eats the slack: centre falls back to Left's position.
        int clock = WIDTH - GUTTER - LEADING - GAP - SIDE_MIN + 10;
        TopPaneSlotRow row = TopPaneSlotRow.shared(WIDTH, LEADING, GUTTER, GAP, clock, SIDE_MIN,
            CENTER);
        assertEquals(LEADING, row.clockStart);
    }

    @Test
    public void sharedClockWiderThanTheRunIsCappedAndLeavesNoNegativeRun() {
        TopPaneSlotRow row = TopPaneSlotRow.shared(WIDTH, LEADING, GUTTER, GAP, WIDTH * 2,
            SIDE_MIN, RIGHT);
        assertEquals(WIDTH - GUTTER - LEADING, row.clockWidth());
        assertEquals(0, row.sideWidth());
    }
}
