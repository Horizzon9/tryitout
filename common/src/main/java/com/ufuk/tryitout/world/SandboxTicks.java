package com.ufuk.tryitout.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.ScheduledTick;

/**
 * Scheduled block ticks of the logic level, drained by the scene ticker. Same ordering and de-duplication
 * as vanilla. Vanilla runs them with a {@code ServerLevel}, which a client-only scene does not have, so the
 * ticker emulates the common redstone ones and counts the rest as skipped.
 */
public final class SandboxTicks<T> implements LevelTickAccess<T> {
    private static final int LIMIT = 4096;

    private final PriorityQueue<ScheduledTick<T>> queue = new PriorityQueue<>(ScheduledTick.DRAIN_ORDER);
    private final Set<Key<T>> pending = new HashSet<>();
    private long currentTick = Long.MIN_VALUE;

    private record Key<T>(T type, BlockPos pos) {
    }

    @Override
    public void schedule(ScheduledTick<T> tick) {
        if (queue.size() >= LIMIT) {
            return;
        }
        Key<T> key = new Key<>(tick.type(), tick.pos().immutable());
        if (pending.add(key)) {
            queue.add(tick);
        }
    }

    @Override
    public boolean hasScheduledTick(BlockPos pos, T type) {
        return pending.contains(new Key<>(type, pos));
    }

    @Override
    public boolean willTickThisTick(BlockPos pos, T type) {
        for (ScheduledTick<T> tick : queue) {
            if (tick.triggerTick() <= currentTick && tick.pos().equals(pos) && tick.type() == type) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int count() {
        return queue.size();
    }

    /** Removes and returns every tick due at {@code gameTime}, in vanilla order. */
    public List<ScheduledTick<T>> drain(long gameTime) {
        currentTick = gameTime;
        List<ScheduledTick<T>> due = new ArrayList<>();
        while (!queue.isEmpty() && queue.peek().triggerTick() <= gameTime) {
            ScheduledTick<T> tick = queue.poll();
            pending.remove(new Key<>(tick.type(), tick.pos()));
            due.add(tick);
        }
        return due;
    }

    public void clear() {
        queue.clear();
        pending.clear();
    }
}
