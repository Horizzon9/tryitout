package com.ufuk.pocketsandbox.testworld;

import com.ufuk.pocketsandbox.scene.FloorType;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which blocks a test room's floor, walls and ceiling are made of (room settings "FloorBlock", "WallBlock",
 * "CeilingBlock"; empty = the pocket scene's defaults: the chosen floor pattern, stone bricks, glass), and whether
 * they are unbreakable ("Locked").
 */
final class RoomBlocks {
    static final String FLOOR = "FloorBlock";
    static final String WALL = "WallBlock";
    static final String CEILING = "CeilingBlock";
    static final String LOCKED = "Locked";
    static final BlockState DEFAULT_WALL = Blocks.STONE_BRICKS.defaultBlockState();
    static final BlockState DEFAULT_CEILING = Blocks.GLASS.defaultBlockState();

    private RoomBlocks() {
    }

    static BlockState wall(CompoundTag room) {
        return custom(room, WALL).orElse(DEFAULT_WALL);
    }

    static BlockState ceiling(CompoundTag room) {
        return custom(room, CEILING).orElse(DEFAULT_CEILING);
    }

    /** Floor block at room position (x, z): the custom block, or the floor pattern of the pocket scene. */
    static BlockState floor(CompoundTag room, FloorType pattern, int x, int z) {
        return custom(room, FLOOR).orElseGet(() -> pattern.block(x, z));
    }

    static boolean locked(CompoundTag room) {
        return room.getBoolean(LOCKED);
    }

    private static Optional<BlockState> custom(CompoundTag room, String key) {
        String id = room.getString(key);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || !BuiltInRegistries.BLOCK.containsKey(location)) {
            return Optional.empty(); // a mod that is gone: back to the default
        }
        return Optional.of(BuiltInRegistries.BLOCK.get(location).defaultBlockState());
    }

    /**
     * Whether {@code block} may build a floor, wall or ceiling: a real block (not air), no command / structure
     * blocks, nothing the admin banned, no block entity (chests or machines as walls would be odd and heavy).
     */
    static boolean usable(Block block) {
        if (block == Blocks.AIR || block instanceof GameMasterBlock
                || block instanceof net.minecraft.world.level.block.EntityBlock) {
            return false;
        }
        Set<String> banned = Set.copyOf(TestServerConfig.get().bannedBlocks);
        return !banned.contains(BuiltInRegistries.BLOCK.getKey(block).toString());
    }
}
