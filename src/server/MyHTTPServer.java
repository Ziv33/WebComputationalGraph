package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import server.RequestParser.RequestInfo;
import servlets.Servlet;

/**
 * A multi-threaded HTTP server implementation of {@link server.HTTPServer} that extends {@link java.lang.Thread}.
 * 
 * <p>The server runs its main connection-accepting socket loop on a dedicated background thread, 
 * preventing the application's primary execution thread from blocking. Incoming connection requests 
 * are delegated to an internal thread pool executor for concurrent processing.</p>
 * 
 * <p><strong>Core Architectural Features</strong></p>
 * <ul>
 *   <li><strong>Thread Pool Concurrency:</strong> Client requests are handled concurrently using a 
 *       fixed-size thread pool managed by an {@link java.util.concurrent.ExecutorService}. This caps the 
 *       maximum number of active client threads, protecting system resources against client volume spikes.</li>
 *   <li><strong>Longest Prefix Routing:</strong> Incoming paths are mapped to servlet controllers using 
 *       the longest-prefix matching algorithm. This enables a registered servlet (e.g., mapped to {@code "/app/"}) 
 *       to handle diverse nested URIs and request parameters (e.g., {@code "/app/download/user?id=123"}).</li>
 *   <li><strong>Periodic Shutdown Evaluation:</strong> Setting a socket timeout of 1000 milliseconds on 
 *       {@link java.net.ServerSocket} ensures that the blocking {@code accept()} method wakes up periodically. 
 *       This allows the server loop to evaluate the state of the volatile {@code running} exit flag and shut 
 *       down cleanly when requested.</li>
 * </ul>
 * 
 * <p><strong>Required Core Framework Components (Internal Dependencies):</strong></p>
 * <ul>
 *   <li>{@link server.HTTPServer} - Main interface specifying server lifecycle controls.</li>
 *   <li>{@link servlets.Servlet} - Controller interface used to resolve requests.</li>
 *   <li>{@link server.RequestParser} - Parsing engine that extracts structural request contexts.</li>
 * </ul>
 * 
 * <p><strong>Code Integration Example:</strong></p>
 * <pre>{@code
 * // Start the server listening on port 8080 with 10 worker threads
 * MyHTTPServer server = new MyHTTPServer(8080, 10);
 * 
 * // Bind servlet controllers to routes
 * server.addServlet("GET", "/publish", new TopicDisplayer());
 * server.addServlet("POST", "/upload", new ConfLoader());
 * 
 * // Start background listening
 * server.start();
 * 
 * // Close resources when done
 * server.close();
 * }</pre>
 * 
 * @see server.HTTPServer
 * @see servlets.Servlet
 * @see server.RequestParser
 * @see java.util.concurrent.ExecutorService
 */
public class MyHTTPServer extends Thread implements HTTPServer {

    // The network port where the server will listen for client requests
    private final int port;

    // Volatile flag to control the execution loop across multiple threads safely
    private volatile boolean running = true;

    // Thread pool executor to manage concurrent client worker tasks
    private final ExecutorService executor;

    // Server socket used to accept client connections
    private ServerSocket serverSocket;

    // Thread safe registries for each supported HTTP method type
    private final Map<String, Servlet> getServlets;
    private final Map<String, Servlet> postServlets;
    private final Map<String, Servlet> deleteServlets;

    /**
     * Initializes the server with a port and thread capacity.
     * Sets up concurrent hash maps to hold registered servlets safely.
     * The port parameter specifies the port number to listen on.
     * The nThreads parameter specifies the maximum number of threads in the pool.
     * 
     * @param port     the local port number to bind the server socket to
     * @param nThreads the maximum number of background threads in the executor pool
     */
    public MyHTTPServer(int port, int nThreads) {

        this.port = port;

        this.executor = Executors.newFixedThreadPool(nThreads);

        this.getServlets = new ConcurrentHashMap<>();
        this.postServlets = new ConcurrentHashMap<>();
        this.deleteServlets = new ConcurrentHashMap<>();
    }

    /**
     * Registers a servlet to handle requests matching the specified HTTP command and URI route prefix.
     * 
     * @param httpCommanmd the targeted HTTP request method (e.g., "GET", "POST", "DELETE")
     * @param uri          the target request URI path prefix
     * @param s            the servlet controller instance to execute
     * @throws IllegalArgumentException if the provided HTTP command is unsupported
     */
    @Override
    public void addServlet(String httpCommanmd, String uri, Servlet s) {
        getMap(httpCommanmd).put(uri, s);
    }

    /**
     * Unregisters the servlet bound to the specified HTTP command and URI route prefix.
     * 
     * @param httpCommanmd the targeted HTTP request method to clear
     * @param uri          the mapped request URI path prefix to clear
     * @throws IllegalArgumentException if the provided HTTP command is unsupported
     */
    @Override
    public void removeServlet(String httpCommanmd, String uri) {
        getMap(httpCommanmd).remove(uri);
    }

