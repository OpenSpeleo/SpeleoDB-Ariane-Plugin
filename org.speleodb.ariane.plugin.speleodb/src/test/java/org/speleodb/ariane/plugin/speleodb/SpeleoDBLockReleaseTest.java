package org.speleodb.ariane.plugin.speleodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.json.Json;
import jakarta.json.JsonObject;

/**
 * Tests for project lock release paths: disconnect, project switch, and shutdown.
 * Verifies that locks are always released on the server before local state is cleared.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpeleoDB Lock Release Tests")
class SpeleoDBLockReleaseTest {

    @Mock
    private SpeleoDBService mockService;

    private SpeleoDBController controller;

    private static final JsonObject TEST_PROJECT = Json.createObjectBuilder()
            .add("id", "proj-release-test")
            .add("name", "Release Test Project")
            .add("permission", "ADMIN")
            .build();

    @BeforeEach
    void setUp() throws Exception {
        controller = new SpeleoDBController(true);
        setPrivateField(controller, "speleoDBService", mockService);
    }

    @Nested
    @DisplayName("hasActiveProjectLock state transitions")
    class LockStateTests {

        @Test
        @DisplayName("Should report no active lock when currentProject is null")
        void shouldReportNoLockWhenNull() {
            assertThat(controller.hasActiveProjectLock()).isFalse();
        }

        @Test
        @DisplayName("Should report active lock when currentProject is set")
        void shouldReportActiveLockWhenSet() throws Exception {
            setPrivateField(controller, "currentProject", TEST_PROJECT);
            assertThat(controller.hasActiveProjectLock()).isTrue();
        }

        @Test
        @DisplayName("getCurrentProjectName should return null when no project")
        void shouldReturnNullNameWhenNoProject() {
            assertThat(controller.getCurrentProjectName()).isNull();
        }

        @Test
        @DisplayName("getCurrentProjectName should return name when project is set")
        void shouldReturnNameWhenProjectSet() throws Exception {
            setPrivateField(controller, "currentProject", TEST_PROJECT);
            assertThat(controller.getCurrentProjectName()).isEqualTo("Release Test Project");
        }
    }

    @Nested
    @DisplayName("releaseProjectLock (private method)")
    class ReleaseProjectLockTests {

        @Test
        @DisplayName("Should call releaseProjectMutex on the service and return success")
        void shouldCallServiceAndReturnSuccess() throws Exception {
            when(mockService.releaseProjectMutex(any(JsonObject.class))).thenReturn(true);

            Method releaseMethod = SpeleoDBController.class.getDeclaredMethod(
                    "releaseProjectLock", JsonObject.class, String.class);
            releaseMethod.setAccessible(true);

            SpeleoDBController.LockReleaseResult result =
                    (SpeleoDBController.LockReleaseResult) releaseMethod.invoke(controller, TEST_PROJECT, "test");

            verify(mockService, times(1)).releaseProjectMutex(TEST_PROJECT);
            assertThat(result.isReleased()).isTrue();
            assertThat(result.getMessage()).contains("Successfully released");
        }

        @Test
        @DisplayName("Should return failure when service returns false")
        void shouldReturnFailureWhenServiceFails() throws Exception {
            when(mockService.releaseProjectMutex(any(JsonObject.class))).thenReturn(false);

            Method releaseMethod = SpeleoDBController.class.getDeclaredMethod(
                    "releaseProjectLock", JsonObject.class, String.class);
            releaseMethod.setAccessible(true);

            SpeleoDBController.LockReleaseResult result =
                    (SpeleoDBController.LockReleaseResult) releaseMethod.invoke(controller, TEST_PROJECT, "test");

            assertThat(result.isReleased()).isFalse();
        }

        @Test
        @DisplayName("Should return failure for null project")
        void shouldReturnFailureForNullProject() throws Exception {
            Method releaseMethod = SpeleoDBController.class.getDeclaredMethod(
                    "releaseProjectLock", JsonObject.class, String.class);
            releaseMethod.setAccessible(true);

            SpeleoDBController.LockReleaseResult result =
                    (SpeleoDBController.LockReleaseResult) releaseMethod.invoke(controller, (JsonObject) null, "test");

            assertThat(result.isReleased()).isFalse();
            verify(mockService, never()).releaseProjectMutex(any());
        }
    }

    @Nested
    @DisplayName("Disconnect lock release (C-4 fix)")
    class DisconnectLockReleaseTests {

        @Test
        @DisplayName("Should release lock on server when disconnecting with active lock")
        void shouldReleaseLockOnDisconnect() throws Exception {
            setPrivateField(controller, "currentProject", TEST_PROJECT);
            // getSDBInstance() is called at the top of disconnectFromSpeleoDB() for logging
            when(mockService.getSDBInstance()).thenReturn("https://test.example.com");
            when(mockService.releaseProjectMutex(any(JsonObject.class))).thenReturn(true);

            assertThat(controller.hasActiveProjectLock()).isTrue();

            Method disconnectMethod = SpeleoDBController.class.getDeclaredMethod("disconnectFromSpeleoDB");
            disconnectMethod.setAccessible(true);

            try {
                disconnectMethod.invoke(controller);
            } catch (Exception e) {
                // UI component NPEs are expected in headless test -- the lock release
                // happens before any UI manipulation
            }

            verify(mockService, times(1)).releaseProjectMutex(TEST_PROJECT);
        }

        @Test
        @DisplayName("Should not call releaseProjectMutex when no active lock on disconnect")
        void shouldNotReleaseLockWhenNoActiveLock() throws Exception {
            when(mockService.getSDBInstance()).thenReturn("https://test.example.com");

            assertThat(controller.hasActiveProjectLock()).isFalse();

            Method disconnectMethod = SpeleoDBController.class.getDeclaredMethod("disconnectFromSpeleoDB");
            disconnectMethod.setAccessible(true);

            try {
                disconnectMethod.invoke(controller);
            } catch (Exception e) {
                // UI component NPEs expected in headless test
            }

            verify(mockService, never()).releaseProjectMutex(any());
        }
    }

    @Nested
    @DisplayName("Shutdown lock release")
    class ShutdownLockReleaseTests {

        @Test
        @DisplayName("Should release lock during shutdown cleanup")
        void shouldReleaseLockDuringShutdown() throws Exception {
            setPrivateField(controller, "currentProject", TEST_PROJECT);
            when(mockService.releaseProjectMutex(any(JsonObject.class))).thenReturn(true);

            assertThat(controller.hasActiveProjectLock()).isTrue();

            controller.performShutdownCleanup();

            verify(mockService, times(1)).releaseProjectMutex(TEST_PROJECT);
        }

        @Test
        @DisplayName("Should not throw when no lock during shutdown")
        void shouldNotThrowWhenNoLockDuringShutdown() throws Exception {
            assertThat(controller.hasActiveProjectLock()).isFalse();

            assertThatCode(() -> controller.performShutdownCleanup())
                    .doesNotThrowAnyException();

            verify(mockService, never()).releaseProjectMutex(any());
        }

        @Test
        @DisplayName("Shutdown cleanup should be idempotent")
        void shutdownCleanupShouldBeIdempotent() throws Exception {
            setPrivateField(controller, "currentProject", TEST_PROJECT);
            when(mockService.releaseProjectMutex(any(JsonObject.class))).thenReturn(true);

            controller.performShutdownCleanup();
            controller.performShutdownCleanup();

            verify(mockService, times(1)).releaseProjectMutex(TEST_PROJECT);
        }

        @Test
        @DisplayName("Should continue shutdown even if lock release fails")
        void shouldContinueShutdownOnReleaseFailure() throws Exception {
            setPrivateField(controller, "currentProject", TEST_PROJECT);
            when(mockService.releaseProjectMutex(any(JsonObject.class))).thenReturn(false);

            assertThatCode(() -> controller.performShutdownCleanup())
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Thread safety of currentProject")
    class ThreadSafetyTests {

        @Test
        @DisplayName("currentProject field should be volatile")
        void currentProjectShouldBeVolatile() throws Exception {
            Field field = SpeleoDBController.class.getDeclaredField("currentProject");
            assertThat(java.lang.reflect.Modifier.isVolatile(field.getModifiers()))
                    .as("currentProject must be volatile for cross-thread visibility")
                    .isTrue();
        }
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
