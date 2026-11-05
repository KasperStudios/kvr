#!/usr/bin/env sh
# Builds src/main/resources/kvr/kvrjs.wasm — QuickJS-ng + kvrjs.c for Chicory.
# Needs: wasi-sdk 24+ (https://github.com/WebAssembly/wasi-sdk/releases) and a checkout of
# https://github.com/quickjs-ng/quickjs at a fixed tag (see README.md). Linux / macOS / WSL.
#
#   WASI_SDK=/opt/wasi-sdk QJS=/path/to/quickjs ./build.sh
set -eu

: "${WASI_SDK:?set WASI_SDK to the wasi-sdk directory}"
: "${QJS:?set QJS to the quickjs-ng checkout}"
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${OUT:-$HERE/../../src/main/resources/kvr/kvrjs.wasm}"

# Core engine sources = qjs_sources from quickjs-ng CMakeLists.txt minus quickjs-libc.c
# (no std/os modules on purpose). Optional files differ between tags, so add them if present.
SRC="$QJS/quickjs.c $QJS/libregexp.c $QJS/libunicode.c $QJS/cutils.c"
for f in xsum.c dtoa.c libbf.c; do
  if [ -f "$QJS/$f" ]; then SRC="$SRC $QJS/$f"; fi
done

mkdir -p "$(dirname "$OUT")"
# --stack-first + 1 MiB stack: a runaway C stack traps instead of overwriting the heap;
# ChicoryJsEngine sets QuickJS's own limit to 896 KiB so JS gets a RangeError first.
# --max-memory 64 MiB caps one app; the JS heap limit (24 MiB) is set at runtime.
"$WASI_SDK/bin/clang" \
  --target=wasm32-wasip1 --sysroot="$WASI_SDK/share/wasi-sysroot" \
  -O2 -mexec-model=reactor \
  -D_GNU_SOURCE -D_WASI_EMULATED_SIGNAL -D_WASI_EMULATED_PROCESS_CLOCKS \
  -I"$QJS" $SRC "$HERE/kvrjs.c" \
  -lwasi-emulated-signal -lwasi-emulated-process-clocks \
  -Wl,--stack-first -Wl,-z,stack-size=1048576 \
  -Wl,--initial-memory=4194304 -Wl,--max-memory=67108864 \
  -Wl,--strip-all \
  -o "$OUT"

echo "built $OUT ($(wc -c < "$OUT") bytes)"
