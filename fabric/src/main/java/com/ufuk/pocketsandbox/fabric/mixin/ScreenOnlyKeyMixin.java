package com.ufuk.pocketsandbox.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla keeps one key mapping per physical key: our "hold W" (only used inside screens) would take W away from
 * walking forward. Our screen-only keys never claim a key another mapping already has; they are read straight from
 * the keyboard inside screens, so they still work. (NeoForge keeps several mappings per key, no fix needed there.)
 */
@Mixin(KeyMapping.class)
abstract class ScreenOnlyKeyMixin {
    @WrapOperation(method = {"<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILjava/lang/String;)V",
            "resetMapping"}, at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object pocketsandbox$screenOnly(Map<Object, Object> map, Object key, Object value,
            Operation<Object> original) {
        if (value instanceof KeyMapping mapping && isScreenOnly(mapping.getName())) {
            Object existing = map.get(key);
            if (existing != null && existing != value) {
                return existing;
            }
        }
        return original.call(map, key, value);
    }

    private static boolean isScreenOnly(String name) {
        return name.equals("key.pocketsandbox.hold_open") || name.equals("key.pocketsandbox.ponder_test_world")
                || name.equals("key.pocketsandbox.ponder_scene");
    }
}
