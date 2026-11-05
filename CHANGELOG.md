# CHANGELOG — Java-код KVR из чата

Каждая запись — одно задание. В «Файлы» только то, что добавлено или изменено.

## 0014 — D1: приложения ПК только внутри KVR (kvr-desktopd, согласие, UDS + shm) — ГОТОВО К ЗАБОРУ (не проверено сборкой)

Решения Создателя — в начале `DESKTOP_PLAN.md`. **Забирать вместе с 0012 и 0013**: дерево уже содержит все три,
а заглушка рабочего стола из 0013 (TCP + JSON, `HostDesktopLink`) **удалена** — её не забирать.

### Главное требование: окон на настоящем рабочем столе нет
`kvr-desktopd` (Rust, `native/kvr-desktopd/`) — Wayland-композитор на Smithay **без бэкенда вывода**: он не подключается к niri
и ничего не рисует на экран. Приложения стартуют с `WAYLAND_DISPLAY=kvr-desktop-<pid>`, без `DISPLAY` (X11-программа упадёт,
а не выскочит в XWayland niri) и, если есть `dbus-run-session`, в частной D-Bus сессии (Firefox/GTK не передадут
запуск уже открытому экземпляру, диалог файлов портала не всплывёт на рабочем столе).

### Согласие и запуск (мод)
- `config/KvrSettingsScreen` — настройки KVR: «Новые окна публичные», «Показывать публичные окна других»,
  «Приложения ПК в KVR» (включение без согласия сразу открывает окно согласия), кнопка «согласие…».
  Пишет `kvr-client.toml` по одному ключу (`KvrConfig.updateClient`, комментарии сохраняются).
- `desktop/client/DesktopConsentScreen` — окно согласия: что за программа и что она делает, **путь и SHA-256 файла**
  (или URL + закреплённый SHA-256 скачивания). Согласие хранится **как хеш** (`host_desktop_consent`): подменили файл —
  спросит снова. Открывается из настроек и само при первом клике по пункту дока «Рабочий стол». Отозвать — там же.
- `desktop/client/DesktopDaemon` — поиск бинарника (`host_desktop_daemon` или `config/kvr/bin/kvr-desktopd`), скачивание **только
  с закреплённым хешем** (другой хеш = файл удаляется), запуск через `ProcessBuilder` в фоне:
  `--socket $XDG_RUNTIME_DIR/kvr/desktopd.sock --shm-dir $XDG_RUNTIME_DIR/kvr` (папка 0700; на Windows `%LOCALAPPDATA%\kvr`),
  случайный токен **только в окружении** (`KVR_DESKTOPD_TOKEN`), лог → `logs/kvr-desktopd.log`. Демон умирает с игрой:
  shutdown hook в моде + `PR_SET_PDEATHSIG` в демоне и у каждого приложения. Без подключения мода 15 с — выходит сам.
- Док «Рабочий стол» → `DesktopLauncherWindow` со списком `host_desktop_apps = "foot;firefox --new-instance;thunar"`
  (без шелла: слова = argv) → каждое окно приложения становится `HostDesktopWindow` (Kind APP, слот RAM, всегда личное).
  Диалоги приложения — отдельные окна KVR чуть правее. Лимит окон — серверный `host_desktop_max_windows`.

### Мост UDS + общая память
- `desktop/DesktopProtocol` (Java) = `src/protocol.rs` (Rust): двоичные сообщения, little-endian, обе стороны тестируют **одни и те же
  «золотые» байты**. Пиксели по сокету не идут.
- `DesktopBridge` — Unix domain socket (Java 16+, есть и в Windows 10 1803+), поток-читатель, HELLO с токеном, очередь LAUNCH
  до HELLO_OK, повтор подключения 5 с, пока демон создаёт сокет.
- `DesktopStream` — файл окна в `$XDG_RUNTIME_DIR/kvr` с 3 слотами BGRA, `FileChannel.map`, «новейший кадр выигрывает»,
  ACK освобождает слот (обратное давление: нет слота — демон держит буфер приложения и то не рисует).
- `RenderCompat.StreamTexture` — своя GL-текстура, `glTexSubImage2D` **прямо из mapped-буфера**, только по damage,
  `GL_BGRA` без перестановки, GL_LINEAR. Вместо 2 млн `setPixelRGBA` из 0013 — одна копия CPU→GPU.
- Размер и fps задаёт мод (`CONFIGURE`): окно 480×300 GUI px → приложение рисует ровно 960×600; 60 к/с с клавиатурой,
  30 видно, 0 свёрнуто / HUD выкл (приложение стоит без frame callbacks).

### Демон (Rust, D1)
`main.rs` (аргументы, токен, PDEATHSIG), `compositor.rs` (Smithay: compositor, xdg-shell, xdg-decoration = server-side,
wl_shm, seat, виртуальный output), `ipc.rs`, `shm.rs`, `launch.rs`, `protocol.rs`. Честный список того, чего в D1 нет
(меню-попапы в кадре, dmabuf, Xwayland, буфер обмена, Windows) — `native/kvr-desktopd/README.md`.
**API Smithay писал по памяти (0.6 / smallvil)**, места риска помечены `VERIFY`. Пришлите полный вывод `cargo build`.

### Клавиатура и мышь в Alt-режиме
- `SpatialWindow.wantsKeyboard()` + `keyEvent/charTyped/keyboardFocus/mouseMoved`. `SpatialInput` сам отдаёт клавиатуру
  окну под рукой (и оставляет ему, пока рука смотрит в пустоту); другое окно под рукой или выход из Alt —
  клавиатура возвращается игре. При захвате `KeyMapping.releaseAll()` (зажатая W не ведёт игрока). Alt проходит всегда.
- Новый миксин `KeyboardHandlerMixin` (`keyPress`, `charTyped`; экраны не трогает) — добавлен в `kvr-spatial.mixins.json`.
- ПКМ над окном приложения — правый клик в нём; колесо жестов — как раньше, вне таких окон.
- JS-приложения: `"keyboard": true` в `config.json` → `kvr.on('key'|'char'|'keyboard', e => ...)` (`VrApp.keyboard`, prelude).

### Конфиг `kvr-client.toml` (заменяет десктопную часть 0013)
`enable_host_desktop_windows`, `host_desktop_daemon`, `host_desktop_download_url`, `host_desktop_download_sha256`,
`host_desktop_consent`, `host_desktop_apps`; `host_desktop_port` удалён. `#` внутри кавычек больше не режет значение (URL).

### Подключение
- клиент: `HostDesktopClient.initClient()` — как в 0013; при отключении от сервера `HostDesktopClient.reset()` (теперь ещё
  закрывает приложения и останавливает демон — желательно повесить на событие дисконнекта).
- настройки:
  - Fabric: `fabric/.../client/KvrModMenu.java` + `modCompileOnly("com.terraformersmc:modmenu:<для 1.21.1>")` +
    `"entrypoints": { "modmenu": ["com.kasperstudios.kvr.fabric.client.KvrModMenu"] }` в fabric.mod.json;
  - NeoForge (в клиентском конструкторе мода): `container.registerExtensionPoint(IConfigScreenFactory.class, (c, parent) -> new KvrSettingsScreen(parent));`
