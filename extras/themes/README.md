# Theme switcher

Pick a wallpaper from `~/themes` with a keybind; the launcher's interface colours follow the wallpaper.

Install (in Termux, from this folder):

    bash install.sh

Then put images into `~/themes`, restart the launcher and press **Ctrl+Alt+T**.

- `theme` opens the picker (yazi), `theme next` cycles, `theme random` picks one.
- `theme fzf` / `theme blocks` are fallbacks if yazi is unavailable.
- Needs the fork's `pane.open float=true` for the floating window; without it the picker opens as a tile.
- Uses `launcherctl wallpaper set FILE`.
