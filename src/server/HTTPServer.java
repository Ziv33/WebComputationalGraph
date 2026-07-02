package server;

import servlets.Servlet;

/**
 * Defines the operational contract for a lightweight, concurrent HTTP server.
 * 
 * <p><strong>Background Concurrency Model:</strong></p>
 * <p>To prevent blocking network I/O operations (such as waiting for incoming TCP client connections 
 * on a socket) from halting the primary application flow, the {@code HTTPServer} extends {@link java.lang.Runnable}. 
 * The main connection-accepting loop is executed on a dedicated background thread, while individual 
 * client connections are delegated to an internal thread pool (Executor Service) for concurrent execution.</p>
 * 
 * <p><strong>Controller routing (Servlets)</strong></p>
 * <p>The server allows developers to map incoming HTTP request contexts (combinations of HTTP methods 
 * like "GET", "POST", "DELETE" and target URI paths) to custom controller implementations of the 
 * {@link servlets.Servlet} interface. This decouples the low-level network session management 
 * from the high-level request-handling and response-generation logic.</p>
 * 
 * <p><strong>Code Integration Example:</strong></p>
 * <pre>{@code
 * HTTPServer server = new MyHTTPServer(8080, 10);
 * 
 * // Register a custom servlet to handle API resource GET requests
 * server.addServlet("GET", "/api/resource", new TopicDisplayer());
 * 
 * // Launch the server background listener thread
 * server.start();
 * System.out.println("HTTP Server listening on port 8080...");
 * 
 * // Gracefully shut down sockets and thread pools on application shutdown
 * Runtime.getRuntime().addShutdownHook(new Thread(() -> {
 *     server.close();
 * }));
 * }</pre>
 * 
 * @see servlets.Servlet
 * @see java.lang.Runnable
 */
public interface HTTPServer extends Runnable{

    /**
     * Associates a specific servlet controller instance with an HTTP command and URI route.
     * 
     * <p>Subsequent client requests matching the specified HTTP command (such as "GET" or "POST") 
     * and having a URI starting with or matching the specified URI path prefix will be routed 
     * to the designated servlet's {@code handle()} method.</p>
     * 
     * @param httpCommanmd the targeted HTTP request method (e.g., "GET", "POST", "DELETE")
     * @param uri          the target request URI path prefix (e.g., "/publish", "/app/")
     * @param s            the servlet controller instance to execute
     */
    public void addServlet(String httpCommanmd, String uri, Servlet s);

    /**
     * Disassociates and removes the servlet mapped to the specified HTTP command and URI route.
     * 
     * @param httpCommanmd the targeted HTTP request method to clear
     * @param uri          the mapped request URI path prefix to clear
     */
    public void removeServlet(String httpCommanmd, String uri);

    /**
     * Spawns the primary socket listener thread to start accepting incoming TCP client connections.
     * 
     * <p>This method initializes the server socket and executes the server's {@link #run()} loop 
     * inside a new background thread, returning execution immediately to the caller.</p>
     */
    public void start();

    /**
     * Gracefully terminates the HTTP server, closing open sockets, halting session thread pools, 
     * and releasing system port bindings.
     */
    public void close();
}