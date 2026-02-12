# JavaGame Engine Optimization Project

## Project Goal
Transform this Minecraft-inspired voxel engine into a high-performance system capable of:
- **16 chunks/second** terrain generation while flying forward
- **Zero frame hiccups** during terrain streaming
- **Infdev 611 authentic terrain** generation

## Current Branch
`engine-optimization` (forked from `origin/fix/sanity-check`)

---

## Architecture Overview

### Key Subsystems
| Subsystem | Files | Status |
|-----------|-------|--------|
| Terrain Gen | `world/gen/` | Authentic Infdev 611 implemented |
| Chunk Streaming | `world/stream/ChunkManager.java` (1003 LOC) | Multi-threaded, but optimizations disabled |
| Meshing | `world/mesh/NaiveMesher.java` (1113 LOC) | Per-face with AO, no greedy meshing |
| Rendering | `render/Renderer.java` | Multi-pass with frustum culling |
| LOD System | `world/lod/` | 4-tier LOD (0-3) |

### Thread Model
- **Main Thread**: Input, physics, GL uploads, rendering
- **Gen Pool**: 8 threads for chunk generation (increased from 4)
- **Mesh Pool**: 6 threads for CPU-side mesh building (increased from 3)

### Per-Frame Budgets (UPDATED)
| Operation | Original | New | Notes |
|-----------|----------|-----|-------|
| Close chunk gens | 4 | **8** | LOD 0-1 chunks |
| Far chunk gens | 6 | **12** | LOD 2-3 chunks |
| Full mesh uploads | 12 | **24** | Main thread GL ops |
| LOD mesh uploads | 16 | **32** | Separate budget |

---

## Existing Optimizations (NOW ENABLED BY DEFAULT ✓)

From `BenchFixes.java` - all now `true`:

| Flag | Purpose | Impact | Status |
|------|---------|--------|--------|
| `FIX_MESH_PRIMITIVE_BUFFERS` | Use `float[]`/`int[]` instead of ArrayList | Reduces GC pressure | ✓ Enabled |
| `FIX_B2_PRIMITIVE_MAP` | fastutil `Long2ObjectOpenHashMap` | No boxing in chunk lookups | ✓ Enabled |
| `FIX_B3_SNAPSHOT_MESH` | Snapshot-based neighbor resolution | Zero map lookups in meshing hot path | ✓ Enabled |
| `FIX_B31_SNAPSHOT_OFFTHREAD` | Create snapshots on worker threads | Removes main thread latency | ✓ Enabled |
| `FIX_ASYNC_REGION_IO` | Async disk writes | Non-blocking saves | ✓ Enabled |
| `FIX_ASYNC_REGION_IO_V2` | Bounded backlog + coalescing | Prevents IO queue explosion | ✓ Enabled |

**All existing optimizations are now enabled by default.**

---

## Optimization Phases

### Phase 1: Enable Existing Optimizations ✓ COMPLETE
- [x] Enable all BenchFixes flags by default
- [x] Increase throughput limits (uploads, gen, thread pools)
- [ ] Benchmark before/after (requires runtime testing)

### Phase 2: Visibility & Heightmap Optimization ✓ COMPLETE
- [x] Add heightmap cache to Chunk class
- [x] Optimize HeightfieldVisibility with O(1) lookups
- [x] Lazy heightmap computation with invalidation on block changes

### Phase 3: Implement Greedy Meshing
- [ ] Replace NaiveMesher with proper greedy meshing algorithm
- [ ] Expected: 30-50% reduction in vertex count
- [ ] Must preserve AO and lighting quality

### Phase 4: Memory & Allocation Optimizations
- [ ] Object pooling for ChunkPos, MeshResult
- [ ] Pre-allocated vertex buffers per thread
- [ ] Thread-local builders to avoid contention

### Phase 5: GPU Pipeline Improvements
- [ ] Persistent mapped buffers (ARB_buffer_storage)
- [ ] Batch multiple chunk uploads in single GL call
- [ ] Reduce draw calls via texture arrays

### Phase 6: Advanced Streaming
- [ ] Predictive chunk loading (velocity-based prefetch)
- [ ] Priority queue based on view direction
- [ ] Hybrid sync/async loading for critical chunks

### Phase 7: Rendering Optimizations
- [ ] Y-axis frustum culling
- [ ] Cascade-specific shadow frustum culling
- [ ] Indirect rendering for chunks

---

## Performance Targets

| Metric | Current | Target |
|--------|---------|--------|
| Frame time (P50) | ~32ms | <16ms |
| Chunk gen/sec | ~? | 16+ |
| Loaded chunks | 1382 | 2000+ |
| Hiccups at speed | Many | Zero |

---

## Session Log

### Session 1 (2026-02-12)
- Forked repo to `machi47/JavaGame`
- Created `engine-optimization` branch from `fix/sanity-check`
- Completed architecture analysis
- Identified that all BenchFixes are disabled by default
- Created this planning document

**Commits:**
1. `acdc50b` - Enable all optimizations and increase throughput limits
2. `a2ca5b3` - Add heightmap cache for O(1) visibility lookups

**Changes made:**
- All BenchFixes now enabled by default (primitive buffers, snapshot meshing, async IO)
- Increased thread pools: Gen 4→8, Mesh 3→6
- Increased upload budgets: Full 12→24, LOD 16→32
- Added heightmap cache to Chunk for fast visibility
- HeightfieldVisibility now uses O(1) heightmap lookups

**Next priorities:**
- Implement greedy meshing for 30-50% vertex reduction
- Consider thread-local vertex builders
- Push branch to origin

---

## Files to Watch

Critical files for optimization work:
- `src/main/java/com/voxelgame/bench/BenchFixes.java` - Optimization flags
- `src/main/java/com/voxelgame/world/stream/ChunkManager.java` - Streaming pipeline
- `src/main/java/com/voxelgame/world/mesh/NaiveMesher.java` - Mesh building
- `src/main/java/com/voxelgame/world/lod/LODConfig.java` - Performance tuning
- `src/main/java/com/voxelgame/render/Renderer.java` - Draw calls
- `src/main/java/com/voxelgame/core/GameLoop.java` - Frame budget

---

## Notes

- Cannot test changes at runtime (pure code analysis)
- Must reason carefully about thread safety
- Prioritize changes that can be validated by reading diffs
- Use existing benchmark infrastructure when possible