- демон: собрать по `native/kvr-desktopd/README.md`, положить в `<игра>/config/kvr/bin/kvr-desktopd`.

### Тесты
- Java: `DesktopProtocolTest` (4, в т.ч. золотые байты), `DesktopDaemonTest` (3), `KvrConfigTest` — теперь 3 (тест кадра v1 удалён вместе
  с `HostDesktopLinkAccess`, добавлены запись ключа и согласие).
- **Ожидаю (0012 + 0013 + 0014): без `.wasm` — 151 passed, 12 skipped; с `.wasm` — 163 passed.**
- Rust: `cargo test` — 3 (золотые байты как в Java, поток сообщений, мусор).

### Файлы
- новые: `desktop/DesktopProtocol.java`, `desktop/client/{DesktopDaemon,DesktopBridge,DesktopStream,DesktopLauncherWindow,DesktopConsentScreen}.java`,
  `config/KvrSettingsScreen.java`, `mixin/client/KeyboardHandlerMixin.java`, `fabric/.../client/KvrModMenu.java`,
  `native/kvr-desktopd/{Cargo.toml,README.md,src/{main,protocol,compositor,ipc,shm,launch}.rs}`,
  тесты `desktop/DesktopProtocolTest.java`, `desktop/client/DesktopDaemonTest.java`
- изменены: `desktop/client/{HostDesktopClient,HostDesktopWindow}.java`, `desktop/HostDesktopServer.java` (javadoc), `config/KvrConfig.java`,
  `spatial/window/SpatialWindow.java`, `spatial/input/SpatialInput.java`, `spatial/render/RenderCompat.java`,
  `script/VrApp.java`, `script/ui/ScriptWindow.java`, `prelude.js`, `kvr-spatial.mixins.json`, `lang/*.json`, тест `KvrConfigTest`
- **удалены:** `desktop/client/HostDesktopLink.java`, `src/test/.../desktop/client/HostDesktopLinkAccess.java`

### Проверить (Arch + niri)
1. `cargo test && cargo build --release`; ручной запуск из README: `foot` в демоне **не появляется в niri**, в логе `new window`.
2. В игре: Настройки KVR → «Приложения ПК» вкл → окно согласия с путём и SHA-256 → «Разрешить».
   Пересобрать демон → согласие спрашивается снова (другой хеш).
3. Док → «Рабочий стол» → foot: окно терминала в мире, **на рабочем столе niri ничего**; Alt → навести руку → бейдж «⌨» →
   набрать `ls`, Enter; Alt+Shift — русские буквы; W/A/S/D в это время не двигают игрока; Alt — клавиатура снова у игры.
4. firefox: прокрутка колесом, клики, ПКМ. Даже если обычный Firefox уже открыт в niri — новое окно открывается в KVR.
   (Меню по ПКМ пока не рисуются — D2.)
5. Свернуть окно / HUD выкл → `top`: приложение не ест CPU. Выйти из мира / закрыть игру → демон и приложения завершаются.
6. В отчёт: вывод `cargo build`, `cargo tree -i smithay | head -3`, хвост `logs/kvr-desktopd.log`.

## 0013 — Меню окна (шестерёнка), публичные окна, конфиги, окна рабочего стола хоста, запуск Chicory — ГОТОВО К ЗАБОРУ (не проверено сборкой)

Директива Создателя (Kasperenok). Эта редакция 0013 **заменяет прежнюю** (её никто не забирал).
**Забирать вместе с 0012**: у вас собрано до 0011, а 0012 (`ChicoryJsEngine`) и 0013 правят одни и те же файлы
(`ScriptHost`, `prelude.js`, lang). **В отчёте жду строки `[kvr-bench]`**:
`./gradlew :1.21.1:test --tests '*ChicoryJsEngineTest*' -i`.

### А. Шестерёнка и меню окна (`spatial/window/`)
- Хром есть у каждого окна с планкой захвата — приложения, ванильные экраны-прокси, Заметка, «Питание», рабочий стол.
  Рисуется поверх содержимого (`WindowSurface` → `renderChrome`), подклассы не трогаются. У дока, тостов и колеса жестов хрома нет.
  - **×** в правом верхнем углу — закрыть окно (срабатывает на следующем `WindowManager.tick()`). Свои «×» у PinnedNote/PowerWindow убраны.
  - **⚙** в правом нижнем — меню:
    ```
    Настройки окна              ×
    Привязка   [ Тело ]  [  Мир  ]
    Видимость  [Публично] [ Лично ]
    (замок) Заблокировать окно
    ```
    Выбор Тело/Мир и Публично/Лично меню **не закрывает** (видно результат); закрывается оно своим **×**,
    шестерёнкой или кликом мимо (такой клик в приложение не уходит). У узких окон (< 174 px) столбец подписей скрывается.
- **Привязка — только через это меню** (и `kvr.window.anchor` из JS). Тумблер рядом с планкой захвата убран
  (`SpatialRaycast.Region.ANCHOR_TOGGLE` больше нет). Цвет самой планки: голубая — Тело, белая — Мир, красная — заблокировано.
  - **Тело** (`BodyAnchor.carry`): окно летит с игроком на постоянном смещении по осям мира и за поворотом головы не крутится —
    не маячит перед глазами. При переключении сохраняет направление от игрока, но плавно подтягивается в радиус
    0.6–2 м и на высоту от колен до чуть выше головы (монитор, оставленный в 20 блоках, не летает за вами в 20 блоках).
  - **Мир**: окно замирает в текущих координатах мира.
- **«Закрепить у плеча» убрано** — и из меню, и `kvr.window.pinToShoulder()`. `kvr.pin(title, text)` (Заметки, Мессенджер)
  теперь открывает обычное окно-заметку перед игроком (чуть правее и ниже глаз), привязка Тело; дальше —
  шестерёнка и ×, как у всех.
- **Блокировка**: красная рамка, шестерёнка превращается в замок. Нельзя схватить за планку, нажать контролы
  (включая × и «Закрыть» внутри окна), крутить колесо; жесты «свернуть» его пропускают; клики поглощаются —
  блок сквозь окно не копается. Снять — клик по замку. «Питание» само снимает блокировку при перегрузке
  (кнопка Power Off не может оказаться заблокированной). Снятие очков / Power Off закрывают всё как раньше.
- Логика хрома — `WindowChrome` без классов Minecraft (тесты). Ввод — `click(x, y, button, view)` / `release(x, y, button)` /
  `scroll(...)`; отпускание кнопки после клика по хрому в приложение не уходит.
- JS: `kvr.window.lock(bool)`, `kvr.window.setPublic(bool)`, `kvr.window.anchor('body'|'world')` (op `window.option`).

### Б. Публичные окна материализуются для других игроков (`share/`)
- **Публично** — окно видят все игроки в радиусе `public_window_range` (48 блоков) в том же измерении,
  зрителю очки не нужны. **Лично** — окно не покидает компьютер владельца (ни сервер, ни HackGlasses).
