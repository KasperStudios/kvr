# CHANGELOG — Java-код KVR из чата

Каждая запись — одно задание. В «Файлы» только то, что добавлено или изменено.

## 0031 — Ввод в окнах ПК (раскладка, хоткеи, колесо, общий буфер) + лаунчер очков — ГОТОВО К ЗАБОРУ (не проверено сборкой)

По живому тесту на Arch + niri (foot, Firefox).

**Важно про границы.** Пункт 4 (лаунчер) и часть пунктов 1–2 исправлены прямо в коде дерева.
Ввод в окнах ПК живёт в файлах, которых в дереве **нет**:
- D1/D2: `DesktopBridge`, `DesktopStream`, `DesktopProtocol`, `DesktopClipboard`, `HostDesktopWindow` на потоках (здесь лежит TCP-версия 0013), миксин захвата клавиатуры;
- весь Rust `kvr-desktopd`.
Поэтому логика сделана **целиком, отдельными модулями**, а подключение к вашим файлам — точными строками ниже («Агенту: подключение»).
Пришлите эти файлы и `native/kvr-desktopd/` — подключу сам в 0031a.

### 1. Клавиатура: раскладка, хоткеи, «залипающий капс»
**Причины** (на них указывает описание D1 в `DESKTOP_PLAN`: «шлёт GLFW key, scancode, модификаторы», «scancode GLFW = X keycode = evdev + 8»):
| Что | Чем кончается |
|---|---|
| Биты `mods` из GLFW передаются как маска xkb. У GLFW Shift=1, **Ctrl=2**, Alt=4, Super=8; у xkb Shift=1, **Lock(Caps)=2**, Control=4, Mod1(Alt)=8 | **Ctrl включает Caps Lock** → «пишет капсом», Ctrl+C/V не работают, Alt становится Ctrl |
| `evdev = scancode − 8` верно только для X11/XWayland. В GLFW 3.4 под нативным Wayland scancode **уже равен** evdev | все клавиши сдвинуты на 8: M → Left Shift, J → Enter, F8 → Caps Lock… |
| События `GLFW_REPEAT` пересылаются | двойные буквы, повторно «нажатые» модификаторы (клиенты Wayland повторяют сами) |
| Рука ушла с окна с зажатым Shift/Ctrl | отпускание не доходит → модификатор навсегда нажат в приложении |
| Кеймап демона собран без имён и без `XKB_DEFAULT_*` | только `us`: русский в foot/Firefox невозможен |
| Left Alt — тумблер KVR | Alt-комбинации до приложения не доходят |

**Новая схема: как настоящая клавиатура у настоящего композитора.**
- `DesktopKeys` (новый): GLFW key → evdev по таблице (буквы, цифры, пунктуация, F1–F24, навигация, цифровой блок, модификаторы, ISO-клавиша, Menu).
  GLFW key — физическая позиция на любом бэкенде, поэтому не зависит ни от X11/Wayland, ни от раскладки. Scancode больше не используется.
- `DesktopKeyboard` (новый) шлёт **только нажатие/отпускание evdev**:
  - никаких модификаторов и повторов;
  - модификаторы, Caps, группу раскладки и автоповтор считает xkb внутри демона (`KeyboardHandle::input`);
  - помнит нажатое; `releaseAll()` при потере фокуса отпускает всё + `KEYS_UP` демону;
  - **Left Alt: тап или аккорд.** Alt нажат и отпущен один → тумблер KVR, приложение его не видит. Во время Alt нажата другая клавиша → приложение получает Alt, затем клавишу (Alt+Tab, Alt+F, **Alt+Shift = смена раскладки**). Right Alt (AltGr) идёт всегда.
- **Раскладки** (работает даже со старым демоном, если он создаёт клавиатуру с `XkbConfig::default()`): `DesktopDaemon.applyXkb` выставляет процессу демона `XKB_DEFAULT_LAYOUT/VARIANT/OPTIONS`. libxkbcommon читает их при пустых именах.
  - Раскладка: `desktop_xkb_layout` в `kvr-client.toml` → иначе `XKB_DEFAULT_LAYOUT` сессии → иначе по языку игры (`ru` → `us,ru`, `uk` → `us,ua`, `be` → `us,by`) → иначе `us`.
  - Переключение: `desktop_xkb_options`, по умолчанию `grp:alt_shift_toggle`. Другие: `grp:win_space_toggle`, `grp:ctrl_shift_toggle`, `grp:caps_toggle`.
  - Новые ключи `kvr-client.toml`: `desktop_xkb_layout`, `desktop_xkb_variant`, `desktop_xkb_options`.
  - Раскладка внутри KVR не зависит от раскладки niri — как у второй клавиатуры. Переключается комбинацией, когда рука на окне.
  - Демон сообщает активную раскладку (`KEYMAP`) → `DesktopInputSession.layoutLabel()` («US» / «RU») для индикатора в заголовке окна.

### 2. Колесо мыши в окнах ПК
- **Исправлено в дереве: `MouseHandlerMixin`.** Колесо отбрасывалось при любом открытом экране (`screen != null`), включая невидимый экран захвата клавиатуры окна ПК — то есть именно тогда, когда игрок работает в окне.
  Теперь экраны с маркером `SpatialCaptureScreen` (новый) колесо пропускают; `TextInputScreen` его уже реализует. Ванильные экраны — как раньше.
- **Единицы (`DesktopScroll`, новый).** Minecraft даёт «щелчки» (+1 = вверх), Wayland ждёт обратный знак, пиксели и `value120`.
  Шёл сырой щелчок = 1 px → страница едва двигалась. Теперь 15 px + 120 на щелчок, дроби тачпада накапливаются, есть горизонталь.
- Демон (`input_0031.rs`): `AxisFrame` с `AxisSource::Wheel`, `value` + `v120`, затем `pointer.frame()`. Без `frame` часть клиентов (GTK, Firefox) событие не применяет.

### 3. Общий буфер обмена
Буфер Minecraft — это и есть системный буфер (GLFW на Wayland держит свой `wl_data_source`). Синхронизируем его с скрытым композитором (`HostClipboardSync`, новый):
- **хост → приложение:**
  - перед Ctrl+V / Ctrl+Shift+V (терминалы) / Shift+Insert мод шлёт системный текст (`CLIP_SET`), затем саму клавишу — по одному сокету, так что текст у демона раньше, чем приложение его запросит;
  - плюс раз в секунду, пока у окна ПК клавиатура (вставка из меню приложения), и в момент начала печати;
  - демон делает текст своим selection (`set_data_device_selection`) и отдаёт его в `send_selection`.
