package searching;

import java.io.*;
import javax.json.*;
import java.util.*;
import java.util.concurrent.*;
import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

/**
 * Provides a simple example of searching for entities in the Senzing repository
 * using futures.
 */
public class SearchViaFutures {
    public static void main(String[] args) {
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        // create a descriptive instance name (can be anything)
        String instanceName = SearchViaFutures.class.getSimpleName();

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
        Map<Future<String>, Criteria> pendingFutures = new IdentityHashMap<>();

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
                    if (line.length() == 0) {
                        continue;
                    }

                    // skip any commented lines
                    if (line.startsWith("#")) {
                        continue;
                    }

                    // construct the Record instance
                    Criteria criteria = new Criteria(lineNumber, line);

                    try {
                        Future<String> future = executor.submit(() -> {
                            // call the searchByAttributes() function with default flags
                            return engine.searchByAttributes(
                                criteria.line, SZ_SEARCH_BY_ATTRIBUTES_DEFAULT_FLAGS);
                        });

                        // add the future to the pending future list
                        pendingFutures.put(future, criteria);

                    } catch (JsonException e) {
                        logFailedSearch(ERROR, e, lineNumber, line);
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
            System.out.println(
                "Searches successfully completed   : " + successCount);
            System.out.println(
                "Total entities found via searches : " + foundEntities.size());
            System.out.println(
                "Searches failed with errors       : " + errorCount);

            // check on any retry records
            if (retryWriter != null) {
                retryWriter.flush();
                retryWriter.close();
            }
            if (retryCount > 0) {
                System.out.println(retryCount + " searches to be retried in " + retryFile);
            }
            System.out.flush();

        }

    }

    private static void handlePendingFutures(Map<Future<String>, Criteria>  pendingFutures,
                                             boolean                        blocking)
        throws Exception
    {
        // check for completed futures
        Iterator<Map.Entry<Future<String>, Criteria>> iter
            = pendingFutures.entrySet().iterator();
        
        // loop through the pending futures
        while (iter.hasNext()) {
            // get the next pending future
            Map.Entry<Future<String>, Criteria> entry = iter.next();
            Future<String>  future      = entry.getKey();
            Criteria        criteria    = entry.getValue();
            
            // if not blocking and this one is not done then continue
            if (!blocking && !future.isDone()) {
                continue;
            }

            // remove the pending future from the map
            iter.remove();

            try {
                try {
                    // get the value and check for an exception
                    String results = future.get();

                    // if we get here then increment the success count
                    successCount++;

                    // parse the results
                    JsonObject jsonObj = Json.createReader(
                        new StringReader(results)).readObject();
                    
                    JsonArray jsonArr = jsonObj.getJsonArray("RESOLVED_ENTITIES");
                    for (JsonObject obj : jsonArr.getValuesAs(JsonObject.class)) {
                        obj = obj.getJsonObject("ENTITY");
                        obj = obj.getJsonObject("RESOLVED_ENTITY");
                        long entityId = obj.getJsonNumber("ENTITY_ID").longValue();
                        foundEntities.add(entityId);
                    }
    

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
                logFailedSearch(ERROR, e, criteria.lineNumber, criteria.line);
                errorCount++;   // increment the error count

            } catch (SzRetryableException | InterruptedException | CancellationException e) {
                // handle thread interruption and cancellation as retries
                logFailedSearch(WARNING, e, criteria.lineNumber, criteria.line);
                errorCount++;   // increment the error count
                retryCount++;   // increment the retry count

                // track the retry record so it can be retried later
                if (retryFile == null) {
                    retryFile = File.createTempFile(RETRY_PREFIX, RETRY_SUFFIX);
                    retryWriter = new PrintWriter(
                        new OutputStreamWriter(new FileOutputStream(retryFile), UTF_8));
                }
                retryWriter.println(criteria.line);

            } catch (Exception e) {
                // catch any other exception (incl. SzException) here
                logFailedSearch(CRITICAL, e, criteria.lineNumber, criteria.line);
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
     * @param criteriaJson The JSON text for the failed search criteria.
     */
    private static void logFailedSearch(String      errorType,
                                        Exception   exception, 
                                        int         lineNumber, 
                                        String      criteriaJson) 
    {
        System.err.println();
        System.err.println(
            "** " + errorType + " ** FAILED TO SEARCH CRITERIA AT LINE " + lineNumber + ": ");
        System.err.println(criteriaJson);
        System.err.println(exception);
        System.err.flush();
    }

    private static final String DEFAULT_FILE_PATH = "../resources/data/search-5K.jsonl";

    private static final String UTF_8 = "UTF-8";

    private static final String RETRY_PREFIX = "retry-";
    private static final String RETRY_SUFFIX = ".jsonl";
    
    private static final int THREAD_COUNT = 8;

    private static final int BACKLOG_FACTOR = 10;

    private static final int MAXIMUM_BACKLOG = THREAD_COUNT * BACKLOG_FACTOR;

    private static final long PAUSE_TIMEOUT = 100L;

    private static final String ERROR       = "ERROR";
    private static final String WARNING     = "WARNING";
    private static final String CRITICAL    = "CRITICAL";
    
    public record Criteria(int lineNumber, String line) { }

    private static int         errorCount      = 0;
    private static int         successCount    = 0;
    private static int         retryCount      = 0;
    private static File        retryFile       = null;
    private static PrintWriter retryWriter     = null;

    private static Set<Long> foundEntities = new HashSet<>();
}