- Путь: клиент владельца и так шлёт сводку своих публичных окон 2 раза в секунду (`kvr:window_summary`, 0006) →
  `PublicWindowRelay` (сервер) каждому зрителю шлёт окна до 8 ближайших владельцев (`kvr:public_windows`, S2C) →
  `PublicWindowsClient` + `PublicWindowRenderer` рисуют стеклянную панель ровно там, где её видит владелец: заголовок,
  ник, текст окна (≤ 256 символов). Свёрнутые окна и окна дальше радиуса от владельца не пересылаются
  (чит-клиент не «посадит» панели по карте); заряд батареи остаётся только для HackGlasses.
- А переключил на Лично / выключил дисплей → у зрителей окно пропадает за ~0.5 с (клиент шлёт пустую сводку).
- Это **сводка, а не пиксели**: другие видят то, что отдаёт `spyText()` (тексты и кнопки приложений, содержимое
  сундука, заметку), а не картинку. Зеркалирование пикселей — отдельный тикет (нужна передача текстур).
- По умолчанию окна **личные** (`default_window_public = false`): теперь «публичное» видят все, а не только хакер.
  Панели без шестерёнки (док, тосты) публичными не бывают никогда.

### В. Конфиги (`config/KvrConfig`)
- `config/kvr-client.toml`: `default_window_public = false`,
  `show_public_windows = true` (выключить — не рисовать чужие публичные окна).
- `config/kvr-server.toml`: `enable_host_desktop_windows = false`, `host_desktop_max_windows = 2`, `public_window_range = 48` (0 — выкл).
- Свой парсер плоского TOML без библиотек (один код на Fabric и NeoForge), файлы с дефолтами создаются
  сами. Папка `./config`; если хотите папку загрузчика — `KvrConfig.setDirectory(path)` до первого обращения.

### Г. Окна рабочего стола хоста — **заменено заданием 0014 (D1)**
- Осталось из 0013: разрешить должны оба — игрок (`kvr-client.toml`) и сервер (`kvr:desktop_query` → `kvr:desktop_policy`);
  пиксели не покидают компьютер, окно всегда личное (в меню «Публично» неактивно); флаг сервера — рекомендательный.
- Заглушка TCP + JSON (`HostDesktopLink`, `host_desktop_port`) **удалена** — вместо неё kvr-desktopd, UDS + общая память (см. 0014).

### Д. Проверка 0012: `ChicoryJsEngine` и `install()`
Перечитал `ChicoryJsEngine` / `ChicoryJsEngineTest` / `kvrjs.c` под правки 0013: ABI (9 функций, 3 импорта) совпадает, новые
ops (`window.option`) идут через тот же `__kvr_host`. Исправлено:
- `ChicoryJsEngine.install()`:
  - идемпотентен (повторный вызов — ничего);
  - сразу регистрирует фабрику, потом в фоне (поток со стеком 16 МБ, как у скриптов) парсит `.wasm` и делает
    **самотест** на настоящем экземпляре: `_initialize` → `kvr_init` → `eval` → очередь Promise → `call` с JSON → `[42,1,"ы"]`;
  - если `.wasm` нет или самотест упал — фабрика снимается (`ScriptEngines.uninstall(причина)`), в каждом окне приложения
    «Движок не запустился: <причина>», в логе полный stacktrace, игра жива;
  - при успехе в логе `KVR OS: QuickJS wasm ready (parse N ms, first instance + self-test M ms)` — **пришлите эту строку**;
    `ChicoryJsEngine.status()` / `statusDetail()` — для диагностики.
- Поздний `Thread.interrupt()` (гонка со сторожем) больше не ломает следующий вызов: настоящее убийство всегда ставит
  флаг `interrupted` раньше, а шальное прерывание потока сбрасывается перед входом в wasm.
- `ScriptHost`: `LinkageError` при создании движка (Chicory не попал в джар — нет `include(...)`) → окно пишет ошибку,
  а не висит на «Запуск…».
- `BuiltinApps.SYSTEM_APPS` = hello, kav, notes, calc, messenger — один список для дока и теста.
- `ChicoryJsEngineTest` (без `.wasm` — skipped): всё идёт на потоке со стеком 16 МБ, как в `ScriptHost`. Новое: самотест
  `install()`, `uninstall` с причиной, шальное прерывание; **каждое из 5 приложений** стартует на настоящем
  `prelude.js`, отдаёт UI и переживает клик по первой кнопке (Hello обязан показать `Clicks: 1`), Калькулятор считает
  `[204,8,3]`; строки `[kvr-bench] app ...` — куча каждого приложения.
- Сборка `.wasm` и зависимости — как в 0012 (wasi-sdk 24+, quickjs-ng `v0.10.1`, Chicory 1.4.0 `runtime` + `wasi` + транзитивные
  через `include(...)`). Если clang или javac ругается — точный текст, поправлю за один заход.

### Init (оба загрузчика)
- клиент, по порядку: `KVRNetwork` → `ChicoryJsEngine.install()` →
  `KVRSpatial.initClient(new SpatialConfig(..., new BuiltinApps(BuiltinApps.SYSTEM_APPS)))` → `SpyClient.initClient()` →
  **`PublicWindowsClient.initClient()`** → `HostDesktopClient.initClient()`;
- общий: `SpyRelay.init()` → **`PublicWindowRelay.init()`** (строго после `SpyRelay`: тот владеет каналом сводки) → `HostDesktopServer.init()`;
- при отключении от сервера, если есть событие в вашем слое: `HostDesktopClient.reset()` (публичные окна чистятся сами).

### Тесты
- новые: `WindowChromeTest` (6), `WindowManagerTest` (6), `BodyAnchorTest` (4),
  `WindowSummaryTest.publicViewDropsMinimisedAndFarWindowsAndHidesBattery` (1), `KvrConfigTest` (2).
- `ChicoryJsEngineTest`: 12 тестов (skipped без `.wasm`).
- Ожидалось для одного 0013: без `.wasm` — 143 passed, 12 skipped. **С 0014 итог другой — см. 0014.**

### Файлы
- новые: `spatial/window/WindowChrome.java`, `share/PublicWindowRelay.java`, `share/client/{PublicWindowsClient,PublicWindowRenderer}.java`,
  `config/KvrConfig.java`, `desktop/HostDesktopServer.java`, `desktop/client/{HostDesktopClient,HostDesktopWindow}.java` (переписаны в 0014);
  тесты `spatial/window/{WindowChromeTest,WindowManagerTest}.java`, `spatial/anchor/BodyAnchorTest.java`, `config/KvrConfigTest.java`
- изменены: `spatial/window/{SpatialWindow,WindowManager,WindowSurface}.java`, `spatial/anchor/{SpatialAnchor,BodyAnchor}.java`,
  `spatial/input/{SpatialInput,SpatialRaycast}.java`, `spatial/render/SpatialRenderer.java`, `spatial/ui/{PinnedNote,PowerWindow}.java`,
  `hack/{WindowSummary,SpyRelay}.java`, `hack/client/SpyClient.java`, `script/{ScriptHost,ScriptEngines,BuiltinApps}.java`,
  `script/ui/ScriptWindow.java`, `script/chicory/ChicoryJsEngine.java`, `prelude.js`, `lang/*.json`;
  тесты `ChicoryJsEngineTest`, `WindowSummaryTest`

