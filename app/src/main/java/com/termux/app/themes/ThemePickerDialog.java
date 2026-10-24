package com.termux.app.themes;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.launcherctl.DeviceControlRoutes;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The theme picker: a search field over a grid of cards (preview and name), a "random" card
 * first. Type to filter, arrow keys move the highlight, Enter or a tap applies, Esc closes.
 * Plain Android views, no layout files. Opened by the {@code theme.pick} action.
 */
public final class ThemePickerDialog {

    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Random RANDOM = new Random();
    private static final int THUMB_PX = 480;
    private static final int ACCENT = 0xFF7AA2F7;

    private ThemePickerDialog() {}

    /** One card: a theme, or the "random" card, which applies any theme when chosen. */
    private static final class Entry {
        @NonNull final String label;
        @NonNull final ThemeStore.Theme theme;
        final boolean random;

        Entry(@NonNull String label, @NonNull ThemeStore.Theme theme, boolean random) {
            this.label = label;
            this.theme = theme;
            this.random = random;
        }
    }

    public static void show(@NonNull Activity activity) {
        final List<ThemeStore.Theme> themes = ThemeStore.list();
        final float density = activity.getResources().getDisplayMetrics().density;

        final Dialog dialog = new Dialog(activity,
            android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        final Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        }
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ESCAPE && event.getAction() == KeyEvent.ACTION_UP) {
                dialog.dismiss();
                return true;
            }
            return false;
        });

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(density, 14), dp(density, 14), dp(density, 14), dp(density, 14));
        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(0xF2101216);
        panelBackground.setCornerRadius(dp(density, 12));
        panel.setBackground(panelBackground);

        if (themes.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("No themes yet. Put a picture in ~/themes, or a folder with "
                + "wallpaper.png in ~/.termux/themes/NAME.");
            empty.setTextColor(0xFFCCCCCC);
            empty.setPadding(dp(density, 8), dp(density, 8), dp(density, 8), dp(density, 8));
            panel.addView(empty);
            dialog.setContentView(panel);
            dialog.show();
            sizeWindow(activity, window, 0.6f, 0.0f);
            return;
        }

        final List<Entry> all = new ArrayList<>();
        all.add(new Entry("random", themes.get(RANDOM.nextInt(themes.size())), true));
        for (ThemeStore.Theme theme : themes) all.add(new Entry(theme.name, theme, false));
        final List<Entry> shown = new ArrayList<>(all);
        final int[] selected = {0};

        final EditText search = new EditText(activity);
        search.setHint("Search / Choose Theme");
        search.setHintTextColor(0xFFB0B4B8);
        search.setTextColor(0xFFFFFFFF);
        search.setSingleLine(true);
        search.setGravity(Gravity.CENTER);
        search.setImeOptions(EditorInfo.IME_ACTION_DONE);
        search.setPadding(dp(density, 12), dp(density, 10), dp(density, 12), dp(density, 10));
        GradientDrawable searchBackground = new GradientDrawable();
        searchBackground.setColor(0xFF2A2F35);
        searchBackground.setCornerRadius(dp(density, 8));
        search.setBackground(searchBackground);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.bottomMargin = dp(density, 10);
        panel.addView(search, searchParams);

        LinearLayout box = new LinearLayout(activity);
        box.setPadding(dp(density, 10), dp(density, 10), dp(density, 10), dp(density, 10));
        GradientDrawable boxBackground = new GradientDrawable();
        boxBackground.setColor(Color.TRANSPARENT);
        boxBackground.setStroke(dp(density, 2), ACCENT);
        boxBackground.setCornerRadius(dp(density, 8));
        box.setBackground(boxBackground);

        final GridView grid = new GridView(activity);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(density, 150));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(density, 10));
        grid.setVerticalSpacing(dp(density, 10));
        final EntryAdapter adapter = new EntryAdapter(activity, shown, selected, density);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) ->
            choose(activity, dialog, shown.get(position), themes));
        box.addView(grid, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        panel.addView(box, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String query = s.toString().trim().toLowerCase(Locale.ROOT);
                shown.clear();
                for (Entry entry : all) {
                    if (query.isEmpty() || entry.label.toLowerCase(Locale.ROOT).contains(query)) {
                        shown.add(entry);
                    }
                }
                selected[0] = 0;
                adapter.notifyDataSetChanged();
                grid.setSelection(0);
            }
        });
        search.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN || shown.isEmpty()) return false;
            int columns = Math.max(1, grid.getNumColumns());
            int target = selected[0];
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT: target++; break;
                case KeyEvent.KEYCODE_DPAD_LEFT: target--; break;
                case KeyEvent.KEYCODE_DPAD_DOWN: target += columns; break;
                case KeyEvent.KEYCODE_DPAD_UP: target -= columns; break;
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    choose(activity, dialog, shown.get(selected[0]), themes);
                    return true;
                default:
                    return false;
            }
            selected[0] = Math.max(0, Math.min(shown.size() - 1, target));
            adapter.notifyDataSetChanged();
            grid.smoothScrollToPosition(selected[0]);
            return true;
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE && !shown.isEmpty()) {
                choose(activity, dialog, shown.get(selected[0]), themes);
            }
            return true;
        });

        dialog.setContentView(panel);
        dialog.show();
        sizeWindow(activity, window, 0.85f, 0.8f);
        search.requestFocus();
    }

    private static void sizeWindow(@NonNull Activity activity, @Nullable Window window,
                                   float widthFraction, float heightFraction) {
        if (window == null) return;
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int height = heightFraction <= 0f ? ViewGroup.LayoutParams.WRAP_CONTENT
            : (int) (metrics.heightPixels * heightFraction);
        window.setLayout((int) (metrics.widthPixels * widthFraction), height);
        window.setGravity(Gravity.CENTER);
    }

    private static void choose(@NonNull Activity activity, @NonNull Dialog dialog,
                               @NonNull Entry entry, @NonNull List<ThemeStore.Theme> themes) {
        ThemeStore.Theme theme = entry.random
            ? themes.get(RANDOM.nextInt(themes.size())) : entry.theme;
        apply(activity, dialog, theme);
    }

    private static void apply(@NonNull Activity activity, @NonNull Dialog dialog,
                              @NonNull ThemeStore.Theme theme) {
        dialog.dismiss();
        Toast.makeText(activity, "Applying " + theme.name + "...", Toast.LENGTH_SHORT).show();
        final Context app = activity.getApplicationContext();
        IO.execute(() -> {
            final boolean ok = DeviceControlRoutes.applyWallpaperFile(app,
                theme.wallpaper.getAbsolutePath());
            MAIN.post(() -> Toast.makeText(activity,
                ok ? "Theme: " + theme.name : "Could not apply " + theme.name,
                Toast.LENGTH_SHORT).show());
        });
    }

    private static int dp(float density, float value) {
        return Math.round(value * density);
    }

    @Nullable
    private static Bitmap decode(@NonNull File file, int maxSide) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private static final class EntryAdapter extends BaseAdapter {
        private final Activity mActivity;
        private final List<Entry> mItems;
        private final int[] mSelected;
        private final float mDensity;
        private final Map<String, Bitmap> mCache = new HashMap<>();

        EntryAdapter(@NonNull Activity activity, @NonNull List<Entry> items,
                     @NonNull int[] selected, float density) {
            mActivity = activity;
            mItems = items;
            mSelected = selected;
            mDensity = density;
        }

        @Override public int getCount() { return mItems.size(); }
        @Override public Object getItem(int position) { return mItems.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, @Nullable View convertView, ViewGroup parent) {
            LinearLayout card;
            ImageView image;
            TextView label;
            if (convertView instanceof LinearLayout) {
                card = (LinearLayout) convertView;
                image = (ImageView) card.getChildAt(0);
                label = (TextView) card.getChildAt(1);
            } else {
                card = new LinearLayout(mActivity);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(mDensity, 6), dp(mDensity, 6), dp(mDensity, 6), dp(mDensity, 6));
                image = new ImageView(mActivity);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setBackgroundColor(0xFF2A2F35);
                final float radius = dp(mDensity, 8);
                image.setOutlineProvider(new ViewOutlineProvider() {
                    @Override public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                    }
                });
                image.setClipToOutline(true);
                card.addView(image, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(mDensity, 90)));
                label = new TextView(mActivity);
                label.setTextColor(0xFFFFFFFF);
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                label.setGravity(Gravity.CENTER);
                label.setSingleLine(true);
                label.setEllipsize(TextUtils.TruncateAt.END);
                label.setPadding(0, dp(mDensity, 6), 0, 0);
                card.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            Entry entry = mItems.get(position);
            label.setText(entry.label);
            if (position == mSelected[0]) {
                GradientDrawable highlight = new GradientDrawable();
                highlight.setColor(0x26FFFFFF);
                highlight.setStroke(dp(mDensity, 2), ACCENT);
                highlight.setCornerRadius(dp(mDensity, 10));
                card.setBackground(highlight);
            } else {
                card.setBackground(null);
            }
            load(image, entry.theme.wallpaper);
            return card;
        }

        private void load(@NonNull ImageView target, @NonNull File file) {
            final String key = file.getAbsolutePath();
            target.setTag(key);
            Bitmap cached = mCache.get(key);
            if (cached != null) {
                target.setImageBitmap(cached);
                return;
            }
            target.setImageDrawable(null);
            IO.execute(() -> {
                final Bitmap bitmap = decode(file, THUMB_PX);
                if (bitmap == null) return;
                MAIN.post(() -> {
                    mCache.put(key, bitmap);
                    if (key.equals(target.getTag())) target.setImageBitmap(bitmap);
                });
            });
        }
    }
}
