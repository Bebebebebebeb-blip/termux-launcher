package com.termux.app.surfaces;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The Edge group's Depth, Highlight and Clear edge over the six stored edge keys. */
public class GlassEdgeControlsTest {

    private static SurfacePresets.Preset preset(String id) {
        for (SurfacePresets.Preset preset : SurfacePresets.presets())
            if (preset.id.equals(id)) return preset;
        throw new AssertionError(id);
    }

    private static GlassEdgeControls.Edge edgeOf(SurfacePresets.Preset preset) {
        Map<String, Object> v = preset.values;
        return new GlassEdgeControls.Edge((Integer) v.get(TERMUX_APP.KEY_FANCIER_GLASS_BEND),
            (Integer) v.get(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH),
            (Integer) v.get(TERMUX_APP.KEY_FANCIER_GLASS_EDGE_LIGHT),
            (Integer) v.get(TERMUX_APP.KEY_FANCIER_GLASS_SPECULAR),
            (Integer) v.get(TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION),
            (Integer) v.get(TERMUX_APP.KEY_FANCIER_GLASS_CLARITY));
    }

    @Test
    public void everyGlassLookSitsOnTheCurvesAndRoundTrips() {
        String[] ids = {"minimal", "frost", "stock"};
        // The smallest position on each Look's plateau: Clear 85..88, Mist 79..81, Tint 65..68.
        int[] depth = {85, 79, 65};
        int[] highlight = {30, 28, 25};
        int[] clearEdge = {100, 90, 85};
        for (int i = 0; i < ids.length; i++) {
            GlassEdgeControls.Edge edge = edgeOf(preset(ids[i]));
            assertEquals(ids[i], depth[i], edge.depth());
            assertEquals(ids[i], highlight[i], edge.highlight());
            assertEquals(ids[i], clearEdge[i], edge.clearEdge());
            // Each slider written back at its reading reproduces the Look exactly.
            assertEquals(ids[i], edge, edge.withDepth(edge.depth()));
            assertEquals(ids[i], edge, edge.withHighlight(edge.highlight()));
            assertEquals(ids[i], edge, edge.withClearEdge(edge.clearEdge()));
        }
    }

    @Test
    public void depthWritesTheBendAndAWidthThatFollowsIt() {
        assertEquals(0, GlassEdgeControls.bendForDepth(0));
        assertEquals(10, GlassEdgeControls.widthForDepth(0));
        assertEquals(30, GlassEdgeControls.bendForDepth(100));
        assertEquals(28, GlassEdgeControls.widthForDepth(100));
        // R(0.30 * 85) = R(25.5) rounds up, in integers.
        assertEquals(26, GlassEdgeControls.bendForDepth(85));
        assertEquals(25, GlassEdgeControls.bendForDepth(84));
        assertEquals(26, GlassEdgeControls.widthForDepth(85));
        assertEquals(24, GlassEdgeControls.widthForDepth(80));
        assertEquals(22, GlassEdgeControls.widthForDepth(66));
        for (int t = 1; t <= 100; t++) {
            assertTrue(GlassEdgeControls.bendForDepth(t) >= GlassEdgeControls.bendForDepth(t - 1));
            assertTrue(GlassEdgeControls.widthForDepth(t)
                >= GlassEdgeControls.widthForDepth(t - 1));
        }
    }

    @Test
    public void highlightBrightensTheTopFirstThenCarriesTheLightRound() {
        assertEquals(0, GlassEdgeControls.lightForHighlight(0));
        assertEquals(0, GlassEdgeControls.specularForHighlight(0));
        assertEquals(50, GlassEdgeControls.lightForHighlight(50));
        assertEquals(0, GlassEdgeControls.specularForHighlight(50));
        assertEquals(51, GlassEdgeControls.lightForHighlight(51));
        assertEquals(1, GlassEdgeControls.specularForHighlight(51));
        assertEquals(100, GlassEdgeControls.lightForHighlight(100));
        assertEquals(50, GlassEdgeControls.specularForHighlight(100));
        for (int h = 0; h <= 100; h++)
            assertEquals(h, GlassEdgeControls.highlightFor(GlassEdgeControls.lightForHighlight(h),
                GlassEdgeControls.specularForHighlight(h)));
    }

    @Test
    public void slidersClampOutsideTheirRange() {
        assertEquals(GlassEdgeControls.bendForDepth(0), GlassEdgeControls.bendForDepth(-20));
        assertEquals(GlassEdgeControls.bendForDepth(100), GlassEdgeControls.bendForDepth(150));
        assertEquals(GlassEdgeControls.widthForDepth(100), GlassEdgeControls.widthForDepth(150));
        assertEquals(0, GlassEdgeControls.lightForHighlight(-1));
        assertEquals(50, GlassEdgeControls.specularForHighlight(400));
        assertEquals(0, GlassEdgeControls.clarityForClearEdge(-5));
        assertEquals(100, GlassEdgeControls.clarityForClearEdge(101));
        assertEquals(100, GlassEdgeControls.clearEdgeFor(140));
        assertEquals(0, GlassEdgeControls.clearEdgeFor(-3));
        // A stored edge past every curve output reads at the end it is nearest: 30/28, first
        // written at 99.
        assertEquals(99, GlassEdgeControls.depthFor(30, 60));
        assertEquals(0, GlassEdgeControls.depthFor(0, 1));
    }

