package servlets;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import server.RequestParser.RequestInfo;
import views.HtmlGraphWriter;
import configs.GenericConfig;
import graph.Graph;
import graph.TopicManagerSingleton;

/**
 * An HTTP controller servlet responsible for managing the lifecycle of pipeline configurations.
 * 
 * <p>The servlet processes dynamic deployment operations initiated via multipart form uploads 
 * or explicit system clears. It parses raw HTTP payloads, updates execution pipelines, and 
 * generates graph representations in real-time.</p>
 * 
 * <p><strong>Dynamic Reload Lifecycle</strong></p>
 * <p>When a new configuration layout is uploaded, the servlet executes the following transition pipeline:</p>
 * <ol>
 *   <li><strong>Multipart Parsing:</strong> Isolates the clean config text content from boundary separators, 
 *       MIME headers (e.g., {@code Content-Disposition}), and extracts the client filename.</li>
 *   <li><strong>State Teardown:</strong> Invokes {@link #unloadActiveConfiguration()} to terminate previously running 
 *       parallel agents, clear registered topics from {@link graph.TopicManagerSingleton}, and reset 
 *       buffered values. This prevents resource conflicts.</li>
 *   <li><strong>Pipeline Bootstrapping:</strong> Instantiates {@link configs.GenericConfig} with the 
 *       uploaded file, which validates the dependency flow for circular loops and launches background worker units.</li>
 *   <li><strong>Rendering and Navigation:</strong> Builds a logical model using {@link graph.Graph}, 
 *       compiles diagram layouts via {@link views.HtmlGraphWriter}, saves the markup as a safe view file 
 *       ({@code active_graph.html}), and redirects the client frame using root-relative path routing.</li>
 * </ol>
 * 
 * <p><strong>Required Core Framework Components (Internal Dependencies):</strong></p>
 * <ul>
 *   <li>{@link configs.GenericConfig} - Dynamic config compiler and background agent launcher.</li>
 *   <li>{@link graph.Graph} - Model used to parse registered relationships.</li>
 *   <li>{@link views.HtmlGraphWriter} - Code-generator compiling structural network diagram views.</li>
 *   <li>{@link servlets.TopicDisplayer} - Peer servlet whose buffered value caches are cleared on reload.</li>
 * </ul>
 * 
 * @see servlets.Servlet
 * @see configs.GenericConfig
 * @see graph.Graph
 * @see views.HtmlGraphWriter
 */
public class ConfLoader implements Servlet {

    // Keep track of the active running configuration to allow for clean, dynamic closures
    private static GenericConfig activeConfig = null;

