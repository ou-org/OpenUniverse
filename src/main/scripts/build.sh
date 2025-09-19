#!/bin/sh                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             

######################################################
# OpenUnvierse — The UaC (Universe as Code) platform #
######################################################

# OpenUniverse Build Script
# This script builds the OpenUniverse project from source.

export OU_VERSION="1.0.22"

set -e
set -a
. ./build.properties
set +a

# -----------------------------
# URLs
# -----------------------------
REPO_URL="https://github.com/ou-org/OpenUniverse.git"
JDK_TAR_DOWNLOAD_URL="https://download.oracle.com/java/24/latest/jdk-24_linux-x64_bin.tar.gz"
APP_IMAGE_TOOL_DOWNLOAD_URL="https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage"
RUNTIME_x86_64_DOWNLOAD_URL="https://github.com/AppImage/type2-runtime/releases/download/continuous/runtime-x86_64"

# -----------------------------
# DIRS
# -----------------------------
OUT_DIR="$HOME/ou-${OU_VERSION}"
CACHE_DIR="$HOME/.cache/build-tools"

mkdir -p "$OUT_DIR"
mkdir -p "$CACHE_DIR"

# -----------------------------
# FILE NAMES
# -----------------------------
JDK_TAR_FILE_NAME="${JDK_TAR_DOWNLOAD_URL##*/}"
APP_IMAGE_TOOL_FILE_NAME="${APP_IMAGE_TOOL_DOWNLOAD_URL##*/}"
RUNTIME_x86_64_FILE_NAME="${RUNTIME_x86_64_DOWNLOAD_URL##*/}"

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
# ENSURE KEYSTORE EXISTS
# -----------------------------
if [ ! -f "$SIGN_JAR_KEYSTORE" ]; then
  echo "Keystore not found: $SIGN_JAR_KEYSTORE"
  exit 1
else
  echo "Using keystore: $SIGN_JAR_KEYSTORE"
fi

# -----------------------------
# TEMP WORK DIR
# -----------------------------
WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT INT TERM
echo "Working in temp dir: $WORKDIR"
cd "$WORKDIR"

# -----------------------------
# CLONE REPO
# -----------------------------
git clone "$REPO_URL" repo
cd repo

TAG="v$OU_VERSION"
COMMIT_HASH=$(git rev-parse "$TAG")

echo "Checking out commit $COMMIT_HASH"
git checkout "$COMMIT_HASH"

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

cp "$REPO_DIR/target/ou-$OU_VERSION.jar" "$OUT_DIR"
cp "$REPO_DIR/target/ou" "$OUT_DIR"
cp "$REPO_DIR/target/ou-linux-x86_64" "$OUT_DIR"

echo "Build completed. Artifacts should be in $OUT_DIR"
