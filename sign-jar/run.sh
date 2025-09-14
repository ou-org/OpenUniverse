#!/bin/bash
set -e  # stop on first error

# Resolve script directory
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
KEYS_DIR="$SCRIPT_DIR/keys"

# Ensure keys directory exists
mkdir -p "$KEYS_DIR"

# Generate private key for Root CA
openssl genrsa -out "$KEYS_DIR/rootCA.key" 4096

# Create self-signed Root CA certificate (valid 10 years)
openssl req -x509 -new -nodes -key "$KEYS_DIR/rootCA.key" -sha256 -days 3650 \
  -out "$KEYS_DIR/rootCA.crt" \
  -subj "/C=US/ST=State/L=City/O=MyCompany/CN=MyCompany Root CA"

# Generate private key for code signing
openssl genrsa -out "$KEYS_DIR/codesign.key" 2048

# Create certificate signing request (CSR) for code signing cert
openssl req -new -key "$KEYS_DIR/codesign.key" -out "$KEYS_DIR/codesign.csr" \
  -subj "/C=US/ST=State/L=City/O=MyCompany/CN=MyCompany Code Signing"

# Create config file with code signing extensions
cat > "$KEYS_DIR/codesign.ext" <<EOF
basicConstraints=CA:FALSE
keyUsage = digitalSignature
extendedKeyUsage = codeSigning
EOF

# Sign CSR with Root CA to issue code signing certificate (valid ~2 years)
openssl x509 -req -in "$KEYS_DIR/codesign.csr" -CA "$KEYS_DIR/rootCA.crt" -CAkey "$KEYS_DIR/rootCA.key" -CAcreateserial \
  -out "$KEYS_DIR/codesign.crt" -days 825 -sha256 -extfile "$KEYS_DIR/codesign.ext"

# Export code signing cert + key + CA chain into PKCS#12 keystore
openssl pkcs12 -export \
  -in "$KEYS_DIR/codesign.crt" \
  -inkey "$KEYS_DIR/codesign.key" \
  -certfile "$KEYS_DIR/rootCA.crt" \
  -out "$KEYS_DIR/keystore.p12" \
  -name signing_alias \
  -passout pass:your_password
