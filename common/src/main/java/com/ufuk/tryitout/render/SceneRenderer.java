package com.ufuk.tryitout.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.ufuk.tryitout.platform.SandboxPlatform;
import com.ufuk.tryitout.safety.FaultRegistry;
import com.ufuk.tryitout.safety.Safe;
import com.ufuk.tryitout.safety.SandboxGuard;
import com.ufuk.tryitout.scene.SandboxCamera;
import com.ufuk.tryitout.scene.SandboxScene;
import com.ufuk.tryitout.scene.SceneMath;
import com.ufuk.tryitout.world.SandboxLevel;
import com.ufuk.tryitout.world.SandboxWorld;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

/**
 * Renders a {@link SandboxScene} into its own off-screen frame buffer (own depth buffer, perspective
 * projection) and blits the result into the GUI. Nothing of the GUI's depth, scissor or projection leaks in
 * or out: everything the pass changes is restored from a {@link RenderStateSnapshot} in {@code finally}.
 *
 * <p>Rule 4 (VRAM): the frame buffer, the per-layer vertex buffers and the light map belong to this
 * instance; {@link #close()} frees them and {@link #ensureTarget} recreates the frame buffer on resize.
 */
public final class SceneRenderer implements AutoCloseable {
    private static final float RESORT_DISTANCE = 0.5F;
    private static final List<RenderType> OPAQUE_LAYERS =
            List.of(RenderType.solid(), RenderType.cutoutMipped(), RenderType.cutout());

    private final Map<RenderType, VertexBuffer> layers = new LinkedHashMap<>();
    private final SandboxLightmap lightmap = new SandboxLightmap();
    private final RandomSource random = RandomSource.create();
    @Nullable
    private TextureTarget target;
    private int builtVersion = -1;
    @Nullable
    private Vector3f translucentSortEye;
    private boolean closed;

    public SandboxLightmap lightmap() {
        return lightmap;
    }

    /** Renders the scene into the given GUI rectangle. Never throws for modded content. */
    public void render(GuiGraphics graphics, SandboxScene scene, SandboxCamera camera, int x, int y, int width,
            int height, float partialTick, @Nullable BlockHitResult hover) {
        if (closed || width <= 0 || height <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        int pixelWidth = Math.max(1, (int) Math.round(width * scale));
        int pixelHeight = Math.max(1, (int) Math.round(height * scale));
        graphics.flush();
        TextureTarget fbo = ensureTarget(pixelWidth, pixelHeight);

        Matrix4f view = SceneMath.view(camera, scene.world().bounds().centerY());
        Matrix4f projection = SceneMath.projection((float) pixelWidth / pixelHeight);
        Vector3f eye = SceneMath.eye(view);

        RenderStateSnapshot snapshot = RenderStateSnapshot.capture();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try (SandboxGuard.Scope ignored = SandboxGuard.enter()) {
            fbo.setClearColor(0.11F, 0.12F, 0.14F, 1.0F);
            fbo.clear(Minecraft.ON_OSX);
            fbo.bindWrite(true);
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            modelView.identity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
            RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            lightmap.activate();
            Lighting.setupLevel();

            rebuildIfNeeded(scene, eye);
            for (RenderType layer : OPAQUE_LAYERS) {
                drawLayer(layer, view, projection, fbo);
            }
            renderBlockEntities(scene, view, partialTick, fbo);
            WeaponPass.render(scene, view, partialTick, fbo);
            drawLayer(RenderType.translucent(), view, projection, fbo);
            drawLayer(RenderType.tripwire(), view, projection, fbo);
            scene.particles().render(view, eye, camera.yaw(), camera.pitch(), partialTick);
            fbo.bindWrite(false);
            if (hover != null) {
                renderOutline(scene, hover.getBlockPos(), view, fbo);
            }
        } finally {
            SandboxLightmap.deactivate();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            snapshot.restore();
        }
        blit(graphics, fbo, x, y, width, height);
    }

    // ---- frame buffer ----

    private TextureTarget ensureTarget(int width, int height) {
        if (target == null) {
            target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        } else if (target.width != width || target.height != height) {
            // resize() deletes the old GL objects before creating the new ones: no leak.
            target.resize(width, height, Minecraft.ON_OSX);
        }
        return target;
    }

    private static void blit(GuiGraphics graphics, TextureTarget fbo, int x, int y, int width, int height) {
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, fbo.getColorTextureId());
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        // Frame buffer textures start at the bottom-left: flip v.
        builder.addVertex(matrix, x, y + height, 0).setUv(0, 0);
        builder.addVertex(matrix, x + width, y + height, 0).setUv(1, 0);
        builder.addVertex(matrix, x + width, y, 0).setUv(1, 1);
        builder.addVertex(matrix, x, y, 0).setUv(0, 1);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.enableDepthTest();
    }

