package org.speleodb.ariane.plugin.speleodb;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
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
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.HEADERS;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.MESSAGES;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.PATHS;

import com.github.tomakehurst.wiremock.client.MappingBuilder;

import jakarta.json.Json;
import jakarta.json.JsonObject;

/**
 * WireMock-driven tests for {@link SpeleoDBService#uploadProject(String, JsonObject)}.
 * Covers the auth/message/empty-template guards, the 200/304 success contracts, and every
 * failure status against every error envelope (with the byte-body UTF-8 decode path).
 */
@DisplayName("SpeleoDBService.uploadProject(...)")
class SpeleoDBProjectUploadApiTest extends AbstractSpeleoDBServiceWireMockTest {

    private String projectId;
    private JsonObject project;
    private String uploadPath;
    private Path tmlFile;

    @BeforeEach
    void authenticateAndStageFile() throws Exception {
        authenticateAgainstWireMock();
        projectId = "upload-test-" + UUID.randomUUID();
        project = Json.createObjectBuilder().add("id", projectId).add("name", "Upload Cave").build();
        uploadPath = API.PROJECTS_ENDPOINT + projectId + API.UPLOAD_ARIANE_TML_PATH;

        tmlFile = Paths.get(PATHS.SDB_PROJECT_DIR, projectId + PATHS.TML_FILE_EXTENSION);
        Files.createDirectories(tmlFile.getParent());
        Files.write(tmlFile, "non-empty test payload not matching empty_project.tml SHA-256".getBytes());
    }

    @AfterEach
    void deleteTmlFile() throws IOException {
        Files.deleteIfExists(tmlFile);
    }

    // ====================================================================== //
    //                          PRE-NETWORK GUARDS                            //
    // ====================================================================== //

    @Test
    @DisplayName("Throws IllegalStateException when not authenticated")
    void requiresAuthentication() {
        service.logout();
        assertThatThrownBy(() -> service.uploadProject("msg", project))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest(name = "Message [{0}] -> IllegalArgumentException, no HTTP")
    @ValueSource(strings = {"", "   ", "\n\t  \n"})
    @DisplayName("Null/empty/whitespace upload message rejected before any HTTP call")
    void rejectsBlankMessages(String blankMessage) {
        assertThatThrownBy(() -> service.uploadProject(blankMessage, project))
                .isInstanceOf(IllegalArgumentException.class);
        wm.verify(0, putRequestedFor(urlEqualTo(uploadPath)));
    }

    @Test
    @DisplayName("Null upload message rejected before any HTTP call")
    void rejectsNullMessage() {
        assertThatThrownBy(() -> service.uploadProject(null, project))
                .isInstanceOf(IllegalArgumentException.class);
        wm.verify(0, putRequestedFor(urlEqualTo(uploadPath)));
    }

    @Test
    @DisplayName("On-disk TML matching empty_project.tml SHA-256 is rejected with PROJECT_UPLOAD_REJECTED_EMPTY")
    void rejectsEmptyTemplate() throws IOException {
        // Replace the staged file with the empty template byte-for-byte.
        try (InputStream tpl = getClass().getResourceAsStream(PATHS.EMPTY_TML)) {
            assertThat(tpl).as("empty template resource present").isNotNull();
            Files.write(tmlFile, tpl.readAllBytes());
        }

        assertThatThrownBy(() -> service.uploadProject("real change", project))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MESSAGES.PROJECT_UPLOAD_REJECTED_EMPTY);

        wm.verify(0, putRequestedFor(urlEqualTo(uploadPath)));
    }

    // ====================================================================== //
    //                            SUCCESS PATHS                               //
    // ====================================================================== //

    @Test
    @DisplayName("200 returns normally; PUT carries multipart body with message + artifact parts")
    void uploadSucceeds200() throws Exception {
        wm.stubFor(put(urlEqualTo(uploadPath)).willReturn(aResponse().withStatus(200)));

        service.uploadProject("Initial commit", project);

        wm.verify(putRequestedFor(urlEqualTo(uploadPath))
                .withHeader(HEADERS.CONTENT_TYPE, matching("(?i)^multipart/form-data; boundary=.+$"))
                .withRequestBody(containing("name=\"message\""))
                .withRequestBody(containing("Initial commit"))
                .withRequestBody(containing("name=\"artifact\""))
                .withRequestBody(containing(projectId + PATHS.TML_FILE_EXTENSION)));
        verifyAuthHeader(putRequestedFor(urlEqualTo(uploadPath)));
    }

    @Test
    @DisplayName("304 -> NotModifiedException with PROJECT_UPLOAD_NOT_MODIFIED")
    void notModified304() {
        wm.stubFor(put(urlEqualTo(uploadPath)).willReturn(aResponse().withStatus(304)));

        assertThatThrownBy(() -> service.uploadProject("no-op", project))
                .isInstanceOf(NotModifiedException.class)
                .hasMessage(MESSAGES.PROJECT_UPLOAD_NOT_MODIFIED);
    }

    // ====================================================================== //
    //                           FAILURE STATUS GRID                          //
    // ====================================================================== //

    @ParameterizedTest(name = "[{0}] 500 surfaces parsed detail (when present) on byte body")
    @MethodSource("errorEnvelopes")
    @DisplayName("5xx parameterized over every v2 error envelope shape (UTF-8 decode path)")
    void serverErrorsAcrossEnvelopes(String label, ErrorBodyStubber stubber, Optional<String> expectedDetail) {
        MappingBuilder request = put(urlEqualTo(uploadPath));
        stubber.stub(this, request, 500);

        Throwable thrown = catchThrowing(() -> service.uploadProject("msg", project));

        assertThat(thrown).isInstanceOf(Exception.class);
        assertThat(thrown).isNotInstanceOf(NotModifiedException.class);
        assertThat(thrown.getMessage()).contains(MESSAGES.PROJECT_UPLOAD_FAILED_STATUS).contains("500");
        expectedDetail.ifPresent(detail -> assertThat(thrown.getMessage()).contains(detail));
    }

    @ParameterizedTest(name = "Status {0} carries detail from single-error body")
    @ValueSource(ints = {400, 401, 403, 413, 502, 503})
    @DisplayName("Other failure status codes with single-error envelope")
    void otherFailureStatuses(int status) {
        stubV2ErrorSingle(put(urlEqualTo(uploadPath)), status, "stubbed-" + status);

        Throwable thrown = catchThrowing(() -> service.uploadProject("msg", project));

        assertThat(thrown).isInstanceOf(Exception.class);
        assertThat(thrown.getMessage()).contains(String.valueOf(status)).contains("stubbed-" + status);
    }

    @Test
    @DisplayName("Empty error body produces the exact status-only message")
    void emptyBodyExactMessage() {
        stubV2ErrorEmptyBody(put(urlEqualTo(uploadPath)), 502);

        assertThatThrownBy(() -> service.uploadProject("msg", project))
                .isInstanceOf(Exception.class)
                .hasMessage(MESSAGES.PROJECT_UPLOAD_FAILED_STATUS + "502");
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
