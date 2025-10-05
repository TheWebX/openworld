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

    // NPCs and projectiles
    public static class NPC {
        public String id;
        public String kind; // "villager" | "police"
        public Vec3 position = new Vec3(0, 0, 0);
        public Vec3 velocity = new Vec3(0, 0, 0);
        public int hp = 100;
        public String targetPlayerId; // police target
        public double thinkTimer = 0; // seconds until next decision
        public double shootCooldown = 0; // seconds until next shot
        public String aiState = "idle";
        public Integer coverX = null;
        public Integer coverZ = null;
        public NPC(String id, String kind) { this.id = id; this.kind = kind; }
    }

    public static class Projectile {
        public String ownerPlayerId;
        public Vec3 position;
        public Vec3 velocity;
        public double ttlSeconds;
        public Projectile(String ownerPlayerId, Vec3 position, Vec3 velocity, double ttlSeconds) {
            this.ownerPlayerId = ownerPlayerId; this.position = position; this.velocity = velocity; this.ttlSeconds = ttlSeconds;
        }
    }

    private final Map<String, NPC> npcs = new ConcurrentHashMap<>();
    private final java.util.List<Projectile> projectiles = new java.util.ArrayList<>();
    private final Map<String, Double> threatByPlayerSeconds = new ConcurrentHashMap<>();

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
        public int hp = 100;
        public double yaw = 0;
        public double pitch = 0;
        public Player(String id) { this.id = id; }
    }

    public void onPlayerConnect(String sessionId) {
        Player p = new Player(sessionId);
        // find safe spawn near origin (avoid water/blocks, ensure headroom)
        Vec3 spawn = findSafeSpawn(0, 0, 48);
        p.position = spawn;
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
        // camera orientation (for third-person visuals)
        p.yaw = input.path("yaw").asDouble(p.yaw);
        p.pitch = input.path("pitch").asDouble(p.pitch);
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
        double dt = 0.016;
        for (Player p : players.values()) {
            applyFlatPhysics(p, dt);
        }
        // simple NPC wander and social/police behavior
        for (NPC n : npcs.values()) {
            n.thinkTimer -= dt;
            n.shootCooldown = Math.max(0, n.shootCooldown - dt);
            if ("villager".equals(n.kind)) {
                if (n.thinkTimer <= 0) {
                    n.thinkTimer = 0.5 + rng.nextDouble() * 1.0;
                    // cohesion with nearby villagers
                    Vec3 center = new Vec3(0, 0, 0);
                    int count = 0;
                    for (NPC m : npcs.values()) {
                        if (m == n || !"villager".equals(m.kind)) continue;
                        double dx = m.position.x - n.position.x;
                        double dz = m.position.z - n.position.z;
                        if (dx*dx + dz*dz < 36) { // within 6m
                            center.x += m.position.x;
                            center.z += m.position.z;
                            count++;
                        }
                    }
                    double vx = 0, vz = 0;
                    if (count > 0) {
                        center.x /= count; center.z /= count;
                        vx += (center.x - n.position.x);
                        vz += (center.z - n.position.z);
                    }
                    // wander
                    double ang = rng.nextDouble() * Math.PI * 2;
                    vx += Math.cos(ang) * 2.0;
                    vz += Math.sin(ang) * 2.0;
                    double len = Math.hypot(vx, vz);
                    if (len > 0) { vx/=len; vz/=len; }
                    n.velocity.x = vx * 2.5;
                    n.velocity.z = vz * 2.5;
                }
            } else if ("police".equals(n.kind)) {
                // periodic decision making
                if (n.thinkTimer <= 0) {
                    n.thinkTimer = 0.25;
                    // choose threatened player if any
                    String target = pickHighestThreatPlayer();
                    if (target != null) n.targetPlayerId = target;
                    // sometimes re-evaluate cover
                    if (rng.nextDouble() < 0.3) { n.coverX = null; n.coverZ = null; }
                }
                Player t = (n.targetPlayerId != null) ? players.get(n.targetPlayerId) : null;
                if (t == null) {
                    // guard nearest villager
                    NPC nearestVill = null; double best = Double.MAX_VALUE;
                    for (NPC m : npcs.values()) {
                        if (!"villager".equals(m.kind)) continue;
                        double dx = m.position.x - n.position.x;
                        double dz = m.position.z - n.position.z;
                        double d2 = dx*dx + dz*dz;
                        if (d2 < best) { best = d2; nearestVill = m; }
                    }
                    if (nearestVill != null) {
                        double dx = nearestVill.position.x - n.position.x;
                        double dz = nearestVill.position.z - n.position.z;
                        double len = Math.hypot(dx, dz);
                        if (len > 0) { dx/=len; dz/=len; }
                        n.velocity.x = dx * 2.5;
                        n.velocity.z = dz * 2.5;
                    }
                } else {
                    double tx = t.position.x, tz = t.position.z;
                    double dx = tx - n.position.x;
                    double dz = tz - n.position.z;
                    double dist = Math.hypot(dx, dz);
                    // desired distance band
                    double minD = 10.0, maxD = 20.0;
                    // seek cover if close and no cover
                    if ((n.coverX == null || n.coverZ == null) && dist < maxD) {
                        int[] cover = findCoverNear(n, t);
                        if (cover != null) { n.coverX = cover[0]; n.coverZ = cover[1]; }
                    }
                    if (n.coverX != null && n.coverZ != null) {
                        double cx = n.coverX + 0.5, cz = n.coverZ + 0.5;
                        double cdx = cx - n.position.x, cdz = cz - n.position.z;
                        double clen = Math.hypot(cdx, cdz);
                        if (clen > 0.6) {
                            // move to cover
                            cdx /= clen; cdz /= clen;
                            n.velocity.x = cdx * 3.0; n.velocity.z = cdz * 3.0;
                        } else {
                            // at cover: hold, occasionally peek and shoot
                            n.velocity.x = 0; n.velocity.z = 0;
                            boolean los = hasLineOfSight(n.position.x, n.position.y + 1.2, n.position.z, tx, t.position.y + 1.2, tz);
                            if (!los) {
                                // side peek
                                double perpX = -dz / Math.max(0.001, dist);
                                double perpZ = dx / Math.max(0.001, dist);
                                double dir = rng.nextBoolean() ? 1 : -1;
                                n.velocity.x += perpX * dir * 1.8;
                                n.velocity.z += perpZ * dir * 1.8;
                            } else if (n.shootCooldown <= 0) {
                                shootAt(n.position.x, n.position.y + 1.4, n.position.z, tx, t.position.y + 1.0, tz, 38.0);
                                n.shootCooldown = 1.0;
                            }
                        }
                    } else {
                        // no cover: keep distance and strafe
                        double ux = dx / Math.max(0.001, dist), uz = dz / Math.max(0.001, dist);
                        double moveX = 0, moveZ = 0;
                        if (dist < minD) { moveX -= ux * 3.0; moveZ -= uz * 3.0; }
                        else if (dist > maxD) { moveX += ux * 3.5; moveZ += uz * 3.5; }
                        // strafe component
                        double sx = -uz, sz = ux;
                        double sdir = rng.nextBoolean() ? 1 : -1;
                        moveX += sx * sdir * 2.0;
                        moveZ += sz * sdir * 2.0;
                        n.velocity.x = moveX; n.velocity.z = moveZ;
                        // opportunistic shot if clear LOS
                        boolean los = hasLineOfSight(n.position.x, n.position.y + 1.2, n.position.z, tx, t.position.y + 1.2, tz);
                        if (los && n.shootCooldown <= 0 && dist < 22.0) {
                            shootAt(n.position.x, n.position.y + 1.4, n.position.z, tx, t.position.y + 1.0, tz, 40.0);
                            n.shootCooldown = 0.9;
                        }
                    }
                }
            }
            // integrate and ground collide
            n.position.x += n.velocity.x * dt;
            n.position.z += n.velocity.z * dt;
            int groundY = Math.max(0, getSurfaceY((int)Math.floor(n.position.x), (int)Math.floor(n.position.z)));
            n.position.y = groundY;
        }
        // projectiles
        java.util.Iterator<Projectile> it = projectiles.iterator();
        while (it.hasNext()) {
            Projectile pr = it.next();
            pr.ttlSeconds -= dt;
            if (pr.ttlSeconds <= 0) { it.remove(); continue; }
            pr.position.x += pr.velocity.x * dt;
            pr.position.y += pr.velocity.y * dt;
            pr.position.z += pr.velocity.z * dt;
            // hit NPCs
            for (NPC n : npcs.values()) {
                if (distance2(pr.position, n.position) < 0.6*0.6) {
                    n.hp -= 25;
                    if ("villager".equals(n.kind) && pr.ownerPlayerId != null) {
                        // mark threat for owner
                        threatByPlayerSeconds.put(pr.ownerPlayerId, 10.0);
                    }
                    if (n.hp <= 0) {
                        npcs.remove(n.id);
                    }
                    it.remove();
                    break;
                }
            }
            // hit players
            for (Player pl : players.values()) {
                if (distance2(pr.position, pl.position) < 0.6*0.6) {
                    pl.hp -= 25;
                    it.remove();
                    break;
                }
            }
        }
        // decay threats
        java.util.Iterator<Map.Entry<String, Double>> tit = threatByPlayerSeconds.entrySet().iterator();
        while (tit.hasNext()) {
            Map.Entry<String, Double> e = tit.next();
            double v = e.getValue() - dt;
            if (v <= 0) tit.remove(); else e.setValue(v);
        }
    }

    private static double distance2(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
        return dx*dx + dy*dy + dz*dz;
    }

    public void handleShoot(String playerId, double dx, double dy, double dz) {
        Player p = players.get(playerId);
        if (p == null) return;
        double len = Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len == 0) return;
        dx/=len; dy/=len; dz/=len;
        Vec3 start = new Vec3(p.position.x, p.position.y + 1.4, p.position.z);
        Vec3 vel = new Vec3(dx * 40, dy * 40, dz * 40);
        projectiles.add(new Projectile(playerId, start, vel, 2.0));
    }

    private void shootAt(double sx, double sy, double sz, double tx, double ty, double tz, double speed) {
        double vx = tx - sx, vy = ty - sy, vz = tz - sz;
        double l = Math.sqrt(vx*vx + vy*vy + vz*vz);
        if (l == 0) return; vx/=l; vy/=l; vz/=l;
        projectiles.add(new Projectile(null, new Vec3(sx, sy, sz), new Vec3(vx*speed, vy*speed, vz*speed), 2.0));
    }

    private boolean hasLineOfSight(double x0, double y0, double z0, double x1, double y1, double z1) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
        int steps = Math.max(1, (int)Math.ceil(dist * 3));
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            double x = x0 + dx * t;
            double y = y0 + dy * t;
            double z = z0 + dz * t;
            int cx = (int)Math.floor(x);
            int cy = (int)Math.floor(y);
            int cz = (int)Math.floor(z);
            if (isSolidCell(cx, cy, cz)) return false;
        }
        return true;
    }

    private int[] findCoverNear(NPC n, Player t) {
        int r = 8;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int bx = (int)Math.floor(n.position.x) + dx;
                int bz = (int)Math.floor(n.position.z) + dz;
                int by = 1;
                if (isSolidCell(bx, by, bz)) {
                    double vx = (bx + 0.5) - t.position.x;
                    double vz = (bz + 0.5) - t.position.z;
                    int sx = vx >= 0 ? 1 : -1;
                    int sz = vz >= 0 ? 1 : -1;
                    int cx = bx + sx;
                    int cz = bz + sz;
                    if (!isSolidCell(cx, 0, cz) && !isSolidCell(cx, 1, cz)) {
                        return new int[]{cx, cz};
                    }
                }
            }
        }
        return null;
    }

    private String pickHighestThreatPlayer() {
        String bestKey = null; double bestVal = -1;
        for (Map.Entry<String, Double> e : threatByPlayerSeconds.entrySet()) {
            if (e.getValue() > bestVal) { bestVal = e.getValue(); bestKey = e.getKey(); }
        }
        return bestKey;
    }

    public Map<String, Player> getPlayers() {
        return players;
    }

    public Map<String, NPC> getNpcs() {
        return npcs;
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

        // Spawn some NPCs
        for (int i = 0; i < 10; i++) {
            String id = "npc_v_" + i;
            NPC n = new NPC(id, "villager");
            n.position = new Vec3(rng.nextInt(max - min + 1) + min + 0.5, getSurfaceY(0, 0), rng.nextInt(max - min + 1) + min + 0.5);
            npcs.put(id, n);
        }
        for (int i = 0; i < 5; i++) {
            String id = "npc_p_" + i;
            NPC n = new NPC(id, "police");
            n.position = new Vec3(rng.nextInt(max - min + 1) + min + 0.5, getSurfaceY(0, 0), rng.nextInt(max - min + 1) + min + 0.5);
            npcs.put(id, n);
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

    private Vec3 findSafeSpawn(int cx, int cz, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int x = cx + dx;
                    int z = cz + dz;
                    Integer tBelow = getType(x, 0, z);
                    if (tBelow != null && tBelow == TYPE_WATER) continue;
                    int y = getSurfaceY(x, z);
                    // ensure headroom and not water at y
                    Integer tHere = getType(x, y, z);
                    if (tHere != null && tHere == TYPE_WATER) continue;
                    // spawn 2m above surface
                    Vec3 candidate = new Vec3(x + 0.5, y + 2.0, z + 0.5);
                    if (!collides(candidate.x, candidate.y, candidate.z)) {
                        return candidate;
                    }
                }
            }
        }
        return new Vec3(cx + 0.5, Math.max(0, getSurfaceY(cx, cz)), cz + 0.5);
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
