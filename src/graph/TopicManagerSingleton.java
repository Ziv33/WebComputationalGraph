package graph;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Access coordinator that exposes a thread-safe, globally accessible single instance of 
 * the system's {@link graph.TopicManagerSingleton.TopicManager} directory.
 * 
 * <p><strong>The Initialization-on-Demand Holder Idiom (Lazy &amp; Thread-Safe)</strong></p>
 * <p>This class implements the Singleton design pattern using the Initialization-on-Demand Holder Idiom 
 * (also known as the Lazy Holder pattern). By nesting the final static instance declaration inside the static 
 * nested class {@code TopicManager}, JVM specifications guarantee that the instance is not constructed 
 * during initial application startup. Instead, it is instantiated <em>only</em> when {@link #get()} is 
 * first invoked, triggering the class loader to load {@code TopicManager}.</p>
 * 
 * <p>This pattern provides three major architectural benefits:</p>
 * <ul>
 *   <li><strong>Lazy Initialization:</strong> Resources are not allocated until the directory is actively needed.</li>
 *   <li><strong>Zero Synchronization Overhead:</strong> Avoids the runtime performance penalties of synchronization blocks, 
 *       volatile variables, or double-checked locking mechanisms.</li>
 *   <li><strong>JVM-Guaranteed Thread Safety:</strong> The class loading and static initialization process is executed 
 *       atomically by the JVM, preventing race conditions during concurrent initialization.</li>
 * </ul>
 * 
 * <p><strong>Code Integration Example:</strong></p>
 * <pre>{@code
 * // Retrieve the single, global TopicManager instance
 * TopicManager manager = TopicManagerSingleton.get();
 * 
 * // Obtain a topic reference (atomically retrieved or instantiated)
 * Topic inputChannel = manager.getTopic("temperature_readings");
 * }</pre>
 * 
 * @see graph.TopicManagerSingleton.TopicManager
 * @see graph.Topic
 */
public class TopicManagerSingleton {

    /**
     * Static method to get the single instance of the TopicManager.
     * 
     * @return the single, globally managed TopicManager instance
     */
    public static TopicManager get() {
        return TopicManager.instance;
    }

    /**
     * The registry and factory directory that manages, queries, and clears 
     * all active {@link graph.Topic} channels in the system.
     * 
     * <p><strong>Flyweight Factory Design Pattern</strong></p>
     * <p>The {@code TopicManager} acts as a Flyweight design pattern factory. To ensure that 
     * only one unique {@link graph.Topic} instance exists for any given channel name, topics 
     * are stored and managed inside a {@link java.util.concurrent.ConcurrentHashMap}. 
     * Resolving topics via {@link #getTopic(String)} uses {@code computeIfAbsent} to atomically 
     * retrieve an existing instance or construct a new one, ensuring thread safety and 
     * preventing duplicate topic instances.</p>
     */
    public static class TopicManager {
        // Single static final instance created only when this inner class is loaded
        private static final TopicManager instance = new TopicManager();
        
        // Thread safe (atomic) map to prevent race conditions during topic creation/retrieval 
        private final ConcurrentHashMap<String, Topic> topicsMap;

        /**
         * Private constructor to prevent instantiation from outside.
         */
        private TopicManager() {
            this.topicsMap = new ConcurrentHashMap<>();
        }

        /**
         * Retrieves an existing Topic or creates a new one if it doesn't exist (Flyweight).
         * computeIfAbsent guarantees that the operation is atomic and thread safe.
         * 
         * @param name the unique descriptive name of the topic to retrieve or construct
         * @return the managed topic instance
         * @throws IllegalArgumentException if the provided topic name is null or empty
         */
        public Topic getTopic(String name) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("Topic name cannot be null or empty");
            }
            // Attempts to fetch, and safely (the entire method invocation is performed atomically) creates via Topic's constructor if absent
            return topicsMap.computeIfAbsent(name, Topic::new);
        }

        /**
         * Returns a Collection of all currently managed Topics.
         * 
         * @return a view collection containing all registered topic instances
         */
        public Collection<Topic> getTopics() {
            return topicsMap.values();
        }

        /**
         * Clears all mapped topics, ensuring subscribers and publishers 
         * are cleanly detached to avoid memory leaks (Cascading Clean).
         * 
         * <p>To prevent strong reference chains from keeping decommissioned topics or agents in memory, 
         * this method explicitly clears subscriber and publisher registration lists from each topic 
         * prior to clearing the internal directory map.</p>
         */
        public void clear() {
            // 1. Disconnect all agents from topics to break strong reference chains
            for (Topic topic : topicsMap.values()) {
                if (topic.getSubs() != null) {
                    topic.getSubs().clear();
                }
                if (topic.getPubs() != null) {
                    topic.getPubs().clear();
                }
            }
            // 2. Clear the map itself
            topicsMap.clear();
        }
    }
}