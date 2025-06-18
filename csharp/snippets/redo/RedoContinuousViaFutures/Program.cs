using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Reflection;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using Senzing.Sdk;
using Senzing.Sdk.Core;
using Senzing.Snippets.Support; // supporting classes for this example

using static Senzing.Sdk.SzFlags;

#pragma warning disable CA1303 // Do not pass literals as localized parameters (example messages)

// get the senzing repository settings
string? settings = Environment.GetEnvironmentVariable("SENZING_ENGINE_CONFIGURATION_JSON");
if (settings == null)
{
  Console.Error.WriteLine("Unable to get settings.");
  throw new ArgumentException("Unable to get settings");
}

// create a descriptive instance name (can be anything)
Assembly assembly = Assembly.GetExecutingAssembly();
string? instanceName = assembly.GetName().Name;

// initialize the Senzing environment
SzEnvironment env = SzCoreEnvironment.NewBuilder()
    .Settings(settings)
    .InstanceName(instanceName)
    .VerboseLogging(false)
    .Build();

// Create a TaskSchedular using an implementation that restricts
// execution to a specific limited pool of threads.  In order to
// improve performance and conserve memory we want to use the same
// threads for Senzing work.  The TaskScheduler implementation used
// here is directly pulled from Microsoft's TaskScheduler documentation
TaskScheduler taskScheduler
    = new LimitedConcurrencyLevelTaskScheduler(ThreadCount);

// create a TaskFactory and pass it our custom scheduler
TaskFactory factory = new TaskFactory(taskScheduler);

// keep track of the pending tasks and don't backlog too many for memory's sake
IList<(Task, string)> pendingFutures = new List<(Task, string)>(MaximumBacklog);

AppDomain.CurrentDomain.ProcessExit += (s, e) =>
{
#pragma warning disable CA1031 // Need to catch all exceptions here
  try
  {
    HandlePendingFutures(pendingFutures, true);
  }
  catch (Exception exception)
  {
    Console.Error.WriteLine(exception);
  }
#pragma warning restore CA1031 // Need to catch all exceptions here

  // IMPORTANT: make sure to destroy the environment
  env.Destroy();
  OutputRedoStatistics();
};

try
{
  // get the engine from the environment
  SzEngine engine = env.GetEngine();

  while (true)
  {
    // loop through the example records and queue them up so long
    // as we have more records and backlog is not too large
    while (pendingFutures.Count < MaximumBacklog)
    {

      // get the next redo record
      string redo = engine.GetRedoRecord();

      // check if no redo records are available
      if (redo == null) break;

      Task task = factory.StartNew(() =>
          {
            engine.ProcessRedoRecord(redo, SzNoFlags);
          },
          CancellationToken.None,
          TaskCreationOptions.None,
          taskScheduler);

      // add the future to the pending future list
      pendingFutures.Add((task, redo));
    }

    do
    {
      // handle any pending futures WITHOUT blocking to reduce the backlog
      HandlePendingFutures(pendingFutures, false);

      // if we still have exceeded the backlog size then pause
      // briefly before trying again
      if (pendingFutures.Count >= MaximumBacklog)
      {
        try
        {
          Thread.Sleep(HandlePauseTimeout);

        }
        catch (ThreadInterruptedException)
        {
          // do nothing
        }
      }
    } while (pendingFutures.Count >= MaximumBacklog);

    // check if there are no redo records right now
    if (engine.CountRedoRecords() == 0)
    {
      OutputRedoStatistics();
      Console.WriteLine();
      Console.WriteLine(
          "No redo records to process.  Pausing for "
          + RedoPauseDescription + "....");
      Console.WriteLine("Press CTRL-C to exit.");
      try
      {
        Thread.Sleep(RedoPauseTimeout);
      }
      catch (ThreadInterruptedException)
      {
        // ignore the exception
      }
      continue;
    }
  }

}
catch (Exception e)
{
  Console.Error.WriteLine();
  Console.Error.WriteLine("*** Terminated due to critical error ***");
  Console.Error.WriteLine(e);
  Console.Error.Flush();
  throw;

}
finally
{
  // normally we would call env.destroy() here, but we have registered
  // a shutdown hook to do that since termination will typically occur
  // via CTRL-C being pressed, and the shutdown hook will still run if
  // we get an exception
}

