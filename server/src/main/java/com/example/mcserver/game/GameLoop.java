package com.example.mcserver.game;

import com.example.mcserver.websocket.GameWebSocketHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GameLoop {
    private final GameService gameService;
    private final GameWebSocketHandler ws;
    private long lastBroadcastNanos = 0L;

    public GameLoop(GameService gameService, GameWebSocketHandler ws) {
        this.gameService = gameService;
        this.ws = ws;
    }

    @Scheduled(fixedRate = 16)
    public void tickLoop() {
        gameService.tick();
        // throttle broadcasts to ~20 FPS to reduce bandwidth/CPU
        long now = System.nanoTime();
        if (now - lastBroadcastNanos < 50_000_000L) {
            return;
        }
        lastBroadcastNanos = now;
        // broadcast player states
        List<Map<String, Object>> players = new ArrayList<>();
        for (GameService.Player p : gameService.getPlayers().values()) {
            Map<String, Object> ps = new HashMap<>();
            ps.put("id", p.id);
            ps.put("x", p.position.x);
            ps.put("y", p.position.y);
            ps.put("z", p.position.z);
            ps.put("hp", p.hp);
            ps.put("yaw", p.yaw);
            ps.put("pitch", p.pitch);
            players.add(ps);
        }
        // NPCs
        List<Map<String, Object>> npcs = new ArrayList<>();
        for (GameService.NPC n : gameService.getNpcs().values()) {
            Map<String, Object> ns = new HashMap<>();
            ns.put("id", n.id);
            ns.put("kind", n.kind);
            ns.put("x", n.position.x);
            ns.put("y", n.position.y);
            ns.put("z", n.position.z);
            npcs.add(ns);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "state");
        payload.put("players", players);
        payload.put("npcs", npcs);
        ws.broadcast(payload);
        ws.broadcastEphemeral(gameService);
    }
}
