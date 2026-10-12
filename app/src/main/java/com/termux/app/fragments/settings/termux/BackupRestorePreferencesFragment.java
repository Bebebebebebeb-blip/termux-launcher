package com.termux.app.fragments.settings.termux;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.notice.AppNotice;
import com.termux.app.settings.backup.SettingsBackupFormat;
import com.termux.app.settings.backup.SettingsBackupFormat.InvalidBackupException;
import com.termux.app.settings.backup.SettingsBackupIo;
import com.termux.app.settings.backup.SettingsRestart;
import com.termux.shared.logger.Logger;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Backup & restore: saves the launcher's settings and config files to one file, and restores them
 * from one ({@link SettingsBackupFormat}). Reading, writing and restoring all run on one worker
 * thread; a restore asks first, then restarts the launcher so nothing runs half-restored.
 */
@Keep
public final class BackupRestorePreferencesFragment extends MaterialPreferenceFragment {

    private static final String LOG_TAG = "BackupRestore";

    private static final String KEY_EXPORT = "settings_backup_export";
    private static final String KEY_IMPORT = "settings_backup_import";

    private static final String MIME_ZIP = "application/zip";
    /** Some pickers report a zip by its generic or legacy type; the reader checks the contents. */
    private static final String[] IMPORT_MIME_TYPES = {MIME_ZIP, "application/octet-stream", "application/x-zip-compressed"};

    /** Long enough to read the one-sentence notice before the launcher restarts. */
    private static final long RESTART_AFTER_NOTICE_MS = 3000;

