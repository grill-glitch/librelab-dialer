#!/bin/bash
# Generate the LibreDialer release keystore (30-year validity).
#
#   ./scripts/gen-keystore.sh
#
# Then export the Soong certificate pair:
#   ./scripts/export-cert.sh
#
# Keystore password: android (matches the other LibreLab release keystores;
# change it via KEYSTORE_PASS if you want something else).

set -euo pipefail
cd "$(dirname "$0")/.."

KEYSTORE="${1:-libredialer-release.keystore}"
PASS="${KEYSTORE_PASS:-android}"

if [ -f "$KEYSTORE" ]; then
  echo "keystore already exists: $KEYSTORE"
  exit 0
fi

keytool -genkeypair -v \
  -keystore "$KEYSTORE" \
  -alias libredialer \
  -keyalg RSA -keysize 4096 \
  -validity 10950 \
  -storepass "$PASS" -keypass "$PASS" \
  -dname "CN=LibreLab Dialer, OU=LibreLab, O=LibreLab, L=Beijing, S=Beijing, C=CN"

chmod 600 "$KEYSTORE"
echo "Created $KEYSTORE (alias: libredialer)"
