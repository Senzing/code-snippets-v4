using System.Collections;
using System.Diagnostics;
using System.Reflection;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using Microsoft.Data.Sqlite;
using Senzing.Sdk;
using Senzing.Sdk.Core;
using static Senzing.Sdk.SzFlags;

Assembly assembly = Assembly.GetExecutingAssembly();
string? assemblyName = assembly.GetName().Name;

const string SourceKeyPrefix = "source.";

const string LoadKeyPrefix = "load.";

const string InputKeyPrefix = "input.";

const string DestroyAfterKey = "destroyAfter";

const string DataSource = "DATA_SOURCE";

const string RecordID = "RECORD_ID";

const string TestSource = "TEST_SOURCE";

const int SigtermExitCode = 143;

DirectoryInfo dir = new DirectoryInfo(Directory.GetCurrentDirectory());
DirectoryInfo? csharpDir = null;
DirectoryInfo? snippetDir = null;
DirectoryInfo? runnerDir = null;
switch (dir.Name)
{
    case "snippets":
        snippetDir = dir;
        break;
    case "runner":
        runnerDir = dir;
        break;
    case "csharp":
        csharpDir = dir;
        break;
    default:
        HandleWrongDirectory();
        break;
}

// if no snippet dir, try to find the csharp dir from the runner dir
if (snippetDir == null && runnerDir != null)
{
    csharpDir = Directory.GetParent(runnerDir.FullName);
    if (!"csharp".Equals(csharpDir?.Name))
    {
        HandleWrongDirectory();
    }
}

// if no snippet dir, try to find it using the csharp dir
if (snippetDir == null && csharpDir != null)
{
    snippetDir = new DirectoryInfo(Path.Combine(csharpDir.FullName, "snippets"));
    if (!snippetDir.Exists)
    {
        HandleWrongDirectory();
    }
}

if (snippetDir == null)
{
    HandleWrongDirectory();
    Environment.Exit(1);
    return;
}

