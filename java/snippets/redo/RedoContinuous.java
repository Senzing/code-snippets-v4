package redo;

import java.io.*;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

/**
 * Provides an example of a process that continuously processes
 * the pending redo records in the Senzing repository.
 */
public class RedoContinuous {
    public static void main(String[] args) {
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        // create a descriptive instance name (can be anything)
        String instanceName = RedoContinuous.class.getSimpleName();

        // initialize the Senzing environment
        SzEnvironment env = SzCoreEnvironment.newBuilder()
                .settings(settings)
                .instanceName(instanceName)
                .verboseLogging(false)
                .build();

        // make sure we cleanup if exiting by CTRL-C or due to an exception
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // IMPORTANT: make sure to destroy the environment
            env.destroy();
            outputRedoStatistics();
        }));

        try {
            // get the engine from the environment
            SzEngine engine = env.getEngine();

            while (true) {
                // get the next redo record
                String redo = engine.getRedoRecord();

                // check if no redo records are available
                if (redo == null) {
                    outputRedoStatistics();
                    System.out.println();
                    System.out.println(
                            "No redo records to process.  Pausing for "
                                    + REDO_PAUSE_DESCRIPTION + "....");
                    System.out.println("Press CTRL-C to exit.");
                    try {
                        Thread.sleep(REDO_PAUSE_TIMEOUT);
                    } catch (InterruptedException ignore) {
                        // ignore the exception
                    }
                    continue;
                }

                try {
                    // process the redo record
                    engine.processRedoRecord(redo, SZ_NO_FLAGS);

                    // increment the redone count
                    redoneCount++;

                } catch (SzRetryableException e) {
                    logFailedRedo(WARNING, e, redo);
                    errorCount++;
                    retryCount++;
                    trackRetryRecord(redo);

                } catch (Exception e) {
                    logFailedRedo(CRITICAL, e, redo);
                    errorCount++;
                    throw e;
                }
            }

        } catch (Exception e) {
            System.err.println();
            System.err.println("*** Terminated due to critical error ***");
            System.err.flush();
            if (e instanceof RuntimeException) {
                throw ((RuntimeException) e);
            }
            throw new RuntimeException(e);

        } finally {
            // normally we would call env.destroy() here, but we have registered
            // a shutdown hook to do that since termination will typically occur
            // via CTRL-C being pressed, and the shutdown hook will still run if
            // we get an exception
        }

    }

    private static void outputRedoStatistics() {
        System.out.println();
        System.out.println("Redos successfully processed : " + redoneCount);
        System.out.println("Total failed records/redos   : " + errorCount);

        // check on any retry records
        if (retryWriter != null) {
            retryWriter.flush();
            retryWriter.close();
        }
        if (retryCount > 0) {
            System.out.println(
                    retryCount + " records/redos to be retried in " + retryFile);
        }
        System.out.flush();
    }

    /**
     * Example method for logging failed records.
     * 
     * @param errorType  The error type description.
     * @param exception  The exception itself.
     * @param redoRecord The JSON text for the redo record.
     */
    private static void logFailedRedo(String errorType,
            Exception exception,
            String redoRecord) {
        System.err.println();
        System.err.println("** " + errorType + " ** FAILED TO PROCESS REDO: ");
        System.err.println(redoRecord);
        System.err.println(exception);
        System.err.flush();
    }

    /**
     * Tracks the specified JSON record definition to be retried in a
     * retry file.
     * 
     * @param recordJson The JSON text defining the record to be retried.
     * 
     * @throws IOException If a failure occurs in writing the record to the
     *                     retry file.
     */
    private static void trackRetryRecord(String recordJson)
            throws IOException {
        // track the retry record so it can be retried later
        if (retryFile == null) {
            retryFile = File.createTempFile(RETRY_PREFIX, RETRY_SUFFIX);
            retryWriter = new PrintWriter(
                    new OutputStreamWriter(new FileOutputStream(retryFile), UTF_8));
        }
        retryWriter.println(recordJson);
    }

    private static final String UTF_8 = "UTF-8";

    private static final String RETRY_PREFIX = "retry-";
    private static final String RETRY_SUFFIX = ".jsonl";

    private static final long REDO_PAUSE_TIMEOUT = 30000L;

    private static final String REDO_PAUSE_DESCRIPTION = "30 seconds";

    private static final String WARNING = "WARNING";
    private static final String CRITICAL = "CRITICAL";

    private static int errorCount = 0;
    private static int redoneCount = 0;
    private static int retryCount = 0;
    private static File retryFile = null;
    private static PrintWriter retryWriter = null;
}
