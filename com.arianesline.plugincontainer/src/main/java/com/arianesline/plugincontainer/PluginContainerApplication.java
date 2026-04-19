package com.arianesline.plugincontainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

import com.arianesline.ariane.plugin.api.DataServerPlugin;
import com.arianesline.ariane.plugin.api.Plugin;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class PluginContainerApplication extends Application {

    private static final String ARIANE_VERSION = "25.2.2";

    /**
     * When set (typically via {@code -DsmokeExitAfterStart=true} or the Gradle property
     * {@code -PsmokeExitAfterStart=true}), {@link #main(String[])} runs a headless plugin
     * discovery against {@code ./plugins/} and exits before {@link Application#launch}
     * boots the JavaFX toolkit. This keeps CI off xvfb/DISPLAY and produces a sub-second
     * sanity check that the freshly built plugin JAR is loadable as a JPMS module.
     */
    private static final String SMOKE_FLAG = "smokeExitAfterStart";
    private static final String PLUGINS_DIR = "./plugins";

    public static PluginContainer pluginContainer = new PluginContainer();

    public PluginContainerApplication() {
        super();
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(PluginContainerApplication.class.getResource("plugincontainer-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 800, 800);
        stage.setTitle("Ariane Plugin Container");
        stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("log128sepia.png"))));
        stage.setScene(scene);
        stage.show();
        Plugin.containerVersion.append(ARIANE_VERSION);
        pluginContainer.loadPlugins();
    }

    public static void main(String[] args) {
        if (Boolean.getBoolean(SMOKE_FLAG)) {
            System.exit(runSmoke());
        }
        launch();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        CoreContext.getInstance().shutdownNow();
        pluginContainer.getDataServerPlugins().forEach(DataServerPlugin::closeUI);
    }

    /**
     * Headless plugin discovery used by CI. Runs synchronously, BEFORE the JavaFX toolkit
     * is initialised, so it does not require a display server (no xvfb in CI). Reuses
     * {@link PluginContainer#createPluginLayer(Path)} so the discovery semantics cannot
     * drift from the runtime FX path.
     *
     * <p>Exit codes:
     * <ul>
     *   <li>0 -- at least one DataServerPlugin discovered</li>
     *   <li>2 -- {@code ./plugins/} directory missing</li>
     *   <li>3 -- module(s) present but ServiceLoader returned no DataServerPlugin instance</li>
     *   <li>4 -- exception while resolving the plugin module layer (e.g. FindException)</li>
     * </ul>
     *
     * @return the exit code described above
     */
    static int runSmoke() {
        System.out.println("[smoke] running plugin discovery (smokeExitAfterStart=true)");

        Path pluginsDir = Paths.get(PLUGINS_DIR).toAbsolutePath().normalize();
        if (!Files.isDirectory(pluginsDir)) {
            System.err.println("[smoke] FAIL: plugins/ directory not found at " + pluginsDir);
            return 2;
        }

        try {
            ModuleLayer layer = PluginContainer.createPluginLayer(pluginsDir);

            List<DataServerPlugin> loaded = ServiceLoader.load(layer, DataServerPlugin.class).stream()
                    .map(ServiceLoader.Provider::get)
                    .toList();

            if (loaded.isEmpty()) {
                System.err.println("[smoke] FAIL: ServiceLoader returned no DataServerPlugin from " + pluginsDir);
                return 3;
            }

            System.out.println("[smoke] OK -- discovered " + loaded.size() + " DataServerPlugin(s):");
            for (DataServerPlugin p : loaded) {
                System.out.println("  - name='" + p.getName() + "', class=" + p.getClass().getName()
                        + ", type=" + p.getType());
            }
            return 0;
        } catch (Exception e) {
            System.err.println("[smoke] FAIL: exception while resolving plugin module layer: " + e);
            e.printStackTrace(System.err);
            return 4;
        }
    }
}
