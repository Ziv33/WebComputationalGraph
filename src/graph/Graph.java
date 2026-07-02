package graph;

import java.util.ArrayList;
import java.util.HashMap;

import graph.TopicManagerSingleton.TopicManager;

/**
 * Represents a directed graph of communication topics and processing agents, 
 * extending {@link java.util.ArrayList} of {@link graph.Node}.
 * 
 * <p>The graph is constructed dynamically by mapping the registry state of the global 
 * {@link graph.TopicManagerSingleton} into nodes and directed edges. This topology is then 
 * analyzed to ensure that no circular message routes or processing loops exist.</p>
 * 
 * <p><strong>Topology Mapping Rules:</strong></p>
 * <ul>
 *   <li>Each {@link graph.Topic} is represented as a Node prefixed with "T" (e.g., "TA").</li>
 *   <li>Each {@link graph.Agent} is represented as a Node prefixed with "A" (e.g., "APlusAgent").</li>
 *   <li>Directed edges are drawn from a Topic to its subscriber Agents (Topic -> Agent).</li>
 *   <li>Directed edges are drawn from publishing Agents to their target Topics (Agent -> Topic).</li>
 * </ul>
 * 
 * <p><strong>Instance Distinction:</strong></p>
 * <p>To prevent multiple distinct instances of the same Agent class from merging into a single node 
 * on the logical canvas, each agent's node name is uniquely distinguished by suffixing it with its 
 * system identity hash code (e.g., {@code "A[AgentName]_[IdentityHashCode]"}).</p>
 * 
 * <p><strong>Code Integration Example:</strong></p>
 * <pre>{@code
 * // Resolve the dynamic messaging environment
 * TopicManager manager = TopicManagerSingleton.get();
 * 
 * // Build the topological graph from active topic registrations
 * Graph networkGraph = new Graph();
 * networkGraph.createFromTopics();
 * 
 * // Validate structural integrity before committing/launching background threads
 * if (networkGraph.hasCycles()) {
 *     throw new IllegalStateException("Deployment aborted: Cyclical processing dependencies detected.");
 * }
 * }</pre>
 * 
 * @see graph.Node
 * @see graph.TopicManagerSingleton
 */
public class Graph extends ArrayList<Node> {

    /**
     * Determines whether the graph contains any cycles within its connected components.
     * Delegates cycle detection to each individual Node's implementation.
     * 
     * @return true if a cycle is detected in any reachable component of the graph; false otherwise
     */
    public boolean hasCycles() {
        for (Node node : this) {
            if (node.hasCycles()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rebuilds the graph layout based on topics and agents currently in TopicManager.
     * Safely filters out UI tracking agents to prevent them from rendering on the canvas,
     * and isolates distinct agent instances using their unique object identity handles.
     */
    public void createFromTopics() {
        this.clear();
        HashMap<String, Node> nodeMap = new HashMap<>();
        TopicManager tm = TopicManagerSingleton.get();

        for (Topic topic : tm.getTopics()) {
            String topicNodeName = "T" + topic.name;
            Node topicNode = nodeMap.get(topicNodeName);
            if (topicNode == null) {
                topicNode = new Node(topicNodeName);
                nodeMap.put(topicNodeName, topicNode);
            }

            // Subscriber relations (Topic -> Agent)
            if (topic.getSubs() != null) {
                for (Agent agent : topic.getSubs()) {
                    // Filter out UI tracking agents so they are never drawn on the canvas
                    if (agent.getName().startsWith("TableTracker")) {
                        continue;
                    }
                    
                    // Generate a unique identifier for each agent instance to prevent identical classes from merging on the canvas
                    String agentNodeName = "A" + agent.getName() + "_" + System.identityHashCode(agent);
                    Node agentNode = nodeMap.get(agentNodeName);
                    if (agentNode == null) {
                        agentNode = new Node(agentNodeName);
                        nodeMap.put(agentNodeName, agentNode);
                    }
                    topicNode.addEdge(agentNode);
                }
            }

            // Publisher relations (Agent -> Topic)
            if (topic.getPubs() != null) {
                for (Agent agent : topic.getPubs()) {
                    // Filter out UI tracking agents in case they are listed under publishers
                    if (agent.getName().startsWith("TableTracker")) {
                        continue;
                    }

                    // Generate a unique identifier for each agent instance to prevent identical classes from merging on the canvas
                    String agentNodeName = "A" + agent.getName() + "_" + System.identityHashCode(agent);
                    Node agentNode = nodeMap.get(agentNodeName);
                    if (agentNode == null) {
                        agentNode = new Node(agentNodeName);
                        nodeMap.put(agentNodeName, agentNode);
                    }
                    agentNode.addEdge(topicNode);
                }
            }
        }

        this.addAll(nodeMap.values());
    }
}