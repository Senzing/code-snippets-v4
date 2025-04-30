using System;
using System.Collections.Generic;
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

FileStream fs = new FileStream(filePath, FileMode.Open, FileAccess.Read, FileShare.Read);

// create a reader
StreamReader rdr = new StreamReader(fs, Encoding.UTF8);
try
{
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
        if (line.StartsWith('#')) continue;

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

            successCount++;

            // check if it is time obtain stats
            if ((successCount % StatsInterval) == 0)
            {
                try
                {
                    string stats = engine.GetStats();
                    if (stats.Length > StatsTruncate)
                    {
                        stats = string.Concat(stats.AsSpan(0, StatsTruncate), " ...");
                    }
                    Console.WriteLine("* STATS: " + stats);

                }
                catch (SzException e)
                {
                    // trap the stats exeption so it is not misinterpreted
                    // as an exception from engine.addRecord()
                    Console.WriteLine("**** FAILED TO OBTAIN STATS: " + e);
                }
            }

        }
        catch (SzBadInputException e)
        {
            LogFailedRecord(Error, e, lineNumber, line);
            errorCount++;   // increment the error count

        }
        catch (SzRetryableException e)
        {
            LogFailedRecord(Warning, e, lineNumber, line);
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
        catch (Exception e)
        {
            // catch any other exception (incl. SzException) here
            LogFailedRecord(Critical, e, lineNumber, line);
            errorCount++;
            throw; // rethrow since exception is critical
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
    rdr.Close();

    fs.Close();

    // IMPORTANT: make sure to destroy the environment
    env.Destroy();

    Console.WriteLine();
    Console.WriteLine("Records successfully added : " + successCount);
    Console.WriteLine("Records failed with errors : " + errorCount);

    // check on any retry records
    if (retryWriter != null)
    {
        retryWriter.Flush();
        retryWriter.Close();
    }
    if (retryCount > 0)
    {
        Console.WriteLine(retryCount + " records to be retried in " + retryFile);
    }
    Console.Out.Flush();
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

    private const string Error = "ERROR";

    private const string Warning = "WARNING";

    private const string Critical = "CRITICAL";

    private const int StatsInterval = 100;

    private const int StatsTruncate = 70;

    private static int errorCount;
    private static int successCount;
    private static int retryCount;
    private static FileInfo? retryFile;
    private static StreamWriter? retryWriter;
}