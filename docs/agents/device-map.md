# Device map

How a test agent reaches each part of the launcher over adb, so a device check spends its time
checking rather than exploring. Commands use the `ad` tool of the host's android-control skill;
plain `adb -s <serial>` works the same. Add what you had to find out; fix what turned out wrong.

## The app

- Package `com.termux` on the Termux edition (`com.termux.launcher.nix`, `io.vaj.tl` on the
  others). Main activity `.app.TermuxActivity`; it is also the HOME activity.
- Build for the emulator: `./gradlew :app:assembleDebug`, then install
  `app/build/outputs/apk/debug/termux-app_apt-android-7-debug_x86_64.apk` (`ad install` keeps
  data). Pong takes release builds only; see AGENTS.md.
- After installing on the emulator the launcher becomes home. Before handing the emulator over:
  `ad shell cmd package set-home-activity com.google.android.apps.nexuslauncher/.NexusLauncherActivity`.
- Owed checks: `project-docs/device-checks.md` (Agent items can run here; Developer items need
  the developer's hand).

## Running commands inside the app

Debug builds allow `run-as`. With this prelude, `ad runas` runs anything in the terminal's own
environment and prints the output — no typing into the terminal, no reading files back:

```bash
export AD_RUNAS_PRELUDE='P=/data/data/com.termux/files; export HOME=$P/home PREFIX=$P/usr TMPDIR=$P/usr/tmp PATH=$P/usr/bin:/system/bin LD_PRELOAD=$P/usr/lib/libtermux-exec-ld-preload.so TERMUX_EXEC__SYSTEM_LINKER_EXEC__MODE=disable; cd $HOME'
ad -s emu runas com.termux 'launcherctl pane list'
```

- `launcherctl` (`launcherctl --help`, `docs/en/LauncherCtl.md`) changes the live UI from there:
  `pane open|resize|close`, `window open|focus|close`, `keyboard show|hide`,
  `wallpaper set FILE`, `launch <app>`, `notify`, `toast`, `clipboard copy|paste`.
- To see a command run in the UI, open your own pane and drive it: `id=$(launcherctl pane open
  --print-id --no-focus)`, `launcherctl pane write "$id" --enter 'cmd'`, `launcherctl pane read
  "$id" --lines 20`. `pane write`/`read` refuse panes you did not open (`not_owned`).
- Typing into the user's own pane needs `ad type` one word at a time with `ad key SPACE`
  (e.g. `seq -s w 1 900` fills the pane with long lines).
- `launcherctl keyboard show|hide` raises or drops the in-app keyboard without a tap.
- A fresh `pre` debug install starts Docked, light, font scale 1.0, and opens with a Welcome sheet
  (Skip the tour), a media-permission dialog and a "You're set" card (Start).
- **The Look page's Edge sliders (Bend, Edge width, Edge light, Shine, Dispersion) stay disabled
  until a wallpaper is set from the launcher.** Push a PNG to `/data/local/tmp`, `chmod 644`, then
  `ad runas com.termux 'cp /data/local/tmp/x.png $HOME/x.png; launcherctl wallpaper set $HOME/x.png'`
  (an `/sdcard` path is refused). There is no unset: the emulator keeps that wallpaper.
- `launcherctl keyboard hide` can answer "inaccessible" right after the editor closes; retry.
- Prefs live in `shared_prefs/com.termux_preferences.xml`; edit them **before** a force-stop,
  never after (AGENTS.md, "It is the home screen").

## Places

| Place | How to get there | Useful ids / text |
|---|---|---|
| Settings | `ad shell am start -n com.termux/.app.activities.SettingsActivity` | `settings_search_input` finds any setting by name; rows by `title` text (Appearance, Apps, …) |
| Appearance editor | long-press the top-left corner of the pane (about 40,262 on the emulator at 420 dpi), then tap Appearance within about a second (`ad find Appearance`) | pages `appearance_page_mode_wallpaper`, `_look`, `_icon_pack` (Layout has no id: it is the grid icon in the top pill, about 655,218); styles `appearance_editor_style_docked`, `_floating`; Done `appearance_page_done`. Tap by id: the tab row shifts per page. Changes apply only on Done. Look page: tap a Look by its label (Clear, Mist, Tint, Solid, Custom), then the Surface/Edge toggle by text. Sliders (Shine, Blur, …) are vertical and fill from the bottom; a drag must start inside the slider (y about 1980–2295): `ad swipe 745 2270 745 1850 800` takes Shine to 100%, `2000 → 2310` to 0. Tapping a status pill in the preview swaps the sheet to Status bar controls; Back with changes asks Keep editing / Discard / Save |
| Icon size, Icons per page | Appearance editor → Layout page | value shows only on screen (not in the accessibility tree); read it from a `zoom` crop |
| Command palette | tap the terminal so the keyboard is up, then swipe up on its space bar: `ad swipe 540 2250 540 2000 250` | type to filter, `ad key DPAD_DOWN` moves the selection; chip strip kbd, pick, split, font; app chips need usage history; a theme change closes it |
| Icon pack | Appearance editor → `appearance_page_mode_icon_pack` (the editor reopens on its last page) | tiles `icon_pack_tile_label` (tap the tile art if the label does not take); "Pinned app icons only" is `icon_pack_pinned_only`, on by default. MBE Style (`me.iacn.mbestyle_27.apk`, F-Droid) installs as a PNG pack |
| AI models | Settings → Model centre; Export/Import use the system folder picker (USE THIS FOLDER, then ALLOW), export lands in `/sdcard/Documents/Termux Launcher models` | Get models goes stale after a `tai delete`: leave and re-enter the page |
| Keyboard layouts | Settings → Keyboard → Layouts (list at about 487,1242) → Layouts (487,671); a tap on a catalogue row slides its strip open, `ad find` by description works ("Add Colemak to the cycle"); cycle to the next layout by swiping down on the space bar: `ad swipe 540 2200 540 2330 250` | — |
| Pinned apps | long-press the dock's empty area (`ad swipe x y x y 1200`) → "Edit pinned apps": tap names under MOST USED, then Done; remove with "Remove from pinned apps" | a letter in the dock strip shows that letter's apps in the dock; the letters shift when an app is installed. The dock's grid glyph (about 810,1700) opens a split pane, not the drawer; a pane opened that way is not `launcherctl`'s to close (`exit` in it) |
| App drawer, A–Z | not yet mapped | `launcher/drawer/`, `AzPullTabLayer` |
| Split panes, window chips | `launcherctl pane open` / `window open -- CMD` (a command is required) is faster than the UI; `window close <id>` | window chips switch windows; on Docked, the grid glyph on the dock row opens a split |
| Sessions sidebar (legacy) | tap the session indicator `terminal_sessions_indicator`, the number box left of the chips | after it closes, focus stays on the indicator: tap the terminal before typing |
| Widgets page | not yet mapped | `WidgetPaneView` |
| Voice pill | needs a microphone: pong only | — |

## Device quirks

- The emulator renders on the CPU: judge looks there, never motion or frame time.
- Injected swipes release with zero velocity; read the end state, not the fling.
- `ad ui` does not see dialog windows; screenshot and tap coordinates.
- `ad ui` truncates the terminal's text: read the terminal from a `zoom` crop, or run the command
  through `ad runas` instead.
- Force-stop the app after a density or font-scale change.
- Settings reopens on the page it was left on: Back to the root before searching (`settings_search_input`).
- Voice: with no speech model the dictate action opens Google's dictation dialog, not the card, and
  the emulator has no microphone; the card's checks run on pong.
- A contact with a photo: grant `com.termux` READ_CONTACTS, `content insert` into
  `content://com.android.contacts/raw_contacts` and `/data`, push a PNG to `/sdcard/Pictures` and
  broadcast MEDIA_SCANNER_SCAN_FILE, then `am start -a android.intent.action.EDIT -d
  content://com.android.contacts/contacts/lookup/<key>/<id>` → Add picture › Gallery › Save.
- `ad runas` needs `AD_RUNAS_PRELUDE` exported in the same shell call.
- Dock zoom crop: `ad zoom 0 1455 1080 1650`.
- Never `pkill -f` a pattern that appears in your own command line: it kills your shell.
- The only AVD is tl16 (API 36); there is none for API 29/30.
