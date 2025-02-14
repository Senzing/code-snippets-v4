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

    // setup a loop to handle race-condition conflicts on 
    // replacing the default config ID
    bool replacedConfig = false;
    while (!replacedConfig)
    {
        // get the current default config ID and associated config JSON
        long configID = configMgr.GetDefaultConfigID();
        string configDefinition = configMgr.GetConfig(configID);

        // prepare an in-memory config to be modified and get the handle
        IntPtr configHandle = config.ImportConfig(configDefinition);
        string? modifiedConfig = null;
        try
        {
            // create an array of the data sources to add
            string[] dataSources = { "CUSTOMERS", "EMPLOYEES", "WATCHLIST" };

            // loop through the array and add each data source
            foreach (string dataSource in dataSources)
            {
                config.AddDataSource(configHandle, dataSource);
            }

            // export the modified config to JSON text
            modifiedConfig = config.ExportConfig(configHandle);

        }
        finally
        {
            config.CloseConfig(configHandle);
        }

        // add the modified config to the repository with a comment
        long newConfigID = configMgr.AddConfig(
            modifiedConfig, "Added truth set data sources");

        try
        {
            // replace the default config
            configMgr.ReplaceDefaultConfigID(configID, newConfigID);

            // if we get here then set the flag indicating success
            replacedConfig = true;

        }
        catch (SzReplaceConflictException)
        {
            // if we get here then another thread or process has
            // changed the default config ID since we retrieved it
            // (i.e.: we have a race condition) so we allow the 
            // loop to repeat with the latest default config ID
        }
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
    Console.Error.WriteLine(e);
    throw;
}
finally
{
    // IMPORTANT: make sure to destroy the environment
    env.Destroy();
}
