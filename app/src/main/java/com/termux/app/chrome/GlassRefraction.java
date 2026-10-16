package com.termux.app.chrome;

import android.graphics.BitmapShader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.Arrays;

/**
 * The one AGSL glass every surface refracts through (API 33+).
 *
 * <p>What separates real glass from frosted polymer is that glass <em>bends</em> light at its
 * edges and catches a crisp highlight there, instead of only diffusing it. The program samples
 * the blurred wallpaper and, within a band along the surface's rounded-rect rim, pulls the sample
 * inward along the rim's own normal on Haze's edge profile: the whole bend at the rim, under a
 * third of it a quarter of the way in, under a tenth half way and nothing at the band's inner
 * edge, so the bend reads as a narrow ring round a face that samples exactly where it is. How far
 * the rim reaches in for its picture is held at half the surface's short side, Haze's plain cap,
 * so a small pane never shows the other edge's picture. Across the outermost dp the pull eases
 * back to nothing, as Haze's edge softness does, so the outline's own pixel shows the picture just
 * outside it and the glass is continuous with the wallpaper there.</p>
 * <p>The outline gathers the wallpaper's own light: the sample is brightened in proportion to
 * itself, most toward one key light up and to the left with a quarter share all round, fading
 * inward over about a dp and a half, and less over a pale backdrop, so the edge reads as the
 * wallpaper's colour caught at the outline rather than a white line drawn on it. The brightening
 * is held under the headroom the sample's brightest channel has left, so no channel clips and a
 * pale sky's edge stays blue rather than turning white or cyan. The light is fixed material, not
 * a knob, and every surface that runs the program wears it. Where a clearer picture of the wallpaper is handed in
 * ({@link Program#setClarityInput}), the band shows it through the bend in place of the frost,
 * growing clearer toward the rim, which is what a thick glass edge does to what is behind it.
 * Dispersion splits the bent picture into red and blue at the corners, in step with how far it is
 * bent there, the way a prism fans out white light; the middle of a straight edge takes none. A
 * pressed extra key adds a lens that magnifies toward the key's centre and eases to nothing at its
 * pill rim, and a finger resting on the glass ({@link Program#setTouch}) lights the rim nearest
 * it, scatters a faint glow under it and draws the picture very slightly toward it.</p>
 * <p>It used to live in the activity as the dock's own effect. It is one program now, with two
 * ways in: as a {@code RenderEffect} over a view's finished pixels, which is what the dock and
 * the under-pill strip still do (the content is the view, so the aim is identity), and as the
 * paint shader a {@link SharedFrameDrawable} or a pane slab draws the shared frame through, with
 * that frame's {@link BitmapShader} as the child input and the surface's aim — the same matrix
 * the plain draw uses — handed over as uniforms, so the frame is sampled at the surface's live
 * screen position and bent per pixel in one pass, with no offscreen layer per surface.</p>
 *
 * <p>Nothing here animates. Uniforms are written only when what they describe moved — the
 * surface, the parallax, a knob — so a surface at rest costs nothing; a slide costs one uniform
 * write per moved frame and no allocation.</p>
 */
public final class GlassRefraction {

    private GlassRefraction() {}

    /**
     * The blur radius, in dp, of the clearer frame a surface hands in for the bent edge to show
     * ({@link Program#setClarityInput}): a touch of blur so the upscale does not alias, kept under
     * the pre-blur's 25 px script radius so the frame stays full size. A surface whose own frost
     * is this clear or clearer takes none.
     */
    public static final int CLARITY_RADIUS_DP = 2;

    /** True on phones that run an AGSL shader at all. Everything below is reachable only then. */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    public static boolean available() {
        return Build.VERSION.SDK_INT >= FancierGlassPolicy.MIN_SDK;
    }

    // ------------------------------------------------------------------------------ the look

    /**
     * A surface's glass as stored, as one value: how far the picture is bent under the rim, how
     * far in from the rim the bend reaches, how far the colours part at the corners, and how clear
     * the wallpaper shows through the bent edge. It also carries the stored edge light and
     * specular, which the program no longer reads: the edge's light is fixed material. Stored in
     * dp and percent so two reads compare equal whatever the screen's density; the program
     * converts when it is handed one.
     */
    public static final class Look {

        /** The numbers the dock's refraction always ran with; the default mode is exactly this. */
        @NonNull public static final Look DEFAULT = new Look(
            TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_BEND,
            TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_EDGE_WIDTH,
            TERMUX_APP.DEFAULT_VALUE_FANCIER_GLASS_EDGE_LIGHT);

        /**
         * The outermost width the pull eases in across: 1 dp and never under 2 px (Haze's edge
         * softness and its sample step), so the outline's own pixel shows the picture just
         * outside it. The program's {@code kRimDp} and {@code kMinRimPx}.
         */
        static final float RIM_WIDTH_DP = 1f;
        static final float MIN_RIM_WIDTH_PX = 2f;

        /**
         * The deepest the pull may reach at the rim, as a share of the band: one and a half times
         * it. Every Look the editor writes sits under it; it only holds an odd stored pair.
         */
        static final float MAX_PULL_SHARE = 1.5f;

        /**
         * How far red and blue part from green at 100%, as a share of the local pull, at a corner;
         * nothing at the middle of a straight edge ({@link GlassRefraction#cornerWeight}). Clear's
         * 40% is Haze Clear's 0.02, a glint at the corners, not a rainbow.
         */
        static final float MAX_DISPERSION_SHARE = 0.05f;

        /** How much of the clearer picture the band shows at the rim at 100%: all of it. */
        static final float MAX_CLARITY = 1f;

        // The gathered light, the edge every surface's outline wears. Each is the program's
        // constant of the same name.

