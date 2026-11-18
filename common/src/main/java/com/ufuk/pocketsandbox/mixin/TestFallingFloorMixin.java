package com.ufuk.pocketsandbox.mixin;

import com.ufuk.pocketsandbox.testworld.TestSessions;
import com.ufuk.pocketsandbox.testworld.TestWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A test room's floor floats above the void: when it is made of sand, gravel, concrete powder... it must not fall.
 * Only the floor cells are held; the same blocks placed anywhere else in the test world still fall.
 */
@Mixin(FallingBlock.class)
public abstract class TestFallingFloorMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void pocketsandbox$holdFloor(BlockState state, ServerLevel level, BlockPos pos, RandomSource random,
            CallbackInfo ci) {
        if (TestWorld.isTestWorld(level) && TestSessions.isFloorCell(pos)) {
            ci.cancel();
        }
    }
}
