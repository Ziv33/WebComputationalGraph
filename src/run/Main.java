package run;

import server.*;
import servlets.ConfLoader;
import servlets.HtmlLoader;
import servlets.TopicDisplayer;

/**
 * The primary bootstrap entry point of the application, responsible for configuring 
 * routing servlets, initializing port bindings, and launching the concurrent HTTP server.
 * 
 * <p><strong>Unified Architectural Integration</strong></p>
 * <p>The main class serves as the composition root that integrates the model, view, and controller (MVC) 
 * layers of the system into a cohesive running application:</p>
 * <ul>
 *   <li><strong>Controller Layer:</strong> Spawns {@link server.MyHTTPServer} bound to port {@code 8080} 
 *       with a thread-pool capacity of {@code 5} background worker threads.</li>
 * 	 <li><strong>Routing &amp; Business Logic:</strong> Maps incoming REST API queries, layout configurations, 
 *       and asset requests to specific servlet implementations:
 *       <ul>
 *         <li>{@code /publish} - Routed to {@link servlets.TopicDisplayer} to handle real-time value tracking, 
 *             REST JSON querying, and dashboard rendering.</li>
 *         <li>{@code /upload} - Routed to {@link servlets.ConfLoader} to process dynamic multipart config file uploads 
 *             and compile graph visualization representations.</li>
 *         <li>{@code /app/} - Routed to {@link servlets.HtmlLoader} to serve static assets (scripts, styles, and markup) 
 *             safely from the local {@code "html_files"} workspace.</li>
 *       </ul>
 *   </li>
 *   <li><strong>Background Execution Loop:</strong> Initiates the non-blocking accept loop via {@code server.start()}.</li>
 * </ul>
 * 
 * <p><strong>Graceful Lifecycle Shutdown</strong></p>
 * <p>The execution thread blocks on {@link java.lang.System#in} using {@code System.in.read()}. This allows 
 * administrators to keep the application running safely in the background, and shut it down cleanly 
 * by pressing 'Enter' in the terminal. The termination sequence triggers {@code server.close()}, which safely 
 * closes open server sockets, terminates pool executors, and unregisters observation trackers to prevent thread and memory leaks.</p>
 * 
 * @see server.MyHTTPServer
 * @see servlets.TopicDisplayer
 * @see servlets.ConfLoader
 * @see servlets.HtmlLoader
 */
public class Main {

    /**
     * Bootstraps the application, registers route controllers, and starts the concurrent HTTP server.
     * 
     * @param args the command-line arguments (Currently not utilized)
     * @throws Exception if a socket binding or resource initialization error occurs
     */
	public static void main(String[] args) throws Exception {
		HTTPServer server=new MyHTTPServer(8080,5);
		server.addServlet("GET", "/publish", new TopicDisplayer());
		server.addServlet("POST", "/upload", new ConfLoader());
		server.addServlet("GET", "/app/", new HtmlLoader("html_files"));
		server.start();
		System.in.read();
		server.close();
		System.out.println("done");
	}
}