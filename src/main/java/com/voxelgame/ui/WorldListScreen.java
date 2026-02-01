package com.voxelgame.ui;

import com.voxelgame.save.WorldMeta;
import com.voxelgame.sim.Difficulty;
import com.voxelgame.sim.GameMode;

import java.io.IOException;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.lwjgl.opengl.GL33.*;

/**
 * World selection screen. Shows all saved worlds with metadata.
 * Allows loading, creating, or deleting worlds.
 */
public class WorldListScreen extends Screen {

    /** Callback interface for world list actions. */
    public interface WorldListCallback {
        void onPlayWorld(String worldName);
        void onCreateNew();
        void onBack();
    }

    /** Info about a saved world (for display). */
    public static class WorldInfo {
        public final String folderName;
        public final String displayName;
        public final GameMode gameMode;
        public final Difficulty difficulty;
        public final long lastPlayed;
        public final long seed;

        public WorldInfo(String folderName, String displayName, GameMode gameMode,
                         Difficulty difficulty, long lastPlayed, long seed) {
            this.folderName = folderName;
            this.displayName = displayName;
            this.gameMode = gameMode;
            this.difficulty = difficulty;
            this.lastPlayed = lastPlayed;
            this.seed = seed;
        }
    }

    private WorldListCallback callback;
    private List<WorldInfo> worlds = new ArrayList<>();
    private int selectedIndex = -1;
    private int hoveredIndex = -1;
    private int hoveredButton = -1; // 0=Play, 1=Create, 2=Delete, 3=Back
    private boolean showDeleteConfirm = false;
    private int deleteConfirmHover = -1; // 0=Yes, 1=No

    private static final float LIST_ITEM_HEIGHT = 60.0f;
    private static final float LIST_GAP = 4.0f;
    private static final float LIST_WIDTH = 500.0f;
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private int scrollOffset = 0;
    private static final int MAX_VISIBLE = 6;

    public void setCallback(WorldListCallback callback) {
        this.callback = callback;
    }

