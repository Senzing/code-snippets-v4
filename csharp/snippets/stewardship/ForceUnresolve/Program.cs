using System;
using System.Collections.Generic;
using System.Reflection;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using Senzing.Sdk;
using Senzing.Sdk.Core;

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

try
{
    // get the engine from the environment
    SzEngine engine = env.GetEngine();

    IDictionary<(string, string), string> records = GetRecords();

    // loop through the example records and add them to the repository
    foreach (KeyValuePair<(string, string), string> pair in records)
    {
        (string dataSourceCode, string recordID) = pair.Key;
        string recordDefinition = pair.Value;

        // call the addRecord() function with no flags
        engine.AddRecord(dataSourceCode, recordID, recordDefinition, SzNoFlags);

        Console.WriteLine("Record " + recordID + " added");
        Console.Out.Flush();
    }

    Console.WriteLine();
    foreach ((string dataSourceCode, string recordID) in records.Keys)
    {
        string result = engine.GetEntity(
            dataSourceCode, recordID, SzEntityBriefDefaultFlags);

        JsonObject? jsonObj = JsonNode.Parse(result)?.AsObject();
        jsonObj = jsonObj?["RESOLVED_ENTITY"]?.AsObject();
        long? entityID = jsonObj?["ENTITY_ID"]?.GetValue<long>();

        Console.WriteLine(
            "Record " + dataSourceCode + ":" + recordID
            + " originally resolves to entity " + entityID);
    }
    Console.WriteLine();
    Console.WriteLine("Updating records with TRUSTED_ID to force resolve...");

    string record4 = engine.GetRecord(TEST, "4", SzRecordDefaultFlags);
    string record6 = engine.GetRecord(TEST, "6", SzRecordDefaultFlags);

    JsonObject? obj4 = JsonNode.Parse(record4)?.AsObject();
    JsonObject? obj6 = JsonNode.Parse(record6)?.AsObject();

    obj4 = obj4?["JSON_DATA"]?.AsObject();
    obj6 = obj6?["JSON_DATA"]?.AsObject();

    if (obj4 == null || obj6 == null)
    {
        throw new Exception("The JSON_DATA parses as null: "
            + record4 + " / " + record6);
    }

    obj4["TRUSTED_ID_NUMBER"] = JsonNode.Parse("\"TEST_R4-TEST_R6\"");
    obj4["TRUSTED_ID_TYPE"] = JsonNode.Parse("\"FORCE_UNRESOLVE\"");

    obj6["TRUSTED_ID_NUMBER"] = JsonNode.Parse("\"TEST_R6-TEST_R4\"");
    obj6["TRUSTED_ID_TYPE"] = JsonNode.Parse("\"FORCE_UNRESOLVE\"");

    engine.AddRecord(TEST, "4", obj4.ToJsonString());
    engine.AddRecord(TEST, "6", obj6.ToJsonString());

    Console.WriteLine();

    foreach ((string dataSourceCode, string recordID) in records.Keys)
    {
        string result = engine.GetEntity(
            dataSourceCode, recordID, SzEntityBriefDefaultFlags);

        JsonObject? jsonObj = JsonNode.Parse(result)?.AsObject();
        jsonObj = jsonObj?["RESOLVED_ENTITY"]?.AsObject();
        long? entityID = jsonObj?["ENTITY_ID"]?.GetValue<long>();

        Console.WriteLine(
            "Record " + dataSourceCode + ":" + recordID
            + " now resolves to entity " + entityID);
    }
    Console.WriteLine();
}
catch (SzException e)
{
    // handle any exception that may have occurred
    Console.Error.WriteLine("Senzing Error Message : " + e.Message);
    Console.Error.WriteLine("Senzing Error Code    : " + e.ErrorCode);
    Console.Error.WriteLine(e);
    throw;

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
}

/// <summary>
/// This is a support method for providing example records to add.
/// </summary>
/// 
/// <returns>
/// A <see cref="IDictionary{(string,string),string}"/> of record key tuple keys
/// to <c>string</c> JSON text values desribing the records to be added.
/// </returns>
static IDictionary<(string, string), string> GetRecords()
{
    IDictionary<(string, string), string> records
        = new SortedDictionary<(string, string), string>();

    records.Add(
        ("TEST", "4"),
        """
        {
            "DATA_SOURCE": "TEST",
            "RECORD_ID": "4",
            "PRIMARY_NAME_FULL": "Elizabeth Jonas",
            "ADDR_FULL": "202 Rotary Dr, Rotorville, RI, 78720",
            "SSN_NUMBER": "767-87-7678",
            "DATE_OF_BIRTH": "1/12/1990"
        }
        """);

    records.Add(
        ("TEST", "5"),
        """
        {
            "DATA_SOURCE": "TEST",
            "RECORD_ID": "5",
            "PRIMARY_NAME_FULL": "Beth Jones",
            "ADDR_FULL": "202 Rotary Dr, Rotorville, RI, 78720",
            "SSN_NUMBER": "767-87-7678",
            "DATE_OF_BIRTH": "1/12/1990"
        }
        """);

    records.Add(
        ("TEST", "6"),
        """
        {
            "DATA_SOURCE": "TEST",
            "RECORD_ID": "6",
            "PRIMARY_NAME_FULL": "Betsey Jones",
            "ADDR_FULL": "202 Rotary Dr, Rotorville, RI, 78720",
            "PHONE_NUMBER": "202-787-7678"
        }
        """);

    return records;
}

public partial class Program
{
    public const string TEST = "Test";
}