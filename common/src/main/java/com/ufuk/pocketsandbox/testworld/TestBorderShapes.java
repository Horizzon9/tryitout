package com.ufuk.pocketsandbox.testworld;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The test area's border as pure collision: six invisible, unbreakable, unselectable walls on the faces of the
 * border box (no blocks at all; the red edge markers are the only thing you see). Added to an entity's collision
 * shapes while it moves inside the test world, the same way the vanilla world border works. On the server every
 * entity in an active area collides with it; on the client the local player (for smooth movement), from the box
 * the server sent.
 */
public final class TestBorderShapes {
    /** Border box of the local player's area (client side), or null. */
    @Nullable
    private static volatile double[] clientBox;

    private TestBorderShapes() {
    }

    /** Client: the server told us our area's border box (min x/y/z, max x/y/z, block bounds inclusive). */
    /** Client: x / z of the middle of the area's box (its centre), or null. */
    @Nullable
    public static double[] clientCentre() {
        double[] box = clientBox;
        return box == null ? null : new double[] {(box[0] + box[3] + 1) / 2.0, (box[2] + box[5] + 1) / 2.0};
    }

    public static void setClientBox(@Nullable double[] box) {
        clientBox = box;
    }

    static double[] box(TestArea area) {
        return new double[] {area.markerMinX(), area.bottomLimitY(), area.markerMinZ(),
                area.markerMaxX(), area.markerTopY(), area.markerMaxZ()};
    }

    /** Collision shapes to add for {@code entity} moving through {@code sweep} (empty when none apply). */
    public static List<VoxelShape> shapesFor(Entity entity, AABB sweep) {
        if (!TestWorld.isTestWorld(entity.level())) {
            return List.of();
        }
        double[] box;
        if (entity.level().isClientSide()) {
            box = clientBox;
        } else {
            box = TestSessions.borderBoxAt(entity.getX(), entity.getZ());
        }
        if (box == null) {
            return List.of();
        }
        double x0 = box[0];
        double y0 = box[1];
        double z0 = box[2];
        double x1 = box[3] + 1;
        double y1 = box[4] + 1;
        double z1 = box[5] + 1;
        List<VoxelShape> shapes = new ArrayList<>(6);
        add(shapes, sweep, new AABB(x0, y0, z0, x0 + 1, y1, z1));      // west
        add(shapes, sweep, new AABB(x1 - 1, y0, z0, x1, y1, z1));      // east
        add(shapes, sweep, new AABB(x0, y0, z0, x1, y1, z0 + 1));      // north
        add(shapes, sweep, new AABB(x0, y0, z1 - 1, x1, y1, z1));      // south
        add(shapes, sweep, new AABB(x0, y0, z0, x1, y0 + 1, z1));      // floor of the box
        add(shapes, sweep, new AABB(x0, y1 - 1, z0, x1, y1, z1));      // top of the box
        return shapes;
    }

    private static void add(List<VoxelShape> shapes, AABB sweep, AABB wall) {
        if (wall.intersects(sweep.inflate(1.0E-7))) {
            shapes.add(Shapes.create(wall));
        }
    }
}
