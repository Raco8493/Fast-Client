#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
printf '\n=== WINDOWS VM: INSTALL QEMU ===\n'
pkg update -y
pkg install -y qemu-system-x86-64-headless qemu-utils
mkdir -p "$HOME/win10-qemu"
if [ ! -d "$HOME/storage/downloads" ]; then
  echo 'Run termux-setup-storage and grant storage permission when prompted.'
  termux-setup-storage || true
fi
printf '\nQEMU installed. Use the Android app to import an ISO, or copy Windows10.iso into:\n'
printf '  ~/win10-qemu/windows10.iso\n'
printf 'Or: ~/storage/downloads/Win10-VM/Windows10.iso\n'
