#!/usr/bin/env python3
"""
Generate terrain.png for JavaGame - 90s high-contrast pixel art style.
Based on Machi's reference images: chunky cobblestone, vibrant grass, textured dirt.
"""
from PIL import Image, ImageDraw
import random

# Atlas config
TILE_SIZE = 16
TILES_PER_ROW = 8
TILE_COUNT = 64  # 8×8 grid
ATLAS_WIDTH = TILES_PER_ROW * TILE_SIZE
ATLAS_HEIGHT = ((TILE_COUNT + TILES_PER_ROW - 1) // TILES_PER_ROW) * TILE_SIZE

# Create blank atlas
atlas = Image.new('RGBA', (ATLAS_WIDTH, ATLAS_HEIGHT), (0, 0, 0, 0))

def set_pixel(x, y, color):
    """Set pixel with bounds checking"""
    if 0 <= x < ATLAS_WIDTH and 0 <= y < ATLAS_HEIGHT:
        atlas.putpixel((x, y), color)

def tile_origin(tile_idx):
    """Get top-left pixel coords for a tile index"""
    col = tile_idx % TILES_PER_ROW
    row = tile_idx // TILES_PER_ROW
    return col * TILE_SIZE, row * TILE_SIZE

def draw_tile(tile_idx, pixels):
    """Draw a 16×16 tile at the given index"""
    ox, oy = tile_origin(tile_idx)
    for y in range(TILE_SIZE):
        for x in range(TILE_SIZE):
            if y < len(pixels) and x < len(pixels[y]):
                color = pixels[y][x]
                set_pixel(ox + x, oy + y, color)

def noise(x, y, seed=0):
    """Simple deterministic noise"""
    n = x * 374761393 + y * 668265263 + seed * 1597463007
    n = (n ^ (n >> 13)) * 1274126177
    return (n ^ (n >> 16)) & 0xFF

def clamp(v, min_v=0, max_v=255):
    """Clamp value to range"""
    return max(min_v, min(max_v, v))

# ============================================================================
# TILE GENERATORS - Hand-crafted pixel art
# ============================================================================

def gen_air():
    """Tile 0: AIR - fully transparent"""
    return [[(0,0,0,0)] * TILE_SIZE for _ in range(TILE_SIZE)]

def gen_stone():
    """Tile 1: STONE - medium gray with dark cracks, high contrast"""
    pixels = []
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            n = noise(x, y, 1)
            # Base gray
            v = 120 + ((n % 40) - 20)
            # Add dark cracks (high contrast)
            if noise(x, y, 7) % 16 < 2:
                v -= 60  # Deep cracks
            elif noise(x, y, 13) % 12 < 2:
                v += 40  # Bright highlights
            v = clamp(v)
            row.append((v, v, v, 255))
        pixels.append(row)
    return pixels

def gen_cobblestone():
    """Tile 2: COBBLESTONE - THE REFERENCE TEXTURE
    Mostly gray stones with thin black cracks between them. White highlights on stones.
    """
    pixels = []
    # Define chunky stone shapes (hand-placed for good tiling) - BIGGER coverage
    stones = [
        # (x, y, radius, base_value)
        (3, 3, 3.5, 175),
        (11, 3, 3, 155),
        (7, 8, 4, 185),
        (2, 11, 2.5, 165),
        (13, 12, 3, 170),
        (7, 14, 2.5, 160),
        (14, 7, 2, 180),
    ]
    
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            # Default: medium gray (most of tile should be stone, not crack)
            v = 140
            in_stone = False
            
            # Check if pixel is inside a stone
            for sx, sy, sr, base in stones:
                dist = ((x - sx)**2 + (y - sy)**2) ** 0.5
                if dist < sr:
                    # Inside stone
                    in_stone = True
                    v = base
                    # Add texture variation
                    v += (noise(x, y, 5) % 25) - 12
                    # Add bright highlights on top-left
                    if x < sx and y < sy and dist < sr * 0.7:
                        v += 50
                    # Add shadows on bottom-right
                    elif x > sx and y > sy:
                        v -= 25
                    break
            
            # If NOT in a stone, it's a crack (but keep cracks THIN)
            if not in_stone:
                # Thin cracks between stones
                v = 5 + (noise(x, y, 2) % 15)
            
            # Add occasional pure black pixels for deep cracks
            if not in_stone and noise(x, y, 11) % 8 < 1:
                v = 0
            
            # Add bright white highlights on stone edges
            if in_stone and v > 160 and noise(x, y, 17) % 12 < 1:
                v = 255
            
            v = clamp(v)
            row.append((v, v, v, 255))
        pixels.append(row)
    return pixels

def gen_dirt():
    """Tile 3: DIRT - THE REFERENCE TEXTURE
    Warm browns with pebbles and natural texture. Tiles seamlessly.
    """
    pixels = []
    base_r, base_g, base_b = 139, 90, 60  # Warm brown
    
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            r, g, b = base_r, base_g, base_b
            
            # Natural color variation
            n = noise(x, y, 3)
            r += (n % 30) - 15
            g += ((n >> 2) % 25) - 12
            b += ((n >> 4) % 20) - 10
            
            # Add small pebbles (lighter spots)
            if noise(x, y, 19) % 25 < 2:
                r += 35
                g += 25
                b += 20
            
            # Add dark organic spots
            if noise(x, y, 23) % 30 < 1:
                r -= 40
                g -= 30
                b -= 25
            
            # Small lighter grains
            if noise(x, y, 29) % 15 < 1:
                r += 20
                g += 15
                b += 10
            
            row.append((clamp(r), clamp(g), clamp(b), 255))
        pixels.append(row)
    return pixels

def gen_grass_top():
    """Tile 4: GRASS TOP - THE REFERENCE TEXTURE
    Vibrant almost neon green with texture variation. High saturation.
    """
    pixels = []
    # Vibrant green (#7CBD6B but punched up)
    base_r, base_g, base_b = 105, 195, 95
    
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            r, g, b = base_r, base_g, base_b
            
            # Texture variation (keep it bright)
            n = noise(x, y, 4)
            r += (n % 30) - 10
            g += ((n >> 2) % 35) - 10
            b += ((n >> 4) % 25) - 10
            
            # Bright lime-green highlights (almost neon)
            if noise(x, y, 31) % 12 < 2:
                r += 30
                g += 45
                b += 25
            
            # Darker green accents (but still vibrant)
            if noise(x, y, 37) % 18 < 1:
                r -= 25
                g -= 35
                b -= 20
            
            # Tiny yellow-green blades
            if noise(x, y, 41) % 20 < 1:
                r += 40
                g += 30
                b -= 10
            
            row.append((clamp(r, 50, 255), clamp(g, 100, 255), clamp(b, 50, 255), 255))
        pixels.append(row)
    return pixels

def gen_grass_side():
    """Tile 5: GRASS SIDE - bright green strip on top, dirt below"""
    pixels = []
    grass_top = gen_grass_top()
    dirt = gen_dirt()
    
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            if y < 3:  # Top 3 pixels: grass
                row.append(grass_top[y][x])
            else:  # Rest: dirt
                row.append(dirt[y][x])
        pixels.append(row)
    return pixels

def gen_sand():
    """Tile 6: SAND - tan/beige with subtle grain"""
    pixels = []
    base_r, base_g, base_b = 210, 200, 145
    
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            n = noise(x, y, 6)
            r = base_r + (n % 20) - 10
            g = base_g + ((n >> 2) % 18) - 9
            b = base_b + ((n >> 4) % 22) - 11
            row.append((clamp(r), clamp(g), clamp(b), 255))
        pixels.append(row)
    return pixels

def gen_gravel():
    """Tile 7: GRAVEL - brownish gray with visible rocks"""
    pixels = []
    base = 125
    
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            n = noise(x, y, 7)
            v = base + (n % 35) - 17
            
            # Add darker rocks
            if noise(x, y, 43) % 15 < 2:
                v -= 45
            
            # Brownish tint
            r = v
            g = clamp(v - 8)
            b = clamp(v - 12)
            
            row.append((clamp(r), clamp(g), clamp(b), 255))
        pixels.append(row)
    return pixels

def gen_log_end():
    """Tile 8: LOG END - oak rings (concentric circles)"""
    pixels = []
    center_x, center_y = 7.5, 7.5
    
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            dist = ((x - center_x)**2 + (y - center_y)**2) ** 0.5
            ring = int(dist * 1.8) % 2
            
            if ring == 0:
                base = 160
            else:
                base = 120
            
            n = noise(x, y, 8)
            r = base + (n % 20) - 10
            g = clamp(int(r * 0.7))
            b = clamp(int(r * 0.42))
            
            row.append((clamp(r), clamp(g), clamp(b), 255))
        pixels.append(row)
    return pixels

def gen_log_bark():
    """Tile 9: LOG BARK - vertical oak bark texture"""
    pixels = []
    base_r, base_g, base_b = 145, 115, 70
    
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            r, g, b = base_r, base_g, base_b
            
            n = noise(x, y, 9)
            r += (n % 25) - 12
            g += ((n >> 2) % 20) - 10
            b += ((n >> 4) % 18) - 9
            
            # Vertical bark stripes
            if x % 4 == 0:
                r -= 20
                g -= 15
                b -= 10
            
            row.append((clamp(r), clamp(g), clamp(b), 255))
        pixels.append(row)
    return pixels

def gen_leaves():
    """Tile 10: LEAVES - forest green, semi-transparent organic"""
    pixels = []
    base_r, base_g, base_b = 80, 140, 70
    
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            r, g, b = base_r, base_g, base_b
            
            n = noise(x, y, 10)
            r += (n % 35) - 17
            g += ((n >> 2) % 40) - 20
            b += ((n >> 4) % 30) - 15
            
            # Holes in leaves (transparent)
            if noise(x, y, 47) % 22 < 1:
                row.append((clamp(r, 40, 120), clamp(g, 80, 180), clamp(b, 40, 120), 160))
            else:
                row.append((clamp(r, 50, 140), clamp(g, 90, 200), clamp(b, 50, 130), 220))
        pixels.append(row)
    return pixels

def gen_water():
    """Tile 11: WATER - classic blue, semi-transparent"""
    pixels = []
    base_r, base_g, base_b = 50, 105, 220
    
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            n = noise(x, y, 11)
            r = base_r + (n % 18) - 9
            g = base_g + ((n >> 2) % 22) - 11
            b = base_b + ((n >> 4) % 25) - 12
            row.append((clamp(r), clamp(g), clamp(b), 140))
        pixels.append(row)
    return pixels

def gen_ore(tile_idx, ore_r, ore_g, ore_b):
    """Generate ore texture: stone base + colored ore veins"""
    pixels = []
    stone = gen_stone()
    
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            # Check if this pixel is an ore vein
            if noise(x * 3 + 7, y * 5 + 11, tile_idx) % 16 < 3:
                # Ore vein pixel
                n = noise(x, y, tile_idx + 50)
                r = ore_r + (n % 20) - 10
                g = ore_g + ((n >> 2) % 20) - 10
                b = ore_b + ((n >> 4) % 20) - 10
                row.append((clamp(r), clamp(g), clamp(b), 255))
            else:
                # Stone base
                row.append(stone[y][x])
        pixels.append(row)
    return pixels

def gen_bedrock():
    """Tile 16: BEDROCK - very dark gray, almost black"""
    pixels = []
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            n = noise(x, y, 16)
            v = 25 + (n % 30)
            row.append((clamp(v), clamp(v), clamp(v), 255))
        pixels.append(row)
    return pixels

def gen_planks():
    """Tile 19: PLANKS - oak wood planks with grain"""
    pixels = []
    base_r, base_g, base_b = 175, 140, 90
    
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            r, g, b = base_r, base_g, base_b
            
            n = noise(x, y, 19)
            r += (n % 20) - 10
            g += ((n >> 2) % 18) - 9
            b += ((n >> 4) % 15) - 7
            
            # Horizontal plank separation
            if y % 4 == 0:
                r -= 30
                g -= 25
                b -= 20
            
            # Wood grain
            if (x + y // 2) % 3 == 0:
                r -= 10
                g -= 8
                b -= 6
            
            row.append((clamp(r), clamp(g), clamp(b), 255))
        pixels.append(row)
    return pixels

def gen_solid_item(base_r, base_g, base_b, shape_mask=None):
    """Generate a solid-color item icon with optional shape mask"""
    pixels = []
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            # Default shape: rough oval
            if shape_mask is None:
                cx, cy = x - 7.5, y - 7.5
                if cx*cx/25 + cy*cy/20 < 1:
                    n = noise(x, y, 99)
                    r = base_r + (n % 15) - 7
                    g = base_g + ((n >> 2) % 15) - 7
                    b = base_b + ((n >> 4) % 15) - 7
                    row.append((clamp(r), clamp(g), clamp(b), 255))
                else:
                    row.append((0, 0, 0, 0))
            else:
                if shape_mask(x, y):
                    n = noise(x, y, 99)
                    r = base_r + (n % 15) - 7
                    g = base_g + ((n >> 2) % 15) - 7
                    b = base_b + ((n >> 4) % 15) - 7
                    row.append((clamp(r), clamp(g), clamp(b), 255))
                else:
                    row.append((0, 0, 0, 0))
        pixels.append(row)
    return pixels

def gen_glass():
    """Tile 33: GLASS - transparent with subtle frame"""
    pixels = []
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            n = noise(x, y, 33)
            # Border/frame (more opaque)
            if x == 0 or x == 15 or y == 0 or y == 15:
                r = 170 + (n % 15)
                g = 190 + ((n >> 2) % 15)
                b = 200 + ((n >> 4) % 15)
                row.append((clamp(r), clamp(g), clamp(b), 220))
            # Interior (very transparent)
            else:
                r = 200 + (n % 10)
                g = 220 + ((n >> 2) % 10)
                b = 230 + ((n >> 4) % 10)
                row.append((clamp(r), clamp(g), clamp(b), 60))
        pixels.append(row)
    return pixels

def gen_lava():
    """Tile 46: LAVA - glowing orange-red with dark veins"""
    pixels = []
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            n1 = noise(x * 2, y * 3, 461)
            n2 = noise(x * 5 + 7, y * 4 + 3, 462)
            
            # Base: glowing orange
            r = 210 + (n1 % 30)
            g = 90 + ((n1 >> 2) % 40)
            b = 15 + ((n1 >> 4) % 20)
            
            # Dark veins
            if n2 % 20 < 2:
                r -= 80
                g -= 40
                b -= 5
            
            # Bright yellow hot spots
            if n2 % 25 < 1:
                r = 255
                g = 230
                b = 60
            
            row.append((clamp(r), clamp(g), clamp(b), 255))
        pixels.append(row)
    return pixels

def gen_obsidian():
    """Tile 47: OBSIDIAN - dark purple-black"""
    pixels = []
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            n = noise(x, y, 47)
            
            # Very dark purple-black base
            r = 18 + (n % 15)
            g = 10 + ((n >> 2) % 10)
            b = 28 + ((n >> 4) % 18)
            
            # Subtle purple highlights
            if n % 20 < 1:
                r += 25
                b += 30
            
            row.append((clamp(r), clamp(g), clamp(b), 255))
        pixels.append(row)
    return pixels

def gen_farmland():
    """Tile 48: FARMLAND - tilled dirt with furrows"""
    pixels = []
    dirt = gen_dirt()
    
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            r, g, b, a = dirt[y][x]
            
            # Darker than regular dirt
            r = clamp(r - 25)
            g = clamp(g - 20)
            b = clamp(b - 15)
            
            # Furrow lines (horizontal)
            if y % 4 < 2:
                r = clamp(r - 20)
                g = clamp(g - 15)
                b = clamp(b - 10)
            
            row.append((r, g, b, a))
        pixels.append(row)
    return pixels

def gen_wheat_stage(stage):
    """Tiles 49-56: WHEAT crop stages 0-7 (green → golden)"""
    pixels = []
    
    # Stage progression: height and color
    height = [4, 6, 8, 10, 11, 13, 14, 16][stage]
    
    if stage <= 2:
        stalk_r, stalk_g, stalk_b = 55, 150, 35  # Young green
    elif stage <= 4:
        stalk_r, stalk_g, stalk_b = 65, 135, 30  # Growing green
    elif stage == 5:
        stalk_r, stalk_g, stalk_b = 110, 145, 35  # Green-yellow
    elif stage == 6:
        stalk_r, stalk_g, stalk_b = 140, 140, 30  # Yellow-green
    else:
        stalk_r, stalk_g, stalk_b = 180, 155, 45  # Golden
    
    # Grain head color (stages 5-7)
    has_head = stage >= 5
    if stage == 5:
        head_r, head_g, head_b = 150, 130, 35
    elif stage == 6:
        head_r, head_g, head_b = 190, 160, 45
    else:
        head_r, head_g, head_b = 210, 180, 55
    
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            pixels_from_bottom = TILE_SIZE - 1 - y
            
            if pixels_from_bottom >= height:
                # Above crop - transparent
                row.append((0, 0, 0, 0))
            else:
                # Check if pixel is part of wheat stalk/leaves/head
                is_stalk = x in [3, 7, 11, 14]
                is_leaf = x in [2, 4, 6, 8, 10, 12, 13, 15] and \
                          1 < pixels_from_bottom < height - 1 and \
                          (pixels_from_bottom + x) % 3 == 0
                is_head = has_head and pixels_from_bottom >= height - 3 and \
                         (is_stalk or (2 <= x <= 14 and (pixels_from_bottom + x) % 2 == 0))
                
                if is_head:
                    n = noise(x, y, stage + 490)
                    r = head_r + (n % 15) - 7
                    g = head_g + ((n >> 2) % 15) - 7
                    b = head_b + ((n >> 4) % 15) - 7
                    row.append((clamp(r), clamp(g), clamp(b), 255))
                elif is_stalk:
                    n = noise(x, y, stage + 490)
                    r = stalk_r + (n % 12) - 6
                    g = stalk_g + ((n >> 2) % 12) - 6
                    b = stalk_b + ((n >> 4) % 12) - 6
                    row.append((clamp(r), clamp(g), clamp(b), 255))
                elif is_leaf:
                    n = noise(x, y, stage + 491)
                    r = clamp(stalk_r - 10 + (n % 10) - 5)
                    g = clamp(stalk_g + 10 + ((n >> 2) % 10) - 5)
                    b = clamp(stalk_b - 5 + ((n >> 4) % 10) - 5)
                    row.append((r, g, b, 200))
                else:
                    row.append((0, 0, 0, 0))
        pixels.append(row)
    return pixels

def gen_hoe_icon():
    """Tile 57: HOE - wooden handle + stone blade"""
    pixels = []
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            # Handle: diagonal line from bottom-left to center
            hx = x - 2
            hy = (TILE_SIZE - 1 - y) - 2
            is_handle = 0 <= hx <= 8 and 0 <= hy <= 8 and abs(hx - hy) <= 1
            
            # Blade: horizontal piece at top-right
            is_blade = 8 <= x <= 14 and 2 <= y <= 5
            
            if is_blade:
                # Stone blade
                n = noise(x, y, 57)
                v = 130 + (n % 20) - 10
                row.append((clamp(v), clamp(v), clamp(v), 255))
            elif is_handle:
                # Wood handle
                n = noise(x, y, 571)
                r = 115 + (n % 15) - 7
                g = 80 + ((n >> 2) % 15) - 7
                b = 45 + ((n >> 4) % 15) - 7
                row.append((clamp(r), clamp(g), clamp(b), 255))
            else:
                row.append((0, 0, 0, 0))
        pixels.append(row)
    return pixels

def gen_seeds_icon():
    """Tile 58: SEEDS - scattered green seed dots"""
    pixels = []
    # Hand-placed seed positions for nice look
    seed_positions = [(4,6), (7,4), (10,7), (5,10), (8,9), (12,5), (6,13), (11,11), (3,8), (9,12)]
    
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            is_seed = any(abs(x - sx) <= 1 and abs(y - sy) == 0 for sx, sy in seed_positions)
            
            if is_seed:
                n = noise(x, y, 58)
                r = 90 + (n % 15) - 7
                g = 120 + ((n >> 2) % 15) - 7
                b = 35 + ((n >> 4) % 15) - 7
                row.append((clamp(r), clamp(g), clamp(b), 255))
            else:
                row.append((0, 0, 0, 0))
        pixels.append(row)
    return pixels

def gen_wheat_item():
    """Tile 59: WHEAT ITEM - golden wheat sheaf"""
    pixels = []
    for y in range(TILE_SIZE):
        row = []
        for x in range(TILE_SIZE):
            # Three stalks
            stalk1 = x == 6 and 5 <= y <= 14
            stalk2 = x == 8 and 4 <= y <= 14
            stalk3 = x == 10 and 5 <= y <= 14
            
            # Grain heads at top
            head1 = 5 <= x <= 7 and 2 <= y <= 5
            head2 = 7 <= x <= 9 and 1 <= y <= 4
            head3 = 9 <= x <= 11 and 2 <= y <= 5
            
            if head1 or head2 or head3:
                n = noise(x, y, 59)
                r = 210 + (n % 15) - 7
                g = 175 + ((n >> 2) % 15) - 7
                b = 50 + ((n >> 4) % 15) - 7
                row.append((clamp(r), clamp(g), clamp(b), 255))
            elif stalk1 or stalk2 or stalk3:
                n = noise(x, y, 591)
                r = 170 + (n % 15) - 7
                g = 145 + ((n >> 2) % 15) - 7
                b = 40 + ((n >> 4) % 15) - 7
                row.append((clamp(r), clamp(g), clamp(b), 255))
            else:
                row.append((0, 0, 0, 0))
        pixels.append(row)
    return pixels

# ============================================================================
# GENERATE ALL TILES
# ============================================================================

print("Generating terrain.png...")
print(f"Atlas size: {ATLAS_WIDTH}×{ATLAS_HEIGHT}")

# Core blocks
draw_tile(0, gen_air())
draw_tile(1, gen_stone())
draw_tile(2, gen_cobblestone())
draw_tile(3, gen_dirt())
draw_tile(4, gen_grass_top())
draw_tile(5, gen_grass_side())
draw_tile(6, gen_sand())
draw_tile(7, gen_gravel())
draw_tile(8, gen_log_end())
draw_tile(9, gen_log_bark())
draw_tile(10, gen_leaves())
draw_tile(11, gen_water())

# Ores
draw_tile(12, gen_ore(12, 40, 40, 40))     # coal
draw_tile(13, gen_ore(13, 170, 130, 90))   # iron
draw_tile(14, gen_ore(14, 245, 205, 0))    # gold
draw_tile(15, gen_ore(15, 90, 210, 245))   # diamond

draw_tile(16, gen_bedrock())

# Items (sparse - leave gaps)
draw_tile(17, gen_solid_item(230, 130, 120))  # raw porkchop
draw_tile(18, gen_solid_item(130, 95, 60))    # rotten flesh

# Advanced blocks
draw_tile(19, gen_planks())
# 20-21: crafting table (TODO)
# 22-23: chest (TODO)
# 24: rail (TODO)
# 25-26: TNT (TODO)
# 27-29: furnace (TODO)
# 30: torch (TODO)

draw_tile(31, gen_solid_item(30, 30, 30))     # coal item
draw_tile(32, gen_solid_item(190, 185, 180))  # iron ingot
draw_tile(33, gen_glass())
draw_tile(34, gen_solid_item(190, 120, 75))   # cooked porkchop
# 35-36: flowers (TODO)
draw_tile(37, gen_solid_item(110, 220, 245))  # diamond
draw_tile(38, gen_solid_item(50, 35, 20))     # charcoal
draw_tile(39, gen_solid_item(245, 190, 40))   # gold ingot
# 40-45: redstone stuff (TODO)
draw_tile(46, gen_lava())
draw_tile(47, gen_obsidian())

# Farming
draw_tile(48, gen_farmland())
for i in range(8):
    draw_tile(49 + i, gen_wheat_stage(i))
draw_tile(57, gen_hoe_icon())
draw_tile(58, gen_seeds_icon())
draw_tile(59, gen_wheat_item())

# Save
output_path = 'src/main/resources/textures/terrain.png'
atlas.save(output_path)
print(f"✅ Saved to {output_path}")
print(f"✅ Generated {TILE_COUNT} tile slots (populated: ~40)")
