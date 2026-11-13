#!/usr/bin/env bash
# Pull the maintainer's latest changes into this branch.
# Our additions live in app/src/launcher/ (+ one block in app/build.gradle.kts), so merges rarely conflict.
set -euo pipefail
# the maintainer's own repository (origin, rozeappletree/buddy-android-use, is a fork of it that lags until synced)
UPSTREAM_URL="https://github.com/ghorbelhamdi/buddy-android"
if git remote get-url upstream >/dev/null 2>&1; then git remote set-url upstream "$UPSTREAM_URL"; else git remote add upstream "$UPSTREAM_URL"; fi
git fetch upstream
git merge --no-edit upstream/main
echo "Merged. Build with: ./gradlew assembleFdroidLauncher"
