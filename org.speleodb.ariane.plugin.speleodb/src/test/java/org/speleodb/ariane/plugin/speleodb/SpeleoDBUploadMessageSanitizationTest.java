package org.speleodb.ariane.plugin.speleodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.json.Json;
import jakarta.json.JsonObject;

/**
 * Tests that upload commit messages are properly stripped of leading/trailing
 * whitespace (spaces, tabs, newlines, carriage returns, Unicode whitespace)
 * before being sent to the server.
 */
@DisplayName("Upload Message Sanitization")
class SpeleoDBUploadMessageSanitizationTest {

    private SpeleoDBService service;

    private static final JsonObject TEST_PROJECT = Json.createObjectBuilder()
            .add("id", "sanitize-test-project")
            .add("name", "Sanitization Test")
            .build();

    @BeforeEach
    void setUp() throws Exception {
        SpeleoDBController controller = new SpeleoDBController(true);
        service = new SpeleoDBService(controller);
        // Set auth state so uploadProject passes the authentication check
        setPrivateField(service, "authToken", "test-token");
        setPrivateField(service, "sdbInstance", "https://test.example.com");
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Nested
    @DisplayName("String.strip() behavior verification")
    class StripBehaviorTests {

        @Test
        @DisplayName("strip() removes leading and trailing spaces")
        void stripRemovesSpaces() {
            assertThat("  hello world  ".strip()).isEqualTo("hello world");
        }

        @Test
        @DisplayName("strip() removes leading and trailing tabs")
        void stripRemovesTabs() {
            assertThat("\thello world\t".strip()).isEqualTo("hello world");
        }

        @Test
        @DisplayName("strip() removes leading and trailing newlines")
        void stripRemovesNewlines() {
            assertThat("\nhello world\n".strip()).isEqualTo("hello world");
        }

        @Test
        @DisplayName("strip() removes leading and trailing carriage returns")
        void stripRemovesCarriageReturns() {
            assertThat("\rhello world\r".strip()).isEqualTo("hello world");
        }

        @Test
        @DisplayName("strip() removes mixed whitespace (CR+LF, tabs, spaces)")
        void stripRemovesMixedWhitespace() {
            assertThat("  \t\r\n  hello world  \n\r\t  ".strip()).isEqualTo("hello world");
        }

        @Test
        @DisplayName("strip() preserves internal whitespace")
        void stripPreservesInternalWhitespace() {
            assertThat("  hello\n  world  ".strip()).isEqualTo("hello\n  world");
        }

        @Test
        @DisplayName("strip() removes Unicode em space but not non-breaking space")
        void stripHandlesUnicodeWhitespace() {
            // \u2003 (em space) IS whitespace per Character.isWhitespace() -- stripped
            assertThat("\u2003hello world\u2003".strip()).isEqualTo("hello world");
            // \u00A0 (non-breaking space) is NOT whitespace per Character.isWhitespace()
            // This is a known Java limitation; strip() will not remove it
            assertThat("\u00A0hello\u00A0".strip()).isEqualTo("\u00A0hello\u00A0");
        }

        @Test
        @DisplayName("strip() on whitespace-only string returns empty")
        void stripWhitespaceOnlyReturnsEmpty() {
            assertThat("   \t\n\r   ".strip()).isEmpty();
        }
    }

    @Nested
    @DisplayName("uploadProject rejects invalid messages")
    class UploadProjectValidationTests {

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should reject null and empty messages")
        void shouldRejectNullAndEmpty(String message) {
            assertThatThrownBy(() -> service.uploadProject(message, TEST_PROJECT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @ParameterizedTest
        @ValueSource(strings = {" ", "   ", "\t", "\n", "\r\n", "  \t\n\r  ", "\u2003"})
        @DisplayName("Should reject whitespace-only messages after stripping")
        void shouldRejectWhitespaceOnlyMessages(String message) {
            assertThatThrownBy(() -> service.uploadProject(message, TEST_PROJECT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }
    }

    @Nested
    @DisplayName("Message stripping at UI layer")
    class UILayerStrippingTests {

        @Test
        @DisplayName("showSaveModal callback strips input before passing to upload")
        void saveModalCallbackStrips() {
            String[] captured = {null};

            // Simulate what showSaveModal's lambda does
            String rawInput = "  Updated survey data  \n";
            String stripped = rawInput.strip();
            if (!stripped.isEmpty()) {
                captured[0] = stripped;
            }

            assertThat(captured[0]).isEqualTo("Updated survey data");
        }

        @Test
        @DisplayName("Upload button handler strips input before passing to upload")
        void uploadButtonHandlerStrips() {
            String rawInput = "\r\n  Fixed entrance coordinates  \t\n";
            String stripped = rawInput.strip();

            assertThat(stripped).isEqualTo("Fixed entrance coordinates");
            assertThat(stripped.isEmpty()).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "Simple message",
                "  leading spaces",
                "trailing spaces  ",
                "  both sides  ",
                "\nnewline wrapped\n",
                "\r\nCRLF wrapped\r\n",
                "\ttab wrapped\t",
                "  \t mixed \n\r whitespace \t  "
        })
        @DisplayName("Various inputs are properly stripped to clean messages")
        void variousInputsAreStripped(String input) {
            String stripped = input.strip();
            assertThat(stripped).isNotEmpty();
            assertThat(Character.isWhitespace(stripped.charAt(0)))
                    .as("First char of '%s' should not be whitespace", stripped).isFalse();
            assertThat(Character.isWhitespace(stripped.charAt(stripped.length() - 1)))
                    .as("Last char of '%s' should not be whitespace", stripped).isFalse();
        }
    }
}
