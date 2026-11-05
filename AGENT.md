# Работа с агентом: как забирать код и возвращать результаты

Здесь лежит Java-код мода KVR, который пишется в этом чате. Цикл такой:
задание → код здесь → агент забирает → собирает и тестирует на ноутбуке → присылает результат → правки.

## Где что лежит

| Путь (от корня репозитория приложения) | Что это |
|---|---|
| `frontend/kvr-java/` | Java-исходники **в раскладке репозитория мода**: `src/`, `fabric/`, `neoforge/`, `src/test/`, `src/main/resources/`. Копируются поверх корня мода 1:1 |
| `frontend/kvr-java/README.md` | Архитектура Spatial UI и порядок подключения |
| `frontend/kvr-java/CHANGELOG.md` | Что и в каком задании менялось: забирать только перечисленные файлы |
| `frontend/textures/kvr/` | Генераторы текстур KVR. Готовые PNG/mcmeta/geo.json выгружаются кнопкой «Скачать ZIP» на странице `/kvr` |
| `frontend/pages/kvr/java/files.ts` | Список файлов для просмотрщика `/kvr/ui` (служебный, в мод не копировать) |

Если прямого доступа к файлам нет: на странице `/kvr/ui` → «Java-классы» → «Скачать всё (ZIP)».
Внутри архива папка `kvr-spatial-ui/` с той же раскладкой.

## Формат задания (от агента)

```
ЗАДАНИЕ <номер>: <коротко>
Блок SPEC: <1–5>
Что сделать: ...
Ограничения / решения Создателя: ...
```

## Формат отчёта после теста (от агента)

```
ОТЧЁТ <номер задания>
Коммит/версия кода: <дата или хеш>
Сборка: ./gradlew build — OK / FAIL
Тесты: ./gradlew test — N passed / M failed
Запуск: fabric 1.21.1 клиент — OK / краш
Ошибки (дословно, с файлом и строкой):
  src/main/java/.../SpatialRenderer.java:87: error: cannot find symbol ...
Краш-лог: <первые ~40 строк stacktrace + "Caused by">
Что видно в игре: <поведение, скриншот если есть>
```

Главное — **точный текст ошибки компилятора с путём и номером строки**. В этой среде нет
Gradle и Minecraft, поэтому сборка и запуск проверяются только у вас.

## Правила, которых держится код (из DevNotes)

- `src/` (common) — никаких `net.fabricmc.*` / `net.neoforged.*`; доступ к загрузчику только через `platform/`.
- Версионные различия — только Stonecutter (`//? if`), активная ветка пишется раскомментированной. Сейчас код написан под **1.21.1**.
- Mojmap, Java 21 (17 для 1.20.1), логгер `KVR.logger()`, пользовательские строки — через lang-ключи.
- Одно задание — один тикет. После изменений: `./gradlew test` и `./gradlew build` на всех нодах.

## API проекта, под которые пишется новый код

### Сеть — `platform/KVRNetwork` (ваша реализация, отчёт 0005)
Канальная модель: `ResourceLocation` + `Consumer<FriendlyByteBuf>`.
```java
KVRNetwork.sendToServer(ResourceLocation channel, Consumer<FriendlyByteBuf> encoder);
KVRNetwork.sendToPlayer(ServerPlayer player, ResourceLocation channel, Consumer<FriendlyByteBuf> encoder);
KVRNetwork.registerClientReceiver(ResourceLocation channel, PacketHandler handler);         // handle(FriendlyByteBuf)
KVRNetwork.registerServerReceiver(ResourceLocation channel, ServerPacketHandler handler);   // handle(ServerPlayer, FriendlyByteBuf)
```
Соглашения для новых пакетов:
- каналы `kvr:<имя>`, константы рядом с фичей;
- данные декодируются сразу в хендлере (буфер живёт только на время вызова),
  дальше — `server.execute(...)` / `Minecraft.getInstance().execute(...)`;
- строки с лимитом: `writeUtf(s, max)` / `readUtf(max)`.

### Фасады из чата
`KVRClient` (клавиши, тик, рендер мира, init экрана, HUD-оверлей), `KVRServer` (команды, тик сервера),
`KVREnergy` (FE док-плиты), `KVRLoot` (лут сундуков).

### Переименования на вашей стороне
- `DockPlateBlock.properties()` → `dockProperties()` (конфликт с `BlockBehaviour.properties()`).
