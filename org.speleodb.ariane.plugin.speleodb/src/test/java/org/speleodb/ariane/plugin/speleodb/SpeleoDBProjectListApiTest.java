package org.speleodb.ariane.plugin.speleodb;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.API;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.HEADERS;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.MESSAGES;

import com.github.tomakehurst.wiremock.client.MappingBuilder;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

/**
 * WireMock-driven tests for {@link SpeleoDBService#listProjects()}.
 * Verifies the v2 unwrapped-array contract, the ARIANE/non-WEB_VIEWER filter,
 * the auth-required guard, and every failure status against every error envelope.
 */
@DisplayName("SpeleoDBService.listProjects()")
class SpeleoDBProjectListApiTest extends AbstractSpeleoDBServiceWireMockTest {

    @BeforeEach
    void authenticate() throws Exception {
        authenticateAgainstWireMock();
    }

    // ====================================================================== //
    //                              GUARD CHECKS                              //
    // ====================================================================== //

    @Test
    @DisplayName("Throws IllegalStateException when not authenticated")
    void requiresAuthentication() {
        service.logout();
        assertThatThrownBy(() -> service.listProjects())
                .isInstanceOf(IllegalStateException.class);
    }

    // ====================================================================== //
    //                            SUCCESS PATHS                               //
    // ====================================================================== //

    @Test
    @DisplayName("200 with empty v2 array returns empty result")
    void emptyArray() throws Exception {
        stubV2Array(get(urlEqualTo(API.PROJECTS_ENDPOINT)), Json.createArrayBuilder().build());

        JsonArray result = service.listProjects();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("200 with mixed types: only ARIANE non-WEB_VIEWER survives the client-side filter")
    void mixedTypesFiltering() throws Exception {
        JsonArray body = Json.createArrayBuilder()
                .add(arianeProject("kept-1", "READ_AND_WRITE"))
                .add(nonArianeProject("dropped-compass", "COMPASS"))
                .add(arianeProject("dropped-webviewer", "WEB_VIEWER"))
                .add(arianeProject("kept-2", "ADMIN"))
                .add(nonArianeProject("dropped-survex", "SURVEX"))
                .build();
        stubV2Array(get(urlEqualTo(API.PROJECTS_ENDPOINT)), body);

        JsonArray result = service.listProjects();

        assertThat(result).hasSize(2);
        assertThat(result.getJsonObject(0).getString("id")).isEqualTo("kept-1");
        assertThat(result.getJsonObject(1).getString("id")).isEqualTo("kept-2");
    }

    @Test
    @DisplayName("Authorization header (Token <test-token>) is sent on every request")
    void sendsAuthHeader() throws Exception {
        stubV2Array(get(urlEqualTo(API.PROJECTS_ENDPOINT)), Json.createArrayBuilder().build());

        service.listProjects();

        verifyAuthHeader(getTo(API.PROJECTS_ENDPOINT));
    }

    // ====================================================================== //
    //                             BAD PAYLOAD                                //
    // ====================================================================== //

    @Test
    @DisplayName("200 with malformed JSON propagates a parse exception")
    void malformedJson() {
        wm.stubFor(get(urlEqualTo(API.PROJECTS_ENDPOINT))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HEADERS.CONTENT_TYPE, "application/json")
                        .withBody("{not-json")));

        assertThatThrownBy(() -> service.listProjects()).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("200 with object root (not array) propagates a parse exception")
    void objectRootInsteadOfArray() {
        wm.stubFor(get(urlEqualTo(API.PROJECTS_ENDPOINT))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HEADERS.CONTENT_TYPE, "application/json")
                        .withBody("{\"data\": []}")));  // pre-v2 wrapped shape - must now fail

        assertThatThrownBy(() -> service.listProjects()).isInstanceOf(Exception.class);
    }

    // ====================================================================== //
    //                           FAILURE STATUS GRID                          //
    // ====================================================================== //

    @ParameterizedTest(name = "[{0}] Status surfaces parsed detail (when present) in the exception message")
    @MethodSource("errorEnvelopes")
    @DisplayName("4xx/5xx parameterized over every v2 error envelope shape")
    void failureStatusesAcrossEnvelopes(String label, ErrorBodyStubber stubber, Optional<String> expectedDetail) {
        MappingBuilder request = get(urlEqualTo(API.PROJECTS_ENDPOINT));
        stubber.stub(this, request, 503);

        Throwable thrown = catchThrowing(() -> service.listProjects());

        assertThat(thrown).isInstanceOf(Exception.class);
        assertThat(thrown.getMessage()).contains(MESSAGES.PROJECT_LIST_FAILED_STATUS).contains("503");
        expectedDetail.ifPresent(detail -> assertThat(thrown.getMessage()).contains(detail));
    }

    @ParameterizedTest(name = "Status {0} carries detail from single-error body")
    @ValueSource(ints = {400, 401, 403, 404, 500, 502})
    @DisplayName("Common failure status codes with single-error envelope")
    void otherFailureStatuses(int status) {
        stubV2ErrorSingle(get(urlEqualTo(API.PROJECTS_ENDPOINT)), status, "stubbed-detail-" + status);

        Throwable thrown = catchThrowing(() -> service.listProjects());

        assertThat(thrown).isInstanceOf(Exception.class);
        assertThat(thrown.getMessage()).contains(String.valueOf(status)).contains("stubbed-detail-" + status);
    }

    @Test
    @DisplayName("Empty body produces the exact status-only message")
    void emptyBodyExactMessage() {
        stubV2ErrorEmptyBody(get(urlEqualTo(API.PROJECTS_ENDPOINT)), 500);

        assertThatThrownBy(() -> service.listProjects())
                .isInstanceOf(Exception.class)
                .hasMessage(MESSAGES.PROJECT_LIST_FAILED_STATUS + "500");
    }

    // ====================================================================== //
    //                              HELPERS                                   //
    // ====================================================================== //

    private static JsonObject arianeProject(String id, String permission) {
        return Json.createObjectBuilder()
                .add("id", id)
                .add("name", "Project " + id)
                .add("type", "ARIANE")
                .add("permission", permission)
                .build();
    }

    private static JsonObject nonArianeProject(String id, String type) {
        return Json.createObjectBuilder()
                .add("id", id)
                .add("name", "Project " + id)
                .add("type", type)
                .add("permission", "READ_AND_WRITE")
                .build();
    }

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
