package com.ufuk.tryitout.testworld;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * "What does this build need?": every block the player put in the test area, counted as the item that places
 * it. The floor, the walls, the ceiling and the border are not counted; two-block things (doors, tall plants,
 * beds) count once.
 */
final class TestMaterials {
    private TestMaterials() {
    }

    /** Item id → count, most used first. */
    static Map<String, Integer> count(ServerLevel level, TestArea area, CompoundTag room) {
        SceneSnapshot settings = SceneSnapshot.fromTag(room, null);
        Map<BlockPos, BlockState> structure = TestAreaBuilder.structureCells(room, area);
        Map<Item, Integer> counts = new LinkedHashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // The whole build box: blocks next to and below the floor count too.
        for (int x = area.buildMinX(); x <= area.buildMaxX(); x++) {
            for (int z = area.buildMinZ(); z <= area.buildMaxZ(); z++) {
                for (int y = area.buildMinY(); y <= area.buildMaxY(); y++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || secondHalf(state) || state == structure.get(pos)
                            || isRoomFloor(area, settings, room, pos, state)
                            || state == TestAreaBuilder.BARRIER || state == TestAreaBuilder.MARKER) {
                        continue;
                    }
                    Item item = state.getBlock().asItem();
                    if (item != Items.AIR) {
                        counts.merge(item, 1, Integer::sum);
                    }
                }
            }
        }
        List<Map.Entry<Item, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> entry : sorted) {
            out.put(BuiltInRegistries.ITEM.getKey(entry.getKey()).toString(), entry.getValue());
        }
        return out;
    }

    /** The room's own floor (floor square, floor layer, still the floor block): not part of the build. */
    private static boolean isRoomFloor(TestArea area, SceneSnapshot settings, CompoundTag room, BlockPos pos,
            BlockState state) {
        return pos.getY() == area.floorY() && pos.getX() >= area.minX() && pos.getX() <= area.maxX()
                && pos.getZ() >= area.minZ() && pos.getZ() <= area.maxZ()
                && state == RoomBlocks.floor(room, settings.floor(), pos.getX() - area.centerX(),
                        pos.getZ() - area.centerZ());
    }

    private static boolean secondHalf(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return true;
        }
        return state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD;
    }
}
