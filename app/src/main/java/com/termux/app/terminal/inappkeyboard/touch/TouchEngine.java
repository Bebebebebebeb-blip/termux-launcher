package com.termux.app.terminal.inappkeyboard.touch;

import juloo.keyboard2.TapGeometry;

/**
 * The keyboard's touch engine: decides when learned borders correct a tap, and turns the stream
 * of released taps into what the {@link TapModel} learns. Pure; no Android types, and every time
 * and size arrives as a plain number, so all of it runs on the JVM.
 *
 * <p>What it learns from:
 * <ul>
 *   <li>A landed tap, at weight 1, filed against the key the plain grid gave it (never the key a
 *   correction sent), unless it lands more than {@link #OUTLIER_DISTANCE} from that key's
 *   centre.</li>
 *   <li>A take-back — key X, exactly one Backspace, then key Y, Y a grid neighbour of X, all
 *   within {@link #TAKE_BACK_WINDOW_MS} with nothing in between — filed as a tap meant for Y at
 *   X's landing point, at weight {@link #TAKE_BACK_WEIGHT}.</li>
 * </ul>
 * Each tap is held for one keystroke before it is filed, so a take-back never has to undo a
 * filing: the next keystroke files it, or a Backspace holds it one more. A tap erased by a
 * Backspace that does not complete a take-back is dropped. Esc and Tab learn from landed taps
 * only: a Backspace does not undo either, so it cannot take them back and files them instead.
 *
 * <p>Ignored, and each one ends any take-back in progress: swipes, long presses, key repeats,
 * presses with Ctrl or Alt held, presses on keys that are never corrected, and input from
 * anywhere else ({@link #interrupt()}). Every key also accepts only
 * {@link GridMemory#MAX_OBSERVATIONS_PER_KEY_PER_MINUTE} observations a minute.
 *
 * <p>Correction runs only while the {@link LearningGate} is active: after warm-up, until the
 * drift alarm pauses it.
 */
public final class TouchEngine {

    /** The longest a take-back may take, from the missed tap to the retyped one. */
    public static final long TAKE_BACK_WINDOW_MS = 2_000L;
    /** How many landed taps one take-back counts as. */
    public static final float TAKE_BACK_WEIGHT = 3f;
    /** A landed tap further than this from its key's centre, in key widths, is not learned. */
    public static final float OUTLIER_DISTANCE = 0.5f;

    private static final class Pending {
        final GridMemory grid;
        final TapGeometry geometry;
        final Tap tap;
        boolean sawBackspace;

        Pending(GridMemory grid, TapGeometry geometry, Tap tap) {
            this.grid = grid;
            this.geometry = geometry;
            this.tap = tap;
        }
    }

    private final LearningGate mGate;
    private long mTapsLearned;
    private boolean mSwitchedOn;
    /** Numbers the current warm-up, so every grid's {@link PassBarCounts} can tell its own. */
    private int mEpoch;
    private Pending mPending;
    private boolean mHasLastDown;
    private long mLastDownAt;

    public TouchEngine() {
        this(new LearningGate(), 0L, false, 0);
    }

    public TouchEngine(LearningGate gate, long tapsLearned, boolean switchedOn, int epoch) {
        mGate = gate == null ? new LearningGate() : gate;
        mTapsLearned = Math.max(0L, tapsLearned);
        mSwitchedOn = switchedOn;
        mEpoch = epoch;
    }

    /** The current warm-up's number; it changes whenever a warm-up starts. */
    public int epoch() {
        return mEpoch;
    }

    public LearningGate gate() {
        return mGate;
    }

    /** Observations filed into any model since the last reset. */
    public long tapsLearned() {
        return mTapsLearned;
    }

    /** Whether the feature was on when last seen; turning it on starts a warm-up. */
    public boolean switchedOn() {
        return mSwitchedOn;
    }

    /**
     * Follows the feature's switch. Turning it on starts a fresh warm-up. Returns whether the
     * state changed and needs saving.
     */
    public boolean onSwitch(boolean on) {
        if (on == mSwitchedOn)
            return false;
        mSwitchedOn = on;
        if (on)
            restartWarmUp();
        else
            interrupt();
        return true;
    }

    /** Leaves a pause: a fresh warm-up, keeping what was learned. */
    public void resume() {
        restartWarmUp();
    }

    private void restartWarmUp() {
        mGate.restartWarmUp();
        mEpoch++;
    }

    /**
     * The key a plain tap released on {@code rawIndex} should commit: {@code rawIndex} unless
     * correction is active and the model moves it.
     */
    public int resolve(GridMemory grid, TapGeometry geometry, int rawIndex, float x, float y) {
        if (grid == null || geometry == null || !mGate.isCorrecting()
            || grid.keyCount() != geometry.keyCount)
            return rawIndex;
        return grid.model().resolve(geometry, rawIndex, x, y);
    }

