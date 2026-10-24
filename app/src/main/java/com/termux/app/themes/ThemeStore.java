package com.termux.app.themes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The themes on disk. A theme is a folder {@code ~/.termux/themes/NAME/} holding a picture named
 * {@code wallpaper.png|jpg|jpeg|webp} (or, failing that, the first picture in it), and for
 * convenience every loose picture in {@code ~/themes} counts as a theme of its own.
 */
public final class ThemeStore {

    /** One theme: its name and the wallpaper it applies. */
    public static final class Theme {
        @NonNull public final String name;
        @NonNull public final File wallpaper;

        Theme(@NonNull String name, @NonNull File wallpaper) {
            this.name = name;
            this.wallpaper = wallpaper;
        }
    }

    private static final String[] PICTURE_NAMES = {
        "wallpaper.png", "wallpaper.jpg", "wallpaper.jpeg", "wallpaper.webp"
    };

    private ThemeStore() {}

    /** {@code ~/.termux/themes}, where themes saved by the launcher live. */
    @NonNull
    public static File themesDir() {
        return new File(new File(TermuxConstants.TERMUX_HOME_DIR_PATH), ".termux/themes");
    }

    /** Every theme found, folders first and then loose pictures, each group sorted by name. */
    @NonNull
    public static List<Theme> list() {
        List<Theme> themes = new ArrayList<>();
        File[] folders = themesDir().listFiles(File::isDirectory);
        if (folders != null) {
            Arrays.sort(folders);
            for (File folder : folders) {
                File picture = pictureIn(folder);
                if (picture != null) themes.add(new Theme(folder.getName(), picture));
            }
        }
        File[] loose = new File(new File(TermuxConstants.TERMUX_HOME_DIR_PATH), "themes")
            .listFiles(file -> file.isFile() && isPicture(file.getName()));
        if (loose != null) {
            Arrays.sort(loose);
            for (File file : loose) themes.add(new Theme(stripExtension(file.getName()), file));
        }
        return themes;
    }

    @Nullable
    private static File pictureIn(@NonNull File folder) {
        for (String name : PICTURE_NAMES) {
            File candidate = new File(folder, name);
            if (candidate.isFile()) return candidate;
        }
        File[] files = folder.listFiles(file -> file.isFile() && isPicture(file.getName()));
        if (files == null || files.length == 0) return null;
        Arrays.sort(files);
        return files[0];
    }

    private static boolean isPicture(@NonNull String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg")
            || lower.endsWith(".jpeg") || lower.endsWith(".webp");
    }

    @NonNull
    private static String stripExtension(@NonNull String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
