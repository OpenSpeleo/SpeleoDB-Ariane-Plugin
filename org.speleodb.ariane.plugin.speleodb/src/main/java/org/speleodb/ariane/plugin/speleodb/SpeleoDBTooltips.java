package org.speleodb.ariane.plugin.speleodb;

import java.util.ArrayList;
import java.util.List;

import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.DIMENSIONS;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.ICONS;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.MESSAGES;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.STYLES;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Centralized tooltip management for SpeleoDB plugin with Material Design styling.
 * Provides consistent success and error tooltips throughout the application.
 *
 * Tooltips are displayed at a fixed position in the middle upper part of the application,
 * completely decoupled from any parent panes.
 */
public class SpeleoDBTooltips {

    private static final SpeleoDBLogger logger = SpeleoDBLogger.getInstance();

    // List to track running animations for cleanup
    private static final List<Timeline> runningAnimations = new ArrayList<>();

    // Global references for the tooltip system
    private static Window mainWindow;
    private static boolean initialized = false;

    /**
     * Tooltip type enumeration with styling configuration
     */
    public enum TooltipType {
        SUCCESS(ICONS.SUCCESS_CHECKMARK, "tooltip-success",
                MESSAGES.SUCCESS_DEFAULT, DIMENSIONS.SUCCESS_LABEL_MIN_WIDTH,
                DIMENSIONS.SUCCESS_LABEL_MAX_WIDTH, 4.0, 400, false),

        ERROR(ICONS.ERROR_X, "tooltip-error",
              MESSAGES.ERROR_DEFAULT, DIMENSIONS.ERROR_LABEL_MIN_WIDTH,
              DIMENSIONS.ERROR_LABEL_MAX_WIDTH, 5.0, 500, false),

        INFO("ℹ️ ", "tooltip-info",
             "Info", 150, 400, 4.0, 400, true),

        WARNING("⚠️ ", "tooltip-warning",
                "Warning", 150, 400, 4.0, 400, true);

        final String icon;
        final String cssClass;
        final String defaultMessage;
        final double minWidth;
        final double maxWidth;
        final double displayDuration;
        final int fadeOutDuration;
        final boolean fadeIn;

        TooltipType(String icon, String cssClass, String defaultMessage,
                   double minWidth, double maxWidth, double displayDuration,
                   int fadeOutDuration, boolean fadeIn) {
            this.icon = icon;
            this.cssClass = cssClass;
            this.defaultMessage = defaultMessage;
            this.minWidth = minWidth;
            this.maxWidth = maxWidth;
            this.displayDuration = displayDuration;
            this.fadeOutDuration = fadeOutDuration;
            this.fadeIn = fadeIn;
        }
    }

    /**
     * Initializes the tooltip system with the main application stage.
     * This method must be called once during application startup.
     *
     * @param stage the main application stage
     */
    public static void initialize(Stage stage) {
        if (initialized) {
            logger.warn("Tooltip system already initialized");
            return;
        }

        if (stage == null) {
            logger.error("Cannot initialize tooltip system - stage is null");
            return;
        }

        mainWindow = stage;
        initialized = true;
        logger.info("Tooltip system initialized successfully with stage");
    }

    /**
     * Initializes the tooltip system with a scene.
     * Alternative initialization method when stage is not directly available.
     *
     * @param scene the main application scene
     */
    public static void initialize(Scene scene) {
        if (initialized) {
            logger.warn("Tooltip system already initialized");
            return;
        }

        if (scene == null || scene.getWindow() == null) {
            logger.error("Cannot initialize tooltip system - invalid scene");
            return;
        }

        mainWindow = scene.getWindow();
        initialized = true;
    }

    /**
     * Shows a success tooltip with customizable message.
     *
     * @param message the success message to display (optional, defaults to "Success")
     */
    public static void showSuccess(String message) {
        show(message, TooltipType.SUCCESS);
    }

    /**
     * Shows a success tooltip with the default "Success" message.
     */
    public static void showSuccess() {
        showSuccess(null);
    }

    /**
     * Shows an error tooltip with customizable message.
     *
     * @param message the error message to display (optional, defaults to "ERROR")
     */
    public static void showError(String message) {
        show(message, TooltipType.ERROR);
    }

    /**
     * Shows an error tooltip with the default "ERROR" message.
     */
    public static void showError() {
        showError(null);
    }

    /**
     * Shows an info tooltip with customizable message.
     *
     * @param message the info message to display
     */
    public static void showInfo(String message) {
        show(message, TooltipType.INFO);
    }

    /**
     * Shows a warning tooltip with customizable message.
     *
     * @param message the warning message to display
     */
    public static void showWarning(String message) {
        show(message, TooltipType.WARNING);
    }

