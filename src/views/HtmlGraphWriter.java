package views;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import graph.Graph;
import graph.Node;

/**
 * A dynamic view-rendering utility that compiles a logical graph topology into an 
 * interactive HTML visualization.
 * 
 * <p>The renderer acts as the View compiler in the server's MVC architecture. It translates a 
 * logical {@link graph.Graph} model into raw node and edge JSON structures, searches standard filesystem 
 * locations to locate a static template file ({@code graph.html}), and injects the JSON arrays into 
 * pre-defined template placeholders ({@code ${NODES_DATA}} and {@code ${EDGES_DATA}}).</p>
 * 
 * <p><strong>Real-Time View Features</strong></p>
 * <ul>
 *   <li><strong>Identity Suffix Stripping:</strong> To support visual simplicity and prevent identical 
 *       agent classes from overlapping on the canvas, agents are registered with unique memory identity suffixes 
 *       (e.g., {@code "APlusAgent_182942"}). The renderer automatically strips out this memory hash code, rendering 
 *       clean, human-readable labels (e.g., {@code "PlusAgent"}) on the canvas.</li>
 *   <li><strong>Live State Value Annotation:</strong> During compilation, the renderer queries the shared value 
 *       cache from {@link servlets.TopicDisplayer#lastTopicValues} in real-time. It appends the current values 
 *       directly to the topic labels (e.g., {@code "A (10.0)"}), generating a live-annotated structural network.</li>
 *   <li><strong>Fallback Rendering:</strong> If the static template resource cannot be resolved locally, the 
 *       renderer compiles a safe fallback view to protect the pipeline and prevent rendering errors.</li>
 * </ul>
 * 
 * <p><strong>Required Core Framework Components (Internal Dependencies):</strong></p>
 * <ul>
 *   <li>{@link graph.Graph} - Logical graph mapping of registered topics and agents.</li>
 *   <li>{@link graph.Node} - Structural nodes representing individual communication vertices.</li>
 *   <li>{@link servlets.TopicDisplayer} - Peer servlet whose thread-safe value map is read to fetch live states.</li>
 * </ul>
 * 
 * @see graph.Graph
 * @see graph.Node
 * @see servlets.TopicDisplayer
 */
public class HtmlGraphWriter {

    /**
     * Transforms a logical Graph instance into visual HTML layout content.
     * 
     * <p>Compiles JSON representations of vertices and directed edges, loads the target 
     * template file, and replaces the placeholders with the compiled data. If the template is 
     * missing, a safe fallback message is returned.</p>
     * 
     * @param graph the logical graph model representing current topic and agent registrations
     * @return a list of lines containing the updated HTML/Javascript view markup
     */
    public static List<String> getGraphHTML(Graph graph) {
        String nodesJson = buildNodesJson(graph);
        String edgesJson = buildEdgesJson(graph);

        List<String> templateLines = loadTemplateFile();

        if (templateLines != null) {
            List<String> processedLines = new ArrayList<>();
            for (String line : templateLines) {
                String updatedLine = line.replace("${NODES_DATA}", nodesJson)
                                         .replace("${EDGES_DATA}", edgesJson);
                processedLines.add(updatedLine);
            }
            return processedLines;
        }

        return generateFallbackHTML(nodesJson, edgesJson);
    }

    /**
     * Generates a structural JSON array representing all Node entities in the graph.
     * Appends the current live value of the topics to their labels on the fly
     * and strips the unique identity suffixes from agent nodes to keep labels visually clean.
     * 
     * @param graph the logical graph model containing the node list
     * @return a JSON string representing the node collection
     */
    private static String buildNodesJson(Graph graph) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < graph.size(); i++) {
            Node node = graph.get(i);
            String rawName = node.getName();
            String type = rawName.startsWith("T") ? "topic" : "agent";
            String displayName = rawName.substring(1);

            // Strip the unique memory identity suffix to keep agent labels visually clean
            if (type.equals("agent")) {
                int identitySuffixIdx = displayName.indexOf('_');
                if (identitySuffixIdx != -1) {
                    displayName = displayName.substring(0, identitySuffixIdx);
                }
            }

            // Fetch live values for topics from the global servlet map
            if (type.equals("topic")) {
                String val = servlets.TopicDisplayer.lastTopicValues.getOrDefault(displayName, "N/A");
                try {
                    double d = Double.parseDouble(val);
                    val = String.valueOf(d);
                } catch (NumberFormatException ignored) {}
                displayName = displayName + " (" + val + ")";
            }

            sb.append(String.format("{id:\"%s\", label:\"%s\", type:\"%s\"}", rawName, displayName, type));
            if (i < graph.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Generates a structural JSON array representing all directional connections (edges) in the graph.
     * 
     * @param graph the logical graph model containing directed relationships
     * @return a JSON string representing the edge collection
     */
    private static String buildEdgesJson(Graph graph) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Node node : graph) {
            String fromName = node.getName();
            if (node.getEdges() != null) {
                for (Node target : node.getEdges()) {
                    if (!first) {
                        sb.append(",");
                    }
                    sb.append(String.format("{from:\"%s\", to:\"%s\"}", fromName, target.getName()));
                    first = false;
                }
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Searches standard filesystem locations to locate and load the static "graph.html" template.
     * 
     * @return a list of lines containing the raw template file, or null if it cannot be found
     */
    private static List<String> loadTemplateFile() {
        File[] candidatePaths = {
            new File("html_files/graph.html"),
            new File("html/graph.html"),
            new File("graph.html")
        };

        for (File path : candidatePaths) {
            if (path.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
                    List<String> lines = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                    return lines;
                } catch (IOException ignored) {}
            }
        }
        return null;
    }

    /**
     * Generates an interactive canvas-based layout representation if the template resource is missing.
     * 
     * @param nodesJson the compiled node data string
     * @param edgesJson the compiled edge data string
     * @return a list of lines containing a standard fallback message
     */
    private static List<String> generateFallbackHTML(String nodesJson, String edgesJson) {
        List<String> html = new ArrayList<>();
        html.add("<!DOCTYPE html><html><body style='text-align:center;'><h3>Graph loaded. Place graph.html template to see visual rendering.</h3></body></html>");
        return html;
    }
}