- **приложение → хост:** клиент делает `set_selection` → демон читает текст через пару сокетов в фоновом потоке (тайм-аут 2 с) → `CLIP_CHANGED` → мод ставит его в системный буфер. Он сразу доступен в niri, в чате Minecraft и в других окнах KVR.
- Только текст (`text/plain;charset=utf-8`, `text/plain`, `UTF8_STRING`, `STRING`), до 1 Мибисимволов. Без эха: пришедшее от демона не уходит обратно. Выключатель — прежний `desktop_clipboard_sync`.
- Если ваш `DesktopClipboard` (D2) делал часть этого — его сообщения про буфер следует убрать, чтобы не было двух источников истины. `HostDesktopClient.reset()` теперь сбрасывает и `DesktopInputSession`.

### 4. Лаунчер очков — только установленное (исправлено в дереве)
- **Причина:** `AppLauncherWindow` строил список из `BuiltinApps.SYSTEM_APPS` — все JS-приложения джара (hello, kav, notes, calc, messenger), независимо от установки.
  А `BuiltinApps.launch` запускал любой из них прямо из джара.
- **Сейчас в лаунчере строго:**
  1. App Market, Messenger (`PREINSTALLED`), Настройки;
  2. «Приложения ПК» — только если включены (пункт ОС, как в доке);
  3. пакеты, установленные в **надетые** очки (`AppsClient.installed()`), с иконками;
  4. последняя карточка «+ Ещё программы» → App Market.
- Пока сервер не прислал список — надпись «загрузка…», а не чужой список. Больше 10 строк — прокрутка колесом.
- **Вторая линия защиты, `BuiltinApps.launch`:** из джара запускается только id из `ids` (`PREINSTALLED` + «Рабочий стол»). Установленные встроенные (notes, calc…) идут как пакеты через `AppCatalog`. Любой другой id → тост «… не установлено — установите в App Market» и открывается Market.
  **Агенту:** проверьте, что `KVRClientBootstrap` создаёт `new BuiltinApps(BuiltinApps.PREINSTALLED)` (0027), а не `SYSTEM_APPS`. Иначе защита пропустит всё.
  Ваши `ide` / `player`, если они должны быть в чистых очках, — только через `index.json` и установку (решение Создателя 0027: ровно три программы).

### Агенту: подключение (ваши файлы — их в дереве нет)
**Java** — всё через `DesktopInputSession` (один на окно ПК):
```java
// HostDesktopWindow (потоковая версия D1)
private final DesktopInputSession input = new DesktopInputSession(stream.id(), stream.bridge()::sendFrame, KVRSpatial.cursor()::toggle);
// захват клавиатуры (ваш миксин/экран) — ВМЕСТО старого сообщения с key/scancode/mods и ВМЕСТО charTyped:
if (input.key(glfwKey, action)) cancel();
// рука ушла с окна / окно закрыто / открыт ванильный экран / игра потеряла фокус (Minecraft#isWindowActive):
input.blur();
// tick() окна:
input.tick(hasKeyboard);
// mouseScrolled(x, y, amount) — ВМЕСТО старого scroll-сообщения:
input.scroll(amount); return true;
// onClosed(): input.blur();
// заголовок окна (по желанию): текст + "  " + DesktopInputSession.layoutLabel()

// DesktopBridge, чтение кадра от демона (payload — без 8-байтового заголовка):
if (DesktopInputSession.fromDaemon(type, payload)) continue;
// DesktopBridge: метод sendFrame(byte[]) — записать готовый кадр в сокет в порядке очереди (если есть свой writer — адаптер в одну строку)
```
- Экран захвата клавиатуры окна ПК (если это `Screen`): `implements SpatialCaptureScreen`, иначе колесо так и не дойдёт.
- `KVRSpatial.cursor()::toggle` — ваш метод тумблера Alt (имя подставьте). Left Alt в захвате теперь решает `DesktopKeyboard`: обычная привязка Alt не должна срабатывать на нажатие, пока окно держит клавиатуру.
- Удалить из вашего захвата: передачу `modifiers`, `scancode`, `charTyped` в демон (текст делает xkb демона, иначе буквы задвоятся).

**Rust** — `native/kvr-desktopd/src/input_0031.rs` (новый, Smithay 0.6), четыре точки `INTEGRATE`:
1. поле `input0031: Input0031` в `State` (`Input0031::new(&loop_handle)`);
2. `State::send_to_mod(ty, payload)` — ваш IPC-писатель кадра моду;
3. диспетчер сообщений: `if input_0031::handle(state, ty, payload) { continue }`;
4. `impl SelectionHandler for State`: `type SelectionUserData = Arc<[u8]>`, `new_selection` / `send_selection` → функции модуля (+ `DataDeviceState` / `delegate_data_device!`, если D2 их ещё не завёл).
И ещё две правки:
- клавиатура сиденья — `seat.add_keyboard(XkbConfig::default(), 400, 30)`: пустые имена → берутся `XKB_DEFAULT_*` от мода; 400/30 — задержка и частота автоповтора для клиентов;
- удалить старую обработку key с `scancode - 8` и ручными модификаторами.

**VERIFY (написано по документации Smithay, не компилятором):**
| Где | Что | Если не сошлось |
|---|---|---|
| `key()` | `KeyboardHandle::input` принимает xkb-keycode = evdev + 8 | если в 0.6 он ждёт evdev — убрать `+ 8` (одна строка). Признак: каждая буква на место правее/левее |
| `report_group()` | `modifier_state().serialized.layout_effective` | удалить вызов — пропадёт только индикатор US/RU |
| `axis()` | `AxisFrame::v120` | на старом API — `discrete(ax, v120 / 120)` |
| `set_host_clipboard` | `set_data_device_selection(&DisplayHandle, &Seat, Vec<String>, UserData)` | сигнатура из ошибки |
| `new_selection` | `SelectionSource::mime_types()`, `request_data_device_client_selection(&Seat, String, OwnedFd)` | сигнатура из ошибки |
| `now_ms()` | `rustix::time::clock_gettime` (rustix уже есть в зависимостях Smithay) | любой монотонный таймер в мс |
| `DesktopInputProtocol` | id 0x31–0x36 не заняты в вашем `DesktopProtocol` | поменять константы в обоих файлах |

**Lang (слить):**
```json
"kvr.launcher.more": "Ещё программы" / "Get more apps",
"kvr.launcher.more_hint": "App Market: установить в эти очки" / "App Market: install into these glasses",
"kvr.launcher.loading": "загрузка…" / "loading…",
"kvr.launcher.running": "● работает" / "● running",
"kvr.launcher.start": "запустить" / "start",
"kvr.launcher.not_installed": "«%s» не установлено — установите в App Market" / "\"%s\" is not installed — get it in App Market"
```

### Чек-лист (Arch + niri, после подключения)
**Клавиатура (foot):**
1. Напечатать `echo Hello` + Enter → регистр как набран; Enter выполняет, регистр не меняется.
2. Alt+Shift → `привет` печатается кириллицей, в заголовке окна «RU»; Alt+Shift → снова латиница.
   Alt, нажатый один, переключает режим руки KVR, как раньше.
