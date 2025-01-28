package initialization;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

/**
 * Provides a simple example of adding records to the Senzing repository.
 */
public class EnvironmentAndHubs {
    private static final long ONE_MILLION = 1000000L;

    public static void main(String[] args) {
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        // create a descriptive instance name (can be anything)
        String instanceName = EnginePriming.class.getSimpleName();

        // initialize the Senzing environment
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(instanceName)
            .verboseLogging(false)
            .build();
        
        try {
            SzProduct       product     = env.getProduct();
            SzConfig        config      = env.getConfig();
            SzConfigManager configMgr   = env.getConfigManager();
            SzDiagnostic    diagnostic  = env.getDiagnostic();
            SzEngine        engine      = env.getEngine();
            
            System.out.println(product);
            System.out.println(config);
            System.out.println(configMgr);
            System.out.println(diagnostic);
            System.out.println(engine);

            // do work with the hub handles which are valid
            // until the env.destroy() function is called

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