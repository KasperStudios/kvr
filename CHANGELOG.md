# CHANGELOG — Java-код KVR из чата

Каждая запись — одно задание. В «Файлы» только то, что добавлено или изменено.

## 0034a — Уточнение к 0034: [X] реально закрывает, сетка пола удалена, AR-инспектор блоков — ГОТОВО К ЗАБОРУ (не проверено сборкой)

Пункты 4–6 уточнения (непрозрачный кокон, закрытие окон при выходе в Мир, ключи App Market) **уже сделаны в 0034** ниже — если в игре всё ещё старое поведение, значит 0034 не слит. Проверить: `CocoonRenderType.java` есть в репо и `CocoonRenderer` вызывает его (а не `ArDraw.quad`); `VirtualSpaces.switchTo` вызывает `endSession()`; в lang есть `kvr.apps.tab.installed`.

### 1. [X] не закрывал окно
**Причина:** клик по крестику обрабатывался глубоко в пути окна (`SpatialWindow.click` / в репо `WindowChrome`) и давал только флаг `closeRequested`. В репо этот путь для угла не достигается (хром пропускается при `hasSettings() == false`, клик съедают модалка/контент раньше), либо флаг ставится, но никто его не обрабатывает.

**Исправление (не зависит от хрома и окна):**
- Новый `spatial/window/CloseButton` — единственное место с геометрией (`x/y`, `SIZE = 16`, `MARGIN = 3`), хитбоксом (`hit`, +4 px запаса на неточность луча) и отрисовкой (`render`). Рисунок и хитбокс теперь гарантированно совпадают.
- `SpatialInput.handleAttack`: **до** любой обработки клика окном проверяется `CloseButton.hit(w, px, py)` → `windows.close(w)` сразу (без флага и без ожидания тика), клик поглощается, `hover` сбрасывается. Ни хром, ни модалка, ни `mouseClicked` приложения не могут перехватить.
- `SpatialWindow.click` и `renderChrome` теперь только делегируют в `CloseButton` (запасной путь для других вызывающих).

**Агенту (важно, в репо другой хром):**
1. В `SpatialInput.handleAttack` (репо) вставить блок с `CloseButton.hit` перед циклом `while (attack.consumeClick())`. Если в репо есть отдельный регион для хрома/заголовка (не `CONTENT`) — убрать условие `region() == CONTENT`, проверять `CloseButton.hit` по `px/py` любого региона внутри окна.
2. `WindowChrome`: свой `CLOSE_WINDOW` / `close(w, h)` удалить или перевести на `CloseButton.render(g, w, mx, my)` / `CloseButton.hit(...)` — чтобы был один крестик с одним хитбоксом. Отрисовка [X] не должна стоять под `if (hasSettings())`.
3. `hasCloseButton()` по умолчанию = `hasGrabBar()`. Если в репо `AppMarketWindow` (или другое окно) переопределяет `hasGrabBar() = false`, добавить ему `hasCloseButton() { return true; }`.

### 2. Сетка пола — УДАЛЕНА
`ArRenderer.grid/segment` и кольцо горизонта удалены, `ArScanner.groundY` удалён, ключ `ar_grid` и тумблер «Сетка пола» удалены. Старый `ar_grid` в `kvr-space.toml` просто игнорируется.

### 3. AR-инспектор блоков (`spatial/ar/ArInspector` + `inspect/InspectServer`)
- Блок под взглядом (луч до 8 блоков, `player.pick`) получает неоновые уголки по его форме (`getShape`, не всегда куб) цветом акцента темы, с лёгким «дыханием»; при смене блока контур сжимается на место, плашка плавно проявляется и всплывает (220 мс).
- Плашка: название (`Block.getName()`), id реестра (`kvr:up_ore`, `minecraft:chest`), прочность / «Неразрушимый», инструмент (теги `mineable/*`) + уровень (`needs_*_tool`), «Добывается ✔ / Нужен инструмент лучше» по предмету в руке, сигнал редстоуна (выход / входной), рост растений (`age` → %), занятость слотов контейнера.
- Слоты: содержимое сундуков клиенту не приходит, поэтому очки спрашивают сервер (`kvr:inspect_query` → `kvr:inspect_reply`) раз в секунду, пока смотришь на контейнер. Сервер отвечает только на загруженный блок до 10 блоков от глаз с ванильным `Container`, не чаще 1 раза в 150 мс на игрока, и шлёт только числа (занято/всего), не предметы. До ответа — «Слоты: …». Двойной сундук показывает половину (27).
- Позиция (Настройки → Дисплей и док → «Плашка»): **Сверху** (над блоком, высота 0–100 см шагом 5, «Высота плашки»), **Центр** (на центре блока, сдвинута к игроку на 0.6), **HUD** (виджет в левом верхнем углу экрана с иконкой блока, выезжает слева). В мире плашка — голограмма, повёрнутая к камере: тёмная подложка 80 %, акцентная полоса слева и линия под заголовком.
- Выключен: когда под лучом руки окно, открыт экран, активно пространство (мир скрыт коконом), F1, HUD_OFF. Тумблер «Инспектор» стоит на месте бывшей «Сетки пола». Окно настроек 288 → 328 px высотой.
- Конфиг `kvr-space.toml`: `ar_inspector`, `inspector_position = top|center|hud`, `inspector_offset_cm`.

