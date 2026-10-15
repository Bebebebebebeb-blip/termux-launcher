package com.termux.app.surfaces;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** An install still on a shipped Look whose edge has since moved takes the Look as it ships now. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SurfaceLooksReadoptTest {

    private SharedPreferences store;
    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences("surface-looks-readopt-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
    }

    private static SurfacePresets.Preset preset(String id) {
        for (SurfacePresets.Preset preset : SurfacePresets.presets())
            if (preset.id.equals(id)) return preset;
        throw new AssertionError(id);
    }

    /** The store's contents without the generation marker. */
    private Map<String, ?> looks() {
        HashMap<String, Object> all = new HashMap<>(store.getAll());
        all.remove(SurfacePresets.KEY_LOOKS_GENERATION);
        return all;
    }

    @Test
    public void anOldClearReadsAsTheNewClear() {
        SurfacePresets.apply(preferences,
            SurfacePresets.withEdges(preset("minimal"), new int[] {6, 14, 40, 0, 0, 70}));
        assertFalse(SurfacePresets.matches(preferences, preset("minimal")));

        SurfacePresets.readoptMovedLooks(preferences);

        assertTrue(SurfacePresets.matches(preferences, preset("minimal")));
        // Clear's bend is a whole band since the lobe rework (was 11).
        assertEquals(26, preferences.getFancierGlassBendDp());
        assertEquals(100, preferences.getFancierGlassClarityPercent());
        assertEquals(SurfacePresets.LOOKS_GENERATION,
            store.getInt(SurfacePresets.KEY_LOOKS_GENERATION, 0));
    }

    @Test
    public void anInstallAlreadyOnGenerationOneTakesTheReworkedEdge() {
        // Generation 1 brought it to 11/28/70/0/25/100; the lobe rework moves it once more.
        store.edit().putInt(SurfacePresets.KEY_LOOKS_GENERATION, 1).commit();
        SurfacePresets.apply(preferences,
            SurfacePresets.withEdges(preset("minimal"), new int[] {11, 28, 70, 0, 25, 100}));

        SurfacePresets.readoptMovedLooks(preferences);

        assertTrue(SurfacePresets.matches(preferences, preset("minimal")));
        assertEquals(30, preferences.getFancierGlassEdgeLightPercent());
        assertEquals(2, store.getInt(SurfacePresets.KEY_LOOKS_GENERATION, 0));
    }

    @Test
    public void everyEarlierEdgeOfEveryMovedLookIsReadopted() {
        int[][][] earlier = {
            {{11, 28, 70, 0, 25, 100}, {6, 14, 40, 0, 0, 70}, {6, 14, 24, 0, 0, 70}, {4, 10, 18, 0, 0, 0}},
            {{13, 32, 60, 0, 20, 90}, {9, 20, 30, 0, 0, 60}, {9, 20, 18, 0, 0, 45}, {9, 20, 18, 20, 0, 0},
                {9, 20, 18, 0, 0, 0}},
            {{10, 26, 60, 0, 0, 85}, {6, 14, 36, 0, 0, 55}, {6, 14, 22, 0, 0, 55}, {4, 10, 18, 0, 0, 0}},
        };
        String[] ids = {"minimal", "frost", "stock"};
        for (int look = 0; look < ids.length; look++) {
            for (int[] edges : earlier[look]) {
                store.edit().clear().commit();
                SurfacePresets.apply(preferences, SurfacePresets.withEdges(preset(ids[look]), edges));
                SurfacePresets.readoptMovedLooks(preferences);
                // Clear and Tint once shared 4/10/18: the rest of the look tells them apart.
                assertTrue(ids[look] + " " + java.util.Arrays.toString(edges),
                    SurfacePresets.matches(preferences, preset(ids[look])));
            }
        }
    }

    @Test
    public void aCustomLookIsUntouched() {
        SurfacePresets.apply(preferences,
            SurfacePresets.withEdges(preset("minimal"), new int[] {7, 14, 40, 0, 0, 70}));
        Map<String, ?> before = looks();

        SurfacePresets.readoptMovedLooks(preferences);

        assertEquals(before, looks());
        assertEquals(7, preferences.getFancierGlassBendDp());
    }

    @Test
    public void untouchedDefaultEdgesOffEveryLookAreUntouched() {
        // Mist's oldest edge is the defaults; without Mist's surface values it is not Mist.
        SurfacePresets.apply(preferences, preset("minimal"));
        for (String key : new String[] {
            com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP.KEY_FANCIER_GLASS_BEND,
            com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP.KEY_FANCIER_GLASS_EDGE_WIDTH,
            com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP.KEY_FANCIER_GLASS_EDGE_LIGHT,
            com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP.KEY_FANCIER_GLASS_DISPERSION,
            com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP.KEY_FANCIER_GLASS_CLARITY,
        })
            store.edit().remove(key).commit();
        Map<String, ?> before = looks();

        SurfacePresets.readoptMovedLooks(preferences);

        assertEquals(before, looks());
    }

    @Test
    public void aStoreAlreadyOnTheNewClearIsUnchanged() {
        SurfacePresets.apply(preferences, preset("minimal"));
        Map<String, ?> before = looks();

        SurfacePresets.readoptMovedLooks(preferences);

        assertEquals(before, looks());
        assertTrue(SurfacePresets.matches(preferences, preset("minimal")));
    }

    @Test
    public void itRunsOncePerGeneration() {
        SurfacePresets.readoptMovedLooks(preferences);
        SurfacePresets.apply(preferences,
            SurfacePresets.withEdges(preset("minimal"), new int[] {6, 14, 40, 0, 0, 70}));
        Map<String, ?> before = looks();

        SurfacePresets.readoptMovedLooks(preferences);

        assertEquals(before, looks());
    }
}
