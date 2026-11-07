#!/data/data/com.termux/files/usr/bin/bash
# Real Windows VM on Android: QEMU launcher for Termux (no bundled Windows OS).
set -euo pipefail
umask 077

BASE="${WINVM_HOME:-$HOME/winvm}"
DISK="$BASE/windows.qcow2"
ISO="$BASE/windows.iso"
PID="$BASE/qemu.pid"
LOG="$BASE/qemu.log"
MON="$BASE/monitor.sock"
QEMU_BIN="${WINVM_QEMU_BIN:-qemu-system-x86_64}"
QEMU_IMG_BIN="${WINVM_QEMU_IMG_BIN:-qemu-img}"
mkdir -p "$BASE"

say() { printf '%s\n' "$*"; }
die() { say "ERROR: $*" >&2; exit 1; }
command_exists() { command -v "$1" >/dev/null 2>&1; }

running() {
  [[ -f "$PID" ]] || return 1
  local p
  p="$(cat "$PID" 2>/dev/null || true)"
  [[ "$p" =~ ^[0-9]+$ ]] || return 1
  kill -0 "$p" 2>/dev/null || return 1
  # Avoid treating an unrelated process with a recycled PID as our VM.
  if [[ -r "/proc/$p/cmdline" ]]; then
    tr '\0' ' ' < "/proc/$p/cmdline" | grep -Fq -- "$DISK" || return 1
  fi
}

ram_mb() {
  if [[ -n "${WINVM_RAM_MB:-}" ]]; then
    [[ "$WINVM_RAM_MB" =~ ^[0-9]+$ ]] || die 'WINVM_RAM_MB must be a number'
    (( WINVM_RAM_MB >= 1024 && WINVM_RAM_MB <= 8192 )) || die 'WINVM_RAM_MB must be between 1024 and 8192'
    echo "$WINVM_RAM_MB"; return
  fi
  local total=0
  total="$(awk '/MemTotal:/{printf "%d", $2/1024; exit}' /proc/meminfo 2>/dev/null || echo 0)"
  if [[ -n "$total" ]] && (( total > 0 && total < 6144 )); then
    echo 2048
  elif [[ -n "$total" ]] && (( total >= 6144 && total < 8192 )); then
    echo 3072
  else
    echo 4096
  fi
}

create_disk() {
  local gb="${1:-64}"
  [[ "$gb" =~ ^[0-9]+$ ]] || die 'Disk size must be a number of GB'
  (( gb >= 32 && gb <= 256 )) || die 'Disk size must be between 32 and 256 GB'
  command_exists "$QEMU_IMG_BIN" || die 'qemu-img not found. Run: pkg install qemu-utils'
  if [[ -e "$DISK" ]]; then
    say "Virtual disk already exists: $DISK"
    return
  fi
  "$QEMU_IMG_BIN" create -f qcow2 "$DISK" "${gb}G"
  say "Created sparse ${gb}GB virtual disk: $DISK"
  say 'Disk grows on Android storage as the guest writes data.'
}

