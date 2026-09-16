package org.speleodb.ariane.plugin.speleodb;

import java.io.IOException;
import java.io.EOFException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.TIMINGS;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.UPLOAD;

/**
 * Freezes the bytes before validating them. The host never receives the private snapshot path.
 * A quiet source is only a timing heuristic: the host API does not acknowledge save completion.
 */
class TmlUploadPreparer {
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final long maxBytes;
    private final long timeoutMillis;
    private final long quietMillis;
    private final LongSupplier clock;
    private final Sleeper sleeper;
    private final Path temporaryParent;

    TmlUploadPreparer() {
        this(UPLOAD.MAX_COMPRESSED_BYTES, TIMINGS.FILE_STABILITY_TIMEOUT_MILLIS,
                UPLOAD.QUIET_PERIOD_MILLIS, System::nanoTime, Thread::sleep, null);
    }

    TmlUploadPreparer(long maxBytes, long timeoutMillis, long quietMillis,
            LongSupplier clock, Sleeper sleeper, Path temporaryParent) {
        this.maxBytes = maxBytes;
        this.timeoutMillis = timeoutMillis;
        this.quietMillis = quietMillis;
        this.clock = clock;
        this.sleeper = sleeper;
        this.temporaryParent = temporaryParent;
    }

    Snapshot prepare(Path source, String attemptId) throws IOException, InterruptedException {
        if (source == null) {
            throw new IOException(UPLOAD.SOURCE_MISSING);
        }
        long started = clock.getAsLong();
        long deadline = started + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        Path directory = temporaryParent == null
                ? Files.createTempDirectory(UPLOAD.TEMP_PREFIX)
                : Files.createTempDirectory(temporaryParent, UPLOAD.TEMP_PREFIX);
        Path snapshot = directory.resolve(UPLOAD.SNAPSHOT_NAME);
        long backoff = TIMINGS.FILE_STABILITY_INITIAL_BACKOFF_MILLIS;
        IOException lastFailure = null;
        int attempt = 0;
        try {
            while (true) {
                checkDeadline(deadline, lastFailure);
                attempt++;
                try {
                    BasicFileAttributes before = attributes(source);
                    pause(quietMillis, deadline);
                    if (!unchanged(before, attributes(source))) {
                        throw new SourceChangedException();
                    }
                    copyBounded(source, snapshot, deadline);
                    if (!unchanged(before, attributes(source))) {
                        throw new SourceChangedException();
                    }
                    int entries = validate(snapshot, deadline);
                    if (!unchanged(before, attributes(source))) {
                        throw new SourceChangedException();
                    }
                    byte[] bytes = Files.readAllBytes(snapshot);
                    checkDeadline(deadline, null);
                    Snapshot result = new Snapshot(bytes, entries);
                    SpeleoDBLogger.getInstance().debug(UPLOAD.SNAPSHOT_LOG.formatted(attemptId,
                            bytes.length, result.sha256(), entries, attempt,
                            TimeUnit.NANOSECONDS.toMillis(clock.getAsLong() - started)));
                    return result;
                } catch (ZipException | EOFException | NoSuchFileException | SourceChangedException e) {
                    lastFailure = e;
                    SpeleoDBLogger.getInstance().debug(UPLOAD.PREPARATION_LOG.formatted(
                            attemptId, attempt, e.getClass().getSimpleName()));
                } finally {
                    Files.deleteIfExists(snapshot);
                }
                pause(backoff, deadline);
                backoff = Math.min(backoff * 2, TIMINGS.FILE_STABILITY_MAX_BACKOFF_MILLIS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            Files.deleteIfExists(directory);
        }
    }

    private BasicFileAttributes attributes(Path source) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(source, BasicFileAttributes.class);
        if (!attributes.isRegularFile()) {
            throw new IOException(UPLOAD.SOURCE_MISSING);
        }
        if (attributes.size() > maxBytes) {
            throw new IOException(UPLOAD.TOO_LARGE);
        }
        return attributes;
    }

    private boolean unchanged(BasicFileAttributes before, BasicFileAttributes after) {
        return before.size() == after.size()
                && before.lastModifiedTime().equals(after.lastModifiedTime())
                && Objects.equals(before.fileKey(), after.fileKey());
    }

    private void copyBounded(Path source, Path snapshot, long deadline)
            throws IOException, InterruptedException {
        try (InputStream input = Files.newInputStream(source);
                var output = Files.newOutputStream(snapshot)) {
            byte[] buffer = new byte[UPLOAD.BUFFER_BYTES];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                checkDeadline(deadline, null);
                total += count;
                if (total > maxBytes) {
                    throw new IOException(UPLOAD.TOO_LARGE);
                }
                output.write(buffer, 0, count);
            }
        }
    }

