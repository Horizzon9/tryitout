package com.ufuk.tryitout.testworld;

import com.ufuk.tryitout.TryItOut;
import dev.architectury.platform.Platform;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Sable (the physics engine of Create Aeronautics / Simulated) inside the test world, without a compile
 * dependency (reflection, bound once; on any mismatch the bridge switches itself off and nothing else changes).
 *
 * <p>Physics itself runs unchanged: a ship ("sub-level") is assembled, flies and collides in the test dimension
 * exactly as anywhere else. Sable keeps a ship's blocks in a far-away plot yard and shows them at the ship's pose,
 * so the test world handles ships separately:
 * <ul>
 *   <li>a ship whose centre leaves the border box of every active area is put back above the centre of the
 *       nearest area with its velocity reset (a ship of a closed area is removed);</li>
 *   <li>clearing an area removes the ships in it (their plot-yard blocks go with them);</li>
 *   <li>saves do not contain ships (the player is told).</li>
 * </ul>
 */
final class SableCompat {
    /** Ships are looked at this far around an area's border box (a big ship's centre can sit outside it). */
    private static final int MARGIN = 24;

    private static Boolean ready;
    private static Method getAnyContainer;
    private static Method inBounds;
    private static Method getContainer;
    private static Method getAllSubLevels;
    private static Method logicalPose;
    private static Method position;
    private static Method orientation;
    private static Method isRemoved;
    private static Method physicsSystem;
    private static Method getPipeline;
    private static Method resetVelocity;
    private static Method teleport;
    private static Method removeSubLevel;
    private static Object removedReason;
    private static Class<?> subLevelClass;

    private SableCompat() {
    }

