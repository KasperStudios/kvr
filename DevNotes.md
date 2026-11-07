# KVR DevNotes

> IMPORTANT: This file contains critical project rules, implementation context, and constraints for the author and AI assistants.
> Treat this file as the operational contract of the project.
> If something is unclear, prefer existing code/project patterns over invention.

---

## 1. Project Overview

### What this project is
KVR ("Kasper Virtual Reality") is a Minecraft mod that implements a spatial operating system inside
the game: floating 3D windows anchored to the world or the body, hardware tiers for the worn glasses,
battery physics with thermal-runaway explosions, and a QuickJS runtime for user apps (`.vrapp`).

### Core goal
Deliver the full feature set described in `SPEC.md` on Fabric and NeoForge, for Minecraft 1.20.x
through 1.21.11+, from one shared code base.

### Success criteria
- A single `:common` core compiles unchanged for every supported loaders/version combination.
- No gameplay feature is duplicated per loader: loaders only provide platform facades.
- `SPEC.md` roadmap blocks are the source of truth for scope; each block is one implementation ticket.

### Project type
- [x] Game / mod

---

## 2. Current Status

### Status summary
Early development. Block 1 (architectural skeleton) is complete and verified: the build compiles and
packages all Matrix entries, and the core tests pass on every node.

### Implemented
- [x] Multi-module Architectury Loom project: `src/` (= `:common`), `fabric/`, `neoforge/`.
- [x] Stonecutter matrix: 1.20.1, 1.20.4, 1.21.1, 1.21.11 (`versions/<mc>/gradle.properties`);
      Fabric for all four, NeoForge for 1.20.4+ (NeoForge does not exist for 1.20.1).
- [x] Platform facades in `com.kasperstudios.kvr.platform`: `KVRPlatform` (metadata/paths/side),
      `KVRRegistrar` (items/blocks/entity types, ids already validated), `KVRGui` (screens) with
      `SimpleKVRGui` installed on the client side, each with a provider.
- [x] Fabric and NeoForge entrypoints installing the facades and booting `KVR.init()`.
- [x] Tests: bootstrap seam, registry-path validation (incl. path traversal), GUI facade,
      module isolation, hardware tiers/spec matrix/chassis rule/build aggregation/palette
      (38 tests x 4 versions, 152 total).
- [x] Block 2a content: 34 items (25 module items, 5 chassis, UpOre raw/ingot, upgrader template) and
      the `up_ore` block, registered through the loader facade; version-shaped recipes + ore loot table;
      generated 16x16 textures/models with no external assets.
- [x] Upgrade price rules: `TierUpgrade` (4/16/64/256, template required), `UpgradeRules` (exactly what
      is missing, chassis-room check), `CompressedOre` (64 ore per block, so 256 = 4 blocks).
- [x] Runtime verification: `kvr-fabric-0.1.0+1.21.1.jar` loads on a real Fabric 1.21.1 dedicated
      server and logs `KVR OS core 0.1.0 booting on fabric (dedicated server)`.
- [x] Architectury platform-side implementations (`...platform.fabric.*ProviderImpl`,
      `...platform.neoforge.*ProviderImpl`) for the transformed facade calls.
- [x] Block 4 Spatial UI & Engine (Task 0001 from Retool):
      - 21 core Spatial classes: `SpatialWindow`, `WindowSurface` (offscreen FBO), `WindowManager` (RAM check),
        `SpatialCursor` (Alt-toggle, deadzone camera turn), `SpatialRaycast`, `GrabController`, `SpatialInput`,
        `SpatialRenderer` (in-world quads + pointer), `GlassStyle` (nine-slice glass GUI).
      - Anchors: `WorldAnchor`, `BodyAnchor`, `LazyFollowAnchor` (Quest-style dock & toast panels).
      - Detached vanilla screens: `SpatialScreenProxy` (3D button on any container screen).
      - Generated 9-slice GUI sprites & mcmeta directly into `assets/kvr/textures/gui/sprites/`.
      - SpongePowered Mixin compileOnly dependency added to `:common` build.
      - Full compilation & test verification on 1.21.1 (Fabric & NeoForge jar assembled and verified).
