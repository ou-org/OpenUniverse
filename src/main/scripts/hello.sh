#!/bin/sh
set -e

OU_VERSION="1.0.22"
TAG="v{$OU_VERSION}"

# GitHub raw content URL for OpenUniverse repository
RAW_URL="https://raw.githubusercontent.com/ou-org/OpenUniverse/refs/heads/master"

RELEASE_DOWNLOAD_URL="https://github.com/ou-org/OpenUniverse/releases/download/$TAG"


# -----------------------------
# TEMP WORK DIR
# -----------------------------

BASE_DIR="$HOME/OpenUniverseDemo"
mkdir -p "$BASE_DIR"
cd "$BASE_DIR"

# Create repo
REPO_DIR="$BASE_DIR/HelloUniverseRepo"
mkdir "$REPO_DIR"

# Download example markdown file
EXAMPLE_MD_URL="$RAW_URL/doc/examples/HelloUniverse.md"
curl -L -o "$REPO_DIR/$(basename $EXAMPLE_MD_URL)" "$EXAMPLE_MD_URL"

# Initialize git repo and make initial commit
cd "$REPO_DIR"
git init
git config user.name "Test User"
git config user.email "test@example.com"
git add .
git commit -m "Initial commit"

# Build sample keystore
KEYSTORE_SCRIPT_URL="$RAW_URL/src/main/scripts/create-keystore.sh
KEYSTORE_SCRIPT="$BASE_DIR/$(basename $KEYSTORE_SCRIPT_URL)"
curl -L -o "$KEYSTORE_SCRIPT" "$KEYSTORE_SCRIPT_URL"
chmod +x "$KEYSTORE_SCRIPT"
sh "$KEYSTORE_SCRIPT"

# Download and run OpenUniverse build script
BUILD_SCRIPT_URL="$RELEASE_DOWNLOAD_URL/build.sh"
BUILD_SCRIPT="$BASE_DIR/$(basename $RELEASE_DOWNLOAD_URL)"
curl -L -o "$BUILD_SCRIPT" "$BUILD_SCRIPT_URL"
chmod +x "$BUILD_SCRIPT"

RELEASE_DIR="$BASE_DIR/ou-${OU_VERSION}"
sh "$BUILD_SCRIPT" "$RELEASE_DIR"

# Start OpenUniverse to process example repo
"$RELEASE_DIR/ou" "$REPO_DIR" start --stdout

#EOF
