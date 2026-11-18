package com.ufuk.tryitout.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Tessellates one block model into per-{@link RenderType} buffers. Loader specific: NeoForge models pick
 * their render types and model data themselves; on Fabric, FRAPI renderers (Indigo, Continuity CTM) hook
 * into the vanilla path.
 */
public interface BlockMesher {
    void mesh(BlockRenderDispatcher dispatcher, BlockState state, BlockPos pos, BlockAndTintGetter level,
            @Nullable BlockEntity blockEntity, PoseStack pose, Function<RenderType, VertexConsumer> buffers,
            RandomSource random);
}
