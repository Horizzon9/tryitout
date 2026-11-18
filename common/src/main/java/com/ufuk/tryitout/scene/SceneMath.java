package com.ufuk.tryitout.scene;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Camera matrices shared by the renderer and the mouse picker, so what is clicked is what is drawn.
 * Scene space: block (0,1,0) spans 0..1; the orbit centre is (0.5, centerY, 0.5).
 */
public final class SceneMath {
    public static final float FOV_DEGREES = 50.0F;
    public static final float NEAR = 0.05F;
    public static final float FAR = 450.0F;

    private SceneMath() {
    }

    public static Matrix4f view(SandboxCamera camera, double centerY) {
        return new Matrix4f()
                .translate(0.0F, 0.0F, -camera.distance())
                .rotateX((float) Math.toRadians(camera.pitch()))
                .rotateY((float) Math.toRadians(camera.yaw()))
                .translate(-0.5F - camera.pivotX(), (float) -centerY - camera.pivotY(), -0.5F - camera.pivotZ());
    }

    public static Matrix4f projection(float aspect) {
        return new Matrix4f().setPerspective((float) Math.toRadians(FOV_DEGREES), aspect, NEAR, FAR);
    }

    /** Camera position in scene space. */
    public static Vector3f eye(Matrix4f view) {
        return new Matrix4f(view).invert().transformPosition(new Vector3f());
    }

    /** Pixels per block at the orbit centre, for panning. */
    public static double pixelsPerBlock(SandboxCamera camera, int viewHeight) {
        double visible = 2.0 * camera.distance() * Math.tan(Math.toRadians(FOV_DEGREES) / 2.0);
        return viewHeight / visible;
    }

    /**
     * Ray through a point of the view rectangle: {@code [origin, end]} in scene space. {@code fx, fy} are the
     * point's position in the rectangle, 0..1 from the top-left.
     */
    public static Vector3f[] ray(Matrix4f view, Matrix4f projection, double fx, double fy) {
        float ndcX = (float) (fx * 2.0 - 1.0);
        float ndcY = (float) (1.0 - fy * 2.0);
        Matrix4f inverse = new Matrix4f(projection).mul(view).invert();
        Vector3f near = unproject(inverse, ndcX, ndcY, -1.0F);
        Vector3f far = unproject(inverse, ndcX, ndcY, 1.0F);
        Vector3f direction = new Vector3f(far).sub(near).normalize();
        Vector3f end = new Vector3f(near).add(new Vector3f(direction).mul(400.0F));
        return new Vector3f[] {near, end};
    }

    private static Vector3f unproject(Matrix4f inverse, float x, float y, float z) {
        Vector4f v = inverse.transform(new Vector4f(x, y, z, 1.0F));
        return new Vector3f(v.x / v.w, v.y / v.w, v.z / v.w);
    }
}
