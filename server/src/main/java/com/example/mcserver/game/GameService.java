package com.example.mcserver.game;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {
    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final Map<String, Integer> blocks = new ConcurrentHashMap<>(); // key: "x,y,z" -> type id
    private static final double PLAYER_HALF = 0.3;
    private static final double PLAYER_HEIGHT = 1.8;
    private static final double EPS = 1e-3;
    private static final int TYPE_STONE = 1;
    private static final int TYPE_WOOD = 2;
    private static final int TYPE_LEAVES = 3;
    private static final int TYPE_DIRT = 4;
    private static final int TYPE_SAND = 5;
    private static final int TYPE_WATER = 6;
    private static final int TYPE_ROAD = 7;
    private static final int TYPE_GLASS = 8;
    private static final int TYPE_ROOF = 9;

    private final Random rng = new Random(1337L);

    public GameService() {
        seedDemo();
    }

    public static class Vec3 {
        public double x, y, z;
        public Vec3() {}
        public Vec3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }

    public static class Player {
        public String id;
        public Vec3 position = new Vec3(0, 0, 0);
        public Vec3 velocity = new Vec3(0, 0, 0);
        public boolean onGround = true;
        public Player(String id) { this.id = id; }
    }

    public void onPlayerConnect(String sessionId) {
        Player p = new Player(sessionId);
        // spawn on top of surface near origin
        int sx = 0, sz = 0;
        int sy = getSurfaceY(sx, sz);
        p.position = new Vec3(sx + 0.5, sy, sz + 0.5);
        p.onGround = true;
        players.put(sessionId, p);
    }

    public void onPlayerDisconnect(String sessionId) {
        players.remove(sessionId);
    }

    public void handlePlayerInput(String sessionId, JsonNode input) {
        Player p = players.get(sessionId);
        if (p == null) return;
        double ax = input.path("ax").asDouble(0);
        double az = input.path("az").asDouble(0);
        boolean jump = input.path("jump").asBoolean(false);
        boolean sprint = input.path("sprint").asBoolean(false);
        double accel = sprint ? 140 : 90;
        p.velocity.x += ax * accel * 0.016;
        p.velocity.z += az * accel * 0.016;
        // clamp planar speed
        double maxSpeed = sprint ? 12.0 : 7.0;
        double planar = Math.hypot(p.velocity.x, p.velocity.z);
        if (planar > maxSpeed) {
            double scale = maxSpeed / planar;
            p.velocity.x *= scale;
            p.velocity.z *= scale;
        }
        if (jump && p.onGround) { p.velocity.y = 8.5; p.onGround = false; }
    }

    public void tick() {
        for (Player p : players.values()) {
            applyFlatPhysics(p, 0.016);
        }
    }

    public Map<String, Player> getPlayers() {
        return players;
    }

    public Map<String, Integer> getBlocks() {
        return blocks;
    }

    public boolean setBlock(int x, int y, int z, int type) {
        if (y < 0) return false;
        String k = key(x, y, z);
        Integer prev = blocks.put(k, type);
        return prev == null || prev != type;
    }

    public boolean removeBlock(int x, int y, int z) {
        String k = key(x, y, z);
        return blocks.remove(k) != null;
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    public void seedDemo() {
        // Clear and generate a small city-like area [-64,64]
        blocks.clear();
        int min = -64, max = 64;

        // Roads grid every 16 units
        for (int x = min; x <= max; x++) {
            for (int z = min; z <= max; z++) {
                if (x % 16 == 0 || z % 16 == 0) {
                    setBlock(x, 0, z, TYPE_ROAD);
                }
            }
        }

        // Lakes: place 2-3 ellipses filled with water at y=1
        int lakeCount = 3;
        for (int i = 0; i < lakeCount; i++) {
            int cx = rng.nextInt(max - min + 1) + min;
            int cz = rng.nextInt(max - min + 1) + min;
            int rx = 6 + rng.nextInt(8);
            int rz = 6 + rng.nextInt(8);
            placeLake(cx, cz, rx, rz);
        }
        // Ensure at least one lake near spawn
        placeLake(12, -10, 8, 6);

        // Houses near roads (at offsets)
        for (int gx = min; gx <= max; gx += 16) {
            for (int gz = min; gz <= max; gz += 16) {
                if (rng.nextDouble() < 0.6) {
                    int ox = gx + (rng.nextBoolean() ? 3 : -10);
                    int oz = gz + (rng.nextBoolean() ? 3 : -10);
                    buildHouse(ox, oz, 7, 7);
                }
            }
        }

        // Trees scattered
        for (int i = 0; i < 80; i++) {
            int x = rng.nextInt(max - min + 1) + min;
            int z = rng.nextInt(max - min + 1) + min;
            if (x % 16 == 0 || z % 16 == 0) continue; // not on roads
            {
                Integer t = getType(x, 1, z);
                if (t != null && t == TYPE_WATER) continue;
            }
            buildTree(x, z);
        }
    }

    private void placeLake(int cx, int cz, int rx, int rz) {
        for (int x = cx - rx; x <= cx + rx; x++) {
            for (int z = cz - rz; z <= cz + rz; z++) {
                double nx = (x - cx) / (double) rx;
                double nz = (z - cz) / (double) rz;
                if (nx * nx + nz * nz <= 1.0) {
                    if (x % 16 != 0 && z % 16 != 0) { // avoid roads
                        setBlock(x, 1, z, TYPE_WATER);
                    }
                }
            }
        }
    }

    private void buildHouse(int baseX, int baseZ, int w, int d) {
        // ensure area is not water
        for (int x = baseX; x < baseX + w; x++) {
            for (int z = baseZ; z < baseZ + d; z++) {
                Integer t = getType(x, 1, z);
                if (t != null && t == TYPE_WATER) return;
            }
        }
        // floor
        for (int x = baseX; x < baseX + w; x++) {
            for (int z = baseZ; z < baseZ + d; z++) {
                setBlock(x, 0, z, TYPE_STONE);
            }
        }
        // walls
        for (int y = 1; y <= 3; y++) {
            for (int x = baseX; x < baseX + w; x++) {
                setBlock(x, y, baseZ, TYPE_WOOD);
                setBlock(x, y, baseZ + d - 1, TYPE_WOOD);
            }
            for (int z = baseZ; z < baseZ + d; z++) {
                setBlock(baseX, y, z, TYPE_WOOD);
                setBlock(baseX + w - 1, y, z, TYPE_WOOD);
            }
        }
        // door opening
        int doorX = baseX + w / 2;
        removeBlock(doorX, 1, baseZ);
        removeBlock(doorX, 2, baseZ);
        // windows
        setBlock(baseX + 1, 2, baseZ, TYPE_GLASS);
        setBlock(baseX + w - 2, 2, baseZ, TYPE_GLASS);
        setBlock(baseX + 1, 2, baseZ + d - 1, TYPE_GLASS);
        setBlock(baseX + w - 2, 2, baseZ + d - 1, TYPE_GLASS);
        // roof
        for (int x = baseX - 1; x <= baseX + w; x++) {
            for (int z = baseZ - 1; z <= baseZ + d; z++) {
                setBlock(x, 4, z, TYPE_ROOF);
            }
        }
    }

    private void buildTree(int x, int z) {
        for (int y = 0; y <= 3; y++) setBlock(x, y, z, TYPE_WOOD);
        for (int y = 3; y <= 5; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) <= 3) setBlock(x + dx, y, z + dz, TYPE_LEAVES);
                }
            }
        }
    }

    private Integer getType(int x, int y, int z) {
        return blocks.get(key(x, y, z));
    }

    private int getSurfaceY(int x, int z) {
        int y = 0;
        while (y < 64 && blocks.containsKey(key(x, y, z))) y++;
        return y; // first empty space above stack, >=0
    }

    private void applyFlatPhysics(Player p, double dt) {
        boolean inWater = intersectsWater(p.position.x, p.position.y, p.position.z);
        double g = inWater ? 8.0 : 25.0;
        p.velocity.y -= g * dt;
        double damp = inWater ? 0.85 : 0.92;
        p.velocity.x *= Math.pow(damp, dt * 60);
        p.velocity.z *= Math.pow(damp, dt * 60);
        if (inWater && p.velocity.y < -3) p.velocity.y = -3; // limit sinking speed

        double dx = p.velocity.x * dt;
        double dy = p.velocity.y * dt;
        double dz = p.velocity.z * dt;

        // move X
        if (!collides(p.position.x + dx, p.position.y, p.position.z)) {
            p.position.x += dx;
        } else {
            p.velocity.x = 0;
        }

        // move Y
        p.onGround = false;
        if (!collides(p.position.x, p.position.y + dy, p.position.z)) {
            p.position.y += dy;
        } else {
            if (dy < 0) p.onGround = true;
            p.velocity.y = 0;
        }

        // move Z
        if (!collides(p.position.x, p.position.y, p.position.z + dz)) {
            p.position.z += dz;
        } else {
            p.velocity.z = 0;
        }
    }

    private boolean collides(double cx, double cy, double cz) {
        double minX = cx - PLAYER_HALF;
        double minY = cy;
        double minZ = cz - PLAYER_HALF;
        double width = PLAYER_HALF * 2;
        double height = PLAYER_HEIGHT;
        double depth = PLAYER_HALF * 2;

        int minXi = (int) Math.floor(minX);
        int maxXi = (int) Math.floor(minX + width - EPS);
        int minYi = (int) Math.floor(minY);
        int maxYi = (int) Math.floor(minY + height - EPS);
        int minZi = (int) Math.floor(minZ);
        int maxZi = (int) Math.floor(minZ + depth - EPS);

        // ground plane as solid below y=0
        if (minYi < 0) return true;

        for (int y = minYi; y <= maxYi; y++) {
            for (int x = minXi; x <= maxXi; x++) {
                for (int z = minZi; z <= maxZi; z++) {
                    if (isSolidCell(x, y, z)) return true;
                }
            }
        }
        return false;
    }

    private boolean isSolidCell(int x, int y, int z) {
        if (y < 0) return true;
        Integer t = blocks.get(key(x, y, z));
        if (t == null) return false;
        return t != TYPE_WATER; // water is non-solid
    }

    private boolean intersectsWater(double cx, double cy, double cz) {
        double minX = cx - PLAYER_HALF;
        double minY = cy;
        double minZ = cz - PLAYER_HALF;
        double width = PLAYER_HALF * 2;
        double height = PLAYER_HEIGHT;
        double depth = PLAYER_HALF * 2;

        int minXi = (int) Math.floor(minX);
        int maxXi = (int) Math.floor(minX + width - EPS);
        int minYi = (int) Math.floor(minY);
        int maxYi = (int) Math.floor(minY + height - EPS);
        int minZi = (int) Math.floor(minZ);
        int maxZi = (int) Math.floor(minZ + depth - EPS);

        for (int y = minYi; y <= maxYi; y++) {
            for (int x = minXi; x <= maxXi; x++) {
                for (int z = minZi; z <= maxZi; z++) {
                    Integer t = getType(x, y, z);
                    if (t != null && t == TYPE_WATER) return true;
                }
            }
        }
        return false;
    }
}
