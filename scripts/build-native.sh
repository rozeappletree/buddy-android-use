#!/usr/bin/env bash
# Builds Buddy's native libraries (proot, talloc, libandroid-shmem, the pty helper) from source into
# app/src/main/jniLibs/arm64-v8a with upstream's own, unchanged native/build.sh and the NDK upstream's CI uses
# (27.2.12479018). Works in Git Bash on Windows (the NDK brings make; llvm-readelf stands in for readelf) and on Linux.
# Without these the APK has no proot, and Buddy's Linux set-up fails with "Cannot run program".
# Run it after ./scripts/sync-upstream.sh when native/ or third_party/ changed.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD

# the proot and libandroid-shmem sources, at the commits upstream pins; LF line endings even on Windows
git -c core.autocrlf=false -c core.eol=lf submodule update --init

SDK=${ANDROID_HOME:-${LOCALAPPDATA:+$(cygpath -u "$LOCALAPPDATA" 2>/dev/null)/Android/Sdk}}
NDK=${ANDROID_NDK_HOME:-$SDK/ndk/27.2.12479018}
[[ -d $NDK/toolchains ]] || { echo "NDK 27.2.12479018 not found at $NDK (install it with Android Studio's SDK Manager)" >&2; exit 1; }

TOOLS=$(mktemp -d)
trap 'rm -rf "$TOOLS"' EXIT
BIN=$(echo "$NDK"/toolchains/llvm/prebuilt/*/bin)
command -v readelf >/dev/null || printf '#!/bin/sh\nexec "%s/llvm-readelf" "$@"\n' "$BIN" > "$TOOLS/readelf"
if ! command -v make >/dev/null; then
  # Git for Windows: the NDK's make is a native Windows program, with these quirks worked around here:
  # - it needs a shell path without spaces (C:\Program Files breaks it): the 8.3 short name
  # - it garbles the quotes in proot's $(shell egrep 'include[[:space:]]+"build.h"' ...), so the objects that need the
  #   generated build.h come out empty and cli.c compiles before build.h exists: listed here instead (a variable on
  #   make's command line overrides the makefile's)
  # - it turns the \" around -DVERSION in native/build.sh's CPPFLAGS into slashes: single quotes instead
  # - it runs Git's egrep script by its full path, unquoted: a copy outside "Program Files"
  # - Git Bash would rewrite -DVERSION="5.1.107.102" and PROOT_UNBUNDLE_LOADER=/nonexistent into
  #   C:/Program Files/Git/... (they'd end up inside libproot.so): excluded from that
  export MAKE_EXE SH USE_BUILD_H
  MAKE_EXE=$(echo "$NDK"/prebuilt/*/bin/make.exe)
  SH=$(cygpath -m "$(cygpath -d "$(command -v sh)")")
  USE_BUILD_H=$(cd third_party/proot/src && grep -rlE 'include[[:space:]]+"build.h"' --include='*.c' . | sed 's|^\./||; s|\.c$|.o|' | tr '\n' ' ')
  cat > "$TOOLS/make" <<'WRAPPER'
#!/bin/bash
CPPFLAGS=$(printf '%s' "${CPPFLAGS:-}" | sed -E "s/-DVERSION=\\\\\"([^\\\\]*)\\\\\"/'-DVERSION=\"\1\"'/")
export CPPFLAGS
exec "$MAKE_EXE" SHELL="$SH" USE_BUILD_H="$USE_BUILD_H" "$@"
WRAPPER
  printf '#!/bin/sh\nexec grep -E "$@"\n' > "$TOOLS/egrep"
  export MSYS2_ARG_CONV_EXCL="-DVERSION;PROOT_UNBUNDLE_LOADER=;-DPROOT_UNBUNDLE_LOADER"
fi
chmod +x "$TOOLS"/* 2>/dev/null || true
export PATH="$TOOLS:$PATH"

ANDROID_NDK_HOME="$NDK" bash native/build.sh
ls -l "$ROOT/app/src/main/jniLibs/arm64-v8a"