**Агенту:**
- `InspectServer.init()` — добавить в общий bootstrap рядом с `RednetServer.init()`. Каналы `kvr:inspect_query` (C2S) и `kvr:inspect_reply` (S2C) зарегистрировать в провайдерах так же, как остальные (если список каналов ведётся вручную).
- `ArInspector.initClient()` вызывается из `SpaceClient.init` (идемпотентно). Если NeoForge требует регистрации клиентских приёмников раньше — вызвать из клиентского bootstrap рядом с `RednetClient.initClient()`.

### VERIFY
| # | Проверка | Ожидание |
|---|---|---|
| 1 | [X] на App Market, Настройках, ПК-окне, приложении, пикере | Окно исчезает сразу по клику |
| 2 | [X] при открытой модалке установки в Маркете | Закрывает Маркет |
| 3 | [X] на заблокированном окне | Закрывает |
| 4 | Попасть лучом чуть мимо крестика (≤ 4 px) | Тоже закрывает; крестик краснеет в той же зоне |
| 5 | Холмы, очки надеты | Никакой сетки на земле |
| 6 | Взгляд на камень / обсидиан / пшеницу / провод / руду KVR | Контур + плашка: имя, id, прочность, инструмент/уровень, рост %, сигнал |
| 7 | Взгляд на сундук (в одиночной игре и на сервере) | «Слоты: …» → «Слоты: 5/27», обновляется каждую секунду |
| 8 | Позиции Сверху / Центр / HUD, высота 0 и 100 см | Плашка там, где выбрано; HUD — с иконкой в углу |
| 9 | Быстро вести взглядом по блокам | Каждая новая плашка проявляется плавно, без мерцания |
| 10 | Луч руки на окне / внутри пространства | Инспектора нет |

### Файлы
Новые:
- `src/main/java/com/kasperstudios/kvr/spatial/window/CloseButton.java`
- `src/main/java/com/kasperstudios/kvr/spatial/ar/ArInspector.java`
- `src/main/java/com/kasperstudios/kvr/inspect/InspectServer.java`

Изменены:
- `src/main/java/com/kasperstudios/kvr/spatial/input/SpatialInput.java`
- `src/main/java/com/kasperstudios/kvr/spatial/window/SpatialWindow.java`
- `src/main/java/com/kasperstudios/kvr/spatial/ar/ArRenderer.java`
- `src/main/java/com/kasperstudios/kvr/spatial/ar/ArScanner.java`
- `src/main/java/com/kasperstudios/kvr/spatial/space/SpaceClient.java`
- `src/main/java/com/kasperstudios/kvr/config/KvrSpaceConfig.java`
- `src/main/java/com/kasperstudios/kvr/spatial/ui/SettingsWindow.java`
- `src/main/resources/assets/kvr/lang/ru_ru.json`
- `src/main/resources/assets/kvr/lang/en_us.json`

## 0034 — Пространства как сессия, непрозрачный кокон, [X] на всех окнах, ключи App Market — ГОТОВО К ЗАБОРУ (не проверено сборкой)

По тесту Каспера в игре.

### 1. Жизненный цикл окон: пространство = сессия
**Было (0033):** при выходе в реальный мир окна пространства только скрывались и висели в памяти (и в RAM-слотах) бесконечно.

**Стало:** `VirtualSpaces.switchTo(REAL)` → `endSession()` закрывает (`WindowManager.close` → `onClosed`) **все** APP/VANILLA-окна, открытые внутри любого пространства: скрипты останавливаются, RAM освобождается. Тост: «Сессия завершена · закрыто окон: N».
- Окна, открытые в реальном мире, не трогаются (пока игрок в пространстве, они скрыты; вернулся — на месте).
- Переход между пространствами (1 ↔ 2) — сессия продолжается: окна другого пространства скрыты, но живы. Закрывает только выход в Мир (или снятие очков). *Если Создатель хочет, чтобы и 1 → 2 закрывало окна 1 — это одна строка в `switchTo`, скажите.*
- Подсказка в пикере: «выход в Мир закрывает окна пространств».

