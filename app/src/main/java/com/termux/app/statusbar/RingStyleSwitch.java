package com.termux.app.statusbar;

import java.lang.reflect.Method;

/**
 * TEMP ring-style A/B: which indeterminate ring the window chips draw, read from the system property
 * {@code debug.termux.ring_style} ({@code smooth}, {@code chaser} or {@code heartbeat}), so the
 * styles can be compared on the device with {@code adb shell setprop} and no restart.
 *
 * <p>Read through {@code android.os.SystemProperties} by reflection: it is hidden API, which a
 * targetSdk 28 app may still call. The property is re-read at most once a second, so a draw or a
 * tick costs a clock comparison, and a change lands within about a second. Anything that fails —
 * no such class, as on the JVM, or no such property — is the smooth arc.
 *
 * <p>Main thread only: the bar's tick and the chips' draws both run there.
 */
public final class RingStyleSwitch {

    private static final String PROPERTY = "debug.termux.ring_style";
    /** How stale the cached style may get before the property is read again. */
    private static final long REREAD_MS = 1000L;

    /** Reused, so a re-read allocates nothing but the string the property comes back as. */
    private static final Object[] GET_ARGS = {PROPERTY, ""};

    private static WindowActivityRing.Style sStyle = WindowActivityRing.Style.SMOOTH;
    private static boolean sRead;
    private static long sReadAtMs;
    private static Method sGet;
    private static boolean sGetMissing;

    private RingStyleSwitch() {}

    /** The style to draw at {@code nowMs}, which is {@code SystemClock.uptimeMillis()}. */
    public static WindowActivityRing.Style current(long nowMs) {
        if (sRead && nowMs - sReadAtMs < REREAD_MS && nowMs >= sReadAtMs) return sStyle;
        sRead = true;
        sReadAtMs = nowMs;
        sStyle = WindowActivityRing.Style.parse(readProperty());
        return sStyle;
    }

    private static String readProperty() {
        if (sGetMissing) return null;
        try {
            if (sGet == null) {
                sGet = Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class, String.class);
            }
            Object value = sGet.invoke(null, GET_ARGS);
            return value instanceof String ? (String) value : null;
        } catch (Throwable error) {
            // Not there at all: stop asking, and draw the smooth arc for the life of the process.
            sGetMissing = true;
            return null;
        }
    }
}
