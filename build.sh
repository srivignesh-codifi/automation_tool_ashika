#!/bin/bash
set -e

# ---------------------------------------------------------------------------
# build.sh — Automation Tool build script
#
# Modeled on tradion_mobile/build.sh, for this Android-only app. The version
# lives in pubspec.yaml ("version: 1.0.0+1") and is bumped there.
#
# Usage:
#   ./build.sh                                # release APK (the default)
#   ./build.sh apk                            # release APK
#   ./build.sh apk split                      # release APKs split per ABI
#   ./build.sh appbundle                      # Android App Bundle
#   ./build.sh apk debug                      # debug APK (version still bumped)
#   ./build.sh apk --no-bump                  # build the current version, no bump,
#                                             #   no release note, no commit
#   ./build.sh apk --no-gitc                  # build + bump, no commit prompt
#   ./build.sh apk --no-rename                # keep Flutter's file name
#   ./build.sh apk --no-reveal                # do not open Finder on the result
#   ./build.sh apk --no-note                  # no release note
#   ./build.sh apk --no-clean                 # skip `flutter clean` (faster)
#   ./build.sh apk --skip-checks              # skip `flutter analyze` and `flutter test`
#   ./build.sh apk gitc "what changed"        # build + commit with a message
#   ./build.sh apk gitc "msg" --commit-all    # commit every change, not only the version files
#   ./build.sh apk -- --obfuscate ...         # everything after -- goes to `flutter build`
#
# Before anything is changed, `flutter analyze` and `flutter test` must pass
# (unless --skip-checks), so a broken tree never consumes a version number. If
# the build itself fails, pubspec.yaml and lib/build_info.dart are put back.
# ---------------------------------------------------------------------------

APP_NAME="AutomationTool"
PUBSPEC="pubspec.yaml"
BUILD_INFO_FILE="lib/build_info.dart"
APK_OUTPUT_DIR="build/app/outputs/flutter-apk"
AAB_OUTPUT_DIR="build/app/outputs/bundle/release"
RELEASE_NOTE_DIR="release_notes"
NOTE_PATHS=(lib android/app/src test scripts build.sh)

SKIP_BUMP=false
SPLIT_APK=false
NO_GITC=false
NO_RENAME=false
NO_REVEAL=false
NO_NOTE=false
NO_CLEAN=false
SKIP_CHECKS=false
COMMIT_ALL=false
DEBUG_BUILD=false
GIT_MESSAGE=""

is_platform() {
  case "$1" in apk|appbundle) return 0 ;; *) return 1 ;; esac
}

