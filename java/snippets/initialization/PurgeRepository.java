package initialization;

import java.io.*;
import java.util.*;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

/**
 * Provides an example of purging the entity repository.
 * NOTE: purging the repository should be done with great care and with
 * no processes actively accessing the repository with the exception of
 * the one doing the purging.
 */
public class PurgeRepository {
    private static final String PURGE_MESSAGE = """
            **************************************** WARNING ****************************************

            This example will purge all currently loaded data from the Senzing datastore!
            Before proceeding, all instances of Senzing (custom code, tools, etc.) must be shut down.

            *****************************************************************************************

            Are you sure you want to continue and purge the Senzing datastore? (y/n) """;

    private static final Set<String> YES_ANSWERS
        = Set.of("y", "Y", "Yes", "yes", "YES");
            
    private static final long ONE_MILLION = 1000000L;

    public static void main(String[] args) {
        System.out.println(PURGE_MESSAGE);
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String response = br.readLine();
            if (!YES_ANSWERS.contains(response)) {
                System.exit(1);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        // create a descriptive instance name (can be anything)
        String instanceName = PurgeRepository.class.getSimpleName();

        // initialize the Senzing environment
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(instanceName)
            .verboseLogging(false)
            .build();
        
        try {
            SzDiagnostic diagnostic = env.getDiagnostic();
            
            long start = System.nanoTime();

            System.out.println("Purging Senzing repository...");
            diagnostic.purgeRepository();

            long duration = (System.nanoTime() - start) / ONE_MILLION;
            System.out.println("Purged Senzing repository.  (" + duration + "ms)");
            
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
