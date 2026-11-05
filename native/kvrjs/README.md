# kvrjs.wasm — JS-движок KVR OS

QuickJS-ng, собранный в WebAssembly, + своя прослойка `kvrjs.c`. Исполняется на JVM через Chicory
(`script/chicory/ChicoryJsEngine`). Нативных библиотек нет: один `.wasm` на все ОС и оба загрузчика.

## Почему своя прослойка, а не готовая обёртка
- ABI из 9 функций, который мы контролируем (описан в шапке `kvrjs.c`). Значения ходят только JSON.
- Прерывание watchdog встроено в QuickJS (`JS_SetInterruptHandler` → импорт `kvr.interrupted`):
  `while(true){}` останавливается без убийства потока.
- Без `quickjs-libc`: у скриптов нет `std`/`os`, файлов и сети. Всё только через `kvr.*`.
- WASI подключается без открытых каталогов и без stdio: он нужен только для часов (`Date.now`) и libc.

## Сборка (один раз, результат коммитится)
1. wasi-sdk 24+ — https://github.com/WebAssembly/wasi-sdk/releases (на Windows через WSL).
2. `git clone --branch v0.10.1 https://github.com/quickjs-ng/quickjs` (тег фиксируем; при смене тега
   пересобрать и прогнать `ChicoryJsEngineTest`).
3. `WASI_SDK=/opt/wasi-sdk QJS=/path/to/quickjs ./build.sh`
4. Результат: `src/main/resources/kvr/kvrjs.wasm` (ожидаю около 0.8–1.2 МБ). Закоммитить.

Файл лежит вне `assets/`: ресурс-пак не должен иметь возможности подменить движок.

## Если не собирается
- Нет `xsum.c` / `dtoa.c` / `libbf.c`, ругается на неопределённые символы: откройте `CMakeLists.txt`
  нужного тега, список `qjs_sources`, и приведите `SRC` в `build.sh` к нему (без `quickjs-libc.c`).
- Ругается на `JS_NewCFunctionData`/`JS_ExecutePendingJob`: проверьте, что это quickjs-ng, а не bellard/quickjs.
- В отчёт: точный текст ошибки clang и тег quickjs-ng.
