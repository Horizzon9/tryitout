package com.ufuk.tryitout.testworld.net;

import com.ufuk.tryitout.TryItOut;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server → client: the materials of the build in the test area (item ids and counts, most used first). */
public record TestMaterialsPayload(List<String> items, List<Integer> counts) implements CustomPacketPayload {
    public static final Type<TestMaterialsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TryItOut.MOD_ID, "test_materials"));
    public static final StreamCodec<ByteBuf, TestMaterialsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(4096)), TestMaterialsPayload::items,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(4096)), TestMaterialsPayload::counts,
            TestMaterialsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