### 2. Непрозрачный кокон (полное погружение)
**Было:** полупрозрачная дымка + сетка в render type окон (`entityTranslucentEmissive`, тест глубины без записи) → сквозь стенки видно мир, а блоки внутри кокона его заслоняли.

**Стало:**
- Новый `spatial/render/CocoonRenderType`: POSITION_COLOR, без текстуры, без отсечения граней, **тест глубины ALWAYS + запись глубины**. Оболочка закрывает всё, что уже нарисовано (блоки, пол, мобов, небо), и записывает свою глубину (~2 блока). Всё, что рисуется позже с обычным тестом глубины (окна, AR, частицы, дождь, вода), видно только **внутри** кокона.
- Ванильный `NO_DEPTH_TEST` не подходит: он выключает тест, а с выключенным тестом OpenGL не пишет глубину. Поэтому свой шард `AlwaysDepth`: тест включён, функция `GL_ALWAYS`; сброс как у ванили (выкл, `GL_LEQUAL`).
- `CocoonRenderer` переписан: сплошная тёмная оболочка («глубокий космос» `#070B14` на экваторе → `#010205` у полюсов, 10 % оттенка цвета кокона), поверх — неоновая сетка 16×32, скан-полоса с ореолом, светящиеся кончики. Альфа оболочки < 1 только на разворачивании (350 мс) и затухании (250 мс).
- Камера всегда внутри выпуклой оболочки → каждый луч взгляда встречает ровно одну стенку, сортировка не нужна (порядок отправки: оболочка → сетка).
- Настройка `cocoon_opacity` теперь управляет **яркостью неона** (подпись «Яркость неона»), оболочка всегда плотная; превью в Настройках — тёмная полоса с неоном.
- Последствия (ожидаемые): пол под ногами скрыт (вместо него AR-сетка пола внутри кокона); в виде от 3-го лица скрыта и модель игрока; рука в 1-м лице видна (рисуется отдельным проходом). Окно, унесённое дальше стенки (~2 блока), скрывается — рабочая зона = кокон. Редстоун/мобы снаружи в AR не видны (отсекает глубина), моб вплотную (внутри кокона) — виден контуром.

### 3. Крестик [X] на всех окнах
- `SpatialWindow`: новые `hasCloseButton()` (по умолчанию = `hasGrabBar()`), `requestClose()`, `isCloseRequested()`, константы `CLOSE = 16`, `CLOSE_MARGIN = 3`.
- [X] рисуется в `renderChrome` **поверх** содержимого, в правом верхнем углу каждого окна: непрозрачная тёмная плитка 16×16 со светлой рамкой и белым крестом 2 px; при наведении — красная. Читается на любом фоне (стекло/градиент/непрозрачный).
- Клик по [X] проверяется **первым** в `click()` (раньше шестерёнки, меню, блокировки и приложения) → `requestClose()`; `WindowManager.tick()` в том же тике закрывает окна с `isCloseRequested()`. Работает и на заблокированном окне (цель маленькая и явная).
- Есть у: приложений (ScriptWindow), App Market, Настроек, ПК-окон, лаунчеров, звонков, питания, заметки, пикера пространств (у него `hasCloseButton() = true` без ручки). Нет у: дока, тостов, колеса жестов.
- Старые тусклые «×» в `PowerWindow` и `PinnedNote` убраны (иначе два крестика). В `AppLauncherWindow` подпись «Загрузка…» сдвинута влево, чтобы не лезла под [X].

### 4. Ключи App Market
Добавлены точные ключи со скриншота: `kvr.apps.tab.installed` (Установленные / Installed), `kvr.apps.tab.store` (Магазин / Store), `kvr.apps.tab.dev` (Разработка / Developer), `kvr.apps.open` (Открыть / Open), `kvr.apps.system_badge` (Системное / System). Плюс вероятные соседи: `kvr.apps.running`, `.empty`, `.loading`, `.dev_badge`, `.refresh`, `.close`, а также `kvr.window.close`.

**Агенту:** в песочнице нет `AppMarketWindow`, поэтому проверить его целиком не могу. Прогнать `grep -oE '"kvr\.apps\.[a-z0-9_.]+"' AppMarketWindow.java | sort -u` и прислать ключи, которых нет в lang. Также проверить, что у Маркета в правом верхнем углу (x ≥ width−20, y ≤ 20) нет своих кнопок — [X] их перекроет; если есть свой крестик — убрать.

