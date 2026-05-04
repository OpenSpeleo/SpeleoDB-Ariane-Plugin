package org.speleodb.ariane.plugin.speleodb;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.mockito.Mockito.mock;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.provider.Arguments;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.API;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.HEADERS;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

/**
 * Base for hermetic per-endpoint tests of {@link SpeleoDBService}. Spins up a WireMock
 * server on a random loopback port; the service routes to it via the existing local-host
 * detection in {@link SpeleoDBService#resolveInstanceUrl(String)} which forces HTTP/1.1
 * for {@code localhost} -- a perfect match for WireMock's HTTP listener.
 *
 * <p>Subclasses get:
 * <ul>
 *   <li>A {@code service} field wired to a Mockito-mocked controller.</li>
 *   <li>{@link #authenticateAgainstWireMock()} for suites that need an authenticated state.</li>
 *   <li>v2-shaped stub helpers: success bodies are returned as the JSON root (no wrapper);
 *       error bodies use the v2 envelope ({@code {"error":...}} or {@code {"errors":[...]}}).</li>
 *   <li>The {@link #errorEnvelopes()} parameterized source covering the four canonical
 *       failure-body shapes: single-string, list-of-strings, list-of-detail-objects,
 *       and empty body (status-only fallback).</li>
 * </ul>
 *
 * <p>No production-code seam is required: the service exercises its real {@link
 * java.net.http.HttpClient} over loopback against WireMock.
 */
abstract class AbstractSpeleoDBServiceWireMockTest {

    /** Token used by {@link #authenticateAgainstWireMock()}; asserted in {@code Authorization} header verifications. */
    protected static final String TEST_TOKEN = "test-token-abc123";

    @RegisterExtension
    static final WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    protected SpeleoDBService service;
    protected SpeleoDBController controller;

    @BeforeEach
    void setUpServiceAndResetWireMock() {
        wm.resetAll();
        controller = mock(SpeleoDBController.class);
        service = new SpeleoDBService(controller);
    }

    @AfterEach
    void clearAuthState() {
        if (service != null) {
            service.logout();
        }
    }

    // ====================================================================== //
    //                          AUTH STATE PRIMER                             //
    // ====================================================================== //

    /**
     * Stubs the auth-token endpoint and authenticates the service so subsequent
     * authenticated calls carry {@code Authorization: Token TEST_TOKEN}. Use from
     * suites that need to bypass auth and exercise other endpoints.
     */
    protected void authenticateAgainstWireMock() throws Exception {
        stubV2Object(post(urlEqualTo(API.AUTH_TOKEN_ENDPOINT)),
                Json.createObjectBuilder().add("token", TEST_TOKEN).build());
        service.authenticate("test@example.com", "password", null, instanceUrl());
        wm.resetRequests(); // clear the auth call so test verifications start clean
    }

    /** Returns the {@code host:port} string suitable for {@link SpeleoDBService#authenticate}. */
    protected String instanceUrl() {
        return "localhost:" + wm.getPort();
    }

    // ====================================================================== //
    //                       SUCCESS-BODY STUB HELPERS                        //
    // ====================================================================== //

    /** Stubs a 200 response whose JSON root is the given object (v2 unwrapped shape). */
    protected void stubV2Object(MappingBuilder request, JsonObject body) {
        wm.stubFor(request.willReturn(jsonOk(body.toString())));
    }

    /** Stubs a 200 response whose JSON root is the given array (v2 unwrapped shape). */
    protected void stubV2Array(MappingBuilder request, JsonArray body) {
        wm.stubFor(request.willReturn(jsonOk(body.toString())));
    }

