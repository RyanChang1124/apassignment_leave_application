package org.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.mcp.McpClientService;

public class MainDashboardController {
    private final McpClientService mcpClientService = new McpClientService();
    String todayDate = java.time.LocalDate.now().toString();
    private String loggedInStudentId = ""; // Default fallback value
    private String loggedInStudentName = "Student";
    // Chat Panel bindings
    @FXML private ScrollPane scrollChat;
    @FXML private VBox vBoxChatContainer;
    @FXML private TextField txtChatInput;
    @FXML private Button btnSendMessage;

    // Form Panel bindings
    @FXML private Button btnviewActiveLeaves;
    @FXML private Button btnGenerateDraft;
    @FXML private ComboBox<String> comboLeaveType;
    @FXML private DatePicker dateStart;
    @FXML private DatePicker dateEnd;
    @FXML private TextArea txtReason;
    @FXML private Button btnSubmit;

    @FXML
    public void initialize() {
        comboLeaveType.setItems(FXCollections.observableArrayList(
                "Medical Leave", "Compassionate Leave", "Personal/Emergency Leave"
        ));

        comboLeaveType.setPromptText("Select Type");

        vBoxChatContainer.heightProperty().addListener((observable, oldValue, newValue) ->
                scrollChat.setVvalue(1.0));

        System.out.println("Main Dashboard layout fields bound successfully.");
    }

    public void setStudentProfile(String studentId, String fullName) {
        if (studentId != null && !studentId.trim().isEmpty()) {
            this.loggedInStudentId = studentId.trim();
        }
        if (fullName != null && !fullName.trim().isEmpty()) {
            this.loggedInStudentName = fullName.trim();
        }
        System.out.println("👤 Dashboard session initialized for: " + loggedInStudentName + " (" + loggedInStudentId + ")");
    }

    @FXML
    private void handleSendMessage() {
        String userInput = txtChatInput.getText().trim();
        if (userInput.isEmpty()) return;

        Label userLabel = new Label("👤 You: " + userInput);
        userLabel.setStyle("-fx-background-color: #E9E9EB; -fx-padding: 8px; -fx-background-radius: 10px;");
        vBoxChatContainer.getChildren().add(userLabel);
        txtChatInput.clear();

        Label assistantLabel = new Label("🤖 Claude is processing query...");
        assistantLabel.setStyle("-fx-background-color: #DEEBF7; -fx-padding: 8px; -fx-background-radius: 10px;");
        vBoxChatContainer.getChildren().add(assistantLabel);

        javafx.concurrent.Task<String> orchestrationTask = new javafx.concurrent.Task<>() {
            @Override
            protected String call() throws Exception {
                // 1. fetch tools
                System.out.println("Background thread: Discovering active MCP tools...");
                String rawToolsJson = mcpClientService.discoverServerTools();
                System.out.println("Background thread: Tools discovered -> " + rawToolsJson);

                // 2. pass to orchestrator
                org.service.ClaudeOrchestrator orchestrator = new org.service.ClaudeOrchestrator();

                return orchestrator.chatWithGroundedContext(userInput, rawToolsJson);
            }
        };

        orchestrationTask.setOnSucceeded(event -> {
            String targetResult = orchestrationTask.getValue();

            javafx.application.Platform.runLater(() -> {
                // chatbox text
                javafx.scene.text.Text messageText = new javafx.scene.text.Text("🤖 Assistant:\n" + targetResult);
                messageText.setStyle("-fx-fill: #01579B; -fx-font-family: 'System';");

                // 2. speech bubble
                javafx.scene.text.TextFlow speechBubble = new javafx.scene.text.TextFlow(messageText);
                speechBubble.setStyle("-fx-background-color: #E1F5FE; -fx-padding: 10px; -fx-background-radius: 10px;");

                // 3. wrapping text
                speechBubble.setMaxWidth(Double.MAX_VALUE);

                vBoxChatContainer.getChildren().add(speechBubble);
            });
        });

        orchestrationTask.setOnFailed(event -> {
            Throwable exception = orchestrationTask.getException();
            if (exception != null) exception.printStackTrace();

            javafx.application.Platform.runLater(() -> {
                assistantLabel.setWrapText(true);
                assistantLabel.maxWidthProperty().bind(vBoxChatContainer.widthProperty().subtract(40));
                assistantLabel.setText("❌ Connection loop failure.");
                assistantLabel.setStyle("-fx-background-color: #FFEBEE; -fx-padding: 10px; -fx-background-radius: 10px; -fx-text-fill: #C62828;");
            });
        });

        Thread backgroundThread = new Thread(orchestrationTask);
        backgroundThread.setDaemon(true);
        backgroundThread.start();
    }

