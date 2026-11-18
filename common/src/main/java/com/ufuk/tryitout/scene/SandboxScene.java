package com.ufuk.tryitout.scene;

import com.ufuk.tryitout.render.SandboxParticles;
import com.ufuk.tryitout.safety.FaultRegistry;
import com.ufuk.tryitout.safety.Safe;
import com.ufuk.tryitout.world.SandboxBounds;
import com.ufuk.tryitout.world.SandboxClientView;
import com.ufuk.tryitout.world.SandboxLevel;
import com.ufuk.tryitout.world.SandboxLogicLevel;
import com.ufuk.tryitout.world.SandboxPlayer;
import com.ufuk.tryitout.world.SandboxWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * One open sandbox: the data, both level facades, the scene player, the editor and the fault list. Owned by
 * the screen and disposed with it; nothing here is static.
 */
public final class SandboxScene {
    /** The floor block under the first placement: centre of the floor. */
    public static final BlockPos FLOOR_CENTRE = BlockPos.ZERO;

    private final ItemStack target;
    private final FaultRegistry faults = new FaultRegistry();
    private final SandboxWorld world;
    private final SandboxLevel level;
    private final SandboxLogicLevel logic;
    private final SandboxPlayer player;
    private final SceneEditor editor;
    private final SceneTicker ticker;
    private final SandboxParticles particles;
    private final boolean block;
    @org.jetbrains.annotations.Nullable
    private final SandboxClientView view;
    @org.jetbrains.annotations.Nullable
    private WeaponRig rig;
    private boolean weaponMode;
    private final java.util.EnumSet<WallSide> walls = java.util.EnumSet.noneOf(WallSide.class);
    private boolean wallBreakable;
    private FloorType floor = FloorType.PONDER;
    private boolean ceiling;
    private static final BlockState WALL_BLOCK = Blocks.STONE_BRICKS.defaultBlockState();
    /** Glass: hanging blocks attach to it like to stone, and the camera still sees the room from above. */
    private static final BlockState CEILING_BLOCK = Blocks.GLASS.defaultBlockState();

    private SandboxScene(ItemStack target, SandboxBounds bounds) {
        this(target, bounds, null);
    }

    private SandboxScene(ItemStack target, SandboxBounds bounds,
            @org.jetbrains.annotations.Nullable com.ufuk.tryitout.testworld.SceneSnapshot restore) {
        this.target = target;
        this.world = new SandboxWorld(bounds);
        this.level = SandboxLevel.create(world, faults);
        this.logic = SandboxLogicLevel.create(world, faults);
        this.player = new SandboxPlayer(logic);
        this.editor = new SceneEditor(this);
        this.ticker = new SceneTicker(this);
        this.view = Safe.get(faults, "client-view", () -> SandboxClientView.create(level), null);
        this.particles = new SandboxParticles(view);
        world.setParticleSink(particles::add);
        // Paintings and item frames are placed like blocks (on the wall), not tested as weapons.
        this.block = target.getItem() instanceof BlockItem
                || target.getItem() instanceof net.minecraft.world.item.HangingEntityItem;
        if (restore != null) {
            restoreFrom(restore);
        } else {
            buildFloor();
            placeTarget();
        }
        if (!block) {
            setWeaponMode(true);
        }
    }

    /** Reopens a room as it was left in the test world (blocks only; the scene has no server logic). */
    public static SandboxScene restore(ItemStack target, com.ufuk.tryitout.testworld.SceneSnapshot snapshot) {
        return new SandboxScene(target, SandboxBounds.ofSize(snapshot.size()), snapshot);
    }

    private void restoreFrom(com.ufuk.tryitout.testworld.SceneSnapshot snapshot) {
        floor = snapshot.floor();
        walls.addAll(snapshot.walls());
        ceiling = snapshot.ceiling();
        buildFloor();
        placeWall();
        int flags = net.minecraft.world.level.block.Block.UPDATE_CLIENTS
                | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE;
        snapshot.blocks().forEach((pos, state) -> {
            if (world.bounds().editable(pos.getX(), pos.getY(), pos.getZ()) && !world.isExtraCell(pos)) {
                Safe.attempt(faults, "restore@" + SandboxLevel.posKey(pos), () -> editLevel().setBlock(pos, state, flags));
            }
        });
    }

