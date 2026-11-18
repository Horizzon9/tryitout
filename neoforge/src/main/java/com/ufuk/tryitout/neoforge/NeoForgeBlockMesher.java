package com.ufuk.tryitout.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ufuk.tryitout.render.BlockMesher;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * NeoForge path: the model chooses its render types (json {@code render_type}) and receives the block
 * entity's {@link ModelData}, which connected-texture and dynamic models rely on.
 */
final class NeoForgeBlockMesher implements BlockMesher {
    @Override
    public void mesh(BlockRenderDispatcher dispatcher, BlockState state, BlockPos pos, BlockAndTintGetter level,
            @Nullable BlockEntity blockEntity, PoseStack pose, Function<RenderType, VertexConsumer> buffers,
            RandomSource random) {
        BakedModel model = dispatcher.getBlockModel(state);
        ModelData base = blockEntity != null ? blockEntity.getModelData() : ModelData.EMPTY;
        ModelData data = model.getModelData(level, pos, state, base);
        random.setSeed(state.getSeed(pos));
        for (RenderType type : model.getRenderTypes(state, random, data)) {
            dispatcher.renderBatched(state, pos, level, pose, buffers.apply(type), true, random, data, type);
        }
    }
}
