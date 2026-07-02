package graph;

import java.util.function.BinaryOperator;
import graph.TopicManagerSingleton.TopicManager;

/**
 * An event-driven agent that applies a binary mathematical operator to inputs from
 * two subscription topics and publishes the computed results to an output topic.
 * 
 * <p>The agent implements the Strategy design pattern by accepting a functional
 * {@link java.util.function.BinaryOperator} at runtime. This design allows customization 
 * of the mathematical or logical operation without changing the message routing logic.</p>
 * 
 * <p><strong>Required Core Framework Components (Internal Dependencies):</strong></p>
 * <p>To successfully integrate this component into a processing pipeline, the following framework 
 * classes must be present and correctly compiled within the application classpath:</p>
 * <ul>
 *   <li>{@link graph.Agent} - The core interface defining the callback, reset, and close lifecycles. 
 *       {@code BinOpAgent} implements this interface.</li>
 *   <li>{@link graph.Message} - The immutable data container used to wrap the numerical values 
 *       dispatched through the network.</li>
 *   <li>{@link graph.TopicManagerSingleton} - The global directory manager used to resolve the subscription 
 *       topics and establish publisher status.</li>
 * </ul>
 * 
 * <p><strong>Operational Details and Initialization Rules:</strong></p>
 * <ul>
 *   <li><strong>Initial Cache State:</strong> Upon initial construction, the local caches {@code val1} 
 *       and {@code val2} are uninitialized ({@code null}). This requires that both subscription topics 
 *       receive at least one message before the first calculation executes. This differs from specialized 
 *       agents (like {@code PlusAgent}) which may default their values to {@code 0.0} immediately on construction.</li>
 *   <li><strong>Reset Behavior:</strong> Calling {@link #reset()} initializes both input caches to {@code 0.0}. 
 *       Once reset, any subsequent update to a single topic will immediately trigger an execution using the 
 *       {@code 0.0} default value for the other topic.</li>
 *   <li><strong>State Retention:</strong> After the initial calculation, subsequent updates to either 
 *       topic trigger calculations using the last known value of the opposite topic.</li>
 *   <li><strong>Lifecycle and Cleanup:</strong> Invoking {@link #close()} unsubscribes 
 *       the agent from input channels and removes it from the publisher directory to 
 *       prevent resource retention and memory leaks.</li>
 * </ul>
 * 
 * <p><strong>Code Integration Example:</strong></p>
 * <pre>{@code
 * // Resolve the system's TopicManager
 * TopicManager manager = TopicManagerSingleton.get();
 * 
 * // Instantiate the agent (computes A * B, publishing results to R1)
 * BinOpAgent multiplier = new BinOpAgent(
 *     "MultiplyAgent", 
 *     "A", 
 *     "B", 
 *     "R1", 
 *     (val1, val2) -> val1 * val2
 * );
 * 
 * // Publish inputs to trigger the calculation flow
 * manager.getTopic("A").publish(new Message(5.0)); // Cached (no output generated yet)
 * manager.getTopic("B").publish(new Message(4.0)); // Triggers calculation -> R1 receives 20.0
 * 
 * // Terminate and cleanup registrations
 * multiplier.close();
 * }</pre>
 * 
 * @see graph.Agent
 * @see graph.Message
 * @see graph.TopicManagerSingleton
 * @see java.util.function.BinaryOperator
 */
public class BinOpAgent implements Agent {
    private final String name;
    private final String input1;
    private final String input2;
    private final String output;
    private final BinaryOperator<Double> op;

    // Holds the latest numerical value received from the first input topic
    private Double val1 = null;
    // Holds the latest numerical value received from the second input topic
    private Double val2 = null;

    /**
     * Constructs a BinOpAgent and registers it with the designated input and output topics.
     * 
     * @param name   the unique descriptive name of this agent
     * @param input1 the first input topic name to subscribe to
     * @param input2 the second input topic name to subscribe to
     * @param output the output topic name to publish results to
     * @param op     the binary mathematical operator strategy to apply to inputs
     * @throws IllegalArgumentException if any of the provided parameters are null
     */
    public BinOpAgent(String name, String input1, String input2, String output, BinaryOperator<Double> op) {
        if (name == null || input1 == null || input2 == null || output == null || op == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        this.name = name;
        this.input1 = input1;
        this.input2 = input2;
        this.output = output;
        this.op = op;

        TopicManager tm = TopicManagerSingleton.get();
        tm.getTopic(input1).subscribe(this);
        tm.getTopic(input2).subscribe(this);
        tm.getTopic(output).addPublisher(this);
    }

    /**
     * Retrieves the unique descriptive name of this agent instance.
     * 
     * @return the agent's name string
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Resets the input values val1 and val2 back to default values (0.0).
     */
    @Override
    public void reset() {
        this.val1 = 0.0;
        this.val2 = 0.0;
    }

    /**
     * Receives messages from input topics, stores them in val1 and val2, 
     * and publishes the result when both values are available.
     * 
     * <p><strong>Defensive Design:</strong> Null messages and corrupted numeric values 
     * (Double.NaN) are ignored to protect the stability of the active worker threads.</p>
     * 
     * @param topic the name of the topic that dispatched the update
     * @param msg   the immutable message container holding the new value
     */
    @Override
    public void callback(String topic, Message msg) {
        if (msg == null) {
            return;
        }

        double val = msg.asDouble;
        if (Double.isNaN(val)) {
            return;
        }

        // Store the incoming value in the correct input variable based on the topic
        if (topic.equals(input1)) {
            val1 = val;
        }
        if (topic.equals(input2)) {
            val2 = val;
        }

        // Run the calculation and publish the result only when both inputs have received a value
        if (val1 != null && val2 != null) {
            double result = op.apply(val1, val2);
            TopicManagerSingleton.get().getTopic(output).publish(new Message(result));
        }
    }

    /**
     * Cleans up topic registrations by unsubscribing from inputs and removing publisher status.
     * Prevents dangling references and memory leaks.
     */
    @Override
    public void close() {
        TopicManager tm = TopicManagerSingleton.get();
        tm.getTopic(input1).unsubscribe(this);
        tm.getTopic(input2).unsubscribe(this);
        tm.getTopic(output).removePublisher(this);
    }
}