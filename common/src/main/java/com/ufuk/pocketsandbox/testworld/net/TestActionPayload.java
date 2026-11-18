package com.ufuk.pocketsandbox.testworld.net;

import com.ufuk.pocketsandbox.PocketSandbox;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: a test world request. {@code arg} carries an optional name (save name, invited player).
 * The server validates everything itself; the client only asks.
 */
public record TestActionPayload(Action action, String arg) implements CustomPacketPayload {
    public static final Type<TestActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PocketSandbox.MOD_ID, "test_action"));
    private static final int MAX_ARG = 64;

    public static final StreamCodec<ByteBuf, TestActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(Action::byId, Action::ordinal), TestActionPayload::action,
            ByteBufCodecs.stringUtf8(MAX_ARG), TestActionPayload::arg,
            TestActionPayload::new);

    public enum Action {
        ENTER, EXIT, SAVE, DUMMY_ADD, DUMMY_REMOVE, LIST, DELETE, LOAD, MATERIALS, INVITE,
        /** Floor / walls / ceiling block ({@code arg} = block id, empty = default). */
        SET_FLOOR, SET_WALL, SET_CEILING,
        /** Dummy health ({@code arg} = number) / dummy name ({@code arg} = "uuid|name"). */
        DUMMY_HEALTH, DUMMY_RENAME;

        static Action byId(int id) {
            Action[] values = values();
            return id >= 0 && id < values.length ? values[id] : ENTER;
        }
    }

    public TestActionPayload {
        arg = arg == null ? "" : arg.length() > MAX_ARG ? arg.substring(0, MAX_ARG) : arg;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