        /** How fast the light fades inward, in dp: 1 at the outline, 1/e this far in. {@code kGlowDp}. */
        static final float GLOW_DP = 1.4f;
        /** The share every side gets, so the light follows the whole shape. {@code kAmbient}. */
        static final float AMBIENT = 0.25f;
        /** The key light's falloff round the outline, a wide lobe. {@code kKeyExp}. */
        static final float KEY_EXPONENT = 2f;
        /** How much of the glint a white backdrop takes away: over white a quarter is left. {@code kPaleCut}. */
        static final float PALE_CUT = 0.75f;
        /** How much the light brightens the backdrop's own colour. {@code kGain}. */
        static final float GAIN = 1.6f;
        /**
         * The cover the light's strength is tuned at: Clear's 10% tint. The light is drawn under a
         * surface's tint and Shade, which let {@code 1 - cover} of it through, so a surface under
         * more cover has its light raised by {@code (1 - REFERENCE_COVER) / (1 - cover)}, never
         * lowered below the tuned strength and never above {@link #MAX_EDGE_GAIN}
         * ({@link #edgeGain}). {@code uEdgeGain}.
         */
        static final float REFERENCE_COVER = 0.10f;
        /**
         * The most the light is raised; a pass-2 tuning knob. The headroom hold
         * ({@link GlassRefraction#heldK}) keeps any gain from clipping, but past its knee (half the
         * headroom) the light flattens toward a ceiling and the ring loses its falloff. At 2 the
         * ambient sides of a mid-grey backdrop (0.5) peak at about the knee (0.31 against 0.29)
         * and a dark one's (0.2) well inside it (0.43 against 1.03), so the ring keeps its shape
         * on the backdrops glass mostly sits on; the key-light corner of a mid backdrop is past
         * the knee at any gain, as it was before. Mist's 60% asks 2.25 and gets 2; Solid's 92%
         * asks over eleven and gets 2 as well.
         */
        static final float MAX_EDGE_GAIN = 2f;
        /** A little grey light, so a black wallpaper still shows an edge. {@code kLift}. */
        static final float LIFT = 0.07f;
        /** The faint dark under the light. {@code kEdgeShade}. */
        static final float EDGE_SHADE = 0.05f;
        /**
         * Where the brightening starts to give way to the headroom the sample's brightest channel
         * has left: below half of it the light is exactly as asked, above it the light eases
         * toward that headroom without reaching it, on {@code 1 - 1 / (4 r)}, which meets the
         * straight line at a half with the same slope. Fixed by that join; named for the tests.
         * {@link GlassRefraction#heldK}.
         */
        static final float HEADROOM_KNEE = 0.5f;
        /**
         * How far in the light reaches at all, in dp: {@code exp(-10 / 1.4)} is under one 8-bit
         * step even at full gain, so pixels further in skip the term. {@code kGlowReachDp}.
         */
        static final float GLOW_REACH_DP = 10f;

        public final int bendDp;
        public final int edgeWidthDp;
        /**
         * The stored edge light, a percentage. Kept so a saved look loads and compares as it
         * was; the program ignores it: the edge is the gathered light, fixed material.
         */
        public final int edgeLightPercent;
        /**
         * The stored specular, a percentage. Kept so a saved look loads and compares as it was;
         * the program ignores it, as {@link #edgeLightPercent}.
         */
        public final int specularPercent;
        /** The chromatic split at the corners, a percentage; 0 samples r, g and b alike. */
        public final int dispersionPercent;
        /** How clear the wallpaper shows through the bent edge, a percentage; 0 keeps the frost. */
        public final int clarityPercent;
        /**
         * How much the layers drawn over this frost cover it, a percentage: the surface's tint and
         * Shade ({@link GlassShade#cover}). Not stored; each surface states its own. 0 (as read
         * from the preferences) leaves the light at its tuned strength.
         */
        public final int coverPercent;

        public Look(int bendDp, int edgeWidthDp, int edgeLightPercent) {
            this(bendDp, edgeWidthDp, edgeLightPercent, 0, 0, 0);
        }

        public Look(int bendDp, int edgeWidthDp, int edgeLightPercent,
                    int specularPercent, int dispersionPercent) {
            this(bendDp, edgeWidthDp, edgeLightPercent, specularPercent, dispersionPercent, 0);
        }

        public Look(int bendDp, int edgeWidthDp, int edgeLightPercent,
                    int specularPercent, int dispersionPercent, int clarityPercent) {
            this(bendDp, edgeWidthDp, edgeLightPercent, specularPercent, dispersionPercent,
                clarityPercent, 0);
        }

        private Look(int bendDp, int edgeWidthDp, int edgeLightPercent, int specularPercent,
                     int dispersionPercent, int clarityPercent, int coverPercent) {
            this.bendDp = bendDp;
            this.edgeWidthDp = edgeWidthDp;
            this.edgeLightPercent = edgeLightPercent;
            this.specularPercent = specularPercent;
            this.dispersionPercent = dispersionPercent;
            this.clarityPercent = clarityPercent;
            this.coverPercent = Math.max(0, Math.min(100, coverPercent));
        }

        /**
         * This look on a surface whose tint and Shade cover the frost by {@code cover} (0..1): the
         * gathered light is raised to read through them ({@link #edgeGain}). Rounded to a percent,
         * so a surface re-dressed at the same opacity compares equal and writes nothing.
         */
        @NonNull
        public Look withCover(float cover) {
            int percent = Math.round(Math.max(0f, Math.min(1f, cover)) * 100f);
            if (percent == coverPercent) return this;
            return new Look(bendDp, edgeWidthDp, edgeLightPercent, specularPercent,
                dispersionPercent, clarityPercent, percent);
        }

        /**
         * {@code uEdgeGain}: what the gathered light's strength is multiplied by under this
         * look's cover, 1 at or under {@link #REFERENCE_COVER} and at most {@link #MAX_EDGE_GAIN}.
         * The program then holds the raised light under the headroom as ever, so no channel clips.
         */
        public float edgeGain() {
            return edgeGainFor(coverPercent / 100f);
        }

        /** {@link #edgeGain} for a cover of {@code cover}, 0..1. */
        static float edgeGainFor(float cover) {
            float through = Math.max(1f - Math.max(0f, Math.min(1f, cover)), 1e-3f);
            return Math.max(1f, Math.min(MAX_EDGE_GAIN, (1f - REFERENCE_COVER) / through));
        }

        /** The user's knobs, already clamped by their accessors. */
        @NonNull
        public static Look of(@NonNull TermuxAppSharedPreferences preferences) {
            return new Look(preferences.getFancierGlassBendDp(),
                preferences.getFancierGlassEdgeWidthDp(),
                preferences.getFancierGlassEdgeLightPercent(),
                preferences.getFancierGlassSpecularPercent(),
                preferences.getFancierGlassDispersionPercent(),
                preferences.getFancierGlassClarityPercent());
        }

        /** A percentage as 0..1, clamped. */
        private static float share(int percent) {
            return Math.max(0, Math.min(100, percent)) / 100f;
        }

