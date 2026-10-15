package com.termux.app.surfaces;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * The Edge group's three sliders over the six stored glass edge keys, as pure functions.
 *
 * <p>Each slider runs 0 to 100 and owns one group of keys, so moving it writes only its own and a
 * saved Custom never loses a value it does not touch:</p>
 * <ul>
 *   <li><b>Depth</b> owns the bend and the edge width: how thick the glass edge looks. Bend is
 *   {@code R(0.30 t)} and the width follows it, {@code R(10 + 0.6 bend)}.</li>
 *   <li><b>Highlight</b> owns the edge light and the specular. The light is the slider itself;
 *   above the middle the specular rises with it ({@code max(h - 50, 0)}), carrying the light
 *   round the corners and down the sides.</li>
 *   <li><b>Clear edge</b> owns the clarity, one to one.</li>
 * </ul>
 * <p>Dispersion is on no slider: each Look carries its own and a Custom keeps whatever it has.
 * {@code R(v)} is {@code floor(v + 0.5)}, done here in integers so a curve never lands a hair off.</p>
 *
 * <p>A slider reads the stored pair it owns back as the position whose curve output is nearest:
 * squared errors, each over the key's full range, summed; the smaller position on a tie. A pair
 * on the curve therefore reads as the smallest position that writes it. Reading never writes.
 * The shipped Looks sit on the curves (held by a test), so a Look's stop and its sliders agree.</p>
 */
public final class GlassEdgeControls {

    private GlassEdgeControls() {}

    /** Every slider's range. */
    public static final int MIN = 0;
    public static final int MAX = 100;

    private static final int BEND_RANGE =
        TERMUX_APP.MAX_FANCIER_GLASS_BEND - TERMUX_APP.MIN_FANCIER_GLASS_BEND;
    private static final int WIDTH_RANGE =
        TERMUX_APP.MAX_FANCIER_GLASS_EDGE_WIDTH - TERMUX_APP.MIN_FANCIER_GLASS_EDGE_WIDTH;
    private static final int LIGHT_RANGE =
        TERMUX_APP.MAX_FANCIER_GLASS_EDGE_LIGHT - TERMUX_APP.MIN_FANCIER_GLASS_EDGE_LIGHT;
    private static final int SPECULAR_RANGE =
        TERMUX_APP.MAX_FANCIER_GLASS_SPECULAR - TERMUX_APP.MIN_FANCIER_GLASS_SPECULAR;

    /** Where the specular starts to rise on the Highlight slider. */
    private static final int HIGHLIGHT_SPECULAR_FROM = 50;

    // ---------------------------------------------------------------------------------- Depth

    /** The bend a Depth position writes, in dp: {@code R(0.30 t)}. */
    public static int bendForDepth(int depth) {
        int t = slider(depth);
        return clamp((3 * t + 5) / 10,
            TERMUX_APP.MIN_FANCIER_GLASS_BEND, TERMUX_APP.MAX_FANCIER_GLASS_BEND);
    }

    /** The edge width a Depth position writes, in dp: {@code R(10 + 0.6 bend)}. */
    public static int widthForDepth(int depth) {
        int bend = bendForDepth(depth);
        return clamp((6 * bend + 105) / 10,
            TERMUX_APP.MIN_FANCIER_GLASS_EDGE_WIDTH, TERMUX_APP.MAX_FANCIER_GLASS_EDGE_WIDTH);
    }

