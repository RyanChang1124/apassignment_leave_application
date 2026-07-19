module org.example.apassignment {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;
    requires java.logging;
    opens org.model to javafx.base;
    opens org.campus.app to javafx.fxml;
    exports org.campus.app;
    exports org.controller;
    opens org.controller to javafx.fxml;
}