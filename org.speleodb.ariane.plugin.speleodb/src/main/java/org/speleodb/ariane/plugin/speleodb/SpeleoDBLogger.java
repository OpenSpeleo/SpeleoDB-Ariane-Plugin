package org.speleodb.ariane.plugin.speleodb;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.LOGGING.LOG_DATE_FORMAT;
import static org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.LOGGING.LOG_DIR;
import static org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.LOGGING.LOG_ERROR_CREATING_DIR;
import static org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.LOGGING.LOG_ERROR_ROTATING;
import static org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.LOGGING.LOG_ERROR_WRITING;
import static org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.LOGGING.LOG_FILE_MAX_BACKUP_COUNT;
import static org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.LOGGING.LOG_FILE_MAX_SIZE_BYTES;
import static org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.LOGGING.LOG_FILE_MAX_SIZE_MB;
import static org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.LOGGING.LOG_FILE_PATH;
import static org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.LOGGING.LOG_FILE_ROTATION_MESSAGE;
import static org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.LOGGING.LOG_FORMAT;
import static org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.LOGGING.LOG_LEVEL_DEBUG;
import static org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.LOGGING.LOG_LEVEL_ERROR;
import static org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.LOGGING.LOG_LEVEL_INFO;
import static org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.LOGGING.LOG_LEVEL_WARN;
import static org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.LOGGING.LOG_SHUTDOWN_MESSAGE;
import static org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.LOGGING.LOG_STARTUP_MESSAGE;

import javafx.application.Platform;

/**
 * Centralized logging system for the SpeleoDB plugin.
 *
 * Features:
 * - Thread-safe file logging with automatic rotation
 * - Multiple log levels (DEBUG, INFO, WARN, ERROR)
 * - UI console integration with level filtering
 * - File logging: stores ALL levels (DEBUG, INFO, WARN, ERROR)
 * - Console logging: shows only >= INFO (INFO, WARN, ERROR) - DEBUG is hidden
 * - Graceful fallback to console if file logging fails
 * - Automatic log directory creation
 * - Clean shutdown handling
 *
 * Log files are saved to: ~/.ariane/logs/speleodb-plugin.log
 */
public final class SpeleoDBLogger {

    private static SpeleoDBLogger instance;
    private static final Object instanceLock = new Object();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(LOG_DATE_FORMAT);
    private final AtomicInteger messageCounter = new AtomicInteger(0);

    private PrintWriter logWriter;
    private boolean initialized = false;
    private boolean shutdownCalled = false;

    // UI Console integration
    private SpeleoDBController uiController = null;

    /**
     * Private constructor for singleton pattern
     */
    private SpeleoDBLogger() {
        initialize();
    }

    /**
     * Gets the singleton logger instance
     */
    public static SpeleoDBLogger getInstance() {
        synchronized (instanceLock) {
            if (instance == null) {
                instance = new SpeleoDBLogger();
            }
        }
        return instance;
    }

    /**
     * Sets the UI controller for console logging integration.
     * This allows the logger to display messages in the UI console.
     *
     * @param controller the SpeleoDBController instance for UI integration
     */
    public void setUIController(SpeleoDBController controller) {
        this.uiController = controller;
    }

    /**
     * Safely disconnects the UI controller without shutting down file logging.
     * This should be called when the UI is being destroyed but the logger should continue.
     */
    public void disconnectUIController() {
        this.uiController = null;
    }

    // ==================== PUBLIC LOGGING API ====================

    /**
     * Logs a DEBUG message - goes to file only (not shown in UI console)
     */
    public void debug(String message) {
        log(LOG_LEVEL_DEBUG, message, false); // DEBUG not shown in UI
    }

    /**
     * Logs an INFO message - goes to both file and UI console
     */
    public void info(String message) {
        log(LOG_LEVEL_INFO, message, true); // INFO shown in UI
    }

    /**
     * Logs a WARNING message - goes to both file and UI console
     */
    public void warn(String message) {
        log(LOG_LEVEL_WARN, message, true); // WARN shown in UI
    }

    /**
     * Logs an ERROR message - goes to both file and UI console
     */
    public void error(String message) {
        log(LOG_LEVEL_ERROR, message, true); // ERROR shown in UI
    }

