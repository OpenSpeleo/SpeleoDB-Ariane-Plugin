module com.arianesline.plugincontainer {
    uses com.arianesline.ariane.plugin.api.DataServerPlugin;
    requires javafx.controls;
    requires javafx.fxml;
    requires java.prefs;
    requires jakarta.xml.bind;
    requires jakarta.json;
    requires java.net.http;
    requires com.arianesline.ariane.plugin.api;
    requires com.arianesline.cavelib.api;

    opens com.arianesline.plugincontainer to javafx.fxml;
    exports com.arianesline.plugincontainer;
}