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
 *   <li>The blur wallpaper mode remembers for its return moves onto the curve beside Base's grain
 *       ({@link GlassControls#blurWithGrain}),
 *       and the Glass slider's positions are written for Base and every detached surface.</li>
 *   <li>The saved Custom look is rewritten in format 5 ({@link SurfacePresets#fromLegacy}).</li>
 * </ul>
 *
 * <p>A store that never wrote a tint key read the old default, the full strength; it is pinned
 * first, so nothing reads the new default of 0 in its place. Every write, the format marker
 * included, lands in one editor ({@link SurfacePresets.Batch}): a process killed half way leaves
 * the store as it was, never a Tint look stripped of its colour with the marker unset.</p>
 */
public final class GlassControlsMigration {

    private GlassControlsMigration() {}

    /** The format this build brings a store to. */
    static final int FORMAT = 1;

    /**
     * The Looks' start-up work, in its order: this migration first, so a Look it recognises at its
     * old values is applied as it ships now, then {@link SurfacePresets#readoptMovedLooks} for a
     * Look whose edge moved. The application calls this, not the two apart.
     */
    public static void runAtStartUp(@NonNull TermuxAppSharedPreferences prefs) {
        run(prefs);
        SurfacePresets.readoptMovedLooks(prefs);
    }

    public static void run(@NonNull TermuxAppSharedPreferences prefs) {
        SharedPreferences store = prefs.getSharedPreferences();
        if (store == null || store.getInt(TERMUX_APP.KEY_SURFACE_GLASS_CONTROLS_FORMAT, 0) >= FORMAT)
            return;
        SurfacePresets.Batch batch = new SurfacePresets.Batch(store);
        TermuxAppSharedPreferences into = SurfacePresets.writingInto(prefs, batch);
        pinLegacyTintStrengths(into, batch);
        SurfacePresets.Preset shipped = SurfacePresets.lookAsShippedBeforeFormat5(into);
        if (shipped != null)
            SurfacePresets.apply(into, shipped);
        else
            migrateLive(into);
        snapRememberedBlur(into, batch);
        writeGlassPositions(into);
        String custom = into.getSurfaceCustomPreset();
        if (!custom.isEmpty()) {
            Map<String, Object> look = SurfacePresets.deserialize(custom);
            if (look != null && !look.isEmpty())
                into.setSurfaceCustomPreset(SurfacePresets.serialize(look));
        }
        batch.edit().putInt(TERMUX_APP.KEY_SURFACE_GLASS_CONTROLS_FORMAT, FORMAT).apply();
        batch.applyToStore();
    }

    /**
     * The blur wallpaper mode puts back when it is turned on again, on the curve: the position
     * nearest that blur with Base's grain, as it will be worn. No blur stays no blur.
     */
    private static void snapRememberedBlur(@NonNull TermuxAppSharedPreferences prefs,
                                           @NonNull SharedPreferences store) {
        if (!store.contains(TERMUX_APP.KEY_WALLPAPER_ENABLED_EXTRAKEYS_BLUR_RADIUS))
            return;
        int remembered = prefs.getWallpaperEnabledExtraKeysBlurRadius();
        int blur = GlassControls.blurWithGrain(remembered,
            prefs.getSurfaceBaseValue(SurfaceProperty.GRAIN));
        if (blur != remembered)
            prefs.setWallpaperEnabledExtraKeysBlurRadius(blur);
    }

    /** The Glass slider's position for Base, and for each surface with its own blur or grain. */
    private static void writeGlassPositions(@NonNull TermuxAppSharedPreferences prefs) {
        int blur = prefs.getSurfaceBaseValue(SurfaceProperty.BLUR);
        int grain = prefs.getSurfaceBaseValue(SurfaceProperty.GRAIN);
        prefs.setSurfaceGlassPosition(null, GlassControls.glassFor(blur, grain));
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            boolean ownBlur = !prefs.isSurfaceInheriting(slot, SurfaceProperty.BLUR);
            boolean ownGrain = !prefs.isSurfaceInheriting(slot, SurfaceProperty.GRAIN);
            if (!ownBlur && !ownGrain)
                continue;
            prefs.setSurfaceGlassPosition(slot, GlassControls.glassFor(
                ownBlur ? prefs.getSurfaceOverrideValue(slot, SurfaceProperty.BLUR) : blur,
                ownGrain ? prefs.getSurfaceOverrideValue(slot, SurfaceProperty.GRAIN) : grain));
        }
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