### Проверить в игре
1. × и шестерёнка есть у окон приложений, сундука-прокси, Заметки, «Питания»; нет у дока, тостов, колеса.
   Сундук: шестерёнка не перекрывает последний слот хотбара (по расчёту — впритык, проверьте глазами).
2. Меню: Тело/Мир и Публично/Лично переключаются, меню остаётся открытым, закрывается своим ×.
   Тело — окно идёт с вами и не крутится за головой; Мир — стоит на месте; далёкое окно на «Тело» подлетает на ≤ 2 м.
3. Заблокировать → планка красная, не тащится, кнопки и × не жмутся, блок сквозь окно не копается, «свернуть всё»
   его не трогает; клик по замку снимает.
4. Два клиента: А делает окно Публичным → Б (даже без очков) видит панель с заголовком, ником А и текстом
   там же, где окно у А. А → Лично (или HUD выкл) → у Б пропадает меньше чем за секунду.
5. Док → Hello → KAV → Заметки («Закрепить» → окно-заметка перед вами) → Калькулятор → Мессенджер на настоящем JS.
6. Рабочий стол: без помощника — «Помощник не запущен»; сервер false → тост «сервер не разрешает».

### Вопрос Создателю: HackGlasses и личные окна
Сейчас «Лично» — строго: личные окна не уходят с компьютера, поэтому HackGlasses видят только публичные
(которые и так видят все) плюс заряд над головой, а тосты больше не читают. Если взлом должен читать и личные
окна (MITM — суть артефакта), скажите: клиент будет слать и личные с пометкой, а сервер отдаст их только носителю
HackGlasses (около 20 строк).

## 0012 — 5a: `ChicoryJsEngine` — QuickJS-ng в WASM на Chicory — ГОТОВО К ЗАБОРУ (не проверено сборкой)

Отчёт 0011 принят (c4f9293, 124/124). Спасибо за проверку фокуса поля ввода.

> **Про спайк, честно.** В отчёте есть `chicory:runtime` и `chicory:wasi`, но не сказано, **какой файл QuickJS .wasm
> запускался**, а цифры не ложатся на таблицу из `BLOCK5_PLAN.md`: «< 2–3 мс» против порога 0.3 мс на тик, а прерывание,
> память на экземпляр и 200 host-вызовов не замерены. Поэтому замеры теперь встроены в тест:
> `ChicoryJsEngineTest.benchmark()` печатает таблицу строками `[kvr-bench]`. **Пришлите эти строки в отчёте 0012 дословно.**
> От них зависит, хватит ли интерпретатора или нужен AOT-компилятор Chicory (тогда задание 0013).

### Архитектура
- **Не готовая обёртка, а свой `.wasm`**: `native/kvrjs/kvrjs.c` (прослойка ~250 строк) + QuickJS-ng,
  сборка `native/kvrjs/build.sh` (wasi-sdk). ABI из 9 функций под наш `JsEngine`, значения только JSON.
  Преимущества: прерывание встроено в QuickJS (`JS_SetInterruptHandler`), без `quickjs-libc` — у скриптов нет файлов и сети,
  очередь Promise прогоняется после каждого `eval`/`call` (нужно для `kvr.fs` и `kvr.msg`).
- `script/chicory/ChicoryJsEngine` — **единственный файл с API Chicory**. Модуль парсится один раз (фоном при старте),
  каждому приложению — свой `Instance` (своя память = песочница), JS-куча ≤ 24 МБ, стек QuickJS 896 КБ при стеке wasm 1 МБ.
  WASI без каталогов и stdio (только часы для `Date.now`). Ошибка в host-функции = ловимый `InternalError` в JS,
  trap/переполнение стека JVM = `JsException`, движок помечается сломанным, игра жива.
- `.wasm` лежит в `src/main/resources/kvr/kvrjs.wasm` (**не** в `assets/`: ресурс-пак не должен подменять движок).
- `ScriptHost`: движок создаётся **на потоке скрипта** (15–20 мс не ложатся на кадр); стек потока 16 МБ;
  старт (`prelude` + `main.js`) ограничен 5 с (`while(true)` на верхнем уровне тоже убивается); первые 3 с после
  старта — прогрев: «не отвечает» не считается, убийство только после 250 мс (иначе холодный JIT убивал бы всё на T5).
- `ScriptEngines.installed()`; lang `kvr.app.no_engine` теперь говорит про отсутствие `kvrjs.wasm`.

### Что сделать агенту (по порядку)
1. **Собрать `kvrjs.wasm`** по `native/kvrjs/README.md` (wasi-sdk 24+, quickjs-ng `v0.10.1`, Linux/macOS/WSL) и закоммитить.
2. **Зависимости** (версия 1.4.0, как в спайке):
   - common: `implementation "com.dylibso.chicory:runtime:1.4.0"`, `implementation "com.dylibso.chicory:wasi:1.4.0"`;
   - fabric и neoforge: те же два + их транзитивные (`chicory:wasm`, `chicory:log` — список сверить через
     `./gradlew :fabric:1.21.1:dependencies`) через `include(...)` (jar-in-jar; Architectury Loom делает это и для NeoForge),
     для dev-запуска NeoForge ещё `forgeRuntimeLibrary(...)`. Допишите свои строки в `AGENT.md`, если вышло иначе.
3. **Init** в клиентском входе обоих загрузчиков, **до** `KVRSpatial.initClient(...)`: `ChicoryJsEngine.install();`
4. `./gradlew :1.21.1:test` — `ChicoryJsEngineTest` без `.wasm` пропускается (skipped), с ним идёт 9 проверок:
   JSON туда-обратно, host-функции и их ошибки, Promise, файл:строка в ошибках, `while(true)` + `interrupt()`, лимит
   памяти, глубокая рекурсия, **все 5 приложений стартуют на настоящем `prelude.js` и отдают UI**, парсер Калькулятора, замеры.
   Строки `[kvr-bench]` видны с `./gradlew :1.21.1:test --tests '*ChicoryJsEngineTest*' -i`.
5. В игре: док → Hello (часы идут, кнопка считает) → KAV → Заметки → Калькулятор → Мессенджер (чек-лист из 0011).
   Отдельно на T1 и T5: появляется ли «не отвечает» без причины.

### Если не собирается
Сигнатуры Chicory 1.4 (`HostFunction(module, name, FunctionType, handle)`, `ImportValues.builder().addFunction`,
`Instance.builder(m).withImportValues`, `WasiPreview1.builder().withOptions`, `Memory.write/readBytes/writeByte`,
`Parser.parse(InputStream)`) писал по памяти — в спайке их не было. Всё в одном файле; пришлите точные
ошибки javac — поправлю за один заход. Ошибки clang — тоже дословно, с тегом quickjs-ng.

### Файлы
- новые: `native/kvrjs/kvrjs.c`, `native/kvrjs/build.sh`, `native/kvrjs/README.md`,
  `script/chicory/ChicoryJsEngine.java`, `src/test/.../script/chicory/ChicoryJsEngineTest.java`
- изменены: `script/ScriptHost.java`, `script/ScriptEngines.java`, `lang/ru_ru.json`, `lang/en_us.json`
- ваш: `src/main/resources/kvr/kvrjs.wasm` (результат `build.sh`), строки Gradle, вызов `install()`

