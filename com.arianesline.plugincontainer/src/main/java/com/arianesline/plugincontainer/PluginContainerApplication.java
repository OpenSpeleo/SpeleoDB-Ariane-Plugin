package com.arianesline.plugincontainer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class PluginContainerApplication extends Application {

    public static PluginContainer pluginContainer = new PluginContainer();

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(PluginContainerApplication.class.getResource("plugincontainer-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 320, 240);
        stage.setTitle("Ariane Plugin Container");
        stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("log128sepia.png"))));
        stage.setScene(scene);
        stage.show();
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
    }
}