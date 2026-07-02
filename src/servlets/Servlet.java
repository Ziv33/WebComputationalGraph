package servlets;

import java.io.IOException;
import java.io.OutputStream;

import server.RequestParser.RequestInfo;

/**
 * Defines the contract for HTTP request processors (controllers) in the server's MVC architecture.
 * 
 * <p>A servlet processes pre-parsed HTTP request metadata (encapsulated as {@link server.RequestParser.RequestInfo}) 
 * and compiles standard-compliant HTTP responses, transmitting them directly to the client via a socket-connected 
 * output stream.</p>
 * 
 * <p><strong>Design and Resource Decoupling</strong></p>
 * <p>This abstraction allows low-level HTTP socket session handling and request parsing to remain completely 
 * separated from the high-level business logic (such as saving configuration files, returning dashboard views, 
 * or querying database states). Servlets are completely stateless or manage their state safely via 
 * concurrent structures, allowing them to handle multiple worker sessions simultaneously.</p>
 * 
 * <p><strong>Required Core Framework Components (Internal Dependencies):</strong></p>
 * <ul>
 *   <li>{@link server.RequestParser.RequestInfo} - Container encapsulating pre-parsed HTTP query data.</li>
 * </ul>
 * 
 * <p><strong>Code Integration Example:</strong></p>
 * <pre>{@code
 * Servlet pingServlet = new Servlet() {
 *     @Override
 *     public void handle(RequestInfo ri, OutputStream toClient) throws IOException {
 *         String responseBody = "pong";
 *         String headers = "HTTP/1.1 200 OK\r\n" +
 *                          "Content-Type: text/plain\r\n" +
 *                          "Content-Length: " + responseBody.length() + "\r\n" +
 *                          "Connection: close\r\n\r\n";
 *                          
 *         toClient.write(headers.getBytes());
 *         toClient.write(responseBody.getBytes());
 *         toClient.flush();
 *     }
 *     
 *     @Override
 *     public void close() throws IOException {
 *         // Perform mandatory resource cleanup and release system references
 *     }
 * };
 * }</pre>
 * 
 * @see server.RequestParser.RequestInfo
 * @see server.HTTPServer
 */
public interface Servlet {

    /**
     * Resolves an incoming client HTTP request and streams the corresponding response data.
     * 
     * <p>Implementations should construct a standardized HTTP status line and header blocks 
     * (including appropriate Content-Type and Content-Length properties) followed by the 
     * response payload content, and write them directly to the client's output stream.</p>
     * 
     * @param ri       the decoded container holding the request context, HTTP command, and parameters
     * @param toClient the output destination stream connected to the client socket
     * @throws IOException if a network transmission or resource reading error occurs
     */
    void handle(RequestInfo ri, OutputStream toClient) throws IOException;

    /**
     * Disposes of any persistent resources, file handles, or active caches managed by 
     * the servlet during system teardown.
     * 
     * @throws IOException if an error occurs during resource cleanup
     */
    void close() throws IOException;
}