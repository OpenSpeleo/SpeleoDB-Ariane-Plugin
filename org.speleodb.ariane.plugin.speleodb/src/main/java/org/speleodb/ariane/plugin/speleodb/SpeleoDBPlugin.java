package org.speleodb.ariane.plugin.speleodb;


import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.DEBUG;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.NETWORK;

import com.arianesline.ariane.plugin.api.DataServerCommands;
import com.arianesline.ariane.plugin.api.DataServerPlugin;
import com.arianesline.ariane.plugin.api.PluginInterface;
import com.arianesline.ariane.plugin.api.PluginType;
import com.arianesline.cavelib.api.CaveSurveyInterface;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;


public class SpeleoDBPlugin implements DataServerPlugin {

    public static final int TIMEOUT = NETWORK.DEFAULT_TIMEOUT_MILLIS;
    private final StringProperty commandProperty = new SimpleStringProperty();
    private CaveSurveyInterface survey;
    private File surveyFile;
    private final AtomicBoolean lock = new AtomicBoolean(false);

    /* Executor Service for Background Tasks */
    public final ExecutorService executorService = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true); // Mark as daemon thread to allow JVM shutdown
        t.setName(DEBUG.SPELEODB_WORKER_THREAD_NAME);
        return t;
    });

    // ==================== CENTRALIZED LOGGING SYSTEM ====================

    /**
     * Centralized logger instance - used directly without wrapper methods
     */
    private static final SpeleoDBLogger logger = SpeleoDBLogger.getInstance();

    // ==================== PLUGIN IMPLEMENTATION ====================

    /**
     * Default constructor for SpeleoDBPlugin.
     */
    public SpeleoDBPlugin() {
    }

    @Override
    public synchronized File getSurveyFile() {
        return surveyFile;
    }

    @Override
    public synchronized void setSurveyFile(File file) {
        surveyFile = file;

    }

    @Override
    public StringProperty getCommandProperty() {
        return commandProperty;
    }

    @Override
    public synchronized CaveSurveyInterface getSurvey() {
        return survey;
    }

    @Override
    public synchronized void setSurvey(CaveSurveyInterface survey) {
        this.survey = survey;
        lock.set(false);
    }

    /**
     * Shows a confirmation dialog asking the user if they want to release the project lock before shutdown.
     *
     * @param projectName the name of the project that has an active lock
     * @return true if the user wants to release the lock, false otherwise
     */
    public boolean showShutdownConfirmation(String projectName) {
        // Deprecated behavior: always release without prompting. Keep method for compatibility.
        return true;
    }

    /**
     * Loads the FXML UI using the singleton controller instance.
     * This ensures the same controller is used whether called from showUI() or getUINode().
     */
    private Parent loadUIWithSharedController() {
        SpeleoDBController controller = SpeleoDBController.getInstance();
        controller.parentPlugin = this; // Set the parent plugin reference
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/SpeleoDB.fxml"));
        fxmlLoader.setController(controller);

        try {
            Parent root = fxmlLoader.load();

            return root;
        } catch (IOException e) {
            logger.error("Error loading FXML for SpeleoDB UI: " + e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void showUI() {
        Parent root = loadUIWithSharedController();
        if (root != null) {
            Stage stage = new Stage();
            stage.initStyle(StageStyle.DECORATED);
            stage.setScene(new Scene(root));
            stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/logo.png"))));
            stage.setTitle("SpeleoDB");
            // Attach global FX event logger to help discover control IDs/events
            // FX Event Logger (disabled by default) — enable via SpeleoDBConstants.DEBUG.ENABLE_FX_EVENT_LOGGER
            if (SpeleoDBConstants.DEBUG.ENABLE_FX_EVENT_LOGGER) {
                stage.getScene().addEventFilter(javafx.event.Event.ANY, evt -> {
                    try {
                        Object tgt = evt.getTarget();
                        Object src = evt.getSource();
                        String target = (tgt != null) ? tgt.getClass().getName() : "unknown";
                        String source = (src != null) ? src.getClass().getName() : "unknown";
                        String type = (evt.getEventType() != null) ? evt.getEventType().getName() : "unknown";
                        String nodeId = (tgt instanceof javafx.scene.Node) ? ((javafx.scene.Node) tgt).getId() : null;
                        String text = null;
                        if (tgt instanceof javafx.scene.control.Labeled) {
                            text = ((javafx.scene.control.Labeled) tgt).getText();
                        } else if (tgt instanceof javafx.scene.control.TextInputControl) {
                            text = ((javafx.scene.control.TextInputControl) tgt).getText();
                        }
                        logger.info("FX EVENT: type=" + type +
                                    ", target=" + target +
                                    (nodeId != null ? ", id=" + nodeId : "") +
                                    (text != null ? ", text=\"" + text + "\"" : "") +
                                    ", source=" + source);
                    } catch (Exception e) {
                        logger.debug("FX event logging error: " + e.getMessage());
                    }
                });
            }
            stage.show();
        }
    }

    @Override
    public Node getUINode() {
        return loadUIWithSharedController();
    }

    @Override
    public Image getIcon() {
        return new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/logo.png")));
    }

    @Override
    public void closeUI() {
        // Plugin cleanup during application shutdown
        // NOTE: No confirmation dialogs here - they're handled by the window close handler
        // This prevents app crashes during the JavaFX shutdown sequence

        SpeleoDBController controller = SpeleoDBController.getInstance();

        // Just log the current state, don't show any dialogs
        if (controller.hasActiveProjectLock()) {
            String projectName = controller.getCurrentProjectName();
            logger.info("Application shutting down with active lock on: " + projectName);
            logger.info("JVM shutdown hook will release the project lock.");
        }

		// Proactively perform shutdown cleanup to release locks even if JVM is not terminated yet.
		// This call is idempotent and will not conflict with the JVM shutdown hook.
		controller.performShutdownCleanup();

        // Cleanup resources to prevent shutdown hangs
        controller.cleanup();

        // Shutdown the executor service quickly during application shutdown
        try {
            executorService.shutdown(); // Disable new tasks from being submitted
            if (!executorService.awaitTermination(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                // Cancel currently executing tasks
                executorService.shutdownNow();
                // Wait briefly for tasks to respond to being cancelled
                if (!executorService.awaitTermination(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    logger.warn("SpeleoDB executor did not terminate cleanly");
                }
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            executorService.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }

        // Shutdown centralized logging system last
        logger.info("SpeleoDB Plugin shutting down");
        logger.shutdown();
    }


    public void saveSurvey() {
        // Programmatically trigger the host app's Save button if present (id="saveButton")
        try {
            SpeleoDBController controller = SpeleoDBController.getInstance();
            javafx.scene.Scene scene = (controller != null && controller.getSpeleoDBAnchorPane() != null) ?
                    controller.getSpeleoDBAnchorPane().getScene() : null;
            if (scene != null) {
                javafx.scene.Node node = scene.lookup("#saveButton");
                if (node instanceof javafx.scene.control.Button) {
                    ((javafx.scene.control.Button) node).fire();
                }
            }
        } catch (Exception e) {
            logger.debug("Host save button lookup/fire failed: " + e.getMessage());
        }

        // Also trigger host save via command property as a fallback
        commandProperty.set(DataServerCommands.SAVE.name());
    }

    @Override
    public PluginInterface getInterfaceType() {
        return PluginInterface.LEFT_TAB;
    }

    @Override
    public String getName() {
        return "SpeleoDB";
    }

    @Override
    public PluginType getType() {
        return PluginType.DATASERVER;
    }

    @Override
    public void showSettings() {

    }
}
