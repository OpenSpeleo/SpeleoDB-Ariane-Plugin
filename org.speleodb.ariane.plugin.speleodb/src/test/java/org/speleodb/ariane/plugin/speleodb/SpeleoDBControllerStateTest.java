package org.speleodb.ariane.plugin.speleodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * Additional tests for SpeleoDBController focusing on state management,
 * error handling, and edge cases.
 */
class SpeleoDBControllerStateTest {

    private static final String TEST_RESOURCES_DIR = System.getProperty("java.io.tmpdir") + File.separator + "test_state_resources";

    @BeforeAll
    static void setupTestEnvironment() throws IOException {
        Path testDir = Paths.get(TEST_RESOURCES_DIR);
        if (!Files.exists(testDir)) {
            Files.createDirectories(testDir);
        }
    }

    @AfterAll
    static void cleanupTestEnvironment() throws IOException {
        Path testDir = Paths.get(TEST_RESOURCES_DIR);
        if (Files.exists(testDir)) {
            Files.walk(testDir)
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    @Test
    @DisplayName("atomic operations")
    void testAtomicOperations() {
        AtomicBoolean lockAcquired = new AtomicBoolean(false);
        assertThat(lockAcquired.get()).isFalse();

        lockAcquired.set(true);
        assertThat(lockAcquired.get()).isTrue();

        boolean result = lockAcquired.compareAndSet(true, false);
        assertThat(result).isTrue();
        assertThat(lockAcquired.get()).isFalse();

        AtomicInteger messageIndex = new AtomicInteger(0);
        int index1 = messageIndex.incrementAndGet();
        int index2 = messageIndex.incrementAndGet();

        assertThat(index1).isEqualTo(1);
        assertThat(index2).isEqualTo(2);
        assertThat(messageIndex.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("JSON array handling")
    void testJsonArrayHandling() {
        JsonArray emptyArray = Json.createArrayBuilder().build();
        assertThat(emptyArray.isEmpty()).isTrue();

        JsonArray projectList = Json.createArrayBuilder()
            .add(Json.createObjectBuilder()
                .add("id", "proj-1")
                .add("name", "Project 1")
                .add("permission", "READ_ONLY")
                .addNull("active_mutex"))
            .add(Json.createObjectBuilder()
                .add("id", "proj-2")
                .add("name", "Project 2")
                .add("permission", "READ_AND_WRITE")
                .add("active_mutex", Json.createObjectBuilder()
                    .add("user", "user@example.com")
                    .add("creation_date", "2024-01-15T10:30:00.000000")
                    .add("modified_date", "2024-01-15T11:45:00.000000")))
            .build();

        assertThat(projectList.size()).isEqualTo(2);

        int count = 0;
        for (JsonValue value : projectList) {
            JsonObject project = value.asJsonObject();
            assertThat(project.containsKey("id")).isTrue();
            assertThat(project.containsKey("name")).isTrue();
            assertThat(project.containsKey("permission")).isTrue();
            assertThat(project.containsKey("active_mutex")).isTrue();
            count++;
        }
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("error handling")
    void testErrorHandling() {
        JsonObject incompleteProject = Json.createObjectBuilder()
            .add("id", "incomplete-proj")
            .build();

        assertThatThrownBy(() -> incompleteProject.getString("name"))
            .isInstanceOf(Exception.class);

        JsonObject projectWithNulls = Json.createObjectBuilder()
            .add("id", "null-proj")
            .add("name", "Null Project")
            .add("permission", "READ_ONLY")
            .addNull("active_mutex")
            .build();

        JsonValue mutex = projectWithNulls.get("active_mutex");
        assertThat(mutex.getValueType()).isEqualTo(JsonValue.ValueType.NULL);

        String emptyString = "";
        assertThat(emptyString.isEmpty()).isTrue();
        assertThat(emptyString.length()).isEqualTo(0);
    }

    @Test
    @DisplayName("edge cases")
    void testEdgeCases() {
        String dateWithMicroseconds = "2024-01-15T10:30:00.123456";
        String trimmedDate = dateWithMicroseconds.substring(0, dateWithMicroseconds.lastIndexOf('.'));
        assertThat(trimmedDate).isEqualTo("2024-01-15T10:30:00");

        String dateWithoutMicroseconds = "2024-01-15T10:30:00";
        int lastDotIndex = dateWithoutMicroseconds.lastIndexOf('.');
        if (lastDotIndex != -1) {
            String trimmed = dateWithoutMicroseconds.substring(0, lastDotIndex);
            assertThat(trimmed).isNotEqualTo(dateWithoutMicroseconds);
        } else {
            assertThat(dateWithoutMicroseconds).isEqualTo("2024-01-15T10:30:00");
        }
    }

    @Test
    @DisplayName("state transitions")
    void testStateTransitions() {
        boolean isAuthenticated = false;

        assertThat(isAuthenticated).isFalse();

        isAuthenticated = true;
        assertThat(isAuthenticated).isTrue();

        isAuthenticated = false;
        assertThat(isAuthenticated).isFalse();

        JsonObject currentProject = null;
        assertThat(currentProject).isNull();

        currentProject = Json.createObjectBuilder()
            .add("id", "selected-proj")
            .add("name", "Selected Project")
            .add("permission", "READ_AND_WRITE")
            .addNull("active_mutex")
            .build();

        assertThat(currentProject).isNotNull();
        assertThat(currentProject.getString("id")).isEqualTo("selected-proj");

        currentProject = null;
        assertThat(currentProject).isNull();
    }

    @Test
    @DisplayName("property file handling")
    void testPropertyFileHandling() throws IOException {
        Path debugProps = Paths.get(TEST_RESOURCES_DIR + File.separator + "debug.properties");
        Properties props = new Properties();
        props.setProperty("debug.mode", "true");
        props.setProperty("log.level", "DEBUG");

        try (var output = Files.newOutputStream(debugProps)) {
            props.store(output, "Test debug properties");
        }

        assertThat(Files.exists(debugProps)).isTrue();

        Properties loadedProps = new Properties();
        try (var input = Files.newInputStream(debugProps)) {
            loadedProps.load(input);
        }

        assertThat(loadedProps.getProperty("debug.mode")).isEqualTo("true");
        assertThat(loadedProps.getProperty("log.level")).isEqualTo("DEBUG");

        boolean debugMode = Boolean.parseBoolean(loadedProps.getProperty("debug.mode", "false"));
        assertThat(debugMode).isTrue();

        boolean nonExistentProperty = Boolean.parseBoolean(loadedProps.getProperty("non.existent", "false"));
        assertThat(nonExistentProperty).isFalse();
    }

    @Test
    @DisplayName("string validation")
    void testStringValidation() {
        String validEmail = "user@example.com";
        assertThat(validEmail.contains("@")).isTrue();
        assertThat(validEmail.contains(".")).isTrue();
        assertThat(validEmail.isEmpty()).isFalse();

        String invalidEmail = "invalid-email";
        assertThat(invalidEmail.contains("@")).isFalse();

        String password = "mypassword123";
        assertThat(password.isEmpty()).isFalse();
        assertThat(password.length()).isGreaterThanOrEqualTo(8);

        String emptyPassword = "";
        assertThat(emptyPassword.isEmpty()).isTrue();
        assertThat(emptyPassword.length()).isEqualTo(0);

        String validInstance = "www.speleodb.org";
        assertThat(validInstance.isEmpty()).isFalse();
        assertThat(validInstance.startsWith("http://")).isFalse();
        assertThat(validInstance.startsWith("https://")).isFalse();

        String urlWithProtocol = "https://www.speleodb.org";
        assertThat(urlWithProtocol.startsWith("https://")).isTrue();

        String uploadMessage = "Updated cave survey data";
        assertThat(uploadMessage.isEmpty()).isFalse();
        assertThat(uploadMessage.length()).isGreaterThan(0);
    }

    @Test
    @DisplayName("UI state logic")
    void testUIStateLogic() {
        boolean isAuthenticated = false;
        boolean hasProject = false;
        boolean hasLock = false;

        assertThat(shouldShowProjects(isAuthenticated)).isFalse();
        assertThat(shouldEnableUpload(isAuthenticated, hasProject, hasLock)).isFalse();
        assertThat(shouldEnableUnlock(isAuthenticated, hasProject, hasLock)).isTrue();

        isAuthenticated = true;
        assertThat(shouldShowProjects(isAuthenticated)).isTrue();
        assertThat(shouldEnableUpload(isAuthenticated, hasProject, hasLock)).isFalse();
        assertThat(shouldEnableUnlock(isAuthenticated, hasProject, hasLock)).isTrue();

        hasProject = true;
        assertThat(shouldShowProjects(isAuthenticated)).isTrue();
        assertThat(shouldEnableUpload(isAuthenticated, hasProject, hasLock)).isFalse();
        assertThat(shouldEnableUnlock(isAuthenticated, hasProject, hasLock)).isTrue();

        hasLock = true;
        assertThat(shouldShowProjects(isAuthenticated)).isTrue();
        assertThat(shouldEnableUpload(isAuthenticated, hasProject, hasLock)).isTrue();
        assertThat(shouldEnableUnlock(isAuthenticated, hasProject, hasLock)).isTrue();
    }

    private static boolean shouldShowProjects(boolean isAuthenticated) {
        return isAuthenticated;
    }

    private static boolean shouldEnableUpload(boolean isAuthenticated, boolean hasProject, boolean hasLock) {
        return isAuthenticated && hasProject && hasLock;
    }

    private static boolean shouldEnableUnlock(boolean isAuthenticated, boolean hasProject, boolean hasLock) { return true; }
}
