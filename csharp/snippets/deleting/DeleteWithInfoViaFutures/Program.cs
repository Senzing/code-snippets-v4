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
IList<(Task<string>, Record)> pendingFutures
    = new List<(Task<string>, Record)>(MaximumBacklog);

FileStream fs = new FileStream(filePath, FileMode.Open);
try
{
    // create a reader
    StreamReader rdr = new StreamReader(fs, Encoding.UTF8);

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
            if (line.StartsWith("#")) continue;

            // construct the Record instance
            Record record = new Record(lineNumber, line);

            try
            {
                // parse the line as a JSON object
                JsonObject? recordJson = JsonNode.Parse(line)?.AsObject();
                if (recordJson == null)
                {
                    // parsed JSON null
                    throw new SzBadInputException("Record must be a JSON object: " + line);
                }

                // extract the data source code and record ID
                string? dataSourceCode = recordJson[DataSource]?.GetValue<string>();
                string? recordID = recordJson[RecordID]?.GetValue<string>();

                Task<string> task = factory.StartNew(() =>
                {
                    // call the DeleteRecord() function with info flags
                    return engine.DeleteRecord(dataSourceCode, recordID, SzWithInfo);
                });

                // add the future to the pending future list
                pendingFutures.Add((task, record));

            }
            catch (SzBadInputException e)
            {
                LogFailedRecord(Error, e, lineNumber, line);
                errorCount++;   // increment the error count          
            }
        }

        do
        {
            // handle any pending futures WITHOUT blocking to reduce the backlog
            HandlePendingFutures(engine, pendingFutures, false);

            // if we still have exceeded the backlog size then pause
            // briefly before trying again
            if (pendingFutures.Count >= MaximumBacklog)
            {
                try
                {
                    Thread.Sleep(PauseTimeout);

                }
                catch (Exception)
                {
                    // do nothing
                }
            }
        } while (pendingFutures.Count >= MaximumBacklog);
    }

    // after we have submitted all records we need to handle the remaining
    // pending futures so this time we block on each future
    HandlePendingFutures(engine, pendingFutures, true);

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
    fs.Close();
    
    // IMPORTANT: make sure to destroy the environment
    env.Destroy();

    Console.WriteLine();
    Console.WriteLine("Successful delete operations : " + successCount);
    Console.WriteLine("Total entities deleted       : " + entityIDSet.Count);
    Console.WriteLine("Failed delete operations     : " + errorCount);

    // check on any retry records
    if (retryWriter != null)
    {
        retryWriter.Flush();
        retryWriter.Close();
    }
    if (retryCount > 0)
    {
        Console.WriteLine(retryCount + " deletions to be retried in " + retryFile);
    }
    Console.Out.Flush();

}

static void HandlePendingFutures(SzEngine engine,
                                 IList<(Task<string>, Record)> pendingFutures,
                                 bool blocking)
{
    // loop through the pending futures
    for (int index = 0; index < pendingFutures.Count; index++)
    {
        // get the next pending future
        (Task<string> task, Record record) = pendingFutures[index];

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
                string info = task.Result;

                // if we get here then increment the success count
                successCount++;

                // process the info
                ProcessInfo(engine, info);

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
            LogFailedRecord(Error, e, record.LineNumber, record.Line);
            errorCount++;   // increment the error count

        }
        catch (SzRetryableException e)
        {
            // handle thread interruption and cancellation as retries
            LogFailedRecord(Warning, e, record.LineNumber, record.Line);
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
                retryWriter.WriteLine(record.Line);
            }

        }
        catch (Exception e)
        {
            // catch any other exception (incl. SzException) here
            LogFailedRecord(Critical, e, record.LineNumber, record.Line);
            errorCount++;
            throw; // rethrow since exception is critical
        }
    }
}

/// <summary>
/// Example method for parsing and handling the INFO message (formatted
/// as JSON)
/// </summary>
/// 
/// <remarks>
/// This example implementation simply tracks all entity ID's that appear
/// as <c>"AFFECTED_ENTITIES"<c> to count the number of entities deleted
/// for the records -- essentially a contrived data mart.
/// </remarks>
/// 
/// <param name="engine">The <see cref="SzEngine"/> to use.</param>
/// <param name="info">The info message</param> 
static void ProcessInfo(SzEngine engine, string info)
{
    JsonObject? jsonObject = JsonNode.Parse(info)?.AsObject();
    if (jsonObject == null) return;
    if (!jsonObject.ContainsKey(AffectedEntities)) return;

    JsonArray? affectedArr = jsonObject[AffectedEntities]?.AsArray();
    if (affectedArr == null) return;

    for (int index = 0; index < affectedArr.Count; index++)
    {
        JsonObject? affected = affectedArr[index]?.AsObject();
        long entityID = affected?[EntityID]?.GetValue<long>() ?? 0L;
        if (entityID == 0L) continue;

        try
        {
            engine.GetEntity(entityID, null);
            entityIDSet.Remove(entityID);
        }
        catch (SzNotFoundException)
        {
            entityIDSet.Add(entityID);
        }
        catch (SzException e)
        {
            // simply log the exception, do not rethrow
            Console.Error.WriteLine();
            Console.Error.WriteLine("**** FAILED TO RETRIEVE ENTITY: " + entityID);
            Console.Error.WriteLine(e);
            Console.Error.Flush();
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
/// <param name="recordJson">The JSON text for the failed record.</param>
static void LogFailedRecord(string errorType,
                            Exception exception,
                            int lineNumber,
                            string recordJson)
{
    Console.Error.WriteLine();
    Console.Error.WriteLine(
        "** " + errorType + " ** FAILED TO DELETE RECORD AT LINE "
        + lineNumber + ": ");
    Console.Error.WriteLine(recordJson);
    Console.Error.WriteLine(exception);
    Console.Error.Flush();
}

public partial class Program
{
    private const string DefaultFilePath = "../../resources/data/del-500.jsonl";

    private const string RetryPrefix = "retry-";

    private const string RetrySuffix = ".jsonl";

    private const string DataSource = "DATA_SOURCE";

    private const string RecordID = "RECORD_ID";

    private const string AffectedEntities = "AFFECTED_ENTITIES";

    private const string EntityID = "ENTITY_ID";

    private const int ThreadCount = 8;

    private const int BacklogFactor = 10;

    private const int MaximumBacklog = ThreadCount * BacklogFactor;

    private const int PauseTimeout = 100;

    private const string Error = "ERROR";

    private const string Warning = "WARNING";

    private const string Critical = "CRITICAL";

    public record Record(int LineNumber, String Line) { }

    private static int errorCount = 0;

    private static int successCount = 0;

    private static int retryCount = 0;

    private static FileInfo? retryFile = null;

    private static StreamWriter? retryWriter = null;

    private static readonly ISet<long> entityIDSet = new HashSet<long>();
}