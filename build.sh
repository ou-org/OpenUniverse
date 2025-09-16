#!/bin/sh

######################################################
# OpenUnvierse — The UaC (Universe as Code) platform #
######################################################

# OpenUniverse Build Script
# This script builds the OpenUniverse project from source.

set -e

export OU_VERSION="1.0.22"

--------------------------------------------- MODIFIABLE CONFIGURATION BEGIN ------------------------------------------------
# -----------------------------
# OUTPUT DIR
# -----------------------------
OUT_DIR="$HOME/ou_dist"

COMMIT_HASH="" # If unset or empty use the lattest (⚠️ WARNING! NOT RECOMMENDED IN PRODUCTION!)

# -----------------------------
# URLs & FILES
# -----------------------------
REPO_URL="https://github.com/ou-org/OpenUniverse.git"

JDK_TAR_FILE_NAME="jdk-24_linux-x64_bin.tar.gz"
JDK_TAR_DOWNLOAD_URL="https://download.oracle.com/java/24/latest/$JDK_TAR_FILE_NAME"

APP_IMAGE_TOOL_FILE_NAME="appimagetool-x86_64.AppImage"
APP_IMAGE_TOOL_DOWNLOAD_URL="https://github.com/AppImage/appimagetool/releases/download/continuous/$APP_IMAGE_TOOL_FILE_NAME"

RUNTIME_x86_64_FILE_NAME="runtime-x86_64"
RUNTIME_x86_64_DOWNLOAD_URL="https://github.com/AppImage/type2-runtime/releases/download/continuous/$RUNTIME_x86_64_FILE_NAME"

# -----------------------------
# JAR SIGNING CONFIG
# -----------------------------
export SIGN_JAR_KEYSTORE="$HOME/keystore.p12" # Use unexisting keystore file to generate default keystore (⚠️ WARNING! NOT RECOMMENDED IN PRODUCTION!)
export SIGN_JAR_STORETYPE="PKCS12"
export SIGN_JAR_ALIAS="signing_alias"
export SIGN_JAR_KEYPASS="your_password"
export SIGN_JAR_STOREPASS="your_password"
export SIGN_JAR_TSA="http://timestamp.digicert.com"

# -----------------------------
# APP IMAGE SIGNING CONFIG
# -----------------------------

# Generate a secure GPG key pair using the following command:
# gpg --batch --passphrase 'YOUR_STRONG_PASSWORD' --pinentry-mode loopback --gen-key <(echo -e 'Key-Type: RSA\nKey-Length: 4096\nSubkey-Type: RSA\nSubkey-Length: 4096\nName-Real: YourName\nName-Email: your-mail@example.com\nExpire-Date: 0\n%commit')
#
# ⚠️ Replace `'YOUR_STRONG_PASSWORD'` with a secure passphrase.<br>
# ⚠️ Replace `YourName` with your name.<br>
# ⚠️ Replace `your-mail@example.com` with your email address.
#
# List Your Secret Keys
# gpg --list-secret-keys

#YOUR_40_CHARACTER_HEX_FINGERPRINT="86A32BE7AB448F546095841B16F66731F8F57B73" # If unset or empty, generate unsigned AppImage (⚠️ WARNING! NOT RECOMMENDED IN PRODUCTION!)

--------------------------------------------- MODIFIABLE CONFIGURATION END --------------------------------------------------

# -----------------------------
# PREREQUISITE CHECKS
# -----------------------------
for cmd in git curl rsync tar mvn openssl; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Error: $cmd is required but not installed." >&2
    exit 1
  fi
done

# -----------------------------
# DIRs
# -----------------------------
CACHE_DIR="$HOME/.cache/build-tools"

# -----------------------------
# ENSURE KEYSTORE EXISTS
# -----------------------------
if [ ! -f "$SIGN_JAR_KEYSTORE" ]; then
  echo "Keystore not found at $SIGN_JAR_KEYSTORE"
  echo "Generating new default keystore..."
  KEYS_DIR="$CACHE_DIR/keys"
  mkdir -p "$CACHE_DIR" "$KEYS_DIR"
  export SIGN_JAR_KEYSTORE="$KEYS_DIR/keystore.p12"

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
    -out "$SIGN_JAR_KEYSTORE" \
    -name "$SIGN_JAR_ALIAS" \
    -passout pass:"$SIGN_JAR_STOREPASS"

  echo "Keystore generated at $SIGN_JAR_KEYSTORE"