Узлы 1.20.1 / 1.20.4 / 1.21.11 по-прежнему ни разу не собирались. Для 1.20.1 (Java 17) в тесте есть текстовый блок
`"""` — это Java 15+, так что ок; сам Chicory 1.x требует Java 11+.

## 0011 — 5e: системные приложения Заметки / Калькулятор / Мессенджер — ГОТОВО К ЗАБОРУ (не проверено сборкой)

Отчёт 0010 принят. Три правки по нему, **до** забора 0011:
1. **Верните лимит записи VFS на 30 000 байт** (`VfsStore.MAX_WRITE_BYTES`). Текст уходит C2S-пакетом, а у ванили
   потолок 32 767 — с большим лимитом сохранение длинной заметки кикнет игрока с сервера.
2. Убедитесь, что временные `.tmp` (атомарная запись) **не попадают** в `usage()` и `list()` — иначе квота «2 файла»
   на T1 съедается при каждом сохранении.
3. В отчёте 0010 не было `prelude.js` и `ScriptHost.java` — их изменения (`kvr.fs`) заберите, без них `kvr.fs` = `null`.

> **Блокер всё тот же — спайк Chicory.** Всё ниже написано поверх `JsEngine` и оживёт, как только появится
> `ChicoryJsEngine`. Без него окна приложений открываются и пишут «JS-движок не установлен».

### Что внутри
- **Поле ввода `kvr.TextInput`** (`script/ui/TextInputScreen`, `ScriptWindow`). Клик по полю в окне → невидимый
  экран-захват клавиатуры (как чат: игрок стоит, мир и окно видны, игра не на паузе). Набранное сразу видно в окне
  (локальное значение, дерево скрипта отстаёт на тик), каждое изменение → событие `input`, Enter/Esc → `submit`.
  Многострочное: Enter = новая строка, Ctrl+Enter/Esc = готово. Ctrl+V вставка, Ctrl+Backspace — слово.
  `keyPressed/charTyped` без `@Override`, чтобы узел 1.21.9+ (KeyEvent) компилировался. HackGlasses видят и текст полей.
- **«Закрепить у плеча»** (`spatial/ui/PinnedNote`, CONCEPT §4): `kvr.pin(title, text)` → маленькая панель у правого
  плеча (BodyAnchor), идёт за игроком. Системное окно, RAM-слот не занимает. Повторный pin с тем же заголовком заменяет.
- **`ClientInfo`** — снимок координат / измерения / ника / игроков онлайн / языка, обновляется в `KVRSpatial.tickStart`.
  Потоки скриптов читают только снимок, не `Minecraft`.
- **Мессенджер** (`messenger/`): каналы `kvr:msg_send` (C2S), `kvr:msg_ack`, `kvr:msg_recv` (S2C). Сервер — ретранслятор:
  оба игрока в очках с работающей ОС; вложение — файл из `/home` отправителя → `/home/inbox/<от>-<имя>` получателя,
  в его квоте. Ошибки: `OFFLINE`, `NO_GLASSES`, `RECIPIENT_GLASSES_OFF`, `RECIPIENT_FULL`, `RATE_LIMIT` (5 сообщений / 5 с),
  либо код VFS. Входящее всегда даёт тост; если приложение закрыто — ждёт в очереди (до 50), приложение забирает при старте.
  Клиент обрезает поля до лимитов кодека (иначе `writeUtf` бросает исключение в сетевом потоке).
- **Новые `kvr.*` в `prelude.js`**: `kvr.TextInput`, `kvr.pin`, `kvr.player.coords()` → `{x,y,z,dimension}`,
  `kvr.player.name()`, `kvr.players.online()`, `kvr.lang`, `kvr.msg.send(to, text, {attach}) → Promise`,
  `kvr.msg.pending()`, `kvr.msg.on(fn)`. Ops моста: `window.pin`, `player.coords`, `player.name`, `players.online`,
  `client.lang`, `msg.send`, `msg.pending`. **Мост теперь возвращает значения** (JSON-строка из Java).
- **Приложения** (`assets/kvr/apps/`, русский/английский по языку игры, без эмодзи):
  - `notes` — список `/home/*.txt` по 6 на страницу, редактор (имя + многострочный текст), Сохранить (кириллица в
    имени файла транслитерируется — VFS пускает только `[A-Za-z0-9._-]`), Координаты (вставляет `X Y Z (измерение)`),
    Закрепить, Удалить с подтверждением. Переименование удаляет старый файл.
  - `calc` — свой парсер (`+ - * / % ^ ()`, без `eval`), поле + кнопки, результат в стаках по 64 и шалкерах (1728).
  - `messenger` — контакты (онлайн + история, непрочитанные сверху), чат (последние 6), отправка текста и файла из `/home`,
    открыть полученный файл и закрепить его у плеча. История — `/data/messenger/history.json` (40 на контакт).
- Тест: `MessengerProtocolTest` (2).

### Контракт для `ChicoryJsEngine` (дополнение)
- `expose`: значение, которое вернула Java-функция (JSON-строка), адаптер отдаёт в JS **через `JSON.parse`**
  (`kvr.player.coords()` должен вернуть объект, `kvr.player.name()` — строку).
- Как и в 0010: после каждого `eval`/`call` прогонять очередь Promise — все три приложения на Promise.

### Подключение
- Общий init: `MessengerServer.init()` (после `VfsServer.init()`); клиент: `MessengerClient.initClient()`.
- Список приложений: `new BuiltinApps(List.of("hello", "kav", "notes", "calc", "messenger"))`.
- `ClientInfo.tick()` уже вызывается из `KVRSpatial.tickStart` — ничего делать не нужно.

### Файлы
- новые: `script/ClientInfo.java`, `script/ui/TextInputScreen.java`, `spatial/ui/PinnedNote.java`,
  `messenger/MessengerProtocol.java`, `messenger/MessengerServer.java`, `messenger/client/MessengerClient.java`,
  `assets/kvr/apps/{notes,calc,messenger}/{config.json,main.js}`, `src/test/.../messenger/MessengerProtocolTest.java`
- изменены: `script/ScriptHost.java` (`running(appId)`, значения из моста, новые ops, `Listener.pin`),
  `script/ui/ScriptWindow.java` (виджет `input`, `pin`), `vfs/VfsServer.java` (`context(player)`),
  `spatial/KVRSpatial.java` (`ClientInfo.tick()`), `assets/kvr/kvr_os/prelude.js`, `lang/ru_ru.json`, `lang/en_us.json`

### Что проверить в игре (после движка; двумя клиентами для мессенджера)
1. Заметки: создать, набрать 3 строки, Координаты, Сохранить, закрыть/открыть — файл в списке. Закрепить — панель у плеча идёт за игроком.
2. Клик по полю: курсор-режим (Alt) после Enter возвращается корректно? Мышь не «залипает»? (это место я не мог проверить)
3. Калькулятор: `3*64+12` → `= 204`, `3 стаков + 12`; `2^(1+2)` → 8; `5/0` → ошибка с дрожанием.
4. Мессенджер: А пишет Б (у Б приложение закрыто) → у Б тост; Б открывает → сообщение в чате. Отправка заметки файлом →
   у Б в `/home/inbox/`, «Открыть» → «Закрепить». Б снимает очки → у А «У получателя очки выключены».
