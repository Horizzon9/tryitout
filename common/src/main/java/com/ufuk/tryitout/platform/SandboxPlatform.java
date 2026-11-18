package com.ufuk.tryitout.platform;

import com.ufuk.tryitout.render.BlockMesher;
import com.ufuk.tryitout.render.VanillaBlockMesher;
import java.util.function.Consumer;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Loader-specific pieces. Fabric keeps the vanilla defaults; NeoForge installs its own at startup. */
public final class SandboxPlatform {
    private static BlockMesher mesher = new VanillaBlockMesher();
    private static Consumer<BlockEntity> blockEntityLoaded = be -> {
    };

    private SandboxPlatform() {
    }

    public static void installMesher(BlockMesher value) {
        mesher = value;
    }

    public static BlockMesher mesher() {
        return mesher;
    }

    /** NeoForge: {@code BlockEntity#onLoad}, which the scene would otherwise never call. */
    public static void installBlockEntityLoaded(Consumer<BlockEntity> hook) {
        blockEntityLoaded = hook;
    }

    public static void onBlockEntityLoaded(BlockEntity be) {
        blockEntityLoaded.accept(be);
    }
}
