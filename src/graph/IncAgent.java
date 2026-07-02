package graph;

/**
 * An event-driven agent that increments values received on its input topic by 1 and 
 * publishes the result to an output topic.
 * 
 * <p><strong>Design Patterns and Implementation Details:</strong></p>
 * <p>This class extends {@link graph.BinOpAgent} by adapting a unary increment operation 
 * to the binary execution pattern of the superclass. It binds the single subscription topic 
 * to both of the superclass's input channels. When a message is received, both input cache variables 
 * are updated to the same numeric value, executing the increment lambda strategy ({@code x + 1}) 
 * and publishing the result to the target output topic.</p>
 * 
 * <p><strong>Instant-Fire Execution Behavior:</strong></p>
 * <p>Because both internal inputs are bound to the same incoming topic, a single message arrival 
 * immediately updates both caches simultaneously, satisfying the execution guard block. This means 
 * {@code IncAgent} produces output on its very first message receipt, without requiring a second 
 * subscription update.</p>
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
 * String[] subscriptions = { "source_numbers" };
 * String[] publications = { "incremented_numbers" };
 * 
 * // Create and register the increment agent
 * IncAgent incAgent = new IncAgent(subscriptions, publications);
 * 
 * // Publish a value to trigger execution
 * TopicManagerSingleton.get().getTopic("source_numbers").publish(new Message(10.0));
 * // Output topic "incremented_numbers" receives the value 11.0
 * 
 * // Clean up resources when done
 * incAgent.close();
 * }</pre>
 * 
 * @see graph.BinOpAgent
 * @see graph.TopicManagerSingleton
 */
public class IncAgent extends BinOpAgent {

    /**
     * Constructs an IncAgent and registers it with the designated input and output topics.
     * 
     * Throws an IllegalArgumentException during initialization if the structural requirements 
     * (at least 1 subscription and 1 publication) are not met to prevent silent configuration failures.
     * 
     * @param subs the subscription topics array (must contain at least 1 valid topic at index 0)
     * @param pubs the publication topics array (must contain at least 1 valid topic at index 0)
     * @throws IllegalArgumentException if subscription or publication arrays are null, empty, 
     *                                  or if their primary elements are null.
     */
    public IncAgent(String[] subs, String[] pubs) {
        // Pass the single input topic as both input1 and input2, ignoring the second variable in the lambda
        super(
        	"IncAgent", 
            validateAndGetInput(subs), 
            subs[0], 
            validateAndGetOutput(pubs), 
            (x, y) -> x + 1
        );
    }

    /**
     * Validates the subscription array and returns the single input topic.
     * 
     * @param subs the subscription input topics array to validate
     * @return the primary input topic name
     * @throws IllegalArgumentException if the array is null, empty, or if its first element is null
     */
    private static String validateAndGetInput(String[] subs) {
        // Enforce that subscription arrays must contain at least one item for increment operation
        if (subs == null || subs.length < 1) {
            throw new IllegalArgumentException("IncAgent requires at least one subscription input topic.");
        }
        // Prevent registering null topic names which would crash the TopicManager
        if (subs[0] == null) {
            throw new IllegalArgumentException("Topic names cannot be null.");
        }
        return subs[0];
    }

    /**
     * Validates the publication array and returns the first output topic.
     * 
     * @param pubs the publication output topics array to validate
     * @return the primary output topic name
     * @throws IllegalArgumentException if the array is null, empty, or if its first element is null
     */
    private static String validateAndGetOutput(String[] pubs) {
        // Enforce that publication arrays must contain at least one output topic
        if (pubs == null || pubs.length < 1) {
            throw new IllegalArgumentException("IncAgent requires at least one publication output topic.");
        }
        // Prevent registering null topic names which would crash the TopicManager
        if (pubs[0] == null) {
            throw new IllegalArgumentException("Topic names cannot be null.");
        }
        return pubs[0];
    }
}