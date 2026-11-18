package com.ufuk.pocketsandbox.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Global render state the scene pass changes, captured before and restored in {@code finally}: projection,
 * vertex sorting, fog, shader colour, bound frame buffer / viewport, GUI lighting and depth/blend/cull.
 */
public final class RenderStateSnapshot {
    private final Matrix4f projection;
    private final VertexSorting sorting;
    private final float fogStart;
    private final float fogEnd;
    private final FogShape fogShape;
    private final float[] shaderColor;

    private RenderStateSnapshot() {
        this.projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        this.sorting = RenderSystem.getVertexSorting();
        this.fogStart = RenderSystem.getShaderFogStart();
        this.fogEnd = RenderSystem.getShaderFogEnd();
        this.fogShape = RenderSystem.getShaderFogShape();
        this.shaderColor = RenderSystem.getShaderColor().clone();
    }

    public static RenderStateSnapshot capture() {
        return new RenderStateSnapshot();
    }

    public void restore() {
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        RenderSystem.setProjectionMatrix(projection, sorting);
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
        RenderSystem.setShaderFogShape(fogShape);
        RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
        Lighting.setupFor3DItems();
        // GUI defaults.
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        GlStateManager._polygonOffset(0.0F, 0.0F);
        RenderSystem.disablePolygonOffset();
    }
}