- [x] Block 3a Power Model & Overload Physics (Task 0002 from Retool):
      - `PowerTable`, `PowerState` (ON, HUD_OFF, OFF), `PowerBuild`, `PowerData` (FE charge, wear degradation, temperature),
        `PowerSimulator` (cold boot by CPU tier, step FE simulation, 30s overload countdown, Power Off defuse).
      - Server overload physics: `OverloadEffects` (server-wide siren horn, chat warning, 4 explosion outcomes, 50% part burning `PartBurn`).
      - Mixins: `ItemEntityMixin` (ticking overload on dropped glasses item), damage type `kvr:battery_explosion`.
      - Full test suite: `PartBurnTest`, `PowerBuildTest`, `PowerDataTest`, `PowerSimulatorTest` (89 tests total passed).
- [x] Block 3b & 3c: KVRNetwork Facade & Dock Plate Assets (Task 0004 & 0005 from Retool):
      - `KVRNetwork` facade with `sendToServer`, `sendToPlayer`, client/server packet handlers.
      - `DockLogic`, `DockPlateBlock`, `DockPlateBlockEntity`, `KVRDock`: pressure-plate block with glasses & solar module interaction,
        4000 FE buffer, redstone/daylight generation, glasses charging 50 FE/t, overload explosion handling on plate.
      - Client & Network: `SetPowerStatePayload` (C2S), `PowerServerHandler`, `PowerClient` snapshot & strobe,
        physical power key `PowerKey` ('O'), floating `PowerWindow`, screen-space vignette `AlarmOverlay`, `onHudRender` hook.
      - Full test suite: `DockLogicTest`, `PowerDataTest` additions (95 tests total passed, 0 failures).
      - Both Fabric and NeoForge jars fully built and verified.
- [x] Block 4 Artifact: HackGlasses & MITM Projection (Task 0006 from Retool):
      - Registered `kvr:hackglasses` as standalone secret artifact without crafting, creative tabs or battery requirements.
      - `KVRLoot` facade + `FabricKVRLootProvider` & `NeoForgeKVRLootProvider`: 0.5% drop in any generated chest.
      - `SpyMath`, `WindowSummary`, `SpyRelay`, `SpyClient`, `GhostRenderer`: looking at a player with KVR glasses projects their
        floating windows and battery percentage with green `hack_grid` overlay in real-time.
      - Animated item texture (10 frames) `hackglasses.png` + `.mcmeta` + model `hackglasses.json`.
      - Unit tests: `SpyMathTest`, `WindowSummaryTest` (102 tests total passed, 0 failures).
- [x] HackGlasses Part 2 & Multiplayer Alt Hand Synchronization:
      - `HackProtocol`: C2S `kvr:hack_input`, S2C `kvr:inject_input`, S2C `kvr:hack_targets` (radar for KVR players in range).
      - `GhostRaycast` & `SpatialInput`: raycasting against victim ghost windows in Alt mode, drawing green crosshair on target, remote click/release/char injection.
      - Multiplayer Alt Hand Pose Sync (ADR-001): `HandPose`, `HandPoseRelay` (C2S `kvr:hand_report`, S2C `kvr:hand_view`), `HandPoseClient`, and `PlayerModelMixin` updated so remote players' right arms actively track their spatial cursor in multiplayer.
      - Unit tests: `HackProtocolTest`, `HandPoseTest` passing.