    /** A press on {@code geometry} was released. */
    public void onRelease(GridMemory grid, TapGeometry geometry, Tap tap) {
        if (tap == null || grid == null || geometry == null
            || grid.keyCount() != geometry.keyCount
            || !inRange(geometry, tap.rawIndex) || !inRange(geometry, tap.committedIndex)) {
            interrupt();
            return;
        }
        long gap = mHasLastDown ? tap.downAtMs - mLastDownAt : -1L;
        mHasLastDown = true;
        mLastDownAt = tap.downAtMs;

        boolean clean = tap.plain && !tap.modified;
        if (geometry.role[tap.committedIndex] == TapGeometry.ROLE_BACKSPACE) {
            onBackspace(clean, tap);
            return;
        }
        if (!clean || !geometry.correctable[tap.rawIndex]
            || !geometry.correctable[tap.committedIndex]) {
            interrupt();
            return;
        }

        boolean takenBack = false;
        Pending previous = mPending;
        mPending = null;
        if (previous != null) {
            if (!previous.sawBackspace) {
                fileLanded(previous);
            } else if (isTakeBack(previous, grid, geometry, tap)) {
                fileTakeBack(previous, tap.committedIndex, tap.upAtMs);
                takenBack = true;
            }
            // Otherwise the Backspace erased it and it taught nothing.
        }
        PassBarCounts counts = grid.counts();
        counts.sync(mEpoch);
        int phase = mGate.state();
        if (takenBack) {
            counts.countTakeBack(tap.committedIndex, phase);
            mGate.onTakeBack();
        }
        counts.countTap(tap.committedIndex, phase);
        mGate.onTap();
        grid.log().addSample(tap.rawIndex,
            tap.x - geometry.centerX(tap.rawIndex), tap.y - geometry.centerY(tap.rawIndex),
            tap.upAtMs - tap.downAtMs, tap.contactSize, gap);
        mPending = new Pending(grid, geometry, tap);
    }

    /**
     * Input that did not come from a plain tap on these keys: a paste, a hardware key, the
     * keyboard going away. Files the held tap unless a Backspace erased it, and ends any
     * take-back in progress.
     */
    public void interrupt() {
        Pending previous = mPending;
        mPending = null;
        if (previous != null && !previous.sawBackspace)
            fileLanded(previous);
    }

    private void onBackspace(boolean clean, Tap tap) {
        Pending previous = mPending;
        if (previous == null)
            return;
        if (!takesBack(previous.geometry, previous.tap.committedIndex)) {
            // Esc and Tab are not undone by a Backspace; they landed.
            mPending = null;
            fileLanded(previous);
            return;
        }
        if (clean && !previous.sawBackspace
            && tap.upAtMs - previous.tap.upAtMs <= TAKE_BACK_WINDOW_MS) {
            previous.sawBackspace = true;
            return;
        }
        // A second Backspace, a held or modified one, or a late one: the tap is erased.
        mPending = null;
    }

    private static boolean isTakeBack(Pending previous, GridMemory grid, TapGeometry geometry,
                                      Tap tap) {
        int x = previous.tap.committedIndex;
        int y = tap.committedIndex;
        return previous.grid == grid
            && tap.upAtMs - previous.tap.upAtMs <= TAKE_BACK_WINDOW_MS
            && takesBack(geometry, x) && takesBack(geometry, y)
            && KeyGroups.adjacent(geometry, x, y);
    }

    private void fileLanded(Pending pending) {
        TapGeometry geometry = pending.geometry;
        int raw = pending.tap.rawIndex;
        if (!geometry.correctable[raw])
            return;
        float dx = KeyGroups.isWide(geometry, raw) ? 0f : pending.tap.x - geometry.centerX(raw);
        float dy = pending.tap.y - geometry.centerY(raw);
        if (dx * dx + dy * dy > OUTLIER_DISTANCE * OUTLIER_DISTANCE)
            return;
        if (!pending.grid.admit(raw, pending.tap.upAtMs))
            return;
        if (pending.grid.model().observe(geometry, raw, pending.tap.x, pending.tap.y, 1f))
            mTapsLearned++;
    }

    private void fileTakeBack(Pending pending, int meant, long nowMs) {
        TapGeometry geometry = pending.geometry;
        pending.grid.log().addTakeBack(meant, pending.tap.committedIndex,
            pending.tap.x - geometry.centerX(meant), pending.tap.y - geometry.centerY(meant));
        if (!pending.grid.admit(meant, nowMs))
            return;
        if (pending.grid.model().observe(geometry, meant, pending.tap.x, pending.tap.y,
            TAKE_BACK_WEIGHT))
            mTapsLearned++;
    }

    /** Characters and space can be taken back; Esc and Tab cannot. */
    private static boolean takesBack(TapGeometry geometry, int i) {
        int role = geometry.role[i];
        return role == TapGeometry.ROLE_CHAR || role == TapGeometry.ROLE_SPACE;
    }

    private static boolean inRange(TapGeometry geometry, int i) {
        return i >= 0 && i < geometry.keyCount;
    }
}
