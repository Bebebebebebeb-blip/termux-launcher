package com.termux.app.settings;

import androidx.annotation.NonNull;

import com.termux.app.surfaces.SurfacePresets;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

/**
 * The one-tap "Low-memory preset" of Settings → App behavior: the preferences that keep a weak
 * phone smooth, written through the same setters the Appearance editor and the other Settings
 * pages use. Nothing here changes a default; it is an action the user takes.
 */
public final class LowMemoryPreset {

    private static final String SOLID_LOOK_ID = "solid";
    private static final String EFFECT_NONE = "none";
    private static final int TERMINAL_OPACITY_PERCENT = 100;

    private LowMemoryPreset() {}

    public static void apply(@NonNull TermuxAppSharedPreferences prefs) {
        for (SurfacePresets.Preset look : SurfacePresets.presets()) {
            if (SOLID_LOOK_ID.equals(look.id)) SurfacePresets.apply(prefs, look);
        }
        // What the editor's Terminal Opacity slider writes: the terminal's own cell, and the copy
        // wallpaper mode restores from. The other surfaces keep Solid's opacity.
        prefs.detachSurfaceValue(SurfaceSlot.CANVAS, SurfaceProperty.OPACITY, TERMINAL_OPACITY_PERCENT);
        prefs.setTerminalBackgroundOpacity(TERMINAL_OPACITY_PERCENT);
        prefs.setTerminalCursorTrailEnabled(false);
        // Solid's defaults already set the classic glass motion.
        prefs.setTerminalRetroEffect(EFFECT_NONE);
        prefs.setWallpaperParallaxEnabled(false);
        prefs.setLazyModeEnabled(true);
    }
}
