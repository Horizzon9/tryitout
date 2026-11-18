package com.ufuk.tryitout.testworld.net;

import com.ufuk.tryitout.TryItOut;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Client → server: enter the test world with this room (a compressed
 * {@link com.ufuk.tryitout.testworld.SceneSnapshot}) and the scene's item, which goes into the test
 * character's hand. Serverbound payloads are limited to 32 767 bytes in total.
 */
public record TestEnterPayload(byte[] data, ItemStack target) implements CustomPacketPayload {
    public static final int MAX_BYTES = 30_000;
    public static final Type<TestEnterPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TryItOut.MOD_ID, "test_enter"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TestEnterPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.byteArray(MAX_BYTES), TestEnterPayload::data,
            ItemStack.OPTIONAL_STREAM_CODEC, TestEnterPayload::target,
            TestEnterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
