package org.controller;

import org.service.ValidationService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

public class VerificationController {

    @FXML private TextField txtStudentId;
    @FXML private TextField txtFullName;
    @FXML private Button btnVerify;

    private final ValidationService validationService = new ValidationService();

    @FXML
    public void initialize() {
        System.out.println("Verification UI successfully bound to controller runtime.");
    }

    @FXML
    private void handleVerification() {
        String inputId = txtStudentId.getText();
        String inputName = txtFullName.getText();

        // call validation
        boolean isAuthenticated = validationService.validateStudentCredentials(inputId, inputName);

        if (isAuthenticated) {
            showNotification(Alert.AlertType.INFORMATION, "Verification Successful",
                    "Welcome back, " + inputName.trim() + "! Initializing your campus dashboard...");

            try {
                // locate the dashboard layout
                javafx.fxml.FXMLLoader dashboardLoader = new javafx.fxml.FXMLLoader(
                        getClass().getResource("/org/campus/app/MainDashboard.fxml")
                );

                javafx.scene.Parent dashboardRoot = dashboardLoader.load();

                MainDashboardController dashboardController = dashboardLoader.getController();

                dashboardController.setStudentProfile(inputId, inputName);

                javafx.stage.Stage currentStage = (javafx.stage.Stage) txtStudentId.getScene().getWindow();

                // swap to main dashboard
                currentStage.setScene(new javafx.scene.Scene(dashboardRoot, 900, 600));
                currentStage.setTitle("Campus Assistant - Student Workspace");
                currentStage.setResizable(true);
                currentStage.centerOnScreen();

            } catch (java.io.IOException e) {
                System.err.println("Navigation Error: Dashboard layout compilation broken.");
                e.printStackTrace();
            }

        } else {
            showNotification(Alert.AlertType.ERROR, "Verification Failed",
                    "The provided Student ID or Name could not be matched against our enrollment records. Please try again.");
        }
    }

    private void showNotification(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}