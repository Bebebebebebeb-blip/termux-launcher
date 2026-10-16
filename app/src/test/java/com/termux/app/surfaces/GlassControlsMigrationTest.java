package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.app.chrome.GlassLook;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The glass controls' one-time migration: a shipped Look stored at its old values becomes the
 * Look as it ships, drawing the same colour; any other look keeps its colour under the new Tint
 * amount and has its blur and grain moved onto the Glass curve; the saved Custom is rewritten.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class GlassControlsMigrationTest {

    private static final int BASE = 0xFF141218;
    private static final int CONTAINER = 0xFF4F378B;
    private static final int ACCENT = 0xFFD0BCFF;

    private SharedPreferences store;
    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences("glass-controls-migration-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    private static SurfacePresets.Preset preset(String id) {
        for (SurfacePresets.Preset preset : SurfacePresets.presets())
            if (preset.id.equals(id)) return preset;
        throw new AssertionError(id);
    }

    /** {@code id} as a build before the glass controls stored it. */
    private void storeOldLook(String id) {
        SurfacePresets.apply(preferences, preset(id));
        SharedPreferences.Editor editor = store.edit()
            .remove(TERMUX_APP.KEY_SURFACE_GLASS_TINT_HUE)
            .remove(TERMUX_APP.KEY_SURFACE_GLASS_SHADE)
            .remove(TERMUX_APP.KEY_SURFACE_GLASS_VIBRANCY)
            .putInt(TERMUX_APP.KEY_SURFACE_BASE_TINT, 100);
        switch (id) {
            case "minimal":
                editor.putInt(TERMUX_APP.KEY_SURFACE_BASE_BLUR, 4)
                    .putInt(TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 4);
                break;
            case "frost":
                editor.putInt(TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 8);
                break;
            case "stock":
                editor.putInt(TERMUX_APP.KEY_SURFACE_BASE_BLUR, 10)
                    .putInt(TERMUX_APP.KEY_SURFACE_BASE_GRAIN, 10)
                    .putString(TERMUX_APP.KEY_SURFACE_GLASS_TINT, TERMUX_APP.GLASS_TINT_MATERIAL);
                break;
            default:
                break;
        }
        editor.commit();
    }

    /** The base colour the dock's tint is drawn in now, its accent resolved. */
    private int dockColourNow() {
        return GlassLook.of(preferences).withAmountPercent(preferences.getDockTintStrength())
            .withAccentColors(CONTAINER, ACCENT).tintBase(BASE);
    }

    /** What the old build drew for each Look at the full strength: its ink, or the Material blend. */
    private static int oldColour(String id) {
        switch (id) {
            case "frost":
                return 0xFF161822;
            case "stock":
                return GlassLook.SCHEME.withAmountPercent(100).withAccentColors(CONTAINER, ACCENT)
                    .tintBase(BASE);
            default:
                return BASE;
        }
    }

    @Test
    public void everyShippedLookStoredBeforeIsTheLookAsItShipsAndDrawsTheSameColour() {
        for (String id : new String[] {"minimal", "frost", "stock", "solid"}) {
            store.edit().clear().commit();
            storeOldLook(id);
            assertFalse(id, SurfacePresets.matches(preferences, preset(id)));

            GlassControlsMigration.run(preferences);

            assertTrue(id + " keeps its stop", SurfacePresets.matches(preferences, preset(id)));
            assertEquals(id + " draws the colour it drew", oldColour(id), dockColourNow());
            assertEquals(GlassControlsMigration.FORMAT,
                store.getInt(TERMUX_APP.KEY_SURFACE_GLASS_CONTROLS_FORMAT, 0));
        }
    }

    /** A Look at an earlier generation's edge is recognised too, and readopted whole. */
    @Test
    public void anOldClearAtAnEarlierEdgeIsStillClear() {
        storeOldLook("minimal");
        preferences.setFancierGlassBendDp(26);
        preferences.setFancierGlassEdgeWidthDp(26);
        preferences.setFancierGlassEdgeLightPercent(50);
        preferences.setFancierGlassDispersionPercent(35);
        preferences.setFancierGlassClarityPercent(100);
        GlassControlsMigration.run(preferences);
        assertTrue(SurfacePresets.matches(preferences, preset("minimal")));
    }

    @Test
    public void aMaterialLookKeepsItsStrengthsAsAmountsOnTheSchemeInk() {
        preferences.setSurfaceGlassTint(TERMUX_APP.GLASS_TINT_MATERIAL);
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 17);
        preferences.setSurfaceBaseValue(SurfaceProperty.TINT, 60);
        preferences.detachSurfaceValue(SurfaceSlot.STATUS, SurfaceProperty.TINT, 30);
        int before = GlassLook.SCHEME.withAmountPercent(60).withAccentColors(CONTAINER, ACCENT)
            .tintBase(BASE);

        GlassControlsMigration.run(preferences);

        assertEquals(TERMUX_APP.GLASS_TINT_SCHEME, preferences.getSurfaceGlassTint());
        assertEquals(60, preferences.getDockTintStrength());
        assertEquals(30, preferences.getStatusBarTintStrength());
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.TINT));
        assertEquals("the same colour", before, dockColourNow());
    }

    @Test
    public void aSchemeOrObsidianLookWearsItsOwnColour() {
        preferences.setSurfaceGlassTint(TERMUX_APP.GLASS_TINT_OBSIDIAN);
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 17);
        preferences.setSurfaceBaseValue(SurfaceProperty.TINT, 40);
        preferences.detachSurfaceValue(SurfaceSlot.KEYBOARD, SurfaceProperty.TINT, 70);

        GlassControlsMigration.run(preferences);

        assertEquals(TERMUX_APP.GLASS_TINT_OBSIDIAN, preferences.getSurfaceGlassTint());
        assertEquals(0, preferences.getDockTintStrength());
        assertEquals(0, preferences.getInAppKeyboardTintStrength());
        assertEquals("Obsidian's ink, untouched", 0xFF161822, dockColourNow());
    }

    /** A store that never wrote a tint read the old default, the full strength. */
    @Test
    public void aTintNeverStoredIsTheOldFullStrength() {
        preferences.setSurfaceGlassTint(TERMUX_APP.GLASS_TINT_MATERIAL);
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 17);
        assertFalse(store.contains(TERMUX_APP.KEY_SURFACE_BASE_TINT));

        GlassControlsMigration.run(preferences);

        assertEquals(100, preferences.getDockTintStrength());
        assertEquals(100, preferences.getTerminalTintStrength());
    }

    @Test
    public void blurAndGrainMoveToTheNearestGlassAndASurfaceTakesBoth() {
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 10);
        preferences.setSurfaceBaseValue(SurfaceProperty.GRAIN, 10);
        preferences.detachSurfaceValue(SurfaceSlot.DOCK, SurfaceProperty.BLUR, 20);

        GlassControlsMigration.run(preferences);

        assertEquals(13, preferences.getSurfaceBaseValue(SurfaceProperty.BLUR));
        assertEquals(5, preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN));
        int[] dock = GlassControls.snapped(20, 10);
        assertEquals(dock[0], preferences.getExtraKeysBlurRadius());
        assertEquals(dock[1], preferences.getDockGlassGrain());
        assertFalse(preferences.isSurfaceInheriting(SurfaceSlot.DOCK, SurfaceProperty.GRAIN));
        assertTrue("an untouched surface still follows Base",
            preferences.isSurfaceInheriting(SurfaceSlot.STATUS, SurfaceProperty.BLUR));
    }

    @Test
    public void noFrostStaysNoFrost() {
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 0);
        preferences.setSurfaceBaseValue(SurfaceProperty.GRAIN, 0);
        preferences.setSurfaceBaseValue(SurfaceProperty.OPACITY, 80);
        GlassControlsMigration.run(preferences);
        assertEquals(0, preferences.getSurfaceBaseValue(SurfaceProperty.BLUR));
        assertEquals(0, preferences.getSurfaceBaseValue(SurfaceProperty.GRAIN));
    }

    @Test
    public void theSavedCustomIsRewrittenInFormatFive() throws Exception {
        preferences.setSurfaceCustomPreset("{\"format_version\":4,"
            + "\"surface_glass_tint\":\"material\",\"surface_base_tint\":80,"
            + "\"surface_base_blur\":4,\"surface_base_grain\":4,\"surface_base_opacity\":30}");

        GlassControlsMigration.run(preferences);

        JSONObject saved = new JSONObject(preferences.getSurfaceCustomPreset());
        assertEquals(SurfacePresets.FORMAT_VERSION, saved.getInt("format_version"));
        assertEquals(TERMUX_APP.GLASS_TINT_SCHEME, saved.getString(TERMUX_APP.KEY_SURFACE_GLASS_TINT));
        assertEquals(80, saved.getInt(TERMUX_APP.KEY_SURFACE_BASE_TINT));
        assertEquals(5, saved.getInt(TERMUX_APP.KEY_SURFACE_BASE_BLUR));
        assertEquals(3, saved.getInt(TERMUX_APP.KEY_SURFACE_BASE_GRAIN));
        assertEquals(TERMUX_APP.DEFAULT_SURFACE_GLASS_TINT_HUE,
            saved.getString(TERMUX_APP.KEY_SURFACE_GLASS_TINT_HUE));
        assertEquals(0, saved.getInt(TERMUX_APP.KEY_SURFACE_GLASS_SHADE));
        assertEquals(100, saved.getInt(TERMUX_APP.KEY_SURFACE_GLASS_VIBRANCY));
    }

    /** An older blob read on its own (a theme import) is carried across the same way. */
    @Test
    public void anOldLookReadAnywhereIsCarriedAcross() {
        java.util.Map<String, Object> look = SurfacePresets.deserialize("{\"format_version\":4,"
            + "\"surface_glass_tint\":\"scheme\",\"surface_base_tint\":55,"
            + "\"surface_base_blur\":25,\"surface_base_grain\":8,\"status_bar_tint_strength\":20}");
        assertEquals(0, look.get(TERMUX_APP.KEY_SURFACE_BASE_TINT));
        assertEquals(0, look.get(TERMUX_APP.KEY_STATUS_BAR_TINT_STRENGTH));
        assertEquals(25, look.get(TERMUX_APP.KEY_SURFACE_BASE_BLUR));
        assertEquals(7, look.get(TERMUX_APP.KEY_SURFACE_BASE_GRAIN));
        java.util.Map<String, Object> current = SurfacePresets.deserialize(
            "{\"format_version\":5,\"surface_base_tint\":55,\"surface_base_grain\":8,"
                + "\"surface_base_blur\":25}");
        assertEquals("a format 5 look is read as it is", 55,
            current.get(TERMUX_APP.KEY_SURFACE_BASE_TINT));
        assertEquals(8, current.get(TERMUX_APP.KEY_SURFACE_BASE_GRAIN));
    }

    @Test
    public void itRunsOnce() {
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 17);
        GlassControlsMigration.run(preferences);
        preferences.setSurfaceBaseValue(SurfaceProperty.TINT, 45);
        preferences.setSurfaceBaseValue(SurfaceProperty.BLUR, 10);
        GlassControlsMigration.run(preferences);
        assertEquals(45, preferences.getSurfaceBaseValue(SurfaceProperty.TINT));
        assertEquals(10, preferences.getSurfaceBaseValue(SurfaceProperty.BLUR));
    }

    /** The new knobs are clamped on the way in and out, and default to today's glass. */
    @Test
    public void theNewKnobsAreClampedAndDefaultToToday() {
        assertEquals(0, preferences.getSurfaceGlassShade());
        assertEquals(100, preferences.getSurfaceGlassVibrancy());
        assertEquals(TERMUX_APP.GLASS_TINT_HUE_PRIMARY, preferences.getSurfaceGlassTintHue());
        preferences.setSurfaceGlassShade(140);
        assertEquals(100, preferences.getSurfaceGlassShade());
        preferences.setSurfaceGlassShade(-5);
        assertEquals(0, preferences.getSurfaceGlassShade());
        preferences.setSurfaceGlassVibrancy(400);
        assertEquals(150, preferences.getSurfaceGlassVibrancy());
        preferences.setSurfaceGlassVibrancy(10);
        assertEquals(70, preferences.getSurfaceGlassVibrancy());
        store.edit().putInt(TERMUX_APP.KEY_SURFACE_GLASS_VIBRANCY, 999)
            .putInt(TERMUX_APP.KEY_SURFACE_GLASS_SHADE, -40).commit();
        assertEquals("clamped on read", 150, preferences.getSurfaceGlassVibrancy());
        assertEquals("clamped on read", 0, preferences.getSurfaceGlassShade());
        preferences.setSurfaceGlassTintHue("tertiary");
        assertEquals(TERMUX_APP.GLASS_TINT_HUE_TERTIARY, preferences.getSurfaceGlassTintHue());
        preferences.setSurfaceGlassTintHue("ultraviolet");
        assertEquals(TERMUX_APP.GLASS_TINT_HUE_PRIMARY, preferences.getSurfaceGlassTintHue());
    }
}
