package com.termux.app.launcher.icon;

import androidx.annotation.Nullable;

/**
 * Where an icon's visible artwork sits inside its square, and where to draw the whole of it so
 * that part fills a given footprint. Pure arithmetic over ARGB pixels, so it is tested without a
 * device; {@link DrawablePixels#fillFootprint} rasterises and applies the answer.
 *
 * <p>Icon-pack PNGs usually carry a transparent margin of their own, and the dock draws every
 * source into the same box: a pack icon came out smaller than the stock adaptive icon beside it,
 * whose mask reaches the edges of its box. Measuring the visible part once, when the artwork is
 * resolved, lets the pack icon be drawn as big as the stock one at every size.
 */
public final class ArtworkFootprint {

    /**
     * A pixel counts as artwork from this share of the icon's strongest alpha up, which leaves a
     * soft drop shadow out of the measure while keeping the antialiased edge of the shape.
     */
    static final float VISIBLE_ALPHA_FRACTION = 0.25f;
    /** And never from below this alpha, so an icon drawn wholly faint still has an edge. */
    static final int MIN_VISIBLE_ALPHA = 8;

    private ArtworkFootprint() {
    }

    /**
     * The visible box of {@code width} x {@code height} ARGB pixels as {@code {left, top, right,
     * bottom}}, right and bottom exclusive, or null when nothing in it is visible.
     */
    @Nullable
    public static int[] visibleBounds(int[] argb, int width, int height) {
        if (argb == null || width <= 0 || height <= 0 || argb.length < width * height) return null;
        int maxAlpha = 0;
        for (int i = 0; i < width * height; i++) {
            maxAlpha = Math.max(maxAlpha, argb[i] >>> 24);
        }
        int threshold = Math.max(MIN_VISIBLE_ALPHA, Math.round(maxAlpha * VISIBLE_ALPHA_FRACTION));
        int left = width;
        int top = height;
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                if ((argb[row + x] >>> 24) < threshold) continue;
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        }
        return right >= left && bottom >= top
            ? new int[] {left, top, right + 1, bottom + 1}
            : null;
    }

    /**
     * How much of its square the visible box spans along its longer side: 1 for artwork that
     * reaches both edges, less for artwork inside a margin.
     */
    public static float extent(int[] bounds, int width, int height) {
        return Math.max((bounds[2] - bounds[0]) / (float) width,
            (bounds[3] - bounds[1]) / (float) height);
    }

    /**
     * Where the whole artwork is drawn, as {@code {left, top, right, bottom}} fractions of the
     * box, so that its visible box is centred and its longer side spans {@code targetExtent}.
     * The visible part stays inside the box by construction; the margin around it may fall
     * outside, which is transparent. Null when the move would be under one measured pixel, so
     * artwork already at the footprint is left exactly as it is.
     */
    @Nullable
    public static float[] placement(int[] bounds, int width, int height, float targetExtent) {
        float extent = extent(bounds, width, height);
        if (extent <= 0f || targetExtent <= 0f) return null;
        float scale = targetExtent / extent;
        float centerX = (bounds[0] + bounds[2]) / (2f * width);
        float centerY = (bounds[1] + bounds[3]) / (2f * height);
        float left = 0.5f - centerX * scale;
        float top = 0.5f - centerY * scale;
        float onePixel = 1f / Math.max(width, height);
        if (Math.abs(scale - 1f) < onePixel && Math.abs(left) < onePixel
            && Math.abs(top) < onePixel)
            return null;
        return new float[] {left, top, left + scale, top + scale};
    }
}
