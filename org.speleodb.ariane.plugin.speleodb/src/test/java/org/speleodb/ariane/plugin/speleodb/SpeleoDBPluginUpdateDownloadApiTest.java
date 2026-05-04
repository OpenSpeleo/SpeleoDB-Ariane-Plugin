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
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.HEADERS;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.MESSAGES;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.http.Fault;

/**
 * WireMock-driven tests for {@link SpeleoDBService#downloadPluginUpdate(String)}.
 * Verifies the binary 200 contract, redirect-following, host case-insensitivity, and
 * every failure status against every error envelope (with the byte-body UTF-8 decode).
 */
@DisplayName("SpeleoDBService.downloadPluginUpdate(...)")
class SpeleoDBPluginUpdateDownloadApiTest extends AbstractSpeleoDBServiceWireMockTest {

    private static final String DOWNLOAD_PATH = "/releases/v1/plugin.jar";

    private String url() {
        return "http://localhost:" + wm.getPort() + DOWNLOAD_PATH;
    }

    // ====================================================================== //
    //                            SUCCESS PATHS                               //
    // ====================================================================== //

    @Test
    @DisplayName("200 with binary body returns the bytes verbatim; no Authorization header is sent")
    void successReturnsBytesWithoutAuth() throws Exception {
        byte[] body = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00, 0x08, 0x00};
        wm.stubFor(get(urlEqualTo(DOWNLOAD_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/octet-stream")
                        .withBody(body)));

        byte[] downloaded = service.downloadPluginUpdate(url());

        assertThat(downloaded).containsExactly(body);
        wm.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                        urlEqualTo(DOWNLOAD_PATH))
                .withoutHeader(HEADERS.AUTHORIZATION));
    }

    @Test
    @DisplayName("301 redirect to a 200 resource is followed (Redirect.NORMAL)")
    void followsRedirect301() throws Exception {
        byte[] body = new byte[]{0x01, 0x02, 0x03};
        wm.stubFor(get(urlEqualTo(DOWNLOAD_PATH))
                .willReturn(aResponse().withStatus(301).withHeader("Location", "/redirected/plugin.jar")));
        wm.stubFor(get(urlEqualTo("/redirected/plugin.jar"))
                .willReturn(aResponse().withStatus(200).withBody(body)));

        byte[] downloaded = service.downloadPluginUpdate(url());

        assertThat(downloaded).containsExactly(body);
    }

    @Test
    @DisplayName("302 redirect to a 200 resource is followed (Redirect.NORMAL)")
    void followsRedirect302() throws Exception {
        byte[] body = new byte[]{0x0A, 0x0B, 0x0C};
        wm.stubFor(get(urlEqualTo(DOWNLOAD_PATH))
                .willReturn(aResponse().withStatus(302).withHeader("Location", "/moved.jar")));
        wm.stubFor(get(urlEqualTo("/moved.jar"))
                .willReturn(aResponse().withStatus(200).withBody(body)));

        byte[] downloaded = service.downloadPluginUpdate(url());

        assertThat(downloaded).containsExactly(body);
    }

    @Test
    @DisplayName("Host is case-folded so URL with uppercase host still resolves to the WireMock loopback")
    void hostCaseInsensitive() throws Exception {
        byte[] body = new byte[]{0x55, 0x66};
        wm.stubFor(get(urlEqualTo(DOWNLOAD_PATH))
                .willReturn(aResponse().withStatus(200).withBody(body)));

        String urlWithUppercaseHost = "http://LOCALHOST:" + wm.getPort() + DOWNLOAD_PATH;
        byte[] downloaded = service.downloadPluginUpdate(urlWithUppercaseHost);

        assertThat(downloaded).containsExactly(body);
    }

    // ====================================================================== //
    //                           FAILURE STATUS GRID                          //
    // ====================================================================== //

    @ParameterizedTest(name = "[{0}] 500 surfaces parsed detail (when present) on byte body")
    @MethodSource("errorEnvelopes")
    @DisplayName("5xx parameterized over every v2 error envelope shape (UTF-8 decode path)")
    void serverErrorsAcrossEnvelopes(String label, ErrorBodyStubber stubber, Optional<String> expectedDetail) {
        MappingBuilder request = get(urlEqualTo(DOWNLOAD_PATH));
        stubber.stub(this, request, 500);

        Throwable thrown = catchThrowing(() -> service.downloadPluginUpdate(url()));

        assertThat(thrown).isInstanceOf(Exception.class);
        assertThat(thrown.getMessage()).contains(MESSAGES.PLUGIN_UPDATE_DOWNLOAD_FAILED_STATUS).contains("500");
        expectedDetail.ifPresent(detail -> assertThat(thrown.getMessage()).contains(detail));
    }

    @ParameterizedTest(name = "Status {0} carries detail from single-error body")
    @ValueSource(ints = {400, 401, 403, 404, 502, 503})
    @DisplayName("Other failure status codes with single-error envelope")
    void otherFailureStatuses(int status) {
        stubV2ErrorSingle(get(urlEqualTo(DOWNLOAD_PATH)), status, "stubbed-" + status);

        Throwable thrown = catchThrowing(() -> service.downloadPluginUpdate(url()));

        assertThat(thrown).isInstanceOf(Exception.class);
        assertThat(thrown.getMessage()).contains(String.valueOf(status)).contains("stubbed-" + status);
    }

    @Test
    @DisplayName("Empty error body produces the exact status-only message")
    void emptyBodyExactMessage() {
        stubV2ErrorEmptyBody(get(urlEqualTo(DOWNLOAD_PATH)), 502);

        assertThatThrownBy(() -> service.downloadPluginUpdate(url()))
                .isInstanceOf(Exception.class)
                .hasMessage(MESSAGES.PLUGIN_UPDATE_DOWNLOAD_FAILED_STATUS + "502");
    }

    @Test
    @DisplayName("Connection-reset surfaces as Exception")
    void connectionResetPropagates() {
        wm.stubFor(get(urlEqualTo(DOWNLOAD_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> service.downloadPluginUpdate(url()))
                .isInstanceOf(Exception.class);
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
