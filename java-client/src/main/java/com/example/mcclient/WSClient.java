package com.example.mcclient;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class WSClient extends WebSocketClient {
    private final ConcurrentLinkedQueue<String> outgoing;
    private final ObjectMapper mapper = new ObjectMapper();
    public volatile State state = new State();
    public WSClient(URI serverUri, ConcurrentLinkedQueue<String> outgoing) {
        super(serverUri);
        this.outgoing = outgoing;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        new Thread(() -> {
            try {
                while (isOpen()) {
                    String msg;
                    while ((msg = outgoing.poll()) != null) {
                        send(msg);
                    }
                    Thread.sleep(33);
                }
            } catch (InterruptedException ignored) {}
        }, "ws-sender").start();
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode root = mapper.readTree(message);
            String type = root.path("type").asText("");
            if ("state".equals(type)) {
                state.updateFrom(root);
            } else if ("hello".equals(type)) {
                state.loadHello(root);
            } else if ("blockUpdate".equals(type)) {
                state.applyBlockUpdate(root);
            } else if ("shot".equals(type)) {
                state.registerShot(root);
            } else if ("death".equals(type)) {
                state.registerDeath(root);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }
}
