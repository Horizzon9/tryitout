package com.ufuk.tryitout.testworld.net;

import com.ufuk.tryitout.TryItOut;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client copy of the room settings. A separate id from {@link TestSettingsPayload} (client → server):
 * NeoForge does not allow one payload id to be registered for both directions twice.
 */
public record TestSettingsSyncPayload(TestSettingsPayload settings) implements CustomPacketPayload {
    public static final Type<TestSettingsSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TryItOut.MOD_ID, "test_settings_sync"));
    public static final StreamCodec<ByteBuf, TestSettingsSyncPayload> CODEC =
            TestSettingsPayload.CODEC.map(TestSettingsSyncPayload::new, TestSettingsSyncPayload::settings);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
