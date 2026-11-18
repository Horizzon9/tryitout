package com.ufuk.pocketsandbox.testworld.net;

import com.ufuk.pocketsandbox.PocketSandbox;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: the border box of the player's test area (block bounds, inclusive), so the client collides
 * with the invisible border walls the same way the server does (smooth movement, no rubber-banding).
 */
public record TestAreaPayload(int minX, int minY, int minZ, int maxX, int maxY, int maxZ)
        implements CustomPacketPayload {
    public static final Type<TestAreaPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PocketSandbox.MOD_ID, "test_area"));
    public static final StreamCodec<ByteBuf, TestAreaPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TestAreaPayload::minX,
            ByteBufCodecs.VAR_INT, TestAreaPayload::minY,
            ByteBufCodecs.VAR_INT, TestAreaPayload::minZ,
            ByteBufCodecs.VAR_INT, TestAreaPayload::maxX,
            ByteBufCodecs.VAR_INT, TestAreaPayload::maxY,
            ByteBufCodecs.VAR_INT, TestAreaPayload::maxZ,
            TestAreaPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