- [x] Complete System Apps & Spatial Gestures Pipeline (SPEC Block 5.3 & ADR-001):
      - `kvr.storage`: fast local key-value persistence in `config/kvr/data/<appId>/storage.json` (`AppStorage` + `kvr.storage.get/set/remove/keys`). Unit-tested in `AppStorageTest`.
      - Builtin app `kvr.player`: in-visor music & disc player with Play/Stop/Next/Prev, live status, and track memory.
      - Builtin app `kvr.ide`: live JavaScript code studio with multiline text editor, live `eval()` execution, log output, shake/sound effects, and VFS file save/load (`/home/script.js`).
      - In-visor system screens: `SettingsWindow` (opacity, visibility, desktop toggle, power), `AppLauncherWindow` (ADR-001 Gesture 1), `VirtualMirrorWindow` (ADR-001 Gesture 4: 3D player avatar mirror), `WorkspaceWindow` (ADR-001 Gesture 5: create empty space/monitor).
      - Full unit test verification across ChicoryJsEngineTest and Stonecutter matrix.
- [x] Block 5 Skeleton: Script Engine Core & Watchdog (Task 0007 from Retool):
      - Engine-agnostic contract: `JsEngine` (eval, call with JSON args, host functions, interrupt, memory measurement).
      - Fixed-step clock: `ScriptClock` implementing CPU tiers (10/20/50/100/200 Hz) with max 3-tick catch-up limit.
      - Watchdog timer: `Watchdog` enforcing 25% tick budget limit, strike counter (3 slow ticks -> NOT_RESPONDING), and 4x budget instant KILL.
      - Unit tests: `ScriptClockTest`, `WatchdogTest` (109 tests total passed, 0 failures).
- [x] Block 5a & D1: Headless Desktop Daemon & Settings Chrome (Tasks 0012, 0013, 0014):
      - `kvr-desktopd`: native Rust headless Wayland compositor built with Smithay 0.6. No windows in WM/niri.
      - Zero-copy shared memory bridge (`FileChannel.map` + triple buffering) with damage regions for <1 ms per frame.
      - Security consent screen (`DesktopConsentScreen`) storing SHA-256 hash before launch.
      - `WindowChrome` gear menu on all windows: Anchor toggle (Body/World), Visibility toggle (Public/Private), Lock toggle (misclick protection in PvP/mining).
      - Cross `×` button in top right for instant window close.
      - `ChicoryJsEngine` + QuickJS-ng WASM runtime bundled (165/165 tests passed, 0 failures).
- [x] Block 5e System Apps & TextInput (Task 0011 from Retool):
      - Text input system: `TextInput` widget, `TextInputScreen` capturing keyboard focus for floating 3D windows with single/multiline support, Enter, Ctrl+Enter, and Ctrl+V clipboard pasting.
      - Shoulder pin: `PinnedNote` floating at right shoulder via `LazyFollowAnchor` without consuming RAM slots.
      - Messenger protocol: `MessengerProtocol`, `MessengerServer` (rate limiting 5 msg / 5s, server file forwarding between `/home/` and `/home/inbox/`), `MessengerClient` (toasts + pending offline message queue).
      - `prelude.js` expanded: `kvr.TextInput`, `kvr.pin()`, `kvr.player.coords()/name()`, `kvr.players.online()`, `kvr.lang`, `kvr.msg.send/pending/on()`.
      - Builtin apps: Notes (notes manager, VFS integration, pinning), Calc (math parser, stack & shulker converter), Messenger (contacts, chat, coordinate sharing, file attachments).
      - Unit tests: `MessengerProtocolTest` (124 tests total passed, 0 failures).
