package com.termux.app.chrome;

import androidx.annotation.ColorInt;

/**
 * Shade: a flat layer laid under a glass surface's tint, over its frost, that dims the face
 * behind the content. Theme-aware: where the glass is dark it is black, up to
 * {@link #DARK_CAP}; where it is light it is white, up to the lower {@link #LIGHT_CAP}, so a
 * light glass can be lifted toward its own white without ever going milky. The stored value is a
 * percentage of the theme's cap, so the slider's full travel is always the most either theme
 * allows. 0, every Look's value, draws nothing and measures as nothing.
 *
 * <p>Pure ints, no {@code Context}. The ink check measures it ({@link ChromeInk}), so the veil
 * pays only what Shade has not already bought, and the gathered light counts it as cover
 * ({@link #cover}).</p>
 */
public final class GlassShade {

    private GlassShade() {}

    /** The most a dark glass is darkened: 40% black (the planned film's cap). */
    public static final float DARK_CAP = 0.40f;
    /** The most a light glass is lifted: 25% white, under where a light face reads as milk. */
    public static final float LIGHT_CAP = 0.25f;

    /**
     * The shade layer's colour for glass whose base is {@code baseColor} at {@code percent} of the
     * theme's cap (clamped to 0..100): black or white at its alpha, or transparent at 0.
     */
    @ColorInt
    public static int color(@ColorInt int baseColor, int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        if (clamped == 0) return 0;
        boolean dark = GlassLook.isDark(baseColor);
        float cap = dark ? DARK_CAP : LIGHT_CAP;
        int alpha = Math.round(255f * cap * clamped / 100f);
        return (alpha << 24) | (dark ? 0x000000 : 0xFFFFFF);
    }

    /** The shade's opacity, 0..1. */
    public static float alpha(@ColorInt int shade) {
        return (shade >>> 24) / 255f;
    }

    /**
     * {@code tint} laid over {@code shade}, as one colour: what a surface that draws a single tint
     * paint (the panes) wears with Shade under it. A transparent shade returns {@code tint}
     * exactly; a transparent tint is the shade alone.
     */
    @ColorInt
    public static int under(@ColorInt int tint, @ColorInt int shade) {
        int shadeAlpha = shade >>> 24;
        if (shadeAlpha == 0) return tint;
        float a = (tint >>> 24) / 255f;
        float s = shadeAlpha / 255f;
        float outA = a + s * (1f - a);
        if (outA <= 0f) return 0;
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            float top = (tint >> shift) & 0xFF;
            float bottom = (shade >> shift) & 0xFF;
            float mixed = (top * a + bottom * s * (1f - a)) / outA;
            rgb |= Math.round(Math.max(0f, Math.min(255f, mixed))) << shift;
        }
        return (Math.round(outA * 255f) << 24) | rgb;
    }

    /**
     * How much of what lies under a surface's tint the tint stack covers, 0..1: the tint at
     * {@code tintAlpha} over the shade. What the gathered light under the stack is scaled up by
     * ({@link GlassRefraction.Look#withCover}).
     */
    public static float cover(float tintAlpha, @ColorInt int shade) {
        float a = Math.max(0f, Math.min(1f, tintAlpha));
        return 1f - (1f - a) * (1f - alpha(shade));
    }
}
