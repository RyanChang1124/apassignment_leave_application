package org.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import org.model.LeaveApplication;
import org.service.LeaveRecordService;

import java.time.LocalDate;

public class LeaveHistoryController {

    @FXML private TableView<LeaveApplication> tableLeaveHistory;
    @FXML private TableColumn<LeaveApplication, String> colType;
    @FXML private TableColumn<LeaveApplication, LocalDate> colStart;
    @FXML private TableColumn<LeaveApplication, LocalDate> colEnd;
    @FXML private TableColumn<LeaveApplication, String> colReason;
    @FXML private TableColumn<LeaveApplication, String> colRef;
    @FXML private Button btnClose;

    private final LeaveRecordService recordService = new LeaveRecordService();

    @FXML
    public void initialize() {
        colType.setCellValueFactory(new PropertyValueFactory<>("leaveType"));
        colStart.setCellValueFactory(new PropertyValueFactory<>("startDate"));
        colEnd.setCellValueFactory(new PropertyValueFactory<>("endDate"));
        colReason.setCellValueFactory(new PropertyValueFactory<>("reason"));
        colRef.setCellValueFactory(new PropertyValueFactory<>("referenceCode"));

        System.out.println("Leave History layout cell properties bound successfully.");
    }

    /**
     * Initializes the dynamic table window content based on the active student profile session.
     */
    public void loadDataForStudent(String studentId) {
        if (studentId == null) return;
        tableLeaveHistory.setItems(recordService.fetchRecordsForStudent(studentId));
    }

    @FXML
    private void handleCloseWindow() {
        Stage stage = (Stage) btnClose.getScene().getWindow();
        stage.close();
    }
}