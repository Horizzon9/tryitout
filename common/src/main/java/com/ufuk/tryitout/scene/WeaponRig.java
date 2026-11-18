package com.ufuk.tryitout.scene;

import com.mojang.authlib.GameProfile;
import com.ufuk.tryitout.combat.Ballistics;
import com.ufuk.tryitout.combat.CombatMath;
import com.ufuk.tryitout.combat.CombatStats;
import com.ufuk.tryitout.combat.RangedProfile;
import com.ufuk.tryitout.render.SandboxParticles;
import com.ufuk.tryitout.world.PreviewPlayer;
import com.ufuk.tryitout.world.SandboxClientView;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Weapon mode: the preview player holding the item, facing a target dummy (an armour stand, so nothing new
 * is registered) at an adjustable distance.
 *
 * <p>Melee: a swing checks the real reach (eye to the dummy's box, like vanilla) and shows the damage at the
 * current charge; Shift makes it a jump attack, which is a critical hit at nearly full charge (x1.5, crit
 * particles, the difference is shown). Ranged (bow, crossbow, trident, throwables): drawing shows the
 * predicted flight; releasing fires a projectile that flies with vanilla physics, hits the dummy (damage
 * like vanilla, critical arrows included) or lands, and its distance is shown.
 */
public final class WeaponRig {
    public static final double MIN_DISTANCE = 1.0;
    private static final double MAX_MELEE_DISTANCE = 7.0;
    /** Half the armour stand's width: reach is measured to its box, not its centre. */
    private static final double DUMMY_HALF_WIDTH = 0.25;
    /** How far inside the reach edge the dummy starts, so the first swing still hits. */
    private static final double EDGE_MARGIN = 0.05;
    /** Ruler colour: blue, so it never reads as a light level (red / grey / yellow / green). */
    public static final int RULER_COLOR = 0x55AAFF;
    private static final double MAX_RANGED_DISTANCE = 48.0;
    private static final double PLAYER_X = 0.5;
    private static final double PLAYER_Z = -2.5;
    public static final double FLOOR_TOP = 1.0;
    private static final int POPUP_TICKS = 40;
    private static final int PROJECTILE_LIFE = 200;
    private static final int STUCK_TICKS = 40;
    private static final int HOP_TICKS = 8;
    private static final byte ARMOR_STAND_HIT_EVENT = 32;

    /** A floating label (damage, landing distance) at a scene position. */
    public record Popup(Vec3 position, String text, int color, long born, boolean big) {
    }

    /** Outcome of the last swing or shot, for the panel. */
    public record Swing(boolean hit, double charge, double damage, double normalDamage, boolean critical,
            double distance, boolean ranged) {
    }

    private static final class Shot {
        final Entity entity;
        Vec3 velocity;
        final double power;
        final boolean critical;
        final Vec3 origin;
        int age;
        int stuck = -1;

        Shot(Entity entity, Vec3 velocity, double power, boolean critical, Vec3 origin) {
            this.entity = entity;
            this.velocity = velocity;
            this.power = power;
            this.critical = critical;
            this.origin = origin;
        }
    }

    private final SandboxClientView view;
    private final ItemStack item;
    private final PreviewPlayer player;
    private final ArmorStand dummy;
    private final CombatStats stats;
    @Nullable
    private final RangedProfile ranged;
    private final SandboxParticles particles;
    private final RandomSource random = RandomSource.create();
    private final List<Popup> popups = new ArrayList<>();
    private final List<Shot> shots = new ArrayList<>();
    private double distance;
    private long lastSwing = Long.MIN_VALUE / 2;
    private long drawStart = -1;
    private int hop;
    private Swing last;

    private WeaponRig(SandboxClientView view, ItemStack item, PreviewPlayer player, ArmorStand dummy,
            CombatStats stats, @Nullable RangedProfile ranged, SandboxParticles particles) {
        this.view = view;
        this.item = item;
        this.player = player;
        this.dummy = dummy;
        this.stats = stats;
        this.ranged = ranged;
        this.particles = particles;
        // Melee: the dummy starts exactly at the edge of the weapon's reach (modded reach included).
        this.distance = ranged != null ? 12.0
                : Math.max(MIN_DISTANCE, stats.reach() + DUMMY_HALF_WIDTH - EDGE_MARGIN);
        place();
    }

    /** {@code statsBase} supplies base attribute values (the scene player). */
    static WeaponRig create(SandboxClientView view, ItemStack item, Player statsBase, SandboxParticles particles) {
        PreviewPlayer player = new PreviewPlayer(view, profile());
        player.hold(item);
        player.face(0.0F);
        ArmorStand dummy = new ArmorStand(EntityType.ARMOR_STAND, view);
        dummy.setShowArms(true);
        return new WeaponRig(view, item, player, dummy, CombatStats.of(item, statsBase),
                RangedProfile.of(item, statsBase), particles);
    }

    /** The real player's profile, so the preview wears their skin (skins are looked up by profile id). */
    private static GameProfile profile() {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            return mc.player.getGameProfile();
        }
        return new GameProfile(UUID.fromString("5a4dbb0c-7c2b-4f0e-9a3c-50c4e7a5b0c2"), "TryItOut");
    }

    private void place() {
        setPlayerY(FLOOR_TOP, true);
        dummy.setPos(PLAYER_X, FLOOR_TOP, PLAYER_Z + distance);
        dummy.xo = dummy.getX();
        dummy.yo = dummy.getY();
        dummy.zo = dummy.getZ();
        dummy.setYRot(180.0F);
        dummy.yRotO = 180.0F;
        dummy.yBodyRot = 180.0F;
        dummy.yBodyRotO = 180.0F;
    }

    private void setPlayerY(double y, boolean snap) {
        player.xo = PLAYER_X;
        player.zo = PLAYER_Z;
        player.yo = snap ? y : player.getY();
        player.setPos(PLAYER_X, y, PLAYER_Z);
    }

    // ---- accessors for the panel / renderer ----

    public List<Entity> entities() {
        List<Entity> list = new ArrayList<>();
        list.add(player);
        list.add(dummy);
        for (Shot shot : shots) {
            list.add(shot.entity);
        }
        return list;
    }

    public CombatStats stats() {
        return stats;
    }

    @Nullable
    public RangedProfile ranged() {
        return ranged;
    }

    public double distance() {
        return distance;
    }

    public Swing lastSwing() {
        return last;
    }

    public List<Popup> popups() {
        return popups;
    }

    public boolean drawing() {
        return drawStart >= 0;
    }

    public void moveDummy(double delta) {
        double max = ranged != null ? MAX_RANGED_DISTANCE : Math.max(MAX_MELEE_DISTANCE, stats.reach() + 2.0);
        distance = Math.max(MIN_DISTANCE, Math.min(max, distance + delta));
        place();
    }

    /** Step for the arrow keys: whole blocks for ranged weapons. */
    public double distanceStep() {
        return ranged != null ? 1.0 : 0.5;
    }

    public Vec3 eye() {
        return new Vec3(PLAYER_X, FLOOR_TOP + player.getEyeHeight(), PLAYER_Z);
    }

    public Vec3 feet() {
        return new Vec3(PLAYER_X, FLOOR_TOP, PLAYER_Z);
    }

    public Vec3 dummyCentre() {
        return dummy.getBoundingBox().getCenter();
    }

    /** Distance from the eye to the closest point of the dummy's box (what vanilla compares to reach). */
    public double reachDistance() {
        AABB box = dummy.getBoundingBox();
        return Math.sqrt(box.distanceToSqr(eye()));
    }

    public boolean inReach() {
        return reachDistance() <= stats.reach();
    }

    /** Current melee attack charge, for the panel's charge bar. */
    public double charge(long gameTime) {
        return CombatMath.charge(gameTime - lastSwing, stats.attackSpeed());
    }

    /** Current draw power 0..1 (ranged), or 0 when not drawing. */
    public double drawPower(long gameTime) {
        if (ranged == null) {
            return 0.0;
        }
        if (!ranged.charges()) {
            return 1.0;
        }
        return drawing() ? ranged.power((int) (gameTime - drawStart)) : 0.0;
    }

    // ---- melee ----

    /** Left click: swing. {@code jump}: jump attack, critical at nearly full charge. */
    public Swing swing(long gameTime, boolean jump) {
        double charge = CombatMath.charge(gameTime - lastSwing, stats.attackSpeed());
        lastSwing = gameTime;
        player.attack();
        if (jump) {
            hop = HOP_TICKS;
        }
        boolean hit = inReach();
        boolean critical = jump && charge > CombatMath.CRIT_MIN_CHARGE;
        double normal = CombatMath.damage(stats.damage(), stats.enchantBonus(), charge, false);
        double damage = hit ? CombatMath.damage(stats.damage(), stats.enchantBonus(), charge, critical) : 0.0;
        Vec3 above = dummyCentre().add(0, 1.2, 0);
        if (hit) {
            dummy.handleEntityEvent(ARMOR_STAND_HIT_EVENT);
            if (critical) {
                popups.add(new Popup(above, "✦ " + num(damage), 0xFFAA00, gameTime, true));
                critBurst(dummyCentre());
            } else {
                popups.add(new Popup(above, num(damage), charge >= 1.0 ? 0xFFFF55 : 0xFFFFFF, gameTime, false));
            }
        } else {
            popups.add(new Popup(above, "✖", 0xFF5555, gameTime, false));
        }
        last = new Swing(hit, charge, damage, hit ? normal : 0.0, hit && critical, reachDistance(), false);
        return last;
    }

    private void critBurst(Vec3 centre) {
        for (int i = 0; i < 16; i++) {
            double dx = random.nextGaussian() * 0.4;
            double dy = random.nextGaussian() * 0.4;
            double dz = random.nextGaussian() * 0.4;
            particles.add(ParticleTypes.CRIT, centre.x + dx, centre.y + dy, centre.z + dz, dx, dy + 0.2, dz);
        }
    }

    // ---- right click: use animation, or draw / fire for ranged items ----

    public void rightClick(long gameTime) {
        if (ranged == null) {
            player.toggleUse();
            return;
        }
        if (!ranged.charges()) {
            player.attack();
            fire(gameTime, 1.0);
            return;
        }
        if (!drawing()) {
            drawStart = gameTime;
            if (!player.isUsingItem()) {
                player.toggleUse();
            }
            return;
        }
        double power = ranged.power((int) (gameTime - drawStart));
        drawStart = -1;
        player.stopUse();
        if (power <= 0.0) {
            popups.add(new Popup(eye().add(0, 0.6, 1), "…", 0xAAAAAA, gameTime, false));
            return;
        }
        fire(gameTime, power);
    }

    /** Launch pitch that points straight at the dummy (gravity makes long shots drop, as in game). */
    private double aimPitch() {
        Vec3 to = dummyCentre().subtract(eye());
        return Math.toDegrees(Math.atan2(to.y, to.z));
    }

    private void fire(long gameTime, double power) {
        double speed = ranged.speed() * power;
        double pitch = Math.toRadians(aimPitch());
        Vec3 velocity = new Vec3(0, Math.sin(pitch) * speed, Math.cos(pitch) * speed);
        Vec3 start = eye().add(0, -0.1, 0);
        boolean critical = ranged.critical(power);
        Entity entity = projectileEntity(start);
        if (entity instanceof Arrow arrow) {
            arrow.setCritArrow(critical);
        }
        orient(entity, velocity);
        shots.add(new Shot(entity, velocity, power, critical, start));
    }

    private Entity projectileEntity(Vec3 at) {
        return switch (ranged.kind()) {
            case TRIDENT -> new ThrownTrident(view, at.x, at.y, at.z, item.copy());
            case THROWABLE -> {
                Snowball snowball = new Snowball(view, at.x, at.y, at.z);
                snowball.setItem(item.copy());
                yield snowball;
            }
            case POTION -> {
                ThrownPotion potion = new ThrownPotion(view, at.x, at.y, at.z);
                potion.setItem(item.copy());
                yield potion;
            }
            default -> new Arrow(view, at.x, at.y, at.z, new ItemStack(Items.ARROW), item.copy());
        };
    }

    private static void orient(Entity entity, Vec3 velocity) {
        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        float yaw = (float) Math.toDegrees(Math.atan2(velocity.x, velocity.z));
        float pitch = (float) Math.toDegrees(Math.atan2(velocity.y, horizontal));
        entity.yRotO = entity.getYRot();
        entity.xRotO = entity.getXRot();
        entity.setYRot(yaw);
        entity.setXRot(pitch);
    }

    /** Predicted flight at the current draw power (or full power when idle), in scene coordinates. */
    public List<Vec3> predictedFlight(long gameTime) {
        if (ranged == null) {
            return List.of();
        }
        double power = drawing() ? Math.max(0.05, drawPower(gameTime)) : 1.0;
        Ballistics.Flight flight = Ballistics.simulate(eye().y, ranged.speed() * power, aimPitch(), ranged.gravity(),
                ranged.drag(), FLOOR_TOP);
        List<Vec3> points = new ArrayList<>();
        for (double[] p : flight.points()) {
            points.add(new Vec3(PLAYER_X, p[1], PLAYER_Z + p[0]));
        }
        return points;
    }

    /** Horizontal distance of a flat (0 degree) full-power shot until it reaches the floor. */
    public double flatRange() {
        return ranged == null ? 0.0 : Ballistics.simulate(eye().y, ranged.speed(), 0.0, ranged.gravity(),
                ranged.drag(), FLOOR_TOP).distance();
    }

    public double maxRange() {
        return ranged == null ? 0.0 : Ballistics.maxRange(eye().y, ranged.speed(), ranged.gravity(), ranged.drag(),
                FLOOR_TOP);
    }

    /** Ruler marks along the path: (floor position, label). */
    public List<Popup> ruler() {
        double length = ranged != null ? Math.max(distance, Math.min(flatRange(), MAX_RANGED_DISTANCE))
                : Math.max(distance, stats.reach());
        int step = length > 16 ? 5 : 1;
        List<Popup> marks = new ArrayList<>();
        for (int d = step; d <= Math.ceil(length) + step; d += step) {
            marks.add(new Popup(new Vec3(PLAYER_X - 1.2, FLOOR_TOP + 0.02, PLAYER_Z + d), d + "", RULER_COLOR, 0, false));
        }
        return marks;
    }

    // ---- tick ----

    void tick(long gameTime) {
        player.tickAnimation();
        if (drawing() && !player.isUsingItem()) {
            player.toggleUse(); // keep the draw pose while charging (use animation ran out)
        }
        tickHop();
        dummy.tickCount++;
        tickShots(gameTime);
        Iterator<Popup> it = popups.iterator();
        while (it.hasNext()) {
            if (gameTime - it.next().born() > POPUP_TICKS) {
                it.remove();
            }
        }
    }

    private void tickHop() {
        if (hop <= 0) {
            setPlayerY(FLOOR_TOP, false);
            return;
        }
        hop--;
        double t = (HOP_TICKS - hop) / (double) HOP_TICKS;
        setPlayerY(FLOOR_TOP + Math.sin(Math.PI * t) * 0.5, false);
    }

    private void tickShots(long gameTime) {
        AABB target = dummy.getBoundingBox().inflate(0.3);
        Iterator<Shot> it = shots.iterator();
        while (it.hasNext()) {
            Shot shot = it.next();
            Entity entity = shot.entity;
            entity.xo = entity.getX();
            entity.yo = entity.getY();
            entity.zo = entity.getZ();
            entity.yRotO = entity.getYRot();
            entity.xRotO = entity.getXRot();
            entity.tickCount++;
            if (shot.stuck >= 0) {
                if (++shot.stuck > STUCK_TICKS) {
                    it.remove();
                }
                continue;
            }
            shot.age++;
            Vec3 from = entity.position();
            Vec3 to = from.add(shot.velocity);
            Optional<Vec3> hit = target.clip(from, to);
            if (hit.isPresent()) {
                onHit(shot, hit.get(), gameTime);
                it.remove();
                continue;
            }
            if (to.y <= FLOOR_TOP && shot.velocity.y < 0) {
                double t = (from.y - FLOOR_TOP) / (from.y - to.y);
                Vec3 land = from.add(shot.velocity.scale(t));
                entity.setPos(land.x, land.y, land.z);
                shot.stuck = 0;
                double flown = Math.sqrt(Math.pow(land.x - shot.origin.x, 2) + Math.pow(land.z - shot.origin.z, 2));
                popups.add(new Popup(land.add(0, 0.6, 0), "↓ " + num(flown), 0x55FFFF, gameTime, false));
                continue;
            }
            entity.setPos(to.x, to.y, to.z);
            if (shot.critical) {
                particles.add(ParticleTypes.CRIT, to.x, to.y, to.z, 0, 0, 0);
            }
            shot.velocity = shot.velocity.scale(ranged.drag()).subtract(0, ranged.gravity(), 0);
            orient(entity, shot.velocity);
            if (shot.age > PROJECTILE_LIFE) {
                it.remove();
            }
        }
    }

    private void onHit(Shot shot, Vec3 at, long gameTime) {
        dummy.handleEntityEvent(ARMOR_STAND_HIT_EVENT);
        double impact = shot.velocity.length();
        int[] range = ranged.damage(impact, shot.power);
        int damage = range[0] + (range[1] > range[0] ? random.nextInt(range[1] - range[0] + 1) : 0);
        double flown = Math.sqrt(Math.pow(at.x - shot.origin.x, 2) + Math.pow(at.z - shot.origin.z, 2));
        Vec3 above = dummyCentre().add(0, 1.2, 0);
        if (shot.critical) {
            popups.add(new Popup(above, "✦ " + damage, 0xFFAA00, gameTime, true));
            critBurst(at);
        } else {
            popups.add(new Popup(above, String.valueOf(damage), 0xFFFF55, gameTime, false));
        }
        last = new Swing(true, shot.power, damage, range[0], shot.critical, flown, true);
    }

    public static int popupTicks() {
        return POPUP_TICKS;
    }

    static String num(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
