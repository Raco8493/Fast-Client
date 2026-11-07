#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
PIDFILE="$HOME/win10-qemu/qemu.pid"
if [ ! -f "$PIDFILE" ]; then echo 'No VM PID file found.'; exit 0; fi
pid="$(cat "$PIDFILE")"
case "$pid" in ''|*[!0-9]*) echo 'Invalid PID'; exit 2;; esac
if [ -r "/proc/$pid/cmdline" ] && tr '\0' ' ' < "/proc/$pid/cmdline" | grep -q 'qemu-system-x86_64'; then
  kill -TERM "$pid"
  echo "Sent shutdown to QEMU ($pid). Guest may lose unsaved work."
else
  echo 'VM process is not running.'
fi
rm -f "$PIDFILE"
