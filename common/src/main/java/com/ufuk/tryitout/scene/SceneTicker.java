package com.ufuk.tryitout.scene;

import com.ufuk.tryitout.safety.FaultRegistry;
import com.ufuk.tryitout.safety.Safe;
import com.ufuk.tryitout.world.AbstractSandboxLevel;
import com.ufuk.tryitout.world.SandboxLevel;
import com.ufuk.tryitout.world.SandboxLogicLevel;
import com.ufuk.tryitout.world.SandboxWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.ticks.ScheduledTick;

/**
 * The scene's 20 TPS loop, driven by the screen's tick: advances the scene clock, runs due scheduled ticks
 * (emulated, see {@link ScheduledTickEmulation}), client block-entity tickers (never server ones),
 * {@code animateTick} and particles. Every step is guarded; a failing block entity or block stops ticking
 * and stays static.
 */
public final class SceneTicker {
    /** Chance per block per tick to call animateTick (vanilla picks random nearby positions each tick). */
    private static final int ANIMATE_ONE_IN = 8;

    private final SandboxScene scene;
    private final RandomSource random = RandomSource.create();
    private int skippedTicks;

    SceneTicker(SandboxScene scene) {
        this.scene = scene;
    }

    /** Scheduled ticks that needed a server and could not be emulated. */
    public int skippedTicks() {
        return skippedTicks;
    }

    public void tick() {
        SandboxWorld world = scene.world();
        world.advanceTime();
        scene.level().syncTime();
        scene.logic().syncTime();
        if (scene.view() != null) {
            scene.view().setGameTime(world.gameTime());
        }
        // Every stage is guarded on its own: a failure in one never stops the others or reaches the game.
        Safe.attempt(scene.faults(), "stage-scheduled-ticks", () -> runScheduledTicks(world.gameTime()));
        var config = com.ufuk.tryitout.config.SandboxConfig.get();
        if (config.blockEntityTicks) {
            Safe.attempt(scene.faults(), "stage-block-entities", this::tickBlockEntities);
        }
        if (config.animateTicks) {
            Safe.attempt(scene.faults(), "stage-animate", this::animate);
        }
        Safe.attempt(scene.faults(), "stage-particles", scene.particles()::tick);
        WeaponRig rig = scene.rig();
        if (rig != null && scene.weaponMode()) {
            Safe.run(scene.faults(), "weapon-tick", () -> rig.tick(world.gameTime()));
        }
    }

    private void runScheduledTicks(long gameTime) {
        SandboxLogicLevel logic = scene.logic();
        for (ScheduledTick<Block> due : logic.sandboxBlockTicks().drain(gameTime)) {
            BlockPos pos = due.pos();
            boolean[] handled = {true};
            Safe.attempt(scene.faults(), "tick@" + AbstractSandboxLevel.posKey(pos),
                    () -> handled[0] = ScheduledTickEmulation.run(logic, pos, due.type()));
            if (!handled[0]) {
                skippedTicks++;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void tickBlockEntities() {
        SandboxLevel level = scene.level();
        FaultRegistry faults = scene.faults();
        for (BlockEntity be : new ArrayList<>(scene.world().blockEntities().values())) {
            if (be.isRemoved()) {
                continue;
            }
            BlockPos pos = be.getBlockPos();
            Safe.run(faults, "be-tick@" + AbstractSandboxLevel.posKey(pos), () -> {
                BlockState state = be.getBlockState();
                BlockEntityTicker<BlockEntity> ticker = state.getTicker(level, (BlockEntityType<BlockEntity>) be.getType());
                if (ticker != null) {
                    ticker.tick(level, pos, state, be);
                }
            });
        }
    }

    private void animate() {
        SandboxLevel level = scene.level();
        FaultRegistry faults = scene.faults();
        List<Map.Entry<BlockPos, BlockState>> blocks = new ArrayList<>(scene.world().states().entrySet());
        for (Map.Entry<BlockPos, BlockState> entry : blocks) {
            if (random.nextInt(ANIMATE_ONE_IN) != 0) {
                continue;
            }
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();
            Safe.run(faults, "animate@" + AbstractSandboxLevel.posKey(pos),
                    () -> state.getBlock().animateTick(state, level, pos, random));
        }
    }
}
