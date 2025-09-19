#!/bin/sh

######################################################
# OpenUnvierse — The UaC (Universe as Code) platform #
######################################################

# OpenUniverse Build Script
# This script builds the OpenUniverse project from source.
set -e

OU_VERSION="1.0.22"
TAG="v$OU_VERSION"
git tag -a "$TAG" -m "Release $TAG"
git push origin "$TAG" --force
