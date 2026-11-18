package com.ufuk.pocketsandbox.combat;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla projectile flight in a vertical plane, pure so it is unit tested. Per tick, like
 * {@code AbstractArrow#tick}: move by the velocity, then multiply it by the drag and subtract gravity.
 * Distances are horizontal, in blocks, from the launch point.
 */
public final class Ballistics {
    public static final int MAX_TICKS = 400;
    private static final double MAX_PITCH = 60.0;
    private static final double PITCH_STEP = 0.25;

    private Ballistics() {
    }

    /**
     * One flight.
     *
     * @param distance horizontal distance where it reached {@code floorY} (or the last point if it never did)
     * @param points   (horizontal, height) per tick, starting at the launch point
     * @param landed   true when it came down to {@code floorY} within {@link #MAX_TICKS}
     */
    public record Flight(double distance, int ticks, List<double[]> points, boolean landed) {
    }

    /** Pitch in degrees, positive upwards. */
    public static Flight simulate(double startY, double speed, double pitchDegrees, double gravity, double drag,
            double floorY) {
        double pitch = Math.toRadians(pitchDegrees);
        double h = 0.0;
        double y = startY;
        double vh = speed * Math.cos(pitch);
        double vy = speed * Math.sin(pitch);
        List<double[]> points = new ArrayList<>();
        points.add(new double[] {h, y});
        for (int tick = 1; tick <= MAX_TICKS; tick++) {
            double nh = h + vh;
            double ny = y + vy;
            if (ny <= floorY && vy < 0) {
                double t = (y - floorY) / (y - ny);
                double landH = h + (nh - h) * t;
                points.add(new double[] {landH, floorY});
                return new Flight(landH, tick, points, true);
            }
            h = nh;
            y = ny;
            points.add(new double[] {h, y});
            vh *= drag;
            vy = vy * drag - gravity;
        }
        return new Flight(h, MAX_TICKS, points, false);
    }

    /** Longest horizontal distance over launch angles 0..60 degrees. */
    public static double maxRange(double startY, double speed, double gravity, double drag, double floorY) {
        double best = 0.0;
        for (double pitch = 0.0; pitch <= MAX_PITCH; pitch += PITCH_STEP) {
            Flight flight = simulate(startY, speed, pitch, gravity, drag, floorY);
            if (flight.landed() && flight.distance() > best) {
                best = flight.distance();
            }
        }
        return best;
    }

    /** Speed after {@code ticks} ticks of flight with the given drag and gravity (for impact damage). */
    public static double speedAfter(double speed, double pitchDegrees, double gravity, double drag, int ticks) {
        double pitch = Math.toRadians(pitchDegrees);
        double vh = speed * Math.cos(pitch);
        double vy = speed * Math.sin(pitch);
        for (int i = 0; i < ticks; i++) {
            vh *= drag;
            vy = vy * drag - gravity;
        }
        return Math.sqrt(vh * vh + vy * vy);
    }

    /**
     * Arrow damage range at impact speed, as {@code AbstractArrow#onHitEntity}: {@code ceil(speed * base)};
     * a critical arrow adds a random 0 .. damage/2 + 1.
     */
    public static int[] arrowDamage(double impactSpeed, double baseDamage, boolean critical) {
        int damage = (int) Math.ceil(Math.max(0.0, impactSpeed * baseDamage));
        int max = critical ? damage + damage / 2 + 1 : damage;
        return new int[] {damage, max};
    }
}
