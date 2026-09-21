package org.speleodb.ariane.plugin.speleodb;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.MESSAGES;
import org.speleodb.ariane.plugin.speleodb.SpeleoDBConstants.MULTIPART;

/**
 * Helper class for building HTTP multipart form data request bodies.
 * Enables file uploads in HTTP requests while following standard multipart encoding.
 *
 * Usage:
 * HTTPRequestMultipartBody body = new HTTPRequestMultipartBody.Builder()
 *     .addPart("message", "Upload message")
 *     .addPart("file", file, "application/octet-stream", "filename.txt")
 *     .build();
 */
public class HTTPRequestMultipartBody {

    private final String boundary;
    private final byte[] body;

    private HTTPRequestMultipartBody(String boundary, byte[] body) {
        this.boundary = boundary;
        this.body = body;
    }

    public String getContentType() {
        return MULTIPART.CONTENT_TYPE_PREFIX + this.getBoundary();
    }

    public String getBoundary() { return this.boundary; }
    public byte[] getBody() { return this.body; }

    // Multipart constants for byte arrays
    private static final byte[] CRLF = MULTIPART.CRLF.getBytes(StandardCharsets.UTF_8);
    private static final byte[] CONTENT_TYPE_PREFIX = MULTIPART.CONTENT_TYPE_HEADER.getBytes(StandardCharsets.UTF_8);
    private static final byte[] OCTET_STREAM_HEADER = MULTIPART.OCTET_STREAM_HEADER.getBytes(StandardCharsets.UTF_8);

    /**
     * Builder class for constructing multipart request bodies
     */
    public static class Builder {
        private final String boundary;
        private final List<PartRecord> parts;

        public Builder() {
            this.boundary = generateBoundary();
            this.parts = new ArrayList<>();
        }

        /**
         * Adds a simple text field part to the multipart body
         */
        public Builder addPart(String fieldName, String value) {
            parts.add(new PartRecord(fieldName, value, null, null, null));
            return this;
        }

        /**
         * Adds a file part to the multipart body
         */
        public Builder addPart(String fieldName, File file, String contentType, String filename) {
            parts.add(new PartRecord(fieldName, null, file, contentType, filename));
            return this;
        }

        /** Adds already captured binary content, defensively owned by this builder. */
        public Builder addPart(String fieldName, byte[] bytes, String contentType, String filename) {
            PartRecord part = new PartRecord(fieldName, null, null, contentType, filename);
            part.bytes = bytes.clone();
            parts.add(part);
            return this;
        }

        /**
         * Builds the final HTTPRequestMultipartBody instance
         */
        public HTTPRequestMultipartBody build() throws IOException {
            byte[] body = buildBody();
            return new HTTPRequestMultipartBody(boundary, body);
        }

        /**
         * Generates a unique boundary string for this multipart request
         */
        private String generateBoundary() {
            return MULTIPART.BOUNDARY_PREFIX + UUID.randomUUID().toString().replace(MULTIPART.DASH, "");
        }

        /**
         * Builds the complete multipart body as a byte array
         */
        private byte[] buildBody() throws IOException {
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                for (PartRecord part : parts) {
                    writePart(out, part);
                }
                writeClosingBoundary(out);
                return out.toByteArray();
            } catch (IOException e) {
                throw new IOException(MESSAGES.ERROR_BUILDING_MULTIPART_BODY, e);
            }
        }

        /**
         * Writes a single part to the output stream
         */
        @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
        private void writePart(ByteArrayOutputStream out, PartRecord record) throws IOException {
            try {
                // Write part header
                StringBuilder sb = new StringBuilder();
                sb.append(MULTIPART.BOUNDARY_START).append(boundary).append(MULTIPART.CRLF)
                    .append(MULTIPART.CONTENT_DISPOSITION_FORM_DATA).append(record.getFieldName());

                if (record.getFilename() != null) {
                    sb.append(MULTIPART.FILENAME_PARAM).append(record.getFilename());
                }
                sb.append(MULTIPART.QUOTE_CRLF);

                out.write(sb.toString().getBytes(StandardCharsets.UTF_8));

                // Write content based on type
                if (record.isFile()) {
                    writeFileContent(out, record);
                } else {
                    writeTextContent(out, record);
                }

                out.write(CRLF);
            } catch (Exception e) {
                throw new IOException(MESSAGES.ERROR_WRITING_MULTIPART_PART + record.getFieldName(), e);
            }
        }

        /**
         * Writes file content to the output stream
         */
        private void writeFileContent(ByteArrayOutputStream out, PartRecord record) throws IOException {
            try {
                // Write content type if specified
                if (record.getContentType() != null) {
                    out.write(CONTENT_TYPE_PREFIX);
                    out.write(record.getContentType().getBytes(StandardCharsets.UTF_8));
                    out.write(CRLF);
                    out.write(CRLF); // Empty line after content-type header
                } else {
                    out.write(OCTET_STREAM_HEADER); // Already includes \r\n\r\n
                }

                // Write file content directly - no extra CRLF needed
                byte[] fileBytes = record.bytes != null ? record.bytes : Files.readAllBytes(record.getFile().toPath());
                out.write(fileBytes);
            } catch (IOException copyException) {
                throw new IOException(MESSAGES.ERROR_COPYING_FILE_CONTENT + record.getFilename(), copyException);
            }
        }

        /**
         * Writes text content to the output stream
         */
        private void writeTextContent(ByteArrayOutputStream out, PartRecord record) throws IOException {
            out.write(CRLF);
            out.write(record.getValue().getBytes(StandardCharsets.UTF_8));
        }

        /**
         * Writes the closing boundary to finish the multipart body
         */
        private void writeClosingBoundary(ByteArrayOutputStream out) throws IOException {
            out.write((MULTIPART.BOUNDARY_START + boundary + MULTIPART.BOUNDARY_END).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Internal record class for storing part information
     */
    private static class PartRecord {
        private final String fieldName;
        private final String value;
        private final File file;
        private final String contentType;
        private final String filename;
        private byte[] bytes;

        private PartRecord(String fieldName, String value, File file, String contentType, String filename) {
            this.fieldName = fieldName;
            this.value = value;
            this.file = file;
            this.contentType = contentType;
            this.filename = filename;
        }

        private String getFieldName() { return fieldName; }
        private String getValue() { return value; }
        private File getFile() { return file; }
        private String getContentType() { return contentType; }
        private String getFilename() { return filename; }

        private boolean isFile() { return file != null || bytes != null; }
    }
}
