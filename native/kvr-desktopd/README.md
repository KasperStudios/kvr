# kvr-desktopd — приложения ПК внутри KVR (D1)

Демон на Rust. Запускает программы компьютера **в своём скрытом Wayland-композиторе** (Smithay, без единого
вывода на экран) и отдаёт их окна моду. На настоящем рабочем столе (niri, GNOME, KDE…) этих окон **нет**.

```
мод ──UDS: HELLO/LAUNCH/CONFIGURE/ACK/ввод──▶ kvr-desktopd ──WAYLAND_DISPLAY=kvr-desktop-<pid>──▶ foot, firefox…
мод ◀─UDS: STREAM/BUFFER/FRAME/TITLE/CLOSED── kvr-desktopd ◀─wl_shm буферы, damage, frame callbacks── приложения
мод ◀═══ общая память $XDG_RUNTIME_DIR/kvr/stream-<id>-<gen>.buf (3 слота BGRA) ═══ kvr-desktopd
```

## Сборка (Arch Linux)
```sh
sudo pacman -S --needed rust libxkbcommon wayland pkgconf   # rustup тоже подойдёт, нужен Rust ≥ 1.80
cd native/kvr-desktopd
cargo test            # протокол: те же «золотые» байты, что и DesktopProtocolTest.java
cargo build --release # → target/release/kvr-desktopd
mkdir -p <папка игры>/config/kvr/bin && cp target/release/kvr-desktopd <папка игры>/config/kvr/bin/
```
Затем в игре: Настройки KVR → «Приложения ПК в KVR» → окно согласия (показывает путь и SHA-256 файла) → «Разрешить».
Сервер тоже должен разрешить: `enable_host_desktop_windows = true` в `kvr-server.toml` (в одиночной игре — свой).

Проверка без мода:
```sh
KVR_DESKTOPD_TOKEN=$(head -c32 /dev/urandom | xxd -p -c64) target/release/kvr-desktopd \
  --socket $XDG_RUNTIME_DIR/kvr/test.sock --shm-dir $XDG_RUNTIME_DIR/kvr
# в логе: kvr-desktopd ready: wayland_display=kvr-desktop-<pid>
WAYLAND_DISPLAY=kvr-desktop-<pid> foot     # окно НЕ появляется в niri; в логе демона: new window
```
Через 15 с без мода демон сам выходит — это нормально.

## Что умеет D1 и чего пока нет
| Есть | Позже |
|---|---|
| xdg_toplevel → отдельное окно KVR; заголовок и app_id | попапы (меню) и сабсёрфейсы в кадре — D2 |
| wl_shm ARGB/XRGB8888, damage, тройной буфер, обратное давление | linux-dmabuf (GPU-клиенты без софт-рендера) — D4 |
| размер окна задаёт мод (`xdg_toplevel.configure`) — без масштабирования | Xwayland для X11-программ — D3 |
| fps от мода: 60 в фокусе / 30 / 0 (свёрнуто, HUD выкл) через frame callbacks | буфер обмена и drag-n-drop — D2 |
| мышь, кнопки, колесо (v120), клавиатура xkb `us,ru` (Alt+Shift) | курсор приложения поверх окна — D2 |
| server-side decorations: рамку рисует KVR | Windows (скрытый рабочий стол) — D5 |
| приватная D-Bus сессия: приложение не «перепрыгнет» в уже открытый экземпляр на рабочем столе | |

## Если не собирается
API Smithay писался по памяти под 0.6 / smallvil. Места риска помечены `VERIFY` в `src/compositor.rs`.
Пришлите **полный вывод** `cargo build 2>&1 | head -100` и `cargo tree -i smithay | head -3` (версия) — поправлю за заход.
