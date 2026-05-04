package org.speleodb.ariane.plugin.speleodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;

/**
 * Tests for Import from Local Disk flow focusing on ordering:
 * - Project must be loaded BEFORE uploading
 * - On load failure, project must NOT be uploaded
 *
 * <p>Disabled on CI ({@code CI=true}) because every test in this class
 * instantiates JavaFX controls ({@code new ProgressIndicator()}, {@code new Button()},
 * etc.) inside {@code @BeforeEach}. Loading any {@code javafx.scene.control.*}
 * class triggers {@code Control.<clinit>} which calls
 * {@code Platform.runLater} which blocks indefinitely on a {@code CountDownLatch}
 * waiting for the FX toolkit to start -- a toolkit that cannot start on a
 * headless Linux runner without DISPLAY/Monocle. Per-method {@code assumeTrue}
 * guards do NOT help here because {@code @BeforeEach} fires before the test body.
 */
@DisabledIfEnvironmentVariable(named = "CI", matches = "true",
        disabledReason = "Headless CI cannot bootstrap JavaFX toolkit; @BeforeEach instantiates JavaFX controls")
class SpeleoDBImportFlowTest {

    @BeforeAll
    static void initFX() throws InterruptedException {
        // Best-effort FX bootstrap for hosts where the toolkit IS available
        // (developer macOS/Windows). Failures are non-fatal -- the class-level
        // CI guard handles the headless case.
        try {
            if (!Platform.isFxApplicationThread()) {
                Platform.startup(() -> {});
                Thread.sleep(100);
            }
        } catch (IllegalStateException e) {
            if (!e.getMessage().contains("Toolkit already initialized")) {
                throw e;
            }
        } catch (UnsupportedOperationException ignored) {
            // No DISPLAY available; the class is already disabled on CI via the
            // class-level annotation, so this should only happen on dev machines
            // without a display (e.g., remote SSH session).
        }
    }

    private SpeleoDBController controller;
    private SpeleoDBPlugin testPlugin;
    private ProgressIndicator progress;
    private Button uploadBtn;

    @BeforeEach
    void setup() throws Exception {
        // Use a fresh controller instance via protected constructor for isolation
        controller = new SpeleoDBController(true);

        // Stub UI nodes to prevent NPEs when setUILoadingState runs
        progress = new ProgressIndicator();
        uploadBtn = new Button();

        setPrivateField(controller, "serverProgressIndicator", progress);
        setPrivateField(controller, "uploadButton", uploadBtn);
        setPrivateField(controller, "uploadMessageTextField", new javafx.scene.control.TextField());
        setPrivateField(controller, "projectListView", new javafx.scene.control.ListView<>());
        setPrivateField(controller, "createNewProjectButton", new Button());
        setPrivateField(controller, "refreshProjectsButton", new Button());
        setPrivateField(controller, "sortByNameButton", new Button());
        setPrivateField(controller, "sortByDateButton", new Button());
        setPrivateField(controller, "projectsListingPane", new javafx.scene.control.TitledPane());
        setPrivateField(controller, "projectActionsPane", new javafx.scene.control.TitledPane());

        // Count down the FXML latch so setUILoadingState doesn't block for 10 seconds
        java.util.concurrent.CountDownLatch fxmlLatch =
                (java.util.concurrent.CountDownLatch) getPrivateField(controller, "fxmlInitializedLatch");
        fxmlLatch.countDown();

        // Assign a test plugin and ensure LOAD events set a dummy survey in tests
        testPlugin = new SpeleoDBPlugin();
        controller.parentPlugin = testPlugin;

        // Set a current project
        JsonObject project = Json.createObjectBuilder()
            .add("id", "test-project-import")
            .add("name", "Import Flow Project")
            .add("permission", "ADMIN")
            .build();
        setPrivateField(controller, "currentProject", project);
    }

