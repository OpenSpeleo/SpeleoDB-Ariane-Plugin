package com.arianesline.plugincontainer;

import java.io.IOException;
import java.util.Objects;

import com.arianesline.ariane.plugin.api.DataServerPlugin;
import com.arianesline.ariane.plugin.api.Plugin;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class PluginContainerApplication extends Application {

    private static final String ARIANE_VERSION = "25.2.2";

    public static PluginContainer pluginContainer = new PluginContainer();
    
    /**
     * Default constructor for PluginContainerApplication.
     */
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
        launch();
    }

    /* (non-Javadoc)
     * @see javafx.application.Application#stop()
     */
    @Override
    public void stop() throws Exception {
        super.stop(); //To change body of generated methods, choose Tools | Templates.
        CoreContext.getInstance().shutdownNow();
        pluginContainer.getDataServerPlugins().forEach(DataServerPlugin::closeUI);
    }
}