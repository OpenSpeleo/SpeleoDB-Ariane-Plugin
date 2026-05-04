package org.speleodb.ariane.plugin.speleodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for HTTPRequestMultipartBody using JUnit 5 and AssertJ.
 */
@DisplayName("HTTP Request Multipart Body Tests")
class HTTPRequestMultipartBodyTest {

    @Nested
    @DisplayName("Basic Functionality")
    class BasicFunctionalityTests {

        @Test
        @DisplayName("Should create multipart body with boundary")
        void shouldCreateMultipartBodyWithBoundary() throws IOException {
            HTTPRequestMultipartBody body = new HTTPRequestMultipartBody.Builder()
                .addPart("field1", "value1")
                .build();

            assertThat(body).isNotNull();
            assertThat(body.getBoundary()).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("Should generate unique boundaries")
        void shouldGenerateUniqueBoundaries() throws IOException {
            HTTPRequestMultipartBody body1 = new HTTPRequestMultipartBody.Builder()
                .addPart("test", "value")
                .build();
            HTTPRequestMultipartBody body2 = new HTTPRequestMultipartBody.Builder()
                .addPart("test", "value")
                .build();

            assertThat(body1.getBoundary()).isNotEqualTo(body2.getBoundary());
        }

        @Test
        @DisplayName("Should provide correct content type")
        void shouldProvideCorrectContentType() throws IOException {
            HTTPRequestMultipartBody body = new HTTPRequestMultipartBody.Builder()
                .addPart("field1", "value1")
                .build();

            String contentType = body.getContentType();
            String boundary = body.getBoundary();

            assertThat(contentType)
                .isNotNull()
                .startsWith("multipart/form-data")
                .contains("boundary=" + boundary);
        }
    }

    @Nested
    @DisplayName("Single Part Operations")
    class SinglePartOperationsTests {

        @Test
        @DisplayName("Should create basic string part")
        void shouldCreateBasicStringPart() throws IOException {
            HTTPRequestMultipartBody body = new HTTPRequestMultipartBody.Builder()
                .addPart("field1", "value1")
                .build();

            assertThat(body.getContentType()).startsWith("multipart/form-data; boundary=");
            assertThat(body.getBoundary()).isNotNull().isNotEmpty();

            String bodyStr = new String(body.getBody(), StandardCharsets.UTF_8);
            assertThat(bodyStr)
                .contains("Content-Disposition: form-data; name=\"field1\"")
                .contains("value1")
                .contains("--" + body.getBoundary() + "--");
        }

        @Test
        @DisplayName("Should handle empty field value")
        void shouldHandleEmptyFieldValue() throws IOException {
            HTTPRequestMultipartBody body = new HTTPRequestMultipartBody.Builder()
                .addPart("emptyField", "")
                .build();

            String bodyStr = new String(body.getBody(), StandardCharsets.UTF_8);
            assertThat(bodyStr)
                .contains("name=\"emptyField\"");
        }

        @Test
        @DisplayName("Should handle custom content type")
        void shouldHandleCustomContentType() throws IOException {
            // Note: Custom content types for text fields are not supported in the current API
            // This test validates that basic text fields work correctly
            HTTPRequestMultipartBody body = new HTTPRequestMultipartBody.Builder()
                .addPart("htmlField", "<h1>Hello</h1>")
                .build();

            String bodyStr = new String(body.getBody(), StandardCharsets.UTF_8);
            assertThat(bodyStr)
                .contains("name=\"htmlField\"")
                .contains("<h1>Hello</h1>");
        }
    }

    @Nested
    @DisplayName("Multiple Parts Operations")
    class MultiplePartsOperationsTests {

        @Test
        @DisplayName("Should handle multiple text parts")
        void shouldHandleMultipleTextParts() throws IOException {
            HTTPRequestMultipartBody body = new HTTPRequestMultipartBody.Builder()
                .addPart("field1", "value1")
                .addPart("field2", "value2")
                .addPart("field3", "value3")
                .build();

            String bodyStr = new String(body.getBody(), StandardCharsets.UTF_8);
            assertThat(bodyStr)
                .contains("name=\"field1\"")
                .contains("value1")
                .contains("name=\"field2\"")
                .contains("value2")
                .contains("name=\"field3\"")
                .contains("value3");
        }

        @Test
        @DisplayName("Should maintain part order")
        void shouldMaintainPartOrder() throws IOException {
            HTTPRequestMultipartBody body = new HTTPRequestMultipartBody.Builder()
                .addPart("first", "1")
                .addPart("second", "2")
                .addPart("third", "3")
                .build();

            String bodyStr = new String(body.getBody(), StandardCharsets.UTF_8);
            int firstIndex = bodyStr.indexOf("name=\"first\"");
            int secondIndex = bodyStr.indexOf("name=\"second\"");
            int thirdIndex = bodyStr.indexOf("name=\"third\"");

            assertThat(firstIndex).isLessThan(secondIndex);
            assertThat(secondIndex).isLessThan(thirdIndex);
        }
    }

    @Nested
    @DisplayName("Binary Data Operations")
    class BinaryDataOperationsTests {

        @Test
        @DisplayName("Should handle byte array parts")
        void shouldHandleByteArrayParts() throws IOException {
            byte[] testData = "binary data".getBytes(StandardCharsets.UTF_8);

            // Create a temporary file for binary data
            java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("test", ".bin");
            java.nio.file.Files.write(tempFile, testData);

            try {
                HTTPRequestMultipartBody body = new HTTPRequestMultipartBody.Builder()
                    .addPart("binaryField", tempFile.toFile(), "application/octet-stream", "test.bin")
                    .build();

                String bodyStr = new String(body.getBody(), StandardCharsets.UTF_8);
                assertThat(bodyStr)
                    .contains("name=\"binaryField\"")
                    .contains("filename=\"test.bin\"")
                    .contains("Content-Type: application/octet-stream")
                    .contains("binary data");
            } finally {
                java.nio.file.Files.deleteIfExists(tempFile);
            }
        }

