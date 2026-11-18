package com.ufuk.pocketsandbox.testworld;

import com.ufuk.pocketsandbox.scene.FloorType;
import com.ufuk.pocketsandbox.scene.WallSide;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A room passed between the pocket scene and the test world: floor size and block, walls, ceiling and the
 * blocks the player built (relative to the floor centre: x/z in -half..half, y from 1). Floor, walls and
 * ceiling are settings, not blocks, so the packet stays small. Block states travel as names (NBT), never as
 * numeric ids. Only blocks: no items, no block entity contents.
 */
public record SceneSnapshot(int size, FloorType floor, Set<WallSide> walls, boolean ceiling, boolean breakable,
        Map<BlockPos, BlockState> blocks) {
    public static final int MAX_BLOCKS = 20_000;

    public CompoundTag toTag() {
        CompoundTag tag = settingsTag();
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> index = new HashMap<>();
        int[] positions = new int[blocks.size()];
        int[] states = new int[blocks.size()];
        int i = 0;
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            positions[i] = pack(entry.getKey());
            states[i] = index.computeIfAbsent(entry.getValue(), state -> {
                palette.add(state);
                return palette.size() - 1;
            });
            i++;
        }
        ListTag paletteTag = new ListTag();
        for (BlockState state : palette) {
            paletteTag.add(NbtUtils.writeBlockState(state));
        }
        tag.put("Palette", paletteTag);
        tag.putIntArray("Positions", positions);
        tag.putIntArray("States", states);
        return tag;
    }

    /** Size, floor, walls, ceiling and breakable, without the blocks (kept with the session). */
    public CompoundTag settingsTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Size", size);
        tag.putString("Floor", floor.name());
        int wallBits = 0;
        for (WallSide side : walls) {
            wallBits |= 1 << side.ordinal();
        }
        tag.putInt("Walls", wallBits);
        tag.putBoolean("Ceiling", ceiling);
        tag.putBoolean("Breakable", breakable);
        return tag;
    }

    /** Reads a snapshot; unknown blocks (a mod missing on this side) are skipped. */
    public static SceneSnapshot fromTag(CompoundTag tag, HolderGetter<Block> blocks) {
        int size = tag.getInt("Size");
        FloorType floor = FloorType.PONDER;
        for (FloorType type : FloorType.values()) {
            if (type.name().equals(tag.getString("Floor"))) {
                floor = type;
            }
        }
        Set<WallSide> walls = EnumSet.noneOf(WallSide.class);
        int wallBits = tag.getInt("Walls");
        for (WallSide side : WallSide.values()) {
            if ((wallBits & (1 << side.ordinal())) != 0) {
                walls.add(side);
            }
        }
        ListTag paletteTag = tag.getList("Palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < paletteTag.size(); i++) {
            palette.add(NbtUtils.readBlockState(blocks, paletteTag.getCompound(i)));
        }
        int[] positions = tag.getIntArray("Positions");
        int[] states = tag.getIntArray("States");
        Map<BlockPos, BlockState> placed = new HashMap<>();
        for (int i = 0; i < Math.min(positions.length, Math.min(states.length, MAX_BLOCKS)); i++) {
            int s = states[i];
            if (s < 0 || s >= palette.size() || palette.get(s).isAir()) {
                continue;
            }
            placed.put(unpack(positions[i]), palette.get(s));
        }
        return new SceneSnapshot(size, floor, walls, tag.getBoolean("Ceiling"), tag.getBoolean("Breakable"), placed);
    }

    /** x, z in -128..127 and y in 0..255, packed into one int. */
    static int pack(BlockPos pos) {
        return RoomPacking.pack(pos.getX(), pos.getY(), pos.getZ());
    }

    static BlockPos unpack(int packed) {
        return new BlockPos(RoomPacking.unpackX(packed), RoomPacking.unpackY(packed), RoomPacking.unpackZ(packed));
    }

    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(toTag(), out);
        return out.toByteArray();
    }

    public static SceneSnapshot fromBytes(byte[] data, HolderGetter<Block> blocks) throws IOException {
        // Limited read: a hostile client cannot make the server allocate much.
        CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.create(4L * 1024 * 1024));
        return fromTag(tag, blocks);
    }
}
