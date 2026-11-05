# KVR Spatial UI — пример реализации на Java

Интерфейс KVR OS без HUD. Всё, что видит игрок, — окна в мире, как в Quest / visionOS:
приложения, док, уведомления, колесо жестов, откреплённые ванильные экраны.

Написано под архитектуру из DevNotes: ядро в `src/` (`:common`) без импортов загрузчиков,
загрузчики дают тонкий фасад, mojmap. Активная ветка Stonecutter — **1.21.1**. Места,
которые меняются в 1.21.2+, помечены `//? if`.

## Слои

| Пакет | Что делает |
|---|---|
| `spatial` | `SpatialPose` (где окно и куда смотрит), `PlayerView` (снимок головы/тела), `SpatialConfig` (точки интеграции), `KVRSpatial` (загрузка) |
| `spatial.anchor` | `WorldAnchor` — висит в мире. `BodyAnchor` — идёт с игроком, не следует за головой. `LazyFollowAnchor` — системные панели в стиле Quest: стоят на месте и подтягиваются, когда игрок отвернулся |
| `spatial.window` | `SpatialWindow` — база окна (рисует себя через обычный `GuiGraphics`). `WindowSurface` — offscreen FBO 2×. `WindowManager` — лимит RAM по тиру (ванильные экраны и системные панели не считаются) |
| `spatial.input` | `SpatialCursor` — Alt-тумблер, deadzone и доворот камеры у края. `SpatialRaycast` — луч руки против плоскости окна. `GrabController` — окно «в руке» за планку. `SpatialInput` — маршрутизация кликов за тик |
| `spatial.render` | `SpatialRenderer` — квады окон, планки захвата и указатель на поверхности (без версионных веток). `RenderCompat` — единственное место с версионным рендер-API. `GlassStyle` — стекло из nine-slice спрайтов |
| `spatial.ui` | `DockBar` (низко перед игроком), `ToastPanel` (чуть выше линии взгляда), `GestureWheel` (появляется у руки) |
| `spatial.vanilla` | `SpatialScreenProxy` — кнопка «3D» на любом контейнере, экран становится парящим окном, меню не закрывается |
| `platform` | `KVRClient` + `KVRClientProvider` — 4 хука: клавиша, начало тика, рендер мира, init экрана |
| `mixin.client` | мышь, колесо, скрытие центрального прицела, «не копать сквозь панель», фон откреплённого экрана |

## Подключение

1. Скопировать `src/`, `fabric/`, `neoforge/` поверх репозитория.
2. Добавить `kvr-spatial.mixins.json` в `fabric.mod.json` (`"mixins"`) и в `[[mixins]]` у
   `neoforge.mods.toml`.
3. Слить lang-ключи в существующие `ru_ru.json` / `en_us.json`.
4. GUI-спрайты (`textures/gui/sprites/*.png` + `.mcmeta`) скачать со страницы текстур KVR,
   категория «GUI-спрайты».
5. В клиентском entrypoint каждого загрузчика:

```java
KVRClient.install(new FabricKVRClientProvider());          // или new NeoForgeKVRClientProvider(modBus)
KVRSpatial.initClient(new SpatialConfig(ramWindows, glassesWorn, batteryPercent, appHost));
```

`SpatialConfig` специально принимает лямбды: UI не угадывает, как хранится сборка очков, —
её отдаёт код из Блока 2 (`GlassesCodec` / `HardwareTable`), а приложения — рантайм QuickJS (Блок 5).

## Как это работает за кадр

1. `ClientTick START` → `SpatialInput`: Alt, луч, клики по окнам съедаются до ванильного `handleKeybinds`.
2. `AFTER_TRANSLUCENT` → `SpatialRenderer`: якоря считают позы, каждое окно рисует GUI в свой FBO,
   затем квады рисуются в мире от дальнего к ближнему, сверху планки и указатель.

## Стыки версий (Stonecutter)

Весь версионный рендер собран в `spatial/render/RenderCompat.java`, остальные классы от версии
не зависят. Исключения: `SpatialCursor` (категория клавиш 1.21.9+), `SpatialScreenProxy`
(`MouseButtonEvent` 1.21.9+), `GlassStyle.id` (`ResourceLocation`) и провайдеры загрузчиков.
На 1.21.5+ (нода 1.21.11) пространственный UI пока выключен (`RenderCompat.SUPPORTED = false`).
Подробности — `CHANGELOG.md`, запись 0002.

## Тесты

`SpatialRaycastTest`, `LazyFollowAnchorTest` — чистая математика на `Vec3`, без клиента.
Запускаются обычным `./gradlew test` на всех нодах.