3. Ctrl+C прерывает `sleep 100`; Ctrl+L чистит экран; стрелки и Tab работают; Caps Lock включает и выключает заглавные.
4. Зажать Shift, увести руку с окна, отпустить, вернуть руку → буквы строчные.
5. Зажать `a` → автоповтор с нормальной скоростью, без двойных букв.
6. Minecraft под нативным Wayland и под XWayland — пункты 1–5 одинаково (scancode больше не используется).
7. `desktop_xkb_layout = "us,ru"`, `desktop_xkb_options = "grp:win_space_toggle"` → после перезапуска демона переключение по Super+Space.

**Колесо:**
8. Firefox: колесо листает страницу нормальным шагом, в правильную сторону (на себя = вниз). foot: история прокручивается. Списки GTK листаются.
9. Во время печати (клавиатура захвачена) колесо тоже листает окно. В инвентаре (E) колесо ведёт себя как в ваниле.

**Буфер:**
10. В niri `wl-copy привет` → в foot внутри KVR Ctrl+Shift+V → `привет`. В Firefox внутри KVR Ctrl+V → то же.
11. В Firefox внутри KVR выделить текст, Ctrl+C → в niri `wl-paste` печатает его; в чате Minecraft Ctrl+V вставляет его.
12. Копирование из foot в другое окно KVR (Firefox) работает без участия хоста (один композитор).
13. `desktop_clipboard_sync = false` → ни 10, ни 11 не работают; 12 работает.

**Лаунчер очков (жест 1):**
14. Чистые очки → в списке ровно App Market, Messenger, Настройки (+ «Приложения ПК», если включены) и «+ Ещё программы». Никаких Notes/Calc/KAV/Hello.
15. Market → установить Calc → Calc появился в лаунчере и запускается. Удалить → исчез.
16. Снять эти очки, надеть другие (без Calc) → Calc в лаунчере нет.
17. Из dev-приложения вызвать запуск `notes` через док-id (или старый пункт дока) без установки → тост «не установлено» и открывается Market.

### Файлы
- **новые (Java):**
  - `desktop/DesktopInputProtocol.java`;
  - `desktop/client/input/{DesktopKeys,DesktopKeyboard,DesktopScroll,HostClipboardSync,DesktopInputSession}.java`;
  - `spatial/input/SpatialCaptureScreen.java`.
- **новый (Rust):** `native/kvr-desktopd/src/input_0031.rs`
- **переписан:** `spatial/ui/AppLauncherWindow.java`
- **изменены:**
  - `script/BuiltinApps.java` (запуск только установленного);
  - `mixin/client/MouseHandlerMixin.java` (колесо при экране захвата), `script/ui/TextInputScreen.java` (`implements SpatialCaptureScreen`, одна строка);
  - `config/KvrConfig.java` (`desktop_xkb_*`, `desktopXkb()`), `desktop/client/DesktopDaemon.java` (`applyXkb`), `desktop/client/HostDesktopClient.java` (сброс ввода).
- **ваши (подключение выше):** `HostDesktopWindow` (D1), `DesktopBridge`, миксин/экран захвата клавиатуры, `DesktopClipboard`, `kvr-desktopd` (`main.rs`/state, seat, диспетчер, `SelectionHandler`).
- **lang:** ключи выше.

## 0030 — Расхождения 0028/0029 + устойчивость мультиплеера — ГОТОВО К ЗАБОРУ (не проверено сборкой)

Исправлены пять расхождений кода с описанием 0028/0029 и сделана защита от крашей при входе и выходе игроков.
**Про `RegistrySyncManager.unmap` NPE, `PublicWindowRelay` и `HandPoseRelay` — см. «Что не сделано»**: этих файлов и краш-лога в дереве нет.

### 1. Приложения ПК: один источник закреплений
- **`DesktopLauncherWindow` переписан на `DesktopApps` + `DockPrefs`** (как было описано в 0028):
  - вкладки «Недавние» (до 20, сначала новые) и «Все приложения» (по алфавиту); старт на «Недавних», если история есть;
  - строка: иконка 16×16 (`PackageIcons`, лениво, только видимые строки; без PNG — плитка с буквой), имя · описание (`Name[ru]`), ★/☆ справа;
  - файлы `.desktop` из XDG + Flatpak/Snap, `TryExec`, `Terminal=true` — всё из `DesktopApps` (0028);
  - **поиск**: строка поиска под заголовком, клик → захват клавиатуры через `TextInputScreen`, список фильтруется на лету (имя, id, описание); Enter/Esc — готово, × — очистить;
  - прокрутка колесом: `mouseScrolled(double, double, double)` **с `@Override`** (в присланной версии был четырёхаргументный метод без `@Override` — он никогда не вызывался);
  - внизу: состояние демона · «программ: N».
- **Единственное хранилище — `DockPrefs` (`config/kvr/dock.json`).** ★ в лаунчере пишет туда же, откуда читает док → закреплённое в доке со следующего кадра.
  - `KvrUiConfig.getPinnedDesktopApps/togglePinnedDesktopApp/getRecentApps/recordRecentApp` помечены `@Deprecated`, KVR их больше не зовёт.
  - **Миграция:** если в `kvr-ui.toml` есть ключ `desktop_pinned_apps`, его команды один раз переносятся в `dock.json` как `deskcmd:`-закрепления, ключ очищается.
    Встроенный список по умолчанию (firefox/foot/thunar) **не** переносится: программы ПК сами не закрепляются (0028).
  - `DockPrefs.recent(prefix, limit)` — новый метод для вкладки «Недавние».
  - `SystemAppScanner` удалён (дубль `DesktopApps`). **Агенту:** удалите его и в репо, если `grep SystemAppScanner` больше ничего не находит.
- **Запуск с проверкой политик (`HostDesktopClient.launchApp`, новый).** Док и лаунчер идут через ту же цепочку, что и пункт «Рабочий стол»:
  локальный выключатель → политика сервера → согласие на этот бинарник → бинарник / проверенная загрузка → демон → лимит окон.
  - Раньше `DesktopDock.launch` звал `launch()` напрямую, и до ответа сервера политика не проверялась.
  - Всё, что требует ответа (запрос политики, экран согласия, загрузка), потом **повторяет именно этот запуск** (флаг `openWhenAnswered` заменён на `Runnable`).
  - `launch(List)` стал private: запуск без проверок больше невозможен.
  - В 0028 этот метод был назван `launchApp` — имя таким и осталось.

### 2. Слот модема и настоящий мост к CC
- **`WearableGlassesItem.overrideOtherStackedOnMe`** (новый): ПКМ модемом CC на курсоре по очкам — модем в слоте (`GlassesModem.install`, списывается один из стака);
  ПКМ пустым курсором — модем на курсоре (`GlassesModem.remove`).
  - В слоте уже есть модем → клик поглощается без изменений (никакой случайной подмены очков модемом).
  - CC убрали из сборки → модем остаётся в слоте, ПКМ берёт очки как раньше.
  - Как у мешочка: работает на обеих сторонах, серверный результат приходит обычной синхронизацией слота.
