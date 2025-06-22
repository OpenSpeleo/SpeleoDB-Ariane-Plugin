module com.arianesline.plugincontainer {
    uses com.arianesline.ariane.plugin.api.DataServerPlugin;
    requires transitive javafx.controls;
    requires transitive javafx.fxml;
    requires javafx.web;
    requires java.prefs;
    requires jakarta.xml.bind;
    requires jakarta.json;
    requires java.net.http;
    requires transitive com.arianesline.ariane.plugin.api;
    requires transitive com.arianesline.cavelib.api;

    opens com.arianesline.plugincontainer to javafx.fxml;
    exports com.arianesline.plugincontainer;
}