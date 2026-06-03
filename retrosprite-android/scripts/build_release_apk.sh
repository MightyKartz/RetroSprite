#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

TAG="${TAG:-v0.1.0}"
ARTIFACT_DIR="$ROOT_DIR/app/build/release-artifacts"
KEYSTORE_PROPERTIES="$ROOT_DIR/keystore.properties"

mkdir -p "$ARTIFACT_DIR"

property_value() {
  local key="$1"
  [[ -f "$KEYSTORE_PROPERTIES" ]] || return 0

  awk -F= -v key="$key" '
    /^[[:space:]]*(#|$)/ { next }
    {
      candidate = $1
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", candidate)
      if (candidate == key) {
        sub(/^[^=]*=/, "", $0)
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", $0)
        print $0
        exit
      }
    }
  ' "$KEYSTORE_PROPERTIES"
}

signing_value() {
  local property_name="$1"
  local env_name="$2"
  local value="${!env_name:-}"
  if [[ -z "$value" ]]; then
    value="$(property_value "$property_name")"
  fi
  printf '%s' "$value"
}

missing=()
check_signing_value() {
  local property_name="$1"
  local env_name="$2"
  local value
  value="$(signing_value "$property_name" "$env_name")"
  if [[ -z "$value" || "$value" == REPLACE_WITH_* ]]; then
    missing+=("$property_name or $env_name")
  fi
}

check_signing_value "storeFile" "RETROSPRITE_RELEASE_STORE_FILE"
check_signing_value "storePassword" "RETROSPRITE_RELEASE_STORE_PASSWORD"
check_signing_value "keyAlias" "RETROSPRITE_RELEASE_KEY_ALIAS"
check_signing_value "keyPassword" "RETROSPRITE_RELEASE_KEY_PASSWORD"

store_file_value="$(signing_value "storeFile" "RETROSPRITE_RELEASE_STORE_FILE")"
if [[ -n "$store_file_value" && "$store_file_value" != /* ]]; then
  store_file_value="$ROOT_DIR/$store_file_value"
fi

if [[ -z "$store_file_value" || ! -f "$store_file_value" ]]; then
  missing+=("existing release keystore file")
fi

if (( ${#missing[@]} > 0 )); then
  echo "ERROR: release signing is not fully configured." >&2
  printf 'Missing: %s\n' "${missing[@]}" >&2
  echo >&2
  echo "Create the local key with:" >&2
  echo "  ./scripts/generate_release_keystore.sh" >&2
  echo "Then fill keystore.properties, or provide RETROSPRITE_RELEASE_* environment variables." >&2
  exit 2
fi

if [[ -z "${JAVA_HOME:-}" && -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi

gradle_tasks=("./gradlew" ":app:clean")
if [[ "${SKIP_TESTS:-0}" != "1" ]]; then
  gradle_tasks+=(":app:testDebugUnitTest")
fi
gradle_tasks+=(":app:assembleRelease")
if [[ "${RUN_LINT:-0}" != "1" ]]; then
  gradle_tasks+=(
    "-x" "lintVitalRelease"
    "-x" "lintVitalAnalyzeRelease"
    "-x" "lintVitalReportRelease"
  )
fi

echo "Running clean release build for $TAG..."
"${gradle_tasks[@]}"

mkdir -p "$ARTIFACT_DIR"

shopt -s nullglob
release_apks=("$ROOT_DIR"/app/build/outputs/apk/release/*-release.apk)
signed_apk="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"

if [[ ! -f "$signed_apk" ]]; then
  if (( ${#release_apks[@]} > 0 )); then
    signed_apk="${release_apks[0]}"
  else
    echo "ERROR: no release APK was generated." >&2
    exit 3
  fi
fi

if [[ "$signed_apk" == *unsigned* ]]; then
  echo "ERROR: generated APK is unsigned: $signed_apk" >&2
  exit 3
fi

find_apksigner() {
  if command -v apksigner >/dev/null 2>&1; then
    command -v apksigner
    return 0
  fi

  local sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
  find "$sdk_dir/build-tools" -maxdepth 2 -type f -name apksigner 2>/dev/null | sort | tail -n 1
}

APKSIGNER="$(find_apksigner)"
if [[ -z "$APKSIGNER" || ! -x "$APKSIGNER" ]]; then
  echo "ERROR: apksigner was not found. Install Android SDK build-tools first." >&2
  exit 4
fi

artifact_apk="$ARTIFACT_DIR/RetroSprite-${TAG}-release.apk"
certs_file="$ARTIFACT_DIR/RetroSprite-${TAG}-release.apk.certs.txt"
sha_file="$artifact_apk.sha256"

"$APKSIGNER" verify --print-certs "$signed_apk" > "$certs_file"
if grep -q "CN=Android Debug" "$certs_file"; then
  echo "ERROR: release APK is signed with the Android debug certificate." >&2
  exit 5
fi

cp "$signed_apk" "$artifact_apk"
shasum -a 256 "$artifact_apk" > "$sha_file"

echo "Release APK ready:"
ls -lh "$artifact_apk" "$sha_file" "$certs_file"
