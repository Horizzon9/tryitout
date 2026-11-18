package com.ufuk.pocketsandbox.world;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The single source of truth of the scene: block states, block entities and the box they live in. Every
 * level facade reads and writes here. Not thread safe: render thread only.
 *
 * <p>Rule 1 (BE level ownership): every block entity stored here is bound to the client-side
 * {@link SandboxLevel}, whichever level created it.
 */
public final class SandboxWorld {
    private final Map<BlockPos, BlockState> states = new HashMap<>();
    private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
    /** Decorations placed in the scene (paintings, item frames, armour stands); shared by both levels. */
    private final SandboxEntities entities = new SandboxEntities();

    public SandboxEntities entities() {
        return entities;
    }

    /** Virtual redstone power (1-15) fed into a block, as if every neighbour emitted it towards it. */
    private final Map<BlockPos, Integer> power = new HashMap<>();
    /** Scene game time, advanced by the scene ticker; both level facades report it. */
    private long gameTime;
    private SandboxBounds bounds;
    @Nullable
    private SandboxLevel owner;
    /** Bumped on every change, so renderers know when to rebuild their meshes. */
    private int version;
    /** Block and sky light of the box, shared by both level facades. */
    private final com.ufuk.pocketsandbox.world.light.SceneLighting lighting =
            new com.ufuk.pocketsandbox.world.light.SceneLighting();

    public SandboxWorld(SandboxBounds bounds) {
        this.bounds = bounds;
    }

    void attach(SandboxLevel level) {
        this.owner = level;
    }

    public SandboxBounds bounds() {
        return bounds;
    }

    public com.ufuk.pocketsandbox.world.light.SceneLighting lighting() {
        return lighting;
    }

    public int power(BlockPos pos) {
        Integer value = power.get(pos);
        return value == null ? 0 : value;
    }

    /** Sets the virtual power fed into {@code pos} (0 removes it). */
    public void setPower(BlockPos pos, int value) {
        int clamped = Math.max(0, Math.min(15, value));
        if (clamped == 0) {
            power.remove(pos);
        } else {
            power.put(pos.immutable(), clamped);
        }
    }

    public Map<BlockPos, Integer> powered() {
        return Collections.unmodifiableMap(power);
    }

    /** Receives particles emitted by blocks; set by the scene (no-op until then). */
    public interface ParticleSink {
        void emit(net.minecraft.core.particles.ParticleOptions options, double x, double y, double z, double vx,
                double vy, double vz);
    }

    private ParticleSink particleSink = (options, x, y, z, vx, vy, vz) -> {
    };

    public void setParticleSink(ParticleSink sink) {
        this.particleSink = sink;
    }

    public void emitParticle(net.minecraft.core.particles.ParticleOptions options, double x, double y, double z,
            double vx, double vy, double vz) {
        particleSink.emit(options, x, y, z, vx, vy, vz);
    }

    public long gameTime() {
        return gameTime;
    }

    public void advanceTime() {
        gameTime++;
    }

    public int version() {
        return version;
    }

    public void markChanged() {
        version++;
    }

    /** Cells outside the floor box that may hold blocks too (the test walls beside the floor). */
    private final java.util.Set<BlockPos> extraCells = new java.util.HashSet<>();

    /** True when a block may be stored at the position: inside the box or on a wall cell. */
    public boolean accepts(BlockPos pos) {
        return bounds.contains(pos.getX(), pos.getY(), pos.getZ()) || extraCells.contains(pos);
    }

    /** True for a wall cell outside the floor box. */
    public boolean isExtraCell(BlockPos pos) {
        return extraCells.contains(pos);
    }

    /** Replaces the wall cells; blocks left on cells that are no longer allowed are dropped. */
    public void setExtraCells(java.util.Collection<BlockPos> cells) {
        extraCells.clear();
        for (BlockPos cell : cells) {
            extraCells.add(cell.immutable());
        }
        states.keySet().removeIf(pos -> !accepts(pos));
        blockEntities.entrySet().removeIf(entry -> {
            if (accepts(entry.getKey())) {
                return false;
            }
            entry.getValue().setRemoved();
            return true;
        });
        version++;
    }

    public BlockState getState(BlockPos pos) {
        BlockState state = states.get(pos);
        return state == null ? Blocks.AIR.defaultBlockState() : state;
    }

    /** Stores {@code state}; returns false outside the box. Air removes the entry. */
    public boolean setState(BlockPos pos, BlockState state) {
        if (!accepts(pos)) {
            return false;
        }
        BlockPos key = pos.immutable();
        if (state.isAir()) {
            states.remove(key);
        } else {
            states.put(key, state);
        }
        version++;
        return true;
    }

    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos) {
        return blockEntities.get(pos);
    }

    /** Stores {@code be} and binds it to the client sandbox level (rule 1). */
    public void putBlockEntity(BlockEntity be) {
        BlockPos key = be.getBlockPos().immutable();
        if (!accepts(key)) {
            return;
        }
        if (owner != null && be.getLevel() != owner) {
            be.setLevel(owner);
        }
        BlockEntity previous = blockEntities.put(key, be);
        if (previous != null && previous != be) {
            previous.setRemoved();
        }
        version++;
    }

    public void removeBlockEntity(BlockPos pos) {
        BlockEntity removed = blockEntities.remove(pos);
        if (removed != null) {
            removed.setRemoved();
            version++;
        }
    }

    public Map<BlockPos, BlockState> states() {
        return Collections.unmodifiableMap(states);
    }

    public Map<BlockPos, BlockEntity> blockEntities() {
        return Collections.unmodifiableMap(blockEntities);
    }

    /** Changes the floor size; blocks outside the new box are dropped. */
    public void resize(SandboxBounds newBounds) {
        this.bounds = newBounds;
        states.keySet().removeIf(pos -> !newBounds.contains(pos.getX(), pos.getY(), pos.getZ()));
        power.keySet().removeIf(pos -> !newBounds.contains(pos.getX(), pos.getY(), pos.getZ()));
        blockEntities.entrySet().removeIf(entry -> {
            BlockPos pos = entry.getKey();
            if (newBounds.contains(pos.getX(), pos.getY(), pos.getZ())) {
                return false;
            }
            entry.getValue().setRemoved();
            return true;
        });
        version++;
    }

    /** Marks every block entity removed; called when the scene closes. */
    public void dispose() {
        blockEntities.values().forEach(BlockEntity::setRemoved);
        blockEntities.clear();
        states.clear();
        extraCells.clear();
        power.clear();
        entities.clear();
        version++;
    }
}
