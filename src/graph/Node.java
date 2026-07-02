package graph;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a vertex within a directed network graph.
 * 
 * <p>Each node corresponds to either an active message topic or a processing agent, containing 
 * outgoing references to other nodes to represent directed dependencies (edges). Additionally, 
 * nodes can hold a local message payload reference for structural tracing.</p>
 * 
 * <p><strong>Cycle Detection Mechanism:</strong></p>
 * <p>Cycle validation is implemented using a Depth-First Search (DFS) traversal algorithm. 
 * The algorithm tracks the path state using two collections:</p>
 * <ul>
 *   <li><strong>Visited List (Global):</strong> Prevents redundant traversal of nodes 
 *       previously fully explored and confirmed to be cycle-free, optimizing performance.</li>
 *   <li><strong>Recursion Stack List (Path-Specific):</strong> Tracks the sequence of nodes in the 
 *       current active DFS path. Re-encountering any node currently in this stack indicates 
 *       a back-edge, revealing a cyclic dependency.</li>
 * </ul>
 * 
 * <p><strong>Code Integration Example (Manual Graph Assembly):</strong></p>
 * <pre>{@code
 * // Instantiate vertex components representing nodes
 * Node nodeA = new Node("TA");
 * Node nodeB = new Node("APlusAgent");
 * Node nodeC = new Node("TC");
 * 
 * // Establish directed path: TA -> APlusAgent -> TC
 * nodeA.addEdge(nodeB);
 * nodeB.addEdge(nodeC);
 * 
 * // Intentionally construct a cyclic dependency back-edge: TC -> TA
 * nodeC.addEdge(nodeA);
 * 
 * // Evaluate cyclic status
 * if (nodeA.hasCycles()) {
 *     System.err.println("Structural circular dependency discovered!");
 * }
 * }</pre>
 */
public class Node {
    private String name;
    private List<Node> edges;
    private Message msg;

    /**
     * Constructs a Node with the given name.
     * 
     * @param name the unique descriptive name of the node
     */
    public Node(String name) {
        this.name = name;
        this.edges = new ArrayList<>();
    }

    /**
     * Retrieves the name of the node.
     * 
     * @return the node's name string
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the node.
     * 
     * @param name the new name string to assign
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Retrieves the list of outgoing directed edges from this node.
     * 
     * @return a list containing the adjacent target nodes
     */
    public List<Node> getEdges() {
        return edges;
    }

    /**
     * Sets the list of outgoing directed edges from this node.
     * 
     * @param edges the list of adjacent nodes representing outgoing edges
     */
    public void setEdges(List<Node> edges) {
        this.edges = edges;
    }

    /**
     * Retrieves the message payload associated with this node.
     * 
     * @return the associated message container
     */
    public Message getMsg() {
        return msg;
    }

    /**
     * Associates a message payload with this node.
     * 
     * @param msg the message container to link to this node
     */
    public void setMsg(Message msg) {
        this.msg = msg;
    }

    /**
     * Adds an outgoing directed edge to another node, ensuring duplicates are avoided.
     * 
     * @param node the destination node of the directed edge
     */
    public void addEdge(Node node) {
        if (node != null && !edges.contains(node)) {
            edges.add(node);
        }
    }

    /**
     * Determines whether there is a directed cycle reachable from this node.
     * 
     * @return true if a cycle is reachable from this node; false otherwise
     */
    public boolean hasCycles() {
        List<Node> visited = new ArrayList<>();
        List<Node> recStack = new ArrayList<>();
        return hasCyclesHelper(this, visited, recStack);
    }

    /**
     * Performs Depth-First Search (DFS) cycle detection.
     * Utilizes a recursion stack to identify cycles and a visited tracking list 
     * to prevent redundant exploration of safe paths.
     * 
     * @param current  the node being processed in the current traversal step
     * @param visited  tracks all nodes globally confirmed to be cycle-free
     * @param recStack tracks the nodes in the active recursion path
     * @return true if a cycle is detected; false otherwise
     */
    private boolean hasCyclesHelper(Node current, List<Node> visited, List<Node> recStack) {
        if (recStack.contains(current)) {
            return true;
        }
        if (visited.contains(current)) {
            return false;
        }

        visited.add(current);
        recStack.add(current);

        if (current.edges != null) {
            for (Node neighbor : current.edges) {
                if (hasCyclesHelper(neighbor, visited, recStack)) {
                    return true;
                }
            }
        }

        recStack.remove(current);
        return false;
    }
}