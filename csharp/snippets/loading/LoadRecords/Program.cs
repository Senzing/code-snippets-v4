using System;
using System.Collections.Generic;
using System.Reflection;
using System.Text;
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
    foreach (KeyValuePair<(string, string), string> pair in GetRecords())
    {
        (string dataSourceCode, string recordID) = pair.Key;
        string recordDefinition = pair.Value;

        // call the addRecord() function with no flags
        engine.AddRecord(dataSourceCode, recordID, recordDefinition, SzNoFlags);

        Console.WriteLine("Record " + recordID + " added");
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
        ("TEST", "1001"),
        """
        {
            "DATA_SOURCE": "TEST",
            "RECORD_ID": "1001",
            "RECORD_TYPE": "PERSON",
            "PRIMARY_NAME_FIRST": "Robert",
            "PRIMARY_NAME_LAST": "Smith",
            "DATE_OF_BIRTH": "12/11/1978",
            "ADDR_TYPE": "MAILING",
            "ADDR_FULL": "123 Main Street, Las Vegas, NV 89132",
            "PHONE_TYPE": "HOME",
            "PHONE_NUMBER": "702-919-1300",
            "EMAIL_ADDRESS": "bsmith@work.com"
        }
        """);

    records.Add(
        ("TEST", "1002"),
        """
        {
            "DATA_SOURCE": "TEST",
            "RECORD_ID": "1002",
            "RECORD_TYPE": "PERSON",
            "PRIMARY_NAME_FIRST": "Bob",
            "PRIMARY_NAME_LAST": "Smith",
            "PRIMARY_NAME_GENERATION": "II",
            "DATE_OF_BIRTH": "11/12/1978",
            "ADDR_TYPE": "HOME",
            "ADDR_LINE1": "1515 Adela Lane",
            "ADDR_CITY": "Las Vegas",
            "ADDR_STATE": "NV",
            "ADDR_POSTAL_CODE": "89111",
            "PHONE_TYPE": "MOBILE",
            "PHONE_NUMBER": "702-919-1300"
        }
        """);

    records.Add(
        ("TEST", "1003"),
        """
        {
            "DATA_SOURCE": "TEST",
            "RECORD_ID": "1003",
            "RECORD_TYPE": "PERSON",
            "PRIMARY_NAME_FIRST": "Bob",
            "PRIMARY_NAME_LAST": "Smith",
            "PRIMARY_NAME_MIDDLE": "J",
            "DATE_OF_BIRTH": "12/11/1978",
            "EMAIL_ADDRESS": "bsmith@work.com"
        }
        """);

    records.Add(
        ("TEST", "1004"),
        """
        {
            "DATA_SOURCE": "TEST",
            "RECORD_ID": "1004",
            "RECORD_TYPE": "PERSON",
            "PRIMARY_NAME_FIRST": "B",
            "PRIMARY_NAME_LAST": "Smith",
            "ADDR_TYPE": "HOME",
            "ADDR_LINE1": "1515 Adela Ln",
            "ADDR_CITY": "Las Vegas",
            "ADDR_STATE": "NV",
            "ADDR_POSTAL_CODE": "89132",
            "EMAIL_ADDRESS": "bsmith@work.com"
        }
        """);

    records.Add(
        ("TEST", "1005"),
        """
        {
            "DATA_SOURCE": "TEST",
            "RECORD_ID": "1005",
            "RECORD_TYPE": "PERSON",
            "PRIMARY_NAME_FIRST": "Rob",
            "PRIMARY_NAME_MIDDLE": "E",
            "PRIMARY_NAME_LAST": "Smith",
            "DRIVERS_LICENSE_NUMBER": "112233",
            "DRIVERS_LICENSE_STATE": "NV",
            "ADDR_TYPE": "MAILING",
            "ADDR_LINE1": "123 E Main St",
            "ADDR_CITY": "Henderson",
            "ADDR_STATE": "NV",
            "ADDR_POSTAL_CODE": "89132"
        }
        """);

    return records;
}
