#!/usr/bin/env bash
# Grants the Automation Tool what Android lets a computer grant, so the first
# launch has nothing left to ask for. Run it once per device, after installing
# the tool (and, ideally, the apps under test).
#
#   scripts/provision_device.sh [adb-serial] [--relax-play-protect]
#
# --relax-play-protect (off by default) also turns off the install-time package
# verification that adb-installed apps go through, on this device only. It does NOT
# switch Play Protect itself off: that switch lives in the Play Store app (see the
# message printed at the end). It lowers the device's protection, so use it on test
# phones only.
#
# It changes only the device you point it at: with no serial, adb must see
# exactly one. Do not run it on a device someone is using.
#
# What it can and cannot do:
#   * Accessibility service  - switched on through Settings.Secure, which adb may write.
#   * "Allow restricted settings" (Android 13+) - set with appops.
#   * The tool's notification permission - pm grant.
#   * The notification permission of the apps under test - pm grant. NOTE: the tool
#     clears each app's data at the start of every run, and that revokes runtime
#     permissions again, so this alone does not stop the in-run prompt. The current
#     Tradion and Ashika builds skip the prompt themselves when the tool launches
#     them; this grant only matters for use outside a run.
#   * Screen recording - NOT grantable. Android 14+ asks at the start of every run;
#     the tool taps "Share screen" itself once the service is on.
set -euo pipefail

TOOL=com.mobileautomation.tool
SERVICE="$TOOL/$TOOL.AutomationAccessibilityService"
TARGETS=(com.codifi.rmoney com.codifi.dhanush com.codifi.itrade)

ADB_ARGS=()
RELAX_PLAY_PROTECT=0
for arg in "$@"; do
  case "$arg" in
    --relax-play-protect) RELAX_PLAY_PROTECT=1 ;;
    *) ADB_ARGS=(-s "$arg") ;;
  esac
done
adb_shell() { adb ${ADB_ARGS[@]+"${ADB_ARGS[@]}"} shell "$@"; }

installed() { adb_shell pm list packages "$1" | grep -q "package:$1\$"; }

if ! installed "$TOOL"; then
  echo "The Automation Tool ($TOOL) is not installed on this device. Install it first." >&2
  exit 1
fi

echo "- Allow restricted settings (Android 13+; harmless on older versions)"
adb_shell cmd appops set "$TOOL" ACCESS_RESTRICTED_SETTINGS allow || true

echo "- Notification permission for the tool"
adb_shell pm grant "$TOOL" android.permission.POST_NOTIFICATIONS || true

echo "- Notification permission for the apps under test"
for pkg in "${TARGETS[@]}"; do
  if installed "$pkg"; then
    adb_shell pm grant "$pkg" android.permission.POST_NOTIFICATIONS || true
    echo "    $pkg granted"
  else
    echo "    $pkg not installed, skipped"
  fi
done

echo "- Accessibility service"
current="$(adb_shell settings get secure enabled_accessibility_services | tr -d '\r')"
case "$current" in
  *"$SERVICE"*) echo "    already enabled" ;;
  ""|null) adb_shell settings put secure enabled_accessibility_services "$SERVICE" ;;
  *) adb_shell settings put secure enabled_accessibility_services "$current:$SERVICE" ;;
esac
adb_shell settings put secure accessibility_enabled 1

if [[ "$RELAX_PLAY_PROTECT" == 1 ]]; then
  echo "- Relaxing install-time package verification (test devices only)"
  adb_shell settings put global verifier_verify_adb_installs 0 || true
  adb_shell settings put global package_verifier_enable 0 || true
  echo "    Play Protect itself is not changed. To turn it off: Play Store > profile picture >"
  echo "    Play Protect > settings (gear) > turn off 'Scan apps with Play Protect'."
fi

echo
echo "Done. Open the tool: its setup screen should show the accessibility service as enabled."
echo "Screen recording is still asked at the start of each run (Android 14+); the tool taps it for you."
