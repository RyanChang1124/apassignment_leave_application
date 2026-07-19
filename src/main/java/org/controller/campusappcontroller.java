package org.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class campusappcontroller {
    @FXML
    private Label welcomeText;

    @FXML
    protected void onHelloButtonClick() {
        welcomeText.setText("Welcome to JavaFX Application!");
    }
}