    /** Where Depth stands for a stored bend and edge width. */
    public static int depthFor(int bend, int width) {
        int best = MIN;
        long bestError = Long.MAX_VALUE;
        for (int t = MIN; t <= MAX; t++) {
            // Each error over its key's range, squared and summed, in integers: scaled by both
            // ranges squared so the comparison is exact.
            long b = bend - bendForDepth(t);
            long w = width - widthForDepth(t);
            long error = b * b * WIDTH_RANGE * WIDTH_RANGE + w * w * BEND_RANGE * BEND_RANGE;
            if (error < bestError) {
                bestError = error;
                best = t;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------------------ Highlight

    /** The edge light a Highlight position writes, in percent. */
    public static int lightForHighlight(int highlight) {
        return clamp(slider(highlight),
            TERMUX_APP.MIN_FANCIER_GLASS_EDGE_LIGHT, TERMUX_APP.MAX_FANCIER_GLASS_EDGE_LIGHT);
    }

    /** The specular a Highlight position writes, in percent: none below the middle. */
    public static int specularForHighlight(int highlight) {
        return clamp(Math.max(slider(highlight) - HIGHLIGHT_SPECULAR_FROM, 0),
            TERMUX_APP.MIN_FANCIER_GLASS_SPECULAR, TERMUX_APP.MAX_FANCIER_GLASS_SPECULAR);
    }

    /** Where Highlight stands for a stored edge light and specular. */
    public static int highlightFor(int light, int specular) {
        int best = MIN;
        long bestError = Long.MAX_VALUE;
        for (int h = MIN; h <= MAX; h++) {
            long l = light - lightForHighlight(h);
            long s = specular - specularForHighlight(h);
            long error = l * l * SPECULAR_RANGE * SPECULAR_RANGE
                + s * s * LIGHT_RANGE * LIGHT_RANGE;
            if (error < bestError) {
                bestError = error;
                best = h;
            }
        }
        return best;
    }

    // ----------------------------------------------------------------------------- Clear edge

    /** The clarity a Clear edge position writes, in percent. */
    public static int clarityForClearEdge(int clearEdge) {
        return clamp(slider(clearEdge),
            TERMUX_APP.MIN_FANCIER_GLASS_CLARITY, TERMUX_APP.MAX_FANCIER_GLASS_CLARITY);
    }

    /** Where Clear edge stands for a stored clarity. */
    public static int clearEdgeFor(int clarity) {
        return slider(clarity);
    }

    // ---------------------------------------------------------------------------- the six keys

    /**
     * The six stored edge values, read once; {@code with...} gives the edge one slider writes,
     * every key outside that slider's group as it was.
     */
    public static final class Edge {
        public final int bend;
        public final int width;
        public final int light;
        public final int specular;
        public final int dispersion;
        public final int clarity;

        public Edge(int bend, int width, int light, int specular, int dispersion, int clarity) {
            this.bend = bend;
            this.width = width;
            this.light = light;
            this.specular = specular;
            this.dispersion = dispersion;
            this.clarity = clarity;
        }

        public int depth() {
            return depthFor(bend, width);
        }

        public int highlight() {
            return highlightFor(light, specular);
        }

        public int clearEdge() {
            return clearEdgeFor(clarity);
        }

        @NonNull
        public Edge withDepth(int depth) {
            return new Edge(bendForDepth(depth), widthForDepth(depth), light, specular,
                dispersion, clarity);
        }

        @NonNull
        public Edge withHighlight(int highlight) {
            return new Edge(bend, width, lightForHighlight(highlight),
                specularForHighlight(highlight), dispersion, clarity);
        }

        @NonNull
        public Edge withClearEdge(int clearEdge) {
            return new Edge(bend, width, light, specular, dispersion,
                clarityForClearEdge(clearEdge));
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Edge))
                return false;
            Edge edge = (Edge) other;
            return bend == edge.bend && width == edge.width && light == edge.light
                && specular == edge.specular && dispersion == edge.dispersion
                && clarity == edge.clarity;
        }

        @Override
        public int hashCode() {
            int hash = bend;
            hash = 31 * hash + width;
            hash = 31 * hash + light;
            hash = 31 * hash + specular;
            hash = 31 * hash + dispersion;
            return 31 * hash + clarity;
        }

        @NonNull
        @Override
        public String toString() {
            return bend + "/" + width + "/" + light + "/" + specular + "/" + dispersion + "/"
                + clarity;
        }
    }

    private static int slider(int value) {
        return clamp(value, MIN, MAX);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