    /** The room for the test world: settings plus every block the player built (not floor, walls, ceiling). */
    public com.ufuk.tryitout.testworld.SceneSnapshot snapshot() {
        java.util.Map<BlockPos, BlockState> built = new java.util.HashMap<>();
        SandboxBounds bounds = world.bounds();
        world.states().forEach((pos, state) -> {
            if (!bounds.editable(pos.getX(), pos.getY(), pos.getZ()) || world.isExtraCell(pos)) {
                return;
            }
            built.put(pos.immutable(), state);
        });
        return new com.ufuk.tryitout.testworld.SceneSnapshot(bounds.size(), floor,
                java.util.EnumSet.copyOf(walls.isEmpty() ? java.util.EnumSet.noneOf(WallSide.class) : walls),
                ceiling, true, built);
    }

    public static SandboxScene open(ItemStack target) {
        return new SandboxScene(target, SandboxBounds.ofSize(com.ufuk.tryitout.config.SandboxConfig.get().defaultFloorSize));
    }

    public ItemStack target() {
        return target;
    }

    public boolean isBlock() {
        return block;
    }

    public SandboxWorld world() {
        return world;
    }

    /** Client-side level: rendering and client ticks. */
    public SandboxLevel level() {
        return level;
    }

    /** Server-side level: block logic only. */
    public SandboxLogicLevel logic() {
        return logic;
    }

    /**
     * Where edits run: the logic level (server-side block logic), or the client level when
     * {@code logicSimulation} is off in the config (safe mode for packs whose server logic misbehaves).
     */
    public com.ufuk.tryitout.world.AbstractSandboxLevel editLevel() {
        return com.ufuk.tryitout.config.SandboxConfig.get().logicSimulation ? logic : level;
    }

    public SandboxPlayer player() {
        return player;
    }

    public SceneEditor editor() {
        return editor;
    }

    /** The ClientLevel view (particles, preview entities); null when it could not be built. */
    @org.jetbrains.annotations.Nullable
    public SandboxClientView view() {
        return view;
    }

    /** Weapon mode needs the client view (for the preview player and the dummy). */
    public boolean weaponAvailable() {
        return view != null;
    }

    public boolean weaponMode() {
        return weaponMode && rig != null;
    }

    @org.jetbrains.annotations.Nullable
    public WeaponRig rig() {
        return rig;
    }

    /** Switches between block editing and weapon testing; weapon mode widens the floor to 9x9. */
    public void setWeaponMode(boolean on) {
        if (!on || view == null) {
            weaponMode = false;
            return;
        }
        if (world.bounds().size() < 9) {
            resizeTo(SandboxBounds.ofSize(9));
        }
        if (rig == null) {
            rig = Safe.get(faults, "weapon-rig", () -> WeaponRig.create(view, target, player, particles), null);
        }
        weaponMode = rig != null;
    }

    public SceneTicker ticker() {
        return ticker;
    }

    public SandboxParticles particles() {
        return particles;
    }

    /**
     * Sets the virtual redstone power fed into {@code pos} and lets the block react (on the logic level, so
     * lamps, doors, pistons and wire update as they would on a server).
     */
    public void setPower(BlockPos pos, int value) {
        // The floor may be powered too: thin parts (dust, rails) are often hard to hit, the block under them is not.
        if (!world.bounds().contains(pos.getX(), pos.getY(), pos.getZ())) {
            return;
        }
        world.setPower(pos, value);
        Safe.attempt(faults, "power@" + SandboxLevel.posKey(pos), () -> {
            editLevel().neighborChanged(pos, Blocks.REDSTONE_BLOCK, pos);
            editLevel().updateNeighborsAt(pos, Blocks.REDSTONE_BLOCK);
        });
    }

    public FaultRegistry faults() {
        return faults;
    }

    /** Cycles 5 → 7 → 9 → 5; rebuilds the floor (and wall), keeps the blocks that still fit. */
    public void cycleSize() {
        resizeTo(world.bounds().nextSize());
    }

