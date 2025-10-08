package com.example.mcclient;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class State {
    public static class Player {
        public String id;
        public double x,y,z;
        public int hp = 100;
        public double yaw=0, pitch=0;
    }
    public static class NPC {
        public String id;
        public String kind;
        public double x,y,z;
    }

    public volatile String myId = null;
    public final ConcurrentHashMap<String, Integer> blocks = new ConcurrentHashMap<>(); // key: "x,y,z"
    public volatile List<Player> players = List.of();
    public volatile List<NPC> npcs = List.of();
    public final ConcurrentHashMap<String, Long> lastShotById = new ConcurrentHashMap<>();
    public volatile boolean dead = false;

    public void updateFrom(JsonNode root){
        // players
        List<Player> pl = new ArrayList<>();
        JsonNode arr = root.path("players");
        if (arr.isArray()){
            for (JsonNode p : arr){
                Player pp = new Player();
                pp.id = p.path("id").asText("");
                pp.x = p.path("x").asDouble(0);
                pp.y = p.path("y").asDouble(0);
                pp.z = p.path("z").asDouble(0);
                pp.hp = p.path("hp").asInt(100);
                pp.yaw = p.path("yaw").asDouble(0);
                pp.pitch = p.path("pitch").asDouble(0);
                pl.add(pp);
            }
        }
        players = pl;
        // npcs
        List<NPC> nl = new ArrayList<>();
        JsonNode an = root.path("npcs");
        if (an.isArray()){
            for (JsonNode n : an){
                NPC nn = new NPC();
                nn.id = n.path("id").asText("");
                nn.kind = n.path("kind").asText("");
                nn.x = n.path("x").asDouble(0);
                nn.y = n.path("y").asDouble(0);
                nn.z = n.path("z").asDouble(0);
                nl.add(nn);
            }
        }
        npcs = nl;
    }

    public void applyBlockUpdate(JsonNode root){
        String action = root.path("action").asText("");
        int x = root.path("x").asInt();
        int y = root.path("y").asInt();
        int z = root.path("z").asInt();
        String key = x+","+y+","+z;
        if ("set".equals(action)){
            int t = root.path("block").asInt(1);
            blocks.put(key, t);
        } else if ("remove".equals(action)){
            blocks.remove(key);
        }
    }

    public void loadHello(JsonNode root){
        myId = root.path("id").asText(null);
        JsonNode bs = root.path("blocks");
        if (bs.isArray()){
            for (JsonNode b : bs){
                int x = b.path("x").asInt();
                int y = b.path("y").asInt();
                int z = b.path("z").asInt();
                int t = b.path("type").asInt(1);
                blocks.put(x+","+y+","+z, t);
            }
        }
    }

    public void registerShot(JsonNode root) {
        String ownerId = root.path("ownerId").asText(null);
        if (ownerId != null) {
            lastShotById.put(ownerId, System.currentTimeMillis());
        } else {
            String ownerKind = root.path("ownerKind").asText("");
            if ("npc".equals(ownerKind)) {
                double sx = root.path("sx").asDouble();
                double sy = root.path("sy").asDouble();
                double sz = root.path("sz").asDouble();
                String best = null; double bestD = Double.POSITIVE_INFINITY;
                for (NPC n : npcs) {
                    double dx = n.x - sx, dy = n.y - sy, dz = n.z - sz;
                    double d = Math.sqrt(dx*dx + dy*dy + dz*dz);
                    if (d < bestD) { bestD = d; best = n.id; }
                }
                if (best != null && bestD < 2.0) {
                    lastShotById.put(best, System.currentTimeMillis());
                }
            }
        }
    }

    public void registerDeath(JsonNode root) {
        String pid = root.path("playerId").asText(null);
        if (pid != null && pid.equals(myId)) {
            dead = true;
        }
    }
}
