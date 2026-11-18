package com.ufuk.tryitout.testworld.net;

import com.ufuk.tryitout.TryItOut;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: the player's test world state and an optional message (a translation key shown in chat).
 */
public record TestStatusPayload(boolean inTest, String messageKey) implements CustomPacketPayload {
    public static final Type<TestStatusPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TryItOut.MOD_ID, "test_status"));

    public static final StreamCodec<ByteBuf, TestStatusPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TestStatusPayload::inTest,
            ByteBufCodecs.stringUtf8(128), TestStatusPayload::messageKey,
            TestStatusPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
