package servlets;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import server.RequestParser.RequestInfo;

/**
 * An HTTP controller servlet responsible for retrieving and streaming static resource files 
 * (such as HTML, JavaScript, etc.) from a local filesystem workspace.
 * 
 * <p>The servlet processes resource queries routed through path patterns starting with the 
 * {@code "/app/"} prefix, translating URI segment elements into secure local filesystem paths.</p>
 * 
 * <p><strong>Dynamic Directory Scoping (Clean Architecture)</strong></p>
 * <p>To adhere to standard software reusability practices and prevent hardcoded environment configurations, 
 * the target parent directory path is dynamically injected into the servlet at construction time. This 
 * decouples the server's network routing context from local storage structures, allowing easy deployment 
 * across varying platforms or filesystem locations.</p>
 * 
 * <p><strong>Path Resolution &amp; Resource Safety</strong></p>
 * <p>The servlet maps incoming URIs to filesystem targets using the parsed {@code uriSegments} array. 
 * Since index {@code 0} holds the route prefix (e.g., {@code "app"}), the servlet reconstructs the relative sub-path 
 * starting from segment index {@code 1} using the platform-specific directory separator ({@link java.io.File#separator}). 
 * Access is restricted to files; any requests targeting directories or non-existent files are safely rejected 
 * with a standardized HTTP 404 response.</p>
 * 
 * <p><strong>Required Core Framework Components (Internal Dependencies):</strong></p>
 * <ul>
 *   <li>{@link server.RequestParser.RequestInfo} - The decoded request properties container carrying path segments.</li>
 * </ul>
 * 
 * <p><strong>Code Integration Example:</strong></p>
 * <pre>{@code
 * // Bind the asset loader to stream resources from the local "html_files" directory
 * HtmlLoader assetLoader = new HtmlLoader("html_files");
 * 
 * // Registered to handle all HTTP GET requests targeting URIs starting with /app/
 * server.addServlet("GET", "/app/", assetLoader);
 * }</pre>
 * 
 * @see servlets.Servlet
 * @see server.RequestParser.RequestInfo
 */
public class HtmlLoader implements Servlet {

    // Target parent directory path configured via the constructor
    private final String rootDirectory;

    /**
     * Constructs an HtmlLoader pointing to a local filesystem workspace.
     * 
     * @param rootDirectory the directory path on the local filesystem containing the static files to stream
     */
    public HtmlLoader(String rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    /**
     * Resolves requests targeting the "/app/" route to load and stream files.
     * Extracts path segments, matches target files, evaluates MIME types, and streams bytes.
     * 
     * <p>Constructs the local target path dynamically from index 1 of the request segments, 
     * validates resource properties, and streams raw bytes alongside standard HTTP headers 
     * (including matching Content-Type and Content-Length).</p>
     * 
     * @param ri       the decoded container holding the request context and parameters
     * @param toClient the output destination stream connected to the client socket
     * @throws IOException if a network or file reading error occurs
     */
    @Override
    public void handle(RequestInfo ri, OutputStream toClient) throws IOException {
        String[] segments = ri.getUriSegments();
        
        // Ensure that there is at least one segment specified after the "/app/" prefix
        if (segments == null || segments.length <= 1) {
            sendNotFoundResponse(toClient);
            return;
        }

        // Reconstruct the relative path of the file from the URI segments
        StringBuilder relativePath = new StringBuilder();
        for (int i = 1; i < segments.length; i++) {
            relativePath.append(segments[i]);
            if (i < segments.length - 1) {
                relativePath.append(File.separator);
            }
        }

        File targetFile = new File(rootDirectory, relativePath.toString());

        // Respond with a 404 status if the requested path is a folder or does not exist
        if (!targetFile.exists() || targetFile.isDirectory()) {
            sendNotFoundResponse(toClient);
            return;
        }

        // Safely read all bytes of the target resource file
        byte[] fileBytes = Files.readAllBytes(targetFile.toPath());

        // Determine the matching Content-Type header based on the file extension
        String mimeType = getContentType(targetFile.getName());

        // Construct standard HTTP success response headers
        String headers = "HTTP/1.1 200 OK\r\n" +
                         "Content-Type: " + mimeType + "\r\n" +
                         "Content-Length: " + fileBytes.length + "\r\n" +
                         "Connection: close\r\n\r\n";

        // Stream the parsed headers followed by the raw resource bytes
        toClient.write(headers.getBytes(StandardCharsets.UTF_8));
        toClient.write(fileBytes);
        toClient.flush();
    }

    /**
     * Maps file extensions to standard MIME content type definitions.
     * 
     * @param filename the name of the target file to evaluate
     * @return the associated MIME Content-Type header value
     */
    private String getContentType(String filename) {
        if (filename.endsWith(".html") || filename.endsWith(".htm")) {
            return "text/html; charset=UTF-8";
        } else if (filename.endsWith(".css")) {
            return "text/css";
        } else if (filename.endsWith(".js")) {
            return "application/javascript";
        } else if (filename.endsWith(".png")) {
            return "image/png";
        } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "text/plain";
    }

    /**
     * Transmits a standard HTTP 404 Not Found error response back to the client.
     * 
     * @param toClient the output destination stream connected to the client socket
     * @throws IOException if a network transmission error occurs
     */
    private void sendNotFoundResponse(OutputStream toClient) throws IOException {
        String html = "<!DOCTYPE html>\n<html>\n<body>\n<h2>404 - Resource Not Found</h2>\n" +
                      "<p>The requested static file could not be located on the server.</p>\n</body>\n</html>";
        byte[] bodyBytes = html.getBytes(StandardCharsets.UTF_8);

        String headers = "HTTP/1.1 404 Not Found\r\n" +
                         "Content-Type: text/html; charset=UTF-8\r\n" +
                         "Content-Length: " + bodyBytes.length + "\r\n" +
                         "Connection: close\r\n\r\n";

        toClient.write(headers.getBytes(StandardCharsets.UTF_8));
        toClient.write(bodyBytes);
        toClient.flush();
    }

    /**
     * Closes the servlet resources. No persistent stream or file handles need to be closed.
     * 
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        // No persistent stream or file handles need to be closed
    }
}