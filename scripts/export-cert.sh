#!/bin/bash
# Export the release keystore to Soong's android_app_certificate format.
#
# The private key (certs/libredialer.pk8) is gitignored for good reason;
# run this once after cloning to make the module buildable:
#   ./scripts/export-cert.sh
#
# Requires: keytool (JDK) + openssl.

set -euo pipefail
cd "$(dirname "$0")/.."

KEYSTORE="${1:-libredialer-release.keystore}"
ALIAS="libredialer"
PASS="${KEYSTORE_PASS:-android}"
DEST="certs"

[ -f "$KEYSTORE" ] || { echo "keystore not found: $KEYSTORE (build it with scripts/gen-keystore.sh)"; exit 1; }

mkdir -p "$DEST"

# Public certificate (committed)
keytool -exportcert -keystore "$KEYSTORE" -alias "$ALIAS" -rfc -storepass "$PASS" \
  > "$DEST/libredialer.x509.pem" 2>/dev/null

# Private key → PKCS#8 DER (gitignored)
keytool -importkeystore -srckeystore "$KEYSTORE" -srcstorepass "$PASS" \
  -srcalias "$ALIAS" -destkeystore /tmp/libredialer-export.p12 \
  -deststoretype PKCS12 -deststorepass "$PASS" -destalias "$ALIAS" >/dev/null 2>&1
openssl pkcs12 -in /tmp/libredialer-export.p12 -nodes -nocerts -passin "pass:$PASS" 2>/dev/null \
  | openssl pkcs8 -topk8 -inform PEM -outform DER -nocrypt -out "$DEST/libredialer.pk8"
rm -f /tmp/libredialer-export.p12

echo "Wrote $DEST/libredialer.x509.pem + $DEST/libredialer.pk8"
