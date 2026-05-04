package org.speleodb.ariane.plugin.speleodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Direct unit tests for {@link SpeleoDBService#parseV2ErrorMessage(String)} and
 * {@link SpeleoDBService#formatStatusError(String, int, String)}. These pin the
 * v2 error-envelope contract independent of any HTTP plumbing -- they will catch
 * a regression in the parser even if every per-endpoint WireMock suite is broken.
 */
@DisplayName("v2 error envelope parser")
class SpeleoDBServiceErrorParsingTest {

    // ====================================================================== //
    //                          parseV2ErrorMessage                           //
    // ====================================================================== //

    @Nested
    @DisplayName("parseV2ErrorMessage(...)")
    class ParseV2ErrorMessageTests {

        @Test
        @DisplayName("null body returns empty Optional")
        void nullBody() {
            assertThat(SpeleoDBService.parseV2ErrorMessage(null)).isEmpty();
        }

        @Test
        @DisplayName("empty/whitespace body returns empty Optional")
        void blankBody() {
            assertThat(SpeleoDBService.parseV2ErrorMessage("")).isEmpty();
            assertThat(SpeleoDBService.parseV2ErrorMessage("   ")).isEmpty();
            assertThat(SpeleoDBService.parseV2ErrorMessage("\n\t  \n")).isEmpty();
        }

        @Test
        @DisplayName("non-JSON garbage returns empty Optional (parser tolerates it)")
        void garbageBody() {
            assertThat(SpeleoDBService.parseV2ErrorMessage("not json")).isEmpty();
            assertThat(SpeleoDBService.parseV2ErrorMessage("{ broken")).isEmpty();
            assertThat(SpeleoDBService.parseV2ErrorMessage("<html>500</html>")).isEmpty();
        }

        @Test
        @DisplayName("JSON array root returns empty Optional (we only parse object roots)")
        void arrayRoot() {
            assertThat(SpeleoDBService.parseV2ErrorMessage("[\"a\", \"b\"]")).isEmpty();
        }

        @Test
        @DisplayName("empty JSON object returns empty Optional")
        void emptyObject() {
            assertThat(SpeleoDBService.parseV2ErrorMessage("{}")).isEmpty();
        }

        @Test
        @DisplayName("object with unrelated keys returns empty Optional")
        void unrelatedKeys() {
            assertThat(SpeleoDBService.parseV2ErrorMessage("{\"foo\":\"bar\",\"baz\":42}")).isEmpty();
        }

        @Test
        @DisplayName("{\"error\":\"\"} (blank value) returns empty Optional")
        void blankErrorString() {
            assertThat(SpeleoDBService.parseV2ErrorMessage("{\"error\":\"\"}")).isEmpty();
            assertThat(SpeleoDBService.parseV2ErrorMessage("{\"error\":\"   \"}")).isEmpty();
        }

        @Test
        @DisplayName("{\"error\":\"single message\"} returns the message")
        void singleErrorString() {
            assertThat(SpeleoDBService.parseV2ErrorMessage("{\"error\":\"name taken\"}"))
                    .contains("name taken");
        }

        @Test
        @DisplayName("{\"error\": null} (null value) returns empty Optional (not a STRING)")
        void nullErrorValue() {
            assertThat(SpeleoDBService.parseV2ErrorMessage("{\"error\":null}")).isEmpty();
        }

        @Test
        @DisplayName("{\"errors\":[]} (empty array) returns empty Optional")
        void emptyErrorsArray() {
            assertThat(SpeleoDBService.parseV2ErrorMessage("{\"errors\":[]}")).isEmpty();
        }

        @Test
        @DisplayName("{\"errors\":[\"a\",\"b\",\"c\"]} joins entries with '; '")
        void listOfStrings() {
            assertThat(SpeleoDBService.parseV2ErrorMessage("{\"errors\":[\"a\",\"b\",\"c\"]}"))
                    .contains("a; b; c");
        }

        @Test
        @DisplayName("blank/empty string entries are skipped during join")
        void blankEntriesSkipped() {
            assertThat(SpeleoDBService.parseV2ErrorMessage("{\"errors\":[\"a\",\"\",\"  \",\"b\"]}"))
                    .contains("a; b");
        }

        @Test
        @DisplayName("JSON null entries are skipped during join")
        void jsonNullEntriesSkipped() {
            assertThat(SpeleoDBService.parseV2ErrorMessage("{\"errors\":[\"a\",null,\"b\"]}"))
                    .contains("a; b");
        }

        @Test
        @DisplayName("{\"errors\":[{\"detail\":\"d\"}]} extracts the detail field")
        void listOfDetailObjects() {
            assertThat(SpeleoDBService.parseV2ErrorMessage("{\"errors\":[{\"detail\":\"d\"}]}"))
                    .contains("d");
        }

        @Test
        @DisplayName("{\"errors\":[{\"message\":\"m\"}]} extracts the message field")
        void listOfMessageObjects() {
            assertThat(SpeleoDBService.parseV2ErrorMessage("{\"errors\":[{\"message\":\"m\"}]}"))
                    .contains("m");
        }

        @Test
        @DisplayName("{\"errors\":[{\"error\":\"e\"}]} extracts the error field")
        void listOfErrorObjects() {
            assertThat(SpeleoDBService.parseV2ErrorMessage("{\"errors\":[{\"error\":\"e\"}]}"))
                    .contains("e");
        }

        @Test
        @DisplayName("Object entry with unknown keys falls back to JSON serialization (info preserved)")
        void unknownObjectFallsBackToToString() {
            Optional<String> parsed = SpeleoDBService.parseV2ErrorMessage("{\"errors\":[{\"unknown\":\"x\"}]}");
            assertThat(parsed).isPresent();
            assertThat(parsed.get()).contains("unknown").contains("x");
        }

        @Test
        @DisplayName("Mixed shape: single 'error' takes precedence over 'errors' (documented contract)")
        void mixedShapePrefersSingle() {
            assertThat(SpeleoDBService.parseV2ErrorMessage("{\"error\":\"single\",\"errors\":[\"list\"]}"))
                    .contains("single");
        }

        @Test
        @DisplayName("{\"non_field_errors\":[\"bad\"]} (DRF default) joins entries like 'errors'")
        void nonFieldErrorsArray() {
            assertThat(SpeleoDBService.parseV2ErrorMessage("{\"non_field_errors\":[\"bad\",\"worse\"]}"))
                    .contains("bad; worse");
        }

        @Test
        @DisplayName("Mixed shape: 'errors' takes precedence over 'non_field_errors'")
        void errorsTakesPrecedenceOverNonFieldErrors() {
            assertThat(SpeleoDBService.parseV2ErrorMessage(
                            "{\"errors\":[\"from-errors\"],\"non_field_errors\":[\"from-nfe\"]}"))
                    .hasValueSatisfying(joined -> assertThat(joined)
                            .contains("from-errors")
                            .doesNotContain("from-nfe"));
        }

        @Test
        @DisplayName("Mixed types in errors array (string + object) are both surfaced")
        void mixedEntryTypes() {
            assertThat(SpeleoDBService.parseV2ErrorMessage(
                            "{\"errors\":[\"alpha\",{\"detail\":\"beta\"},{\"unknown\":\"gamma\"}]}"))
                    .hasValueSatisfying(joined -> assertThat(joined)
                            .contains("alpha")
                            .contains("beta")
                            .contains("gamma"));
        }
    }

    // ====================================================================== //
    //                            formatStatusError                           //
    // ====================================================================== //

    @Nested
    @DisplayName("formatStatusError(...)")
    class FormatStatusErrorTests {

        @Test
        @DisplayName("Empty body produces only the prefix + status code")
        void emptyBodyJustStatus() {
            assertThat(SpeleoDBService.formatStatusError("Failed: ", 500, ""))
                    .isEqualTo("Failed: 500");
        }

        @Test
        @DisplayName("Null body produces only the prefix + status code")
        void nullBodyJustStatus() {
            assertThat(SpeleoDBService.formatStatusError("Failed: ", 503, null))
                    .isEqualTo("Failed: 503");
        }

        @Test
        @DisplayName("Garbage body produces only the prefix + status code")
        void garbageBodyJustStatus() {
            assertThat(SpeleoDBService.formatStatusError("Failed: ", 502, "<html>nope</html>"))
                    .isEqualTo("Failed: 502");
        }

        @Test
        @DisplayName("Single-error body appends ' - <detail>'")
        void singleErrorAppended() {
            assertThat(SpeleoDBService.formatStatusError("Auth failed: ", 401, "{\"error\":\"Invalid token\"}"))
                    .isEqualTo("Auth failed: 401 - Invalid token");
        }

        @Test
        @DisplayName("List-of-strings body appends ' - <joined>'")
        void listOfStringsAppended() {
            assertThat(SpeleoDBService.formatStatusError("Validation: ", 400,
                            "{\"errors\":[\"name required\",\"country invalid\"]}"))
                    .isEqualTo("Validation: 400 - name required; country invalid");
        }

        @Test
        @DisplayName("List-of-detail-objects body appends ' - <detail>'")
        void listOfDetailObjectsAppended() {
            assertThat(SpeleoDBService.formatStatusError("Forbidden: ", 403,
                            "{\"errors\":[{\"detail\":\"Permission denied\"}]}"))
                    .isEqualTo("Forbidden: 403 - Permission denied");
        }

        @Test
        @DisplayName("Prefix is preserved verbatim (caller is responsible for trailing space)")
        void prefixVerbatim() {
            assertThat(SpeleoDBService.formatStatusError("X", 1, "")).isEqualTo("X1");
            assertThat(SpeleoDBService.formatStatusError("", 200, "")).isEqualTo("200");
        }
    }
}
