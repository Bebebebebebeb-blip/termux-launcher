package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
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
 * The pure half of the glass: how the six knobs become uniforms, the Java mirrors of the program's
 * pull, light and colour split, and where a surface's rim runs.
 * The program itself compiles only on a phone; nothing here needs one, though its text is read to
 * check it declares every uniform the Java side writes.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlassRefractionTest {

    private static final float DENSITY = 2.625f; // pong, 420 dpi

    @Test
    public void theDefaultLookIsTheDocksOwnRefraction() {
        // The band and the pull were literals in the activity's shader setup: 20 dp band, 9 dp
        // pull. The light is the "medium" depth every Look declares, 18% of the ceiling.
        GlassRefraction.Look look = GlassRefraction.Look.DEFAULT;
        assertEquals(20f * DENSITY, look.bandPx(DENSITY), 1e-4f);
        assertEquals(9f * DENSITY, look.strengthPx(DENSITY), 1e-4f);
        assertEquals(0.18f * GlassRefraction.Look.MAX_LIGHT_ALPHA, look.lightAlpha(), 1e-4f);
    }

    @Test
    public void theKnobsMapToUniformsAtTheScreensDensity() {
        GlassRefraction.Look look = new GlassRefraction.Look(12, 30, 50);
        assertEquals(12f * DENSITY, look.strengthPx(DENSITY), 1e-4f);
        assertEquals(30f * DENSITY, look.bandPx(DENSITY), 1e-4f);
        assertEquals(GlassRefraction.Look.MAX_LIGHT_ALPHA * 0.5f, look.lightAlpha(), 1e-4f);
        assertEquals("all the way up is the brightest light", GlassRefraction.Look.MAX_LIGHT_ALPHA,
            new GlassRefraction.Look(0, 1, 100).lightAlpha(), 1e-4f);
        assertEquals(0f, new GlassRefraction.Look(0, 1, 0).lightAlpha(), 1e-4f);
        assertEquals(0f, new GlassRefraction.Look(0, 1, 0).strengthPx(DENSITY), 1e-4f);
    }

    @Test
    public void theEdgeTermsAreOffByDefaultSoExistingLooksAreUnchanged() {
        GlassRefraction.Look look = GlassRefraction.Look.DEFAULT;
        assertEquals(0, look.specularPercent);
        assertEquals(0, look.dispersionPercent);
        assertEquals("no specular is the narrow glint", GlassRefraction.Look.LOBE_K, look.lobeExponent(), 0f);
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
    public void theThicknessShadingFollowsTheBendAndIsNotAKnob() {
        // uThick: nothing with no bend, full from THICKNESS_FULL_BEND_DP on, linear between.
        assertEquals(0f, new GlassRefraction.Look(0, 20, 18).thickness(), 0f);
        assertEquals(0.5f, new GlassRefraction.Look(4, 20, 18).thickness(), 1e-4f);
        assertEquals(1f, new GlassRefraction.Look(8, 20, 18).thickness(), 1e-4f);
        assertEquals("a thicker slab shades no harder", 1f,
            new GlassRefraction.Look(30, 20, 18).thickness(), 1e-4f);
        assertEquals("the dock's own refraction is a thick slab", 1f,
            GlassRefraction.Look.DEFAULT.thickness(), 1e-4f);
    }

    @Test
    public void specularWidensTheLobeAndLightsItWithinTheCeiling() {
        // uLobeK: a glint at 0, a broad sweep at 100, clamped either side.
        assertEquals(GlassRefraction.Look.LOBE_K,
            new GlassRefraction.Look(9, 20, 30, 0, 0).lobeExponent(), 0f);
        assertEquals(GlassRefraction.Look.MIN_LOBE_K,
            new GlassRefraction.Look(9, 20, 30, 100, 0).lobeExponent(), 1e-4f);
        assertEquals(GlassRefraction.Look.MIN_LOBE_K,
            new GlassRefraction.Look(9, 20, 30, 400, 0).lobeExponent(), 1e-4f);
        assertEquals(GlassRefraction.Look.LOBE_K,
            new GlassRefraction.Look(9, 20, 30, -5, 0).lobeExponent(), 0f);
        // A Custom with Edge light 0 and some Specular still lights the rim: the knob is never dead.
        assertEquals(0.5f * GlassRefraction.Look.MAX_LIGHT_ALPHA,
            new GlassRefraction.Look(9, 20, 0, 50, 0).lightAlpha(), 1e-4f);
        // Light 50 and Specular 50 make 75% of the ceiling, and the ceiling is never passed.
        assertEquals(0.75f * GlassRefraction.Look.MAX_LIGHT_ALPHA,
            new GlassRefraction.Look(9, 20, 50, 50, 0).lightAlpha(), 1e-4f);
        assertEquals(GlassRefraction.Look.MAX_LIGHT_ALPHA,
            new GlassRefraction.Look(9, 20, 100, 100, 0).lightAlpha(), 1e-4f);
        // Specular widens the light round the corner (the normal 45 degrees from straight up).
        float d = (float) Math.sqrt(0.5);
        float narrow = GlassRefraction.lobe(-d, -d, new GlassRefraction.Look(9, 20, 30, 0, 0).lobeExponent());
        float wide = GlassRefraction.lobe(-d, -d, new GlassRefraction.Look(9, 20, 30, 100, 0).lobeExponent());
        assertEquals("at k 6 the corner keeps an eighth", 0.125f, narrow, 1e-3f);
        assertEquals("at k 2 the corner keeps half", 0.5f, wide, 1e-3f);
    }

    @Test
    public void dispersionIsAShareOfThePullAndIsClamped() {
        assertEquals(0.4f * GlassRefraction.Look.MAX_DISPERSION_SHARE,
            new GlassRefraction.Look(28, 40, 85, 0, 40).dispersion(), 1e-4f);
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
        // The split the program takes is pull * uDisp * weight: none where nothing is bent.
        GlassRefraction.Look clear = new GlassRefraction.Look(26, 26, 30, 0, 35, 100);
        float rimPull = GlassRefraction.pullPx(0f, clear.bandPx(DENSITY), clear.strengthPx(DENSITY), by);
        float cornerSplit = rimPull * clear.dispersion() * GlassRefraction.cornerWeight(-bx, -by, bx, by);
        assertTrue("a few px at Clear's corner: " + cornerSplit, cornerSplit > 4f && cornerSplit < 12f);
        float facePull = GlassRefraction.pullPx(clear.bandPx(DENSITY), clear.bandPx(DENSITY),
            clear.strengthPx(DENSITY), by);
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
            "uRadius", "uBand", "uStrength", "uRim", "uLobeK", "uShade", "uDisp", "uDensity",
            "uLensActive", "uLensCenter", "uLensHalf", "uLensRadius", "uLensStrength", "uLensFeather",
            "uClarity", "uThick", "uTouchActive", "uTouch", "uTouchRadius", "uTouchLens",
            "uSlab", "uRimW",
        };
        for (String name : written) {
            assertTrue(name + " is declared",
                Pattern.compile("uniform \\w+ " + name + ";").matcher(GlassRefraction.AGSL).find());
        }
        assertTrue(GlassRefraction.AGSL.contains("uniform shader content;"));
        assertTrue("the clearer picture is a second input", GlassRefraction.AGSL.contains("uniform shader sharp;"));
    }

    @Test
    public void noPixelReadsMoreThanSixSamples() {
        // Was 7 content taps plus 1 sharp in a dispersed band; now 3 + 3 there, 1 + 1 elsewhere in
        // the band and 1 on the face. Four of each in the text: three on the split path, one plain.
        assertEquals(4, count(GlassRefraction.AGSL, "content.eval("));
        assertEquals(4, count(GlassRefraction.AGSL, "sharp.eval("));
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) n++;
        return n;
    }

    @Test
    public void theSlabLensFollowsTheBendAndIsNotAKnob() {
        // uSlab: none with no bend, a quarter of the way to the centre at the slider's maximum,
        // linear between, held there past it.
        assertEquals(0f, new GlassRefraction.Look(0, 28, 70).slabLens(), 0f);
        assertEquals(0.125f, new GlassRefraction.Look(15, 28, 70).slabLens(), 1e-4f);
        assertEquals(GlassRefraction.Look.MAX_SLAB_LENS,
            new GlassRefraction.Look(30, 28, 70).slabLens(), 1e-4f);
        assertEquals(GlassRefraction.Look.MAX_SLAB_LENS,
            new GlassRefraction.Look(90, 28, 70).slabLens(), 1e-4f);
        assertEquals(0f, new GlassRefraction.Look(-4, 28, 70).slabLens(), 0f);
    }

    @Test
    public void thePullReachesPastTheBandAtTheTopOfTheRange() {
        // The 0.45 hold is gone: the pull may reach one and a half bands before the cap.
        assertEquals(1.5f, GlassRefraction.Look.MAX_PULL_SHARE, 0f);
        assertEquals("the top of the range on a 20 dp band is 1.5 bands", 30f * DENSITY,
            new GlassRefraction.Look(30, 20, 30).strengthPx(DENSITY), 1e-3f);
        assertEquals("a narrow band holds it at 1.5 bands", 1.5f * 10f * DENSITY,
            new GlassRefraction.Look(30, 10, 30).strengthPx(DENSITY), 1e-3f);
        assertEquals("a shallow bend is untouched", 6f * DENSITY,
            new GlassRefraction.Look(6, 28, 30).strengthPx(DENSITY), 1e-4f);
        // Every shipped Look sits under the hold, so the slider and the pull agree; Clear's is a
        // whole band.
        int[][] looks = {{26, 26}, {24, 30}, {20, 26}};
        for (int[] look : looks)
            assertEquals(look[0] * DENSITY,
                new GlassRefraction.Look(look[0], look[1], 30).strengthPx(DENSITY), 1e-4f);
    }

    @Test
    public void theBendLivesUnderTheRimAndTheFaceStaysWhereItIs() {
        float band = 26f * DENSITY, strength = 26f * DENSITY, big = 1000f;
        // At the rim of a large surface, the whole strength: a band's worth at Clear.
        assertEquals(strength, GlassRefraction.pullPx(0f, band, strength, big), 1e-3f);
        // Zero at the band's inner edge and everywhere past it.
        assertEquals(0f, GlassRefraction.pullPx(band, band, strength, big), 0f);
        assertEquals(0f, GlassRefraction.pullPx(band * 3f, band, strength, big), 0f);
        // Concentrated at the rim: at mid-band under the old x squared's quarter.
        float mid = GlassRefraction.pullPx(band * 0.5f, band, strength, big);
        assertTrue("mid-band " + mid / strength, mid < 0.25f * strength && mid > 0.1f * strength);
        // Rising all the way from the face to the rim.
        float last = -1f;
        for (int i = 20; i >= 0; i--) {
            float pull = GlassRefraction.pullPx(band * i / 20f, band, strength, big);
            assertTrue("rises toward the rim at " + i, pull >= last);
            last = pull;
        }
        // The program runs exactly these expressions.
        assertTrue(GlassRefraction.AGSL.contains("float e = profile(x);"));
        assertTrue(GlassRefraction.AGSL.contains("return 1.0 - sqrt(max(1.0 - x * x, 0.0));"));
        assertTrue(GlassRefraction.AGSL.contains(
            "float pull = softClamp(e * uStrength, max(min(b.x, b.y) - inside, 0.0));"));
        assertTrue(GlassRefraction.AGSL.contains("return knee + over * span / (span + over);"));
        assertTrue(GlassRefraction.AGSL.contains("const float kKnee = 0.75;"));
        assertEquals(0.75f, GlassRefraction.PULL_KNEE, 0f);
        // Both the pull and the lens are offsets that vanish at e 0, so a face pixel samples
        // exactly fragCoord rather than a rounding of centre + (fragCoord - centre).
        assertTrue(GlassRefraction.AGSL.contains("float2 bent = fragCoord - n * pull;"));
        assertTrue(GlassRefraction.AGSL.contains(
            "float2 sampleCoord = bent - slabOffset(bent, center, n, e);"));
    }

    @Test
    public void thePullNeverReachesTheSurfacesMiddle() {
        // A small pane (half its short side 30 px) under the deepest bend and a wide band: the
        // sample stays short of the middle line, so it never shows the opposite edge's picture.
        float half = 30f, band = 60f * DENSITY, strength = 1.5f * band;
        for (int i = 0; i <= 60; i++) {
            float inside = i * 0.5f;
            float pull = GlassRefraction.pullPx(inside, band, strength, half);
            assertTrue("at " + inside + ": " + pull, inside + pull < half || (pull == 0f && inside >= half));
        }
        // The soft clamp is the identity up to the knee, continuous through it and below the room.
        assertEquals(7f, GlassRefraction.softClamp(7f, 10f), 0f);
        assertEquals(7.5f, GlassRefraction.softClamp(7.5f, 10f), 1e-6f);
        assertEquals(7.5f, GlassRefraction.softClamp(7.5001f, 10f), 1e-3f);
        assertTrue(GlassRefraction.softClamp(1000f, 10f) < 10f);
        assertEquals("no room, no pull", 0f, GlassRefraction.softClamp(5f, 0f), 0f);
        float last = 0f;
        for (int i = 1; i <= 100; i++) {
            float clamped = GlassRefraction.softClamp(i, 10f);
            assertTrue("monotone at " + i, clamped >= last);
            last = clamped;
        }
    }

    @Test
    public void eachKnobMovesItsTermOneWay() {
        float band = 26f * DENSITY;
        float lastPull = -1f, lastBand = 0f, lastLight = -1f, lastShade = -1f, lastSpec = -1f,
            lastK = Float.MAX_VALUE, lastDisp = -1f;
        for (int v = 0; v <= 100; v++) {
            int bend = v * 30 / 100;
            GlassRefraction.Look look = new GlassRefraction.Look(bend, Math.max(1, v * 60 / 100), v, v, v);
            float pull = GlassRefraction.pullPx(0f, band,
                new GlassRefraction.Look(bend, 26, 30).strengthPx(DENSITY), 1000f);
            assertTrue("bend", pull >= lastPull);
            assertTrue("edge width", look.bandPx(DENSITY) >= lastBand);
            float light = new GlassRefraction.Look(9, 20, v).lightAlpha();
            assertTrue("light", light >= lastLight);
            assertTrue("shade", look.shade() >= lastShade);
            float spec = new GlassRefraction.Look(9, 20, 30, v, 0).lightAlpha();
            assertTrue("specular lights", spec >= lastSpec);
            assertTrue("specular widens", look.lobeExponent() <= lastK);
            assertTrue("dispersion", look.dispersion() >= lastDisp);
            lastPull = pull;
            lastBand = look.bandPx(DENSITY);
            lastLight = light;
            lastShade = look.shade();
            lastSpec = spec;
            lastK = look.lobeExponent();
            lastDisp = look.dispersion();
        }
    }

    @Test
    public void theRimLightPeaksTowardTheLightAndIsDarkOppositeIt() {
        float k = GlassRefraction.Look.LOBE_K;
        float d = (float) Math.sqrt(0.5);
        assertEquals("full along the top edge, which faces the light", 1f, GlassRefraction.lobe(0f, -1f, k), 1e-4f);
        assertEquals("an eighth round the corner", 0.125f, GlassRefraction.lobe(-d, -d, k), 1e-3f);
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
        assertTrue(GlassRefraction.AGSL.contains("return c > 0.0 ? pow(c, k) : 0.0;"));
    }

    @Test
    public void theRimLightIsBoundedAndTheDarkEdgeSitsUnderIt() {
        // Edge light 100 is the darkest edge (0.68 of the picture) and the brightest light.
        assertEquals(0.32f, GlassRefraction.Look.MAX_SHADE, 0f);
        assertEquals(GlassRefraction.Look.MAX_SHADE, new GlassRefraction.Look(14, 28, 100).shade(), 1e-4f);
        assertEquals(0.16f, new GlassRefraction.Look(14, 28, 50).shade(), 1e-4f);
        assertEquals("no light, no dark edge", 0f, new GlassRefraction.Look(14, 28, 0).shade(), 0f);
        assertEquals(0f, new GlassRefraction.Look(14, 28, -5).shade(), 0f);

        GlassRefraction.Look full = new GlassRefraction.Look(14, 28, 100, 100, 0);
        for (int i = 0; i <= 20; i++) {
            float face = i / 20f;
            float lit = GlassRefraction.edgeLit(face, 1f, full.shade(), 1f, full.lightAlpha(), 1f);
            assertTrue("never past white at " + face, lit <= 1f);
            assertTrue("never more than the ceiling of white over the face at " + face,
                lit <= face + GlassRefraction.Look.MAX_LIGHT_ALPHA + 1e-6f);
            float dark = GlassRefraction.edgeLit(face, 1f, full.shade(), 1f, full.lightAlpha(), 0f);
            assertEquals("where the light does not reach, only the dark edge",
                face * (1f - GlassRefraction.Look.MAX_SHADE), dark, 1e-6f);
            assertTrue(lit >= dark);
        }
        // Off the rim the picture is untouched.
        assertEquals(0.4f, GlassRefraction.edgeLit(0.4f, 0f, 0.32f, 0f, 0.55f, 1f), 0f);
        assertTrue(GlassRefraction.AGSL.contains("col.rgb = col.rgb * half(1.0 - shadeMask * uShade);"));
        assertTrue(GlassRefraction.AGSL.contains("float light = rim * uRim * (lobe(n, uLobeK) + 1.5 * touchG);"));
        assertTrue(GlassRefraction.AGSL.contains("col.rgb = mix(col.rgb, half3(1.0), half(clamp(light, 0.0, 1.0)));"));
    }

    @Test
    public void theHairlineWidensWithDensityAndTheEdgeWidth() {
        assertEquals(1.5f * DENSITY, new GlassRefraction.Look(14, 28, 70).rimWidthPx(DENSITY), 1e-4f);
        assertEquals("a wide edge widens it", 0.05f * 60f * DENSITY,
            new GlassRefraction.Look(14, 60, 70).rimWidthPx(DENSITY), 1e-4f);
        assertEquals("never under two pixels", 2f,
            new GlassRefraction.Look(14, 28, 70).rimWidthPx(1f), 1e-4f);
    }

    @Test
    public void theSlabLensRunsAlongTheEdgeAndNeverFurtherThanThePull() {
        // The program drops the normal part and caps the rest at the pull: the same expressions
        // the Java mirror runs.
        assertTrue(GlassRefraction.AGSL.contains("d = d - n * dot(d, n);"));
        assertTrue(GlassRefraction.AGSL.contains("float cap = e * uStrength;"));
        assertTrue(GlassRefraction.AGSL.contains("return len > cap ? d * (cap / len) : d;"));
        GlassRefraction.Look clear = new GlassRefraction.Look(11, 28, 70);
        float slab = clear.slabLens();
        float pull = clear.strengthPx(DENSITY);
        float[] out = new float[2];

        // A full-width dock's top band (normal straight up) near its left end, at the rim (e 1):
        // the part toward the centre along the normal is dropped, and the part along the edge,
        // uncapped about 50 px, is held at the pull.
        GlassRefraction.slabOffset(0f, 0f, 540f, 60f, 0f, -1f, slab, 1f, pull, out);
        assertEquals(-pull, out[0], 1e-3f);
        assertEquals(0f, out[1], 1e-4f);

        // A small pane's top rim, 40 px left of its centre: already under the pull, uncapped.
        GlassRefraction.slabOffset(0f, 0f, 40f, 30f, 0f, -1f, slab, 1f, pull, out);
        assertEquals(-40f * slab, out[0], 1e-4f);
        assertEquals(0f, out[1], 1e-4f);

        // Straight across an edge from the centre there is nothing along the edge: no lens.
        GlassRefraction.slabOffset(0f, 0f, 540f, 0f, -1f, 0f, slab, 1f, pull, out);
        assertEquals(0f, out[0], 1e-4f);
        assertEquals(0f, out[1], 1e-4f);

        // Two surfaces of very different size kink by the same amount once both are capped.
        float[] wide = new float[2];
        GlassRefraction.slabOffset(0f, 0f, 2000f, 0f, 0f, -1f, slab, 0.5f, pull, wide);
        GlassRefraction.slabOffset(0f, 0f, 800f, 0f, 0f, -1f, slab, 0.5f, pull, out);
        assertEquals(wide[0], out[0], 1e-4f);
        assertEquals(-0.5f * pull, out[0], 1e-4f);

        // The face (e 0) takes none.
        GlassRefraction.slabOffset(0f, 0f, 540f, 60f, 0f, -1f, slab, 0f, pull, out);
        assertEquals(0f, out[0], 0f);
        assertEquals(0f, out[1], 0f);
    }

    @Test
    public void aSeamClearsTheHairlineAndItsContourOnANarrowBand() {
        // Solid's 1 dp edge ends inside the hairline; the seam is pushed past both regardless.
        GlassRefraction.Look narrow = new GlassRefraction.Look(0, 1, 100);
        assertTrue(GlassRefraction.seamReachPx(narrow, DENSITY, 0f) > narrow.rimReachPx(DENSITY));
        assertTrue(GlassRefraction.seamReachPx(narrow, 1f, 0f) > narrow.rimReachPx(1f));
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
