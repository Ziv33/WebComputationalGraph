package graph;

//import java.util.ArrayList; // We want the subs and pubs lists to be thread safe, so we use import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents a named communication channel (subject) implementing the Observer design pattern.
 * 
 * <p>A topic manages lists of subscriber agents and authorized publisher agents, serving as the 
 * pathway through which immutable messages are dispatched and propagated within the system.</p>
 * 
<p><strong>Thread Safety and Concurrency Model</strong></p>
 * <p>To support asynchronous operations and prevent {@link java.util.ConcurrentModificationException} 
 * errors during runtime, both subscribers ({@code subs}) and publishers ({@code pubs}) lists are 
 * maintained using {@link java.util.concurrent.CopyOnWriteArrayList}. This allows safe, concurrent 
 * write operations (such as dynamically subscribing or unsubscribing an agent) while another thread 
 * is concurrently reading and iterating over the list to publish messages.</p>
 * 
* <p><strong>Encapsulation and Factory Control</strong></p>
 * <p>The constructor of this class has package-private visibility. This constraint guarantees that 
 * topics can only be created by classes residing in the same package (specifically {@link graph.TopicManagerSingleton.TopicManager}), 
 * enforcing a strict Flyweight factory model where topic creation is coordinated and redundant 
 * instances are avoided.</p>
 * 
 * <p><strong>Code Integration Example:</strong></p>
 * <pre>{@code
 * // Resolve the TopicManager directory
 * TopicManager manager = TopicManagerSingleton.get();
 * 
 * // Obtain a topic reference (instantiated via the factory if it does not yet exist)
 * Topic dataChannel = manager.getTopic("sensor_data");
 * 
 * // Register a subscriber agent
 * Agent receiver = new Agent() {
 *     @Override public String getName() { return "Receiver"; }
 *     @Override public void reset() {}
 *     @Override public void close() {}
 *     @Override
 *     public void callback(String topic, Message msg) {
 *         System.out.println("Received: " + msg.asText);
 *     }
 * };
 * dataChannel.subscribe(receiver);
 * 
 * // Publish a message to all registered subscribers
 * dataChannel.publish(new Message("System Active"));
 * }</pre>
 * 
 * @see graph.Agent
 * @see graph.Message
 * @see graph.TopicManagerSingleton
 * @see java.util.concurrent.CopyOnWriteArrayList
 */
public class Topic {

	/** The unique descriptive name of the topic. */
    public final String name;
    
    // Thread safe lists to prevent ConcurrentModificationException during pub/sub
    private final List<Agent> subs;
    private final List<Agent> pubs;

    /**
     * Package private constructor. Only classes within the same package (e.g., TopicManager) can create instances directly.
     * 
     * @param name the unique descriptive name of the topic
     */
    Topic(String name) {
        this.name = name;
        this.subs = new CopyOnWriteArrayList<>();
        this.pubs = new CopyOnWriteArrayList<>();
    }
    
    /**
     * Exposes the thread-safe subscriber list to the graph generation utilities.
     * 
     * @return the list of subscribed agents
     */
    public List<Agent> getSubs() {
        return subs;
    }

    /**
     * Exposes the thread-safe publisher list to the graph generation utilities.
     * 
     * @return the list of registered publisher agents
     */
    public List<Agent> getPubs() {
        return pubs;
    }
    
    /**
     * Registers an agent as a subscriber to receive message updates.
     * 
     * @param a the agent wishing to subscribe
     */
    public void subscribe(Agent a) {
        if (a != null && !subs.contains(a)) {
            subs.add(a);
        }
    }

    /**
     * Unregisters an agent from the subscriber list.
     * 
     * @param a the agent wishing to unsubscribe
     */
    public void unsubscribe(Agent a) {
        if (a != null) {
            subs.remove(a);
        }
    }

    /**
     * Publishes an immutable message, dispatching its contents to the callbacks of all 
     * registered subscriber agents.
     * 
     * @param m the immutable message to publish
     */
    public void publish(Message m) {
        if (m == null) return;
        
        // Iterate over all subscribers and invoke their callback
        for (Agent a : subs) {
            a.callback(this.name, m);
        }
    }

    /**
     * Registers an agent as an authorized publisher for this topic.
     * 
     * @param a the agent representing a publisher
     */
    public void addPublisher(Agent a) {
        if (a != null && !pubs.contains(a)) {
            pubs.add(a);
        }
    }

    /**
     * Unregisters an agent from the publisher list.
     * 
     * @param a the agent representing a publisher
     */
    public void removePublisher(Agent a) {
        if (a != null) {
            pubs.remove(a);
        }
    }
}