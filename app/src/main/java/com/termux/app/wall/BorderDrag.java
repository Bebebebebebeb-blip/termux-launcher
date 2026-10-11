package com.termux.app.wall;

import androidx.annotation.NonNull;

/**
 * A press held on a pane's border, then dragged sideways, which pages the wall. The one gesture
 * every place has for moving to the places beside it, in every mode.
 *
 * <p>The border lives outside the page. Its zone ({@link Zone}) is the strip between each edge of
 * the page — the terminal, the Widgets page, the Display page — and the wall's own edge on that
 * side: the frame line drawn there and the air beyond it, up to whatever stands next to the wall
 * (the status bar, the dock or the keyboard, the screen's edge). Which border a press is on is
 * which page edge it lies beyond. The page itself is never the zone, so a tap, a long press or a
 * mouse click on the terminal's first or last row or column is always the terminal's. The one
 * exception is a page that sits almost flush with the wall: where the air on a side is thinner
 * than {@link #MIN_BAND_DP}, that side's zone reaches into the page by the shortfall, so the
 * gestures never vanish at Margin 0. The corner squares are left out, from the page's corners out
 * through the zone: they are the corner tabs'.
 *
 * <p>A finger that lands in the zone claims nothing at once: one that sets off sideways before the
 * hold, or is joined by a second finger, gives the gesture up, and where the zone reaches into the
 * page that leaves it with the content, as a scroll, a selection or a pinch. Only a finger that
 * holds still through the hold claims the wall — from there to the lift its sideways travel drags
 * the pages, whichever edge it started on, and its release speed decides where the wall lands.
 *
 * <p>The bottom border carries one more gesture, the keyboard's: a clear vertical swipe that
 * starts in its zone — past the slop, more up or down than sideways, before the hold — is claimed
 * at once ({@link Claim#KEYBOARD}), and its release opens the keyboard on the way up and closes it
 * on the way down ({@link #keyboardRelease}). It is the one way to the keyboard that every place
 * and every mode shares.
 *
 * <p>The top border carries its mirror, the status bar's: the same clear vertical swipe from the
 * top zone, before the hold, is claimed as {@link Claim#STATUS}, and drives the bar's fold — down
 * unfolds it, up folds it ({@link #statusRelease}). Never above {@code statusTopLimit}: the
 * phone's own pull for its notification shade starts in the strip at the top of the screen, and
 * a press there is never taken for the bar.
 *
 * <p>The hold is timed by whoever owns the finger; this class only says whether a hold that has
 * elapsed may claim ({@link #holdElapsed}). Pure, so the zone, the hold and the drag can be
 * tested without a view; one instance is reused across gestures, so a swipe allocates nothing.</p>
 */
public final class BorderDrag {

    /**
     * The thinnest a border's zone ever gets, in dp. Where the air between a page edge and the
     * wall's edge is thinner than this (Margin set near 0), the zone reaches into the page by the
     * shortfall, so the border can still be found; at the default Margin nothing reaches in.
     */
    public static final float MIN_BAND_DP = 6f;

    /** How far a keyboard swipe has to travel to open or close the keyboard, in dp. */
    public static final float KEYBOARD_COMMIT_DP = 32f;

    /** How fast a shorter keyboard swipe has to be flicked to count, in dp per second. */
    public static final float KEYBOARD_FLING_DP_PER_SEC = 500f;

    /** Which border a finger is on. */
    public enum Border { NONE, TOP, BOTTOM, LEFT, RIGHT }

    public enum Claim {
        /** No finger, or one that landed off the border. */
        NONE,
        /** Down on a border; waiting to see whether it holds still through the hold. */
        PENDING,
        /** The hold elapsed on a still finger: the wall's, from here to the lift. */
        PAGING,
        /**
         * A vertical swipe that set off from the bottom border before the hold: the keyboard's,
         * from here to the lift, where {@link #keyboardRelease} says which way it went.
         */
        KEYBOARD,
        /**
         * A vertical swipe that set off from the top border before the hold: the status bar's,
         * from here to the lift, where {@link #statusRelease} says which way it went.
         */
        STATUS,
        /** Moved, joined by a second finger, or taken from under: the content keeps this gesture. */
        ABANDONED
    }

    /** What a keyboard swipe's release asks for. */
    public enum KeyboardSwipe { NONE, OPEN, CLOSE }

    /** What a status bar swipe's release asks for: the bar unfolded, or folded. */
    public enum StatusSwipe { NONE, EXPAND, COLLAPSE }

    /**
     * Where the borders are: the page's edges, the air outside each of them, the minimum band and
     * the corner squares. Mutable so the owner can keep one and refill it on every press.
     */
    public static final class Zone {
        float mLeft;
        float mTop;
        float mRight;
        float mBottom;
        float mGapLeft;
        float mGapTop;
        float mGapRight;
        float mGapBottom;
        float mMinBand;
        float mCorner;