import_iso() {
  [[ $# -eq 1 ]] || die 'Usage: winvm import-iso /sdcard/Download/Windows.iso'
  local source="$1"
  [[ -f "$source" ]] || die "ISO not found: $source"
  [[ -r "$source" ]] || die "ISO not readable: $source. Check Termux storage permission."
  if [[ "$source" == "$ISO" ]]; then say 'ISO already imported'; return; fi
  local tmp="$BASE/windows.iso.partial"
  rm -f "$tmp"
  cp "$source" "$tmp"
  mv -f "$tmp" "$ISO"
  say "ISO imported: $ISO"
}

start_vm() {
  local install="${1:-no}"
  command_exists "$QEMU_BIN" || die 'qemu-system-x86_64 not found. Run termux/install.sh.'
  [[ -f "$DISK" ]] || die "No virtual disk. Run: $0 init"
  if [[ "$install" == yes ]]; then [[ -f "$ISO" ]] || die "No ISO. Run: $0 import-iso /sdcard/Download/Windows.iso"; fi
  if running; then say "Windows VM is already running (PID $(cat "$PID"))"; return; fi
  rm -f "$PID" "$MON"
  local ram
  ram="$(ram_mb)"
  local args=(
    -name RealWindowsAndroid
    -machine pc
    -accel tcg,thread=multi
    -cpu max
    -smp 2
    -m "$ram"
    -drive "file=$DISK,format=qcow2,if=ide,cache=writeback"
    -netdev user,id=net0
    -device e1000,netdev=net0
    -vga std
    -display none
    -vnc 127.0.0.1:0
    -usb -device usb-tablet
    -rtc base=localtime
    -monitor "unix:$MON,server=on,wait=off"
    -boot order=c
  )
  if [[ "$install" == yes ]]; then
    args+=( -drive "file=$ISO,media=cdrom,readonly=on,if=ide" -boot order=dc )
  fi
  say "Launching actual x86_64 machine emulation with ${ram}MB guest RAM..."
  nohup "$QEMU_BIN" "${args[@]}" > "$LOG" 2>&1 < /dev/null &
  local p=$!
  echo "$p" > "$PID"
  sleep 2
  if ! running; then
    tail -n 35 "$LOG" >&2 || true
    rm -f "$PID"
    die 'QEMU exited. Check log above or run winvm logs.'
  fi
  say "VM running, PID $p. Screen: VNC 127.0.0.1:5900"
  say 'Guest networking: QEMU user-mode NAT (requires phone internet).'
  say 'Open the WinVM Android viewer, or any VNC client at 127.0.0.1:5900.'
}

stop_vm() {
  if ! running; then say 'No VM is currently running'; return; fi
  if [[ -S "$MON" ]] && command_exists python; then
    python - "$MON" <<'PY' || true
import socket,sys
s=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM)
s.settimeout(2)
try:
    s.connect(sys.argv[1])
    s.sendall(b'system_powerdown\n')
finally:
    s.close()
PY
    say 'Sent ACPI power button to Windows. Allow time for clean shutdown.'
  else
    say 'QEMU monitor unavailable. Shut Windows down from the Start menu.'
  fi
}

case "${1:-help}" in
  init) create_disk "${2:-64}" ;;
  import-iso) shift; import_iso "$@" ;;
  start)
    if [[ "${2:-}" == --install ]]; then start_vm yes; else start_vm no; fi ;;
  install) start_vm yes ;;
  stop) stop_vm ;;
  force-stop)
    if running; then kill "$(cat "$PID")" && say 'QEMU process stopped abruptly (possible data loss)'; else say 'No VM running'; fi ;;
  status)
    if running; then say "VM running PID $(cat "$PID") at 127.0.0.1:5900"; else say 'VM stopped'; fi
    [[ -f "$DISK" ]] && say "Disk: $DISK" || say 'Disk missing'
    [[ -f "$ISO" ]] && say "ISO: $ISO" || say 'ISO missing' ;;
  logs) [[ -e "$LOG" ]] && tail -n 100 "$LOG" || say 'No QEMU logs yet' ;;
  help|--help|-h)
    cat <<'HELP'
Real Windows VM for Android (real QEMU hardware emulation, NOT a desktop mockup).

winvm init [GB]                 Create a sparse Windows disk (default 64GB)
winvm import-iso PATH           Copy your legitimately obtained Windows ISO
winvm start --install           Boot Windows ISO to install Windows
winvm start                     Boot the installed Windows virtual disk
winvm stop                      Request clean Windows shutdown via ACPI
winvm force-stop                Kill QEMU (emergency only, data loss possible)
winvm status                    Report VM and image status
winvm logs                      Display recent QEMU diagnostics

Set WINVM_RAM_MB=2048/3072/4096 (1024..8192) to override guest RAM.
View the Windows screen at 127.0.0.1 port 5900 via the Android app or VNC.
QEMU user-mode NAT shares Android internet with the guest; not a VPN.
HELP
    ;;
  *) die "Unknown command: $1 (try help)" ;;
esac
