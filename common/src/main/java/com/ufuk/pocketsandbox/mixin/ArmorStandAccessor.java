package com.ufuk.pocketsandbox.mixin;

import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Locks every slot of the test body (nothing can be taken from or put on it). */
@Mixin(ArmorStand.class)
public interface ArmorStandAccessor {
    @Accessor("disabledSlots")
    void pocketsandbox$setDisabledSlots(int slots);
}
