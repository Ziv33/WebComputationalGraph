package configs;

import java.util.ArrayList;
import java.util.List;
import graph.Agent;
import graph.BinOpAgent;

/**
 * A hardcoded computational math layout configuration that sets up a multi-stage calculation pipeline.
 * 
 * <p>This class implements the {@link configs.Config} interface to deploy a static math-processing graph. 
 * It instantiates and chains three {@link graph.BinOpAgent} instances using intermediate subscription topics 
 * to execute the algebraic expression:</p>
 * <p style="text-align: center;">{@code R3 = (A + B) * (A - B)}</p>
 * 
 * <p><strong>Data Flow Routing:</strong></p>
 * <ol>
 *   <li><strong>Stage 1 (Parallel Addition &amp; Subtraction):</strong> 
 *       <ul>
 *         <li>The {@code plus} agent subscribes to inputs {@code A} and {@code B}, publishing the sum to {@code R1}.</li>
 *         <li>The {@code minus} agent subscribes to inputs {@code A} and {@code B}, publishing the difference to {@code R2}.</li>
 *       </ul>
 *   </li>
 *   <li><strong>Stage 2 (Multiplication):</strong> 
 *       <ul>
 *         <li>The {@code mul} agent subscribes to intermediate topics {@code R1} and {@code R2}, publishing the final product to {@code R3}.</li>
 *       </ul>
 *   </li>
 * </ol>
 * 
 * <p><strong>Required Core Framework Components (Internal Dependencies):</strong></p>
 * <ul>
 *   <li>{@link configs.Config} - Lifecycle interface determining operational deployment states.</li>
 *   <li>{@link graph.BinOpAgent} - Mathematical strategy-driven operational agent.</li>
 *   <li>{@link graph.Agent} - Core framework abstraction interface.</li>
 * </ul>
 * 
 * <p><strong>Code Integration Example:</strong></p>
 * <pre>{@code
 * MathExampleConfig config = new MathExampleConfig();
 * 
 * // Deploy the math calculation pipeline
 * config.create();
 * System.out.println("Pipeline deployed: " + config.getName() + " [v" + config.getVersion() + "]");
 * 
 * // Resolve the topic manager and publish values
 * TopicManager manager = TopicManagerSingleton.get();
 * manager.getTopic("A").publish(new Message(6.0));
 * manager.getTopic("B").publish(new Message(2.0));
 * 
 * // Output topic "R3" will receive: (6 + 2) * (6 - 2) = 8 * 4 = 32.0
 * 
 * // Tear down execution resources
 * config.close();
 * }</pre>
 * 
 * @see configs.Config
 * @see graph.BinOpAgent
 * @see graph.Agent
 */
public class MathExampleConfig implements Config {

    private final List<Agent> agents;

    /**
     * Constructs a MathExampleConfig instance with an empty registry of managed agents.
     */
    public MathExampleConfig() {
        this.agents = new ArrayList<>();
    }

    /**
     * Dynamically instantiates the addition, subtraction, and multiplication agents, 
     * registers them with the global TopicManager directory, and retains references 
     * for graceful disposal.
     */
    @Override
    public void create() {
        // Create the agents and keep track of them for cleanup
        agents.add(new BinOpAgent("plus", "A", "B", "R1", (x, y) -> x + y));
        agents.add(new BinOpAgent("minus", "A", "B", "R2", (x, y) -> x - y));
        agents.add(new BinOpAgent("mul", "R1", "R2", "R3", (x, y) -> x * y));
    }

    /**
     * Retrieves the unique, human-readable name of the configuration.
     * 
     * @return the configuration's name string
     */
    @Override
    public String getName() {
        return "Math Example";
    }

    /**
     * Retrieves the structural release version number of this configuration.
     * 
     * @return the revision version integer
     */
    @Override
    public int getVersion() {
        return 1;
    }

    /**
     * Safely closes and unregisters all active agents created by this configuration, 
     * preventing resource leaks and dangling references.
     */
    @Override
    public void close() {
        // Cleanly close all created agents
        for (Agent agent : agents) {
            agent.close();
        }
        agents.clear();
    }
}