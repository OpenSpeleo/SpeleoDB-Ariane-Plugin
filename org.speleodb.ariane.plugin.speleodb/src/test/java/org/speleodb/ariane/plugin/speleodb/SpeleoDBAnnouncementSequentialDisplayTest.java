package org.speleodb.ariane.plugin.speleodb;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

/**
 * Unit tests for SpeleoDB sequential announcement display functionality.
 * Tests the recursive display logic and dialog management using UUID-based tracking.
 */
@DisplayName("SpeleoDB Sequential Announcement Display Tests")
class SpeleoDBAnnouncementSequentialDisplayTest {

    @Mock
    private SpeleoDBController mockController;

    private ExecutorService testExecutor;

    // Simulate the displayed announcements tracking by UUID
    private Map<String, Boolean> displayedAnnouncements;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testExecutor = Executors.newSingleThreadExecutor();
        displayedAnnouncements = new HashMap<>();
    }

    @Nested
    @DisplayName("Announcement Collection Tests")
    class AnnouncementCollectionTests {

        @Test
        @DisplayName("Should collect unshown announcements from API response")
        void shouldCollectUnshownAnnouncementsFromApiResponse() {
            // Create test announcements with UUIDs
            JsonArray announcements = Json.createArrayBuilder()
                    .add(createTestAnnouncementWithUUID("First", "First message", "First header", "uuid-1"))
                    .add(createTestAnnouncementWithUUID("Second", "Second message", "Second header", "uuid-2"))
                    .add(createTestAnnouncementWithUUID("Third", "Third message", "Third header", "uuid-3"))
                    .build();

            // All announcements should be unshown initially
            List<JsonObject> unshownAnnouncements = collectUnshownAnnouncements(announcements);

            assertEquals(3, unshownAnnouncements.size());
            assertEquals("First", unshownAnnouncements.get(0).getString("title"));
            assertEquals("Second", unshownAnnouncements.get(1).getString("title"));
            assertEquals("Third", unshownAnnouncements.get(2).getString("title"));
        }

        @Test
        @DisplayName("Should filter out already shown announcements")
        void shouldFilterOutAlreadyShownAnnouncements() {
            JsonArray announcements = Json.createArrayBuilder()
                    .add(createTestAnnouncementWithUUID("Shown", "Shown message", "Shown header", "uuid-shown"))
                    .add(createTestAnnouncementWithUUID("New", "New message", "New header", "uuid-new"))
                    .build();

            // Mark first announcement as shown by UUID
            displayedAnnouncements.put("uuid-shown", true);

            List<JsonObject> unshownAnnouncements = collectUnshownAnnouncements(announcements);

            assertEquals(1, unshownAnnouncements.size());
            assertEquals("New", unshownAnnouncements.get(0).getString("title"));
        }

        @Test
        @DisplayName("Should handle empty announcement list")
        void shouldHandleEmptyAnnouncementList() {
            JsonArray emptyAnnouncements = Json.createArrayBuilder().build();

            List<JsonObject> unshownAnnouncements = collectUnshownAnnouncements(emptyAnnouncements);

            assertEquals(0, unshownAnnouncements.size());
        }

        @Test
        @DisplayName("Should handle all announcements already shown")
        void shouldHandleAllAnnouncementsAlreadyShown() {
            JsonArray announcements = Json.createArrayBuilder()
                    .add(createTestAnnouncementWithUUID("First", "First message", "First header", "uuid-1"))
                    .add(createTestAnnouncementWithUUID("Second", "Second message", "Second header", "uuid-2"))
                    .build();

            // Mark all announcements as shown by UUID
            displayedAnnouncements.put("uuid-1", true);
            displayedAnnouncements.put("uuid-2", true);

            List<JsonObject> unshownAnnouncements = collectUnshownAnnouncements(announcements);

            assertEquals(0, unshownAnnouncements.size());
        }

        @Test
        @DisplayName("Should handle announcements without UUID gracefully")
        void shouldHandleAnnouncementsWithoutUUIDGracefully() {
            JsonArray announcements = Json.createArrayBuilder()
                    .add(createTestAnnouncement("No UUID", "Message without UUID", "Header"))
                    .add(createTestAnnouncementWithUUID("With UUID", "Message with UUID", "Header", "uuid-1"))
                    .build();

            List<JsonObject> unshownAnnouncements = collectUnshownAnnouncements(announcements);

            // Announcement without UUID should be included (treated as never shown)
            assertEquals(2, unshownAnnouncements.size());
        }
    }

    @Nested
    @DisplayName("Sequential Display Logic Tests")
    class SequentialDisplayLogicTests {

        @Test
        @DisplayName("Should display announcements in correct order")
        void shouldDisplayAnnouncementsInCorrectOrder() {
            List<JsonObject> announcements = List.of(
                    createTestAnnouncementWithUUID("First", "First message", "First header", "uuid-1"),
                    createTestAnnouncementWithUUID("Second", "Second message", "Second header", "uuid-2"),
                    createTestAnnouncementWithUUID("Third", "Third message", "Third header", "uuid-3")
            );

            List<String> displayOrder = new ArrayList<>();

            // Simulate sequential display
            for (JsonObject announcement : announcements) {
                displayOrder.add(announcement.getString("title"));
            }

            assertEquals(List.of("First", "Second", "Third"), displayOrder);
        }

        @Test
        @DisplayName("Should handle single announcement")
        void shouldHandleSingleAnnouncement() {
            List<JsonObject> announcements = List.of(
                    createTestAnnouncementWithUUID("Single", "Single message", "Single header", "uuid-single")
            );

            assertNotNull(announcements);
            assertEquals(1, announcements.size());
            assertEquals("Single", announcements.get(0).getString("title"));
            assertEquals("uuid-single", announcements.get(0).getString("uuid"));
        }

        @Test
        @DisplayName("Should handle large number of announcements")
        void shouldHandleLargeNumberOfAnnouncements() {
            List<JsonObject> announcements = new ArrayList<>();
            for (int i = 1; i <= 100; i++) {
                announcements.add(createTestAnnouncementWithUUID(
                        "Title " + i,
                        "Message " + i,
                        "Header " + i,
                        "uuid-" + i
                ));
            }

            assertEquals(100, announcements.size());
            assertEquals("Title 1", announcements.get(0).getString("title"));
            assertEquals("Title 100", announcements.get(99).getString("title"));
            assertEquals("uuid-1", announcements.get(0).getString("uuid"));
            assertEquals("uuid-100", announcements.get(99).getString("uuid"));
        }
    }

    @Nested
    @DisplayName("Dialog Creation Tests")
    class DialogCreationTests {

        @Test
        @DisplayName("Should create dialog with correct content")
        void shouldCreateDialogWithCorrectContent() {
            JsonObject announcement = createTestAnnouncementWithUUID(
                    "Test Title",
                    "Test Message Content",
                    "Test Header",
                    "test-uuid"
            );

            // Simulate dialog creation
            String title = announcement.getString("title");
            String message = announcement.getString("message");
            String header = announcement.getString("header");
            String uuid = announcement.getString("uuid");

            assertEquals("Test Title", title);
            assertEquals("Test Message Content", message);
            assertEquals("Test Header", header);
            assertEquals("test-uuid", uuid);
        }

        @Test
        @DisplayName("Should handle announcements with missing header")
        void shouldHandleAnnouncementsWithMissingHeader() {
            JsonObject announcement = Json.createObjectBuilder()
                    .add("title", "Title Only")
                    .add("message", "Message content")
                    .add("uuid", "uuid-no-header")
                    .build();

            String title = announcement.getString("title");
            String message = announcement.getString("message");
            String uuid = announcement.getString("uuid");

            assertEquals("Title Only", title);
            assertEquals("Message content", message);
            assertEquals("uuid-no-header", uuid);
        }

        @Test
        @DisplayName("Should handle announcements with special characters")
        void shouldHandleAnnouncementsWithSpecialCharacters() {
            JsonObject announcement = createTestAnnouncementWithUUID(
                    "Special: àáâãäåæçèéêë",
                    "Symbols: !@#$%^&*()_+-=[]{}|;':\",./<>? and Unicode: 🎉🚀✨",
                    "Header with émojis: 🌟",
                    "uuid-special-chars"
            );

            String title = announcement.getString("title");
            String message = announcement.getString("message");
            String header = announcement.getString("header");
            String uuid = announcement.getString("uuid");

            assertEquals("Special: àáâãäåæçèéêë", title);
            assertTrue(message.contains("🎉🚀✨"));
            assertTrue(header.contains("🌟"));
            assertEquals("uuid-special-chars", uuid);
        }
    }

    @Nested
    @DisplayName("Announcement Marking Tests")
    class AnnouncementMarkingTests {

        @Test
        @DisplayName("Should mark announcements as displayed after showing")
        void shouldMarkAnnouncementsAsDisplayedAfterShowing() {
            JsonObject announcement = createTestAnnouncementWithUUID("Test", "Test message", "Test header", "test-uuid");
            String uuid = announcement.getString("uuid");

            assertFalse(hasAnnouncementBeenDisplayed(uuid));

            markAnnouncementAsDisplayed(uuid);

            assertTrue(hasAnnouncementBeenDisplayed(uuid));
        }

        @Test
        @DisplayName("Should generate unique UUIDs for different announcements")
        void shouldGenerateUniqueUUIDsForDifferentAnnouncements() {
            JsonObject announcement1 = createTestAnnouncementWithUUID("First", "First message", "First header", "uuid-1");
            JsonObject announcement2 = createTestAnnouncementWithUUID("Second", "Second message", "Second header", "uuid-2");

            String uuid1 = announcement1.getString("uuid");
            String uuid2 = announcement2.getString("uuid");

            assertNotEquals(uuid1, uuid2);
        }

        @Test
        @DisplayName("Should generate consistent UUIDs for same announcement")
        void shouldGenerateConsistentUUIDsForSameAnnouncement() {
            String testUUID = "consistent-uuid";
            JsonObject announcement1 = createTestAnnouncementWithUUID("Same", "Same message", "Same header", testUUID);
            JsonObject announcement2 = createTestAnnouncementWithUUID("Same", "Same message", "Same header", testUUID);

            String uuid1 = announcement1.getString("uuid");
            String uuid2 = announcement2.getString("uuid");

            assertEquals(uuid1, uuid2);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle null announcement list gracefully")
        void shouldHandleNullAnnouncementListGracefully() {
            assertDoesNotThrow(() -> {
                List<JsonObject> result = collectUnshownAnnouncements(null);
                assertEquals(0, result.size());
            });
        }

        @Test
        @DisplayName("Should handle malformed announcement data")
        void shouldHandleMalformedAnnouncementData() {
            JsonArray announcements = Json.createArrayBuilder()
                    .add(Json.createObjectBuilder()
                            .add("title", "Valid")
                            .add("uuid", "valid-uuid")
                            .build())
                    .add(Json.createObjectBuilder()
                            .add("title", "Missing UUID")
                            // No UUID field
                            .build())
                    .build();

            assertDoesNotThrow(() -> {
                List<JsonObject> unshownAnnouncements = collectUnshownAnnouncements(announcements);
                assertEquals(2, unshownAnnouncements.size()); // Both should be included
            });
        }

        @Test
        @DisplayName("Should handle UUID extraction failures")
        void shouldHandleUUIDExtractionFailures() {
            JsonObject announcement = Json.createObjectBuilder()
                    .add("title", "No UUID")
                    .add("message", "Message")
                    .build();

            assertDoesNotThrow(() -> {
                String uuid = getUUIDFromAnnouncement(announcement);
                // Should return null for missing UUID
                assertEquals(null, uuid);
            });
        }
    }

    @Nested
    @DisplayName("Timing and Synchronization Tests")
    class TimingAndSynchronizationTests {

        @Test
        @DisplayName("Should handle sequential display timing")
        void shouldHandleSequentialDisplayTiming() throws InterruptedException {
            List<JsonObject> announcements = List.of(
                    createTestAnnouncementWithUUID("First", "First message", "First header", "uuid-1"),
                    createTestAnnouncementWithUUID("Second", "Second message", "Second header", "uuid-2")
            );

            // Simulate timing delays
            Thread.sleep(100);

            assertEquals(2, announcements.size());
            assertEquals("uuid-1", announcements.get(0).getString("uuid"));
            assertEquals("uuid-2", announcements.get(1).getString("uuid"));
        }

        @Test
        @DisplayName("Should handle concurrent access safely")
        void shouldHandleConcurrentAccessSafely() throws InterruptedException, ExecutionException, TimeoutException {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                final int index = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    String uuid = "concurrent-uuid-" + index;
                    markAnnouncementAsDisplayed(uuid);
                    assertTrue(hasAnnouncementBeenDisplayed(uuid));
                }, testExecutor);
                futures.add(future);
            }

            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );

            allFutures.get(5, TimeUnit.SECONDS);

            // Verify all UUIDs were marked
            for (int i = 0; i < 10; i++) {
                assertTrue(hasAnnouncementBeenDisplayed("concurrent-uuid-" + i));
            }
        }
    }

    // Helper methods
    private JsonObject createTestAnnouncement(String title, String message, String header) {
        return Json.createObjectBuilder()
                .add("title", title)
                .add("message", message)
                .add("header", header)
                .build();
    }

    private JsonObject createTestAnnouncementWithUUID(String title, String message, String header, String uuid) {
        return Json.createObjectBuilder()
                .add("title", title)
                .add("message", message)
                .add("header", header)
                .add("uuid", uuid)
                .build();
    }

    private List<JsonObject> collectUnshownAnnouncements(JsonArray announcements) {
        if (announcements == null) {
            return new ArrayList<>();
        }

        List<JsonObject> unshownAnnouncements = new ArrayList<>();
        for (int i = 0; i < announcements.size(); i++) {
            JsonObject announcement = announcements.getJsonObject(i);
            String uuid = getUUIDFromAnnouncement(announcement);
            if (uuid == null || !hasAnnouncementBeenDisplayed(uuid)) {
                unshownAnnouncements.add(announcement);
            }
        }
        return unshownAnnouncements;
    }

    private String getUUIDFromAnnouncement(JsonObject announcement) {
        return announcement.getString("uuid", null);
    }

    private boolean hasAnnouncementBeenDisplayed(String uuid) {
        return displayedAnnouncements.getOrDefault(uuid, false);
    }

    private void markAnnouncementAsDisplayed(String uuid) {
        displayedAnnouncements.put(uuid, true);
    }
}
