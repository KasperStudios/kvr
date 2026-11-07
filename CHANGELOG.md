# CHANGELOG — Java-код KVR из чата

Каждая запись — одно задание. В «Файлы» только то, что добавлено или изменено.

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
- **Решения, которые я принял (поменять — одна строка):**
  - `.vrapp`, установленный из Market, **закрепляется сразу**: игрок поставил его сам, убрать — ★;
  - программы ПК (их сотни) **не закрепляются никогда сами**.
  - Удаление пакета снимает закрепление. Dev-папки тоже только по ★ (раньше первые четыре шли в док сами).
- **Миграция:** при первом запуске после 0028 всё, что уже установлено, закрепляется один раз.
  Док не опустеет незаметно.
- Предел: 12 закреплённых пакетов и 8 программ ПК. Остальные закрепления сохраняются, но док их не показывает.
- Закрепления и история — на клиенте, как и настройки 0027.

### 4. Посадка дока в Настройках
Дистанция, высота и угол уже были в 0027 («Дисплей и док», шаг 5). Теперь:
- **точная подгонка**: кнопки `« ‹ значение › »` = −10 / −1 / +1 / +10 (см для высоты и дистанции, градусы для угла);
- **«Сбросить посадку»**: −50 см / 85 см / 40°;
- док переезжает **сразу** при каждом нажатии (якорь пересоздаётся со следующего тика) — подгоняется прямо из 3D.

### Агенту
- Ваш `AppLauncherWindow` (если остался) строится из `dockItems()` и теперь видит только закреплённое.
  Полные списки: Market → «Установленные» и «Рабочий стол». Если он должен показывать всё — берите
  `AppsClient.installed()` + `DesktopApps.all()`, сортировка — `DockPrefs.lastLaunch(key)`.
- Новых версионных вызовов Minecraft нет: NIO, Gson, GLFW-константы, уже известные `GlassStyle` / `PackageIcons`.

### Чек-лист (Arch + niri)
1. Док → «Рабочий стол» → «Все приложения»:
   - тот же список, что в меню системы (fuzzel/wofi и т. п.), имена по-русски там, где есть `Name[ru]`;
   - свои команды из конфига сверху;
   - внизу «программ: N».
2. Alt, рука на окне, напечатать `fire` → остаётся Firefox. Esc — список снова полный. WASD не двигает игрока, пока рука на окне.
3. Клик по Firefox → запуск в KVR. Снова открыть лаунчер → вкладка «Недавние», Firefox первый.
4. ★ у Firefox → Firefox в доке с иконкой. Перезайти в мир → он в доке сразу, клик запускает без лаунчера. ☆ — убран из дока.
5. Программа с `Terminal=true` (htop, btop, ranger) открывается в foot внутри KVR.
6. Market:
   - установить Calc → он в доке (★), ☆ → убран, но запускается из «Установленных»;
   - порядок там — по недавним.
7. Старый мир с установленными Notes/Calc: после забора они остались в доке (миграция).
8. Настройки → Дисплей и док:
   - » у дистанции → док отъезжает на 10 см сразу;
   - ‹ у высоты — опускается на 1 см;
   - «Сбросить посадку» возвращает на место.

### Файлы
- **новые:** `config/DockPrefs.java`, `desktop/client/DesktopApps.java`, `desktop/client/DesktopDock.java`
- **переписан:** `desktop/client/DesktopLauncherWindow.java`
- **изменены:** `desktop/client/HostDesktopClient.java` (`launchApp`), `script/BuiltinApps.java` (закреплённые ПК в доке),
  `apps/client/{AppCatalog,AppsClient,AppMarketWindow}.java` (★, недавние, миграция), `spatial/ui/SettingsWindow.java` (±1/±10, сброс),
  `lang/ru_ru.json`, `en_us.json`

## 0027a — СРОЧНО: мир не грузится, «Failed to parse kvr:worldgen/…/ore_up_*» — ГОТОВО К ЗАБОРУ

