#!/bin/sh

# The MIT License
# Copyright © 2025 OpenUniverse
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.

# OpenUnvierse — The UaC (Universe as Code) platform

#
# Hello, Universe! Example.
#

#########################################
# ⚠️ WARNING! NOT FOR PRODUCTION USAGE! #
#########################################

set -e

OU_VERSION="$1"
TAG="v${OU_VERSION}"
ARCH="$2"

# GitHub raw content URL for OpenUniverse repository
RAW_URL="https://raw.githubusercontent.com/ou-org/OpenUniverse/${TAG}"

# -----------------------------
# TEMP WORK DIR
# -----------------------------

BASE_DIR="$HOME/HelloUniverse"
mkdir -p "$BASE_DIR"
cd "$BASE_DIR"

# Create repo
REPO_DIR="$BASE_DIR/HelloUniverseRepo"
mkdir -p "$REPO_DIR"

# Download example markdown file
EXAMPLE_MD_URL="$RAW_URL/doc/examples/HelloUniverse.md"
curl -L -o "$REPO_DIR/$(basename $EXAMPLE_MD_URL)" "$EXAMPLE_MD_URL"

# Initialize git repo and make initial commit
cd "$REPO_DIR"
git init
git config user.name "Test User"
git config user.email "test@example.com"
git add .
if git diff --cached --quiet; then
  echo "No changes to commit"
else
  git commit -m "Initial commit"
fi

# Build sample keystore
echo "Creating sample QaD (quick-and-dirty) keystore..."
KEYSTORE_DIR="$BASE_DIR/MyQuickAndDirtyKeystore"
KEYSTORE_SCRIPT_URL="$RAW_URL/src/main/scripts/create-qad-keystore.sh"
KEYSTORE_SCRIPT="$BASE_DIR/$(basename $KEYSTORE_SCRIPT_URL)"
curl -L -o "$KEYSTORE_SCRIPT" "$KEYSTORE_SCRIPT_URL"
chmod +x "$KEYSTORE_SCRIPT"
sh "$KEYSTORE_SCRIPT" "$KEYSTORE_DIR"

# Download and run OpenUniverse build script
echo "Downloading and running OpenUniverse build script..."
BUILD_SCRIPT_URL="$RAW_URL/src/main/scripts/build.sh"
BUILD_SCRIPT="$BASE_DIR/$(basename $BUILD_SCRIPT_URL)"
curl -L -o "$BUILD_SCRIPT" "$BUILD_SCRIPT_URL"
chmod +x "$BUILD_SCRIPT"

BUILD_PROPERTIES_URL="$RAW_URL/src/main/scripts/build.properties"
BUILD_PROPERTIES="$BASE_DIR/$(basename $BUILD_PROPERTIES_URL)"
curl -L -o "$BUILD_PROPERTIES" "$BUILD_PROPERTIES_URL"

"$BUILD_SCRIPT" "$OU_VERSION" "$ARCH" "$BUILD_PROPERTIES" "$BASE_DIR"

# Start OpenUniverse to process example repo
RELEASE_DIR="$BASE_DIR/ou-${OU_VERSION}"
exec "$RELEASE_DIR/ou-linux-x86_64" "$REPO_DIR" start --stdout < /dev/tty

#EOF
