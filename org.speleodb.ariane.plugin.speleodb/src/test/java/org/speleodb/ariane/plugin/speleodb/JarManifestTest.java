package org.speleodb.ariane.plugin.speleodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Sanity checks that run AFTER the {@code jar} task has executed (i.e. when the JAR exists
 * on disk under {@code build/libs/}). When the JAR isn't present (e.g. during a pure
 * {@code :test} run that bypassed packaging), the assertions are skipped via
 * {@link org.junit.jupiter.api.Assumptions#assumeTrue}.
 *
 * <p>Why this matters under JDK 25:
 * <ul>
 *   <li>The {@code generateReleaseVersion} Gradle task mutates source and JAR manifest;
 *       the new toolchain must keep that wiring intact.</li>
 *   <li>The JAR must keep being sealed and reproducible -- two prerequisites that gain
 *       weight once we publish the JAR to a GitHub Release.</li>
 *   <li>{@code *.psd} sources must NEVER ship inside the JAR (build.gradle excludes them
 *       at the resources stage; this verifies the exclusion still applies).</li>
 * </ul>
 */
class JarManifestTest {

    private static final Pattern CALVER = Pattern.compile("\\d{4}\\.\\d{1,2}\\.\\d{1,2}");

    @Test
    @DisplayName("CI sentinel: built JAR must be present in build/libs/ when running on CI")
    void jarMustBePresentInCi() throws IOException {
        // The other tests in this class skip via assumeTrue when the JAR is missing
        // (so they're cheap to run from an IDE without :assemble). On CI, missing JAR
        // means a regression in the build wiring (`test dependsOn jar` got dropped, or
        // the jar task silently produced nothing). Surface that as RED, not green-skip.
        boolean onCi = System.getenv("CI") != null
                || System.getenv("GITHUB_ACTIONS") != null;
        assumeTrue(onCi, "not running on CI; the assumeTrue skips below are acceptable locally");
        assertThat(locateBuiltJar())
                .as("CI must build the JAR before running :test (`test dependsOn jar`); "
                        + "if this fails, the jar task or the test->jar wiring is broken")
                .isPresent();
    }

    @Test
    @DisplayName("JAR manifest carries CalVer version and required release attributes")
    void manifestAttributes() throws IOException {
        Path jar = locateBuiltJar().orElse(null);
        assumeTrue(jar != null, "JAR not built yet (build/libs/*.jar missing) -- skipping");

        try (JarFile jf = new JarFile(jar.toFile())) {
            Manifest manifest = jf.getManifest();
            assertThat(manifest).as("manifest must be present").isNotNull();

            Attributes main = manifest.getMainAttributes();
            assertThat(main.getValue("Implementation-Title"))
                    .isEqualTo("SpeleoDB Ariane Plugin");
            assertThat(main.getValue("Implementation-Vendor"))
                    .isEqualTo("SpeleoDB Software");
            assertThat(main.getValue("Sealed")).isEqualTo("true");
            assertThat(main.getValue("Multi-Release")).isEqualTo("false");

            String version = main.getValue("Implementation-Version");
            assertThat(version)
                    .as("Implementation-Version must be a CalVer YYYY.MM.DD stamp")
                    .isNotNull()
                    .matches(CALVER);
        }
    }

    @Test
    @DisplayName("JAR contains no Photoshop (*.psd) source assets")
    void noPsdInsideJar() throws IOException {
        Path jar = locateBuiltJar().orElse(null);
        assumeTrue(jar != null, "JAR not built yet (build/libs/*.jar missing) -- skipping");

        try (JarFile jf = new JarFile(jar.toFile())) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                assertThat(entry.getName().toLowerCase())
                        .as("no PSD asset should be packaged: " + entry.getName())
                        .doesNotEndWith(".psd");
            }
        }
    }

    @Test
    @DisplayName("JAR ships module-info.class (required for Java module layer)")
    void hasModuleInfo() throws IOException {
        Path jar = locateBuiltJar().orElse(null);
        assumeTrue(jar != null, "JAR not built yet (build/libs/*.jar missing) -- skipping");

        try (JarFile jf = new JarFile(jar.toFile())) {
            JarEntry moduleInfo = jf.getJarEntry("module-info.class");
            assertThat(moduleInfo)
                    .as("plugin must ship module-info.class so JPMS resolves its providers")
                    .isNotNull();
        }
    }

    /**
     * Locate the latest CalVer JAR under {@code build/libs/}. Search both the test JVM's
     * working directory (set by Gradle to the plugin's project dir) and a relative fallback
     * for IDE runs.
     */
    private static Optional<Path> locateBuiltJar() throws IOException {
        List<Path> candidates = List.of(
                Paths.get("build", "libs"),
                Paths.get("org.speleodb.ariane.plugin.speleodb", "build", "libs")
        );

        for (Path dir : candidates) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> stream = Files.list(dir)) {
                Optional<Path> jar = stream
                        .filter(p -> p.getFileName().toString().endsWith(".jar"))
                        .filter(p -> p.getFileName().toString().startsWith("org.speleodb.ariane.plugin.speleodb"))
                        .max(Comparator.comparing(Path::getFileName));
                if (jar.isPresent()) {
                    return jar;
                }
            }
        }
        return Optional.empty();
    }
}