### Агенту: слияние
- `SpatialWindow` в песочнице **устаревший** — не копировать целиком. Перенести: константы `CLOSE/CLOSE_MARGIN`, `hasCloseButton()`, `closeX()/closeY()`, проверку [X] первой в `click()`, вызов `renderClose` первой строкой `renderChrome()` (до `if (!hasSettings()) return;`), метод `renderClose`. Если в репо уже есть `requestClose()`/`closeRequested` — оставить реповские, мои не дублировать; то же для обработки в `WindowManager.tick()`.
- Если в репо `renderChrome` вызывается не из `WindowSurface`, а иначе — главное, чтобы [X] рисовался после `renderContent`.
- `CocoonRenderType` — новый файл с ветками Stonecutter (`<1.21.5`, `>=1.21`). Использует защищённые члены `RenderType`/`RenderStateShard` через абстрактный подкласс (`create`, `POSITION_COLOR_SHADER`, `TRANSLUCENT_TRANSPARENCY`, `NO_CULL`, `COLOR_DEPTH_WRITE`, `DepthTestStateShard`). Если на какой-то ноде `RenderType.create` недоступен — добавить в access widener / AT (`net/minecraft/client/renderer/RenderType create`) и прислать ошибку компиляции.
- Порядок из 0033 критичен: `SpaceClient.init` (кокон) до `SpatialRenderer` (окна), иначе оболочка закроет окна.
- `HostDesktopWindow`: убедиться, что `onClosed()` шлёт демону `Close` — иначе при выходе из пространства программы ПК останутся жить на хосте.

### VERIFY
| # | Проверка | Ожидание |
|---|---|---|
| 1 | Пространство 1, открыть 2 приложения → Мир | Тост «закрыто окон: 2», RAM освобождена, в доке не «запущены» |
| 2 | Окно в Мире → пространство → Мир | Окно Мира на месте |
| 3 | Пространство 1 → 2 → 1 | Окна 1 живы |
| 4 | Войти в пространство в саванне / пещере / под водой | Мира не видно совсем: тёмная оболочка + неон, пол скрыт |
| 5 | Окна внутри кокона, стена пещеры в 1 блоке | Окна целиком видны, стена нет |
| 6 | Дождь / частицы снаружи | Не просвечивают |
| 7 | Выход в Мир | Кокон гаснет за ~0.25 с, мир виден, депт в порядке (блоки и окна не мерцают) |
| 8 | Яркость неона 5 % / 100 % | Сетка едва видна / яркая; оболочка всегда плотная |
| 9 | [X] на приложении, Маркете, Настройках, ПК-окне, пикере | Виден, краснеет при наведении, клик закрывает сразу |
| 10 | [X] на заблокированном окне | Закрывает |
| 11 | Док, тосты, колесо жестов | Без [X] |
| 12 | App Market, ru и en | Вкладки, «Открыть», бейдж «Системное» — без сырых ключей |

### Чек-лист
- [ ] Правки `SpatialWindow` перенесены точечно (файл устаревший)
- [ ] `CocoonRenderType` собирается на 1.20.1 / 1.20.4 / 1.21.1 (на 1.21.11 — no-op)
- [ ] grep ключей `AppMarketWindow` прислан
- [ ] Проверено, что закрытие ПК-окна закрывает программу на хосте

### Файлы
Новые:
- `src/main/java/com/kasperstudios/kvr/spatial/render/CocoonRenderType.java`

Изменены:
- `src/main/java/com/kasperstudios/kvr/spatial/space/VirtualSpaces.java`
- `src/main/java/com/kasperstudios/kvr/spatial/space/CocoonRenderer.java` (переписан)
- `src/main/java/com/kasperstudios/kvr/spatial/space/SpacePickerWindow.java`
- `src/main/java/com/kasperstudios/kvr/spatial/window/SpatialWindow.java`
- `src/main/java/com/kasperstudios/kvr/spatial/window/WindowManager.java`
- `src/main/java/com/kasperstudios/kvr/spatial/ui/SettingsWindow.java`
- `src/main/java/com/kasperstudios/kvr/spatial/ui/PowerWindow.java`
- `src/main/java/com/kasperstudios/kvr/spatial/ui/PinnedNote.java`
- `src/main/java/com/kasperstudios/kvr/spatial/ui/AppLauncherWindow.java`
- `src/main/resources/assets/kvr/lang/ru_ru.json`
- `src/main/resources/assets/kvr/lang/en_us.json`

## 0033 — Virtual Spaces (слоты реальностей), AR-кокон, AR-сканер мира — ГОТОВО К ЗАБОРУ (не проверено сборкой)

По утверждённой Создателем архитектуре (ADR-001, жест 4).

