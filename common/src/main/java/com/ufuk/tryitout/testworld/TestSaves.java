package com.ufuk.tryitout.testworld;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Saved test worlds: {@code <world>/tryitout/saves/<owner>/<name>.nbt} holds the room settings, the test
 * profile (the test character's inventory and state at that moment) and the area's blocks, block entities and
 * entities (a vanilla structure template, walls and floor included). Only the test profile is stored, never
 * the real one, so loading a save can only ever give things back inside the test world.
 */
final class TestSaves {
    static final int MAX_NAME = 32;

    private TestSaves() {
    }

    /** Letters, digits, spaces, - and _; trimmed and shortened. Empty when nothing usable is left. */
    static String cleanName(String raw) {
        StringBuilder out = new StringBuilder();
        for (char c : (raw == null ? "" : raw.trim()).toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_') {
                out.append(c);
            }
            if (out.length() >= MAX_NAME) {
                break;
            }
        }
        return out.toString().trim();
    }

    private static Path dir(MinecraftServer server, UUID owner) {
        return server.getWorldPath(LevelResource.ROOT).resolve("tryitout").resolve("saves").resolve(owner.toString());
    }

    private static Path file(MinecraftServer server, UUID owner, String name) {
        // File names in lower case: no two saves differing only by case on Windows.
        return dir(server, owner).resolve(name.toLowerCase(Locale.ROOT).replace(' ', '_') + ".nbt");
    }

    static List<String> list(MinecraftServer server, UUID owner) {
        List<String> names = new ArrayList<>();
        Path dir = dir(server, owner);
        if (!Files.isDirectory(dir)) {
            return names;
        }
        try (var files = Files.list(dir)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".nbt")).sorted().toList()) {
                read(path).ifPresent(tag -> names.add(tag.getString("Name")));
            }
        } catch (IOException e) {
            return names;
        }
        return names;
    }

    static boolean exists(MinecraftServer server, UUID owner, String name) {
        return Files.exists(file(server, owner, name));
    }

    /**
     * Box saved with the area: the whole build box inside the border markers (blocks next to and below the
     * floor too, not only the floor square).
     */
    private static BlockPos origin(TestArea area) {
        return new BlockPos(area.buildMinX(), area.buildMinY(), area.buildMinZ());
    }

    private static Vec3i size(TestArea area) {
        return new Vec3i(area.buildMaxX() - area.buildMinX() + 1, area.buildMaxY() - area.buildMinY() + 1,
                area.buildMaxZ() - area.buildMinZ() + 1);
    }

    /** Saves made before the build box held only the floor square + wall ring, from the floor layer up. */
    private static BlockPos legacyOrigin(TestArea area) {
        return new BlockPos(area.minX() - 1, area.floorY(), area.minZ() - 1);
    }

    static void save(MinecraftServer server, UUID owner, String name, CompoundTag room, CompoundTag profile,
            ServerLevel level, TestArea area) throws IOException {
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, origin(area), size(area), true, null);
        CompoundTag tag = new CompoundTag();
        tag.putInt("Format", 2);
        tag.putString("Name", name);
        tag.put("Room", room.copy());
        tag.put("Profile", profile.copy());
        tag.put("Structure", template.save(new CompoundTag()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag, out);
        AtomicFiles.write(file(server, owner, name), out.toByteArray());
    }

    static java.util.Optional<CompoundTag> load(MinecraftServer server, UUID owner, String name) {
        return read(file(server, owner, name));
    }

    static void delete(MinecraftServer server, UUID owner, String name) throws IOException {
        Path path = file(server, owner, name);
        Files.deleteIfExists(path);
        Files.deleteIfExists(AtomicFiles.backup(path));
    }

    /** Puts the saved blocks and entities back into a freshly reset area. */
    static void place(ServerLevel level, TestArea area, CompoundTag save) {
        StructureTemplate template = new StructureTemplate();
        template.load(level.holderLookup(Registries.BLOCK), save.getCompound("Structure"));
        BlockPos origin = save.getInt("Format") >= 2 ? origin(area) : legacyOrigin(area);
        template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
    }

    private static java.util.Optional<CompoundTag> read(Path path) {
        return AtomicFiles.read(path,
                data -> NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap()));
    }
}
