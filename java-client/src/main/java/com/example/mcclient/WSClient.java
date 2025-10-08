package com.example.mcclient;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.ConcurrentLinkedQueue;

public class WSClient extends WebSocketClient {
    private final ConcurrentLinkedQueue<String> outgoing;
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
        // TODO: parse state and render
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }
}
