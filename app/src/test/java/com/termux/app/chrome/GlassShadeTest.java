package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Shade: black up to 40% on dark glass, white up to 25% on light glass, nothing at 0. */
public class GlassShadeTest {

    private static final int DARK_BASE = 0xFF1C1B1F;
    private static final int LIGHT_BASE = 0xFFFEF7FF;

    @Test
    public void darkGlassDarkensUpToItsCap() {
        int full = GlassShade.color(DARK_BASE, 100);
        assertEquals("black", 0x000000, full & 0xFFFFFF);
        assertEquals(Math.round(255 * GlassShade.DARK_CAP), full >>> 24);
        assertEquals(0.40f, GlassShade.DARK_CAP, 0f);
        assertEquals(Math.round(255 * 0.20f), GlassShade.color(DARK_BASE, 50) >>> 24);
        assertEquals("clamped", full, GlassShade.color(DARK_BASE, 500));
    }

    @Test
    public void lightGlassWhitensUnderALowerCapSoItNeverGoesMilky() {
        int full = GlassShade.color(LIGHT_BASE, 100);
        assertEquals("white", 0xFFFFFF, full & 0xFFFFFF);
        assertEquals(Math.round(255 * GlassShade.LIGHT_CAP), full >>> 24);
        assertEquals(0.25f, GlassShade.LIGHT_CAP, 0f);
        assertTrue(GlassShade.LIGHT_CAP < GlassShade.DARK_CAP);
        for (int percent = 0; percent <= 100; percent++)
            assertTrue("never past the cap at " + percent,
                GlassShade.alpha(GlassShade.color(LIGHT_BASE, percent)) <= GlassShade.LIGHT_CAP + 1e-3f);
    }

    @Test
    public void offIsNothingAtAll() {
        assertEquals(0, GlassShade.color(DARK_BASE, 0));
        assertEquals(0, GlassShade.color(LIGHT_BASE, -20));
        assertEquals("a tint over no shade is the tint", 0x80123456,
            GlassShade.under(0x80123456, 0));
        assertEquals(0.3f, GlassShade.cover(0.3f, 0), 1e-6f);
    }

    @Test
    public void aTintOverShadeIsOneColourWithTheirCover() {
        int shade = GlassShade.color(DARK_BASE, 100);
        int tint = 0x80FFFFFF;
        int flat = GlassShade.under(tint, shade);
        float a = 128 / 255f;
        float s = (shade >>> 24) / 255f;
        assertEquals(Math.round((a + s * (1f - a)) * 255f), flat >>> 24);
        assertTrue("darker than the tint alone", (flat & 0xFF) < 0xFF);
        assertEquals("the shade alone under a clear tint", shade, GlassShade.under(0, shade));
        assertEquals(1f - (1f - 0.6f) * (1f - s), GlassShade.cover(0.6f, shade), 1e-6f);
        assertEquals(1f, GlassShade.cover(1f, shade), 1e-6f);
    }
}
