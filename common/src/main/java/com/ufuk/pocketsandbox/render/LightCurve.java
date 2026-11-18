package com.ufuk.pocketsandbox.render;

/**
 * Colour of one light map texel, modelled on vanilla's overworld curve. Pure math, unit tested.
 */
public final class LightCurve {
    private static final float AMBIENT_FLOOR = 0.04F;

    private LightCurve() {
    }

    /** Vanilla's per-level brightness curve (ambient light 0). */
    static float brightness(int level) {
        float f = level / 15.0F;
        return f / (4.0F - 3.0F * f);
    }

    /** RGB in 0..1 for {@code block} / {@code sky} light, sky scaled by {@code daylight}, then gamma. */
    public static float[] rgb(int block, int sky, float daylight, float gamma) {
        float skyValue = brightness(sky) * daylight;
        float blockValue = brightness(block) * 1.5F;
        // Torch light is warm: less green and blue.
        float r = blockValue;
        float g = blockValue * ((blockValue * 0.6F + 0.4F) * 0.6F + 0.4F);
        float b = blockValue * (blockValue * blockValue * 0.6F + 0.4F);
        float[] color = {
            r + skyValue, g + skyValue, b + skyValue
        };
        for (int i = 0; i < 3; i++) {
            float c = Math.min(1.0F, color[i]);
            float notGamma = 1.0F - (float) Math.pow(1.0F - c, 4);
            c = c + (notGamma - c) * Math.max(0.0F, gamma);
            c = c + (0.75F - c) * AMBIENT_FLOOR;
            color[i] = Math.max(0.0F, Math.min(1.0F, c));
        }
        return color;
    }

    /** Packed as NativeImage expects (ABGR). */
    public static int abgr(int block, int sky, float daylight, float gamma) {
        float[] c = rgb(block, sky, daylight, gamma);
        int r = (int) (c[0] * 255.0F);
        int g = (int) (c[1] * 255.0F);
        int b = (int) (c[2] * 255.0F);
        return 0xFF000000 | b << 16 | g << 8 | r;
    }
}
