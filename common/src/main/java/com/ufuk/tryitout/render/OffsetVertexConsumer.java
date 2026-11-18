package com.ufuk.tryitout.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Shifts positions by a fixed offset. The liquid renderer writes section-relative coordinates
 * ({@code pos & 15}); this moves them back to scene coordinates.
 */
final class OffsetVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float dx;
    private final float dy;
    private final float dz;

    OffsetVertexConsumer(VertexConsumer delegate, float dx, float dy, float dz) {
        this.delegate = delegate;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x + dx, y + dy, z + dz);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        delegate.setColor(r, g, b, a);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }
}