        /**
         * {@code uStrength}: how far a rim pixel's sample is pulled inward, in px: the bend, held
         * under {@link #MAX_PULL_SHARE} of the band. The program holds it again under half the
         * surface's short side.
         */
        public float strengthPx(float density) {
            return Math.max(0f, Math.min(bendDp * density, MAX_PULL_SHARE * bandPx(density)));
        }

        /** {@code uBand}: how far in from the rim the pull reaches, in px; never under a pixel. */
        public float bandPx(float density) {
            return Math.max(1f, edgeWidthDp * density);
        }

        /**
         * {@code uDisp}: how far red and blue part from green, as a share of the local pull at a
         * corner, 0..{@link #MAX_DISPERSION_SHARE}.
         */
        public float dispersion() {
            return share(dispersionPercent) * MAX_DISPERSION_SHARE;
        }

        /** {@code uClarity}: how much of the clearer picture the rim shows, 0..{@link #MAX_CLARITY}. */
        public float clarity() {
            return share(clarityPercent) * MAX_CLARITY;
        }

        /** The eased pull's width in px at {@code density}, as the program computes it; see {@link #RIM_WIDTH_DP}. */
        public static float rimWidthPx(float density) {
            return Math.max(MIN_RIM_WIDTH_PX, RIM_WIDTH_DP * density);
        }

        /**
         * How far in from the rim the gathered light and the eased pull reach, in px: what a seam
         * edge must be pushed past for neither to show along it, or a soft bright line shows
         * through one sheet of glass. The light reaches much the further.
         */
        public static float rimReachPx(float density) {
            return Math.max(rimWidthPx(density), GLOW_REACH_DP * density);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) return true;
            if (!(other instanceof Look)) return false;
            Look that = (Look) other;
            return bendDp == that.bendDp && edgeWidthDp == that.edgeWidthDp
                && edgeLightPercent == that.edgeLightPercent
                && specularPercent == that.specularPercent
                && dispersionPercent == that.dispersionPercent
                && clarityPercent == that.clarityPercent
                && coverPercent == that.coverPercent;
        }

        @Override
        public int hashCode() {
            return (((((bendDp * 31 + edgeWidthDp) * 31 + edgeLightPercent) * 31 + specularPercent) * 31
                + dispersionPercent) * 31 + clarityPercent) * 31 + coverPercent;
        }

