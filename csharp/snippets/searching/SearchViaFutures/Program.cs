using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Reflection;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;
using Senzing.Sdk;
using Senzing.Sdk.Core;
using Senzing.Snippets.Support; // supporting classes for this example

using static Senzing.Sdk.SzFlag;
using static Senzing.Sdk.SzFlags;

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

string filePath = (args.Length > 0) ? args[0] : DefaultFilePath;

// Create a TaskSchedular using an implementation that restricts
// execution to a specific limited pool of threads.  In order to
// improve performance and conserve memory we want to use the same
// threads for Senzing work.  The TaskScheduler implementation used
// here is directly pulled from Mirosoft's TaskScheduler documentation
TaskScheduler taskScheduler
    = new LimitedConcurrencyLevelTaskScheduler(ThreadCount);

// create a TaskFactory and pass it our custom scheduler
TaskFactory factory = new TaskFactory(taskScheduler);

// keep track of the pending tasks and don't backlog too many for memory's sake
List<(Task<string>, Criteria)> pendingFutures
    = new List<(Task<string>, Criteria)>(MaximumBacklog);

FileStream fs = new FileStream(filePath, FileMode.Open);

// create a reader
StreamReader rdr = new StreamReader(fs, Encoding.UTF8);
try
{
    // get the engine from the environment
    SzEngine engine = env.GetEngine();

    int lineNumber = 0;
    bool eof = false;

    while (!eof)
    {
        // loop through the example records and queue them up so long
        // as we have more records and backlog is not too large
        while (pendingFutures.Count < MaximumBacklog)
        {
            // read the next line
            string? line = rdr.ReadLine();
            lineNumber++;

            // check for EOF
            if (line == null)
            {
                eof = true;
                break;
            }

            // trim the line
            line = line.Trim();

            // skip any blank lines
            if (line.Length == 0) continue;

            // skip any commented lines
            if (line.StartsWith('#')) continue;

            // construct the Record instance
            Criteria criteria = new Criteria(lineNumber, line);

            try
            {
                Task<string> task = factory.StartNew(() =>
                    {
                        // call the addRecord() function with no flags
                        return engine.SearchByAttributes(
                            criteria.Line, SzSearchByAttributesDefaultFlags);
                    },
                    CancellationToken.None,
                    TaskCreationOptions.None,
                    taskScheduler);

                // add the future to the pending future list
                pendingFutures.Add((task, criteria));

            }
            catch (SzBadInputException e)
            {
                LogFailedSearch(Error, e, lineNumber, line);
                errorCount++;   // increment the error count          
            }
        }

        do
        {
            // handle any pending futures WITHOUT blocking to reduce the backlog
            HandlePendingFutures(pendingFutures, false);

            // if we still have exceeded the backlog size then pause
            // briefly before trying again
            if (pendingFutures.Count >= MaximumBacklog)
            {
                Thread.Sleep(PauseTimeout);
            }
        } while (pendingFutures.Count >= MaximumBacklog);
    }

    // after we have submitted all records we need to handle the remaining
    // pending futures so this time we block on each future
    HandlePendingFutures(pendingFutures, true);

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
    rdr.Close();
    fs.Close();

    // IMPORTANT: make sure to destroy the environment
    env.Destroy();

    Console.WriteLine();
    Console.WriteLine("Searches successfully completed   : " + successCount);
    Console.WriteLine("Total entities found via searches : " + foundEntities.Count);
    Console.WriteLine("Searches failed with errors       : " + errorCount);

    // check on any retry records
    if (retryWriter != null)
    {
        retryWriter.Flush();
        retryWriter.Close();
    }
    if (retryCount > 0)
    {
        Console.WriteLine(retryCount + " searches to be retried in " + retryFile);
    }
    Console.Out.Flush();
}

static void HandlePendingFutures(IList<(Task<string>, Criteria)> pendingFutures, bool blocking)
{
    // loop through the pending futures
    for (int index = 0; index < pendingFutures.Count; index++)
    {
        // get the next pending future
        (Task<string> task, Criteria criteria) = pendingFutures[index];

        // if not blocking and this one is not done then continue
        if (!blocking && !task.IsCompleted) continue;

        // remove the pending future from the list
        pendingFutures.RemoveAt(index--);

        try
        {
            try
            {
                // this will block if the task is not yet completed,
                // however we only get here with a pending task if
                // the blocking parameter is true
                string results = task.Result;

                // if we get here then increment the success count
                successCount++;

                // parse the search results
                JsonObject? jsonObj = JsonNode.Parse(results)?.AsObject();
                JsonArray? jsonArr = jsonObj?["RESOLVED_ENTITIES"]?.AsArray();
                if (jsonArr != null)
                {
                    for (int index2 = 0; index2 < jsonArr.Count; index2++)
                    {
                        JsonObject? obj = jsonArr[index2]?.AsObject();
                        obj = obj?["ENTITY"]?.AsObject();
                        obj = obj?["RESOLVED_ENTITY"]?.AsObject();
                        long? entityID = obj?["ENTITY_ID"]?.GetValue<long>();
                        if (entityID != null)
                        {
                            foundEntities.Add(entityID ?? 0L);
                        }
                    }
                }

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
        catch (SzBadInputException e)
        {
            LogFailedSearch(Error, e, criteria.LineNumber, criteria.Line);
            errorCount++;   // increment the error count

        }
        catch (SzRetryableException e)
        {
            // handle thread interruption and cancellation as retries
            LogFailedSearch(Warning, e, criteria.LineNumber, criteria.Line);
            errorCount++;   // increment the error count
            retryCount++;   // increment the retry count

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
                retryWriter.WriteLine(criteria.Line);
            }

        }
        catch (Exception e)
        {
            // catch any other exception (incl. SzException) here
            LogFailedSearch(Critical, e, criteria.LineNumber, criteria.Line);
            errorCount++;
            throw; // rethrow since exception is critical
        }
    }
}

/// <summary>
/// Example method for logging failed records.
/// </summary>
/// 
/// <param name="errorType">The error type description.</param>
/// <param name="exception">The exception itself.</param>
/// <param name="lineNumber">
/// The line number of the failed record in the JSON input file.
/// </param>
/// <param name="recordJson">The JSON text for the failed criteria.</param>
static void LogFailedSearch(string errorType,
                            Exception exception,
                            int lineNumber,
                            string criteriaJson)
{
    Console.Error.WriteLine();
    Console.Error.WriteLine(
        "** " + errorType + " ** FAILED TO SEARCH CRITERIA AT LINE "
        + lineNumber + ": ");
    Console.Error.WriteLine(criteriaJson);
    Console.Error.WriteLine(exception);
    Console.Error.Flush();
}

public partial class Program
{
    private const string DefaultFilePath = "../../resources/data/search-5K.jsonl";

    private const string RetryPrefix = "retry-";

    private const string RetrySuffix = ".jsonl";

    private const int ThreadCount = 8;

    private const int BacklogFactor = 10;

    private const int MaximumBacklog = ThreadCount * BacklogFactor;

    private const int PauseTimeout = 100;

    private const string Error = "ERROR";

    private const string Warning = "WARNING";

    private const string Critical = "CRITICAL";

    private static int errorCount;
    private static int successCount;
    private static int retryCount;
    private static FileInfo? retryFile;
    private static StreamWriter? retryWriter;

    private static readonly HashSet<long> foundEntities = new HashSet<long>();

}
internal sealed record Criteria(int LineNumber, string Line) { }

