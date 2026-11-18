package com.ufuk.pocketsandbox.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ufuk.pocketsandbox.PocketSandboxClient;
import com.ufuk.pocketsandbox.safety.Safe;
import com.ufuk.pocketsandbox.safety.SandboxGuard;
import com.ufuk.pocketsandbox.scene.SandboxScene;
import com.ufuk.pocketsandbox.scene.WeaponRig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Weapon mode drawing: the preview player and the dummy through the game's own entity renderers (shadows
 * off, each entity guarded), then the reach ring at eye height and a line from the eye to the dummy, green
 * when it is in reach and red when it is not.
 */
final class WeaponPass {
    private static final int RING_SEGMENTS = 64;

    private WeaponPass() {
    }

    static void render(SandboxScene scene, Matrix4f view, float partialTick, TextureTarget fbo) {
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        // Decorations placed in the scene (paintings, item frames, armour stands), in every mode.
        renderEntities(scene, scene.world().entities().getAll(), view, partialTick, fbo);
        WeaponRig rig = scene.rig();
        if (rig == null || !scene.weaponMode()) {
            return;
        }
        renderEntities(scene, rig.entities(), view, partialTick, fbo);
        renderReach(rig, view, buffers, scene.world().gameTime());
        fbo.bindWrite(false);
    }

    private static void renderEntities(SandboxScene scene, Iterable<Entity> entities, Matrix4f view,
            float partialTick, TextureTarget fbo) {
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        try {
            for (Entity entity : entities) {
                Safe.run(scene.faults(), "entity@" + entity.getType().getDescriptionId(), () -> {
                    PoseStack pose = new PoseStack();
                    pose.mulPose(view);
                    int light = LevelRenderer.getLightColor(scene.level(),
                            BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ()));
                    dispatcher.render(entity, entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(),
                            partialTick, pose, buffers, light);
                    buffers.endBatch();
                });
            }
        } finally {
            dispatcher.setRenderShadow(true);
            flush(buffers);
            fbo.bindWrite(false);
        }
    }

    private static void renderReach(WeaponRig rig, Matrix4f view, MultiBufferSource.BufferSource buffers,
            long gameTime) {
        PoseStack pose = new PoseStack();
        pose.mulPose(view);
        PoseStack.Pose last = pose.last();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        Vec3 eye = rig.eye();
        if (rig.ranged() == null) {
            float radius = (float) rig.stats().reach();
            for (int i = 0; i < RING_SEGMENTS; i++) {
                double a0 = Math.PI * 2 * i / RING_SEGMENTS;
                double a1 = Math.PI * 2 * (i + 1) / RING_SEGMENTS;
                line(lines, last, eye.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius),
                        eye.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius), 0.4F, 0.8F, 1.0F, 0.7F);
            }
            boolean inReach = rig.inReach();
            line(lines, last, eye, rig.dummyCentre(), inReach ? 0.3F : 1.0F, inReach ? 1.0F : 0.3F, 0.3F, 1.0F);
        } else {
            // Predicted flight: dashed, yellow while drawing, grey at rest (full power preview).
            java.util.List<Vec3> flight = rig.predictedFlight(gameTime);
            float g = rig.drawing() ? 0.9F : 0.6F;
            float b = rig.drawing() ? 0.2F : 0.6F;
            for (int i = 0; i + 1 < flight.size(); i += 2) {
                line(lines, last, flight.get(i), flight.get(i + 1), 1.0F, g, b, 0.9F);
            }
            if (!flight.isEmpty()) {
                Vec3 land = flight.get(flight.size() - 1);
                line(lines, last, land.add(-0.4, 0.02, -0.4), land.add(0.4, 0.02, 0.4), 0.3F, 1.0F, 1.0F, 1.0F);
                line(lines, last, land.add(-0.4, 0.02, 0.4), land.add(0.4, 0.02, -0.4), 0.3F, 1.0F, 1.0F, 1.0F);
            }
        }
        // Ruler ticks on the floor across the path.
        for (WeaponRig.Popup mark : rig.ruler()) {
            Vec3 p = mark.position().add(1.2, 0, 0);
            line(lines, last, p.add(-0.3, 0, 0), p.add(0.3, 0, 0), 0.33F, 0.67F, 1.0F, 0.9F);
        }
        flush(buffers);
    }

    private static void line(VertexConsumer lines, PoseStack.Pose pose, Vec3 from, Vec3 to, float r, float g, float b,
            float a) {
        float nx = (float) (to.x - from.x);
        float ny = (float) (to.y - from.y);
        float nz = (float) (to.z - from.z);
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0E-5F) {
            return;
        }
        nx /= length;
        ny /= length;
        nz /= length;
        lines.addVertex(pose, (float) from.x, (float) from.y, (float) from.z).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        lines.addVertex(pose, (float) to.x, (float) to.y, (float) to.z).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
    }

    private static void flush(MultiBufferSource.BufferSource buffers) {
        try (SandboxGuard.Scope ignored = SandboxGuard.enter()) {
            buffers.endBatch();
        } catch (RuntimeException e) {
            PocketSandboxClient.LOGGER.debug("Pocket Sandbox: dropped a broken entity batch", e);
        }
    }
}