        /** The page's edges, as it stands now (its slide applied). */
        @NonNull
        public Zone page(float left, float top, float right, float bottom) {
            mLeft = left;
            mTop = top;
            mRight = right;
            mBottom = bottom;
            return this;
        }

        /**
         * The air outside each edge, from the page's edge out to the wall's edge on that side: how
         * far out each border's zone runs, and, below {@link #minBand}, how much it reaches in.
         */
        @NonNull
        public Zone gaps(float left, float top, float right, float bottom) {
            mGapLeft = Math.max(0f, left);
            mGapTop = Math.max(0f, top);
            mGapRight = Math.max(0f, right);
            mGapBottom = Math.max(0f, bottom);
            return this;
        }

        /** The thinnest a zone gets, in px ({@link #MIN_BAND_DP}). */
        @NonNull
        public Zone minBand(float px) {
            mMinBand = Math.max(0f, px);
            return this;
        }

        /** The corner squares' side, in px, measured in from the page's corners. */
        @NonNull
        public Zone corner(float px) {
            mCorner = Math.max(0f, px);
            return this;
        }

        /**
         * How far inside the page's edge on {@code side} its zone reaches: the shortfall of that
         * side's air against the minimum band, 0 wherever the air is at least that thick, and
         * never past the middle of the page.
         */
        public float inside(@NonNull Border side) {
            float gap;
            float half;
            switch (side) {
                case LEFT: gap = mGapLeft; half = (mRight - mLeft) / 2f; break;
                case RIGHT: gap = mGapRight; half = (mRight - mLeft) / 2f; break;
                case TOP: gap = mGapTop; half = (mBottom - mTop) / 2f; break;
                case BOTTOM: gap = mGapBottom; half = (mBottom - mTop) / 2f; break;
                default: return 0f;
            }
            return Math.max(0f, Math.min(mMinBand - gap, half));
        }

        /**
         * The zone along {@code side} as {@code left, top, right, bottom} in {@code out}, less the
         * corner squares: from the wall's edge to the page's, and in by {@link #inside}. False,
         * with {@code out} untouched, where that side has no zone.
         */
        public boolean bounds(@NonNull Border side, @NonNull float[] out) {
            if (mRight <= mLeft || mBottom <= mTop) return false;
            float corner = cornerSize();
            float in = inside(side);
            float l;
            float t;
            float r;
            float b;
            switch (side) {
                case LEFT:
                    l = mLeft - mGapLeft; r = mLeft + in; t = mTop + corner; b = mBottom - corner;
                    break;
                case RIGHT:
                    l = mRight - in; r = mRight + mGapRight; t = mTop + corner; b = mBottom - corner;
                    break;
                case TOP:
                    l = mLeft + corner; r = mRight - corner; t = mTop - mGapTop; b = mTop + in;
                    break;
                case BOTTOM:
                    l = mLeft + corner; r = mRight - corner; t = mBottom - in;
                    b = mBottom + mGapBottom;
                    break;
                default:
                    return false;
            }
            if (r <= l || b <= t) return false;
            out[0] = l;
            out[1] = t;
            out[2] = r;
            out[3] = b;
            return true;
        }

        /** The corner squares' side, never more than half of the page either way. */
        private float cornerSize() {
            return Math.min(mCorner, Math.min(mRight - mLeft, mBottom - mTop) / 2f);
        }
    }

    @NonNull private Claim mClaim = Claim.NONE;
    @NonNull private Border mBorder = Border.NONE;
    private float mDownX;
    private float mDownY;
    private float mSlop;
    /** Whether the hold may page: the wall has another place to go. */
    private boolean mHoldPages;
    /** Whether this press may become the keyboard swipe: it landed in the bottom zone. */
    private boolean mKeyboardEligible;
    /** Whether this press may become the status bar's swipe: it landed in the top zone. */
    private boolean mStatusEligible;

