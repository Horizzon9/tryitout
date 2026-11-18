package com.ufuk.pocketsandbox.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Deque;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Depth of a pose stack, so a failed renderer's leftover pushes can be undone. */
@Mixin(PoseStack.class)
public interface PoseStackAccessor {
    @Accessor("poseStack")
    Deque<PoseStack.Pose> pocketsandbox$stack();
}
