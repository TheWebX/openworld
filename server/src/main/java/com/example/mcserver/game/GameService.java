package com.example.mcserver.game;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {
    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final World world = new World(12345L);

    public record ChunkRLE(int[] blocks, int yMin, int yMax) {}

    public static class Vec3 {
        public double x, y, z;
        public Vec3() {}
        public Vec3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }

    public static class AABB {
        public double x, y, z, w, h, d;
        public AABB(double x, double y, double z, double w, double h, double d) {
            this.x = x; this.y = y; this.z = z; this.w = w; this.h = h; this.d = d;
        }
    }

    public static class Player {
        public String id;
        public Vec3 position = new Vec3(0, 80, 0);
        public Vec3 velocity = new Vec3(0, 0, 0);
        public boolean onGround = false;
        public Player(String id) { this.id = id; }
        public AABB aabb() { return new AABB(position.x-0.3, position.y, position.z-0.3, 0.6, 1.8, 0.6); }
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
        double accel = sprint ? 40 : 25;
        p.velocity.x += ax * accel * 0.016;
        p.velocity.z += az * accel * 0.016;
        if (jump && p.onGround) { p.velocity.y = 9; p.onGround = false; }
    }

    @Scheduled(fixedRate = 16)
    public void tick() {
        for (Player p : players.values()) {
            applyPhysics(p, 0.016);
        }
        // TODO: broadcast state to clients via handler injection or event
    }

    public ChunkRLE getChunk(int cx, int cz) {
        return world.generateChunkRLE(cx, cz);
    }

    private void applyPhysics(Player p, double dt) {
        p.velocity.y -= 25 * dt;
        p.velocity.x *= Math.pow(0.8, dt*60);
        p.velocity.z *= Math.pow(0.8, dt*60);

        moveAndCollide(p, p.velocity.x*dt, 0, 0);
        moveAndCollide(p, 0, p.velocity.y*dt, 0);
        moveAndCollide(p, 0, 0, p.velocity.z*dt);
    }

    private void moveAndCollide(Player p, double dx, double dy, double dz) {
        AABB box = p.aabb();
        double newX = box.x + dx;
        double newY = box.y + dy;
        double newZ = box.z + dz;

        if (!collides(newX, box.y, box.z, box.w, box.h, box.d)) { p.position.x += dx; } else { p.velocity.x = 0; }
        if (!collides(p.position.x-0.3, newY, p.position.z-0.3, box.w, box.h, box.d)) { p.position.y += dy; p.onGround = dy < 0 && collides(p.position.x-0.3, newY-0.01, p.position.z-0.3, box.w, box.h, box.d); } else { if (dy<0) p.onGround = true; p.velocity.y = 0; }
        if (!collides(p.position.x-0.3, box.y, newZ, box.w, box.h, box.d)) { p.position.z += dz; } else { p.velocity.z = 0; }
    }

    private boolean collides(double x, double y, double z, double w, double h, double d) {
        int minX = (int)Math.floor(x);
        int maxX = (int)Math.floor(x + w);
        int minY = (int)Math.floor(y);
        int maxY = (int)Math.floor(y + h);
        int minZ = (int)Math.floor(z);
        int maxZ = (int)Math.floor(z + d);
        for (int yy = minY; yy <= maxY; yy++) {
            for (int xx = minX; xx <= maxX; xx++) {
                for (int zz = minZ; zz <= maxZ; zz++) {
                    if (world.isSolid(xx, yy, zz)) return true;
                }
            }
        }
        return false;
    }
}
