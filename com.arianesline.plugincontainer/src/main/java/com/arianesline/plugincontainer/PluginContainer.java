package com.arianesline.plugincontainer;

import com.arianesline.ariane.plugin.api.*;

import javafx.application.Platform;
import javafx.concurrent.Task;

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;


public class PluginContainer {

    private final CoreContext core = CoreContext.getInstance();

    private final List<DataServerPlugin> dataServerPlugins = new ArrayList<>();

    public List<DataServerPlugin> getDataServerPlugins() {
        return dataServerPlugins;
    }

    public void loadPlugins() {
        PlugingLoadTask plugingLoadTask = new PlugingLoadTask();
        plugingLoadTask.setOnSucceeded((e) -> Platform.runLater(() -> core.mainController.updateUIforPlugin()));
       core.executorService.execute(plugingLoadTask);
    }

    private void loadPlugins(ModuleLayer pluginModuleLayer) {
        var dataServerPlugins = ServiceLoader.load(pluginModuleLayer, DataServerPlugin.class);
        dataServerPlugins.stream().forEach(p -> getDataServerPlugins().add(p.get()));
    }

    public class PlugingLoadTask extends Task<Void> {

        ModuleLayer createPluginLayer(String dir) {
            ModuleFinder finder = ModuleFinder.of(Paths.get(dir));
            Set<ModuleReference> pluginModuleRefs = finder.findAll();
            Set<String> pluginRoots = pluginModuleRefs.stream()
                    .map(ref -> ref.descriptor().name())
                    .filter(name -> name.contains(".ariane.plugin")) // <1>
                    .collect(Collectors.toSet());

            ModuleLayer parent = ModuleLayer.boot();
            Configuration cf = parent.configuration()
                    .resolve(finder, ModuleFinder.of(), pluginRoots); // <2>

            ClassLoader scl = ClassLoader.getSystemClassLoader();

            return parent.defineModulesWithOneLoader(cf, scl);
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
        protected Void call()  {
            var pluginURL = "./plugins";
            ModuleLayer pluginModuleLayer = createPluginLayer(pluginURL);
            loadPlugins(pluginModuleLayer);
            return null;
        }
    }
}

