package com.ufuk.pocketsandbox.testworld;

import com.ufuk.pocketsandbox.PocketSandbox;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Keeps a misbehaving mod inside the test world from taking the server down. In the test dimension only:
 * a block entity or entity that throws while ticking is removed instead of crashing the server; three such
 * errors within ten seconds, or a test dimension that keeps the server lagging (over 50 ms per tick for five
 * seconds), trip the fuse: everyone is sent back with their real profile and the test areas are cleared.
 * Each area may hold at most {@link #MAX_ENTITIES} entities (loose items and projectiles go first).
 * Not covered: running out of memory, an endless loop inside mod code (the server watchdog stops the server),
 * graphics driver crashes. Real profiles and the real world are not touched by any of these.
 */
public final class TestCrashGuard {
    static final int MAX_ENTITIES = 300;
    private static final int FUSE_ERRORS = 3;
    private static final int FUSE_WINDOW_TICKS = 200;
    private static final long SLOW_TICK_NANOS = 50_000_000L;
    private static final int SLOW_TICKS_TO_TRIP = 100;
    private static final Deque<Long> recentErrors = new ArrayDeque<>();
    private static long tickStart;
    private static int slowTicks;
    private static boolean tripping;

    private TestCrashGuard() {
    }

    /** True when ticking in {@code level} should be guarded (the server-side test level). */
    public static boolean guards(Level level) {
        return level instanceof ServerLevel && TestWorld.isTestWorld(level);
    }

    /** A block entity threw while ticking: remove it (and its block), never crash. */
    public static void blockEntityFailed(Level level, BlockPos pos, Throwable error) {
        PocketSandbox.LOGGER.error("Pocket Sandbox: a block entity at {} in the test world failed and was removed", pos, error);
        try {
            level.removeBlockEntity(pos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        } catch (RuntimeException e) {
            PocketSandbox.LOGGER.error("Pocket Sandbox: could not remove the failing block entity", e);
        }
        failed((ServerLevel) level, "pocketsandbox.test.guard.block");
    }

    /** An entity threw while ticking: remove it, never crash. */
    public static void entityFailed(Entity entity, Throwable error) {
        PocketSandbox.LOGGER.error("Pocket Sandbox: entity {} in the test world failed and was removed", entity, error);
        if (!(entity instanceof Player)) {
            entity.discard();
        }
        if (entity.level() instanceof ServerLevel level) {
            failed(level, "pocketsandbox.test.guard.entity");
        }
    }

    private static void failed(ServerLevel level, String messageKey) {
        long now = level.getGameTime();
        recentErrors.addLast(now);
        while (!recentErrors.isEmpty() && now - recentErrors.peekFirst() > FUSE_WINDOW_TICKS) {
            recentErrors.removeFirst();
        }
        for (Player player : level.players()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(messageKey)
                    .withStyle(net.minecraft.ChatFormatting.GOLD), false);
        }
        if (recentErrors.size() >= FUSE_ERRORS) {
            trip(level, "errors");
        }
    }

    private static void trip(ServerLevel level, String reason) {
        if (tripping) {
            return;
        }
        tripping = true;
        try {
            PocketSandbox.LOGGER.warn("Pocket Sandbox: test world fuse tripped ({}): everyone is sent back", reason);
            recentErrors.clear();
            slowTicks = 0;
            // Deferred to the end of the tick: no profile swaps while the level is in the middle of ticking.
            level.getServer().execute(() -> TestSessions.evacuateAll("pocketsandbox.test.guard.fuse"));
        } finally {
            tripping = false;
        }
    }

    static void levelTickStart(ServerLevel level) {
        tickStart = System.nanoTime();
    }

    /** End of a test level tick: lag fuse, then every second the entity limit of each area. */
    static void levelTickEnd(ServerLevel level) {
        long took = System.nanoTime() - tickStart;
        boolean anyone = !level.players().isEmpty();
        // Ship physics (Sable) makes ticks heavier on purpose: a looser limit there.
        long limit = SableCompat.present() ? SLOW_TICK_NANOS * 3 : SLOW_TICK_NANOS;
        slowTicks = anyone && took > limit ? slowTicks + 1 : 0;
        if (slowTicks >= SLOW_TICKS_TO_TRIP) {
            trip(level, "lag");
        }
        if (level.getGameTime() % 20 == 0) {
            for (TestArea area : TestSessions.activeAreas()) {
                limitEntities(level, area);
            }
        }
    }

    private static void limitEntities(ServerLevel level, TestArea area) {
        AABB box = new AABB(area.markerMinX(), area.bottomLimitY() - 16, area.markerMinZ(),
                area.markerMaxX() + 1, area.markerTopY() + 16, area.markerMaxZ() + 1);
        List<Entity> entities = level.getEntities((Entity) null, box, entity -> !(entity instanceof Player));
        int excess = entities.size() - MAX_ENTITIES;
        if (excess <= 0) {
            return;
        }
        entities.sort((a, b) -> Integer.compare(rank(a), rank(b)));
        for (int i = 0; i < excess; i++) {
            entities.get(i).discard();
        }
    }

    /** Loose items and projectiles are removed before mobs; dummies last. */
    private static int rank(Entity entity) {
        if (entity instanceof ItemEntity || entity instanceof Projectile) {
            return 0;
        }
        return TestDummies.isDummy(entity) ? 2 : 1;
    }
}
