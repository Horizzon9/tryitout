package com.ufuk.pocketsandbox.testworld.net;

import com.ufuk.pocketsandbox.PocketSandbox;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: the player's saved test worlds, the save limit, the save the current test world came from
 * (empty for a new one) and whether this player may save here (the owner yes, a guest no).
 */
public record TestSavesPayload(List<String> names, int limit, String current, boolean canSave)
        implements CustomPacketPayload {
    public static final Type<TestSavesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PocketSandbox.MOD_ID, "test_saves"));
    public static final StreamCodec<ByteBuf, TestSavesPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(64)), TestSavesPayload::names,
            ByteBufCodecs.VAR_INT, TestSavesPayload::limit,
            ByteBufCodecs.stringUtf8(64), TestSavesPayload::current,
            ByteBufCodecs.BOOL, TestSavesPayload::canSave,
            TestSavesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