    /**
     * Main execution logic running in a background thread.
     * Listens for client connections and submits them to the thread pool.
     */
    @Override
    public void run() {
        try {

            serverSocket = new ServerSocket(port);
            System.out.println("MyHTTPServer [Success]: HTTP Server is up and listening on port " + port);

            
            // As required by the task guidelines, we wait for a client for one second.
            // Setting this socket timeout to 1000 milliseconds lets us wake up
            // periodically to check if the running flag is still true.
            serverSocket.setSoTimeout(1000);

            while (running) {
                try {
                    Socket client = serverSocket.accept();

                    // Delegate the active client processing to our thread pool
                    executor.execute(() -> handleClient(client));

                } catch (SocketTimeoutException ignored) {
                    // This is expected every second when no client connects.
                    // It lets the loop check if the server has been stopped.
                }
            }

        } catch (IOException e) {
            // If the server is supposed to be running, this is a real issue (like a busy port).
            // If running is false, this is just the expected result of calling close().
            if (running) {
                if (serverSocket == null) {
                    System.err.println("Server failed to start on port " + port + ": " + e.getMessage());
                } else {
                    System.err.println("Server failed to run: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Handles the interaction with an individual client connection.
     * Uses the try with resources pattern to automatically close the socket.
     * Closing the socket is essential as it signals the end of transmission to the client.
     * The client parameter is the active socket connection to the client.
     * Catches both IOExceptions and IllegalArgumentExceptions to protect the thread pool.
     * 
     * @param client the active socket connection to the client
     */
    private void handleClient(Socket client) {
        // Declaring a local resource variable (instead just writing try (client)) to ensure compatibility 
    	// with Java 8 and older versions
        try (Socket socketResource = client) {

            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));

            RequestInfo ri = RequestParser.parseRequest(reader);

            if (ri == null) {
                return;
            }

            // This call to findServlet will trigger getMap, which may throw an IllegalArgumentException
            Servlet servlet = findServlet(ri.getHttpCommand(), ri.getUri());

            if (servlet != null) {
                servlet.handle(ri, client.getOutputStream());
            }

        } catch (IOException e) {
            // Logs the network connection exception to the console
            System.err.println("Error handling client connection: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            // Logs the unsupported protocol exception to the console
            System.err.println("Error handling client connection (unsupported protocol exception): " + e.getMessage());
        }
    }
    
    /**
     * Searches for a registered servlet using the longest prefix matching method.
     * This matching style allows servlets to process requests containing different query parameters.
     * The command parameter is the HTTP verb name such as GET, POST, or DELETE.
     * The uri parameter is the path requested by the client.
     * Returns the servlet with the longest prefix match, or null if none is found.
     * 
     * @param command the HTTP verb (e.g., "GET", "POST", "DELETE")
     * @param uri     the raw destination path requested by the client
     * @return the servlet with the longest prefix match, or null if none is found
     */
    private Servlet findServlet(String command, String uri) {
        Map<String, Servlet> map = getMap(command);

        Servlet best = null;
        int bestLength = -1;

        for (Map.Entry<String, Servlet> e : map.entrySet()) {

            String registered = e.getKey();

            // Check if the requested path starts with the registered prefix
            if (uri.startsWith(registered) && (registered.length() > bestLength)) {

                best = e.getValue();
                bestLength = registered.length();
            }
        }

        return best;
    }

    /**
     * Helper method to retrieve the correct registry map based on the HTTP command which is got as the input.
     * Throws an IllegalArgumentException if the HTTP command is not supported.
     * 
     * @param command the raw HTTP command string to resolve
     * @return the associated map holding path registrations
     * @throws IllegalArgumentException if the HTTP command method is unsupported
     */
    private Map<String, Servlet> getMap(String command) {
        switch (command.toUpperCase()) {

            case "GET":
                return getServlets;

            case "POST":
                return postServlets;

            case "DELETE":
                return deleteServlets;

            default:
                throw new IllegalArgumentException("Unsupported HTTP method requested: " + command);
        }
    }
    
    /**
     * Shuts down the HTTP server.
     * Stops the main loop, closes open resources, and terminates the thread pool executor.
     */
    @Override
    public void close() {
        // Set the running flag to false to stop the loop
        running = false;

        try {

            // Close the server socket to stop accepting new clients
            if (serverSocket != null) {
                serverSocket.close();
            }

            // Close all registered servlet instances to release their resources
            for (Servlet s : getServlets.values()) {
                s.close();
            }

            for (Servlet s : postServlets.values()) {
                s.close();
            }

            for (Servlet s : deleteServlets.values()) {
                s.close();
            }

        } catch (IOException e) {
            // Catch exceptions during the closing sequence
        	System.err.println("Error during server closing: " + e.getMessage());
        }

        // Shut down the worker thread pool
        executor.shutdown();
    }
}