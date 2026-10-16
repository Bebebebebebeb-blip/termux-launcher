package com.termux.app.surfaces;

import androidx.annotation.NonNull;

/**
 * The Custom row's Glass slider and the edge's Glint chip, as pure functions.
 *
 * <p>Glass runs 0 to 100, clear to frosted, and owns blur and grain together:
 * {@code blur = R(4 + 32 t^1.5)} dp and {@code grain = R(2 + 6 t)} percent, with
 * {@code t = Glass / 100} and {@code R} rounding half up. Blur grows slowly at the clear end and
 * fast at the frosted one, which is where the eye tells blur apart; grain follows in step so a
 * frosted face keeps its tooth and never reaches the sand above about 40. The shipped Looks sit on
 * the curve: Clear at {@link #CLEAR}, Tint at {@link #TINT}, Mist at {@link #MIST}. Solid's
 * blur 0 and grain 0 are off it (no frost at all, the cheapest glass), and the slider still moves
 * from there: its first step writes the curve.</p>
 *
 * <p>The storage keeps blur and grain, which every renderer reads; Glass is only how the slider
 * reads them back: the position whose pair is nearest, squared differences summed, the lower
 * position on a tie ({@link #glassFor}). Reading never writes.</p>
 */
public final class GlassControls {

    private GlassControls() {}

    /** The Glass slider's range. */
    public static final int GLASS_MIN = 0;
    public static final int GLASS_MAX = 100;

    /** The shipped Looks' Glass positions. */
    public static final int CLEAR = 20;
    public static final int TINT = 40;
    public static final int MIST = 75;

    /** The blur a Glass position writes, in dp: {@code R(4 + 32 t^1.5)}, 4 to 36. */
    public static int blurForGlass(int glass) {
        double t = clampGlass(glass) / 100d;
        return (int) Math.round(4d + 32d * Math.pow(t, 1.5d));
    }

    /** The grain a Glass position writes, in percent: {@code R(2 + 6 t)}, 2 to 8. */
    public static int grainForGlass(int glass) {
        double t = clampGlass(glass) / 100d;
        return (int) Math.round(2d + 6d * t);
    }

    /**
     * Where Glass stands for a stored blur and grain: the position whose pair is nearest, the
     * lower one on a tie. Several neighbouring positions round to one pair; where a shipped Look's
     * position is among them it wins, so a Look's stop reads as the Glass it was written from
     * (Clear 20, not 19). Which position of a tie is read never changes the pair.
     */
    public static int glassFor(int blur, int grain) {
        int best = GLASS_MIN;
        long bestError = Long.MAX_VALUE;
        for (int g = GLASS_MIN; g <= GLASS_MAX; g++) {
            long error = error(g, blur, grain);
            if (error < bestError) {
                bestError = error;
                best = g;
            }
        }
        for (int shipped : new int[] {CLEAR, TINT, MIST}) {
            if (shipped > best && error(shipped, blur, grain) == bestError) return shipped;
        }
        return best;
    }

    private static long error(int glass, int blur, int grain) {
        long db = blurForGlass(glass) - blur;
        long dg = grainForGlass(glass) - grain;
        return db * db + dg * dg;
    }

    /** Whether a stored pair is one some Glass position writes. */
    public static boolean onCurve(int blur, int grain) {
        int g = glassFor(blur, grain);
        return blurForGlass(g) == blur && grainForGlass(g) == grain;
    }

    /**
     * The pair a stored one is migrated to: itself where blur is 0 (no frost, Solid's, which
     * the curve cannot hold), else the nearest Glass position's pair.
     *
     * @return {@code {blur, grain}}
     */
    @NonNull
    public static int[] snapped(int blur, int grain) {
        if (blur <= 0) return new int[] {blur, grain};
        int g = glassFor(blur, grain);
        return new int[] {blurForGlass(g), grainForGlass(g)};
    }

    private static int clampGlass(int glass) {
        return Math.max(GLASS_MIN, Math.min(GLASS_MAX, glass));
    }

    // ---------------------------------------------------------------------------- Dispersion

    /**
     * The edge's colour split at the corners, in three steps over the stored dispersion
     * percentage: Off, Glint (Clear's 40, a fiftieth of the pull) and Prism (twice Glint, a
     * provisional value for tuning). A stored value between steps reads as the nearest, the lower
     * on a tie.
     */
    public enum Dispersion {
        OFF(0), GLINT(40), PRISM(80);

        /** What this step stores, a percentage. */
        public final int percent;

        Dispersion(int percent) {
            this.percent = percent;
        }

        /** The Edge colour menu's id for this step: the values array's entry. */
        @NonNull
        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** The step a menu id names; Off for one this build does not know. */
        @NonNull
        public static Dispersion ofId(@androidx.annotation.Nullable String id) {
            for (Dispersion step : values()) {
                if (step.id().equals(id))
                    return step;
            }
            return OFF;
        }

        /** The step a stored percentage reads as. */
        @NonNull
        public static Dispersion of(int stored) {
            Dispersion best = OFF;
            int bestError = Integer.MAX_VALUE;
            for (Dispersion step : values()) {
                int error = Math.abs(step.percent - stored);
                if (error < bestError) {
                    bestError = error;
                    best = step;
                }
            }
            return best;
        }
    }
}
