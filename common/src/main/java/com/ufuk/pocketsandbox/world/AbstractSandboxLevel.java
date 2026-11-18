package com.ufuk.pocketsandbox.world;

import com.ufuk.pocketsandbox.PocketSandboxClient;
import com.ufuk.pocketsandbox.platform.SandboxPlatform;
import com.ufuk.pocketsandbox.safety.FaultRegistry;
import com.ufuk.pocketsandbox.safety.Safe;
import com.ufuk.pocketsandbox.world.light.SceneLighting;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;
import org.jetbrains.annotations.Nullable;

/**
 * Shared base of the two scene levels. Both read and write the same {@link SandboxWorld}; they differ only in
 * {@code isClientSide}. There is no connection, no server, no chunk storage and no light engine behind them,
 * and neither ever becomes {@code Minecraft.level}.
 *
 * <p>Rule 3: registries, feature flags, tick rate and potion brewing are delegated to the live client level.
 */
public abstract class AbstractSandboxLevel extends Level {
    public static final ResourceKey<Level> DIMENSION =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(PocketSandboxClient.MOD_ID, "sandbox"));
    private static final int MAX_NEIGHBOR_UPDATES = 512;

    protected final SandboxWorld world;
    protected final FaultRegistry faults;
    private final RegistryAccess fallbackRegistries;
    private final Scoreboard scoreboard = new Scoreboard();
    private final Map<Long, SandboxChunk> chunks = new HashMap<>();
    private final SandboxChunkSource chunkSource;
    private final LevelLightEngine lightEngine;
    private final Holder<Biome> biome;

    protected AbstractSandboxLevel(ClientLevel real, SandboxWorld world, FaultRegistry faults, boolean clientSide) {
        super(new ClientLevel.ClientLevelData(Difficulty.PEACEFUL, false, false), DIMENSION, real.registryAccess(),
                real.dimensionTypeRegistration(), () -> InactiveProfiler.INSTANCE, clientSide, false, 0L,
                MAX_NEIGHBOR_UPDATES);
        this.world = world;
        this.faults = faults;
        this.fallbackRegistries = real.registryAccess();
        this.chunkSource = new SandboxChunkSource(this);
        this.lightEngine = new LevelLightEngine(new NoLightChunks(this), false, false);
        this.biome = fallbackRegistries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        ((ClientLevel.ClientLevelData) getLevelData()).setDayTime(6000L);
    }

    /** The live client level; required to build a scene. */
    protected static ClientLevel requireLive() {
        ClientLevel real = Minecraft.getInstance().level;
        if (real == null) {
            throw new IllegalStateException("Pocket Sandbox needs a loaded world");
        }
        return real;
    }

    public SandboxWorld world() {
        return world;
    }

    public FaultRegistry faults() {
        return faults;
    }

    public static String posKey(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    // ---- rule 3: delegation to the live client level ----

    @Nullable
    private static ClientLevel live() {
        return Minecraft.getInstance().level;
    }

    @Override
    public RegistryAccess registryAccess() {
        ClientLevel real = live();
        if (real != null) {
            return real.registryAccess();
        }
        // Only reachable during super() (field not set yet) or after the world was left.
        return fallbackRegistries;
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        ClientLevel real = live();
        return real != null ? real.enabledFeatures() : FeatureFlagSet.of();
    }

    @Override
    public TickRateManager tickRateManager() {
        ClientLevel real = live();
        return real != null ? real.tickRateManager() : new TickRateManager();
    }

    @Override
    public PotionBrewing potionBrewing() {
        ClientLevel real = live();
        return real != null ? real.potionBrewing() : PotionBrewing.EMPTY;
    }

    @Override
    public RecipeManager getRecipeManager() {
        ClientLevel real = live();
        return real != null ? real.getRecipeManager() : new RecipeManager(registryAccess());
    }

    // ---- blocks ----

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return world.getState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        BlockState state = world.getState(pos);
        return state.isAir() ? Fluids.EMPTY.defaultFluidState() : state.getFluidState();
    }

    @Override
    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos) {
        return world.getBlockEntity(pos);
    }

    @Override
    public void setBlockEntity(BlockEntity blockEntity) {
        world.putBlockEntity(blockEntity);
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
        world.removeBlockEntity(pos);
    }

    /**
     * Mirrors vanilla {@code Level.setBlock}: on the logic level (server side) the old state's
     * {@code onRemove} and the new state's {@code onPlace} run, then neighbours are notified and shapes are
     * updated (fences, panes, doors and other connecting blocks). Positions outside the box are refused.
     */
    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
        if (!world.accepts(pos)) {
            return false;
        }
        BlockState old = world.getState(pos);
        if (old == state) {
            return false;
        }
        BlockPos key = pos.immutable();
        world.setState(key, state);
        faults.clearAt(posKey(key));
        if (!isClientSide) {
            Safe.attempt(faults, "remove@" + posKey(key), () -> old.onRemove(this, key, state, false));
        }
        syncBlockEntity(key, old, state);
        if (!isClientSide) {
            Safe.attempt(faults, "place@" + posKey(key), () -> state.onPlace(this, key, old, false));
        }
        if ((flags & Block.UPDATE_NEIGHBORS) != 0) {
            Safe.attempt(faults, "neighbors@" + posKey(key), () -> updateNeighborsAt(key, old.getBlock()));
        }
        if ((flags & Block.UPDATE_KNOWN_SHAPE) == 0 && recursionLeft > 0) {
            int shapeFlags = flags & ~(Block.UPDATE_NEIGHBORS | Block.UPDATE_SUPPRESS_DROPS);
            Safe.attempt(faults, "shape@" + posKey(key), () -> {
                old.updateIndirectNeighbourShapes(this, key, shapeFlags, recursionLeft - 1);
                state.updateNeighbourShapes(this, key, shapeFlags, recursionLeft - 1);
                state.updateIndirectNeighbourShapes(this, key, shapeFlags, recursionLeft - 1);
            });
        }
        return true;
    }

    /** Keeps the block entity in line with the new state: drop, update or create it (guarded, rule 1). */
    private void syncBlockEntity(BlockPos pos, BlockState old, BlockState state) {
        BlockEntity existing = world.getBlockEntity(pos);
        if (existing != null && (!state.hasBlockEntity() || !state.is(old.getBlock())
                || !existing.getType().isValid(state))) {
            world.removeBlockEntity(pos);
            existing = null;
        }
        if (!state.hasBlockEntity()) {
            return;
        }
        if (existing != null) {
            existing.setBlockState(state);
            return;
        }
        if (state.getBlock() instanceof EntityBlock entityBlock) {
            BlockEntity created = Safe.get(faults, "be-create@" + posKey(pos),
                    () -> entityBlock.newBlockEntity(pos, state), null);
            if (created != null) {
                world.putBlockEntity(created); // binds it to the client level (rule 1)
                Safe.attempt(faults, "be-load@" + posKey(pos), () -> SandboxPlatform.onBlockEntityLoaded(created));
            }
        }
    }

    @Override
    public boolean isLoaded(BlockPos pos) {
        return world.accepts(pos);
    }

    @Override
    public LevelChunk getChunk(int chunkX, int chunkZ) {
        return chunks.computeIfAbsent(ChunkPos.asLong(chunkX, chunkZ),
                key -> new SandboxChunk(this, new ChunkPos(chunkX, chunkZ), biome));
    }

    @Override
    public ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus status, boolean require) {
        return getChunk(chunkX, chunkZ);
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        for (int y = world.bounds().maxY(); y >= world.bounds().minY(); y--) {
            if (!world.getState(new BlockPos(x, y, z)).isAir()) {
                return y + 1;
            }
        }
        return world.bounds().minY();
    }

    @Override
    public ChunkSource getChunkSource() {
        return chunkSource;
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return BlackholeTickAccess.emptyLevelList();
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        return BlackholeTickAccess.emptyLevelList();
    }

    @Override
    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        world.markChanged();
    }

    // ---- virtual redstone ----

    /**
     * Signal of the block at {@code pos} towards {@code direction}. A virtually powered block works like a
     * redstone block of that strength (it powers every neighbour: wire, lamps, pistons on any side) and is
     * itself seen as powered by its neighbours, so a lamp or door that is powered directly reacts too.
     */
    @Override
    public int getSignal(BlockPos pos, Direction direction) {
        int real = super.getSignal(pos, direction);
        int emitted = world.power(pos);
        int received = world.power(pos.relative(direction.getOpposite()));
        return Math.max(real, Math.max(emitted, received));
    }

    @Override
    public int getDirectSignal(BlockPos pos, Direction direction) {
        int real = super.getDirectSignal(pos, direction);
        int received = world.power(pos.relative(direction.getOpposite()));
        return Math.max(real, received);
    }

    /** Keeps the level's clock in step with the scene clock (scheduled ticks are keyed by it). */
    public void syncTime() {
        ((ClientLevel.ClientLevelData) getLevelData()).setGameTime(world.gameTime());
    }

    // ---- particles: routed to the scene's own particle list ----

    @Override
    public void addParticle(ParticleOptions options, double x, double y, double z, double vx, double vy, double vz) {
        world.emitParticle(options, x, y, z, vx, vy, vz);
    }

    @Override
    public void addParticle(ParticleOptions options, boolean force, double x, double y, double z, double vx,
            double vy, double vz) {
        world.emitParticle(options, x, y, z, vx, vy, vz);
    }

    @Override
    public void addAlwaysVisibleParticle(ParticleOptions options, double x, double y, double z, double vx,
            double vy, double vz) {
        world.emitParticle(options, x, y, z, vx, vy, vz);
    }

    @Override
    public void addAlwaysVisibleParticle(ParticleOptions options, boolean force, double x, double y, double z,
            double vx, double vy, double vz) {
        world.emitParticle(options, x, y, z, vx, vy, vz);
    }

    // ---- light (flood-filled light map of the box), tint, shade ----

    private SceneLighting lighting() {
        SceneLighting lighting = world.lighting();
        lighting.ensure(world.version(), world.bounds(), this, world::getState, faults);
        return lighting;
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        SceneLighting lighting = lighting();
        return layer == LightLayer.SKY ? lighting.sky(pos) : lighting.block(pos);
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darkening) {
        SceneLighting lighting = lighting();
        return Math.max(lighting.sky(pos) - darkening, lighting.block(pos));
    }

    @Override
    public int getMaxLocalRawBrightness(BlockPos pos) {
        return getRawBrightness(pos, 0);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return lightEngine;
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        if (!shade) {
            return 1.0F;
        }
        return switch (direction) {
            case DOWN -> 0.5F;
            case UP -> 1.0F;
            case NORTH, SOUTH -> 0.8F;
            case WEST, EAST -> 0.6F;
        };
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return resolver.getColor(biome.value(), pos.getX(), pos.getZ());
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return biome;
    }

    // ---- entities (F6 adds the dummy and the preview player) ----

    public SandboxEntities sandboxEntities() {
        return world.entities();
    }

    /**
     * Only decorations are accepted (paintings, item frames, armour stands); everything else a block or item
     * would spawn (drops, mobs from eggs, xp) is refused, so the scene never fills up with things it does not
     * simulate.
     */
    @Override
    public boolean addFreshEntity(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.decoration.HangingEntity
                || entity instanceof net.minecraft.world.entity.decoration.ArmorStand) {
            world.entities().add(entity);
            world.markChanged();
            return true;
        }
        return false;
    }

    @Override
    @Nullable
    public Entity getEntity(int id) {
        return world.entities().get(id);
    }

    @Override
    protected LevelEntityGetter<Entity> getEntities() {
        return world.entities();
    }

    @Override
    public List<? extends Player> players() {
        return List.of();
    }

    // ---- everything that would touch the outside world is a no-op ----

    @Override
    public void playSeededSound(@Nullable Player player, double x, double y, double z, Holder<SoundEvent> sound,
            SoundSource source, float volume, float pitch, long seed) {
    }

    @Override
    public void playSeededSound(@Nullable Player player, Entity entity, Holder<SoundEvent> sound, SoundSource source,
            float volume, float pitch, long seed) {
    }

    @Override
    public void levelEvent(@Nullable Player player, int type, BlockPos pos, int data) {
    }

    @Override
    public void gameEvent(Holder<GameEvent> event, Vec3 position, GameEvent.Context context) {
    }

    @Override
    public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) {
    }

    @Override
    public String gatherChunkSourceStats() {
        return "Pocket Sandbox";
    }

    @Override
    @Nullable
    public MapItemSavedData getMapData(MapId id) {
        return null;
    }

    @Override
    public void setMapData(MapId id, MapItemSavedData data) {
    }

    @Override
    public MapId getFreeMapId() {
        return new MapId(0);
    }

    @Override
    public Scoreboard getScoreboard() {
        return scoreboard;
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        return new DifficultyInstance(Difficulty.PEACEFUL, getDayTime(), 0L, 0.0F);
    }

    /** Light engine stand-in: no chunks, no layers. All light comes from the getBrightness overrides. */
    private record NoLightChunks(BlockGetter level) implements LightChunkGetter {
        @Override
        @Nullable
        public LightChunk getChunkForLighting(int chunkX, int chunkZ) {
            return null;
        }

        @Override
        public BlockGetter getLevel() {
            return level;
        }
    }
}
