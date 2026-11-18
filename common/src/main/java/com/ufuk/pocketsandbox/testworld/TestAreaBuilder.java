package com.ufuk.pocketsandbox.testworld;

import com.ufuk.pocketsandbox.PocketSandbox;
import com.ufuk.pocketsandbox.scene.FloorType;
import com.ufuk.pocketsandbox.scene.WallSide;
import com.ufuk.pocketsandbox.world.SandboxBounds;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Builds, reads and wipes a test area in the test dimension. Room coordinates are the pocket scene's (floor
 * centre at 0, floor layer y = 0); the area's floor centre is ({@code centerX}, {@code floorY}, {@code centerZ}).
 * Wiping never drops anything: block entities are removed before their block (chests do not spill), no
 * neighbour updates run, and loose entities in the box are discarded.
 */
final class TestAreaBuilder {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    /**
     * The visible edges of the border box (red glass). Its faces are no blocks at all: an invisible collision wall
     * ({@link TestBorderShapes}). BARRIER is only removed (areas built by older versions used it).
     */
    static final BlockState MARKER = Blocks.RED_STAINED_GLASS.defaultBlockState();
    static final BlockState BARRIER = Blocks.BARRIER.defaultBlockState();

    private TestAreaBuilder() {
    }

    /**
     * Fresh area built from the pocket scene's room: floor, walls, ceiling (in the blocks the room settings name),
     * the border (red edges + invisible barrier faces) and the player's blocks.
     */
    static void reset(ServerLevel level, TestArea area, SceneSnapshot room, CompoundTag roomTag) {
        clear(level, area);
        plainsBiome(level, area);
        for (int x = -area.half(); x <= area.half(); x++) {
            for (int z = -area.half(); z <= area.half(); z++) {
                level.setBlock(world(area, x, 0, z), RoomBlocks.floor(roomTag, room.floor(), x, z), FLAGS);
            }
        }
        structure(roomTag, area).forEach((pos, state) -> level.setBlock(pos, state, FLAGS));
        placeBorder(level, area, roomTag);
        Set<String> banned = Set.copyOf(TestServerConfig.get().bannedBlocks);
        room.blocks().forEach((rel, state) -> {
            if (!insideRoom(area, rel) || !allowed(state, banned)) {
                return;
            }
            try {
                level.setBlock(world(area, rel.getX(), rel.getY(), rel.getZ()), state, FLAGS);
            } catch (RuntimeException e) {
                PocketSandbox.LOGGER.warn("Pocket Sandbox: could not place {} in the test world", state, e);
            }
        });
    }

    /**
     * The area's chunks get the plains biome (rain and snow show, normal sky), like /fillbiome. Worlds created
     * before the dimension used plains still have the precipitation-less void biome there.
     */
    private static void plainsBiome(ServerLevel level, TestArea area) {
        try {
            net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> plains = level.registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
                    .getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
            java.util.List<net.minecraft.world.level.chunk.ChunkAccess> chunks = new java.util.ArrayList<>();
            for (int cx = area.markerMinX() >> 4; cx <= area.markerMaxX() >> 4; cx++) {
                for (int cz = area.markerMinZ() >> 4; cz <= area.markerMaxZ() >> 4; cz++) {
                    net.minecraft.world.level.chunk.ChunkAccess chunk = level.getChunk(cx, cz);
                    chunk.fillBiomesFromNoise((x, y, z, sampler) -> plains,
                            level.getChunkSource().randomState().sampler());
                    chunk.setUnsaved(true);
                    chunks.add(chunk);
                }
            }
            level.getChunkSource().chunkMap.resendBiomesForChunks(chunks);
        } catch (RuntimeException e) {
            PocketSandbox.LOGGER.warn("Pocket Sandbox: could not set the test area biome", e);
        }
    }

