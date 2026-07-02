package configs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import graph.Agent;
import graph.Graph;
import graph.ParallelAgent;
import graph.TopicManagerSingleton;

/**
 * A configuration manager that parses network layout files, validates the 
 * resulting logical topology for cyclic paths, and instantiates parallel agents.
 * 
 * <p><strong>Required Core Framework Components (Internal Dependencies):</strong></p>
 * <p>This loader relies on the following framework classes being correctly configured and available 
 * in the class path to instantiate and execute the calculation pipeline:</p>
 * <ul>
 *   <li>{@link graph.Agent} - The core interface implemented by loaded agent classes. Target agent 
 *       classes loaded via reflection must expose a public constructor with the exact signature: 
 *       {@code public ClassName(String[] subscriptions, String[] publications)}.</li>
 *   <li>{@link graph.ParallelAgent} - Thread-isolated concurrent decorator that wraps each loaded 
 *       agent instance to run callbacks inside a dedicated queue-backed thread.</li>
 *   <li>{@link graph.Graph} - Graph structure analyzer that inspects topic and agent registrations 
 *       to verify that no cyclical dependencies exist in the routing topology.</li>
 *   <li>{@link graph.TopicManagerSingleton} - The singleton state directory resolving the underlying 
 *       topic references.</li>
 * </ul>
 * 
 * <p><strong>Lifecycle Stages:</strong></p>
 * <ol>
 *   <li><strong>Parsing and Instantiation:</strong> Reads the configuration layout 
 *       in three-line blocks. It loads implementation classes via reflection, invoking 
 *       constructors that accept string arrays of subscription and publication topics.</li>
 *   <li><strong>Validation:</strong> Constructs a graph representation of the topics 
 *       and registered components to perform cycle detection. This checks the flow 
 *       structure to ensure execution safety before processing begins.</li>
 *   <li><strong>Commit and Dispatch:</strong> Wraps the validated agents inside concurrent 
 *       {@link graph.ParallelAgent} active objects to handle execution asynchronously on 
 *       background queues.</li>
 * </ol>
 * 
 * <p><strong>File Formatting Specification:</strong></p>
 * <p>The configuration file expects repeating three-line sets, where empty lines are ignored:</p>
 * <pre>
 * Line 1: Fully qualified name of the agent class
 * Line 2: Comma-separated list of subscription topics (can be empty)
 * Line 3: Comma-separated list of publication topics (can be empty)
 * </pre>
 * 
 *<p><strong>Example Configuration File ({@code simple.conf}):</strong></p>
 * <pre>
 * graph.PlusAgent
 * A,B
 * C
 * graph.IncAgent
 * C
 * D
 * </pre>
 * 
 * <p><strong>Input Initialization Variations:</strong></p>
 * <p>Note that different agent classes handle initial cache states differently. For example, 
 * in the {@code simple.conf} configuration above, {@code PlusAgent} initializes its internal values 
 * to {@code 0.0} upon construction. Consequently, publishing a message of {@code 1.0} to topic 
 * {@code A} will immediately trigger an execution output of {@code 1.0} to topic {@code C} (calculating 
 * {@code 1.0 + 0.0}). In contrast, {@link graph.BinOpAgent} starts with uninitialized ({@code null}) 
 * variables and will not output results until both input topics receive their first messages.</p>
 * 
 * <p><strong>Code Integration Example:</strong></p>
 * <pre>{@code
 * GenericConfig loader = new GenericConfig();
 * loader.setConfFile("config_files/simple.conf");
 * 
 * try {
 *     // Initializes and runs all parallel agents safely after passing cycle validation
 *     loader.create();
 *     System.out.println("Processing pipeline successfully deployed.");
 * } catch (Exception e) {
 *     System.err.println("Deployment failed and rolled back: " + e.getMessage());
 * }
 * 
 * // Shut down execution pipelines when shutting down the application
 * Runtime.getRuntime().addShutdownHook(new Thread(() -> {
 *     loader.close();
 * }));
 * }</pre>
 * 
 * <p><strong>Rollback Mechanics:</strong></p>
 * <p>If a cycle is detected, or if reflective instantiation fails, the configuration manager 
 * performs an automatic cleanup. Any newly constructed agents are closed, and registered topic 
 * states are cleared to maintain consistency and prevent thread leaks.</p>
 * 
 * @see configs.Config
 * @see graph.ParallelAgent
 * @see graph.TopicManagerSingleton
 */
