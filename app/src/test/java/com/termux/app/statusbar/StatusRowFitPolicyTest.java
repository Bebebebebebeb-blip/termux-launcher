package com.termux.app.statusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StatusRowFitPolicyTest {

    @Test public void theChipsKeepWhatIsLeftOfTheGapAfterTheReadingsOwnInset() {
        // A CPU figure with 5px of padding before its icon: the chips keep 1px, 6 in all.
        assertEquals(1, StatusRowFitPolicy.chipsEndPaddingPx(6, 5));
        assertEquals(6, StatusRowFitPolicy.chipsEndPaddingPx(6, 0));
    }

    @Test public void aReadingAlreadyWiderThanTheGapIsNotPushedFurther() {
        assertEquals(0, StatusRowFitPolicy.chipsEndPaddingPx(6, 8));
    }

    @Test public void noReadingMeansNoGapOfTheChipsOwn() {
        assertEquals(0, StatusRowFitPolicy.chipsEndPaddingPx(6, -1));
    }
}
