package servlets;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import server.RequestParser.RequestInfo;
import views.HtmlGraphWriter;
import graph.Graph;
import graph.TopicManagerSingleton;
import graph.Topic;
import graph.Message;
import graph.Agent;

/**
 * An HTTP controller servlet responsible for displaying, updating, and resetting 
 * the real-time value table of all registered system topics.
 * 
 * <p>The servlet functions as a dual-purpose controller, serving both a dynamic, zero-flicker 
 * HTML dashboard view for operators and a JSON REST API endpoint for real-time background tracking.</p>
 * 
 * <p><strong>Reactive Value Tracking (Background Observer Pattern)</strong></p>
 * <p>To capture background values calculated asynchronously by parallel agent threads (such as 
 * those wrapped in {@link graph.ParallelAgent}), the servlet dynamically registers dedicated tracker 
 * agents (named {@code "TableTracker_[TopicName]"}) to subscribe to all active topics. When any agent 
 * publishes a result, these trackers capture the message payload immediately and update a shared, 
 * thread-safe concurrent map ({@link #lastTopicValues}). This ensures that the user interface remains 
 * fully synchronized with the state of the active model layer.</p>
 * 
 * <p><strong>Zero-Flicker Client Engine (DOM Diffing)</strong></p>
 * <p>To avoid screen flickering and minimize browser rendering overhead, the client dashboard utilizes 
 * a lightweight DOM-diffing JavaScript polling script. Every 500ms, the browser queries the JSON REST API 
 * path ({@code /publish?action=values}) to fetch topic updates. The script then compares the retrieved data 
 * against the active table rows and modifies <em>only</em> the specific table cells whose text has changed, 
 * providing a highly responsive user experience.</p>
 * 
 * <p><strong>Cascading State Reset Integration</strong></p>
 * <p>When a reset command is received ({@code action=reset}), the servlet clears the buffered value maps 
 * and cascades {@code reset()} calls to all subscribers and publishers registered across all topics. This 
 * cleanly resets cached values back to their default structural starting values (e.g., {@code 0.0}).</p>
 * 
 * <p><strong>Required Core Framework Components (Internal Dependencies):</strong></p>
 * <ul>
 *   <li>{@link graph.TopicManagerSingleton} - Singleton directory used to resolve and iterate active topics.</li>
 *   <li>{@link graph.Agent} - Core abstraction interface used to instantiate background tracker agents.</li>
 *   <li>{@link views.HtmlGraphWriter} - Renders updated graph visualizations dynamically during page loads.</li>
 * </ul>
 * 
 * @see servlets.Servlet
 * @see graph.TopicManagerSingleton
 * @see graph.Agent
 * @see views.HtmlGraphWriter
 */
public class TopicDisplayer implements Servlet {

	/** Shared thread-safe map storing the last known string values for each topic. */
    public static final ConcurrentHashMap<String, String> lastTopicValues = new ConcurrentHashMap<>();

    /**
     * Handles routing requests to serve the zero-flicker HTML dashboard, returns JSON values, 
     * publishes messages to target topics, or triggers cascading state resets.
     * 
     * @param ri       the decoded container holding the request context and parameters
     * @param toClient the output destination stream connected to the client socket
     * @throws IOException if a network or file writing error occurs
     */
    @Override
    public void handle(RequestInfo ri, OutputStream toClient) throws IOException {
        PrintWriter writer = new PrintWriter(toClient, true);

        Map<String, String> params = parseQueryParams(ri.getUri());
        String topicName = params.get("topic");
        String messageVal = params.get("message");
        String action = params.get("action");

        TopicManagerSingleton.TopicManager tm = TopicManagerSingleton.get();

        // 1. JSON API endpoint for dynamic background polling
        if ("values".equalsIgnoreCase(action)) {
            writer.print("HTTP/1.1 200 OK\r\n");
            writer.print("Content-Type: application/json; charset=UTF-8\r\n");
            writer.print("Access-Control-Allow-Origin: *\r\n");
            writer.print("Connection: close\r\n");
            writer.print("\r\n");
            
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Topic t : tm.getTopics()) {
                if (!first) {
                    json.append(",");
                }
                String rawVal = lastTopicValues.getOrDefault(t.name, "N/A");
                String formatted = formatNumericValue(rawVal);
                json.append(String.format("\"%s\":\"%s\"", t.name, formatted));
                first = false;
            }
            json.append("}");
            writer.print(json.toString());
            writer.flush();
            return;
        }

