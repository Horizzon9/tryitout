package com.ufuk.tryitout.testworld.net;

import com.ufuk.tryitout.TryItOut;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A compressed {@link com.ufuk.tryitout.testworld.SceneSnapshot}. Server → client: the room as it was left, to reopen the pocket scene with it.
 * Only block names and settings travel, never items.
 */
public record TestLeftPayload(byte[] data) implements CustomPacketPayload {
    public static final int MAX_BYTES = 900_000;
    public static final Type<TestLeftPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TryItOut.MOD_ID, "test_left"));
    public static final StreamCodec<ByteBuf, TestLeftPayload> CODEC =
            ByteBufCodecs.byteArray(MAX_BYTES).map(TestLeftPayload::new, TestLeftPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
