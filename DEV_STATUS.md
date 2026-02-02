# dev-integrated Branch Status

**Created:** 2026-02-02  
**Base:** combat-impl (all sub-agent work integrated)  
**Status:** ✅ Compiles, ✅ Launches, ⚠️ Runtime testing needed

## Features in this branch

### ✅ Confirmed Working
- Core game (from main e140e77) - compiles, launches to main menu

### ⚠️ Untested - Needs Verification
- **Building blocks (28 types)** - IDs 70-97
  - Doors, stairs, slabs, fences, ladders, beds, buttons, pressure plates, etc.
  - Code exists, textures assigned, needs gameplay test
  
- **Armor system (16 pieces + LEATHER)** - IDs 71-86, 124
  - Leather/Iron/Diamond/Gold helmets, chestplates, leggings, boots
  - ArmorItem.java exists, inventory serialization added
  - Needs test: equipping, damage reduction, durability

- **Combat: Bow & Arrows** - BOW, ARROW_ITEM blocks + ArrowEntity
  - Bow charging mechanics, arrow physics
  - Player methods: isBowDrawn(), hasArrows(), consumeArrow()
  - Needs test: shooting, damage, arrow pickup

- **Farming blocks** - IDs 114-123
  - Farmland, sugar cane, wheat crops (8 growth stages)
  - Block definitions exist
  - Needs test: placement, growth, harvesting

- **Passive mobs** - Sheep, Cow, Chicken (partial files from crashed sub-agent)
  - Files exist but may be incomplete
  - Needs audit and testing

### ❌ Known Issues
- **Mob expansion crashed** - Spider, Skeleton, Creeper, Slime, Squid files incomplete/missing
- **Fire system catastrophic failure** - No fire mechanics implemented
- **Entity types** - May be missing types for new mobs

## Block ID Map (combat-impl)
- 0-69: Base blocks (from main)
- 70-97: Building blocks (Alpha)
- 98-113: Armor pieces
- 114-123: Farming blocks  
- 124: LEATHER item

## Next Steps
1. Test basic gameplay (walk, jump, break/place blocks)
2. Test building blocks placement/rendering
3. Test armor equipping + damage reduction
4. Test bow/arrow mechanics
5. Test farming block placement
6. Audit mob files - complete or scrap them
7. Document what actually works
8. When feature verified → cherry-pick to main

## Branch Strategy
- `main` = stable, proven features only
- `dev-integrated` = active development, integration testing
- Feature branches = isolated new work