    /**
     * Handles loading new layouts or unloading the current running topology cleanly.
     * 
     * <p>Detects if a clear action is requested, deleting active view files and restoring the blank 
     * workspace view. Otherwise, compiles and deploys uploaded multipart configuration payloads.</p>
     * 
     * @param ri       the decoded container holding the request context and parameters
     * @param toClient the output destination stream connected to the client socket
     * @throws IOException if a network or file writing error occurs
     */
    @Override
    public void handle(RequestInfo ri, OutputStream toClient) throws IOException {
        PrintWriter writer = new PrintWriter(toClient, true);

        // Check if the user requested a configuration clear/unload action
        if (ri.getUri().contains("action=clear")) {
            unloadActiveConfiguration();
            
            // Delete the temporary active graph file so it doesn't linger in storage
            File activeGraphFile = new File("html_files/active_graph.html");
            if (activeGraphFile.exists()) {
                activeGraphFile.delete();
            }

            // Redirect or return the initial blank temp page contents to clear the viewport
            File tempFile = new File("html_files/temp.html");
            if (tempFile.exists()) {
                writer.print("HTTP/1.1 200 OK\r\n");
                writer.print("Content-Type: text/html; charset=UTF-8\r\n");
                writer.print("\r\n");
                writer.print(new String(Files.readAllBytes(tempFile.toPath()), StandardCharsets.UTF_8));
            } else {
                writer.print("HTTP/1.1 200 OK\r\n\r\nCleared");
            }
            writer.flush();
            return;
        }

        byte[] contentBytes = ri.getContent();
        if (contentBytes == null || contentBytes.length == 0) {
            sendErrorResponse(writer, "Configuration body is empty.");
            return;
        }
        
        String rawBody = new String(contentBytes, StandardCharsets.UTF_8);
        if (rawBody.trim().isEmpty()) {
            sendErrorResponse(writer, "Configuration body is empty.");
            return;
        }

        String extractedFilename = parseFilename(rawBody);
        String cleanConfigContent = extractConfigContent(rawBody);

        // Uploaded file is generated inside the config_files/ directory
        File serverConfigFile = new File("config_files", extractedFilename);
        try (FileWriter fileWriter = new FileWriter(serverConfigFile)) {
            fileWriter.write(cleanConfigContent);
        }

        try {
            // Unload any previously loaded configuration first to prevent agent overlap
            unloadActiveConfiguration();

            // Load and instantiate the new dynamic configurations
            activeConfig = new GenericConfig();
            activeConfig.setConfFile(serverConfigFile.getAbsolutePath());
            activeConfig.create();

            Graph graph = new Graph();
            graph.createFromTopics();

            List<String> graphHtmlLines = HtmlGraphWriter.getGraphHTML(graph);

            // Save the generated graph HTML to a separate active file to keep the raw template safe
            File targetGraphHtml = new File("html_files/active_graph.html");
            try (FileWriter graphWriter = new FileWriter(targetGraphHtml)) {
                for (String line : graphHtmlLines) {
                    graphWriter.write(line + "\n");
                }
            }

            writer.print("HTTP/1.1 200 OK\r\n");
            writer.print("Content-Type: text/html; charset=UTF-8\r\n");
            writer.print("Connection: close\r\n");
            writer.print("\r\n");

            // Use a root relative path that adapts to any port while correctly routing through /app/
            writer.print("<html><script>window.location.replace(\"/app/active_graph.html\");</script></html>");
            writer.flush();

        } catch (Exception e) {
            sendErrorResponse(writer, "Failed to load configuration: " + e.getMessage());
        }
    }

    /**
     * Stops active agents, clears the TopicManager, and resets the values table.
     * 
     * <p>Decoupling cleanups ensures that no parallel threads remain running and no stale topic values 
     * linger in the dashboard view buffer on configuration swaps.</p>
     */
    private void unloadActiveConfiguration() {
        if (activeConfig != null) {
            activeConfig.close();
            activeConfig = null;
        }
        TopicManagerSingleton.get().clear();
        TopicDisplayer.lastTopicValues.clear();
    }

    /**
     * Parses the filename attribute from multipart request header lines.
     */
    private String parseFilename(String body) throws IOException {
        BufferedReader reader = new BufferedReader(new StringReader(body));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains("Content-Disposition:") && line.contains("filename=")) {
                int start = line.indexOf("filename=\"");
                if (start != -1) {
                    start += 10;
                    int end = line.indexOf("\"", start);
                    if (end != -1) {
                        return line.substring(start, end);
                    }
                }
            }
        }
        return "uploaded_config.conf";
    }

    /**
     * Extracts pure configuration layout line properties by stripping out multipart bounds 
     * and request header content.
     */
    private String extractConfigContent(String body) throws IOException {
        BufferedReader reader = new BufferedReader(new StringReader(body));
        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.contains("------")) {
                continue;
            }
            String lowerLine = trimmed.toLowerCase();
            if (lowerLine.startsWith("content-disposition:") || lowerLine.startsWith("content-type:")) {
                continue;
            }
            sb.append(line).append("\n");
        }

        return sb.toString().trim();
    }

    /**
     * Transmits a formatted HTTP error notification to the client.
     */
    private void sendErrorResponse(PrintWriter writer, String message) {
        writer.print("HTTP/1.1 400 Bad Request\r\n");
        writer.print("Content-Type: text/html; charset=UTF-8\r\n");
        writer.print("Connection: close\r\n");
        writer.print("\r\n");
        writer.print("<html><body><h3 style='color:red;'>Deployment Error</h3>");
        writer.print("<p>" + message + "</p></body></html>");
        writer.flush();
    }

    /**
     * Performs graceful servlet teardown, closing running background executions.
     * 
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        unloadActiveConfiguration();
    }
}