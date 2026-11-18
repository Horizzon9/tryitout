package com.ufuk.pocketsandbox.scene;

/**
 * Orbit camera: yaw/pitch in degrees, distance in blocks. It turns around a pivot that panning moves (and a
 * middle click can put on a block), so after moving away from the centre you still turn around what you look at.
 * Pure math (no Minecraft classes) so it is unit tested.
 */
public final class SandboxCamera {
    public static final float DEFAULT_YAW = 225.0F;
    public static final float DEFAULT_PITCH = 30.0F;
    public static final float MIN_PITCH = -89.0F;
    public static final float MAX_PITCH = 89.0F;
    public static final float MIN_DISTANCE = 2.0F;
    public static final float MAX_DISTANCE = 200.0F;
    private static final float ORBIT_DEGREES_PER_PIXEL = 0.5F;
    private static final float ZOOM_STEP = 0.9F;

    private float yaw;
    private float pitch;
    private float distance;
    /** Pivot, offset from the scene centre in scene blocks. */
    private float pivotX;
    private float pivotY;
    private float pivotZ;
    private float defaultDistance;

    public SandboxCamera(float defaultDistance) {
        this.defaultDistance = clampDistance(defaultDistance);
        reset();
    }

    /** Default distance that frames a floor of {@code size} blocks. */
    public static float distanceFor(int size) {
        return size * 1.6F + 3.0F;
    }

    public void reset() {
        yaw = DEFAULT_YAW;
        pitch = DEFAULT_PITCH;
        distance = defaultDistance;
        pivotX = 0.0F;
        pivotY = 0.0F;
        pivotZ = 0.0F;
    }

    public void setDefaultDistance(float value) {
        defaultDistance = clampDistance(value);
    }

    /** Mouse drag in pixels: horizontal turns, vertical tilts. */
    public void orbit(double dx, double dy) {
        yaw = wrap(yaw + (float) dx * ORBIT_DEGREES_PER_PIXEL);
        pitch = clamp(pitch + (float) dy * ORBIT_DEGREES_PER_PIXEL, MIN_PITCH, MAX_PITCH);
    }

    /** Mouse drag in pixels; {@code pixelsPerBlock} is how big one block is on screen at this zoom. */
    public void pan(double dx, double dy, double pixelsPerBlock) {
        if (pixelsPerBlock <= 0) {
            return;
        }
        // The pivot moves along the screen's right / up directions (in the scene), against the drag.
        float right = (float) (dx / pixelsPerBlock);
        float up = (float) (dy / pixelsPerBlock);
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        float sinYaw = (float) Math.sin(yawRad);
        float cosYaw = (float) Math.cos(yawRad);
        float sinPitch = (float) Math.sin(pitchRad);
        float cosPitch = (float) Math.cos(pitchRad);
        pivotX += -right * cosYaw + up * sinPitch * sinYaw;
        pivotY += up * cosPitch;
        pivotZ += -right * sinYaw - up * sinPitch * cosYaw;
    }

    /** Turn around this point from now on (offset from the scene centre). */
    public void setPivot(float x, float y, float z) {
        pivotX = x;
        pivotY = y;
        pivotZ = z;
    }

    /** Positive wheel = closer. */
    public void zoom(double wheel) {
        if (wheel == 0) {
            return;
        }
        float factor = (float) Math.pow(ZOOM_STEP, wheel);
        distance = clampDistance(distance * factor);
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public float distance() {
        return distance;
    }

    public float pivotX() {
        return pivotX;
    }

    public float pivotY() {
        return pivotY;
    }

    public float pivotZ() {
        return pivotZ;
    }

    static float wrap(float degrees) {
        float value = degrees % 360.0F;
        return value < 0 ? value + 360.0F : value;
    }

    private static float clampDistance(float value) {
        return clamp(value, MIN_DISTANCE, MAX_DISTANCE);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
