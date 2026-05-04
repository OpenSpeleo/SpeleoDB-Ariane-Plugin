package org.speleodb.ariane.plugin.speleodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the layered resolution order in {@link TestEnvironmentConfig} that lets CI
 * supply credentials via env vars (or {@code -D} system properties) instead of a
 * {@code .env} file.
 *
 * <p>Resolution order under test (highest → lowest):
 * <ol>
 *   <li>{@code .env} file</li>
 *   <li>{@link System#getenv(String)}</li>
 *   <li>{@link System#getProperty(String)}</li>
 * </ol>
 *
 * <p>Real env vars cannot be set from inside the JVM, so the env-var rung is exercised
 * indirectly via the system-property fallback (same code path, same precedence semantics).
 * The {@code .env} rung is exercised through {@link
 * TestEnvironmentConfig#putDotenvForTest(String, String)} which avoids touching the
 * filesystem and works regardless of the current working directory.
 */
class TestEnvironmentConfigEnvFallbackTest {

    private static final String KEY_URL = TestEnvironmentConfig.SPELEODB_INSTANCE_URL;
    private static final String KEY_TOKEN = TestEnvironmentConfig.SPELEODB_OAUTH_TOKEN;
    private static final String KEY_EMAIL = TestEnvironmentConfig.SPELEODB_EMAIL;
    private static final String KEY_PWD = TestEnvironmentConfig.SPELEODB_PASSWORD;

    @BeforeEach
    void resetState() {
        TestEnvironmentConfig.simulateNoDotenvForTest();
        clearAllSysProps();
    }

    @AfterEach
    void cleanup() {
        clearAllSysProps();
        TestEnvironmentConfig.resetForTest();
    }

    @Test
    @DisplayName("dotenv-only: value injected as .env entry is returned")
    void dotenvOnlyResolves() {
        TestEnvironmentConfig.putDotenvForTest(KEY_URL, "https://stage.speleodb.org");
        TestEnvironmentConfig.putDotenvForTest(KEY_TOKEN, "tok-from-dotenv");

        assertThat(TestEnvironmentConfig.get(KEY_URL)).isEqualTo("stage.speleodb.org");
        assertThat(TestEnvironmentConfig.get(KEY_TOKEN)).isEqualTo("tok-from-dotenv");
        assertThat(TestEnvironmentConfig.hasRequiredConfig()).isTrue();
    }

    @Test
    @DisplayName("env-only (via -D system property fallback): value resolves when no .env present")
    void sysPropFallbackResolves() {
        // Skip if a real env var is set on the host -- env wins over sysprop in the
        // resolution chain so this assertion would test the env, not the sysprop, path.
        assumeTrue(System.getenv(KEY_URL) == null,
                "host has " + KEY_URL + " in real env; sysprop assertion would be shadowed");
        assumeTrue(System.getenv(KEY_TOKEN) == null,
                "host has " + KEY_TOKEN + " in real env; sysprop assertion would be shadowed");

        System.setProperty(KEY_URL, "https://stage.speleodb.org");
        System.setProperty(KEY_TOKEN, "tok-from-sysprop");

        assertThat(TestEnvironmentConfig.get(KEY_URL)).isEqualTo("stage.speleodb.org");
        assertThat(TestEnvironmentConfig.get(KEY_TOKEN)).isEqualTo("tok-from-sysprop");
        assertThat(TestEnvironmentConfig.hasRequiredConfig()).isTrue();
    }

    @Test
    @DisplayName(".env wins over env / system-property when both supply the same key")
    void dotenvWinsOverFallback() {
        TestEnvironmentConfig.putDotenvForTest(KEY_TOKEN, "tok-from-dotenv");
        System.setProperty(KEY_TOKEN, "tok-from-sysprop");

        assertThat(TestEnvironmentConfig.get(KEY_TOKEN)).isEqualTo("tok-from-dotenv");
    }

    @Test
    @DisplayName("system-property fills gaps that .env doesn't cover")
    void mixedSourcesCompose() {
        assumeTrue(System.getenv(KEY_TOKEN) == null,
                "host has " + KEY_TOKEN + " in real env; sysprop fill-in would be shadowed");

        TestEnvironmentConfig.putDotenvForTest(KEY_URL, "stage.speleodb.org");
        System.setProperty(KEY_TOKEN, "tok-from-sysprop");

        assertThat(TestEnvironmentConfig.get(KEY_URL)).isEqualTo("stage.speleodb.org");
        assertThat(TestEnvironmentConfig.get(KEY_TOKEN)).isEqualTo("tok-from-sysprop");
        assertThat(TestEnvironmentConfig.hasRequiredConfig()).isTrue();
    }

    @Test
    @DisplayName("email + password combination satisfies auth requirement when token absent")
    void emailPasswordSatisfiesAuth() {
        TestEnvironmentConfig.putDotenvForTest(KEY_URL, "stage.speleodb.org");
        System.setProperty(KEY_EMAIL, "user@example.com");
        System.setProperty(KEY_PWD, "secret");

        assertThat(TestEnvironmentConfig.hasRequiredConfig()).isTrue();
    }

    @Test
    @DisplayName("URL normalization applies via the .env path (uppercase, protocol, trailing slash)")
    void instanceUrlNormalizationViaDotenv() {
        // Tests the URL normalization specifically through the .env source, which is the
        // only source we can fully control regardless of host env-var state.
        TestEnvironmentConfig.simulateNoDotenvForTest();
        TestEnvironmentConfig.putDotenvForTest(KEY_URL, "HTTPS://Stage.SpeleoDB.org/");
        assertThat(TestEnvironmentConfig.get(KEY_URL)).isEqualTo("stage.speleodb.org");
    }

    @Test
    @DisplayName("URL normalization applies via the system-property fallback path")
    void instanceUrlNormalizationViaSysProp() {
        assumeTrue(System.getenv(KEY_URL) == null,
                "host has " + KEY_URL + " in real env; sysprop normalization assertion would be shadowed");

        System.setProperty(KEY_URL, "HTTPS://Stage.SpeleoDB.org/");
        assertThat(TestEnvironmentConfig.get(KEY_URL)).isEqualTo("stage.speleodb.org");
    }

    @Test
    @DisplayName("nothing set anywhere → validateCriticalConfig throws with actionable message")
    void neitherSourceThrows() {
        // Real env vars (e.g. CI secrets) would satisfy validateCriticalConfig via the
        // env-var fallback rung and prevent the throw -- skip cleanly in that case.
        assumeTrue(System.getenv(KEY_URL) == null,
                "host has " + KEY_URL + " in real env; validateCriticalConfig would not throw");
        assumeTrue(System.getenv(KEY_TOKEN) == null,
                "host has " + KEY_TOKEN + " in real env; auth would resolve from env");
        assumeTrue(System.getenv(KEY_EMAIL) == null,
                "host has " + KEY_EMAIL + " in real env; auth would resolve from env");
        assumeTrue(System.getenv(KEY_PWD) == null,
                "host has " + KEY_PWD + " in real env; auth would resolve from env");

        assertThatThrownBy(TestEnvironmentConfig::validateCriticalConfig)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SPELEODB_INSTANCE_URL is required")
                .hasMessageContaining("Authentication required");
        assertThat(TestEnvironmentConfig.hasRequiredConfig()).isFalse();
    }

    @Test
    @DisplayName("URL set but no auth → validateCriticalConfig still throws (auth missing)")
    void urlOnlyStillFailsAuthCheck() {
        // Real auth env vars (CI secrets) would satisfy the auth check via the env-var
        // fallback rung and prevent the throw -- skip cleanly in that case.
        assumeTrue(System.getenv(KEY_TOKEN) == null,
                "host has " + KEY_TOKEN + " in real env; auth would resolve from env");
        assumeTrue(System.getenv(KEY_EMAIL) == null,
                "host has " + KEY_EMAIL + " in real env; auth would resolve from env");
        assumeTrue(System.getenv(KEY_PWD) == null,
                "host has " + KEY_PWD + " in real env; auth would resolve from env");

        TestEnvironmentConfig.putDotenvForTest(KEY_URL, "stage.speleodb.org");

        assertThatThrownBy(TestEnvironmentConfig::validateCriticalConfig)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    @DisplayName("printConfigStatus does NOT throw when nothing is configured")
    void printConfigStatusNeverCrashes() {
        TestEnvironmentConfig.printConfigStatus();
    }

    @Test
    @DisplayName("getInt / getBoolean fall back to defaults when key unresolvable")
    void typedGettersHonourDefaults() {
        // CI sets API_TIMEOUT_MS / API_TEST_ENABLED at the workflow level; those real
        // env vars win over both the default-arg fallback (first two assertions) AND
        // the sysprop overrides set later (last two assertions). Skip cleanly when
        // any of those env vars is present.
        assumeTrue(System.getenv(TestEnvironmentConfig.API_TIMEOUT_MS) == null,
                "host has API_TIMEOUT_MS in real env; default + sysprop fallback would be shadowed");
        assumeTrue(System.getenv(TestEnvironmentConfig.API_TEST_ENABLED) == null,
                "host has API_TEST_ENABLED in real env; default + sysprop fallback would be shadowed");

        assertThat(TestEnvironmentConfig.getInt(TestEnvironmentConfig.API_TIMEOUT_MS, 12345)).isEqualTo(12345);
        assertThat(TestEnvironmentConfig.getBoolean(TestEnvironmentConfig.API_TEST_ENABLED, true)).isTrue();

        System.setProperty(TestEnvironmentConfig.API_TIMEOUT_MS, "9999");
        System.setProperty(TestEnvironmentConfig.API_TEST_ENABLED, "false");

        assertThat(TestEnvironmentConfig.getInt(TestEnvironmentConfig.API_TIMEOUT_MS, 0)).isEqualTo(9999);
        assertThat(TestEnvironmentConfig.isApiTestEnabled()).isFalse();
    }

    private static void clearAllSysProps() {
        System.clearProperty(KEY_URL);
        System.clearProperty(KEY_TOKEN);
        System.clearProperty(KEY_EMAIL);
        System.clearProperty(KEY_PWD);
        System.clearProperty(TestEnvironmentConfig.API_TEST_ENABLED);
        System.clearProperty(TestEnvironmentConfig.API_TIMEOUT_MS);
        System.clearProperty(TestEnvironmentConfig.API_RETRY_COUNT);
    }
}
