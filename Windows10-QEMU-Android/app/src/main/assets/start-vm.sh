#!/data/data/com.termux/files/usr/bin/bash
# Runs an actual x86_64 PC in QEMU TCG. Windows is provided by the user.
set -euo pipefail
RAM="${1:-3072}"
CORES="${2:-2}"
DISK_GB="${3:-48}"
case "$RAM" in 2048|3072|4096) ;; *) echo 'Invalid RAM configuration'; exit 2;; esac
case "$CORES" in 1|2|4) ;; *) echo 'Invalid CPU configuration'; exit 2;; esac
case "$DISK_GB" in 32|48|64) ;; *) echo 'Invalid disk configuration'; exit 2;; esac
command -v qemu-system-x86_64 >/dev/null || { echo 'Install QEMU from the app first.'; exit 3; }
command -v qemu-img >/dev/null || { echo 'Install qemu-utils first.'; exit 3; }
ROOT="$HOME/win10-qemu"
mkdir -p "$ROOT"
DISK="$ROOT/windows10.qcow2"
PIDFILE="$ROOT/qemu.pid"
if [ -f "$PIDFILE" ]; then
  old="$(cat "$PIDFILE")"
  if [ -r "/proc/$old/cmdline" ] && tr '\0' ' ' < "/proc/$old/cmdline" | grep -q 'qemu-system-x86_64'; then
    echo "VM is already running (PID $old), open the viewer."
    exit 0
  fi
fi
if [ ! -f "$DISK" ]; then
  echo "Creating sparse virtual hard disk (${DISK_GB} GB max)..."
  qemu-img create -f qcow2 "$DISK" "${DISK_GB}G"
fi
ISO="$ROOT/windows10.iso"
if [ ! -f "$ISO" ]; then ISO="$HOME/storage/downloads/Win10-VM/Windows10.iso"; fi
CDROM=()
if [ -f "$ISO" ]; then
  echo "Windows installer ISO: $ISO"
  CDROM=(-drive "file=$ISO,media=cdrom,if=ide,readonly=on")
else
  echo 'No installer ISO: booting existing virtual disk.'
  echo 'For a new installation import Windows10.iso in the app first.'
fi
printf 'Launching Windows VM with %s MB RAM, %s CPU(s).\n' "$RAM" "$CORES"
printf 'VNC address: 127.0.0.1:5901 | Network: QEMU NAT (e1000)\n'
echo "$$" > "$PIDFILE"
# PID remains the same after exec; stop script checks process identity before killing.
exec qemu-system-x86_64 \
  -machine pc,accel=tcg -cpu qemu64 -smp "$CORES" -m "$RAM" \
  -drive "file=$DISK,format=qcow2,if=ide,cache=writeback" \
  "${CDROM[@]}" -boot order=dc,menu=on \
  -vga std -usb -device usb-tablet \
  -netdev user,id=nat0 -device e1000,netdev=nat0 \
  -rtc base=localtime -display none -vnc 127.0.0.1:1 \
  -monitor none
