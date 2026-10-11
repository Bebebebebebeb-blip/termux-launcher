package com.termux.app.statusbar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.terminal.TerminalWindowBar;

/**
 * The status bar's row: the place's badge and window chips, then the readings. Along a row it
 * runs the chips up to the readings, one {@link StatusRowFitPolicy#CHIPS_READINGS_GAP_DP} short
 * of the first reading's ink, however much of that gap the readings already carry themselves.
 * Standing in a column it is a plain linear layout.
 */
public final class StatusRowLayout extends LinearLayout {

    public StatusRowLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (getOrientation() != HORIZONTAL) return;
        View strip = findViewById(R.id.terminal_status_place_content);
        TerminalWindowBar bar = findViewById(R.id.terminal_window_bar);
        if (strip == null || bar == null || bar.getVisibility() != VISIBLE) return;
        int gap = Math.round(StatusRowFitPolicy.CHIPS_READINGS_GAP_DP
            * getResources().getDisplayMetrics().density);
        int end = StatusRowFitPolicy.chipsEndPaddingPx(gap, readingsLeadPx(strip, bar));
        // Only on a change, and then measured once more with it: the padding is inside the bar,
        // so the row's own split does not move, only where the chips stop.
        if (bar.setTrailingGapPx(end)) super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * The space from the end of the chips' viewport to the first reading's ink: whatever the strip
     * keeps after the bar, then every inkless thing before the first reading. Negative when the
     * chips are followed by no reading at all — only by the row's end, or by the centring spacer
     * the Widgets place stands the stats on.
     */
    int readingsLeadPx(@NonNull View strip, @NonNull View bar) {
        int lead = strip.getPaddingEnd() + marginEnd(bar);
        int index = indexOfChild(strip);
        if (index < 0) return -1;
        for (int i = index + 1; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int ink = inkLead(child);
            if (ink >= 0) return lead + ink;
            if (!(child instanceof ViewGroup)) return -1;
            lead += footprint(child);
        }
        return -1;
    }

    /** How far into {@code view}, margin included, its first ink starts; negative if it has none. */
    private static int inkLead(@NonNull View view) {
        if (view.getVisibility() == GONE) return -1;
        if (view instanceof StatusBarWidgetView) return marginStart(view) + view.getPaddingStart();
        if (view instanceof MaterialDotSeparatorView) {
            return view.getMeasuredWidth() > 0 ? marginStart(view) : -1;
        }
        if (!(view instanceof ViewGroup)) return -1;
        ViewGroup group = (ViewGroup) view;
        int lead = marginStart(view) + view.getPaddingStart();
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int ink = inkLead(child);
            if (ink >= 0) return lead + ink;
            lead += footprint(child);
        }
        return -1;
    }

    /** The room {@code view} takes along the row, margins included. */
    private static int footprint(@NonNull View view) {
        return marginStart(view) + view.getMeasuredWidth() + marginEnd(view);
    }

    private static int marginStart(@NonNull View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        return params instanceof MarginLayoutParams
            ? ((MarginLayoutParams) params).getMarginStart() : 0;
    }

    private static int marginEnd(@NonNull View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        return params instanceof MarginLayoutParams
            ? ((MarginLayoutParams) params).getMarginEnd() : 0;
    }
}
