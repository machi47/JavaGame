package com.voxelgame.ui;

import com.voxelgame.core.Profiler;
import com.voxelgame.render.Renderer;
import com.voxelgame.render.VisibilityGraph;
import com.voxelgame.sim.Inventory;
import com.voxelgame.sim.Player;
import com.voxelgame.world.World;
import com.voxelgame.world.WorldConstants;
import com.voxelgame.world.stream.ChunkManager;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Debug information overlay (F3 screen).
 * Shows FPS, position, chunk info, fly mode, facing direction, and LOD stats.
 * Uses the BitmapFont renderer for on-screen text.
 *
 * Display modes (cycle with F3):
 *   OFF → HELP → BASIC → BASIC+CHUNKS → BASIC+CHUNKS+PROFILER → OFF
 */
public class DebugOverlay {

    /** Display mode enum */
    public enum DisplayMode {
        OFF,
        HELP,           // F-key reference
        BASIC,
        BASIC_CHUNKS,
        BASIC_CHUNKS_PROFILER
    }

    private DisplayMode mode = DisplayMode.OFF;
    private final BitmapFont font;

    /** Reference to renderer for visibility stats. */
    private Renderer renderer;

    // Base font scale (for 1080p reference resolution) - 1.7x larger
    private static final float BASE_FONT_SCALE = 1.7f;
    private static final float BASE_LINE_HEIGHT = 14.0f; // Scaled up from 10
    private static final float MARGIN_X = 6.0f;
    private static final float MARGIN_Y = 6.0f;
    private static final float PANEL_PADDING = 4.0f;

    // Computed scale values (updated each frame based on window size)
    private float uiScale = 1.0f;
    private float fontScale = BASE_FONT_SCALE;
    private float lineHeight = BASE_LINE_HEIGHT;

    // Max lines to prevent overflow
    private static final int MAX_LINES = 20;

    // Panel background color (translucent dark)
    private static final float BG_R = 0.0f;
    private static final float BG_G = 0.0f;
    private static final float BG_B = 0.0f;
    private static final float BG_A = 0.6f;

    // Shadow color for text readability
    private static final float SHADOW_R = 0.0f;
    private static final float SHADOW_G = 0.0f;
    private static final float SHADOW_B = 0.0f;
    private static final float SHADOW_A = 0.8f;

    // Text color (white)
    private static final float TEXT_R = 1.0f;
    private static final float TEXT_G = 1.0f;
    private static final float TEXT_B = 1.0f;
    private static final float TEXT_A = 1.0f;

    // Section header color (yellow-ish)
    private static final float HEADER_R = 1.0f;
    private static final float HEADER_G = 0.9f;
    private static final float HEADER_B = 0.4f;

    public DebugOverlay(BitmapFont font) {
        this.font = font;
    }

    /** Set renderer reference for visibility stats. */
    public void setRenderer(Renderer renderer) {
        this.renderer = renderer;
    }

    /**
     * Cycle through display modes: OFF → HELP → BASIC → BASIC+CHUNKS → BASIC+CHUNKS+PROFILER → OFF
     */
    public void toggle() {
        mode = switch (mode) {
            case OFF -> DisplayMode.HELP;
            case HELP -> DisplayMode.BASIC;
            case BASIC -> DisplayMode.BASIC_CHUNKS;
            case BASIC_CHUNKS -> DisplayMode.BASIC_CHUNKS_PROFILER;
            case BASIC_CHUNKS_PROFILER -> DisplayMode.OFF;
        };
    }

    /**
     * Get current display mode.
     */
    public DisplayMode getMode() {
        return mode;
    }

    /**
     * Check if overlay is visible (any mode except OFF).
     */
    public boolean isVisible() {
        return mode != DisplayMode.OFF;
    }

    /**
     * Calculate UI scale based on window height.
     * Reference: 1080p = scale 1.0, smaller windows get smaller text.
     */
    private void updateScale(int screenH) {
        // Scale range: 0.6 to 1.0, based on 1080p reference
        uiScale = Math.max(0.6f, Math.min(1.0f, screenH / 1080.0f));
        fontScale = BASE_FONT_SCALE * uiScale;
        lineHeight = BASE_LINE_HEIGHT * uiScale;
    }

    /** Backward-compatible overload (no entity counts or time). */
    public void render(Player player, World world, int fps, int screenW, int screenH, boolean sprinting) {
        render(player, world, fps, screenW, screenH, sprinting, 0, 0, "", null, 0, 0, true);
    }

