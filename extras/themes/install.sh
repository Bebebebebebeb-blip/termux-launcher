#!/data/data/com.termux/files/usr/bin/bash
# Installs the theme switcher: scripts into ~/.local/bin, a themes folder, and the Ctrl+Alt+T bind.
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
CONF="$HOME/.termux/termux-launcher-bindings.conf"
BIND='map --label "Theme picker" ctrl+alt+t pane.open command=theme-popup float=true title=themes'

mkdir -p "$HOME/.local/bin" "$HOME/themes" "$HOME/.termux"
cp "$DIR/theme" "$DIR/theme-popup" "$HOME/.local/bin/"
chmod +x "$HOME/.local/bin/theme" "$HOME/.local/bin/theme-popup"
pkg install -y yazi

if ! grep -qF 'command=theme-popup' "$CONF" 2>/dev/null; then
  printf '%s\n' "$BIND" >> "$CONF"
fi

echo "Done. Put wallpapers into ~/themes, restart the launcher, press Ctrl+Alt+T."
