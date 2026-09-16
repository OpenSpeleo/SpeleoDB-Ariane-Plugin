package org.speleodb.ariane.plugin.speleodb;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.API;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.UPLOAD;

import jakarta.json.Json;
import jakarta.json.JsonObject;

@DisplayName("Validated TML bytes at the HTTP boundary (Java only)")
class TmlUploadBoundaryTest extends AbstractSpeleoDBServiceWireMockTest {
    @TempDir Path root;
    private TmlUploadPreparer preparer;
    private Path source;
    private JsonObject project;
    private String uploadPath;

    @BeforeEach
    void prepareUpload() throws Exception {
        AtomicLong time = new AtomicLong();
        preparer = spy(new TmlUploadPreparer(10000, 1000, 25, time::get,
                millis -> time.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis)), root));
        service = new SpeleoDBService(controller, preparer);
        authenticateAgainstWireMock();
        source = root.resolve("live.tml");
        project = Json.createObjectBuilder().add("id", "snapshot-test").build();
        uploadPath = API.PROJECTS_ENDPOINT + "snapshot-test" + API.UPLOAD_ARIANE_TML_PATH;
        wm.stubFor(put(urlEqualTo(uploadPath)).willReturn(aResponse().withStatus(200)));
    }

    @ParameterizedTest
    @ValueSource(ints = {ZipEntry.STORED, ZipEntry.DEFLATED})
    @DisplayName("HTTP artifact is identical to the validated snapshot after the live source is truncated")
    void sendsFrozenValidatedBytes(int method) throws Exception {
        byte[] expected = TestFixtures.createZipBytes(method, "Data.xml", "binary.bin");
        Files.write(source, expected);
        doAnswer(call -> {
            Object snapshot = call.callRealMethod();
            Files.write(source, new byte[0]); // The old validate-then-read sequence sent this instead.
            return snapshot;
        }).when(preparer).prepare(any(), anyString());

        service.uploadProject("  survey update  ", project, source);

        var requests = wm.findAll(putRequestedFor(urlEqualTo(uploadPath)));
        assertThat(requests).hasSize(1);
        byte[] artifact = requests.getFirst().getPart("artifact").getBody().asBytes();
        assertThat(artifact).isEqualTo(expected);
        assertThat(SpeleoDBService.calculateSHA256(artifact)).isEqualTo(SpeleoDBService.calculateSHA256(expected));
        assertThat(requests.getFirst().getPart("message").getBody().asString()).isEqualTo("survey update");
        verifyArchiveIndependently(artifact);
        assertThat(Files.size(source)).isZero();
    }

    /** Independent consumer assertions over the binary artifact extracted by WireMock. */
    private void verifyArchiveIndependently(byte[] artifact) throws Exception {
        Path received = Files.write(root.resolve("received.zip"), artifact);
        int count = 0;
        try (ZipFile zip = new ZipFile(received.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                byte[] data;
                try (var input = zip.getInputStream(entry)) {
                    data = input.readAllBytes();
                }
                CRC32 checksum = new CRC32();
                checksum.update(data);
                assertThat(data.length).isEqualTo(entry.getSize());
                assertThat(checksum.getValue()).isEqualTo(entry.getCrc());
                count++;
            }
        }
        // ZipInputStream additionally checks local headers, descriptors, and their CRC values.
        int localCount = 0;
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(artifact))) {
            while (input.getNextEntry() != null) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
                localCount++;
            }
        }
        assertThat(count).isEqualTo(2);
        assertThat(localCount).isEqualTo(count);
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain", "truncated", "crc", "missing", "oversize"})
    @DisplayName("Invalid inputs never reach the network")
    void blocksInvalidUploads(String kind) throws Exception {
        byte[] bytes = TestFixtures.createZipBytes(ZipEntry.STORED, "Data.xml");
        switch (kind) {
            case "plain" -> bytes = new byte[] {1, 2, 3};
            case "truncated" -> bytes = java.util.Arrays.copyOf(bytes, bytes.length / 2);
            case "crc" -> bytes[TestFixtures.zipCentralDirectoryOffset(bytes) + 16] ^= 1;
            case "oversize" -> bytes = new byte[10001];
            case "missing" -> { }
            default -> throw new IllegalArgumentException(kind);
        }
        if (!kind.equals("missing")) {
            Files.write(source, bytes);
        }
        assertThatThrownBy(() -> service.uploadProject("message", project, source)).isInstanceOf(IOException.class);
        wm.verify(0, putRequestedFor(urlEqualTo(uploadPath)));
    }

    @Test
    @DisplayName("Session changes after capture cancel the upload")
    void sessionChange() throws Exception {
        Files.write(source, TestFixtures.createZipBytes(ZipEntry.STORED, "Data.xml"));
        doAnswer(call -> {
            Object snapshot = call.callRealMethod();
            service.logout();
            return snapshot;
        }).when(preparer).prepare(any(), anyString());
        assertThatThrownBy(() -> service.uploadProject("message", project, source))
                .isInstanceOf(IllegalStateException.class).hasMessage(UPLOAD.SESSION_CHANGED);
        wm.verify(0, putRequestedFor(urlEqualTo(uploadPath)));
    }

    @Test
    @DisplayName("Project changes after capture cancel the upload")
    void projectChange() throws Exception {
        AtomicBoolean valid = new AtomicBoolean(true);
        Files.write(source, TestFixtures.createZipBytes(ZipEntry.STORED, "Data.xml"));
        doAnswer(call -> {
            Object snapshot = call.callRealMethod();
            valid.set(false);
            return snapshot;
        }).when(preparer).prepare(any(), anyString());
        assertThatThrownBy(() -> service.uploadProject("message", project, source, valid::get))
                .isInstanceOf(IllegalStateException.class).hasMessage(UPLOAD.SESSION_CHANGED);
        wm.verify(0, putRequestedFor(urlEqualTo(uploadPath)));
    }

    @Test
    @DisplayName("Read failures fail closed instead of falling through to HTTP")
    void accessFailure() throws Exception {
        doAnswer(call -> { throw new java.nio.file.AccessDeniedException("test source"); })
                .when(preparer).prepare(any(), anyString());
        assertThatThrownBy(() -> service.uploadProject("message", project, source))
                .isInstanceOf(java.nio.file.AccessDeniedException.class);
        wm.verify(0, putRequestedFor(urlEqualTo(uploadPath)));
    }

    @Test
    @DisplayName("Multipart byte parts defensively own their input")
    void multipartOwnership() throws Exception {
        byte[] bytes = TestFixtures.createZipBytes(ZipEntry.STORED, "Data.xml");
        var builder = new HTTPRequestMultipartBody.Builder().addPart("artifact", bytes, null, "project.tml");
        byte[] before = builder.build().getBody();
        java.util.Arrays.fill(bytes, (byte) 0);
        assertThat(builder.build().getBody()).isEqualTo(before);
    }
}
