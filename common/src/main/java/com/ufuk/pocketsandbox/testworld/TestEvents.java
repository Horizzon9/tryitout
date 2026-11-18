package com.ufuk.pocketsandbox.testworld;

import com.ufuk.pocketsandbox.PocketSandbox;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerPlayer;

/** Server-side hooks of the test world. Every handler is guarded: a failure here never stops the server tick. */
final class TestEvents {
    private TestEvents() {
    }

    static void init() {
        LifecycleEvent.SERVER_STARTED.register(server -> {
            TestSessions.onServerStarted(server);
            TestClock.onServerStarted();
            TestRules.onServerStarted();
        });
        LifecycleEvent.SERVER_STOPPING.register(server -> guard("stopping", TestSessions::onServerStopping));
        LifecycleEvent.SERVER_STOPPED.register(server -> {
            TestSessions.onServerStopped();
            TestClock.onServerStopped();
            TestRules.onServerStopped();
            TestInvites.clear();
        });
        TickEvent.SERVER_POST.register(server -> guard("clock", () -> TestClock.onServerTick(server)));
        TickEvent.SERVER_LEVEL_PRE.register(level -> {
            if (TestWorld.isTestWorld(level)) {
                TestCrashGuard.levelTickStart(level);
            }
        });
        TickEvent.SERVER_LEVEL_POST.register(level -> {
            if (TestWorld.isTestWorld(level)) {
                guard("dummies", () -> TestDummies.tick(level));
                guard("crash-guard", () -> TestCrashGuard.levelTickEnd(level));
                if (level.getGameTime() % 10 == 0) {
                    guard("ships", () -> SableCompat.keepInside(level, TestSessions.activeAreas()));
                }
            }
        });
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            if (!TestWorld.isTestWorld(entity.level())) {
                return EventResult.pass();
            }
            if (TestDummies.preventDeath(entity)) {
                return EventResult.interruptFalse();
            }
            if (entity instanceof ServerPlayer player) {
                guard("death", () -> TestSessions.onDeath(player));
            }
            return EventResult.pass();
        });
        TickEvent.SERVER_POST.register(server -> guard("bodies", () -> TestBodies.tick(server)));
        // A body loaded while its owner is not in the test world any more is not kept.
        EntityEvent.ADD.register((entity, level) ->
                !level.isClientSide() && TestBodies.isOrphan(entity) ? EventResult.interruptFalse() : EventResult.pass());
        dev.architectury.event.events.common.CommandRegistrationEvent.EVENT.register(
                (dispatcher, registry, selection) -> TestInvites.register(dispatcher));
        // Banned items (cross-dimension storage) cannot be used, banned blocks cannot be placed.
        dev.architectury.event.events.common.InteractionEvent.RIGHT_CLICK_ITEM.register((player, hand) -> {
            var stack = player.getItemInHand(hand);
            if (TestWorld.isTestWorld(player.level()) && TestBans.blocked(stack)) {
                TestBans.tell(player);
                return dev.architectury.event.CompoundEventResult.interruptFalse(stack);
            }
            return dev.architectury.event.CompoundEventResult.pass();
        });
        dev.architectury.event.events.common.BlockEvent.PLACE.register((level, pos, state, placer) -> {
            if (TestWorld.isTestWorld(level) && TestBans.blocked(state.getBlock())) {
                if (placer instanceof net.minecraft.world.entity.player.Player player) {
                    TestBans.tell(player);
                }
                return EventResult.interruptFalse();
            }
            // Nothing is built on or beyond the invisible border wall (reach goes a little past it).
            if (TestWorld.isTestWorld(level) && placer instanceof net.minecraft.world.entity.player.Player
                    && !TestSessions.canBuildAt(level, pos)) {
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });
        PlayerEvent.PLAYER_RESPAWN.register((player, conqueredEnd, reason) ->
                guard("respawn", () -> TestSessions.onRespawn(player)));
        // No sleeping in the test world (and so no spawn point there): beds and anchors only show a message.
        dev.architectury.event.events.common.InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, face) -> {
            if (!TestWorld.isTestWorld(player.level())) {
                return EventResult.pass();
            }
            var block = player.level().getBlockState(pos).getBlock();
            if (TestBans.blocked(player.getItemInHand(hand)) || TestBans.blocked(block)) {
                TestBans.tell(player);
                return EventResult.interruptFalse();
            }
            if (block instanceof net.minecraft.world.level.block.BedBlock
                    || block instanceof net.minecraft.world.level.block.RespawnAnchorBlock) {
                if (player instanceof ServerPlayer serverPlayer) {
                    TestNetwork.send(serverPlayer, new com.ufuk.pocketsandbox.testworld.net.TestStatusPayload(true,
                            "pocketsandbox.test.no_sleep"));
                }
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });
        PlayerEvent.PLAYER_JOIN.register(player -> guard("join", () -> TestSessions.onJoin(player)));
        TickEvent.PLAYER_POST.register(player -> {
            if (player instanceof ServerPlayer serverPlayer) {
                guard("tick", () -> TestSessions.onPlayerTick(serverPlayer));
            }
        });
        // Border markers (and walls / ceiling unless breakable) cannot be broken.
        dev.architectury.event.events.common.BlockEvent.BREAK.register((level, pos, state, player, xp) ->
                TestSessions.isProtected(player, pos) ? EventResult.interruptFalse() : EventResult.pass());
        EntityEvent.LIVING_HURT.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player) {
                TestSessions.onHurt(player);
            }
            return EventResult.pass();
        });
    }

    private static void guard(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            PocketSandbox.LOGGER.error("Pocket Sandbox: test world {} handler failed", what, e);
        }
    }
}
