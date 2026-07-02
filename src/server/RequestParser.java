package server;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility parser engine designed to decode incoming HTTP request protocol streams 
 * into structured, type-safe {@link server.RequestParser.RequestInfo} data objects.
 * 
 * <p><strong>Dynamic HTTP Decoding Logic</strong></p>
 * <p>The parser processes network TCP/IP byte streams sequentially according to HTTP standards:</p>
 * <ol>
 *   <li><strong>Request Line Parsing:</strong> Extracts the HTTP verb (e.g., "GET", "POST"), the full URI, 
 *       and splits path segments and URI query-string key/value parameters (e.g., {@code ?topic=A&message=5}).</li>
 *   <li><strong>Header Block Evaluation:</strong> Processes headers line by line until an empty line is reached. 
 *       It searches specifically for the {@code Content-Length} header to determine the size of any attached body content.</li>
 *   <li><strong>Defensive Body Retrieval:</strong> Reads request body data only if {@code Content-Length} is greater 
 *       than zero. It checks {@link java.io.BufferedReader#ready()} before reading bytes to prevent execution threads 
 *       from blocking indefinitely on active sockets.</li>
 * </ol>
 * 
 * <p><strong>Required Core Framework Components (Internal Dependencies):</strong></p>
 * <ul>
 *   <li>{@link server.RequestParser.RequestInfo} - Inner data container representing the parsed request context.</li>
 * </ul>
 * 
 * <p><strong>Code Integration Example:</strong></p>
 * <pre>{@code
 * BufferedReader socketReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
 * 
 * // Decode the raw network stream into a structured RequestInfo entity
 * RequestInfo info = RequestParser.parseRequest(socketReader);
 * 
 * if (info != null) {
 *     System.out.println("HTTP Command: " + info.getHttpCommand());
 *     System.out.println("Requested URI: " + info.getUri());
 *     System.out.println("Target Parameter value: " + info.getParameters().get("topic"));
 * }
 * }</pre>
 * 
 * @see server.RequestParser.RequestInfo
 */
public class RequestParser {

    /**
     * Parses the incoming HTTP request stream and maps its components into a structured RequestInfo object.
     * 
     * <p>Processes the request line, path segments, query parameters, header blocks, and reads body payloads 
     * defensively, checking socket buffer readiness to prevent indefinite blocking.</p>
     * 
     * @param reader the buffered character stream reader linked to the client connection socket
     * @return a decoded, structured RequestInfo container, or null if the stream is empty or invalid
     * @throws IOException if a network reading error occurs during execution
     */
    public static RequestInfo parseRequest(BufferedReader reader) throws IOException {        
        String requestLine = reader.readLine();

        if (requestLine == null || requestLine.isEmpty()) {
            return null;
        }

        // Split the first line into command, URI, and HTTP protocol version
        String[] firstLineParts = requestLine.split(" ");

        String httpCommand = firstLineParts[0];
        String uri = firstLineParts[1];

        Map<String, String> parameters = new HashMap<>();

        String pathOnly = uri;

        int queryIndex = uri.indexOf('?');

        // Extract key value query parameters from the URI if a query string is present
        if (queryIndex >= 0) {
            pathOnly = uri.substring(0, queryIndex);

            String query = uri.substring(queryIndex + 1);

            String[] pairs = query.split("&");

            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);

                if (kv.length == 2) {
                    parameters.put(kv[0], kv[1]);
                }
            }
        }

        String[] uriSegments;

        // Split the path into segment strings using the slash character
        if (pathOnly.equals("/")) {
            uriSegments = new String[0];
        } else {
            uriSegments = pathOnly.substring(1).split("/");
        }

        int contentLength = -1;

        String line;

        // Parse the header block line by line.
        // The headers section ends when we encounter an empty line.
        while ((line = reader.readLine()) != null) {

            if (line.isEmpty()) {
                break;
            }

            String lower = line.toLowerCase();

            // Find and parse the Content Length header to know the size of the body payload
            if (lower.startsWith("content-length:")) {
                try {
                    contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                } catch (NumberFormatException e) {
                    // Invalid content length header
                    System.err.println("Invalid content length header found: " + e.getMessage());
                }
            }
        }

        ByteArrayOutputStream content = new ByteArrayOutputStream();

        // Only parse the request body if a positive Content Length header was received.
        // This check prevents the thread from blocking indefinitely on open client sockets.
        if (contentLength > 0) {
            
            // Parse parameters from the request body
            while ((line = reader.readLine()) != null) {

                if (line.isEmpty()) {
                    break;
                }

                int idx = line.indexOf('=');

                if (idx > 0) {
                    parameters.put(line.substring(0, idx),line.substring(idx + 1));
                }
            }

            // Read the raw body content bytes
            while ((line = reader.readLine()) != null) {

                if (line.isEmpty()) {
                    break;
                }

                content.write((line + "\n").getBytes());

                // Stop reading if no more characters are immediately available in the buffer
                if (!reader.ready()) {
                    break;
                }
            }
        }
        
        return new RequestInfo(httpCommand, uri, uriSegments, parameters, content.toByteArray());
    }
	
	/**
     * An immutable data container that encapsulates decoded HTTP request properties.
     */
    public static class RequestInfo {
        private final String httpCommand;
        private final String uri;
        private final String[] uriSegments;
        private final Map<String, String> parameters;
        private final byte[] content;

        /**
         * Constructs a RequestInfo containing the parsed elements of the client HTTP request.
         * 
         * @param httpCommand the HTTP verb (e.g., "GET", "POST", "DELETE")
         * @param uri         the full requested URI string
         * @param uriSegments the array of split target path segments
         * @param parameters  the resolved map of query string and body parameters
         * @param content     the raw bytes of the request content body
         */
        public RequestInfo(String httpCommand, String uri, String[] uriSegments, Map<String, String> parameters, byte[] content) {
            this.httpCommand = httpCommand;
            this.uri = uri;
            this.uriSegments = uriSegments;
            this.parameters = parameters;
            this.content = content;
        }

        /**
         * Retrieves the HTTP request verb.
         * 
         * @return the HTTP command (e.g., "GET")
         */
        public String getHttpCommand() {
            return httpCommand;
        }

        /**
         * Retrieves the full request URI.
         * 
         * @return the requested URI string
         */
        public String getUri() {
            return uri;
        }

        /**
         * Retrieves the split path segments of the requested target URI.
         * 
         * @return an array containing individual path segment strings
         */
        public String[] getUriSegments() {
            return uriSegments;
        }

        /**
         * Retrieves the decoded map of request parameters.
         * 
         * @return a map containing query-string and request body key/value parameter pairs
         */
        public Map<String, String> getParameters() {
            return parameters;
        }

        /**
         * Retrieves the raw bytes representing the request body content.
         * 
         * @return a byte array containing the request payload content
         */
        public byte[] getContent() {
            return content;
        }
    }
}