5. Остальные узлы Stonecutter (1.20.1 / 1.20.4 / 1.21.11) по-прежнему ни разу не собирались.

## 0010 — 5b: VFS на сервере, привязана к очкам — ГОТОВО К ЗАБОРУ (не проверено сборкой)

Отчёт 0009 принят (9420f5e, 115/115).

> **Блокер Блока 5 — спайк Chicory (0007) ещё не прислан.** Без цифр и сигнатур API я не напишу
> `ChicoryJsEngine`, а значит не оживут Hello/KAV и не запустятся Заметки / Калькулятор / Мессенджер.
> Нужно: таблица замеров (пороги в `BLOCK5_PLAN.md`) + 5 сигнатур (создание движка, eval, вызов функции,
> экспорт Java-функции, прерывание) + есть ли ES-модули и как крутить очередь Promise.

### Что внутри (`vfs/`)
- `VfsPath` — нормализация (без `..`, только `[A-Za-z0-9._-]`, глубина ≤ 6, длина ≤ 128) и песочница:
  `/home` и `/tmp` — общие (Заметки пишут `coords.txt`, Мессенджер его отправляет), `/data/<app>` — только своё
  приложение, `/apps` — только чтение, `/system` — запрещено (под Root-квест позже).
- `VfsQuota` — SPEC 2.5: 256 КБ (2 файла) / 1 МБ (5 файлов) / 8 МБ / 64 МБ / 512 МБ.
- `VfsStore` — файлы на диске сервера, квота на каждую запись (замена файла считается честно),
  запись ≤ 30 000 байт (лимит C2S-пакета ваниллы 32 767), чтение ≤ 256 КБ, защита от выхода из корня.
  При первом списке `/` создаёт `/home /tmp /data /apps`.
- `VfsProtocol` — каналы `kvr:vfs` (C2S) и `kvr:vfs_reply` (S2C), операции LIST / READ / WRITE / DELETE / USAGE.
- `VfsServer` — очки получают UUID при первом доступе (`custom_data → kvr_vfs.id`), файлы в
  `<world>/kvr/vfs/<uuid>/`: **украли очки — украли файлы**. Доступ только к надетым очкам с работающей ОС,
  квота по их Storage. Антифлуд: ≤ 40 запросов/с на игрока.
- `vfs/client/VfsClient` — запросы с id, ответы на клиентском потоке, не больше 256 одновременно.
- `PowerNbt` стал public с `readKey/writeKey` — один стык `custom_data` на питание и VFS (правило reuse).
- JS: `kvr.fs.read / write / list / remove / usage` — возвращают Promise и принимают callback `(err, data)`;
  `err.code` = код из `VfsException`. Ответы приходят через `__kvr_event({type:'fs'})` (`ScriptHost` → `VfsClient`).
- Тесты: `VfsPathTest` (3), `VfsStoreTest` (5, @TempDir), `VfsProtocolTest` (1).

### Подключение
- Общий init: `VfsServer.init()`; клиент: `VfsClient.initClient()` (после `KVRNetwork`).
- Для контракта `ChicoryJsEngine`: Promise в `kvr.fs` требуют, чтобы адаптер после каждого
  `eval`/`call` прогонял очередь заданий QuickJS (`JS_ExecutePendingJob` или его аналог). Callback-стиль работает и без этого.

### Как проверить сейчас, без движка
Юнит-тесты покрывают хранилище и песочницу. В игре — после подключения движка (Заметки в 5e).

## 0009 — HUD выкл прячет окна, а не закрывает — ГОТОВО К ЗАБОРУ (маленькое, можно вместе со спайком)

Отчёт 0008 принят (91301cc, 115/115). Закрывает «известное ограничение» из 0008.

- `WindowManager.setHidden(boolean)`: в скрытом режиме `drawable()` и `pick()` пустые, но окна живы и тикают.
- `KVRSpatial.tickStart`:
  - дисплей горит (`glassesWorn`) → всё видно;
  - ОС работает, дисплей тёмный (HUD_OFF) → окна спрятаны, **приложения продолжают работать**;
  - очки сняты, Power Off или села батарея → `closeAll()` (как раньше).
  «ОС работает» читается со стека само (`GlassesPower.active`), `SpatialConfig` менять не нужно.
- Окно «Питание» при перегрузке само открывается только при горящем дисплее; в HUD_OFF спасают
  виньетка + удержание кнопки питания O.
- Файлы: `spatial/window/WindowManager.java`, `spatial/KVRSpatial.java`.
- Проверка: открыть Hello (с движком) → O (HUD выкл) → подождать → O → часы в Hello ушли вперёд, счётчик сохранился.

## 0008 — 5a: рантайм приложений без движка — ГОТОВО К ЗАБОРУ (не проверено сборкой)

Отчёт 0006 принят (a0e4219, 102/102). Задание 0007 (каркас `script/` + ТЗ спайка) входит сюда — если его
не забирали, заберите вместе.

**`ChicoryJsEngine` в этом задании нет сознательно.** Точного API библиотеки я не знаю, а спайк из 0007 ещё
не сделан. Всё остальное для 5a готово поверх `JsEngine`; движок подключается одной строкой
`ScriptEngines.install(ChicoryJsEngine::new)`. До этого окна приложений открываются и пишут «JS-движок не установлен».

### Что внутри (`script/`)
- `ScriptEngines` — фабрика движка. `VrApp` — `config.json` + `main.js` (полный .vrapp — 5d).
- `ModuleShim` — `import { Window } from 'kvr'` работает уже сейчас (переписывается в `const { Window } = kvr`);
  относительные импорты пока отклоняются понятной ошибкой.
- `ScriptHost` — одно приложение = отдельный daemon-поток (не поток рендера, DevNotes 7), тики по CPU,
  очередь UI-событий, последнее дерево виджетов. Общий поток-монитор каждую 1 мс прерывает
  тик, который бежит дольше 4× бюджета (`while(true){}` не вешает игру). 3 медленных тика → «не отвечает».
- `assets/kvr/kvr_os/prelude.js` — KVR OS API в JS: `kvr.Window/Text/Button/Slider/Stack` (как в SPEC 5.2),
  `setTimeout/setInterval/requestAnimationFrame`, `kvr.on/off/emit`, `kvr.toast`, `kvr.window.shake/resize/setTitle`,
  `kvr.sound.play`, `console.log`. Мост: `__kvr_host(op, payload)`; Java дёргает `__kvr_tick(ms)` и `__kvr_event(e)`.
- `script/ui/UiNode` — дерево виджетов из JSON (лимит 512 узлов). `script/ui/ScriptWindow` — окно
  приложения (Kind APP, занимает слот RAM): текст с переносом, кнопки (success/danger), слайдеры, ряды,
  стекло или градиент `linear(#a, #b)`, тряска, оверлей «не отвечает» с кнопкой «Закрыть».
  Скрипт стартует только после того, как WindowManager принял окно (нет потоков-сирот при нехватке RAM).
  Хакер в HackGlasses читает тексты и кнопки приложения (`spyText`).
