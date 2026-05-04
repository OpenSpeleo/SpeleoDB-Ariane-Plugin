package org.speleodb.ariane.plugin.speleodb;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;

import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.API;

import com.github.tomakehurst.wiremock.http.Fault;

import jakarta.json.Json;
import jakarta.json.JsonObject;

/**
 * WireMock-driven tests for {@link SpeleoDBService#acquireOrRefreshProjectMutex(JsonObject)}
 * and {@link SpeleoDBService#releaseProjectMutex(JsonObject)}. Both expose a {@code boolean}
 * result -- 200 -> true, 4xx/5xx -> false (with a logged warning), network fault -> exception.
 */
@DisplayName("SpeleoDBService.{acquireOrRefreshProjectMutex, releaseProjectMutex}")
class SpeleoDBProjectMutexApiTest extends AbstractSpeleoDBServiceWireMockTest {

    private static final String PROJECT_ID = "proj-uuid-1234";
    private static final JsonObject PROJECT = Json.createObjectBuilder()
            .add("id", PROJECT_ID)
            .add("name", "My Cave")
            .build();

    private static final String ACQUIRE_PATH = API.PROJECTS_ENDPOINT + PROJECT_ID + API.ACQUIRE_LOCK_PATH;
    private static final String RELEASE_PATH = API.PROJECTS_ENDPOINT + PROJECT_ID + API.RELEASE_LOCK_PATH;

    @BeforeEach
    void authenticate() throws Exception {
        resetLoggerShutdownFlag();
        authenticateAgainstWireMock();
    }