### 1. Слоты виртуальных реальностей (`spatial/space/VirtualSpaces`)
- Слот `0` — **Реальный мир**, слоты `1..N` — пространства (по умолчанию N = 3: «Работа», «Медиа», «База»; 2–5 в Настройках).
- Каждое APP/VANILLA-окно при открытии привязывается к активному слоту. SYSTEM-окна (док, тосты, настройки, лаунчеры, питание, звонки, колесо жестов, пикер) глобальные.
- Переключение меняет только то, какой слот рисуется и ловит луч руки. Окна других слотов **продолжают тикать**, сохраняют 3D-позы и слот RAM — ничего не выгружается, возврат мгновенный.
- Изоляция: `drawable()`, `pick()`, `minimized()`, жесты 2/3 и `publicWindows()` (то, что видят другие игроки и HackGlasses) — только активный слот.
- Клик в доке/лаунчере по приложению, открытому в другом слоте → `restore()` переключает на тот слот (как Spaces в macOS). Одно приложение = одно окно на все слоты (id `app:<id>` уникален, как и раньше).
- Снятие очков / выход с сервера (`closeAll`) → назад в реальный мир, привязки очищены.
- Принадлежность хранится в `WeakHashMap` внутри `VirtualSpaces`, **не** в `SpatialWindow` (в песочнице он устаревший — его не трогал).

**Жест 4** (`BuiltinApps.enterVirtualSpace` → `VirtualSpaces.onGesture()`):
- режим «Меню» (по умолчанию) — `SpacePickerWindow`: карточки «Мир / 1 / 2 / 3» с числом окон, клик = переключить; жест 4 ещё раз закрывает;
- режим «Цикл» — Мир → 1 → 2 → 3 → Мир.
- При каждом переключении — тост «Пространство 2 · Медиа — окна восстановлены: N».

### 2. AR-кокон (`spatial/space/CocoonRenderer`)
- Рисуется, пока активно пространство (слот ≠ 0). ~4 блока в ширину (R = 2) и 4 в высоту, центр на 1.4 над ногами.
- Форма — веретено: `r(t) = R·(1−|t|^2.2)^0.9`. У полюсов радиус убывает почти линейно → заострённые кончики (у сферы показатель 0.5 → тупые).
- Визуал: мягкая дымка (14×28 ячеек), неоновая сетка (кольца + меридианы, медленно дрейфует), скан-полоса, бегающая между полюсами, светящиеся точки на кончиках. Вход — разворачивается за 350 мс, выход — гаснет за 250 мс, при переключении между пространствами сетка вспыхивает.
- Рисуется **до** окон, render type без записи глубины → окна внутри всегда поверх стенки.
- Настройки → **Пространство**: вкл/выкл, цвет (Cyan, Neon Purple, Emerald, Amber, Custom HEX — поле ввода через `TextInputScreen`, неверный HEX подсвечивается красным и не сохраняется), плотность 5–100 % с превью.

### 3. AR-сканер мира (`spatial/ar/ArScanner` + `ArRenderer`)
Работает **всегда**, пока очки надеты, включены и дисплей горит (не HUD_OFF), и не только в коконе. При F1 скрыт.
- **Редстоун:** все блоки с `POWER > 0` (провод, плиты, датчик дня, скалк, мишень…) и с `POWERED = true` (повторитель, компаратор, рычаг, кнопка, рельсы, наблюдатель…). Провод — цветная подложка по уровню сигнала (тёмно-красный → ярко-розовый) + цифра уровня (до 8 блоков, ≤ 48 подписей); остальное — маркер над блоком. Радиус зависит от GPU: 4 + 2·T (T1 = 6 … T5 = 14 блоков), ±4 по вертикали, скан раз в 10 тиков, ≤ 384 маркера.
- **Эфир Rednet:** на каждую отправку — волна от игрока (фиолетовая, `TX → #id [протокол]`), на приём — волна к игроку (зелёная, `RX ← #id [протокол] · Nм`). Хук в `RednetClient.send/deliver`.
- **Мобы:** тот, на кого смотрит визор (≤ 7° от оси взгляда, до 24 блоков, в прямой видимости), — кибер-окантовка хитбокса (тонкие рёбра + яркие уголки), полоска здоровья, `❤ 14/20`, имя. Остальные раненые в радиусе 12 блоков — тонкая полоска. Враждебные — красные, игроки — фиолетовые, прочие — акцент темы.
- **Сетка пола:** блочная сетка 13×13 на высоте последней опоры (не прыгает при прыжке), затухает к краям, цвет — акцент темы. Пока вытянут луч руки — ещё кольцо горизонта на уровне глаз (выравнивать окна).
- Настройки → **Дисплей и док** → «AR-сканер»: 4 тумблера (Редстоун, Эфир Rednet, Мобы, Сетка пола), по два в строке. Окно настроек стало выше: 272 → 288.

### Конфиг
Новый файл `config/kvr-space.toml` (`KvrSpaceConfig`, папка = `KvrConfig.directory()`, в загрузчиках ничего подключать не нужно): `space_slots`, `space_gesture`, `cocoon_enabled`, `cocoon_color`, `cocoon_custom_hex`, `cocoon_opacity`, `ar_redstone`, `ar_rednet`, `ar_mobs`, `ar_grid`. Отдельный файл — чтобы не расширять record `KvrUiConfig.Prefs`.

