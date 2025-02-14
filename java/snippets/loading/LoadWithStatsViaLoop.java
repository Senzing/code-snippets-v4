package loading;

import java.io.*;
import javax.json.*;
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

/**
 * Provides a simple example of adding records to the Senzing repository.
 */
public class LoadWithStatsViaLoop {
    public static void main(String[] args) {
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        // create a descriptive instance name (can be anything)
        String instanceName = LoadWithStatsViaLoop.class.getSimpleName();

        // initialize the Senzing environment
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(instanceName)
            .verboseLogging(false)
            .build();
        
        String filePath = (args.length > 0) ? args[0] : DEFAULT_FILE_PATH;

        try (FileInputStream    fis = new FileInputStream(filePath);
             InputStreamReader  isr = new InputStreamReader(fis, UTF_8);
             BufferedReader     br  = new BufferedReader(isr)) 
        {
            // get the engine from the environment
            SzEngine engine = env.getEngine();

            int lineNumber = 0;
            // loop through the example records and add them to the repository
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                // increment the line number
                lineNumber++;

                // trim the line
                line = line.trim();
                
                // skip any blank lines
                if (line.length() == 0) continue;

                // skip any commented lines
                if (line.startsWith("#")) continue;

                try {
                    // parse the line as a JSON object
                    JsonObject recordJson 
                        = Json.createReader(new StringReader(line)).readObject();            
                
                    // extract the data source code and record ID
                    String  dataSourceCode  = recordJson.getString(DATA_SOURCE, null);
                    String  recordId        = recordJson.getString(RECORD_ID, null);

                    // call the addRecord() function with no flags
                    engine.addRecord(
                        SzRecordKey.of(dataSourceCode, recordId), line, SZ_NO_FLAGS);

                    successCount++;

                    // check if it is time obtain stats
                    if ((successCount % STATS_INTERVAL) == 0) {
                        try {
                            String stats = engine.getStats();
                            if (stats.length() > STATS_TRUNCATE) {
                                stats = stats.substring(0, STATS_TRUNCATE) + " ...";
                            }
                            System.out.println("* STATS: " + stats);

                        } catch (SzException e) {
                            // trap the stats exeption so it is not misinterpreted
                            // as an exception from engine.addRecord()
                            System.err.println("**** FAILED TO OBTAIN STATS: " + e);
                        }
                    }

                } catch (JsonException|SzBadInputException e) {
                    logFailedRecord(ERROR, e, lineNumber, line);
                    errorCount++;   // increment the error count

                } catch (SzRetryableException e) {
                    logFailedRecord(WARNING, e, lineNumber, line);
                    errorCount++;   // increment the error count
                    retryCount++;   // increment the retry count

                    // track the retry record so it can be retried later
                    if (retryFile == null) {
                        retryFile = File.createTempFile(RETRY_PREFIX, RETRY_SUFFIX);
                        retryWriter = new PrintWriter(
                            new OutputStreamWriter(new FileOutputStream(retryFile), UTF_8));
                    }
                    retryWriter.println(line);

                } catch (Exception e) {
                    // catch any other exception (incl. SzException) here
                    logFailedRecord(CRITICAL, e, lineNumber, line);
                    errorCount++;
                    throw e; // rethrow since exception is critical
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
            // IMPORTANT: make sure to destroy the environment
            env.destroy();

            System.out.println();
            System.out.println("Records successfully added : " + successCount);
            System.out.println("Records failed with errors : " + errorCount);

            // check on any retry records
            if (retryWriter != null) {
                retryWriter.flush();
                retryWriter.close();
            }
            if (retryCount > 0) {
                System.out.println(retryCount + " records to be retried in " + retryFile);
            }
            System.out.flush();

        }

    }

    /**
     * Example method for logging failed records.
     * 
     * @param errorType The error type description.
     * @param exception The exception itself.
     * @param lineNumber The line number of the failed record in the JSON input file.
     * @param recordJson The JSON text for the failed record.
     */
    private static void logFailedRecord(String      errorType,
                                        Exception   exception, 
                                        int         lineNumber, 
                                        String      recordJson) 
    {
        System.err.println();
        System.err.println(
            "** " + errorType + " ** FAILED TO ADD RECORD AT LINE " + lineNumber + ": ");
        System.err.println(recordJson);
        System.err.println(exception);
        System.err.flush();
    }

    private static final String DEFAULT_FILE_PATH = "../resources/data/load-500.jsonl";

    private static final String UTF_8 = "UTF-8";

    private static final int STATS_INTERVAL = 100;
    private static final int STATS_TRUNCATE = 70;

    private static final String RETRY_PREFIX = "retry-";
    private static final String RETRY_SUFFIX = ".jsonl";

    private static final String DATA_SOURCE = "DATA_SOURCE";
    private static final String RECORD_ID   = "RECORD_ID";

    private static final String ERROR       = "ERROR";
    private static final String WARNING     = "WARNING";
    private static final String CRITICAL    = "CRITICAL";

    private static int         errorCount      = 0;
    private static int         successCount    = 0;
    private static int         retryCount      = 0;
    private static File        retryFile       = null;
    private static PrintWriter retryWriter     = null;
}