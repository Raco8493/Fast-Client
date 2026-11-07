#!/data/data/com.termux/files/usr/bin/bash
# Optional Termux:Widget shortcut. Copy into ~/.shortcuts/ and make executable.
if command -v termux-wake-lock >/dev/null 2>&1; then termux-wake-lock || true; fi
exec "$HOME/phonecraft-start"