# ---------------------------------------------------------------------------
# Parse platforms and flags
# ---------------------------------------------------------------------------
PLATFORMS=()
ALL_ARGS=("$@")
EXTRA_ARGS=()
i=0
while [ $i -lt ${#ALL_ARGS[@]} ]; do
  arg="${ALL_ARGS[$i]}"
  if is_platform "$arg"; then
    PLATFORMS+=("$arg")
  elif [ "$arg" == "split" ];         then SPLIT_APK=true
  elif [ "$arg" == "debug" ];         then DEBUG_BUILD=true
  elif [ "$arg" == "--no-bump" ];     then SKIP_BUMP=true
  elif [ "$arg" == "--no-gitc" ];     then NO_GITC=true
  elif [ "$arg" == "--no-rename" ];   then NO_RENAME=true
  elif [ "$arg" == "--no-reveal" ];   then NO_REVEAL=true
  elif [ "$arg" == "--no-note" ];     then NO_NOTE=true
  elif [ "$arg" == "--no-clean" ];    then NO_CLEAN=true
  elif [ "$arg" == "--skip-checks" ]; then SKIP_CHECKS=true
  elif [ "$arg" == "--commit-all" ];  then COMMIT_ALL=true
  elif [ "$arg" == "gitc" ]; then
    i=$((i + 1))
    GIT_MESSAGE="${ALL_ARGS[$i]}"
  elif [ "$arg" == "--" ]; then
    i=$((i + 1))
    while [ $i -lt ${#ALL_ARGS[@]} ]; do
      EXTRA_ARGS+=("${ALL_ARGS[$i]}")
      i=$((i + 1))
    done
    break
  else
    EXTRA_ARGS+=("$arg")
  fi
  i=$((i + 1))
done

if [ ${#PLATFORMS[@]} -eq 0 ]; then
  PLATFORMS=("apk")
fi

if [ ! -f "$PUBSPEC" ]; then
  echo "Error: $PUBSPEC not found. Run from the project root."
  exit 1
fi

if [ "$DEBUG_BUILD" = true ]; then
  MODE_FLAG="--debug"
  MODE_NAME="debug"
else
  MODE_FLAG="--release"
  MODE_NAME="release"
fi

# ---------------------------------------------------------------------------
# Read the version from pubspec.yaml
# ---------------------------------------------------------------------------
CURRENT_FULL=$(grep -E '^version:' "$PUBSPEC" | head -1 | sed -E 's/^version:[[:space:]]*//' | tr -d '[:space:]')
ANDROID_VERSION="${CURRENT_FULL%%+*}"
if [[ "$CURRENT_FULL" == *"+"* ]]; then ANDROID_BUILD="${CURRENT_FULL##*+}"; else ANDROID_BUILD="1"; fi

if ! [[ "$ANDROID_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || ! [[ "$ANDROID_BUILD" =~ ^[0-9]+$ ]]; then
  echo "Error: could not read a version like 1.0.0+1 from $PUBSPEC (found '$CURRENT_FULL')."
  exit 1
fi

echo "Current version:"
echo "  $APP_NAME : $ANDROID_VERSION+$ANDROID_BUILD"

# ---------------------------------------------------------------------------
# Quality gate: nothing is changed until the tree is healthy
# ---------------------------------------------------------------------------
echo "Running flutter pub get..."
flutter pub get
if [ "$SKIP_CHECKS" = false ]; then
  echo "Running flutter analyze..."
  flutter analyze
  echo "Running flutter test..."
  flutter test
else
  echo "Skipping analyze and test (--skip-checks)"
fi

# ---------------------------------------------------------------------------
# Save old files — rollback on any build failure
# ---------------------------------------------------------------------------
OLD_PUBSPEC=$(cat "$PUBSPEC")
HAD_BUILD_INFO=false
OLD_BUILD_INFO=""
if [ -f "$BUILD_INFO_FILE" ]; then
  HAD_BUILD_INFO=true
  OLD_BUILD_INFO=$(cat "$BUILD_INFO_FILE")
fi

rollback() {
  echo ""
  echo "Build failed — reverting $PUBSPEC and $BUILD_INFO_FILE..."
  printf '%s\n' "$OLD_PUBSPEC" > "$PUBSPEC"
  if [ "$HAD_BUILD_INFO" = true ]; then
    printf '%s\n' "$OLD_BUILD_INFO" > "$BUILD_INFO_FILE"
  else
    rm -f "$BUILD_INFO_FILE"
  fi
}
trap rollback ERR

# ---------------------------------------------------------------------------
# Bump the version (patch + build number)
# ---------------------------------------------------------------------------
NEW_ANDROID_VERSION="$ANDROID_VERSION"
NEW_ANDROID_BUILD="$ANDROID_BUILD"

if [ "$SKIP_BUMP" = false ]; then
  MAJOR=$(echo "$ANDROID_VERSION" | cut -d'.' -f1)
  MINOR=$(echo "$ANDROID_VERSION" | cut -d'.' -f2)
  PATCH=$(echo "$ANDROID_VERSION" | cut -d'.' -f3)
  NEW_ANDROID_VERSION="${MAJOR}.${MINOR}.$(( 10#${PATCH} + 1 ))"
  NEW_ANDROID_BUILD=$(( 10#${ANDROID_BUILD} + 1 ))
  echo "Bump: $ANDROID_VERSION+$ANDROID_BUILD  →  $NEW_ANDROID_VERSION+$NEW_ANDROID_BUILD"

  awk -v v="${NEW_ANDROID_VERSION}+${NEW_ANDROID_BUILD}" \
    '/^version:/ && !done { print "version: " v; done=1; next } { print }' \
    "$PUBSPEC" > "$PUBSPEC.tmp"
  mv "$PUBSPEC.tmp" "$PUBSPEC"
else
  echo "Skipping version bump (--no-bump)"
fi

# ---------------------------------------------------------------------------
# Generate build_info.dart (shown at the foot of the dashboard)
# ---------------------------------------------------------------------------
BUILD_DATE=$(date '+%Y-%m-%d')
BUILD_DATETIME=$(date '+%Y-%m-%d %H:%M:%S')
FILE_DATETIME=$(date '+%d%m%Y_%H%M')

if [ "$SKIP_BUMP" = false ]; then
  cat > "$BUILD_INFO_FILE" <<EOF
// Auto-generated by build.sh — do not edit manually.
class BuildInfo {
  static const String version = '$NEW_ANDROID_VERSION';
  static const String buildNumber = '$NEW_ANDROID_BUILD';
  static const String fullVersion = '$NEW_ANDROID_VERSION+$NEW_ANDROID_BUILD';

  static const String buildDate = '$BUILD_DATE';
  static const String buildDateTime = '$BUILD_DATETIME';
}
EOF
  echo "Generated $BUILD_INFO_FILE"
fi

# ---------------------------------------------------------------------------
# Pre-build clean
# ---------------------------------------------------------------------------
if [ "$NO_CLEAN" = false ]; then
  echo "Running flutter clean..."
  flutter clean
  echo "Running flutter pub get..."
  flutter pub get
fi

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
reveal_path() {
  local target="$1"
  if [[ "$OSTYPE" == "darwin"* ]]; then
    open -R "$target"
  else
    explorer /select,"$(cygpath -w "$target")" 2>/dev/null || true
  fi
}

ARTIFACTS=()

# rename_artifact <built file> <suffix, may be empty> <extension>
rename_artifact() {
  local src="$1" suffix="$2" ext="$3"
  local mode_part=""
  [ "$DEBUG_BUILD" = true ] && mode_part="_debug"
  local dest
  dest="$(dirname "$src")/${APP_NAME}_v${NEW_ANDROID_VERSION}+${NEW_ANDROID_BUILD}${mode_part}${suffix}_${FILE_DATETIME}.${ext}"
  mv "$src" "$dest"
  echo "Renamed → $(basename "$dest")"
  ARTIFACTS+=("$dest")
}

# ---------------------------------------------------------------------------
# Build each platform sequentially
# ---------------------------------------------------------------------------
VERSION_ARGS=(--build-name="$NEW_ANDROID_VERSION" --build-number="$NEW_ANDROID_BUILD")
DISPLAY_VERSIONS=()

for PLATFORM in "${PLATFORMS[@]}"; do
  case "$PLATFORM" in

    apk)
      if [ "$SPLIT_APK" = true ] && [ "$DEBUG_BUILD" = false ]; then
        echo "Building Android APK, $MODE_NAME (split per ABI)..."
        flutter build apk "$MODE_FLAG" --split-per-abi --no-tree-shake-icons \
          "${VERSION_ARGS[@]}" ${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"}
        for abi in arm64-v8a armeabi-v7a x86_64; do
          built="${APK_OUTPUT_DIR}/app-${abi}-release.apk"
          if [ -f "$built" ]; then
            if [ "$NO_RENAME" = false ]; then rename_artifact "$built" "_${abi}" "apk"
            else ARTIFACTS+=("$built"); fi
          fi
        done
      else
        echo "Building Android APK, $MODE_NAME..."
        flutter build apk "$MODE_FLAG" "${VERSION_ARGS[@]}" ${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"}
        built="${APK_OUTPUT_DIR}/app-${MODE_NAME}.apk"
        if [ "$NO_RENAME" = false ]; then rename_artifact "$built" "" "apk"
        else ARTIFACTS+=("$built"); fi
      fi
      DISPLAY_VERSIONS+=("android_${NEW_ANDROID_VERSION}+${NEW_ANDROID_BUILD}")
      ;;

    appbundle)
      echo "Building Android App Bundle, $MODE_NAME..."
      flutter build appbundle "$MODE_FLAG" "${VERSION_ARGS[@]}" ${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"}
      built="${AAB_OUTPUT_DIR}/app-${MODE_NAME}.aab"
      if [ "$DEBUG_BUILD" = true ]; then built="build/app/outputs/bundle/debug/app-debug.aab"; fi
      if [ "$NO_RENAME" = false ]; then rename_artifact "$built" "" "aab"
      else ARTIFACTS+=("$built"); fi
      DISPLAY_VERSIONS+=("android_${NEW_ANDROID_VERSION}+${NEW_ANDROID_BUILD}")
      ;;

  esac
done

DISPLAY_STR=$(IFS=' | '; echo "${DISPLAY_VERSIONS[*]}")
echo ""
echo "Build complete — $DISPLAY_STR ($BUILD_DATE)"
for artifact in ${ARTIFACTS[@]+"${ARTIFACTS[@]}"}; do
  if [ -f "$artifact" ]; then
    size=$(du -h "$artifact" | cut -f1)
    sha=$(shasum -a 256 "$artifact" | cut -d' ' -f1)
    echo "  $artifact"
    echo "    size $size   sha256 ${sha:0:16}…"
  fi
done
if [ "$NO_REVEAL" = false ] && [ ${#ARTIFACTS[@]} -gt 0 ]; then
  reveal_path "${ARTIFACTS[0]}"
fi
echo "  Install on a phone you are using for tests:  adb install -r <apk>"
echo "  (a full install, not a hot reload, is needed for native changes)"

# Build succeeded — disable rollback so git failures don't revert the version
trap - ERR

# ---------------------------------------------------------------------------
# Release note from what changed in lib/, android/app/src, test/, scripts/
# ---------------------------------------------------------------------------
RELEASE_NOTE_FILE=""
if [ "$SKIP_BUMP" = false ] && [ "$NO_NOTE" = false ]; then
  RELEASE_NOTE_SLUG=$(echo "${DISPLAY_STR}" | tr -s ' |+' '_')
  RELEASE_NOTE_FILE="${RELEASE_NOTE_DIR}/${RELEASE_NOTE_SLUG}_${BUILD_DATE}.md"
  mkdir -p "$RELEASE_NOTE_DIR"

  # Tracked changes since the last commit, plus files not yet added to git.
  MODIFIED=$(git diff HEAD --name-only --diff-filter=M -- "${NOTE_PATHS[@]}" 2>/dev/null | grep -v "^$BUILD_INFO_FILE$" || true)
  ADDED=$( { git diff HEAD --name-only --diff-filter=A -- "${NOTE_PATHS[@]}" 2>/dev/null; \
             git ls-files --others --exclude-standard -- "${NOTE_PATHS[@]}" 2>/dev/null; } \
           | sort -u | grep -v "^$BUILD_INFO_FILE$" || true)
  DELETED=$(git diff HEAD --name-only --diff-filter=D -- "${NOTE_PATHS[@]}" 2>/dev/null || true)

  {
    echo "## ${DISPLAY_STR} — ${BUILD_DATE}"
    [ -n "$GIT_MESSAGE" ] && echo "> ${GIT_MESSAGE}"
    echo ""

    if [ -n "$ADDED" ]; then
      echo "### Added"
      echo "$ADDED" | sed 's/^/- /'
      echo ""
    fi
    if [ -n "$MODIFIED" ]; then
      echo "### Changed"
      echo "$MODIFIED" | sed 's/^/- /'
      echo ""
    fi
    if [ -n "$DELETED" ]; then
      echo "### Deleted"
      echo "$DELETED" | sed 's/^/- /'
      echo ""
    fi
    if [ -z "$ADDED" ] && [ -z "$MODIFIED" ] && [ -z "$DELETED" ]; then
      echo "_No changes detected in lib/, android/app/src, test/ or scripts/_"
      echo ""
    fi
  } > "$RELEASE_NOTE_FILE"

  echo "Release note: $RELEASE_NOTE_FILE"
fi

# ---------------------------------------------------------------------------
# Git commit (and, separately confirmed, push)
# ---------------------------------------------------------------------------
if [ "$SKIP_BUMP" = false ] && [ "$NO_GITC" = false ]; then
  if [ -n "$GIT_MESSAGE" ]; then
    COMMIT_MSG="${DISPLAY_STR}_${BUILD_DATE} | —${GIT_MESSAGE}"
  else
    COMMIT_MSG="${DISPLAY_STR}_${BUILD_DATE}"
  fi
  echo ""
  if [ "$COMMIT_ALL" = true ]; then
    echo "This will commit ALL changes in the working tree."
  else
    echo "This will commit only $PUBSPEC, $BUILD_INFO_FILE and the release note"
    echo "(other changes stay uncommitted; use --commit-all to include them)."
  fi
  read -rp "Commit '$COMMIT_MSG'? (y/n): " CONFIRM
  if [[ "$CONFIRM" == "y" || "$CONFIRM" == "Y" ]]; then
    CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
    if [ "$COMMIT_ALL" = true ]; then
      git add .
    else
      git add "$PUBSPEC" "$BUILD_INFO_FILE"
      [ -n "$RELEASE_NOTE_FILE" ] && git add "$RELEASE_NOTE_FILE"
    fi
    git commit -m "$COMMIT_MSG"
    echo "Committed on $CURRENT_BRANCH: $COMMIT_MSG"
    read -rp "Push to origin/$CURRENT_BRANCH? (y/n): " PUSH
    if [[ "$PUSH" == "y" || "$PUSH" == "Y" ]]; then
      git push --set-upstream origin "$CURRENT_BRANCH"
      echo "Pushed."
    else
      echo "Push skipped."
    fi
  else
    echo "Commit skipped."
  fi
fi
