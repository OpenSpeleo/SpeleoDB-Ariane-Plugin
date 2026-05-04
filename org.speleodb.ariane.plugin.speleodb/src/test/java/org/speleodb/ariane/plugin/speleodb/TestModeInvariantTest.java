package org.speleodb.ariane.plugin.speleodb;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for the {@code enableTestMode} Gradle task wiring.
 *
 * <p>The build pipeline mutates {@link SpeleoDBConstants#TEST_MODE} from {@code false} to
 * {@code true} just before {@code compileJava} runs for the test task graph. If that
 * wiring breaks (e.g. a Gradle 9.x or JDK 25 task ordering regression), tests would
 * silently start writing to the real user preferences instead of the isolated test node
 * -- a subtle, dangerous failure that this single assertion catches up front.
 *
 * <p>This is mostly tautological under {@code ./gradlew :test} (the same compile pass
 * that produces the test bytecode also burns in the {@code TEST_MODE = true} constant),
 * but it has real value for IDE / Maven / hand-rolled runners where the Gradle wiring
 * does NOT run -- in that case it surfaces "your runner is bypassing enableTestMode" as
 * a RED test instead of a silent write to real user preferences.
 *
 * <p>The {@code FlowAction} declared in {@code settings.gradle} resets the constant back
 * to {@code false} once the build finishes; the CI pipeline asserts that reset via
 * {@code git diff --exit-code} after {@code :build}.
 */
class TestModeInvariantTest {

    @Test
    @DisplayName("enableTestMode wiring intact OR you're not running through Gradle (TEST_MODE must be true)")
    void testModeMustBeTrueDuringTests() {
        assertThat(SpeleoDBConstants.TEST_MODE)
                .as("Either Gradle's enableTestMode task didn't run before compileJava, or you're "
                        + "running this test outside of Gradle (IDE/Maven). Either way, tests would "
                        + "be writing to REAL user preferences right now -- fix the wiring before "
                        + "running anything else.")
                .isTrue();
    }
}
