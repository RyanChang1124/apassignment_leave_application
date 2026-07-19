package org.campus.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class mainApp extends Application {
    private Process mcpServerProcess;
    @Override
    public void start(Stage stage) throws IOException {
        startMcpServer();
        FXMLLoader fxmlLoader = new FXMLLoader(mainApp.class.getResource("VerificationScreen.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 320, 240);
        stage.setTitle("Hello!");
        stage.setScene(scene);
        stage.show();
    }

    private void startMcpServer() {
        try {
            System.out.println("Initializing background Java RAG/MCP server under Java 25...");

            String projectRoot = System.getProperty("user.dir");
            java.io.File jarFile = new java.io.File(projectRoot, "src/campus-info-mcp-server/target/campus-info-mcp-server.jar");
            java.io.File serverWorkingDir = new java.io.File(projectRoot, "src/campus-info-mcp-server");

            if (!jarFile.exists()) {
                System.err.println("ERROR: Server JAR missing at: " + jarFile.getAbsolutePath());
                return;
            }

            // Dynamically resolve the active Java 25 runtime environment path
            String javaBinary = System.getProperty("java.home")
                    + java.io.File.separator + "bin"
                    + java.io.File.separator + "java";

            ProcessBuilder pb = new ProcessBuilder(javaBinary, "-jar", jarFile.getAbsolutePath());
            pb.directory(serverWorkingDir);

            // Redirect console outputs to your workspace root directory log
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new java.io.File(projectRoot, "mcp_server_output.log")));

            mcpServerProcess = pb.start();
            System.out.println("⏳ Background process spawned. Waiting for server port binding...");

            // Give the background server 3 seconds to complete its boot stage
            Thread.sleep(3000);
            System.out.println("✅ Server initialization sequence complete.");

        } catch (java.io.IOException e) {
            System.err.println("❌ Critical: Could not launch background process. " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop() throws Exception {
        // Reads from the class-level field to kill the process on exit
        if (mcpServerProcess != null && mcpServerProcess.isAlive()) {
            System.out.println("🛑 Shutting down background RAG/MCP server gracefully...");
            mcpServerProcess.destroy();
        }
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

