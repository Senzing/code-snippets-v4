using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

using static System.StringComparison;

/// <summary>
/// Describes the directories on disk used to find the Senzing product
/// installation and the support directories.
/// </summary>
public class InstallLocations
{
    /// <summary>
    /// UTF8 encoding constant.
    /// </summary>
    private static readonly Encoding UTF8 = new UTF8Encoding();

    /// <summary>
    /// The installation location.
    /// </summary>
    private DirectoryInfo? installDir;

    /// <summary>
    /// The location of the configuration files for the config directory.
    /// </summary>
    private DirectoryInfo? configDir;

    /// <summary>
    /// The location of the resource files for the resource directory.
    /// </summary>
    private DirectoryInfo? resourceDir;

    /// <summary>
    /// The location of the support files for the support directory.
    /// </summary>
    private DirectoryInfo? supportDir;

    /// <summary>
    /// The location of the template files for the template directory.
    /// </summary>
    private DirectoryInfo? templatesDir;

    /// <summary>
    /// Indicates if the installation direction is from a development build.
    /// </summary>
    private bool devBuild;

    /// <summary>
    /// Default constructor.
    /// </summary>
    private InstallLocations()
    {
        this.installDir = null;
        this.configDir = null;
        this.resourceDir = null;
        this.supportDir = null;
        this.templatesDir = null;
        this.devBuild = false;
    }

    /// <summary>
    /// Gets the primary installation directory.
    /// </summary>
    /// 
    /// <returns>The primary installation directory.</returns>
    public DirectoryInfo? InstallDirectory
    {
        get
        {
            return this.installDir;
        }
    }

    /// <summary>
    /// Gets the configuration directory.
    /// </summary>
    /// 
    /// <returns>The configuration directory.</returns>
    public DirectoryInfo? ConfigDirectory
    {
        get
        {
            return this.configDir;
        }
    }

    /// <summary>
    /// Gets the resource directory.
    /// </summary>
    /// 
    /// <returns>The resource directory.</returns>
    public DirectoryInfo? ResourceDirectory
    {
        get
        {
            return this.resourceDir;
        }
    }

    /// <summary>
    /// Gets the support directory.
    /// </summary>
    /// 
    /// <returns>The support directory.</returns>
    public DirectoryInfo? SupportDirectory
    {
        get
        {
            return this.supportDir;
        }
    }

    /// <summary>
    /// Gets the templates directory.
    /// </summary>
    /// 
    /// <returns>The templates directory.</returns>
    public DirectoryInfo? TemplatesDirectory
    {
        get
        {
            return this.templatesDir;
        }
    }

    /// <summary>
    /// Checks if the installation is actually a development build.
    /// <summary>
    /// 
    /// <returns>
    /// <c>true</c> if this installation represents a development
    /// build, otherwise <c>false</c>.
    /// </returns>
    public bool IsDevelopmentBuild
    {
        get
        {
            return this.devBuild;
        }
    }

    /// <summary>
    /// Produces a <c>string</c> describing this instance.
    /// 
    /// @return A <c>string</c> describing this instance.
    /// </summary>
    public override string ToString()
    {
        StringBuilder sb = new StringBuilder();

        sb.AppendLine();
        sb.AppendLine("--------------------------------------------------");
        sb.AppendLine("installDirectory   : " + this.InstallDirectory);
        sb.AppendLine("configDirectory    : " + this.ConfigDirectory);
        sb.AppendLine("supportDirectory   : " + this.SupportDirectory);
        sb.AppendLine("resourceDirectory  : " + this.ResourceDirectory);
        sb.AppendLine("templatesDirectory : " + this.TemplatesDirectory);
        sb.AppendLine("developmentBuild   : " + this.IsDevelopmentBuild);

        return sb.ToString();
    }

    /// <summary>
    /// Checks if the specified path is a directory.
    /// </summary>
    /// <param name="path">The path to check</param>
    /// <returns><c>true</c> if it is a directory, otherwise <c>false</c></returns>
    private static bool IsDirectory(string path)
    {
        FileAttributes attrs = File.GetAttributes(path);
        return (attrs & FileAttributes.Directory) == FileAttributes.Directory;
    }