    /**
     * Logs an ERROR message with exception details - goes to both file and UI console
     */
    public void error(String message, Throwable throwable) {
        StringBuilder sb = new StringBuilder(message);
        sb.append(" - Exception: ").append(throwable.getClass().getSimpleName());
        sb.append(": ").append(throwable.getMessage());

        // Add stack trace for file logging (detailed)
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        String fileMessage = sb.toString() + "\nStack trace:\n" + sw.toString();

        // Shorter message for UI console
        String uiMessage = message + " - " + throwable.getMessage();

        // Log full details to file
        log(LOG_LEVEL_ERROR, fileMessage, false);

        // Log shorter message to UI console
        logToUIConsole("ERROR: " + uiMessage);
    }

    // ==================== INTERNAL LOGGING SYSTEM ====================

    /**
     * Initializes the logging system
     */
    private void initialize() {
        lock.writeLock().lock();
        try {
            if (initialized) return;

            // Create log directory if it doesn't exist
            createLogDirectory();

            // Initialize log file writer
            initializeLogWriter();

            initialized = true;

            // Log startup message
            info(LOG_STARTUP_MESSAGE + LOG_FILE_PATH);

        } catch (Exception e) {
            System.err.println(LOG_ERROR_WRITING + e.getMessage());
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Creates the log directory if it doesn't exist
     */
    private void createLogDirectory() {
        try {
            Path logDir = Paths.get(LOG_DIR);
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }
        } catch (IOException e) {
            System.err.println(LOG_ERROR_CREATING_DIR + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Initializes the log file writer
     */
    private void initializeLogWriter() {
        try {
            logWriter = new PrintWriter(new FileWriter(LOG_FILE_PATH, true));
        } catch (IOException e) {
            System.err.println(LOG_ERROR_WRITING + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Core logging method that handles thread safety, file rotation, and UI integration
     *
     * @param level the log level
     * @param message the message to log
     * @param showInUI whether to show this message in the UI console
     */
    private void log(String level, String message, boolean showInUI) {
        if (shutdownCalled) {
            // Fallback to console if shutdown was called
            System.out.println(formatLogMessage(level, message));
            return;
        }

        lock.writeLock().lock();
        try {
            if (!initialized) {
                initialize();
            }

            // Check if log rotation is needed
            checkAndRotateLog();

            // Format and write to file (ALL levels go to file)
            String formattedMessage = formatLogMessage(level, message);

            if (logWriter != null) {
                logWriter.print(formattedMessage);
                logWriter.flush(); // Ensure immediate write
            } else {
                // Fallback to console
                System.out.print(formattedMessage);
            }

            // Log to UI console if requested (only INFO and above)
            if (showInUI) {
                String uiMessage = level.equals(LOG_LEVEL_INFO) ? message : level + ": " + message;
                logToUIConsole(uiMessage);
            }

        } catch (Exception e) {
            // Fallback to console if file logging fails
            System.err.println(LOG_ERROR_WRITING + e.getMessage());
            System.out.print(formatLogMessage(level, message));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Logs a message to the UI console (if controller is available)
     * This handles the UI display part of logging.
     */
    private void logToUIConsole(String message) {
        if (uiController != null && !shutdownCalled) {
            final int messageIndex = messageCounter.incrementAndGet();
            final SpeleoDBController controller = uiController; // Capture reference to avoid race condition

            // Log to UI (must be on JavaFX Application Thread)
            Platform.runLater(() -> {
                // Double-check controller is still valid when the runLater executes
                if (controller != null && !shutdownCalled) {
                    try {
                        controller.appendToUILog(messageIndex + "-" + message + System.lineSeparator());
                    } catch (Exception e) {
                        // UI controller may have been deallocated during shutdown - fail silently
                        // Don't log this error to avoid infinite recursion
                    }
                }
            });
        }
    }

    /**
     * Formats a log message with timestamp and level
     */
    private String formatLogMessage(String level, String message) {
        String timestamp = LocalDateTime.now().format(dateFormatter);
        return String.format(LOG_FORMAT, timestamp, level, message);
    }

    /**
     * Checks if log rotation is needed and performs it
     */
    private void checkAndRotateLog() {
        try {
            Path logFile = Paths.get(LOG_FILE_PATH);
            if (Files.exists(logFile) && Files.size(logFile) > LOG_FILE_MAX_SIZE_BYTES) {
                rotateLog();
            }
        } catch (IOException e) {
            System.err.println(LOG_ERROR_ROTATING + e.getMessage());
        }
    }

    /**
     * Rotates the log file when it gets too large
     */
    private void rotateLog() {
        try {
            // Close current writer
            if (logWriter != null) {
                logWriter.close();
            }

            // Rotate existing backup files
            for (int i = LOG_FILE_MAX_BACKUP_COUNT - 1; i >= 1; i--) {
                Path oldFile = Paths.get(LOG_FILE_PATH + "." + i);
                Path newFile = Paths.get(LOG_FILE_PATH + "." + (i + 1));
                if (Files.exists(oldFile)) {
                    Files.move(oldFile, newFile, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            // Move current log to .1
            Path currentLog = Paths.get(LOG_FILE_PATH);
            Path backupLog = Paths.get(LOG_FILE_PATH + ".1");
            if (Files.exists(currentLog)) {
                Files.move(currentLog, backupLog, StandardCopyOption.REPLACE_EXISTING);
            }

            // Create new log writer
            initializeLogWriter();

            info(LOG_FILE_ROTATION_MESSAGE);

        } catch (IOException e) {
            System.err.println(LOG_ERROR_ROTATING + e.getMessage());
            // Try to reinitialize writer anyway
            try {
                initializeLogWriter();
            } catch (Exception ex) {
                System.err.println("Failed to reinitialize log writer: " + ex.getMessage());
            }
        }
    }

    // ==================== DIAGNOSTIC AND UTILITY METHODS ====================

    /**
     * Gets the current log file path for external access
     */
    public String getLogFilePath() {
        return LOG_FILE_PATH;
    }

    /**
     * Gets the log directory path
     */
    public String getLogDirectory() {
        return LOG_DIR;
    }

    /**
     * Checks if the logger is properly initialized
     */
    public boolean isInitialized() {
        lock.readLock().lock();
        try {
            return initialized;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets comprehensive diagnostic information about the logging system
     */
    public String getDiagnostics() {
        lock.readLock().lock();
        try {
            StringBuilder diagnostics = new StringBuilder();
            diagnostics.append("SpeleoDB Centralized Logger Diagnostics:\n");
            diagnostics.append("- Initialized: ").append(initialized).append("\n");
            diagnostics.append("- Shutdown called: ").append(shutdownCalled).append("\n");
            diagnostics.append("- UI Controller: ").append(uiController != null ? "Connected" : "NULL").append("\n");
            diagnostics.append("- Message Counter: ").append(messageCounter.get()).append("\n");
            diagnostics.append("- Log directory: ").append(LOG_DIR).append("\n");
            diagnostics.append("- Log file: ").append(LOG_FILE_PATH).append("\n");
            diagnostics.append("- Max file size: ").append(LOG_FILE_MAX_SIZE_MB).append(" MB\n");
            diagnostics.append("- Max backup files: ").append(LOG_FILE_MAX_BACKUP_COUNT).append("\n");
            diagnostics.append("- File logging: ALL levels (DEBUG, INFO, WARN, ERROR)\n");
            diagnostics.append("- UI console: INFO and above only (DEBUG hidden)\n");

            try {
                Path logFile = Paths.get(LOG_FILE_PATH);
                if (Files.exists(logFile)) {
                    long sizeBytes = Files.size(logFile);
                    double sizeMB = sizeBytes / (1024.0 * 1024.0);
                    diagnostics.append("- Current file size: ").append(String.format("%.2f MB", sizeMB)).append("\n");
                } else {
                    diagnostics.append("- Current file size: File does not exist\n");
                }
            } catch (IOException e) {
                diagnostics.append("- Current file size: Error reading file size\n");
            }

            return diagnostics.toString();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Shuts down the logging system gracefully
     */
    public void shutdown() {
        lock.writeLock().lock();
        try {
            if (shutdownCalled) return;

            // Clear UI controller reference FIRST to prevent new UI logging attempts
            uiController = null;

            // Write shutdown message to file BEFORE setting the flag so it goes to the log file
            if (logWriter != null) {
                String formattedMessage = formatLogMessage(LOG_LEVEL_INFO, LOG_SHUTDOWN_MESSAGE);
                logWriter.print(formattedMessage);
                logWriter.flush();
            }

            shutdownCalled = true;

            if (logWriter != null) {
                logWriter.close();
                logWriter = null;
            }

        } finally {
            lock.writeLock().unlock();
        }
    }
}
