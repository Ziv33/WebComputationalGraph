package configs;

/**
 * Defines the contract for structural configuration controllers that build and manage 
 * messaging and agent processing topologies.
 * 
 * <p>Implementing classes wrap the logic needed to instantiate communication networks, 
 * validate dependency flows, and manage the execution lifecycle of active components.</p>
 * 
 * <p><strong>Lifecycle Management:</strong></p>
 * <ul>
 *   <li><strong>Instantiation ({@link #create()}):</strong> Sets up target agents and 
 *       topics, evaluates structural acyclic validation, and launches background pipelines.</li>
 *   <li><strong>Identification ({@link #getName()}, {@link #getVersion()}):</strong> Exposes metadata 
 *       identifying the name and release version of the configuration.</li>
 *   <li><strong>Tear Down ({@link #close()}):</strong> Gracefully terminates active background 
 *       execution contexts, freeing thread pools and clearing registered directories to prevent memory leaks.</li>
 * </ul>
 * 
 * <p><strong>Code Integration Example:</strong></p>
 * <pre>{@code
 * Config pipelineConfig = new GenericConfig();
 * ((GenericConfig) pipelineConfig).setConfFile("config_files/pipeline.conf");
 * 
 * // Deploy and initialize the network
 * pipelineConfig.create();
 * System.out.println("Running: " + pipelineConfig.getName() + " [v" + pipelineConfig.getVersion() + "]");
 * 
 * // Shut down execution units when finished
 * pipelineConfig.close();
 * }</pre>
 * 
 * @see configs.GenericConfig
 */
public interface Config {

    /**
     * Initializes, validates, and deploys the processing topology.
     * 
     * <p>This method handles the complete setup chain: parsing target resource definitions, 
     * creating messaging pipelines, running structural cyclic validation checks, and launching 
     * asynchronous worker threads.</p>
     * 
     * @throws RuntimeException if structural validation fails (e.g., circular loops detected) 
     *                          or if reflective instantiation errors occur.
     */
    void create();

    /**
     * Retrieves the unique, human-readable name of the configuration.
     * 
     * @return a descriptive name string
     */
    String getName();

    /**
     * Retrieves the operational release version number of this configuration.
     * 
     * @return the revision version integer
     */
    int getVersion();

    /**
     * Terminates all background execution context pipelines associated with this configuration.
     * 
     * <p>All active thread pools, worker queues, and registered topic subscriptions must be 
     * released and shut down cleanly to prevent resource retention and thread leakage.</p>
     */
    void close();
}