- [x] Block 5b Virtual File System (Task 0010 from Retool):
      - Server-side VFS bound to glasses stack UUID: files stored in `<world>/kvr/vfs/<uuid>/` (steal glasses = steal files).
      - Strict sandbox enforcement in `VfsPath`: `/home/` & `/tmp/` shared, `/data/<app>/` private to app, `/apps/` read-only, `/system/` denied.
      - Path normalization: dot rejection, path traversal protection, max length 128, max depth 6.
      - Storage quotas from hardware tier: T1 256KB (2 files), T2 1MB (5 files), T3 8MB, T4 64MB, T5 512MB.
      - Atomic writes (`.tmp` move), recursive directory cleanup, 128KB write / 256KB read caps per network call.
      - `VfsProtocol` and `VfsServer` with per-player rate limiting (40 req/s flood guard).
      - `VfsClient` with async callback dispatch to client thread and 256 max pending request limiter.
      - Unit tests: `VfsPathTest`, `VfsProtocolTest`, `VfsStoreTest` (122 tests total passed, 0 failures).
      - `ScriptHost`: per-app background thread daemon with watchdog monitor thread and UI event queue.
      - `prelude.js`: full declarative JS API (`kvr.Window`, `Text`, `Button`, `Slider`, `Stack`, `setTimeout`/`setInterval`, `kvr.toast`, `window.shake`, `sound.play`).
      - `ModuleShim`: transforms `import { ... } from 'kvr'` into global destructuring for modern JS support.
      - `UiNode`: AST parser for JS widget tree committed across threads with 512 max node protection.
      - `ScriptWindow`: floating app window occupying RAM slots, rendering dynamic UI nodes, buttons, sliders, gradients and shake.
      - Bundled apps: `Hello KVR` (counter, clock, toasts) and `KAV — PonoScan 3000` (full scam scan, red alert, explosion, reboot).
      - HUD_OFF mode refined: windows and raycast are hidden while apps continue running safely in background.
      - Unit tests: `ModuleShimTest`, `UiNodeTest` (115 tests total passed, 0 failures).

### In progress
- [x] Block 2b (container-menu based, decided by the Creator). Landed: glasses item + stack codec +
      Shift+RMB disassembly, `AssemblyRules` (6 inputs -> build or explicit problems), and
      `KVRRegistrar.menu(...)` so the loader facade can register menu types.
- [x] Menu types are built by the loader now: Fabric `ExtendedScreenHandlerType` (StreamCodec payload
      from 1.20.5, factory-only before) and NeoForge `IMenuTypeExtension`, both through
      `KVRRegistrar.menuType(...)`. `kvr:chassis_assembly` registers and the server boots with it.
- [x] Assembly GUI end to end: right-click a chassis opens `kvr:chassis_assembly`; client screens are
      bound through Fabric `ClientModInitializer` + `MenuScreens.register` and NeoForge
      `RegisterMenuScreensEvent`; the screen paints its own panel (`AbstractContainerScreen`, no textures).
- [x] Native vanilla Smithing Table upgrades (`minecraft:smithing_transform`):
      Template + Part/Chassis + Material (UpIngot for T1->T2, T2->T3; CompressedUpOre for T3->T4, T4->T5).
      The hacky UpgradeMenu/Screen and template right-click behavior were completely removed.
- [x] Paint storage in the vanilla place: `display.color` NBT before 1.20.5, the `dyed_color` component
      from 1.20.5 (1.21.2 dropped its `showInTooltip` flag - Stonecutter helper).
- [x] Painting applied via Crafting Table special recipe (`ChassisDyeRecipe`), matching vanilla `ArmorDyeRecipe`.
      Cross-version recipe serializer registration across Fabric & NeoForge.
- [x] Assembled glasses inherit the chassis paint (KVRPaintData in ChassisAssemblyMenu).
- [x] Block 2c: UpOre veins in the Overworld and the Nether (worldgen: configured/placed features generated
      per version + Fabric BiomeModifications & NeoForge BiomeModifier JSONs), compressed UpOre block
      item & block registered, loot table and recipes wired, UpgradeMenu updated to count & deduct compressed blocks.

### Planned / TODO
- [ ] Blocks 3-5 from `SPEC.md` section 7 (energy/dock plate, spatial engine, QuickJS + apps).
- [ ] Network facade (`KVRNetwork`) — deliberately deferred to the block that ships the first packet,
      so its API is designed against real payloads instead of guesses. Tracked as an open SPEC item,
      not as a completed one.