- **`CcRednetProvider` — настоящий мост через рефлексию** (вместо заглушки «inert»):
  - `ComputerCraftAPI.getWirelessNetwork(MinecraftServer)` (или без аргумента на старых сборках), `addReceiver/removeReceiver`, `transmitSameDimension(Packet, range)` / `transmitInterdimensional` для эндер-модема;
  - `PacketReceiver` и `PacketSender` — `java.lang.reflect.Proxy` (equals/hashCode по идентичности, default-методы через `InvocationHandler.invokeDefault`);
  - фильтр прямо на потоке CC: только наш канал `id % 65500` и broadcast, свои же пакеты отбрасываются. Для этого в `KVRRednet.Placement` добавлен `rednetId()`;
  - дальность как в конфиге CC по умолчанию: 64 блока, выше Y 96 линейно до 384 к Y 320; эндер — без предела;
  - никаких исключений в поток CC; смена имен в API CC = одна строка в логе «bridge failed to start», не краш.
  - **Агенту (Gradle):** API CC при компиляции больше **не нужен** ни на одной ноде. Строки `compileOnly("cc.tweaked:…-common-api…")` и `exclude("com/kasperstudios/kvr/rednet/cc/**")` из 0029 можно убрать.
    Репозиторий squiddev нужен только для `modLocalRuntime` в dev-запуске. SVC `compileOnly` остаётся (его использует `KvrVoicechatPlugin`).

### 3. `ClientCompat.drawFace` — ветки Stonecutter
| Нода | Реализация |
|---|---|
| 1.20.1 | `PlayerInfo#getSkinLocation()` / `DefaultPlayerSkin.getDefaultSkin(uuid)` + `PlayerFaceRenderer.draw(g, ResourceLocation, x, y, size)` |
| 1.20.2 … 1.21.4 (вкл. 1.21.1) | `PlayerInfo#getSkin()` / `DefaultPlayerSkin.get(uuid)` (`PlayerSkin`) + `PlayerFaceRenderer.draw(g, PlayerSkin, x, y, size)` |
| 1.21.5+ (1.21.11) | нейтральная плитка `fill`: рендера пространственного UI там нет (`RenderCompat.SUPPORTED = false`), `PlayerSkin` в 1.21.9 переехал — имена не угадываются |

`PlayerFaceRenderer` рисует лицо вместе со слоем шляпы. Проблемный `g.blit(ResourceLocation, …)` (убран в 1.21.2) больше не используется.

### 4. Мультиплеер: краши и утечки сессий
Найденные в дереве пути к крашу и что с ними сделано:
| Путь | Чем кончалось | Исправление |
|---|---|---|
| Исключение в колбэке `onServerTick` (SVC бросает на игрока, который только что вышел; CC сменил API) | **краш всего сервера** | `ServerSession.guard(…)`: тик теряется один раз, ошибка в лог (не чаще раза в 10 с). Подключено в Rednet, Calls, Spy relay. В `CallServer` все вызовы SVC (`join`, `createGroup`, `removeGroup`, `connected`, `groupOf`) в try/catch |
| S2C-пакет игроку без KVR (ванильный / другой клиент) | NeoForge отклоняет, Fabric может отключить игрока | `KVRNetwork.canSend` / `sendIfAccepted`: Rednet, Calls, Spy relay |
| C2S-пакет с KVR-клиента на сервер без KVR (сводка шпиона шлётся каждые 10 тиков) | NeoForge: исключение в тике клиента = краш клиента | `KVRNetwork.canSendToServer` / `sendToServerIfAccepted`: SpyClient, RednetClient, CallClient, запрос политики десктопа |
| `WindowSummary.read` от любого клиента: отрицательное число окон, `kind` вне enum, NaN в позе | кик отправителя; **краш клиента хакера** в `GhostRenderer` | число ≥ 0, `kind` 0–2, координаты конечные и в пределах мира. Плохая запись выбрасывается, остальные остаются |
| Одиночная игра: вышел из мира → зашёл в другой (новый `MinecraftServer`, статические карты старые) | утечка мёртвых `ServerPlayer`, ноды Rednet в сети старого сервера, «висящие» звонки | `ServerSession.Binding.rebind(server)`: при смене сервера система сбрасывает своё состояние (Rednet отсоединяет ноды, Calls/Spy очищаются) |
| Игрок вышел с сервера | счётчики, отметки «говорит» и сводки шпиона оставались навсегда | `retainAll(online)`: Calls (`RATE`, `KVRVoice.retainOnly`) раз в 10 с, Spy relay каждый цикл релея. Rednet уже делал это сам |
| Клиент отключился / перешёл на другой сервер | модем «онлайн» и звонок от старого сервера, пакеты туда, где канала нет | `ClientSession`: смена `Minecraft#getConnection()` → `RednetClient.reset`, `CallClient.reset` (закрывает окно звонка), `HostDesktopClient.reset`. Для этого событие дисконнекта в фасаде не нужно |

Окна и очки для других игроков:
- **Очки на голове** — ванильный слот HEAD: экипировка синхронизируется игрой, `CustomHeadLayer` поворачивает модель с головой. Кода не нужно.
  Если у других очки не видны или смещены — это `display.head` в `models/item/<очки>.json` либо краска (color provider), не сеть.
- **Окна для HackGlasses** — цепочка в дереве (`SpyClient` → `SpyRelay` → `GhostRenderer`) проверена и усилена (см. таблицу).
- **Публичные окна и рука в Alt** — см. «Что не сделано».

### Агенту: сетевые провайдеры (ваши файлы, в дереве их нет)
В `KVRNetwork.Provider` добавлены два default-метода, старые провайдеры компилируются без них.
Но защита от клиентов и серверов без KVR заработает, только когда вы их реализуете (VERIFY — по документации):
```java
// FabricKVRNetworkProvider (все ноды; на 1.20.5+ есть и перегрузка с Identifier/ResourceLocation)
@Override public boolean canSend(ServerPlayer p, ResourceLocation ch) { return ServerPlayNetworking.canSend(p, ch); }
@Override public boolean canSendToServer(ResourceLocation ch) { return ClientPlayNetworking.canSend(ch); }  // client-only класс — в клиентской части провайдера

// NeoForgeKVRNetworkProvider (20.4 / 21.1): тип вашего payload для канала ch
@Override public boolean canSend(ServerPlayer p, ResourceLocation ch) { return p.connection.hasChannel(typeOf(ch)); }
@Override public boolean canSendToServer(ResourceLocation ch) { var c = Minecraft.getInstance().getConnection(); return c != null && c.hasChannel(typeOf(ch)); }
```
Если имя не сошлось — оставьте default `true` и пришлите ошибку компилятора.

### Агенту: слияние
- **Целые файлы (забрать как есть):**
  - `desktop/client/{DesktopLauncherWindow,DesktopDock}.java`, `rednet/cc/CcRednetProvider.java`;
  - `session/{ServerSession,ClientSession}.java` (новые).