    /** Backward-compatible overload (no LOD stats). */
    public void render(Player player, World world, int fps, int screenW, int screenH,
                       boolean sprinting, int itemEntityCount, int mobCount, String worldTimeStr) {
        render(player, world, fps, screenW, screenH, sprinting, itemEntityCount, mobCount, worldTimeStr, null, 0, 0, true);
    }

    /** Backward-compatible overload (no smooth lighting info). */
    public void render(Player player, World world, int fps, int screenW, int screenH,
                       boolean sprinting, int itemEntityCount, int mobCount, String worldTimeStr,
                       ChunkManager chunkManager, int renderedChunks, int culledChunks) {
        render(player, world, fps, screenW, screenH, sprinting, itemEntityCount, mobCount, worldTimeStr,
               chunkManager, renderedChunks, culledChunks, true);
    }

    /**
     * Render debug text overlay with LOD stats and smooth lighting info.
     * Phase 6: Added smoothLighting parameter.
     */
    public void render(Player player, World world, int fps, int screenW, int screenH,
                       boolean sprinting, int itemEntityCount, int mobCount, String worldTimeStr,
                       ChunkManager chunkManager, int renderedChunks, int culledChunks,
                       boolean smoothLighting) {
        if (mode == DisplayMode.OFF) return;

        // Update scale based on window size
        updateScale(screenH);

        // Build lines based on display mode
        List<String> lines = new ArrayList<>();

        // === HELP MODE: Show F-key reference only ===
        if (mode == DisplayMode.HELP) {
            addControlsHelp(lines);
            lines.add("");
            lines.add("Press F3 again for debug info");
            renderLines(lines, screenW, screenH);
            return;
        }

        Vector3f pos = player.getPosition();
        float yaw = player.getCamera().getYaw();
        float pitch = player.getCamera().getPitch();

        int cx = Math.floorDiv((int) Math.floor(pos.x), WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv((int) Math.floor(pos.z), WorldConstants.CHUNK_SIZE);
        int loadedChunks = world.getChunkMap().size();

        String facing = getFacingDirection(yaw);
        float feetY = pos.y - Player.EYE_HEIGHT;

        // === BASIC INFO (always shown when visible) ===
        lines.add(String.format("FPS: %d", fps));
        lines.add(String.format("XYZ: %.1f / %.1f / %.1f", pos.x, pos.y, pos.z));
        lines.add(String.format("Chunk: %d, %d  Facing: %s", cx, cz, facing));
        lines.add(String.format("Fly: %s  Ground: %s  Sprint: %s",
            player.isFlyMode() ? "ON" : "OFF",
            player.isOnGround() ? "Y" : "N",
            sprinting ? "Y" : "N"));
        lines.add(String.format("Mode: %s  HP: %.0f/%.0f",
            player.getGameMode(), player.getHealth(), player.getMaxHealth()));

        // Phase 6: Show lighting mode
        lines.add(String.format("Lighting: %s",
            smoothLighting ? "Smooth" : "Sharp"));

        // === CHUNK INFO (BASIC_CHUNKS and BASIC_CHUNKS_PROFILER) ===
        if (mode == DisplayMode.BASIC_CHUNKS || mode == DisplayMode.BASIC_CHUNKS_PROFILER) {
            lines.add(""); // Spacer
            if (chunkManager != null) {
                lines.add(String.format("Chunks: %d loaded  %d rendered  %d culled",
                    loadedChunks, renderedChunks, culledChunks));
                lines.add(String.format("LOD: 0=%d 1=%d 2=%d 3=%d",
                    chunkManager.getLod0Count(), chunkManager.getLod1Count(),
                    chunkManager.getLod2Count(), chunkManager.getLod3Count()));
                lines.add(String.format("Pending: %d uploads", chunkManager.getPendingUploads()));
            } else {
                lines.add(String.format("Chunks: %d loaded", loadedChunks));
            }
            lines.add(String.format("Entities: %d items  %d mobs", itemEntityCount, mobCount));
        }

        // === PROFILER + VISIBILITY (BASIC_CHUNKS_PROFILER only) ===
        if (mode == DisplayMode.BASIC_CHUNKS_PROFILER) {
            // Visibility culling stats
            if (renderer != null) {
                lines.add(""); // Spacer
                VisibilityGraph vg = renderer.getVisibilityGraph();
                boolean visCulling = renderer.isVisibilityCullingEnabled();
                lines.add(String.format("[Visibility] %s", visCulling ? "ON" : "OFF"));
                if (vg != null) {
                    lines.add(String.format("  Connected: %d subchunks  Candidates: %d chunks",
                        vg.getLastConnectedCount(), vg.getLastCandidateChunks()));
                    lines.add(String.format("  Connectivity culled: %d",
                        renderer.getConnectivityCulled()));
                }
            }

            Profiler profiler = Profiler.getInstance();
            List<String> sections = profiler.getSections();

            if (!sections.isEmpty()) {
                lines.add(""); // Spacer

                // Compact single-line summary of key metrics
                double frameMs = profiler.getAverageMs("Frame");
                double renderMs = profiler.getAverageMs("Render");
                double uiMs = profiler.getAverageMs("UI");
                double worldMs = profiler.getAverageMs("World");
                double chunkMs = profiler.getAverageMs("ChunkMesh");

                lines.add(String.format("[Perf] frame=%.1f render=%.1f ui=%.1f chunk=%.1f world=%.1fms",
                    frameMs, renderMs, uiMs, chunkMs, worldMs));

                // Additional profiler details (top 4 most expensive)
                List<String> timings = profiler.getTimings(4);
                for (String timing : timings) {
                    lines.add("  " + timing);
                }
            }
        }

        // Add controls at the bottom of every mode
        lines.add(""); // Spacer
        addControlsHelp(lines);

        renderLines(lines, screenW, screenH);
    }

    /**
     * Add F-key controls help to lines list.
     */
    private void addControlsHelp(List<String> lines) {
        lines.add("=== DEBUG KEYS ===");
        lines.add("F2=Screenshot  F3=Debug  F4=GameMode  F5=Difficulty");
        lines.add("F6=Lighting  F7=DebugView  F9=Gamma  F10=Fog  F12=Wireframe");
    }

    /**
     * Render a list of lines with background panel.
     */
    private void renderLines(List<String> lines, int screenW, int screenH) {
        // Clamp to max lines
        if (lines.size() > MAX_LINES) {
            lines = lines.subList(0, MAX_LINES);
        }

        // Calculate panel dimensions
        float charWidth = 8.0f * fontScale;
        int maxLineLength = 0;
        for (String line : lines) {
            maxLineLength = Math.max(maxLineLength, line.length());
        }
        float panelWidth = MARGIN_X + (maxLineLength * charWidth) + PANEL_PADDING * 2;
        float panelHeight = MARGIN_Y + (lines.size() * lineHeight) + PANEL_PADDING * 2;

        // Render translucent background panel
        renderBackgroundPanel(0, 0, panelWidth, panelHeight, screenW, screenH);

        // Render each line
        float y = MARGIN_Y + PANEL_PADDING;
        for (String line : lines) {
            if (line.isEmpty()) {
                y += lineHeight * 0.5f; // Half-height for spacers
                continue;
            }

            float textX = MARGIN_X + PANEL_PADDING;

            // Shadow (offset by 1 pixel scaled)
            float shadowOffset = Math.max(1.0f, uiScale);
            font.drawText(line, textX + shadowOffset, y + shadowOffset, fontScale, screenW, screenH,
                          SHADOW_R, SHADOW_G, SHADOW_B, SHADOW_A);

            // Determine text color (headers get yellow tint)
            float r = TEXT_R, g = TEXT_G, b = TEXT_B;
            if (line.startsWith("[") || line.startsWith("===")) {
                r = HEADER_R; g = HEADER_G; b = HEADER_B;
            }

            // Main text
            font.drawText(line, textX, y, fontScale, screenW, screenH, r, g, b, TEXT_A);
            y += lineHeight;
        }
    }

    /**
     * Render a translucent background rectangle.
     * Note: Currently a no-op since we're using core profile OpenGL (no immediate mode).
     * The text shadow provides sufficient readability. Could add shader-based quad later.
     */
    private void renderBackgroundPanel(float x, float y, float width, float height, int screenW, int screenH) {
        // Background panel rendering disabled - text shadow provides sufficient readability
        // A proper implementation would use a dedicated quad shader
    }

    /**
     * Convert yaw angle to cardinal direction name.
     */
    private static String getFacingDirection(float yaw) {
        // Normalize yaw to 0-360
        float normalized = ((yaw % 360) + 360) % 360;
        if (normalized >= 337.5 || normalized < 22.5) return "E";
        if (normalized < 67.5) return "SE";
        if (normalized < 112.5) return "S";
        if (normalized < 157.5) return "SW";
        if (normalized < 202.5) return "W";
        if (normalized < 247.5) return "NW";
        if (normalized < 292.5) return "N";
        return "NE";
    }
}
