package configuration;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

/**
 * Provides a simple example of adding records to the Senzing repository.
 */
public class AddDataSources {
    public static void main(String[] args) {
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        // create a descriptive instance name (can be anything)
        String instanceName = AddDataSources.class.getSimpleName();

        // initialize the Senzing environment
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(instanceName)
            .verboseLogging(false)
            .build();
        
        try {
            // get the config manager from the environment
            SzConfigManager configMgr = env.getConfigManager();

            // setup a loop to handle race-condition conflicts on 
            // replacing the default config ID
            boolean replacedConfig = false;
            while (!replacedConfig) {
                // get the current default config ID
                long configId = configMgr.getDefaultConfigId();

                // get the SzConfig for the config ID
                SzConfig config = configMgr.createConfig(configId);

                // create an array of the data sources to add
                String[] dataSources = { "CUSTOMERS", "EMPLOYEES", "WATCHLIST" };

                // loop through the array and add each data source
                for (String dataSource : dataSources) {
                    config.addDataSource(dataSource);
                }

                // export the modified config to JSON text
                String modifiedConfig = config.export();

                // register the modified config in the repository
                long newConfigId = configMgr.registerConfig(modifiedConfig);

                try {                    
                    // replace the default config
                    configMgr.replaceDefaultConfigId(configId, newConfigId);

                    // if we get here then set the flag indicating success
                    replacedConfig = true;

                } catch (SzReplaceConflictException e) {
                    // if we get here then another thread or process has
                    // changed the default config ID since we retrieved it
                    // (i.e.: we have a race condition) so we allow the 
                    // loop to repeat with the latest default config ID
                }
            }

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