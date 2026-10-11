package com.termux.app.statusbar;

/**
 * Geometry of the progress ring a working window's pill draws where its process glyph normally sits
 * — the way Windows Terminal swaps a tab's icon for a progress ring while the shell in it reports
 * progress. Indeterminate work is an arc that turns; a reported percentage is a ring that fills
 * clockwise from twelve o'clock over a faint track.
 *
 * <p>Free of Android imports, so every angle here is unit-testable.
 */
public final class WindowActivityRing {

    /** One full turn of the indeterminate arc. Quick enough to read as working, not as a clock. */
    public static final long SPIN_MS = 1280L;

    /**
     * The indeterminate arc, in degrees. Open enough that the turn is visible at glyph size, closed
     * enough that the shape still reads as a ring rather than a comma.
     */
    public static final float INDETERMINATE_SWEEP_DEG = 270f;

    /** Twelve o'clock, in {@code Canvas.drawArc} degrees (which count clockwise from three). */
    public static final float START_DEG = -90f;

    private WindowActivityRing() {}

    /** Position in the turn, in [0, 1). */
    public static float phase(long elapsedMs) {
        float turns = elapsedMs / (float) SPIN_MS;
        float phase = turns - (float) Math.floor(turns);
        return phase < 0f ? phase + 1f : phase;
    }

    /**
     * How many positions the smooth arc visits per turn, and how often it moves. The arc used to be
     * driven one step per vsync, which on a 120 Hz panel is 154 redraws of every working pill per
     * turn for a ring a few pixels wide; 38 stops is past the point where a thin arc reads as
     * gliding, and lands the tick at about 30 a second. The angle itself stays a pure function of
     * elapsed time, so a slower tick changes how many positions the arc visits, never its speed.
     */
    public static final int SMOOTH_STEPS = 38;
    public static final long SMOOTH_TICK_MS = SPIN_MS / SMOOTH_STEPS;

    /** Where the indeterminate arc starts at {@code phase}: one full clockwise turn per cycle. */
    public static float indeterminateStartDeg(float phase) {
        return START_DEG + 360f * phase;
    }

    // TEMP ring-style A/B: everything from here to determinateSweepDeg exists to compare two
    // cheaper indeterminate rings against the smooth arc on the device; the loser goes in one grep.

    /**
     * How the indeterminate ring moves. The smooth arc redraws every working chip about 30 times a
     * second for as long as a shell works, which on the device is close to half a core. Both other
     * styles are built so that a low tick rate is the look rather than a stutter in it.
     */
    public enum Style {
        /** The arc gliding round the outline, on the 33 ms tick. The default. */
        SMOOTH,
        /** One lit dash stepping round the outline with a fading tail, on an 8 Hz tick. */
        CHASER,
        /** One quick lap of the smooth arc, then a long still rest with no ticks at all. */
        HEARTBEAT;

        /** The {@code debug.termux.ring_style} value; anything unknown or empty is smooth. */
        public static Style parse(String value) {
            if ("chaser".equals(value)) return CHASER;
            if ("heartbeat".equals(value)) return HEARTBEAT;
            return SMOOTH;
        }
    }

    /**
     * The chaser's dashes and how long each one stays lit. Ten dashes at 8 Hz is a lap in 1.25 s,
     * close to {@link #SPIN_MS}, so the chaser reads at the speed the arc did. A stepping dash is a
     * discrete motion by design, so eight positions a second reads as a chase, not a dropped frame.
     */
    public static final int CHASER_DASHES = 10;
    public static final long CHASER_STEP_MS = 125L;
    /** The gap after each dash, as a share of a dash: enough that every dash reads as its own. */
    public static final float CHASER_GAP_OF_DASH = 0.3f;
    /** The lit dash and the two behind it, as shares of the ring's alpha; the rest draw nothing. */
    private static final float[] CHASER_TRAIL = {1f, 0.55f, 0.25f};
    public static final int CHASER_TRAIL_LENGTH = CHASER_TRAIL.length;

