# KVR — Kasper Virtual Reality (Spatial OS for Minecraft)

Minecraft mod that turns the game into a spatial operating system: floating 3D windows, a hardware
tier ladder for the glasses you wear, battery physics with thermal runaway, and a QuickJS app runtime.

Design documents:

- [`CONCEPT.md`](CONCEPT.md) — product concept and OS behaviour
- [`SPEC.md`](SPEC.md) — technical specification and implementation roadmap
- [`docs/adr/`](docs/adr) — accepted architecture decisions (controls, spatial windows, hardware)
- [`DevNotes.md`](DevNotes.md) — operational contract: status, rules, how to build

## Status

Block 1 (architectural skeleton) is implemented: a Fabric + NeoForge build that compiles the same
`common` core against Minecraft 1.20.1, 1.20.4, 1.21.1 and 1.21.11. Gameplay content starts with
Block 2; the packet facade is deferred until the first real payload exists.

## Building

Requires JDK 21. The Fabric jar needs **Fabric API** at runtime (registry freeze relaxation for
content mods); NeoForge needs nothing extra.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk

./gradlew test            # tests for every Stonecutter node (16 tests x 4 versions)
./gradlew build           # jars for every loader/version combination
```

Output jars:

```
fabric/versions/<mc>/build/libs/kvr-fabric-<version>+<mc>.jar
neoforge/versions/<mc>/build/libs/kvr-neoforge-<version>+<mc>.jar
```

Supported matrix:

| Minecraft | Fabric | NeoForge |
|---|---|---|
| 1.20.1  | yes | — (NeoForge starts at 1.20.2) |
| 1.20.4  | yes | yes |
| 1.21.1  | yes | yes |
| 1.21.11 | yes | yes |

Every entry above has been booted on a real dedicated server, not just compiled.

## Layout

```
src/            # platform-independent core (:common) — no loader imports allowed
fabric/         # Fabric branch: entrypoint + platform facades
neoforge/       # NeoForge branch: entrypoint + platform facades
versions/<mc>/  # per-version properties consumed by Stonecutter
```

## License

All rights reserved.