    /** No command / structure blocks and nothing the server admin banned. */
    private static boolean allowed(BlockState state, Set<String> banned) {
        if (state.getBlock() instanceof GameMasterBlock) {
            return false;
        }
        return !banned.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }

    /** Above the floor, inside the floor square, up to the ceiling layer. */
    private static boolean insideRoom(TestArea area, BlockPos rel) {
        return Math.abs(rel.getX()) <= area.half() && Math.abs(rel.getZ()) <= area.half()
                && rel.getY() >= 1 && rel.getY() <= area.size() + 1;
    }

    private static BlockPos world(TestArea area, int x, int y, int z) {
        return new BlockPos(area.centerX() + x, area.floorY() + y, area.centerZ() + z);
    }

    /** Wall and ceiling blocks of a room (world positions), same shape as in the pocket scene. */
    private static Map<BlockPos, BlockState> structure(CompoundTag room, TestArea area) {
        SceneSnapshot settings = SceneSnapshot.fromTag(room, null);
        Map<BlockPos, BlockState> cells = new HashMap<>();
        BlockState wall = RoomBlocks.wall(room);
        for (int[] p : WallSide.positions(settings.walls(), SandboxBounds.ofSize(area.size()))) {
            cells.put(world(area, p[0], p[1], p[2]), wall);
        }
        if (settings.ceiling()) {
            BlockState ceiling = RoomBlocks.ceiling(room);
            for (int x = -area.half(); x <= area.half(); x++) {
                for (int z = -area.half(); z <= area.half(); z++) {
                    cells.put(world(area, x, ceilingY(area), z), ceiling);
                }
            }
        }
        return cells;
    }

    /**
     * Walls / ceiling changed (which sides, or their block): structure blocks that are still as built go or are
     * swapped for the new block, new cells are filled where empty. Player blocks are never replaced.
     */
    static void rebuildStructure(ServerLevel level, TestArea area, CompoundTag before, CompoundTag after) {
        Map<BlockPos, BlockState> oldCells = structure(before, area);
        Map<BlockPos, BlockState> newCells = structure(after, area);
        oldCells.forEach((pos, state) -> {
            BlockState wanted = newCells.get(pos);
            if (level.getBlockState(pos) == state && wanted != state) {
                level.setBlock(pos, wanted == null ? Blocks.AIR.defaultBlockState() : wanted, Block.UPDATE_ALL);
            }
        });
        newCells.forEach((pos, state) -> {
            if (!oldCells.containsKey(pos) && level.getBlockState(pos).isAir()) {
                level.setBlock(pos, state, Block.UPDATE_ALL);
            }
        });
    }

    /**
     * The floor block changed: floor cells that are still the old floor, or empty (a floor that fell or was
     * broken), become the new one. Blocks the player put into the floor layer stay.
     */
    static void rebuildFloor(ServerLevel level, TestArea area, CompoundTag before, CompoundTag after) {
        FloorType pattern = SceneSnapshot.fromTag(before, null).floor();
        FloorType newPattern = SceneSnapshot.fromTag(after, null).floor();
        for (int x = -area.half(); x <= area.half(); x++) {
            for (int z = -area.half(); z <= area.half(); z++) {
                BlockPos pos = world(area, x, 0, z);
                BlockState old = RoomBlocks.floor(before, pattern, x, z);
                BlockState next = RoomBlocks.floor(after, newPattern, x, z);
                BlockState now = level.getBlockState(pos);
                if (now != next && (now == old || now.isAir())) {
                    level.setBlock(pos, next, Block.UPDATE_ALL);
                }
            }
        }
    }

