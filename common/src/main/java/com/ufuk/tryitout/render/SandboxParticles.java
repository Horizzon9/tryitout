package com.ufuk.tryitout.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.ufuk.tryitout.TryItOutClient;
import com.ufuk.tryitout.safety.SandboxGuard;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

/**
 * The scene's own particles: created with the game's providers against the scene's client view, ticked by
 * the scene ticker and drawn into the scene frame buffer. Never added to the real particle engine. A
 * particle whose provider, tick or render throws is dropped (and that particle type is not created again).
 */
public final class SandboxParticles {

    private final List<Particle> particles = new ArrayList<>();
    private final java.util.Set<Object> brokenTypes = new java.util.HashSet<>();
    private final SceneCamera camera = new SceneCamera();
    @Nullable
    private final ClientLevel view;

    /** {@code view} null: the client view could not be built, so particles are off. */
    public SandboxParticles(@Nullable ClientLevel view) {
        this.view = view;
    }

    public boolean enabled() {
        return view != null;
    }

    public int count() {
        return particles.size() + spawned.size();
    }

    /**
     * Particles created while the list is being ticked or drawn (campfire smoke, lava pops and many modded
     * particles spawn more particles from their own tick). They join the list afterwards, never during
     * iteration.
     */
    private final List<Particle> spawned = new ArrayList<>();

    public void add(ParticleOptions options, double x, double y, double z, double vx, double vy, double vz) {
        var config = com.ufuk.tryitout.config.SandboxConfig.get();
        if (view == null || !config.particles || count() >= config.maxParticles
                || brokenTypes.contains(options.getType())) {
            return;
        }
        try (SandboxGuard.Scope ignored = SandboxGuard.enter()) {
            Particle particle = ParticleProviders.create(options, view, x, y, z, vx, vy, vz);
            if (particle != null) {
                spawned.add(particle);
            }
        } catch (RuntimeException | LinkageError e) {
            if (brokenTypes.add(options.getType())) {
                TryItOutClient.LOGGER.warn("TryItOut: particle {} failed to spawn", options.getType(), e);
            }
        }
    }

    public void tick() {
        mergeSpawned();
        List<Particle> snapshot = new ArrayList<>(particles);
        List<Particle> dead = new ArrayList<>();
        try (SandboxGuard.Scope ignored = SandboxGuard.enter()) {
            for (Particle particle : snapshot) {
                try {
                    particle.tick();
                    if (!particle.isAlive()) {
                        dead.add(particle);
                    }
                } catch (RuntimeException | LinkageError e) {
                    dead.add(particle);
                }
            }
        }
        particles.removeAll(dead);
        mergeSpawned();
    }

    private void mergeSpawned() {
        if (spawned.isEmpty()) {
            return;
        }
        int room = Math.max(0, com.ufuk.tryitout.config.SandboxConfig.get().maxParticles - particles.size());
        particles.addAll(spawned.subList(0, Math.min(room, spawned.size())));
        spawned.clear();
    }

    /**
     * Draws all particles. {@code view} is the scene view matrix, {@code eye} the camera position and
     * {@code yaw / pitch} the orbit angles (billboards face the camera like in game).
     */
    public void render(Matrix4f view, Vector3f eye, float yaw, float pitch, float partialTick) {
        if (particles.isEmpty()) {
            return;
        }
        camera.set(new Vec3(eye.x, eye.y, eye.z), yaw - 180.0F, pitch);
        Map<ParticleRenderType, List<Particle>> byType = new LinkedHashMap<>();
        for (Particle particle : particles) {
            byType.computeIfAbsent(particle.getRenderType(), key -> new ArrayList<>()).add(particle);
        }
        Minecraft mc = Minecraft.getInstance();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try (SandboxGuard.Scope ignored = SandboxGuard.enter()) {
            modelView.mul(view).translate(eye.x, eye.y, eye.z);
            RenderSystem.applyModelViewMatrix();
            mc.gameRenderer.lightTexture().turnOnLightLayer(); // redirected to the scene light map
            RenderSystem.enableDepthTest();
            for (Map.Entry<ParticleRenderType, List<Particle>> entry : byType.entrySet()) {
                draw(entry.getKey(), entry.getValue(), partialTick, mc);
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private void draw(ParticleRenderType type, List<Particle> list, float partialTick, Minecraft mc) {
        if (type == ParticleRenderType.NO_RENDER) {
            return;
        }
        BufferBuilder builder;
        try {
            builder = type.begin(Tesselator.getInstance(), mc.getTextureManager());
        } catch (RuntimeException e) {
            particles.removeAll(list);
            return;
        }
        if (builder == null) {
            return; // CUSTOM types draw themselves elsewhere in vanilla; not supported in the scene
        }
        for (Particle particle : list) {
            try {
                particle.render(builder, camera, partialTick);
            } catch (RuntimeException | LinkageError e) {
                particles.remove(particle);
            }
        }
        try {
            MeshData mesh = builder.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        } catch (RuntimeException e) {
            particles.removeAll(list); // a half-written particle left the buffer unusable
        }
    }

    public void clear() {
        particles.clear();
        spawned.clear();
    }

    /** A camera that only carries the scene eye and rotation for particle billboards. */
    private static final class SceneCamera extends Camera {
        void set(Vec3 position, float yRot, float xRot) {
            setPosition(position);
            setRotation(yRot, xRot);
        }
    }
}
