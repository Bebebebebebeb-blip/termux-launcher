package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Rect;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.regex.Pattern;

/**
 * The pure half of the glass: how the stored knobs become uniforms, the Java mirrors of the
 * program's pull, hairline and colour split, and where a surface's rim runs.
 * The program itself compiles only on a phone; nothing here needs one, though its text is read to
 * check it declares every uniform the Java side writes and runs the expressions the mirrors do.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlassRefractionTest {

    private static final float DENSITY = 2.625f; // pong, 420 dpi

    @Test
    public void theDefaultLookIsTheDocksOwnRefraction() {
        // The band and the pull were literals in the activity's shader setup: 20 dp band, 9 dp pull.
        GlassRefraction.Look look = GlassRefraction.Look.DEFAULT;
        assertEquals(20f * DENSITY, look.bandPx(DENSITY), 1e-4f);
        assertEquals(9f * DENSITY, look.strengthPx(DENSITY), 1e-4f);
    }

    @Test
    public void theKnobsMapToUniformsAtTheScreensDensity() {
        GlassRefraction.Look look = new GlassRefraction.Look(12, 30, 50);
        assertEquals(12f * DENSITY, look.strengthPx(DENSITY), 1e-4f);
        assertEquals(30f * DENSITY, look.bandPx(DENSITY), 1e-4f);
        assertEquals(0f, new GlassRefraction.Look(0, 1, 0).strengthPx(DENSITY), 1e-4f);
    }

    @Test
    public void theEdgeTermsAreOffByDefaultSoExistingLooksAreUnchanged() {
        GlassRefraction.Look look = GlassRefraction.Look.DEFAULT;
        assertEquals(0, look.specularPercent);
        assertEquals(0, look.dispersionPercent);
        assertEquals(0f, look.dispersion(), 0f);
        assertEquals("the three-knob constructor leaves them off",
            new GlassRefraction.Look(12, 30, 50), new GlassRefraction.Look(12, 30, 50, 0, 0));
        assertEquals(0, look.clarityPercent);
        assertEquals(0f, look.clarity(), 0f);
        assertEquals("the five-knob constructor leaves clarity off",
            new GlassRefraction.Look(12, 30, 50, 3, 4), new GlassRefraction.Look(12, 30, 50, 3, 4, 0));
    }

    @Test
    public void clarityMapsToItsUniformAndIsClamped() {
        // uClarity: how much of the clearer picture the rim shows.
        assertEquals(0.6f * GlassRefraction.Look.MAX_CLARITY,
            new GlassRefraction.Look(9, 20, 18, 0, 0, 60).clarity(), 1e-4f);
        assertEquals(GlassRefraction.Look.MAX_CLARITY,
            new GlassRefraction.Look(9, 20, 18, 0, 0, 100).clarity(), 1e-4f);
        assertEquals(GlassRefraction.Look.MAX_CLARITY,
            new GlassRefraction.Look(9, 20, 18, 0, 0, 400).clarity(), 1e-4f);
        assertEquals(0f, new GlassRefraction.Look(9, 20, 18, 0, 0, -5).clarity(), 0f);
    }

    @Test
    public void dispersionIsAShareOfThePullAndIsClamped() {
        // 100% parts the colours by a twentieth of the pull; Clear's 40% is Haze Clear's 0.02.
        assertEquals(0.05f, GlassRefraction.Look.MAX_DISPERSION_SHARE, 0f);
        assertEquals(0.02f, new GlassRefraction.Look(27, 18, 10, 0, 40).dispersion(), 1e-6f);
        assertEquals(GlassRefraction.Look.MAX_DISPERSION_SHARE,
            new GlassRefraction.Look(0, 1, 0, 0, 100).dispersion(), 1e-4f);
        assertEquals(GlassRefraction.Look.MAX_DISPERSION_SHARE,
            new GlassRefraction.Look(0, 1, 0, 0, 400).dispersion(), 1e-4f);
        assertEquals(0f, new GlassRefraction.Look(0, 1, 0, 0, -5).dispersion(), 0f);
    }

    @Test
    public void theColourSplitLivesAtTheCornersAndScalesWithThePull() {
        float bx = 540f, by = 90f; // a dock-sized surface's half size
        assertEquals("nothing at the middle of the top edge", 0f,
            GlassRefraction.cornerWeight(0f, -by, bx, by), 0f);
        assertEquals("nothing at the middle of a side", 0f,
            GlassRefraction.cornerWeight(bx, 0f, bx, by), 0f);
        assertEquals("all of it at a corner", 1f, GlassRefraction.cornerWeight(-bx, -by, bx, by), 1e-6f);
        assertEquals("held at one past the corner", 1f,
            GlassRefraction.cornerWeight(-bx - 10f, -by - 10f, bx, by), 0f);
        float last = -1f;
        for (int i = 0; i <= 10; i++) {
            float w = GlassRefraction.cornerWeight(-bx * i / 10f, -by, bx, by);
            assertTrue("grows toward the corner", w >= last);
            last = w;
        }
        // The split the program takes is pull * uDisp * weight: about a px at Clear's corner,
        // a hairline in, and none where nothing is bent.
        GlassRefraction.Look clear = new GlassRefraction.Look(27, 18, 10, 0, 40, 76);
        float rimW = GlassRefraction.Look.rimWidthPx(DENSITY);
        float rimPull = GlassRefraction.pullPx(rimW, clear.bandPx(DENSITY), clear.strengthPx(DENSITY), by, rimW);
        float cornerSplit = rimPull * clear.dispersion() * GlassRefraction.cornerWeight(-bx, -by, bx, by);
        assertTrue("about a px at Clear's corner: " + cornerSplit, cornerSplit > 0.5f && cornerSplit < 2f);
        float facePull = GlassRefraction.pullPx(clear.bandPx(DENSITY), clear.bandPx(DENSITY),
            clear.strengthPx(DENSITY), by, rimW);
        assertEquals(0f, facePull * clear.dispersion(), 0f);
    }

    @Test
    public void theEdgeTermsAreInTheLooksValueIdentity() {
        GlassRefraction.Look base = new GlassRefraction.Look(9, 20, 18, 0, 0);
        assertEquals(base, GlassRefraction.Look.DEFAULT);
        assertNotEquals(base, new GlassRefraction.Look(9, 20, 18, 1, 0));
        assertNotEquals(base, new GlassRefraction.Look(9, 20, 18, 0, 1));
        assertNotEquals(new GlassRefraction.Look(9, 20, 18, 1, 0).hashCode(),
            new GlassRefraction.Look(9, 20, 18, 0, 1).hashCode());
    }

    @Test
    public void theProgramDeclaresEveryUniformTheJavaSideWrites() {
        // A uniform the program does not declare throws on the write, and the surface drops to plain.
        String[] written = {
            "uFrameScale", "uFrameOffset", "uLocalX", "uLocalY", "uLocalW", "uRectMin", "uRectMax",
            "uRadius", "uBand", "uStrength", "uDisp", "uDensity",
            "uLensActive", "uLensCenter", "uLensHalf", "uLensRadius", "uLensStrength", "uLensFeather",
            "uClarity", "uTouchActive", "uTouch", "uTouchRadius", "uTouchLens", "uGathered",
        };
        for (String name : written) {
            assertTrue(name + " is declared",
                Pattern.compile("uniform \\w+ " + name + ";").matcher(GlassRefraction.AGSL).find());
        }
        assertTrue(GlassRefraction.AGSL.contains("uniform shader content;"));
        assertTrue("the clearer picture is a second input", GlassRefraction.AGSL.contains("uniform shader sharp;"));
        // The knobs the hairline no longer takes, and the slab lens and thickness shading neither
        // reference draws, are gone from the program.
        for (String gone : new String[] {"uRim", "uLobeK", "uShade", "uThick", "uSlab", "uRimW",
            "slabOffset", "softClamp", "kKnee"}) {
            assertFalse(gone + " is gone", Pattern.compile("\\b" + gone + "\\b").matcher(GlassRefraction.AGSL).find());
        }
    }

    @Test
    public void noPixelReadsMoreThanSixSamples() {
        // 3 + 3 in a dispersed band, 1 + 1 elsewhere in the band and 1 on the face. Four of each in
        // the text: three on the split path, one plain. The eased outermost dp moves the sample
        // rather than reading a second one.
        assertEquals(4, count(GlassRefraction.AGSL, "content.eval("));
        assertEquals(4, count(GlassRefraction.AGSL, "sharp.eval("));
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) n++;
        return n;
    }

    @Test
    public void thePullIsHeldUnderOneAndAHalfBands() {
        assertEquals(1.5f, GlassRefraction.Look.MAX_PULL_SHARE, 0f);
        assertEquals("a narrow band holds it at 1.5 bands", 1.5f * 10f * DENSITY,
            new GlassRefraction.Look(30, 10, 30).strengthPx(DENSITY), 1e-3f);
        assertEquals("a shallow bend is untouched", 6f * DENSITY,
            new GlassRefraction.Look(6, 28, 30).strengthPx(DENSITY), 1e-4f);
        // Every shipped Look sits on or under the hold, so the slider and the pull agree; Clear's
        // is exactly one and a half bands.
        int[][] looks = {{27, 18}, {24, 16}, {21, 14}};
        for (int[] look : looks)
            assertEquals(look[0] * DENSITY,
                new GlassRefraction.Look(look[0], look[1], 10).strengthPx(DENSITY), 1e-4f);
    }

    @Test
    public void theProfileIsHazesEdgeProfile() {
        // exp(-t / 0.225) (1 - t²)²: the whole bend at the rim, nothing at the band's inner edge.
        assertEquals(1f, GlassRefraction.edgeProfile(0f), 0f);
        assertEquals(0f, GlassRefraction.edgeProfile(1f), 0f);
        assertEquals((float) (Math.exp(-0.5 / 0.225) * 0.75 * 0.75), GlassRefraction.edgeProfile(0.5f), 1e-6f);
        // A narrow ring: under a third a quarter of the way in, under a tenth half way.
        assertTrue(GlassRefraction.edgeProfile(0.25f) < 0.3f);
        assertTrue(GlassRefraction.edgeProfile(0.5f) < 0.1f);
        float last = 2f;
        for (int i = 0; i <= 20; i++) {
            float share = GlassRefraction.edgeProfile(i / 20f);
            assertTrue("falls all the way in at " + i, share <= last);
            last = share;
        }
        assertTrue(GlassRefraction.AGSL.contains("const float kDecay = 0.225;"));
        assertEquals(0.225f, GlassRefraction.EDGE_DECAY, 0f);
        assertTrue(GlassRefraction.AGSL.contains("float u = 1.0 - t * t;"));
        assertTrue(GlassRefraction.AGSL.contains("return exp(-t / kDecay) * u * u;"));
    }

    @Test
    public void theBendLivesUnderTheRimAndTheFaceStaysWhereItIs() {
        float band = 18f * DENSITY, strength = 27f * DENSITY, big = 1000f;
        float rimW = GlassRefraction.Look.rimWidthPx(DENSITY);
        // A hairline in, nearly the whole strength.
        float atHairline = GlassRefraction.pullPx(rimW, band, strength, big, rimW);
        assertEquals(strength * GlassRefraction.edgeProfile(rimW / band), atHairline, 1e-3f);
        assertTrue(atHairline > 0.75f * strength);
        // Zero at the band's inner edge and everywhere past it.
        assertEquals(0f, GlassRefraction.pullPx(band, band, strength, big, rimW), 0f);
        assertEquals(0f, GlassRefraction.pullPx(band * 3f, band, strength, big, rimW), 0f);
        // A narrow ring: past a third of the band the pull is under a fifth of the strength.
        assertTrue(GlassRefraction.pullPx(band / 3f, band, strength, big, rimW) < 0.2f * strength);
        // Falling from a hairline in all the way to the band's inner edge.
        float last = Float.MAX_VALUE;
        for (int i = 0; i <= 20; i++) {
            float inside = rimW + (band - rimW) * i / 20f;
            float pull = GlassRefraction.pullPx(inside, band, strength, big, rimW);
            assertTrue("falls toward the face at " + i, pull <= last);
            last = pull;
        }
        // The program runs exactly these expressions.
        assertTrue(GlassRefraction.AGSL.contains("float t = clamp(inside / uBand, 0.0, 1.0);"));
        assertTrue(GlassRefraction.AGSL.contains(
            "float pull = edgeProfile(t) * min(uStrength, min(b.x, b.y)) * edge;"));
        // The pull is an offset that vanishes on the face, so a face pixel samples exactly fragCoord.
        assertTrue(GlassRefraction.AGSL.contains("float2 sampleCoord = fragCoord - n * pull;"));
    }

    @Test
    public void theOutlineIsContinuousWithThePictureOutside() {
        // Across the outermost hairline the pull eases to nothing, so the outline's own pixel shows
        // the picture right there: no jump of a band's worth at the rim (Haze's edge softness).
        float band = 18f * DENSITY, strength = 27f * DENSITY, big = 1000f;
        float rimW = GlassRefraction.Look.rimWidthPx(DENSITY);
        assertEquals(0f, GlassRefraction.pullPx(0f, band, strength, big, rimW), 0f);
        assertEquals("outside the outline nothing is bent", 0f,
            GlassRefraction.pullPx(-3f, band, strength, big, rimW), 0f);
        float last = 0f;
        for (int i = 0; i <= 10; i++) {
            float pull = GlassRefraction.pullPx(rimW * i / 10f, band, strength, big, rimW);
            assertTrue("eases in across the hairline at " + i, pull >= last);
            last = pull;
        }
        assertTrue(GlassRefraction.AGSL.contains("float rimW = max(kMinRimPx, kRimDp * uDensity);"));
        assertTrue(GlassRefraction.AGSL.contains("float edge = smoothstep(0.0, rimW, inside);"));
    }

    @Test
    public void theBendIsHeldAtHalfTheShortSide() {
        // Haze's plain cap: a small pane (half its short side 30 px) under a deep bend reaches in
        // at most to its middle line, at the rim, and the profile only shortens that inward.
        float half = 30f, band = 60f * DENSITY, strength = 1.5f * band;
        float rimW = GlassRefraction.Look.rimWidthPx(DENSITY);
        for (int i = 0; i <= 60; i++) {
            float inside = i * 0.5f;
            float pull = GlassRefraction.pullPx(inside, band, strength, half, rimW);
            assertTrue("at " + inside + ": " + pull, pull <= half);
        }
        assertEquals("a dock is held at its own half height", half * GlassRefraction.edgeProfile(rimW / band),
            GlassRefraction.pullPx(rimW, band, strength, half, rimW), 1e-3f);
        assertEquals("a large surface is not", strength * GlassRefraction.edgeProfile(rimW / band),
            GlassRefraction.pullPx(rimW, band, strength, 1000f, rimW), 1e-3f);
    }

    @Test
    public void theBendMovesThePullOneWay() {
        float band = 18f * DENSITY;
        float rimW = GlassRefraction.Look.rimWidthPx(DENSITY);
        float lastPull = -1f, lastBand = 0f, lastDisp = -1f;
        for (int v = 0; v <= 100; v++) {
            GlassRefraction.Look look = new GlassRefraction.Look(v * 30 / 100, Math.max(1, v * 60 / 100), 10, 0, v);
            float pull = GlassRefraction.pullPx(rimW, band,
                new GlassRefraction.Look(v * 30 / 100, 18, 10).strengthPx(DENSITY), 1000f, rimW);
            assertTrue("bend", pull >= lastPull);
            assertTrue("edge width", look.bandPx(DENSITY) >= lastBand);
            assertTrue("dispersion", look.dispersion() >= lastDisp);
            lastPull = pull;
            lastBand = look.bandPx(DENSITY);
            lastDisp = look.dispersion();
        }
    }

    @Test
    public void theRimLightPeaksTowardTheLightAndIsDarkOppositeIt() {
        float k = GlassRefraction.Look.LOBE_K;
        assertEquals(8f, k, 0f);
        float d = (float) Math.sqrt(0.5);
        assertEquals("full along the top edge, which faces the light", 1f, GlassRefraction.lobe(0f, -1f, k), 1e-4f);
        assertEquals("a sixteenth round the corner", 0.0625f, GlassRefraction.lobe(-d, -d, k), 1e-3f);
        assertEquals("nothing on the bottom edge", 0f, GlassRefraction.lobe(0f, 1f, k), 0f);
        assertEquals("nothing on the right edge", 0f, GlassRefraction.lobe(1f, 0f, k), 0f);
        assertEquals("nothing on the left edge", 0f, GlassRefraction.lobe(-1f, 0f, k), 0f);
        // Falling off from the top edge round the corner arc to the side.
        float last = 2f;
        for (int deg = 0; deg <= 90; deg += 5) {
            double a = Math.toRadians(270 + deg);
            float l = GlassRefraction.lobe((float) Math.cos(a), (float) Math.sin(a), k);
            assertTrue("falls off at " + deg, l <= last);
            last = l;
        }
        assertTrue(GlassRefraction.AGSL.contains("const float2 kLight = float2(0.0, -1.0);"));
        assertTrue(GlassRefraction.AGSL.contains("const float kLobeK = 8.0;"));
        assertTrue(GlassRefraction.AGSL.contains("return c > 0.0 ? pow(c, k) : 0.0;"));
    }

    @Test
    public void theHairlineIsFixedMaterialWhateverTheStoredLightSays() {
        assertEquals(0.45f, GlassRefraction.Look.LIGHT_ALPHA, 0f);
        assertEquals(0.12f, GlassRefraction.Look.SHADE, 0f);
        assertTrue(GlassRefraction.AGSL.contains("const float kRim = 0.45;"));
        assertTrue(GlassRefraction.AGSL.contains("const float kShade = 0.12;"));
        // Two looks that differ only in the stored light and specular draw the same uniforms.
        GlassRefraction.Look faint = new GlassRefraction.Look(24, 16, 10, 0, 0, 0);
        GlassRefraction.Look loud = new GlassRefraction.Look(24, 16, 90, 80, 0, 0);
        assertEquals(faint.strengthPx(DENSITY), loud.strengthPx(DENSITY), 0f);
        assertEquals(faint.bandPx(DENSITY), loud.bandPx(DENSITY), 0f);
        assertEquals(faint.dispersion(), loud.dispersion(), 0f);
        assertEquals(faint.clarity(), loud.clarity(), 0f);
    }

    @Test
    public void theRimLightIsBoundedAndTheDarkEdgeSitsUnderIt() {
        float rimW = GlassRefraction.Look.rimWidthPx(DENSITY);
        for (int i = 0; i <= 20; i++) {
            float face = i / 20f;
            float lit = GlassRefraction.edgeLit(face, 0f, rimW, 1f);
            assertTrue("never past white at " + face, lit <= 1f);
            assertTrue("never more than the ceiling of white over the face at " + face,
                lit <= face + GlassRefraction.Look.LIGHT_ALPHA + 1e-6f);
            float dark = GlassRefraction.edgeLit(face, 0f, rimW, 0f);
            assertEquals("where the light does not reach, only the dark edge",
                face * (1f - GlassRefraction.Look.SHADE), dark, 1e-6f);
            assertTrue(lit >= dark);
        }
        // A hairline in, the picture is untouched.
        assertEquals(0.4f, GlassRefraction.edgeLit(0.4f, rimW, rimW, 1f), 0f);
        assertEquals(0.4f, GlassRefraction.edgeLit(0.4f, 5f * rimW, rimW, 1f), 0f);
        assertTrue(GlassRefraction.AGSL.contains("float rim = 1.0 - edge;"));
        assertTrue(GlassRefraction.AGSL.contains("col.rgb = col.rgb * half(1.0 - rim * kShade);"));
        assertTrue(GlassRefraction.AGSL.contains("float light = rim * kRim * (lobe(n, kLobeK) + 1.5 * touchG);"));
        assertTrue(GlassRefraction.AGSL.contains("col.rgb = mix(col.rgb, half3(1.0), half(clamp(light, 0.0, 1.0)));"));
    }

    @Test
    public void theHairlineIsADpAndNeverUnderTwoPixels() {
        assertEquals(DENSITY, GlassRefraction.Look.rimWidthPx(DENSITY), 1e-4f);
        assertEquals("never under two pixels", 2f, GlassRefraction.Look.rimWidthPx(1f), 1e-4f);
        assertEquals(2f, GlassRefraction.Look.rimWidthPx(0.4f), 1e-4f);
        assertTrue(GlassRefraction.AGSL.contains("const float kRimDp = 1.0;"));
        assertTrue(GlassRefraction.AGSL.contains("const float kMinRimPx = 2.0;"));
        assertEquals(1f, GlassRefraction.Look.RIM_WIDTH_DP, 0f);
        assertEquals(2f, GlassRefraction.Look.MIN_RIM_WIDTH_PX, 0f);
    }

    @Test
    public void thePressedKeyGlowIsItsOwnConstant() {
        assertTrue(GlassRefraction.AGSL.contains("const float kKeyGlow = 0.15;"));
        assertEquals(0.15f, GlassRefraction.KEY_GLOW, 0f);
        assertTrue(GlassRefraction.AGSL.contains(
            "col.rgb = col.rgb + half3(lensGlow * kKeyGlow + 0.045 * touchG * touchG);"));
    }

    @Test
    public void aSeamClearsTheHairlineAndItsContourOnANarrowBand() {
        // Solid's 1 dp edge ends inside the hairline; the seam is pushed past both regardless.
        GlassRefraction.Look narrow = new GlassRefraction.Look(0, 1, 100);
        assertTrue(GlassRefraction.seamReachPx(narrow, DENSITY, 0f) > GlassRefraction.Look.rimReachPx(DENSITY));
        assertTrue(GlassRefraction.seamReachPx(narrow, 1f, 0f) > GlassRefraction.Look.rimReachPx(1f));
    }

    @Test
    public void aBandNeverCollapsesToNothing() {
        // The shader divides by the band; a zero would push every pixel to the rim.
        assertTrue(new GlassRefraction.Look(9, 0, 32).bandPx(DENSITY) >= 1f);
        assertTrue(new GlassRefraction.Look(9, 0, 32).bandPx(0f) >= 1f);
    }

    @Test
    public void aLookIsAValue() {
        GlassRefraction.Look one = new GlassRefraction.Look(9, 20, 18);
        assertEquals(one, GlassRefraction.Look.DEFAULT);
        assertEquals(one.hashCode(), GlassRefraction.Look.DEFAULT.hashCode());
        assertNotEquals(one, new GlassRefraction.Look(10, 20, 18));
        assertNotEquals(one, new GlassRefraction.Look(9, 21, 18));
        assertNotEquals(one, new GlassRefraction.Look(9, 20, 19));
        assertNotEquals(one, new GlassRefraction.Look(9, 20, 18, 0, 0, 1));
    }

    @Test
    public void theRimRunsAlongTheBoundsUnlessAnEdgeIsASeam() {
        Rect bounds = new Rect(0, 0, 1080, 300);
        float[] rim = new float[4];
        GlassRefraction.rimRect(rim, bounds, 100f, 0);
        assertEquals(0f, rim[0], 0f);
        assertEquals(0f, rim[1], 0f);
        assertEquals(1080f, rim[2], 0f);
        assertEquals(300f, rim[3], 0f);

        // The keyboard host over the under-pill strip: its bottom edge is interior glass.
        GlassRefraction.rimRect(rim, bounds, 100f, GlassRefraction.SEAM_BOTTOM);
        assertEquals(0f, rim[1], 0f);
        assertEquals(400f, rim[3], 0f);

        // The window bar under the status band: its top edge is.
        GlassRefraction.rimRect(rim, bounds, 100f, GlassRefraction.SEAM_TOP);
        assertEquals(-100f, rim[1], 0f);
        assertEquals(300f, rim[3], 0f);

        GlassRefraction.rimRect(rim, bounds, 40f,
            GlassRefraction.SEAM_LEFT | GlassRefraction.SEAM_RIGHT);
        assertEquals(-40f, rim[0], 0f);
        assertEquals(1120f, rim[2], 0f);
    }

    @Test
    public void aFractionalRimIsTheSameRuleAsTheBoundsOne() {
        // A corner tab mid-slide stands at fractional bounds; its rim follows the one rule.
        float[] fromRect = new float[4];
        float[] fromFloats = new float[4];
        int seams = GlassRefraction.SEAM_TOP | GlassRefraction.SEAM_RIGHT;
        GlassRefraction.rimRect(fromRect, new Rect(10, 20, 110, 52), 30f, seams);
        GlassRefraction.rimRect(fromFloats, 10f, 20f, 110f, 52f, 30f, seams);
        for (int i = 0; i < 4; i++) assertEquals(fromRect[i], fromFloats[i], 0f);

        GlassRefraction.rimRect(fromFloats, 10.5f, -12.25f, 110.5f, 19.75f, 30f, seams);
        assertEquals(10.5f, fromFloats[0], 0f);
        assertEquals(-42.25f, fromFloats[1], 0f);
        assertEquals(140.5f, fromFloats[2], 0f);
        assertEquals(19.75f, fromFloats[3], 0f);
    }

    // --------------------------------------------- the gathered light (glass plan 2026-10-16, E-1)

    @Test
    public void theGatheredEdgeIsOffByDefault() {
        assertTrue(Pattern.compile("uniform float uGathered;").matcher(GlassRefraction.AGSL).find());
        // The program's first write, and the uniform's own default, is the hairline.
        assertEquals(0f, GlassRefraction.gatheredUniform(false), 0f);
        assertEquals(1f, GlassRefraction.gatheredUniform(true), 0f);
        // One program: the hairline is the branch taken at 0, with today's very lines in it (the
        // hairline tests above read them), and the gathered light the branch taken at 1.
        String agsl = GlassRefraction.AGSL;
        int branch = agsl.indexOf("if (uGathered > 0.5) {");
        int otherwise = agsl.indexOf("} else {", branch);
        assertTrue(branch > 0 && otherwise > branch);
        assertTrue(agsl.indexOf("float rim = 1.0 - edge;") > otherwise);
        assertTrue(agsl.indexOf("float light = rim * kRim * (lobe(n, kLobeK) + 1.5 * touchG);") > otherwise);
        assertTrue(agsl.indexOf("float glow = exp(-max(inside, 0.0) / (kGlowDp * uDensity));") > branch);
        assertTrue(agsl.indexOf("float glow = exp(") < otherwise);
        // The pull's ease across the outermost dp is the same either way.
        assertTrue(agsl.indexOf("float edge = smoothstep(0.0, rimW, inside);") < branch);
    }

    @Test
    public void theGatheredConstantsAreThePrograms() {
        assertConst("kGlowDp", GlassRefraction.Look.GLOW_DP);
        assertConst("kGlowReachDp", GlassRefraction.Look.GLOW_REACH_DP);
        assertConst("kAmbient", GlassRefraction.Look.AMBIENT);
        assertConst("kKeyExp", GlassRefraction.Look.KEY_EXPONENT);
        assertConst("kPaleCut", GlassRefraction.Look.PALE_CUT);
        assertConst("kGain", GlassRefraction.Look.GAIN);
        assertConst("kLift", GlassRefraction.Look.LIFT);
        assertConst("kEdgeShade", GlassRefraction.Look.EDGE_SHADE);
        assertTrue(GlassRefraction.AGSL.contains("const float2 kKeyLight = float2("
            + GlassRefraction.KEY_LIGHT_X + ", " + GlassRefraction.KEY_LIGHT_Y + ");"));
        assertTrue(GlassRefraction.AGSL.contains("const float3 kLumaW = float3("
            + GlassRefraction.LUMA_R + ", " + GlassRefraction.LUMA_G + ", " + GlassRefraction.LUMA_B + ");"));
        float length = (float) Math.hypot(GlassRefraction.KEY_LIGHT_X, GlassRefraction.KEY_LIGHT_Y);
        assertEquals("the key light is a unit vector", 1f, length, 1e-3f);
        assertTrue("up and to the left", GlassRefraction.KEY_LIGHT_X < 0f && GlassRefraction.KEY_LIGHT_Y < 0f);
        // The program runs the twin's expressions.
        assertTrue(GlassRefraction.AGSL.contains(
            "return kAmbient + (1.0 - kAmbient) * (c > 0.0 ? pow(c, kKeyExp) : 0.0);"));
        assertTrue(GlassRefraction.AGSL.contains(
            "float k = glow * (gatherShare(n) + 1.5 * touchG) * (1.0 - kPaleCut * pale);"));
        assertTrue(GlassRefraction.AGSL.contains("float pale = dot(float3(col.rgb), kLumaW);"));
        assertTrue(GlassRefraction.AGSL.contains("col.rgb = col.rgb * half(1.0 - glow * kEdgeShade);"));
        assertTrue(GlassRefraction.AGSL.contains(
            "col.rgb = min(half3(col.a), col.rgb * half(1.0 + kGain * k) + half3(kLift * k));"));
        assertTrue(GlassRefraction.AGSL.contains("if (inside < kGlowReachDp * uDensity) {"));
    }

    private static void assertConst(String name, float java) {
        java.util.regex.Matcher m = Pattern.compile("const float " + name + " = ([0-9.]+);")
            .matcher(GlassRefraction.AGSL);
        assertTrue(name + " is a constant", m.find());
        assertEquals(name, java, Float.parseFloat(m.group(1)), 0f);
    }

    @Test
    public void theGatherBrightensTheBackdropsOwnColour() {
        // A blue backdrop at the outline, facing the key light squarely.
        float r = 0.2f, g = 0.3f, b = 0.8f;
        float nx = GlassRefraction.KEY_LIGHT_X, ny = GlassRefraction.KEY_LIGHT_Y;
        float luma = r * GlassRefraction.LUMA_R + g * GlassRefraction.LUMA_G + b * GlassRefraction.LUMA_B;
        float k = GlassRefraction.gatherK(0f, DENSITY, GlassRefraction.gatherShare(nx, ny), luma);
        float[] out = new float[3];
        GlassRefraction.gatheredLit(r, g, b, 1f, 0f, DENSITY, nx, ny, out);
        float[] in = {r, g, b};
        for (int c = 0; c < 3; c++) {
            assertTrue("channel " + c + " rises", out[c] > in[c]);
            assertTrue("channel " + c + " rises in proportion to itself, plus at most the lift",
                out[c] <= in[c] * (1f + GlassRefraction.Look.GAIN * k)
                    + GlassRefraction.Look.LIFT * k + 1e-6f);
            assertTrue("never past white", out[c] <= 1f);
        }
        // Where no channel is held at white, the hue stays: blue over red within 10%. At the
        // outline itself this sample's blue reaches white and is held there (0.48, 0.69, 1.0),
        // so the ratio is read from where the light has faded enough for nothing to clip.
        int unclipped = 0;
        for (int i = 0; i <= 40; i++) {
            float inside = i * 0.25f * DENSITY;
            GlassRefraction.gatheredLit(r, g, b, 1f, inside, DENSITY, nx, ny, out);
            if (out[2] >= 1f) continue;
            unclipped++;
            float ratio = (out[2] / out[0]) / (b / r);
            assertTrue("blue over red at " + (i * 0.25f) + " dp: " + ratio, ratio > 0.9f && ratio < 1.1f);
        }
        assertTrue(unclipped > 25);
        // A premultiplied sample is held under its own alpha.
        GlassRefraction.gatheredLit(0.4f, 0.4f, 0.4f, 0.5f, 0f, DENSITY, nx, ny, out);
        for (float channel : out) assertTrue(channel <= 0.5f);
    }

    @Test
    public void theLightIsBrightestTopLeft() {
        float d = (float) Math.sqrt(0.5);
        float topLeft = GlassRefraction.gatherShare(-d, -d);
        float top = GlassRefraction.gatherShare(0f, -1f);
        float left = GlassRefraction.gatherShare(-1f, 0f);
        float right = GlassRefraction.gatherShare(1f, 0f);
        float bottom = GlassRefraction.gatherShare(0f, 1f);
        assertTrue(topLeft > top);
        assertTrue(top > left);
        assertTrue(left > right);
        assertEquals(GlassRefraction.Look.AMBIENT, right, 0f);
        assertEquals(GlassRefraction.Look.AMBIENT, bottom, 0f);
        // The shares the plan quotes.
        assertEquals(0.97f, topLeft, 0.01f);
        assertEquals(0.77f, top, 0.01f);
        assertEquals(0.48f, left, 0.01f);
    }

    @Test
    public void theGlintShrinksOverAPaleBackdrop() {
        float share = GlassRefraction.gatherShare(0f, -1f);
        float overBlack = GlassRefraction.gatherK(0f, DENSITY, share, 0f);
        float overWhite = GlassRefraction.gatherK(0f, DENSITY, share, 1f);
        assertEquals(overBlack / 4f, overWhite, 1e-6f);
        // A black backdrop still shows an edge: the lift alone.
        float[] out = new float[3];
        GlassRefraction.gatheredLit(0f, 0f, 0f, 1f, 0f, DENSITY, 0f, -1f, out);
        assertEquals(GlassRefraction.Look.LIFT * overBlack, out[0], 1e-6f);
        assertTrue(out[0] > 0f);
    }

    @Test
    public void theGlowFadesAndStops() {
        assertEquals(1f, GlassRefraction.glow(0f, DENSITY), 0f);
        assertEquals("1/e at 1.4 dp", (float) Math.exp(-1),
            GlassRefraction.glow(1.4f * DENSITY, DENSITY), 1e-5f);
        float reach = GlassRefraction.Look.GLOW_REACH_DP * DENSITY;
        assertEquals("nothing from the reach in", 0f, GlassRefraction.glow(reach, DENSITY), 0f);
        assertEquals(0f, GlassRefraction.glow(reach * 3f, DENSITY), 0f);
        // Just inside the reach the light is already under one 8-bit step, even at full gain on a
        // white pixel facing the light: cutting it off there leaves no edge.
        float[] out = new float[3];
        GlassRefraction.gatheredLit(1f, 1f, 1f, 1f, reach - 0.01f, DENSITY, 0f, -1f, out);
        float justInside = GlassRefraction.glow(reach - 0.01f, DENSITY)
            * (1f + GlassRefraction.Look.GAIN + GlassRefraction.Look.LIFT);
        assertTrue("under a step: " + justInside, justInside < 1f / 255f);
        float last = 2f;
        for (int i = 0; i <= 40; i++) {
            float glow = GlassRefraction.glow(i * 0.25f * DENSITY, DENSITY);
            assertTrue("fades inward at " + i, glow <= last);
            last = glow;
        }
        // The outermost pixel takes the whole light even just outside the outline.
        assertEquals(1f, GlassRefraction.glow(-2f, DENSITY), 0f);
    }

    @Test
    public void aSeamIsPushedPastTheGlow() {
        GlassRefraction.Look[] looks = {GlassRefraction.Look.DEFAULT,
            new GlassRefraction.Look(27, 18, 10, 0, 40, 76), new GlassRefraction.Look(0, 1, 100),
            new GlassRefraction.Look(6, 4, 10)};
        for (GlassRefraction.Look look : looks) {
            for (float radius : new float[] {0f, 60f}) {
                float reach = GlassRefraction.seamReachPx(look, DENSITY, radius, true);
                assertTrue(look + " at " + radius, reach >= GlassRefraction.Look.GLOW_REACH_DP * DENSITY
                    + look.strengthPx(DENSITY) + radius + 2f * DENSITY - 1e-3f);
                assertEquals("the hairline's reach is today's",
                    GlassRefraction.seamReachPx(look, DENSITY, radius),
                    GlassRefraction.seamReachPx(look, DENSITY, radius, false), 0f);
            }
        }
        assertEquals(GlassRefraction.Look.rimReachPx(DENSITY),
            GlassRefraction.Look.rimReachPx(DENSITY, false), 0f);
        assertEquals(GlassRefraction.Look.GLOW_REACH_DP * DENSITY,
            GlassRefraction.Look.rimReachPx(DENSITY, true), 0f);
    }

    @Test
    public void theShippedLooksSeamsDoNotMoveWithTheGlow() {
        // Clear, Mist and Tint (and the default mode's dock) have bands wider than the glow's
        // reach, so their seams sit where they did: the switch moves no geometry for them.
        GlassRefraction.Look[] shipped = {GlassRefraction.Look.DEFAULT,
            new GlassRefraction.Look(27, 18, 10), new GlassRefraction.Look(24, 16, 10),
            new GlassRefraction.Look(21, 14, 10)};
        for (GlassRefraction.Look look : shipped)
            assertEquals(look.toString(), GlassRefraction.seamReachPx(look, DENSITY, 40f),
                GlassRefraction.seamReachPx(look, DENSITY, 40f, true), 0f);
    }

    @Test
    public void aSeamIsPushedPastEverythingTheRimCouldTouch() {
        // Past the band the pull reaches into, the pull itself, and the corner arc — so neither
        // the light nor the bend nor a rounded corner shows along the shared edge.
        GlassRefraction.Look look = GlassRefraction.Look.DEFAULT;
        float reach = GlassRefraction.seamReachPx(look, DENSITY, 60f);
        assertTrue(reach > look.bandPx(DENSITY) + look.strengthPx(DENSITY) + 60f);
        assertTrue("a square seam still clears the band and the pull",
            GlassRefraction.seamReachPx(look, DENSITY, 0f)
                > look.bandPx(DENSITY) + look.strengthPx(DENSITY));
    }
}
