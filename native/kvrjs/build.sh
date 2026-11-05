#!/usr/bin/env sh
# Builds src/main/resources/kvr/kvrjs.wasm — QuickJS-ng + kvrjs.c for Chicory.
set -eu

: "${WASI_SDK:?set WASI_SDK to the wasi-sdk directory}"
: "${QJS:?set QJS to the quickjs-ng checkout}"
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${OUT:-$HERE/../../src/main/resources/kvr/kvrjs.wasm}"

SRC="$QJS/quickjs.c $QJS/libregexp.c $QJS/libunicode.c $QJS/cutils.c"
for f in xsum.c dtoa.c libbf.c; do
  if [ -f "$QJS/$f" ]; then SRC="$SRC $QJS/$f"; fi
done

mkdir -p "$(dirname "$OUT")"
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
