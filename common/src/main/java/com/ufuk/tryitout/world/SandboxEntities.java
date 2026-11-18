package com.ufuk.tryitout.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/** The scene's own entities (dummy, preview player). Plain list; never shared with the real world. */
public final class SandboxEntities implements LevelEntityGetter<Entity> {
    private final Map<Integer, Entity> byId = new LinkedHashMap<>();

    public void add(Entity entity) {
        byId.put(entity.getId(), entity);
    }

    public void remove(Entity entity) {
        byId.remove(entity.getId());
    }

    public void clear() {
        byId.clear();
    }

    @Override
    @Nullable
    public Entity get(int id) {
        return byId.get(id);
    }

    @Override
    @Nullable
    public Entity get(UUID uuid) {
        for (Entity entity : byId.values()) {
            if (entity.getUUID().equals(uuid)) {
                return entity;
            }
        }
        return null;
    }

    @Override
    public Iterable<Entity> getAll() {
        return new ArrayList<>(byId.values());
    }

    @Override
    public <U extends Entity> void get(EntityTypeTest<Entity, U> test, AbortableIterationConsumer<U> consumer) {
        for (Entity entity : snapshot()) {
            U cast = test.tryCast(entity);
            if (cast != null && consumer.accept(cast).shouldAbort()) {
                return;
            }
        }
    }

    @Override
    public void get(AABB box, Consumer<Entity> consumer) {
        for (Entity entity : snapshot()) {
            if (entity.getBoundingBox().intersects(box)) {
                consumer.accept(entity);
            }
        }
    }

    @Override
    public <U extends Entity> void get(EntityTypeTest<Entity, U> test, AABB box, AbortableIterationConsumer<U> consumer) {
        for (Entity entity : snapshot()) {
            U cast = test.tryCast(entity);
            if (cast != null && entity.getBoundingBox().intersects(box) && consumer.accept(cast).shouldAbort()) {
                return;
            }
        }
    }

    private List<Entity> snapshot() {
        return new ArrayList<>(byId.values());
    }
}
