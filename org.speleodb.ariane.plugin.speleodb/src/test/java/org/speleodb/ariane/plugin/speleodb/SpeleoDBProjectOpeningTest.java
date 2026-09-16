package org.speleodb.ariane.plugin.speleodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import com.arianesline.ariane.plugin.api.DataServerCommands;
import com.arianesline.cavelib.api.CaveSurveyInterface;
import com.arianesline.cavelib.api.SurveyDataInterface;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.MESSAGES;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.PATHS;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.TIMINGS;

@DisplayName("Project opening and empty survey UI regressions")
@DisabledIfEnvironmentVariable(named = "CI", matches = "true",
        disabledReason = "FXML controls require a desktop JavaFX toolkit")
class SpeleoDBProjectOpeningTest {
    @TempDir Path root;
    private SpeleoDBController controller;
    private SpeleoDBService service;
    private SpeleoDBPlugin plugin;
    private final BlockingQueue<Runnable> workers = new LinkedBlockingQueue<>();
    private final AtomicReference<CaveSurveyInterface> survey = new AtomicReference<>();
    private final AtomicInteger centerRequests = new AtomicInteger();
    private final AtomicInteger redrawRequests = new AtomicInteger();
    private MockedStatic<SpeleoDBModals> modals;
    private MockedStatic<SpeleoDBTooltips> tooltips;
    private Path tml;

    @BeforeAll
    static void startFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Runnable initialize = () -> {
            // Keep the shared toolkit alive when other tests close their last dialog.
            Platform.setImplicitExit(false);
            started.countDown();
        };
        try {
            Platform.startup(initialize);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(initialize);
        }
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @BeforeEach
    void setup() throws Exception {
        // Exercise real FXML injection, without startup authentication or network requests.
        controller = new SpeleoDBController(true) {
            @Override
            public void initialize(URL location, ResourceBundle resources) { }
        };
        service = mock(SpeleoDBService.class);
        plugin = mock(SpeleoDBPlugin.class);
        ExecutorService executor = mock(ExecutorService.class);
        Field executorField = SpeleoDBPlugin.class.getField("executorService");
        executorField.setAccessible(true);
        executorField.set(plugin, executor);
        doAnswer(call -> { workers.add(call.getArgument(0)); return null; }).when(executor).execute(any());
        controller.parentPlugin = plugin;
        set("speleoDBService", service);
        when(service.isAuthenticated()).thenReturn(true);
        when(service.listProjects()).thenReturn(Json.createArrayBuilder().build());
        when(plugin.getSurvey()).thenAnswer(call -> survey.get());
        doAnswer(call -> { survey.set(call.getArgument(0)); return null; }).when(plugin).setSurvey(any());
        tml = Files.write(root.resolve("empty.tml"), new byte[0]);
        when(service.downloadProject(any())).thenReturn(tml);
        when(service.createEmptyTmlFileFromTemplate(anyString(), anyString())).thenReturn(tml);
        onFx(() -> {
            modals = mockStatic(SpeleoDBModals.class);
            tooltips = mockStatic(SpeleoDBTooltips.class);
            FXMLLoader loader = new FXMLLoader(getClass().getResource(PATHS.SPELEODB_FXML));
            loader.setController(controller);
            loader.load();
            ((CountDownLatch) get("fxmlInitializedLatch")).countDown();
            Button center = new Button();
            center.setTooltip(new Tooltip(SpeleoDBConstants.ARIANE_JAVAFX.CENTER_VIEW_TOOLTIP));
            center.setOnAction(event -> centerRequests.incrementAndGet());
            new Scene(new VBox(controller.getSpeleoDBAnchorPane(), center));
            pane("projectActionsPane").setVisible(false);
            pane("projectsListingPane").setExpanded(true);
            var commands = new SimpleStringProperty();
            commands.addListener((property, before, after) -> {
                if (DataServerCommands.LOAD.name().equals(after)) {
                    survey.set(emptySurvey());
                    commands.set(DataServerCommands.DONE.name());
                } else if (DataServerCommands.REDRAW.name().equals(after)) {
                    redrawRequests.incrementAndGet();
                    commands.set(DataServerCommands.DONE.name());
                }
            });
            when(plugin.getCommandProperty()).thenReturn(commands);
        });
    }

