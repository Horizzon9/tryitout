package com.ufuk.tryitout.world;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * The player model in weapon mode: a {@link RemotePlayer} on the scene's client view, wearing the real
 * player's skin, holding the item. Only animation state is driven here; {@code Item#use} and attack logic
 * are never called, and it is never ticked by any real level.
 *
 * <p>Rule 5: {@link #tickAnimation()} copies {@code attackAnim} into {@code oAttackAnim} and calls
 * {@code updateSwingTime()} every tick, otherwise the swing would freeze half way.
 */
public final class PreviewPlayer extends RemotePlayer {
    private static final int LIVING_FLAG_USING = 1;
    private static final int LIVING_FLAG_OFFHAND = 2;

    public PreviewPlayer(ClientLevel level, GameProfile profile) {
        super(level, profile);
    }

    public void hold(ItemStack stack) {
        setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
    }

    /** Faces {@code yaw} degrees (0 = +Z) with body and head. */
    public void face(float yaw) {
        setYRot(yaw);
        yRotO = yaw;
        yBodyRot = yaw;
        yBodyRotO = yaw;
        yHeadRot = yaw;
        yHeadRotO = yaw;
        setXRot(0.0F);
        xRotO = 0.0F;
    }

    public void attack() {
        swing(InteractionHand.MAIN_HAND);
    }

    /**
     * Starts the item's use animation (bow draw, eating, drinking, shield) or stops it when already using.
     * Returns false when the item has no use animation.
     */
    public boolean toggleUse() {
        if (isUsingItem()) {
            stopUse();
            return true;
        }
        ItemStack held = getMainHandItem();
        int duration = held.isEmpty() ? 0 : held.getUseDuration(this);
        if (duration <= 0) {
            return false;
        }
        useItem = held;
        useItemRemaining = duration;
        setLivingEntityFlag(LIVING_FLAG_USING, true);
        setLivingEntityFlag(LIVING_FLAG_OFFHAND, false);
        return true;
    }

    public void stopUse() {
        setLivingEntityFlag(LIVING_FLAG_USING, false);
        useItem = ItemStack.EMPTY;
        useItemRemaining = 0;
    }

    /** One client tick of animation only (rule 5). */
    public void tickAnimation() {
        tickCount++;
        oAttackAnim = attackAnim;
        updateSwingTime();
        if (isUsingItem()) {
            useItemRemaining--;
            if (useItemRemaining <= 0) {
                stopUse();
            }
        }
    }
}
