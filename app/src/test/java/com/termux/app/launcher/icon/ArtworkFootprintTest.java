package com.termux.app.launcher.icon;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pack artwork is measured once and drawn so its visible part spans what a stock icon's does. The
 * measure must ignore a pack's transparent margin and its soft shadow, and the placement must keep
 * the visible part centred and inside the box.
 */
public class ArtworkFootprintTest {

    private static final int OPAQUE_WHITE = 0xFFFFFFFF;

    /** A square of {@code size} pixels with an opaque block over [left, right) x [top, bottom). */
    private static int[] square(int size, int left, int top, int right, int bottom) {
        int[] pixels = new int[size * size];
        for (int y = top; y < bottom; y++)
            for (int x = left; x < right; x++)
                pixels[y * size + x] = OPAQUE_WHITE;
        return pixels;
    }

    @Test
    public void theVisibleBoxLeavesTheTransparentMarginOut() {
        int[] pixels = new int[12 * 10];
        for (int y = 3; y < 8; y++)
            for (int x = 2; x < 9; x++)
                pixels[y * 12 + x] = OPAQUE_WHITE;
        assertArrayEquals(new int[] {2, 3, 9, 8}, ArtworkFootprint.visibleBounds(pixels, 12, 10));
    }

    @Test
    public void aSoftShadowIsNotPartOfTheVisibleBox() {
        int[] pixels = square(10, 4, 3, 8, 7);
        pixels[1 * 10 + 1] = 0x20000000;
        assertArrayEquals(new int[] {4, 3, 8, 7}, ArtworkFootprint.visibleBounds(pixels, 10, 10));
    }

    @Test
    public void nothingVisibleHasNoBox() {
        assertNull(ArtworkFootprint.visibleBounds(new int[16], 4, 4));
        assertNull(ArtworkFootprint.visibleBounds(new int[3], 4, 4));
    }

    @Test
    public void theExtentIsTheLongerSidesShare() {
        assertEquals(0.8f, ArtworkFootprint.extent(new int[] {10, 20, 90, 70}, 100, 100), 1e-6f);
        assertEquals(1f, ArtworkFootprint.extent(new int[] {0, 0, 100, 100}, 100, 100), 0f);
    }

    /** The reported case: a pack icon with a 10% margin on every side, drawn beside stock icons. */
    @Test
    public void aPackIconWithAMarginIsDrawnToFillTheStockFootprint() {
        int[] bounds = ArtworkFootprint.visibleBounds(square(100, 10, 10, 90, 90), 100, 100);
        assertNotNull(bounds);
        float[] place = ArtworkFootprint.placement(bounds, 100, 100, 1f);
        assertNotNull(place);
        // Drawn 1.25x, so the margin falls outside the box and the artwork reaches its edges.
        assertEquals(-0.125f, place[0], 1e-6f);
        assertEquals(-0.125f, place[1], 1e-6f);
        assertEquals(1.125f, place[2], 1e-6f);
        assertEquals(1.125f, place[3], 1e-6f);
        float scale = place[2] - place[0];
        assertEquals("the visible part spans the footprint", 1f,
            (bounds[2] - bounds[0]) / 100f * scale, 1e-5f);
    }

    @Test
    public void theVisiblePartIsCentredAndStaysInsideTheBox() {
        // Off centre and taller than wide: the taller side fills, and the middle comes to the middle.
        int[] bounds = {20, 5, 60, 75};
        for (float target : new float[] {1f, 0.92f, 0.6f}) {
            float[] place = ArtworkFootprint.placement(bounds, 100, 100, target);
            assertNotNull(place);
            float scale = place[2] - place[0];
            float left = place[0] + bounds[0] / 100f * scale;
            float right = place[0] + bounds[2] / 100f * scale;
            float top = place[1] + bounds[1] / 100f * scale;
            float bottom = place[1] + bounds[3] / 100f * scale;
            assertEquals(target, bottom - top, 1e-5f);
            assertEquals(0.5f, (left + right) / 2f, 1e-5f);
            assertEquals(0.5f, (top + bottom) / 2f, 1e-5f);
            assertTrue(left >= 0f && right <= 1f && top >= -1e-6f && bottom <= 1f + 1e-6f);
        }
    }

    /** Artwork larger than the footprint is brought down to it, not left over the stock size. */
    @Test
    public void artworkPastTheFootprintIsDrawnSmaller() {
        float[] place = ArtworkFootprint.placement(new int[] {0, 0, 100, 100}, 100, 100, 0.9f);
        assertNotNull(place);
        assertEquals(0.9f, place[2] - place[0], 1e-6f);
        assertEquals(0.05f, place[0], 1e-6f);
    }

    /** Artwork already at the footprint is handed back untouched: nothing is redrawn for it. */
    @Test
    public void artworkAlreadyAtTheFootprintIsLeftAlone() {
        assertNull(ArtworkFootprint.placement(new int[] {0, 0, 96, 96}, 96, 96, 1f));
        // Under one measured pixel of difference is not worth a redraw either.
        assertNull(ArtworkFootprint.placement(new int[] {0, 0, 96, 96}, 96, 96, 0.995f));
        assertNull(ArtworkFootprint.placement(new int[] {0, 0, 96, 96}, 96, 96, 0f));
    }
}
