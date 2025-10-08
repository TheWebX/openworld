package com.example.mcclient;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import java.net.URI;
import java.nio.FloatBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Main {
    private long window;
    private double yaw = 0, pitch = 0;
    private boolean pointerLocked = false;
    private float moveX = 0, moveZ = 0;

    private final ConcurrentLinkedQueue<String> outgoing = new ConcurrentLinkedQueue<>();
    private WSClient ws;

    public static void main(String[] args) { new Main().run(); }

    public void run() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!GLFW.glfwInit()) throw new IllegalStateException("Unable to init GLFW");
        // Request a compatibility context that supports fixed-function (glBegin/glEnd)
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 1);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_ANY_PROFILE);
        window = GLFW.glfwCreateWindow(1280, 720, "MC Java Client", MemoryUtil.NULL, MemoryUtil.NULL);
        if (window == MemoryUtil.NULL) throw new RuntimeException("Failed to create window");
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        GLFW.glfwSwapInterval(1);

        int width = 1280, height = 720;
        GLFW.glfwSetWindowSize(window, width, height);
        GL11.glViewport(0, 0, width, height);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        // Basic perspective projection
        float fov = 70f;
        float aspect = (float) width / (float) height;
        float zNear = 0.1f, zFar = 1000f;
        float top = (float) Math.tan(Math.toRadians(fov * 0.5)) * zNear;
        float right = top * aspect;
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glFrustum(-right, right, -top, top, zNear, zFar);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        // input
        GLFW.glfwSetCursorPosCallback(window, (w, mx, my) -> {
            if (pointerLocked) {
                double dx = mx - 640, dy = my - 360;
                yaw -= dx * 0.002; pitch -= dy * 0.002;
                GLFW.glfwSetCursorPos(window, 640, 360);
            }
        });
        GLFW.glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
                pointerLocked = true;
                GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
            }
        });
        GLFW.glfwSetKeyCallback(window, (w, key, sc, action, mods) -> {
            boolean down = action != GLFW.GLFW_RELEASE;
            if (key == GLFW.GLFW_KEY_W) moveZ = down ? 1 : 0;
            if (key == GLFW.GLFW_KEY_S) moveZ = down ? -1 : 0;
            if (key == GLFW.GLFW_KEY_A) moveX = down ? -1 : 0;
            if (key == GLFW.GLFW_KEY_D) moveX = down ? 1 : 0;
        });

        // open websocket
        try {
            ws = new WSClient(new URI("ws://localhost:8080/ws"), outgoing);
            ws.connect();
        } catch (Exception e) { e.printStackTrace(); }

        loop();

        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }

    private void loop() {
        GL11.glClearColor(0.53f, 0.81f, 0.92f, 1f);
        while (!GLFW.glfwWindowShouldClose(window)) {
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            // camera transform (very basic)
            GL11.glLoadIdentity();
            GL11.glRotatef((float) Math.toDegrees(pitch), 1f, 0f, 0f);
            GL11.glRotatef((float) Math.toDegrees(yaw), 0f, 1f, 0f);
            GL11.glTranslatef(0f, -1.6f, -5f);
            // simple ground
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor3f(0.42f, 0.66f, 0.31f);
            GL11.glVertex3f(-50, 0, -50);
            GL11.glVertex3f(50, 0, -50);
            GL11.glVertex3f(50, 0, 50);
            GL11.glVertex3f(-50, 0, 50);
            GL11.glEnd();

            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();

            // send inputs ~30Hz
            if (ws != null && ws.isOpen()) {
                double ax = Math.sin(yaw) * moveZ + Math.cos(yaw) * moveX;
                double az = Math.cos(yaw) * moveZ - Math.sin(yaw) * moveX;
                String json = String.format("{\"type\":\"input\",\"input\":{\"ax\":%.3f,\"az\":%.3f,\"jump\":false,\"sprint\":false,\"yaw\":%.3f,\"pitch\":%.3f}}", ax, az, yaw, pitch);
                outgoing.offer(json);
            }
        }
    }
}
