package com.ufuk.tryitout.mixin;

import com.ufuk.tryitout.testworld.TestSessions;
import com.ufuk.tryitout.testworld.TestWorld;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Explosions in the test world spare what may not be broken: the red border edges, and the floor, walls and ceiling
 * of a room set to unbreakable.
 */
@Mixin(Explosion.class)
public abstract class TestExplosionMixin {
    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    private ObjectArrayList<BlockPos> toBlow;

    @Inject(method = "explode", at = @At("TAIL"))
    private void tryitout$spareProtected(CallbackInfo ci) {
        if (level instanceof ServerLevel server && TestWorld.isTestWorld(server)) {
            toBlow.removeIf(TestSessions::unbreakable);
        }
    }
}
