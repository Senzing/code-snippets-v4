using System;
using System.Collections.Generic;
using System.Collections.Concurrent;
using System.Reflection;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using Senzing.Sdk;
using Senzing.Sdk.Core;

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

FileStream fs = new FileStream(filePath, FileMode.Open);
Thread producer = new Thread(() =>
{
    FileStream fs = new FileStream(filePath, FileMode.Open);
    try
    {
        StreamReader rdr = new StreamReader(fs, Encoding.UTF8);

        // get the engine from the environment
        SzEngine engine = env.GetEngine();

        int lineNumber = 0;

        // loop through the example records and add them to the repository
        for (string? line = rdr.ReadLine(); line != null; line = rdr.ReadLine())
        {
            // increment the line number
            lineNumber++;

            // trim the line
            line = line.Trim();

            // skip any blank lines
            if (line.Length == 0) continue;

            // skip any commented lines
            if (line.StartsWith("#")) continue;

            // add the record to the queue
            recordQueue.Add(new Record(lineNumber, line));
        }

    }
    catch (Exception e)
    {
        producerFailure = e;
    }
    finally
    {
        recordQueue.CompleteAdding();
    }
});

// start the producer
producer.Start();

Thread consumer = new Thread(() =>
{
    try
    {
        // get the engine from the environment
        SzEngine engine = env.GetEngine();

        // loop while producer has not failed and is either still running
        // or there are remaining records
        while (!recordQueue.IsCompleted)
        {
            bool obtained = recordQueue.TryTake(out Record? record, PollTimeout);

            // check if we timed out getting the next record
            if (!obtained || record == null)
            {
                // continue the loop to check if we are done
                continue;
            }

            // get the line number and line from the record
            int lineNumber = record.LineNumber;
            string line = record.Line;

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

                // call the addRecord() function with no flags
                engine.AddRecord(dataSourceCode, recordID, line, SzNoFlags);

                lock (Monitor)
                {
                    successCount++;
                }

            }
            catch (SzBadInputException e)
            {
                LogFailedRecord(Error, e, lineNumber, line);
                lock (Monitor)
                {
                    errorCount++;   // increment the error count
                }

            }
            catch (SzRetryableException e)
            {
                LogFailedRecord(Warning, e, lineNumber, line);
                lock (Monitor)
                {
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
                        retryWriter.WriteLine(line);
                    }
                }

            }
            catch (Exception e)
            {
                // catch any other exception (incl. SzException) here
                LogFailedRecord(Critical, e, lineNumber, line);
                lock (Monitor)
                {
                    errorCount++; // increment the error count
                }
                throw; // rethrow since exception is critical
            }
        }

    }
    catch (Exception e)
    {
        consumerFailure = e;
    }
});

// start the consumer
consumer.Start();

// join the threads
while (!IsStopped(producer))
{
    try
    {
        producer.Join();
    }
    catch (ThreadInterruptedException ignore)
    {
        Console.Error.WriteLine(ignore);
    }
}
while (!IsStopped(consumer))
{
    try
    {
        consumer.Join();
    }
    catch (ThreadInterruptedException ignore)
    {
        Console.Error.WriteLine(ignore);
    }
}

try
{
    // check for producer and consumer failures
    if (producerFailure != null)
    {
        throw producerFailure;
    }
    if (consumerFailure != null)
    {
        throw consumerFailure;
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
    // IMPORTANT: make sure to destroy the environment
    env.Destroy();

    lock (Monitor)
    {
        Console.Out.WriteLine();
        Console.Out.WriteLine("Records successfully added : " + successCount);
        Console.Out.WriteLine("Records failed with errors : " + errorCount);

        // check on any retry records
        if (retryWriter != null)
        {
            retryWriter.Flush();
            retryWriter.Close();
        }
        if (retryCount > 0)
        {
            Console.Out.WriteLine(retryCount + " records to be retried in " + retryFile);
        }
        Console.Out.Flush();
    }

}

static bool IsStopped(Thread thread)
{
    lock (thread)
    {
        return (thread.ThreadState == ThreadState.Stopped);
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
        "** " + errorType + " ** FAILED TO ADD RECORD AT LINE "
        + lineNumber + ": ");
    Console.Error.WriteLine(recordJson);
    Console.Error.WriteLine(exception);
    Console.Error.Flush();
}

public partial class Program
{
    private const string DefaultFilePath = "../../resources/data/load-500.jsonl";

    private const string RetryPrefix = "retry-";

    private const string RetrySuffix = ".jsonl";

    private const string DataSource = "DATA_SOURCE";

    private const string RecordID = "RECORD_ID";

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

    private const int MaximumBacklog = 100;

    private const int PollTimeout = 3000;

    private static readonly object Monitor = new object();

    private static readonly BlockingCollection<Record> recordQueue
        = new BlockingCollection<Record>(MaximumBacklog);

    private static volatile Exception? producerFailure = null;
    private static volatile Exception? consumerFailure = null;
}

