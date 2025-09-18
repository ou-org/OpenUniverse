#!/bin/sh

set -e
set -a
. ./keystore.properties
set +a

echo "Generating new keystore: $SIGN_KEYSTORE"

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT INT TERM
echo "Working in temp dir: $WORKDIR"

# Generate private key for Root CA
openssl genrsa -out "$WORKDIR/rootCA.key" 4096

# Create self-signed Root CA certificate (valid 10 years)
openssl req -x509 -new -nodes -key "$WORKDIR/rootCA.key" -sha256 -days 3650 \
  -out "$WORKDIR/rootCA.crt" \
  -subj "$SIGN_SUBJ_ROOT_CA"

# Generate private key for code signing
openssl genrsa -out "$WORKDIR/codesign.key" 2048

# Create CSR for code signing cert
openssl req -new -key "$WORKDIR/codesign.key" -out "$WORKDIR/codesign.csr" \
  -subj "$SIGN_SUBJ"

# Config file for code signing extensions
cat > "$WORKDIR/codesign.ext" <<EOF
basicConstraints=CA:FALSE
keyUsage = digitalSignature
extendedKeyUsage = codeSigning
EOF

# Sign CSR with Root CA (valid ~2 years)
openssl x509 -req -in "$WORKDIR/codesign.csr" \
  -CA "$WORKDIR/rootCA.crt" -CAkey "$WORKDIR/rootCA.key" -CAcreateserial \
  -out "$WORKDIR/codesign.crt" -days 825 -sha256 -extfile "$WORKDIR/codesign.ext"

# Export into PKCS#12 keystore
openssl pkcs12 -export \
  -in "$WORKDIR/codesign.crt" \
  -inkey "$WORKDIR/codesign.key" \
  -certfile "$WORKDIR/rootCA.crt" \
  -out "$SIGN_KEYSTORE" \
  -name "$SIGN_ALIAS" \
  -passout pass:"$SIGN_STOREPASS"

echo "Keystore generated: $SIGN_KEYSTORE"
