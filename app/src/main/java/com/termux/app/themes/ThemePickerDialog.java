package com.termux.app.themes;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.launcherctl.DeviceControlRoutes;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The theme picker: a grid of cards (preview and name); a tap applies the theme and closes the
 * dialog. Plain Android views, no layout files, so it needs nothing from the launcher but an
 * Activity. Opened by the {@code theme.pick} action.
 */
public final class ThemePickerDialog {

    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int THUMB_PX = 480;

    private ThemePickerDialog() {}

    public static void show(@NonNull Activity activity) {
        final List<ThemeStore.Theme> themes = ThemeStore.list();
        final float density = activity.getResources().getDisplayMetrics().density;

        final Dialog dialog = new Dialog(activity,
            android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(density, 16), dp(density, 14), dp(density, 16), dp(density, 16));
        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(0xF0141418);
        panelBackground.setCornerRadius(dp(density, 24));
        panel.setBackground(panelBackground);

        TextView title = new TextView(activity);
        title.setText("Themes");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setPadding(dp(density, 4), 0, 0, dp(density, 10));
        panel.addView(title, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (themes.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("No themes yet. Put a picture in ~/themes, or a folder with "
                + "wallpaper.png in ~/.termux/themes/NAME.");
            empty.setTextColor(0xFFBBBBBB);
            empty.setPadding(dp(density, 4), dp(density, 8), dp(density, 4), dp(density, 8));
            panel.addView(empty);
        } else {
            GridView grid = new GridView(activity);
            grid.setNumColumns(GridView.AUTO_FIT);
            grid.setColumnWidth(dp(density, 200));
            grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
            grid.setHorizontalSpacing(dp(density, 12));
            grid.setVerticalSpacing(dp(density, 12));
            grid.setAdapter(new ThemeAdapter(activity, themes, density));
            grid.setOnItemClickListener((parent, view, position, id) ->
                apply(activity, dialog, themes.get(position)));
            panel.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        dialog.setContentView(panel);
        dialog.show();
        if (window != null) {
            DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            window.setLayout((int) (metrics.widthPixels * 0.85f),
                (int) (metrics.heightPixels * 0.8f));
            window.setGravity(Gravity.CENTER);
        }
    }

    private static void apply(@NonNull Activity activity, @NonNull Dialog dialog,
                              @NonNull ThemeStore.Theme theme) {
        dialog.dismiss();
        Toast.makeText(activity, "Applying " + theme.name + "...", Toast.LENGTH_SHORT).show();
        final android.content.Context app = activity.getApplicationContext();
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

    private static final class ThemeAdapter extends BaseAdapter {
        private final Activity mActivity;
        private final List<ThemeStore.Theme> mThemes;
        private final float mDensity;
        private final Map<String, Bitmap> mCache = new HashMap<>();

        ThemeAdapter(@NonNull Activity activity, @NonNull List<ThemeStore.Theme> themes,
                     float density) {
            mActivity = activity;
            mThemes = themes;
            mDensity = density;
        }

        @Override public int getCount() { return mThemes.size(); }
        @Override public Object getItem(int position) { return mThemes.get(position); }
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
                image = new ImageView(mActivity);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setBackgroundColor(0xFF2A2A30);
                final float radius = dp(mDensity, 14);
                image.setOutlineProvider(new ViewOutlineProvider() {
                    @Override public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                    }
                });
                image.setClipToOutline(true);
                card.addView(image, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(mDensity, 120)));
                label = new TextView(mActivity);
                label.setTextColor(0xFFFFFFFF);
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                label.setSingleLine(true);
                label.setEllipsize(TextUtils.TruncateAt.END);
                label.setPadding(dp(mDensity, 4), dp(mDensity, 6), dp(mDensity, 4), 0);
                card.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            ThemeStore.Theme theme = mThemes.get(position);
            label.setText(theme.name);
            load(image, theme.wallpaper);
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
