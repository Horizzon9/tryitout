package com.ufuk.tryitout.render;

import com.ufuk.tryitout.TryItOutClient;
import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Creates particles with the game's registered providers but for the scene's own level, without adding them
 * to the real particle engine. The provider map is private and its type differs by loader (int ids on
 * Fabric, resource locations on NeoForge), so it is found once by reflection: the map whose values are
 * providers. Read only.
 */
final class ParticleProviders {
    private static boolean searched;
    @Nullable
    private static Map<?, ?> providers;

    private ParticleProviders() {
    }

    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Particle create(ParticleOptions options, ClientLevel level, double x, double y, double z, double vx,
            double vy, double vz) {
        Map<?, ?> map = providers();
        if (map == null) {
            return null;
        }
        ParticleType<?> type = options.getType();
        // Typed maps (fastutil Int2ObjectMap on Fabric) throw on a key of the wrong type: pick the key by
        // looking at an existing key first.
        Object sampleKey = map.keySet().iterator().next();
        Object key = sampleKey instanceof Integer
                ? Integer.valueOf(BuiltInRegistries.PARTICLE_TYPE.getId(type))
                : BuiltInRegistries.PARTICLE_TYPE.getKey(type);
        Object provider = map.get(key);
        if (!(provider instanceof ParticleProvider particleProvider)) {
            return null;
        }
        return particleProvider.createParticle(options, level, x, y, z, vx, vy, vz);
    }

    @Nullable
    private static Map<?, ?> providers() {
        if (searched) {
            return providers;
        }
        searched = true;
        ParticleEngine engine = Minecraft.getInstance().particleEngine;
        for (Field field : ParticleEngine.class.getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Map<?, ?> map = (Map<?, ?>) field.get(engine);
                if (map != null && !map.isEmpty() && map.values().iterator().next() instanceof ParticleProvider<?>) {
                    providers = map;
                    return providers;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                TryItOutClient.LOGGER.debug("TryItOut: could not read particle field {}", field, e);
            }
        }
        TryItOutClient.LOGGER.warn("TryItOut: particle providers not found; scene particles disabled");
        return null;
    }
}
