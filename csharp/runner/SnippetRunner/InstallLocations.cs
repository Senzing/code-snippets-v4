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
        DirectoryInfo homeDir = new DirectoryInfo(Environment.GetFolderPath(
            Environment.SpecialFolder.UserProfile));
        DirectoryInfo homeSenzing = new DirectoryInfo(
            Path.Combine(homeDir.FullName, "senzing"));
        DirectoryInfo homeInstall = new DirectoryInfo(
            Path.Combine(homeSenzing.FullName, "er"));
        DirectoryInfo homeSupport = new DirectoryInfo(
            Path.Combine(homeSenzing.FullName, "data"));

        DirectoryInfo? senzingDir = null;
        DirectoryInfo? installDir = null;
        DirectoryInfo? configDir = null;
        DirectoryInfo? resourceDir = null;
        DirectoryInfo? supportDir = null;
        DirectoryInfo? templatesDir = null;

        string? defaultSenzingPath = null;
        string defaultInstallPath;
        string? defaultConfigPath = null;
        string? defaultSupportPath = null;

        // get the senzing path
        string? senzingPath = Environment.GetEnvironmentVariable("SENZING_PATH");
        if (senzingPath != null && senzingPath.Trim().Length == 0)
        {
            senzingPath = null;
        }

        // check if we are in the dev structure with no senzing path defined
        if (OperatingSystem.IsWindows())
        {
            defaultSenzingPath = homeSenzing.FullName;
            defaultInstallPath = homeInstall.FullName;
            defaultSupportPath = homeSupport.FullName;
        }
        else if (OperatingSystem.IsMacOS())
        {
            defaultSenzingPath = homeSenzing.FullName;
            defaultInstallPath = homeInstall.FullName;
            defaultSupportPath = homeSupport.FullName;
        }
        else if (OperatingSystem.IsLinux())
        {
            defaultSenzingPath = "/opt/senzing";
            defaultInstallPath = defaultSenzingPath + "/er";
            defaultSupportPath = defaultSenzingPath + "/data";
            defaultConfigPath = "/etc/opt/senzing";
        }
        else
        {
            throw new NotSupportedException(
                "Unsupported Operating System: "
                + Environment.OSVersion.Platform);
        }

        // check for senzing environment variables
        string? installPath = Environment.GetEnvironmentVariable(
            "SENZING_DIR");
        string? configPath = Environment.GetEnvironmentVariable(
            "SENZING_CONFIG_DIR");
        string? supportPath = Environment.GetEnvironmentVariable(
            "SENZING_SUPPORT_DIR");
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

        // check for the root senzing directory
        senzingDir = new DirectoryInfo(senzingPath == null ? defaultSenzingPath : senzingPath);
        if (!senzingDir.Exists)
        {
            senzingDir = null;
        }

        // check the senzing install directory
        installDir = (installPath != null)
            ? new DirectoryInfo(installPath)
            : ((senzingDir == null)
                ? new DirectoryInfo(defaultInstallPath)
                : ("dist".Equals(senzingDir.Name, OrdinalIgnoreCase)
                    ? senzingDir : new DirectoryInfo(Path.Combine(senzingDir.FullName, "er"))));

        if (!installDir.Exists || !IsDirectory(installDir.FullName))
        {
            if (!installDir.Exists)
            {
                Console.Error.WriteLine("Could not find Senzing ER installation directory:");
            }
            else
            {
                Console.Error.WriteLine("Senzing ER installation directory appears invalid:");
            }
            Console.Error.WriteLine("     " + installDir.FullName);
            Console.Error.WriteLine();
            if (installPath != null)
            {
                Console.Error.WriteLine(
                        "Check the SENZING_DIR environment variable.");

            }
            else if (senzingPath != null)
            {
                Console.Error.WriteLine(
                        "Check the SENZING_PATH environment variable.");

            }
            else
            {
                Console.Error.WriteLine(
                        "Use the SENZING_PATH environment variable to specify a "
                        + "base Senzing path.");
                Console.Error.WriteLine();
                Console.Error.WriteLine(
                        "Alternatively, use the SENZING_DIR environment variable to "
                        + "specify a Senzing ER path");
            }

            return null;
        }

        // check the senzing support path
        supportDir = (supportPath != null) ? new DirectoryInfo(supportPath) : null;

        // check if support dir is not defined AND we have a local dev build
        if (supportDir == null && installDir != null
            && "dist".Equals(installDir.Name, OrdinalIgnoreCase))
        {
            supportDir = new DirectoryInfo(Path.Combine(installDir.FullName, "data"));
            if (!supportDir.Exists)
            {
                supportDir = null;
            }
        }

        // check if support dir is not defined BUT senzing path is defined
        if (supportDir == null && senzingPath != null && senzingDir != null)
        {
            supportDir = new DirectoryInfo(Path.Combine(senzingDir.FullName, "data"));
            if (!supportDir.Exists)
            {
                supportDir = null;
            }
        }

        // fall back to whatever the default support directory path is
        if (supportDir == null && defaultSupportPath != null)
        {
            supportDir = new DirectoryInfo(defaultSupportPath);
        }

        // verify the discovered support directory
        if ((supportDir != null)
            && ((!supportDir.Exists) || (!IsDirectory(supportDir.FullName))))
        {
            if (!supportDir.Exists)
            {
                Console.Error.WriteLine("Could not find Senzing support directory:");
            }
            else
            {
                Console.Error.WriteLine("Senzing support directory appears invalid:");
            }
            Console.Error.WriteLine("     " + supportDir.FullName);
            Console.Error.WriteLine();
            if (supportPath != null)
            {
                Console.Error.WriteLine(
                        "Check the SENZING_SUPPORT_DIR environment variable.");

            }
            else if (senzingPath != null)
            {
                Console.Error.WriteLine(
                        "Check the SENZING_PATH environment variable.");

            }
            else
            {
                Console.Error.WriteLine(
                        "Use the SENZING_PATH environment variable to specify a "
                        + "base Senzing path.");
                Console.Error.WriteLine();
                Console.Error.WriteLine(
                        "Alternatively, use the SENZING_SUPPORT_DIR environment variable to "
                        + "specify a Senzing ER path.");
            }

            throw new InvalidOperationException(
                    "The support directory does not exist or is invalid: " + supportDir.FullName);
        }

        // now determine the resource path
        resourceDir = (resourcePath != null) ? new DirectoryInfo(resourcePath) : null;

        // try the "resources" sub-directory of the installation
        if (resourceDir == null && installDir != null)
        {
            resourceDir = new DirectoryInfo(Path.Combine(installDir.FullName, "resources"));
            if (!resourceDir.Exists)
            {
                resourceDir = null;
            }
        }

        // set the templates directory if we have the resource directory
        if (resourceDir != null && resourceDir.Exists
            && IsDirectory(resourceDir.FullName))
        {
            templatesDir = new DirectoryInfo(Path.Combine(resourceDir.FullName, "templates"));
        }

        // verify the discovered resource path
        if ((resourceDir == null) || (!resourceDir.Exists)
                || (!IsDirectory(resourceDir.FullName)))
        {
            if (resourceDir == null || !resourceDir.Exists)
            {
                Console.Error.WriteLine("Could not find Senzing resource directory:");
            }
            else
            {
                Console.Error.WriteLine("Senzing resource directory appears invalid:");
            }
            if (resourceDir != null) Console.Error.WriteLine("         " + resourceDir);

            Console.Error.WriteLine();

            if (resourcePath != null)
            {
                Console.Error.WriteLine(
                        "Check the SENZING_RESOURCE_DIR environment variable.");

            }
            else if (senzingPath != null)
            {
                Console.Error.WriteLine(
                        "Check the SENZING_PATH environment variable.");

            }
            else if (installPath != null)
            {
                Console.Error.WriteLine(
                        "Check the SENZING_DIR environment variable.");

            }
            else
            {
                Console.Error.WriteLine(
                        "Use the or SENZING_PATH environment variable to specify a "
                        + "valid base Senzing path.");
                Console.Error.WriteLine();
                Console.Error.WriteLine(
                        "Alternatively, use the SENZING_RESOURCE_DIR environment variable to "
                        + "specify a Senzing resource path.");
            }

            throw new InvalidOperationException(
                    "The resource directory does not exist or is invalid: " + resourceDir?.FullName);
        }


        // check the senzing config path
        configDir = (configPath != null) ? new DirectoryInfo(configPath) : null;

        // check if config dir is not defined AND we have a local dev build
        if (configDir == null && installDir != null && templatesDir != null
            && "dist".Equals(installDir.Name, OrdinalIgnoreCase))
        {
            configDir = templatesDir;
        }

        // check if config dir is still not defined and fall back to default
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
            configDir = new DirectoryInfo(
                Path.Combine(installDir.FullName, "etc"));
            if (!configDir.Exists)
            {
                configDir = null;
            }
        }

        // validate the contents of the config directory
        List<string> missingFiles = new List<string>();
        string missingFilesString = "";

        // check if the config directory does not exist
        if (configDir != null && supportDir != null && configDir.Exists)
        {
            String[] requiredFiles = ["cfgVariant.json"];

            foreach (string fileName in requiredFiles)
            {
                FileInfo? configFile = new FileInfo(
                    Path.Combine(configDir.FullName, fileName));
                FileInfo? supportFile = new FileInfo(
                    Path.Combine(supportDir.FullName, fileName));
                if (!configFile.Exists && !supportFile.Exists)
                {
                    missingFiles.Add(fileName);
                    missingFilesString = (missingFilesString.Length == 0)
                        ? fileName : missingFiles + ", " + fileName;
                }
            }
        }

        // verify the discovered config directory
        if ((configDir == null) || (!configDir.Exists)
                || (!IsDirectory(configDir.FullName)) || (missingFiles.Count > 0))
        {
            if (configDir == null || !configDir.Exists)
            {
                Console.Error.WriteLine("Could not find Senzing config directory:");
            }
            else
            {
                Console.Error.WriteLine("Senzing config directory appears invalid:");
            }
            if (configDir != null) Console.Error.WriteLine("     " + configDir);

            if (missingFiles.Count > 0)
            {
                foreach (string missing in missingFiles)
                {
                    Console.Error.WriteLine(
                            "         " + missing + " was not found in config directory");
                }
            }

            Console.Error.WriteLine();
            if (configPath != null)
            {
                Console.Error.WriteLine(
                        "Check the SENZING_CONFIG_DIR environment variable.");

            }
            else if (senzingPath != null)
            {
                Console.Error.WriteLine(
                        "Check the SENZING_PATH environment variable.");

            }
            else if (installPath != null)
            {
                Console.Error.WriteLine(
                        "Check the SENZING_DIR environment variable.");

            }
            else
            {
                Console.Error.WriteLine(
                        "Use the SENZING_PATH environment variable to specify a "
                        + "valid base Senzing path.");
                Console.Error.WriteLine();
                Console.Error.WriteLine(
                        "Alternatively, use the SENZING_CONFIG_DIR environment variable "
                        + "to specify a Senzing config path.");
            }

            throw new InvalidOperationException(
                    "The config directory does not exist or is invalid: " + configDir
                    + (missingFiles.Count == 0 ? "" : ", missingFiles=[ " + missingFilesString + " ]"));
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
