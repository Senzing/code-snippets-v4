package redo;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

/**
 * Provides an example of a process that continuously processes
 * the pending redo records in the Senzing repository using
 * futures.
 */
public class RedoContinuousViaFutures {
  public static void main(String[] args) {
    // get the senzing repository settings
    String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
    if (settings == null) {
      System.err.println("Unable to get settings.");
      throw new IllegalArgumentException("Unable to get settings");
    }

    // create a descriptive instance name (can be anything)
    String instanceName = RedoContinuousViaFutures.class.getSimpleName();

    // initialize the Senzing environment
    SzEnvironment env = SzCoreEnvironment.newBuilder()
        .settings(settings)
        .instanceName(instanceName)
        .verboseLogging(false)
        .build();

    // create the thread pool and executor service
    ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

    // keep track of pending futures and don't backlog too many for memory's sake
    Map<Future<?>, String> pendingFutures = new IdentityHashMap<>();

    // make sure we cleanup if exiting by CTRL-C or due to an exception
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      // shutdown the executor service
      if (!executor.isShutdown()) {
        executor.shutdown();
      }

      try {
        handlePendingFutures(pendingFutures, true);
      } catch (Exception e) {
        e.printStackTrace();
      }

      // IMPORTANT: make sure to destroy the environment
      env.destroy();
      outputRedoStatistics();
    }));

    try {
      // get the engine from the environment
      SzEngine engine = env.getEngine();

      while (true) {
        // loop through the example records and queue them up so long
        // as we have more records and backlog is not too large
        while (pendingFutures.size() < MAXIMUM_BACKLOG) {

          // get the next redo record
          String redo = engine.getRedoRecord();

          // check if no redo records are available
          if (redo == null) {
            break;
          }

          Future<?> future = executor.submit(() -> {
            // process the redo record
            engine.processRedoRecord(redo, SZ_NO_FLAGS);

            // return null since we have no "info" to return
            return null;
          });

          // add the future to the pending future list
          pendingFutures.put(future, redo);
        }

        do {
          // handle any pending futures WITHOUT blocking to reduce the backlog
          handlePendingFutures(pendingFutures, false);

          // if we still have exceeded the backlog size then pause
          // briefly before trying again
          if (pendingFutures.size() >= MAXIMUM_BACKLOG) {
            try {
              Thread.sleep(HANDLE_PAUSE_TIMEOUT);

            } catch (InterruptedException ignore) {
              // do nothing
            }
          }
        } while (pendingFutures.size() >= MAXIMUM_BACKLOG);

        // check if there are no redo records right now
        // NOTE: we do NOT want to call countRedoRecords() in a loop that
        // is processing redo records, we call it here AFTER we believe
        // have processed all pending redos to confirm still zero
        if (engine.countRedoRecords() == 0) {
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

  private static void handlePendingFutures(Map<Future<?>, String> pendingFutures,
      boolean blocking)
      throws Exception {
    // check for completed futures
    Iterator<Map.Entry<Future<?>, String>> iter = pendingFutures.entrySet().iterator();

    // loop through the pending futures
    while (iter.hasNext()) {
      // get the next pending future
      Map.Entry<Future<?>, String> entry = iter.next();
      Future<?> future = entry.getKey();
      String redoRecord = entry.getValue();

      // if not blocking and this one is not done then continue
      if (!blocking && !future.isDone()) {
        continue;
      }

      // remove the pending future from the map
      iter.remove();

      try {
        try {
          // get the value to see if there was an exception
          future.get();

          // if we get here then increment the success count
          redoneCount++;

        } catch (InterruptedException e) {
          // this could only happen if blocking is true, just
          // rethrow as retryable and log the interruption
          throw e;

        } catch (ExecutionException e) {
          // if execution failed with an exception then rethrow
          Throwable cause = e.getCause();
          if ((cause == null) || !(cause instanceof Exception)) {
            // rethrow the execution exception
            throw e;
          }
          // cast to an Exception and rethrow
          throw ((Exception) cause);
        }

      } catch (SzRetryableException | InterruptedException | CancellationException e) {
        // handle thread interruption and cancellation as retries
        logFailedRedo(WARNING, e, redoRecord);
        errorCount++; // increment the error count
        retryCount++; // increment the retry count

        // track the retry record so it can be retried later
        trackRetryRecord(redoRecord);

      } catch (Exception e) {
        // catch any other exception (incl. SzException) here
        logFailedRedo(CRITICAL, e, redoRecord);
        errorCount++;
        throw e; // rethrow since exception is critical
      }
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

  private static final int THREAD_COUNT = 8;

  private static final int BACKLOG_FACTOR = 10;

  private static final int MAXIMUM_BACKLOG = THREAD_COUNT * BACKLOG_FACTOR;

  private static final long HANDLE_PAUSE_TIMEOUT = 100L;

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