try
{
    SortedDictionary<string, SortedDictionary<string, string>> snippetsMap
        = GetSnippetsMap(snippetDir);

    SortedDictionary<string, IList<(string, string, string)>> snippetOptions
        = new SortedDictionary<string, IList<(string, string, string)>>();
    foreach (KeyValuePair<string, SortedDictionary<string, string>> entry in snippetsMap)
    {
        string group = entry.Key;
        IDictionary<string, string> snippetMap = entry.Value;
        IList<(string, string, string)> tuples
            = new List<(string, string, string)>(snippetMap.Count);

        foreach (KeyValuePair<string, string> subEntry in snippetMap)
        {
            string snippet = subEntry.Key;
            string snippetPath = subEntry.Value;
            tuples.Add((group, snippet, snippetPath));
        }
        snippetOptions.Add(group, tuples.AsReadOnly());
    }

    foreach (KeyValuePair<string, SortedDictionary<string, string>> entry in snippetsMap)
    {
        string group = entry.Key;
        IDictionary<string, string> snippetMap = entry.Value;
        foreach (KeyValuePair<string, string> subEntry in snippetMap)
        {
            string snippet = subEntry.Key;
            string snippetPath = subEntry.Value;
            IList<(string, string, string)> tuples = new List<(string, string, string)>(1);
            tuples.Add((group, snippet, snippetPath));
            snippetOptions.Add(snippet, tuples.AsReadOnly());
        }
    }

    if (args.Length == 0)
    {
        PrintUsage(snippetsMap);
        Environment.Exit(1);
    }

    // check for settings in the environment
    string? settings
        = Environment.GetEnvironmentVariable("SENZING_ENGINE_CONFIGURATION_JSON");

    // validate the settings if we have them
    if (settings != null)
    {
        settings = settings.Trim();
        JsonObject? settingsJson = null;
        try
        {
            settingsJson = JsonNode.Parse(settings)?.AsObject();
            if (settingsJson == null) {
                throw new Exception("Setting must be a JSON object: " + settings);
            }
        }
        catch (Exception e)
        {
            Console.Error.WriteLine(e);
            Console.Error.WriteLine("The provided Senzing settings were not valid JSON:");
            Console.Error.WriteLine();
            Environment.Exit(1);
        }
    }

    // validate the SENZING_DIR
    InstallLocations? installLocations = null;
    try
    {
        installLocations = InstallLocations.FindLocations();

    }
    catch (Exception e)
    {
        Console.Error.WriteLine(e);
        Environment.Exit(1);
    }
    if (installLocations == null)
    {
        Console.Error.WriteLine("Could not find the Senzing installation.");
        Console.Error.WriteLine("Try setting the SENZING_DIR environment variable.");
        Environment.Exit(1);
        return;
    }

    IList<(string, string)> snippets = new List<(string, string)>(100);
    for (int index = 0; index < args.Length; index++)
    {
        string arg = args[index];
        if (arg.Equals("all"))
        {
            foreach (IDictionary<string, string> snippetMap in snippetsMap.Values)
            {
                foreach (KeyValuePair<string, string> entry in snippetMap)
                {
                    string snippet = entry.Key;
                    string snippetPath = entry.Value;
                    if (!snippets.Contains((snippet, snippetPath)))
                    {
                        snippets.Add((snippet, snippetPath));
                    }
                }
            }
            continue;
        }
        if (!snippetOptions.ContainsKey(arg))
        {
            Console.Error.WriteLine("Unrecognized code snippet or snippet group: " + arg);
            Environment.Exit(1);
        }
        IList<(string, string, string)> tuples = snippetOptions[arg];
        foreach ((string group, string snippet, string path) in tuples)
        {
            if (!snippets.Contains((snippet, path)))
            {
                snippets.Add((snippet, path));
            }
        }
    }

    // check if we do not have settings and if not setup a temporary repository
    if (settings == null)
    {
        settings = SetupTempRepository(installLocations);
    }

    long defaultConfigID;

    SzEnvironment env = SzCoreEnvironment.NewBuilder().Settings(settings).Build();
    try
    {
        SzConfigManager configMgr = env.GetConfigManager();
        defaultConfigID = configMgr.GetDefaultConfigID();

    }
    catch (SzException e)
    {
        Console.Error.WriteLine(e);
        Environment.Exit(1);
        return;

    }
    finally
    {
        env.Destroy();
    }

    foreach ((string snippet, string snippetPath) in snippets)
    {
        Console.WriteLine();
        Stopwatch stopwatch = Stopwatch.StartNew();
        IDictionary<string, string> properties = new Dictionary<string, string>();
        string resourceName = $"""{assemblyName}.Resources.{snippet}.properties""";
        LoadProperties(properties, resourceName);
        Console.WriteLine("Preparing repository for " + snippet + "...");
        env = SzCoreEnvironment.NewBuilder().Settings(settings).Build();
        try
        {
            // first purge the repository
            SzDiagnostic diagnostic = env.GetDiagnostic();
            diagnostic.PurgeRepository();

            // now set the configuration
            SzConfigManager configMgr = env.GetConfigManager();
            // check if we need to configure sources
            if (properties.ContainsKey(SourceKeyPrefix + 0))
            {
                SzConfig config = env.GetConfig();
                IntPtr handle = config.CreateConfig();
                string? snippetConfig = null;
                try
                {
                    for (int index = 0;
                        properties.ContainsKey(SourceKeyPrefix + index);
                        index++)
                    {
                        string sourceKey = SourceKeyPrefix + index;
                        string source = properties[sourceKey];
                        source = source.Trim();
                        Console.WriteLine("Adding data source: " + source);
                        config.AddDataSource(handle, source);
                    }
                    snippetConfig = config.ExportConfig(handle);

                }
                finally
                {
                    config.CloseConfig(handle);
                }

                // register the config
                long configID = configMgr.AddConfig(snippetConfig, snippet);

                // set the default config to the snippet config
                configMgr.SetDefaultConfigID(configID);

            }
            else
            {
                // set the default config to the initial default
                configMgr.SetDefaultConfigID(defaultConfigID);
            }

            // check if there are files we need to load
            if (properties.ContainsKey(LoadKeyPrefix + 0))
            {
                SzEngine engine = env.GetEngine();
                for (int index = 0; properties.ContainsKey(LoadKeyPrefix + index); index++)
                {
                    string loadKey = LoadKeyPrefix + index;
                    string fileName = properties[loadKey];
                    fileName = fileName.Trim();
                    Console.WriteLine("Loading records from file resource: " + fileName);
                    Stream? stream = assembly.GetManifestResourceStream(fileName);
                    if (stream == null)
                    {
                        throw new ArgumentException(
                            "Missing resource (" + fileName + ") for load file ("
                            + loadKey + ") for snippet (" + snippet + ")");
                    }
                    try
                    {
                        StreamReader rdr = new StreamReader(stream, Encoding.UTF8);
                        for (string? line = rdr.ReadLine(); line != null; line = rdr.ReadLine())
                        {
                            line = line.Trim();
                            if (line.Length == 0) continue;
                            if (line.StartsWith("#")) continue;
                            JsonObject? record = JsonNode.Parse(line)?.AsObject();
                            if (record == null)
                            {
                                throw new JsonException("Failed to parse line as JSON: " + line);
                            }
                            string dataSource = record.ContainsKey(DataSource)
                                ? record[DataSource]?.GetValue<string>() ?? TestSource : TestSource;
                            string? recordID = record.ContainsKey(RecordID)
                                ? record[RecordID]?.GetValue<string>() : null;
                            engine.AddRecord(dataSource, recordID, line, SzNoFlags);
                        }
                    }
                    finally
                    {
                        stream.Close();
                    }

                }
            }

        }
        catch (SzException e)
        {
            Console.Error.WriteLine(e);
            Environment.Exit(1);
            return;
        }
        finally
        {
            env.Destroy();
        }
        long duration = stopwatch.ElapsedMilliseconds;
        Console.WriteLine("Prepared repository for " + snippet + ". (" + duration + "ms)");

        ExecuteSnippet(snippet, snippetPath, installLocations, settings, properties);
    }

    Console.WriteLine();

}
catch (Exception e)
{
    Console.Error.WriteLine(e);
    Environment.Exit(1);
    return;
}

