package com.ufuk.pocketsandbox.testworld;

import com.ufuk.pocketsandbox.PocketSandbox;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Shared ids of the server-side test world. The dimension itself comes from the mod's data pack JSON. */
public final class TestWorld {
    public static final ResourceLocation DIMENSION_ID = ResourceLocation.fromNamespaceAndPath(PocketSandbox.MOD_ID, "test");
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, DIMENSION_ID);

    private TestWorld() {
    }

    private static int allowedDepth;

    /** Runs {@code move} with dimension changes into / out of the test world allowed (our own teleports only). */
    public static void allowDimensionChange(Runnable move) {
        allowedDepth++;
        try {
            move.run();
        } finally {
            allowedDepth--;
        }
    }

    /** True when {@code entity} would move into or out of the test world by anything but our own teleport. */
    public static boolean crossesTestBorder(net.minecraft.world.entity.Entity entity, Level target) {
        if (allowedDepth > 0 || target == null) {
            return false;
        }
        return isTestWorld(entity.level()) != isTestWorld(target);
    }

    /** True for a test player's body in the real world. */
    public static boolean isBody(net.minecraft.world.entity.Entity entity) {
        return TestBodies.isBody(entity);
    }

    public static void hurtBody(net.minecraft.world.entity.decoration.ArmorStand body,
            net.minecraft.world.damagesource.DamageSource source, float amount) {
        TestBodies.hurt(body, source, amount);
    }

    public static boolean isTestWorld(Level level) {
        return level != null && level.dimension() == DIMENSION;
    }
}