- `BuiltinApps` — `SpatialConfig.AppHost` с приложениями из джара (`assets/kvr/apps/<id>/`): док, запуск перед игроком,
  частота тиков с CPU надетых очков. Лаунчер / вирт. среда / новое пространство — тост «будет позже».
- Демо: `apps/hello` (счётчик, часы от тика, тост) и `apps/kav` (KAV из CONCEPT §3 целиком: скан, 99%, красный
  градиент, тряска, сирена, resize, «ПОКАКАТЬ И ПЕРЕЗАГРУЗИТЬ»).
- Локализация: `kvr.app.*`, `kvr.os.*`. Тесты: `ModuleShimTest` (4), `UiNodeTest` (2).

### Подключение
- `SpatialConfig(..., new BuiltinApps(List.of("hello", "kav")))` вместо текущей заглушки AppHost.
- После спайка: `ScriptEngines.install(ChicoryJsEngine::new)` в клиентском init.

### Контракт для `ChicoryJsEngine` (проверьте в спайке, что так можно)
- `expose(name, fn)`: JS-функция `name(...args)` → каждый аргумент уходит в Java как JSON-строка,
  возврат Java (JSON-строка) распарсивается обратно в JS-значение.
- `call(fn, jsonArgs...)`: каждый JSON-аргумент парсится и передаётся как значение.
- `interrupt()` вызывается из другого потока и должен привести к `JsException` в текущем `eval`/`call`.
- Если библиотека нативно умеет ES-модули и свой лоадер — напишите в отчёте: тогда в 5d
  `ModuleShim` заменится настоящими импортами.

### Известное ограничение
Режим HUD выкл сейчас закрывает все окна (`KVRSpatial` делает `closeAll`), а значит и убивает приложения.
По SPEC при HUD-off ОС жива — переделаю на «скрыть, не закрывать» вместе с движком.

## 0007 — Блок 5: решения Создателя + каркас скриптов + ТЗ на спайк (не срочно, после 0006)

- Решения и **ТЗ на спайк QuickJS-on-WASM (Chicory)** — в начале `BLOCK5_PLAN.md`: что мерить,
  пороги «тянет / не тянет», что прислать (цифры + сигнатуры API библиотеки). Спайк — отдельный
  проект на ноутбуке, в мод не коммитится.
- Код, не зависящий от движка (`src/main/java/com/kasperstudios/kvr/script/`):
  - `JsEngine` — интерфейс: `eval`, `call` (JSON), `expose` (host-функции для `kvr.*`), `interrupt`, `memoryUsed`.
    Под него пишется `ChicoryJsEngine` по итогам спайка (или JNI-запасной).
  - `ScriptClock` — 10/20/50/100/200 Гц по тиру CPU, не догоняет больше 3 тиков после лага.
  - `Watchdog` — бюджет 25% тика; 3 промаха подряд → «не отвечает»; один тик > 4× бюджета → `interrupt()`.
- Тесты: `ScriptClockTest` (3), `WatchdogTest` (4).
- HackGlasses — подтверждено Создателем как есть (артефакт без питания), правок к 0006 нет.

Порядок: отчёт по 0006 → спайк → я пишу 5a (`ChicoryJsEngine` + `ScriptHost`: запуск .vrapp в окне, `kvr.timers`).

## 0006 — HackGlasses, часть 1 (хвост Блока 4) + план Блока 5 — ГОТОВО К ЗАБОРУ (не проверено сборкой)

Отчёт 0005 принят (6b77222, 95/95). `KVRNetwork` записан в `AGENT.md`, дальше все пакеты — канальные.

**Важно:** «Блок 3 завершён» пока = собран и протестирован на 1.21.1. В игре не запускался,
ноды 1.20.1 / 1.20.4 / 1.21.11 не собирались ни разу. Чек-листы — в 0003 и 0005.

### Синхронизация с вашими правками
- `DockPlateBlock.properties()` → `dockProperties()` (как у вас).
- `SetPowerStatePayload` переписан под канальный `KVRNetwork`: `CHANNEL`, `write(buf)`, `read(buf)`
  (без `CustomPacketPayload`). Если у вас уже своя адаптация — можно оставить свою.

### HackGlasses — что сделано (`hack/`)
- `HackGlasses` — константы (0.5%, дальность 32 блока, конус 8°, 2 Гц) и `isWearing(player)` (слот шлема).
- `WindowSummary` — сводка окон игрока (поза в мире, размер, заголовок, текст ≤ 256 символов,
  ≤ 16 окон) + заряд. Жёсткие лимиты против флуда.
- `SpyRelay` (сервер): каналы `kvr:window_summary` (C2S, 2 Гц от каждого клиента с включённым дисплеем)
  и `kvr:spy_view` (S2C хакеру: сводка игрока в центре его взгляда, в пределах 32 блоков и прямой видимости).
- `client/SpyClient` + `client/GhostRenderer` — окна жертвы рисуются призраками ровно там, где их видит
  жертва: зелёная сетка, «[ник] заголовок», текст окна, заряд над головой. Держатся 3 с после взгляда.
- Что читает хакер (`SpatialWindow.spyText()`): тосты, состояние питания и таймер перегрузки,
  **содержимое откреплённых сундуков**. Приложения Блока 5 добавят своё (заметки, чат).
- `RenderCompat.text(...)` — текст в мире как на табличке (ветка <1.21.5).
- Лут: `platform/KVRLoot` + `FabricKVRLootProvider` (loot v3 с 1.20.5 / v2 до) + `NeoForgeKVRLootProvider`
  (`LootTableLoadEvent`, именованный пул). Добавляется во **все** таблицы `chests/*` (ванилла, датапаки, моды).
- Скрытие из JEI/REI/EMI: тег `data/c/tags/item/hidden_from_recipe_viewers.json` (на 1.20.x папка `items/`;
  сами вьюверы читают этот тег с 1.20.5+). В креатив-вкладку предмет просто не добавлять.
- `KVRServer.Provider.onServerTick` — новый хук (Fabric `END_SERVER_TICK`, NeoForge `ServerTickEvent.Post` / 20.4 `TickEvent`).
- Текстура: `textures/misc/hack_grid.png` (страница `/kvr` → «GUI-спрайты» → «Сетка взлома», тайлится).
  Иконка `hackglasses` и 3D `hackglasses_worn` уже были.
- Тесты: `WindowSummaryTest` (2), `SpyMathTest` (3).

### Подключение
1. `KVRContent`: `kvr:hackglasses` без креатив-вкладки и без рецепта; `HackGlasses.install(s -> s.is(HACKGLASSES))`.
2. Установить `KVRLoot` (провайдер на каждый загрузчик), затем `KVRLoot.addToEveryChest(() -> HACKGLASSES, HackGlasses.CHEST_CHANCE)`.
3. Общий init: `SpyRelay.init()` (после `KVRServer` и `KVRNetwork`). Клиент: `SpyClient.initClient()`.
4. Дописать `onServerTick` в ваши версии `FabricKVRServerProvider` / `NeoForgeKVRServerProvider`.

