# The wall rework: Home pages, then the Workspace (spec, 2026-10-17)

Status: agreed with the developer on 2026-10-17 after a grilling session; not started.
Decision: ADR 0008. Words: `docs/GLOSSARY.md` › The wall and its chrome. Phase one only; §11 lists
what waits.

## 1. Why

The launcher is three bordered places on a pane wall, and the Display place is rarely used. The
rework makes Home a conventional launcher and makes the terminal, with the display folded in, a
Workspace beside it. Nothing about the terminal's own layers (session › window › pane) changes.

## 2. The wall

One horizontal row: Home page 1 … Home page n, then the Workspace's windows.

| From | Gesture | Goes to |
|---|---|---|
| A Home page | plain swipe anywhere, left or right | the next or previous Home page |
| The last Home page | swipe left | the first window, if the Workspace has one; otherwise it stops |
| A window | border drag (unchanged: side borders hold-then-drag, top and bottom air plain swipe) | the next or previous window |
| The first window | border drag right | the last Home page |

Parallax runs across the whole row. The seam is continuous but never silent: the chrome arrives
with the first window (status bar, extra keys, corner tabs, borders), so the user sees which half
they are in.

## 3. Home

- **Content**: today's widget grid, already multi-page (`LauncherWidgetRepository`). Long press on an
  empty cell: Add widget, Wallpaper, Terminal. Phase one adds nothing else to the pages.
- **No borders, no launcher status bar.** Android's system bar shows as on any launcher. The status
  swipe does not exist on Home.
- **Dock**: pinned-apps row and A–Z index, no extra keys. The drawer opens from it as today.
- **Keyboard**: the keyboard swipe works on Home and Home remembers its own keyboard state,
  separately from the Workspace. With the keyboard up: a bound key fires its binding (apps,
  contacts, actions); the palette shortcut opens the command palette; any other key opens the
  drawer's search with that key already typed. `docs/en/Widgets.md` › The keyboard on Home is
  rewritten by this.
- **The Terminal entry**: a launcher-provided app entry, listed in the drawer beside Linux Apps and
  pinned to the dock at migration. Tap: go to the Workspace at the window you left, opening one if
  there is none. Long press: New window. Home's long-press Terminal item always opens a new window.
- **Home layout**: a third saved layout variant beside normal and minimal, edited by the same Layout
  editor and the same put-away machinery, with the element set reduced to the dock's rows (pinned
  apps, A–Z) and the keyboard. Seeded from the normal layout with the status bar, extra keys and
  borders removed. Style, Corners, Margin and Minimal mode do not apply to Home.

## 4. The Workspace

- **Windows in a row**: the current session's windows in their order, then the display windows.
  Switching sessions (drawer, Ctrl+Alt+Shift+C) swaps the window part; the display windows stay.
- **A window** is what it is today: a terminal window with its split tree, or (new) a display
  window. Ctrl+Alt+V and Ctrl+Alt+H split inside a window; Ctrl+Alt+C opens a window; Ctrl+Alt+Shift+C
  opens a session. Nothing about panes changes.
- **Insertion**: a new window (Ctrl+Alt+C, a Terminal=true Linux app, Terminal from Home) lands
  right after the current window. From Home that means first. No drag-to-reorder in phase one.
- **Switching**: the border drag, the window chips in the status bar (display windows are chips
  too), the Ctrl+Alt shortcuts and the drawer. The screen glyph jumps to the first display window,
  or starts the display if none is up.
- **Chrome and layout** are today's, unchanged: the Workspace layout (normal and minimal), Style,
  Corners, Margin, status bar, dock with extra keys, corner tabs, keyboard swipe, status swipe.
  Display windows wear the same chrome as terminal windows.
- **Lifetime**: the Workspace exists while it has at least one window (terminal or display).
  Closing the last one (last shell exits, display stopped) slides Home back in. There is no empty
  Workspace.

## 5. Display

- One display window per X display, shown whole; the display's own window manager (openbox, or a
  desktop session's) manages the toplevels. Per-toplevel windows wait for a rootless server (§11).
- **Starting**: on demand. A Linux app tapped in the drawer starts the server if needed, runs the
  app and opens or raises the display window (`X11LinuxAppRunner`, as today). A **Display** entry
  under Linux Apps opens the bare display, replacing the Start display button. The Start command
  setting stays.
- **The Display switch and the usage mode retire.** Linux apps list whenever Show Linux apps in
  drawer is on. Nothing about X11 is a mode.
- **Stopping**: long-press Stop as today, or the display's last client leaving if the user has that
  on; the display window then leaves the row.
