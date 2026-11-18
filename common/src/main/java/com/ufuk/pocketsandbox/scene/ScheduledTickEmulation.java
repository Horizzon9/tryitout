package com.ufuk.pocketsandbox.scene;

import com.ufuk.pocketsandbox.world.SandboxLogicLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Scheduled block ticks need a {@code ServerLevel} in 1.21.1, which a client-only scene cannot have. This
 * re-implements the scheduled tick of the common vanilla redstone parts (lamp turning off, redstone torch,
 * repeater) against the logic level. Every other scheduled tick is reported as skipped.
 */
final class ScheduledTickEmulation {
    private ScheduledTickEmulation() {
    }

    /** Runs the tick if this block is emulated; false when it was skipped. */
    static boolean run(SandboxLogicLevel level, BlockPos pos, Block type) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(type)) {
            return true; // block changed meanwhile: vanilla drops the tick too
        }
        if (type instanceof RedstoneLampBlock) {
            if (state.getValue(RedstoneLampBlock.LIT) && !level.hasNeighborSignal(pos)) {
                level.setBlock(pos, state.cycle(RedstoneLampBlock.LIT), Block.UPDATE_CLIENTS);
            }
            return true;
        }
        if (type instanceof RedstoneTorchBlock) {
            Direction toSupport = type instanceof RedstoneWallTorchBlock
                    ? state.getValue(RedstoneWallTorchBlock.FACING).getOpposite()
                    : Direction.DOWN;
            boolean shouldBeLit = !level.hasSignal(pos.relative(toSupport), toSupport);
            if (state.getValue(RedstoneTorchBlock.LIT) != shouldBeLit) {
                level.setBlock(pos, state.setValue(RedstoneTorchBlock.LIT, shouldBeLit), Block.UPDATE_ALL);
            }
            return true;
        }
        if (type instanceof RepeaterBlock) {
            if (state.getValue(RepeaterBlock.LOCKED)) {
                return true;
            }
            boolean powered = state.getValue(BlockStateProperties.POWERED);
            boolean input = repeaterInput(level, pos, state) > 0;
            if (powered && !input) {
                level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, false), Block.UPDATE_CLIENTS);
            } else if (!powered) {
                level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, true), Block.UPDATE_CLIENTS);
                if (!input) {
                    level.scheduleTick(pos, type, 2 * state.getValue(RepeaterBlock.DELAY));
                }
            }
            return true;
        }
        return false;
    }

    /** Same input rule as {@code DiodeBlock#getInputSignal}: the block behind, wire counted directly. */
    private static int repeaterInput(SandboxLogicLevel level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(DiodeBlock.FACING);
        BlockPos behind = pos.relative(facing);
        int signal = level.getSignal(behind, facing);
        if (signal >= 15) {
            return signal;
        }
        BlockState behindState = level.getBlockState(behind);
        int wire = behindState.is(net.minecraft.world.level.block.Blocks.REDSTONE_WIRE)
                ? behindState.getValue(RedStoneWireBlock.POWER) : 0;
        return Math.max(signal, wire);
    }
}