        // 2. Handle background reset action
        if ("reset".equalsIgnoreCase(action)) {
            lastTopicValues.clear();
            for (Topic t : tm.getTopics()) {
                for (Agent a : t.getSubs()) {
                    a.reset();
                }
                for (Agent a : t.getPubs()) {
                    a.reset();
                }
            }
            // By not returning here, we let the execution naturally fall through 
            // and render a fresh, clean HTML table showing "N/A" for all topics
        }
        
        // 3. Handle standard message publication (Updates state first, then falls through to draw table)
        if (topicName != null && !topicName.trim().isEmpty() && messageVal != null) {
            Topic topic = tm.getTopic(topicName);
            if (topic != null) {
                topic.publish(new Message(messageVal));
                lastTopicValues.put(topicName, messageVal);
            }
        }

        // 4. Register trackers so that they capture any background calculations instantly
        for (Topic t : tm.getTopics()) {
            registerTrackerIfAbsent(t);
        }

        // 5. Regenerate the graph and overwrite the static active file for initial load speed
        Graph graph = new Graph();
        graph.createFromTopics();
        List<String> graphHtmlLines = HtmlGraphWriter.getGraphHTML(graph);
        File targetGraphHtml = new File("html_files/active_graph.html");
        try (FileWriter graphWriter = new FileWriter(targetGraphHtml)) {
            for (String line : graphHtmlLines) {
                graphWriter.write(line + "\n");
            }
        } catch (IOException ignored) {}

        // 6. Default View: Serve a dynamic, zero-flicker HTML table
        writer.print("HTTP/1.1 200 OK\r\n");
        writer.print("Content-Type: text/html; charset=UTF-8\r\n");
        writer.print("Connection: close\r\n");
        writer.print("\r\n");

        writer.print("<!DOCTYPE html>\n<html>\n<head>\n");
        writer.print("<style>\n");
        writer.print("  body { font-family: 'Segoe UI', Arial, sans-serif; margin: 15px; background-color: #f8fafc; color: #1e293b; }\n");
        writer.print("  h2 { font-size: 16px; margin-bottom: 12px; color: #0f172a; border-bottom: 2px solid #e2e8f0; padding-bottom: 6px; }\n");
        writer.print("  table { width: 100%; border-collapse: collapse; margin-top: 5px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); border-radius: 6px; overflow: hidden; }\n");
        writer.print("  th, td { border: 1px solid #e2e8f0; padding: 10px; text-align: left; font-size: 13px; }\n");
        writer.print("  th { background-color: #10b981; color: white; font-weight: 600; }\n");
        writer.print("  tr { background-color: #ffffff; transition: background-color 0.15s; }\n");
        writer.print("  tr:nth-child(even) { background-color: #f8fafc; }\n");
        writer.print("  .na { color: #94a3b8; font-style: italic; }\n");
        writer.print("</style>\n");
        writer.print("</head>\n<body>\n");
        writer.print("<h2>Current Topic Values</h2>\n");
        writer.print("<table>\n");
        writer.print("  <thead>\n");
        writer.print("    <tr><th>Topic</th><th>Last Value</th></tr>\n");
        writer.print("  </thead>\n");
        writer.print("  <tbody id='table-body'>\n");
        
        // Render initial rows with matching IDs right from the server to prevent initial flashes
        for (Topic t : tm.getTopics()) {
            String valueStr = lastTopicValues.getOrDefault(t.name, "N/A");
            String formattedValue = formatNumericValue(valueStr);
            String valClass = "N/A".equals(formattedValue) ? "class='na'" : "";
            
            writer.print("    <tr id='row_" + t.name + "'>");
            writer.print("<td>" + escapeHtml(t.name) + "</td>");
            writer.print("<td id='val_" + t.name + "' " + valClass + ">" + escapeHtml(formattedValue) + "</td>");
            writer.print("</tr>\n");
        }
        
        writer.print("  </tbody>\n");
        writer.print("</table>\n");
        
        // Zero-flicker DOM-diffing polling script
        writer.print("<script>\n");
        writer.print("  function fetchTableValues() {\n");
        writer.print("      fetch(\"/publish?action=values\")\n");
        writer.print("          .then(response => response.json())\n");
        writer.print("          .then(values => {\n");
        writer.print("              const tbody = document.getElementById('table-body');\n");
        writer.print("              const keys = Object.keys(values).sort();\n");
        writer.print("              if (keys.length === 0) {\n");
        writer.print("                  tbody.innerHTML = '<tr><td colspan=\"2\" class=\"na\" style=\"text-align:center;\">No active topics</td></tr>';\n");
        writer.print("                  return;\n");
        writer.print("              }\n");
        
