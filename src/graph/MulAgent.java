package graph;

/**
 * An event-driven agent that performs binary multiplication on two input topics and 
 * publishes the result to an output topic.
 * 
 * <p><strong>Design Patterns and Implementation Details:</strong></p>
 * <p>This class extends {@link graph.BinOpAgent}, inheriting its registration, callback, 
 * and resource cleanup pathways. It specializes the processing behavior by supplying 
 * a binary multiplication lambda strategy ({@code (x, y) -> x * y}).</p>
 * 
 * <p><strong>Execution Lifecycle:</strong></p>
 * <p>Because this agent inherits the initialization pathways of {@code BinOpAgent}, its internal 
 * caches start as uninitialized ({@code null}). It requires distinct updates on both input 
 * topics ({@code subs[0]} and {@code subs[1]}) before executing and publishing its first output. 
 * Subsequent message arrivals on either topic trigger calculations using the last known value 
 * of the opposite topic.</p>
 * 
 * <p><strong>Required Core Framework Components (Internal Dependencies):</strong></p>
 * <ul>
 *   <li>{@link graph.BinOpAgent} - The parent strategy-driven binary operator agent class.</li>
 *   <li>{@link graph.TopicManagerSingleton} - Directory service that handles the subscription 
 *       and publication routing under the hood.</li>
 * </ul>
 * 
 * <p><strong>Code Integration Example:</strong></p>
 * <pre>{@code
 * String[] subscriptions = { "factor_1", "factor_2" };
 * String[] publications = { "multiplication_results" };
 * 
 * // Create and register the multiplication agent
 * MulAgent mulAgent = new MulAgent(subscriptions, publications);
 * 
 * // Publish to inputs to trigger the calculation flow
 * TopicManagerSingleton.get().getTopic("factor_1").publish(new Message(6.0));  // Cached
 * TopicManagerSingleton.get().getTopic("factor_2").publish(new Message(7.0));  // Triggers output -> 42.0
 * 
 * // Clean up resources when done
 * mulAgent.close();
 * }</pre>
 * 
 * @see graph.BinOpAgent
 * @see graph.TopicManagerSingleton
 */
public class MulAgent extends BinOpAgent {

    /**
     * Constructs a MulAgent and registers it with the designated input and output topics
     * by delegating the registration and multiplication strategy to the BinOpAgent base class.
     * 
     * Throws an IllegalArgumentException during initialization if the structural requirements 
     * (at least 2 subscriptions and 1 publication) are not met.
     * 
     * @param subs the subscription topics array (must contain at least 2 valid topics)
     * @param pubs the publication topics array (must contain at least 1 valid topic)
     * @throws IllegalArgumentException if subscription or publication arrays are null, empty, 
     *                                  or if their required index elements are null.
     */
    public MulAgent(String[] subs, String[] pubs) {
        super(
        	"MulAgent", 
            validateAndGetInput1(subs), 
            subs[1], 
            validateAndGetOutput(pubs), 
            (x, y) -> x * y
        );
    }

    /**
     * Validates the subscription array and returns the first input topic.
     * 
     * @param subs the subscription input topics array to validate
     * @return the primary input topic name at index 0
     * @throws IllegalArgumentException if the array is null, contains fewer than 2 elements, 
     *                                  or if either of the first two elements is null.
     */
    private static String validateAndGetInput1(String[] subs) {
        // Enforce that subscription arrays must contain at least two items for binary multiplication
        if (subs == null || subs.length < 2) {
            throw new IllegalArgumentException("MulAgent requires at least two subscription input topics.");
        }
        // Prevent registering null topic names which would crash the TopicManager
        if (subs[0] == null || subs[1] == null) {
            throw new IllegalArgumentException("Topic names cannot be null.");
        }
        return subs[0];
    }

    /**
     * Validates the publication array and returns the first output topic.
     * 
     * @param pubs the publication output topics array to validate
     * @return the primary output topic name at index 0
     * @throws IllegalArgumentException if the array is null, empty, or if its first element is null
     */
    private static String validateAndGetOutput(String[] pubs) {
        // Enforce that publication arrays must contain at least one output topic
        if (pubs == null || pubs.length < 1) {
            throw new IllegalArgumentException("MulAgent requires at least one publication output topic.");
        }
        // Prevent registering null topic names which would crash the TopicManager
        if (pubs[0] == null) {
            throw new IllegalArgumentException("Topic names cannot be null.");
        }
        return pubs[0];
    }
}