    static boolean present() {
        if (ready == null) {
            ready = Platform.isModLoaded("sable") && bind();
        }
        return ready;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean bind() {
        try {
            Class<?> container = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
            Class<?> serverContainer = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
            subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            Class<?> pose = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
            Class<?> system = Class.forName("dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem");
            Class<?> pipeline = Class.forName("dev.ryanhcode.sable.api.physics.PhysicsPipeline");
            Class<?> body = Class.forName("dev.ryanhcode.sable.api.physics.PhysicsPipelineBody");
            Class<? extends Enum> reason = (Class<? extends Enum>) Class.forName(
                    "dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason");
            getContainer = container.getMethod("getContainer", ServerLevel.class);
            getAnyContainer = container.getMethod("getContainer", net.minecraft.world.level.Level.class);
            inBounds = container.getMethod("inBounds", net.minecraft.core.BlockPos.class);
            getAllSubLevels = serverContainer.getMethod("getAllSubLevels");
            logicalPose = subLevelClass.getMethod("logicalPose");
            position = pose.getMethod("position");
            orientation = pose.getMethod("orientation");
            isRemoved = subLevelClass.getMethod("isRemoved");
            physicsSystem = serverContainer.getMethod("physicsSystem");
            getPipeline = system.getMethod("getPipeline");
            resetVelocity = pipeline.getMethod("resetVelocity", body);
            teleport = pipeline.getMethod("teleport", body, Vector3dc.class, Quaterniondc.class);
            removeSubLevel = container.getMethod("removeSubLevel", subLevelClass, reason);
            removedReason = Enum.valueOf(reason, "REMOVED");
            TryItOut.LOGGER.info("TryItOut: Sable (Create Aeronautics) support enabled in the test world");
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            TryItOut.LOGGER.warn("TryItOut: Sable found but its classes did not match; ships in the test"
                    + " world are not kept inside the border", e);
            return false;
        }
    }

    /**
     * True for a block of a ship: Sable keeps ship blocks in a plot yard far away from the test areas, so the
     * "nothing outside the border" rules (placing, fluids) must not apply there.
     */
    static boolean isShipBlock(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        if (!present()) {
            return false;
        }
        try {
            Object container = getAnyContainer.invoke(null, level);
            return container != null && (Boolean) inBounds.invoke(container, pos);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private record Ship(Object subLevel, Vector3dc position, Quaterniondc orientation) {
    }

    private static List<Ship> ships(ServerLevel level) throws ReflectiveOperationException {
        List<Ship> out = new ArrayList<>();
        Object container = getContainer.invoke(null, level);
        if (container == null) {
            return out;
        }
        for (Object subLevel : List.copyOf((List<?>) getAllSubLevels.invoke(container))) {
            if ((Boolean) isRemoved.invoke(subLevel)) {
                continue;
            }
            Object pose = logicalPose.invoke(subLevel);
            out.add(new Ship(subLevel, new Vector3d((Vector3dc) position.invoke(pose)),
                    (Quaterniondc) orientation.invoke(pose)));
        }
        return out;
    }

    private static boolean near(TestArea area, Vector3dc pos, int margin) {
        return pos.x() >= area.markerMinX() - margin && pos.x() < area.markerMaxX() + 1 + margin
                && pos.z() >= area.markerMinZ() - margin && pos.z() < area.markerMaxZ() + 1 + margin;
    }

    /** Every half second: ships that left their area go back above its centre (or are removed if it is gone). */
    static void keepInside(ServerLevel level, List<TestArea> areas) {
        if (!present()) {
            return;
        }
        try {
            for (Ship ship : ships(level)) {
                TestArea home = nearest(areas, ship.position());
                if (home == null) {
                    remove(level, ship);
                    continue;
                }
                double y = ship.position().y();
                if (TestSessions.insideBuildBox((int) Math.floor(ship.position().x()), (int) Math.floor(y),
                        (int) Math.floor(ship.position().z()))) {
                    continue;
                }
                Object pipeline = getPipeline.invoke(physicsSystem.invoke(getContainer.invoke(null, level)));
                Vector3d centre = new Vector3d(home.centerX() + 0.5, home.floorY() + home.size() / 2.0 + 3,
                        home.centerZ() + 0.5);
                resetVelocity.invoke(pipeline, ship.subLevel());
                teleport.invoke(pipeline, ship.subLevel(), centre, ship.orientation());
                TryItOut.LOGGER.info("TryItOut: a ship left test area at x={} and was put back",
                        home.centerX());
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            TryItOut.LOGGER.warn("TryItOut: could not check ships in the test world", e);
        }
    }

    /** The area whose border box (plus margin) holds {@code pos}, else the nearest one along x (areas are a row). */
    private static TestArea nearest(List<TestArea> areas, Vector3dc pos) {
        TestArea best = null;
        double bestDistance = Double.MAX_VALUE;
        for (TestArea area : areas) {
            if (near(area, pos, MARGIN)) {
                return area;
            }
            double distance = Math.abs(pos.x() - area.centerX()) + Math.abs(pos.z() - area.centerZ());
            if (distance < TestArea.SPACING / 2.0 && distance < bestDistance) {
                best = area;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Removes the ships of an area (when it is cleared). */
    static void removeShips(ServerLevel level, TestArea area) {
        if (!present()) {
            return;
        }
        try {
            for (Ship ship : ships(level)) {
                if (near(area, ship.position(), MARGIN)) {
                    remove(level, ship);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            TryItOut.LOGGER.warn("TryItOut: could not remove ships of a test area", e);
        }
    }

    /** Ships in an area (saves do not keep them). */
    static int shipCount(ServerLevel level, TestArea area) {
        if (!present()) {
            return 0;
        }
        try {
            int count = 0;
            for (Ship ship : ships(level)) {
                if (near(area, ship.position(), MARGIN)) {
                    count++;
                }
            }
            return count;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return 0;
        }
    }

    private static void remove(ServerLevel level, Ship ship) throws ReflectiveOperationException {
        removeSubLevel.invoke(getContainer.invoke(null, level), ship.subLevel(), removedReason);
    }
}
