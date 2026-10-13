package com.termux.app.launcher.icon;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Pixel accounting for held drawables: how big the bitmaps inside one really are, what they cost,
 * and rasterising one down when it is larger than anything will ever draw it. Shared by every
 * bounded store that keeps artwork, so they all measure the same way. Also where pack artwork is
 * redrawn to the footprint a stock icon fills ({@link #fillFootprint}), under the same rules.
 */
public final class DrawablePixels {

    /** Deep enough for an adaptive icon of wrapped layer lists; a guard against a cyclic drawable. */
    private static final int MAX_CONTAINER_DEPTH = 6;

    private DrawablePixels() {
    }

    /**
     * Rasterise artwork that is both larger than {@code maxExtentPx} and actually made of pixels.
     * A vector costs a few kilobytes and scales for free, so flattening one to a bitmap would spend
     * memory rather than save it; only a drawable already carrying bitmaps is worth redrawing.
     */
    @Nullable
    public static Drawable shrink(@NonNull Resources resources, @Nullable Drawable source,
                                  int maxExtentPx) {
        if (source == null) return null;
        // How big the pixels actually are, which is the only thing that costs anything. A drawable
        // is asked what size it is, but not believed on its own: an adaptive icon declares the
        // nominal 72dp while holding the 108dp rasterisation, so trusting the declaration keeps
        // 323 KB per app in order to draw 143 KB of it. A container that will not answer at all is
        // measured entirely by what it holds.
        int largestBitmap = largestBitmapExtent(source, 0);
        if (largestBitmap <= 0) return source;
        int width = source.getIntrinsicWidth();
        int height = source.getIntrinsicHeight();
        if (width <= 0 || height <= 0) width = height = largestBitmap;
        int longestEdge = Math.max(width, height);
        int targetEdge = Math.min(maxExtentPx, longestEdge);
        // Nothing to gain once the pixels are no larger than the size they would be redrawn at.
        if (largestBitmap <= targetEdge) return source;
        try {
            float scale = targetEdge / (float) longestEdge;
            int targetWidth = Math.max(1, Math.round(width * scale));
            int targetHeight = Math.max(1, Math.round(height * scale));
            Bitmap flattened = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
            Rect previousBounds = new Rect(source.getBounds());
            source.setBounds(0, 0, targetWidth, targetHeight);
            source.draw(new Canvas(flattened));
            source.setBounds(previousBounds);
            return new BitmapDrawable(resources, flattened);
        } catch (RuntimeException | OutOfMemoryError e) {
            // Keeping the original costs memory; failing to produce artwork costs the user an icon.
            return source;
        }
    }

    /**
     * The raster artwork is measured at: one pixel is about 1% of the icon's side, finer than any
     * margin a pack leaves, for 72 KB of scratch (the bitmap and its pixels) per icon resolved,
     * given back as soon as it is measured.
     */
    private static final int FOOTPRINT_MEASURE_PX = 96;

    /** {@link #stockIconExtent}, measured once per process; 0 until then. */
    private static volatile float sStockIconExtent;

    /**
     * How much of its box a stock icon spans: the system's adaptive-icon mask, drawn and
     * measured on this device, since that mask is what every adaptive app icon fills. The stock
     * circle and the squircles reach the edges, so this is 1 on most phones; a mask with a
     * margin of its own is measured as it is. 1 when it cannot be measured.
     */
    public static float stockIconExtent() {
        float cached = sStockIconExtent;
        if (cached > 0f) return cached;
        float measured = 1f;
        try {
            AdaptiveIconDrawable stock = new AdaptiveIconDrawable(
                new ColorDrawable(Color.BLACK), new ColorDrawable(Color.BLACK));
            int[] bounds = measureVisibleBounds(stock);
            if (bounds != null) {
                measured = ArtworkFootprint.extent(bounds, FOOTPRINT_MEASURE_PX,
                    FOOTPRINT_MEASURE_PX);
            }
        } catch (RuntimeException | OutOfMemoryError ignored) {
            // The whole box: pack artwork then fills it, which is what the stock masks do.
        }
        sStockIconExtent = measured;
        return measured;
    }

    /**
     * {@code source} drawn so its visible artwork spans {@code targetExtent} of its box, centred
     * ({@link ArtworkFootprint#placement}). Measured once, here, so whatever draws the result —
     * every surface showing the icon — pays nothing for it.
     *
     * <p>Artwork made of pixels is redrawn into a bitmap no larger than the pixels it already
     * holds and never above {@link LauncherIconStore#MAX_RETAINED_PX}, the size the store would
     * keep it at anyway, so it costs no more than it did. A vector stays a vector: it is wrapped
     * in an inset that reaches past its box, which holds no pixels at all.
     *
     * @return {@code source} itself when it is already at the footprint or cannot be measured.
     */
    @NonNull
    public static Drawable fillFootprint(@NonNull Resources resources, @NonNull Drawable source,
                                         float targetExtent) {
        try {
            int[] bounds = measureVisibleBounds(source);
            if (bounds == null) return source;
            float[] place = ArtworkFootprint.placement(bounds, FOOTPRINT_MEASURE_PX,
                FOOTPRINT_MEASURE_PX, targetExtent);
            if (place == null) return source;
            int largestBitmap = largestBitmapExtent(source, 0);
            if (largestBitmap <= 0) {
                return new InsetDrawable(source, place[0], place[1], 1f - place[2],
                    1f - place[3]);
            }
            int edge = Math.min(largestBitmap, LauncherIconStore.MAX_RETAINED_PX);
            Bitmap filled = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888);
            Rect previousBounds = new Rect(source.getBounds());
            try {
                source.setBounds(Math.round(place[0] * edge), Math.round(place[1] * edge),
                    Math.round(place[2] * edge), Math.round(place[3] * edge));
                source.draw(new Canvas(filled));
            } finally {
                source.setBounds(previousBounds);
            }
            return new BitmapDrawable(resources, filled);
        } catch (RuntimeException | OutOfMemoryError e) {
            // Drawn at its own margin is a smaller icon; failing to produce one is no icon.
            return source;
        }
    }

    /** The visible box of {@code drawable} drawn square at the measuring size, or null. */
    @Nullable
    private static int[] measureVisibleBounds(@NonNull Drawable drawable) {
        int size = FOOTPRINT_MEASURE_PX;
        Bitmap scratch = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Rect previousBounds = new Rect(drawable.getBounds());
        try {
            drawable.setBounds(0, 0, size, size);
            drawable.draw(new Canvas(scratch));
            int[] pixels = new int[size * size];
            scratch.getPixels(pixels, 0, size, 0, 0, size, size);
            return ArtworkFootprint.visibleBounds(pixels, size, size);
        } finally {
            drawable.setBounds(previousBounds);
            scratch.recycle();
        }
    }

    /**
     * Byte cost of one held drawable. Anything without a bitmap still costs 1, so occupancy tracks
     * entries and evicting everything returns to zero.
     */
    public static int heldBytes(@Nullable Drawable value) {
        if (!(value instanceof BitmapDrawable)) return 1;
        Bitmap bitmap = ((BitmapDrawable) value).getBitmap();
        if (bitmap == null) return 1;
        long bytes = bitmap.getAllocationByteCount();
        if (bytes < 1L) return 1;
        return bytes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) bytes;
    }

    /**
     * The largest bitmap edge anywhere inside this drawable, or 0 when it holds none.
     *
     * <p>A container is worth whatever it contains, and launcher artwork is almost never a bare
     * bitmap: the framework hands back adaptive icons whose layers are themselves wrapped in
     * insets, scales and rotations, and layer lists of the same — and some of those containers
     * report no intrinsic size at all. Looking only one level down, or believing a -1, keeps every
     * one of them whole, which is the entire cost this class exists to remove. So the walk goes all
     * the way down.</p>
     */
    private static int largestBitmapExtent(@Nullable Drawable drawable, int depth) {
        if (drawable == null || depth > MAX_CONTAINER_DEPTH) return 0;
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            return bitmap == null ? 0 : Math.max(bitmap.getWidth(), bitmap.getHeight());
        }
        // Adaptive icons are checked before layer lists: they are not one, and they expose their
        // two layers by name rather than by index.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            && drawable instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) drawable;
            return Math.max(largestBitmapExtent(adaptive.getBackground(), depth + 1),
                largestBitmapExtent(adaptive.getForeground(), depth + 1));
        }
        if (drawable instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) drawable;
            int largest = 0;
            for (int i = 0; i < layers.getNumberOfLayers(); i++) {
                largest = Math.max(largest, largestBitmapExtent(layers.getDrawable(i), depth + 1));
            }
            return largest;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && drawable instanceof DrawableWrapper) {
            return largestBitmapExtent(((DrawableWrapper) drawable).getDrawable(), depth + 1);
        }
        return 0;
    }
}