    /** Floor size picked from the size list (one of {@link SandboxBounds#SIZES}). */
    public void setSize(int size) {
        if (size != world.bounds().size()) {
            resizeTo(SandboxBounds.ofSize(size));
        }
    }

    private void resizeTo(SandboxBounds bounds) {
        removeWall();
        world.resize(bounds);
        buildFloor();
        placeWall();
    }

    public boolean hasWall(WallSide side) {
        return walls.contains(side);
    }

    public boolean hasAnyWall() {
        return !walls.isEmpty();
    }

    /** Adds or removes the wall on one side; other sides stay as they are. */
    public void toggleWall(WallSide side) {
        removeWall();
        if (!walls.remove(side)) {
            walls.add(side);
        }
        placeWall();
    }

    public boolean hasCeiling() {
        return ceiling;
    }

    /** Adds or removes the glass ceiling one block above the walls' top. */
    public void toggleCeiling() {
        removeWall();
        ceiling = !ceiling;
        placeWall();
    }

    /** y of the ceiling layer: just above a wall as tall as the floor is wide. */
    /** One above the top of the walls (walls of big floors stop at the room height). */
    private int ceilingY() {
        return Math.min(world.bounds().minY() + world.bounds().size(), world.bounds().maxY()) + 1;
    }

    /** Whether the player may break (and rebuild) wall blocks. */
    public boolean wallBreakable() {
        return wallBreakable;
    }

    public void setWallBreakable(boolean breakable) {
        wallBreakable = breakable;
    }

    /**
     * True when the player may break / rotate the block: above the floor, or on a wall cell while walls are
     * breakable.
     */
    public boolean canEdit(BlockPos pos) {
        if (world.isExtraCell(pos)) {
            return true; // walls and ceiling can always be broken (and rebuilt)
        }
        return world.bounds().editable(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Every wall and ceiling cell with the block it is built from. */
    private java.util.Map<BlockPos, BlockState> structureCells() {
        java.util.Map<BlockPos, BlockState> cells = new java.util.LinkedHashMap<>();
        for (int[] p : WallSide.positions(walls, world.bounds())) {
            cells.put(new BlockPos(p[0], p[1], p[2]), WALL_BLOCK);
        }
        if (ceiling) {
            SandboxBounds bounds = world.bounds();
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    cells.put(new BlockPos(x, ceilingY(), z), CEILING_BLOCK);
                }
            }
        }
        return cells;
    }

    private void placeWall() {
        java.util.Map<BlockPos, BlockState> cells = structureCells();
        world.setExtraCells(cells.keySet());
        cells.forEach((pos, state) -> {
            if (world.getState(pos).isAir()) {
                world.setState(pos, state);
            }
        });
    }

    public FloorType floor() {
        return floor;
    }

    /** Rebuilds the floor layer from another block; what stands on it stays. */
    public void setFloor(FloorType type) {
        floor = type;
        buildFloor();
    }