public class GenericConfig implements Config {
    private String confFile;
    private final List<ParallelAgent> activeAgents;

    /**
     * Constructs a GenericConfig instance to handle dynamic agent topology creation.
     */
    public GenericConfig() {
        this.activeAgents = new ArrayList<>();
    }

    /**
     * Sets the path of the target configuration file.
     * 
     * @param confFile the system path to the .conf file
     */
    public void setConfFile(String confFile) {
        this.confFile = confFile;
    }

   /**
    * Reads the configuration file, parses the agent specifications, 
    * validates the graph for cycles, and dynamically instantiates them.
    * 
    * @throws IllegalArgumentException if the configuration file path is null or empty, 
    *                                  or if the line count is not a multiple of 3
    * @throws RuntimeException if an I/O error occurs, if reflection classes cannot be found, 
    *                          or if a cyclic dependency is detected in the topology
    */
    @Override
    public void create() {
        if (confFile == null || confFile.isEmpty()) {
            throw new IllegalArgumentException("Configuration file path is null or empty.");
        }

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(confFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    lines.add(trimmed);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Encountered IOException while reading file: " + e.getMessage(), e);
        }

        if (lines.size() % 3 != 0) {
            throw new IllegalArgumentException("Sizing mismatch! Total non-empty lines parsed (" + lines.size() 
                    + ") is not a multiple of 3. Expected format is exactly 3 lines per agent.");
        }

        // List to hold inner agents temporarily for rollback purposes
        List<Agent> pendingAgents = new ArrayList<>();

        try {
            // --- Phase 1: Parse and instantiate inner agents (hooks them to TopicManager) ---
            for (int i = 0; i < lines.size(); i += 3) {
                String className = lines.get(i);
                String subsLine = lines.get(i + 1);
                String pubsLine = lines.get(i + 2);

                String[] subs = subsLine.isEmpty() ? new String[0] : subsLine.split(",");
                String[] pubs = pubsLine.isEmpty() ? new String[0] : pubsLine.split(",");

                Class<?> agentClass = Class.forName(className);
                Constructor<?> constructor = agentClass.getConstructor(String[].class, String[].class);
                Agent agent = (Agent) constructor.newInstance((Object) subs, (Object) pubs);
                
                pendingAgents.add(agent);
            }

            // --- Phase 2: Validate the graph for cycles BEFORE starting threads ---
            Graph graph = new Graph();
            graph.createFromTopics(); // Builds graph from TopicManager
            if (graph.hasCycles()) {
                throw new IllegalStateException("Cycle detected in configuration! Graph execution aborted.");
            }

            // --- Phase 3: Commit - Graph is safe, start the ParallelAgents ---
            for (Agent agent : pendingAgents) {
                ParallelAgent parallelAgent = new ParallelAgent(agent, 20); // Starts the thread
                activeAgents.add(parallelAgent);
            }
            
            System.out.println("GenericConfig [Success]: Initialization complete. " + activeAgents.size() + " parallel agents running.");

        } catch (Exception e) {
            // --- ROLLBACK: If any error or cycle occurs, clean up everything ---
            System.err.println("GenericConfig [Error]: " + e.getMessage() + " Performing rollback...");
            for (Agent agent : pendingAgents) {
                agent.close();
            }
            TopicManagerSingleton.get().clear(); // Clean global state
            
            // Re-throw so ConfLoader knows the deployment failed and sends HTTP 400
            throw new RuntimeException("Deployment failed: " + e.getMessage() + ((e instanceof ClassNotFoundException) ? " - Unknown class": ""), e);
        }
    }

    /**
     * Retrieves the descriptive, human-readable name of this configuration.
     * 
     * @return the configuration's name string
     */
    @Override
    public String getName() {
        return "Generic Config";
    }

    /**
     * Retrieves the structural release version number of this configuration.
     * 
     * @return the configuration's version integer
     */
    @Override
    public int getVersion() {
        return 1;
    }

    /**
     * Safely terminates all active parallel agents and closes background threads.
     */
    @Override
    public void close() {
        for (ParallelAgent agent : activeAgents) {
            agent.close();
        }
        activeAgents.clear();
    }
}