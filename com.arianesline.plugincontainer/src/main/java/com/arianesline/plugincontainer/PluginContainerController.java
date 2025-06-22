package com.arianesline.plugincontainer;

import static com.arianesline.ariane.plugin.api.DataServerCommands.LOAD;
import static com.arianesline.ariane.plugin.api.DataServerCommands.SAVE;
import static com.arianesline.plugincontainer.PluginContainerApplication.pluginContainer;

import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.ResourceBundle;

import com.arianesline.ariane.plugin.api.Plugin;
import com.arianesline.ariane.plugin.api.PluginInterface;

import javafx.application.Platform;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;

public class PluginContainerController implements Initializable {

    private final CoreContext core = CoreContext.getInstance();

    public TabPane mainTabPane;
    public AnchorPane mainAnchor;
    public ListView<String> mainListView;
    public HBox mainHBox;

    /**
     * Default constructor for PluginContainerController.
     */
    public PluginContainerController() {
        // Default constructor
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        core.mainController = this;
    }

    public void showMessage(String message) {
        mainListView.getItems().add(message);
    }

    public void updateUIforPlugin() {

        pluginContainer.getDataServerPlugins().stream()
                .sorted(Comparator.comparing(Plugin::getName))
                .forEach(plugin -> {

                    //Case the Plugin UI is displayed in a separate windows
                    if (plugin.getInterfaceType() == PluginInterface.WINDOW) {
                        ImageView imageView = new ImageView();
                        imageView.setFitHeight(32);
                        imageView.setFitWidth(32);
                        imageView.setPickOnBounds(true);
                        imageView.setPreserveRatio(true);
                        imageView.setImage(plugin.getIcon());

                        Button button = new Button();
                        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                        button.getStyleClass().add("imagebutton");
                        button.setMnemonicParsing(false);
                        button.setOnAction(actionEvent -> plugin.showUI());
                        button.setGraphic(imageView);
                        button.setTooltip(new Tooltip(plugin.getName()));


                        mainHBox.getChildren().add(button);

                        plugin.getCommandProperty().addListener((observableValue, s, command) -> {
                            if (command.equals(LOAD.name())) {
                                Platform.runLater(() -> {
                                    core.mainController.showMessage("LOAD REQUESTED");
                                    plugin.setSurvey(new CaveSurveyImpl());
                                });
                            } else if (command.equals(SAVE.name())) {
                                core.mainController.showMessage("SAVE REQUESTED");
                            }
                        });
                    }

                    //Case the plugin is integrated in mainUI as Tab on the left Ariane panel
                    if (plugin.getInterfaceType() == PluginInterface.LEFT_TAB) {
                        Tab tab = new Tab(plugin.getName());
                        tab.setContent(plugin.getUINode());
                        mainTabPane.getTabs().add(tab);

                        plugin.getCommandProperty().addListener((observableValue, s, command) -> {
                            if (command.equals(LOAD.name())) {
                                Platform.runLater(() -> {
                                    core.mainController.showMessage("LOAD REQUESTED");
                                   var start= LocalDateTime.now();
                                    while(Duration.between(start,LocalDateTime.now()).toMillis()< 2000){
//Simulate wait time on Ariane to parse TML
                                    }
                                    plugin.setSurvey(new CaveSurveyImpl());
                                    core.mainController.showMessage(plugin.getSurveyFile().getName());
                                });
                            } else if (command.equals(SAVE.name())) {
                                core.mainController.showMessage("SAVE REQUESTED");
                                core.mainController.showMessage(plugin.getSurveyFile().getName());
                            }
                        });
                    }
                });
    }
}