    /** A pair on the curve reads as the smallest position that writes it, and writes it back. */
    @Test
    public void anOnCurvePairReadsAsTheSmallestPositionThatWritesIt() {
        for (int t = 0; t <= 100; t++) {
            int bend = GlassEdgeControls.bendForDepth(t);
            int width = GlassEdgeControls.widthForDepth(t);
            int read = GlassEdgeControls.depthFor(bend, width);
            assertTrue(t + " reads " + read, read <= t);
            assertEquals(bend, GlassEdgeControls.bendForDepth(read));
            assertEquals(width, GlassEdgeControls.widthForDepth(read));
            if (read > 0)
                assertTrue(GlassEdgeControls.bendForDepth(read - 1) != bend
                    || GlassEdgeControls.widthForDepth(read - 1) != width);
        }
    }

    @Test
    public void aTieBetweenTwoPositionsTakesTheSmaller() {
        // Light 60 and specular 11 is one step off h = 60 (60/10) and h = 61 (61/11) alike.
        assertEquals(60, GlassEdgeControls.highlightFor(60, 11));
    }

    @Test
    public void anOffCurveCustomReadsAtTheNearestPosition() {
        // Mist's old 24/30: bend 25 / width 25 (t 82) is nearer, over the keys' ranges, than
        // 24/24 or 26/26.
        assertEquals(82, GlassEdgeControls.depthFor(24, 30));
        // Tint's old 20/26: 21/23 (t 69) is nearest.
        assertEquals(69, GlassEdgeControls.depthFor(20, 26));
        // A strong light with no specular sits between the halves: 60/10 is nearest to 70/0.
        assertEquals(60, GlassEdgeControls.highlightFor(70, 0));
        assertEquals(42, GlassEdgeControls.clearEdgeFor(42));
    }

    @Test
    public void movingOneSliderLeavesTheOtherGroupsKeysAlone() {
        GlassEdgeControls.Edge custom = new GlassEdgeControls.Edge(7, 40, 60, 13, 22, 55);

        GlassEdgeControls.Edge depth = custom.withDepth(50);
        assertEquals(GlassEdgeControls.bendForDepth(50), depth.bend);
        assertEquals(GlassEdgeControls.widthForDepth(50), depth.width);
        assertEquals(new GlassEdgeControls.Edge(depth.bend, depth.width, 60, 13, 22, 55), depth);

        GlassEdgeControls.Edge highlight = custom.withHighlight(75);
        assertEquals(new GlassEdgeControls.Edge(7, 40, 75, 25, 22, 55), highlight);

        GlassEdgeControls.Edge clearEdge = custom.withClearEdge(10);
        assertEquals(new GlassEdgeControls.Edge(7, 40, 60, 13, 22, 10), clearEdge);
    }

    @Test
    public void aRememberedPositionOnTheSameStretchReadsBackWhereItWasLeft() {
        // 87 writes Clear's 26/26, as 85 (the stretch's first position) does.
        GlassEdgeControls.Edge clear = new GlassEdgeControls.Edge(26, 26, 30, 0, 35, 100)
            .withDepth(87);
        assertEquals(85, clear.depth());
        assertEquals(87, clear.depth(87));
        assertEquals(85, clear.depth(GlassEdgeControls.NONE));
        // A remembered position that no longer writes the stored edge (a Look, an Undo, another
        // writer) is ignored: the nearest position reads as before.
        assertEquals(85, clear.depth(84));
        assertEquals(85, clear.depth(150));
        assertEquals(GlassEdgeControls.depthFor(24, 30),
            new GlassEdgeControls.Edge(24, 30, 28, 0, 30, 90).depth(80));

        // Highlight is one to one below the middle, so only an equal position is kept.
        GlassEdgeControls.Edge lit = clear.withHighlight(70);
        assertEquals(70, lit.highlight(70));
        assertEquals(70, lit.highlight(69));
        assertEquals(70, lit.highlight(GlassEdgeControls.NONE));
    }

    @Test
    public void readingAnOldCustomChangesNothing() {
        GlassEdgeControls.Edge custom = new GlassEdgeControls.Edge(13, 32, 60, 20, 20, 90);
        custom.depth();
        custom.highlight();
        custom.clearEdge();
        assertEquals(new GlassEdgeControls.Edge(13, 32, 60, 20, 20, 90), custom);
    }
}
