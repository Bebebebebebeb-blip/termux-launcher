package com.termux.app.surfaces;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.Map;

/**
 * Brings an install's stored glass up to the unified glass controls, once
 * ({@link TERMUX_APP#KEY_SURFACE_GLASS_CONTROLS_FORMAT}). Runs at start-up after the preferences'
 * own migrations and before {@link SurfacePresets#readoptMovedLooks}.
 *
 * <ul>
 *   <li>An install on a shipped Look as it stood before (its old blur and grain, the full tint
 *       strength, Tint's {@code material} family, at any of the Look's edges) takes the Look as it
 *       ships now, so its stop stays that Look. Its colour is exactly what it was: the shipped
 *       Looks render the same tint under the new amount.</li>
 *   <li>Any other look keeps its look: the Tint values become amounts (a {@code material} look's
 *       strengths are its amounts and its family becomes {@code scheme}; any other family's
 *       become 0, its own colour), and every blur and grain pair moves to the nearest Glass
 *       position's ({@link GlassControls#snapped}), Solid's no-blur pair excepted.</li>
 *   <li>The saved Custom look is rewritten in format 5 ({@link SurfacePresets#fromLegacy}).</li>
 * </ul>
 *
 * <p>A store that never wrote a tint key read the old default, the full strength; it is pinned
 * first, so nothing reads the new default of 0 in its place.</p>
 */
public final class GlassControlsMigration {

    private GlassControlsMigration() {}

    /** The format this build brings a store to. */
    static final int FORMAT = 1;

    public static void run(@NonNull TermuxAppSharedPreferences prefs) {
        SharedPreferences store = prefs.getSharedPreferences();
        if (store == null || store.getInt(TERMUX_APP.KEY_SURFACE_GLASS_CONTROLS_FORMAT, 0) >= FORMAT)
            return;
        pinLegacyTintStrengths(prefs, store);
        SurfacePresets.Preset shipped = SurfacePresets.lookAsShippedBeforeFormat5(prefs);
        if (shipped != null)
            SurfacePresets.apply(prefs, shipped);
        else
            migrateLive(prefs);
        String custom = prefs.getSurfaceCustomPreset();
        if (!custom.isEmpty()) {
            Map<String, Object> look = SurfacePresets.deserialize(custom);
            if (look != null && !look.isEmpty())
                prefs.setSurfaceCustomPreset(SurfacePresets.serialize(look));
        }
        store.edit().putInt(TERMUX_APP.KEY_SURFACE_GLASS_CONTROLS_FORMAT, FORMAT).apply();
    }

    /** Writes the old default strength into every tint key the store never set. */
    private static void pinLegacyTintStrengths(@NonNull TermuxAppSharedPreferences prefs,
                                               @NonNull SharedPreferences store) {
        if (!store.contains(TERMUX_APP.KEY_SURFACE_BASE_TINT))
            prefs.setSurfaceBaseValue(SurfaceProperty.TINT, SurfacePresets.LEGACY_TINT_STRENGTH);
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            String key = TermuxAppSharedPreferences.surfaceOverrideKey(slot, SurfaceProperty.TINT);
            if (key != null && !store.contains(key))
                prefs.setSurfaceRawValue(slot, SurfaceProperty.TINT,
                    SurfacePresets.LEGACY_TINT_STRENGTH);
        }
    }

    /** The live look, not a shipped one, carried across with its look kept. */
    static void migrateLive(@NonNull TermuxAppSharedPreferences prefs) {
        boolean material = TERMUX_APP.GLASS_TINT_MATERIAL.equals(prefs.getSurfaceGlassTint());
        if (material) {
            // The Material blend at strength s is the scheme ink at amount s: the numbers stay.
            prefs.setSurfaceGlassTint(TERMUX_APP.GLASS_TINT_SCHEME);
        } else {
            prefs.setSurfaceBaseValue(SurfaceProperty.TINT, 0);
            for (SurfaceSlot slot : SurfaceSlot.values()) {
                if (TermuxAppSharedPreferences.surfaceOverrideKey(slot, SurfaceProperty.TINT) != null)
                    prefs.setSurfaceRawValue(slot, SurfaceProperty.TINT, 0);
            }
        }
        int baseBlur = prefs.getSurfaceBaseValue(SurfaceProperty.BLUR);
        int baseGrain = prefs.getSurfaceBaseValue(SurfaceProperty.GRAIN);
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            if (TermuxAppSharedPreferences.surfaceOverrideKey(slot, SurfaceProperty.BLUR) == null
                || TermuxAppSharedPreferences.surfaceOverrideKey(slot, SurfaceProperty.GRAIN) == null)
                continue;
            boolean ownBlur = !prefs.isSurfaceInheriting(slot, SurfaceProperty.BLUR);
            boolean ownGrain = !prefs.isSurfaceInheriting(slot, SurfaceProperty.GRAIN);
            if (!ownBlur && !ownGrain)
                continue;
            int[] pair = GlassControls.snapped(
                ownBlur ? prefs.getSurfaceOverrideValue(slot, SurfaceProperty.BLUR) : baseBlur,
                ownGrain ? prefs.getSurfaceOverrideValue(slot, SurfaceProperty.GRAIN) : baseGrain);
            prefs.detachSurfaceValue(slot, SurfaceProperty.BLUR, pair[0]);
            prefs.detachSurfaceValue(slot, SurfaceProperty.GRAIN, pair[1]);
        }
        int[] base = GlassControls.snapped(baseBlur, baseGrain);
        prefs.setSurfaceBaseValue(SurfaceProperty.BLUR, base[0]);
        prefs.setSurfaceBaseValue(SurfaceProperty.GRAIN, base[1]);
    }
}