    @FXML
    private void handleLoadTemplate() {
        String selectedType = comboLeaveType.getValue();
        if (selectedType == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Selection Required");
            alert.setHeaderText(null);
            alert.setContentText("Please choose a 'Type of Leave' classification from the dropdown first before generating your draft!");
            alert.showAndWait();
            return;
        }

        String startDate = (dateStart.getValue() != null) ? dateStart.getValue().toString() : "(Not Specified)";
        String endDate = (dateEnd.getValue() != null) ? dateEnd.getValue().toString() : "(Not Specified)";

        btnGenerateDraft.setText("⏳ AI is synthesizing draft from history...");
        btnGenerateDraft.setDisable(true);

        javafx.concurrent.Task<String> aiDraftingTask = new javafx.concurrent.Task<>() {
            @Override
            protected String call() throws Exception {
                org.service.ClaudeOrchestrator orchestrator = new org.service.ClaudeOrchestrator();

                // Pass parameters directly without any local fallback string calculations
                return orchestrator.generateDraftFromHistory(
                        loggedInStudentName,
                        loggedInStudentId,
                        selectedType,
                        startDate,
                        endDate
                );
            }
        };

        aiDraftingTask.setOnSucceeded(event -> {
            String generatedLetterDraft = aiDraftingTask.getValue();
            javafx.application.Platform.runLater(() -> {
                txtReason.setText(generatedLetterDraft);
                btnGenerateDraft.setText("✨ Generate Draft from Chat Context");
                btnGenerateDraft.setDisable(false);
            });
        });

        aiDraftingTask.setOnFailed(event -> {
            Throwable ex = aiDraftingTask.getException();
            if (ex != null) ex.printStackTrace();

            javafx.application.Platform.runLater(() -> {
                txtReason.setText("Failed to generate an AI draft. Please ensure your backend configurations are active.");
                btnGenerateDraft.setText("✨ Generate Draft from Chat Context");
                btnGenerateDraft.setDisable(false);
            });
        });

        Thread backgroundThread = new Thread(aiDraftingTask);
        backgroundThread.setDaemon(true);
        backgroundThread.start();
    }

    @FXML
    private void handleSubmitLeave() {
        String currentActiveStudentId = this.loggedInStudentId;

        String selectedType = comboLeaveType.getValue();
        java.time.LocalDate start = dateStart.getValue();
        java.time.LocalDate end = dateEnd.getValue();
        String theoreticalReason = txtReason.getText();

        org.service.LeaveService leaveService = new org.service.LeaveService();

        try {
            String generationReceipt = leaveService.submitLeaveRequest(
                    currentActiveStudentId, selectedType, start, end, theoreticalReason
            );

            // visual for completed submission
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Submission Successful");
            alert.setHeaderText("Record Logged Successfully");
            alert.setContentText("Your application has been filed natively.\nTracking Reference Number: " + generationReceipt);
            alert.showAndWait();

            // reset form fields
            comboLeaveType.setValue(null);
            dateStart.setValue(null);
            dateEnd.setValue(null);
            txtReason.clear();

        } catch (IllegalArgumentException error) {
            // catch errors and reset
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Validation Warning");
            alert.setHeaderText("Invalid Form Metrics Detected");
            alert.setContentText(error.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void handleViewActiveLeaves() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/org/campus/app/LeaveHistory.fxml")
            );
            javafx.scene.Parent root = loader.load();

            LeaveHistoryController historyController = loader.getController();
            historyController.loadDataForStudent(this.loggedInStudentId); // Dynamic variable!

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setScene(new javafx.scene.Scene(root, 650, 450));
            stage.setTitle("My Leave History Workspace");
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.show();
        } catch (Exception e) {
            System.err.println("Error bringing up history workspace view frame:");
            e.printStackTrace();
        }
    }
}