- **Точечные правки (дифф поверх ваших версий):** `HostDesktopClient`, `KVRNetwork`, `KVRRednet`, `RednetServer`, `RednetClient`, `CallServer`, `CallClient`, `KVRVoice`, `DockPrefs`, `KvrUiConfig`, `WearableGlassesItem`, `ClientCompat`.
- **Файлы, которые в дереве в старой версии (0013):** `hack/SpyRelay`, `hack/client/SpyClient`, `hack/WindowSummary`. **Не копировать целиком** — перенесите только изменения:
  - `SpyRelay`: `ServerSession.guard`, `rebind` → `SUMMARIES.clear()`, `retainAll(online)`, `sendIfAccepted`, `lookTarget(server, hacker)`;
  - `SpyClient`: `sendToServerIfAccepted`;
  - `WindowSummary.read`: санитайзинг. Если в вашем `SpatialWindow.Kind` больше трёх значений — поправьте `kind > 2`.
- Предположения об API вашего репо (в дереве их нет, взяты из присланного кода):
  - `SpatialWindow.requestClose()`, `GlassStyle.surface(g, x, y, w, h, false)`;
  - `new DesktopConsentScreen(Screen parent, Runnable onAllow)`;
  - `SpatialWindow.mouseScrolled(double, double, double)` (как у `HostDesktopWindow`). Если у вас другая сигнатура — одна строка в лаунчере.
- Если в вашем репо у `SpatialWindow` есть свой захват клавиатуры (0014/0028: «рука на окне + печатать») — пришлите `SpatialWindow.java`.
  Переведу поиск на него. Сейчас он сделан через `TextInputScreen`, который точно есть.
- **Lang (слить в `ru_ru.json` / `en_us.json`):**
```json
"kvr.desktop.tab.recent": "Недавние" / "Recent",
"kvr.desktop.tab.all": "Все приложения" / "All apps",
"kvr.desktop.search": "Поиск — нажмите и печатайте" / "Search — click and type",
"kvr.desktop.scanning": "Ищу программы…" / "Looking for programs…",
"kvr.desktop.count": "программ: %s" / "programs: %s",
"kvr.desktop.empty_recent": "Здесь появятся программы, которые вы запускали" / "Programs you start will show up here",
"kvr.desktop.empty_search": "Ничего не найдено" / "Nothing found",
"kvr.desktop.empty_all": "Программ не найдено. Свои команды — host_desktop_apps в kvr-client.toml" / "No programs found. Own commands: host_desktop_apps in kvr-client.toml"
```

### Чек-лист
**Приложения ПК (Arch + niri, приложения ПК включены):**
1. Док → «Рабочий стол» → «Все приложения»: список как в меню системы, с иконками (PNG) и русскими именами; есть Flatpak-приложения, если установлены.
2. Клик по строке поиска → напечатать `fire` → остаётся Firefox; × — список снова полный. Колесо прокручивает, справа полоска позиции.
3. ★ у Firefox → Firefox **сразу в доке**. В `config/kvr/dock.json` есть `desk:firefox.desktop` в `pins` и в `desktop`.
4. Перезайти в мир → Firefox в доке до открытия лаунчера. Клик в доке сразу после входа: сперва ответ сервера, потом Firefox запускается сам (без второго клика).
5. Сервер с `enable_host_desktop_windows = false` → клик по закреплённому Firefox в доке → тост «отключено сервером», ничего не запускается.
6. Согласие отозвано → клик в доке → экран согласия → «Разрешить» → запускается именно Firefox (а не открывается лаунчер).
7. Запустить 2–3 программы → вкладка «Недавние», последняя сверху; лаунчер открывается на ней.
8. Старый `kvr-ui.toml` с `desktop_pinned_apps = "foot"` → после забора foot в доке, ключ в файле пустой.

**Модем (нужен CC: Tweaked):** пункты 1–9 чек-листа Rednet из 0029 теперь должны проходить. Дополнительно:
- в логе `KVR Rednet: bridge to CC: Tweaked ready` (без «inert»);
- ПКМ вторым модемом по очкам с модемом → ничего не происходит;
- стак из 3 модемов → вставлен один, два остались на курсоре;
- компьютер на Y 200: сообщения доходят дальше 64 блоков.

**Лица в «Звонках»:** 1.21.1 и 1.20.1 — скины с шляпой; игрок не в табе — стандартный скин; 1.21.11 компилируется.

**Мультиплеер (выделенный сервер + 2 клиента):**
1. A и B в звонке (SVC), B закрывает игру через Alt+F4 → сервер жив, у A «Звонок завершён».
2. B выходит во время входящего звонка → у A «не в сети»; в логе сервера нет исключений (или только `KVR Calls: server tick failed, continuing`).
3. Хакер в HackGlasses смотрит на B → видит окна; B выходит → призраки исчезают за ≤ 5 с, у хакера нет краша.
4. Одиночная игра: в мире 1 очки с модемом и работающий Rednet → выйти → мир 2: модем работает (пункт 3 чек-листа 0029), в логе нет ошибок Rednet.
5. KVR-клиент заходит на ванильный сервер (после реализации `canSendToServer`) → нет краша и кика через минуту игры.
6. Дисконнект во время входящего звонка → зайти на другой сервер: окна «Звонки» нет, колокольчик не звонит, в Настройках Rednet «CC: Tweaked не установлен» до ответа нового сервера.