static void HandlePendingFutures(IList<(Task, string)> pendingFutures, bool blocking)
{
  // loop through the pending futures
  for (int index = 0; index < pendingFutures.Count; index++)
  {
    // get the next pending future
    (Task task, string redoRecord) = pendingFutures[index];

    // if not blocking and this one is not done then continue
    if (!blocking && !task.IsCompleted) continue;

    // remove the pending future from the list
    pendingFutures.RemoveAt(index--);

    try
    {
      try
      {
        // wait for completion -- if non-blocking then this
        // task is already completed and this will just 
        // throw any exception that might have occurred
        if (blocking && !task.IsCompleted)
        {
          task.Wait();
        }

        // if we get here then increment the success count
        redoneCount++;

      }
      catch (AggregateException e)
          when (e.InnerException is TaskCanceledException
                || e.InnerException is ThreadInterruptedException)
      {
        throw new SzRetryableException(e.InnerException);
      }
      catch (ThreadInterruptedException e)
      {
        throw new SzRetryableException(e.InnerException);
      }
      catch (AggregateException e)
      {
        if (e.InnerException != null)
        {
          // get the inner exception
          throw e.InnerException;
        }
        else
        {
          throw;
        }
      }

    }
    catch (SzRetryableException e)
    {
      // handle thread interruption and cancellation as retries
      LogFailedRedo(Warning, e, redoRecord);
      errorCount++;   // increment the error count
      retryCount++;   // increment the retry count

      // track the retry record so it can be retried later
      TrackRetryRecord(redoRecord);
    }
    catch (Exception e)
    {
      // catch any other exception (incl. SzException) here
      LogFailedRedo(Critical, e, redoRecord);
      errorCount++;
      throw; // rethrow since exception is critical
    }
  }
}

/// <summary>
/// Tracks the specified JSOn record definition to be retried 
/// in a retry file.
/// </summary>
/// <param name="recordJson">
/// The JSON text defining the record to be retried
/// </param>
static void TrackRetryRecord(string recordJson)
{
  // track the retry record so it can be retried later
  if (retryFile == null)
  {
    retryFile = new FileInfo(
        Path.Combine(
            Path.GetTempPath(),
            RetryPrefix + Path.GetRandomFileName() + RetrySuffix));

    retryWriter = new StreamWriter(
        new FileStream(retryFile.FullName,
                        FileMode.Open,
                        FileAccess.Write),
        Encoding.UTF8);
  }
  if (retryWriter != null)
  {
    retryWriter.WriteLine(recordJson);
  }
}

/// <summary>
/// Example method for logging failed records.
/// </summary>
///
/// <param name=""errorType">The error type description.</param>
/// <param name="exception">The exception itself.</param>
/// <param name="redoRecord">The JSON text for the failed record.</param>
static void LogFailedRedo(string errorType,
                          Exception exception,
                          string redoRecord)
{
  Console.Error.WriteLine();
  Console.Error.WriteLine("** " + errorType + " ** FAILED TO PROCESS REDO: ");
  Console.Error.WriteLine(redoRecord);
  Console.Error.WriteLine(exception);
  Console.Error.Flush();
}

static void OutputRedoStatistics()
{
  Console.WriteLine();
  Console.WriteLine("Redos successfully processed : " + redoneCount);
  Console.WriteLine("Total failed records/redos   : " + errorCount);

  // check on any retry records
  if (retryWriter != null)
  {
    retryWriter.Flush();
    retryWriter.Close();
  }
  if (retryCount > 0)
  {
    Console.WriteLine(
        retryCount + " records/redos to be retried in " + retryFile);
  }
  Console.Out.Flush();
}

public partial class Program
{
  private const string RedoPauseDescription = "30 seconds";

  private const int RedoPauseTimeout = 30000;

  private const string RetryPrefix = "retry-";
  private const string RetrySuffix = ".jsonl";
  private const string Warning = "WARNING";
  private const string Critical = "CRITICAL";

  // setup some class-wide variables
  private static int errorCount;
  private static int redoneCount;
  private static int retryCount;
  private static FileInfo? retryFile;
  private static StreamWriter? retryWriter;

  private const int ThreadCount = 8;

  private const int BacklogFactor = 10;

  private const int MaximumBacklog = ThreadCount * BacklogFactor;

  private const int HandlePauseTimeout = 100;
}

#pragma warning restore CA1303 // Do not pass literals as localized parameters (example messages)
