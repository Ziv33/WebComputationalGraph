package graph;

/**
 * An event-driven agent that performs binary addition on two input topics and 
 * publishes the result to an output topic.
 * 
 * <p><strong>Design Patterns and Implementation Details:</strong></p>
 * <p>This class extends {@link graph.BinOpAgent}, inheriting its registration, callback, 
 * and resource cleanup pathways. It specializes the processing behavior by supplying 
 * a binary addition lambda strategy ({@code (x, y) -> x + y}).</p>
 * 
 * <p><strong>Default State Initialization:</strong></p>
 * <p>Unlike the base {@code BinOpAgent} (which defaults to uninitialized {@code null} caches), the 
 * {@code PlusAgent} constructor calls {@link #reset()} immediately after super-construction. This 
 * initializes both internal cache variables to {@code 0.0}.</p>
 * 
 * <p><strong>Execution Behavior:</strong></p>
 * <p>Due to the automatic {@code 0.0} initialization, a message update received on <em>either</em> 
 * subscription topic ({@code subs[0]} or {@code subs[1]}) immediately triggers an execution using the 
 * {@code 0.0} default value for the opposite topic. For example, publishing {@code 5.0} to the first input 
 * topic immediately publishes {@code 5.0} (calculated as {@code 5.0 + 0.0}) to the output topic.</p>
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
 * String[] subscriptions = { "add_input_1", "add_input_2" };
 * String[] publications = { "addition_results" };
 * 
 * // Create and register the addition agent
 * PlusAgent plusAgent = new PlusAgent(subscriptions, publications);
 * 
 * // Publish a value to one input topic (immediately fires output using 0.0 as the default opposite value)
 * TopicManagerSingleton.get().getTopic("add_input_1").publish(new Message(7.0));
 * // Output topic "addition_results" receives: 7.0 (calculated as 7.0 + 0.0)
 * 
 * // Clean up resources when done
 * plusAgent.close();
 * }</pre>
 * 
 * @see graph.BinOpAgent
 * @see graph.TopicManagerSingleton
 */
public class PlusAgent extends BinOpAgent {

    /**
     * Constructs a PlusAgent and registers it with the designated input and output topics.
     * 
     * Throws an IllegalArgumentException during initialization if the structural requirements 
     * (at least 2 subscriptions and 1 publication) are not met to prevent silent configuration failures.
     * 
     * @param subs the subscription topics array (must contain at least 2 valid topics)
     * @param pubs the publication topics array (must contain at least 1 valid topic)
     * @throws IllegalArgumentException if subscription or publication arrays are null, empty, 
     *                                  or if their required index elements are null.
     */
    public PlusAgent(String[] subs, String[] pubs) {
        super(
        	"PlusAgent", 
            validateAndGetInput1(subs), 
            subs[1], 
            validateAndGetOutput(pubs), 
            (x, y) -> x + y
        );
        // Initialize inputs to 0.0 to preserve the original default addition state
        this.reset();
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
        // Enforce that subscription arrays must contain at least two items for binary addition
        if (subs == null || subs.length < 2) {
            throw new IllegalArgumentException("PlusAgent requires at least two subscription input topics.");
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
            throw new IllegalArgumentException("PlusAgent requires at least one publication output topic.");
        }
        // Prevent registering null topic names which would crash the TopicManager
        if (pubs[0] == null) {
            throw new IllegalArgumentException("Topic names cannot be null.");
        }
        return pubs[0];
    }
}