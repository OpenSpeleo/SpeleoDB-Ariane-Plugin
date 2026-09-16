package org.speleodb.ariane.plugin.speleodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import com.arianesline.cavelib.api.CaveSurveyInterface;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.PATHS;

@DisplayName("Upload lifecycle and concurrency guard")
class SpeleoDBUploadLifecycleTest {
    @TempDir Path root;
    private SpeleoDBController controller;
    private SpeleoDBPlugin plugin;
    private SpeleoDBService service;
    private ExecutorService executor;
    private final ArrayList<Runnable> workers = new ArrayList<>();
    private Method upload;
    private JsonObject project;
    private AtomicBoolean inProgress;

    @BeforeEach
    void setup() throws Exception {
        controller = new SpeleoDBController(true);
        plugin = mock(SpeleoDBPlugin.class);
        service = mock(SpeleoDBService.class);
        executor = mock(ExecutorService.class);
        Field executorField = SpeleoDBPlugin.class.getField("executorService");
        executorField.setAccessible(true);
        executorField.set(plugin, executor);
        doAnswer(call -> { workers.add(call.getArgument(0)); return null; }).when(executor).execute(any());
        controller.parentPlugin = plugin;
        set("speleoDBService", service);
        project = Json.createObjectBuilder().add("id", "upload-project").add("name", "Cave").build();
        set("currentProject", project);
        ((CountDownLatch) get("fxmlInitializedLatch")).countDown();
        inProgress = (AtomicBoolean) get("uploadInProgress");
        when(service.getSDBInstance()).thenReturn("http://localhost");
        when(plugin.getSurveyFile()).thenReturn(Path.of("survey.tml").toFile());
        when(plugin.requestSurveySave(any(BooleanSupplier.class))).thenReturn(CompletableFuture.completedFuture(null));
        upload = SpeleoDBController.class.getDeclaredMethod("uploadProjectWithMessage", String.class);
        upload.setAccessible(true);
    }

    private Object get(String name) throws Exception {
        Field field = SpeleoDBController.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(controller);
    }

    private void set(String name, Object value) throws Exception {
        Field field = SpeleoDBController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }

    @Test
    @DisplayName("Duplicate requests schedule just one save and upload; completion releases the guard")
    void duplicateRequests() throws Exception {
        try (var fx = mockStatic(Platform.class)) {
            upload.invoke(controller, "message");
            upload.invoke(controller, "second message");
            assertThat(inProgress).isTrue();
            assertThat(workers).hasSize(1);
            workers.getFirst().run();
            assertThat(inProgress).isFalse();
        }
        verify(plugin, times(1)).requestSurveySave(any(BooleanSupplier.class));
        verify(service, times(1)).uploadProject(eq("message"), eq(project), eq(Path.of("survey.tml")), any());
    }