    /** Stubs a 201 response whose JSON root is the given object (v2 unwrapped shape). */
    protected void stubV2Created(MappingBuilder request, JsonObject body) {
        wm.stubFor(request.willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(body.toString())));
    }

    // ====================================================================== //
    //                         ERROR-BODY STUB HELPERS                        //
    // ====================================================================== //

    /** Emits {@code {"error": "<message>"}} as the response body. */
    protected void stubV2ErrorSingle(MappingBuilder request, int status, String message) {
        JsonObject body = Json.createObjectBuilder().add("error", message).build();
        wm.stubFor(request.willReturn(jsonError(status, body.toString())));
    }

    /** Emits {@code {"errors": ["m1", "m2", ...]}} as the response body. */
    protected void stubV2ErrorList(MappingBuilder request, int status, String... messages) {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        for (String m : messages) {
            arr.add(m);
        }
        JsonObject body = Json.createObjectBuilder().add("errors", arr).build();
        wm.stubFor(request.willReturn(jsonError(status, body.toString())));
    }

    /** Emits {@code {"errors": [{"<detailKey>": "<value>"}, ...]}} for DRF/REST-style error objects. */
    protected void stubV2ErrorListOfObjects(MappingBuilder request, int status, String detailKey, String... details) {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        for (String d : details) {
            arr.add(Json.createObjectBuilder().add(detailKey, d));
        }
        JsonObject body = Json.createObjectBuilder().add("errors", arr).build();
        wm.stubFor(request.willReturn(jsonError(status, body.toString())));
    }

    /** Emits an empty body so the parser falls through to the status-only message. */
    protected void stubV2ErrorEmptyBody(MappingBuilder request, int status) {
        wm.stubFor(request.willReturn(aResponse().withStatus(status)));
    }

    // ====================================================================== //
    //                        REQUEST VERIFICATION                            //
    // ====================================================================== //

    /** Asserts that exactly one request to {@code path} carried {@code Authorization: Token TEST_TOKEN}. */
    protected void verifyAuthHeader(RequestPatternBuilder request) {
        wm.verify(request.withHeader(HEADERS.AUTHORIZATION, equalTo(HEADERS.TOKEN_PREFIX + TEST_TOKEN)));
    }

    // ====================================================================== //
    //                       PARAMETERIZED ERROR SOURCE                       //
    // ====================================================================== //

    /**
     * Standard parameterized source: the four canonical v2 failure-body shapes.
     * <p>Each row provides:
     * <ol>
     *   <li>A short label for test display.</li>
     *   <li>An {@link ErrorBodyStubber} that wires a stub for a given request + status.</li>
     *   <li>The detail substring callers can assert appears in the parsed error message,
     *       or {@link Optional#empty()} when no detail is expected (status-only fallback).</li>
     * </ol>
     */
    static Stream<Arguments> errorEnvelopes() {
        return Stream.of(
            Arguments.of("single-error",
                    (ErrorBodyStubber) (test, request, status) ->
                            test.stubV2ErrorSingle(request, status, "single-failure-detail"),
                    Optional.of("single-failure-detail")),
            Arguments.of("list-of-strings",
                    (ErrorBodyStubber) (test, request, status) ->
                            test.stubV2ErrorList(request, status, "first-msg", "second-msg"),
                    Optional.of("first-msg; second-msg")),
            Arguments.of("list-of-detail-objects",
                    (ErrorBodyStubber) (test, request, status) ->
                            test.stubV2ErrorListOfObjects(request, status, "detail", "drf-style-detail"),
                    Optional.of("drf-style-detail")),
            Arguments.of("empty-body",
                    (ErrorBodyStubber) (test, request, status) ->
                            test.stubV2ErrorEmptyBody(request, status),
                    Optional.<String>empty())
        );
    }

    /** Functional interface used by {@link #errorEnvelopes()} so each row can stub itself. */
    @FunctionalInterface
    interface ErrorBodyStubber {
        void stub(AbstractSpeleoDBServiceWireMockTest test, MappingBuilder request, int status);
    }

    // ====================================================================== //
    //                       REQUEST-PATTERN MATCHERS                         //
    // ====================================================================== //

    /** Reusable POST-request-pattern matcher for body verification. */
    protected RequestPatternBuilder postTo(String path) {
        return postRequestedFor(urlEqualTo(path));
    }

    /** Reusable GET-request-pattern matcher for body verification. */
    protected RequestPatternBuilder getTo(String path) {
        return getRequestedFor(urlEqualTo(path));
    }

    // ====================================================================== //
    //                            INTERNAL                                    //
    // ====================================================================== //

    private static ResponseDefinitionBuilder jsonOk(String body) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }

    private static ResponseDefinitionBuilder jsonError(int status, String body) {
        return aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }
}
