package org.speleodb.ariane.plugin.speleodb;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.API;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.HEADERS;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.MESSAGES;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.http.Fault;

import jakarta.json.Json;

/**
 * WireMock-driven tests for {@link SpeleoDBService#authenticate(String, String, String, String)}.
 * Covers OAuth and email/password success, malformed/missing-token responses, every documented
 * failure status code against every v2 error-envelope shape, and the state-cleared guarantee.
 */
@DisplayName("SpeleoDBService.authenticate(...)")
class SpeleoDBAuthApiTest extends AbstractSpeleoDBServiceWireMockTest {

    private static final String OAUTH_TOKEN = "0123456789abcdef0123456789abcdef01234567";

    // ====================================================================== //
    //                             SUCCESS PATHS                              //
    // ====================================================================== //

    @Test
    @DisplayName("OAuth GET success leaves the service authenticated and routed at the WireMock instance")
    void oauthGetSuccess() throws Exception {
        wm.stubFor(get(urlEqualTo(API.AUTH_TOKEN_ENDPOINT))
                .withHeader(HEADERS.AUTHORIZATION, equalTo(HEADERS.TOKEN_PREFIX + OAUTH_TOKEN))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Json.createObjectBuilder().add("token", "session-token").build().toString())));

        service.authenticate(null, null, OAUTH_TOKEN, instanceUrl());

        assertThat(service.isAuthenticated()).isTrue();
        assertThat(service.getSDBInstance()).isEqualTo("http://" + instanceUrl());

        wm.verify(getRequestedFor(urlEqualTo(API.AUTH_TOKEN_ENDPOINT))
                .withHeader(HEADERS.AUTHORIZATION, equalTo(HEADERS.TOKEN_PREFIX + OAUTH_TOKEN))
                .withHeader(HEADERS.CONTENT_TYPE, equalTo(HEADERS.APPLICATION_JSON)));
    }

    @Test
    @DisplayName("Email/password POST success sends a JSON body and leaves the service authenticated")
    void emailPasswordPostSuccess() throws Exception {
        wm.stubFor(post(urlEqualTo(API.AUTH_TOKEN_ENDPOINT))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token\":\"session-token\"}")));

        service.authenticate("user@example.com", "secret", null, instanceUrl());

        assertThat(service.isAuthenticated()).isTrue();

        // The JDK HttpClient normalizes the charset suffix to uppercase ("UTF-8") on the wire even
        // when the constant is lower-cased; assert case-insensitively.
        wm.verify(postRequestedFor(urlEqualTo(API.AUTH_TOKEN_ENDPOINT))
                .withHeader(HEADERS.CONTENT_TYPE, matching("(?i)^application/json;\\s*charset=utf-8$"))
                .withRequestBody(equalToJson("{\"email\":\"user@example.com\",\"password\":\"secret\"}")));
    }

    // ====================================================================== //
    //                       200-WITH-BAD-PAYLOAD PATHS                       //
    // ====================================================================== //

    @Test
    @DisplayName("200 with malformed JSON -> IllegalArgumentException AND state cleared")
    void okWithMalformedJsonClearsState() {
        wm.stubFor(post(urlEqualTo(API.AUTH_TOKEN_ENDPOINT))
                .willReturn(aResponse().withStatus(200).withBody("{not-json")));

        assertThatThrownBy(() -> service.authenticate("u@e.com", "p", null, instanceUrl()))
                .isInstanceOf(IllegalArgumentException.class);

        assertStateCleared();
    }

    @Test
    @DisplayName("200 with missing 'token' field -> IllegalArgumentException AND state cleared")
    void okWithoutTokenFieldClearsState() {
        wm.stubFor(post(urlEqualTo(API.AUTH_TOKEN_ENDPOINT))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"other\":\"field\"}")));

        assertThatThrownBy(() -> service.authenticate("u@e.com", "p", null, instanceUrl()))
                .isInstanceOf(IllegalArgumentException.class);

        assertStateCleared();
    }

    // ====================================================================== //
    //                           FAILURE STATUS GRID                          //
    // ====================================================================== //

    @ParameterizedTest(name = "[{0}] 401 surfaces parsed detail (when present) AND clears state")
    @MethodSource("errorEnvelopes")
    @DisplayName("401 unauthorized parameterized over every v2 error envelope shape")
    void unauthorizedAcrossEnvelopes(String label, ErrorBodyStubber stubber, Optional<String> expectedDetail) {
        MappingBuilder request = post(urlEqualTo(API.AUTH_TOKEN_ENDPOINT));
        stubber.stub(this, request, 401);

        Throwable thrown = catchThrowing(() -> service.authenticate("u@e.com", "p", null, instanceUrl()));

        assertThat(thrown).isInstanceOf(Exception.class);
        assertThat(thrown.getMessage()).contains(MESSAGES.AUTH_FAILED_STATUS).contains("401");
        expectedDetail.ifPresent(detail -> assertThat(thrown.getMessage()).contains(detail));
        assertStateCleared();
    }

    @ParameterizedTest(name = "Status {0} clears state and throws Exception")
    @ValueSource(ints = {400, 403, 404, 500, 502, 503})
    @DisplayName("Other 4xx/5xx with single-error envelope clears state and throws")
    void otherFailureStatuses(int status) {
        stubV2ErrorSingle(post(urlEqualTo(API.AUTH_TOKEN_ENDPOINT)), status, "stubbed-detail-" + status);

        Throwable thrown = catchThrowing(() -> service.authenticate("u@e.com", "p", null, instanceUrl()));

        assertThat(thrown).isInstanceOf(Exception.class);
        assertThat(thrown.getMessage()).contains(String.valueOf(status)).contains("stubbed-detail-" + status);
        assertStateCleared();
    }

    @Test
    @DisplayName("401 with empty body produces the exact status-only message")
    void unauthorizedEmptyBodyExactMessage() {
        stubV2ErrorEmptyBody(post(urlEqualTo(API.AUTH_TOKEN_ENDPOINT)), 401);

        assertThatThrownBy(() -> service.authenticate("u@e.com", "p", null, instanceUrl()))
                .isInstanceOf(Exception.class)
                .hasMessage(MESSAGES.AUTH_FAILED_STATUS + "401");

        assertStateCleared();
    }

    // ====================================================================== //
    //                          NETWORK-LEVEL FAULTS                          //
    // ====================================================================== //

    @Test
    @DisplayName("Server connection-reset surfaces as Exception and clears state")
    void connectionResetClearsState() {
        wm.stubFor(post(urlEqualTo(API.AUTH_TOKEN_ENDPOINT))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> service.authenticate("u@e.com", "p", null, instanceUrl()))
                .isInstanceOf(Exception.class);

        assertStateCleared();
    }

    @Test
    @DisplayName("OAuth header is sent verbatim under various token shapes")
    void oauthHeaderIsForwardedVerbatim() throws Exception {
        wm.stubFor(get(urlEqualTo(API.AUTH_TOKEN_ENDPOINT))
                .withHeader(HEADERS.AUTHORIZATION, matching("^Token .+$"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token\":\"session-token\"}")));

        service.authenticate(null, null, OAUTH_TOKEN, instanceUrl());

        wm.verify(getRequestedFor(urlEqualTo(API.AUTH_TOKEN_ENDPOINT))
                .withHeader(HEADERS.AUTHORIZATION, equalTo(HEADERS.TOKEN_PREFIX + OAUTH_TOKEN)));
    }

    // ====================================================================== //
    //                              HELPERS                                   //
    // ====================================================================== //

    private void assertStateCleared() {
        assertThat(service.isAuthenticated()).isFalse();
        assertThatThrownBy(() -> service.getSDBInstance()).isInstanceOf(IllegalStateException.class);
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
