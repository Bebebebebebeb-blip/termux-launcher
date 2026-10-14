package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.notice.AppNotice;
import com.termux.app.terminal.inappkeyboard.KeyboardLayoutFiles;
import com.termux.app.terminal.inappkeyboard.LauncherKeyboardLayouts;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Picks the layouts the in-app keyboard hot-swaps between, and the order it walks them in.
 *
 * <p>Two lists, because they answer two different questions. The cycle at the top is short,
 * ordered and editable in place — it is what a bound key steps through. The catalogue below is
 * every layout the app ships, searchable, because ninety-one entries is not a list anyone
 * scrolls.
 *
 * <p>Every edit persists immediately. The live keyboard re-reads the ring in
 * {@code onPreferencesReloaded} when the user returns from Settings, so there is no Save button
 * to forget: leaving the screen is the apply.
 *
 * <p>This is also where layouts are edited. Above the two lists sit the user's own layouts, with
 * + to start a new one; every row opens {@link KeyboardLayoutEditorFragment}, and an edited
 * shipped layout carries an "Edited" tag and a reset. Both are files (see
 * {@link KeyboardLayoutFiles}), so the disk is read on a background thread whenever the screen
 * comes back, which also picks up anything changed from a terminal in between.
 */
@Keep
public class KeyboardLayoutsFragment extends Fragment {

    private TermuxAppSharedPreferences mPreferences;
    private final List<String> mSelection = new ArrayList<>();
    private LinearLayout mUserList;
    private LinearLayout mCycleList;
    private LinearLayout mAvailableList;
    private String mFilter = "";