        // Filter out the initial "No active topics" text if elements are now present
        writer.print("              if (tbody.children.length === 1 && tbody.children[0].cells.length === 1) {\n");
        writer.print("                  tbody.innerHTML = '';\n");
        writer.print("              }\n");
        
        writer.print("              keys.forEach(topic => {\n");
        writer.print("                  const val = values[topic];\n");
        writer.print("                  const valClass = val === 'N/A' ? 'na' : '';\n");
        writer.print("                  const rowId = 'row_' + topic;\n");
        writer.print("                  let row = document.getElementById(rowId);\n");
        
        // If the row doesn't exist, create it once
        writer.print("                  if (!row) {\n");
        writer.print("                      row = document.createElement('tr');\n");
        writer.print("                      row.id = rowId;\n");
        writer.print("                      row.innerHTML = '<td>' + topic + '</td><td id=\"val_' + topic + '\" class=\"' + valClass + '\">' + val + '</td>';\n");
        writer.print("                      tbody.appendChild(row);\n");
        writer.print("                  } else {\n");
        
        // If the row exists, only update the text if the value actually changed
        writer.print("                      const valCell = document.getElementById('val_' + topic);\n");
        writer.print("                      if (valCell && valCell.innerText !== val) {\n");
        writer.print("                          valCell.innerText = val;\n");
        writer.print("                          valCell.className = valClass;\n");
        writer.print("                      }\n");
        writer.print("                  }\n");
        writer.print("              });\n");
        writer.print("          })\n");
        writer.print("          .catch(err => console.warn('Table tracking error: ', err));\n");
        writer.print("  }\n");
        writer.print("  setInterval(fetchTableValues, 500);\n");
        writer.print("</script>\n");
        
        writer.print("</body>\n</html>\n");
        writer.flush();
    }

    /**
     * Subscribes a temporary tracking agent (observer) to the topic if not already registered.
     * 
     * @param t the topic to register the tracker observer to
     */
    private void registerTrackerIfAbsent(Topic t) {
        boolean alreadySubscribed = false;
        for (Agent a : t.getSubs()) {
            if (a.getName().equals("TableTracker_" + t.name)) {
                alreadySubscribed = true;
                break;
            }
        }

        if (!alreadySubscribed) {
            t.subscribe(new Agent() {
                private final String trackerName = "TableTracker_" + t.name;

                @Override
                public String getName() {
                    return trackerName;
                }

                @Override
                public void reset() {
                    // No state to reset
                }

                @Override
                public void callback(String topic, Message msg) {
                    if (msg != null && msg.asText != null) {
                        lastTopicValues.put(topic, msg.asText);
                    }
                }

                @Override
                public void close() {
                    // No persistent streams to close
                }
            });
        }
    }

    /**
     * Validates and formats numerical strings to standard floating-point representation.
     */
    private String formatNumericValue(String val) {
        if (val == null || val.equals("N/A")) {
            return "N/A";
        }
        try {
            double d = Double.parseDouble(val);
            return String.valueOf(d);
        } catch (NumberFormatException e) {
            return val;
        }
    }

    /**
     * Extracts query parameters from the request URI.
     */
    private Map<String, String> parseQueryParams(String uri) {
        Map<String, String> params = new HashMap<>();
        int questionMarkIndex = uri.indexOf('?');
        if (questionMarkIndex != -1) {
            String queryString = uri.substring(questionMarkIndex + 1);
            String[] pairs = queryString.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    params.put(keyValue[0], keyValue[1]);
                } else if (keyValue.length == 1) {
                    params.put(keyValue[0], "");
                }
            }
        }
        return params;
    }

    /**
     * Escapes standard characters to prevent HTML injection.
     */
    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#x27;");
    }

    /**
     * Standard clean sequence, removing all custom tracker agents from topic registries.
     * 
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        TopicManagerSingleton.TopicManager tm = TopicManagerSingleton.get();
        for (Topic t : tm.getTopics()) {
            for (Agent a : t.getSubs()) {
                if (a.getName().startsWith("TableTracker_")) {
                    t.unsubscribe(a);
                }
            }
        }
    }
}