    /// <summary>
    /// Finds the install directories and returns the <c>InstallLocations</c>
    /// instance describing those locations.
    ///
    /// @return The <c>InstallLocations</c> instance describing the install
    ///         locations.
    /// </summary>
    public static InstallLocations? FindLocations()
    {
        DirectoryInfo? installDir = null;
        DirectoryInfo? configDir = null;
        DirectoryInfo? resourceDir = null;
        DirectoryInfo? supportDir = null;
        DirectoryInfo? templatesDir = null;

        string defaultInstallPath;
        string? defaultConfigPath = null;

        // check if we are building within the dev structure
        string[] directoryStructure = ["net8.0", "*", "bin", "Senzing.Sdk.Tests", "sz-sdk-csharp", "csharp", "g2", "apps", "dev"];
        DirectoryInfo? workingDir = new DirectoryInfo(Environment.CurrentDirectory);
        DirectoryInfo? previousDir = null;
        bool devStructure = true;
        foreach (var dirName in directoryStructure)
        {
            if (workingDir == null) break;
            if (!dirName.Equals("*", OrdinalIgnoreCase) && !workingDir.Name.Equals(dirName, OrdinalIgnoreCase))
            {
                devStructure = false;
                break;
            }
            previousDir = workingDir;
            workingDir = workingDir.Parent;
        }
        DirectoryInfo? devDistDir = (devStructure && previousDir != null)
            ? new DirectoryInfo(Path.Combine(previousDir.FullName, "dist")) : null;

        switch (Environment.OSVersion.Platform)
        {
            case PlatformID.Win32NT:
                defaultInstallPath = (devDistDir == null)
                    ? "C:\\Program Files\\Senzing\\er" : devDistDir.FullName;
                break;
            case PlatformID.MacOSX:
                defaultInstallPath = (devDistDir == null)
                    ? "/opt/senzing/er" : devDistDir.FullName;
                break;
            case PlatformID.Unix:
                if (devDistDir == null)
                {
                    defaultInstallPath = "/opt/senzing/er";
                    defaultConfigPath = "/etc/opt/senzing";
                }
                else
                {
                    defaultInstallPath = devDistDir.FullName;
                }
                break;
            default:
                throw new NotSupportedException(
                    "Unsupported Operating System: "
                    + Environment.OSVersion.Platform);
        }

        // check for senzing system properties
        string? installPath = Environment.GetEnvironmentVariable(
            "SENZING_DIR");
        string? configPath = Environment.GetEnvironmentVariable(
            "SENZING_ETC_DIR");
        string? supportPath = Environment.GetEnvironmentVariable(
            "SENZING_DATA_DIR");
        string? resourcePath = Environment.GetEnvironmentVariable(
            "SENZING_RESOURCE_DIR");

        // normalize empty strings as null
        if (installPath != null && installPath.Trim().Length == 0)
        {
            installPath = null;
        }
        if (configPath != null && configPath.Trim().Length == 0)
        {
            configPath = null;
        }
        if (supportPath != null && supportPath.Trim().Length == 0)
        {
            supportPath = null;
        }
        if (resourcePath != null && resourcePath.Trim().Length == 0)
        {
            resourcePath = null;
        }

        // check the senzing directory
        installDir = new DirectoryInfo(
            installPath == null ? defaultInstallPath : installPath);
        if (!installDir.Exists)
        {
            Console.WriteLine("Could not find Senzing installation directory:");
            Console.WriteLine("     " + installDir);
            Console.WriteLine();
            if (installPath != null)
            {
                Console.WriteLine(
                    "Check the SENZING_DIR environment variable.");
            }
            else
            {
                Console.WriteLine(
                    "Use the SENZING_DIR environment variable to specify a path");
            }

            return null;
        }

        // normalize the senzing directory
        string installDirName = installDir.Name;
        if (installDir.Exists && IsDirectory(installDir.FullName)
            && !installDirName.Equals("er", OrdinalIgnoreCase)
            && installDirName.Equals("senzing", OrdinalIgnoreCase))
        {
            // for windows or linux allow the "Senzing" dir as well
            installDir = new DirectoryInfo(Path.Combine(installDir.FullName, "er"));
        }

        if (!IsDirectory(installDir.FullName))
        {
            Console.Error.WriteLine("Senzing installation directory appears invalid:");
            Console.Error.WriteLine("     " + installDir);
            Console.Error.WriteLine();
            if (installPath != null)
            {
                Console.Error.WriteLine(
                    "Check the SENZING_DIR environment variable.");
            }
            else
            {
                Console.Error.WriteLine(
                    "Use the SENZING_DIR environment variable to specify a path");
            }

            return null;
        }

        if (supportPath == null || supportPath.Trim().Length == 0)
        {
            // try to determine the support path
            DirectoryInfo? installParent = installDir.Parent;
            DirectoryInfo? dataRoot = (installParent != null)
                ? new DirectoryInfo(Path.Combine(installParent.FullName, "data"))
                : null;

            if (dataRoot != null && dataRoot.Exists && IsDirectory(dataRoot.FullName))
            {
                DirectoryInfo? versionFile
                    = new DirectoryInfo(
                        Path.Combine(installDir.FullName, "szBuildVersion.json"));

                string? dataVersion = null;
                if (versionFile != null && versionFile.Exists)
                {

                    String text = File.ReadAllText(versionFile.FullName, UTF8);
                    JsonDocument jsonDoc = JsonDocument.Parse(text);
                    JsonElement rootElem = jsonDoc.RootElement;
                    JsonElement dataVerElem = rootElem.GetProperty("DATA_VERSION");
                    dataVersion = dataVerElem.GetString();
                }

                // try the data version directory
                supportDir = (dataVersion == null)
                    ? null
                    : new DirectoryInfo(
                        Path.Combine(dataRoot.FullName, dataVersion.Trim()));

                // check if data version was not found
                if (supportDir == null || !supportDir.Exists)
                {
                    Regex regex = new Regex("\\d+\\.\\d+\\.\\d+");
                    // look to see if we only have one data version installed
                    string[] dirs = Directory.GetDirectories(dataRoot.FullName);
                    List<DirectoryInfo> versionDirs
                        = new List<DirectoryInfo>();
                    foreach (string dir in dirs)
                    {
                        DirectoryInfo versionDir = new DirectoryInfo(dir);
                        if (!regex.IsMatch(versionDir.Name)) continue;
                        versionDirs.Add(versionDir);
                    }
                    if (versionDirs.Count == 1 && supportDir == null)
                    {
                        // use the single data version found
                        supportDir = versionDirs[0];
                    }
                    else if (versionDirs.Count > 1)
                    {
                        Console.Error.WriteLine(
                            "Could not infer support directory.  Multiple data "
                                + "directory versions at: ");
                        Console.Error.WriteLine("     " + dataRoot);
                        if (supportDir != null)
                        {
                            Console.Error.WriteLine();
                            Console.Error.WriteLine("Expected to find: " + supportDir);
                        }
                        throw new InvalidOperationException(
                            ((supportDir == null)
                                ? "Could not infer support directory."
                                : "Could not find support directory (" + supportDir + ").")
                                + "  Multiple data directory versions found at: "
                                + dataRoot);
                    }
                    else
                    {
                        // no version directories were found, maybe the data root is
                        // the actual support directory (mapped in a docker image)
                        string[] files = Directory.GetFiles(dataRoot.FullName);
                        List<FileInfo> ibmFiles = new List<FileInfo>();
                        foreach (string file in files)
                        {
                            if (file.EndsWith(".ibm", OrdinalIgnoreCase))
                            {
                                ibmFiles.Add(new FileInfo(file));
                            }
                        }
                        DirectoryInfo? libPostalDir = new DirectoryInfo(
                            Path.Combine(dataRoot.FullName, "libpostal"));

                        // require the .ibm files and libpostal to exist
                        if (ibmFiles.Count > 0 && libPostalDir.Exists)
                        {
                            supportDir = dataRoot;
                        }
                    }
                }

            }
            if (supportDir == null)
            {
                // use the default path
                supportDir = new DirectoryInfo(
                    Path.Combine(installDir.FullName, "data"));
            }

        }
        else
        {
            // use the specified explicit path
            supportDir = new DirectoryInfo(supportPath);
        }

        if (!supportDir.Exists)
        {
            Console.Error.WriteLine("The support directory does not exist:");
            Console.Error.WriteLine("         " + supportDir);
            if (supportPath != null)
            {
                Console.Error.WriteLine(
                    "Check the SENZING_DATA_DIR environment variable.");
            }
            else
            {
                Console.Error.WriteLine(
                    "Use the SENZING_DATA_DIR environment variable to specify a path");
            }

            throw new InvalidOperationException(
                "The support directory does not exist: " + supportDir);
        }

        if (!IsDirectory(supportDir.FullName))
        {
            Console.Error.WriteLine("The support directory is invalid:");
            Console.Error.WriteLine("         " + supportDir);
            if (supportPath != null)
            {
                Console.Error.WriteLine(
                    "Check the SENZING_DATA_DIR environment variable.");
            }
            else
            {
                Console.Error.WriteLine(
                    "Use the SENZING_DATA_DIR environment variable to specify a path");
            }
            throw new InvalidOperationException(
                "The support directory is invalid: " + supportDir);

        }

        // check the config path
        if (configPath != null)
        {
            configDir = new DirectoryInfo(configPath);
        }

        // check for a dev build installation
        if (configDir == null && installDir != null && "dist".Equals(installDir.Name, OrdinalIgnoreCase))
        {
            configDir = new DirectoryInfo(Path.Combine(installDir.FullName, "data"));
        }

        // if still null and there is a default, then use it
        if (configDir == null && defaultConfigPath != null)
        {
            configDir = new DirectoryInfo(defaultConfigPath);
            if (!configDir.Exists)
            {
                configDir = null;
            }
        }

        // if still null, try to use the install's etc directory
        if (configDir == null && installDir != null)
        {
            configDir = new DirectoryInfo(Path.Combine(installDir.FullName, "etc"));
            if (!configDir.Exists)
            {
                configDir = null;
            }
        }

        if (configPath != null && configDir != null && !configDir.Exists)
        {
            Console.Error.WriteLine(
                "The SENZING_ETC_DIR environment variable specifies a path that does not exist:");
            Console.Error.WriteLine(
                "         " + configPath);

            throw new InvalidOperationException(
                "Explicit config path does not exist: " + configPath);
        }
        if (configDir != null && configDir.Exists)
        {
            if (!IsDirectory(configDir.FullName))
            {
                Console.Error.WriteLine(
                    "The SENZING_ETC_DIR environment variable specifies a file, not a directory:");
                Console.Error.WriteLine(
                    "         " + configPath);

                throw new InvalidOperationException(
                    "Explicit config path is not directory: " + configPath);
            }

            String[] requiredFiles = ["cfgVariant.json"];
            List<string> missingFiles = new List<string>(requiredFiles.Length);

            foreach (string fileName in requiredFiles)
            {
                DirectoryInfo? configFile = new DirectoryInfo(
                    Path.Combine(configDir.FullName, fileName));
                DirectoryInfo? supportFile = new DirectoryInfo(
                    Path.Combine(supportDir.FullName, fileName));
                if (!configFile.Exists && !supportFile.Exists)
                {
                    missingFiles.Add(fileName);
                }
            }
            if (missingFiles.Count > 0 && configPath != null)
            {
                Console.Error.WriteLine(
                    "The SENZING_ETC_DIR environment variable specifies an invalid config directory:");
                foreach (string missing in missingFiles)
                {
                    Console.Error.WriteLine(
                        "         " + missing + " was not found");
                }
                throw new InvalidOperationException(
                    "Explicit config path missing required files: " + missingFiles);
            }
        }

        // now determine the resource path
        resourceDir = (resourcePath == null) ? null : new DirectoryInfo(resourcePath);
        if (resourceDir == null && installDir != null)
        {
            resourceDir = new DirectoryInfo(
                Path.Combine(installDir.FullName, "resources"));
            if (!resourceDir.Exists) resourceDir = null;
        }

        if (resourceDir != null && resourceDir.Exists
            && IsDirectory(resourceDir.FullName))
        {
            templatesDir = new DirectoryInfo(
                Path.Combine(resourceDir.FullName, "templates"));
        }

        if (resourcePath != null)
        {
            if (resourceDir != null && !resourceDir.Exists)
            {
                Console.Error.WriteLine(
                    "The SENZING_RESOURCE_DIR environment variable specifies a path that does not exist:");
                Console.Error.WriteLine(
                    "         " + resourcePath);

                throw new InvalidOperationException(
                    "Explicit resource path does not exist: " + resourcePath);
            }

            if (resourceDir == null || !IsDirectory(resourceDir.FullName)
                || templatesDir == null || !templatesDir.Exists
                || !IsDirectory(templatesDir.FullName))
            {
                Console.Error.WriteLine(
                    "The SENZING_RESOURCE_DIR environment variable specifies an invalid "
                        + "resource directory:");
                Console.Error.WriteLine("         " + resourcePath);

                throw new InvalidOperationException(
                    "Explicit resource path is not valid: " + resourcePath);
            }

        }
        else if (resourceDir == null || !resourceDir.Exists || !IsDirectory(resourceDir.FullName)
            || templatesDir == null || !templatesDir.Exists || !IsDirectory(templatesDir.FullName))
        {
            resourceDir = null;
            templatesDir = null;
        }

        // construct and initialize the result
        InstallLocations result = new InstallLocations();
        result.installDir = installDir;
        result.configDir = configDir;
        result.supportDir = supportDir;
        result.resourceDir = resourceDir;
        result.templatesDir = templatesDir;
        result.devBuild = (installDir != null) && ("dist".Equals(installDir.Name, OrdinalIgnoreCase));

        // return the result
        return result;
    }
}
