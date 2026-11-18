package com.ufuk.pocketsandbox.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SandboxCameraTest {
    private static final float EPS = 1e-4F;

    @Test
    void orbitWrapsYawAndClampsPitch() {
        SandboxCamera camera = new SandboxCamera(10);
        camera.orbit(400, 0); // +200 degrees
        assertEquals((SandboxCamera.DEFAULT_YAW + 200) % 360, camera.yaw(), EPS);
        camera.orbit(0, 10_000);
        assertEquals(SandboxCamera.MAX_PITCH, camera.pitch(), EPS);
        camera.orbit(0, -10_000);
        assertEquals(SandboxCamera.MIN_PITCH, camera.pitch(), EPS);
    }

    @Test
    void zoomStaysInRange() {
        SandboxCamera camera = new SandboxCamera(10);
        camera.zoom(1);
        assertTrue(camera.distance() < 10);
        for (int i = 0; i < 200; i++) {
            camera.zoom(1);
        }
        assertEquals(SandboxCamera.MIN_DISTANCE, camera.distance(), EPS);
        for (int i = 0; i < 200; i++) {
            camera.zoom(-1);
        }
        assertEquals(SandboxCamera.MAX_DISTANCE, camera.distance(), EPS);
    }

    @Test
    void panUsesPixelsPerBlockAndResetRestoresDefaults() {
        SandboxCamera camera = new SandboxCamera(10);
        camera.orbit(-SandboxCamera.DEFAULT_YAW * 2, -SandboxCamera.DEFAULT_PITCH * 2); // yaw 0, pitch 0
        camera.pan(20, 10, 10);
        assertEquals(-2.0F, camera.pivotX(), EPS);
        assertEquals(1.0F, camera.pivotY(), EPS);
        assertEquals(0.0F, camera.pivotZ(), EPS);
        camera.pan(5, 5, 0); // ignored
        assertEquals(-2.0F, camera.pivotX(), EPS);
        camera.orbit(50, 50);
        camera.zoom(3);
        camera.reset();
        assertEquals(SandboxCamera.DEFAULT_YAW, camera.yaw(), EPS);
        assertEquals(SandboxCamera.DEFAULT_PITCH, camera.pitch(), EPS);
        assertEquals(10.0F, camera.distance(), EPS);
        assertEquals(0.0F, camera.pivotX(), EPS);
    }

    @Test
    void wrapHandlesNegatives() {
        assertEquals(350.0F, SandboxCamera.wrap(-10.0F), EPS);
        assertEquals(10.0F, SandboxCamera.wrap(370.0F), EPS);
    }
}
