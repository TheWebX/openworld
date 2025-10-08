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
    public final ConcurrentHashMap<Long, Integer> blocks = new ConcurrentHashMap<>(); // key packed x,y,z
    public volatile List<Player> players = List.of();
    public volatile List<NPC> npcs = List.of();

    public static long pack(int x,int y,int z){
        return (((long)(x & 0x3FFFF))<<42) | (((long)(y & 0x3FFFF))<<21) | (long)(z & 0x3FFFF);
    }

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
        long key = pack(x,y,z);
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
                blocks.put(pack(x,y,z), t);
            }
        }
    }
}
