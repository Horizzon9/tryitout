package com.ufuk.pocketsandbox.world;

import com.mojang.authlib.GameProfile;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.MenuProvider;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The placer / user of scene interactions. A bare {@link Player}: no connection, no game mode, not a
 * {@code LocalPlayer} or {@code ServerPlayer}, never added to any real world. Creative, so nothing is consumed.
 * Uses its own fixed id so mods keyed by player UUID never mix it up with the real player.
 */
public final class SandboxPlayer extends Player {
    private static final GameProfile PROFILE =
            new GameProfile(UUID.fromString("5a4dbb0c-7c2b-4f0e-9a3c-50c4e7a5b0c1"), "PocketSandbox");

    @Nullable
    private MenuProvider pendingMenu;

    public SandboxPlayer(Level level) {
        super(level, BlockPos.ZERO, 0.0F, PROFILE);
        getAbilities().instabuild = true;
        getAbilities().mayBuild = true;
    }

    /** A block asked to open its GUI: remembered for the sandbox screen, never sent anywhere. */
    @Override
    public OptionalInt openMenu(@Nullable MenuProvider provider) {
        if (provider == null) {
            return OptionalInt.empty();
        }
        pendingMenu = provider;
        return OptionalInt.of(1);
    }

    /** NeoForge's {@code openMenu(provider, extraData)} (modded machines); the extra data is not needed locally. */
    public OptionalInt openMenu(@Nullable MenuProvider provider, Consumer<RegistryFriendlyByteBuf> extraData) {
        return openMenu(provider);
    }

    /** The GUI requested by the last interaction, cleared on read. */
    @Nullable
    public MenuProvider takePendingMenu() {
        MenuProvider provider = pendingMenu;
        pendingMenu = null;
        return provider;
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return true;
    }

    /** Puts the player at the camera, looking along {@code (dx, dy, dz)}, so facing-aware placement works. */
    public void aim(double x, double y, double z, double dx, double dy, double dz) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-6) {
            return;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.asin(dy));
        setPos(x, y - getEyeHeight(), z);
        setYRot(yaw);
        setXRot(pitch);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        yRotO = yaw;
        xRotO = pitch;
    }
}
