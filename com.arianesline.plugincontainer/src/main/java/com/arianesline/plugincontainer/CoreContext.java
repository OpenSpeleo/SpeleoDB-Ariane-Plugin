/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.arianesline.plugincontainer;

// NOTE: Auto-generated Javadoc

import javafx.concurrent.Task;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This Singleton class is the main entry point to application wide controller
 * classes, variables and settings.
 *
 * @author Sebastien
 */
public final class CoreContext {

    /**
     * Minimal Singleton.
     */
    private final static CoreContext INSTANCE = new CoreContext();

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    /**
     * Controller for the main Stage content.
     */
    public PluginContainerController mainController;

    public CoreContext() {
    }

    /**
     * gets the Singleton.
     *
     * @return single instance of CoreContext
     */
    public static CoreContext getInstance() {
        return INSTANCE;
    }

    public void shutdownNow() {
        executorService.shutdownNow();
    }

    public void executeTask(Task<Void> task) {
        executorService.execute(task);
    }
}