    @AfterEach
    void cleanup() throws Exception {
        onFx(() -> {
            @SuppressWarnings("unchecked")
            List<Timeline> animations = (List<Timeline>) get("runningAnimations");
            animations.forEach(Timeline::stop);
            animations.clear();
            if (tooltips != null) tooltips.close();
            if (modals != null) modals.close();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"READ_ONLY", "READ_AND_WRITE"})
    @DisplayName("Read-only permission and failed lock both select the project and disable every action")
    void readOnlyProject(String permission) throws Exception {
        JsonObject project = project(permission);
        open(project);
        onFx(() -> {
            assertProjectPane(project, true);
            assertThat(controller.hasActiveProjectLock()).isFalse();
            VBox status = (VBox) get("lockStatusBox");
            assertThat(status.getChildren().getFirst()).isInstanceOf(SVGPath.class);
            assertThat(statusText()).isEqualTo(MESSAGES.PROJECT_READ_ONLY_STATUS);
            // Loading/list refresh must not re-enable project actions.
            invoke("setUILoadingState", new Class<?>[] {boolean.class}, true);
            invoke("setUILoadingState", new Class<?>[] {boolean.class}, false);
        });
        onFx(() -> assertProjectPane(project, true));
        if (permission.equals("READ_ONLY")) {
            verify(service, never()).acquireOrRefreshProjectMutex(any());
        } else {
            verify(service).acquireOrRefreshProjectMutex(project);
        }
    }

    @Test
    @DisplayName("Creating an empty project opens editable actions without redraw or centering requests")
    void newProject() throws Exception {
        open(project("READ_ONLY"));
        JsonObject created = project("ADMIN");
        when(service.createProject(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(created);
        when(service.acquireOrRefreshProjectMutex(created)).thenReturn(true);
        onFx(() -> {
            pane("projectsListingPane").setExpanded(true);
            ((TextField) get("uploadMessageTextField")).setText("previous message");
            try (var dialogs = mockConstruction(NewProjectDialog.class, (dialog, context) ->
                    when(dialog.showAndWait()).thenReturn(Optional.of(
                        new NewProjectDialog.ProjectData("New cave", "Description", "US", "", ""))))) {
                controller.onCreateNewProject(null);
            }
        });
        runWorker(); // Create and acquire the lock; queue template loading.
        onFx(() -> assertThat(pane("projectActionsPane").isDisabled()).isTrue());
        finishLoad();
        onFx(() -> {
            assertProjectPane(created, false);
            assertThat(controller.hasActiveProjectLock()).isTrue();
            assertThat(((TextField) get("uploadMessageTextField")).getText()).isEmpty();
            assertThat(((VBox) get("lockStatusBox")).getChildren().getFirst()).isInstanceOf(ImageView.class);
            assertThat(statusText()).isEqualTo(MESSAGES.PROJECT_EDITING_STATUS);
        });
        awaitAnimations(TIMINGS.REDRAW_DELAY_MILLIS + TIMINGS.REDRAW_DELAY_MILLIS_2
                + TIMINGS.CENTER_VIEW_DELAY_MILLIS + 300);
        assertThat(redrawRequests.get()).isZero();
        assertThat(centerRequests.get()).isZero();
        verify(service).createEmptyTmlFileFromTemplate(created.getString("id"), "New cave");
    }

    @Test
    @DisplayName("Opening a writable project restores controls and editing status after read-only")
    void writableAfterReadOnly() throws Exception {
        open(project("READ_ONLY"));
        JsonObject writable = project("READ_AND_WRITE");
        when(service.acquireOrRefreshProjectMutex(writable)).thenReturn(true);
        open(writable);
        onFx(() -> {
            assertProjectPane(writable, false);
            assertThat(statusText()).isEqualTo(MESSAGES.PROJECT_EDITING_STATUS);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"populated", "empty", "nullSurvey", "nullData", "emptiedDuringDelay"})
    @DisplayName("Automatic centering runs only when the current survey has data at dispatch time")
    void centerOnlyWithData(String mode) throws Exception {
        CaveSurveyInterface data = emptySurvey();
        if (mode.equals("populated") || mode.equals("emptiedDuringDelay")) {
            when(data.getSurveyDataInterface()).thenReturn(new ArrayList<>(List.of(mock(SurveyDataInterface.class))));
        } else if (mode.equals("nullData")) {
            when(data.getSurveyDataInterface()).thenReturn(null);
        }
        survey.set(mode.equals("nullSurvey") ? null : data);
        onFx(() -> {
            invoke("centerMapViewerAsync", new Class<?>[0]);
            if (mode.equals("emptiedDuringDelay")) survey.set(emptySurvey());
        });
        awaitAnimations(TIMINGS.CENTER_VIEW_DELAY_MILLIS + 200);
        assertThat(centerRequests.get()).isEqualTo(mode.equals("populated") ? 1 : 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"populated", "empty", "nullSurvey", "nullData", "emptiedBeforeFirst", "emptiedBeforeSecond"})
    @DisplayName("Each delayed redraw checks for data before asking Ariane to render")
    void redrawOnlyWithData(String mode) throws Exception {
        CaveSurveyInterface data = emptySurvey();
        if (mode.equals("populated") || mode.startsWith("emptiedBefore")) {
            when(data.getSurveyDataInterface()).thenReturn(new ArrayList<>(List.of(mock(SurveyDataInterface.class))));
        } else if (mode.equals("nullData")) {
            when(data.getSurveyDataInterface()).thenReturn(null);
        }
        survey.set(mode.equals("nullSurvey") ? null : data);
        onFx(() -> {
            if (mode.equals("emptiedBeforeSecond")) {
                plugin.getCommandProperty().addListener((property, before, after) -> {
                    // The host acknowledges REDRAW by setting DONE inside its listener.
                    if (redrawRequests.get() == 1) {
                        survey.set(emptySurvey());
                    }
                });
            }
            invoke("scheduleRedrawAfterMillis", new Class<?>[] {long.class, boolean.class}, 1L, true);
            if (mode.equals("emptiedBeforeFirst")) survey.set(emptySurvey());
        });
        awaitAnimations(TIMINGS.REDRAW_DELAY_MILLIS_2 + TIMINGS.CENTER_VIEW_DELAY_MILLIS + 300);
        assertThat(redrawRequests.get()).isEqualTo(switch (mode) {
            case "populated" -> 2;
            case "emptiedBeforeSecond" -> 1;
            default -> 0;
        });
        assertThat(centerRequests.get()).isEqualTo(mode.equals("populated") ? 2 : 0);
    }

    private void open(JsonObject project) throws Exception {
        onFx(() -> {
            Button card = new Button();
            card.setUserData(project);
            invoke("clickSpeleoDBProject", new Class<?>[] {ActionEvent.class}, new ActionEvent(card, card));
        });
        runWorker(); // Lock attempt and download.
        finishLoad();
    }

    private void finishLoad() throws Exception {
        runWorker(); // Host LOAD, then successful completion on FX.
        onFx(() -> { });
        runWorker(); // Metadata and list refresh scheduling.
        runWorker(); // Actual list refresh.
        onFx(() -> { });
        onFx(() -> { }); // Drain loading-state update queued by completion.
    }

    private void runWorker() throws Exception {
        Runnable worker = workers.poll(5, TimeUnit.SECONDS);
        assertThat(worker).as("Expected background work").isNotNull();
        worker.run();
    }

    private void assertProjectPane(JsonObject project, boolean disabled) throws Exception {
        TitledPane actions = pane("projectActionsPane");
        assertThat(actions.isVisible()).isTrue();
        assertThat(actions.isExpanded()).isTrue();
        assertThat(actions.isDisabled()).isFalse();
        assertThat(actions.getText()).contains(project.getString("name"));
        assertThat(pane("projectsListingPane").isExpanded()).isFalse();
        for (String field : List.of("uploadButton", "importFromDiskButton", "reloadProjectButton", "uploadMessageTextField")) {
            assertThat(((javafx.scene.Node) get(field)).isDisabled()).as(field).isEqualTo(disabled);
        }
        assertThat(((VBox) get("lockStatusBox")).isDisabled()).isFalse();
    }

    private String statusText() throws Exception {
        TextFlow text = (TextFlow) ((VBox) get("lockStatusBox")).getChildren().get(1);
        return ((Text) text.getChildren().getFirst()).getText();
    }

    private static CaveSurveyInterface emptySurvey() {
        CaveSurveyInterface empty = mock(CaveSurveyInterface.class);
        when(empty.getSurveyDataInterface()).thenReturn(new ArrayList<>());
        return empty;
    }

    private static JsonObject project(String permission) {
        return Json.createObjectBuilder().add("id", permission).add("name", permission + " cave")
                .add("permission", permission).addNull("active_mutex").build();
    }

    private TitledPane pane(String name) throws Exception { return (TitledPane) get(name); }

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

    private void invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = SpeleoDBController.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(controller, args);
    }

    private static void awaitAnimations(int millis) throws Exception {
        CountDownLatch elapsed = new CountDownLatch(1);
        onFx(() -> {
            PauseTransition wait = new PauseTransition(Duration.millis(millis));
            wait.setOnFinished(event -> elapsed.countDown());
            wait.play();
        });
        assertThat(elapsed.await(10, TimeUnit.SECONDS)).isTrue();
    }

    private static void onFx(FxAction action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { action.run(); return null; });
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }

    @FunctionalInterface
    private interface FxAction { void run() throws Exception; }
}