Эти JSON пишет ваш Gradle-генератор (DevNotes, Блок 2c), в этом дереве их не было. Положил **эталонные** файлы.
Формат `minecraft:ore` и placement-модификаторов одинаков на 1.20.1, 1.20.4, 1.21.1 и 1.21.11,
папки `worldgen/configured_feature` и `worldgen/placed_feature` в единственном числе на всех нодах. **Версионной генерации для них не нужно.**

### Что сделать агенту
1. В Gradle-генераторе **убрать генерацию** этих четырёх файлов (и NeoForge biome modifiers, если он их тоже пишет).
   Иначе генератор перетрёт статические файлы при сборке. Забрать файлы ниже как есть.
2. Проверить, что в собранном jar лежит именно оно:
   `unzip -p fabric/versions/1.21.1/build/libs/kvr-fabric-*.jar data/kvr/worldgen/configured_feature/ore_up_overworld.json`.
3. Fabric: ваш `BiomeModifications.addFeature(..., GenerationStep.Decoration.UNDERGROUND_ORES, ResourceKey(PLACED_FEATURE, kvr:ore_up_overworld))`
   должен ссылаться ровно на эти id: `kvr:ore_up_overworld` / `kvr:ore_up_nether`.
4. В отчёт — **строку после `Failed to parse …`** из старого `latest.log`: там причина дословно
   (`No key …`, `Unknown block …`, `Not a JSON object`). По ней понятно, что именно делал генератор.

### Типичные причины именно такой ошибки (в эталоне исключены все)
| Ошибка в JSON | Правильно |
|---|---|
| нет `discard_chance_on_air_exposure` (поле **обязательное** в кодеке `OreConfiguration`) | `"discard_chance_on_air_exposure": 0.0` |
| `"tag": "#minecraft:stone_ore_replaceables"` | без `#`: `"minecraft:stone_ore_replaceables"` |
| `"block_state"` / `"blockState"` вместо `"state"`, `"name"` вместо `"Name"` | `"state": { "Name": "kvr:up_ore" }` |
| несуществующий блок (`kvr:nether_up_ore`, `kvr:deepslate_up_ore`) | зарегистрирован только `kvr:up_ore` (`KVRContent`) — он и в камне, и в глубинном сланце, и в незерраке |
| `"size"` вне 0–64, `discard_chance` вне 0–1 | 9 / 10 и 0.0 |
| в placed feature `"feature"` указывает на неразобранную configured feature | placed feature падает каскадом — поэтому в логе все четыре |
| `height_range` с `"min"`/`"max"`, `"bottom"` и т. п. | `"min_inclusive": { "absolute": -48 }` / `{ "above_bottom": 10 }` / `{ "below_top": 10 }` |
| модификаторы без неймспейса либо в старом формате 1.17 (`"decorator"`) | `"type": "minecraft:count"` и т. д. |

### Параметры жил (ADR-003: «генерируется часто»)
- **Верхний мир:**
  - жила 9, 14 попыток на чанк, trapezoid Y −48…80 (чаще всего около Y 16);
  - камень + глубинный сланец.
  - Для сравнения: железо — жила 9, 10 попыток.
- **Незер:**
  - жила 10, 12 попыток, равномерно от дна+10 до потолка−10;
  - только незеррак — как у кварца.
- Поменять — числа `size` / `count` / `absolute` прямо в JSON. Код не нужен.

### NeoForge biome modifiers (справочно)
- `data/kvr/neoforge/biome_modifier/ore_up_{overworld,nether}.json`, тип `neoforge:add_features`, шаг `underground_ores`,
  биомы `#minecraft:is_overworld` / `#minecraft:is_nether`.
- Путь `neoforge/biome_modifier` действует на 20.4, 21.1 и 21.11.
- Если у вас уже есть свои с теми же id — оставьте один комплект. Два модификатора с разными id добавят руду дважды.

### Проверка
1. Новый мир создаётся без экрана Safe Mode. В логе нет `Failed to parse kvr:`.
2. Старый мир, упавший в Safe Mode, тоже открывается: датапак мода читается из jar заново.
3. `/place feature kvr:ore_up_overworld` (op) — рядом появляется жила UpOre.
   В новых чанках руда на Y около 0–30 встречается чаще железа. В незере — в незерраке.
4. То же на 1.20.1 и 1.20.4 (файлы те же).

