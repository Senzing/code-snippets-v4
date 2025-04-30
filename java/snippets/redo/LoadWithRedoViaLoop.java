package redo;

import java.io.*;
import java.util.List;

import javax.json.*;
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

/**
 * Provides a simple example of processing redo records while 
 * loading records to the Senzing repository.
 */
public class LoadWithRedoViaLoop {
    public static void main(String[] args) {
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        // create a descriptive instance name (can be anything)
        String instanceName = LoadWithRedoViaLoop.class.getSimpleName();

        // initialize the Senzing environment
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(instanceName)
            .verboseLogging(false)
            .build();
        
        try {
            // get the engine from the environment
            SzEngine engine = env.getEngine();

            // loop through the input files
            for (String filePath: INPUT_FILES) {
                try (FileInputStream    fis = new FileInputStream(filePath);
                     InputStreamReader  isr = new InputStreamReader(fis, UTF_8);
                     BufferedReader     br  = new BufferedReader(isr)) 
                {
                    int lineNumber = 0;
                    // loop through the example records and add them to the repository
                    for (String line = br.readLine(); line != null; line = br.readLine()) {
                        // increment the line number
                        lineNumber++;

                        // trim the line
                        line = line.trim();
                        
                        // skip any blank lines
                        if (line.length() == 0) {
                            continue;
                        }

                        // skip any commented lines
                        if (line.startsWith("#")) {
                            continue;
                        }

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

                        } catch (JsonException | SzBadInputException e) {
                            logFailedRecord(ERROR, e, filePath, lineNumber, line);
                            errorCount++;   // increment the error count

                        } catch (SzRetryableException e) {
                            logFailedRecord(WARNING, e, filePath, lineNumber, line);
                            errorCount++;   // increment the error count
                            retryCount++;   // increment the retry count

                            trackRetryRecord(line);

                        } catch (Exception e) {
                            // catch any other exception (incl. SzException) here
                            logFailedRecord(CRITICAL, e, filePath, lineNumber, line);
                            errorCount++;
                            throw e; // rethrow since exception is critical
                        }
                    }
                }
            }

            // now that we have loaded the records, check for redos and handle them
            while (engine.countRedoRecords() > 0) {
                // get the next redo record
                String redo = engine.getRedoRecord();

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
            // IMPORTANT: make sure to destroy the environment
            env.destroy();

            System.out.println();
            System.out.println("Records successfully added   : " + successCount);
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

    }

    /**
     * Example method for logging failed records.
     * 
     * @param errorType The error type description.
     * @param exception The exception itself.
     * @param filePath The path to the file that was the source of the failed record.
     * @param lineNumber The line number of the failed record in the JSON input file.
     * @param recordJson The JSON text for the failed record.
     */
    private static void logFailedRecord(String      errorType,
                                        Exception   exception,
                                        String      filePath,
                                        int         lineNumber, 
                                        String      recordJson) 
    {
        File file = new File(filePath);
        String fileName = file.getName();

        System.err.println();
        System.err.println(
            "** " + errorType + " ** FAILED TO ADD RECORD IN " + fileName 
            + " AT LINE " + lineNumber + ": ");
        System.err.println(recordJson);
        System.err.println(exception);
        System.err.flush();
    }

    /**
     * Example method for logging failed records.
     * 
     * @param errorType The error type description.
     * @param exception The exception itself.
     * @param redoRecord The JSON text for the failed record.
     */
    private static void logFailedRedo(String      errorType,
                                      Exception   exception,  
                                      String      redoRecord) 
    {
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
        throws IOException
    {
        // track the retry record so it can be retried later
        if (retryFile == null) {
            retryFile = File.createTempFile(RETRY_PREFIX, RETRY_SUFFIX);
            retryWriter = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(retryFile), UTF_8));
        }
        retryWriter.println(recordJson);        
    }
    
    private static final List<String> INPUT_FILES = List.of(
        "../resources/data/truthset/customers.jsonl",
        "../resources/data/truthset/reference.jsonl",
        "../resources/data/truthset/watchlist.jsonl");

    private static final String UTF_8 = "UTF-8";

    private static final String RETRY_PREFIX = "retry-";
    private static final String RETRY_SUFFIX = ".jsonl";

    private static final String DATA_SOURCE = "DATA_SOURCE";
    private static final String RECORD_ID   = "RECORD_ID";

    private static final String ERROR       = "ERROR";
    private static final String WARNING     = "WARNING";
    private static final String CRITICAL    = "CRITICAL";

    private static int         errorCount      = 0;
    private static int         successCount    = 0;
    private static int         redoneCount     = 0;
    private static int         retryCount      = 0;
    private static File        retryFile       = null;
    private static PrintWriter retryWriter     = null;
}
