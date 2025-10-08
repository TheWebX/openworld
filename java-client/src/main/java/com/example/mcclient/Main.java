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
import java.util.HashMap;
import java.util.Map;

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
    private float deathOverlayAlpha = 0f;
    private int winWidth = 1280, winHeight = 720;
    private final Map<String, Rig> playerRigMap = new HashMap<>();
    private final Map<String, Rig> npcRigMap = new HashMap<>();

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
        window = GLFW.glfwCreateWindow(winWidth, winHeight, "MC Java Client", MemoryUtil.NULL, MemoryUtil.NULL);
        if (window == MemoryUtil.NULL) throw new RuntimeException("Failed to create window");
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        GLFW.glfwSwapInterval(1);

        GLFW.glfwSetWindowSize(window, winWidth, winHeight);
        GL11.glViewport(0, 0, winWidth, winHeight);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);
        // Basic perspective projection
        float fov = 70f;
        float aspect = (float) winWidth / (float) winHeight;
        float zNear = 0.1f, zFar = 1000f;
        float top = (float) Math.tan(Math.toRadians(fov * 0.5)) * zNear;
        float right = top * aspect;
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glFrustum(-right, right, -top, top, zNear, zFar);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GLFW.glfwSetWindowSizeCallback(window, (w,newW,newH)->{
            winWidth = newW; winHeight = newH;
            GL11.glViewport(0,0,winWidth,winHeight);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            float asp = (float)winWidth/(float)winHeight;
            float top2 = (float) Math.tan(Math.toRadians(70f * 0.5)) * 0.1f;
            float right2 = top2 * asp;
            GL11.glFrustum(-right2, right2, -top2, top2, 0.1f, 1000f);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        });

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
                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    pointerLocked = false;
                    GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
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
            // camera transform based on local player (first-person)
            GL11.glLoadIdentity();
            GL11.glRotatef((float) Math.toDegrees(pitch), 1f, 0f, 0f);
            GL11.glRotatef((float) Math.toDegrees(yaw), 0f, 1f, 0f);
            Vector3f cam = getCameraPosition();
            GL11.glTranslatef(-cam.x, -cam.y, -cam.z);
            // simple ground
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor3f(0.42f, 0.66f, 0.31f);
            GL11.glVertex3f(-50, 0, -50);
            GL11.glVertex3f(50, 0, -50);
            GL11.glVertex3f(50, 0, 50);
            GL11.glVertex3f(-50, 0, 50);
            GL11.glEnd();

            // draw blocks and entities
            if (ws != null && ws.state != null) {
                // opaque blocks first
                for (var e : ws.state.blocks.entrySet()) {
                    int t = e.getValue(); if (t==6 || t==8) continue;
                    String[] parts = e.getKey().split(",");
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    drawCube(x, y, z, t);
                }
                // transparent
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                for (var e : ws.state.blocks.entrySet()) {
                    int t = e.getValue(); if (!(t==6 || t==8)) continue;
                    String[] parts = e.getKey().split(",");
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    drawCube(x, y, z, t);
                }
                GL11.glDisable(GL11.GL_BLEND);

                // players rigs with walk animation
                long now = System.currentTimeMillis();
                for (var p : ws.state.players) {
                    if (ws.state.myId != null && ws.state.myId.equals(p.id)) continue;
                    Rig rig = playerRigMap.computeIfAbsent(p.id, k -> new Rig());
                    float swing = rig.update((float)p.x, (float)p.z, now);
                    drawHumanRigAnimatedYawPitch((float)p.x, (float)p.y, (float)p.z, 0.99f, 0.66f, 0.55f, swing, (float)p.yaw, (float)p.pitch, shotIntensity(p.id));
                }
                // NPC rigs
                for (var n : ws.state.npcs) {
                    Rig rig = npcRigMap.computeIfAbsent(n.id, k -> new Rig());
                    float swing = rig.update((float)n.x, (float)n.z, now);
                    boolean police = "police".equals(n.kind);
                    float r= police?0.2f:0.6f, g= police?0.4f:0.7f, b= police?0.9f:0.5f;
                    drawHumanRigAnimatedYawPitch((float)n.x, (float)n.y, (float)n.z, r,g,b, swing, 0f, 0f, shotIntensity(n.id));
                }
            }

            // first-person hands + gun (simple boxes)
            if (selectedType == 6 && pointerLocked) {
                drawFPHandsGun();
            }

            // 2D HUD overlay (hotbar, crosshair, pause, death)
            drawHUD();

            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();

            // send inputs ~30Hz
            if (!isDead() && pointerLocked && ws != null && ws.isOpen()) {
                double ax = Math.sin(yaw) * moveZ + Math.cos(yaw) * moveX;
                double az = Math.cos(yaw) * moveZ - Math.sin(yaw) * moveX;
                String json = String.format("{\"type\":\"input\",\"input\":{\"ax\":%.3f,\"az\":%.3f,\"jump\":%s,\"sprint\":%s,\"yaw\":%.3f,\"pitch\":%.3f}}",
                        ax, az, jump?"true":"false", sprint?"true":"false", yaw, pitch);
                outgoing.offer(json);
            }

            // handle clicks (debounced)
            long nowMs = System.currentTimeMillis();
            if (!isDead() && pointerLocked && (leftMouseDown || rightMouseDown) && nowMs - lastClickMs > 120) {
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
            if (muzzleFlashTimer > 0f) { muzzleFlashTimer -= 0.016f; }
            // death overlay fade
            if (ws != null && ws.state != null && ws.state.dead) {
                deathOverlayAlpha = Math.min(1f, deathOverlayAlpha + 0.05f);
                pointerLocked = false;
                GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                // allow click to respawn
                if (leftMouseDown) {
                    outgoing.offer("{\"type\":\"respawn\"}");
                    ws.state.dead = false;
                    deathOverlayAlpha = 0f;
                }
            }
        }
    }

    private void drawCube(int x, int y, int z, int type) {
        float fx = x + 0.5f, fy = y + 0.5f, fz = z + 0.5f;
        float s = 0.5f;
        GL11.glPushMatrix();
        GL11.glTranslatef(fx, fy, fz);
        // color per type
        float[] base = switch (type) {
            case 1 -> new float[]{0.47f,0.47f,0.47f};
            case 2 -> new float[]{0.55f,0.35f,0.17f};
            case 3 -> new float[]{0.25f,0.66f,0.25f};
            case 4 -> new float[]{0.6f,0.4f,0.2f};
            case 5 -> new float[]{0.76f,0.7f,0.5f};
            case 6 -> new float[]{0.23f,0.65f,1f};
            default -> new float[]{0.8f,0.8f,0.8f};
        };
        // per-face slight noise to mimic JS texture
        float n1 = noise(x,y,z)*0.08f, n2=noise(x+1,y,z)*0.08f, n3=noise(x,y+1,z)*0.08f, n4=noise(x,y,z+1)*0.08f;
        GL11.glBegin(GL11.GL_QUADS);
        // +Y
        GL11.glColor3f(clamp(base[0]+n1),clamp(base[1]+n1),clamp(base[2]+n1));
        GL11.glVertex3f(-s, s, -s); GL11.glVertex3f( s, s, -s); GL11.glVertex3f( s, s,  s); GL11.glVertex3f(-s, s,  s);
        // -Y
        GL11.glColor3f(clamp(base[0]+n2),clamp(base[1]+n2),clamp(base[2]+n2));
        GL11.glVertex3f(-s,-s, s); GL11.glVertex3f( s,-s, s); GL11.glVertex3f( s,-s,-s); GL11.glVertex3f(-s,-s,-s);
        // +X
        GL11.glColor3f(clamp(base[0]+n3),clamp(base[1]+n3),clamp(base[2]+n3));
        GL11.glVertex3f( s,-s,-s); GL11.glVertex3f( s, s,-s); GL11.glVertex3f( s, s, s); GL11.glVertex3f( s,-s, s);
        // -X
        GL11.glColor3f(clamp(base[0]+n4),clamp(base[1]+n4),clamp(base[2]+n4));
        GL11.glVertex3f(-s,-s, s); GL11.glVertex3f(-s, s, s); GL11.glVertex3f(-s, s,-s); GL11.glVertex3f(-s,-s,-s);
        // +Z
        GL11.glColor3f(clamp(base[0]+n2),clamp(base[1]+n2),clamp(base[2]+n2));
        GL11.glVertex3f(-s,-s, s); GL11.glVertex3f( s,-s, s); GL11.glVertex3f( s, s, s); GL11.glVertex3f(-s, s, s);
        // -Z
        GL11.glColor3f(clamp(base[0]+n3),clamp(base[1]+n3),clamp(base[2]+n3));
        GL11.glVertex3f( s,-s,-s); GL11.glVertex3f(-s,-s,-s); GL11.glVertex3f(-s, s,-s); GL11.glVertex3f( s, s,-s);
        GL11.glEnd();
        GL11.glPopMatrix();
    }

    private void drawHumanRigAnimated(float x, float y, float z, float r, float g, float b, float swing) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, z);
        // legs
        float legW=0.11f, legH=0.8f, legD=0.11f, legOffX=legW+0.05f;
        GL11.glColor3f(r*0.5f,g*0.5f,b*0.5f);
        // left leg
        GL11.glPushMatrix();
        GL11.glTranslatef(-legOffX, legH/2f, 0);
        GL11.glRotatef((float)Math.toDegrees(swing), 1,0,0);
        box(legW,legH,legD);
        GL11.glPopMatrix();
        // right leg
        GL11.glPushMatrix();
        GL11.glTranslatef(legOffX, legH/2f, 0);
        GL11.glRotatef((float)Math.toDegrees(-swing), 1,0,0);
        box(legW,legH,legD);
        GL11.glPopMatrix();
        // body
        GL11.glColor3f(r,g,b);
        GL11.glPushMatrix();
        GL11.glTranslatef(0, legH + 0.4f, 0);
        box(0.25f,0.8f,0.14f);
        GL11.glPopMatrix();
        // arms
        float armW=0.09f, armH=0.8f, armD=0.09f, armOffX=0.25f+armW;
        GL11.glColor3f(1.0f, 0.84f, 0.7f);
        GL11.glPushMatrix();
        GL11.glTranslatef(-armOffX, legH + armH/2f, 0);
        GL11.glRotatef((float)Math.toDegrees(-swing*0.8f), 1,0,0);
        box(armW,armH,armD);
        GL11.glPopMatrix();
        GL11.glPushMatrix();
        GL11.glTranslatef(armOffX, legH + armH/2f, 0);
        GL11.glRotatef((float)Math.toDegrees(swing*0.8f), 1,0,0);
        box(armW,armH,armD);
        GL11.glPopMatrix();
        // head
        GL11.glColor3f(1.0f, 0.84f, 0.7f);
        GL11.glPushMatrix();
        GL11.glTranslatef(0, legH + 0.8f + 0.1f, 0);
        box(0.25f,0.2f,0.25f);
        GL11.glPopMatrix();
        GL11.glPopMatrix();
    }

    // Variant that applies yaw/pitch and draws a simple gun with brief muzzle flash when shooting
    private void drawHumanRigAnimatedYawPitch(float x, float y, float z, float r, float g, float b, float swing, float yawRad, float pitchRad, float shot) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, z);
        GL11.glRotatef((float)Math.toDegrees(yawRad), 0,1,0);
        // legs
        float legW=0.11f, legH=0.8f, legD=0.11f, legOffX=legW+0.05f;
        GL11.glColor3f(r*0.5f,g*0.5f,b*0.5f);
        // left leg
        GL11.glPushMatrix();
        GL11.glTranslatef(-legOffX, legH/2f, 0);
        GL11.glRotatef((float)Math.toDegrees(swing), 1,0,0);
        box(legW,legH,legD);
        GL11.glPopMatrix();
        // right leg
        GL11.glPushMatrix();
        GL11.glTranslatef(legOffX, legH/2f, 0);
        GL11.glRotatef((float)Math.toDegrees(-swing), 1,0,0);
        box(legW,legH,legD);
        GL11.glPopMatrix();
        // body
        GL11.glColor3f(r,g,b);
        GL11.glPushMatrix();
        GL11.glTranslatef(0, legH + 0.4f, 0);
        box(0.25f,0.8f,0.14f);
        GL11.glPopMatrix();
        // arms (aiming when shot>0)
        float armW=0.09f, armH=0.8f, armD=0.09f, armOffX=0.25f+armW;
        GL11.glColor3f(1.0f, 0.84f, 0.7f);
        // left arm
        GL11.glPushMatrix();
        GL11.glTranslatef(-armOffX, legH + armH/2f, 0);
        float aimPitchDeg = (float)Math.toDegrees(pitchRad*0.5f);
        if (shot > 0f) GL11.glRotatef(-aimPitchDeg, 1,0,0);
        else GL11.glRotatef((float)Math.toDegrees(-swing*0.8f), 1,0,0);
        box(armW,armH,armD);
        GL11.glPopMatrix();
        // right arm + gun
        GL11.glPushMatrix();
        GL11.glTranslatef(armOffX, legH + armH/2f, 0);
        if (shot > 0f) GL11.glRotatef(aimPitchDeg, 1,0,0);
        else GL11.glRotatef((float)Math.toDegrees(swing*0.8f), 1,0,0);
        box(armW,armH,armD);
        if (shot > 0f) {
            // simple gun box at hand
            GL11.glColor3f(0.2f,0.2f,0.2f);
            GL11.glPushMatrix();
            GL11.glTranslatef(0.0f, -armH/2f+0.06f, -0.12f);
            box(0.12f,0.06f,0.25f);
            // muzzle flash as bright quad
            GL11.glColor4f(1f,0.95f,0.6f, shot);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex3f(-0.05f, 0.02f, -0.24f); GL11.glVertex3f(0.05f, 0.02f, -0.24f);
            GL11.glVertex3f(0.05f,-0.02f, -0.24f); GL11.glVertex3f(-0.05f,-0.02f, -0.24f);
            GL11.glEnd();
            GL11.glPopMatrix();
        }
        GL11.glPopMatrix();
        // head (pitch)
        GL11.glColor3f(1.0f, 0.84f, 0.7f);
        GL11.glPushMatrix();
        GL11.glTranslatef(0, legH + 0.8f + 0.1f, 0);
        GL11.glRotatef((float)Math.toDegrees(pitchRad*0.5f), 1,0,0);
        box(0.25f,0.2f,0.25f);
        GL11.glPopMatrix();
        GL11.glPopMatrix();
    }

    private boolean recentlyShot(String id) {
        if (ws == null || ws.state == null) return false;
        Long t = ws.state.lastShotById.get(id);
        if (t == null) return false;
        return (System.currentTimeMillis() - t) < 150;
    }

    private float shotIntensity(String id) {
        if (ws == null || ws.state == null) return 0f;
        Long t = ws.state.lastShotById.get(id);
        if (t == null) return 0f;
        long dt = System.currentTimeMillis() - t;
        if (dt >= 150) return 0f;
        return 1f - (dt / 150f);
    }

    private void box(float w,float h,float d){
        float sX=w, sY=h, sZ=d;
        GL11.glBegin(GL11.GL_QUADS);
        // top
        GL11.glVertex3f(-sX, sY, -sZ); GL11.glVertex3f(sX, sY, -sZ); GL11.glVertex3f(sX, sY, sZ); GL11.glVertex3f(-sX, sY, sZ);
        GL11.glVertex3f(-sX, 0, sZ); GL11.glVertex3f(sX, 0, sZ); GL11.glVertex3f(sX, 0, -sZ); GL11.glVertex3f(-sX, 0, -sZ);
        GL11.glVertex3f(sX,0,-sZ); GL11.glVertex3f(sX,sY,-sZ); GL11.glVertex3f(sX,sY,sZ); GL11.glVertex3f(sX,0,sZ);
        GL11.glVertex3f(-sX,0,sZ); GL11.glVertex3f(-sX,sY,sZ); GL11.glVertex3f(-sX,sY,-sZ); GL11.glVertex3f(-sX,0,-sZ);
        GL11.glVertex3f(-sX,0,sZ); GL11.glVertex3f(sX,0,sZ); GL11.glVertex3f(sX,sY,sZ); GL11.glVertex3f(-sX,sY,sZ);
        GL11.glVertex3f(sX,0,-sZ); GL11.glVertex3f(-sX,0,-sZ); GL11.glVertex3f(-sX,sY,-sZ); GL11.glVertex3f(sX,sY,-sZ);
        GL11.glEnd();
    }

    private static class Rig {
        float lastX=Float.NaN, lastZ=Float.NaN, phase=0f; long lastT=0;
        float update(float x,float z,long now){
            if (Float.isNaN(lastX)) { lastX=x; lastZ=z; lastT=now; return 0f; }
            float dx = x-lastX, dz = z-lastZ; float dist = (float)Math.sqrt(dx*dx+dz*dz);
            float dt = Math.max(0.001f, (now-lastT)/1000f);
            float speed = Math.min(1f, dist/(dt*5f));
            phase += speed*dt*6f;
            lastX=x; lastZ=z; lastT=now;
            return (float)Math.sin(phase)*0.6f*speed;
        }
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
                    return new Vector3f((float)p.x, (float)(p.y + 1.6f), (float)p.z);
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

    // 2D HUD drawing
    private void begin2D() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, winWidth, 0, winHeight, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
    }
    private void end2D() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }
    private void rect(float x,float y,float w,float h, float r,float g,float b,float a) {
        GL11.glColor4f(r,g,b,a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x,y); GL11.glVertex2f(x+w,y); GL11.glVertex2f(x+w,y+h); GL11.glVertex2f(x,y+h);
        GL11.glEnd();
    }
    private void drawHUD() {
        begin2D();
        // crosshair (only in gun mode)
        if (!isDead() && selectedType == 6 && pointerLocked) {
            float cx = winWidth*0.5f, cy = winHeight*0.5f;
            rect(cx-5, cy-1, 10, 2, 1,1,1,1);
            rect(cx-1, cy-5, 2, 10, 1,1,1,1);
        }
        // hotbar
        float slotW = 44, slotH = 44, gap = 8;
        int slots = 6;
        float totalW = slots*slotW + (slots-1)*gap;
        float x0 = (winWidth-totalW)/2f, y0 = 24;
        for (int i=0;i<slots;i++) {
            int idx = i+1;
            boolean sel = (selectedType == idx);
            rect(x0 + i*(slotW+gap), y0, slotW, slotH, sel?0.27f:0.13f, sel?0.27f:0.13f, sel?0.27f:0.13f, 0.7f);
        }
        // pause hint
        if (!isDead() && !pointerLocked) {
            rect(0,0,winWidth,winHeight, 0,0,0,0.3f);
            // simple center box
            float bw=260,bh=60; rect((winWidth-bw)/2f,(winHeight-bh)/2f,bw,bh,0.1f,0.1f,0.1f,0.9f);
        }
        // death overlay
        if (deathOverlayAlpha > 0f) {
            rect(0,0,winWidth,winHeight, 0,0,0, 0.5f * deathOverlayAlpha);
            // simple center box for text background
            float bw=320,bh=100; rect((winWidth-bw)/2f,(winHeight-bh)/2f,bw,bh,0.05f,0.05f,0.05f, 0.9f * deathOverlayAlpha);
            // simple white rectangle representing text area (no text rendering here)
            rect((winWidth-bw)/2f+20,(winHeight-bh)/2f+bh/2f-2, bw-40, 4, 1,1,1, deathOverlayAlpha);
        }
        end2D();
    }

    private boolean isDead() {
        return ws != null && ws.state != null && ws.state.dead;
    }

    private float clamp(float v){ return Math.max(0f, Math.min(1f, v)); }
    private float noise(int x,int y,int z){
        int n = x*73856093 ^ y*19349663 ^ z*83492791;
        n ^= (n<<13);
        return ((n * (n*n*15731 + 789221) + 1376312589) & 0x7fffffff)/ (float)0x7fffffff - 0.5f;
    }

    private void drawFPHandsGun() {
        Vector3f cam = getCameraPosition();
        GL11.glPushMatrix();
        GL11.glTranslatef(cam.x, cam.y, cam.z);
        GL11.glRotatef((float) Math.toDegrees(yaw), 0f,1f,0f);
        GL11.glRotatef((float) Math.toDegrees(pitch), 1f,0f,0f);
        GL11.glTranslatef(0.35f, -0.35f, -0.8f - muzzleFlashTimer*2f);
        // arm
        GL11.glColor3f(1.0f, 0.84f, 0.7f);
        GL11.glBegin(GL11.GL_QUADS);
        float aW=0.06f,aH=0.2f,aD=0.06f;
        // simple arm cube
        // top
        GL11.glVertex3f(-aW, aH, -aD); GL11.glVertex3f(aW, aH, -aD); GL11.glVertex3f(aW, aH, aD); GL11.glVertex3f(-aW, aH, aD);
        GL11.glVertex3f(-aW, 0, aD); GL11.glVertex3f(aW, 0, aD); GL11.glVertex3f(aW, 0, -aD); GL11.glVertex3f(-aW, 0, -aD);
        GL11.glVertex3f(aW,0,-aD); GL11.glVertex3f(aW,aH,-aD); GL11.glVertex3f(aW,aH,aD); GL11.glVertex3f(aW,0,aD);
        GL11.glVertex3f(-aW,0,aD); GL11.glVertex3f(-aW,aH,aD); GL11.glVertex3f(-aW,aH,-aD); GL11.glVertex3f(-aW,0,-aD);
        GL11.glVertex3f(-aW,0,aD); GL11.glVertex3f(aW,0,aD); GL11.glVertex3f(aW,aH,aD); GL11.glVertex3f(-aW,aH,aD);
        GL11.glVertex3f(aW,0,-aD); GL11.glVertex3f(-aW,0,-aD); GL11.glVertex3f(-aW,aH,-aD); GL11.glVertex3f(aW,aH,-aD);
        GL11.glEnd();
        // gun
        GL11.glColor3f(0.2f,0.2f,0.2f);
        GL11.glBegin(GL11.GL_QUADS);
        float gW=0.12f,gH=0.06f,gD=0.25f;
        GL11.glVertex3f(-gW, gH, -gD); GL11.glVertex3f(gW, gH, -gD); GL11.glVertex3f(gW, gH, gD); GL11.glVertex3f(-gW, gH, gD);
        GL11.glVertex3f(-gW, 0, gD); GL11.glVertex3f(gW, 0, gD); GL11.glVertex3f(gW, 0, -gD); GL11.glVertex3f(-gW, 0, -gD);
        GL11.glVertex3f(gW,0,-gD); GL11.glVertex3f(gW,gH,-gD); GL11.glVertex3f(gW,gH,gD); GL11.glVertex3f(gW,0,gD);
        GL11.glVertex3f(-gW,0,gD); GL11.glVertex3f(-gW,gH,gD); GL11.glVertex3f(-gW,gH,-gD); GL11.glVertex3f(-gW,0,-gD);
        GL11.glVertex3f(-gW,0,gD); GL11.glVertex3f(gW,0,gD); GL11.glVertex3f(gW,gH,gD); GL11.glVertex3f(-gW,gH,gD);
        GL11.glVertex3f(gW,0,-gD); GL11.glVertex3f(-gW,0,-gD); GL11.glVertex3f(-gW,gH,-gD); GL11.glVertex3f(gW,gH,-gD);
        GL11.glEnd();
        GL11.glPopMatrix();
    }
}