    /**
     * Core method to display tooltips with Material Design styling and animations.
     * Uses Popup to ensure tooltips appear on top of everything.
     *
     * @param message the message to display (null uses default)
     * @param type the type of tooltip
     */
    private static void show(String message, TooltipType type) {
        // Ensure we're on JavaFX Application Thread
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(message, type));
            return;
        }

        // Check if system is initialized
        if (!initialized || mainWindow == null) {
            logger.error("Tooltip system not initialized. Call SpeleoDBTooltips.initialize() first.");
            return;
        }

        try {
            // Use default message if none provided
            String displayMessage = (message == null || message.trim().isEmpty()) ?
                                  type.defaultMessage : message;

            // Create tooltip popup
            Popup tooltipPopup = new Popup();
            tooltipPopup.setAutoHide(false);
            tooltipPopup.setHideOnEscape(false);

            // Create tooltip label with Material Design styling
            Label tooltipLabel = new Label(type.icon + displayMessage);
            tooltipLabel.getStyleClass().addAll("tooltip-base", type.cssClass);
            tooltipLabel.setWrapText(true);
            tooltipLabel.setAlignment(Pos.CENTER);

            // Set size constraints
            tooltipLabel.setMinWidth(type.minWidth);
            tooltipLabel.setMaxWidth(type.maxWidth);
            tooltipLabel.setPrefWidth(Label.USE_COMPUTED_SIZE);

            // Add label to popup
            tooltipPopup.getContent().add(tooltipLabel);

            // First show the popup at a temporary position to get its actual size
            double tempX = mainWindow.getX();
            double y = mainWindow.getY() + 50; // 50px from top of window

            // Show the popup to render it
            tooltipPopup.show(mainWindow, tempX, y);

            // Load CSS if not already loaded
            if (tooltipLabel.getScene() != null && tooltipLabel.getScene().getStylesheets().isEmpty()) {
                tooltipLabel.getScene().getStylesheets().add(
                    SpeleoDBTooltips.class.getResource(STYLES.MAIN_CSS_PATH).toExternalForm()
                );
            }

            // Set initial opacity
            tooltipPopup.setOpacity(type.fadeIn ? 0.0 : 1.0);

            // Now get the actual width and reposition to center
            Platform.runLater(() -> {
                double actualWidth = tooltipLabel.getWidth();
                if (actualWidth <= 0) {
                    // Fallback to computed width if not yet rendered
                    actualWidth = tooltipLabel.prefWidth(-1);
                }
                if (actualWidth <= 0) {
                    // Last resort fallback
                    actualWidth = type.minWidth;
                }

                // Calculate centered position with actual width
                double centeredX = mainWindow.getX() + (mainWindow.getWidth() - actualWidth) / 2;

                // Reposition to center
                tooltipPopup.setX(centeredX);
                tooltipPopup.setY(y);

                // Animate fade-in if enabled
                if (type.fadeIn) {
                    FadeTransition fadeIn = new FadeTransition(Duration.millis(400), tooltipLabel);
                    fadeIn.setFromValue(0.0);
                    fadeIn.setToValue(1.0);
                    fadeIn.play();
                }
            });

            // Create auto-hide timeline
            Timeline hideTimeline = new Timeline(
                new KeyFrame(Duration.seconds(type.displayDuration), e -> {
                    if (type.fadeOutDuration > 0) {
                        // Fade out the label directly
                        FadeTransition fadeOut = new FadeTransition(
                            Duration.millis(type.fadeOutDuration), tooltipLabel);
                        fadeOut.setFromValue(1.0);
                        fadeOut.setToValue(0.0);
                        fadeOut.setOnFinished(event -> {
                            tooltipPopup.hide();
                        });
                        fadeOut.play();
                    } else {
                        tooltipPopup.hide();
                    }
                })
            );

            // Track animation for cleanup
            runningAnimations.add(hideTimeline);
            hideTimeline.setOnFinished(e -> runningAnimations.remove(hideTimeline));

            hideTimeline.play();

        } catch (Exception e) {
            logger.error("Failed to show " + type.name().toLowerCase() + " tooltip", e);
        }
    }

    /**
     * Cleans up all running animations and resets the tooltip system.
     * Should be called during application shutdown.
     */
    public static void cleanup() {
        for (Timeline timeline : new ArrayList<>(runningAnimations)) {
            timeline.stop();
        }
        runningAnimations.clear();

        mainWindow = null;
        initialized = false;
    }

    /**
     * Checks if the tooltip system is initialized.
     *
     * @return true if initialized, false otherwise
     */
    public static boolean isInitialized() {
        return initialized;
    }
}
