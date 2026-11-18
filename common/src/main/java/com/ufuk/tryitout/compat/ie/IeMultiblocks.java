package com.ufuk.tryitout.compat.ie;

import com.ufuk.tryitout.TryItOutClient;
import com.ufuk.tryitout.scene.FloorType;
import com.ufuk.tryitout.scene.WallSide;
import com.ufuk.tryitout.testworld.SceneSnapshot;
import com.ufuk.tryitout.world.SandboxBounds;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Immersive Engineering multiblocks. JEI (and "Just Enough Immersive Multiblocks") show a multiblock as the item of
 * its formed block; alone in the scene that is one broken part. For those items the scene opens with the whole
 * unformed structure instead, ready to be formed with the Engineer's Hammer in the test world.
 * No compile dependency: IE's API is read by reflection (checked with javap against IE 12.4.2) and the bridge turns
 * itself off when it does not match.
 */
public final class IeMultiblocks {
    private static final String HANDLER = "blusunrize.immersiveengineering.api.multiblocks.MultiblockHandler";
    private static final String MULTIBLOCK = HANDLER + "$IMultiblock";

    private static Boolean ready;
    private static Method getMultiblocks;
    private static Method getBlock;
    private static Method getStructure;
    private static Method getSize;
    /** Formed block → its multiblock, built once IE's multiblocks are registered. */
    private static Map<Block, Object> byBlock;

    /** The scene to open: the item to show and the structure. */
    public record Capture(ItemStack stack, SceneSnapshot room) {
    }

    private IeMultiblocks() {
    }

    private static boolean ready() {
        if (ready == null) {
            ready = bind();
        }
        return ready;
    }

    private static boolean bind() {
        try {
            Class<?> handler = Class.forName(HANDLER);
            Class<?> multiblock = Class.forName(MULTIBLOCK);
            getMultiblocks = handler.getMethod("getMultiblocks");
            getBlock = multiblock.getMethod("getBlock");
            getStructure = multiblock.getMethod("getStructure", Level.class);
            getSize = multiblock.getMethod("getSize", Level.class);
            TryItOutClient.LOGGER.info("TryItOut: Immersive Engineering multiblock support enabled");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (ReflectiveOperationException | LinkageError e) {
            TryItOutClient.LOGGER.warn("TryItOut: Immersive Engineering API changed, multiblock support off", e);
            return false;
        }
    }

    /** True when {@code stack} is the formed block of an IE multiblock. */
    public static boolean isMultiblock(ItemStack stack) {
        return multiblockOf(stack) != null;
    }

    private static Object multiblockOf(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem) || !ready()) {
            return null;
        }
        if (byBlock == null) {
            Map<Block, Object> map = new IdentityHashMap<>();
            try {
                for (Object multiblock : (List<?>) getMultiblocks.invoke(null)) {
                    Object block = getBlock.invoke(multiblock);
                    if (block instanceof Block b && b != net.minecraft.world.level.block.Blocks.AIR) {
                        map.put(b, multiblock);
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                TryItOutClient.LOGGER.warn("TryItOut: could not list Immersive Engineering multiblocks", e);
                ready = false;
                return null;
            }
            if (map.isEmpty()) {
                return null; // not registered yet: try again next time
            }
            byBlock = map;
        }
        return byBlock.get(blockItem.getBlock());
    }

    /** The whole unformed structure of the multiblock behind {@code stack}, centred on the floor. */
    public static Optional<Capture> capture(ItemStack stack) {
        Object multiblock = multiblockOf(stack);
        Level level = Minecraft.getInstance().level;
        if (multiblock == null || level == null) {
            return Optional.empty();
        }
        try {
            Vec3i size = (Vec3i) getSize.invoke(multiblock, level);
            List<?> structure = (List<?>) getStructure.invoke(multiblock, level);
            int offX = size.getX() / 2;
            int offZ = size.getZ() / 2;
            Map<BlockPos, BlockState> blocks = new HashMap<>();
            for (Object entry : structure) {
                if (!(entry instanceof StructureTemplate.StructureBlockInfo info) || info.state().isAir()) {
                    continue;
                }
                BlockPos pos = info.pos();
                blocks.put(new BlockPos(pos.getX() - offX, pos.getY() + 1, pos.getZ() - offZ), info.state());
            }
            if (blocks.isEmpty()) {
                return Optional.empty();
            }
            int reach = 1;
            int top = 1;
            for (BlockPos pos : blocks.keySet()) {
                reach = Math.max(reach, Math.max(Math.abs(pos.getX()), Math.abs(pos.getZ())));
                top = Math.max(top, pos.getY());
            }
            SceneSnapshot room = new SceneSnapshot(sceneSize(Math.max(reach * 2 + 3, top + 1)), FloorType.PONDER,
                    EnumSet.noneOf(WallSide.class), false, true, blocks);
            return Optional.of(new Capture(firstItem(blocks, stack), room));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            TryItOutClient.LOGGER.warn("TryItOut: could not read the Immersive Engineering multiblock", e);
            return Optional.empty();
        }
    }

    private static int sceneSize(int needed) {
        for (int size : SandboxBounds.SIZES) {
            if (size >= needed) {
                return size;
            }
        }
        return SandboxBounds.SIZES[SandboxBounds.SIZES.length - 1];
    }

    /** A block of the structure for palette slot 1 (the formed block itself cannot be placed alone). */
    private static ItemStack firstItem(Map<BlockPos, BlockState> blocks, ItemStack fallback) {
        for (BlockState state : blocks.values()) {
            Item item = state.getBlock().asItem();
            if (item != Items.AIR) {
                return new ItemStack(item);
            }
        }
        return fallback.copyWithCount(1);
    }
}
