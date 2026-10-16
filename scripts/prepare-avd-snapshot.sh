#!/usr/bin/env bash
# Let a restored AVD load its cached boot snapshot. Cold-boot flags are removed
# so the emulator runner can reuse the snapshot saved on the previous cache miss.
set -euo pipefail

shopt -s nullglob
for avd in "${HOME}/.android/avd/"*.avd; do
  config="${avd}/config.ini"
  if [[ ! -f "${config}" ]]; then
    continue
  fi
  if [[ -d "${avd}/snapshots/default_boot" ]]; then
    if grep -q '^snapshot.present=' "${config}"; then
      sed -i 's/^snapshot.present=.*/snapshot.present=true/' "${config}"
    else
      printf '\nsnapshot.present=true\n' >> "${config}"
    fi
  fi
  grep -v '^fastboot.forceColdBoot=' "${config}" > "${config}.tmp"
  mv "${config}.tmp" "${config}"
done
