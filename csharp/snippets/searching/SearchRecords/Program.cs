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

    // loop through the example records and add them to the repository
    foreach (string criteria in GetSearchCriteria())
    {
        // call the searchByAttributes() function with default flags
        string result = engine.SearchByAttributes(
            criteria, SzSearchByAttributesDefaultFlags);

        JsonObject? jsonObj = JsonNode.Parse(result)?.AsObject();

        Console.WriteLine();
        JsonArray? jsonArr = jsonObj?["RESOLVED_ENTITIES"]?.AsArray();
        if (jsonArr == null || jsonArr.Count == 0)
        {
            Console.WriteLine("No results for criteria: " + criteria);
        }
        else
        {
            Console.WriteLine("Results for criteria: " + criteria);
            for (int index = 0; index < jsonArr.Count; index++)
            {
                JsonObject? obj = jsonArr[index]?.AsObject();
                obj = obj?["ENTITY"]?.AsObject();
                obj = obj?["RESOLVED_ENTITY"]?.AsObject();
                if (obj == null)
                {
                    throw new JsonException("Unexpected result format: " + result);
                }
                long? entityID = obj["ENTITY_ID"]?.GetValue<long>();
                string? name = obj["ENTITY_NAME"]?.GetValue<string>();
                Console.WriteLine(entityID + ": " + name);
            }
        }
        Console.Out.Flush();
    }

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
/// This is a support method for providing a list of criteria to search on.
/// </summary>
/// 
/// <returns>
/// A <see cref="IList{string}"/> of JSON text values desribing the
/// sets of criteria with which to search.
/// </returns>
static IList<string> GetSearchCriteria()
{
    IList<string> records = new List<string>();
    records.Add(
        """
        {
            "NAME_FULL": "Susan Moony",
            "DATE_OF_BIRTH": "15/6/1998",
            "SSN_NUMBER": "521212123"
        }
        """);

    records.Add(
        """
        {
            "NAME_FIRST": "Robert",
            "NAME_LAST": "Smith",
            "ADDR_FULL": "123 Main Street Las Vegas NV 89132"
        }
        """);

    records.Add(
        """
        {
            "NAME_FIRST": "Makio",
            "NAME_LAST": "Yamanaka",
            "ADDR_FULL": "787 Rotary Drive Rotorville FL 78720"
        }
        """);

    return records;
}