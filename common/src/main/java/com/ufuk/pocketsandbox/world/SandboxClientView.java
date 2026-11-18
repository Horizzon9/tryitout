package com.ufuk.pocketsandbox.world;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.Packet;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link ClientLevel} that is only a window onto the scene, for APIs that demand a ClientLevel (particle
 * providers above all). Blocks, block entities, light, tint and collisions come from the client
 * {@link SandboxLevel}; it holds no chunks of its own and is never {@code Minecraft.level}.
 *
 * <p>Rule 2: every callback that would reach the real {@code LevelRenderer} (dirty sections, block updates,
 * light) is a no-op, the view distance is the minimum, and nothing is sent to the server, played or ticked.
 * NeoForge's {@code LevelEvent.Load} from the constructor is suppressed by a mixin while
 * {@link #isConstructing()} is true.
 */
public final class SandboxClientView extends ClientLevel {
    private static final int VIEW_DISTANCE = 2;
    private static boolean constructing;

    private final SandboxLevel scene;

    private SandboxClientView(ClientPacketListener connection, ClientLevel real, SandboxLevel scene) {
        super(connection, new ClientLevelData(Difficulty.PEACEFUL, false, false), AbstractSandboxLevel.DIMENSION,
                real.dimensionTypeRegistration(), VIEW_DISTANCE, VIEW_DISTANCE, () -> InactiveProfiler.INSTANCE,
                Minecraft.getInstance().levelRenderer, false, 0L);
        this.scene = scene;
    }

    /** Builds the view for {@code scene}; throws when there is no live world (callers guard this). */
    public static SandboxClientView create(SandboxLevel scene) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel real = mc.level;
        ClientPacketListener connection = mc.getConnection();
        if (real == null || connection == null) {
            throw new IllegalStateException("Pocket Sandbox needs a loaded world");
        }
        constructing = true;
        try {
            return new SandboxClientView(connection, real, scene);
        } finally {
            constructing = false;
        }
    }

    /** True only while the constructor runs (NeoForge mixin: skip LevelEvent.Load). */
    public static boolean isConstructing() {
        return constructing;
    }

    // ---- rule 3 ----

    @Override
    public RegistryAccess registryAccess() {
        return scene == null ? super.registryAccess() : scene.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return scene == null ? super.enabledFeatures() : scene.enabledFeatures();
    }

    // ---- reads go to the scene ----

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return scene.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return scene.getFluidState(pos);
    }

    @Override
    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos) {
        return scene.getBlockEntity(pos);
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return scene.getBrightness(layer, pos);
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darkening) {
        return scene.getRawBrightness(pos, darkening);
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return scene.getShade(direction, shade);
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return scene.getBlockTint(pos, resolver);
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return scene.getUncachedNoiseBiome(x, y, z);
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return true;
    }

    @Override
    public boolean isLoaded(BlockPos pos) {
        return scene.isLoaded(pos);
    }

    @Override
    public BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        return scene.getChunk(chunkX, chunkZ);
    }

    @Override
    public List<net.minecraft.client.player.AbstractClientPlayer> players() {
        return List.of();
    }

    // ---- writes and side effects: nothing reaches the real game ----

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
        return false;
    }

    @Override
    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
    }

    @Override
    public void setBlocksDirty(BlockPos pos, BlockState oldState, BlockState newState) {
    }

    @Override
    public void setSectionDirtyWithNeighbors(int x, int y, int z) {
    }

    @Override
    public void onChunkLoaded(net.minecraft.world.level.ChunkPos pos) {
    }

    @Override
    public void queueLightUpdate(Runnable task) {
    }

    @Override
    public void addDestroyBlockEffect(BlockPos pos, BlockState state) {
    }

    @Override
    public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) {
    }

    @Override
    public void levelEvent(@Nullable Player player, int type, BlockPos pos, int data) {
    }

    @Override
    public void globalLevelEvent(int type, BlockPos pos, int data) {
    }

    @Override
    public void gameEvent(Holder<GameEvent> event, Vec3 position, GameEvent.Context context) {
    }

    @Override
    public void sendPacketToServer(Packet<?> packet) {
    }

    @Override
    public void playSeededSound(@Nullable Player player, double x, double y, double z, Holder<SoundEvent> sound,
            SoundSource source, float volume, float pitch, long seed) {
    }

    @Override
    public void playSeededSound(@Nullable Player player, Entity entity, Holder<SoundEvent> sound, SoundSource source,
            float volume, float pitch, long seed) {
    }

    @Override
    public void playLocalSound(Entity entity, SoundEvent sound, SoundSource source, float volume, float pitch) {
    }

    @Override
    public void playLocalSound(double x, double y, double z, SoundEvent sound, SoundSource source, float volume,
            float pitch, boolean distanceDelay) {
    }

    @Override
    public void createFireworks(double x, double y, double z, double vx, double vy, double vz,
            List<FireworkExplosion> explosions) {
    }

    @Override
    public void addParticle(ParticleOptions options, double x, double y, double z, double vx, double vy, double vz) {
        scene.addParticle(options, x, y, z, vx, vy, vz);
    }

    @Override
    public void addParticle(ParticleOptions options, boolean force, double x, double y, double z, double vx,
            double vy, double vz) {
        scene.addParticle(options, x, y, z, vx, vy, vz);
    }

    @Override
    public void addAlwaysVisibleParticle(ParticleOptions options, double x, double y, double z, double vx,
            double vy, double vz) {
        scene.addParticle(options, x, y, z, vx, vy, vz);
    }

    @Override
    public void addAlwaysVisibleParticle(ParticleOptions options, boolean force, double x, double y, double z,
            double vx, double vy, double vz) {
        scene.addParticle(options, x, y, z, vx, vy, vz);
    }
}
