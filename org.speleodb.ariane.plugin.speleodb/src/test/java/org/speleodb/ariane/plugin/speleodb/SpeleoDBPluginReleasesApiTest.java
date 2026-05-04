package org.speleodb.ariane.plugin.speleodb;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
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

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

/**
 * WireMock-driven tests for {@link SpeleoDBService#fetchPluginReleases(String)}.
 * Verifies the v2 unwrapped-array contract, the software + version-bounds filter,
 * and every failure status against every error envelope. This endpoint is unauthenticated.
 *
 * <p>Note: the version-bounds filter compares against {@link SpeleoDBConstants#ARIANE_VERSION}
 * which is loaded from the host plugin API at class-load time. We bracket bounds around it
 * so the filter outcome is deterministic regardless of the actual value.
 */
@DisplayName("SpeleoDBService.fetchPluginReleases(...)")
class SpeleoDBPluginReleasesApiTest extends AbstractSpeleoDBServiceWireMockTest {

    private static final String CURRENT = SpeleoDBConstants.ARIANE_VERSION;

    // ====================================================================== //
    //                            SUCCESS PATHS                               //
    // ====================================================================== //

    @Test
    @DisplayName("200 with v2 unwrapped array; software + version-bounds filter applied")
    void successAndFiltering() throws Exception {
        // Skip the test if ARIANE_VERSION is not parseable (test environment edge case).
        org.junit.jupiter.api.Assumptions.assumeTrue(
                CURRENT != null && CURRENT.matches("\\d+\\.\\d+\\.\\d+"),
                "ARIANE_VERSION must be a parseable semver for this test");

        JsonArray body = Json.createArrayBuilder()
                .add(release("kept-no-bounds",          "ARIANE", null, null,                      "1.0.0"))
                .add(release("kept-in-range",           "ARIANE", "0.0.1", bumpMajor(CURRENT, 1), "1.0.1"))
                .add(release("dropped-other-software",  "OTHER",  "0.0.1", bumpMajor(CURRENT, 1), "1.0.2"))
                .add(release("dropped-below-min",       "ARIANE", bumpMajor(CURRENT, 1), null,    "1.0.3"))
                .add(release("dropped-above-max",       "ARIANE", null,    bumpMajor(CURRENT, -10), "1.0.4"))
                .build();
        stubV2Array(get(urlEqualTo(API.PLUGIN_RELEASES_ENDPOINT)), body);

        JsonArray result = service.fetchPluginReleases(instanceUrl());

        assertThat(result).extracting(j -> ((JsonObject) j).getString("plugin_version"))
                .contains("1.0.0", "1.0.1")
                .doesNotContain("1.0.2", "1.0.3", "1.0.4");
    }

