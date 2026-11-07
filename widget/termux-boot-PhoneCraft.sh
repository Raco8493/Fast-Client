#!/data/data/com.termux/files/usr/bin/bash
# Optional auto-launch from Termux:Boot, if Android actually allows background start.
termux-wake-lock 2>/dev/null || true
sleep 5
exec "$HOME/phonecraft-start"
