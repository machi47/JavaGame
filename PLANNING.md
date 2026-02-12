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
- **Gen Pool**: 4 threads for chunk generation
- **Mesh Pool**: 3 threads for CPU-side mesh building

### Per-Frame Budgets
| Operation | Current Limit | Notes |
|-----------|---------------|-------|
| Close chunk gens | 4 | LOD 0-1 chunks |
| Far chunk gens | 6 | LOD 2-3 chunks |
| Full mesh uploads | 12 | Main thread GL ops |
| LOD mesh uploads | 16 | Separate budget |

---

## Existing Optimizations (DISABLED BY DEFAULT)

From `BenchFixes.java` - all are `false`:

| Flag | Purpose | Impact |
|------|---------|--------|
| `FIX_MESH_PRIMITIVE_BUFFERS` | Use `float[]`/`int[]` instead of ArrayList | Reduces GC pressure |
| `FIX_B2_PRIMITIVE_MAP` | fastutil `Long2ObjectOpenHashMap` | No boxing in chunk lookups |
| `FIX_B3_SNAPSHOT_MESH` | Snapshot-based neighbor resolution | Zero map lookups in meshing hot path |
| `FIX_B31_SNAPSHOT_OFFTHREAD` | Create snapshots on worker threads | Removes main thread latency |
| `FIX_ASYNC_REGION_IO` | Async disk writes | Non-blocking saves |
| `FIX_ASYNC_REGION_IO_V2` | Bounded backlog + coalescing | Prevents IO queue explosion |

**PRIORITY 1: Enable all existing optimizations**

---

## Optimization Phases

### Phase 1: Enable Existing Optimizations
- [ ] Enable all BenchFixes flags by default
- [ ] Verify each optimization works correctly
- [ ] Benchmark before/after

### Phase 2: Increase Throughput Limits
- [ ] Increase `MAX_MESH_UPLOADS_PER_FRAME` from 12 to 24
- [ ] Increase `MAX_LOD_UPLOADS_PER_FRAME` from 16 to 32
- [ ] Increase thread pools if CPU allows (8 gen, 6 mesh)
- [ ] Profile GPU upload bandwidth vs frame budget

### Phase 3: Implement Greedy Meshing
- [ ] Replace NaiveMesher with proper greedy meshing algorithm
- [ ] Expected: 30-50% reduction in vertex count
- [ ] Must preserve AO and lighting quality

### Phase 4: Memory & Allocation Optimizations
- [ ] Object pooling for ChunkPos, MeshResult
- [ ] Pre-allocated vertex buffers per thread
- [ ] Reduce HeightfieldVisibility per-vertex cost

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

**Next session priority**: Enable BenchFixes, benchmark before/after

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
