package loading;

import java.io.*;
import java.util.concurrent.*;

import javax.json.*;
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;
import static java.lang.Thread.State.*;

/**
 * Provides a simple example of adding records to the Senzing repository.
 */
public class LoadViaQueue {    
    public static void main(String[] args) {
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        // create a descriptive instance name (can be anything)
        String instanceName = LoadViaQueue.class.getSimpleName();

        // initialize the Senzing environment
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(instanceName)
            .verboseLogging(false)
            .build();
        
        String filePath = (args.length > 0) ? args[0] : DEFAULT_FILE_PATH;

        Thread producer = new Thread(() -> {
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

                    // add the record to the queue
                    recordQueue.put(new Record(lineNumber, line));
                }

            } catch (Exception e) {
                producerFailure = e;
            }
        });

        // start the producer
        producer.start();

        Thread consumer = new Thread(() -> {
            try {
                // get the engine from the environment
                SzEngine engine = env.getEngine();
    
                // loop while producer has not failed and is either still running
                // or there are remaining records
                while (producerFailure == null 
                       && (!isTerminated(producer) || recordQueue.size() > 0)) 
                {
                    Record record = recordQueue.poll(POLL_TIMEOUT, POLL_TIME_UNIT);

                    // check if we timed out getting the next record
                    if (record == null) {
                        // continue the loop to check if we are done
                        continue;
                    }

                    // get the line number and line from the record
                    int     lineNumber  = record.lineNumber;
                    String  line        = record.line;

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
    
                        synchronized (MONITOR) {
                            successCount++;
                        }
    
                    } catch (JsonException|SzBadInputException e) {
                        logFailedRecord(ERROR, e, lineNumber, line);
                        synchronized (MONITOR) {
                            errorCount++;   // increment the error count
                        }
    
                    } catch (SzRetryableException e) {
                        logFailedRecord(WARNING, e, lineNumber, line);
                        synchronized (MONITOR) {
                            errorCount++;   // increment the error count
                            retryCount++;   // increment the retry count
    
                            // track the retry record so it can be retried later
                            if (retryFile == null) {
                                retryFile = File.createTempFile(RETRY_PREFIX, RETRY_SUFFIX);
                                retryWriter = new PrintWriter(
                                    new OutputStreamWriter(
                                        new FileOutputStream(retryFile), UTF_8));
                            }
                            retryWriter.println(line);
                        }
    
                    } catch (Exception e) {
                        // catch any other exception (incl. SzException) here
                        logFailedRecord(CRITICAL, e, lineNumber, line);
                        synchronized (MONITOR) {
                            errorCount++; // increment the error count
                        }
                        throw e; // rethrow since exception is critical
                    }
                }

            } catch (Exception e) {
                consumerFailure = e;
            }
        });

        // start the consumer
        consumer.start();

        // join the threads
        while (!isTerminated(producer)) {
            try {
                producer.join();
            } catch (InterruptedException ignore) {
                ignore.printStackTrace();
           }
        }
        while (!isTerminated(consumer)) {
            try {
                consumer.join();
            } catch (InterruptedException ignore) {
                ignore.printStackTrace();
            }
        }
        
        try {
            // check for producer and consumer failures
            if (producerFailure != null) {
                throw producerFailure;
            }
            if (consumerFailure != null) {
                throw consumerFailure;
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

            synchronized (MONITOR) {
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

    }

    private static boolean isTerminated(Thread thread) {
        synchronized (thread) {
            return (thread.getState() == TERMINATED);
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

    private static final int MAXIMUM_BACKLOG = 100;
    private static final long POLL_TIMEOUT = 3000L;
    private static final TimeUnit POLL_TIME_UNIT = TimeUnit.MILLISECONDS;

    private static final String UTF_8 = "UTF-8";

    private static final String RETRY_PREFIX = "retry-";
    private static final String RETRY_SUFFIX = ".jsonl";

    private static final String DATA_SOURCE = "DATA_SOURCE";
    private static final String RECORD_ID   = "RECORD_ID";

    private static final String ERROR       = "ERROR";
    private static final String WARNING     = "WARNING";
    private static final String CRITICAL    = "CRITICAL";

    private static final Object MONITOR = new Object();

    private static int         errorCount      = 0;
    private static int         successCount    = 0;
    private static int         retryCount      = 0;
    private static File        retryFile       = null;
    private static PrintWriter retryWriter     = null;

    public record Record(int lineNumber, String line) { }

    private static final BlockingQueue<Record> recordQueue
        = new LinkedBlockingQueue<>(MAXIMUM_BACKLOG);

    private static volatile Exception producerFailure = null;
    private static volatile Exception consumerFailure = null;
}