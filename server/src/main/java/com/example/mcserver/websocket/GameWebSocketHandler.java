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
        // Send hello with world meta
        var meta = gameService.getWorldMeta();
        send(session, objectMapper.createObjectNode()
                .put("type", "hello")
                .put("id", session.getId())
                .put("chunkSize", meta.chunkSize())
                .put("yMin", meta.yMin())
                .put("yMax", meta.yMax()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = root.path("type").asText("");
        switch (type) {
            case "input":
                gameService.handlePlayerInput(session.getId(), root.path("input"));
                break;
            case "requestChunk":
                int cx = root.path("cx").asInt();
                int cz = root.path("cz").asInt();
                var chunk = gameService.getChunk(cx, cz);
                send(session, objectMapper.createObjectNode()
                        .put("type", "chunk")
                        .put("cx", cx)
                        .put("cz", cz)
                        .put("format", "dense")
                        .put("size", chunk.blocks().length)
                        .put("yMin", chunk.yMin())
                        .put("yMax", chunk.yMax())
                        .set("data", objectMapper.valueToTree(chunk.blocks())));
                break;
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
