package information;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

/**
 * Provides an example of obtaining the datastore information
 * from the diagnostic hub.
 */
public class GetRepositoryInfo {
    public static void main(String[] args) {
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        // create a descriptive instance name (can be anything)
        String instanceName = GetRepositoryInfo.class.getSimpleName();

        // initialize the Senzing environment
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(instanceName)
            .verboseLogging(false)
            .build();
        
        try {
            SzDiagnostic diagnostic = env.getDiagnostic();
            
            String result = diagnostic.getRepositoryInfo();

            System.out.println(result);
            
        } catch (SzException e) {
            // handle any exception that may have occurred
            System.err.println("Senzing Error Message : " + e.getMessage());
            System.err.println("Senzing Error Code    : " + e.getErrorCode());
            e.printStackTrace();
            throw new RuntimeException(e);

        } catch (Exception e) {
            System.err.println();
            System.err.println("*** Terminated due to critical error ***");
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