    /**
     * Scan the saves directory and populate the world list.
     */
    public void refreshWorldList() {
        worlds.clear();
        selectedIndex = -1;
        showDeleteConfirm = false;

        String home = System.getProperty("user.home");
        Path savesDir = Paths.get(home, ".voxelgame", "saves");

        if (!Files.exists(savesDir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(savesDir)) {
            for (Path dir : stream) {
                if (!Files.isDirectory(dir)) continue;
                Path worldDat = dir.resolve("world.dat");
                if (!Files.exists(worldDat)) continue;

                try {
                    WorldMeta meta = WorldMeta.load(dir);
                    if (meta != null) {
                        String folderName = dir.getFileName().toString();
                        String displayName = meta.getWorldName();
                        if (displayName == null || displayName.isEmpty()) {
                            displayName = folderName;
                        }
                        worlds.add(new WorldInfo(
                            folderName,
                            displayName,
                            meta.getGameMode(),
                            meta.getDifficulty(),
                            meta.getLastPlayedAt(),
                            meta.getSeed()
                        ));
                    }
                } catch (IOException e) {
                    System.err.println("Failed to read world meta for " + dir.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to scan saves directory: " + e.getMessage());
        }

        // Sort by last played (most recent first)
        worlds.sort((a, b) -> Long.compare(b.lastPlayed, a.lastPlayed));
    }

    /**
     * Delete the selected world folder.
     */
    public boolean deleteSelectedWorld() {
        if (selectedIndex < 0 || selectedIndex >= worlds.size()) return false;

        WorldInfo info = worlds.get(selectedIndex);
        String home = System.getProperty("user.home");
        Path worldDir = Paths.get(home, ".voxelgame", "saves", info.folderName);

        try {
            deleteDirectory(worldDir);
            worlds.remove(selectedIndex);
            selectedIndex = -1;
            showDeleteConfirm = false;
            return true;
        } catch (IOException e) {
            System.err.println("Failed to delete world: " + e.getMessage());
            return false;
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.delete(p); }
                    catch (IOException e) { /* best effort */ }
                });
        }
    }

    @Override
    public void render(int screenW, int screenH, float dt) {
        this.screenW = screenW;
        this.screenH = screenH;

        beginDraw(screenW, screenH);

        // Background
        fillRect(0, 0, screenW, screenH, 0.08f, 0.09f, 0.12f, 1.0f);

        endDraw();

        // Title
        drawCenteredTextWithShadow("Select World", screenH * 0.06f, 3.0f,
                                    0.8f, 0.9f, 1.0f, 1.0f);

        // World list area
        float listX = (screenW - LIST_WIDTH) / 2.0f;
        float listTopY = screenH * 0.15f; // top-left Y
        float listBottomLimit = screenH * 0.78f;

        beginDraw(screenW, screenH);

        // List background
        float listAreaH = listBottomLimit - listTopY;
        float listBgY = screenH - listBottomLimit; // bottom-left Y
        fillRect(listX - 8, listBgY - 8, LIST_WIDTH + 16, listAreaH + 16,
                 0.05f, 0.05f, 0.08f, 0.8f);

        // Render world entries
        int startIdx = scrollOffset;
        int endIdx = Math.min(worlds.size(), scrollOffset + MAX_VISIBLE);

        for (int i = startIdx; i < endIdx; i++) {
            WorldInfo info = worlds.get(i);
            int displayIdx = i - scrollOffset;
            float itemTopY = listTopY + displayIdx * (LIST_ITEM_HEIGHT + LIST_GAP);
            float itemY = screenH - itemTopY - LIST_ITEM_HEIGHT; // bottom-left Y

            // Background (selected/hovered)
            if (i == selectedIndex) {
                fillRect(listX, itemY, LIST_WIDTH, LIST_ITEM_HEIGHT,
                         0.15f, 0.25f, 0.4f, 0.9f);
                strokeRect(listX, itemY, LIST_WIDTH, LIST_ITEM_HEIGHT, 2,
                           0.4f, 0.6f, 0.9f, 1.0f);
            } else if (i == hoveredIndex) {
                fillRect(listX, itemY, LIST_WIDTH, LIST_ITEM_HEIGHT,
                         0.12f, 0.15f, 0.2f, 0.8f);
            } else {
                fillRect(listX, itemY, LIST_WIDTH, LIST_ITEM_HEIGHT,
                         0.1f, 0.1f, 0.13f, 0.7f);
            }
        }

        // Buttons at bottom
        float btnY_top = screenH * 0.82f;
        float btnRowY = screenH - btnY_top - BUTTON_HEIGHT;
        float halfBtnW = (BUTTON_WIDTH - BUTTON_GAP) / 2.0f;
        float btn0X = (screenW - BUTTON_WIDTH) / 2.0f; // Play Selected (full width)
        float btn1X = (screenW - BUTTON_WIDTH) / 2.0f; // Create New (half)
        float btn2X = btn1X + halfBtnW + BUTTON_GAP;   // Delete (half)
        float btn3X = (screenW - BUTTON_WIDTH) / 2.0f;  // Back (full width)

        float row1Y = btnRowY;
        float row2Y = row1Y - BUTTON_HEIGHT - BUTTON_GAP;
        float row3Y = row2Y - BUTTON_HEIGHT - BUTTON_GAP;

        boolean hasSelection = selectedIndex >= 0;
        drawButton("Play Selected World", btn0X, row1Y, BUTTON_WIDTH, BUTTON_HEIGHT,
                   hoveredButton == 0, hasSelection);
        drawButton("Create New World", btn1X, row2Y, halfBtnW, BUTTON_HEIGHT,
                   hoveredButton == 1, true);
        drawButton("Delete", btn2X, row2Y, halfBtnW, BUTTON_HEIGHT,
                   hoveredButton == 2, hasSelection);
        drawButton("Back", btn3X, row3Y, BUTTON_WIDTH, BUTTON_HEIGHT,
                   hoveredButton == 3, true);

        endDraw();

        // Render world text info (outside beginDraw/endDraw since font uses its own shader)
        for (int i = startIdx; i < endIdx; i++) {
            WorldInfo info = worlds.get(i);
            int displayIdx = i - scrollOffset;
            float itemTopY = listTopY + displayIdx * (LIST_ITEM_HEIGHT + LIST_GAP);

            // World name
            font.drawText(info.displayName, listX + 8, itemTopY + 6, 2.0f,
                           screenW, screenH, 1.0f, 1.0f, 1.0f, 1.0f);

            // Mode + Difficulty
            String modeStr = info.gameMode.name() + " | " + info.difficulty.name();
            font.drawText(modeStr, listX + 8, itemTopY + 26, 1.5f,
                           screenW, screenH, 0.6f, 0.7f, 0.8f, 0.9f);

            // Last played
            String timeStr = info.lastPlayed > 0
                ? "Last played: " + DATE_FMT.format(new Date(info.lastPlayed))
                : "Never played";
            font.drawText(timeStr, listX + 8, itemTopY + 42, 1.5f,
                           screenW, screenH, 0.5f, 0.5f, 0.5f, 0.7f);
        }

        if (worlds.isEmpty()) {
            drawCenteredText("No worlds found", screenH * 0.45f, 2.0f,
                              0.5f, 0.5f, 0.5f, 0.8f);
            drawCenteredText("Click 'Create New World' to get started!", screenH * 0.50f, 1.5f,
                              0.4f, 0.5f, 0.6f, 0.7f);
        }

        // Scroll indicator
        if (worlds.size() > MAX_VISIBLE) {
            String scrollText = String.format("(%d-%d of %d)", scrollOffset + 1,
                Math.min(scrollOffset + MAX_VISIBLE, worlds.size()), worlds.size());
            drawCenteredText(scrollText, listBottomLimit + 2, 1.5f,
                              0.4f, 0.4f, 0.4f, 0.6f);
        }

        // Delete confirmation dialog
        if (showDeleteConfirm && selectedIndex >= 0) {
            renderDeleteConfirm(screenW, screenH);
        }
    }

    private void renderDeleteConfirm(int screenW, int screenH) {
        beginDraw(screenW, screenH);

        // Darken background
        fillRect(0, 0, screenW, screenH, 0.0f, 0.0f, 0.0f, 0.5f);

        // Dialog box
        float dlgW = 400;
        float dlgH = 160;
        float dlgX = (screenW - dlgW) / 2.0f;
        float dlgY = (screenH - dlgH) / 2.0f;

        fillRect(dlgX, dlgY, dlgW, dlgH, 0.12f, 0.12f, 0.15f, 0.95f);
        strokeRect(dlgX, dlgY, dlgW, dlgH, 2, 0.8f, 0.3f, 0.3f, 1.0f);

        // Buttons
        float yesX = dlgX + 30;
        float noX = dlgX + dlgW / 2 + 10;
        float btnW = dlgW / 2 - 40;
        float btnBY = dlgY + 20;
        drawButton("Yes, Delete", yesX, btnBY, btnW, BUTTON_HEIGHT,
                   deleteConfirmHover == 0, true);
        drawButton("Cancel", noX, btnBY, btnW, BUTTON_HEIGHT,
                   deleteConfirmHover == 1, true);

        endDraw();

        // Text
        WorldInfo info = worlds.get(selectedIndex);
        float textTopY = screenH - dlgY - dlgH + 20;
        drawCenteredTextWithShadow("Delete world?", textTopY, 2.5f,
                                    1.0f, 0.3f, 0.3f, 1.0f);
        drawCenteredTextWithShadow("\"" + info.displayName + "\"", textTopY + 30, 2.0f,
                                    0.9f, 0.9f, 0.9f, 1.0f);
        drawCenteredText("This cannot be undone!", textTopY + 55, 1.5f,
                          0.7f, 0.5f, 0.5f, 0.8f);
    }

    @Override
    public void handleClick(double mx, double my, int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;

        float clickY = screenH - (float) my;
        float clickX = (float) mx;

        // Handle delete confirmation first
        if (showDeleteConfirm) {
            handleDeleteConfirmClick(clickX, clickY, screenW, screenH);
            return;
        }

        // Check world list items
        float listX = (screenW - LIST_WIDTH) / 2.0f;
        float listTopY = screenH * 0.15f;

        int startIdx = scrollOffset;
        int endIdx = Math.min(worlds.size(), scrollOffset + MAX_VISIBLE);

        for (int i = startIdx; i < endIdx; i++) {
            int displayIdx = i - scrollOffset;
            float itemTopY = listTopY + displayIdx * (LIST_ITEM_HEIGHT + LIST_GAP);
            float itemY = screenH - itemTopY - LIST_ITEM_HEIGHT;

            if (isInside(clickX, clickY, listX, itemY, LIST_WIDTH, LIST_ITEM_HEIGHT)) {
                if (selectedIndex == i) {
                    // Double-click to play
                    if (callback != null) callback.onPlayWorld(worlds.get(i).folderName);
                }
                selectedIndex = i;
                return;
            }
        }

        // Check buttons
        float btnY_top = screenH * 0.82f;
        float btnRowY = screenH - btnY_top - BUTTON_HEIGHT;
        float halfBtnW = (BUTTON_WIDTH - BUTTON_GAP) / 2.0f;
        float btn0X = (screenW - BUTTON_WIDTH) / 2.0f;
        float btn1X = btn0X;
        float btn2X = btn1X + halfBtnW + BUTTON_GAP;
        float btn3X = btn0X;

        float row1Y = btnRowY;
        float row2Y = row1Y - BUTTON_HEIGHT - BUTTON_GAP;
        float row3Y = row2Y - BUTTON_HEIGHT - BUTTON_GAP;

        if (isInside(clickX, clickY, btn0X, row1Y, BUTTON_WIDTH, BUTTON_HEIGHT) && selectedIndex >= 0) {
            if (callback != null) callback.onPlayWorld(worlds.get(selectedIndex).folderName);
        } else if (isInside(clickX, clickY, btn1X, row2Y, halfBtnW, BUTTON_HEIGHT)) {
            if (callback != null) callback.onCreateNew();
        } else if (isInside(clickX, clickY, btn2X, row2Y, halfBtnW, BUTTON_HEIGHT) && selectedIndex >= 0) {
            showDeleteConfirm = true;
        } else if (isInside(clickX, clickY, btn3X, row3Y, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            if (callback != null) callback.onBack();
        }
    }

    private void handleDeleteConfirmClick(float clickX, float clickY, int screenW, int screenH) {
        float dlgW = 400;
        float dlgH = 160;
        float dlgX = (screenW - dlgW) / 2.0f;
        float dlgY = (screenH - dlgH) / 2.0f;

        float yesX = dlgX + 30;
        float noX = dlgX + dlgW / 2 + 10;
        float btnW = dlgW / 2 - 40;
        float btnBY = dlgY + 20;

        if (isInside(clickX, clickY, yesX, btnBY, btnW, BUTTON_HEIGHT)) {
            deleteSelectedWorld();
        } else if (isInside(clickX, clickY, noX, btnBY, btnW, BUTTON_HEIGHT)) {
            showDeleteConfirm = false;
        }
    }

    /**
     * Update hover state from mouse position.
     */
    public void updateHover(double mx, double my, int screenW, int screenH) {
        this.screenW = screenW;
        this.screenH = screenH;
        hoveredIndex = -1;
        hoveredButton = -1;
        deleteConfirmHover = -1;

        float clickY = screenH - (float) my;
        float clickX = (float) mx;

        if (showDeleteConfirm) {
            float dlgW = 400;
            float dlgH = 160;
            float dlgX = (screenW - dlgW) / 2.0f;
            float dlgY = (screenH - dlgH) / 2.0f;
            float yesX = dlgX + 30;
            float noX = dlgX + dlgW / 2 + 10;
            float btnW = dlgW / 2 - 40;
            float btnBY = dlgY + 20;

            if (isInside(clickX, clickY, yesX, btnBY, btnW, BUTTON_HEIGHT)) deleteConfirmHover = 0;
            else if (isInside(clickX, clickY, noX, btnBY, btnW, BUTTON_HEIGHT)) deleteConfirmHover = 1;
            return;
        }

        // World list hover
        float listX = (screenW - LIST_WIDTH) / 2.0f;
        float listTopY = screenH * 0.15f;
        int startIdx = scrollOffset;
        int endIdx = Math.min(worlds.size(), scrollOffset + MAX_VISIBLE);

        for (int i = startIdx; i < endIdx; i++) {
            int displayIdx = i - scrollOffset;
            float itemTopY = listTopY + displayIdx * (LIST_ITEM_HEIGHT + LIST_GAP);
            float itemY = screenH - itemTopY - LIST_ITEM_HEIGHT;
            if (isInside(clickX, clickY, listX, itemY, LIST_WIDTH, LIST_ITEM_HEIGHT)) {
                hoveredIndex = i;
                break;
            }
        }

        // Button hover
        float btnY_top = screenH * 0.82f;
        float btnRowY = screenH - btnY_top - BUTTON_HEIGHT;
        float halfBtnW = (BUTTON_WIDTH - BUTTON_GAP) / 2.0f;
        float btn0X = (screenW - BUTTON_WIDTH) / 2.0f;
        float btn1X = btn0X;
        float btn2X = btn1X + halfBtnW + BUTTON_GAP;
        float btn3X = btn0X;

        float row1Y = btnRowY;
        float row2Y = row1Y - BUTTON_HEIGHT - BUTTON_GAP;
        float row3Y = row2Y - BUTTON_HEIGHT - BUTTON_GAP;

        if (isInside(clickX, clickY, btn0X, row1Y, BUTTON_WIDTH, BUTTON_HEIGHT)) hoveredButton = 0;
        else if (isInside(clickX, clickY, btn1X, row2Y, halfBtnW, BUTTON_HEIGHT)) hoveredButton = 1;
        else if (isInside(clickX, clickY, btn2X, row2Y, halfBtnW, BUTTON_HEIGHT)) hoveredButton = 2;
        else if (isInside(clickX, clickY, btn3X, row3Y, BUTTON_WIDTH, BUTTON_HEIGHT)) hoveredButton = 3;
    }

    /**
     * Handle scroll input for the world list.
     */
    public void handleScroll(double scrollY) {
        if (showDeleteConfirm) return;
        if (scrollY > 0 && scrollOffset > 0) scrollOffset--;
        if (scrollY < 0 && scrollOffset + MAX_VISIBLE < worlds.size()) scrollOffset++;
    }

    public List<WorldInfo> getWorlds() { return worlds; }
}
