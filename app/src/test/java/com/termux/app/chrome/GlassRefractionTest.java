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
        // 100% parts the colours by 0.08 of the pull; Clear's 40% is Haze Clear's 0.02, and up
        // to it every value is what it was (0.05 per whole), Prism's 80 is 0.06.
        assertEquals(0.08f, GlassRefraction.Look.MAX_DISPERSION_SHARE, 0f);
        assertEquals(0.02f, new GlassRefraction.Look(27, 18, 10, 0, 40).dispersion(), 1e-6f);
        assertEquals("Glint exactly as it was", 0.4f * 0.05f,
            new GlassRefraction.Look(27, 18, 10, 0, 40).dispersion(), 0f);
        assertEquals(0.25f * 0.05f, new GlassRefraction.Look(27, 18, 10, 0, 25).dispersion(), 0f);
        assertEquals(0.06f, new GlassRefraction.Look(27, 18, 10, 0, 80).dispersion(), 1e-6f);
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
            "uClarity", "uTouchActive", "uTouch", "uTouchRadius", "uTouchLens", "uEdgeGain",
        };
        for (String name : written) {
            assertTrue(name + " is declared",
                Pattern.compile("uniform \\w+ " + name + ";").matcher(GlassRefraction.AGSL).find());
        }
        assertTrue(GlassRefraction.AGSL.contains("uniform shader content;"));
        assertTrue("the clearer picture is a second input", GlassRefraction.AGSL.contains("uniform shader sharp;"));
        // The knobs the hairline no longer takes, and the slab lens and thickness shading neither
        // reference draws, are gone from the program.
        // The white hairline the gathered light replaced, and the switch that chose between them,
        // are gone as well.
        for (String gone : new String[] {"uRim", "uLobeK", "uShade", "uThick", "uSlab", "uRimW",
            "slabOffset", "softClamp", "kKnee", "uGathered", "kRim", "kShade", "kLobeK", "kLight",
            "lobe"}) {
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
    public void theLightRunsRoundTheWholeOutlineAndPeaksTowardTheKeyLight() {
        // The one directional light left is the gathered light's key light: every side keeps the
        // ambient share, so the outline is never dark down a side as the hairline's was.
        float last = 2f;
        for (int deg = 0; deg <= 180; deg += 5) {
            // From facing the key light squarely round to facing away from it.
            double a = Math.atan2(GlassRefraction.KEY_LIGHT_Y, GlassRefraction.KEY_LIGHT_X)
                + Math.toRadians(deg);
            float share = GlassRefraction.gatherShare((float) Math.cos(a), (float) Math.sin(a));
            assertTrue("falls off at " + deg, share <= last + 1e-6f);
            assertTrue("never under the ambient share at " + deg,
                share >= GlassRefraction.Look.AMBIENT - 1e-6f);
            last = share;
        }
        assertEquals(1f, GlassRefraction.gatherShare(GlassRefraction.KEY_LIGHT_X,
            GlassRefraction.KEY_LIGHT_Y), 1e-3f);
        assertTrue("the program has one light", GlassRefraction.AGSL.contains(
            "float c = max(dot(n, kKeyLight), 0.0);"));
        assertFalse(GlassRefraction.AGSL.contains("dot(n, kLight)"));
    }

    @Test
    public void theEdgeIsFixedMaterialWhateverTheStoredLightSays() {
        // Two looks that differ only in the stored light and specular draw the same uniforms.
        GlassRefraction.Look faint = new GlassRefraction.Look(24, 16, 10, 0, 0, 0);
        GlassRefraction.Look loud = new GlassRefraction.Look(24, 16, 90, 80, 0, 0);
        assertEquals(faint.strengthPx(DENSITY), loud.strengthPx(DENSITY), 0f);
        assertEquals(faint.bandPx(DENSITY), loud.bandPx(DENSITY), 0f);
        assertEquals(faint.dispersion(), loud.dispersion(), 0f);
        assertEquals(faint.clarity(), loud.clarity(), 0f);
    }

    @Test
    public void theGatheredLightIsBoundedAndTheShadeSitsUnderIt() {
        float d = (float) Math.sqrt(0.5);
        float[] out = new float[3];
        for (int i = 0; i <= 20; i++) {
            float face = i / 20f;
            // The brightest spot on the outline: facing the key light, at the outline itself.
            GlassRefraction.gatheredLit(face, face, face, 1f, 0f, DENSITY, -d, -d, out);
            float shaded = face * (1f - GlassRefraction.Look.EDGE_SHADE);
            assertTrue("never past white at " + face, out[0] < 1f || face >= 1f);
            assertTrue("never under the faint shade at " + face, out[0] >= shaded - 1e-6f);
            assertTrue("never more than the gain and the lift allow at " + face,
                out[0] <= shaded * (1f + GlassRefraction.Look.GAIN) + GlassRefraction.Look.LIFT + 1e-6f);
        }
        // Past the light's reach the picture is untouched.
        float reach = GlassRefraction.Look.GLOW_REACH_DP * DENSITY;
        GlassRefraction.gatheredLit(0.4f, 0.4f, 0.4f, 1f, reach, DENSITY, 0f, -1f, out);
        assertEquals(0.4f, out[0], 0f);
        GlassRefraction.gatheredLit(0.4f, 0.4f, 0.4f, 1f, 5f * reach, DENSITY, 0f, -1f, out);
        assertEquals(0.4f, out[0], 0f);
    }

    @Test
    public void theEasedPullIsADpAndNeverUnderTwoPixels() {
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
    public void aSeamClearsTheGlowOnANarrowBand() {
        // Solid's 1 dp edge ends well inside the light's reach; the seam is pushed past the light.
        GlassRefraction.Look narrow = new GlassRefraction.Look(0, 1, 100);
        assertTrue(GlassRefraction.seamReachPx(narrow, DENSITY, 0f) > GlassRefraction.Look.rimReachPx(DENSITY));
        assertTrue(GlassRefraction.seamReachPx(narrow, 1f, 0f) > GlassRefraction.Look.rimReachPx(1f));
        assertTrue(GlassRefraction.seamReachPx(narrow, DENSITY, 0f)
            > GlassRefraction.Look.GLOW_REACH_DP * DENSITY);
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
    public void theGatheredLightIsTheOnlyEdge() {
        // No switch and no hairline left to fall back to: every surface that runs the program,
        // a look or none (the dock and the strip draw Look.DEFAULT), wears the gathered light.
        String agsl = GlassRefraction.AGSL;
        assertFalse(agsl.contains("uGathered"));
        int light = agsl.indexOf("    if (inside < kGlowReachDp * uDensity) {");
        assertTrue(light > 0);
        assertTrue(agsl.indexOf("float glow = exp(-max(inside, 0.0) / (kGlowDp * uDensity));") > light);
        assertFalse("no other branch beside it", agsl.substring(light).contains("} else {\n        float rim"));
        // The pull's ease across the outermost dp is still there, before the light.
        assertTrue(agsl.indexOf("float edge = smoothstep(0.0, rimW, inside);") < light);
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
            "float k = glow * (gatherShare(n) + 1.5 * touchG) * (1.0 - kPaleCut * pale) * uEdgeGain;"));
        assertTrue(GlassRefraction.AGSL.contains("float pale = dot(float3(col.rgb), kLumaW);"));
        assertTrue(GlassRefraction.AGSL.contains("col.rgb = col.rgb * half(1.0 - glow * kEdgeShade);"));
        assertTrue(GlassRefraction.AGSL.contains(
            "col.rgb = min(half3(col.a), col.rgb * half(1.0 + kGain * k) + half3(kLift * k));"));
        assertTrue(GlassRefraction.AGSL.contains("if (inside < kGlowReachDp * uDensity) {"));
        // The headroom the light is held under, after the shade and before the brightening.
        String agsl = GlassRefraction.AGSL;
        String held = "k = heldK(k, max(float(col.a) - peak, 0.0) / (kGain * peak + kLift));";
        assertTrue(agsl.contains("float peak = float(max(col.r, max(col.g, col.b)));"));
        assertTrue(agsl.contains(held));
        assertTrue(agsl.indexOf(held) > agsl.indexOf("col.rgb = col.rgb * half(1.0 - glow * kEdgeShade);"));
        assertTrue(agsl.indexOf(held) < agsl.indexOf("col.rgb = min(half3(col.a), col.rgb * half(1.0 + kGain * k)"));
        assertTrue(agsl.contains("return r <= 0.5 ? k : kMax * (1.0 - 0.25 / r);"));
        assertEquals(0.5f, GlassRefraction.Look.HEADROOM_KNEE, 0f);
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
        // The hue stays all the way in, the outline itself included: the light is held under the
        // blue's headroom rather than clipping it at white. Blue over red within 10%.
        for (int i = 0; i <= 40; i++) {
            float inside = i * 0.25f * DENSITY;
            GlassRefraction.gatheredLit(r, g, b, 1f, inside, DENSITY, nx, ny, out);
            assertTrue("blue held under white at " + (i * 0.25f) + " dp", out[2] < 1f);
            float ratio = (out[2] / out[0]) / (b / r);
            assertTrue("blue over red at " + (i * 0.25f) + " dp: " + ratio, ratio > 0.9f && ratio < 1.1f);
        }
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
                float reach = GlassRefraction.seamReachPx(look, DENSITY, radius);
                assertTrue(look + " at " + radius, reach >= GlassRefraction.Look.GLOW_REACH_DP * DENSITY
                    + look.strengthPx(DENSITY) + radius + 2f * DENSITY - 1e-3f);
            }
        }
        // The rim's reach is the light's, at every density: it reaches much further than the
        // eased pull's width does.
        for (float density : new float[] {0.4f, 1f, DENSITY, 4f}) {
            assertEquals(GlassRefraction.Look.GLOW_REACH_DP * density,
                GlassRefraction.Look.rimReachPx(density), 1e-4f);
            assertTrue(GlassRefraction.Look.rimReachPx(density)
                >= GlassRefraction.Look.rimWidthPx(density));
        }
    }

    @Test
    public void theShippedLooksSeamsDoNotMoveWithTheGlow() {
        // Clear, Mist and Tint (and the default mode's dock) have bands wider than the glow's
        // reach, so their seams sit where the hairline's sat: the band, the pull, the corner and
        // a little to spare, the glow adding nothing.
        GlassRefraction.Look[] shipped = {GlassRefraction.Look.DEFAULT,
            new GlassRefraction.Look(27, 18, 10), new GlassRefraction.Look(24, 16, 10),
            new GlassRefraction.Look(21, 14, 10)};
        for (GlassRefraction.Look look : shipped) {
            assertTrue(look.toString(),
                look.bandPx(DENSITY) >= GlassRefraction.Look.GLOW_REACH_DP * DENSITY);
            assertEquals(look.toString(), look.bandPx(DENSITY) + look.strengthPx(DENSITY) + 40f
                    + 2f * DENSITY, GlassRefraction.seamReachPx(look, DENSITY, 40f), 1e-3f);
        }
    }

    @Test
    public void aPaleBackdropsOutlineNeverClipsAndKeepsItsHue() {
        // The review's cases: a mid grey and a pale grey went to 0.98 and 1.0 at the outline, and
        // a pale blue sky clipped its green and blue to cyan-white. Held under the brightest
        // channel's headroom, no channel reaches the alpha and the channels keep their ratios.
        float d = (float) Math.sqrt(0.5);
        float[][] backdrops = {{0.5f, 0.5f, 0.5f}, {0.6f, 0.6f, 0.6f}, {0.4f, 0.7f, 0.95f},
            {0.9f, 0.9f, 0.9f}, {0.95f, 0.2f, 0.1f}, {0.98f, 0.98f, 0.99f}};
        float[] out = new float[3];
        for (float[] c : backdrops) {
            for (float[] n : new float[][] {{-d, -d}, {GlassRefraction.KEY_LIGHT_X,
                GlassRefraction.KEY_LIGHT_Y}, {0f, -1f}, {1f, 0f}}) {
                for (int i = 0; i <= 12; i++) {
                    float inside = i * 0.25f * DENSITY;
                    GlassRefraction.gatheredLit(c[0], c[1], c[2], 1f, inside, DENSITY, n[0], n[1], out);
                    String at = java.util.Arrays.toString(c) + " at " + (i * 0.25f) + " dp";
                    for (int ch = 0; ch < 3; ch++)
                        assertTrue("channel " + ch + " clips: " + at + " " + out[ch], out[ch] < 1f);
                    // The hue: every channel moves by the same gain, so their ratios hold but for
                    // the small grey lift.
                    float ratio = (out[2] / out[1]) / (c[2] / c[1]);
                    assertTrue("hue kept " + at + ": " + ratio, ratio > 0.97f && ratio < 1.03f);
                }
            }
        }
        // Over the pale sky the outline is still brighter than the backdrop, only less so.
        GlassRefraction.gatheredLit(0.4f, 0.7f, 0.95f, 1f, 0f, DENSITY, -d, -d, out);
        assertTrue(out[2] > 0.95f * (1f - GlassRefraction.Look.EDGE_SHADE));
        assertTrue(out[1] > 0.7f);
    }

    @Test
    public void theHeadroomHoldIsExactBelowItsKneeAndOnlyNearsTheHeadroom() {
        float kMax = 0.4f;
        float last = 0f;
        for (int i = 0; i <= 400; i++) {
            float k = i * 0.01f;
            float held = GlassRefraction.heldK(k, kMax);
            if (k <= GlassRefraction.Look.HEADROOM_KNEE * kMax) assertEquals(k, held, 0f);
            assertTrue("under the headroom at " + k, held < kMax);
            assertTrue("never more than asked at " + k, held <= k + 1e-7f);
            assertTrue("rising at " + k, held >= last - 1e-7f);
            last = held;
        }
        // The two pieces meet with the same slope: no kink at the knee.
        float knee = GlassRefraction.Look.HEADROOM_KNEE * kMax;
        float e = 1e-4f;
        float below = (GlassRefraction.heldK(knee, kMax) - GlassRefraction.heldK(knee - e, kMax)) / e;
        float above = (GlassRefraction.heldK(knee + e, kMax) - GlassRefraction.heldK(knee, kMax)) / e;
        assertEquals(below, above, 0.01f);
        // No headroom, no light; none asked, none given.
        assertEquals(0f, GlassRefraction.heldK(0.5f, 0f), 1e-6f);
        assertEquals(0f, GlassRefraction.heldK(0f, 0.3f), 0f);
        // A dark backdrop's light is exactly as before: the hold never reaches it.
        assertEquals(0.77f, GlassRefraction.heldK(0.77f, GlassRefraction.headroomK(0f, 1f)), 0f);
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

    // ------------------------------------------------------------- the edge follows the cover

    /**
     * The gathered light is drawn under the tint and Shade, which let {@code 1 - cover} of it
     * through. Up to Clear's 10% it is at its tuned strength; above, it is raised by the ratio, and
     * held at a cap low enough that a mid backdrop's ring stays near the headroom's knee: dark
     * Mist's 60% asks 2.25, which is the cap, and the opaque slabs get the cap.
     */
    @Test
    public void theEdgeGainFollowsWhatCoversIt() {
        assertEquals(1f, GlassRefraction.Look.edgeGainFor(0f), 0f);
        assertEquals("Clear is the reference", 1f, GlassRefraction.Look.edgeGainFor(0.10f), 1e-6f);
        assertEquals("Tint, 46%", 0.9f / 0.54f, GlassRefraction.Look.edgeGainFor(0.46f), 1e-5f);
        assertEquals("the cap is 2.25", 2.25f, GlassRefraction.Look.MAX_EDGE_GAIN, 0f);
        assertEquals("Mist, 60%, at the cap", GlassRefraction.Look.MAX_EDGE_GAIN,
            GlassRefraction.Look.edgeGainFor(0.60f), 1e-5f);
        assertEquals("light Mist, 35%, the plain ratio", 0.9f / 0.65f,
            GlassRefraction.Look.edgeGainFor(0.35f), 1e-5f);
        assertEquals("half cover is the plain ratio", 1.8f,
            GlassRefraction.Look.edgeGainFor(0.50f), 1e-5f);
        assertEquals("Solid, 92%, held at the cap", GlassRefraction.Look.MAX_EDGE_GAIN,
            GlassRefraction.Look.edgeGainFor(0.92f), 0f);
        assertEquals(GlassRefraction.Look.MAX_EDGE_GAIN, GlassRefraction.Look.edgeGainFor(1f), 0f);
        float last = 0f;
        for (int percent = 0; percent <= 100; percent++) {
            float gain = GlassRefraction.Look.edgeGainFor(percent / 100f);
            assertTrue("never below the tuned light at " + percent, gain >= 1f);
            assertTrue("never above the cap at " + percent,
                gain <= GlassRefraction.Look.MAX_EDGE_GAIN);
            assertTrue("rising at " + percent, gain >= last);
            last = gain;
        }
    }

    /** A surface states its cover on its look; read from the preferences a look has none. */
    @Test
    public void theCoverIsPartOfALooksIdentityAndRoundsToAPercent() {
        GlassRefraction.Look look = new GlassRefraction.Look(27, 18, 10, 0, 40, 76);
        assertEquals(0, look.coverPercent);
        assertEquals(1f, look.edgeGain(), 0f);
        GlassRefraction.Look mist = look.withCover(0.6f);
        assertEquals(60, mist.coverPercent);
        assertEquals(GlassRefraction.Look.MAX_EDGE_GAIN, mist.edgeGain(), 1e-5f);
        assertNotEquals(look, mist);
        assertEquals(mist, look.withCover(0.6001f));
        assertTrue("the same cover writes nothing new", mist == mist.withCover(0.6f));
        assertEquals("clamped", 100, look.withCover(3f).coverPercent);
        assertEquals("clamped", 0, look.withCover(-1f).coverPercent);
        assertEquals("the stored knobs are kept", look.dispersionPercent,
            mist.dispersionPercent);
    }

    /**
     * Raised however far, the light is still held under the headroom the sample has left: no
     * channel clips and the hue's order is kept, over a dark, a mid and a pale backdrop.
     */
    @Test
    public void aRaisedEdgeNeverClips() {
        float[][] samples = {{0.05f, 0.08f, 0.2f}, {0.5f, 0.3f, 0.2f}, {0.6f, 0.75f, 0.95f},
            {0.98f, 0.98f, 0.98f}};
        float[] plain = new float[3];
        float[] raised = new float[3];
        for (float[] c : samples) {
            for (float inside = 0f; inside <= 4f * DENSITY; inside += 0.5f * DENSITY) {
                GlassRefraction.gatheredLit(c[0], c[1], c[2], 1f, inside, DENSITY, -0.552f,
                    -0.834f, plain);
                GlassRefraction.gatheredLit(c[0], c[1], c[2], 1f, inside, DENSITY, -0.552f,
                    -0.834f, GlassRefraction.Look.MAX_EDGE_GAIN, raised);
                String at = java.util.Arrays.toString(c) + " at " + inside + " px";
                for (int i = 0; i < 3; i++) {
                    assertTrue("no clip " + at, raised[i] <= 1f);
                    assertTrue("raised, never dimmer " + at, raised[i] >= plain[i] - 1e-6f);
                }
                assertTrue("the hue's order is kept " + at,
                    (raised[2] >= raised[0]) == (c[2] >= c[0]));
            }
        }
    }

    /**
     * The Edge colour pill's Prism splits the picture three times as far as Glint at a corner
     * (Clear's sharp rim shows that split itself) and stays under the program's share cap.
     */
    @Test
    public void prismSplitsThreeTimesGlintAndUnderTheCap() {
        GlassRefraction.Look glint = new GlassRefraction.Look(27, 18, 10, 0, 40, 76);
        GlassRefraction.Look prism = new GlassRefraction.Look(27, 18, 10, 0, 80, 76);
        assertEquals(3f * glint.dispersion(), prism.dispersion(), 1e-6f);
        assertTrue(prism.dispersion() <= GlassRefraction.Look.MAX_DISPERSION_SHARE);
        float bx = 300f, by = 200f;
        float pull = glint.strengthPx(DENSITY);
        float corner = GlassRefraction.cornerWeight(-bx, -by, bx, by);
        assertTrue("the corner takes the split", corner > 0.5f);
        assertTrue("Prism's sampled fringe at the corner at least 2.5x Glint's",
            pull * prism.dispersion() * corner >= 2.5f * pull * glint.dispersion() * corner);
    }

    /**
     * On a clarity-0 Look (Mist, Tint) the gathered light carries the fringe: at a corner, red's
     * light over blue's summed across the depth it covers (the fringe's whole amount) is at least
     * 2.5 times Glint's under Prism (calc 2.6 to 2.9; the peak alone is held by heldK to about
     * twice), and no channel passes the sample's alpha, bright or dark.
     */
    @Test
    public void prismsFringeOnTheLightIsPlainlyPastGlintsAndNeverClips() {
        GlassRefraction.Look glint = new GlassRefraction.Look(24, 16, 10, 0, 40, 0);
        GlassRefraction.Look prism = new GlassRefraction.Look(24, 16, 10, 0, 80, 0);
        float glintSplit = GlassRefraction.rimSplitPx(glint.dispersion(), 0f, 1f, DENSITY);
        float prismSplit = GlassRefraction.rimSplitPx(prism.dispersion(), 0f, 1f, DENSITY);
        assertEquals("Glint half a dp, as before", 0.5f * DENSITY, glintSplit, 1e-4f);
        assertEquals("Prism one and a half", 1.5f * DENSITY, prismSplit, 1e-4f);
        float d = (float) Math.sqrt(0.5);
        float[] out = new float[3];
        for (float gain : new float[] {1f, GlassRefraction.Look.MAX_EDGE_GAIN}) {
            for (float grey : new float[] {0.15f, 0.3f, 0.5f, 0.7f}) {
                float glintFringe = 0f;
                float prismFringe = 0f;
                for (int i = 0; i < 400; i++) {
                    float inside = i * 0.025f * DENSITY;
                    GlassRefraction.gatheredLitSplit(grey, grey, grey, 1f, inside, DENSITY, -d, -d,
                        gain, glintSplit, out);
                    glintFringe += (out[0] - out[2]) * 0.025f;
                    GlassRefraction.gatheredLitSplit(grey, grey, grey, 1f, inside, DENSITY, -d, -d,
                        gain, prismSplit, out);
                    prismFringe += (out[0] - out[2]) * 0.025f;
                }
                assertTrue(gain + "/" + grey + ": Prism " + prismFringe + " vs Glint " + glintFringe,
                    prismFringe >= 2.5f * glintFringe && glintFringe > 0f);
            }
        }
        for (float[] c : new float[][] {{1f, 1f, 1f}, {0.95f, 0.95f, 0.95f}, {0.9f, 0.6f, 0.3f},
                {0.3f, 0.6f, 0.95f}}) {
            for (int i = 0; i <= 12; i++) {
                GlassRefraction.gatheredLitSplit(c[0], c[1], c[2], 1f, i * 0.25f * DENSITY, DENSITY,
                    -d, -d, GlassRefraction.Look.MAX_EDGE_GAIN, prismSplit, out);
                for (float channel : out) assertTrue("held under white: " + channel, channel <= 1f);
            }
            GlassRefraction.gatheredLitSplit(c[0] * 0.5f, c[1] * 0.5f, c[2] * 0.5f, 0.5f, 0f,
                DENSITY, -d, -d, GlassRefraction.Look.MAX_EDGE_GAIN, prismSplit, out);
            for (float channel : out) assertTrue("held under its alpha", channel <= 0.5f);
        }
    }

    /**
     * At the cap the ambient sides of a mid-grey backdrop sit just past the headroom's knee and
     * are held only a few percent flat, and a dark backdrop's stay inside the straight part: the
     * ring keeps its falloff.
     */
    @Test
    public void theCapKeepsTheRingNearTheKnee() {
        float mid = 0.5f;
        float k = GlassRefraction.gatherK(0f, DENSITY, GlassRefraction.Look.AMBIENT, mid)
            * GlassRefraction.Look.MAX_EDGE_GAIN;
        float kMax = GlassRefraction.headroomK(mid, 1f);
        float knee = GlassRefraction.Look.HEADROOM_KNEE * kMax;
        assertEquals("mid grey's sides near the knee", knee, k, 0.07f);
        float held = GlassRefraction.heldK(k, kMax);
        assertTrue("held under the headroom", held < kMax);
        assertTrue("held only a few percent flat", held > 0.95f * k);
        float dark = 0.2f;
        float kDark = GlassRefraction.gatherK(0f, DENSITY, GlassRefraction.Look.AMBIENT, dark)
            * GlassRefraction.Look.MAX_EDGE_GAIN;
        assertTrue("a dark backdrop's inside the straight part",
            kDark < GlassRefraction.Look.HEADROOM_KNEE * GlassRefraction.headroomK(dark, 1f));
    }

    // ------------------------------- dispersion on the gathered light (glass tuning 2026-10-17)

    /**
     * Mist bends through its frost (clarity 0), which blurs the sampled split away: Off and Prism
     * looked the same. The split laid on the gathered light is what Dispersion does there, at a
     * corner, and nothing along a straight edge.
     */
    @Test
    public void dispersionSplitsTheLightAtClarityZero() {
        GlassRefraction.Look mistPrism = new GlassRefraction.Look(24, 16, 10, 0, 80, 0);
        float corner = GlassRefraction.rimSplitPx(mistPrism.dispersion(), mistPrism.clarity(), 1f, DENSITY);
        assertEquals("Prism parts the light a dp and a half at the corner", 1.5f * DENSITY, corner, 1e-4f);
        assertEquals("nothing along a straight edge", 0f,
            GlassRefraction.rimSplitPx(mistPrism.dispersion(), 0f, 0f, DENSITY), 0f);
        GlassRefraction.Look mistOff = new GlassRefraction.Look(24, 16, 10, 0, 0, 0);
        assertEquals("Off parts nothing", 0f,
            GlassRefraction.rimSplitPx(mistOff.dispersion(), 0f, 1f, DENSITY), 0f);

        // A grey corner a dp in, facing the key light: red rises over green, blue falls under it.
        float d = (float) Math.sqrt(0.5);
        float[] plain = new float[3];
        float[] split = new float[3];
        float inside = 1f * DENSITY;
        GlassRefraction.gatheredLit(0.3f, 0.3f, 0.3f, 1f, inside, DENSITY, -d, -d, 1f, plain);
        GlassRefraction.gatheredLitSplit(0.3f, 0.3f, 0.3f, 1f, inside, DENSITY, -d, -d, 1f, corner, split);
        assertEquals("green is the unsplit light", plain[1], split[1], 0f);
        assertTrue("red's light reaches further in", split[0] > plain[0] + 0.01f);
        assertTrue("blue's less far", split[2] < plain[2] - 0.01f);
        assertTrue(split[0] <= 1f);
        // Off draws the unsplit light exactly.
        GlassRefraction.gatheredLitSplit(0.3f, 0.3f, 0.3f, 1f, inside, DENSITY, -d, -d, 1f, 0f, split);
        for (int c = 0; c < 3; c++) assertEquals(plain[c], split[c], 0f);
    }

    /** Glint stays a glint where it is new: red and blue part by well under the light's own fade. */
    @Test
    public void glintOnTheLightStaysSubtle() {
        GlassRefraction.Look mistGlint = new GlassRefraction.Look(24, 16, 10, 0, 40, 0);
        float corner = GlassRefraction.rimSplitPx(mistGlint.dispersion(), 0f, 1f, DENSITY);
        assertEquals(0.5f * DENSITY, corner, 1e-4f);
        assertTrue("under the glow's own fade", corner < GlassRefraction.Look.GLOW_DP * DENSITY);
    }

    /** Clear at Glint 40 is drawn as it was: its clear rim carries the fringe, the light is not split. */
    @Test
    public void clearsGlintIsUnchanged() {
        GlassRefraction.Look clear = new GlassRefraction.Look(27, 18, 10, 0, 40, 76);
        assertEquals(0f, GlassRefraction.rimSplitPx(clear.dispersion(), clear.clarity(), 1f, DENSITY), 0f);
        // Half way to Clear's clarity, half the split.
        float none = GlassRefraction.rimSplitPx(clear.dispersion(), 0f, 1f, DENSITY);
        float half = GlassRefraction.rimSplitPx(clear.dispersion(),
            GlassRefraction.Look.SHARP_CLARITY / 2f, 1f, DENSITY);
        assertEquals(none / 2f, half, 1e-4f);
    }

    /**
     * Every share up to Glint's, at every clarity and corner weight, splits the light exactly as
     * the old single fade did (its arithmetic written out here), to the bit.
     */
    @Test
    public void glintAndBelowSplitTheLightExactlyAsBefore() {
        assertEquals(GlassRefraction.Look.GLINT_SHARE,
            new GlassRefraction.Look(27, 18, 10, 0, 40, 76).dispersion(), 0f);
        for (int percent = 0; percent <= GlassRefraction.Look.GLINT_PERCENT; percent += 5) {
            float disp = new GlassRefraction.Look(27, 18, 10, 0, percent, 0).dispersion();
            for (float clarity : new float[] {0f, 0.2f, 0.38f, 0.6f, 0.76f, 1f}) {
                for (float cw : new float[] {0f, 0.3f, 1f}) {
                    float old = 0f;
                    if (disp > 0f) {
                        float sharp = Math.max(0f, Math.min(1f, clarity / 0.76f));
                        old = disp / 0.08f * cw * 2f * DENSITY * (1f - sharp);
                    }
                    assertEquals(percent + "% at " + clarity, old,
                        GlassRefraction.rimSplitPx(disp, clarity, cw, DENSITY), 0f);
                }
            }
        }
    }

    /**
     * Clear's Prism keeps a fringe on the light: what Prism adds past Glint is not faded by the
     * clear rim, so at Clear's clarity (0.76) the corner's light parts by a dp, two thirds of
     * Mist's dp and a half, where Glint's parts by nothing. Red's light over blue's, summed across
     * the depth it covers, is at least 2.5 times Glint's on Clear (Glint's own is the sampled
     * fringe alone) and at least 0.6 of Mist's Prism (calc 0.67 to 0.71); no channel clips.
     */
    @Test
    public void prismKeepsItsFringeOnClear() {
        GlassRefraction.Look glint = new GlassRefraction.Look(27, 18, 10, 0, 40, 76);
        GlassRefraction.Look prism = new GlassRefraction.Look(27, 18, 10, 0, 80, 76);
        GlassRefraction.Look mistPrism = new GlassRefraction.Look(24, 16, 10, 0, 80, 0);
        assertEquals(0.76f, prism.clarity(), 1e-6f);
        float glintSplit = GlassRefraction.rimSplitPx(glint.dispersion(), glint.clarity(), 1f, DENSITY);
        float prismSplit = GlassRefraction.rimSplitPx(prism.dispersion(), prism.clarity(), 1f, DENSITY);
        float mistSplit = GlassRefraction.rimSplitPx(mistPrism.dispersion(), 0f, 1f, DENSITY);
        assertEquals("Glint parts nothing on Clear", 0f, glintSplit, 0f);
        assertEquals("Prism a dp on Clear", 1f * DENSITY, prismSplit, 1e-4f);
        assertEquals("nothing along a straight edge", 0f,
            GlassRefraction.rimSplitPx(prism.dispersion(), prism.clarity(), 0f, DENSITY), 0f);
        float d = (float) Math.sqrt(0.5);
        float[] out = new float[3];
        for (float gain : new float[] {1f, GlassRefraction.Look.MAX_EDGE_GAIN}) {
            for (float grey : new float[] {0.15f, 0.3f, 0.5f, 0.7f}) {
                float glintFringe = 0f, prismFringe = 0f, mistFringe = 0f;
                for (int i = 0; i < 400; i++) {
                    float inside = i * 0.025f * DENSITY;
                    GlassRefraction.gatheredLitSplit(grey, grey, grey, 1f, inside, DENSITY, -d, -d,
                        gain, glintSplit, out);
                    glintFringe += (out[0] - out[2]) * 0.025f;
                    GlassRefraction.gatheredLitSplit(grey, grey, grey, 1f, inside, DENSITY, -d, -d,
                        gain, prismSplit, out);
                    prismFringe += (out[0] - out[2]) * 0.025f;
                    GlassRefraction.gatheredLitSplit(grey, grey, grey, 1f, inside, DENSITY, -d, -d,
                        gain, mistSplit, out);
                    mistFringe += (out[0] - out[2]) * 0.025f;
                }
                String at = gain + "/" + grey + ": Prism " + prismFringe + ", Glint " + glintFringe
                    + ", Mist's Prism " + mistFringe;
                assertTrue(at, prismFringe >= 2.5f * glintFringe && prismFringe > 0.3f);
                assertTrue(at, prismFringe >= 0.6f * mistFringe);
            }
        }
        for (float[] c : new float[][] {{1f, 1f, 1f}, {0.95f, 0.95f, 0.95f}, {0.9f, 0.6f, 0.3f},
                {0.3f, 0.6f, 0.95f}}) {
            for (int i = 0; i <= 12; i++) {
                GlassRefraction.gatheredLitSplit(c[0], c[1], c[2], 1f, i * 0.25f * DENSITY, DENSITY,
                    -d, -d, GlassRefraction.Look.MAX_EDGE_GAIN, prismSplit, out);
                for (float channel : out) assertTrue("held under white: " + channel, channel <= 1f);
            }
            GlassRefraction.gatheredLitSplit(c[0] * 0.5f, c[1] * 0.5f, c[2] * 0.5f, 0.5f, 0f,
                DENSITY, -d, -d, GlassRefraction.Look.MAX_EDGE_GAIN, prismSplit, out);
            for (float channel : out) assertTrue("held under its alpha", channel <= 0.5f);
        }
    }

    @Test
    public void theLightsSplitIsTheProgramsOwn() {
        assertConst("kMaxDisp", GlassRefraction.Look.MAX_DISPERSION_SHARE);
        assertConst("kRimSplitDp", GlassRefraction.Look.RIM_SPLIT_DP);
        assertConst("kSharpClarity", GlassRefraction.Look.SHARP_CLARITY);
        assertConst("kGlintDisp", GlassRefraction.Look.GLINT_SHARE);
        String agsl = GlassRefraction.AGSL;
        assertTrue(agsl.contains("float rimCw = uDisp > 0.0 ? cornerWeight(p, b) : 0.0;"));
        assertTrue(agsl.contains(
            "float rimSplit = uDisp > 0.0 ? min(uDisp, kGlintDisp) / kMaxDisp * rimCw * kRimSplitDp * uDensity"));
        assertTrue(agsl.contains("* (1.0 - clamp(uClarity / kSharpClarity, 0.0, 1.0))\n"));
        assertTrue(agsl.contains(
            "+ max(uDisp - kGlintDisp, 0.0) / kMaxDisp * rimCw * kRimSplitDp * uDensity : 0.0;"));
        assertTrue(agsl.contains(
            "float kr = heldK(kRaw * exp((at - max(inside - rimSplit, 0.0)) / gs), kMax);"));
        assertTrue(agsl.contains(
            "float kb = heldK(kRaw * exp((at - max(inside + rimSplit, 0.0)) / gs), kMax);"));
        // No new pass and no new input: the same four taps of each picture.
        assertEquals(4, count(agsl, "content.eval("));
        assertEquals(4, count(agsl, "sharp.eval("));
    }
}