    /**
     * Removes only the wall's own blocks (anything the player put in its place stays), through the edit level
     * so attached vines / moss / torches update; paintings and frames left without support are taken down.
     */
    private void removeWall() {
        for (var cell : structureCells().entrySet()) {
            BlockPos pos = cell.getKey();
            if (world.getState(pos) == cell.getValue()) {
                Safe.attempt(faults, "wall@" + SandboxLevel.posKey(pos),
                        () -> editLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL));
            }
        }
        for (net.minecraft.world.entity.Entity entity : world.entities().getAll()) {
            if (entity instanceof net.minecraft.world.entity.decoration.HangingEntity hanging) {
                Safe.attempt(faults, "hanging-check", () -> {
                    if (!hanging.survives()) {
                        world.entities().remove(hanging);
                    }
                });
            }
        }
        // Whatever the player built on the wall cells goes with them.
        world.setExtraCells(java.util.List.of());
    }

    /** Clears everything above the floor and places the target block again. */
    public void reset() {
        world.dispose();
        logic.sandboxBlockTicks().clear();
        particles.clear();
        faults.clear();
        buildFloor();
        placeWall();
        placeTarget();
    }

    private void buildFloor() {
        SandboxBounds bounds = world.bounds();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                world.setState(new BlockPos(x, bounds.minY(), z), floor.block(x, z));
            }
        }
    }

    /**
     * Places the hovered block the way a player would: a click on top of the floor centre, looking from the
     * default camera. Doors, beds and tall plants get their second half from their own placement logic.
     */
    private void placeTarget() {
        if (!block) {
            return;
        }
        SandboxCamera camera = new SandboxCamera(SandboxCamera.distanceFor(world.bounds().size()));
        Vector3f eye = SceneMath.eye(SceneMath.view(camera, world.bounds().centerY()));
        Vec3 click = new Vec3(FLOOR_CENTRE.getX() + 0.5, FLOOR_CENTRE.getY() + 1.0, FLOOR_CENTRE.getZ() + 0.5);
        editor.aim(eye, click);
        int entitiesBefore = entityCount();
        BlockHitResult hit = new BlockHitResult(click, Direction.UP, FLOOR_CENTRE, false);
        // Sneaking: never "use" the floor block, always place.
        Safe.attempt(faults, "place-target", () -> editor.useOrPlace(hit, target, true));
        BlockPos above = FLOOR_CENTRE.above();
        if (placed(above, entitiesBefore)) {
            return;
        }
        // Seeds, saplings, flowers, chorus, nether wart... cannot stand on the checkerboard: try a floor they can.
        if (floor == FloorType.PONDER || floor == FloorType.STONE) {
            FloorType checkerboard = floor;
            for (FloorType type : FloorType.FALLBACKS) {
                setFloor(type);
                Safe.attempt(faults, "place-target", () -> editor.useOrPlace(hit, target, true));
                if (placed(above, entitiesBefore)) {
                    return;
                }
            }
            setFloor(checkerboard);
        }
        // Vines, ladders, paintings, frames... only go on a wall: open the south wall and click its inner face.
        boolean hadWall = walls.contains(WallSide.SOUTH);
        if (!hadWall) {
            toggleWall(WallSide.SOUTH);
        }
        BlockPos wallBlock = new BlockPos(FLOOR_CENTRE.getX(), world.bounds().minY() + 2, world.bounds().maxZ() + 1);
        if (placeAgainst(eye, wallBlock, Direction.NORTH, entitiesBefore)) {
            return;
        }
        if (!hadWall) {
            toggleWall(WallSide.SOUTH);
        }
        // Spore blossoms, hanging roots, hanging signs... only hang from a ceiling.
        boolean hadCeiling = ceiling;
        if (!hadCeiling) {
            toggleCeiling();
        }
        BlockPos ceilingBlock = new BlockPos(FLOOR_CENTRE.getX(), ceilingY(), FLOOR_CENTRE.getZ());
        if (placeAgainst(eye, ceilingBlock, Direction.DOWN, entitiesBefore)) {
            return;
        }
        if (!hadCeiling) {
            toggleCeiling();
        }
    }

    /** Clicks {@code face} of {@code support}; true when the target ended up in front of it. */
    private boolean placeAgainst(Vector3f eye, BlockPos support, Direction face, int entitiesBefore) {
        Vec3 click = Vec3.atCenterOf(support).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        editor.aim(eye, click);
        BlockHitResult hit = new BlockHitResult(click, face, support, false);
        Safe.attempt(faults, "place-target", () -> editor.useOrPlace(hit, target, true));
        return placed(support.relative(face), entitiesBefore);
    }

    /** The target landed: a block in the cell, or a new decoration entity (painting, frame). */
    private boolean placed(BlockPos cell, int entitiesBefore) {
        return !world.getState(cell).isAir() || entityCount() > entitiesBefore;
    }

    private int entityCount() {
        int n = 0;
        for (var ignored : world.entities().getAll()) {
            n++;
        }
        return n;
    }

    public void dispose() {
        world.setParticleSink((options, x, y, z, vx, vy, vz) -> {
        });
        particles.clear();
        logic.sandboxBlockTicks().clear();
        world.dispose();
        level.sandboxEntities().clear();
        logic.sandboxEntities().clear();
    }
}
