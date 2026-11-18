package com.ufuk.pocketsandbox.scene;

import com.ufuk.pocketsandbox.safety.Safe;
import com.ufuk.pocketsandbox.world.AbstractSandboxLevel;
import com.ufuk.pocketsandbox.world.SandboxLogicLevel;
import com.ufuk.pocketsandbox.world.SandboxPlayer;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3f;

/**
 * Player actions in the scene, run the way the game runs them on a server, but against the
 * {@link SandboxLogicLevel}: use a block, place / use the held item, break, rotate. Nothing touches the
 * real game mode or connection; every action is guarded and a failure only adds a badge entry.
 */
public final class SceneEditor {
    private final SandboxScene scene;

    SceneEditor(SandboxScene scene) {
        this.scene = scene;
    }

    /** First block hit by the ray, if any. */
    public Optional<BlockHitResult> pick(Vector3f from, Vector3f to) {
        AbstractSandboxLevel level = scene.level();
        BlockHitResult hit = Safe.get(scene.faults(), "pick", () -> level.clip(new ClipContext(
                new Vec3(from.x, from.y, from.z), new Vec3(to.x, to.y, to.z),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty())), null);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        return Optional.of(hit);
    }

    /** A decoration hit by the ray and its distance from {@code from}. */
    public record EntityHit(net.minecraft.world.entity.Entity entity, double distance) {
    }

    /** Nearest decoration (painting, frame, armour stand) on the ray, if any. */
    public Optional<EntityHit> pickEntity(Vector3f from, Vector3f to) {
        Vec3 start = new Vec3(from.x, from.y, from.z);
        Vec3 end = new Vec3(to.x, to.y, to.z);
        EntityHit best = null;
        for (net.minecraft.world.entity.Entity entity : scene.world().entities().getAll()) {
            Optional<Vec3> at = entity.getBoundingBox().inflate(0.05).clip(start, end);
            if (at.isPresent()) {
                double distance = at.get().distanceTo(start);
                if (best == null || distance < best.distance()) {
                    best = new EntityHit(entity, distance);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Takes a decoration down (left click). */
    public void removeEntity(net.minecraft.world.entity.Entity entity) {
        scene.world().entities().remove(entity);
        scene.world().markChanged();
    }

    /** Points the scene player from {@code eye} towards {@code target} before an action. */
    public void aim(Vector3f eye, Vec3 target) {
        scene.player().aim(eye.x, eye.y, eye.z, target.x - eye.x, target.y - eye.y, target.z - eye.z);
    }

    /**
     * Right click. Blocks that can be lit (furnace, smoker, campfire, candles, lamps) are lit / put out so
     * their light and particles can be seen; {@code openGui} (Ctrl) uses the block normally instead, which
     * opens its GUI. Otherwise, unless sneaking, the clicked block reacts first (doors open, levers flip),
     * then the held item is used on it, which places blocks. Returns true when something happened.
     */
    public boolean useOrPlace(BlockHitResult hit, ItemStack held, boolean sneaking, boolean openGui) {
        if (!sneaking && !openGui && toggleLit(hit.getBlockPos())) {
            return true;
        }
        return useOrPlace(hit, held, sneaking);
    }

    /** Lights / puts out a block with a LIT state; false when it has none. */
    public boolean toggleLit(BlockPos pos) {
        AbstractSandboxLevel level = scene.editLevel();
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.LIT)) {
            return false;
        }
        boolean[] changed = {false};
        Safe.attempt(scene.faults(), "lit@" + AbstractSandboxLevel.posKey(pos),
                () -> changed[0] = level.setBlock(pos, state.cycle(BlockStateProperties.LIT), Block.UPDATE_ALL));
        return changed[0];
    }

    /** Right click without the lighting shortcut (also used to place the first block). */
    public boolean useOrPlace(BlockHitResult hit, ItemStack held, boolean sneaking) {
        AbstractSandboxLevel level = scene.editLevel();
        SandboxPlayer player = scene.player();
        player.setItemInHand(InteractionHand.MAIN_HAND, held.copy());
        player.setShiftKeyDown(sneaking);
        boolean[] acted = {false};
        Safe.attempt(scene.faults(), "use@" + AbstractSandboxLevel.posKey(hit.getBlockPos()), () -> {
            BlockState state = level.getBlockState(hit.getBlockPos());
            if (!sneaking) {
                ItemInteractionResult withItem = state.useItemOn(player.getMainHandItem(), level, player,
                        InteractionHand.MAIN_HAND, hit);
                if (withItem.consumesAction()) {
                    acted[0] = true;
                    return;
                }
                if (withItem == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
                    InteractionResult without = state.useWithoutItem(level, player, hit);
                    if (without.consumesAction()) {
                        acted[0] = true;
                        return;
                    }
                }
            }
            if (!player.getMainHandItem().isEmpty()) {
                // Context bound to the edit level (not player.level()), so safe mode edits the client level.
                UseOnContext context = new UseOnContext(level, player, InteractionHand.MAIN_HAND,
                        player.getMainHandItem(), hit) {
                };
                acted[0] = player.getMainHandItem().useOn(context).consumesAction();
            }
        });
        player.setShiftKeyDown(false);
        return acted[0];
    }

    /** Left click: breaks the block the way a creative player does. The floor layer cannot be broken. */
    public boolean breakBlock(BlockPos pos) {
        if (!scene.canEdit(pos)) {
            return false;
        }
        AbstractSandboxLevel level = scene.editLevel();
        SandboxPlayer player = scene.player();
        boolean[] removed = {false};
        Safe.attempt(scene.faults(), "break@" + AbstractSandboxLevel.posKey(pos), () -> {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                return;
            }
            BlockState willDestroy = state.getBlock().playerWillDestroy(level, pos, state, player);
            removed[0] = level.removeBlock(pos, false);
            if (removed[0]) {
                state.getBlock().destroy(level, pos, willDestroy);
            }
        });
        return removed[0];
    }

    /**
     * Turns a block 90 degrees. Blocks without a horizontal rotation (logs, pillars, up/down facing) cycle
     * their axis / facing instead.
     */
    public boolean rotate(BlockPos pos) {
        if (!scene.canEdit(pos)) {
            return false;
        }
        AbstractSandboxLevel level = scene.editLevel();
        boolean[] changed = {false};
        Safe.attempt(scene.faults(), "rotate@" + AbstractSandboxLevel.posKey(pos), () -> {
            BlockState state = level.getBlockState(pos);
            BlockState next = state.rotate(Rotation.CLOCKWISE_90);
            if (next == state) {
                next = cycleFirst(state);
            }
            if (next != state) {
                changed[0] = level.setBlock(pos, next, Block.UPDATE_ALL);
            }
        });
        return changed[0];
    }

    private static BlockState cycleFirst(BlockState state) {
        for (Property<?> property : new Property<?>[] {
            BlockStateProperties.AXIS, BlockStateProperties.FACING, BlockStateProperties.FACING_HOPPER,
            BlockStateProperties.VERTICAL_DIRECTION, BlockStateProperties.ATTACH_FACE
        }) {
            if (state.hasProperty(property)) {
                return state.cycle(property);
            }
        }
        return state;
    }
}
