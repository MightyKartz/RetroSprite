#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

KEYSTORE_PATH="${KEYSTORE_PATH:-release/retrosprite-release.jks}"
KEY_ALIAS="${KEY_ALIAS:-retrosprite-release}"
DISTINGUISHED_NAME="${DISTINGUISHED_NAME:-CN=RetroSprite, OU=RetroSprite, O=RetroSprite, L=Unknown, ST=Unknown, C=US}"

if ! command -v keytool >/dev/null 2>&1; then
  echo "ERROR: keytool was not found. Install Android Studio or a JDK 17 distribution first." >&2
  exit 1
fi

if [[ -f "$KEYSTORE_PATH" ]]; then
  echo "ERROR: $KEYSTORE_PATH already exists. Keep the existing release key safe and reuse it." >&2
  exit 1
fi

mkdir -p "$(dirname "$KEYSTORE_PATH")"

echo "Creating RetroSprite release keystore at: $KEYSTORE_PATH"
echo "Use a strong password and store it outside the repository."
keytool -genkeypair -v \
  -keystore "$KEYSTORE_PATH" \
  -alias "$KEY_ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -dname "$DISTINGUISHED_NAME"

chmod 600 "$KEYSTORE_PATH"

if [[ ! -f keystore.properties ]]; then
  {
    echo "# Local RetroSprite release signing config. Never commit this file."
    echo "storeFile=$KEYSTORE_PATH"
    echo "storePassword="
    echo "keyAlias=$KEY_ALIAS"
    echo "keyPassword="
  } > keystore.properties
  chmod 600 keystore.properties
  echo "Created keystore.properties. Fill storePassword and keyPassword before building a release APK."
else
  echo "keystore.properties already exists; update it manually if the path or alias changed."
fi

echo "Done. Back up $KEYSTORE_PATH and the passwords securely."
