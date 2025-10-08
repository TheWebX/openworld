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
    private boolean jump = false, sprint = false;
    private int selectedType = 1; // 1..5 blocks; 6 gun
    private boolean leftMouseDown = false, rightMouseDown = false;
    private long lastClickMs = 0;
    private float muzzleFlashTimer = 0f;

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
            boolean down = action == GLFW.GLFW_PRESS;
            if (!pointerLocked && down) {
                pointerLocked = true;
                GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                GLFW.glfwSetCursorPos(window, 640, 360);
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) leftMouseDown = down;
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) rightMouseDown = down;
        });
        GLFW.glfwSetKeyCallback(window, (w, key, sc, action, mods) -> {
            boolean down = action != GLFW.GLFW_RELEASE;
            if (key == GLFW.GLFW_KEY_W) moveZ = down ? 1 : 0;
            if (key == GLFW.GLFW_KEY_S) moveZ = down ? -1 : 0;
            if (key == GLFW.GLFW_KEY_A) moveX = down ? -1 : 0;
            if (key == GLFW.GLFW_KEY_D) moveX = down ? 1 : 0;
            if (key == GLFW.GLFW_KEY_SPACE) jump = down;
            if (key == GLFW.GLFW_KEY_LEFT_SHIFT) sprint = down;
            if (down) {
                if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_6) {
                    selectedType = (key - GLFW.GLFW_KEY_1) + 1;
                }
            }
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

            // draw blocks (very simple cubes)
            if (ws != null && ws.state != null) {
                for (var e : ws.state.blocks.entrySet()) {
                    String[] parts = e.getKey().split(",");
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    drawCube(x, y, z, e.getValue());
                }
                // draw players and npcs as simple rigs
                for (var p : ws.state.players) {
                    if (ws.state.myId != null && ws.state.myId.equals(p.id)) continue;
                    drawHumanRig((float)p.x, (float)p.y, (float)p.z, 0.8f, 0.5f, 0.2f);
                }
                for (var n : ws.state.npcs) {
                    boolean police = "police".equals(n.kind);
                    drawHumanRig((float)n.x, (float)n.y, (float)n.z, police?0.2f:0.6f, police?0.4f:0.7f, police?0.9f:0.5f);
                }
            }

            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();

            // send inputs ~30Hz
            if (ws != null && ws.isOpen()) {
                double ax = Math.sin(yaw) * moveZ + Math.cos(yaw) * moveX;
                double az = Math.cos(yaw) * moveZ - Math.sin(yaw) * moveX;
                String json = String.format("{\"type\":\"input\",\"input\":{\"ax\":%.3f,\"az\":%.3f,\"jump\":%s,\"sprint\":%s,\"yaw\":%.3f,\"pitch\":%.3f}}",
                        ax, az, jump?"true":"false", sprint?"true":"false", yaw, pitch);
                outgoing.offer(json);
            }

            // handle clicks (debounced)
            long nowMs = System.currentTimeMillis();
            if (pointerLocked && (leftMouseDown || rightMouseDown) && nowMs - lastClickMs > 120) {
                lastClickMs = nowMs;
                if (selectedType == 6 && leftMouseDown) {
                    // gun mode: shoot
                    Vector3f dir = getForward();
                    String shoot = String.format("{\"type\":\"shoot\",\"dir\":{\"x\":%.3f,\"y\":%.3f,\"z\":%.3f}}", dir.x, dir.y, dir.z);
                    outgoing.offer(shoot);
                    muzzleFlashTimer = 0.08f;
                } else {
                    // block mode
                    Vector3f origin = getCameraPosition();
                    Vector3f dir = getForward();
                    Hit hit = raycast(origin, dir, 8f);
                    if (rightMouseDown) {
                        if (hit != null && hit.blockKey != null) {
                            outgoing.offer(String.format("{\"type\":\"removeBlock\",\"x\":%d,\"y\":%d,\"z\":%d}", hit.x, hit.y, hit.z));
                        }
                    } else if (leftMouseDown) {
                        if (hit != null && hit.blockKey != null) {
                            int nx = hit.x + hit.nx;
                            int ny = hit.y + hit.ny;
                            int nz = hit.z + hit.nz;
                            outgoing.offer(String.format("{\"type\":\"placeBlock\",\"x\":%d,\"y\":%d,\"z\":%d,\"block\":%d}", nx, ny, nz, selectedType));
                        } else if (hit != null && hit.ground) {
                            outgoing.offer(String.format("{\"type\":\"placeBlock\",\"x\":%d,\"y\":0,\"z\":%d,\"block\":%d}", hit.gx, hit.gz, selectedType));
                        }
                    }
                }
            }

            // HUD crosshair and muzzle flash
            if (muzzleFlashTimer > 0f) {
                muzzleFlashTimer -= 0.016f;
            }
        }
    }

    private void drawCube(int x, int y, int z, int type) {
        float fx = x + 0.5f, fy = y + 0.5f, fz = z + 0.5f;
        float s = 0.5f;
        GL11.glPushMatrix();
        GL11.glTranslatef(fx, fy, fz);
        // color per type
        switch (type) {
            case 1 -> GL11.glColor3f(0.47f,0.47f,0.47f);
            case 2 -> GL11.glColor3f(0.55f,0.35f,0.17f);
            case 3 -> GL11.glColor3f(0.25f,0.66f,0.25f);
            case 4 -> GL11.glColor3f(0.6f,0.4f,0.2f);
            case 5 -> GL11.glColor3f(0.76f,0.7f,0.5f);
            case 6 -> GL11.glColor4f(0.23f,0.65f,1f,0.6f);
            default -> GL11.glColor3f(0.8f,0.8f,0.8f);
        }
        GL11.glBegin(GL11.GL_QUADS);
        // +Y
        GL11.glVertex3f(-s, s, -s); GL11.glVertex3f( s, s, -s); GL11.glVertex3f( s, s,  s); GL11.glVertex3f(-s, s,  s);
        // -Y
        GL11.glVertex3f(-s,-s, s); GL11.glVertex3f( s,-s, s); GL11.glVertex3f( s,-s,-s); GL11.glVertex3f(-s,-s,-s);
        // +X
        GL11.glVertex3f( s,-s,-s); GL11.glVertex3f( s, s,-s); GL11.glVertex3f( s, s, s); GL11.glVertex3f( s,-s, s);
        // -X
        GL11.glVertex3f(-s,-s, s); GL11.glVertex3f(-s, s, s); GL11.glVertex3f(-s, s,-s); GL11.glVertex3f(-s,-s,-s);
        // +Z
        GL11.glVertex3f(-s,-s, s); GL11.glVertex3f( s,-s, s); GL11.glVertex3f( s, s, s); GL11.glVertex3f(-s, s, s);
        // -Z
        GL11.glVertex3f( s,-s,-s); GL11.glVertex3f(-s,-s,-s); GL11.glVertex3f(-s, s,-s); GL11.glVertex3f( s, s,-s);
        GL11.glEnd();
        GL11.glPopMatrix();
    }

    private void drawHumanRig(float x, float y, float z, float r, float g, float b) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, z);
        GL11.glColor3f(r, g, b);
        // body
        GL11.glBegin(GL11.GL_QUADS);
        float w=0.25f, h=0.8f, d=0.14f;
        // top body quad (approx; simple pillar)
        GL11.glVertex3f(-w, h, -d); GL11.glVertex3f(w, h, -d); GL11.glVertex3f(w, h, d); GL11.glVertex3f(-w, h, d);
        GL11.glVertex3f(-w, 0, d); GL11.glVertex3f(w, 0, d); GL11.glVertex3f(w, 0, -d); GL11.glVertex3f(-w, 0, -d);
        GL11.glVertex3f(w,0,-d); GL11.glVertex3f(w,h,-d); GL11.glVertex3f(w,h,d); GL11.glVertex3f(w,0,d);
        GL11.glVertex3f(-w,0,d); GL11.glVertex3f(-w,h,d); GL11.glVertex3f(-w,h,-d); GL11.glVertex3f(-w,0,-d);
        GL11.glVertex3f(-w,0,d); GL11.glVertex3f(w,0,d); GL11.glVertex3f(w,h,d); GL11.glVertex3f(-w,h,d);
        GL11.glVertex3f(w,0,-d); GL11.glVertex3f(-w,0,-d); GL11.glVertex3f(-w,h,-d); GL11.glVertex3f(w,h,-d);
        GL11.glEnd();
        GL11.glPopMatrix();
    }

    private Vector3f getForward() {
        float cx = (float) Math.sin(yaw);
        float cz = (float) Math.cos(yaw);
        float cy = (float) -Math.sin(pitch);
        Vector3f v = new Vector3f(cx, cy, -cz);
        v.normalize();
        return v;
    }

    private Vector3f getCameraPosition() {
        // use local player position if available
        if (ws != null && ws.state != null && ws.state.myId != null) {
            for (var p : ws.state.players) {
                if (ws.state.myId.equals(p.id)) {
                    return new Vector3f((float)p.x, (float)(p.y + 1.6), (float)p.z);
                }
            }
        }
        return new Vector3f(0, 1.6f, 5f);
    }

    private static class Hit {
        int x,y,z; String blockKey; int nx,ny,nz; boolean ground; int gx,gz;
    }

    private Hit raycast(Vector3f origin, Vector3f dir, float maxDist) {
        float step = 0.1f;
        Vector3f p = new Vector3f(origin);
        for (float t=0; t<maxDist; t+=step) {
            p.x = origin.x + dir.x * t;
            p.y = origin.y + dir.y * t;
            p.z = origin.z + dir.z * t;
            int bx = (int)Math.floor(p.x);
            int by = (int)Math.floor(p.y);
            int bz = (int)Math.floor(p.z);
            String key = bx+","+by+","+bz;
            if (ws != null && ws.state.blocks.containsKey(key)) {
                // compute normal approx by neighbor empty check
                int nx=0,ny=0,nz=0;
                if (!ws.state.blocks.containsKey((bx+1)+","+by+","+bz)) nx=1;
                else if (!ws.state.blocks.containsKey((bx-1)+","+by+","+bz)) nx=-1;
                else if (!ws.state.blocks.containsKey(bx+","+(by+1)+","+bz)) ny=1;
                else if (!ws.state.blocks.containsKey(bx+","+(by-1)+","+bz)) ny=-1;
                else if (!ws.state.blocks.containsKey(bx+","+by+","+(bz+1))) nz=1;
                else if (!ws.state.blocks.containsKey(bx+","+by+","+(bz-1))) nz=-1;
                Hit h = new Hit();
                h.x=bx; h.y=by; h.z=bz; h.blockKey=key; h.nx=nx; h.ny=ny; h.nz=nz; return h;
            }
            if (p.y <= 0.0f) {
                Hit h = new Hit(); h.ground=true; h.gx=(int)Math.floor(p.x); h.gz=(int)Math.floor(p.z); return h;
            }
        }
        return null;
    }
}
