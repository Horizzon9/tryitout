package com.ufuk.tryitout.testworld.net;

import com.ufuk.tryitout.TryItOut;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Settings of the test room, both ways: the client sends the state it wants, the server applies what it allows
 * and sends back the state that is now true. {@code walls} is a bit set of {@code WallSide} ordinals;
 * {@code flags} holds the switches ({@link #CEILING} ...); {@code time} indexes {@link #TIMES}
 * ({@link #TIME_CYCLE} = day cycle on); {@code weather} 0 clear, 1 rain, 2 thunder; {@code gameMode} is a vanilla
 * game type id (0 survival, 1 creative, 2 adventure, 3 spectator); {@code size} the floor size (0 from the client =
 * keep it). Server → client only: {@code maxSize} (largest size allowed here) and the ids of the floor / wall /
 * ceiling blocks ("" = default).
 */
public record TestSettingsPayload(int walls, int flags, int time, int weather, int gameMode, int size, int maxSize,
        String floorBlock, String wallBlock, String ceilingBlock, int dummyHp) implements CustomPacketPayload {
    public static final int CEILING = 1;
    public static final int MOB_SPAWNING = 1 << 1;
    public static final int FIRE_SPREAD = 1 << 2;
    public static final int RANDOM_TICKS = 1 << 3;
    public static final int KEEP_INVENTORY = 1 << 4;
    public static final int COMMANDS = 1 << 5;
    /** Floor, walls and ceiling cannot be broken. */
    public static final int LOCKED = 1 << 6;
    /** Training dummies can die. */
    public static final int DUMMY_KILLABLE = 1 << 7;
    /** A dummy that died stays gone (else it comes back after a few seconds). */
    public static final int DUMMY_REMOVE = 1 << 8;
    /** Largest dummy health a player may type. */
    public static final int MAX_DUMMY_HP = 10_000;
    /** The red glass edges of the border are not shown (the invisible wall stays). */
    public static final int BORDER_HIDDEN = 1 << 11;
    /** No border wall: the area reaches up to 4900 blocks from its centre. */
    public static final int UNLIMITED = 1 << 12;
    /** Server -> client only: the server allows the unlimited switch. */
    public static final int UNLIMITED_ALLOWED = 1 << 13;
    private static final int ALL_FLAGS = (1 << 14) - 1;
    private static final int MAX_ID = 128;

    /** Day time of the fixed presets: day (noon), dusk, night, midnight. */
    public static final long[] TIMES = {6000L, 12500L, 14000L, 18000L};
    public static final int TIME_CYCLE = TIMES.length;
    public static final int WEATHERS = 3;
    public static final int GAME_MODES = 4;

    public static final Type<TestSettingsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TryItOut.MOD_ID, "test_settings"));
    public static final StreamCodec<ByteBuf, TestSettingsPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.VAR_INT.encode(buf, p.walls());
                ByteBufCodecs.VAR_INT.encode(buf, p.flags());
                ByteBufCodecs.VAR_INT.encode(buf, p.time());
                ByteBufCodecs.VAR_INT.encode(buf, p.weather());
                ByteBufCodecs.VAR_INT.encode(buf, p.gameMode());
                ByteBufCodecs.VAR_INT.encode(buf, p.size());
                ByteBufCodecs.VAR_INT.encode(buf, p.maxSize());
                ByteBufCodecs.stringUtf8(MAX_ID).encode(buf, p.floorBlock());
                ByteBufCodecs.stringUtf8(MAX_ID).encode(buf, p.wallBlock());
                ByteBufCodecs.stringUtf8(MAX_ID).encode(buf, p.ceilingBlock());
                ByteBufCodecs.VAR_INT.encode(buf, p.dummyHp());
            },
            buf -> new TestSettingsPayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.stringUtf8(MAX_ID).decode(buf),
                    ByteBufCodecs.stringUtf8(MAX_ID).decode(buf), ByteBufCodecs.stringUtf8(MAX_ID).decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf)));

    public TestSettingsPayload {
        floorBlock = clip(floorBlock);
        wallBlock = clip(wallBlock);
        ceilingBlock = clip(ceilingBlock);
    }

    /** Client request without a size change. */
    public TestSettingsPayload(int walls, int flags, int time, int weather, int gameMode) {
        this(walls, flags, time, weather, gameMode, 0, 0, "", "", "", 0);
    }

    private static String clip(String id) {
        return id == null ? "" : id.length() > MAX_ID ? id.substring(0, MAX_ID) : id;
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public TestSettingsPayload toggled(int flag) {
        return new TestSettingsPayload(walls, flags ^ flag, time, weather, gameMode, 0, maxSize, floorBlock,
                wallBlock, ceilingBlock, dummyHp);
    }

    /** The same settings asking for another floor size. */
    public TestSettingsPayload withSize(int newSize) {
        return new TestSettingsPayload(walls, flags, time, weather, gameMode, newSize, maxSize, floorBlock,
                wallBlock, ceilingBlock, dummyHp);
    }

    /** Same settings with every value in range. */
    public TestSettingsPayload sanitized() {
        return new TestSettingsPayload(walls & 0xF, flags & ALL_FLAGS, Math.floorMod(time, TIME_CYCLE + 1),
                Math.floorMod(weather, WEATHERS), Math.floorMod(gameMode, GAME_MODES), Math.max(0, size),
                Math.max(0, maxSize), floorBlock, wallBlock, ceilingBlock, Math.max(0, Math.min(MAX_DUMMY_HP, dummyHp)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
