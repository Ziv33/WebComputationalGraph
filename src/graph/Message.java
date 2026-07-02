package graph;

import java.util.Date;

/**
 * An immutable container that encapsulates message payloads and automatically handles 
 * common data type conversions.
 * 
 * <p><strong>Design and Thread Safety:</strong></p>
 * <p>This class is designed to be completely immutable. All state fields are declared as 
 * {@code public final}, allowing safe, direct access from concurrent thread pathways (such as 
 * asynchronous worker threads or multiple subscriber agents) without requiring synchronization or risking 
 * data corruption.</p>
 * 
 * <p><strong>Conversion and Exception Safeguards:</strong></p>
 * <p>Constructors reuse the byte-array representation to provide consistent, automated data type 
 * conversions. If a text string cannot be successfully parsed into a floating-point numeric value, the 
 * {@code asDouble} field is gracefully assigned {@link java.lang.Double#NaN} (Not-a-Number) instead of throwing 
 * a {@link java.lang.NumberFormatException}. This guarantees processing pipeline stability.</p>
 * 
 * <p><strong>Code Integration Example:</strong></p>
 * <pre>{@code
 * // Instantiate a message using a floating-point value
 * Message doubleMessage = new Message(42.5);
 * System.out.println("Text: " + doubleMessage.asText);     // Outputs "42.5"
 * System.out.println("Double: " + doubleMessage.asDouble); // Outputs 42.5
 * System.out.println("Timestamp: " + doubleMessage.date);   // Record creation timestamp
 * 
 * // Instantiate with raw text that cannot be parsed as a double
 * Message textMessage = new Message("Hello World");
 * System.out.println("Double representation: " + textMessage.asDouble); // Outputs NaN
 * }</pre>
 * 
 * @see graph.Agent
 * @see graph.TopicManagerSingleton
 */
public class Message {
	
	/** The raw binary payload byte array of this message. */
    public final byte[] data;
    
    /** The text representation of the payload. */
    public final String asText;
    
    /** The numeric representation of the payload. */
    public final double asDouble;
    
    /** The creation timestamp recording when the message was instantiated. */
    public final Date date;
    

    /**
     * Main constructor taking a byte array.
     * Ensures raw binary data (such as images or video frames) is safe.
     * Initializes all final fields, handles conversions, and records the creation time.
     * 
     * @param data the raw binary payload array (can be null)
     */
    public Message(byte[] data) {
        this.data = data;
        this.asText = (data != null) ? new String(data) : null;
        
        // Record creation timestamp
        this.date = new Date();
        
        // Attempts conversion to double. If it does not succeed, assigns Double.NaN.
        double parsedDouble;
        try {
            parsedDouble = Double.parseDouble(this.asText);
        } catch (NumberFormatException | NullPointerException e) {
            parsedDouble = Double.NaN;
        }
        this.asDouble = parsedDouble;
    }

    /**
     * Constructor taking a String, reusing the main constructor.
     * 
     * @param asText the text payload string (can be null)
     */
    public Message(String asText) {
        this(asText != null ? asText.getBytes() : null);
    }

    /**
     * Constructor taking a double, reusing the main constructor.
     * 
     * @param asDouble the numeric payload value
     */
    public Message(double asDouble) {
        this(String.valueOf(asDouble));
    }
}