static void LoadProperties(IDictionary<string, string> properties, String resourceName)
{
    Assembly assembly = Assembly.GetExecutingAssembly();
    Stream? stream = assembly.GetManifestResourceStream(resourceName);
    if (stream != null)
    {
        StreamReader rdr = new StreamReader(stream, Encoding.UTF8);
        try
        {
            for (string? line = rdr.ReadLine(); line != null; line = rdr.ReadLine())
            {
                if (line.Trim().Length == 0) continue;
                if (line.StartsWith("#")) continue;
                if (line.StartsWith("!")) continue;
                int index = line.IndexOf('=');
                if (index < 1) continue;
                string key = line.Substring(0, index).Trim();
                string value = "";
                if (index < line.Length - 1)
                {
                    value = line.Substring(index + 1);
                }
                value = value.Trim();
                while (value.EndsWith("\\"))
                {
                    line = rdr.ReadLine();
                    if (line == null) break;
                    line = line.Trim();
                    value = value.Substring(0, value.Length - 1) + line;
                }
                properties[key] = value;
            }
        }
        finally
        {
            stream.Close();
        }
    }
}

static SortedDictionary<string, SortedDictionary<string, string>>
    GetSnippetsMap(DirectoryInfo snippetDir)
{
    SortedDictionary<string, SortedDictionary<string, string>> snippetsMap
        = new SortedDictionary<string, SortedDictionary<string, string>>();

    foreach (string dir in Directory.GetDirectories(snippetDir.FullName))
    {
        string? group = Path.GetFileName(dir);
        if (group == null)
        {
            continue;
        }
        if (!snippetsMap.ContainsKey(group))
        {
            snippetsMap.Add(group, new SortedDictionary<string, string>());
        }
        SortedDictionary<string, string> snippetMap = snippetsMap[group];

        foreach (string subdir in Directory.GetDirectories(dir))
        {
            string? snippet = Path.GetFileName(subdir);
            if (snippet == null)
            {
                continue;
            }
            string csprojPath = Path.Combine(subdir, snippet + ".csproj");
            if (!File.Exists(csprojPath))
            {
                continue;
            }
            snippetMap.Add(group + "." + snippet, subdir);
        }
    }
    return snippetsMap;
}