- [ ] Register the first real screens in `SimpleKVRGui` (Block 2, `ChassisAssemblyScreen`).

### Not in scope right now
- No content registration yet: zero items/blocks are registered, so the mod currently boots and does nothing visible.

---

## 3. Tech Stack

### Core stack
- Runtime: Java 21 (Java 17 source level for 1.20.1 targets)
- Language(s): Java
- Framework(s): Architectury Loom (Fabric + NeoForge), Stonecutter versioning
- Build tools: Gradle 9.8.2 (wrapper), Kotlin DSL build scripts, `buildSrc` helpers
- Storage / DB: none yet (VFS arrives with the QuickJS block)

### Versions
- Minecraft: 1.20.1 / 1.21.1 / 1.21.11
- Mappings: official Mojang mappings (mojmap) on every loader
- Architectury Loom: 1.17-SNAPSHOT; Architectury Gradle plugin: 3.5-SNAPSHOT (maven.shedaniel.me)
- Stonecutter: 0.9.8 (requires Gradle 9)
- Fabric Loader: 0.19.2
- NeoForge: 20.4.237 (1.20.4), 21.1.264 (1.21.1), 21.11.48 (1.21.11)
- JUnit: 5.10.2

### Environment notes
- OS: Linux (cross-platform project)
- Required tools: git, JDK 21 (`JAVA_HOME` must point at it), no system Gradle needed
- Special setup notes:
  - `architectury-injectables` (1.0.13) is the only Architectury artifact used: it provides the
    `@ExpectPlatform` annotation and is version independent, which keeps the version matrix free of
    per-version Architectury API versions.
  - Architectury Loom 1.17 needs Gradle 9 and the Architectury Gradle plugin 3.5 (3.4 crashes on
    `RunConfig`). Both constraints are load-bearing - do not "downgrade to a stable release".
  - NeoForge 21.11 needs no yarn mapping patch because the project uses mojmap.

---

## 4. Architecture Notes

### High-level architecture
Three layers:

1. `src/` — the platform-independent KVR core (`com.kasperstudios.kvr`). Compiles against Minecraft
   classes (mojmap) but never against a loader.
2. `fabric/`, `neoforge/` — thin branches: entrypoint installs the facade implementations, nothing else.
3. `versions/<mc>/gradle.properties` — per-Minecraft dependency data used by Stonecutter.

### Main modules / layers
- `core/` — `KVRNames` (path validation, pure Java), `KVRIds` (identifier factory).
- `platform/` — facade interfaces + providers (`KVRPlatformProvider`, `KVRRegistryProvider`, `KVRGuiProvider`).
- `KVR` — lifecycle entry point called by both loaders.

### Dependency direction
```txt
[fabric]     -> [common core]
[neoforge]   -> [common core]
[common core]-> [minecraft classes] only
```

### Important boundaries
- `src/main/java` must not import `net.fabricmc.*` or `net.neoforged.*` — enforced by
  `ArchitectureIsolationTest`, not by review habit.
- The core asks for a screen by id (`KVRGui.open("chassis_assembly")`); it never names a `Screen`
  class, so server-side code stays clean.

---

## 5. Project Structure

```txt
kvr/
├── build.gradle.kts        # common (root) project
├── settings.gradle.kts     # Stonecutter node/branch declaration
├── stonecutter.gradle.kts  # active version + plugin versions
├── buildSrc/               # gradle helpers (mod.prop/mod.dep accessors)
├── src/                    # common core sources + tests
├── fabric/                 # Fabric branch (build script, sources, resources)
├── neoforge/               # NeoForge branch
├── versions/<mc>/          # per-version gradle.properties
└── docs/adr/               # accepted architecture decisions
```

---

## 6. Language & Text Rules

- Code, identifiers and comments: English.
- User-facing strings: Russian (the target audience), added via lang files when content lands.
- Javadoc explains *why* a decision exists, not what the line does.

---