- Terminal=true Linux apps open a new terminal window after the current one, not a pane.
- `active/display-fullscreen/PARKED.md` is answered: full-screen display is a display window in
  minimal mode. Delete it when this ships.

## 6. The sessions drawer

Today: Live and Saved tabs, sessions with windows and panes nested, a + in the header, a one-row
footer Save as workspace… It gains:

- A **Displays** group beside the sessions: one row per display window, with its X toplevels
  (EWMH names, `X11WindowList`) nested; a tap on a toplevel raises it on that display.
- A **Home** row in the footer, above the save row, that goes to Home page 1.
- The rename: Save as workspace… becomes Save sessions…, the Saved tab lists Saved sessions.
  `~/.termux/workspaces/` and the `launcherctl` verbs keep their names.

## 7. Entering, leaving, resuming

| Event | Result |
|---|---|
| Terminal entry tapped | Workspace at the window last shown; a new window if none |
| Long-press › Terminal, or Terminal long-press › New window | a new window, right after the current one, Workspace slides in |
| Linux app tapped | display window opened or raised (terminal window for Terminal=true) |
| Home press while a window is in front | Home page 1 |
| Home press while a Home page is in front | Home page 1 (overlays close, as today) |
| Home press from another app | the page that was in front when the launcher was left, Home page or window, with the keyboard state of that half |
| Corner tab › Home, drawer › Home | Home page 1 |
| Last window closed | the last Home page slides in |
| Cold start, process killed in the background | Start in: Home (default) shows page 1; Workspace opens one fresh shell window. Saved sessions restore as they do today; nothing else rebuilds the row |

## 8. Settings and migration

- **Start in**: Home | Workspace. New, under the launcher's settings.
- **Gone**: Usage mode (Terminal alone, with Home, with Display); the Display switch.
- **Migration, once**: Terminal alone → Start in = Workspace; the other two → Home. Display on →
  nothing (on demand now). The normal and minimal layouts become the Workspace layouts unchanged;
  the Home layout is seeded as in §3. The Terminal entry is pinned to the dock. Per-place state
  (`PaneWallPage` names in saved instance state, place-scoped keys left from before ADR 0003) is
  dropped. No toggle back to the old wall.

## 9. Code that this touches, by name

`PaneWallController`, `PaneWallLayout`, `PaneWallPage`, `PaneWallPolicy` (the row and its policy;
`homePage()` returns TERMINAL today and the class comment calls Terminal the home screen);
`LauncherUseCaseMode`, `X11DisplaySwitch`, `SettingsActivity` and
`TermuxStylePreferencesFragment` (mode and switch); `PlaceLayoutStore` (the Home variant);
`SessionsDrawerView` (Displays group, Home row, rename); `AppDrawerItem` and `LauncherAppEntry`
(the Terminal entry and the Display entry); `TermuxActivity.onNewIntent` (home press lands on Home
page 1); `LinuxAppCatalog`, `X11LinuxAppRunner` (unchanged, callers change).

## 10. Words

See the glossary. In short: **wall** (the row), **page** (one stop), **Home** and **Home page**,
**Workspace**, **window** (terminal or **display window**), **Terminal** (the entry), **Start in**,
**Saved sessions**, **Linux app**. Retired: place, pane wall, usage mode, Display place, Create Pane.

## 11. Out of scope (phase two and later)

- The icon grid on Home pages: app icons, folders, drag and drop between pages. Home stays a widget
  canvas with a dock and a drawer in phase one.
- Drag-to-reorder of windows, and Niri's consume/expel of panes into and out of columns.
- Per-toplevel X11 windows in the row (needs a rootless server).
- Rebuilding the row after a background kill beyond what Saved sessions restore.
- A terminal or display window placed among the Home pages.

## 12. Docs to rewrite when it ships

`docs/en/Launcher_Usage.md`, `docs/en/Widgets.md` (keyboard on Home), `docs/en/X11_Display.md`
(no Display place, no switch, the Display entry), `docs/en/Panes_Windows_And_Sessions.md` (the row,
Displays group, Saved sessions), `docs/en/Home_Screen_And_Apps.md`, `docs/en/Launcher_Settings.md`
(Start in; usage mode gone), `project-docs/reference/launcher/gestures-and-style.md` (Home's plain
swipe, the seam), `project-docs/reference/launcher/pane-wall-x11-study.md` (point at ADR 0008).

## 13. Device checks owed

On the emulator first, pong when the developer asks: the seam both ways; the home press from a
window, from a Home page and from another app; last window closed; Start in = Workspace cold start;
a Linux app tap with no display up; Ctrl+Alt+C from Home's Terminal item; keyboard state remembered
per half; the Home layout in the Layout editor; migration from each of the three usage modes.
