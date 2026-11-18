package com.ufuk.tryitout.mixin;

import com.ufuk.tryitout.safety.SandboxGuard;
import com.ufuk.tryitout.screen.SandboxMenus;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hard network isolation: while sandbox code runs on the render thread ({@link SandboxGuard#active()}),
 * every outgoing packet is dropped. While a sandbox GUI is open, its container / recipe / custom-payload
 * packets are dropped as well. All other {@code send} overloads end up in this one.
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void tryitout$dropSandboxPackets(Packet<?> packet, @Nullable PacketSendListener listener,
            boolean flush, CallbackInfo ci) {
        if (SandboxGuard.active() || (SandboxMenus.active() && SandboxMenus.isMenuPacket(packet))) {
            SandboxGuard.onBlocked(packet);
            ci.cancel();
        }
    }
}
