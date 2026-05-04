package org.speleodb.ariane.plugin.speleodb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Configuration loader for SpeleoDB API tests.
 *
 * <p>Resolution order for any key (pytest-env style):
 * <ol>
 *   <li>{@code .env} file (searched in CWD, the plugin module dir, and two parent dirs)</li>
 *   <li>Process environment variables ({@link System#getenv(String)})</li>
 *   <li>JVM system properties ({@link System#getProperty(String)})</li>
 * </ol>
 *
 * <p>Designed to be safe to load when no {@code .env} exists -- {@link #get(String)} simply
 * falls through to the env-var / system-property fallback. This lets CI provide credentials
 * via GitHub secrets without ever materializing a {@code .env} file. {@link
 * #validateCriticalConfig()} only throws when {@code SPELEODB_INSTANCE_URL} plus an auth
 * method cannot be resolved from any source.
 */
public class TestEnvironmentConfig {

    public static final String SPELEODB_INSTANCE_URL = "SPELEODB_INSTANCE_URL";
    public static final String SPELEODB_OAUTH_TOKEN = "SPELEODB_OAUTH_TOKEN";
    public static final String SPELEODB_EMAIL = "SPELEODB_EMAIL";
    public static final String SPELEODB_PASSWORD = "SPELEODB_PASSWORD";
    public static final String API_TEST_ENABLED = "API_TEST_ENABLED";
    public static final String API_TIMEOUT_MS = "API_TIMEOUT_MS";
    public static final String API_RETRY_COUNT = "API_RETRY_COUNT";

    private static final Map<String, String> dotenvConfig = new HashMap<>();
    private static volatile boolean configLoaded = false;
    private static String envFilePath = null;
    private static String envFileLoadError = null;

    /**
     * Lazy-load the {@code .env} file the first time anything reads config. Failure to find
     * the file is NOT fatal -- env-var / system-property fallback may still satisfy callers.
     */
    private static synchronized void loadConfiguration() {
        if (configLoaded) {
            return;
        }

        String[] searchPaths = {
            ".env",
            "org.speleodb.ariane.plugin.speleodb/.env",
            "../.env",
            "../../.env"
        };

        Path envFile = null;
        for (String searchPath : searchPaths) {
            Path candidate = Path.of(searchPath).toAbsolutePath().normalize();
            if (Files.exists(candidate)) {
                envFile = candidate;
                envFilePath = candidate.toString();
                break;
            }
        }

        if (envFile == null) {
            // Not fatal -- env vars / system properties may still provide everything we need.
            envFileLoadError = ".env file not found in any of: "
                + String.join(", ", searchPaths);
            configLoaded = true;
            return;
        }

        try {
            System.out.println("OK: Loading .env file from: " + envFile);

            Files.lines(envFile)
                .filter(line -> !line.trim().isEmpty())
                .filter(line -> !line.trim().startsWith("#"))
                .forEach(line -> {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String value = parts[1].trim();

                        if ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length() - 1);
                        }

                        if (SPELEODB_INSTANCE_URL.equals(key)) {
                            value = normalizeInstanceUrl(value);
                        }

                        dotenvConfig.put(key, value);
                    }
                });

            configLoaded = true;
            System.out.println("OK: .env file loaded with " + dotenvConfig.size() + " entries");

        } catch (IOException e) {
            envFileLoadError = "Failed to read .env file at " + envFile + ": " + e.getMessage();
            configLoaded = true;
            System.err.println("WARN: " + envFileLoadError);
        }
    }

    /**
     * Resolve a value with fallback order: .env → env var → system property → null.
     *
     * <p>Env-var and sysprop values are trimmed of leading/trailing whitespace
     * (including CR/LF) before being returned. GitHub Actions secrets pasted from
     * a clipboard frequently carry a trailing newline, which would otherwise survive
     * into HTTP headers and trip
     * {@link java.net.http.HttpRequest.Builder#setHeader(String, String)}'s RFC 7230
     * validation with a confusing {@code IllegalArgumentException}. Instance URL
     * values are additionally normalized (lower-cased, protocol stripped, trailing
     * slashes removed) the same way as .env-sourced values.
     */
    public static String get(String key) {
        if (!configLoaded) {
            loadConfiguration();
        }

        String fromDotenv = dotenvConfig.get(key);
        if (fromDotenv != null && !fromDotenv.isEmpty()) {
            return fromDotenv;
        }

        String fromEnv = System.getenv(key);
        if (fromEnv != null) {
            String trimmed = fromEnv.trim();
            if (!trimmed.isEmpty()) {
                return SPELEODB_INSTANCE_URL.equals(key) ? normalizeInstanceUrl(trimmed) : trimmed;
            }
        }

        String fromProp = System.getProperty(key);
        if (fromProp != null) {
            String trimmed = fromProp.trim();
            if (!trimmed.isEmpty()) {
                return SPELEODB_INSTANCE_URL.equals(key) ? normalizeInstanceUrl(trimmed) : trimmed;
            }
        }

        return null;
    }

    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }

    public static int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println("Warning: Invalid integer value for " + key + ": " + value + ", using default: " + defaultValue);
            return defaultValue;
        }
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value.trim());
    }

    public static double getDouble(String key, double defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            System.err.println("Warning: Invalid double value for " + key + ": " + value + ", using default: " + defaultValue);
            return defaultValue;
        }
    }

    public static boolean isApiTestEnabled() {
        return getBoolean(API_TEST_ENABLED, true);
    }

    /**
     * Validate that critical configuration is present somewhere in the resolution chain.
     * Throws {@link RuntimeException} if {@code SPELEODB_INSTANCE_URL} or an auth method
     * cannot be resolved from {@code .env}, env vars, or system properties.
     */
    public static void validateCriticalConfig() {
        if (!configLoaded) {
            loadConfiguration();
        }

        StringBuilder errors = new StringBuilder();

        String instanceUrl = get(SPELEODB_INSTANCE_URL);
        if (instanceUrl == null || instanceUrl.trim().isEmpty()) {
            errors.append("• SPELEODB_INSTANCE_URL is required (set in .env or as env var)\n");
        } else if (!isValidInstanceUrl(instanceUrl.trim())) {
            errors.append("• SPELEODB_INSTANCE_URL must be a valid hostname or URL (got: '")
                  .append(instanceUrl).append("')\n");
        }

        String oauthToken = get(SPELEODB_OAUTH_TOKEN);
        String email = get(SPELEODB_EMAIL);
        String password = get(SPELEODB_PASSWORD);

        boolean hasOAuth = oauthToken != null && !oauthToken.trim().isEmpty();
        boolean hasEmailPassword = email != null && !email.trim().isEmpty()
                                && password != null && !password.trim().isEmpty();

        if (!hasOAuth && !hasEmailPassword) {
            errors.append("• Authentication required: set SPELEODB_OAUTH_TOKEN or both ")
                  .append("SPELEODB_EMAIL and SPELEODB_PASSWORD (.env or env var)\n");
        }

        if (errors.length() > 0) {
            String configSource = envFilePath != null
                ? ".env at " + envFilePath
                : "(no .env file -- " + (envFileLoadError != null ? envFileLoadError : "none provided") + ")";
            String errorMessage = """

                ERROR: Invalid SpeleoDB API test configuration

                Source: %s

                Errors:
                %s
                Resolution order for each key:
                  1. .env file
                  2. Process environment variable
                  3. JVM system property (-DKEY=value)

                In CI: set the values as job-level env or repository secrets.
                Locally: copy env.dist to .env and fill in real values.
                """.formatted(configSource, errors.toString());

            throw new RuntimeException(errorMessage);
        }
    }

    /**
     * Convenience: returns true iff {@link #validateCriticalConfig()} would succeed.
     */
    public static boolean hasRequiredConfig() {
        try {
            validateCriticalConfig();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Print configuration status with masked credentials. Records which source supplied
     * each value so CI logs make troubleshooting credentials trivial.
     */
    public static void printConfigStatus() {
        if (!configLoaded) {
            loadConfiguration();
        }

        System.out.println("=== SpeleoDB API Test Configuration ===");
        System.out.println(".env file: " + (envFilePath != null ? envFilePath : "(none -- using env vars / system properties)"));
        if (envFilePath == null && envFileLoadError != null) {
            System.out.println("  reason: " + envFileLoadError);
        }
        System.out.println("Instance URL: " + maskSensitive(get(SPELEODB_INSTANCE_URL))
                + sourceLabel(SPELEODB_INSTANCE_URL));
        System.out.println("Has OAuth Token: " + hasNonEmpty(SPELEODB_OAUTH_TOKEN)
                + sourceLabel(SPELEODB_OAUTH_TOKEN));
        System.out.println("Has Email/Password: " + (hasNonEmpty(SPELEODB_EMAIL) && hasNonEmpty(SPELEODB_PASSWORD)));
        System.out.println("API Test Enabled: " + isApiTestEnabled());
        System.out.println("API Timeout: " + getInt(API_TIMEOUT_MS, 10000) + "ms");
        System.out.println("API Retry Count: " + getInt(API_RETRY_COUNT, 3));
        System.out.println("Required Config Present: " + hasRequiredConfig());
        System.out.println("========================================");
    }

    /**
     * Reset cached state. Test-only helper used by {@link TestEnvironmentConfigEnvFallbackTest}
     * to exercise different .env / env-var combinations without spawning a new JVM.
     */
    static synchronized void resetForTest() {
        dotenvConfig.clear();
        configLoaded = false;
        envFilePath = null;
        envFileLoadError = null;
    }

    /**
     * Test-only helper: pretend no {@code .env} file was found and freeze the cached state.
     * Forces subsequent {@link #get(String)} calls to use only env-var / system-property
     * fallback. Used to verify CI behaviour (no .env, secrets via env) without chdir hacks.
     */
    static synchronized void simulateNoDotenvForTest() {
        dotenvConfig.clear();
        envFilePath = null;
        envFileLoadError = "(simulated absence for test)";
        configLoaded = true;
    }

    /**
     * Test-only helper: inject a synthetic {@code .env} entry without touching the filesystem.
     * Always normalizes {@link #SPELEODB_INSTANCE_URL} the same way the real loader does.
     */
    static synchronized void putDotenvForTest(String key, String value) {
        if (!configLoaded) {
            loadConfiguration();
        }
        if (SPELEODB_INSTANCE_URL.equals(key) && value != null) {
            value = normalizeInstanceUrl(value);
        }
        dotenvConfig.put(key, value);
    }

    private static boolean hasNonEmpty(String key) {
        String v = get(key);
        return v != null && !v.isEmpty();
    }

    private static String sourceLabel(String key) {
        if (!configLoaded) {
            loadConfiguration();
        }
        if (dotenvConfig.containsKey(key)) {
            return "  [source: .env]";
        }
        if (System.getenv(key) != null && !System.getenv(key).isEmpty()) {
            return "  [source: env var]";
        }
        if (System.getProperty(key) != null && !System.getProperty(key).isEmpty()) {
            return "  [source: system property]";
        }
        return "  [source: (unset)]";
    }

    private static String normalizeInstanceUrl(String url) {
        if (url == null) {
            return null;
        }

        // Per RFC 3986, scheme and host are case-insensitive: lowercase first so the
        // protocol-stripping below correctly recognises uppercase variants like HTTPS://.
        String normalized = url.trim().toLowerCase(Locale.ROOT);

        if (normalized.startsWith("https://")) {
            normalized = normalized.substring(8);
        } else if (normalized.startsWith("http://")) {
            normalized = normalized.substring(7);
        }

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    private static boolean isValidInstanceUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        String trimmed = url.trim();

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return true;
        }

        if (trimmed.contains(" ") || trimmed.contains("\t") || trimmed.contains("\n")) {
            return false;
        }

        return !trimmed.startsWith(".") && !trimmed.endsWith(".");
    }

    private static String maskSensitive(String value) {
        if (value == null || value.isEmpty()) {
            return "(not set)";
        }
        if (value.length() <= 8) {
            return "*".repeat(value.length());
        }
        return value.substring(0, 4) + "*".repeat(value.length() - 8) + value.substring(value.length() - 4);
    }
}
