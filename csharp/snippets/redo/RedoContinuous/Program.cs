using System;
using System.Collections.Generic;
using System.Reflection;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using Senzing.Sdk;
using Senzing.Sdk.Core;

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

AppDomain.CurrentDomain.ProcessExit += (s, e) =>
{
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
        // get the next redo record
        string redo = engine.GetRedoRecord();

        // check if no redo records are available
        if (redo == null)
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

        try
        {
            // process the redo record
            engine.ProcessRedoRecord(redo, SzNoFlags);

            // increment the redone count
            redoneCount++;

        }
        catch (SzRetryableException e)
        {
            LogFailedRedo(Warning, e, redo);
            errorCount++;
            retryCount++;
            TrackRetryRecord(redo);

        }
        catch (Exception e)
        {
            LogFailedRedo(Critical, e, redo);
            errorCount++;
            throw;
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
}

#pragma warning restore CA1303 // Do not pass literals as localized parameters (example messages)
