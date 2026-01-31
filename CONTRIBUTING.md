# Contributing to VoxelGame

## Getting Started

1. Fork the repo
2. Clone your fork
3. Make sure Java 21 is installed
4. Run `./gradlew build` to verify setup

## Development

- Follow existing code style and package conventions
- Add javadoc to all public classes and methods
- Keep commits focused — one logical change per commit
- Test builds compile before pushing: `./gradlew build`

## WorldGen Changes

When modifying world generation:
1. Create a new preset JSON rather than modifying existing production presets
2. Use the WorldGen Lab (F5) to test changes visually
3. Document parameter changes in the preset file's description field

## Pull Requests

- Describe what changed and why
- Reference any related issues
- Keep PRs small and reviewable
