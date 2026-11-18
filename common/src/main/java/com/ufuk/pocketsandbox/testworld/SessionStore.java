package com.ufuk.pocketsandbox.testworld;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Per-player session files under {@code <world>/pocketsandbox/}: {@code sessions/<uuid>.dat} holds the state
 * and the saved real profile (written with {@link AtomicFiles} before any swap), {@code slots.dat} the area
 * slot of every owner. Nothing here is kept only in memory.
 */
public final class SessionStore {
    private final Path root;

    public SessionStore(MinecraftServer server) {
        this.root = server.getWorldPath(LevelResource.ROOT).resolve("pocketsandbox");
    }

    /** Saved state of one player. {@code real} is the full real player NBT (empty while REAL). */
    public record Session(SessionState state, int slot, CompoundTag real, String realDimension,
            double x, double y, double z, float yaw, float pitch, CompoundTag room) {
        public Session withState(SessionState next) {
            return new Session(next, slot, real, realDimension, x, y, z, yaw, pitch, room);
        }

        /** Floor size of the room (falls back to the configured area size). */
        public int size() {
            int size = room.getInt("Size");
            return size > 0 ? size : Math.min(25, TestServerConfig.get().areaSize);
        }

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("State", state.name());
            tag.putInt("Slot", slot);
            tag.put("Real", real);
            tag.putString("RealDimension", realDimension);
            tag.putDouble("X", x);
            tag.putDouble("Y", y);
            tag.putDouble("Z", z);
            tag.putFloat("Yaw", yaw);
            tag.putFloat("Pitch", pitch);
            tag.put("Room", room);
            return tag;
        }

        static Session fromTag(CompoundTag tag) {
            return new Session(SessionState.byName(tag.getString("State")), tag.getInt("Slot"),
                    tag.getCompound("Real"), tag.getString("RealDimension"), tag.getDouble("X"),
                    tag.getDouble("Y"), tag.getDouble("Z"), tag.getFloat("Yaw"), tag.getFloat("Pitch"),
                    tag.getCompound("Room"));
        }
    }

    private Path sessionFile(UUID player) {
        return root.resolve("sessions").resolve(player + ".dat");
    }

    public Optional<Session> load(UUID player) {
        return AtomicFiles.read(sessionFile(player), data -> Session.fromTag(readTag(data)));
    }

    public void save(UUID player, Session session) throws IOException {
        AtomicFiles.write(sessionFile(player), writeTag(session.toTag()));
    }

    /** The most recently written session that still needs its real profile put back. */
    public Optional<UUID> newestNeedingRecovery() {
        Path dir = root.resolve("sessions");
        if (!java.nio.file.Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (var files = java.nio.file.Files.list(dir)) {
            List<Path> sessions = files.filter(file -> file.getFileName().toString().endsWith(".dat"))
                    .sorted(java.util.Comparator.comparingLong(SessionStore::modified).reversed()).toList();
            for (Path file : sessions) {
                String name = file.getFileName().toString();
                try {
                    UUID id = UUID.fromString(name.substring(0, name.length() - 4));
                    if (load(id).map(session -> session.state().needsRecovery()).orElse(false)) {
                        return Optional.of(id);
                    }
                } catch (IllegalArgumentException ignored) {
                    // not a session file
                }
            }
        } catch (IOException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static long modified(Path file) {
        try {
            return java.nio.file.Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    /** Area slot of {@code owner}; a new one is taken (and saved) the first time. */
    public int slotFor(UUID owner) throws IOException {
        Path file = root.resolve("slots.dat");
        CompoundTag slots = AtomicFiles.read(file, SessionStore::readTag).orElseGet(CompoundTag::new);
        String key = owner.toString();
        if (slots.contains(key)) {
            return slots.getInt(key);
        }
        List<Integer> taken = new ArrayList<>();
        for (String other : slots.getAllKeys()) {
            taken.add(slots.getInt(other));
        }
        int slot = SlotAllocator.lowestFree(taken);
        slots.putInt(key, slot);
        AtomicFiles.write(file, writeTag(slots));
        return slot;
    }

    private static CompoundTag readTag(byte[] data) throws IOException {
        return NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap());
    }

    private static byte[] writeTag(CompoundTag tag) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag, out);
        return out.toByteArray();
    }
}
