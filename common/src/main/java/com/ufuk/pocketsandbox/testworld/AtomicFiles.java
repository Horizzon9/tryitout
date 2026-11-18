package com.ufuk.pocketsandbox.testworld;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * Crash-safe file writes for the saved real profile: write a temporary file, flush it to disk, keep the
 * previous version as {@code .bak}, then move the new one in place. A reader falls back to {@code .bak} when
 * the main file is missing or broken. Pure Java; unit tested.
 */
public final class AtomicFiles {
    private AtomicFiles() {
    }

    public static void write(Path file, byte[] data) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.write(java.nio.ByteBuffer.wrap(data));
            channel.force(true);
        }
        if (Files.exists(file)) {
            retry(() -> Files.copy(file, backup(file), StandardCopyOption.REPLACE_EXISTING));
        }
        retry(() -> {
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        });
    }

    /** Wait between attempts: OneDrive, antivirus or the search indexer may hold the file for a moment. */
    private static final long[] RETRY_WAIT_MS = {20, 50, 100, 200, 400};

    /** Runs {@code step}, retrying when Windows briefly denies access to the file. */
    static void retry(IoStep step) throws IOException {
        for (int attempt = 0; ; attempt++) {
            try {
                step.run();
                return;
            } catch (java.nio.file.FileSystemException e) {
                if (attempt >= RETRY_WAIT_MS.length) {
                    throw e;
                }
                try {
                    Thread.sleep(RETRY_WAIT_MS[attempt]);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    @FunctionalInterface
    interface IoStep {
        void run() throws IOException;
    }

    /** Reads the file, or its backup when the file is missing or {@code parser} rejects it. */
    public static <T> Optional<T> read(Path file, Parser<T> parser) {
        for (Path candidate : new Path[] {file, backup(file)}) {
            if (!Files.exists(candidate)) {
                continue;
            }
            try {
                return Optional.of(parser.parse(Files.readAllBytes(candidate)));
            } catch (IOException | RuntimeException e) {
                // broken file: try the backup
            }
        }
        return Optional.empty();
    }

    public static Path backup(Path file) {
        return file.resolveSibling(file.getFileName() + ".bak");
    }

    @FunctionalInterface
    public interface Parser<T> {
        T parse(byte[] data) throws IOException;
    }
}