    /**
     * Which border the point {@code (x, y)} presses in {@code zone}: the page edge it lies beyond,
     * within that side's air (or, where the air is thinner than the minimum band, within the
     * shortfall inside the edge). {@link Border#NONE} for the page itself, anything past the air,
     * and the corner squares, which run from the page's corners out through the zone.
     */
    @NonNull
    public static Border borderAt(float x, float y, @NonNull Zone zone) {
        float left = zone.mLeft;
        float top = zone.mTop;
        float right = zone.mRight;
        float bottom = zone.mBottom;
        if (right <= left || bottom <= top) return Border.NONE;
        if (x < left - zone.mGapLeft || x > right + zone.mGapRight
            || y < top - zone.mGapTop || y > bottom + zone.mGapBottom) {
            return Border.NONE;
        }
        float corner = zone.cornerSize();
        boolean cornerColumn = x < left + corner || x >= right - corner;
        boolean cornerRow = y < top + corner || y >= bottom - corner;
        if (cornerColumn && cornerRow) return Border.NONE;
        // A page's first pixel is at its left and top edges and its last just short of its right
        // and bottom ones, so a press is beyond the right or bottom edge from the edge itself on.
        if (y < top + zone.inside(Border.TOP)) return Border.TOP;
        if (y >= bottom - zone.inside(Border.BOTTOM)) return Border.BOTTOM;
        if (x < left + zone.inside(Border.LEFT)) return Border.LEFT;
        if (x >= right - zone.inside(Border.RIGHT)) return Border.RIGHT;
        return Border.NONE;
    }

    /**
     * The finger landed at {@code (x, y)}. Arms the drag when the point presses a border of
     * {@code zone} (see {@link #borderAt}) that the press can do something on; otherwise the
     * gesture is not this one and every later call is a no-op.
     *
     * @param canPage whether the hold may page the wall: it has another place to go
     * @param keyboardSwipe whether a vertical swipe from the bottom border is the keyboard's
     * @param statusSwipe whether a vertical swipe from the top border folds the status bar
     * @param statusTopLimit the highest point, in the same coordinates, the status bar's swipe
     *                       may start at: the foot of the phone's own gesture strip at the top of
     *                       the screen ({@link Float#NEGATIVE_INFINITY} for none)
     * @return whether the press is armed
     */
    public boolean down(float x, float y, @NonNull Zone zone, float slopPx, boolean canPage,
                        boolean keyboardSwipe, boolean statusSwipe, float statusTopLimit) {
        mDownX = x;
        mDownY = y;
        mSlop = Math.max(0f, slopPx);
        mBorder = borderAt(x, y, zone);
        mHoldPages = canPage && mBorder != Border.NONE;
        mKeyboardEligible = keyboardSwipe && mBorder == Border.BOTTOM;
        mStatusEligible = statusSwipe && mBorder == Border.TOP && !(y < statusTopLimit);
        boolean armed = mHoldPages || mKeyboardEligible || mStatusEligible;
        if (!armed) mBorder = Border.NONE;
        mClaim = armed ? Claim.PENDING : Claim.NONE;
        return armed;
    }

    /**
     * One move of the finger. Before the hold, travel past the slop gives the gesture away for
     * good: a finger that sets off is not holding. Sideways, or from anywhere but the bottom and
     * top zones, it is given up; up or down from the bottom zone it is the keyboard's, and
     * from the top zone the status bar's. After a claim the move is the claimant's, and the claim
     * never changes.
     */
    @NonNull
    public Claim move(float x, float y) {
        if (mClaim != Claim.PENDING) return mClaim;
        float dx = x - mDownX;
        float dy = y - mDownY;
        if (dx * dx + dy * dy <= mSlop * mSlop) return mClaim;
        boolean vertical = Math.abs(dy) > Math.abs(dx);
        mClaim = vertical && mKeyboardEligible ? Claim.KEYBOARD
            : vertical && mStatusEligible ? Claim.STATUS : Claim.ABANDONED;
        return mClaim;
    }

    /**
     * The hold time passed. A finger that travelled or was joined has already given the gesture
     * up through {@link #move} or {@link #secondPointer}, so a still pending press is a still one.
     * Where the hold may not page — a wall of one place — a still finger is given up, and where
     * the zone reaches into the page it is the content's long press. A later vertical move can
     * then no longer turn into the keyboard's or the status bar's swipe.
     *
     * @return true when the wall just claimed the finger, and the content is owed a cancel
     */
    public boolean holdElapsed() {
        if (mClaim != Claim.PENDING) return false;
        if (!mHoldPages) {
            mClaim = Claim.ABANDONED;
            return false;
        }
        mClaim = Claim.PAGING;
        return true;
    }

    /**
     * A second finger landed. Before the claim it settles the question — two fingers are the
     * content's pinch or scroll, never a page — and after it changes nothing: the wall is already
     * moving under the first finger and its lift still ends the drag.
     */
    @NonNull
    public Claim secondPointer() {
        if (mClaim == Claim.PENDING) mClaim = Claim.ABANDONED;
        return mClaim;
    }

    /**
     * Something else took the wall from under this finger (the wall was moved by a tile, a key,
     * {@code wall.go}, or the gestures were switched off): the rest of the gesture is swallowed
     * and moves nothing.
     */
    @NonNull
    public Claim abandon() {
        if (mClaim == Claim.PENDING || mClaim == Claim.PAGING || mClaim == Claim.KEYBOARD
            || mClaim == Claim.STATUS) {
            mClaim = Claim.ABANDONED;
        }
        return mClaim;
    }

