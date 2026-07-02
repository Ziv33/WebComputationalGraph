package graph;

import java.util.function.BinaryOperator;
import graph.Agent;
import graph.Message;
import graph.TopicManagerSingleton;

/**
 * An event-driven agent that performs non-mathematical string concatenation 
 * on both text and numeric payloads received from two input topics.
 * 
 * <p>It extends {@link graph.BinOpAgent} to inherit structural registration paths, 
 * but overrides execution methods to operate on string values (supporting mixed text 
 * and mathematical outputs).</p>
 * 
 * <p><strong>Code Integration Example:</strong></p>
 * <pre>{@code
 * String[] subscriptions = { "text_prefix", "numeric_result" };
 * String[] publications = { "final_message" };
 * 
 * // Create and register the text-merging agent
 * TextMergeAgent merger = new TextMergeAgent(subscriptions, publications);
 * 
 * // Publish a text string to the first topic
 * TopicManagerSingleton.get().getTopic("text_prefix").publish(new Message("The final calculation is:"));
 * 
 * // Publish a numeric calculation output to the second topic
 * TopicManagerSingleton.get().getTopic("numeric_result").publish(new Message(33.0));
 * 
 * // Output topic "final_message" instantly receives the concatenated string: 
 * // "The final calculation is: 33.0"
 * 
 * // Clean up resources when done
 * merger.close();
 * }</pre>
 * 
 * @see graph.BinOpAgent
 * @see graph.TopicManagerSingleton
 */
public class TextMergeAgent extends BinOpAgent {

    private final String input1;
    private final String input2;
    private final String output;
    private String val1 = null;
    private String val2 = null;

    /**
     * Constructs a TextMergeAgent and registers it with the designated input and output topics.
     * 
     * @param subs the subscription topics array (must contain at least 2 valid topics)
     * @param pubs the publication topics array (must contain at least 1 valid topic)
     * @throws IllegalArgumentException if subscription or publication arrays are null or empty
     */
    public TextMergeAgent(String[] subs, String[] pubs) {
        // Delegates structural topic registration to the parent constructor, 
        // while overriding callback execution to support text-based operations.
        super(
            "TextMergeAgent", 
            validateAndGetInput1(subs), 
            subs[1], 
            validateAndGetOutput(pubs), 
            (x, y) -> 0.0
        );
        this.input1 = subs[0];
        this.input2 = subs[1];
        this.output = pubs[0];
    }

    /**
     * Resets the internal text caches back to null.
     */
    @Override
    public void reset() {
        this.val1 = null;
        this.val2 = null;
    }

    /**
     * Receives message updates, caches them as strings, and publishes the concatenated result 
     * when both inputs are available.
     * 
     * @param topic the name of the topic that dispatched the update
     * @param msg   the immutable message container holding the payload
     */
    @Override
    public void callback(String topic, Message msg) {
        if (msg == null || msg.asText == null) {
            return;
        }

        // Cache the incoming payload as a text string (supports both text and numbers)
        if (topic.equals(input1)) {
            val1 = msg.asText;
        }
        if (topic.equals(input2)) {
            val2 = msg.asText;
        }

        // Publish the merged string when both are populated
        if (val1 != null && val2 != null) {
            String mergedText = val1 + " " + val2;
            TopicManagerSingleton.get().getTopic(output).publish(new Message(mergedText));
        }
    }

    /**
     * Validates the subscription array and returns the first input topic.
     * 
     * @param subs the subscription input topics array to validate
     * @return the primary input topic name at index 0
     * @throws IllegalArgumentException if the array is null or contains fewer than 2 elements
     */
    private static String validateAndGetInput1(String[] subs) {
        if (subs == null || subs.length < 2) {
            throw new IllegalArgumentException("TextMergeAgent requires at least two subscription input topics.");
        }
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
     * @throws IllegalArgumentException if the array is null or empty
     */
    private static String validateAndGetOutput(String[] pubs) {
        if (pubs == null || pubs.length < 1) {
            throw new IllegalArgumentException("TextMergeAgent requires at least one publication output topic.");
        }
        if (pubs[0] == null) {
            throw new IllegalArgumentException("Topic names cannot be null.");
        }
        return pubs[0];
    }
}