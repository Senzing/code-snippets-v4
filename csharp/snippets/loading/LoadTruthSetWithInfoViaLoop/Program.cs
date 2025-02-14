using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Reflection;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using Senzing.Sdk;
using Senzing.Sdk.Core;

using static Senzing.Sdk.SzFlag;

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

try
{
    // get the engine from the environment
    SzEngine engine = env.GetEngine();

    // loop through the input files
    foreach (string filePath in InputFiles)
    {
        FileStream fs = new FileStream(filePath, FileMode.Open, FileAccess.Read);

        try
        {
            StreamReader rdr = new StreamReader(fs, Encoding.UTF8);
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

                    // call the addRecord() function with info flags
                    string info = engine.AddRecord(
                        dataSourceCode, recordID, line, SzWithInfo);

                    successCount++;

                    // process the info
                    ProcessInfo(engine, info);

                }
                catch (SzBadInputException e)
                {
                    LogFailedRecord(Error, e, filePath, lineNumber, line);
                    errorCount++;   // increment the error count

                }
                catch (SzRetryableException e)
                {
                    LogFailedRecord(Warning, e, filePath, lineNumber, line);
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
                    LogFailedRecord(Critical, e, filePath, lineNumber, line);
                    errorCount++;
                    throw; // rethrow since exception is critical
                }
            }
        }
        finally
        {
            fs.Close();
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
    // IMPORTANT: make sure to destroy the environment
    env.Destroy();

    Console.WriteLine();
    Console.WriteLine("Records successfully added : " + successCount);
    Console.WriteLine("Total entities created     : " + entityIDSet.Count);
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
/// Example method for parsing and handling the INFO message (formatted
/// as JSON)
/// </summary>
/// 
/// <remarks>
/// This example implementation simply tracks all entity ID's that appear
/// as <c>"AFFECTED_ENTITIES"<c> to count the number of entities created
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
            entityIDSet.Add(entityID);

        }
        catch (SzNotFoundException)
        {
            entityIDSet.Remove(entityID);

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
/// <param name="filePath">The path to the file being loaded</param> 
/// <param name="lineNumber">
/// The line number of the failed record in the JSON input file.
/// </param>
/// <param name="recordJson">The JSON text for the failed record.</param>
static void LogFailedRecord(string errorType,
                            Exception exception,
                            string filePath,
                            int lineNumber,
                            string recordJson)
{
    string fileName = Path.GetFileName(filePath);

    Console.Error.WriteLine();
    Console.Error.WriteLine(
        "** " + errorType + " ** FAILED TO ADD RECORD IN " + fileName
        + " AT LINE " + lineNumber + ": ");
    Console.Error.WriteLine(recordJson);
    Console.Error.WriteLine(exception);
    Console.Error.Flush();
}

public partial class Program
{
    private static readonly IList<string> InputFiles = new ReadOnlyCollection<string>(
    new string[] {
        "../../resources/data/truthset/customers.jsonl",
        "../../resources/data/truthset/reference.jsonl",
        "../../resources/data/truthset/watchlist.jsonl"
    });

    private const string RetryPrefix = "retry-";
    private const string RetrySuffix = ".jsonl";
    private const string DataSource = "DATA_SOURCE";
    private const string RecordID = "RECORD_ID";
    private const string AffectedEntities = "AFFECTED_ENTITIES";
    private const string EntityID = "ENTITY_ID";
    private const string Error = "ERROR";
    private const string Warning = "WARNING";
    private const string Critical = "CRITICAL";

    // setup some class-wide variables
    private static int errorCount = 0;
    private static int successCount = 0;
    private static int retryCount = 0;
    private static FileInfo? retryFile = null;
    private static StreamWriter? retryWriter = null;
    private static readonly ISet<long> entityIDSet = new HashSet<long>();
}