## 7. Code Rules

### General style
- Follow existing patterns (facade + provider + entrypoint) before inventing new ones.
- Keep the core minimal; do not add helpers that only one call site uses.

### Naming
- Mod id / namespace: `kvr`; resource paths validated by `KVRNames.validated`.
- Packages: `com.kasperstudios.kvr.<layer>`; loader packages `...kvr.fabric`, `...kvr.neoforge`.

### Logging
- Use the SLF4J logger from `KVR.logger()`.
- Do NOT use `System.out` or `printStackTrace`.

### Errors
- Fail clearly: a missing platform/registrar throws `IllegalStateException` with the fix in the message.
- Never swallow exceptions silently.

### Async / threading / concurrency
- Not applicable yet; when the scripting block lands, script execution must be off the render thread
  and guarded by the watchdog described in `SPEC.md` 5.1.

### Performance
- Nothing may run per-frame in the core until the spatial engine block defines the render hooks.

### Reuse rules
1. Check `core/` and `platform/` first.
2. Reuse an existing facade before adding a new provider.
3. Only add an abstraction when two call sites need it.

---

## 8. Critical Rules

> These rules are non-negotiable.

0. Creator-confirmed hardware semantics: CPU = script tick rate / app responsiveness only (no invented
   boot-time numbers), GPU = window rendering and frame drops, RAM = simultaneous `.vrapp` windows
   (vanilla detached screens do not count), Storage = VFS capacity, Battery = capacity + safe discharge.
   Upgrade costs are exactly 4/16/64/256 UpOre and are consumed by the KVR station, not by a vanilla
   recipe slot.

1. `src/` (the core) must never import a loader class. Platform access goes through `platform/` facades.
2. Loader-specific code lives only in `fabric/` or `neoforge/` and must be thin (entrypoint + facades).
3. Version differences are handled by Stonecutter only (`//? if <cond> {`), never by runtime version checks.
4. The active Stonecutter version (`stonecutter active` in `stonecutter.gradle.kts`) must always be
   the version whose code is written uncommented in the sources; older/newer branches stay commented.
5. No shipped feature may be duplicated per loader or per Minecraft version.

---

## 9. AI Assistant Rules

- Read `SPEC.md` and the ADRs before implementing; do not invent mechanics that contradict them.
- One roadmap block per implementation run (ticket-sized).
- After a change: run `./gradlew test` (all nodes) and `./gradlew build` before claiming success.
- Update this file when status/stack/rules change: it is the contract.
- Do not add Architectury API (`dev.architectury:architectury`) without a concrete need; it drags a
  per-version dependency into the matrix.

---

## 13. Common Pitfalls

- **Stonecutter 0.9.8 requires Gradle 9**; Loom 1.17 requires Gradle 9 too. Gradle 8.x is not an option.
- **Architectury Gradle plugin 3.4 crashes** with Loom 1.17 (`NoClassDefFoundError: RunConfig`) - use 3.5-SNAPSHOT.
- **NeoForge reads a different metadata file per era**: `META-INF/mods.toml` before 1.20.5,
  `META-INF/neoforge.mods.toml` from 1.20.5. `neoforge/build.gradle.kts` renames the single template at
  build time; a wrong name makes FML silently report "1 invalid mod files" with no reason at INFO level.
- **Maven version ranges with identical bounds are invalid** (`[1.20.4, 1.20.4]` -> FML 2 throws
  `InvalidVersionSpecificationException`). Use half-open ranges: `mod.mc_dep_forgelike=[1.20.4,1.20.5)`.
- **Content mods need Fabric API, not just the loader**: without `fabric-api` the vanilla registries are
  already frozen when `ModInitializer` runs and constructing an `Item`/`Block` throws
  `This registry can't create intrusive holders`. KVR declares `fabric-api` as a dependency and the
  test server must have it in `mods/`.
