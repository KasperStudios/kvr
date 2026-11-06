# kvr-desktopd — приложения ПК внутри KVR (D2)

Демон на Rust. Запускает программы компьютера **в своём скрытом Wayland-композиторе** (Smithay, без единого
вывода на экран) и отдаёт их окна моду. На настоящем рабочем столе (niri, GNOME, KDE…) этих окон **нет**.

```
мод ──UDS: HELLO/LAUNCH/CONFIGURE/ACK/ввод/CLIPBOARD_SET──▶ kvr-desktopd ──WAYLAND_DISPLAY=kvr-desktop-<pid>──▶ foot, firefox…
мод ◀─UDS: STREAM/BUFFER/FRAME/TITLE/CLOSED/CURSOR/CLIPBOARD── kvr-desktopd ◀─wl_shm буферы, damage, frame callbacks, data device── приложения
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

## Что умеет (D1 + D2) и чего пока нет
Протокол **v2**: мод 0019+ и демон 0.2.0 работают только в паре (старый демон с новым модом — ошибка с подсказкой).

| Есть | Позже |
|---|---|
| xdg_toplevel → отдельное окно KVR; заголовок и app_id | linux-dmabuf (GPU-клиенты без софт-рендера) — D4 |
| **D2:** попапы (меню, подсказки, выпадающие списки, контекстные меню) и сабсёрфейсы сведены в кадр окна | Xwayland для X11-программ — D3 |
| **D2:** меню не вылезают за окно (positioner: сдвиг/переворот внутри окна) | ресайз окна за угол — D3 |
| **D2:** grab меню: клавиатура в открытом меню, клик мимо закрывает меню и съедается | primary selection (вставка средней кнопкой) между KVR и системой |
| **D2:** курсор приложения (картинка ≤ 64×64 или имя формы) рисуется у указателя руки | Windows (скрытый рабочий стол) — D5 |
| **D2:** буфер обмена (текст) в обе стороны с системным буфером | |
| **D2:** окно обрезается по window geometry: тени GTK4/libadwaita не попадают в кадр | |
| wl_shm ARGB/XRGB8888, damage, тройной буфер, обратное давление | |
| размер окна задаёт мод (`xdg_toplevel.configure`) — без масштабирования | |
| fps от мода: 60 в фокусе / 30 / 0 (свёрнуто, HUD выкл) через frame callbacks | |
| мышь, кнопки, колесо (v120), клавиатура xkb `us,ru` (Alt+Shift) | |
| server-side decorations: рамку рисует KVR | |
| приватная D-Bus сессия: приложение не «перепрыгнет» в уже открытый экземпляр на рабочем столе | |

## Если не собирается
API Smithay писался по памяти под 0.6 / smallvil. Места риска помечены `VERIFY` в `src/compositor.rs` и `src/surfaces.rs`.
Пришлите **полный вывод** `cargo build 2>&1 | head -100` и `cargo tree -i smithay | head -3` (версия) — поправлю за заход.