    // ---- block meshes ----

    private void rebuildIfNeeded(SandboxScene scene, Vector3f eye) {
        SandboxWorld world = scene.world();
        boolean changed = world.version() != builtVersion;
        boolean resort = translucentSortEye == null || translucentSortEye.distance(eye) > RESORT_DISTANCE;
        if (!changed && !(resort && layers.containsKey(RenderType.translucent()))) {
            return;
        }
        builtVersion = world.version();
        translucentSortEye = new Vector3f(eye);
        long start = System.nanoTime();
        buildMeshes(scene, eye);
        logSlowRebuild(world, (System.nanoTime() - start) / 1_000_000L);
    }

    /** Big floors (15x15, 25x25) rebuild every mesh on each change: report it when it gets slow. */
    private long lastSlowLog;

    private void logSlowRebuild(SandboxWorld world, long millis) {
        long now = System.currentTimeMillis();
        if (millis > SLOW_REBUILD_MS && now - lastSlowLog > 5000L) {
            lastSlowLog = now;
            com.ufuk.tryitout.TryItOutClient.LOGGER.info(
                    "TryItOut: mesh rebuild took {} ms ({} blocks, floor {}x{})", millis,
                    world.states().size(), world.bounds().size(), world.bounds().size());
        }
    }

    private static final long SLOW_REBUILD_MS = 15L;

