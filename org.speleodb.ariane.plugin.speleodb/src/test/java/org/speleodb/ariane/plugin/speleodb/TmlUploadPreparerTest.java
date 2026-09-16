package org.speleodb.ariane.plugin.speleodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.speleodb.ariane.plugin.speleodb.TestFixtures.createZipBytes;
import static org.speleodb.ariane.plugin.speleodb.TestFixtures.zipCentralDirectoryOffset;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.UPLOAD;

@DisplayName("Frozen TML upload preparation")
class TmlUploadPreparerTest {
    @TempDir Path root;
    private final AtomicLong clock = new AtomicLong();

    private TmlUploadPreparer preparer(long limit, Runnable duringPause) {
        return new TmlUploadPreparer(limit, 1000, 25, clock::get, millis -> {
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis));
            duringPause.run();
        }, root);
    }

    private TmlUploadPreparer preparer() {
        return preparer(UPLOAD.MAX_COMPRESSED_BYTES, () -> {});
    }

    private Path source(byte[] bytes) throws IOException {
        return Files.write(root.resolve("live.tml"), bytes);
    }

    private void assertClean() throws IOException {
        try (var paths = Files.list(root)) {
            assertThat(paths.map(p -> p.getFileName().toString()))
                    .noneMatch(name -> name.startsWith(UPLOAD.TEMP_PREFIX));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {ZipEntry.STORED, ZipEntry.DEFLATED})
    @DisplayName("Accepts full archives unchanged and never exposes mutable snapshot bytes")
    void acceptsAndFreezes(int method) throws Exception {
        byte[] expected = createZipBytes(method, "Data.xml", "attachment.bin");
        Path source = source(expected);
        TmlUploadPreparer.Snapshot snapshot = preparer().prepare(source, "test");
        assertThat(snapshot.entryCount()).isEqualTo(2);
        assertThat(snapshot.bytes()).isEqualTo(expected);
        byte[] exposed = snapshot.bytes();
        exposed[0] ^= 1;
        Files.write(source, new byte[0]);
        assertThat(snapshot.bytes()).isEqualTo(expected);
        assertThat(snapshot.sha256()).isEqualTo(SpeleoDBService.calculateSHA256(expected));
        assertClean();
    }

    @Test
    @DisplayName("Accepts the real TML fixture without changing the live file")
    void realFixture() throws Exception {
        byte[] expected;
        try (var fixture = getClass().getResourceAsStream("/artifacts/project.tml")) {
            expected = fixture.readAllBytes();
        }
        Path source = source(expected);
        assertThat(preparer().prepare(source, "test").bytes()).isEqualTo(expected);
        assertThat(Files.readAllBytes(source)).isEqualTo(expected);
        assertClean();
    }

    @Test
    @DisplayName("Waits for ZIP finalization before accepting a snapshot")
    void waitsForFinalization() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] partial;
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("Data.xml"));
            zip.write("survey".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            partial = bytes.toByteArray();
        }
        byte[] complete = bytes.toByteArray();
        Path source = source(partial);
        AtomicBoolean finished = new AtomicBoolean();
        TmlUploadPreparer preparer = preparer(UPLOAD.MAX_COMPRESSED_BYTES, () -> {
            // First quiet period sees a non-finalized ZIP. Finalize during the retry pause.
            if (clock.get() >= TimeUnit.MILLISECONDS.toNanos(75) && finished.compareAndSet(false, true)) {
                try {
                    Files.write(source, complete);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }
        });
        assertThat(preparer.prepare(source, "test").bytes()).isEqualTo(complete);
        assertThat(finished).isTrue();
        assertClean();
    }

    @Test
    @DisplayName("Detects replacement of an initially valid file during the quiet period")
    void validFileReplaced() throws Exception {
        Path source = source(createZipBytes(ZipEntry.DEFLATED, "old.xml"));
        byte[] replacement = createZipBytes(ZipEntry.DEFLATED, "new.xml", "photo.bin");
        AtomicBoolean replaced = new AtomicBoolean();
        TmlUploadPreparer preparer = preparer(UPLOAD.MAX_COMPRESSED_BYTES, () -> {
            if (replaced.compareAndSet(false, true)) {
                try {
                    Files.write(source, replacement);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }
        });
        assertThat(preparer.prepare(source, "test").bytes()).isEqualTo(replacement);
        assertThat(clock.get()).isGreaterThan(TimeUnit.MILLISECONDS.toNanos(25));
        assertClean();
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain", "empty", "truncated", "directories", "crc", "size", "duplicate", "encrypted", "method", "localName"})
    @DisplayName("Rejects malformed archives and cleans every rejected snapshot")
    void rejectsMalformed(String kind) throws Exception {
        byte[] bytes = createZipBytes(ZipEntry.STORED, "Data.xml");
        int central = zipCentralDirectoryOffset(bytes);
        switch (kind) {
            case "plain" -> bytes = "not a zip".getBytes(StandardCharsets.UTF_8);
            case "empty" -> bytes = createZipBytes(ZipEntry.STORED);
            case "truncated" -> bytes = Arrays.copyOf(bytes, bytes.length - 12);
            case "directories" -> bytes = createZipBytes(ZipEntry.STORED, "folder/");
            case "crc" -> bytes[central + 16] ^= 1; // Readable payload, incorrect CENTRAL checksum only.
            case "size" -> bytes[central + 24] ^= 1;
            case "encrypted" -> { bytes[6] |= 1; bytes[central + 8] |= 1; }
            case "method" -> { bytes[8] = 99; bytes[central + 10] = 99; }
            case "localName" -> bytes[30] = 'X';
            case "duplicate" -> {
                bytes = createZipBytes(ZipEntry.STORED, "Data.xml", "Else.xml");
                byte[] oldName = "Else.xml".getBytes(StandardCharsets.UTF_8);
                byte[] newName = "Data.xml".getBytes(StandardCharsets.UTF_8);
                for (int i = 0; i <= bytes.length - oldName.length; i++) {
                    if (Arrays.equals(bytes, i, i + oldName.length, oldName, 0, oldName.length)) {
                        System.arraycopy(newName, 0, bytes, i, newName.length);
                    }
                }
            }
            default -> throw new IllegalArgumentException(kind);
        }
        Path source = source(bytes);
        assertThatThrownBy(() -> preparer().prepare(source, "test")).isInstanceOf(IOException.class);
        assertThat(Files.readAllBytes(source)).isEqualTo(bytes);
        assertClean();
    }

    @Test
    @DisplayName("Rejects oversized files before allocating a request body")
    void oversized() throws Exception {
        Path source = source(createZipBytes(ZipEntry.STORED, "Data.xml"));
        assertThatThrownBy(() -> preparer(10, () -> {}).prepare(source, "test"))
                .isInstanceOf(IOException.class).hasMessage(UPLOAD.TOO_LARGE);
        assertClean();
    }

    @Test
    @DisplayName("A rewrite during copying is discarded and a later complete version is captured")
    void rewriteDuringCopy() throws Exception {
        Path source = source(createZipBytes(ZipEntry.STORED, "Data.xml"));
        byte[] replacement = createZipBytes(ZipEntry.DEFLATED, "Data.xml", "photo.bin");
        AtomicBoolean rewritten = new AtomicBoolean();
        AtomicBoolean restored = new AtomicBoolean();
        TmlUploadPreparer preparer = new TmlUploadPreparer(10000, 1000, 25, () -> {
            if (!rewritten.get() && snapshotExists()) {
                rewritten.set(true);
                try {
                    Files.write(source, new byte[0]);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }
            return clock.get();
        }, millis -> {
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis));
            if (rewritten.get() && restored.compareAndSet(false, true)) {
                try {
                    Files.write(source, replacement);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }
        }, root);
        assertThat(preparer.prepare(source, "test").bytes()).isEqualTo(replacement);
        assertThat(rewritten).isTrue();
        assertClean();
    }

    @Test
    @DisplayName("Growth beyond the compressed limit while copying is rejected")
    void growsDuringCopy() throws Exception {
        Path source = source(createZipBytes(ZipEntry.STORED, "Data.xml"));
        AtomicBoolean grown = new AtomicBoolean();
        TmlUploadPreparer preparer = new TmlUploadPreparer(500, 1000, 0, () -> {
            if (!grown.get() && snapshotExists()) {
                grown.set(true);
                try {
                    Files.write(source, new byte[501], java.nio.file.StandardOpenOption.APPEND);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }
            return clock.get();
        }, millis -> clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis)), root);
        assertThatThrownBy(() -> preparer.prepare(source, "test"))
                .isInstanceOf(IOException.class).hasMessage(UPLOAD.TOO_LARGE);
        assertThat(grown).isTrue();
        assertClean();
    }

    private boolean snapshotExists() {
        try (var children = Files.list(root)) {
            return children.anyMatch(path -> Files.exists(path.resolve(UPLOAD.SNAPSHOT_NAME)));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("Null, missing, and non-regular sources never produce snapshots")
    void missingSources() throws Exception {
        assertThatThrownBy(() -> preparer().prepare(null, "test")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> preparer().prepare(root.resolve("missing.tml"), "test"))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> preparer().prepare(root, "test"))
                .isInstanceOf(IOException.class).hasMessage(UPLOAD.SOURCE_MISSING);
        assertClean();
    }

    @Test
    @DisplayName("Cancellation preserves interruption and removes all temporary files")
    void interruption() throws Exception {
        Path source = source(createZipBytes(ZipEntry.STORED, "Data.xml"));
        TmlUploadPreparer preparer = new TmlUploadPreparer(10000, 1000, 25, clock::get,
                millis -> { throw new InterruptedException(); }, root);
        try {
            assertThatThrownBy(() -> preparer.prepare(source, "test")).isInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        assertClean();
    }

    @Test
    @DisplayName("Validation has a cooperative deadline even while reading entries")
    void deadlineDuringReads() throws Exception {
        Path source = source(createZipBytes(ZipEntry.STORED, "Data.xml", "other.xml", "third.xml"));
        TmlUploadPreparer preparer = new TmlUploadPreparer(10000, 100, 0,
                () -> clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(10)), millis -> {}, root);
        assertThatThrownBy(() -> preparer.prepare(source, "test"))
                .isInstanceOf(IOException.class).hasMessage(UPLOAD.NOT_READY);
        assertClean();
    }
}