- **Never construct items/blocks eagerly at class init**: NeoForge builds mod instances before
  registries are open. `KVRContent` stores factories and lets each loader execute them at its own
  registration point (Fabric: mod init, NeoForge: `RegisterEvent`), memoized so lookups stay cheap.
- **MC 1.21.9+ requires explicit ids in item/block properties** (`properties.setId(ResourceKey...)`),
  otherwise NeoForge 21.11 dies with `NullPointerException: Item id not set` during `RegisterEvent`.
  Handled by a Stonecutter branch in `KVRContent`.
- **FML loader range differs per era**: NeoForge 20.4 uses FML 2 (`loaderVersion = "[2,)"`), 21.x uses
  FML 4/10 (`"[4,)"`). Both are templated per version (`mod.fml_loader_range`, `mod.neoforge_range`).
- **NeoForge < 20.5 has no `ModContainer`/`IEventBus` constructor injection**: the entrypoint needs
  `FMLJavaModLoadingContext.get().getModEventBus()` + `ModList.get().getModContainerById(...)`, which is
  a Stonecutter branch in `KVRNeoForge`.
- **Mojang renamed `ResourceLocation` to `Identifier` in 1.21.11** (same class, same API, package
  `net.minecraft.resources`). One Stonecutter string replacement handles it project-wide; without it
  every id call fails to compile on 1.21.11.
- **NeoForge 21.11 / FML 10 replaced `FMLEnvironment.dist`** with `FMLEnvironment.getDist()`.
- **Architectury really does transform `@ExpectPlatform` methods at build time** and expects the
  implementation as `<class package>.<loader>.<Class>Impl` (e.g.
  `com.kasperstudios.kvr.platform.fabric.KVRPlatformProviderImpl`). Missing that class compiles
  fine and only fails when the mod loads (`NoClassDefFoundError` in the entrypoint).
- **Stonecutter's Gradle DSL cannot be moved into `buildSrc` or an applied `.gradle.kts` script**:
  applied scripts are compiled without the plugin classpath (`stonecutter`/`loom` unresolved), so the
  `stonecutter { replacements... }` block stays inline in all three build scripts. Only plain Gradle
  helpers (e.g. `applyKvrJavaVersion`) belong in `buildSrc`.
- **Metadata is templated from gradle.properties, never hardcoded**: mod version = project version
  (`0.1.0+<mc>`), fabric loader floor = `deps.fabric_loader`, `pack_format` = per-version
  `mod.pack_format` (1.20.1=15, 1.21.1=34, 1.21.11=75). A stale `pack_format` or version string ships
  silently and only breaks clients.
- **`DyedItemColor` took `(int rgb, boolean showInTooltip)` until 1.21.1 and `(int rgb)` from 1.21.2.**
- **`Inventory.items` / `Inventory.offhand` are private from 1.21.11**: iterate
  `getContainerSize()` + `getItem(i)`/`setItem(i, stack)` instead of touching the arrays.
- **`renderBackground(GuiGraphics, int, int, float)` only exists from 1.20.5**: before that the method
  takes just `GuiGraphics`. Do not override `Screen#render` to call it; let `AbstractContainerScreen`
  handle the background.
- **`new MenuType<>(...)` does not compile with mojmap**: the constructor and its `MenuSupplier` are not
  public, so every custom menu type has to be built by the loader (NeoForge `IMenuTypeExtension`,
  Fabric `ExtendedScreenHandlerType`). Keep menu-type creation in the platform modules.
- **`Item#use` changed in 1.21.2, not 1.20.5**: before 1.21.2 it returns `InteractionResultHolder<ItemStack>`,
  after it returns `InteractionResult`. Important version seam in 1.21.11: `Level.isClientSide` became a
  method (`isClientSide()`) and `InteractionResult.sidedSuccess(boolean)` was replaced by the `SUCCESS`
  constant - `GlassesItem` wraps both in tiny Stonecutter-branched helpers.