else
  echo "Using existing keystore at $SIGN_JAR_KEYSTORE"
fi

# -----------------------------
# TEMP WORK DIR
# -----------------------------
WORKDIR="$(mktemp -d)"
echo "Working in $WORKDIR"
cd "$WORKDIR"

# -----------------------------
# CLONE REPO
# -----------------------------
git clone "$REPO_URL" repo
cd repo
if [ -n "$COMMIT_HASH" ]; then
  echo "Checking out commit $COMMIT_HASH"
  git checkout "$COMMIT_HASH"
fi
REPO_DIR="$(pwd)"

# -----------------------------
# BUILD PROJECT
# -----------------------------
echo "Running Maven build..."
mvn clean verify -e -DskipTests


# -----------------------------
# APP IMAGE SETUP
# -----------------------------
APP_IMAGE_TOOL="$CACHE_DIR/$APP_IMAGE_TOOL_FILE_NAME"
if [ ! -f "$APP_IMAGE_TOOL" ]; then
  echo "Downloading appimagetool..."
  curl -L -o "$APP_IMAGE_TOOL" "$APP_IMAGE_TOOL_DOWNLOAD_URL"
  chmod +x "$APP_IMAGE_TOOL"
else
  echo "Using cached appimagetool: $APP_IMAGE_TOOL"
fi

RUNTIME="$CACHE_DIR/$RUNTIME_x86_64_FILE_NAME"
if [ ! -f "$RUNTIME" ]; then
  echo "Downloading AppImage runtime..."
  curl -L -o "$RUNTIME" \
    "$RUNTIME_x86_64_DOWNLOAD_URL"
  chmod +x "$RUNTIME"
fi

APP_DIR="$REPO_DIR/OpenUniverse.AppDir"

# -----------------------------
# JDK SETUP
# -----------------------------
JDK_TAR="$CACHE_DIR/$JDK_TAR_FILE_NAME"
JDK_DIR="$CACHE_DIR/jdk"

if [ ! -d "$JDK_DIR" ]; then
  if [ -f "$JDK_TAR" ]; then
    echo "Using cached JDK tarball: $JDK_TAR"
  else
    echo "Downloading JDK..."
    curl -L -o "$JDK_TAR" \
      "$JDK_TAR_DOWNLOAD_URL"
  fi
  mkdir -p "$JDK_DIR"
  tar -xzf "$JDK_TAR" -C "$JDK_DIR" --strip-components=1
else
  echo "Using cached JDK installation: $JDK_DIR"
fi

# Copy into AppDir/jre
mkdir -p "$APP_DIR/jre"
rsync -a --delete "$JDK_DIR/" "$APP_DIR/jre/"

cp "$REPO_DIR/target/ou" "$APP_DIR"

if [ -z "${YOUR_40_CHARACTER_HEX_FINGERPRINT:-}" ]; then
    ARCH=x86_64 "$APP_IMAGE_TOOL" "$APP_DIR" "$REPO_DIR/target/ou-linux-x86_64" --runtime-file "$RUNTIME"
else
    ARCH=x86_64 "$APP_IMAGE_TOOL" "$APP_DIR" "$REPO_DIR/target/ou-linux-x86_64" --runtime-file "$RUNTIME" --sign --sign-key "$YOUR_40_CHARACTER_HEX_FINGERPRINT"
fi

mkdir -p "$OUT_DIR"
cp "$REPO_DIR/target/ou-$OU_VERSION.jar" "$OUT_DIR"
cp "$REPO_DIR/target/ou" "$OUT_DIR"
cp "$REPO_DIR/target/ou-linux-x86_64" "$OUT_DIR"

echo "Build completed. Artifacts should be in $OUT_DIR"
