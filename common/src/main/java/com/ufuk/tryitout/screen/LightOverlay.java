package com.ufuk.tryitout.screen;

import com.ufuk.tryitout.scene.SandboxScene;
import com.ufuk.tryitout.world.SandboxBounds;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Block light (0-15) written on the top surface of every column. Overworld monster rule (1.18+): block
 * light must be 0, and sky light minus the time-of-day darkening must be at most 7. So 0 is red only where a
 * monster really could spawn at the chosen time; grey 0 = no block light, but daylight still prevents
 * spawning. Yellow / green = lit, nothing spawns. Labels hidden behind blocks are skipped.
 */
final class LightOverlay {
    private static final int SPAWNABLE = 0xFF5555;
    private static final int DAYLIT = 0xAAAAAA;
    /** Highest sky-derived light a monster still spawns in (vanilla upper bound of the 0..7 roll). */
    private static final int MONSTER_SKY_LIMIT = 7;
    private static final int DIM = 0xFFDD55;
    private static final int BRIGHT = 0x55FF55;
    private static final int BRIGHT_FROM = 11;

    private LightOverlay() {
    }

    /** @param skyDarken vanilla sky darkening of the chosen time: 0 at noon, 11 at midnight */
    static void render(GuiGraphics graphics, Font font, SandboxScene scene, Matrix4f view, Matrix4f projection,
            int left, int top, int width, int height, int skyDarken) {
        SandboxBounds bounds = scene.world().bounds();
        Vector3f eye = com.ufuk.tryitout.scene.SceneMath.eye(view);
        Matrix4f viewProjection = new Matrix4f(projection).mul(view);
        graphics.enableScissor(left, top, left + width, top + height);
        try {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    BlockPos surface = surface(scene, bounds, x, z);
                    if (surface != null) {
                        label(graphics, font, scene, surface, eye, viewProjection, left, top, width, height, skyDarken);
                    }
                }
            }
        } finally {
            graphics.disableScissor();
        }
    }

    /** First position above the highest block with a solid top in this column. */
    private static BlockPos surface(SandboxScene scene, SandboxBounds bounds, int x, int z) {
        for (int y = bounds.maxY(); y >= bounds.minY(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = scene.level().getBlockState(pos);
            if (!state.isAir() && state.isFaceSturdy(scene.level(), pos, Direction.UP)) {
                return y + 1 <= bounds.maxY() ? pos.above() : null;
            }
        }
        return null;
    }

    private static void label(GuiGraphics graphics, Font font, SandboxScene scene, BlockPos pos, Vector3f eye,
            Matrix4f viewProjection, int left, int top, int width, int height, int skyDarken) {
        Vector3f point = new Vector3f(pos.getX() + 0.5F, pos.getY() + 0.02F, pos.getZ() + 0.5F);
        if (!visible(scene, eye, point, pos)) {
            return;
        }
        Vector4f clip = viewProjection.transform(new Vector4f(point, 1.0F));
        if (clip.w <= 0.0F) {
            return;
        }
        float sx = left + (clip.x / clip.w * 0.5F + 0.5F) * width;
        float sy = top + (0.5F - clip.y / clip.w * 0.5F) * height;
        int light = scene.level().getBrightness(LightLayer.BLOCK, pos);
        String text = String.valueOf(light);
        int color;
        if (light > 0) {
            color = light < BRIGHT_FROM ? DIM : BRIGHT;
        } else {
            int sky = scene.level().getBrightness(LightLayer.SKY, pos) - skyDarken;
            color = sky <= MONSTER_SKY_LIMIT ? SPAWNABLE : DAYLIT;
        }
        graphics.drawString(font, text, Math.round(sx - font.width(text) / 2.0F), Math.round(sy - 4), color, true);
    }

    /** True when nothing but the surface itself stands between the camera and the label point. */
    private static boolean visible(SandboxScene scene, Vector3f eye, Vector3f point, BlockPos labelPos) {
        Vector3f towards = new Vector3f(point).sub(eye);
        float distance = towards.length();
        if (distance < 0.01F) {
            return false;
        }
        Vector3f end = new Vector3f(eye).add(towards.mul((distance - 0.05F) / distance));
        Optional<BlockHitResult> hit = scene.editor().pick(eye, end);
        return hit.isEmpty() || hit.get().getBlockPos().equals(labelPos)
                || hit.get().getBlockPos().equals(labelPos.below());
    }
}