    /** Shipped layouts with an override file, {@code main} included. Filled off the main thread. */
    private final Set<String> mOverridden = new HashSet<>();
    /** The user's own layouts, as last read from disk. */
    private final List<KeyboardLayoutFiles.UserLayout> mUserLayouts = new ArrayList<>();
    private boolean mFilesLoaded;
    private final ExecutorService mFileExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private int mColorText;
    private int mColorSubtle;
    private int mColorAccent;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        mPreferences = TermuxAppSharedPreferences.build(context);
        if (mPreferences == null)
            throw new IllegalStateException("Termux preferences unavailable");
        mColorText = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
        mColorSubtle = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);
        mColorAccent = MaterialColors.getColor(context,
            androidx.appcompat.R.attr.colorPrimary, mColorText);
        mSelection.clear();
        mSelection.addAll(LauncherKeyboardLayouts.parseSelection(getResources(),
            mPreferences.getInAppKeyboardLayouts()));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(24));

        TextView instructions = new TextView(context);
        instructions.setText(R.string.settings_keyboard_layouts_instructions);
        instructions.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        instructions.setTextColor(mColorSubtle);
        root.addView(instructions);

        root.addView(userHeader(context));
        mUserList = new LinearLayout(context);
        mUserList.setOrientation(LinearLayout.VERTICAL);
        root.addView(mUserList);

        root.addView(sectionHeader(context, getString(R.string.settings_keyboard_layouts_in_cycle)));
        mCycleList = new LinearLayout(context);
        mCycleList.setOrientation(LinearLayout.VERTICAL);
        root.addView(mCycleList);

        root.addView(sectionHeader(context,
            getString(R.string.settings_keyboard_layouts_available)));
        root.addView(buildSearchField(context));
        mAvailableList = new LinearLayout(context);
        mAvailableList.setOrientation(LinearLayout.VERTICAL);
        root.addView(mAvailableList);

        renderUser();
        renderCycle();
        renderAvailable();

        ScrollView scroller = new ScrollView(context);
        scroller.addView(root, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroller;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Back from the editor, or from a terminal: what is on disk may have moved.
        refreshFiles();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mFileExecutor.shutdown();
    }

    /** Re-reads which layouts are edited and which are the user's own, then re-renders. */
    private void refreshFiles() {
        File home = KeyboardLayoutFiles.defaultHome();
        mFileExecutor.execute(() -> {
            Set<String> overridden = KeyboardLayoutFiles.overriddenIds(home);
            List<KeyboardLayoutFiles.UserLayout> users = KeyboardLayoutFiles.userLayouts(home);
            for (KeyboardLayoutFiles.UserLayout layout : users)
                LauncherKeyboardLayouts.rememberUserLabel(layout.id, layout.name);
            mMainHandler.post(() -> {
                if (!isAdded() || mCycleList == null) return;
                mOverridden.clear();
                mOverridden.addAll(overridden);
                mUserLayouts.clear();
                mUserLayouts.addAll(users);
                mFilesLoaded = true;
                pruneMissingUserLayouts();
                renderUser();
                renderCycle();
                renderAvailable();
            });
        });
    }

    /**
     * A user layout whose file is gone — deleted from a terminal — leaves the ring, so the keyboard
     * does not keep a stop that only shows the fallback QWERTY.
     */
    private void pruneMissingUserLayouts() {
        Set<String> present = new HashSet<>();
        for (KeyboardLayoutFiles.UserLayout layout : mUserLayouts) present.add(layout.id);
        boolean changed = false;
        for (int i = mSelection.size() - 1; i >= 0; i--) {
            String id = mSelection.get(i);
            if (LauncherKeyboardLayouts.isUserLayout(id) && !present.contains(id)) {
                mSelection.remove(i);
                changed = true;
            }
        }
        if (!changed) return;
        if (mSelection.isEmpty()) mSelection.add(LauncherKeyboardLayouts.LAYOUT_MAIN);
        mPreferences.setInAppKeyboardLayouts(LauncherKeyboardLayouts.joinSelection(mSelection));
    }

    @NonNull
    private View buildSearchField(@NonNull Context context) {
        TextInputLayout field = new TextInputLayout(context);
        field.setHint(getString(R.string.settings_keyboard_layouts_search_hint));
        TextInputEditText input = new TextInputEditText(context);
        input.setSingleLine(true);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override public void afterTextChanged(Editable editable) {
                mFilter = editable == null ? "" : editable.toString().trim();
                renderAvailable();
            }
        });
        field.addView(input, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return field;
    }

    /** The ordered ring: the reorder and remove controls live on the rows themselves. */
    private void renderCycle() {
        Context context = requireContext();
        mCycleList.removeAllViews();
        for (int i = 0; i < mSelection.size(); i++) {
            final int index = i;
            String id = mSelection.get(i);
            String label = LauncherKeyboardLayouts.labelFor(getResources(), id);
            LinearLayout row = row(context, label, meta(String.valueOf(i + 1), id));
            row.addView(glyphButton(context, "↑",
                getString(R.string.settings_keyboard_layouts_move_up, label),
                index > 0, view -> move(index, -1)));
            row.addView(glyphButton(context, "↓",
                getString(R.string.settings_keyboard_layouts_move_down, label),
                index < mSelection.size() - 1, view -> move(index, 1)));
            row.addView(glyphButton(context, "✕",
                getString(R.string.settings_keyboard_layouts_remove, label),
                true, view -> remove(index)));
            addFileButtons(context, row, id, label);
            mCycleList.addView(row);
        }
    }

    /** Everything the app ships, minus what is already in the ring, narrowed by the search box. */
    private void renderAvailable() {
        Context context = requireContext();
        mAvailableList.removeAllViews();
        String needle = mFilter.toLowerCase(Locale.US);
        int shown = 0;
        for (LauncherKeyboardLayouts.Layout layout
            : LauncherKeyboardLayouts.catalog(getResources())) {
            if (mSelection.contains(layout.id)) continue;
            if (!needle.isEmpty()
                && !layout.label.toLowerCase(Locale.US).contains(needle)
                && !layout.id.toLowerCase(Locale.US).contains(needle)) continue;
            LinearLayout row = row(context, layout.label, meta(layout.id, layout.id));
            row.addView(glyphButton(context, "+",
                getString(R.string.settings_keyboard_layouts_add, layout.label),
                true, view -> add(layout.id)));
            addFileButtons(context, row, layout.id, layout.label);
            mAvailableList.addView(row);
            shown++;
        }
        if (shown == 0 && !needle.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(getString(R.string.settings_keyboard_layouts_none_match, mFilter));
            empty.setTextColor(mColorSubtle);
            empty.setPadding(0, dp(8), 0, dp(8));
            mAvailableList.addView(empty);
        }
    }

    /** The user's own layouts: add to the cycle, edit, delete. */
    private void renderUser() {
        Context context = requireContext();
        mUserList.removeAllViews();
        if (!mFilesLoaded) return;
        if (mUserLayouts.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(R.string.settings_keyboard_layouts_yours_empty);
            empty.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
            empty.setTextColor(mColorSubtle);
            empty.setPadding(0, dp(4), 0, dp(4));
            mUserList.addView(empty);
            return;
        }
        for (KeyboardLayoutFiles.UserLayout layout : mUserLayouts) {
            LinearLayout row = row(context, layout.name,
                KeyboardLayoutFiles.displayPath(layout.id));
            row.addView(glyphButton(context, "+",
                getString(R.string.settings_keyboard_layouts_add, layout.name),
                !mSelection.contains(layout.id), view -> add(layout.id)));
            addFileButtons(context, row, layout.id, layout.name);
            mUserList.addView(row);
        }
    }

    /**
     * Edit on every row; reset on an edited shipped layout, delete on a user's own. Nothing but
     * edit until the disk has been read, so a reset is never offered for a file not yet seen.
     */
    private void addFileButtons(@NonNull Context context, @NonNull LinearLayout row,
                                @NonNull String id, @NonNull String label) {
        row.addView(iconButton(context, R.drawable.ic_symbol_edit,
            getString(R.string.settings_keyboard_layouts_edit, label), view -> openEditor(id)));
        if (LauncherKeyboardLayouts.isUserLayout(id)) {
            if (mFilesLoaded)
                row.addView(iconButton(context, R.drawable.ic_symbol_delete,
                    getString(R.string.settings_keyboard_layouts_delete, label),
                    view -> confirmDelete(id, label)));
        } else if (mOverridden.contains(id)) {
            row.addView(iconButton(context, R.drawable.ic_symbol_restart,
                getString(R.string.settings_keyboard_layouts_reset, label),
                view -> confirmReset(id, label)));
        }
    }

    /** A row's second line, with the "Edited" tag in front when the layout has an override. */
    @NonNull
    private CharSequence meta(@NonNull String text, @NonNull String id) {
        if (!mOverridden.contains(id)) return text;
        SpannableStringBuilder meta = new SpannableStringBuilder(
            getString(R.string.settings_keyboard_layouts_edited));
        meta.setSpan(new ForegroundColorSpan(mColorAccent), 0, meta.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        meta.append(" · ").append(text);
        return meta;
    }

    /** The editor for {@code id}, or for a new layout of the user's own when null. */
    private void openEditor(@Nullable String id) {
        if (!(getActivity() instanceof SettingsActivity)) return;
        Bundle args = new Bundle();
        if (id == null) args.putBoolean(KeyboardLayoutEditorFragment.ARG_NEW, true);
        else args.putString(KeyboardLayoutEditorFragment.ARG_LAYOUT_ID, id);
        ((SettingsActivity) getActivity()).openScreen(KeyboardLayoutEditorFragment.class,
            id == null ? R.string.settings_keyboard_layout_editor_new_title
                : R.string.settings_keyboard_layout_editor_title, args);
    }

    private void confirmReset(@NonNull String id, @NonNull String label) {
        String path = KeyboardLayoutFiles.displayPath(id);
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.settings_keyboard_layouts_reset_title, label))
            .setMessage(getString(R.string.settings_keyboard_layouts_reset_message, path))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_keyboard_layouts_reset_action,
                (dialog, which) -> deleteFile(id))
            .show();
    }

    private void confirmDelete(@NonNull String id, @NonNull String label) {
        String path = KeyboardLayoutFiles.displayPath(id);
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.settings_keyboard_layouts_delete_title, label))
            .setMessage(getString(R.string.settings_keyboard_layouts_delete_message, path))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_keyboard_layouts_delete_action,
                (dialog, which) -> deleteFile(id))
            .show();
    }

    /** Resets an edited layout or deletes a user's own; the ring loses a deleted one. */
    private void deleteFile(@NonNull String id) {
        File file = KeyboardLayoutFiles.overrideFile(KeyboardLayoutFiles.defaultHome(), id);
        Context appContext = requireContext().getApplicationContext();
        String path = KeyboardLayoutFiles.displayPath(id);
        mFileExecutor.execute(() -> {
            boolean deleted = KeyboardLayoutFiles.delete(file);
            mMainHandler.post(() -> {
                if (!deleted)
                    AppNotice.show(appContext, appContext.getString(
                        R.string.settings_keyboard_layouts_file_error, path), true);
                if (isAdded()) refreshFiles();
            });
        });
    }

    private void add(@NonNull String layoutId) {
        if (mSelection.size() >= LauncherKeyboardLayouts.MAX_SELECTION) {
            AppNotice.show(requireContext(), getString(R.string.settings_keyboard_layouts_full,
                LauncherKeyboardLayouts.MAX_SELECTION), false);
            return;
        }
        if (mSelection.contains(layoutId)) return;
        mSelection.add(layoutId);
        persist();
    }

    private void remove(int index) {
        if (index < 0 || index >= mSelection.size()) return;
        // The ring is what the keyboard types on; emptying it would leave nothing to render.
        if (mSelection.size() == 1) {
            AppNotice.show(requireContext(),
                getString(R.string.settings_keyboard_layouts_last), false);
            return;
        }
        mSelection.remove(index);
        persist();
    }

    private void move(int index, int delta) {
        int target = index + delta;
        if (index < 0 || index >= mSelection.size() || target < 0 || target >= mSelection.size())
            return;
        mSelection.add(target, mSelection.remove(index));
        persist();
    }

    private void persist() {
        mPreferences.setInAppKeyboardLayouts(LauncherKeyboardLayouts.joinSelection(mSelection));
        renderUser();
        renderCycle();
        renderAvailable();
    }

    @NonNull
    private LinearLayout row(@NonNull Context context, @NonNull String label,
                             @NonNull CharSequence meta) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, dp(2));

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);
        title.setText(label);
        title.setTextColor(mColorText);
        title.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        text.addView(title);
        TextView subtitle = new TextView(context);
        subtitle.setText(meta);
        subtitle.setTextColor(mColorSubtle);
        subtitle.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        text.addView(subtitle);
        row.addView(text, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    /**
     * A single-glyph control. Disabled rather than hidden at the ends of the list, so the row
     * keeps its shape and the buttons stay under the same thumb from row to row.
     */
    @NonNull
    private MaterialButton glyphButton(@NonNull Context context, @NonNull String glyph,
                                       @NonNull String description, boolean enabled,
                                       @NonNull View.OnClickListener onClick) {
        MaterialButton button = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(glyph);
        button.setContentDescription(description);
        button.setEnabled(enabled);
        button.setMinWidth(dp(48));
        button.setMinimumWidth(dp(48));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setOnClickListener(onClick);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(4));
        button.setLayoutParams(params);
        return button;
    }

    /**
     * An icon-only control at the 48dp touch target the glyph buttons keep, borderless so a row
     * of them reads lighter than the ring's own reorder controls.
     */
    @NonNull
    private MaterialButton iconButton(@NonNull Context context, int icon,
                                      @NonNull String description,
                                      @NonNull View.OnClickListener onClick) {
        MaterialButton button = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialIconButtonStyle);
        button.setIconResource(icon);
        button.setContentDescription(description);
        TooltipCompat.setTooltipText(button, description);
        button.setMinWidth(dp(48));
        button.setMinimumWidth(dp(48));
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setOnClickListener(onClick);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(4));
        button.setLayoutParams(params);
        return button;
    }

    /** "Your layouts", with + at its end to start a new one. */
    @NonNull
    private View userHeader(@NonNull Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = sectionHeader(context, getString(R.string.settings_keyboard_layouts_yours));
        header.addView(title, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialButton add = iconButton(context, R.drawable.ic_symbol_add,
            getString(R.string.settings_keyboard_layouts_new), view -> openEditor(null));
        header.addView(add);
        return header;
    }

    @NonNull
    private TextView sectionHeader(@NonNull Context context, @NonNull String text) {
        TextView header = new TextView(context);
        header.setText(text);
        header.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        header.setTextColor(mColorText);
        header.setPadding(0, dp(16), 0, dp(4));
        return header;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
