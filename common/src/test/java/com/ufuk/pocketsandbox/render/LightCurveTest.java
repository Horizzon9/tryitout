package com.ufuk.pocketsandbox.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LightCurveTest {
    @Test
    void brightnessCurveEndsAtZeroAndOne() {
        assertEquals(0.0F, LightCurve.brightness(0), 1e-6F);
        assertEquals(1.0F, LightCurve.brightness(15), 1e-6F);
        assertTrue(LightCurve.brightness(8) < 8 / 15.0F, "vanilla curve is below linear");
    }

    @Test
    void fullSkyIsBrightAndNightIsDark() {
        float[] noon = LightCurve.rgb(0, 15, 1.0F, 0.5F);
        float[] night = LightCurve.rgb(0, 15, 0.0F, 0.5F);
        assertTrue(noon[0] > 0.95F);
        assertTrue(night[0] < 0.1F);
    }

    @Test
    void torchLightIsWarm() {
        // Mid level: near 15 all channels saturate to white, as in vanilla.
        float[] torch = LightCurve.rgb(7, 0, 0.0F, 0.0F);
        assertTrue(torch[0] > torch[1] && torch[1] > torch[2], "red > green > blue");
    }

    @Test
    void moreLightIsNeverDarker() {
        for (int sky = 0; sky < 15; sky++) {
            float[] a = LightCurve.rgb(5, sky, 1.0F, 0.5F);
            float[] b = LightCurve.rgb(5, sky + 1, 1.0F, 0.5F);
            assertTrue(b[0] >= a[0]);
        }
    }

    @Test
    void packedAsOpaqueAbgr() {
        int packed = LightCurve.abgr(0, 15, 1.0F, 0.5F);
        assertEquals(0xFF, packed >>> 24);
    }
}
