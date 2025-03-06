package com.arianesline.plugincontainer;

import com.arianesline.ariane.plugin.api.Plugin;
import com.arianesline.ariane.plugin.api.PluginInterface;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.Comparator;
import java.util.ResourceBundle;

import static com.arianesline.ariane.plugin.api.DataServerCommands.LOAD;
import static com.arianesline.ariane.plugin.api.DataServerCommands.SAVE;
import static com.arianesline.plugincontainer.PluginContainerApplication.pluginContainer;

public class PluginContainerController implements Initializable {
    public Label messageLabel;
    public VBox mainVBox;

    private final CoreContext core = CoreContext.getInstance();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        core.mainController = this;
    }

    public void showMessage(String message) {
        messageLabel.setText(message);
    }

    public void updateUIforPlugin() {

        pluginContainer.getDataServerPlugins().stream()
                .sorted(Comparator.comparing(Plugin::getName))
                .forEach(plugin -> {
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

                        mainVBox.getChildren().add(button);

                        plugin.getCommandProperty().addListener((observableValue, s, command) -> {
                            if (command.equals(LOAD.name())) {
                                Platform.runLater(() -> {
                                    core.mainController.showMessage("LOAD REQUESTED");
                                    plugin.setSurvey(core.dummyCave);
                                });
                            } else if (command.equals(SAVE.name())) {
                                core.mainController.showMessage("SAVE REQUESTED");
                            }
                        });
                    }
                });
    }
}