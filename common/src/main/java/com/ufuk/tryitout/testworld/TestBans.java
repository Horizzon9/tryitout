package com.ufuk.tryitout.testworld;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Items and blocks the server admin banned from the test world (by default: known cross-dimension storage such as
 * Ender Storage, AE2 wireless terminals and quantum links, Refined Storage wireless grids and network
 * transmitters, Mekanism's quantum entangloporter). Their ids only matter when those mods are installed; other
 * mods with the same kind of link can be added to {@code bannedBlocks} / {@code bannedItems}.
 */
final class TestBans {
    private TestBans() {
    }

    static boolean blocked(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof BlockItem blockItem && blocked(blockItem.getBlock())) {
            return true;
        }
        return TestServerConfig.get().bannedItems.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    static boolean blocked(Block block) {
        return TestServerConfig.get().bannedBlocks.contains(BuiltInRegistries.BLOCK.getKey(block).toString());
    }

    static void tell(Player player) {
        if (!player.level().isClientSide()) {
            player.displayClientMessage(Component.translatable("tryitout.test.banned"), true);
        }
    }
}
