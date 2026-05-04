package org.speleodb.ariane.plugin.speleodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for hostname handling and URL normalization in {@link SpeleoDBService}.
 * Drives the production {@link SpeleoDBService#resolveInstanceUrl(String)} static method
 * directly so production behavior cannot drift from test expectations.
 */
class SpeleoDBHostnameHandlingTest {

    @Test
    @DisplayName("Should handle basic hostnames without protocol")
    void shouldHandleBasicHostnames() {
        // Public hostnames -> https://
        assertEquals("https://www.speleodb.org", SpeleoDBService.resolveInstanceUrl("www.speleodb.org"));
        assertEquals("https://stage.speleodb.org", SpeleoDBService.resolveInstanceUrl("stage.speleodb.org"));
        assertEquals("https://api.example.com", SpeleoDBService.resolveInstanceUrl("api.example.com"));

        // Local hostnames -> http://
        assertEquals("http://localhost", SpeleoDBService.resolveInstanceUrl("localhost"));
        assertEquals("http://localhost:8000", SpeleoDBService.resolveInstanceUrl("localhost:8000"));
        assertEquals("http://127.0.0.1:8000", SpeleoDBService.resolveInstanceUrl("127.0.0.1:8000"));
        assertEquals("http://192.168.1.100:3000", SpeleoDBService.resolveInstanceUrl("192.168.1.100:3000"));
    }

    @Test
    @DisplayName("Should handle hostnames with HTTP prefix")
    void shouldHandleHttpPrefix() {
        // Public hostnames with http:// prefix -> still https://
        assertEquals("https://www.speleodb.org", SpeleoDBService.resolveInstanceUrl("http://www.speleodb.org"));
        assertEquals("https://stage.speleodb.org", SpeleoDBService.resolveInstanceUrl("http://stage.speleodb.org"));
        assertEquals("https://api.example.com", SpeleoDBService.resolveInstanceUrl("http://api.example.com"));

        // Local hostnames with http:// prefix -> http://
        assertEquals("http://localhost", SpeleoDBService.resolveInstanceUrl("http://localhost"));
        assertEquals("http://localhost:8000", SpeleoDBService.resolveInstanceUrl("http://localhost:8000"));
        assertEquals("http://127.0.0.1:8000", SpeleoDBService.resolveInstanceUrl("http://127.0.0.1:8000"));
        assertEquals("http://192.168.1.100:3000", SpeleoDBService.resolveInstanceUrl("http://192.168.1.100:3000"));
    }

    @Test
    @DisplayName("Should handle hostnames with HTTPS prefix")
    void shouldHandleHttpsPrefix() {
        // Public hostnames with https:// prefix -> https:// (preserved)
        assertEquals("https://www.speleodb.org", SpeleoDBService.resolveInstanceUrl("https://www.speleodb.org"));
        assertEquals("https://stage.speleodb.org", SpeleoDBService.resolveInstanceUrl("https://stage.speleodb.org"));
        assertEquals("https://api.example.com", SpeleoDBService.resolveInstanceUrl("https://api.example.com"));

        // Local hostnames with https:// prefix -> http:// (force http for loopback/private)
        assertEquals("http://localhost", SpeleoDBService.resolveInstanceUrl("https://localhost"));
        assertEquals("http://localhost:8000", SpeleoDBService.resolveInstanceUrl("https://localhost:8000"));
        assertEquals("http://127.0.0.1:8000", SpeleoDBService.resolveInstanceUrl("https://127.0.0.1:8000"));
        assertEquals("http://192.168.1.100:3000", SpeleoDBService.resolveInstanceUrl("https://192.168.1.100:3000"));
    }

    @Test
    @DisplayName("Should handle hostnames with trailing slashes")
    void shouldHandleTrailingSlashes() {
        assertEquals("https://www.speleodb.org", SpeleoDBService.resolveInstanceUrl("www.speleodb.org/"));
        assertEquals("https://stage.speleodb.org", SpeleoDBService.resolveInstanceUrl("stage.speleodb.org/"));
        assertEquals("https://api.example.com", SpeleoDBService.resolveInstanceUrl("api.example.com/"));

        assertEquals("http://localhost", SpeleoDBService.resolveInstanceUrl("localhost/"));
        assertEquals("http://localhost:8000", SpeleoDBService.resolveInstanceUrl("localhost:8000/"));
        assertEquals("http://127.0.0.1:8000", SpeleoDBService.resolveInstanceUrl("127.0.0.1:8000/"));
        assertEquals("http://192.168.1.100:3000", SpeleoDBService.resolveInstanceUrl("192.168.1.100:3000/"));
    }

    @Test
    @DisplayName("Should handle hostnames with both prefix and trailing slash")
    void shouldHandlePrefixAndTrailingSlash() {
        assertEquals("https://www.speleodb.org", SpeleoDBService.resolveInstanceUrl("http://www.speleodb.org/"));
        assertEquals("https://stage.speleodb.org", SpeleoDBService.resolveInstanceUrl("http://stage.speleodb.org/"));
        assertEquals("https://www.speleodb.org", SpeleoDBService.resolveInstanceUrl("https://www.speleodb.org/"));
        assertEquals("https://stage.speleodb.org", SpeleoDBService.resolveInstanceUrl("https://stage.speleodb.org/"));

        assertEquals("http://localhost:8000", SpeleoDBService.resolveInstanceUrl("http://localhost:8000/"));
        assertEquals("http://127.0.0.1:8000", SpeleoDBService.resolveInstanceUrl("http://127.0.0.1:8000/"));
        assertEquals("http://localhost:8000", SpeleoDBService.resolveInstanceUrl("https://localhost:8000/"));
        assertEquals("http://127.0.0.1:8000", SpeleoDBService.resolveInstanceUrl("https://127.0.0.1:8000/"));
    }

    @Test
    @DisplayName("Should handle edge cases with multiple trailing slashes")
    void shouldHandleMultipleTrailingSlashes() {
        assertEquals("https://www.speleodb.org", SpeleoDBService.resolveInstanceUrl("www.speleodb.org//"));
        assertEquals("https://www.speleodb.org", SpeleoDBService.resolveInstanceUrl("www.speleodb.org///"));
        assertEquals("http://localhost:8000", SpeleoDBService.resolveInstanceUrl("http://localhost:8000//"));
        assertEquals("https://stage.speleodb.org", SpeleoDBService.resolveInstanceUrl("https://stage.speleodb.org////"));
    }

    @Test
    @DisplayName("Should handle private IP ranges correctly")
    void shouldHandlePrivateIpRanges() {
        // 10.x.x.x range
        assertEquals("http://10.0.0.1", SpeleoDBService.resolveInstanceUrl("10.0.0.1"));
        assertEquals("http://10.255.255.255:8080", SpeleoDBService.resolveInstanceUrl("10.255.255.255:8080"));

        // 172.16-31.x.x range
        assertEquals("http://172.16.0.1", SpeleoDBService.resolveInstanceUrl("172.16.0.1"));
        assertEquals("http://172.31.255.255:3000", SpeleoDBService.resolveInstanceUrl("172.31.255.255:3000"));

        // 192.168.x.x range
        assertEquals("http://192.168.0.1", SpeleoDBService.resolveInstanceUrl("192.168.0.1"));
        assertEquals("http://192.168.255.255:9000", SpeleoDBService.resolveInstanceUrl("192.168.255.255:9000"));
    }

    @Test
    @DisplayName("Should handle public IP addresses correctly")
    void shouldHandlePublicIpAddresses() {
        assertEquals("https://8.8.8.8", SpeleoDBService.resolveInstanceUrl("8.8.8.8"));
        assertEquals("https://1.1.1.1:443", SpeleoDBService.resolveInstanceUrl("1.1.1.1:443"));
        assertEquals("https://8.8.8.8", SpeleoDBService.resolveInstanceUrl("https://8.8.8.8/"));
    }

    @Test
    @DisplayName("Should handle null and blank inputs gracefully")
    void shouldHandleNullAndEmptyInputs() {
        assertNull(SpeleoDBService.resolveInstanceUrl(null));
        assertEquals("https://", SpeleoDBService.resolveInstanceUrl(""));
        assertEquals("https://", SpeleoDBService.resolveInstanceUrl("   "));
    }

    @Test
    @DisplayName("Should handle various port numbers correctly")
    void shouldHandlePortNumbers() {
        assertEquals("https://www.speleodb.org:80", SpeleoDBService.resolveInstanceUrl("www.speleodb.org:80"));
        assertEquals("https://www.speleodb.org:443", SpeleoDBService.resolveInstanceUrl("www.speleodb.org:443"));
        assertEquals("http://localhost:80", SpeleoDBService.resolveInstanceUrl("localhost:80"));
        assertEquals("http://localhost:443", SpeleoDBService.resolveInstanceUrl("localhost:443"));
        assertEquals("https://api.example.com:8080", SpeleoDBService.resolveInstanceUrl("api.example.com:8080"));
        assertEquals("http://localhost:3000", SpeleoDBService.resolveInstanceUrl("localhost:3000"));
    }

    @Test
    @DisplayName("Should normalize hostname casing per RFC 7230")
    void shouldLowercaseHostnames() {
        assertEquals("https://www.speleodb.org", SpeleoDBService.resolveInstanceUrl("WWW.SPELEODB.ORG"));
        assertEquals("http://localhost:8000", SpeleoDBService.resolveInstanceUrl("LocalHost:8000"));
        assertEquals("https://www.speleodb.org", SpeleoDBService.resolveInstanceUrl("HTTPS://www.SPELEODB.org"));
    }
}