    @Test
    @DisplayName("Should load project before uploading on successful import")
    void shouldLoadBeforeUploadOnSuccess() throws Exception {
        AtomicInteger stage = new AtomicInteger(0); // 0 = start, 1 = loaded, 2 = uploaded
        CountDownLatch uploadLatch = new CountDownLatch(1);

        SpeleoDBService fakeService = new SpeleoDBService(controller) {
            @Override
            public void uploadProject(String message, jakarta.json.JsonObject project) {
                stage.compareAndSet(1, 2);
                uploadLatch.countDown();
            }
        };
        setPrivateField(controller, "speleoDBService", fakeService);

        SpeleoDBPlugin orderingPlugin = new SpeleoDBPlugin();
        orderingPlugin.getCommandProperty().addListener((obs, o, n) -> {
            if (n != null && n.equals(com.arianesline.ariane.plugin.api.DataServerCommands.LOAD.name())) {
                stage.compareAndSet(0, 1);
                orderingPlugin.setSurvey(new com.arianesline.cavelib.api.CaveSurveyInterface() {
                    @Override public String getExtraData() { return ""; }
                    @Override public void setExtraData(String data) {}
                    @Override public java.util.ArrayList<com.arianesline.cavelib.api.SurveyDataInterface> getSurveyDataInterface() { return new java.util.ArrayList<>(); }
                    @Override public String getDescription() { return ""; }
                    @Override public String getGeoCoding() { return ""; }
                    @Override public String getCaveName() { return ""; }
                    @Override public String getUnit() { return ""; }
                    @Override public void setUnit(String unit) {}
                    @Override public Boolean getUseMagneticAzimuth() { return false; }
                    @Override public int addStation(String sectionname, String explorer, java.time.LocalDate datetime, String nomstation,
                                              double direction, double length, double depthin, double depth, int fromid, int toid, String type, javafx.scene.paint.Color color,
                                              double longitude, double latitude,
                                              double up, double down, double left, double right, String comment, String profiletype, boolean islocked) { return 0; }
                });
            }
        });
        controller.parentPlugin = orderingPlugin;

        File tempTml = createValidTmlZip("import-order");

        Method method = SpeleoDBController.class.getDeclaredMethod("performImportUploadAndLoad", File.class, String.class);
        method.setAccessible(true);
        method.invoke(controller, tempTml, "test message");

        assertThat(uploadLatch.await(10, java.util.concurrent.TimeUnit.SECONDS))
            .as("Upload should complete within timeout")
            .isTrue();
        assertThat(stage.get())
            .as("Should follow order: start(0) -> loaded(1) -> uploaded(2)")
            .isEqualTo(2);
    }

    @Test
    @DisplayName("Should not upload project when load fails")
    void shouldNotUploadWhenLoadFails() throws Exception {
        AtomicInteger uploadCallCount = new AtomicInteger(0);

        SpeleoDBService trackingService = new SpeleoDBService(controller) {
            @Override
            public void uploadProject(String message, jakarta.json.JsonObject project) {
                uploadCallCount.incrementAndGet();
            }
        };
        setPrivateField(controller, "speleoDBService", trackingService);

        SpeleoDBPlugin failPlugin = new SpeleoDBPlugin();
        failPlugin.getCommandProperty().addListener((obs, o, n) -> {
            if (n != null && n.equals(com.arianesline.ariane.plugin.api.DataServerCommands.LOAD.name())) {
                // Interrupt the executor thread to make loadSurveyAsync fail quickly
                // instead of waiting for the full 10-second timeout.
                failPlugin.executorService.shutdownNow();
            }
        });
        controller.parentPlugin = failPlugin;

        File tempTml = createValidTmlZip("import-fail");

        Method method = SpeleoDBController.class.getDeclaredMethod("performImportUploadAndLoad", File.class, String.class);
        method.setAccessible(true);
        method.invoke(controller, tempTml, "msg");

        // Allow async error path to complete
        Thread.sleep(1000);
        assertThat(uploadCallCount.get())
            .as("Upload should not be called when load fails")
            .isZero();
    }

    private static File createValidTmlZip(String prefix) throws Exception {
        File tempTml = File.createTempFile(prefix, ".tml");
        tempTml.deleteOnExit();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempTml))) {
            zos.putNextEntry(new ZipEntry("survey.xml"));
            zos.write("<tml/>".getBytes());
            zos.closeEntry();
        }
        return tempTml;
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object getPrivateField(Object target, String fieldName) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(target);
    }
}