### Агенту: слияние
- `WindowManager` — если в репо версия отличается, перенести 8 правок вручную: `claim` в `open`, `forget` в `close`, фильтр `VirtualSpaces.shown` в `minimizeAll`, `minimizeFocused`, `publicWindows`, `pick`, `drawable`, `minimized`; `reveal` в начале `restore`; `reset` в конце `closeAll`.
- `KVRSpatial.initClient` — одна строка `SpaceClient.init(client)` **перед** `onWorldRender(SpatialRenderer::render)`.
- `BuiltinApps.enterVirtualSpace` — вызывает `VirtualSpaces.onGesture()` вместо `soon()`.
- `RednetClient` — две строки `ArScanner.rednet(...)`.
- `SettingsWindow` — раздел `SPACE`, метод `space(...)`, `editHex()`, блок AR в `display(...)`, H = 288. Использует `GlassStyle.surface` и `GlassStyle.text(g, String, …)` из репо (в песочнице `GlassStyle` устаревший).
- `DockBar`: если трей свёрнутых окон собирается не через `windows.minimized()`, а через `all()`, добавить фильтр `VirtualSpaces.shown(w)`.
- Сплошной цвет AR берётся из центрального тексела `grab_handle.png` (`ArDraw.WHITE`). Если линии выглядят тусклыми или цветными — положить белый PNG 1×1 (`textures/misc/ar_white.png`) и поменять одну константу.
- 1.21.11: `RenderCompat.SUPPORTED = false` → `KVRSpatial` не стартует → кокон/сканер тоже не запускаются (события Rednet просто копятся, ≤ 8 штук).

### VERIFY
| # | Проверка | Ожидание |
|---|---|---|
| 1 | Жест 4 | Пикер: Мир / 1 / 2 / 3, текущий выделен |
| 2 | Пространство 1, открыть 2 окна (терминал ПК, заметки) | Кокон разворачивается, окна на местах |
| 3 | Жест 4 → 2, открыть плеер/мессенджер | Окна из 1 исчезли мгновенно, кокон вспыхнул |
| 4 | Обратно в 1 | Окна 1 на тех же 3D-позициях, их состояние не сбросилось (таймер/видео шёл) |
| 5 | Жест 4 → Мир | Кокон гаснет, видны только окна реального мира |
| 6 | Счётчик RAM при окнах в 3 пространствах | Учитываются все (окна в памяти) |
| 7 | Клик в доке по приложению из другого пространства | Переключает туда |
| 8 | Жест 2 (свернуть всё) в пространстве 2 | Сворачиваются только окна пространства 2 |
| 9 | Снять очки в пространстве 2, надеть | Реальный мир, окон нет, кокона нет |
| 10 | Настройки → Пространство: цвет Purple, плотность 80 % | Кокон меняется сразу |
| 11 | Свой HEX `#ff8800` / `zzz` | Оранжевый / поле красное, ничего не сохранено |
| 12 | Режим «Цикл» | Жест 4: Мир → 1 → 2 → 3 → Мир |
| 13 | Очки надеты, линия редстоуна с рычагом | Подсветка по уровню сигнала, цифры 15…1 |
| 14 | GPU T1 и T5 | Радиус редстоуна 6 и 14 блоков |
| 15 | Приложение шлёт rednet / приходит сообщение | Волна наружу TX / внутрь RX с подписью |
| 16 | Посмотреть на зомби / корову | Красная / акцентная окантовка, полоска, ❤ |
| 17 | Сетка пола, прыжок | Сетка на месте, не прыгает |
| 18 | Тумблеры AR по одному | Каждый слой выключается отдельно |
| 19 | HUD_OFF / F1 | AR и кокон не рисуются |
| 20 | FPS на ферме с редстоуном | Без заметной просадки (скан раз в 0.5 с) |

### Чек-лист
- [ ] Правки `WindowManager` перенесены (если версия в репо другая)
- [ ] `SpaceClient.init` до `SpatialRenderer` в `KVRSpatial`
- [ ] Проверен трей `DockBar` (фильтр по пространству)
- [ ] Lang слит (ключи `kvr.space.*`, `kvr.settings.space.*`, `kvr.settings.cocoon*`, `kvr.settings.ar*`, `kvr.settings.section.space`)
- [ ] Сборка всех нод Stonecutter (1.20.1 / 1.20.4 / 1.21.1; на 1.21.11 модуль неактивен)

