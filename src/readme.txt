Campus Policy Assistant - Part B

Project Info
Project Name: Leave Application System with RAG & MCP
Development environment:

- JDK 25
- IntelliJ IDEA
- Maven
- JavaFX

How to run the project

1. Extract the compressed project file, open the root folder APassignment copy in IntelliJ IDEA
2. Set Project SDK and language level to JDK 25
3. Open IDE Settings, find Maven -> Runner, change JRE to Java 25
4. On the right Maven sidebar:
  - Click Reload All Maven Projects to load dependencies
  - Double click package under Lifecycle to compile the project
5. Expand Plugins -> javafx, run javafx:run to launch the application

Project Structure
APassignment copy
├── .mvn              Maven configuration files
├── src               All Java source code (MVC packages here)
├── pom.xml           Maven build config
├── campus_handbook.txt  Local knowledge base for RAG
├── student_leaves.csv  Store submitted leave records
├── students.txt      Student login identity data

MVC Architecture Description
This project follows the MVC layered structure for code organization:

1. View: All JavaFX pages, including login window, leave application form and consultation chat interface. It is only responsible for page display and user operation, without any business logic.
2. Controller: The bridge connecting interface and data. It responds to button clicks and input behaviors, calls related business modules, and feeds back results to the page.
3. Model: Core business and data layer, including reading and writing local txt/csv files, MCP template acquisition, RAG intelligent query, and leave data management. It runs independently of the UI.

Main tools and resources used in the project:

1. MCP Tool: submit_leave_application
  - Submits completed student leave application information to the MCP server.

2. MCP Tool: draft_leave_request
  - Generates a structured leave request draft based on student input.

3. MCP Resource: Leave Application Template
  - Retrieves the standard leave application form template to simplify the form filling process.

4. RAG Question Answering Feature
  - Uses the local campus handbook text file as a knowledge base to provide intelligent answers about campus leave policies.

RAG Question Answering Feature

The system implements a RAG-based campus policy assistant.
It retrieves relevant information from the local knowledge base file:

- campus_handbook.txt

The retrieved information is used to generate answers related to campus policies and leave regulations.

Core Functions

1. Student identity verification: Log in with student ID and full name
2. Intelligent campus policy query (RAG): Input questions about leave rules to get replies
Known minor issue: The returned message contains extra JSON raw data, while valid text content can be displayed normally
3. One-click load leave form template via MCP
4. Fill in and submit leave requests
5. View all submitted active leave applications