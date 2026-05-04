package org.speleodb.ariane.plugin.speleodb;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
 * WireMock-driven tests for {@link SpeleoDBService#fetchAnnouncements(String)}.
 * Verifies the v2 unwrapped-array contract, the four client-side filters
 * (active / software / expiry / version), and every failure status against
 * every error envelope. This endpoint is unauthenticated.
 */
@DisplayName("SpeleoDBService.fetchAnnouncements(...)")
class SpeleoDBAnnouncementsApiTest extends AbstractSpeleoDBServiceWireMockTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    // ====================================================================== //
    //                            SUCCESS PATHS                               //
    // ====================================================================== //

    @Test
    @DisplayName("200 with v2 unwrapped array, all four filters applied")
    void successAndFiltering() throws Exception {
        String futureDate = LocalDate.now().plusDays(30).format(ISO);
        String pastDate = LocalDate.now().minusDays(1).format(ISO);

        JsonArray body = Json.createArrayBuilder()
                .add(announcement("kept-active-ariane-future", true,  "ARIANE", futureDate, null))
                .add(announcement("dropped-inactive",          false, "ARIANE", futureDate, null))
                .add(announcement("dropped-other-software",    true,  "OTHER",  futureDate, null))
                .add(announcement("dropped-expired",           true,  "ARIANE", pastDate,   null))
                .add(announcement("kept-no-expiry",            true,  "ARIANE", null,       null))
                .add(announcement("dropped-malformed-expiry",  true,  "ARIANE", "not-a-date", null))
                .add(announcement("dropped-version-mismatch",  true,  "ARIANE", futureDate, "9999.99.99"))
                .build();
        stubV2Array(get(urlEqualTo(API.ANNOUNCEMENTS_ENDPOINT)), body);

        JsonArray result = service.fetchAnnouncements(instanceUrl());

        // Either two (kept-active-ariane-future + kept-no-expiry) when version is null/empty,
        // or fewer if SpeleoDBConstants.VERSION is set during build (unlikely in test mode).
        assertThat(result).extracting(j -> ((JsonObject) j).getString("title"))
                .contains("kept-active-ariane-future", "kept-no-expiry")
                .doesNotContain("dropped-inactive", "dropped-other-software",
                        "dropped-expired", "dropped-malformed-expiry", "dropped-version-mismatch");
    }

    @Test
    @DisplayName("200 with empty array returns empty result, no exception")
    void emptyArray() throws Exception {
        stubV2Array(get(urlEqualTo(API.ANNOUNCEMENTS_ENDPOINT)), Json.createArrayBuilder().build());

        JsonArray result = service.fetchAnnouncements(instanceUrl());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Endpoint is public: NO Authorization header is sent")
    void doesNotSendAuthHeader() throws Exception {
        stubV2Array(get(urlEqualTo(API.ANNOUNCEMENTS_ENDPOINT)), Json.createArrayBuilder().build());

        service.fetchAnnouncements(instanceUrl());

        wm.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                        urlEqualTo(API.ANNOUNCEMENTS_ENDPOINT))
                .withoutHeader(HEADERS.AUTHORIZATION));
    }

    // ====================================================================== //
    //                             BAD PAYLOAD                                //
    // ====================================================================== //

    @Test
    @DisplayName("200 with malformed JSON propagates a parse exception")
    void malformedJson() {
        wm.stubFor(get(urlEqualTo(API.ANNOUNCEMENTS_ENDPOINT))
                .willReturn(aResponse().withStatus(200)
                        .withHeader(HEADERS.CONTENT_TYPE, "application/json")
                        .withBody("{not-json")));

        assertThatThrownBy(() -> service.fetchAnnouncements(instanceUrl()))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("200 with object root (pre-v2 wrapper) propagates a parse exception")
    void objectRootInsteadOfArray() {
        wm.stubFor(get(urlEqualTo(API.ANNOUNCEMENTS_ENDPOINT))
                .willReturn(aResponse().withStatus(200)
                        .withHeader(HEADERS.CONTENT_TYPE, "application/json")
                        .withBody("{\"data\": []}")));

        assertThatThrownBy(() -> service.fetchAnnouncements(instanceUrl()))
                .isInstanceOf(Exception.class);
    }

    // ====================================================================== //
    //                           FAILURE STATUS GRID                          //
    // ====================================================================== //

    @ParameterizedTest(name = "[{0}] 503 surfaces parsed detail (when present)")
    @MethodSource("errorEnvelopes")
    @DisplayName("5xx parameterized over every v2 error envelope shape")
    void failureStatusesAcrossEnvelopes(String label, ErrorBodyStubber stubber, Optional<String> expectedDetail) {
        MappingBuilder request = get(urlEqualTo(API.ANNOUNCEMENTS_ENDPOINT));
        stubber.stub(this, request, 503);

        Throwable thrown = catchThrowing(() -> service.fetchAnnouncements(instanceUrl()));

        assertThat(thrown).isInstanceOf(Exception.class);
        assertThat(thrown.getMessage()).contains(MESSAGES.ANNOUNCEMENTS_FETCH_FAILED_STATUS).contains("503");
        expectedDetail.ifPresent(detail -> assertThat(thrown.getMessage()).contains(detail));
    }

    @ParameterizedTest(name = "Status {0} carries detail from single-error body")
    @ValueSource(ints = {400, 404, 500, 502})
    @DisplayName("Other failure status codes with single-error envelope")
    void otherFailureStatuses(int status) {
        stubV2ErrorSingle(get(urlEqualTo(API.ANNOUNCEMENTS_ENDPOINT)), status, "stubbed-" + status);

        Throwable thrown = catchThrowing(() -> service.fetchAnnouncements(instanceUrl()));

        assertThat(thrown).isInstanceOf(Exception.class);
        assertThat(thrown.getMessage()).contains(String.valueOf(status)).contains("stubbed-" + status);
    }

    @Test
    @DisplayName("Empty body produces the exact status-only message")
    void emptyBodyExactMessage() {
        stubV2ErrorEmptyBody(get(urlEqualTo(API.ANNOUNCEMENTS_ENDPOINT)), 500);

        assertThatThrownBy(() -> service.fetchAnnouncements(instanceUrl()))
                .isInstanceOf(Exception.class)
                .hasMessage(MESSAGES.ANNOUNCEMENTS_FETCH_FAILED_STATUS + "500");
    }

    // ====================================================================== //
    //                              HELPERS                                   //
    // ====================================================================== //

    private static JsonObject announcement(String title, boolean isActive, String software,
                                           String expiryDate, String version) {
        JsonObjectBuilder b = Json.createObjectBuilder()
                .add("title", title)
                .add("message", "msg")
                .add("is_active", isActive)
                .add("software", software);
        if (expiryDate != null) {
            b.add("expiracy_date", expiryDate);
        }
        if (version != null) {
            b.add("version", version);
        }
        return b.build();
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