    @BeforeAll
    static void startJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // "Toolkit already initialized" is the happy path -- FX is up from a prior class.
            if (!e.getMessage().contains("Toolkit already initialized")) {
                throw e;
            }
        } catch (UnsupportedOperationException e) {
            // Headless Linux CI without DISPLAY/Monocle: the FX-dependent tests in this
            // class will fail their Mockito timeout(1000) verify (because Platform.runLater
            // never executes), but they fail in 1s rather than blocking class init.
        }
    }

    @AfterEach
    void disconnectLoggerUiController() {
        SpeleoDBLogger.getInstance().disconnectUIController();
    }

    private static void resetLoggerShutdownFlag() throws ReflectiveOperationException {
        SpeleoDBLogger logger = SpeleoDBLogger.getInstance();
        Field shutdownCalled = SpeleoDBLogger.class.getDeclaredField("shutdownCalled");
        shutdownCalled.setAccessible(true);
        shutdownCalled.setBoolean(logger, false);
    }

    // ====================================================================== //
    //                               ACQUIRE                                  //
    // ====================================================================== //

    @Test
    @DisplayName("acquire: 200 -> true, request goes to /<id>/acquire/")
    void acquireSuccess() throws Exception {
        wm.stubFor(post(urlEqualTo(ACQUIRE_PATH)).willReturn(aResponse().withStatus(200)));

        boolean acquired = service.acquireOrRefreshProjectMutex(PROJECT);

        assertThat(acquired).isTrue();
        verifyAuthHeader(postTo(ACQUIRE_PATH));
    }

    @ParameterizedTest(name = "acquire: HTTP {0} returns false")
    @ValueSource(ints = {400, 401, 403, 409, 423, 500, 502, 503})
    @DisplayName("acquire: every non-200 status returns false")
    void acquireFailureStatusesReturnFalse(int status) throws Exception {
        stubV2ErrorSingle(post(urlEqualTo(ACQUIRE_PATH)), status, "lock held by other user");

        boolean acquired = service.acquireOrRefreshProjectMutex(PROJECT);

        assertThat(acquired).isFalse();
    }

    @Test
    @DisplayName("acquire: empty error body still returns false (no exception)")
    void acquireEmptyBodyReturnsFalse() throws Exception {
        stubV2ErrorEmptyBody(post(urlEqualTo(ACQUIRE_PATH)), 423);

        boolean acquired = service.acquireOrRefreshProjectMutex(PROJECT);

        assertThat(acquired).isFalse();
    }

    @Test
    @DisplayName("acquire: list-of-strings error body returns false")
    void acquireListErrorReturnsFalse() throws Exception {
        stubV2ErrorList(post(urlEqualTo(ACQUIRE_PATH)), 409, "already-locked", "by-user-x");

        boolean acquired = service.acquireOrRefreshProjectMutex(PROJECT);

        assertThat(acquired).isFalse();
    }

    @Test
    @DisplayName("acquire: parsed failure detail is surfaced to the UI logger")
    @DisabledIfEnvironmentVariable(named = "CI", matches = "true",
            disabledReason = "Logger.setUIController triggers Platform.runLater which blocks indefinitely on headless CI")
    void acquireFailureSurfacesParsedDetailToUiLog() throws Exception {
        SpeleoDBLogger.getInstance().setUIController(controller);
        stubV2ErrorSingle(post(urlEqualTo(ACQUIRE_PATH)), 423, "locked by alice@example.com");

        boolean acquired = service.acquireOrRefreshProjectMutex(PROJECT);

        assertThat(acquired).isFalse();
        verify(controller, timeout(1000)).appendToUILog(argThat(message ->
                message.contains("WARN: " + SpeleoDBConstants.MESSAGES.MUTEX_ACQUIRE_FAILED_STATUS + "423")
                        && message.contains("locked by alice@example.com")));
    }

    @Test
    @DisplayName("acquire: connection-reset propagates as exception")
    void acquireConnectionResetPropagates() {
        wm.stubFor(post(urlEqualTo(ACQUIRE_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> service.acquireOrRefreshProjectMutex(PROJECT))
                .isInstanceOf(Exception.class);
    }

    // ====================================================================== //
    //                                RELEASE                                 //
    // ====================================================================== //

    @Test
    @DisplayName("release: 200 -> true, request goes to /<id>/release/")
    void releaseSuccess() throws Exception {
        wm.stubFor(post(urlEqualTo(RELEASE_PATH)).willReturn(aResponse().withStatus(200)));

        boolean released = service.releaseProjectMutex(PROJECT);

        assertThat(released).isTrue();
        verifyAuthHeader(postTo(RELEASE_PATH));
    }

    @ParameterizedTest(name = "release: HTTP {0} returns false")
    @ValueSource(ints = {400, 401, 403, 404, 500, 503})
    @DisplayName("release: every non-200 status returns false")
    void releaseFailureStatusesReturnFalse(int status) throws Exception {
        stubV2ErrorSingle(post(urlEqualTo(RELEASE_PATH)), status, "no mutex held");

        boolean released = service.releaseProjectMutex(PROJECT);

        assertThat(released).isFalse();
    }

    @Test
    @DisplayName("release: parsed failure detail is surfaced to the UI logger")
    @DisabledIfEnvironmentVariable(named = "CI", matches = "true",
            disabledReason = "Logger.setUIController triggers Platform.runLater which blocks indefinitely on headless CI")
    void releaseFailureSurfacesParsedDetailToUiLog() throws Exception {
        SpeleoDBLogger.getInstance().setUIController(controller);
        stubV2ErrorSingle(post(urlEqualTo(RELEASE_PATH)), 409, "no mutex held by current user");

        boolean released = service.releaseProjectMutex(PROJECT);

        assertThat(released).isFalse();
        verify(controller, timeout(1000)).appendToUILog(argThat(message ->
                message.contains("WARN: " + SpeleoDBConstants.MESSAGES.MUTEX_RELEASE_FAILED_STATUS + "409")
                        && message.contains("no mutex held by current user")));
    }

    @Test
    @DisplayName("release: connection-reset propagates as exception")
    void releaseConnectionResetPropagates() {
        wm.stubFor(post(urlEqualTo(RELEASE_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> service.releaseProjectMutex(PROJECT))
                .isInstanceOf(Exception.class);
    }
}