    /** The finger lifted or the stream was cancelled: back to nothing. */
    public void reset() {
        mClaim = Claim.NONE;
        mBorder = Border.NONE;
        mKeyboardEligible = false;
        mStatusEligible = false;
    }

    @NonNull
    public Claim claim() {
        return mClaim;
    }

    /** The border the finger landed on, from the DOWN to the lift; NONE off a border. */
    @NonNull
    public Border border() {
        return mBorder;
    }

    /** True from the DOWN on a border until the lift, whichever way the claim went. */
    public boolean isArmed() {
        return mClaim != Claim.NONE;
    }

    /** True while the wall owns the finger. */
    public boolean isPaging() {
        return mClaim == Claim.PAGING;
    }

    /** True while the keyboard swipe owns the finger. */
    public boolean isKeyboardSwipe() {
        return mClaim == Claim.KEYBOARD;
    }

    /** True while the status bar's swipe owns the finger. */
    public boolean isStatusSwipe() {
        return mClaim == Claim.STATUS;
    }

    /**
     * True from a DOWN in the status bar swipe's zone — the top border, below the phone's own
     * strip — until the lift, whichever way the claim went.
     */
    public boolean isStatusEligible() {
        return mStatusEligible;
    }

    /** The finger's vertical travel since the DOWN, positive downward. */
    public float verticalTravel(float y) {
        return y - mDownY;
    }

    /**
     * True from a DOWN in the keyboard swipe's zone — the bottom border — until the lift,
     * whichever way the claim went.
     */
    public boolean isKeyboardEligible() {
        return mKeyboardEligible;
    }

    /**
     * What the wall is dragged by for a finger now at {@code x}: its sideways travel since the
     * DOWN, positive to the right, on every border alike. The wall only ever moves sideways, so a
     * drag along a side border still pages by how far it went across, and a finger that pulls
     * the right border leftward brings the place on the right in exactly as the top border does.
     */
    public float travel(float x) {
        return x - mDownX;
    }

    /**
     * What a keyboard swipe released at {@code y}, moving at {@code velocityYPxPerSec} (positive
     * downward), asks for: {@link KeyboardSwipe#OPEN} for one that went up, {@link
     * KeyboardSwipe#CLOSE} for one that went down, each once it has travelled {@code commitPx} or
     * is flicked on the same way at {@code flingPxPerSec}. A release short of both, or flicked
     * back against its own travel, asks for nothing; so does any gesture that is not a keyboard
     * swipe.
     */
    @NonNull
    public KeyboardSwipe keyboardRelease(float y, float velocityYPxPerSec, float commitPx,
                                         float flingPxPerSec) {
        if (mClaim != Claim.KEYBOARD) return KeyboardSwipe.NONE;
        int way = verticalRelease(y, velocityYPxPerSec, commitPx, flingPxPerSec);
        return way < 0 ? KeyboardSwipe.OPEN : way > 0 ? KeyboardSwipe.CLOSE : KeyboardSwipe.NONE;
    }

    /**
     * What a status bar swipe released at {@code y} asks for, by the keyboard's own rule
     * ({@link #keyboardRelease}) mirrored: {@link StatusSwipe#EXPAND} for one that went down, away
     * from the bar, and {@link StatusSwipe#COLLAPSE} for one that went up, into it.
     */
    @NonNull
    public StatusSwipe statusRelease(float y, float velocityYPxPerSec, float commitPx,
                                     float flingPxPerSec) {
        if (mClaim != Claim.STATUS) return StatusSwipe.NONE;
        int way = verticalRelease(y, velocityYPxPerSec, commitPx, flingPxPerSec);
        return way > 0 ? StatusSwipe.EXPAND : way < 0 ? StatusSwipe.COLLAPSE : StatusSwipe.NONE;
    }

    /**
     * The way a vertical swipe released at {@code y} went: -1 up, +1 down, each once it has
     * travelled {@code commitPx} or is flicked on the same way at {@code flingPxPerSec}; 0 for a
     * release short of both or flicked back against its own travel.
     */
    private int verticalRelease(float y, float velocityYPxPerSec, float commitPx,
                                float flingPxPerSec) {
        float dy = y - mDownY;
        if (dy == 0f) return 0;
        boolean up = dy < 0f;
        // The release speed along the swipe's own travel; negative is a flick back.
        float along = up ? -velocityYPxPerSec : velocityYPxPerSec;
        float fling = Math.max(0f, flingPxPerSec);
        if (along <= -fling && fling > 0f) return 0;
        boolean far = Math.abs(dy) >= commitPx;
        boolean flung = fling > 0f && along >= fling;
        if (!far && !flung) return 0;
        return up ? -1 : 1;
    }
}
