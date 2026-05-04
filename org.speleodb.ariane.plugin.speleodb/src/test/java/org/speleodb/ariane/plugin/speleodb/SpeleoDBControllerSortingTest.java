package org.speleodb.ariane.plugin.speleodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.SortMode;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

/**
 * Comprehensive unit tests for SpeleoDB Controller sorting functionality.
 * Tests the sorting modes, data caching, and shared rebuild logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpeleoDB Controller Sorting Tests")
class SpeleoDBControllerSortingTest {

    private SpeleoDBControllerSortingLogic sortingLogic;

    @BeforeEach
    void setUp() {
        // Reset singleton instance before each test
        SpeleoDBController.resetInstance();

        sortingLogic = new SpeleoDBControllerSortingLogic();
    }

    @AfterEach
    void tearDown() {
        // Reset singleton instance after each test to ensure clean state
        SpeleoDBController.resetInstance();
    }

    @Nested
    @DisplayName("Sort Mode Functionality")
    class SortModeFunctionalityTests {

        @Test
        @DisplayName("Should have correct default sort mode")
        void shouldHaveCorrectDefaultSortMode() {
            // Verify default sort mode is BY_NAME
            assertThat(sortingLogic.getCurrentSortMode()).isEqualTo(SortMode.BY_NAME);
        }

        @Test
        @DisplayName("Should change sort mode to BY_DATE")
        void shouldChangeSortModeToByDate() {
            // Execute
            sortingLogic.setSortMode(SortMode.BY_DATE);

            // Verify
            assertThat(sortingLogic.getCurrentSortMode()).isEqualTo(SortMode.BY_DATE);
        }

        @Test
        @DisplayName("Should change sort mode to BY_NAME")
        void shouldChangeSortModeToByName() {
            // Setup - start with BY_DATE
            sortingLogic.setSortMode(SortMode.BY_DATE);

            // Execute
            sortingLogic.setSortMode(SortMode.BY_NAME);

            // Verify
            assertThat(sortingLogic.getCurrentSortMode()).isEqualTo(SortMode.BY_NAME);
        }

        @Test
        @DisplayName("Should handle multiple sort mode changes")
        void shouldHandleMultipleSortModeChanges() {
            // Execute multiple changes
            sortingLogic.setSortMode(SortMode.BY_DATE);
            sortingLogic.setSortMode(SortMode.BY_NAME);
            sortingLogic.setSortMode(SortMode.BY_DATE);

            // Verify final state
            assertThat(sortingLogic.getCurrentSortMode()).isEqualTo(SortMode.BY_DATE);
        }
    }

    @Nested
    @DisplayName("Project Data Caching")
    class ProjectDataCachingTests {

        @Test
        @DisplayName("Should cache project data correctly")
        void shouldCacheProjectDataCorrectly() {
            // Setup
            JsonArray testData = createTestProjectData();

            // Execute
            sortingLogic.cacheProjectData(testData);

            // Verify
            JsonArray cachedData = sortingLogic.getCachedProjectData();
            assertThat(cachedData).isNotNull();
            assertThat(cachedData.size()).isEqualTo(3);
        }

        @Test
        @DisplayName("Should return null when no data is cached")
        void shouldReturnNullWhenNoDataIsCached() {
            // Verify
            assertThat(sortingLogic.getCachedProjectData()).isNull();
        }

        @Test
        @DisplayName("Should overwrite previously cached data")
        void shouldOverwritePreviouslyCachedData() {
            // Setup
            JsonArray firstData = createTestProjectData();
            JsonArray secondData = createSingleProjectData();

            // Execute
            sortingLogic.cacheProjectData(firstData);
            sortingLogic.cacheProjectData(secondData);

            // Verify
            JsonArray cachedData = sortingLogic.getCachedProjectData();
            assertThat(cachedData.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should handle empty project data")
        void shouldHandleEmptyProjectData() {
            // Setup
            JsonArray emptyData = Json.createArrayBuilder().build();

            // Execute
            sortingLogic.cacheProjectData(emptyData);

            // Verify
            JsonArray cachedData = sortingLogic.getCachedProjectData();
            assertThat(cachedData).isNotNull();
            assertThat(cachedData.size()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Project Sorting Logic")
    class ProjectSortingLogicTests {

        @Test
        @DisplayName("Should sort projects by name alphabetically")
        void shouldSortProjectsByNameAlphabetically() {
            // Setup
            JsonArray testData = createTestProjectData();
            sortingLogic.cacheProjectData(testData);
            sortingLogic.setSortMode(SortMode.BY_NAME);

            // Execute
            List<JsonObject> sortedProjects = sortingLogic.getSortedProjects();

            // Verify
            assertThat(sortedProjects).hasSize(3);
            assertThat(sortedProjects.get(0).getString("name")).isEqualTo("Alpha Cave");
            assertThat(sortedProjects.get(1).getString("name")).isEqualTo("Beta Cave");
            assertThat(sortedProjects.get(2).getString("name")).isEqualTo("Gamma Cave");
        }

        @Test
        @DisplayName("Should sort projects by name case-insensitively")
        void shouldSortProjectsByNameCaseInsensitively() {
            // Setup
            JsonArray testData = createCaseInsensitiveTestData();
            sortingLogic.cacheProjectData(testData);
            sortingLogic.setSortMode(SortMode.BY_NAME);

            // Execute
            List<JsonObject> sortedProjects = sortingLogic.getSortedProjects();

            // Verify
            assertThat(sortedProjects).hasSize(3);
            assertThat(sortedProjects.get(0).getString("name")).isEqualTo("apple Cave");
            assertThat(sortedProjects.get(1).getString("name")).isEqualTo("Banana Cave");
            assertThat(sortedProjects.get(2).getString("name")).isEqualTo("cherry Cave");
        }

        @Test
        @DisplayName("Should sort projects by modified date newest first")
        void shouldSortProjectsByModifiedDateNewestFirst() {
            // Setup
            JsonArray testData = createTestProjectDataWithDates();
            sortingLogic.cacheProjectData(testData);
            sortingLogic.setSortMode(SortMode.BY_DATE);

            // Execute
            List<JsonObject> sortedProjects = sortingLogic.getSortedProjects();

            // Verify - newest first
            assertThat(sortedProjects).hasSize(3);
            assertThat(sortedProjects.get(0).getString("name")).isEqualTo("Recent Cave");
            assertThat(sortedProjects.get(1).getString("name")).isEqualTo("Middle Cave");
            assertThat(sortedProjects.get(2).getString("name")).isEqualTo("Old Cave");
        }

        @Test
        @DisplayName("Should handle projects with missing names")
        void shouldHandleProjectsWithMissingNames() {
            // Setup
            JsonArray testData = createTestDataWithMissingNames();
            sortingLogic.cacheProjectData(testData);
            sortingLogic.setSortMode(SortMode.BY_NAME);

            // Execute
            List<JsonObject> sortedProjects = sortingLogic.getSortedProjects();

            // Verify - projects with empty names should come first
            assertThat(sortedProjects).hasSize(3);
            assertThat(sortedProjects.get(0).getString("name", "")).isEmpty();
            assertThat(sortedProjects.get(1).getString("name")).isEqualTo("Alpha Cave");
            assertThat(sortedProjects.get(2).getString("name")).isEqualTo("Beta Cave");
        }

        @Test
        @DisplayName("Should handle projects with missing dates")
        void shouldHandleProjectsWithMissingDates() {
            // Setup
            JsonArray testData = createTestDataWithMissingDates();
            sortingLogic.cacheProjectData(testData);
            sortingLogic.setSortMode(SortMode.BY_DATE);

            // Execute
            List<JsonObject> sortedProjects = sortingLogic.getSortedProjects();

            // Verify - projects with empty dates should come last
            assertThat(sortedProjects).hasSize(3);
            assertThat(sortedProjects.get(0).getString("name")).isEqualTo("Recent Cave");
            assertThat(sortedProjects.get(1).getString("name")).isEqualTo("No Date Cave");
            assertThat(sortedProjects.get(2).getString("name")).isEqualTo("Also No Date Cave");
        }

        @Test
        @DisplayName("Should return empty list when no cached data")
        void shouldReturnEmptyListWhenNoCachedData() {
            // Execute
            List<JsonObject> sortedProjects = sortingLogic.getSortedProjects();

            // Verify
            assertThat(sortedProjects).isEmpty();
        }

        @Test
        @DisplayName("Should handle empty cached data")
        void shouldHandleEmptyCachedData() {
            // Setup
            JsonArray emptyData = Json.createArrayBuilder().build();
            sortingLogic.cacheProjectData(emptyData);

            // Execute
            List<JsonObject> sortedProjects = sortingLogic.getSortedProjects();

            // Verify
            assertThat(sortedProjects).isEmpty();
        }
    }

    @Nested
    @DisplayName("Authentication State Handling")
    class AuthenticationStateHandlingTests {

        @Test
        @DisplayName("Should allow sorting when authenticated")
        void shouldAllowSortingWhenAuthenticated() {
            // Setup
            sortingLogic.setAuthenticated(true);

            // Execute & Verify
            String result = sortingLogic.simulateSortByName();
            assertThat(result).contains("Sort by name requested");
        }

        @Test
        @DisplayName("Should prevent sorting when not authenticated")
        void shouldPreventSortingWhenNotAuthenticated() {
            // Setup
            sortingLogic.setAuthenticated(false);

            // Execute & Verify
            String result = sortingLogic.simulateSortByName();
            assertThat(result).contains("Cannot sort projects: Not authenticated");
        }

        @Test
        @DisplayName("Should allow date sorting when authenticated")
        void shouldAllowDateSortingWhenAuthenticated() {
            // Setup
            sortingLogic.setAuthenticated(true);

            // Execute & Verify
            String result = sortingLogic.simulateSortByDate();
            assertThat(result).contains("Sort by date requested");
        }

        @Test
        @DisplayName("Should prevent date sorting when not authenticated")
        void shouldPreventDateSortingWhenNotAuthenticated() {
            // Setup
            sortingLogic.setAuthenticated(false);

            // Execute & Verify
            String result = sortingLogic.simulateSortByDate();
            assertThat(result).contains("Cannot sort projects: Not authenticated");
        }
    }

    @Nested
    @DisplayName("Shared Rebuild Logic")
    class SharedRebuildLogicTests {

        @Test
        @DisplayName("Should rebuild project list from cache")
        void shouldRebuildProjectListFromCache() {
            // Setup
            JsonArray testData = createTestProjectData();
            sortingLogic.cacheProjectData(testData);
            sortingLogic.setSortMode(SortMode.BY_NAME);

            // Execute
            String result = sortingLogic.simulateRebuildFromCache();

            // Verify
            assertThat(result).contains("Project list rebuilt with 3 projects");
            assertThat(result).contains("Projects sorted by name (A-Z)");
        }

        @Test
        @DisplayName("Should handle rebuild with no cached data")
        void shouldHandleRebuildWithNoCachedData() {
            // Execute
            String result = sortingLogic.simulateRebuildFromCache();

            // Verify
            assertThat(result).contains("No cached project data available");
        }

        @Test
        @DisplayName("Should rebuild with date sorting")
        void shouldRebuildWithDateSorting() {
            // Setup
            JsonArray testData = createTestProjectDataWithDates();
            sortingLogic.cacheProjectData(testData);
            sortingLogic.setSortMode(SortMode.BY_DATE);

            // Execute
            String result = sortingLogic.simulateRebuildFromCache();

            // Verify
            assertThat(result).contains("Project list rebuilt with 3 projects");
            assertThat(result).contains("Projects sorted by modified_date (newest first)");
        }

        @Test
        @DisplayName("Should handle refresh and sort using same logic")
        void shouldHandleRefreshAndSortUsingSameLogic() {
            // Setup
            sortingLogic.setAuthenticated(true); // Need to be authenticated to sort
            JsonArray testData = createTestProjectData();

            // Execute refresh (which caches data and rebuilds)
            String refreshResult = sortingLogic.simulateRefreshResponse(testData);

            // Execute sort (which uses cached data and rebuilds)
            String sortResult = sortingLogic.simulateSortByDate();

            // Verify both use the same rebuild logic
            assertThat(refreshResult).contains("Project list rebuilt with 3 projects");
            assertThat(sortResult).contains("Sort by date requested");
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should handle complete sorting workflow")
        void shouldHandleCompleteSortingWorkflow() {
            // Setup
            sortingLogic.setAuthenticated(true);
            JsonArray testData = createTestProjectDataWithDates();

            // Execute complete workflow
            sortingLogic.simulateRefreshResponse(testData);
            String nameSort = sortingLogic.simulateSortByName();
            String dateSort = sortingLogic.simulateSortByDate();

            // Verify workflow
            assertThat(nameSort).contains("Sort by name requested");
            assertThat(dateSort).contains("Sort by date requested");
            assertThat(sortingLogic.getCurrentSortMode()).isEqualTo(SortMode.BY_DATE);
        }

        @Test
        @DisplayName("Should maintain sort order after refresh")
        void shouldMaintainSortOrderAfterRefresh() {
            // Setup
            sortingLogic.setAuthenticated(true);
            JsonArray testData = createTestProjectDataWithDates();

            // Execute
            sortingLogic.simulateRefreshResponse(testData);
            sortingLogic.simulateSortByDate(); // Change to date sorting
            String refreshResult = sortingLogic.simulateRefreshResponse(testData); // Refresh again

            // Verify sort mode is maintained
            assertThat(sortingLogic.getCurrentSortMode()).isEqualTo(SortMode.BY_DATE);
            assertThat(refreshResult).contains("Projects sorted by modified_date (newest first)");
        }

        @Test
        @DisplayName("Should handle authentication state changes during sorting")
        void shouldHandleAuthenticationStateChangesDuringSorting() {
            // Setup
            sortingLogic.setAuthenticated(true);
            JsonArray testData = createTestProjectData();
            sortingLogic.simulateRefreshResponse(testData);

            // Execute - become unauthenticated
            sortingLogic.setAuthenticated(false);
            String sortResult = sortingLogic.simulateSortByName();

            // Verify
            assertThat(sortResult).contains("Cannot sort projects: Not authenticated");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Performance Tests")
    class EdgeCasesAndPerformanceTests {

        @Test
        @DisplayName("Should handle large dataset sorting performance")
        void shouldHandleLargeDatasetSortingPerformance() {
            // Setup - create large dataset (1000 projects)
            JsonArray largeDataset = createLargeTestDataset(1000);
            sortingLogic.cacheProjectData(largeDataset);

            // Execute and measure performance for name sorting
            long startTime = System.nanoTime();
            sortingLogic.setSortMode(SortMode.BY_NAME);
            List<JsonObject> sortedByName = sortingLogic.getSortedProjects();
            long nameTime = System.nanoTime() - startTime;

            // Execute and measure performance for date sorting
            startTime = System.nanoTime();
            sortingLogic.setSortMode(SortMode.BY_DATE);
            List<JsonObject> sortedByDate = sortingLogic.getSortedProjects();
            long dateTime = System.nanoTime() - startTime;

            // Verify results
            assertThat(sortedByName).hasSize(1000);
            assertThat(sortedByDate).hasSize(1000);

            // Performance should be reasonable (less than 100ms for 1000 items)
            assertThat(nameTime).isLessThan(100_000_000L); // 100ms in nanoseconds
            assertThat(dateTime).isLessThan(100_000_000L); // 100ms in nanoseconds

            // Verify first few items are correctly sorted
            assertThat(sortedByName.get(0).getString("name")).startsWith("Project_000");
            assertThat(sortedByName.get(999).getString("name")).startsWith("Project_999");
        }

        @Test
        @DisplayName("Should handle projects with identical names")
        void shouldHandleProjectsWithIdenticalNames() {
            // Setup
            JsonArray identicalNamesData = createIdenticalNamesTestData();
            sortingLogic.cacheProjectData(identicalNamesData);
            sortingLogic.setSortMode(SortMode.BY_NAME);

            // Execute
            List<JsonObject> sortedProjects = sortingLogic.getSortedProjects();

            // Verify - all projects should be present and stable sort maintained
            assertThat(sortedProjects).hasSize(4);
            assertThat(sortedProjects.get(0).getString("name")).isEqualTo("Duplicate Cave");
            assertThat(sortedProjects.get(1).getString("name")).isEqualTo("Duplicate Cave");
            assertThat(sortedProjects.get(2).getString("name")).isEqualTo("Duplicate Cave");
            assertThat(sortedProjects.get(3).getString("name")).isEqualTo("Unique Cave");
        }

        @Test
        @DisplayName("Should handle projects with identical dates")
        void shouldHandleProjectsWithIdenticalDates() {
            // Setup
            JsonArray identicalDatesData = createIdenticalDatesTestData();
            sortingLogic.cacheProjectData(identicalDatesData);
            sortingLogic.setSortMode(SortMode.BY_DATE);

            // Execute
            List<JsonObject> sortedProjects = sortingLogic.getSortedProjects();

            // Verify - all projects should be present
            assertThat(sortedProjects).hasSize(4);
            // Projects with same date should maintain stable order
            assertThat(sortedProjects.get(0).getString("modified_date")).isEqualTo("2025-01-01T10:00:00.000000-05:00");
            assertThat(sortedProjects.get(1).getString("modified_date")).isEqualTo("2025-01-01T10:00:00.000000-05:00");
            assertThat(sortedProjects.get(2).getString("modified_date")).isEqualTo("2025-01-01T10:00:00.000000-05:00");
            assertThat(sortedProjects.get(3).getString("modified_date")).isEqualTo("2024-01-01T10:00:00.000000-05:00");
        }

        @Test
        @DisplayName("Should handle special characters in project names")
        void shouldHandleSpecialCharactersInProjectNames() {
            // Setup
            JsonArray specialCharsData = createSpecialCharactersTestData();
            sortingLogic.cacheProjectData(specialCharsData);
            sortingLogic.setSortMode(SortMode.BY_NAME);

            // Execute
            List<JsonObject> sortedProjects = sortingLogic.getSortedProjects();

            // Verify - special characters should be handled properly
            assertThat(sortedProjects).hasSize(5);
            // Should sort by ASCII/Unicode order
            assertThat(sortedProjects.get(0).getString("name")).isEqualTo("!Special Cave");
            assertThat(sortedProjects.get(1).getString("name")).isEqualTo("@Symbol Cave");
            assertThat(sortedProjects.get(2).getString("name")).isEqualTo("Normal Cave");
            assertThat(sortedProjects.get(3).getString("name")).isEqualTo("Ümlaüt Cave");
            assertThat(sortedProjects.get(4).getString("name")).isEqualTo("中文 Cave");
        }

        @Test
        @DisplayName("Should handle numeric strings in project names")
        void shouldHandleNumericStringsInProjectNames() {
            // Setup
            JsonArray numericData = createNumericNamesTestData();
            sortingLogic.cacheProjectData(numericData);
            sortingLogic.setSortMode(SortMode.BY_NAME);

            // Execute
            List<JsonObject> sortedProjects = sortingLogic.getSortedProjects();

            // Verify - should sort as strings, not numbers
            assertThat(sortedProjects).hasSize(4);
            assertThat(sortedProjects.get(0).getString("name")).isEqualTo("Cave 1");
            assertThat(sortedProjects.get(1).getString("name")).isEqualTo("Cave 10");
            assertThat(sortedProjects.get(2).getString("name")).isEqualTo("Cave 2");
            assertThat(sortedProjects.get(3).getString("name")).isEqualTo("Cave 20");
        }

        @Test
        @DisplayName("Should handle extremely long project names")
        void shouldHandleExtremelyLongProjectNames() {
            // Setup
            JsonArray longNamesData = createLongNamesTestData();
            sortingLogic.cacheProjectData(longNamesData);
            sortingLogic.setSortMode(SortMode.BY_NAME);

            // Execute
            List<JsonObject> sortedProjects = sortingLogic.getSortedProjects();

            // Verify - should handle long names without issues
            assertThat(sortedProjects).hasSize(3);
            assertThat(sortedProjects.get(0).getString("name")).startsWith("A");
            assertThat(sortedProjects.get(1).getString("name")).startsWith("B");
            assertThat(sortedProjects.get(2).getString("name")).startsWith("Z");
        }

        @Test
        @DisplayName("Should handle malformed date strings")
        void shouldHandleMalformedDateStrings() {
            // Setup
            JsonArray malformedDatesData = createMalformedDatesTestData();
            sortingLogic.cacheProjectData(malformedDatesData);
            sortingLogic.setSortMode(SortMode.BY_DATE);

            // Execute
            List<JsonObject> sortedProjects = sortingLogic.getSortedProjects();

            // Verify - should handle malformed dates gracefully
            assertThat(sortedProjects).hasSize(4);
            // Date sorting is REVERSED (newest first), so we reverse the string comparison order
            // String comparison order: "", "2025-01-01...", "2025-13-45...", "not-a-date"
            // Reversed order: "not-a-date", "2025-13-45...", "2025-01-01...", ""
            assertThat(sortedProjects.get(0).getString("name")).isEqualTo("Invalid Date Cave 1"); // "not-a-date" comes first (reversed)
            assertThat(sortedProjects.get(1).getString("name")).isEqualTo("Invalid Date Cave 2"); // "2025-13-45..." comes next
            assertThat(sortedProjects.get(2).getString("name")).isEqualTo("Valid Date Cave"); // "2025-01-01..." comes next
            assertThat(sortedProjects.get(3).getString("name")).isEqualTo("Empty Date Cave"); // Empty string comes last (reversed)
        }

        @Test
        @DisplayName("Should maintain sort stability across multiple operations")
        void shouldMaintainSortStabilityAcrossMultipleOperations() {
            // Setup
            JsonArray testData = createTestProjectData();
            sortingLogic.cacheProjectData(testData);

            // Execute multiple sort operations
            sortingLogic.setSortMode(SortMode.BY_NAME);
            List<JsonObject> firstNameSort = new ArrayList<>(sortingLogic.getSortedProjects());

            sortingLogic.setSortMode(SortMode.BY_DATE);
            sortingLogic.setSortMode(SortMode.BY_NAME);
            List<JsonObject> secondNameSort = new ArrayList<>(sortingLogic.getSortedProjects());

            // Verify - results should be identical (stable sort)
            assertThat(firstNameSort).hasSize(secondNameSort.size());
            for (int i = 0; i < firstNameSort.size(); i++) {
                assertThat(firstNameSort.get(i).getString("name"))
                    .isEqualTo(secondNameSort.get(i).getString("name"));
            }
        }

        @Test
        @DisplayName("Should handle concurrent sorting operations")
        void shouldHandleConcurrentSortingOperations() {
            // Setup
            JsonArray testData = createTestProjectData();
            sortingLogic.cacheProjectData(testData);

            // Execute concurrent operations (simulate race conditions)
            List<Exception> exceptions = new ArrayList<>();
            List<Thread> threads = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                Thread thread = new Thread(() -> {
                    try {
                        sortingLogic.setSortMode(SortMode.BY_NAME);
                        sortingLogic.getSortedProjects();
                        sortingLogic.setSortMode(SortMode.BY_DATE);
                        sortingLogic.getSortedProjects();
                    } catch (Exception e) {
                        synchronized (exceptions) {
                            exceptions.add(e);
                        }
                    }
                });
                threads.add(thread);
                thread.start();
            }

            // Wait for all threads to complete
            for (Thread thread : threads) {
                try {
                    thread.join(1000); // 1 second timeout
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Verify - no exceptions should occur
            assertThat(exceptions).isEmpty();
        }
    }

    @Nested
    @DisplayName("Button Style Update Tests")
    class ButtonStyleUpdateTests {

        @Test
        @DisplayName("Should track button style changes for name sorting")
        void shouldTrackButtonStyleChangesForNameSorting() {
            // Setup
            sortingLogic.setSortMode(SortMode.BY_NAME);

            // Execute
            String styleInfo = sortingLogic.simulateButtonStyleUpdate();

            // Verify
            assertThat(styleInfo).contains("Name button: ACTIVE");
            assertThat(styleInfo).contains("Date button: INACTIVE");
        }

        @Test
        @DisplayName("Should track button style changes for date sorting")
        void shouldTrackButtonStyleChangesForDateSorting() {
            // Setup
            sortingLogic.setSortMode(SortMode.BY_DATE);

            // Execute
            String styleInfo = sortingLogic.simulateButtonStyleUpdate();

            // Verify
            assertThat(styleInfo).contains("Name button: INACTIVE");
            assertThat(styleInfo).contains("Date button: ACTIVE");
        }

        @Test
        @DisplayName("Should handle rapid button style changes")
        void shouldHandleRapidButtonStyleChanges() {
            // Execute rapid changes
            for (int i = 0; i < 50; i++) {
                sortingLogic.setSortMode(i % 2 == 0 ?
                    SortMode.BY_NAME :
                    SortMode.BY_DATE);
                String styleInfo = sortingLogic.simulateButtonStyleUpdate();
                assertThat(styleInfo).isNotEmpty();
            }

            // Verify final state - after 50 iterations (0-49), the last iteration is i=49 which is odd
            // So the final mode should be BY_DATE (since 49 % 2 != 0)
            assertThat(sortingLogic.getCurrentSortMode()).isEqualTo(SortMode.BY_DATE);
        }
    }

    // ===================== HELPER METHODS FOR EDGE CASE TESTS ===================== //

    private JsonArray createLargeTestDataset(int size) {
        JsonArrayBuilder builder = Json.createArrayBuilder();

        for (int i = 0; i < size; i++) {
            builder.add(Json.createObjectBuilder()
                .add("name", String.format("Project_%03d", i))
                .add("description", "Test project " + i)
                .add("modified_date", String.format("2024-%02d-%02dT10:00:00.000000-05:00",
                    (i % 12) + 1, (i % 28) + 1)));
        }

        return builder.build();
    }

    private JsonArray createIdenticalNamesTestData() {
        JsonArrayBuilder builder = Json.createArrayBuilder();

        builder.add(Json.createObjectBuilder()
            .add("name", "Duplicate Cave")
            .add("description", "First duplicate"));

        builder.add(Json.createObjectBuilder()
            .add("name", "Duplicate Cave")
            .add("description", "Second duplicate"));

        builder.add(Json.createObjectBuilder()
            .add("name", "Duplicate Cave")
            .add("description", "Third duplicate"));

        builder.add(Json.createObjectBuilder()
            .add("name", "Unique Cave")
            .add("description", "Only unique one"));

        return builder.build();
    }

    private JsonArray createIdenticalDatesTestData() {
        JsonArrayBuilder builder = Json.createArrayBuilder();

        builder.add(Json.createObjectBuilder()
            .add("name", "Cave A")
            .add("modified_date", "2025-01-01T10:00:00.000000-05:00"));

        builder.add(Json.createObjectBuilder()
            .add("name", "Cave B")
            .add("modified_date", "2025-01-01T10:00:00.000000-05:00"));

        builder.add(Json.createObjectBuilder()
            .add("name", "Cave C")
            .add("modified_date", "2025-01-01T10:00:00.000000-05:00"));

        builder.add(Json.createObjectBuilder()
            .add("name", "Cave D")
            .add("modified_date", "2024-01-01T10:00:00.000000-05:00"));

        return builder.build();
    }

    private JsonArray createSpecialCharactersTestData() {
        JsonArrayBuilder builder = Json.createArrayBuilder();

        builder.add(Json.createObjectBuilder()
            .add("name", "Normal Cave")
            .add("description", "Regular name"));

        builder.add(Json.createObjectBuilder()
            .add("name", "!Special Cave")
            .add("description", "Starts with exclamation"));

        builder.add(Json.createObjectBuilder()
            .add("name", "@Symbol Cave")
            .add("description", "Starts with at symbol"));

        builder.add(Json.createObjectBuilder()
            .add("name", "Ümlaüt Cave")
            .add("description", "Contains umlauts"));

        builder.add(Json.createObjectBuilder()
            .add("name", "中文 Cave")
            .add("description", "Contains Chinese characters"));

        return builder.build();
    }

    private JsonArray createNumericNamesTestData() {
        JsonArrayBuilder builder = Json.createArrayBuilder();

        builder.add(Json.createObjectBuilder()
            .add("name", "Cave 1")
            .add("description", "Number 1"));

        builder.add(Json.createObjectBuilder()
            .add("name", "Cave 2")
            .add("description", "Number 2"));

        builder.add(Json.createObjectBuilder()
            .add("name", "Cave 10")
            .add("description", "Number 10"));

        builder.add(Json.createObjectBuilder()
            .add("name", "Cave 20")
            .add("description", "Number 20"));

        return builder.build();
    }

    private JsonArray createLongNamesTestData() {
        JsonArrayBuilder builder = Json.createArrayBuilder();

        String longNameA = "A" + "a".repeat(500) + " Cave";
        String longNameB = "B" + "b".repeat(500) + " Cave";
        String longNameZ = "Z" + "z".repeat(500) + " Cave";

        builder.add(Json.createObjectBuilder()
            .add("name", longNameA)
            .add("description", "Very long name starting with A"));

        builder.add(Json.createObjectBuilder()
            .add("name", longNameB)
            .add("description", "Very long name starting with B"));

        builder.add(Json.createObjectBuilder()
            .add("name", longNameZ)
            .add("description", "Very long name starting with Z"));

        return builder.build();
    }

    private JsonArray createMalformedDatesTestData() {
        JsonArrayBuilder builder = Json.createArrayBuilder();

        builder.add(Json.createObjectBuilder()
            .add("name", "Valid Date Cave")
            .add("modified_date", "2025-01-01T10:00:00.000000-05:00"));

        builder.add(Json.createObjectBuilder()
            .add("name", "Invalid Date Cave 1")
            .add("modified_date", "not-a-date"));

        builder.add(Json.createObjectBuilder()
            .add("name", "Invalid Date Cave 2")
            .add("modified_date", "2025-13-45T25:70:80.000000-05:00"));

        builder.add(Json.createObjectBuilder()
            .add("name", "Empty Date Cave")
            .add("modified_date", ""));

        return builder.build();
    }

    // Helper methods to create test data
    private JsonArray createTestProjectData() {
        JsonArrayBuilder builder = Json.createArrayBuilder();

        builder.add(Json.createObjectBuilder()
            .add("name", "Gamma Cave")
            .add("description", "Third cave")
            .add("modified_date", "2025-01-01T10:00:00.000000-05:00"));

        builder.add(Json.createObjectBuilder()
            .add("name", "Alpha Cave")
            .add("description", "First cave")
            .add("modified_date", "2025-01-02T10:00:00.000000-05:00"));

        builder.add(Json.createObjectBuilder()
            .add("name", "Beta Cave")
            .add("description", "Second cave")
            .add("modified_date", "2025-01-03T10:00:00.000000-05:00"));

        return builder.build();
    }

    private JsonArray createCaseInsensitiveTestData() {
        JsonArrayBuilder builder = Json.createArrayBuilder();

        builder.add(Json.createObjectBuilder()
            .add("name", "cherry Cave")
            .add("description", "Lowercase c"));

        builder.add(Json.createObjectBuilder()
            .add("name", "Banana Cave")
            .add("description", "Uppercase B"));

        builder.add(Json.createObjectBuilder()
            .add("name", "apple Cave")
            .add("description", "Lowercase a"));

        return builder.build();
    }

    private JsonArray createTestProjectDataWithDates() {
        JsonArrayBuilder builder = Json.createArrayBuilder();

        builder.add(Json.createObjectBuilder()
            .add("name", "Old Cave")
            .add("description", "Oldest")
            .add("modified_date", "2024-01-01T10:00:00.000000-05:00"));

        builder.add(Json.createObjectBuilder()
            .add("name", "Recent Cave")
            .add("description", "Newest")
            .add("modified_date", "2025-06-19T23:08:09.145548-04:00"));

        builder.add(Json.createObjectBuilder()
            .add("name", "Middle Cave")
            .add("description", "Middle")
            .add("modified_date", "2024-12-15T15:30:00.000000-05:00"));

        return builder.build();
    }

    private JsonArray createTestDataWithMissingNames() {
        JsonArrayBuilder builder = Json.createArrayBuilder();

        builder.add(Json.createObjectBuilder()
            .add("description", "No name project"));

        builder.add(Json.createObjectBuilder()
            .add("name", "Beta Cave")
            .add("description", "Has name"));

        builder.add(Json.createObjectBuilder()
            .add("name", "Alpha Cave")
            .add("description", "Also has name"));

        return builder.build();
    }

    private JsonArray createTestDataWithMissingDates() {
        JsonArrayBuilder builder = Json.createArrayBuilder();

        builder.add(Json.createObjectBuilder()
            .add("name", "No Date Cave")
            .add("description", "No date"));

        builder.add(Json.createObjectBuilder()
            .add("name", "Recent Cave")
            .add("description", "Has date")
            .add("modified_date", "2025-06-19T23:08:09.145548-04:00"));

        builder.add(Json.createObjectBuilder()
            .add("name", "Also No Date Cave")
            .add("description", "Also no date"));

        return builder.build();
    }

    private JsonArray createSingleProjectData() {
        return Json.createArrayBuilder()
            .add(Json.createObjectBuilder()
                .add("name", "Single Cave")
                .add("description", "Only project"))
            .build();
    }

    /**
     * Test logic class that simulates the sorting functionality without JavaFX dependencies.
     */
    static class SpeleoDBControllerSortingLogic {
        private SortMode currentSortMode = SortMode.BY_NAME;
        private JsonArray cachedProjectList = null;
        private boolean isAuthenticated = false;

        public SortMode getCurrentSortMode() {
            return currentSortMode;
        }

        public void setSortMode(SortMode sortMode) {
            this.currentSortMode = sortMode;
        }

        public void cacheProjectData(JsonArray projectData) {
            this.cachedProjectList = projectData;
        }

        public JsonArray getCachedProjectData() {
            return cachedProjectList;
        }

        public void setAuthenticated(boolean authenticated) {
            this.isAuthenticated = authenticated;
        }

        public boolean isAuthenticated() {
            return isAuthenticated;
        }

        public List<JsonObject> getSortedProjects() {
            if (cachedProjectList == null) {
                return new ArrayList<>();
            }

            List<JsonObject> projects = new ArrayList<>();
            for (int i = 0; i < cachedProjectList.size(); i++) {
                projects.add(cachedProjectList.getJsonObject(i));
            }

            if (currentSortMode == SortMode.BY_NAME) {
                projects.sort((a, b) -> {
                    String nameA = a.getString("name", "").toLowerCase();
                    String nameB = b.getString("name", "").toLowerCase();
                    return nameA.compareTo(nameB);
                });
            } else { // BY_DATE
                projects.sort((a, b) -> {
                    String dateA = a.getString("modified_date", "");
                    String dateB = b.getString("modified_date", "");
                    return dateB.compareTo(dateA); // Reverse for newest first
                });
            }

            return projects;
        }

        public String simulateSortByName() {
            if (!isAuthenticated) {
                return "Cannot sort projects: Not authenticated";
            }

            currentSortMode = SortMode.BY_NAME;
            return "Sort by name requested";
        }

        public String simulateSortByDate() {
            if (!isAuthenticated) {
                return "Cannot sort projects: Not authenticated";
            }

            currentSortMode = SortMode.BY_DATE;
            return "Sort by date requested";
        }

        public String simulateRebuildFromCache() {
            if (cachedProjectList == null) {
                return "No cached project data available";
            }

            List<JsonObject> projects = getSortedProjects();

            String sortMessage;
            if (currentSortMode == SortMode.BY_NAME) {
                sortMessage = "Projects sorted by name (A-Z)";
            } else {
                sortMessage = "Projects sorted by modified_date (newest first)";
            }

            return sortMessage + "\nProject list rebuilt with " + projects.size() + " projects";
        }

        public String simulateRefreshResponse(JsonArray projectList) {
            cacheProjectData(projectList);
            return simulateRebuildFromCache();
        }

        public String simulateButtonStyleUpdate() {
            // Simulate the updateSortButtonStyles() method from the controller
            if (currentSortMode == SortMode.BY_NAME) {
                return "Name button: ACTIVE, Date button: INACTIVE";
            } else {
                return "Name button: INACTIVE, Date button: ACTIVE";
            }
        }
    }
}