static void PrintUsage(SortedDictionary<string, SortedDictionary<string, string>> snippetsMap)
{
    Assembly assembly = Assembly.GetExecutingAssembly();
    string? assemblyName = assembly.GetName().Name;
    Console.Error.WriteLine($"""dotnet run --project {assemblyName} [ all | <group> | <snippet> ]*""");
    Console.Error.WriteLine();
    Console.Error.WriteLine("  - Specifying no arguments will print this message");
    Console.Error.WriteLine("  - Specifying \"all\" will run all snippets");
    Console.Error.WriteLine("  - Specifying one or more groups will run all snippets in those groups");
    Console.Error.WriteLine("  - Specifying one or more snippets will run those snippet");
    Console.Error.WriteLine();
    Console.Error.WriteLine("Examples:");
    Console.Error.WriteLine();
    Console.Error.WriteLine($"""  dotnet run --project {assemblyName} all""");
    Console.Error.WriteLine();
    Console.Error.WriteLine($"""  dotnet run --project {assemblyName} loading.LoadRecords loading.LoadViaFutures""");
    Console.Error.WriteLine();
    Console.Error.WriteLine($"""  dotnet run --project {assemblyName} initialization deleting loading.LoadRecords""");
    Console.Error.WriteLine();
    Console.Error.WriteLine("Snippet Group Names:");
    foreach (string group in snippetsMap.Keys)
    {
        Console.Error.WriteLine("  - " + group);
    }
    Console.Error.WriteLine();
    Console.Error.WriteLine("Snippet Names:");
    foreach (IDictionary<string, string> snippetMap in snippetsMap.Values)
    {
        foreach (string snippet in snippetMap.Keys)
        {
            Console.Error.WriteLine("  - " + snippet);
        }
    }
    Console.Error.WriteLine();
}

static void HandleWrongDirectory()
{
    Console.Error.WriteLine(
        "Must be run from the csharp, csharp/runner or csharp/snippets directory");
    Environment.Exit(1);
}

static void SetupEnvironment(ProcessStartInfo startInfo,
                             InstallLocations installLocations,
                             string settings)
{
    System.Collections.IDictionary origEnv = Environment.GetEnvironmentVariables();
    foreach (DictionaryEntry entry in origEnv)
    {
        startInfo.Environment[entry.Key?.ToString() ?? ""]
            = entry.Value?.ToString() ?? "";
    }
    startInfo.Environment["SENZING_ENGINE_CONFIGURATION_JSON"] = settings;
}

static void ExecuteSnippet(string snippet,
                           string snippetPath,
                           InstallLocations senzingInstall,
                           string settings,
                           IDictionary<string, string> properties)
{
    ProcessStartInfo startInfo = new ProcessStartInfo(
        "dotnet",
        "run --project " + snippetPath);
    SetupEnvironment(startInfo, senzingInstall, settings);
    startInfo.WindowStyle = ProcessWindowStyle.Hidden;
    startInfo.UseShellExecute = false;
    startInfo.RedirectStandardInput = true;

    Console.WriteLine();
    Console.WriteLine("---------------------------------------");
    Console.WriteLine("Executing " + snippet + "...");
    Stopwatch stopWatch = Stopwatch.StartNew();

    Process? process = Process.Start(startInfo);
    if (process == null)
    {
        throw new Exception("Failed to execute snippet; " + snippet);
    }

    if (properties != null && properties.ContainsKey(InputKeyPrefix + 0))
    {
        // sleep for 1 second to give the process a chance to start up
        Thread.Sleep(1000);
        for (int index = 0;
             properties.ContainsKey(InputKeyPrefix + index);
             index++)
        {
            string inputLine = properties[InputKeyPrefix + index];
            Console.WriteLine(inputLine);
            Console.Out.Flush();

            inputLine = (inputLine == null) ? "" : inputLine.Trim();
            process.StandardInput.WriteLine(inputLine);
            process.StandardInput.Flush();
        }
    }
    int exitValue = 0;
    int expectedExitValue = 0;
    if (properties != null && properties.ContainsKey(DestroyAfterKey))
    {
        string propValue = properties[DestroyAfterKey];
        int delay = Int32.Parse(propValue);
        bool exited = process.WaitForExit(delay);
        if (!exited && !process.HasExited)
        {
            expectedExitValue = SigtermExitCode;
            Console.WriteLine();
            Console.WriteLine("Runner destroying " + snippet + " process...");


            ProcessStartInfo killStartInfo
                = new ProcessStartInfo("kill", "" + process.Id);

            startInfo.WindowStyle = ProcessWindowStyle.Hidden;
            startInfo.UseShellExecute = false;
            Process? killer = Process.Start(killStartInfo);
            if (killer == null) {
                process.Kill(true);
                process.WaitForExit();        
            } else {
                killer.WaitForExit();
                process.WaitForExit();
            }
        }
        exitValue = process.ExitCode;
    }
    else
    {
        // wait indefinitely for the process to terminate
        process.WaitForExit();
        exitValue = process.ExitCode;
    }

    if (exitValue != expectedExitValue)
    {
        throw new Exception("Failed to execute snippet; " + snippet
                            + " (" + exitValue + ")");
    }
    stopWatch.Stop();
    int duration = stopWatch.Elapsed.Milliseconds;
    Console.WriteLine("Executed " + snippet + ". (" + duration + "ms)");
}