    private int validate(Path snapshot, long deadline) throws IOException, InterruptedException {
        var entries = new HashMap<String, ZipEntry>();
        boolean hasFile = false;
        try (ZipFile zip = new ZipFile(snapshot.toFile())) {
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                checkDeadline(deadline, null);
                ZipEntry entry = enumeration.nextElement();
                if (entries.putIfAbsent(entry.getName(), entry) != null) {
                    throw new ZipException(UPLOAD.DUPLICATE_ENTRY);
                }
                hasFile |= !entry.isDirectory();
                try (InputStream input = zip.getInputStream(entry)) {
                    verifyEntry(input, entry, deadline);
                }
            }
        } catch (IllegalArgumentException e) {
            throw new ZipException(UPLOAD.INVALID_ZIP);
        }
        if (!hasFile) {
            throw new ZipException(UPLOAD.INVALID_ZIP);
        }
        // Also check local headers/data descriptors, which ZipFile's directory view alone
        // does not fully validate. Neither view is sufficient on its own.
        var seen = new HashSet<String>();
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(snapshot))) {
            ZipEntry local;
            while ((local = input.getNextEntry()) != null) {
                ZipEntry central = entries.get(local.getName());
                if (central == null || !seen.add(local.getName()) || local.getMethod() != central.getMethod()) {
                    throw new ZipException(UPLOAD.INVALID_ENTRY);
                }
                verifyEntry(input, central, deadline);
                if (local.getCrc() != central.getCrc() || local.getSize() != central.getSize()
                        || local.getCompressedSize() != central.getCompressedSize()) {
                    throw new ZipException(UPLOAD.INVALID_ENTRY);
                }
            }
        } catch (IllegalArgumentException e) {
            throw new ZipException(UPLOAD.INVALID_ZIP);
        }
        if (seen.size() != entries.size()) {
            throw new ZipException(UPLOAD.INVALID_ZIP);
        }
        return entries.size();
    }

    private void verifyEntry(InputStream input, ZipEntry entry, long deadline)
            throws IOException, InterruptedException {
        CRC32 crc = new CRC32();
        byte[] buffer = new byte[UPLOAD.BUFFER_BYTES];
        long size = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            checkDeadline(deadline, null);
            size += count;
            if (size > entry.getSize()) {
                throw new ZipException(UPLOAD.INVALID_ENTRY);
            }
            crc.update(buffer, 0, count);
        }
        if (size != entry.getSize() || crc.getValue() != entry.getCrc()) {
            throw new ZipException(UPLOAD.INVALID_ENTRY);
        }
    }

    private void pause(long millis, long deadline) throws IOException, InterruptedException {
        checkDeadline(deadline, null);
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - clock.getAsLong());
        sleeper.sleep(Math.min(millis, Math.max(1, remaining)));
        checkDeadline(deadline, null);
    }

    private void checkDeadline(long deadline, IOException cause) throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
        if (clock.getAsLong() - deadline >= 0) {
            throw new IOException(UPLOAD.NOT_READY, cause);
        }
    }

    private static final class SourceChangedException extends IOException {
        SourceChangedException() {
            super(UPLOAD.SOURCE_CHANGED);
        }
    }

    /** Bytes have no externally writable reference; only the preparer can construct this type. */
    static final class Snapshot {
        private final byte[] bytes;
        private final String sha256;
        private final int entryCount;

        private Snapshot(byte[] bytes, int entryCount) {
            this.bytes = bytes;
            this.sha256 = SpeleoDBService.calculateSHA256(bytes);
            this.entryCount = entryCount;
        }

        byte[] bytes() { return bytes.clone(); }
        String sha256() { return sha256; }
        int entryCount() { return entryCount; }

        void addPart(HTTPRequestMultipartBody.Builder builder, String field, String filename) {
            builder.addPart(field, bytes, null, filename);
        }
    }
}
