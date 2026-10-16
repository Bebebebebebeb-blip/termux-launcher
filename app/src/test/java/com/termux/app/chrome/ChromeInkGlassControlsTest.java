package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The ink check measures the glass as it is drawn: the base colour with the Tint amount's accent
 * mixed in, and the Shade under it. No Tint or Shade value can carry a band past its target
 * unmeasured.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ChromeInkGlassControlsTest {

    private static final int NIGHT_BASE = 0xFF1C1B1F;
    private static final int LABEL = 0xFFE6E1E5;
    private static final int LIGHT_WALLPAPER = 0xFFE8E4DA;
    private static final Rect KEYBOARD_RECT = new Rect(0, 1800, 1080, 2400);
    private static final int CAP = OnGlass.withAlpha(0xFF2B2930, 96);

    private static final int DAY_BASE = 0xFFFEF7FF;
    private static final int DARK_WALLPAPER = 0xFF202226;
    private static final Rect STATUS_RECT = new Rect(0, 0, 1080, 96);
    /** termux_primary, light and night: the chrome's two inks. */
    private static final int LIGHT_INK = 0xFF345CA8;
    private static final int NIGHT_INK = 0xFFB8C7FF;

    private FakeChromeSurfaces surfaces;
    private ChromeInk ink;
    private int wallpaper = LIGHT_WALLPAPER;

    @Before
    public void setUp() {
        surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        surfaces.glassBase = NIGHT_BASE;
        ink = fresh();
    }

    /** A new ink check over the surfaces and the wallpaper as they stand. */
    private ChromeInk fresh() {
        ChromeInk fresh = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), () -> { });
        fresh.backdrops().setSampler(rect -> wallpaper);
        return fresh;
    }

    private OnGlass.Resolution keys() {
        return ink.onFixedInk(GlassBackdropCache.Band.KEYBOARD, KEYBOARD_RECT, LABEL, LABEL, CAP,
            OnGlass.TARGET_BODY_TEXT);
    }

    @Test
    public void theVeilIsTheTintedBaseTheSurfaceTold() {
        int tinted = GlassLook.SCHEME.withAmountPercent(100)
            .withAccentColors(0xFF4F378B, 0xFFD0BCFF).tintBase(NIGHT_BASE);
        ink.noteBandGlass(GlassBackdropCache.Band.KEYBOARD, 0.2f, false, 0f, 0.8f, tinted,
            Color.TRANSPARENT);
        OnGlass.Resolution accented = keys();
        assertTrue("a light wallpaper still asks for a veil", Color.alpha(accented.veil) > 0);
        assertEquals("of the colour the base is really drawn in", OnGlass.opaque(tinted),
            OnGlass.opaque(accented.veil));

        ink.noteBandGlass(GlassBackdropCache.Band.KEYBOARD, 0.2f, false, 0f, 0.8f);
        assertEquals("the untold glass is the mode's base", OnGlass.opaque(NIGHT_BASE),
            OnGlass.opaque(keys().veil));
    }

    @Test
    public void shadeUnderTheTintIsMeasuredAndBuysPartOfTheVeil() {
        ink.noteBandGlass(GlassBackdropCache.Band.KEYBOARD, 0.2f, false, 0f, 0.8f, NIGHT_BASE,
            Color.TRANSPARENT);
        OnGlass.Resolution bare = keys();
        int shade = GlassShade.color(NIGHT_BASE, 100);
        ink.noteBandGlass(GlassBackdropCache.Band.KEYBOARD, 0.2f, false, 0f, 0.8f, NIGHT_BASE,
            shade);
        OnGlass.Resolution shaded = keys();
        assertTrue("dark Shade dims the light wallpaper, so less veil: " + Color.alpha(shaded.veil)
            + " vs " + Color.alpha(bare.veil), Color.alpha(shaded.veil) < Color.alpha(bare.veil));
        assertTrue("and the label reads: " + shaded.ratio,
            shaded.ratio >= OnGlass.TARGET_BODY_TEXT && !shaded.shortfall);
    }

    // --------------------------------------------------------------- Shade in the ink's choice

    /** The status band at Clear's 10% with {@code shade} under it, resolved for the chrome's inks. */
    private OnGlass.Resolution status(ChromeInk on, int base, int shade) {
        on.noteBandGlass(GlassBackdropCache.Band.DOCK, 0.10f, true, 0f, 1f, base, shade);
        return on.onGlass(GlassBackdropCache.Band.DOCK, STATUS_RECT, LIGHT_INK, NIGHT_INK,
            OnGlass.TARGET_BODY_TEXT);
    }

    /**
     * Light theme, Clear, a dark wallpaper: the white Shade lifts the glass, and the ink is chosen
     * against what is drawn, so the label reads at Shade 0 and at Shade 100 alike.
     */
    @Test
    public void lightShadeOverADarkWallpaperStillReads() {
        surfaces.glassBase = DAY_BASE;
        wallpaper = DARK_WALLPAPER;
        for (int percent : new int[] {0, 50, 100}) {
            ChromeInk on = fresh();
            OnGlass.Resolution label = status(on, DAY_BASE, GlassShade.color(DAY_BASE, percent));
            assertTrue("Shade " + percent + ": " + label.ratio,
                label.ratio >= OnGlass.TARGET_BODY_TEXT && !label.shortfall);
        }
    }

    /**
     * The ink vote counts the Shade: over a mid wallpaper the light glass is pale ink's without
     * it and dark ink's once the white Shade lifts it.
     */
    @Test
    public void theInkVoteCountsTheShade() {
        surfaces.glassBase = DAY_BASE;
        wallpaper = 0xFF606060;
        ChromeInk bare = fresh();
        status(bare, DAY_BASE, 0);
        assertEquals(ChromeInk.Polarity.PALE_INK, bare.polarity());
        ChromeInk shaded = fresh();
        status(shaded, DAY_BASE, GlassShade.color(DAY_BASE, 100));
        assertEquals(ChromeInk.Polarity.DARK_INK, shaded.polarity());
    }

    /**
     * Dark theme near the threshold: the pale label on Clear over a mid wallpaper needs a little
     * veil to reach its target; with the black Shade under it, it reads with no more veil than
     * that, and the Shade is kept whole.
     */
    @Test
    public void darkShadeNearTheThresholdHelpsAndIsKept() {
        wallpaper = 0xFF707070;
        ChromeInk bareInk = fresh();
        OnGlass.Resolution bare = status(bareInk, NIGHT_BASE, 0);
        int shade = GlassShade.color(NIGHT_BASE, 100);
        ChromeInk shadedInk = fresh();
        OnGlass.Resolution shaded = status(shadedInk, NIGHT_BASE, shade);
        assertTrue("reads with the Shade: " + shaded.ratio,
            shaded.ratio >= OnGlass.TARGET_BODY_TEXT && !shaded.shortfall);
        assertTrue("no more veil than without: " + Color.alpha(shaded.veil) + " vs "
            + Color.alpha(bare.veil), Color.alpha(shaded.veil) <= Color.alpha(bare.veil));
        assertEquals("a Shade that helps is never held back", shade,
            shadedInk.heldShade(GlassBackdropCache.Band.DOCK, shade));
    }

    /**
     * Where the Shade alone would leave a fixed label short of its target (a dark label over a
     * light wallpaper on dark glass, which the black Shade darkens), it is held back until the
     * label reads; it is never what makes the ink unreadable.
     */
    @Test
    public void aShadeThatWouldBreakTheTargetIsHeldBack() {
        wallpaper = 0xFFB0B0B0;
        int label = 0xFF1C1B1F;
        int shade = GlassShade.color(NIGHT_BASE, 100);
        ink.noteBandGlass(GlassBackdropCache.Band.KEYBOARD, 0.2f, false, 0f, 0.8f, NIGHT_BASE,
            Color.TRANSPARENT);
        OnGlass.Resolution bare = ink.onFixedInk(GlassBackdropCache.Band.KEYBOARD, KEYBOARD_RECT,
            label, label, Color.TRANSPARENT, OnGlass.TARGET_BODY_TEXT);
        assertTrue("reads without the Shade: " + bare.ratio, !bare.shortfall);
        ink.noteBandGlass(GlassBackdropCache.Band.KEYBOARD, 0.2f, false, 0f, 0.8f, NIGHT_BASE,
            shade);
        OnGlass.Resolution whole = ink.onFixedInk(GlassBackdropCache.Band.KEYBOARD, KEYBOARD_RECT,
            label, label, Color.TRANSPARENT, OnGlass.TARGET_BODY_TEXT);
        assertTrue("the whole Shade would break it: " + whole.ratio, whole.shortfall);

        int held = ink.heldShade(GlassBackdropCache.Band.KEYBOARD, shade);
        assertTrue("held back: " + Color.alpha(held), Color.alpha(held) < Color.alpha(shade));
        OnGlass.Resolution after = ink.onFixedInk(GlassBackdropCache.Band.KEYBOARD, KEYBOARD_RECT,
            label, label, Color.TRANSPARENT, OnGlass.TARGET_BODY_TEXT);
        assertTrue("and the label reads: " + after.ratio, !after.shortfall);
    }
}
