package com.voxelgame.world;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The game world. Owns all loaded chunks, provides block access across chunk boundaries.
 */
public class World implements WorldAccess {

    private final ConcurrentHashMap<ChunkPos, Chunk> chunks = new ConcurrentHashMap<>();

    @Override
    public int getBlock(int x, int y, int z) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return 0;
        int cx = Math.floorDiv(x, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(z, WorldConstants.CHUNK_SIZE);
        Chunk chunk = chunks.get(new ChunkPos(cx, cz));
        if (chunk == null) return 0;
        int lx = Math.floorMod(x, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(z, WorldConstants.CHUNK_SIZE);
        return chunk.getBlock(lx, y, lz);
    }

    public void setBlock(int x, int y, int z, int blockId) {
        if (y < 0 || y >= WorldConstants.WORLD_HEIGHT) return;
        int cx = Math.floorDiv(x, WorldConstants.CHUNK_SIZE);
        int cz = Math.floorDiv(z, WorldConstants.CHUNK_SIZE);
        Chunk chunk = chunks.get(new ChunkPos(cx, cz));
        if (chunk == null) return;
        int lx = Math.floorMod(x, WorldConstants.CHUNK_SIZE);
        int lz = Math.floorMod(z, WorldConstants.CHUNK_SIZE);
        chunk.setBlock(lx, y, lz, blockId);
    }

    @Override
    public Chunk getChunk(int cx, int cz) {
        return chunks.get(new ChunkPos(cx, cz));
    }

    @Override
    public boolean isLoaded(int cx, int cz) {
        return chunks.containsKey(new ChunkPos(cx, cz));
    }

    public void addChunk(ChunkPos pos, Chunk chunk) {
        chunks.put(pos, chunk);
    }

    public void removeChunk(ChunkPos pos) {
        Chunk chunk = chunks.remove(pos);
        if (chunk != null) {
            chunk.dispose();
        }
    }

    public Collection<Chunk> getLoadedChunks() {
        return chunks.values();
    }

    public ConcurrentHashMap<ChunkPos, Chunk> getChunkMap() {
        return chunks;
    }
}
