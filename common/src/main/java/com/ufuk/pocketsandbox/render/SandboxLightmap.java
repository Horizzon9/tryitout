package com.ufuk.pocketsandbox.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * The scene's own 16x16 light map (x = block light, y = sky light), so the scene can be bright noon or pitch
 * night regardless of the real world's time. While {@link #activate()} is in effect the LightTexture mixin
 * binds this texture instead of the world's one.
 *
 * <p>Rule 4: owned by the renderer instance and closed with it; only the active texture id is static, and
 * it is cleared in {@code finally}.
 */
public final class SandboxLightmap implements AutoCloseable {
    private static final int SIZE = 16;
    private static int activeId = -1;

    private final DynamicTexture texture;
    /** 1 = full day, 0 = no sky light at all. */
    private float daylight = 1.0F;
    private boolean dirty = true;

    public SandboxLightmap() {
        this.texture = new DynamicTexture(SIZE, SIZE, false);
    }

    /** Texture id the mixin should bind, or -1 when the real world's lightmap applies. */
    public static int activeId() {
        return activeId;
    }

    public void setDaylight(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        if (clamped != daylight) {
            daylight = clamped;
            dirty = true;
        }
    }

    public float daylight() {
        return daylight;
    }

    public void activate() {
        if (dirty) {
            upload();
        }
        activeId = texture.getId();
    }

    public static void deactivate() {
        activeId = -1;
    }

    private void upload() {
        NativeImage pixels = texture.getPixels();
        if (pixels == null) {
            return;
        }
        float gamma = Minecraft.getInstance().options.gamma().get().floatValue();
        for (int sky = 0; sky < SIZE; sky++) {
            for (int block = 0; block < SIZE; block++) {
                pixels.setPixelRGBA(block, sky, LightCurve.abgr(block, sky, daylight, gamma));
            }
        }
        texture.upload();
        texture.setFilter(true, false);
        dirty = false;
    }

    @Override
    public void close() {
        if (activeId == texture.getId()) {
            activeId = -1;
        }
        texture.close();
    }
}
