package com.ufuk.pocketsandbox.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Vanilla / Fabric path: one chunk render type per block; FRAPI renderers hook into renderBatched. */
public final class VanillaBlockMesher implements BlockMesher {
    @Override
    public void mesh(BlockRenderDispatcher dispatcher, BlockState state, BlockPos pos, BlockAndTintGetter level,
            @Nullable BlockEntity blockEntity, PoseStack pose, Function<RenderType, VertexConsumer> buffers,
            RandomSource random) {
        RenderType layer = ItemBlockRenderTypes.getChunkRenderType(state);
        dispatcher.renderBatched(state, pos, level, pose, buffers.apply(layer), true, random);
    }
}