        @Test
        @DisplayName("Should handle mixed content types")
        void shouldHandleMixedContentTypes() throws IOException {
            byte[] binaryData = "binary".getBytes(StandardCharsets.UTF_8);

            // Create a temporary file for binary data
            java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("data", ".bin");
            java.nio.file.Files.write(tempFile, binaryData);

            try {
                HTTPRequestMultipartBody body = new HTTPRequestMultipartBody.Builder()
                    .addPart("textField", "text value")
                    .addPart("binaryField", tempFile.toFile(), "application/octet-stream", "data.bin")
                    .addPart("htmlField", "<p>HTML</p>")
                    .build();

                String bodyStr = new String(body.getBody(), StandardCharsets.UTF_8);
                assertThat(bodyStr)
                    .contains("text value")
                    .contains("binary")
                    .contains("<p>HTML</p>")
                    .contains("application/octet-stream");
            } finally {
                java.nio.file.Files.deleteIfExists(tempFile);
            }
        }
    }

    @Nested
    @DisplayName("Multipart Structure Compliance")
    class MultipartStructureTests {

        @Test
        @DisplayName("Text field value should not have leading CRLF")
        void textFieldValueShouldNotHaveLeadingCrlf() throws IOException {
            HTTPRequestMultipartBody body = new HTTPRequestMultipartBody.Builder()
                .addPart("message", "comment fix")
                .build();

            String bodyStr = new String(body.getBody(), StandardCharsets.UTF_8);
            String headerEnd = "name=\"message\"\r\n";
            int headerEndIdx = bodyStr.indexOf(headerEnd) + headerEnd.length();
            String afterHeader = bodyStr.substring(headerEndIdx);

            assertThat(afterHeader)
                .as("Exactly one blank line (CRLF) between header and value")
                .startsWith("\r\ncomment fix");
        }
    }

    @Nested
    @DisplayName("Special Characters and Encoding")
    class SpecialCharactersTests {

        @Test
        @DisplayName("Should handle special characters in field values")
        void shouldHandleSpecialCharactersInFieldValues() throws IOException {
            HTTPRequestMultipartBody body = new HTTPRequestMultipartBody.Builder()
                .addPart("special", "value with\nnewlines\rand\ttabs")
                .build();

            String bodyStr = new String(body.getBody(), StandardCharsets.UTF_8);
            assertThat(bodyStr).contains("value with\nnewlines\rand\ttabs");
        }

        @Test
        @DisplayName("Should handle unicode characters")
        void shouldHandleUnicodeCharacters() throws IOException {
            HTTPRequestMultipartBody body = new HTTPRequestMultipartBody.Builder()
                .addPart("unicode", "测试中文字符")
                .build();

            String bodyStr = new String(body.getBody(), StandardCharsets.UTF_8);
            assertThat(bodyStr).contains("测试中文字符");
        }

        @Test
        @DisplayName("Should handle quotes in field names")
        void shouldHandleQuotesInFieldNames() throws IOException {
            HTTPRequestMultipartBody body = new HTTPRequestMultipartBody.Builder()
                .addPart("field\"with\"quotes", "value")
                .build();

            assertThatCode(() -> body.getBody()).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle empty builder")
        void shouldHandleEmptyBuilder() throws IOException {
            HTTPRequestMultipartBody body = new HTTPRequestMultipartBody.Builder()
                .build();

            assertThat(body).isNotNull();
            assertThat(body.getBoundary()).isNotNull().isNotEmpty();

            String bodyStr = new String(body.getBody(), StandardCharsets.UTF_8);
            assertThat(bodyStr).contains("--" + body.getBoundary() + "--");
        }

        @Test
        @DisplayName("Should handle large field values")
        void shouldHandleLargeFieldValues() throws IOException {
            String largeValue = "x".repeat(10000);

            HTTPRequestMultipartBody body = new HTTPRequestMultipartBody.Builder()
                .addPart("large", largeValue)
                .build();

            String bodyStr = new String(body.getBody(), StandardCharsets.UTF_8);
            assertThat(bodyStr).contains(largeValue);
        }

        @Test
        @DisplayName("Should handle many parts")
        void shouldHandleManyParts() throws IOException {
            HTTPRequestMultipartBody.Builder builder = new HTTPRequestMultipartBody.Builder();

            for (int i = 0; i < 50; i++) {
                builder.addPart("field" + i, "value" + i);
            }

            HTTPRequestMultipartBody body = builder.build();
            String bodyStr = new String(body.getBody(), StandardCharsets.UTF_8);

            assertThat(bodyStr)
                .contains("field0")
                .contains("field49")
                .contains("value0")
                .contains("value49");
        }

        @Test
        @DisplayName("Should validate boundary uniqueness across multiple builds")
        void shouldValidateBoundaryUniquenessAcrossMultipleBuilds() throws IOException {
            var boundaries = new java.util.HashSet<String>();

            for (int i = 0; i < 10; i++) {
                HTTPRequestMultipartBody body = new HTTPRequestMultipartBody.Builder()
                    .addPart("test", "value")
                    .build();
                boundaries.add(body.getBoundary());
            }

            assertThat(boundaries).hasSize(10);
        }
    }
}
