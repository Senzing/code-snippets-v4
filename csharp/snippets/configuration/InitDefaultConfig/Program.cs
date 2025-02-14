using System;
using System.Collections.Generic;
using System.Reflection;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using Senzing.Sdk;
using Senzing.Sdk.Core;

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
    // get the config and config manager from the environment
    SzConfig config = env.GetConfig();
    SzConfigManager configMgr = env.GetConfigManager();

    // prepare an in-memory config to be modified and get the handle
    IntPtr configHandle = config.CreateConfig();
    string? configDefinition = null;

    try
    {
        configDefinition = config.ExportConfig(configHandle);

    }
    finally
    {
        config.CloseConfig(configHandle);
    }

    // add the modified config to the repository with a comment
    long configID = configMgr.AddConfig(
        configDefinition, "Initial configuration");

    // replace the default config
    configMgr.SetDefaultConfigID(configID);
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
    Console.Error.WriteLine(e);
    throw;
}
finally
{
    // IMPORTANT: make sure to destroy the environment
    env.Destroy();
}
