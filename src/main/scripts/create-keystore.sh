#!/bin/sh

set -e
set -a
. ./keystore.properties
set +a

echo "Generating new keystore: $SIGN_KEYSTORE"

KEYS_DIR="/tmp/keys"
mkdir -p "$KEYS_DIR"

# Generate private key for Root CA
openssl genrsa -out "$KEYS_DIR/rootCA.key" 4096

# Create self-signed Root CA certificate (valid 10 years)
openssl req -x509 -new -nodes -key "$KEYS_DIR/rootCA.key" -sha256 -days 3650 \
  -out "$KEYS_DIR/rootCA.crt" \
  -subj "/C=US/ST=State/L=City/O=MyCompany/CN=MyCompany Root CA"

# Generate private key for code signing
openssl genrsa -out "$KEYS_DIR/codesign.key" 2048

# Create CSR for code signing cert
openssl req -new -key "$KEYS_DIR/codesign.key" -out "$KEYS_DIR/codesign.csr" \
  -subj "/C=US/ST=State/L=City/O=MyCompany/CN=MyCompany Code Signing"

# Config file for code signing extensions
cat > "$KEYS_DIR/codesign.ext" <<EOF
basicConstraints=CA:FALSE
keyUsage = digitalSignature
extendedKeyUsage = codeSigning
EOF

# Sign CSR with Root CA (valid ~2 years)
openssl x509 -req -in "$KEYS_DIR/codesign.csr" \
  -CA "$KEYS_DIR/rootCA.crt" -CAkey "$KEYS_DIR/rootCA.key" -CAcreateserial \
  -out "$KEYS_DIR/codesign.crt" -days 825 -sha256 -extfile "$KEYS_DIR/codesign.ext"
# Export into PKCS#12 keystore

openssl pkcs12 -export \
  -in "$KEYS_DIR/codesign.crt" \
  -inkey "$KEYS_DIR/codesign.key" \
  -certfile "$KEYS_DIR/rootCA.crt" \
  -out "$SIGN_KEYSTORE" \
  -name "$SIGN_ALIAS" \
  -passout pass:"$SIGN_STOREPASS"

echo "Keystore generated: $SIGN_KEYSTORE"
