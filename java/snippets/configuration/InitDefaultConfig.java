package configuration;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

/**
 * Provides a simple example of adding records to the Senzing repository.
 */
public class InitDefaultConfig {
    public static void main(String[] args) {
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        // create a descriptive instance name (can be anything)
        String instanceName = InitDefaultConfig.class.getSimpleName();

        // initialize the Senzing environment
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(instanceName)
            .verboseLogging(false)
            .build();
        
        try {
            // get the config and config manager from the environment
            SzConfig        config      = env.getConfig();
            SzConfigManager configMgr   = env.getConfigManager();

            // prepare an in-memory config to be modified and get the handle
            long    configHandle        = config.createConfig();
            String  configDefinition    = null;
            try {
                configDefinition = config.exportConfig(configHandle);

            } finally {
                config.closeConfig(configHandle);
            }

            // add the modified config to the repository with a comment
            long configId = configMgr.addConfig(
                configDefinition, "Initial configuration");

            // replace the default config
            configMgr.setDefaultConfigId(configId);

        } catch (SzException e) {
            // handle any exception that may have occurred
            System.err.println("Senzing Error Message : " + e.getMessage());
            System.err.println("Senzing Error Code    : " + e.getErrorCode());
            e.printStackTrace();
            throw new RuntimeException(e);

        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof RuntimeException) {
                throw ((RuntimeException) e);
            }
            throw new RuntimeException(e);

        } finally {
            // IMPORTANT: make sure to destroy the environment
            env.destroy();
        }

    }
}