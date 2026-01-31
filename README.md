# VoxelGame

A voxel sandbox game built from scratch in Java with LWJGL 3.

## Tech Stack

- **Java 21** — language & runtime
- **LWJGL 3** — OpenGL, GLFW, STB bindings
- **JOML** — math (vectors, matrices, quaternions)
- **SLF4J + Logback** — logging
- **Gradle 8.5** — build system (Kotlin DSL)

## Building & Running

```bash
# Build
./gradlew build

# Run
./gradlew run
```

> **macOS note:** The Gradle `run` task automatically passes `-XstartOnFirstThread` for GLFW compatibility.

## Project Structure

```
src/main/java/com/voxelgame/
├── Main.java                 # Entry point
├── core/                     # Game loop, timing, config, profiling
├── platform/                 # Window & input (GLFW)
├── render/                   # OpenGL rendering pipeline
├── math/                     # Noise, hashing, RNG, curves, SDF
├── world/                    # Chunks, blocks, world management
│   ├── mesh/                 # Chunk meshing (naive + greedy)
│   ├── stream/               # Async chunk loading/generation
│   ├── gen/                  # World generation pipeline & passes
│   └── evolve/               # World evolution system
├── save/                     # Save/load, region files
├── sim/                      # Player, physics, collision
├── ui/                       # HUD, debug overlay
└── tools/                    # Dev tools (heightmap dump, etc.)

src/main/resources/
├── shaders/                  # GLSL vertex & fragment shaders
├── textures/                 # Block texture atlas (TBD)
└── config/                   # Game defaults & worldgen presets
```

## Keybind Conventions

| Key | Action |
|-----|--------|
| W/A/S/D | Move forward/left/back/right |
| Space | Jump |
| Shift | Sprint |
| Left Click | Break block |
| Right Click | Place block |
| 1–9 | Select hotbar slot |
| F3 | Toggle debug overlay |
| F5 | Toggle WorldGen Lab |
| Esc | Pause / release cursor |

## WorldGen Iteration Workflow

1. Edit a preset JSON in `src/main/resources/config/worldgen_presets/`
2. Launch with `./gradlew run`
3. Press **F5** to open the WorldGen Lab overlay
4. Toggle individual passes on/off to isolate behavior
5. Use dev tools (`HeightmapDumpTool`, `MaskDumpTool`) for offline visualization
6. Iterate on noise parameters until terrain looks right
7. Lock the preset for production worlds via `GeneratorLock`

## Block Registry (MVP)

15 blocks: AIR, STONE, COBBLESTONE, DIRT, GRASS, SAND, GRAVEL, LOG, LEAVES, WATER, COAL_ORE, IRON_ORE, GOLD_ORE, DIAMOND_ORE, BEDROCK

## License

MIT — see [LICENSE](LICENSE).
