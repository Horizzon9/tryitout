package com.ufuk.pocketsandbox.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Keys, all rebindable in Controls and only handled inside screens: hold W over an item to open the sandbox
 * (like Create's Ponder; items with a Ponder scene keep Ponder's own W), and in a Ponder screen B opens its
 * current scene in the test world, G in the pocket scene.
 */
public final class SandboxKeys {
    public static final KeyMapping OPEN = new KeyMapping("key.pocketsandbox.hold_open", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_W, "key.categories.pocketsandbox");
    public static final KeyMapping PONDER_TEST = new KeyMapping("key.pocketsandbox.ponder_test_world",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.pocketsandbox");
    public static final KeyMapping PONDER_SCENE = new KeyMapping("key.pocketsandbox.ponder_scene",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.pocketsandbox");

    private SandboxKeys() {
    }

    public static void init() {
        KeyMappingRegistry.register(OPEN);
        KeyMappingRegistry.register(PONDER_TEST);
        KeyMappingRegistry.register(PONDER_SCENE);
    }

    /** True while the key bound to {@code mapping} is physically held (works inside screens, unlike isDown). */
    public static boolean held(KeyMapping mapping) {
        InputConstants.Key key = InputConstants.getKey(mapping.saveString());
        if (key == InputConstants.UNKNOWN) {
            return false;
        }
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, key.getValue());
    }
}
