package com.example.mcserver.game;

public class World {
    private final int CHUNK_SIZE = 16;
    private final int WORLD_MIN_Y = 0;
    private final int WORLD_MAX_Y = 128;

    public World(long seed) {
        // seed reserved for future noise; deterministic hash used below
    }

    public int getChunkSize() { return CHUNK_SIZE; }
    public int getWorldMinY() { return WORLD_MIN_Y; }
    public int getWorldMaxY() { return WORLD_MAX_Y; }

    public boolean isSolid(int x, int y, int z) {
        if (y < WORLD_MIN_Y || y >= WORLD_MAX_Y) return false;
        int height = (int)(40 + 20 * noise2d(x * 0.02, z * 0.02) + 5 * noise2d(x * 0.1, z * 0.1));
        return y <= height;
    }

    public GameService.ChunkRLE generateChunkRLE(int cx, int cz) {
        int yMin = WORLD_MIN_Y;
        int yMax = WORLD_MAX_Y;
        int size = CHUNK_SIZE * CHUNK_SIZE * (yMax - yMin);
        int[] dense = new int[size];
        int idx = 0;
        for (int y = yMin; y < yMax; y++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    int wx = cx * CHUNK_SIZE + x;
                    int wz = cz * CHUNK_SIZE + z;
                    boolean solid = isSolid(wx, y, wz);
                    dense[idx++] = solid ? 1 : 0;
                }
            }
        }
        return new GameService.ChunkRLE(dense, yMin, yMax);
    }

    private double noise2d(double x, double z) {
        long n = (long)(x * 49632) ^ (long)(z * 325176) ^ 0x9E3779B97F4A7C15L;
        n = (n << 13) ^ n;
        long nn = (n * (n * n * 15731 + 789221) + 1376312589L);
        return 1.0 - ((nn & 0x7fffffff) / 1073741824.0);
    }
}
