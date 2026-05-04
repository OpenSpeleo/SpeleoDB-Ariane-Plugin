package org.speleodb.ariane.plugin.speleodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.arianesline.ariane.plugin.api.DataServerPlugin;
import com.arianesline.ariane.plugin.api.PluginType;

/**
 * Guards the {@code provides DataServerPlugin with SpeleoDBPlugin} declaration in
 * {@code module-info.java} by loading the built JAR via a real Java module layer and
 * exercising the same {@link ServiceLoader} mechanism the host application uses.
 *
 * <p>Cannot use the test classpath directly: {@code ServiceLoader.load(Class)} only
 * honors {@code provides} directives when running on the modulepath, but Gradle runs
 * the JUnit JVM on the classpath. Instead we build an isolated child module layer from
 * the freshly produced JAR under {@code build/libs/}, mirroring exactly what
 * {@code PluginContainerApplication#runSmokeAndExit()} does in CI.
 *
 * <p>If the JAR isn't on disk yet (running {@code :test} in isolation, or from an IDE
 * without {@code :assemble}) the assertions are skipped via {@link
 * org.junit.jupiter.api.Assumptions#assumeTrue}. The smoke-load CI job covers the
 * end-to-end path on every push regardless.
 */
class ModuleProvidesTest {

    @Test
    @DisplayName("ServiceLoader on the JAR's module layer discovers SpeleoDBPlugin")
    void serviceLoaderDiscoversSpeleoDBPlugin() throws IOException {
        ModuleLayer layer = buildPluginModuleLayer().orElse(null);
        assumeTrue(layer != null, "JAR not built yet (build/libs/*.jar missing) -- skipping");

        boolean found = ServiceLoader.load(layer, DataServerPlugin.class).stream()
                .map(p -> p.type().getName())
                .anyMatch(SpeleoDBPlugin.class.getName()::equals);

        assertThat(found)
                .as("module-info.java must declare 'provides DataServerPlugin with SpeleoDBPlugin'")
                .isTrue();
    }

    @Test
    @DisplayName("Loaded SpeleoDBPlugin instance reports the DATASERVER plugin type")
    void instanceReportsCorrectType() throws IOException {
        ModuleLayer layer = buildPluginModuleLayer().orElse(null);
        assumeTrue(layer != null, "JAR not built yet (build/libs/*.jar missing) -- skipping");

        DataServerPlugin plugin = ServiceLoader.load(layer, DataServerPlugin.class).stream()
                .map(ServiceLoader.Provider::get)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No DataServerPlugin discoverable in built JAR's module layer"));

        assertThat(plugin.getType()).isEqualTo(PluginType.DATASERVER);
        assertThat(plugin.getName()).isNotBlank();
    }

    /**
     * Build an isolated child module layer rooted in {@code build/libs/} that contains
     * the freshly assembled plugin JAR. Returns empty if the JAR cannot be located.
     *
     * <p>Plugin's {@code module-info.java} requires {@code com.arianesline.ariane.plugin.api}
     * and {@code com.arianesline.cavelib.api}, which live in sibling submodules. We add
     * their {@code build/libs/} directories as additional finder paths so the module-system
     * resolver can satisfy those requires; otherwise {@code Configuration.resolve} throws
     * {@link java.lang.module.FindException}.
     */
    private static Optional<ModuleLayer> buildPluginModuleLayer() throws IOException {
        Optional<Path> pluginJar = locateBuiltJar();
        if (pluginJar.isEmpty()) {
            return Optional.empty();
        }
        Path pluginLibs = pluginJar.get().getParent();

        Path[] dependencyLibs = locateApiDependencyLibsDirs();
        Path[] allDirs = new Path[dependencyLibs.length + 1];
        allDirs[0] = pluginLibs;
        System.arraycopy(dependencyLibs, 0, allDirs, 1, dependencyLibs.length);

        ModuleFinder finder = ModuleFinder.of(allDirs);
        Set<ModuleReference> refs = finder.findAll();
        Set<String> pluginRoots = refs.stream()
                .map(ref -> ref.descriptor().name())
                .filter(name -> name.contains(".ariane.plugin") && !name.equals("com.arianesline.ariane.plugin.api"))
                .collect(Collectors.toSet());

        if (pluginRoots.isEmpty()) {
            return Optional.empty();
        }

        ModuleLayer parent = ModuleLayer.boot();
        Configuration cf;
        try {
            cf = parent.configuration().resolve(finder, ModuleFinder.of(), pluginRoots);
        } catch (java.lang.module.FindException e) {
            // Required transitive module not on disk yet (e.g. running :test without
            // having built the API submodules). Skip rather than fail -- the smoke-load
            // CI job validates the same path with a complete modulepath.
            return Optional.empty();
        }
        ClassLoader scl = ClassLoader.getSystemClassLoader();
        return Optional.of(parent.defineModulesWithOneLoader(cf, scl));
    }

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

    /**
     * Resolve paths to the API submodule JAR directories. Search for both possible CWDs:
     * the plugin module dir (Gradle test fork) and the monorepo root (IDE runs).
     */
    private static Path[] locateApiDependencyLibsDirs() {
        String[] subDirs = {
                "com.arianesline.ariane.plugin.api/build/libs",
                "com.arianesline.cavelib.api/build/libs"
        };
        List<Path> found = new java.util.ArrayList<>();
        for (String s : subDirs) {
            Path fromCwd = Paths.get(s);
            Path fromRepoRoot = Paths.get("..").resolve(s);
            if (Files.isDirectory(fromCwd)) {
                found.add(fromCwd);
            } else if (Files.isDirectory(fromRepoRoot)) {
                found.add(fromRepoRoot);
            }
        }
        return found.toArray(new Path[0]);
    }
}