### Файлы
- `data/kvr/worldgen/configured_feature/ore_up_overworld.json`, `ore_up_nether.json`
- `data/kvr/worldgen/placed_feature/ore_up_overworld.json`, `ore_up_nether.json`
- `data/kvr/neoforge/biome_modifier/ore_up_overworld.json`, `ore_up_nether.json` (справочно)

## 0027 — Ровно 3 предустановленные программы, встроенный каталог, полноценные Настройки — ГОТОВО К ЗАБОРУ (не проверено сборкой)

Решение Создателя: в чистых очках ровно **три** программы — App Market, Связь (Messenger), Настройки.
Всё остальное ставится из App Market. Закрывает вопрос из 0025 п. 5 в пользу CONCEPT §2.3.

### 1. Док чистых очков: Market → Messenger → Settings
- `BuiltinApps.PREINSTALLED = ["messenger"]`. Market и Settings — нативные окна, они добавляются в `dockItems()` сами.
- Порядок в доке:
  1. Market;
  2. Messenger («Рабочий стол», если включены приложения ПК);
  3. Settings;
  дальше установленные пакеты и dev-приложения.
- `KVRClientBootstrap`: `new BuiltinApps(BuiltinApps.PREINSTALLED)`.
  `SYSTEM_APPS` остался списком всех JS-приложений в джаре — `ChicoryJsEngineTest` продолжает гонять все на настоящем prelude.
- Иконка шестерёнки для `settings` в `AppIcons`.

### 2. Встроенный каталог (Notes, Calc, KAV, Hello — из Market)
- `assets/kvr/apps/index.json` — список приложений из джара, которые НЕ предустановлены, со всеми файлами.
  **Агенту: добавьте свои `ide` и `player`** (все файлы каждого).
- `BundledApps` (common) читает их из **classpath**, а не из менеджера ресурсов:
  - у выделенного сервера нет клиентских ресурсов;
  - ресурс-пак не должен подменять код, который ставит сервер.
- Новый op `INSTALL_BUNDLED` (последним в enum): клиент шлёт только id, **сервер сам пакует** пакет из своего джара.
  - Пакет пишется в `/apps/<id>.vrapp` и занимает квоту Storage. На T1 (256 КБ и 2 файла) влезают два приложения — или одно и заметка.
  - Официальные id по-прежнему зарезервированы для заливки: чужой `notes.vrapp` не подменит настоящий.
  - Политика `allow_app_install` / allowlist на встроенные приложения **не действует**: это часть мода, а не сторонний код.
- App Market → «Каталог»:
  - сверху раздел «Встроенные приложения KVR» (Установить / Обновить при более новой версии в моде / Установлено), ниже — «Онлайн-каталог»;
  - удаление — как у любого пакета; `/data/<id>/` остаётся, заметки не пропадают.
- `notes/config.json`: `"permissions": ["fs.home", "player.coords"]` → `[]`.
  Эти имена не из `AppPermissions`, и формат `.vrapp` отклонил бы пакет. Файлы и координаты разрешениями не закрыты.
  **Проверьте `config.json` своих ide/player** — только `network`, `fs_read_all`, `hardware_override`.
  У пакетов добавлены `description` для каталога.
- `VrAppPackage.packFiles(Map)` — та же детерминированная упаковка для файлов из памяти.

