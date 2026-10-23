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
- [x] Stonecutter matrix: 1.20.1, 1.21.1, 1.21.11 (`versions/<mc>/gradle.properties`).
- [x] Platform facades in `com.kasperstudios.kvr.platform`: `KVRPlatform` (metadata/paths/side),
      `KVRRegistrar` (items/blocks), `KVRGui` (screens), each with a provider.
- [x] Fabric and NeoForge entrypoints installing the facades and booting `KVR.init()`.
- [x] Tests: bootstrap seam, registry-path validation, module isolation (11 tests x 3 versions).

### In progress
- [ ] Block 2 (items, crafts, hardware tiers) — not started.

### Planned / TODO
- [ ] Blocks 3-5 from `SPEC.md` section 7 (energy/dock plate, spatial engine, QuickJS + apps).
- [ ] Network facade (`KVRNetwork`) — deliberately deferred to the block that ships the first packet,
      so its API is designed against real payloads instead of guesses.
- [ ] Client-side screen registry implementation for `KVRGui` (Block 2, `ChassisAssemblyScreen`).

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
- NeoForge: 21.1.264 (1.21.1), 21.11.48 (1.21.11)
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
- **Mojang renamed `ResourceLocation` to `Identifier` in 1.21.11** (same class, same API, package
  `net.minecraft.resources`). One Stonecutter string replacement handles it project-wide; without it
  every id call fails to compile on 1.21.11.
- **NeoForge 21.11 / FML 10 replaced `FMLEnvironment.dist`** with `FMLEnvironment.getDist()`.
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

---

## 17. Status Tables / Feature Matrix

| Block (SPEC section 7) | Status |
|---|---|
| 1. Architectural skeleton | done |
| 2. Items, crafts, hardware | not started |
| 3. Energy, dock plate, Note 7 | not started |
| 4. Spatial engine, 3D rendering | not started |
| 5. QuickJS sandbox and apps | not started |

---

## 19. Lessons / New Rules

- Verify a version-matrix assumption (does this loader/version combination exist?) before writing
  build config: 1.20.1 has no NeoForge, and yarn mapping patches stop at 1.21, which is why the
  project uses mojmap everywhere.

---

## 20. Change Log of DevNotes

- 2026-10-23: created after Block 1 (skeleton) landed.
