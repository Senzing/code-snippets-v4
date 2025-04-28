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
    // get the config and config from the environment
    SzConfigManager configMgr = env.GetConfigManager();

    // prepare a config to be modified
    SzConfig config             = configMgr.CreateConfig();
    string   configDefinition   = config.Export();

    // add the modified config to the repository with a comment
    configMgr.SetDefaultConfig(configDefinition);
    
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
