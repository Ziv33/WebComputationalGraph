package graph;

/**
 * Defines the operational contract for reactive processing nodes (agents) within 
 * the message-routing network.
 * 
 * <p>An agent acts as a message consumer (Observer) subscribing to topics, executing custom processing 
 * strategies upon receiving updates, and optionally publishing results to downstream topics.</p>
 * 
 * <p><strong>Threading and Execution Contract:</strong></p>
 * <p>To prevent processing delays (e.g., intensive mathematical computations) from blocking the 
 * publishing threads, implementations can be decorated using {@code graph.ParallelAgent} (Active Object 
 * design pattern). This decouples the thread initiating the message publication from the thread 
 * executing the agent's actual {@link #callback(String, Message)} logic.</p>
 * 
 * <p><strong>Lifecycle States:</strong></p>
 * <ul>
 *   <li><strong>Identification ({@link #getName()}):</strong> Returns a descriptive, unique string 
 *       identifying the agent instance within the topology.</li>
 *   <li><strong>Message Arrival ({@link #callback(String, Message)}):</strong> Re-actively handles messages 
 *       dispatched by subscribed topics.</li>
 *   <li><strong>State Reinitialization ({@link #reset()}):</strong> Restores the agent's internal caches 
 *       and parameters back to their default structural starting values.</li>
 *   <li><strong>Graceful Disposal ({@link #close()}):</strong> Disconnects topic subscriptions and 
 *       unregisters from publishers to release execution and directory system resources.</li>
 * </ul>
 * 
 * <p><strong>Code Integration Example:</strong></p>
 * <pre>{@code
 * Agent myAgent = new Agent() {
 *     private double lastValue = 0.0;
 *     
 *     @Override public String getName() { return "CustomAgent"; }
 *     @Override public void reset() { lastValue = 0.0; }
 *     @Override public void close() { // Unsubscribe if necessary }
 *     
 *     @Override
 *     public void callback(String topic, Message msg) {
 *         if (msg != null && !Double.isNaN(msg.asDouble)) {
 *             lastValue = msg.asDouble;
 *             System.out.println("Processed: " + lastValue);
 *         }
 *     }
 * };
 * }</pre>
 * 
 * @see graph.Message
 * @see graph.TopicManagerSingleton
 */
public interface Agent {

    /**
     * Retrieves the unique, descriptive name of this agent instance.
     * 
     * @return the agent's name string
     */
    String getName();

    /**
     * Restores the agent's internal cache values and state variables back to their 
     * default structural starting values.
     */
    void reset();

    /**
     * Reactively processes incoming messages dispatched from subscription topics.
     * 
     * <p>Implementations must handle incoming data defensively, ignoring null values 
     * or invalid numeric payloads ({@code Double.NaN}) to ensure processing thread stability.</p>
     * 
     * @param topic the name of the topic that dispatched the update
     * @param msg   the immutable message container holding the new payload value
     */
    void callback(String topic, Message msg);

    /**
     * Cleans up all topic subscriptions and unregisters publisher entries from the global 
     * directory before disposal, preventing dangling references and memory leaks.
     */
    void close();
}