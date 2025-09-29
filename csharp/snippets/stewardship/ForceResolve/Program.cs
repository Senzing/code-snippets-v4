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

    string record1 = engine.GetRecord(TestDataSource, "1", SzRecordDefaultFlags);
    string record3 = engine.GetRecord(TestDataSource, "3", SzRecordDefaultFlags);

    JsonObject?[] jsonObjects = {
        JsonNode.Parse(record1)?.AsObject()?["JSON_DATA"]?.AsObject(),
        JsonNode.Parse(record3)?.AsObject()?["JSON_DATA"]?.AsObject()
    };
    foreach (JsonObject? obj in jsonObjects)
    {
        if (obj == null)
        {
            throw new JsonException("Parsed record is unexpectedly null: "
                + record1 + " / " + record3);
        }
        obj["TRUSTED_ID_NUMBER"] = JsonNode.Parse("\"TEST_R1-TEST_R3\"");
        obj["TRUSTED_ID_TYPE"] = JsonNode.Parse("\"FORCE_RESOLVE\"");
    }
    engine.AddRecord(TestDataSource, "1", jsonObjects[0]?.ToJsonString());
    engine.AddRecord(TestDataSource, "3", jsonObjects[1]?.ToJsonString());

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
/// to <c>string</c> JSON text values describing the records to be added.
/// </returns>
static IDictionary<(string, string), string> GetRecords()
{
    SortedDictionary<(string, string), string> records
        = new SortedDictionary<(string, string), string>();

    records.Add(
        ("TEST", "1"),
        """
        {
            "DATA_SOURCE": "TEST",
            "RECORD_ID": "1",
            "PRIMARY_NAME_FULL": "Patrick Smith",
            "AKA_NAME_FULL": "Paddy Smith",
            "ADDR_FULL": "787 Rotary Dr, Rotorville, RI, 78720",
            "PHONE_NUMBER": "787-767-2688",
            "DATE_OF_BIRTH": "1/12/1990"
        }
        """);

    records.Add(
        ("TEST", "2"),
        """
        {
            "DATA_SOURCE": "TEST",
            "RECORD_ID": "2",
            "PRIMARY_NAME_FULL": "Patricia Smith",
            "ADDR_FULL": "787 Rotary Dr, Rotorville, RI, 78720",
            "PHONE_NUMBER": "787-767-2688",
            "DATE_OF_BIRTH": "5/4/1994"
        }
        """);

    records.Add(
        ("TEST", "3"),
        """
        {
            "DATA_SOURCE": "TEST",
            "RECORD_ID": "3",
            "PRIMARY_NAME_FULL": "Pat Smith",
            "ADDR_FULL": "787 Rotary Dr, Rotorville, RI, 78720",
            "PHONE_NUMBER": "787-767-2688"
        }
        """);

    return records;
}

public partial class Program
{
    private const string TestDataSource = "Test";
}

#pragma warning restore CA1303 // Do not pass literals as localized parameters (example messages)
