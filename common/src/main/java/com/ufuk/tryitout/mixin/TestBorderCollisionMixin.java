package com.ufuk.tryitout.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ufuk.tryitout.testworld.TestBorderShapes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The test area's invisible border walls join the shapes an entity collides with (like the world border). */
@Mixin(Entity.class)
public abstract class TestBorderCollisionMixin {
    @WrapOperation(method = "collide", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<VoxelShape> tryitout$border(Level level, Entity entity, AABB sweep,
            Operation<List<VoxelShape>> original) {
        List<VoxelShape> shapes = original.call(level, entity, sweep);
        List<VoxelShape> border = TestBorderShapes.shapesFor((Entity) (Object) this, sweep);
        if (border.isEmpty()) {
            return shapes;
        }
        List<VoxelShape> all = new ArrayList<>(shapes.size() + border.size());
        all.addAll(shapes);
        all.addAll(border);
        return all;
    }
}