### Как проверить (нужны 2 клиента)
1. Игрок A в KVR-очках: включить (O), открепить сундук в 3D, открыть окно «Питание».
2. Игрок B: `/give @s kvr:hackglasses`, надеть, посмотреть на A → зелёные окна-призраки с
   содержимым сундука и заряд над головой A. Отвести взгляд → через 3 с исчезают.
3. JEI/EMI: `hackglasses` не ищется. Лут: `/loot spawn ~ ~ ~ loot minecraft:chests/simple_dungeon` много раз.

### Вопрос Создателю по HackGlasses
Они работают как артефакт **без питания и сборки** (сейчас так) или сами — полноценные очки с
фиксированным железом и батареей? Часть 2 (инъекция кликов, перехват Rednet) — после Блока 5.

### Блок 5
План и 6 вопросов — `BLOCK5_PLAN.md` (движок QuickJS: JNI / WASM / Rhino; где выполняется JS;
где лежит VFS; скачивание из Store; бюджет watchdog; браузер).

## 0005 — Блок 3b + 3c: код док-плиты, пакет питания, окно «Питание» — ГОТОВО К ЗАБОРУ (не проверено сборкой)

Отчёт 0004 принят (76af3ba). **Просьба: в следующем отчёте приложите `KVRNetwork.java` и фрагмент
регистрации из `KVRContent`** — дальше пишу сразу под их API, без адаптеров.

### Изменение плана 3b: S2C-пакет тревоги не нужен
Данные питания лежат на стеке очков и пишутся сервером раз в секунду, ванилла сама синхронизирует слот.
Клиент читает перегрузку, таймер и заряд прямо со стека (`PowerClient`). Остаётся один пакет:
C2S `SetPowerStatePayload` (Вкл / HUD выкл / Power Off).

### Док-плита (`dock/`)
- `DockPlateBlock` — свойства `facing / has_glasses / charging / solar` (как в blockstate), форма нажимной плиты,
  без коллизии, свет 7 при зарядке. ПКМ очками — положить, ПКМ солнечным модулем — установить,
  ПКМ пустой рукой — забрать очки, Shift + пустая рука — забрать модуль, поломка — всё выпадает.
  Ветки: `use` (<1.20.5) / `useItemOn`+`useWithoutItem` с `ItemInteractionResult` (1.20.5–1.21.1) /
  `InteractionResult` (1.21.2+); `onRemove` (<1.21.5) / `preRemoveSideEffects` (1.21.5+, VERIFY).
- `DockPlateBlockEntity` — буфер 4000 FE, редстоун 1 FE/t, солнце 4 FE/t (день + открытое небо + без дождя),
  зарядка очков 50 FE/t шагами по 20 тиков. Питание очков на плите тоже считается (иначе
  время на плите списалось бы задним числом); перегрузка на плите взрывает плиту (исход «на земле»).
  Сохранение: `CompoundTag + HolderLookup` (1.20.5–1.21.5), старый `load` (<1.20.5), `ValueInput/Output` (1.21.6+, VERIFY).
- `DockLogic` — чистые правила + `DockLogicTest`. `KVRDock` — стыковка с регистрами.
- `OverloadEffects` дополнен: `startAt`, `detonateAt`, `siren` теперь public.

### Модовая энергия (опционально)
- `platform/KVREnergy` + `NeoForgeKVREnergyProvider` (capability `IEnergyStorage`, без зависимостей).
- `FabricKVREnergyProvider` — Team Reborn Energy. **Копировать только вместе с зависимостью**
  `include(modImplementation("teamreborn:energy:<версия>"))` в fabric; без неё файл не соберётся. Без провайдера
  плита просто работает от редстоуна и солнца.

### Сеть и клиент
- `power/SetPowerStatePayload` (`CustomPacketPayload` + `StreamCodec`, ветка `FriendlyByteBuf` до 1.20.5),
  `power/PowerServerHandler` (Power Off во время перегрузки = спасение + сообщение).
- `power/client/PowerClient` — снимок питания надетых очков, стробоскоп 4/4 тика.
- `power/client/PowerKey` — **физическая кнопка питания (O)**: коротко — Вкл / HUD выкл, удержание 1 с —
  Power Off. Без неё из выключенных очков не выйти: когда дисплей погашен, окон нет.
- `spatial/ui/PowerWindow` — парящее окно «Питание» (батарея, ток/лимит, °C, три кнопки). Открывается
  кликом по статусу дока и **само перед игроком при перегрузке** с одной красной кнопкой Power Off.
- Стробоскоп: `SpatialRenderer` красит все окна красным; `spatial/render/AlarmOverlay` — красная виньетка.
- `KVRClientProvider.onHudRender` — новый хук (Fabric `HudRenderCallback`, NeoForge `RenderGuiEvent.Post`).
- `PowerData.displayOn` / `GlassesPower.displayOn` — для `SpatialConfig.glassesWorn` теперь брать **displayOn**
  (HUD_OFF прячет все окна), а не `active`.

### Подключение
1. `KVRContent`: блок `new DockPlateBlock(DockPlateBlock.properties())`, BE-тип, затем
   `KVRDock.install(() -> DOCK_PLATE_BE, s -> s.is(SOLAR_UPGRADE), () -> new ItemStack(SOLAR_UPGRADE))`.
2. Энергия (по желанию): `KVREnergy.install(...)` + `KVREnergy.registerDockPlate(() -> DOCK_PLATE_BE)`.
3. `KVRNetwork`: серверный ресивер `SetPowerStatePayload` → `PowerServerHandler.handle(player, payload)`
   (на серверном потоке); на клиенте `PowerClient.installSender(s -> KVRNetwork.sendToServer(new SetPowerStatePayload(s)))`.
4. Fabric: `BlockRenderLayerMap.INSTANCE.putBlock(DOCK_PLATE, RenderType.translucent())`.
5. `SpatialConfig.glassesWorn = () -> GlassesPower.displayOn(head, level.getGameTime())`.
6. Локализация: новые ключи `kvr.power.window`, `kvr.power.state.*`, `kvr.power.btn.*`, `kvr.power.overload_left`,
   `kvr.power.booting`, `kvr.overload.detonated_dock`, `key.kvr.power`.

### Тесты
`DockLogicTest` (5) + `PowerDataTest.hudOffIsActiveButDisplayDark` — всего +6.

### Чек-лист в игре
1. Поставить плиту, рядом рычаг (вкл) → буфер растёт 1 FE/t. ПКМ очками → лежат на плите, видна голограмма,
   контур яркий. ПКМ пустой рукой → очки в инвентаре, `/kvr power status` показывает заряд.
2. Солнечный модуль днём под небом → текстура `dock_plate_solar`, буфер растёт.
3. Надеть очки, O → через бут появляется док; клик по батарее в доке → окно «Питание».
4. T5-детали + T1-батарея → окна мигают красным, виньетка, само открывается окно с таймером;
   клик по POWER OFF (или удержание O) → «Цепь разомкнута».

## 0004 — Блок 3c: текстуры и модели док-плиты — ГОТОВО К ЗАБОРУ

Отчёт 0002 принят: 3a в dccd24f, 89/89. 3b (`KVRNetwork`) и код 3c (`DockPlateBlock` + BE) пишете 