### Файлы
Новые:
- `src/main/java/com/kasperstudios/kvr/config/KvrSpaceConfig.java`
- `src/main/java/com/kasperstudios/kvr/spatial/space/VirtualSpaces.java`
- `src/main/java/com/kasperstudios/kvr/spatial/space/SpacePickerWindow.java`
- `src/main/java/com/kasperstudios/kvr/spatial/space/CocoonRenderer.java`
- `src/main/java/com/kasperstudios/kvr/spatial/space/SpaceClient.java`
- `src/main/java/com/kasperstudios/kvr/spatial/ar/ArDraw.java`
- `src/main/java/com/kasperstudios/kvr/spatial/ar/ArScanner.java`
- `src/main/java/com/kasperstudios/kvr/spatial/ar/ArRenderer.java`

Изменены:
- `src/main/java/com/kasperstudios/kvr/spatial/window/WindowManager.java`
- `src/main/java/com/kasperstudios/kvr/spatial/KVRSpatial.java`
- `src/main/java/com/kasperstudios/kvr/script/BuiltinApps.java`
- `src/main/java/com/kasperstudios/kvr/rednet/client/RednetClient.java`
- `src/main/java/com/kasperstudios/kvr/spatial/ui/SettingsWindow.java`
- `src/main/resources/assets/kvr/lang/ru_ru.json`
- `src/main/resources/assets/kvr/lang/en_us.json`

## 0032 — Док без автодобавления, непрозрачная модалка Маркета, все lang-ключи, новый URL каталога — ГОТОВО К ЗАБОРУ (не проверено сборкой)

### 1. Док: только закреплённое вручную
**Причина:** `AppCatalog.packageItems()` выдавал в док все установленные пакеты; плюс миграция 0028 «закрепить всё установленное» и `DockPrefs.pin(...)` после установки в Маркете.

**Исправлено в дереве:**
- `AppCatalog.packageItems()` — только пакеты, закреплённые в `DockPrefs` и установленные (`AppsClient.installed(key)`), плюс закреплённые `dev:<id>` (если `DevApps.enabled`). Порядок — порядок закрепления.
- По умолчанию в доке: Маркет, Мессенджер, Настройки (системные, не через `DockPrefs`).
- Новые `AppCatalog.togglePin(dockId)` / `isPinned(dockId)`.
- `AppsClient.uninstall` при успехе — `DockPrefs.unpin(appId)` (не остаётся «мёртвой» иконки).
- `AppLauncherWindow`: кнопка ★/☆ (20px) на строках установленных — закрепить/открепить.

**Агенту (в файлах, которых нет в песочнице):**
- `AppMarketWindow` / `StoreClient` / repo-версия `AppsClient`: удалить любой `DockPrefs.pin(...)` после установки.
- Удалить одноразовую миграцию 0028 «pin всё установленное» (`DockPrefs.migrated()/setMigrated()` и её вызов). Сами методы в `DockPrefs` можно оставить — флаг безвреден.
- Звёздочка в Маркете (если есть) — `AppCatalog.togglePin(appId)`, состояние — `AppCatalog.isPinned(appId)`.

### 2. Модалка подтверждения установки — непрозрачная
**Вероятная причина:** диалог рисовался поверх стеклянной панели окна с альфой фона (`GlassStyle`), без своей подложки — сквозь него видно список.

**Новое:** `spatial/ui/Modal` — затемнение `0xE6050810` на всё окно + тело `0xFF0F172A` (альфа FF), заголовок, перенос текста (`font.split`), кнопки «Отмена» (`gui.cancel`) / подтверждение. Пока открыта, `click()` съедает все клики.

**Агенту — встроить в `AppMarketWindow`:**
```java
private final Modal modal = new Modal();
// вместо текущего диалога:
modal.open(Component.translatable("kvr.apps.install.title"),
           Component.translatable("kvr.apps.install.message", app.name(), perms),
           Component.translatable("kvr.apps.install.confirm"),
           () -> AppsClient.install(app));
// в конце renderContent(...) — ПОСЛЕДНИМ:
modal.render(g, width, height, mx, my);
// первой строкой обработчика клика:
if (modal.isOpen()) return modal.click(mx, my);
```
Старый полупрозрачный диалог удалить.

### 3. Lang-ключи
`ru_ru.json` и `en_us.json` переписаны целиком, наборы ключей идентичны. Добавлено: `itemGroup`, все предметы (`glasses`, `hackglasses`, компоненты `cpu/gpu/ram/storage/battery/chassis` для tier 1–5), руда/слиток/шаблон улучшения/солнечный модуль, блоки (`item.*` и `block.*`), `container.kvr.chassis_assembly`, лаунчер (`kvr.launcher.running`, pin/unpin), Маркет (установка, разрешения, коды ошибок), Настройки (разделы, темы, подложки, названия тиров 1–5 на «Об устройстве», железо, rednet), звонки (`kvr.calls.app` и все причины, включая `peer_*`), ПК-рабочий стол (вкладки, поиск, состояния демона, ошибки), сообщение смерти `.player`.

