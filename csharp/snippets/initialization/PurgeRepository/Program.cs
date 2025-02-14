using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Diagnostics;
using System.Reflection;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using Senzing.Sdk;
using Senzing.Sdk.Core;

// confirm purge
Console.WriteLine(PurgeMessage);
string? response = Console.ReadLine();
if (response == null || !YesAnswers.Contains(response))
{
    Environment.Exit(1);
    return;
}

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
    SzDiagnostic diagnostic = env.GetDiagnostic();

    Stopwatch stopwatch = Stopwatch.StartNew();

    Console.WriteLine("Purging Senzing repository...");
    diagnostic.PurgeRepository();

    long duration = stopwatch.ElapsedMilliseconds;

    Console.WriteLine("Purged Senzing repository. (" + duration + "ms)");

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

public partial class Program
{
    private const string PurgeMessage = """
            **************************************** WARNING ****************************************

            This example will purge all currently loaded data from the Senzing datastore!
            Before proceeding, all instances of Senzing (custom code, tools, etc.) must be shut down.

            *****************************************************************************************

            Are you sure you want to continue and purge the Senzing datastore? (y/n) 
            """;

    private static readonly ReadOnlyCollection<string> YesAnswers
        = new ReadOnlyCollection<string>(["y", "Y", "Yes", "yes", "YES"]);
}