package com.arianesline.plugincontainer;

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import com.arianesline.ariane.plugin.api.DataServerPlugin;

import javafx.application.Platform;
import javafx.concurrent.Task;


public class PluginContainer {

    private final CoreContext core = CoreContext.getInstance();

    private final List<DataServerPlugin> dataServerPlugins = new ArrayList<>();

    /**
     * Default constructor for PluginContainer.
     */
    public PluginContainer() {
        // Default constructor
    }

    public List<DataServerPlugin> getDataServerPlugins() {
        return dataServerPlugins;
    }

    public void loadPlugins() {
        PlugingLoadTask plugingLoadTask = new PlugingLoadTask();
        plugingLoadTask.setOnSucceeded((e) -> Platform.runLater(() -> core.mainController.updateUIforPlugin()));
        core.executeTask(plugingLoadTask);
    }

    private void loadPlugins(ModuleLayer pluginModuleLayer) {
        var dataServerPlugins = ServiceLoader.load(pluginModuleLayer, DataServerPlugin.class);
        dataServerPlugins.stream().forEach(p -> getDataServerPlugins().add(p.get()));
    }

    /**
     * Build a child {@link ModuleLayer} rooted in {@code pluginsDir} and containing every
     * Ariane plugin module discoverable via {@link ModuleFinder#of(Path...)}. Shared by
     * the runtime FX path ({@link PlugingLoadTask}) and the headless CI smoke path
     * ({@code PluginContainerApplication.runSmokeAndExit}) so the discovery logic cannot
     * drift between them.
     *
     * @param pluginsDir directory containing plugin JARs
     * @return resolved module layer with the system class loader as parent
     * @throws java.lang.module.FindException if a required transitive module cannot be located
     */
    public static ModuleLayer createPluginLayer(Path pluginsDir) {
        ModuleFinder finder = ModuleFinder.of(pluginsDir);
        Set<ModuleReference> pluginModuleRefs = finder.findAll();
        Set<String> pluginRoots = pluginModuleRefs.stream()
                .map(ref -> ref.descriptor().name())
                .filter(name -> name.contains(".ariane.plugin"))
                .collect(Collectors.toSet());

        ModuleLayer parent = ModuleLayer.boot();
        Configuration cf = parent.configuration()
                .resolve(finder, ModuleFinder.of(), pluginRoots);

        ClassLoader scl = ClassLoader.getSystemClassLoader();
        return parent.defineModulesWithOneLoader(cf, scl);
    }

    public class PlugingLoadTask extends Task<Void> {

        /**
         * Default constructor for PlugingLoadTask.
         */
        public PlugingLoadTask() {
            super();
        }

        @Override
        protected void succeeded() {
            super.succeeded();
            Platform.runLater(() -> CoreContext.getInstance().mainController.showMessage("PLUGIN LOADED"));

        }

        @Override
        protected void failed() {
            super.failed();
            Platform.runLater(() -> CoreContext.getInstance().mainController.showMessage("ERROR LOADING PLUGIN"));
        }

        @Override
        protected void cancelled() {
            super.cancelled();
        }

        @Override
        protected Void call() {
            ModuleLayer pluginModuleLayer = createPluginLayer(Paths.get("./plugins"));
            loadPlugins(pluginModuleLayer);
            return null;
        }
    }
}

