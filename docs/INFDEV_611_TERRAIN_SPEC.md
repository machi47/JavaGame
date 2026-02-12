# Infdev 611 Terrain Generation Specification

## Reference Document for Engine Optimization

This document captures the authentic Infdev 611 terrain generation algorithm for reference during optimization work.

---

## 1) Generation Model Topology

InfDev/Indev 611 terrain is produced through a **single-pass density field evaluation** over a finite or semi-finite chunk lattice.

There is no modern biome stack, no climate simulation, no erosion passes, and no multi-stage blending. Terrain is determined by evaluating a scalar density function:

```
D(x,y,z) = f_base(x,z) + f_detail(x,z) + f_3D(x,y,z) - y_bias(y)
```

Block material is resolved by thresholding this density.

**Key properties**

| Property    | Behavior                                                  |
| ----------- | --------------------------------------------------------- |
| Determinism | Fully seed deterministic                                  |
| Locality    | Density computed per column with optional 3D perturbation |
| Streaming   | Chunk-local, no global simulation                         |
| Biomes      | None                                                      |
| Erosion     | None                                                      |
| Rivers      | None                                                      |
| Structures  | Post-terrain only                                         |

---

## 2) Spatial Lattice

### Chunk Resolution

Indev/InfDev terrain operates on chunk grids:

* Horizontal: 16 × 16
* Vertical: 128 (Indev) or variable (InfDev variants)

Each chunk column evaluates density at each voxel.

---

## 3) Noise Stack Composition

Terrain is governed by **stacked coherent noise fields** with distinct spatial frequencies.

### 3.1 Base Height Noise

Primary continental shape:

```
H_base(x,z) = N_octave(x * s_1, z * s_1)
```

Where:
* Noise type: Perlin / Simplex ancestor
* Octaves: ~4–6
* Persistence: ~0.5
* Lacunarity: ~2.0
* Scale (s_1): Low frequency (large landmasses)

Effect:
* Establishes macro hills and basins
* No plateaus or biome plate separation

### 3.2 Secondary Detail Noise

Adds sharper relief:

```
H_detail(x,z) = N_octave(x * s_2, z * s_2)
```

Where:
* Higher frequency than base
* Lower amplitude
* Introduces ruggedness and chaotic slopes

### 3.3 Vertical Perturbation Field

This is the defining InfDev/Indev cave and overhang driver.

```
P_3D(x,y,z) = N_3D(x * s_3, y * s_3, z * s_3)
```

Purpose:
* Carves voids
* Generates floating terrain
* Produces volumetric caverns
* Breaks strict heightmap monotonicity

---

## 4) Full Density Equation

```
D(x,y,z) = H_base(x,z) + H_detail(x,z) + A * P_3D(x,y,z) - B * y
```

Where:

| Term | Function                    |
| ---- | --------------------------- |
| A    | Cave amplitude              |
| B    | Vertical attenuation factor |

Interpretation:
* Density decreases with altitude
* 3D noise locally increases or decreases density
* Positive density → solid
* Negative density → air

---

## 5) Floating Island Formation

Floating islands occur when:

```
D(x,y,z) > 0 at high y
```

This requires:
* Strong positive 3D perturbation
* Weak vertical attenuation locally

Thus terrain can exist detached from ground mass. No structural anchoring is required.

---

## 6) Cave Topology

Caves are **emergent voids** formed by 3D density oscillation, not carved tunnels.

| Property       | InfDev Caves           |
| -------------- | ---------------------- |
| Shape          | Blobby, cellular       |
| Connectivity   | Often massive chambers |
| Directionality | None                   |
| Tubularity     | Rare                   |

---

## 7) Surface Material Resolution

Once density resolves solid vs air:

1. Find surface: `y_surface = max{ y : D(x,y,z) > 0 }`
2. Apply material layers:

| Depth      | Block |
| ---------- | ----- |
| Surface    | Grass |
| 1–3 below  | Dirt  |
| Below dirt | Stone |

---

## 8) Water Table

Global constant water level applied passively:
* If terrain height < water level: Air → Water
* No shoreline smoothing

---

## 9) Visual Signature Traits

| Feature           | Cause                     |
| ----------------- | ------------------------- |
| Sheer cliffs      | High octave interference  |
| Floating slabs    | Positive 3D density lobes |
| Giant caverns     | 3D noise void pockets     |
| Jagged coastlines | No erosion smoothing      |
| Chaotic hills     | Non-biome noise stacking  |

---

## 10) Optimization Opportunities

### Noise Evaluation
- Pre-compute 2D base/detail noise per chunk (shared by all Y levels)
- Cache height columns for visibility calculations
- Use SIMD for noise evaluation if possible

### Density Sampling
- Current: 4×8×4 noise cells interpolated to block resolution
- This is already optimized (trilinear interpolation)

### Material Resolution
- Surface detection is O(height) per column
- Could cache surface height during generation

---

## 11) Comparison: Our Implementation vs Spec

Our `Infdev611TerrainPass.java` implementation:
- ✓ Uses correct noise stack (min/max/main limit noise)
- ✓ Uses 4×8×4 interpolation cells
- ✓ Applies vertical attenuation
- ✓ No depth/=2 dampening (authentic extreme terrain)
- ✓ Configurable via GenConfig

The implementation is accurate to the spec.
