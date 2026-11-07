#!/data/data/com.termux/files/usr/bin/bash
# Run this once inside Termux from F-Droid or official Termux GitHub releases.
set -euo pipefail
[[ -d "/data/data/com.termux/files/usr" ]] || { echo 'ERROR: Run in the official Termux app on Android.' >&2; exit 1; }
case "$(uname -m)" in aarch64|x86_64) ;; *) echo 'ERROR: This build supports only 64-bit Android devices' >&2; exit 1;; esac
pkg update -y
pkg install -y qemu-system-x86-64-headless qemu-utils python coreutils
if ! command -v qemu-system-x86_64 >/dev/null; then
  echo 'ERROR: QEMU executable missing even after installation. Try pkg upgrade or change Termux mirror.' >&2
  exit 1
fi
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p "$HOME/winvm"
install -m 700 "$ROOT/termux/winvm.sh" "$HOME/winvm/winvm.sh"
mkdir -p "$HOME/.termux"
PROP="$HOME/.termux/termux.properties"
# Allow only if the user elects to use the optional controller Android app.
if ! grep -q '^allow-external-apps=true$' "$PROP" 2>/dev/null; then
  printf '\nTo allow optional WinVM controller, set allow-external-apps=true in %s\n' "$PROP"
  printf 'This permits apps with the special Termux permission to execute Termux commands.\n'
fi
cat <<'MSG'

QEMU installed. Run next:
  termux-setup-storage
  ~/winvm/winvm.sh init
  ~/winvm/winvm.sh import-iso ~/storage/downloads/Windows.iso
  ~/winvm/winvm.sh start --install

Point the included Android viewer at 127.0.0.1:5900.
No Windows ISO or license is included.
MSG
