package graph;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * A thread-safe wrapper that introduces asynchronous execution to any {@link graph.Agent}.
 * 
 * <p><strong>Design Patterns Implemented</strong></p>
 * <ul>
 *   <li><strong>Decorator Pattern (Structural):</strong> Dynamically adds concurrency capabilities 
 *       to any {@link graph.Agent} implementation without altering its original source code. 
 *       It implements the same {@link graph.Agent} interface and delegates lifecycle and state methods 
 *       directly to the wrapped instance.</li>
 *   <li><strong>Active Object Pattern (Behavioral/Concurrency):</strong> Decouples message dispatching 
 *       (the thread calling {@link #callback(String, Message)}) from task execution (the 
 *       background consumer thread calling the wrapped agent's native callback logic) using 
 *       an internal {@link java.util.concurrent.BlockingQueue}.</li>
 * </ul>
 * 
 * <p><strong>Threading and Queue Processing Model</strong></p>
 * <p>The concurrency model operates as a single-producer, single-consumer task processor:</p>
 * <ul>
 *   <li>When a message is received via {@link #callback(String, Message)}, a task containing the 
 *       message and its source topic is instantly pushed onto an internal {@link java.util.concurrent.BlockingQueue} 
 *       via the blocking {@code put()} method. This returns immediately to the publisher thread.</li>
 *   <li>A dedicated background consumer thread polls tasks sequentially via the blocking {@code take()} method 
 *       and executes the wrapped agent's native {@code callback} logic.</li>
 * </ul>
 * 
 * <p><strong>Lifecycle and Safety Guards</strong></p>
 * <ul>
 *   <li><strong>Capacity Bounds:</strong> Uses a fixed-size {@link java.util.concurrent.ArrayBlockingQueue} 
 *       to manage backpressure. If the consumer thread cannot keep up with high-throughput traffic, the publishing 
 *       thread is safely blocked when queue capacity is reached.</li>
 *   <li><strong>Graceful Tear Down:</strong> Invoking {@link #close()} flags {@code running} to false, wakes 
 *       up the consumer thread if blocked on {@code take()} using interrupts, and delegates cleanup to the 
 *       inner agent.</li>
 *   <li><strong>Redundant Closures:</strong> Safe checks prevent re-entry errors or concurrent thread 
 *       interrupt blockages when closing an already terminated agent.</li>
 * </ul>
 * 
 * <p><strong>Code Integration Example:</strong></p>
 * <pre>{@code
 * Agent basicAgent = new BinOpAgent("plus", "A", "B", "R1", (x, y) -> x + y);
 * 
 * // Decorate the basic agent to run asynchronously on a dedicated thread with a queue capacity of 50
 * ParallelAgent parallelAgent = new ParallelAgent(basicAgent, 50);
 * 
 * // Triggering callbacks now returns immediately, executing the addition strategy in the background
 * parallelAgent.callback("A", new Message(10.0));
 * 
 * // Safely shut down the background consumer thread
 * parallelAgent.close();
 * }</pre>
 * 
 * @see graph.Agent
 * @see java.util.concurrent.BlockingQueue
 * @see java.util.concurrent.ArrayBlockingQueue
 */
public class ParallelAgent implements Agent {

    private final Agent agent;
    private final BlockingQueue<MessageTask> queue;
    private final Thread consumerThread;
    private volatile boolean running = true;

    /**
     * Represents a task containing a message payload and its source topic 
     * captured during message dispatching.
     */
    private static class MessageTask {
        final String topic;
        final Message message;

        MessageTask(String topic, Message message) {
            this.topic = topic;
            this.message = message;
        }
    }

    /**
     * Constructs a ParallelAgent wrapping an existing Agent.
     * Throws explicit validation exceptions for illegal arguments.
     * 
     * @param agent    the target Agent to decorate
     * @param capacity the maximum capacity of the message queue (must be greater than zero)
     * @throws IllegalArgumentException if the provided agent is null or if the capacity is invalid
     */
    public ParallelAgent(Agent agent, int capacity) {
        if (agent == null) {
            throw new IllegalArgumentException("Wrapped agent cannot be null");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Queue capacity must be greater than zero");
        }
        
        this.agent = agent;
        this.queue = new ArrayBlockingQueue<>(capacity);

        this.consumerThread = new Thread(() -> {
            while (running) {
                try {
                    MessageTask task = queue.take();
                    this.agent.callback(task.topic, task.message);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        this.consumerThread.start();
    }

    /**
     * Retrieves the descriptive, unique name of the decorated agent.
     * 
     * @return the agent's name string
     */
    @Override
    public String getName() {
        return agent.getName();
    }

    /**
     * Resets the internal state and cache values of the decorated agent.
     */
    @Override
    public void reset() {
        agent.reset();
    }

    /**
     * Places the incoming message task onto the active message queue to be processed 
     * asynchronously by the background consumer thread.
     * 
     * <p>If the queue is full, the calling publisher thread is blocked until capacity becomes available.</p>
     * 
     * @param topic the name of the topic that dispatched the update
     * @param msg   the immutable message container holding the new payload value
     */
    @Override
    public void callback(String topic, Message msg) {
        // Prevent infinite blocking if a message is published after close() has stopped the thread
        if (!running) {
            return; 
        }
        try {
            queue.put(new MessageTask(topic, msg));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Cleans up processing execution context threads and closes the decorated agent.
     * 
     * <p>Wakes up the active consumer thread if blocked on queue polling and unregisters 
     * topic subscriptions to prevent memory leaks.</p>
     */
    @Override
    public void close() {
        // Prevent redundant closures
        if (!running) {
            return; 
        }
        this.running = false;
        this.consumerThread.interrupt(); // Wake thread up if blocked on take()
        
        agent.close();
    }
}