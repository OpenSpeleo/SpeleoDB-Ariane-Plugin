package org.speleodb.ariane.plugin.speleodb;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SpeleoDBConstants.AccessLevel enum.
 * Tests enum values, ordering, and basic enum operations.
 */
@DisplayName("SpeleoDB Access Level Tests")
class SpeleoDBAccessLevelTest {

    @Nested
    @DisplayName("Enum Values")
    class EnumValuesTests {

        @Test
        @DisplayName("Should have three access levels")
        void shouldHaveThreeAccessLevels() {
            SpeleoDBConstants.AccessLevel[] levels = SpeleoDBConstants.AccessLevel.values();
            assertThat(levels).hasSize(3);
        }

        @Test
        @DisplayName("Should contain ADMIN access level")
        void shouldContainAdminAccessLevel() {
            assertThat(SpeleoDBConstants.AccessLevel.ADMIN)
                .isNotNull()
                .hasToString("ADMIN");
        }

        @Test
        @DisplayName("Should contain READ_AND_WRITE access level")
        void shouldContainReadAndWriteAccessLevel() {
            assertThat(SpeleoDBConstants.AccessLevel.READ_AND_WRITE)
                .isNotNull()
                .hasToString("READ_AND_WRITE");
        }

        @Test
        @DisplayName("Should contain READ_ONLY access level")
        void shouldContainReadOnlyAccessLevel() {
            assertThat(SpeleoDBConstants.AccessLevel.READ_ONLY)
                .isNotNull()
                .hasToString("READ_ONLY");
        }
    }

    @Nested
    @DisplayName("Enum Operations")
    class EnumOperationsTests {

        @Test
        @DisplayName("Should support valueOf operation")
        void shouldSupportValueOfOperation() {
            assertThat(SpeleoDBConstants.AccessLevel.valueOf("ADMIN"))
                .isEqualTo(SpeleoDBConstants.AccessLevel.ADMIN);
            assertThat(SpeleoDBConstants.AccessLevel.valueOf("READ_AND_WRITE"))
                .isEqualTo(SpeleoDBConstants.AccessLevel.READ_AND_WRITE);
            assertThat(SpeleoDBConstants.AccessLevel.valueOf("READ_ONLY"))
                .isEqualTo(SpeleoDBConstants.AccessLevel.READ_ONLY);
        }

        @Test
        @DisplayName("Should support ordinal values")
        void shouldSupportOrdinalValues() {
            assertThat(SpeleoDBConstants.AccessLevel.ADMIN.ordinal()).isEqualTo(0);
            assertThat(SpeleoDBConstants.AccessLevel.READ_AND_WRITE.ordinal()).isEqualTo(1);
            assertThat(SpeleoDBConstants.AccessLevel.READ_ONLY.ordinal()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should support name() method")
        void shouldSupportNameMethod() {
            assertThat(SpeleoDBConstants.AccessLevel.ADMIN.name()).isEqualTo("ADMIN");
            assertThat(SpeleoDBConstants.AccessLevel.READ_AND_WRITE.name()).isEqualTo("READ_AND_WRITE");
            assertThat(SpeleoDBConstants.AccessLevel.READ_ONLY.name()).isEqualTo("READ_ONLY");
        }

        @Test
        @DisplayName("Should support compareTo operation")
        void shouldSupportCompareToOperation() {
            assertThat(SpeleoDBConstants.AccessLevel.ADMIN.compareTo(SpeleoDBConstants.AccessLevel.READ_ONLY))
                .isLessThan(0);
            assertThat(SpeleoDBConstants.AccessLevel.READ_ONLY.compareTo(SpeleoDBConstants.AccessLevel.ADMIN))
                .isGreaterThan(0);
            assertThat(SpeleoDBConstants.AccessLevel.READ_AND_WRITE.compareTo(SpeleoDBConstants.AccessLevel.READ_AND_WRITE))
                .isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Permission Level Semantics")
    class PermissionSemanticsTests {

        @Test
        @DisplayName("Should maintain proper permission hierarchy")
        void shouldMaintainProperPermissionHierarchy() {
            // ADMIN should be the highest level (ordinal 0)
            assertThat(SpeleoDBConstants.AccessLevel.ADMIN.ordinal())
                .isLessThan(SpeleoDBConstants.AccessLevel.READ_AND_WRITE.ordinal());

            // READ_AND_WRITE should be higher than READ_ONLY
            assertThat(SpeleoDBConstants.AccessLevel.READ_AND_WRITE.ordinal())
                .isLessThan(SpeleoDBConstants.AccessLevel.READ_ONLY.ordinal());
        }

        @Test
        @DisplayName("Should allow checking if level allows writing")
        void shouldAllowCheckingIfLevelAllowsWriting() {
            // This test demonstrates how the enum could be used for permission checks
            assertThat(allowsWriting(SpeleoDBConstants.AccessLevel.ADMIN)).isTrue();
            assertThat(allowsWriting(SpeleoDBConstants.AccessLevel.READ_AND_WRITE)).isTrue();
            assertThat(allowsWriting(SpeleoDBConstants.AccessLevel.READ_ONLY)).isFalse();
        }

        @Test
        @DisplayName("Should allow checking if level allows admin operations")
        void shouldAllowCheckingIfLevelAllowsAdminOperations() {
            assertThat(allowsAdmin(SpeleoDBConstants.AccessLevel.ADMIN)).isTrue();
            assertThat(allowsAdmin(SpeleoDBConstants.AccessLevel.READ_AND_WRITE)).isFalse();
            assertThat(allowsAdmin(SpeleoDBConstants.AccessLevel.READ_ONLY)).isFalse();
        }
    }

    // Helper methods to simulate permission checking logic
    private boolean allowsWriting(SpeleoDBConstants.AccessLevel level) {
        return level != SpeleoDBConstants.AccessLevel.READ_ONLY;
    }

    private boolean allowsAdmin(SpeleoDBConstants.AccessLevel level) {
        return level == SpeleoDBConstants.AccessLevel.ADMIN;
    }
}