### 3. Настройки как у настоящих очков (`spatial/ui/SettingsWindow`, системное окно)
Слева список разделов, справа настройки. Всё применяется сразу, без кнопки «Сохранить».
| Раздел | Что там | Где действует |
|---|---|---|
| **Персонализация** | 5 тем (Cyan Cyberpunk, Purple Neon, Amber Matrix, Emerald Hack, Monochrome OLED) — цветные образцы; плотность фона 0–100 % шагом 5; подложка Стекло / Градиент / Сплошная; живой предпросмотр окна | `GlassStyle.ACCENT` теперь не константа и обновляется в каждом `surface()` — тема сразу во всех окнах, ваших тоже. Градиент = `fillGradient` с оттенком акцента, Сплошная = непрозрачная подложка |
| **Дисплей и док** | высота дока (−80…−20 см), дистанция (60…140 см), угол следования (20…90°); новые окна Публично / Лично; чужие публичные окна; часы и заряд в доке | `DockBar` перестраивает `LazyFollowAnchor` при изменении; с выключенными часами и зарядом блок статуса исчезает |
| **Звук и отклик** | звук нажатий, уведомлений, звуки приложений; тряска окон | клик по окну → `UI_BUTTON_CLICK` (`SpatialInput`); тосты (`ToastPanel`); `sound.play` и `window.shake` из JS (`ScriptWindow`) |
| **Система** | «Питание и батарея» → `PowerWindow`; «Расширенные» → `KvrSettingsScreen` (приложения ПК, согласие); «Об устройстве»: имя очков, серийный №, KVR OS 1.0.0, CPU/GPU/RAM/диск/аккумулятор с тиром и названием (Бомжатский…Фронтирный), окна RAM x/y, занятое место, заряд % | серийный № = начало UUID диска очков (`kvr_vfs.id`, выдаётся сервером при первом доступе) — у каждых очков свой |

Хранение:
- новое — `config/kvr-ui.toml` (`KvrUiConfig`, тот же TOML-парсер);
- плотность и Публично/Лично — в прежнем `kvr-client.toml`, общие с экраном настроек мода.

Настройки хранятся **на клиенте, а не в очках**: другие очки — та же тема. Хранение в очках
(украл очки — украл тему, как с файлами) можно сделать отдельным заданием через VFS, если Создатель хочет.

### 4. Агенту: слияние с вашим кодом
- **Ваш `SettingsWindow`** (из DevNotes: opacity, visibility, desktop toggle, power) заменяется этим.
  Всё его содержимое есть в новом, desktop toggle — через «Расширенные».
  Если он в другом пакете — удалите его и переведите вызовы на `com.kasperstudios.kvr.spatial.ui.SettingsWindow.open()`.
- **Ваш `BuiltinApps.SYSTEM_APPS`** (с player/ide): список оставьте для теста.
  В док идёт `PREINSTALLED`, а player/ide — в `index.json`.
- **Ваш `AppLauncherWindow`**, если остаётся, сам увидит новый порядок: он строится из `dockItems()`.
- После забора у старых тестовых миров из дока пропадут Notes/Calc/KAV/Hello. Они в Market → Каталог → «Встроенные», заметки в `/home` и `/data/notes` целы.

### 5. Версии
Нового версионного API нет:
- `fillGradient`, `drawString`, `forUI(Holder|SoundEvent, float)`, `getAbilities()`, `readNBytes` — на всех нодах;
- цвета текста с альфой (1.21.6+).
На 1.21.11 окно компилируется, но, как и всё пространственное, без рендера (`RenderCompat.SUPPORTED = false`).

### Чек-лист (1.21.1 и 1.20.1)
1. Новые очки → O → в доке ровно Market, Messenger, Settings (+ статус).
2. Market → Каталог → «Встроенные» → Notes → Установить → Notes в доке, запускается, старые заметки на месте.
   На сервере есть `<world>/kvr/vfs/<uuid>/apps/notes.vrapp`. То же на выделенном сервере (проверка classpath).
3. T1: Notes + Calc → третье приложение или новая заметка → «Не хватает места…».
4. Settings → Персонализация:
   - Purple Neon → акценты всех окон (вкладки Market, заголовки) фиолетовые сразу;
   - «Градиент» / «Сплошная» — видно в предпросмотре и в окнах;
   - плотность −/+ меняет фон.
5. Дисплей:
   - высота дока −20 → док поднимается;
   - дистанция 140 → отодвигается;
   - часы и заряд ВЫКЛ → блок статуса исчез, док уже.
6. Звук: нажатия кликают / молчат; KAV с выключенной тряской — окно не трясётся, с выключенными звуками приложений — без сирены.
7. Система:
   - тиры совпадают со сборкой очков;
   - серийный № `KVR-XXXXXXXX` после первого открытия Market/Notes;
   - «Питание» открывает PowerWindow, «Расширенные» — экран настроек мода.
8. Перезапуск игры — все настройки сохранились (`config/kvr-ui.toml`).

### Файлы
- **новые:** `config/KvrUiConfig.java`, `apps