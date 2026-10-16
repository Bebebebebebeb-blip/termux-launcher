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

    private ChromeInk ink;

    @Before
    public void setUp() {
        FakeChromeSurfaces surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        surfaces.glassBase = NIGHT_BASE;
        ink = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), () -> { });
        ink.backdrops().setSampler(rect -> LIGHT_WALLPAPER);
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
}
