package com.ufuk.pocketsandbox.mixin;

import com.ufuk.pocketsandbox.testworld.TestSessions;
import com.ufuk.pocketsandbox.testworld.TestWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Water and lava in the test world stop at the border box: no endless streams falling into the void. */
@Mixin(FlowingFluid.class)
public abstract class TestFluidMixin {
    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void pocketsandbox$stayInside(LevelAccessor level, BlockPos pos, BlockState state, Direction direction,
            FluidState fluid, CallbackInfo ci) {
        if (level instanceof ServerLevel server && TestWorld.isTestWorld(server)
                && !TestSessions.canBuildAt(server, pos)) {
            ci.cancel();
        }
    }
}
