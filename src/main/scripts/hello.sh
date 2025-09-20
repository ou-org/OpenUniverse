#!/bin/sh
set -e

# GitHub raw content URL for OpenUniverse repository
RAW_URL = "https://raw.githubusercontent.com/ou-org/OpenUniverse/refs/heads/master"


BASE_DIR="$HOME/OpenUniverseDemo"
mkdir -p "$BASE_DIR"
cd "$BASE_DIR"

# Create repo
REPO_DIR="$BASE_DIR/HelloUniverseRepo"
mkdir "$REPO_DIR"

# Download example markdown file
EXAMPLE_MD_URL = "$RAW_URL/doc/examples/HelloUniverse.md"
curl -L -o "$REPO_DIR/$(basename $EXAMPLE_MD_URL)" "$EXAMPLE_MD_URL"

# Initialize git repo and make initial commit
cd "$REPO_DIR"
git init
git config user.name "Test User"
git config user.email "test@example.com"
git add .
git commit -m "Initial commit"

# Build sample keystore
KEYSTORE_SCRIPT_URL="https://raw.githubusercontent.com/youruser/yourrepo/main/sign-jar/create-keystore.sh"
KEYSTORE_SCRIPT="$BASE_DIR/create-keystore.sh"
curl -L -o "$KEYSTORE_SCRIPT" "$KEYSTORE_SCRIPT_URL"
chmod +x "$KEYSTORE_SCRIPT"
sh "$KEYSTORE_SCRIPT"
echo "Created sample keystore"

# 4) Download and run OpenUniverse build script
BUILD_SCRIPT_URL="https://raw.githubusercontent.com/youruser/yourrepo/main/build.sh"
BUILD_SCRIPT="$BASE_DIR/build.sh"
curl -L -o "$BUILD_SCRIPT" "$BUILD_SCRIPT_URL"
chmod +x "$BUILD_SCRIPT"
sh "$BUILD_SCRIPT"
echo "Ran OpenUniverse build script"

# 5) Start OpenUniverse to process example repo
# Assume OpenUniverse CLI jar is in BASE_DIR/OpenUniverse.AppDir/ou
OU_JAR="$BASE_DIR/OpenUniverse.AppDir/ou/OpenUniverse.jar"
EXAMPLE_REPO="$BASE_DIR/example"

if [ -f "$OU_JAR" ]; then
    java -jar "$OU_JAR" process "$EXAMPLE_REPO"
    echo "Started OpenUniverse to process example repo"
else
    echo "Error: OpenUniverse jar not found at $OU_JAR"
fi