static string SetupTempRepository(InstallLocations senzingInstall)
{
    DirectoryInfo? supportDir = senzingInstall.SupportDirectory;
    DirectoryInfo? resourcesDir = senzingInstall.ResourceDirectory;
    DirectoryInfo? templatesDir = senzingInstall.TemplatesDirectory;
    DirectoryInfo? configDir = senzingInstall.ConfigDirectory;
    if (supportDir == null || configDir == null
        || resourcesDir == null || templatesDir == null)
    {
        throw new Exception(
            "At least one of the required directories is missing from "
            + "the installation.  installLoocations=[ "
            + senzingInstall + " ]");
    }

    DirectoryInfo schemaDir = new DirectoryInfo(
        Path.Combine(resourcesDir.FullName, "schema"));
    string schemaFile = Path.Combine(
        schemaDir.FullName, "szcore-schema-sqlite-create.sql");
    string configFile = Path.Combine(
        templatesDir.FullName, "g2config.json");

    // lay down the database schema
    string databaseFile = Path.Combine(
        Path.GetTempPath(), "G2C-" + Path.GetRandomFileName() + ".db");
    String jdbcUrl = "jdbc:sqlite:" + databaseFile;

    SqliteConnection? sqlite = null;
    try
    {
        String connectSpec = "Data Source=" + databaseFile;
        sqlite = new SqliteConnection(connectSpec);
        sqlite.Open();
        SqliteCommand cmd = sqlite.CreateCommand();

        string[] sqlLines = File.ReadAllLines(schemaFile, Encoding.UTF8);

        foreach (string sql in sqlLines)
        {
            if (sql.Trim().Length == 0) continue;
#pragma warning disable CA2100
            cmd.CommandText = sql.Trim();
#pragma warning restore CA2100
            cmd.ExecuteNonQuery();
        }
    }
    finally
    {
        if (sqlite != null)
        {
            sqlite.Close();
        }
    }

    string supportPath = supportDir.FullName.Replace("\\", "\\\\");
    string configPath = configDir.FullName.Replace("\\", "\\\\");;
    string resourcePath = resourcesDir.FullName.Replace("\\", "\\\\");;
    string baseConfig = File.ReadAllText(configFile).Replace("\\", "\\\\");;
    string settings = $$"""
            {
                "PIPELINE": {
                    "SUPPORTPATH": "{{supportPath}}",
                    "CONFIGPATH": "{{configPath}}",
                    "RESOURCEPATH": "{{resourcePath}}"
                },
                "SQL": {
                    "CONNECTION": "sqlite3://na:na@{{databaseFile}}"
                }
            }
            """.Trim();

    SzEnvironment env = SzCoreEnvironment.NewBuilder().Settings(settings).Build();
    try
    {
        SzConfigManager configMgr = env.GetConfigManager();

        long configID = configMgr.AddConfig(baseConfig, "Default Config");
        configMgr.SetDefaultConfigID(configID);

    }
    finally
    {
        env.Destroy();
    }

    return settings;
}
