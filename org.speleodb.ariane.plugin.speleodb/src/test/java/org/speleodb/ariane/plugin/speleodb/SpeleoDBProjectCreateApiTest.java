package org.speleodb.ariane.plugin.speleodb;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
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
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.MESSAGES;

import com.github.tomakehurst.wiremock.client.MappingBuilder;

import jakarta.json.Json;
import jakarta.json.JsonObject;

/**
 * WireMock-driven tests for {@link SpeleoDBService#createProject(String, String, String, String, String)}.
 * Verifies the v2 unwrapped-object 201 contract, the request-body shape (with/without optional
 * coordinates), the auth-required guard, and every failure status against every error envelope.
 */
@DisplayName("SpeleoDBService.createProject(...)")
class SpeleoDBProjectCreateApiTest extends AbstractSpeleoDBServiceWireMockTest {

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
        assertThatThrownBy(() -> service.createProject("n", "d", "FR", null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    // ====================================================================== //
    //                            SUCCESS PATHS                               //
    // ====================================================================== //

    @Test
    @DisplayName("201 returns the response object as-is (v2 unwrapped: NO 'data' nesting)")
    void returnsUnwrappedV2Object() throws Exception {
        JsonObject responseBody = Json.createObjectBuilder()
                .add("id", "new-id-42")
                .add("name", "Cave Sample")
                .add("type", "ARIANE")
                .build();
        stubV2Created(post(urlEqualTo(API.PROJECTS_ENDPOINT)), responseBody);

        JsonObject created = service.createProject("Cave Sample", "Description", "FR", null, null);

        assertThat(created.getString("id")).isEqualTo("new-id-42");
        assertThat(created.getString("name")).isEqualTo("Cave Sample");
        assertThat(created.getString("type")).isEqualTo("ARIANE");
    }

    @Test
    @DisplayName("201 with full lat/lon: request body contains both coordinates and the ARIANE type")
    void requestBodyWithCoordinates() throws Exception {
        stubV2Created(post(urlEqualTo(API.PROJECTS_ENDPOINT)),
                Json.createObjectBuilder().add("id", "abc").add("name", "n").build());

        service.createProject("Cave A", "Survey", "MX", "20.1234", "-99.5678");

        wm.verify(postRequestedFor(urlEqualTo(API.PROJECTS_ENDPOINT))
                .withRequestBody(equalToJson("""
                        {
                          "name": "Cave A",
                          "description": "Survey",
                          "country": "MX",
                          "latitude": "20.1234",
                          "longitude": "-99.5678",
                          "type": "ARIANE"
                        }
                        """)));
    }

    @Test
    @DisplayName("201 with null/blank lat/lon: request body omits the coordinate fields")
    void requestBodyWithoutCoordinates() throws Exception {
        stubV2Created(post(urlEqualTo(API.PROJECTS_ENDPOINT)),
                Json.createObjectBuilder().add("id", "abc").add("name", "n").build());

        service.createProject("Cave B", "Survey", "GB", "  ", null);

        wm.verify(postRequestedFor(urlEqualTo(API.PROJECTS_ENDPOINT))
                .withRequestBody(equalToJson("""
                        {
                          "name": "Cave B",
                          "description": "Survey",
                          "country": "GB",
                          "type": "ARIANE"
                        }
                        """))
                .withRequestBody(notMatching(".*latitude.*"))
                .withRequestBody(notMatching(".*longitude.*")));
    }

    @Test
    @DisplayName("201 with only latitude: longitude is omitted")
    void requestBodyWithOnlyLatitude() throws Exception {
        stubV2Created(post(urlEqualTo(API.PROJECTS_ENDPOINT)),
                Json.createObjectBuilder().add("id", "abc").add("name", "n").build());

        service.createProject("Cave C", "Survey", "US", "40.7128", null);

        wm.verify(postRequestedFor(urlEqualTo(API.PROJECTS_ENDPOINT))
                .withRequestBody(equalToJson("""
                        {
                          "name": "Cave C",
                          "description": "Survey",
                          "country": "US",
                          "latitude": "40.7128",
                          "type": "ARIANE"
                        }
                        """))
                .withRequestBody(notMatching(".*longitude.*")));
    }

    @Test
    @DisplayName("201 with only longitude: latitude is omitted")
    void requestBodyWithOnlyLongitude() throws Exception {
        stubV2Created(post(urlEqualTo(API.PROJECTS_ENDPOINT)),
                Json.createObjectBuilder().add("id", "abc").add("name", "n").build());

        service.createProject("Cave D", "Survey", "FR", null, "2.3522");

        wm.verify(postRequestedFor(urlEqualTo(API.PROJECTS_ENDPOINT))
                .withRequestBody(equalToJson("""
                        {
                          "name": "Cave D",
                          "description": "Survey",
                          "country": "FR",
                          "longitude": "2.3522",
                          "type": "ARIANE"
                        }
                        """))
                .withRequestBody(notMatching(".*latitude.*")));
    }

    @Test
    @DisplayName("Authorization header (Token <test-token>) is sent on every request")
    void sendsAuthHeader() throws Exception {
        stubV2Created(post(urlEqualTo(API.PROJECTS_ENDPOINT)),
                Json.createObjectBuilder().add("id", "x").add("name", "y").build());

        service.createProject("n", "d", "FR", null, null);

        verifyAuthHeader(postTo(API.PROJECTS_ENDPOINT));
    }

    // ====================================================================== //
    //                           FAILURE STATUS GRID                          //
    // ====================================================================== //

    @Test
    @DisplayName("201 with object root under data (pre-v2 wrapper) is rejected")
    void objectRootWrappedInsideData() {
        wm.stubFor(post(urlEqualTo(API.PROJECTS_ENDPOINT))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"data":{"id":"wrapped-id","name":"Wrapped Project","type":"ARIANE"}}
                                """)));

        assertThatThrownBy(() -> service.createProject("n", "d", "FR", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MESSAGES.PROJECT_CREATE_INVALID_RESPONSE);
    }

    @ParameterizedTest(name = "[{0}] 400 surfaces parsed detail (when present)")
    @MethodSource("errorEnvelopes")
    @DisplayName("400 validation errors parameterized over every v2 error envelope shape")
    void validationErrorsAcrossEnvelopes(String label, ErrorBodyStubber stubber, Optional<String> expectedDetail) {
        MappingBuilder request = post(urlEqualTo(API.PROJECTS_ENDPOINT));
        stubber.stub(this, request, 400);

        Throwable thrown = catchThrowing(() -> service.createProject("n", "d", "FR", null, null));

        assertThat(thrown).isInstanceOf(Exception.class);
        assertThat(thrown.getMessage()).contains(MESSAGES.PROJECT_CREATE_FAILED_STATUS).contains("400");
        expectedDetail.ifPresent(detail -> assertThat(thrown.getMessage()).contains(detail));
    }

    @ParameterizedTest(name = "Status {0} carries detail from single-error body")
    @ValueSource(ints = {401, 403, 409, 500, 503})
    @DisplayName("Other failure status codes with single-error envelope")
    void otherFailureStatuses(int status) {
        stubV2ErrorSingle(post(urlEqualTo(API.PROJECTS_ENDPOINT)), status, "stubbed-" + status);

        Throwable thrown = catchThrowing(() -> service.createProject("n", "d", "FR", null, null));

        assertThat(thrown).isInstanceOf(Exception.class);
        assertThat(thrown.getMessage()).contains(String.valueOf(status)).contains("stubbed-" + status);
    }

    @Test
    @DisplayName("Empty error body produces the exact status-only message")
    void emptyBodyExactMessage() {
        stubV2ErrorEmptyBody(post(urlEqualTo(API.PROJECTS_ENDPOINT)), 500);

        assertThatThrownBy(() -> service.createProject("n", "d", "FR", null, null))
                .isInstanceOf(Exception.class)
                .hasMessage(MESSAGES.PROJECT_CREATE_FAILED_STATUS + "500");
    }

    @Test
    @DisplayName("Garbage body produces the status-only message (parser tolerates non-JSON)")
    void garbageBodyFallsThrough() {
        wm.stubFor(post(urlEqualTo(API.PROJECTS_ENDPOINT))
                .willReturn(aResponse().withStatus(503).withBody("<html>oops</html>")));

        assertThatThrownBy(() -> service.createProject("n", "d", "FR", null, null))
                .isInstanceOf(Exception.class)
                .hasMessage(MESSAGES.PROJECT_CREATE_FAILED_STATUS + "503");
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