Тиры: ru — Бомжатский/Бедняжественный/Среднячковский/Нормальный/Фронтирный; en — Bomzh/Bednyak/Srednyak/Normal/Frontier.

Точные id регистра в песочнице неизвестны, поэтому для компонентов даны обе формы: `item.kvr.cpu_tier1` и `item.kvr.cpu_t1` (то же для остальных); для руды — `up_raw` и `raw_up_ore`; для шаблона — `tier_upgrader_template` и `upgrader_template`; для солнца — `solar_upgrade` и `solar_module`. Лишние ключи безвредны.

**Агенту — слияние:** не заменять файлы целиком. Добавить отсутствующие ключи; если ключ есть и в репо, и здесь — оставить значение репо, если оно не сырое. Ключи, которые есть только в репо, сохранить. После слияния:
```
grep -rhoE '"kvr\.[a-z0-9_.]+"|translatable\("[^"]+"' src/main/java | sort -u
```
и сверить с lang-файлом — прислать список оставшихся сырых ключей (вероятно, из `AppMarketWindow`, `DesktopConsentScreen`, `KvrSettingsScreen`, тексты шаблона кузнечного стола `item.kvr.smithing_template.*`).

### 4. Онлайн-каталог: 404
**Причина:** старый URL (`kasper-studios/...` из BLOCK5_PLAN) не существует.

**Новое:** `apps/client/StoreIndexSource`:
- `DEFAULT_URL = https://raw.githubusercontent.com/KasperStudios/kvr-apps-index/main/index.json`.
- `url()` — переопределение через `KvrConfig.storeIndexUrl()` (ключ `app_store_index_url`, пусто = по умолчанию); если указан каталог — `index.json` дописывается один раз.
- `fetchApps()` — async, HTTP/1.1, редиректы, User-Agent, повтор при 429/5xx/сетевой ошибке, BOM, принимает массив или `{"apps":[...]}`.
- Ошибка — `IndexException` с полным URL в тексте; для 404 подсказка (репо публичный? ветка `main`? файл `index.json` в корне?).

**Агенту:** в `StoreClient` заменить свою загрузку индекса на `StoreIndexSource.fetchApps()` (парсинг элементов оставить свой); убрать захардкоженный старый URL. Проверить URL из песочницы не могу (нет сети) — если 404 останется, в сообщении будет точный адрес.

### VERIFY
| # | Проверка | Ожидание |
|---|---|---|
| 1 | Новый мир, открыть док | Только Маркет, Мессенджер, Настройки |
| 2 | Установить приложение из Маркета | В доке не появилось |
| 3 | Лаунчер → ★ у установленного | Появилось в доке; повторно ☆ — пропало |
| 4 | Удалить закреплённое | Исчезло из дока |
| 5 | Маркет → «Установить» | Модалка непрозрачная, список под ней не виден и не кликается |
| 6 | Модалка → «Отмена» / клик мимо | Закрылась, ничего не установлено |
| 7 | Предметы в творческом инвентаре (ru/en) | Нет сырых `item.kvr.*` |
| 8 | Настройки → «Об устройстве» | Названия тиров, не ключи |
| 9 | Звонок / отказ / сброс | Тексты причин переведены |
| 10 | Маркет → вкладка онлайн | Каталог загружен; при ошибке — текст с URL |
| 11 | `app_store_index_url` = свой адрес | Загружается из него |

### Чек-лист
- [ ] Удалены `DockPrefs.pin` после установки и миграция 0028
- [ ] `Modal` встроен в `AppMarketWindow`, старый диалог удалён
- [ ] Lang слит, прислан список оставшихся сырых ключей
- [ ] `StoreClient` использует `StoreIndexSource`
- [ ] Сборка всех нод Stonecutter

### Файлы
Изменены:
- `src/main/java/com/kasperstudios/kvr/apps/client/AppCatalog.java`
- `src/main/java/com/kasperstudios/kvr/apps/client/AppsClient.java`
- `src/main/java/com/kasperstudios/kvr/spatial/ui/AppLauncherWindow.java`
- `src/main/java/com/kasperstudios/kvr/config/KvrConfig.java` (ключ `app_store_index_url`, `storeIndexUrl()`)
- `src/main/resources/assets/kvr/lang/ru_ru.json`
- `src/main/resources/assets/kvr/lang/en_us.json`

Новые:
- `src/main/java/com/kasperstudios/kvr/spatial/ui/Modal.java`
- `src/main/java/com/kasperstudios/kvr/apps/client/StoreIndexSource.java`

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
  - **поиск**: строка поиск