    private void buildMeshes(SandboxScene scene, Vector3f eye) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        SandboxLevel level = scene.level();
        FaultRegistry faults = scene.faults();
        Map<RenderType, ByteBufferBuilder> memory = new HashMap<>();
        Map<RenderType, BufferBuilder> builders = new HashMap<>();
        try {
            for (Map.Entry<BlockPos, BlockState> entry : scene.world().states().entrySet()) {
                BlockPos pos = entry.getKey();
                BlockState state = entry.getValue();
                FluidState fluid = state.getFluidState();
                if (!fluid.isEmpty()) {
                    Safe.run(faults, "fluid@" + SandboxLevel.posKey(pos), () -> {
                        // The liquid renderer writes section-relative positions (pos & 15): shift them back.
                        VertexConsumer target = new OffsetVertexConsumer(
                                builder(ItemBlockRenderTypes.getRenderLayer(fluid), memory, builders),
                                pos.getX() & ~15, pos.getY() & ~15, pos.getZ() & ~15);
                        dispatcher.renderLiquid(pos, level, target, state, fluid);
                    });
                }
                if (state.getRenderShape() != RenderShape.MODEL) {
                    continue;
                }
                BlockEntity be = level.getBlockEntity(pos);
                Safe.run(faults, "mesh@" + SandboxLevel.posKey(pos), () -> {
                    PoseStack pose = new PoseStack();
                    pose.translate(pos.getX(), pos.getY(), pos.getZ());
                    SandboxPlatform.mesher().mesh(dispatcher, state, pos, level, be, pose,
                            type -> builder(type, memory, builders), random);
                });
            }
            uploadAll(builders, memory, eye);
        } finally {
            memory.values().forEach(ByteBufferBuilder::close);
        }
    }

    private static VertexConsumer builder(RenderType requested, Map<RenderType, ByteBufferBuilder> memory,
            Map<RenderType, BufferBuilder> builders) {
        RenderType type = requested.format() == DefaultVertexFormat.BLOCK ? requested : RenderType.translucent();
        return builders.computeIfAbsent(type, key -> {
            ByteBufferBuilder bytes = new ByteBufferBuilder(key.bufferSize());
            memory.put(key, bytes);
            return new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        });
    }

    private void uploadAll(Map<RenderType, BufferBuilder> builders, Map<RenderType, ByteBufferBuilder> memory,
            Vector3f eye) {
        layers.values().forEach(VertexBuffer::close);
        layers.clear();
        for (Map.Entry<RenderType, BufferBuilder> entry : builders.entrySet()) {
            RenderType type = entry.getKey();
            MeshData mesh = entry.getValue().build();
            if (mesh == null) {
                continue;
            }
            if (type.sortOnUpload()) {
                mesh.sortQuads(memory.get(type), VertexSorting.byDistance(eye.x, eye.y, eye.z));
            }
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(mesh);
            VertexBuffer.unbind();
            layers.put(type, buffer);
        }
    }

    private void drawLayer(RenderType type, Matrix4f view, Matrix4f projection, TextureTarget fbo) {
        VertexBuffer buffer = layers.get(type);
        if (buffer == null) {
            return;
        }
        type.setupRenderState();
        // Fabulous graphics: translucent layers bind the level's own targets; draw into ours instead.
        fbo.bindWrite(false);
        ShaderInstance shader = RenderSystem.getShader();
        if (shader != null) {
            if (shader.CHUNK_OFFSET != null) {
                shader.CHUNK_OFFSET.set(0.0F, 0.0F, 0.0F);
            }
            buffer.bind();
            buffer.drawWithShader(view, projection, shader);
            VertexBuffer.unbind();
        }
        type.clearRenderState();
        fbo.bindWrite(false);
    }

    // ---- block entities ----

    private static void renderBlockEntities(SandboxScene scene, Matrix4f view, float partialTick, TextureTarget fbo) {
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        FaultRegistry faults = scene.faults();
        SandboxLevel level = scene.level();
        try {
            for (BlockEntity be : scene.world().blockEntities().values()) {
                BlockEntityRenderer<BlockEntity> renderer = Safe.get(faults,
                        "be-renderer@" + SandboxLevel.posKey(be.getBlockPos()),
                        () -> mc.getBlockEntityRenderDispatcher().getRenderer(be), null);
                if (renderer == null) {
                    continue;
                }
                BlockPos pos = be.getBlockPos();
                Safe.run(faults, "be-render@" + SandboxLevel.posKey(pos), () -> {
                    PoseStack pose = new PoseStack();
                    pose.mulPose(view);
                    pose.translate(pos.getX(), pos.getY(), pos.getZ());
                    int light = LevelRenderer.getLightColor(level, pos);
                    renderer.render(be, partialTick, pose, buffers, light, OverlayTexture.NO_OVERLAY);
                    // Flush per block entity so a broken one cannot corrupt the next one's buffers.
                    buffers.endBatch();
                });
            }
        } finally {
            flushQuietly(buffers);
            fbo.bindWrite(false);
        }
    }

    /** Always attempted (never marked as faulted): a half-written buffer from a failed renderer is dropped. */
    private static void flushQuietly(MultiBufferSource.BufferSource buffers) {
        try (SandboxGuard.Scope ignored = SandboxGuard.enter()) {
            buffers.endBatch();
        } catch (RuntimeException e) {
            com.ufuk.tryitout.TryItOutClient.LOGGER.debug("TryItOut: dropped a broken batch", e);
        }
    }

    /** Thin dark outline of the block under the mouse, like the in-world selection box. */
    private static void renderOutline(SandboxScene scene, BlockPos pos, Matrix4f view, TextureTarget fbo) {
        SandboxLevel level = scene.level();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        VoxelShape shape = Safe.get(scene.faults(), "outline@" + SandboxLevel.posKey(pos),
                () -> state.getShape(level, pos, CollisionContext.empty()), null);
        if (shape == null || shape.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();
        pose.mulPose(view);
        pose.translate(pos.getX(), pos.getY(), pos.getZ());
        PoseStack.Pose last = pose.last();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float nx = (float) (x2 - x1);
            float ny = (float) (y2 - y1);
            float nz = (float) (z2 - z1);
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= length;
            ny /= length;
            nz /= length;
            lines.addVertex(last, (float) x1, (float) y1, (float) z1).setColor(0.0F, 0.0F, 0.0F, 0.6F)
                    .setNormal(last, nx, ny, nz);
            lines.addVertex(last, (float) x2, (float) y2, (float) z2).setColor(0.0F, 0.0F, 0.0F, 0.6F)
                    .setNormal(last, nx, ny, nz);
        });
        flushQuietly(buffers);
        fbo.bindWrite(false);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        layers.values().forEach(VertexBuffer::close);
        layers.clear();
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
        lightmap.close();
    }
}