    /**
     * The room grows or shrinks around the same centre: old border and structure go, everything outside the new
     * border is wiped (shrinking), the floor fills the new square where empty, walls / ceiling and the border are
     * built at the new size. The player's blocks inside the new border stay.
     */
    static void resize(ServerLevel level, TestArea from, TestArea to, CompoundTag before, CompoundTag after) {
        removeBorder(level, from);
        structure(before, from).forEach((pos, state) -> {
            if (level.getBlockState(pos) == state) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        });
        FloorType pattern = SceneSnapshot.fromTag(before, null).floor();
        for (int x = -from.half(); x <= from.half(); x++) {
            for (int z = -from.half(); z <= from.half(); z++) {
                if (Math.abs(x) > to.half() || Math.abs(z) > to.half()) {
                    BlockPos pos = world(from, x, 0, z);
                    if (level.getBlockState(pos) == RoomBlocks.floor(before, pattern, x, z)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                    }
                }
            }
        }
        if (to.size() < from.size()) {
            wipeOutside(level, from, to);
        }
        plainsBiome(level, to);
        FloorType newPattern = SceneSnapshot.fromTag(after, null).floor();
        for (int x = -to.half(); x <= to.half(); x++) {
            for (int z = -to.half(); z <= to.half(); z++) {
                BlockPos pos = world(to, x, 0, z);
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, RoomBlocks.floor(after, newPattern, x, z), FLAGS);
                }
            }
        }
        structure(after, to).forEach((pos, state) -> {
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, state, FLAGS);
            }
        });
        placeBorder(level, to, after);
    }

    /** Everything of the old (bigger) area that is outside the new build box: blocks and loose entities. */
    private static void wipeOutside(ServerLevel level, TestArea from, TestArea to) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = from.markerMinX(); x <= from.markerMaxX(); x++) {
            for (int z = from.markerMinZ(); z <= from.markerMaxZ(); z++) {
                for (int y = from.bottomLimitY(); y <= from.markerTopY(); y++) {
                    if (x >= to.buildMinX() && x <= to.buildMaxX() && z >= to.buildMinZ() && z <= to.buildMaxZ()
                            && y >= to.buildMinY() && y <= to.buildMaxY()) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).isAir()) {
                        continue;
                    }
                    level.removeBlockEntity(pos);
                    level.setBlock(pos, air, FLAGS);
                }
            }
        }
        AABB box = new AABB(from.markerMinX(), from.bottomLimitY(), from.markerMinZ(),
                from.markerMaxX() + 1, from.markerTopY() + 1, from.markerMaxZ() + 1);
        for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
            BlockPos at = entity.blockPosition();
            if (to.isOutside(at.getX(), at.getY(), at.getZ()) || to.isShell(at.getX(), at.getY(), at.getZ())) {
                entity.discard();
            }
        }
    }

    /** Wall and ceiling cells of the room settings {@code room} (session data). */
    static Map<BlockPos, BlockState> structureCells(CompoundTag room, TestArea area) {
        return structure(room, area);
    }

    /** True for a wall or ceiling cell of the room settings {@code room} (session data). */
    static boolean isStructure(CompoundTag room, TestArea area, BlockPos pos) {
        // Worked out from the shape (cheap): this runs for every broken block and every block of an explosion.
        int x = pos.getX() - area.centerX();
        int y = pos.getY() - area.floorY();
        int z = pos.getZ() - area.centerZ();
        int half = area.half();
        if (room.getBoolean("Ceiling") && y == ceilingY(area) && Math.abs(x) <= half && Math.abs(z) <= half) {
            return true;
        }
        if (y < 0 || y > wallTop(area)) {
            return false;
        }
        int walls = room.getInt("Walls");
        boolean north = has(walls, WallSide.NORTH);
        boolean south = has(walls, WallSide.SOUTH);
        boolean west = has(walls, WallSide.WEST);
        boolean east = has(walls, WallSide.EAST);
        int edge = half + 1;
        boolean inX = Math.abs(x) <= half;
        boolean inZ = Math.abs(z) <= half;
        if ((north && z == -edge && inX) || (south && z == edge && inX) || (west && x == -edge && inZ)
                || (east && x == edge && inZ)) {
            return true;
        }
        // Corner columns where two neighbouring walls meet.
        return (z == -edge && ((x == -edge && north && west) || (x == edge && north && east)))
                || (z == edge && ((x == -edge && south && west) || (x == edge && south && east)));
    }

    private static boolean has(int walls, WallSide side) {
        return (walls & (1 << side.ordinal())) != 0;
    }

    /** Highest wall layer (big floors: walls stop at the room height, like in the pocket scene). */
    static int wallTop(TestArea area) {
        return Math.min(area.size(), SandboxBounds.ofSize(area.size()).maxY());
    }

    /** Ceiling layer: one above the top of the walls. */
    static int ceilingY(TestArea area) {
        return wallTop(area) + 1;
    }

    /** True for a floor cell (floor layer inside the floor square). */
    static boolean isFloor(TestArea area, BlockPos pos) {
        return pos.getY() == area.floorY() && pos.getX() >= area.minX() && pos.getX() <= area.maxX()
                && pos.getZ() >= area.minZ() && pos.getZ() <= area.maxZ();
    }

    /**
     * The room as the player leaves it: same settings and every block the player built anywhere inside the
     * border (walls / ceiling excluded). When blocks reach past the floor square the pocket scene grows to the
     * smallest size that holds them (up to the largest); blocks below or beside the floor layer cannot be shown
     * there and are left out.
     */
    static byte[] captureBytes(ServerLevel level, TestArea area, CompoundTag roomSettings) {
        SceneSnapshot settings = SceneSnapshot.fromTag(roomSettings, null);
        Map<BlockPos, BlockState> structure = structure(roomSettings, area);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int reach = area.half();
        for (int x = area.buildMinX(); x <= area.buildMaxX(); x++) {
            for (int z = area.buildMinZ(); z <= area.buildMaxZ(); z++) {
                for (int y = area.floorY() + 1; y <= area.buildMaxY(); y++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state == structure.get(pos)) {
                        continue;
                    }
                    int rx = x - area.centerX();
                    int rz = z - area.centerZ();
                    blocks.put(new BlockPos(rx, y - area.floorY(), rz), state);
                    reach = Math.max(reach, Math.max(Math.abs(rx), Math.abs(rz)));
                }
            }
        }
        int size = sceneSize(Math.max(area.size(), reach * 2 + 1));
        SceneSnapshot room = new SceneSnapshot(size, settings.floor(), settings.walls(), settings.ceiling(),
                settings.breakable(), blocks);
        try {
            return room.toBytes();
        } catch (IOException e) {
            PocketSandbox.LOGGER.warn("Pocket Sandbox: could not pack the test room", e);
            return null;
        }
    }

    /** Smallest pocket scene size that holds {@code needed} (the largest one when nothing does). */
    private static int sceneSize(int needed) {
        for (int size : SandboxBounds.SIZES) {
            if (size >= needed) {
                return size;
            }
        }
        return SandboxBounds.SIZES[SandboxBounds.SIZES.length - 1];
    }

    /** Red glass on the edges of the border box; old barrier blocks on its faces are cleared. */
    /** Room setting: the red edges are hidden (only the invisible wall is left). */
    static final String BORDER_HIDDEN = "BorderHidden";

    /** Shows or hides the red edges of an area right away (the setting changed). */
    static void setEdgesVisible(ServerLevel level, TestArea area, boolean visible) {
        if (visible) {
            placeBorder(level, area, new CompoundTag());
        } else {
            removeBorder(level, area);
        }
    }

    private static void placeBorder(ServerLevel level, TestArea area, CompoundTag room) {
        boolean edges = !room.getBoolean(BORDER_HIDDEN) && !room.getBoolean(TestSessions.UNLIMITED);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        forEachShell(area, (x, y, z) -> {
            if (edges && area.isMarker(x, y, z)) {
                level.setBlock(pos.set(x, y, z), MARKER, FLAGS);
            } else if (level.getBlockState(pos.set(x, y, z)) == BARRIER) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        });
    }

    private static void removeBorder(ServerLevel level, TestArea area) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        forEachShell(area, (x, y, z) -> {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (state == MARKER || state == BARRIER) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        });
    }

    private interface CellAction {
        void at(int x, int y, int z);
    }

    /** Every cell on the six faces of the border box, once each. */
    private static void forEachShell(TestArea area, CellAction action) {
        int x0 = area.markerMinX();
        int x1 = area.markerMaxX();
        int z0 = area.markerMinZ();
        int z1 = area.markerMaxZ();
        int y0 = area.bottomLimitY();
        int y1 = area.markerTopY();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                action.at(x, y0, z);
                action.at(x, y1, z);
            }
        }
        for (int y = y0 + 1; y < y1; y++) {
            for (int x = x0; x <= x1; x++) {
                action.at(x, y, z0);
                action.at(x, y, z1);
            }
            for (int z = z0 + 1; z < z1; z++) {
                action.at(x0, y, z);
                action.at(x1, y, z);
            }
        }
    }

    /**
     * Wipes every block in the columns x0..x1, z0..z1 (what an unlimited area used). Only chunk sections that hold
     * blocks are visited, so a big empty region costs little.
     */
    static void clearColumns(ServerLevel level, int x0, int z0, int x1, int z1) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(cx, cz);
                net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    if (sections[i].hasOnlyAir()) {
                        continue;
                    }
                    int baseY = level.getSectionYFromSectionIndex(i) << 4;
                    for (int y = baseY; y < baseY + 16; y++) {
                        for (int x = cx << 4; x < (cx << 4) + 16; x++) {
                            for (int z = cz << 4; z < (cz << 4) + 16; z++) {
                                pos.set(x, y, z);
                                if (!level.getBlockState(pos).isAir()) {
                                    level.removeBlockEntity(pos);
                                    level.setBlock(pos, air, FLAGS);
                                }
                            }
                        }
                    }
                }
            }
        }
        AABB box = new AABB(x0, TestArea.WORLD_MIN_Y - 1, z0, x1 + 1, TestArea.WORLD_MAX_Y + 2, z1 + 1);
        for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
            entity.discard();
        }
    }

    /** Removes every block and non-player entity of the area (the unsaved test world is gone). */
    static void clear(ServerLevel level, TestArea area) {
        SableCompat.removeShips(level, area);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = area.markerMinX(); x <= area.markerMaxX(); x++) {
            for (int z = area.markerMinZ(); z <= area.markerMaxZ(); z++) {
                // A little below the border too (fluids cannot leave it any more; this catches older leftovers).
                for (int y = Math.max(level.getMinBuildHeight(), area.bottomLimitY() - 16); y <= area.markerTopY();
                        y++) {
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).isAir()) {
                        continue;
                    }
                    level.removeBlockEntity(pos);
                    level.setBlock(pos, air, FLAGS);
                }
            }
        }
        AABB box = new AABB(area.markerMinX() - 4, area.bottomLimitY() - 16, area.markerMinZ() - 4,
                area.markerMaxX() + 5, area.markerTopY() + 16, area.markerMaxZ() + 5);
        List<Entity> loose = level.getEntities((Entity) null, box, entity -> !(entity instanceof Player));
        for (Entity entity : loose) {
            entity.discard();
        }
    }

    /** Standing spot at the centre: on top of whatever is built there, or on the floor. */
    static double[] safeCentre(ServerLevel level, TestArea area) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(area.centerX(), area.maxY(), area.centerZ());
        int top = area.floorY();
        // Highest solid block below the ceiling layer.
        for (int y = area.maxY() - 1; y >= area.floorY(); y--) {
            pos.setY(y);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                top = y;
                break;
            }
        }
        return new double[] {area.centerX() + 0.5, Math.min(top + 1, area.maxY() - 1), area.centerZ() + 0.5};
    }
}