    @Test
    @DisplayName("200 with empty array returns empty result, no exception")
    void emptyArray() throws Exception {
        stubV2Array(get(urlEqualTo(API.PLUGIN_RELEASES_ENDPOINT)), Json.createArrayBuilder().build());

        JsonArray result = service.fetchPluginReleases(instanceUrl());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Endpoint is public: NO Authorization header is sent")
    void doesNotSendAuthHeader() throws Exception {
        stubV2Array(get(urlEqualTo(API.PLUGIN_RELEASES_ENDPOINT)), Json.createArrayBuilder().build());

        service.fetchPluginReleases(instanceUrl());

        wm.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                        urlEqualTo(API.PLUGIN_RELEASES_ENDPOINT))
                .withoutHeader(HEADERS.AUTHORIZATION));
    }

    // ====================================================================== //
    //                             BAD PAYLOAD                                //
    // ====================================================================== //

    @Test
    @DisplayName("200 with malformed JSON propagates a parse exception")
    void malformedJson() {
        wm.stubFor(get(urlEqualTo(API.PLUGIN_RELEASES_ENDPOINT))
                .willReturn(aResponse().withStatus(200)
                        .withHeader(HEADERS.CONTENT_TYPE, "application/json")
                        .withBody("[")));

        assertThatThrownBy(() -> service.fetchPluginReleases(instanceUrl()))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("200 with object root (pre-v2 wrapper) propagates a parse exception")
    void objectRootInsteadOfArray() {
        wm.stubFor(get(urlEqualTo(API.PLUGIN_RELEASES_ENDPOINT))
                .willReturn(aResponse().withStatus(200)
                        .withHeader(HEADERS.CONTENT_TYPE, "application/json")
                        .withBody("{\"data\": []}")));

        assertThatThrownBy(() -> service.fetchPluginReleases(instanceUrl()))
                .isInstanceOf(Exception.class);
    }

    // ====================================================================== //
    //                           FAILURE STATUS GRID                          //
    // ====================================================================== //

    @ParameterizedTest(name = "[{0}] 503 surfaces parsed detail (when present)")
    @MethodSource("errorEnvelopes")
    @DisplayName("5xx parameterized over every v2 error envelope shape")
    void failureStatusesAcrossEnvelopes(String label, ErrorBodyStubber stubber, Optional<String> expectedDetail) {
        MappingBuilder request = get(urlEqualTo(API.PLUGIN_RELEASES_ENDPOINT));
        stubber.stub(this, request, 503);

        Throwable thrown = catchThrowing(() -> service.fetchPluginReleases(instanceUrl()));

        assertThat(thrown).isInstanceOf(Exception.class);
        assertThat(thrown.getMessage()).contains(MESSAGES.PLUGIN_RELEASES_FETCH_FAILED_STATUS).contains("503");
        expectedDetail.ifPresent(detail -> assertThat(thrown.getMessage()).contains(detail));
    }

    @ParameterizedTest(name = "Status {0} carries detail from single-error body")
    @ValueSource(ints = {400, 404, 500, 502})
    @DisplayName("Other failure status codes with single-error envelope")
    void otherFailureStatuses(int status) {
        stubV2ErrorSingle(get(urlEqualTo(API.PLUGIN_RELEASES_ENDPOINT)), status, "stubbed-" + status);

        Throwable thrown = catchThrowing(() -> service.fetchPluginReleases(instanceUrl()));

        assertThat(thrown).isInstanceOf(Exception.class);
        assertThat(thrown.getMessage()).contains(String.valueOf(status)).contains("stubbed-" + status);
    }

    @Test
    @DisplayName("Empty body produces the exact status-only message")
    void emptyBodyExactMessage() {
        stubV2ErrorEmptyBody(get(urlEqualTo(API.PLUGIN_RELEASES_ENDPOINT)), 500);

        assertThatThrownBy(() -> service.fetchPluginReleases(instanceUrl()))
                .isInstanceOf(Exception.class)
                .hasMessage(MESSAGES.PLUGIN_RELEASES_FETCH_FAILED_STATUS + "500");
    }

    // ====================================================================== //
    //                              HELPERS                                   //
    // ====================================================================== //

    private static JsonObject release(String label, String software, String minVersion,
                                      String maxVersion, String pluginVersion) {
        JsonObjectBuilder b = Json.createObjectBuilder()
                .add("software", software)
                .add("plugin_version", pluginVersion)
                .add("download_url", "https://example.test/releases/" + label + ".jar")
                .add("sha256_hash", "deadbeef");
        if (minVersion != null) {
            b.add("min_software_version", minVersion);
        }
        if (maxVersion != null) {
            b.add("max_software_version", maxVersion);
        }
        return b.build();
    }

    /** Returns {@code current} with the major component bumped by {@code delta} (clamped to >= 0). */
    private static String bumpMajor(String current, int delta) {
        String[] parts = current.split("\\.");
        int major = Integer.parseInt(parts[0]) + delta;
        if (major < 0) {
            major = 0;
        }
        return major + "." + parts[1] + "." + parts[2];
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
