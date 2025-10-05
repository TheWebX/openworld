package com.example.mcserver.game;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {
    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final Map<String, Integer> blocks = new ConcurrentHashMap<>(); // key: "x,y,z" -> type id
    private static final double PLAYER_HALF = 0.3;
    private static final double PLAYER_HEIGHT = 1.8;
    private static final double EPS = 1e-3;

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
        players.put(sessionId, new Player(sessionId));
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
        // Stone pad near origin (type 1)
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                setBlock(x, 0, z, 1);
            }
        }
        // Tree trunk (type 2) and leaves canopy (type 3)
        int tx = 6, tz = 6;
        for (int y = 0; y <= 3; y++) setBlock(tx, y, tz, 2);
        for (int y = 3; y <= 5; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) <= 3) setBlock(tx + dx, y, tz + dz, 3);
                }
            }
        }
        // Dirt pile (type 4)
        for (int x = -8; x <= -5; x++) {
            for (int z = -8; z <= -5; z++) {
                setBlock(x, 0, z, 4);
                if ((x + z) % 2 == 0) setBlock(x, 1, z, 4);
            }
        }
    }

    private void applyFlatPhysics(Player p, double dt) {
        // gravity and damping
        p.velocity.y -= 25 * dt;
        p.velocity.x *= Math.pow(0.92, dt * 60);
        p.velocity.z *= Math.pow(0.92, dt * 60);

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
        return blocks.containsKey(key(x, y, z));
    }
}