### Что не сделано — нужны файлы или лог
1. **`RegistrySyncManager.unmap` NPE.** Это код Fabric API: синхронизация реестров при выходе с сервера откатывает клиентские реестры к исходному состоянию. Кода регистрации KVR (`KVRContent`, Fabric-реализация `KVRRegistrar`, entrypoint'ы) в дереве нет, а без стектрейса причину можно только угадать. Частые причины именно такого NPE:
   - регистрация чего-то (предмет, блок, menu type, recipe serializer, sound, data component) **вне `onInitialize`**: в `ClientModInitializer`, в статическом инициализаторе класса, который впервые грузится при входе в мир, лениво «при первом обращении»;
   - регистрация, которая **зависит от стороны или конфига** (`if (isModLoaded("computercraft"))`, `if (client)`, ключи `kvr-client.toml`) → реестры клиента и сервера расходятся;
   - один и тот же id регистрируется дважды (common + fabric).
   В 0030 ничего в реестрах не регистрируется; модем CC ищется по id и ничего не добавляет.
   **Пришлите:** полный стектрейс с `Caused by`; строки лога перед ним с `Registry` / `remap` / `Mismatched`; версии Fabric API и MC; сценарий (выход с сервера / кик / одиночная); файлы `KVRContent`, Fabric `KVRRegistrar*`, `KVRFabric` / `KVRFabricClient`.
2. **`PublicWindowRelay`, `PublicWindowRenderer`, `HandPoseRelay`, `HandPoseClient`, `PlayerModelMixin`** в дереве отсутствуют — проверить их я не могу. Пришлите их, и я применю то же, что к `SpyRelay`. Или пройдитесь сами по этому списку:
   - тик обёрнут в `ServerSession.guard`; `Binding.rebind` → очистка; `retainAll(ServerSession.online(server))`;
   - все рассылки другим игрокам — через `sendIfAccepted`, клиентские отчёты — через `sendToServerIfAccepted`;
   - декодер: счётчики ≥ 0 и с потолком, позы и углы `isFinite`, enum-индексы в границах;
   - на клиенте данные других игроков чистятся по тайм-ауту и в `ClientSession.onLeave`;
   - в `PlayerModelMixin` нет падения, если поза руки для игрока ещё не пришла (`null` → ванильная поза).

### Файлы
- **новые:** `session/ServerSession.java`, `session/ClientSession.java`
- **переписаны:** `desktop/client/DesktopLauncherWindow.java`, `desktop/client/DesktopDock.java`, `rednet/cc/CcRednetProvider.java`
- **изменены:** `desktop/client/HostDesktopClient.java` (`launchApp`, повтор после ответа, сброс сессии), `config/DockPrefs.java` (`recent`), `config/KvrUiConfig.java` (deprecated + миграция),
  `glasses/WearableGlassesItem.java` (слот модема), `rednet/KVRRednet.java` (`Placement.rednetId`), `rednet/RednetServer.java`, `rednet/client/RednetClient.java`,
  `voice/CallServer.java`, `voice/KVRVoice.java` (`retainOnly`), `voice/client/CallClient.java`, `compat/client/ClientCompat.java` (`drawFace`),
  `platform/KVRNetwork.java` (`canSend`, `sendIfAccepted`, `canSendToServer`, `sendToServerIfAccepted`),
  `hack/SpyRelay.java`, `hack/client/SpyClient.java`, `hack/WindowSummary.java` (только дифф, см. выше)
- **удалён:** `desktop/client/SystemAppScanner.java`
- **lang:** ключи выше (в дереве lang старые, не трогал)

## 0029 — Rednet через модем в очках (CC: Tweaked) + Звонки (Simple Voice Chat) — ГОТОВО К ЗАБОРУ (не проверено сборкой)

Последний открытый пункт SPEC §7 (Блок 5, тикет 5g). Оба мода **необязательные**:
- без CC слот модема просто не находит модем;
- без SVC звонок отвечает «на сервере нет Simple Voice Chat».

Ни один класс CC/SVC не грузится, если мода нет.

### 1. Слот расширения: модем CC в очках (`rednet/GlassesModem`)
- **ПКМ модемом на курсоре по очкам** в инвентаре — модем вставлен (как в мешочек). **ПКМ пустым курсором** по очкам с модемом — модем снова на курсоре.
  Без модема ПКМ работает как раньше (берёт очки).
- Подходят `computercraft:wireless_modem_normal` (беспроводной: 64 блока, одно измерение) и `wireless_modem_advanced` (эндер: без предела, между измерениями).
  Модемы узнаются по id предмета, без классов CC.
- Хранится в `custom_data → kvr_modem` {kind, id}. **Rednet ID** очков — случайный 50000–64999 (выше id компьютеров), остаётся на очках и после снятия модема.
- Если CC убрали из сборки, модем из слота не достаётся и не пропадает — лежит до возвращения CC.

### 2. Очки = мобильная нода Rednet (`RednetServer`, `cc/CcRednetProvider`)
- Раз в секунду: надетые очки + работающая ОС + модем → очки подключаются к беспроводной сети CC как приёмник **в глазах игрока** (позиция обновляется каждый тик).
  Сняли очки, выключили (O), вынули модем — отключаются.
- **Формат — ровно `rednet.lua`**, поэтому обычные Lua-программы говорят с очками без библиотек:
  - таблица `{nMessageID, nRecipient, nSender, message, sProtocol}`;
  - канал `id % 65500` (или 65535 = broadcast) + копия на 65533 для repeat;
  - reply channel = наш id. Принимаем свой канал (с `nRecipient == id`) и broadcast, дубли отсекаются по `nMessageID`.
  - Сырые `modem.transmit` (не rednet) игнорируются.
- Lua ⇄ JSON (`RednetPayload`):
  - таблица `1..n` → массив, остальные → объект;
  - числа-целые остаются целыми;
  - `nil`/`null` выпадают.
  - Лимиты: 8 КБ JSON, глубина 16, 1024 значения. Больше — сообщение отбрасывается целиком.
- Защита от спама: 10 отправок/с на очки (и на клиенте, и на сервере).
- Поток: CC вызывает приёмник со своих потоков компьютеров → payload копируется сразу, доставка — через `server.execute`.
- Мост к CC создаётся рефлексией после `Class.forName("dan200.computercraft.api.ComputerCraftAPI")`. Только `rednet/cc/**` линкуется с API CC.

### 3. JS API (`prelude.js`, `ScriptHost`) — всё требует разрешения `network`
- `kvr.rednet.available()` / `id()` / `status()` → `{available, modem, ender, id}`.
- `kvr.rednet.send(id, message, protocol?)`, `kvr.rednet.broadcast(message, protocol?)` → `true`, если ушло в модем.
- `kvr.rednet.on(m => m.sender, m.message, m.protocol, m.broadcast, m.distance)`.
- **Шина CONCEPT §3:** `kvr.emit('base:toggle_power', {state:true})` дополнительно рассылает `{type:'base:toggle_power', state:true}` с протоколом `kvr_events`.
  Входящие `kvr_events` приходят в `kvr.on(type, (data, meta) => …)`, в `meta` — `{sender, distance}`.
  - **Решение (одна строка в prelude):** в сеть уходят только типы с `:` (namespaced), чтобы локальные `kvr.emit('refresh')` приложений не сыпались в эфир.
- `kvr.calls.call(name | [names])`, `accept()`, `decline()`, `hangup()`, `show()`, `state()`, `on(fn)`.
- Пример для компьютера: `examples/cc/kvr_base.lua` (редстоун по `base:toggle_power`, датчик раз в 5 с, `kvr_rpc` ping).

### 4. Звонки (`voice/*`)
- Плагин SVC `KvrVoicechatPlugin`:
  - Fabric — entrypoint `voicechat`;
  - NeoForge — аннотация `@ForgeVoicechatPlugin`.
  - API SVC одинаковый на всех версиях MC — веток Stonecutter нет.
- Звонок = **скрытая, с паролем, непостоянная ISOLATED-группа SVC**: участники слышат только друг друга, где бы ни были, посторонний не зайдёт из списка групп.
- `CallServer`:
  - INVITE → у вызываемого звонок 30 с (потом «пропущенный» / «не отвечает»);
  - первый ACCEPT создаёт группу и переносит туда всех;
  - ADD добавляет людей по ходу, до 8 участников.
  - Выход: «Завершить», очки сняты/выключены, выход из игры, отключился голосовой клиент, вышел из группы в меню SVC.
    Остался один — звонок кончается, группа удаляется.
  - Кто говорит (`MicrophonePacketEvent`, 350 мс) — рассылка 5 раз в секунду, только при изменении.
  - **Решение:** после звонка игрок выходит из всех групп. Довызывная группа не восстанавливается — SVC мог её уже удалить.
- Нужно у обоих: надетые включённые очки и подключённый SVC. Причина отказа приходит тостом (BUSY, OFFLINE, PEER_NO_GLASSES, NO_VOICE_CLIENT, …).
- Окно **«Звонки»** (`CallWindow`, системное, без слота RAM, плывёт за головой как «Питание»):
  - входящий: имя, звонок колокольчиком раз в 2 с (выключается в Настройках → «Звук уведомлений»), Принять/Отклонить, × = отклонить;
  - в звонке: лица участников (скин из таба), зелёная рамка + «говорит», таймер, Добавить (список онлайн) / Завершить;
  - без звонка — тот же список как «номеронабиратель»; после конца звонка окно закрывается через 3 с.
- Откуда звонить:
  - Мессенджер → чат → **«Позвонить»**;
  - Настройки → Система → **«Звонки»**;
  - из любого приложения через `kvr.calls`.
  - Отдельной иконки в доке нет — правило 0027 «ровно три программы».
- Настройки → Система → О устройстве: строка **Rednet** — «ID 51234 · эндер-модем» / «модема в слоте нет (ID …)» / «CC: Tweaked не установлен».

### Агенту: Gradle и метаданные
1. Репозитории: `maven("https://maven.squiddev.cc")` (CC: Tweaked), `maven("https://maven.maxhenkel.de/repository/public")` (SVC).
2. common, **все ноды:** `compileOnly("de.maxhenkel.voicechat:voicechat-api:2.5.+")` (последняя 2.5.x; API не зависит от MC).
3. common, **по нодам:** `compileOnly("cc.tweaked:cc-tweaked-<mc>-common-api:<ver>")` — последняя версия CC:T для этой MC с maven.squiddev.cc.
   Нет CC:T для ноды (возможно 1.21.11) → в этой ноде исключить мост:
   `sourceSets.main { java { exclude("com/kasperstudios/kvr/rednet/cc/**") } }`. Тогда в логе будет «this KVR build has no CC bridge», остальное работает.
4. `fabric.mod.json`: `"entrypoints": { "voicechat": ["com.kasperstudios.kvr.voice.svc.KvrVoicechatPlugin"] }` (рядом с вашими main/client/modmenu),
   `"suggests": { "computercraft": "*", "voicechat": "*" }`.
5. `neoforge.mods.toml`: необязательные зависимости `computercraft` и `voicechat` (`type="optional"`, `side="BOTH"`). Плагин SVC находится по аннотации — регистрировать не нужно.
6. Для теста в dev-запуске: `modLocalRuntime` CC:T и SVC (fabric/neoforge артефакты) — по желанию.
7. Новые S2C-каналы объявлены в `KVRCommonBootstrap.declareS2C`: `rednet_recv`, `rednet_state`, `call_state`. C2S: `rednet_send`, `call_op`.

### Агенту: слияние
- `ScriptHost`: новые host-op `rednet.status`, `rednet.send`, `calls.state`, `calls.op` (после `msg.pending`), метод `allRunning()`, хелпер `network()`.
- `prelude.js`:
  - `emitLocal` получил третий аргумент `meta`;
  - `kvr.emit` зовёт `bridgeEmit`;
  - новые блоки `kvr.rednet` / `kvr.calls` после `msgAck`;
  - две строки в `__kvr_event` (`rednet`, `call`).
  Ваши правки prelude (если есть) — сохранить.
- `WearableGlassesItem.overrideOtherStackedOnMe` — если у вас в `GlassesItem`/`HackGlasses` есть свой override, вызовите `super` первым.
- Бутстрапы: по две строки (`RednetServer`/`CallServer`, `RednetClient`/`CallClient`) + 3 канала в `declareS2C`.
- `SettingsWindow` (строка Rednet + пункт «Звонки»), `messenger/main.js` (кнопка «Позвонить»), lang.

### VERIFY (написано по документации, не компилятором)
| Где | Что | Если не сошлось |
|---|---|---|
| `CcRednetProvider` | `ComputerCraftAPI.getWirelessNetwork(MinecraftServer)`, record `Packet(channel, replyChannel, payload, sender)`, `PacketNetwork.transmitSameDimension/transmitInterdimensional/addReceiver/removeReceiver`, методы `PacketReceiver`/`PacketSender` | имена из ошибки — правка только в этом файле |
| `KvrVoicechatPlugin` | `Group.Builder.setHidden` (API 2.5.0+), `VoicechatServerApi.removeGroup(UUID)` | удалить строку `setHidden`; `removeGroup` — убрать вызов (пустая группа умрёт сама) |
| `WearableGlassesItem` | `overrideOtherStackedOnMe(ItemStack, ItemStack, Slot, ClickAction, Player, SlotAccess)` на 1.21.11 | сигнатура из ошибки |
| `GlassesModem` | `BuiltInRegistries.ITEM.getOptional(ResourceLocation)` на 1.21.11 | одна строка в `remove()` |
| `ClientCompat.drawFace` | `PlayerFaceRenderer.draw(g, PlayerSkin, …)` 1.20.2–1.21.4, `(g, ResourceLocation, …)` + `getSkinLocation()` на 1.20.1 | только этот метод |
| `CallWindow` | `SoundEvents.NOTE_BLOCK_BELL.value()` | `SoundEvents.NOTE_BLOCK_BELL` без `.value()` |

### Чек-лист
**Rednet (нужен CC: Tweaked):**
1. Без CC:
   - в логе `KVR Rednet: CC: Tweaked not installed`;
   - Настройки → Система → `Rednet: CC: Tweaked не установлен`;
   - ПКМ предметами по очкам — как раньше.
2. С CC:
   - беспроводной модем на курсор, ПКМ по очкам → модем исчез, в Настройках `ID 5xxxx · беспроводной модем`;
   - ПКМ пустым курсором → модем на курсоре, строка `модема в слоте нет (ID 5xxxx)`;
   - вставить снова → тот же ID.
3. Компьютер с беспроводным модемом:
   - на нём `rednet.open("back") print(rednet.receive())`;
   - в dev-приложении `kvr.rednet.send(<id компьютера>, {hello: 1}, 'test')`;
   - компьютер печатает `5xxxx table test`, `msg.hello == 1`.
4. Компьютер: `rednet.send(5xxxx, {x=1, list={1,2,3}}, "t")`. Приложение с `kvr.rednet.on(m => kvr.log(JSON.stringify(m)))` → в логе
   `{"type":"rednet","sender":<id>,"protocol":"t","message":{"x":1,"list":[1,2,3]},"broadcast":false,"distance":N}`.
5. `examples/cc/kvr_base.lua` на компьютере (лампа сверху, рычаг сзади):
   - `kvr.emit('base:toggle_power', {state: true})` → лампа горит, приложению приходит `kvr.on('base:power_state')`;
   - рычаг сзади → каждые 5 с `base:sensor_update` с `value 15`.
6. Дальность:
   - беспроводной — отойти на 100+ блоков у земли → сообщения пропадают. Компьютер высоко в небе «слышит» дальше: правило CC, берётся бо́льшая дальность из двух;
   - эндер-модем — работает из Незера.
7. O (выключить очки) → не позднее чем через 1 с `kvr.rednet.available() === false`, компьютер больше не достаёт очки.
8. `.vrapp` без `network` → `available()` false, событий `rednet` нет.
9. Цикл из 100 `send` → компьютер получает около 10 в секунду, игра не тормозит.

**Звонки (нужен Simple Voice Chat на сервере и у обоих):**
1. Без SVC: Мессенджер → чат → «Позвонить» → тост «на сервере нет Simple Voice Chat».
2. A и B в очках, SVC подключён. A: Мессенджер → B → «Позвонить».
   - У A окно «Звонки» — «Вызов…».
   - У B тост «Входящий звонок», окно с колокольчиком.
   - B «Принять» → оба слышат друг друга на любом расстоянии; в списке групп SVC группы не видно.
   - В окне: лица, таймер, зелёная рамка у того, кто говорит.
3. «Завершить» → у обоих тост «Звонок завершён: трубку положили». Обычный голос по расстоянию снова работает.
4. Не отвечать 30 с → у B «пропущенный звонок», у A «не отвечает».
5. B снимает очки посреди звонка → через ≤1 с звонок кончается у обоих.
6. Втроём: A в звонке → «Добавить» → C. C принимает → три лица; один «Завершить» → двое продолжают.
7. B выходит из группы через меню SVC → «вышел из голосовой группы».
8. Настройки → Система → «Звонки» → список онлайн, клик — вызов.
9. B уже в звонке, C звонит B → у C «игрок уже в другом звонке».

### Не сделано (вне этого задания)
- Расход батареи на «сетевой опрос Rednet» (CONCEPT §1.3) — добавлю в `PowerBuild.draw`, если нужен.
- Перехват Rednet HackGlasses (SPEC 5) и Plasmo Voice (через тот же `KVRVoice.Backend`) — по запросу.

### Файлы
- **новые:**
  - `rednet/{GlassesModem,KVRRednet,RednetPayload,RednetProtocol,RednetServer}.java`, `rednet/cc/CcRednetProvider.java`, `rednet/client/RednetClient.java`;
  - `voice/{KVRVoice,CallProtocol,CallServer}.java`, `voice/svc/KvrVoicechatPlugin.java`, `voice/client/{CallClient,CallWindow}.java`;
  - `examples/cc/kvr_base.lua`.
- **изменены:**
  - `glasses/WearableGlassesItem.java` (слот модема);
  - `script/ScriptHost.java`, `assets/kvr/kvr_os/prelude.js`, `assets/kvr/apps/messenger/main.js`;
  - `bootstrap/KVRCommonBootstrap.java`, `bootstrap/KVRClientBootstrap.java`;
  - `compat/client/ClientCompat.java` (`drawFace`), `spatial/ui/SettingsWindow.java`;
  - `lang/ru_ru.json`, `en_us.json`.

## 0028 — Программы ПК из системы, закрепление ★ и недавние, точная посадка дока — ГОТОВО К ЗАБОРУ (не проверено сборкой)

Отчёт по 0027 / 0027a принят: руда грузится, в чистых очках три программы.

### 1. Все программы ПК (`DesktopApps`, новый)
- Сканируются freedesktop `.desktop`, порядок и правила — как в меню любого рабочего стола:
  - папки: `$XDG_DATA_HOME/applications` (по умолчанию `~/.local/share/applications`), затем каждая `$XDG_DATA_DIRS/applications`
    (по умолчанию `/usr/local/share:/usr/share`) + экспорты Flatpak и Snap;
  - id по спецификации (`sub/foo.desktop` → `sub-foo.desktop`), побеждает первый найденный.
    Пользовательский `Hidden=true` прячет системный ярлык.
  - Пропускаются:
    - `Type` ≠ Application;
    - `NoDisplay` / `Hidden`;
    - `OnlyShowIn` (служебные утилиты конкретных DE);
    - `TryExec`, которого нет в PATH.
  - Имя и описание локализуются по языку игры: `Name[ru_RU]` → `Name[ru]` → `Name`.
  - `Exec` разбирается по правилам спецификации (кавычки, `\" \$ \` \\`), field codes `%f %F %u %U %i %c %k` убираются,
    `%%` → `%`. Шелла нет — как и для `host_desktop_apps`.
  - `Terminal=true` запускается через терминал (`foot -e …`) в том же скрытом композиторе.
    Терминал меняется ключом `"terminal"` в `config/kvr/dock.json`.
  - Иконки: тема hicolor (PNG 48/64/32/96/128/256/24), затем `pixmaps`.
    Рисуются 16×16 через `PackageIcons`, подгружаются лениво (только видимые строки). У SVG-иконок — плитка с буквой.
- Скан идёт в фоновом потоке, не чаще раза в 30 с (при открытии лаунчера), игру не дёргает.
- Свои команды из `host_desktop_apps` остались — они идут первыми.
- Windows: `.desktop` нет, там только свои команды (демон всё равно Linux-only до D5).

### 2. Лаунчер «Приложения ПК» (`DesktopLauncherWindow`, переписан)
- Вкладки:
  - **«Недавние»** — история запусков, сначала новые, до 20;
  - **«Все приложения»** — всё найденное, по алфавиту.
  При открытии — «Недавние», если история есть.
- Строка: иконка, имя · описание; клик — запуск; **★/☆ справа — закрепить в док / убрать**.
- **Поиск**: навести руку на окно в Alt-режиме и печатать — клавиатура уходит окну, как у приложений ПК.
  Backspace — стереть, Esc — очистить. Ищет по имени и id. Прокрутка — колесом.
- Внизу: состояние демона и число найденных программ.

### 3. Док — только закреплённое (`DockPrefs`, новый, `config/kvr/dock.json`)
- Док = 3 системные программы (+ «Рабочий стол», если включены приложения ПК) + **то, что игрок закрепил**, в порядке закрепления.
- ★ есть также в App Market: у установленных `.vrapp` и у dev-папок.
- «Установленные» в Market сортируются по недавнему запуску, незапускавшиеся — по алфавиту.
  Незакреплённое запускается оттуда (жест 1 = Market).
- Закреплённая программа ПК хранится вместе с именем, argv и иконкой — в доке она сразу, до скана.
  Клик в доке → `HostDesktopClient.launchApp`: если всё готово (сервер разрешил, согласие, бинарник) — сразу запуск.
  Иначе обычная цепочка через лаунчер.
- **Р