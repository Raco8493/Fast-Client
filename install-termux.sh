#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail
printf '\n=== PhoneCraft: Minecraft hosting on Android ===\n'
if [ "$(uname -o 2>/dev/null || true)" != "Android" ] && [ -z "${PREFIX:-}" ]; then
  echo 'This installer is intended for Termux on an Android phone.'
  exit 1
fi
pkg update -y
pkg install -y python openjdk-25 curl
if ! java -version 2>&1 | grep -qE 'version "(25|2[6-9]|[3-9][0-9])'; then
  echo 'Warning: Paper Minecraft 26.3 requires Java 25 or newer.'
  java -version
fi
if command -v termux-wake-lock >/dev/null 2>&1; then
  termux-wake-lock || true
fi
DIR="$(cd "$(dirname "$0")" && pwd)"
cat > "$HOME/phonecraft-start" <<EOF
#!/data/data/com.termux/files/usr/bin/bash
cd '$(printf '%s' "$DIR" | sed "s/'/'\\\\''/g")'
if command -v termux-wake-lock >/dev/null 2>&1; then termux-wake-lock || true; fi
python phonecraft.py
EOF
chmod +x "$HOME/phonecraft-start"
printf '\nDone! Type: ~/phonecraft-start\nThen open http://127.0.0.1:8765 on this phone.\n'
printf 'Note: turn OFF Android battery optimization for Termux.\n'