    /**
     * The heartbeat: one lap of the smooth arc in {@link #HEARTBEAT_PASS_MS}, then the resting
     * outline for the rest of the cycle. The lap is quick enough to read as a pulse; the rest is
     * where the saving is, since nothing ticks or redraws during it.
     */
    public static final long HEARTBEAT_PASS_MS = 600L;
    public static final long HEARTBEAT_CYCLE_MS = 3000L;
    /** What {@link #heartbeatPassPhase} answers while the ring rests between passes. */
    public static final float HEARTBEAT_RESTING = -1f;

    /** Which chaser dash is lit at {@code nowMs}: dash 0 starts where the smooth arc does. */
    public static int chaserLitDash(long nowMs) {
        return (int) floorMod(floorDiv(nowMs, CHASER_STEP_MS), CHASER_DASHES);
    }

    /** How bright a dash {@code stepsBehind} the lit one draws, as a share of the ring's alpha. */
    public static float chaserTrailAlpha(int stepsBehind) {
        return stepsBehind >= 0 && stepsBehind < CHASER_TRAIL.length ? CHASER_TRAIL[stepsBehind] : 0f;
    }

    /** Where dash {@code index} starts, as a fraction of the way round the outline. */
    public static float chaserDashStartFraction(int index) {
        return (float) floorMod(index, CHASER_DASHES) / CHASER_DASHES;
    }

    /** How long each dash runs, as a fraction of the outline: its slot less the gap after it. */
    public static float chaserDashLengthFraction() {
        return 1f / CHASER_DASHES / (1f + CHASER_GAP_OF_DASH);
    }

    /**
     * Position in the heartbeat's lap at {@code nowMs}, in [0, 1), or {@link #HEARTBEAT_RESTING}
     * between laps. Read through the same arc geometry as {@link #phase}.
     */
    public static float heartbeatPassPhase(long nowMs) {
        long inCycle = floorMod(nowMs, HEARTBEAT_CYCLE_MS);
        if (inCycle >= HEARTBEAT_PASS_MS) return HEARTBEAT_RESTING;
        return inCycle / (float) HEARTBEAT_PASS_MS;
    }

    /**
     * How long the bar's clock waits before its next redraw of the turning rings. The angle is
     * always a pure function of time, so this only decides when a redraw is worth having: every
     * 33 ms for the smooth arc, on the next step boundary for the chaser, and for the heartbeat
     * every 33 ms through the lap — with the last tick landing exactly on the rest, so the arc is
     * cleared on time — then once, at the start of the next lap. Never 0.
     */
    public static long nextTickDelayMs(Style style, long nowMs) {
        switch (style) {
            case CHASER:
                return CHASER_STEP_MS - floorMod(nowMs, CHASER_STEP_MS);
            case HEARTBEAT: {
                long inCycle = floorMod(nowMs, HEARTBEAT_CYCLE_MS);
                if (inCycle < HEARTBEAT_PASS_MS) {
                    return Math.min(SMOOTH_TICK_MS, HEARTBEAT_PASS_MS - inCycle);
                }
                return HEARTBEAT_CYCLE_MS - inCycle;
            }
            case SMOOTH:
            default:
                return SMOOTH_TICK_MS;
        }
    }

    private static long floorDiv(long value, long divisor) {
        long quotient = value / divisor;
        return (value % divisor != 0 && (value < 0) != (divisor < 0)) ? quotient - 1 : quotient;
    }

    private static long floorMod(long value, long divisor) {
        return value - floorDiv(value, divisor) * divisor;
    }

    /**
     * How much of the ring a reported percentage fills. Clamped: a shell that reports 140% has a bug,
     * and the ring should not wrap around and look nearly empty because of it.
     */
    public static float determinateSweepDeg(int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        return 360f * clamped / 100f;
    }
}