    /** The shared Downloads folder, where the pickers open; a picker that cannot go there ignores it. */
    private static final Uri DOWNLOADS = DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents", "primary:" + android.os.Environment.DIRECTORY_DOWNLOADS);

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "settings-backup");
        thread.setDaemon(true);
        return thread;
    });
    /** One export or import at a time, across screen rotations. */
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);

    private ActivityResultLauncher<String> mExportLauncher;
    private ActivityResultLauncher<String[]> mImportLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mExportLauncher = registerForActivityResult(new CreateZipDocument(), this::onExportTarget);
        mImportLauncher = registerForActivityResult(new OpenZipDocument(), this::onImportSource);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        if (getContext() == null) return;
        setPreferencesFromResource(R.xml.backup_restore_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        click(KEY_EXPORT, () -> {
            LocalDate today = LocalDate.now();
            mExportLauncher.launch(SettingsBackupFormat.suggestedFileName(
                today.getYear(), today.getMonthValue(), today.getDayOfMonth()));
        });
        click(KEY_IMPORT, () -> mImportLauncher.launch(IMPORT_MIME_TYPES.clone()));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_destination_backup_restore);
    }

    private void click(@NonNull String key, @NonNull Runnable action) {
        Preference row = findPreference(key);
        if (row == null) return;
        row.setOnPreferenceClickListener(preference -> {
            if (!BUSY.get()) {
                try {
                    action.run();
                } catch (RuntimeException e) {
                    // No document picker on this device.
                    Logger.logStackTraceWithMessage(LOG_TAG, "Opening the file picker failed", e);
                    AppNotice.show(getContext(), R.string.settings_backup_import_read_failed, false);
                }
            }
            return true;
        });
    }

    // ------------------------------------------------------------------ export

    private void onExportTarget(@Nullable Uri uri) {
        Context context = getContext();
        if (uri == null || context == null || !BUSY.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            @StringRes int message;
            try (OutputStream out = app.getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new java.io.IOException("No stream for " + uri);
                SettingsBackupIo.ExportResult result = SettingsBackupIo.export(
                    SettingsBackupIo.environment(app, true), out, System.currentTimeMillis());
                message = result.skipped.isEmpty()
                    ? R.string.settings_backup_exported : R.string.settings_backup_exported_skipped;
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Saving the settings failed", e);
                deleteQuietly(app, uri);
                message = R.string.settings_backup_export_failed;
            }
            int shown = message;
            onMain(() -> {
                BUSY.set(false);
                AppNotice.show(app, shown, false);
            });
        });
    }

    /** A failed export leaves no half-written file behind. */
    private static void deleteQuietly(@NonNull Context app, @NonNull Uri uri) {
        try {
            DocumentsContract.deleteDocument(app.getContentResolver(), uri);
        } catch (Exception ignored) {
            // The provider may not allow it; the file is then simply incomplete.
        }
    }

    // ------------------------------------------------------------------ import

    private void onImportSource(@Nullable Uri uri) {
        Context context = getContext();
        if (uri == null || context == null || !BUSY.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            SettingsBackupIo.Environment env = null;
            SettingsBackupIo.Staged staged = null;
            @StringRes int error = 0;
            try (InputStream in = app.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new java.io.IOException("No stream for " + uri);
                env = SettingsBackupIo.environment(app, false);
                staged = SettingsBackupIo.read(env, in);
            } catch (InvalidBackupException e) {
                error = messageFor(e.reason);
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Reading the backup failed", e);
                error = R.string.settings_backup_import_read_failed;
            }
            SettingsBackupIo.Environment readEnv = env;
            SettingsBackupIo.Staged readStaged = staged;
            int readError = error;
            onMain(() -> {
                if (readStaged == null || readEnv == null) {
                    BUSY.set(false);
                    AppNotice.show(app, readError != 0 ? readError : R.string.settings_backup_import_read_failed, false);
                } else if (!isAdded() || getActivity() == null) {
                    discard(readStaged);
                } else {
                    confirm(readEnv, readStaged);
                }
            });
        });
    }

    @StringRes
    private static int messageFor(@NonNull InvalidBackupException.Reason reason) {
        switch (reason) {
            case NEWER_FORMAT: return R.string.settings_backup_import_newer;
            case TOO_LARGE: return R.string.settings_backup_import_too_large;
            default: return R.string.settings_backup_import_not_backup;
        }
    }

    private void confirm(@NonNull SettingsBackupIo.Environment env, @NonNull SettingsBackupIo.Staged staged) {
        Activity activity = requireActivity();
        SettingsBackupFormat.Manifest manifest = staged.manifest;
        String date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(manifest.createdAt));
        boolean[] confirmed = {false};
        new MaterialAlertDialogBuilder(activity)
            .setTitle(getString(R.string.settings_backup_import_confirm_title, date))
            .setMessage(getString(R.string.settings_backup_import_confirm_message, manifest.versionName))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_backup_import_confirm_action, (dialog, which) -> {
                confirmed[0] = true;
                restore(activity, env, staged);
            })
            .setOnDismissListener(dialog -> {
                if (!confirmed[0]) discard(staged);
            })
            .show();
    }

    private static void discard(@NonNull SettingsBackupIo.Staged staged) {
        WORKER.execute(() -> {
            SettingsBackupIo.discard(staged);
            BUSY.set(false);
        });
    }

    /**
     * Writes the backup, then restarts the launcher whatever happened, so it never runs with half
     * a restore in memory. A partial failure is said first, long enough to read.
     */
    private static void restore(@NonNull Activity activity, @NonNull SettingsBackupIo.Environment env,
                                @NonNull SettingsBackupIo.Staged staged) {
        Context app = activity.getApplicationContext();
        WORKER.execute(() -> {
            int failures;
            try {
                failures = SettingsBackupIo.apply(env, staged).failures
                    + SettingsBackupIo.applyWallpapers(app, env, staged);
            } catch (RuntimeException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Restoring the settings failed", e);
                failures = 1;
            }
            int failed = failures;
            onMain(() -> {
                if (failed == 0) {
                    SettingsRestart.restart(activity);
                    return;
                }
                AppNotice.show(app, R.string.settings_backup_import_partial, true);
                new Handler(Looper.getMainLooper()).postDelayed(
                    () -> SettingsRestart.restart(activity), RESTART_AFTER_NOTICE_MS);
            });
        });
    }

    private static void onMain(@NonNull Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }

    // ------------------------------------------------------------------ pickers

    /** Save-as for a zip, opening in Downloads. */
    static final class CreateZipDocument extends ActivityResultContracts.CreateDocument {
        CreateZipDocument() {
            super(MIME_ZIP);
        }

        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @NonNull String input) {
            return super.createIntent(context, input).putExtra(DocumentsContract.EXTRA_INITIAL_URI, DOWNLOADS);
        }
    }

    /** Open a zip, opening in Downloads; virtual documents are left out since the bytes are needed. */
    static final class OpenZipDocument extends ActivityResultContracts.OpenDocument {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @NonNull String[] input) {
            return super.createIntent(context, input)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(DocumentsContract.EXTRA_INITIAL_URI, DOWNLOADS);
        }
    }
}
