package org.speleodb.ariane.plugin.speleodb;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.API;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.MESSAGES;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.PATHS;

import com.github.tomakehurst.wiremock.client.MappingBuilder;

import jakarta.json.Json;
import jakarta.json.JsonObject;

/**
 * WireMock-driven tests for {@link SpeleoDBService#downloadProject(JsonObject)}.
 * Covers the auth guard, the 200 binary write, the 422-empty-template path, and every
 * non-200/422 failure status against every error envelope (with the byte-body UTF-8 decode).
 */
@DisplayName("SpeleoDBService.downloadProject(...)")
class SpeleoDBProjectDownloadApiTest extends AbstractSpeleoDBServiceWireMockTest {

    private String projectId;
    private JsonObject project;
    private String downloadPath;
    private Path expectedTmlPath;

    @BeforeEach
    void authenticateAndPrepare() throws Exception {
        authenticateAgainstWireMock();
        projectId = "download-test-" + UUID.randomUUID();
        project = Json.createObjectBuilder().add("id", projectId).add("name", "Download Cave").build();
        downloadPath = API.PROJECTS_ENDPOINT + projectId + API.DOWNLOAD_ARIANE_TML_PATH;
        expectedTmlPath = Paths.get(PATHS.SDB_PROJECT_DIR, projectId + PATHS.TML_FILE_EXTENSION);
        Files.createDirectories(expectedTmlPath.getParent());
        Files.deleteIfExists(expectedTmlPath);
    }

    @AfterEach
    void deleteTmlFile() throws IOException {
        Files.deleteIfExists(expectedTmlPath);
    }

    // ====================================================================== //
    //                              GUARD CHECKS                              //
    // ====================================================================== //

    @Test
    @DisplayName("Throws IllegalStateException when not authenticated")
    void requiresAuthentication() {
        service.logout();
        assertThatThrownBy(() -> service.downloadProject(project))
                .isInstanceOf(IllegalStateException.class);
    }

    // ====================================================================== //
    //                            SUCCESS PATHS                               //
    // ====================================================================== //

    @Test
    @DisplayName("200 with binary body writes the file under PATHS.SDB_PROJECT_DIR and returns its path")
    void successWritesFileAndReturnsPath() throws Exception {
        byte[] body = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00, 0x08, 0x00}; // ZIP magic prefix
        wm.stubFor(get(urlEqualTo(downloadPath))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/octet-stream")
                        .withBody(body)));

        Path written = service.downloadProject(project);

        assertThat(written).isEqualTo(expectedTmlPath);
        assertThat(Files.exists(written)).isTrue();
        assertThat(Files.readAllBytes(written)).containsExactly(body);
        verifyAuthHeader(getTo(downloadPath));
    }

    @Test
    @DisplayName("422 -> empty template extracted from resources, NO exception")
    void unprocessableEntity422EmitsEmptyTemplate() throws Exception {
        wm.stubFor(get(urlEqualTo(downloadPath)).willReturn(aResponse().withStatus(422)));

        Path written = service.downloadProject(project);

        assertThat(written).isEqualTo(expectedTmlPath);
        assertThat(Files.exists(written)).isTrue();
        assertThat(Files.size(written)).isPositive();
    }

    // ====================================================================== //
    //                           FAILURE STATUS GRID                          //
    // ====================================================================== //

    @ParameterizedTest(name = "[{0}] 500 surfaces parsed detail (when present) on byte body")
    @MethodSource("errorEnvelopes")
    @DisplayName("5xx parameterized over every v2 error envelope shape (UTF-8 decode path)")
    void serverErrorsAcrossEnvelopes(String label, ErrorBodyStubber stubber, Optional<String> expectedDetail) {
        MappingBuilder request = get(urlEqualTo(downloadPath));
        stubber.stub(this, request, 500);

        Throwable thrown = catchThrowing(() -> service.downloadProject(project));

        assertThat(thrown).isInstanceOf(RuntimeException.class);
        assertThat(thrown.getMessage()).contains(MESSAGES.PROJECT_DOWNLOAD_FAILED_STATUS).contains("500");
        expectedDetail.ifPresent(detail -> assertThat(thrown.getMessage()).contains(detail));
    }

    @ParameterizedTest(name = "Status {0} carries detail from single-error body")
    @ValueSource(ints = {400, 401, 403, 404, 502, 503})
    @DisplayName("Other failure status codes with single-error envelope")
    void otherFailureStatuses(int status) {
        stubV2ErrorSingle(get(urlEqualTo(downloadPath)), status, "stubbed-" + status);

        Throwable thrown = catchThrowing(() -> service.downloadProject(project));

        assertThat(thrown).isInstanceOf(RuntimeException.class);
        assertThat(thrown.getMessage()).contains(String.valueOf(status)).contains("stubbed-" + status);
    }

    @Test
    @DisplayName("Empty error body still includes the unexpected-status hint")
    void emptyBodyContainsHint() {
        stubV2ErrorEmptyBody(get(urlEqualTo(downloadPath)), 500);

        assertThatThrownBy(() -> service.downloadProject(project))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(MESSAGES.PROJECT_DOWNLOAD_FAILED_STATUS + "500")
                .hasMessageContaining(MESSAGES.PROJECT_DOWNLOAD_UNEXPECTED_STATUS);
    }

    // ====================================================================== //
    //                              HELPERS                                   //
    // ====================================================================== //

    private static Throwable catchThrowing(ThrowingRunnable r) {
        try {
            r.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
