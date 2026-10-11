package com.termux.app.statusbar;

/**
 * How the status row shares its width between the window chips and the readings after them (CPU,
 * RAM, weather, the AI glyph, the mouse).
 *
 * <p>The chips run right up to the readings and stop one {@link #CHIPS_READINGS_GAP_DP} short of
 * the first reading's ink. Whatever spacing the readings already carry before their ink — a
 * container's padding, a widget's own padding — counts towards that gap, so the chips' end padding
 * is only what is left of it.
 */
public final class StatusRowFitPolicy {

    private StatusRowFitPolicy() {}

    /**
     * The one gap between the chips' last visible pixel and the first reading's ink. The same 6dp
     * the badge keeps before the chips, and the stats cluster keeps before the end widgets.
     */
    public static final float CHIPS_READINGS_GAP_DP = 6f;

    /**
     * The padding the chip strip keeps at its end so the readings' own leading inset and it add up
     * to {@code gapPx}: never negative, so a reading whose own inset is already wider than the gap
     * keeps its inset and the chips simply stop at its edge.
     *
     * @param readingsLeadPx the space between the chip strip's end and the first reading's ink,
     *     or a negative value when no reading follows the chips (the gap then belongs to the row's
     *     end, which keeps its own margin)
     */
    public static int chipsEndPaddingPx(int gapPx, int readingsLeadPx) {
        if (readingsLeadPx < 0) return 0;
        return Math.max(0, gapPx - readingsLeadPx);
    }
}
