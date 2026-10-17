package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.termux.app.terminal.inappkeyboard.InAppKeyboardPaletteFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import juloo.keyboard2.Theme;

/**
 * The keyboard area's labels judged on the glass they stand on (pass 2, 2026-10-17): the A&ndash;Z
 * letters on the dock's glass rather than the wallpaper alone, the extra keys and the in-app
 * keyboard's legends on the measured face rather than the theme's. Over a pale face at opacity
 * 100 and over a dark one, and re-chosen when the opacity moves.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class KeyboardAreaInkTest {

    /** The light theme's glass base: white glass at opacity 100 is a pale face. */
    private static final int LIGHT_BASE = 0xFFF7F2FA;
    /** The Material dark neutral. */
    private static final int NIGHT_BASE = 0xFF1C1B1F;
    /** A dark wallpaper, blurred. */
    private static final int DARK_WALLPAPER = 0xFF202226;
    /** The chrome's two inks ({@code termux_primary}, light and night). */
    private static final int DARK_INK = 0xFF345CA8;
    private static final int PALE_INK = 0xFFB8C7FF;
    /** The dark theme's on-surface, the extra keys' theme colour there. */
    private static final int NIGHT_ON_SURFACE = 0xFFE6E1E5;
    /** The light theme's on-surface. */
    private static final int LIGHT_ON_SURFACE = 0xFF1D1B20;

    private static final Rect AZ_RECT = new Rect(0, 2000, 1080, 2060);
    private static final Rect EXTRA_KEYS_RECT = new Rect(0, 2060, 1080, 2160);
    private static final Rect KEYBOARD_RECT = new Rect(0, 1400, 1080, 2000);

    private FakeChromeSurfaces surfaces;
    private ChromeInk ink;
    private GlassSurfaceFactory glass;

    @Before
    public void setUp() {
        surfaces = new FakeChromeSurfaces(RuntimeEnvironment.getApplication());
        surfaces.glassBase = LIGHT_BASE;
        ink = new ChromeInk(surfaces, new WallpaperBlurCache(surfaces), () -> { });
        ink.backdrops().setSampler(rect -> DARK_WALLPAPER);
        glass = new GlassSurfaceFactory(surfaces, ink);
    }

    private static boolean pale(int color) {
        return ColorUtils.calculateLuminance(OnGlass.opaque(color)) > 0.5d;
    }

    @NonNull
    private OnGlass.Resolution azLetters() {
        return GlassInk.bareBandInk(ink, GlassBackdropCache.Band.AZ_STRIP, AZ_RECT,
            Color.TRANSPARENT, DARK_INK, PALE_INK, OnGlass.TARGET_LARGE_TEXT);
    }

    // ------------------------------------------------------------------ the A-Z letters

    @Test
    public void theLettersStandOnTheDocksGlassNotTheWallpaper() {
        OnGlass.Resolution bare = azLetters();
        assertFalse("untold, the strip is the dark wallpaper", pale(bare.surface));

        assertTrue(glass.noteDockGlassUnder(GlassBackdropCache.Band.AZ_STRIP, 1f));
        OnGlass.Resolution onGlass = azLetters();
        assertTrue("a pale face at opacity 100", pale(onGlass.surface));
        assertFalse("dark letters on it", pale(onGlass.ink));
        assertTrue(OnGlass.ratio(onGlass.ink, onGlass.surface) >= OnGlass.TARGET_LARGE_TEXT);
    }

    @Test
    public void theLettersOnADarkFaceArePale() {
        surfaces.glassBase = NIGHT_BASE;
        glass.noteDockGlassUnder(GlassBackdropCache.Band.AZ_STRIP, 1f);
        OnGlass.Resolution letters = azLetters();
        assertFalse(pale(letters.surface));
        assertTrue(pale(letters.ink));
        assertTrue(OnGlass.ratio(letters.ink, letters.surface) >= OnGlass.TARGET_LARGE_TEXT);
    }

    /** The dock's opacity moves: the face is told again and the letters re-chosen on it. */
    @Test
    public void anOpacityChangeReChoosesTheLetters() {
        glass.noteDockGlassUnder(GlassBackdropCache.Band.AZ_STRIP, 1f);
        assertFalse(pale(azLetters().ink));
        assertFalse("the same glass told again is no change",
            glass.noteDockGlassUnder(GlassBackdropCache.Band.AZ_STRIP, 1f));

        assertTrue("a new opacity moves the face",
            glass.noteDockGlassUnder(GlassBackdropCache.Band.AZ_STRIP, 0.1f));
        OnGlass.Resolution thin = azLetters();
        assertFalse("thin light glass over a dark wallpaper is a dark face", pale(thin.surface));
        assertTrue("pale letters there", pale(thin.ink));
        assertTrue(OnGlass.ratio(thin.ink, thin.surface) >= OnGlass.TARGET_LARGE_TEXT);
    }

    // ------------------------------------------------------------------ the extra keys

    private int extraKeysInk(int themeInk) {
        int face = ink.faceUnder(GlassBackdropCache.Band.DOCK, EXTRA_KEYS_RECT);
        return GlassInk.faceInk(face, themeInk, ink.target(OnGlass.TARGET_BODY_TEXT));
    }

    @Test
    public void extraKeysOnAPaleFaceTakeTheDarkSide() {
        glass.noteDockGlassUnder(GlassBackdropCache.Band.DOCK, 1f);
        int face = ink.faceUnder(GlassBackdropCache.Band.DOCK, EXTRA_KEYS_RECT);
        assertTrue(pale(face));
        int keys = extraKeysInk(NIGHT_ON_SURFACE);
        assertFalse("the dark theme's pale label moves dark", pale(keys));
        assertTrue(OnGlass.ratio(keys, face) >= OnGlass.TARGET_BODY_TEXT);
        assertEquals("the light theme's own label already reads there: kept", LIGHT_ON_SURFACE,
            extraKeysInk(LIGHT_ON_SURFACE));
    }

    @Test
    public void extraKeysOnADarkFaceTakeThePaleSide() {
        surfaces.glassBase = NIGHT_BASE;
        glass.noteDockGlassUnder(GlassBackdropCache.Band.DOCK, 1f);
        int face = ink.faceUnder(GlassBackdropCache.Band.DOCK, EXTRA_KEYS_RECT);
        assertFalse(pale(face));
        assertEquals("the theme's colour where it reads", NIGHT_ON_SURFACE,
            extraKeysInk(NIGHT_ON_SURFACE));
        int keys = extraKeysInk(LIGHT_ON_SURFACE);
        assertTrue("the light theme's dark label moves to the pale side",
            GlassInk.isPaleSide(keys, face));
        assertTrue(OnGlass.ratio(keys, face) >= OnGlass.TARGET_BODY_TEXT);
    }

    @Test
    public void anOpacityChangeReChoosesTheExtraKeys() {
        glass.noteDockGlassUnder(GlassBackdropCache.Band.DOCK, 1f);
        int opaque = extraKeysInk(NIGHT_ON_SURFACE);
        assertFalse(pale(opaque));
        glass.noteDockGlassUnder(GlassBackdropCache.Band.DOCK, 0.1f);
        assertEquals("over the dark wallpaper the theme's pale label is back", NIGHT_ON_SURFACE,
            extraKeysInk(NIGHT_ON_SURFACE));
    }

    // ------------------------------------------------------------------ the keyboard's legends

    private void askKeyboardBand(float opacity) {
        ink.onFixedInk(GlassBackdropCache.Band.KEYBOARD, KEYBOARD_RECT, NIGHT_ON_SURFACE,
            NIGHT_ON_SURFACE, Color.TRANSPARENT, OnGlass.TARGET_BODY_TEXT);
        glass.veilOnlyLayer(GlassBackdropCache.Band.KEYBOARD, opacity, 0f, 1f, false);
    }

    private static void assertLegendsRead(@NonNull Theme.Palette palette, int face, String at) {
        int key = ColorUtils.compositeColors(palette.keyBackground, OnGlass.opaque(face));
        int function = ColorUtils.compositeColors(palette.functionKeyBackground,
            OnGlass.opaque(face));
        assertTrue("label " + at, ColorUtils.calculateContrast(palette.labelColor, key) >= 4.5d);
        assertTrue("sub-label " + at,
            ColorUtils.calculateContrast(palette.subLabelColor, key) >= 4.5d);
        assertTrue("function label " + at,
            ColorUtils.calculateContrast(palette.functionLabelColor, function) >= 4.5d);
    }

    @Test
    public void theLegendsAreCheckedOnTheMeasuredFace() {
        Context context = RuntimeEnvironment.getApplication();
        for (String variant : new String[] {"system", "light", "dark"}) {
            askKeyboardBand(1f);
            int pale = ink.bandFace(GlassBackdropCache.Band.KEYBOARD);
            assertTrue(pale(pale));
            assertLegendsRead(InAppKeyboardPaletteFactory.createGlass(context, variant,
                GlassLook.SCHEME, pale), pale, variant + " on a pale face");

            surfaces.glassBase = NIGHT_BASE;
            askKeyboardBand(1f);
            int dark = ink.bandFace(GlassBackdropCache.Band.KEYBOARD);
            assertFalse(pale(dark));
            assertLegendsRead(InAppKeyboardPaletteFactory.createGlass(context, variant,
                GlassLook.SCHEME, dark), dark, variant + " on a dark face");
            surfaces.glassBase = LIGHT_BASE;
        }
    }

    /** Opacity 100 to 10 over a dark wallpaper: the face moves, and the legends follow it. */
    @Test
    public void anOpacityChangeReChoosesTheLegends() {
        Context context = RuntimeEnvironment.getApplication();
        askKeyboardBand(1f);
        int opaque = ink.bandFace(GlassBackdropCache.Band.KEYBOARD);
        askKeyboardBand(0.1f);
        int thin = ink.bandFace(GlassBackdropCache.Band.KEYBOARD);
        assertNotEquals(opaque, thin);
        assertFalse(pale(thin));
        assertLegendsRead(InAppKeyboardPaletteFactory.createGlass(context, "light",
            GlassLook.SCHEME, thin), thin, "after the opacity change");
    }

    /** Not measured yet: the theme's glass base stands in, exactly as before. */
    @Test
    public void anUnmeasuredFaceIsTheThemesBase() {
        Context context = RuntimeEnvironment.getApplication();
        assertEquals(Color.TRANSPARENT, ink.bandFace(GlassBackdropCache.Band.KEYBOARD));
        Theme.Palette before = InAppKeyboardPaletteFactory.createGlass(context, "dark");
        Theme.Palette unmeasured = InAppKeyboardPaletteFactory.createGlass(context, "dark",
            GlassLook.SCHEME, Color.TRANSPARENT);
        assertEquals(before.labelColor, unmeasured.labelColor);
        assertEquals(before.subLabelColor, unmeasured.subLabelColor);
        assertEquals(before.functionLabelColor, unmeasured.functionLabelColor);
    }
}
