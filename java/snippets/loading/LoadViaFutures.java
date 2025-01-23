package loading;

import java.io.*;
import javax.json.*;
import java.util.*;
import java.util.concurrent.*;
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

/**
 * Provides a simple example of adding records to the Senzing repository.
 */
public class LoadViaFutures {
    private static final String DEFAULT_FILE_PATH = "../resources/data/load-500.jsonl";

    private static final String UTF_8 = "UTF-8";

    private static final String RETRY_PREFIX = "retry-";
    private static final String RETRY_SUFFIX = ".jsonl";
    
    private static final int THREAD_COUNT = 8;

    private static final int BACKLOG_FACTOR = 10;

    private static final int MAXIMUM_BACKLOG = THREAD_COUNT * BACKLOG_FACTOR;

    private static final long PAUSE_TIMEOUT = 100L;

    private static final String DATA_SOURCE = "DATA_SOURCE";
    private static final String RECORD_ID   = "RECORD_ID";

    private static final String ERROR       = "ERROR";
    private static final String WARNING     = "WARNING";
    private static final String CRITICAL    = "CRITICAL";
    
    public record Record(int lineNumber, String line) { }

    private static int         errorCount      = 0;
    private static int         successCount    = 0;
    private static int         retryCount      = 0;
    private static File        retryFile       = null;
    private static PrintWriter retryWriter     = null;

    public static void main(String[] args) {
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        // create a descriptive instance name (can be anything)
        String instanceName = LoadViaFutures.class.getSimpleName();

        // initialize the Senzing environment
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(instanceName)
            .verboseLogging(false)
            .build();
        
        String filePath = (args.length > 0) ? args[0] : DEFAULT_FILE_PATH;

        // create the thread pool and executor service
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        // keep track of pending futures and don't backlog too many for memory's sake
        Map<Future<?>, Record> pendingFutures = new IdentityHashMap<>();

        try (FileInputStream    fis = new FileInputStream(filePath);
             InputStreamReader  isr = new InputStreamReader(fis, UTF_8);
             BufferedReader     br  = new BufferedReader(isr)) 
        {
            // get the engine from the environment
            SzEngine engine = env.getEngine();

            int     lineNumber  = 0;
            boolean eof         = false;

            while (!eof) {
                // loop through the example records and queue them up so long
                // as we have more records and backlog is not too large
                while (pendingFutures.size() < MAXIMUM_BACKLOG) {
                    // read the next line
                    String line = br.readLine();
                    lineNumber++;

                    // check for EOF
                    if (line == null) {
                        eof = true;
                        break;
                    }

                    // trim the line
                    line = line.trim();
                    
                    // skip any blank lines
                    if (line.length() == 0) continue;

                    // skip any commented lines
                    if (line.startsWith("#")) continue;

                    // construct the Record instance
                    Record record = new Record(lineNumber, line);

                    try {
                        // parse the line as a JSON object
                        JsonObject recordJson 
                            = Json.createReader(new StringReader(line)).readObject();            
                    
                        // extract the data source code and record ID
                        String      dataSourceCode  = recordJson.getString(DATA_SOURCE, null);
                        String      recordId        = recordJson.getString(RECORD_ID, null);
                        SzRecordKey recordKey       = SzRecordKey.of(dataSourceCode, recordId);

                        Future<?> future = executor.submit(() -> {
                            // call the addRecord() function with no flags
                            engine.addRecord(recordKey, record.line, SZ_NO_FLAGS);
                            
                            return null;
                        });

                        // add the futures to the pending future list
                        pendingFutures.put(future, record);

                    } catch (JsonException e) {
                        logFailedRecord(ERROR, e, lineNumber, line);
                        errorCount++;   // increment the error count          
                    }
                }

                do {
                    // handle any pending futures WITHOUT blocking to reduce the backlog
                    handlePendingFutures(pendingFutures, false);

                    // if we still have exceeded the backlog size then pause
                    // briefly before trying again
                    if (pendingFutures.size() >= MAXIMUM_BACKLOG) {
                        try {
                            Thread.sleep(PAUSE_TIMEOUT);

                        } catch (InterruptedException ignore) {
                            // do nothing
                        }
                    }
                } while (pendingFutures.size() >= MAXIMUM_BACKLOG);
            }

            // shutdown the executor service
            executor.shutdown();

            // after we have submitted all records we need to handle the remaining
            // pending futures so this time we block on each future
            handlePendingFutures(pendingFutures, true);

        } catch (Exception e) {
            System.err.println();
            System.err.println("*** Terminated due to critical error ***");
            System.err.flush();
            if (e instanceof RuntimeException) {
                throw ((RuntimeException) e);
            }
            throw new RuntimeException(e);

        } finally {
            // check if executor service is shutdown
            if (!executor.isShutdown()) {
                executor.shutdown();
            }

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

    private static void handlePendingFutures(Map<Future<?>, Record> pendingFutures, boolean blocking)
        throws Exception
    {
        // check for completed futures
        Iterator<Map.Entry<Future<?>,Record>> iter
        = pendingFutures.entrySet().iterator();
        
        // loop through the pending futures
        while (iter.hasNext()) {
            // get the next pending future
            Map.Entry<Future<?>,Record> entry = iter.next();
            Future<?> future  = entry.getKey();
            Record              record  = entry.getValue();
            
            // if not blocking and this one is not done then continue
            if (!blocking && !future.isDone()) continue;

            // remove the pending future from the map
            iter.remove();

            try {
                try {
                    // get the value to see if there was an exception
                    future.get();

                    // if we get here then increment the success count
                    successCount++;

                } catch (InterruptedException e) {
                    // this could only happen if blocking is true, just
                    // rethrow as retryable and log the interruption
                    throw e;

                } catch (ExecutionException e) {
                    // if execution failed with an exception then retrhow
                    Throwable cause = e.getCause();
                    if ((cause == null) || !(cause instanceof Exception)) {
                        // rethrow the execution exception
                        throw e;
                    }
                    // cast to an Exception and rethrow
                    throw ((Exception) cause);
                }

            } catch (SzBadInputException e) {
                logFailedRecord(ERROR, e, record.lineNumber, record.line);
                errorCount++;   // increment the error count

            } catch (SzRetryableException|InterruptedException|CancellationException e) {
                // handle thread interruption and cancellation as retries
                logFailedRecord(WARNING, e, record.lineNumber, record.line);
                errorCount++;   // increment the error count
                retryCount++;   // increment the retry count

                // track the retry record so it can be retried later
                if (retryFile == null) {
                    retryFile = File.createTempFile(RETRY_PREFIX, RETRY_SUFFIX);
                    retryWriter = new PrintWriter(
                        new OutputStreamWriter(new FileOutputStream(retryFile), UTF_8));
                }
                retryWriter.println(record.line);

            } catch (Exception e) {
                // catch any other exception (incl. SzException) here
                logFailedRecord(CRITICAL, e, record.lineNumber, record.line);
                errorCount++;
                throw e; // rethrow since exception is critical
            }
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

}