    @Test
    @DisplayName("Executor rejection releases the guard without dispatching a save")
    void executorRejected() throws Exception {
        doThrow(new RejectedExecutionException()).when(executor).execute(any());
        try (var fx = mockStatic(Platform.class)) {
            upload.invoke(controller, "message");
            assertThat(inProgress).isFalse();
        }
        verify(plugin, times(0)).requestSurveySave(any(BooleanSupplier.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"preparation", "network", "notModified"})
    @DisplayName("Failures and 304 release the upload guard")
    void failureCleanup(String kind) throws Exception {
        Exception failure = switch (kind) {
            case "preparation" -> new java.io.IOException("invalid ZIP");
            case "network" -> new java.net.ConnectException("offline");
            default -> new NotModifiedException("unchanged");
        };
        doThrow(failure).when(service).uploadProject(anyString(), any(), any(), any(BooleanSupplier.class));
        try (var fx = mockStatic(Platform.class)) {
            upload.invoke(controller, "message");
            workers.getFirst().run();
            assertThat(inProgress).isFalse();
        }
    }

    @Test
    @DisplayName("A project change before the worker starts prevents save and upload")
    void projectChanged() throws Exception {
        try (var fx = mockStatic(Platform.class)) {
            upload.invoke(controller, "message");
            set("currentProject", Json.createObjectBuilder().add("id", "different").build());
            workers.getFirst().run();
            assertThat(inProgress).isFalse();
        }
        verify(plugin, times(0)).requestSurveySave(any(BooleanSupplier.class));
        verify(service, times(0)).uploadProject(anyString(), any(), any(), any(BooleanSupplier.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"survey", "source"})
    @DisplayName("A host survey or source change before the worker prevents save and upload")
    void hostContextChangedBeforeWorker(String change) throws Exception {
        try (var fx = mockStatic(Platform.class)) {
            upload.invoke(controller, "message");
            if (change.equals("survey")) {
                when(plugin.getSurvey()).thenReturn(mock(CaveSurveyInterface.class));
            } else {
                when(plugin.getSurveyFile()).thenReturn(Path.of("different.tml").toFile());
            }
            workers.getFirst().run();
            assertThat(inProgress).isFalse();
        }
        verify(plugin, times(0)).requestSurveySave(any(BooleanSupplier.class));
        verify(service, times(0)).uploadProject(anyString(), any(), any(), any(BooleanSupplier.class));
    }

    @Test
    @DisplayName("Controller supplies a live survey guard to the queued save")
    void contextChangesBeforeDispatch() throws Exception {
        when(plugin.requestSurveySave(any(BooleanSupplier.class))).thenAnswer(call -> {
            BooleanSupplier valid = call.getArgument(0);
            assertThat(valid.getAsBoolean()).isTrue();
            when(plugin.getSurvey()).thenReturn(mock(CaveSurveyInterface.class));
            assertThat(valid.getAsBoolean()).isFalse();
            return CompletableFuture.failedFuture(new IllegalStateException("survey changed"));
        });
        try (var fx = mockStatic(Platform.class)) {
            upload.invoke(controller, "message");
            workers.getFirst().run();
            assertThat(inProgress).isFalse();
        }
        verify(service, times(0)).uploadProject(anyString(), any(), any(), any(BooleanSupplier.class));
    }

    @Test
    @DisplayName("A survey replacement during save dispatch prevents uploading its file to the old project")
    void surveyChangesDuringDispatch() throws Exception {
        when(plugin.requestSurveySave(any(BooleanSupplier.class))).thenAnswer(call -> {
            when(plugin.getSurvey()).thenReturn(mock(CaveSurveyInterface.class));
            return CompletableFuture.completedFuture(null);
        });
        try (var fx = mockStatic(Platform.class)) {
            upload.invoke(controller, "message");
            workers.getFirst().run();
        }
        verify(service, times(0)).uploadProject(anyString(), any(), any(), any(BooleanSupplier.class));
    }

    @Test
    @DisplayName("Save As may change the path for the same survey after guarded dispatch")
    void sameSurveySaveAs() throws Exception {
        when(plugin.getSurvey()).thenReturn(mock(CaveSurveyInterface.class));
        when(plugin.requestSurveySave(any(BooleanSupplier.class))).thenAnswer(call -> {
            assertThat(((BooleanSupplier) call.getArgument(0)).getAsBoolean()).isTrue();
            when(plugin.getSurveyFile()).thenReturn(Path.of("saved-as.tml").toFile());
            return CompletableFuture.completedFuture(null);
        });
        doAnswer(call -> {
            BooleanSupplier valid = call.getArgument(3);
            assertThat(valid.getAsBoolean()).isTrue();
            when(plugin.getSurvey()).thenReturn(mock(CaveSurveyInterface.class));
            assertThat(valid.getAsBoolean()).isFalse();
            return null;
        }).when(service).uploadProject(anyString(), any(), any(), any(BooleanSupplier.class));
        try (var fx = mockStatic(Platform.class)) {
            upload.invoke(controller, "message");
            workers.getFirst().run();
        }
        verify(service).uploadProject(eq("message"), eq(project), eq(Path.of("saved-as.tml")), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"active", "null", "missing", "changedBeforeWorker", "changedBeforeDispatch"})
    @DisplayName("Reload uses active saved bytes, falls back only for null, and rejects context changes")
    void reloadUsesActiveSource(String mode) throws Exception {
        String id = "reload-review-" + UUID.randomUUID();
        project = Json.createObjectBuilder().add("id", id).add("name", "Reload Cave").build();
        set("currentProject", project);
        Path canonical = Path.of(PATHS.SDB_PROJECT_DIR, id + PATHS.TML_FILE_EXTENSION);
        Files.createDirectories(canonical.getParent());
        byte[] oldBytes = TestFixtures.createZipBytes(java.util.zip.ZipEntry.STORED, "old.xml");
        byte[] savedBytes = TestFixtures.createZipBytes(java.util.zip.ZipEntry.STORED, "new.xml");
        Files.write(canonical, oldBytes);
        Path active = Files.write(root.resolve("saved-as.tml"), savedBytes);
        if (mode.equals("missing")) {
            Files.delete(active);
        }
        when(plugin.getSurveyFile()).thenReturn(mode.equals("null") ? null : active.toFile());
        when(plugin.getSurvey()).thenReturn(mock(CaveSurveyInterface.class));
        when(plugin.getCommandProperty()).thenReturn(new SimpleStringProperty());
        AtomicBoolean runNextFx = new AtomicBoolean();
        try (var fx = mockStatic(Platform.class); var modals = mockStatic(SpeleoDBModals.class)) {
            modals.when(() -> SpeleoDBModals.showConfirmation(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(true);
            fx.when(() -> Platform.runLater(any())).thenAnswer(call -> {
                if (runNextFx.compareAndSet(true, false)) {
                    if (mode.equals("changedBeforeDispatch")) {
                        set("currentProject", Json.createObjectBuilder().add("id", "other").build());
                    }
                    ((Runnable) call.getArgument(0)).run();
                }
                return null;
            });
            controller.onReloadProject(null);
            if (mode.equals("missing")) {
                assertThat(workers).isEmpty();
            } else {
                if (mode.equals("changedBeforeWorker")) {
                    set("currentProject", Json.createObjectBuilder().add("id", "other").build());
                }
                workers.getFirst().run();
                if (mode.equals("changedBeforeWorker")) {
                    assertThat(workers).hasSize(1);
                } else {
                    assertThat(workers).hasSize(2);
                    runNextFx.set(true);
                    workers.get(1).run();
                }
            }
        } finally {
            Files.deleteIfExists(canonical);
        }
        if (mode.equals("active") || mode.equals("null")) {
            verify(plugin).setSurveyFile((mode.equals("active") ? active : canonical).toFile());
            assertThat(plugin.getCommandProperty().get()).isEqualTo("LOAD");
            assertThat(Files.readAllBytes(active)).isEqualTo(savedBytes);
        } else {
            verify(plugin, times(0)).setSurveyFile(any());
            assertThat(plugin.getCommandProperty().get()).isNull();
        }
    }

    @Test
    @DisplayName("A rejected queued load never reports success or mutates the survey")
    void rejectedLoadNeverReportsSuccess() throws Exception {
        Runnable success = mock(Runnable.class);
        @SuppressWarnings("unchecked")
        java.util.function.Consumer<Exception> error = mock(java.util.function.Consumer.class);
        Method load = SpeleoDBController.class.getDeclaredMethod("loadSurveyAsync", java.io.File.class,
                Runnable.class, java.util.function.Consumer.class, BooleanSupplier.class);
        load.setAccessible(true);
        try (var fx = mockStatic(Platform.class)) {
            fx.when(() -> Platform.runLater(any())).thenAnswer(call -> {
                ((Runnable) call.getArgument(0)).run();
                return null;
            });
            load.invoke(controller, root.resolve("survey.tml").toFile(), success, error,
                    (BooleanSupplier) () -> false);
            workers.getFirst().run();
        }
        verify(success, times(0)).run();
        verify(error).accept(any());
        verify(plugin, times(0)).setSurveyFile(any());
    }

    @Test
    @DisplayName("An interrupted worker cancels queued save dispatch and preserves interruption")
    void interruptedSave() throws Exception {
        CompletableFuture<Void> pending = new CompletableFuture<>();
        when(plugin.requestSurveySave(any(BooleanSupplier.class))).thenReturn(pending);
        try (var fx = mockStatic(Platform.class)) {
            upload.invoke(controller, "message");
            Thread.currentThread().interrupt();
            try {
                workers.getFirst().run();
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
                assertThat(pending).isCancelled();
                assertThat(inProgress).isFalse();
            } finally {
                Thread.interrupted();
            }
        }
        verify(service, times(0)).uploadProject(anyString(), any(), any(), any(BooleanSupplier.class));
    }

    @Test
    @DisplayName("Dispatch timeout cancels the pending action without invoking a second save")
    void saveTimeout() throws Exception {
        @SuppressWarnings("unchecked")
        CompletableFuture<Void> pending = mock(CompletableFuture.class);
        when(pending.get(org.mockito.ArgumentMatchers.anyLong(), any(TimeUnit.class))).thenThrow(new TimeoutException());
        when(plugin.requestSurveySave(any(BooleanSupplier.class))).thenReturn(pending);
        try (var fx = mockStatic(Platform.class)) {
            upload.invoke(controller, "message");
            workers.getFirst().run();
            assertThat(inProgress).isFalse();
        }
        verify(pending).cancel(false);
        verify(plugin, times(1)).requestSurveySave(any(BooleanSupplier.class));
        verify(service, times(0)).uploadProject(anyString(), any(), any(), any(BooleanSupplier.class));
    }
}