        @NonNull
        @Override
        public String toString() {
            return "Look{bend=" + bendDp + "dp, edge=" + edgeWidthDp + "dp, light=" + edgeLightPercent + "%, specular="
                + specularPercent + "%, dispersion=" + dispersionPercent + "%, clarity=" + clarityPercent
                + "%, cover=" + coverPercent + "%}";
        }
    }

    // ----------------------------------------------------------------------------- the seams

    /** An edge of the surface another glass surface continues past, so it takes no rim and no bend. */
    public static final int SEAM_LEFT = 1;
    public static final int SEAM_TOP = 1 << 1;
    public static final int SEAM_RIGHT = 1 << 2;
    public static final int SEAM_BOTTOM = 1 << 3;

    /**
     * The rim rect for a surface whose bounds are {@code bounds}: the bounds themselves, pushed
     * out by {@code reachPx} on every seam side so that edge — and the corner arcs that would
     * have met it — lands past the visible surface and reads as interior glass. The keyboard
     * host over the under-pill strip and the status band over the window bar are the cases; a
     * rim drawn at their shared edge is a bright line through one sheet of glass.
     *
     * @param out {@code {left, top, right, bottom}}
     */
    public static void rimRect(@NonNull float[] out, @NonNull Rect bounds, float reachPx, int seams) {
        rimRect(out, bounds.left, bounds.top, bounds.right, bounds.bottom, reachPx, seams);
    }

    /** {@link #rimRect(float[], Rect, float, int)} for a surface drawn at fractional bounds: a corner tab mid-slide. */
    public static void rimRect(@NonNull float[] out, float left, float top, float right, float bottom,
                               float reachPx, int seams) {
        out[0] = left - ((seams & SEAM_LEFT) != 0 ? reachPx : 0f);
        out[1] = top - ((seams & SEAM_TOP) != 0 ? reachPx : 0f);
        out[2] = right + ((seams & SEAM_RIGHT) != 0 ? reachPx : 0f);
        out[3] = bottom + ((seams & SEAM_BOTTOM) != 0 ? reachPx : 0f);
    }

    /**
     * How far a seam edge is pushed out: past the band (or the gathered light, where a narrow
     * band ends inside its reach), the pull and the corner arc, with a little to spare.
     */
    public static float seamReachPx(@NonNull Look look, float density, float cornerRadiusPx) {
        return Math.max(look.bandPx(density), Look.rimReachPx(density))
            + look.strengthPx(density) + Math.max(0f, cornerRadiusPx) + 2f * density;
    }

    // ---------------------------------------------------------------------------- the program

    /**
     * The AGSL. {@code content} is whatever the surface shows — the view's own pixels under a
     * {@code RenderEffect}, the frame's {@link BitmapShader} as a paint shader — and
     * {@code uFrameScale}/{@code uFrameOffset} are the aim that puts frame pixel {@code (0, 0)}
     * where the frame's rect begins, as {@link SharedFrameDrawable#aim} states it: identity under
     * a {@code RenderEffect}. {@code uLocalX}/{@code uLocalY}/{@code uLocalW} are the rows of
     * {@link GlassAnchor.Aim#local}, from the surface's own px to px from its origin on screen,
     * applied first with its perspective divide: identity everywhere but on a wall page that is
     * sunk or tipped, where it keeps the wallpaper fixed to the screen. {@link #framePx} is the
     * same mapping in Java.
     */
    static final String AGSL =
        "uniform shader content;\n" +
        // The same picture with less frost, aimed exactly as content is; bound to content itself
        // where there is none, so the mix below is then the identity.
        "uniform shader sharp;\n" +
        "uniform float2 uFrameScale;\n" +
        "uniform float2 uFrameOffset;\n" +
        "uniform float3 uLocalX;\n" +
        "uniform float3 uLocalY;\n" +
        "uniform float3 uLocalW;\n" +
        "uniform float2 uRectMin;\n" +
        "uniform float2 uRectMax;\n" +
        "uniform float uRadius;\n" +
        "uniform float uBand;\n" +
        "uniform float uStrength;\n" +
        "uniform float uDisp;\n" +
        "uniform float uClarity;\n" +
        "uniform float uDensity;\n" +
        // How much the gathered light is raised to read through the tint and Shade drawn over it;
        // 1 at Clear's cover. Look.edgeGain in Java.
        "uniform float uEdgeGain;\n" +
        // A finger on the glass: where it rests, how far its light reaches, and its 0..1 fade.
        "uniform float uTouchActive;\n" +
        "uniform float2 uTouch;\n" +
        "uniform float uTouchRadius;\n" +
        "uniform float uTouchLens;\n" +
        // Active extra-key "lens": a rounded-rect that MAGNIFIES (bends) the backdrop strongest from
        // the middle and eases to nothing at its rim, so a pressed key reads as a thick glass pill
        // refracting the wallpaper that shows through the transparent key cell. uLensActive carries
        // the 0..1 fade intensity.
        "uniform float uLensActive;\n" +
        "uniform float2 uLensCenter;\n" +
        "uniform float2 uLensHalf;\n" +
        "uniform float uLensRadius;\n" +
        "uniform float uLensStrength;\n" +
        "uniform float uLensFeather;\n" +
        // The outermost width the pull eases in across, a dp never under two px.
        // Look.RIM_WIDTH_DP and MIN_RIM_WIDTH_PX in Java.
        "const float kRimDp = 1.0;\n" +
        "const float kMinRimPx = 2.0;\n" +
        // How fast Haze's edge profile decays into the band; EDGE_DECAY in Java.
        "const float kDecay = 0.225;\n" +
        // The pressed key's added glow at its centre; KEY_GLOW in Java.
        "const float kKeyGlow = 0.15;\n" +
        // The gathered light: one key light up and to the left, how fast the light fades inward
        // and how far it reaches, the share every side gets, how much a pale backdrop takes off the
        // glint, how much it brightens the backdrop's own colour, a little grey lift, and the faint
        // dark under it. KEY_LIGHT_X, KEY_LIGHT_Y, LUMA_R/G/B and Look.GLOW_DP, GLOW_REACH_DP,
        // AMBIENT, KEY_EXPONENT, PALE_CUT, GAIN, LIFT and EDGE_SHADE in Java.
        "const float2 kKeyLight = float2(-0.552, -0.834);\n" +
        "const float3 kLumaW = float3(0.2126, 0.7152, 0.0722);\n" +
        "const float kGlowDp = 1.4;\n" +
        "const float kGlowReachDp = 10.0;\n" +
        "const float kAmbient = 0.25;\n" +
        "const float kKeyExp = 2.0;\n" +
        "const float kPaleCut = 0.75;\n" +
        "const float kGain = 1.6;\n" +
        "const float kLift = 0.07;\n" +
        "const float kEdgeShade = 0.05;\n" +
        "float sdRoundRect(float2 p, float2 b, float r) {\n" +
        "    float2 q = abs(p) - b + float2(r, r);\n" +
        "    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;\n" +
        "}\n" +
        // The outward normal of the rounded rect: round the corner arc, straight out along an edge,
        // and on the inner rect, where no arc reaches, the axis of the nearer edge.
        "float2 rectNormal(float2 p, float2 b, float r) {\n" +
        "    float2 q = abs(p) - b + float2(r, r);\n" +
        "    float2 outside = max(q, float2(0.0, 0.0));\n" +
        "    float len = length(outside);\n" +
        "    float2 s = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);\n" +
        "    if (len > 0.001) {\n" +
        "        return s * (outside / len);\n" +
        "    }\n" +
        "    float useX = step(q.y, q.x);\n" +
        "    return s * float2(useX, 1.0 - useX);\n" +
        "}\n" +
        // A surface pixel to the content's own pixel: the local matrix, then the aim undone. With
        // the local matrix at identity this is exactly the old (c - offset) / scale.
        "float2 framePx(float2 c) {\n" +
        "    float3 p = float3(c, 1.0);\n" +
        "    float3 h = float3(dot(uLocalX, p), dot(uLocalY, p), dot(uLocalW, p));\n" +
        "    return (h.xy / h.z - uFrameOffset) / uFrameScale;\n" +
        "}\n" +
        // The bend's share across the band, t 0 at the rim to 1 at the band's inner edge: Haze's
        // edge profile, 1 at the rim, falling off steeply and exactly 0 at t 1, so the bend is a
        // narrow ring and the face beyond it is untouched. edgeProfile in Java.
        "float edgeProfile(float t) {\n" +
        "    float u = 1.0 - t * t;\n" +
        "    return exp(-t / kDecay) * u * u;\n" +
        "}\n" +
        // How much of the colour split a pixel at p (from the centre) of a surface of half size b
        // takes: nothing on the two axes, so nothing at the middle of a straight edge, all of it
        // at a corner (Kyant's and Haze's weight). cornerWeight in Java.
        "float cornerWeight(float2 p, float2 b) {\n" +
        "    return min(abs(p.x * p.y) / max(b.x * b.y, 1.0), 1.0);\n" +
        "}\n" +
        // How much of the gathered light the outline with outward normal n takes: the ambient share
        // all round, the rest on the key light's wide lobe. gatherShare in Java.
        "float gatherShare(float2 n) {\n" +
        "    float c = max(dot(n, kKeyLight), 0.0);\n" +
        "    return kAmbient + (1.0 - kAmbient) * (c > 0.0 ? pow(c, kKeyExp) : 0.0);\n" +
        "}\n" +
        // The light's strength k held under kMax, the most the sample's brightest channel can take
        // before it clips: exactly k below half of kMax, then easing toward kMax on 1 - 1/(4r),
        // which meets the straight line there with the same slope and never reaches kMax. heldK in
        // Java.
        "float heldK(float k, float kMax) {\n" +
        "    float r = k / max(kMax, 0.000001);\n" +
        "    return r <= 0.5 ? k : kMax * (1.0 - 0.25 / r);\n" +
        "}\n" +
        "half4 main(float2 fragCoord) {\n" +
        "    float2 center = (uRectMin + uRectMax) * 0.5;\n" +
        "    float2 b = (uRectMax - uRectMin) * 0.5;\n" +
        "    float2 p = fragCoord - center;\n" +
        "    float inside = -sdRoundRect(p, b, uRadius);\n" +
        // The normal eases round the corner on an arc wider than the drawn one (1.5x, held under the
        // half size), so the bend never snaps where two edges meet.
        "    float2 n = rectNormal(p, b, min(uRadius * 1.5, min(b.x, b.y)));\n" +
        // t runs 0 at the rim to 1 at the band's inner edge, and stays 1 across the face.
        "    float t = clamp(inside / uBand, 0.0, 1.0);\n" +
        // The eased pull's width, and how far in from the outline this pixel is across it: 0 at
        // the outline, 1 from that width in.
        "    float rimW = max(kMinRimPx, kRimDp * uDensity);\n" +
        "    float edge = smoothstep(0.0, rimW, inside);\n" +
        // The pull: inward along the rim's normal by the profile's share of the bend, the bend held
        // at half the surface's short side (Haze's plain cap) so a small pane never reaches across
        // to the other edge's picture, and eased to nothing across the outermost dp (Haze's
        // edge softness) so the outline's pixel shows the picture just outside it. An offset that
        // vanishes at t 1: a face pixel samples exactly where it is. A seam edge lies past the band
        // (rimRect), so it takes none. pullPx in Java is the same arithmetic.
        "    float pull = edgeProfile(t) * min(uStrength, min(b.x, b.y)) * edge;\n" +
        "    float2 sampleCoord = fragCoord - n * pull;\n" +
        // A finger on the glass: its reach, 1 under the fingertip and 0 at uTouchRadius. The
        // picture is drawn a little toward the touch (a shallow lens, uTouchLens of the distance
        // at most), and the glow and the rim light below reuse the same reach.
        "    float touchG = 0.0;\n" +
        "    if (uTouchActive > 0.001) {\n" +
        "        float2 tp = fragCoord - uTouch;\n" +
        "        touchG = (1.0 - smoothstep(0.0, max(uTouchRadius, 1.0), length(tp))) * uTouchActive;\n" +
        "        sampleCoord = sampleCoord - tp * (uTouchLens * touchG);\n" +
        "    }\n" +
        // Per-key lens: magnify the backdrop toward the key centre, fading out to the pill rim.
        "    float lensGlow = 0.0;\n" +
        "    if (uLensActive > 0.001) {\n" +
        "        float2 lp = fragCoord - uLensCenter;\n" +
        "        float ld = -sdRoundRect(lp, uLensHalf, uLensRadius);\n" +
        "        if (ld > 0.0) {\n" +
        "            float2 ln = float2(lp.x / max(uLensHalf.x, 1.0), lp.y / max(uLensHalf.y, 1.0));\n" +
        "            float rr = clamp(length(ln), 0.0, 1.0);\n" +
        "            float kFull = mix(1.0 - uLensStrength, 1.0, smoothstep(0.5, 1.0, rr));\n" +
        "            float k = mix(1.0, kFull, uLensActive);\n" +
        "            float fade = smoothstep(0.0, max(uLensFeather, 1.0), ld) * uLensActive;\n" +
        "            float2 lensCoord = uLensCenter + lp * k;\n" +
        "            float2 lensBent = lensCoord - n * pull;\n" +
        "            sampleCoord = mix(sampleCoord, lensBent, fade);\n" +
        "            lensGlow = fade * (1.0 - rr);\n" +
        "        }\n" +
        "    }\n" +
        // How far the clearer picture takes over: easing in from the frost at the band's inner
        // edge, half way at the band's middle, uClarity under the rim. Zero on the face.
        "    float x = 1.0 - t;\n" +
        "    float clarityK = uClarity > 0.0 && x > 0.0 ? x * x * (3.0 - 2.0 * x) * uClarity : 0.0;\n" +
        // The colour split, at the corners only and in step with the pull there: red reads the
        // picture bent a little less, blue a little more, green as bent (three taps, Haze's
        // Simple). Where a clearer picture shows through, it is split the same way, so the fringe
        // survives at the rim. A pixel with no split, every straight-edge middle and every face
        // pixel among them, takes the one sample.
        "    float split = uDisp > 0.0 ? pull * uDisp * cornerWeight(p, b) : 0.0;\n" +
        "    half4 col = half4(0.0);\n" +
        "    if (split > 0.0) {\n" +
        "        float2 fr = framePx(sampleCoord + n * split);\n" +
        "        float2 fg = framePx(sampleCoord);\n" +
        "        float2 fb = framePx(sampleCoord - n * split);\n" +
        "        half4 g = content.eval(fg);\n" +
        "        col = half4(content.eval(fr).r, g.g, content.eval(fb).b, g.a);\n" +
        "        if (clarityK > 0.0) {\n" +
        "            half4 sg = sharp.eval(fg);\n" +
        "            half4 clear = half4(sharp.eval(fr).r, sg.g, sharp.eval(fb).b, sg.a);\n" +
        "            col = mix(col, clear, half(clarityK));\n" +
        "        }\n" +
        "    } else {\n" +
        "        float2 f = framePx(sampleCoord);\n" +
        "        col = content.eval(f);\n" +
        "        if (clarityK > 0.0) {\n" +
        "            col = mix(col, sharp.eval(f), half(clarityK));\n" +
        "        }\n" +
        "    }\n" +
        // The gathered light: the backdrop's own colour brightened at the outline and fading
        // inward over kGlowDp, brightest toward the key light with the ambient share all round, a
        // finger's light added to it, and less of it over a pale backdrop. It multiplies the
        // colour, so a blue wallpaper gives a brighter blue edge; only kLift is grey. Its strength
        // is held under the headroom the brightest channel has left (heldK), so no channel clips
        // and the hue is kept: a pale sky's edge stays blue rather than going cyan or white. The
        // min under the alpha is only a guard. Pixels past kGlowReachDp skip it. gatheredLit in
        // Java.
        "    if (inside < kGlowReachDp * uDensity) {\n" +
        "        float glow = exp(-max(inside, 0.0) / (kGlowDp * uDensity));\n" +
        "        float pale = dot(float3(col.rgb), kLumaW);\n" +
        "        float k = glow * (gatherShare(n) + 1.5 * touchG) * (1.0 - kPaleCut * pale) * uEdgeGain;\n" +
        "        col.rgb = col.rgb * half(1.0 - glow * kEdgeShade);\n" +
        "        float peak = float(max(col.r, max(col.g, col.b)));\n" +
        "        k = heldK(k, max(float(col.a) - peak, 0.0) / (kGain * peak + kLift));\n" +
        "        col.rgb = min(half3(col.a), col.rgb * half(1.0 + kGain * k) + half3(kLift * k));\n" +
        "    }\n" +
        // The pressed key's glow and the faint scatter under a finger stay small additive light.
        "    col.rgb = col.rgb + half3(lensGlow * kKeyGlow + 0.045 * touchG * touchG);\n" +
        "    return col;\n" +
        "}\n";

    /** The local matrix's values, row-major as {@link Matrix#getValues} gives them, for none. */
    private static final float[] IDENTITY = {1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f};

    /**
     * The program's own mapping from a surface pixel {@code (x, y)} to the content's pixel, into
     * {@code out}: {@code local} (a {@link Matrix}'s nine values) with its perspective divide,
     * then the aim undone. The plain draw's shader matrix ({@link GlassAnchor.Aim#shaderMatrix})
     * is its inverse, so the two draws sample the same wallpaper.
     */
    static void framePx(@NonNull float[] local, float scaleX, float scaleY, float offsetX,
                        float offsetY, float x, float y, @NonNull float[] out) {
        float hx = local[0] * x + local[1] * y + local[2];
        float hy = local[3] * x + local[4] * y + local[5];
        float hw = local[6] * x + local[7] * y + local[8];
        out[0] = (hx / hw - offsetX) / Math.max(1e-4f, scaleX);
        out[1] = (hy / hw - offsetY) / Math.max(1e-4f, scaleY);
    }

    /** The gathered light's key light, {@code kKeyLight}: up and to the left, a unit vector. */
    static final float KEY_LIGHT_X = -0.552f;
    static final float KEY_LIGHT_Y = -0.834f;

    /** Rec. 709's luma weights, {@code kLumaW}: how pale a sample is. */
    static final float LUMA_R = 0.2126f;
    static final float LUMA_G = 0.7152f;
    static final float LUMA_B = 0.0722f;

    /** The program's {@code kDecay}: Haze's edge profile's decay length, as a share of the band. */
    static final float EDGE_DECAY = 0.225f;

    /** The program's {@code kKeyGlow}: the white a pressed key adds at its centre. */
    static final float KEY_GLOW = 0.15f;

    /** AGSL's {@code smoothstep}. */
    static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    /**
     * The program's {@code edgeProfile}: the bend's share across the band, {@code t} 0 at the rim
     * to 1 at the band's inner edge, Haze's {@code exp(-t / 0.225) (1 - t²)²}.
     */
    static float edgeProfile(float t) {
        float u = 1f - t * t;
        return (float) Math.exp(-t / EDGE_DECAY) * u * u;
    }

    /**
     * The program's pull, in px, for a pixel {@code insidePx} in from the rim of a surface whose
     * half short side is {@code halfShortPx}: the profile across the band times the strength held
     * at that half side, eased to nothing across the outermost {@code rimWidthPx}.
     */
    static float pullPx(float insidePx, float bandPx, float strengthPx, float halfShortPx, float rimWidthPx) {
        float t = Math.max(0f, Math.min(1f, insidePx / bandPx));
        return edgeProfile(t) * Math.min(strengthPx, halfShortPx) * smoothstep(0f, rimWidthPx, insidePx);
    }

    /** The program's {@code cornerWeight}, for a pixel at {@code (px, py)} from the centre of a surface of half size {@code (bx, by)}. */
    static float cornerWeight(float px, float py, float bx, float by) {
        return Math.min(Math.abs(px * py) / Math.max(bx * by, 1f), 1f);
    }

    /**
     * The program's {@code gatherShare}: how much of the gathered light the outline with outward
     * normal {@code (nx, ny)} takes, {@link Look#AMBIENT} all round and the rest on the key
     * light's lobe.
     */
    static float gatherShare(float nx, float ny) {
        float c = Math.max(nx * KEY_LIGHT_X + ny * KEY_LIGHT_Y, 0f);
        float key = c > 0f ? (float) Math.pow(c, Look.KEY_EXPONENT) : 0f;
        return Look.AMBIENT + (1f - Look.AMBIENT) * key;
    }

    /**
     * The program's {@code glow}: 1 at the outline, 1/e {@link Look#GLOW_DP} in, and nothing from
     * {@link Look#GLOW_REACH_DP} in, where the program skips the term.
     */
    static float glow(float insidePx, float density) {
        if (insidePx >= Look.GLOW_REACH_DP * density) return 0f;
        return (float) Math.exp(-Math.max(insidePx, 0f) / (Look.GLOW_DP * density));
    }

    /**
     * The program's {@code k}, with no finger on the glass: the gathered light's strength at a
     * pixel {@code insidePx} in from the outline, whose outline faces the light by
     * {@code share} ({@link #gatherShare}), over a sample of luma {@code luma}.
     */
    static float gatherK(float insidePx, float density, float share, float luma) {
        return glow(insidePx, density) * share * (1f - Look.PALE_CUT * luma);
    }

    /**
     * The program's {@code heldK}: the light's strength {@code k} held under {@code kMax}, the
     * most the sample's brightest channel takes before it clips. Exactly {@code k} up to
     * {@link Look#HEADROOM_KNEE} of {@code kMax}; past it {@code kMax (1 - 1 / (4 r))} with
     * {@code r = k / kMax}, which joins the straight line there with the same slope and only
     * nears {@code kMax}.
     */
    static float heldK(float k, float kMax) {
        float r = k / Math.max(kMax, 1e-6f);
        return r <= Look.HEADROOM_KNEE ? k : kMax * (1f - Look.HEADROOM_KNEE * Look.HEADROOM_KNEE / r);
    }

    /**
     * The headroom {@link #heldK} holds the light under, for a premultiplied sample whose
     * brightest channel is {@code peak} at alpha {@code a}: the {@code k} at which that channel,
     * brightened by {@link Look#GAIN} and lifted by {@link Look#LIFT}, would reach the alpha.
     */
    static float headroomK(float peak, float a) {
        return Math.max(a - peak, 0f) / (Look.GAIN * peak + Look.LIFT);
    }

    /**
     * The program's gathered light on a premultiplied sample {@code (r, g, b, a)}, with no finger
     * on the glass, into {@code out}: darkened by {@link Look#EDGE_SHADE} on the glow, brightened
     * in proportion to itself by {@link Look#GAIN} plus {@link Look#LIFT} of grey, both times
     * {@code k} held under the brightest channel's headroom ({@link #heldK}), and held under the
     * alpha as a guard.
     */
    static void gatheredLit(float r, float g, float b, float a, float insidePx, float density,
                            float nx, float ny, @NonNull float[] out) {
        gatheredLit(r, g, b, a, insidePx, density, nx, ny, 1f, out);
    }

    /** {@link #gatheredLit(float, float, float, float, float, float, float, float, float[])} raised by {@code edgeGain} ({@code uEdgeGain}). */
    static void gatheredLit(float r, float g, float b, float a, float insidePx, float density,
                            float nx, float ny, float edgeGain, @NonNull float[] out) {
        float luma = r * LUMA_R + g * LUMA_G + b * LUMA_B;
        float k = gatherK(insidePx, density, gatherShare(nx, ny), luma) * edgeGain;
        float shade = 1f - glow(insidePx, density) * Look.EDGE_SHADE;
        float sr = r * shade, sg = g * shade, sb = b * shade;
        if (k > 0f) k = heldK(k, headroomK(Math.max(sr, Math.max(sg, sb)), a));
        float gain = 1f + Look.GAIN * k;
        float lift = Look.LIFT * k;
        out[0] = Math.min(a, sr * gain + lift);
        out[1] = Math.min(a, sg * gain + lift);
        out[2] = Math.min(a, sb * gain + lift);
    }

    /** The key lens's own strength and feather, which no knob reaches: the press reads the same in both modes. */
    private static final float LENS_STRENGTH = 0.20f;
    private static final float LENS_FEATHER_DP = 10f;

    /**
     * How far the picture is drawn toward a resting finger, as a share of the distance to it at
     * the touch's centre: a shallow lens, a few px at most on a phone, so the glass answers the
     * finger without the picture swimming.
     */
    static final float TOUCH_LENS = 0.10f;

    /**
     * One compiled program and the uniforms it was last given. Every setter compares before it
     * writes, so a draw that re-states an unmoved surface pushes nothing to the GPU. Held only on
     * API 33+; the outer class keeps no field of this type, so a phone below that never loads it.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static final class Program {

        @NonNull private final RuntimeShader mShader;
        private final float mDensity;
        private float mScaleX = Float.NaN, mScaleY, mOffsetX, mOffsetY;
        /** The local matrix last written, and scratch to read the next one into. */
        private final float[] mLocal = new float[9];
        private final float[] mLocalNext = new float[9];
        private boolean mLocalWritten;
        private float mLeft = Float.NaN, mTop, mRight, mBottom, mRadius;
        @Nullable private Look mLook;
        private boolean mLensOn;
        private float mLensCx, mLensCy, mLensHx, mLensHy, mLensRadius, mLensIntensity;
        /** Whether {@code sharp} has a shader bound: only then may the program read it. */
        private boolean mSharpBound;
        private float mClarityWritten = Float.NaN;
        private boolean mTouchOn;
        private float mTouchX, mTouchY, mTouchRadius, mTouchIntensity;

        private Program(float density) {
            mShader = new RuntimeShader(AGSL);
            mDensity = density;
            mShader.setFloatUniform("uDensity", density);
            mShader.setFloatUniform("uLensStrength", LENS_STRENGTH);
            mShader.setFloatUniform("uLensFeather", density * LENS_FEATHER_DP);
            mShader.setFloatUniform("uTouchLens", TOUCH_LENS);
            setAim(1f, 1f, 0f, 0f);
            setLocal(null);
            setLook(Look.DEFAULT);
            mLensOn = true; // so the clear below is a write, not a skip
            clearLens();
            mTouchOn = true;
            clearTouch();
        }

        /**
         * Compiles the program, or answers null when this phone's driver refuses it — every
         * caller then draws the frame plain, which is the default look.
         */
        @Nullable
        public static Program create(float density) {
            try {
                return new Program(density);
            } catch (Throwable t) {
                Logger.logStackTraceWithMessage("GlassRefraction", "Glass refraction shader failed; drawing plain", t);
                return null;
            }
        }

        /** The shader, for {@code RenderEffect.createRuntimeShaderEffect(shader, "content")}. */
        @NonNull
        public RuntimeShader shader() {
            return mShader;
        }

        /**
         * Makes {@code paint} draw through this program. Here rather than at the caller so that a
         * drawable that runs from API 26 never names {@code RuntimeShader} in its own code.
         */
        public void applyTo(@NonNull Paint paint) {
            paint.setShader(mShader);
        }

        public float density() {
            return mDensity;
        }

        /**
         * The frame this program samples, drawn as a paint shader. The child is sampled by its own
         * pixels — the aim is a uniform, see {@link #setAim} — so its local matrix is cleared, and
         * it is filtered, since the frame is held at blur resolution and scaled up here. Always
         * written: the program captures the child as it is now, and a matrix the plain path set
         * since would otherwise be baked in.
         */
        public void setInput(@NonNull Shader content) {
            content.setLocalMatrix(null);
            if (content instanceof BitmapShader) {
                ((BitmapShader) content).setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
            }
            mShader.setInputShader("content", content);
            // The clearer picture is the frost itself until a caller hands one in, so the mix
            // reads back what it was given; the clarity term is then free to be written.
            mShader.setInputShader("sharp", content);
            mSharpBound = true;
            syncClarity();
        }

        /**
         * A clearer picture of the same wallpaper, captured for the same frame rect as the
         * content but held at its own resolution: {@code pxScaleX}/{@code pxScaleY} are its
         * pixels per content pixel (its width over the content's), so the program, which reads
         * everything in content pixels, lands on the same spot in it. Null hands the content back
         * as its own clear picture, which is no clarity at all. Under a {@code RenderEffect},
         * where the content is the view and nothing can be bound here, the clarity term stays at
         * zero, so the program never reads an unbound shader.
         */
        public void setClarityInput(@Nullable Shader sharp, float pxScaleX, float pxScaleY) {
            if (sharp == null) {
                mSharpBound = false;
                syncClarity();
                return;
            }
            Matrix toSharp = new Matrix();
            toSharp.setScale(1f / Math.max(1e-4f, pxScaleX), 1f / Math.max(1e-4f, pxScaleY));
            sharp.setLocalMatrix(toSharp);
            if (sharp instanceof BitmapShader) {
                ((BitmapShader) sharp).setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
            }
            mShader.setInputShader("sharp", sharp);
            mSharpBound = true;
            syncClarity();
        }

        /** True once a shader is bound as the clearer picture, by {@link #setInput} or {@link #setClarityInput}. */
        public boolean hasClarityInput() {
            return mSharpBound;
        }

        /** {@code uClarity}: the look's clarity while a sharp shader is bound, zero otherwise. */
        private void syncClarity() {
            Look look = mLook;
            float clarity = look != null && mSharpBound ? look.clarity() : 0f;
            if (clarity == mClarityWritten) return;
            mClarityWritten = clarity;
            mShader.setFloatUniform("uClarity", clarity);
        }

        /**
         * Where the content's pixel {@code (0, 0)} lands and how big its pixels are, in the
         * coordinates the surface draws in: the {@link SharedFrameDrawable#aim} matrix as four
         * numbers. Identity under a {@code RenderEffect}, where the content is the view itself.
         */
        public void setAim(float scaleX, float scaleY, float offsetX, float offsetY) {
            if (mScaleX == scaleX && mScaleY == scaleY && mOffsetX == offsetX && mOffsetY == offsetY) return;
            mScaleX = scaleX;
            mScaleY = scaleY;
            mOffsetX = offsetX;
            mOffsetY = offsetY;
            mShader.setFloatUniform("uFrameScale", Math.max(1e-4f, scaleX), Math.max(1e-4f, scaleY));
            mShader.setFloatUniform("uFrameOffset", offsetX, offsetY);
        }

        /**
         * What the surface's transform does past {@link #setAim}, from its own px to px from its
         * origin on screen: {@link GlassAnchor.Aim#local}, a scale or a perspective on a wall page
         * that is sunk or tipped. Null, the default, is identity — every surface but the wall's,
         * and the wall's at rest, which then samples exactly as it did before there was one.
         */
        public void setLocal(@Nullable Matrix local) {
            float[] next = mLocalNext;
            if (local == null) System.arraycopy(IDENTITY, 0, next, 0, IDENTITY.length);
            else local.getValues(next);
            if (mLocalWritten && Arrays.equals(next, mLocal)) return;
            mLocalWritten = true;
            System.arraycopy(next, 0, mLocal, 0, next.length);
            mShader.setFloatUniform("uLocalX", next[0], next[1], next[2]);
            mShader.setFloatUniform("uLocalY", next[3], next[4], next[5]);
            mShader.setFloatUniform("uLocalW", next[6], next[7], next[8]);
        }

        /** The rounded rect the rim runs along, in the surface's own coordinates. */
        public void setRect(float left, float top, float right, float bottom, float cornerRadiusPx) {
            if (mLeft == left && mTop == top && mRight == right && mBottom == bottom && mRadius == cornerRadiusPx) return;
            mLeft = left;
            mTop = top;
            mRight = right;
            mBottom = bottom;
            mRadius = cornerRadiusPx;
            mShader.setFloatUniform("uRectMin", left, top);
            mShader.setFloatUniform("uRectMax", Math.max(left + 1f, right), Math.max(top + 1f, bottom));
            mShader.setFloatUniform("uRadius", Math.max(0f, cornerRadiusPx));
        }

        /** The knobs, converted at this program's density. The gathered light is fixed and takes none. */
        public void setLook(@NonNull Look look) {
            if (look.equals(mLook)) return;
            mLook = look;
            mShader.setFloatUniform("uBand", look.bandPx(mDensity));
            mShader.setFloatUniform("uStrength", look.strengthPx(mDensity));
            mShader.setFloatUniform("uDisp", look.dispersion());
            mShader.setFloatUniform("uEdgeGain", look.edgeGain());
            syncClarity();
        }

        /**
         * A finger resting on the glass at {@code (x, y)} in the surface's own coordinates: its
         * light reaches {@code radiusPx}, at {@code intensity} (0..1, the press's fade). Written
         * on every move and every fade frame, so a caller animates it; the program itself never
         * does.
         */
        public void setTouch(float x, float y, float radiusPx, float intensity) {
            radiusPx = Math.max(1f, radiusPx);
            if (mTouchOn && mTouchX == x && mTouchY == y && mTouchRadius == radiusPx
                && mTouchIntensity == intensity) return;
            mTouchOn = true;
            mTouchX = x;
            mTouchY = y;
            mTouchRadius = radiusPx;
            mTouchIntensity = intensity;
            mShader.setFloatUniform("uTouchActive", intensity);
            mShader.setFloatUniform("uTouch", x, y);
            mShader.setFloatUniform("uTouchRadius", radiusPx);
        }

        /** No finger on the glass: the resting look, which is every surface nobody is touching. */
        public void clearTouch() {
            if (!mTouchOn) return;
            mTouchOn = false;
            mTouchIntensity = 0f;
            mShader.setFloatUniform("uTouchActive", 0f);
            mShader.setFloatUniform("uTouch", 0f, 0f);
            mShader.setFloatUniform("uTouchRadius", 1f);
        }

        /**
         * The pressed extra key's lens, in the surface's own coordinates: a stadium pill at
         * {@code (cx, cy)} with half extents {@code (hx, hy)}, held at {@code intensity} (0..1).
         */
        public void setLens(float cx, float cy, float hx, float hy, float cornerRadiusPx, float intensity) {
            hx = Math.max(1f, hx);
            hy = Math.max(1f, hy);
            if (mLensOn && mLensCx == cx && mLensCy == cy && mLensHx == hx && mLensHy == hy
                && mLensRadius == cornerRadiusPx && mLensIntensity == intensity) return;
            mLensOn = true;
            mLensCx = cx;
            mLensCy = cy;
            mLensHx = hx;
            mLensHy = hy;
            mLensRadius = cornerRadiusPx;
            mLensIntensity = intensity;
            mShader.setFloatUniform("uLensActive", intensity);
            mShader.setFloatUniform("uLensCenter", cx, cy);
            mShader.setFloatUniform("uLensHalf", hx, hy);
            mShader.setFloatUniform("uLensRadius", cornerRadiusPx);
        }

        /** No lens: the rim and the bend alone, which is every surface but a dock under a pressed key. */
        public void clearLens() {
            if (!mLensOn) return;
            mLensOn = false;
            mLensIntensity = 0f;
            mShader.setFloatUniform("uLensActive", 0f);
            mShader.setFloatUniform("uLensCenter", 0f, 0f);
            mShader.setFloatUniform("uLensHalf", 1f, 1f);
            mShader.setFloatUniform("uLensRadius", 0f);
        }
    }
}