- **`CompoundTag.getIntArray` returns `Optional<int[]>` from 1.21.11** (plain `int[]` in 1.21.1), so the
  glasses data reader branches three ways: NBT (<1.20.5), component + plain array (1.20.5-1.21.1),
  component + Optional (1.21.11+).
- **Never write the "wrong" branch uncommented**: the sources as written are the *active* version's
  variant; the other side must be wrapped in `/* ... *///?}`. A wrong active branch compiles against
  the wrong API and the error looks like a missing symbol.
- `git clean`/deleting `versions/*/.gradle` forces a full Loom re-setup of that version (minutes).

---

## 14. Testing & Validation

- `./gradlew test` runs the suite for every Stonecutter node (root + each branch version).
- Seams under test:
  - `KVRNames.validated` - path rules for every registry id.
  - `KVR.init()` bootstrap - platform required, idempotent, reports `platformName()`.
  - Module isolation - no loader imports in `src/main/java`.
- `./gradlew build` must produce jars for all five matrix entries before a block is called done.
- Runtime smoke test (the only proof the mod actually loads):

```bash
# Fabric
mkdir -p /tmp/kvr-srv/mods && cd /tmp/kvr-srv
curl -LO https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.1.2/fabric-installer-1.1.2.jar
java -jar fabric-installer-1.1.2.jar server -mcversion 1.21.1 -loader 0.19.2 -downloadMinecraft
cp <repo>/fabric/versions/1.21.1/build/libs/kvr-fabric-*.jar mods/
printf 'eula=true\n' > eula.txt
java -Xmx1200M -jar fabric-server-launch.jar nogui

# NeoForge
curl -LO https://maven.neoforged.net/releases/net/neoforged/neoforge/21.11.48/neoforge-21.11.48-installer.jar
java -jar neoforge-21.11.48-installer.jar --installServer .
cp <repo>/neoforge/versions/1.21.11/build/libs/kvr-neoforge-*.jar mods/
env JAVA_HOME=/usr/lib/jvm/java-21-openjdk PATH=$JAVA_HOME/bin:$PATH ./run.sh nogui
# expect: "Found mod file "kvr-neoforge-...jar"" and "KVR OS core <ver> booting on <loader> (dedicated server)" then "Done (...)"
```

Verified 2026-10-23 on real dedicated servers: fabric 1.20.1/1.20.4/1.21.1/1.21.11,
neoforge 1.20.4/1.21.1/1.21.11 — all boot KVR with no errors.

---

## 17. Status Tables / Feature Matrix

| Block (SPEC section 7) | Status |
|---|---|
| 1. Architectural skeleton | done (network facade intentionally open) |
| 2. Items, crafts, hardware | not started |
| 3. Energy, dock plate, Note 7 | not started |
| 4. Spatial engine, 3D rendering | not started |
| 5. QuickJS sandbox and apps | not started |

---

## 19. Lessons / New Rules

- Recipes are version-shaped data, not code: the folder name (`recipes` → `recipe`), the ingredient
  form (`{"item": id}` → `"id"`) and the result key (`item` → `id`) all moved between 1.20.4 and
  1.21.11. They are generated per version by `gradle/kvr-recipes.gradle.kts` from the tier ladder.
- A vanilla crafting/smithing slot consumes one item per slot, so a "16 UpOre" cost cannot be a recipe.
  Item costs above 9 belong in a custom screen that consumes from the inventory.
- Verify a version-matrix assumption (does this loader/version combination exist?) before writing
  build config: 1.20.1 has no NeoForge, and yarn mapping patches stop at 1.21, which is why the
  project uses mojmap everywhere.

---

## 20. Change Log of DevNotes

- 2026-10-23: created after Block 1 (skeleton) landed.
- 2026-10-23: two-axis review fixes - single namespace constant, validated ids through the registrar,
  entity-type facade, client-side GUI registry, stronger isolation test, templated mod metadata
  (real version / loader floor / pack_format), path-traversal rejection.
