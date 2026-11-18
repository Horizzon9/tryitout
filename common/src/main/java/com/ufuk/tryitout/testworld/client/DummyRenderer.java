package com.ufuk.tryitout.testworld.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.architectury.registry.client.level.entity.EntityModelLayerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

/**
 * How TryItOut clients draw a training dummy (the server sends a husk): its own model and texture, like an
 * armour stand but wider: a stone base, a wooden post, a rope-bound sack body, a wide shoulder beam and a sack
 * head with a painted target. It rocks back and flashes red when hit and tips over when it dies. The name tag
 * above it shows the hits (and health when it can die). The hitbox stays the husk's.
 */
public final class DummyRenderer {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("tryitout", "training_dummy"), "main");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("tryitout", "textures/entity/training_dummy.png");
    /** The model is 26 px tall; this makes it about as tall as the husk's 1.95 block hitbox. */
    private static final float SCALE = 1.2F;

    private static ModelPart model;

    private DummyRenderer() {
    }

    /** Client start-up: the model layer (64 x 64 texture, box UV). */
    public static void init() {
        EntityModelLayerRegistry.register(LAYER, DummyRenderer::createLayer);
    }

    private static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // Model space: y grows downwards, y = 24 is the ground (like every entity model).
        root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-7, 22, -7, 14, 2, 14),
                PartPose.ZERO);
        root.addOrReplaceChild("post", CubeListBuilder.create().texOffs(56, 0).addBox(-1, 2, -1, 2, 20, 2),
                PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 16).addBox(-5, 8, -3, 10, 10, 6),
                PartPose.ZERO);
        root.addOrReplaceChild("beam", CubeListBuilder.create().texOffs(0, 32).addBox(-12, 5, -1.5F, 24, 3, 3),
                PartPose.ZERO);
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 38).addBox(-3.5F, -2, -3.5F, 7, 7, 7),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static ModelPart model() {
        if (model == null) {
            model = Minecraft.getInstance().getEntityModels().bakeLayer(LAYER);
        }
        return model;
    }

    public static void render(LivingEntity dummy, double x, double y, double z, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(x, y, z);
        float bodyYaw = Mth.rotLerp(partialTick, dummy.yBodyRotO, dummy.yBodyRot);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        if (dummy.deathTime > 0) {
            float fall = Math.min(1.0F, (dummy.deathTime + partialTick - 1.0F) / 20.0F * 1.6F);
            pose.mulPose(Axis.XP.rotationDegrees(-fall * 90.0F));
        } else if (dummy.hurtTime > 0) {
            float hurt = dummy.hurtTime - partialTick;
            float swing = Mth.sin(hurt * 1.6F) * hurt / Math.max(1, dummy.hurtDuration) * 14.0F;
            pose.mulPose(Axis.XP.rotationDegrees(-swing));
        }
        pose.scale(-SCALE, -SCALE, SCALE);
        pose.translate(0.0F, -1.501F, 0.0F);
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int overlay = OverlayTexture.pack(OverlayTexture.u(0.0F),
                OverlayTexture.v(dummy.hurtTime > 0 || dummy.deathTime > 0));
        model().render(pose, buffer, light, overlay);
        pose.popPose();
        if (dummy.hasCustomName() && dummy.isCustomNameVisible()
                && mc.getEntityRenderDispatcher().distanceToSqr(dummy) <= 64 * 64) {
            label(mc, dummy.getCustomName(), x, y + 2.15, z, pose, buffers, light);
        }
    }

    /** Name tag like the vanilla one: faces the camera, readable through walls faintly. */
    private static void label(Minecraft mc, Component text, double x, double y, double z, PoseStack pose,
            MultiBufferSource buffers, int light) {
        Font font = mc.font;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(0.025F, -0.025F, 0.025F);
        Matrix4f matrix = pose.last().pose();
        float left = -font.width(text) / 2.0F;
        int background = (int) (mc.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        font.drawInBatch(text, left, 0, 0x20FFFFFF, false, matrix, buffers, Font.DisplayMode.SEE_THROUGH, background,
                light);
        font.drawInBatch(text, left, 0, 0xFFFFFFFF, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }
}
