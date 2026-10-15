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
 * The pure half of the glass: how the five knobs become uniforms, and where a surface's rim runs.
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
        // pull. The rim light is the "medium" depth every Look declares (18% of MAX_RIM, spec
        // appearance-layout-editor §4), since the 1dp drawable rim carries the edge and the
        // shader only adds a whisper on top.
        GlassRefraction.Look look = GlassRefraction.Look.DEFAULT;
        assertEquals(20f * DENSITY, look.bandPx(DENSITY), 1e-4f);
        assertEquals(9f * DENSITY, look.strengthPx(DENSITY), 1e-4f);
        assertEquals(0.18f * GlassRefraction.Look.MAX_RIM, look.rim(), 1e-4f);
    }

    @Test
    public void theKnobsMapToUniformsAtTheScreensDensity() {
        GlassRefraction.Look look = new GlassRefraction.Look(12, 30, 50);
        assertEquals(12f * DENSITY, look.strengthPx(DENSITY), 1e-4f);
        assertEquals(30f * DENSITY, look.bandPx(DENSITY), 1e-4f);
        assertEquals(GlassRefraction.Look.MAX_RIM * 0.5f, look.rim(), 1e-4f);
        assertEquals("all the way up is the brightest rim", GlassRefraction.Look.MAX_RIM,
            new GlassRefraction.Look(0, 1, 100).rim(), 1e-4f);
        assertEquals(0f, new GlassRefraction.Look(0, 1, 0).rim(), 1e-4f);
        assertEquals(0f, new GlassRefraction.Look(0, 1, 0).strengthPx(DENSITY), 1e-4f);
    }

    @Test
    public void theEdgeTermsAreOffByDefaultSoExistingLooksAreUnchanged() {
        GlassRefraction.Look look = GlassRefraction.Look.DEFAULT;
        assertEquals(0, look.specularPercent);
        assertEquals(0, look.dispersionPercent);
        assertEquals(0f, look.specular(), 0f);
        assertEquals(0f, look.dispersionPx(DENSITY), 0f);
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
    public void specularAndDispersionMapToUniformsAndAreClamped() {
        // uSpec, the bevel light's brightness, and uDisp, the colour split's width at the rim.
        GlassRefraction.Look look = new GlassRefraction.Look(28, 40, 85, 70, 40);
        assertEquals(0.7f * GlassRefraction.Look.MAX_SPEC, look.specular(), 1e-4f);
        assertEquals("all the way up is the brightest bevel", GlassRefraction.Look.MAX_SPEC,
            new GlassRefraction.Look(0, 1, 0, 100, 0).specular(), 1e-4f);
        assertEquals("all the way up is the widest split", GlassRefraction.Look.MAX_DISPERSION_DP * DENSITY,
            new GlassRefraction.Look(0, 1, 0, 0, 100).dispersionPx(DENSITY), 1e-4f);
        assertEquals(0.4f * GlassRefraction.Look.MAX_DISPERSION_DP * DENSITY,
            look.dispersionPx(DENSITY), 1e-4f);
        assertEquals(GlassRefraction.Look.MAX_SPEC,
            new GlassRefraction.Look(0, 1, 0, 400, 0).specular(), 1e-4f);
        assertEquals(0f, new GlassRefraction.Look(0, 1, 0, -5, -5).specular(), 0f);
        assertEquals(0f, new GlassRefraction.Look(0, 1, 0, -5, -5).dispersionPx(DENSITY), 0f);
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
    public void theBevelLightReachesHalfTheEdgeWidthWithinItsLimits() {
        assertEquals(10f * DENSITY, GlassRefraction.Look.DEFAULT.bevelPx(DENSITY), 1e-4f);
        assertEquals(8f * DENSITY, new GlassRefraction.Look(9, 16, 18).bevelPx(DENSITY), 1e-4f);
        assertEquals("a wide band keeps a thin lip", GlassRefraction.Look.MAX_BEVEL_DP * DENSITY,
            new GlassRefraction.Look(9, 60, 18).bevelPx(DENSITY), 1e-4f);
        assertEquals("a narrow band still lights a pixel's worth", GlassRefraction.Look.MIN_BEVEL_DP * DENSITY,
            new GlassRefraction.Look(9, 1, 18).bevelPx(DENSITY), 1e-4f);
    }

    @Test
    public void theBevelLightNeverReachesPastTheBand() {
        // So the seam reach, which clears the band, clears the bevel light too.
        float[] densities = {0.75f, 1f, 2f, DENSITY, 4f};
        for (float density : densities) {
            for (int edge = 0; edge <= 60; edge++) {
                GlassRefraction.Look look = new GlassRefraction.Look(9, edge, 18, 100, 0);
                assertTrue("edge " + edge + "dp at " + density,
                    look.bevelPx(density) <= look.bandPx(density));
                assertTrue("edge " + edge + "dp at " + density, look.bevelPx(density) > 0f);
            }
        }
    }

    @Test
    public void theProgramDeclaresEveryUniformTheJavaSideWrites() {
        // A uniform the program does not declare throws on the write, and the surface drops to plain.
        String[] written = {
            "uFrameScale", "uFrameOffset", "uLocalX", "uLocalY", "uLocalW", "uRectMin", "uRectMax",
            "uRadius", "uBand", "uStrength", "uRim", "uDisp", "uSpec", "uBevel", "uDensity",
            "uLensActive", "uLensCenter", "uLensHalf", "uLensRadius", "uLensStrength", "uLensFeather",
            "uClarity", "uThick", "uTouchActive", "uTouch", "uTouchRadius", "uTouchLens",
            "uSlab", "uRimW", "uContour",
        };
        for (String name : written) {
            assertTrue(name + " is declared",
                Pattern.compile("uniform \\w+ " + name + ";").matcher(GlassRefraction.AGSL).find());
        }
        assertTrue(GlassRefraction.AGSL.contains("uniform shader content;"));
        assertTrue("the clearer picture is a second input", GlassRefraction.AGSL.contains("uniform shader sharp;"));
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
    public void theHairlineWidensWithDensityAndTheEdgeWidth() {
        assertEquals(1.5f * DENSITY, new GlassRefraction.Look(14, 28, 70).rimWidthPx(DENSITY), 1e-4f);
        assertEquals("a wide edge widens it", 0.05f * 60f * DENSITY,
            new GlassRefraction.Look(14, 60, 70).rimWidthPx(DENSITY), 1e-4f);
        assertEquals("never under two pixels", 2f,
            new GlassRefraction.Look(14, 28, 70).rimWidthPx(1f), 1e-4f);
    }

    @Test
    public void theContourFollowsTheLight() {
        // Edge light 100 is the brightest rim and the darkest contour (0.85 of the picture).
        assertEquals(0.8f, GlassRefraction.Look.MAX_RIM, 0f);
        assertEquals(GlassRefraction.Look.MAX_CONTOUR, new GlassRefraction.Look(14, 28, 100).contour(), 1e-4f);
        assertEquals(0.075f, new GlassRefraction.Look(14, 28, 50).contour(), 1e-4f);
        assertEquals("no light, no contour", 0f, new GlassRefraction.Look(14, 28, 0).contour(), 0f);
        assertEquals(0f, new GlassRefraction.Look(14, 28, -5).contour(), 0f);
    }

    @Test
    public void theBendActsAcrossTheWholeBandAndLeavesTheFaceWhereItIs() {
        // The squared profile, not the quarter circle that was near zero over most of the band.
        assertTrue(GlassRefraction.AGSL.contains("float e = x * x;"));
        assertTrue(!GlassRefraction.AGSL.contains("sqrt("));
        // Both the pull and the lens are offsets that vanish at e 0, so a face pixel samples
        // exactly fragCoord rather than a rounding of centre + (fragCoord - centre).
        assertTrue(GlassRefraction.AGSL.contains("float2 bent = fragCoord - n * (e * uStrength);"));
        assertTrue(GlassRefraction.AGSL.contains(
            "float2 sampleCoord = bent - (bent - center) * (uSlab * e);"));
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
