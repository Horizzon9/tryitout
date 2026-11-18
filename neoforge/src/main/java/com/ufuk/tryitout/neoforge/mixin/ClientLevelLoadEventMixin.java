package com.ufuk.tryitout.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ufuk.tryitout.world.SandboxClientView;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge posts {@code LevelEvent.Load} from the ClientLevel constructor. The sandbox client view is not a
 * real level, so while it is being built the event is not posted (other mods must not see or cache it).
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelLoadEventMixin {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/bus/api/IEventBus;post(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;"))
    private Event tryitout$skipSandboxLoadEvent(IEventBus bus, Event event, Operation<Event> original) {
        if (SandboxClientView.isConstructing()) {
            return event;
        }
        return original.call(bus, event);
    }
}
