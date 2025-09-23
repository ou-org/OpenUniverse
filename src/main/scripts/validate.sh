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
# OpenUniverse distribution validator
#

# --------------------------
# Check parameter
# --------------------------
if [ $# -ne 1 ]; then
    echo "OpenUniverse distribution validator"
    echo
    echo "Usage:"
    echo "  $0 <distribution>"
    echo
    echo "Supported distribution types:"
    echo "  - ou-linux-<arch> Native executable, validated by external AppImage validator"
    echo "                    and then its embedded JAR is checked with internal jarsigner"
    echo "  - ou              Cross-platform executable validated by external jarsigner"
    echo "  - ou-<ver>.jar    JAR-file, validated by external jarsigner"
    echo
    echo "Exit codes:"
    echo "   0   Validation successful"
    echo "   1   Validation failed"
    echo
    echo "Examples:"
    echo "  $0 ou-linux-x86_64"
    echo "  $0 ou"
    echo "  $0 ou-1.2.3.jar"
    exit 1
fi

DIST="$1"

if [ ! -f "$DIST" ]; then
    echo "File not found: $DIST"
    exit 1
fi

BASENAME=$(basename "$DIST")

# --------------------------
# Case 1: AppImage
# --------------------------
if echo "$BASENAME" | grep -Eq '^ou-linux-[a-z0-9_]+$'; then
    echo "Detected AppImage distribution"

    ARCH=$(echo "$BASENAME" | sed -E 's/^ou-linux-([a-z0-9_]+)$/\1/')
    if [ -z "$ARCH" ]; then
        echo "Cannot determine architecture from filename: $BASENAME"
        exit 1
    fi
    echo "Detected architecture: $ARCH"

    BASE_URL="https://github.com/AppImageCommunity/AppImageUpdate/releases/download/continuous"
    CACHE_DIR="$HOME/.cache/validator"
    mkdir -p "$CACHE_DIR"

    case "$ARCH" in
      x86_64) FILE="validate-x86_64.AppImage" ;;
      i686) FILE="validate-i686.AppImage" ;;
      aarch64) FILE="validate-aarch64.AppImage" ;;
      armv7l|armhf) FILE="validate-armhf.AppImage" ;;
      *) echo "Unsupported architecture: $ARCH" ; exit 1 ;;
    esac

    VALIDATOR="$CACHE_DIR/$FILE"
    if [ ! -f "$VALIDATOR" ]; then
        echo "Downloading validator for $ARCH..."
        curl -L -o "$VALIDATOR" "$BASE_URL/$FILE"
        chmod +x "$VALIDATOR"
        echo "Validator cached: $VALIDATOR"
    else
        echo "Using cached validator: $VALIDATOR"
    fi

    echo "Validating $DIST..."
    "$VALIDATOR" "$DIST"
    if [ $? -ne 0 ]; then
        echo "AppImage validation failed!"
        exit 1
    fi
    echo "AppImage validation successful."

    echo "Extracting $DIST..."
    rm -rf squashfs-root
    "$DIST" --appimage-extract
    if [ ! -d squashfs-root ]; then
        echo "Extraction failed!"
        exit 1
    fi

    JARSIGNER="squashfs-root/jre/bin/jarsigner"
    OU_JAR="squashfs-root/ou"

    if [ ! -x "$JARSIGNER" ]; then
        echo "jarsigner not found at $JARSIGNER"
        rm -rf squashfs-root
        exit 1
    fi
    if [ ! -f "$OU_JAR" ]; then
        echo "ou not found at $OU_JAR"
        rm -rf squashfs-root
        exit 1
    fi

    echo "Validating ou with internal jarsigner..."
    "$JARSIGNER" -verify "$OU_JAR"
    if [ $? -eq 0 ]; then
        echo "ou validation successful!"
        rm -rf squashfs-root
    else
        echo "ou validation failed!"
        rm -rf squashfs-root
        exit 1
    fi

# --------------------------
# Case 2: Plain ou or JAR
# --------------------------
elif [ "$BASENAME" = "ou" ] || echo "$BASENAME" | grep -qE '\.jar$'; then
    echo "Detected JAR distribution: $BASENAME"
    if ! command -v jarsigner >/dev/null 2>&1; then
        echo "jarsigner not found in PATH"
        exit 1
    fi
    echo "Validating $DIST with external jarsigner..."
    jarsigner -verify "$DIST"
    if [ $? -eq 0 ]; then
        echo "$DIST validation successful!"
    else
        echo "$DIST validation failed!"
        exit 1
    fi

# --------------------------
# Unknown type
# --------------------------
else
    echo "Unsupported distribution type: $BASENAME"
    exit 1
fi

# EOF
