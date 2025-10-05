package com.example.mcserver.websocket;

import com.example.mcserver.game.GameService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GameService gameService;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public GameWebSocketHandler(GameService gameService) {
        this.gameService = gameService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        gameService.onPlayerConnect(session.getId());
        // Send hello with only player id
        var hello = objectMapper.createObjectNode()
                .put("type", "hello")
                .put("id", session.getId());
        var blocksNode = objectMapper.createArrayNode();
        for (var entry : gameService.getBlocks().entrySet()) {
            String[] parts = entry.getKey().split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            int t = entry.getValue();
            blocksNode.add(objectMapper.createObjectNode()
                    .put("x", x).put("y", y).put("z", z).put("type", t));
        }
        hello.set("blocks", blocksNode);
        send(session, hello);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = root.path("type").asText("");
        switch (type) {
            case "input":
                gameService.handlePlayerInput(session.getId(), root.path("input"));
                break;
            case "placeBlock":
                int x = root.path("x").asInt();
                int y = root.path("y").asInt();
                int z = root.path("z").asInt();
                int type = root.path("type").asInt(1);
                if (gameService.setBlock(x, y, z, type)) {
                    broadcast(objectMapper.createObjectNode()
                            .put("type", "blockUpdate")
                            .put("action", "set")
                            .put("x", x).put("y", y).put("z", z)
                            .put("block", type));
                }
                break;
            case "removeBlock":
                int rx = root.path("x").asInt();
                int ry = root.path("y").asInt();
                int rz = root.path("z").asInt();
                if (gameService.removeBlock(rx, ry, rz)) {
                    broadcast(objectMapper.createObjectNode()
                            .put("type", "blockUpdate")
                            .put("action", "remove")
                            .put("x", rx).put("y", ry).put("z", rz));
                }
                break;
            // no world/chunk messages in flat world mode
            default:
                break;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        gameService.onPlayerDisconnect(session.getId());
    }

    public void broadcast(Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            return;
        }
        for (WebSocketSession s : sessions.values()) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(new TextMessage(json));
                } catch (IOException ignored) {}
            }
        }
    }

    private void send(WebSocketSession s, JsonNode node) throws IOException {
        s.sendMessage(new TextMessage(objectMapper.writeValueAsString(node)));
    }
}
