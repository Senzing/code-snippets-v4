package com.senzing.runner;

import javax.json.JsonObject;
import java.io.File;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import static com.senzing.runner.Utilities.*;

/**
 * Describes the directories on disk used to find the Senzing product
 * installation and the support directories.
 */
public class InstallLocations {
    /**
     * The installation location.
     */
    private File installDir;

    /**
     * The location of the configuration files for the config directory.
     */
    private File configDir;

    /**
     * The location of the resource files for the resource directory.
     */
    private File resourceDir;

    /**
     * The location of the support files for the support directory.
     */
    private File supportDir;

    /**
     * The location of the template files for the template directory.
     */
    private File templatesDir;

    /**
     * Indicates if the installation direction is from a development build.
     */
    private boolean devBuild = false;

    /**
     * Default constructor.
     */
    private InstallLocations() {
        this.installDir = null;
        this.configDir = null;
        this.resourceDir = null;
        this.supportDir = null;
        this.templatesDir = null;
        this.devBuild = false;
    }

    /**
     * Gets the primary installation directory.
     *
     * @return The primary installation directory.
     */
    public File getInstallDirectory() {
        return this.installDir;
    }

    /**
     * Gets the configuration directory.
     *
     * @return The configuration directory.
     */
    public File getConfigDirectory() {
        return this.configDir;
    }

    /**
     * Gets the resource directory.
     *
     * @return The resource directory.
     */
    public File getResourceDirectory() {
        return this.resourceDir;
    }

    /**
     * Gets the support directory.
     *
     * @return The support directory.
     */
    public File getSupportDirectory() {
        return this.supportDir;
    }

    /**
     * Gets the templates directory.
     *
     * @return The templates directory.
     */
    public File getTemplatesDirectory() {
        return this.templatesDir;
    }

    /**
     * Checks if the installation is actually a development build.
     * 
     * @return <code>true</code> if this installation represents a development
     *         build, otherwise <code>false</code>.
     */
    public boolean isDevelopmentBuild() {
        return this.devBuild;
    }

    /**
     * Produces a {@link String} describing this instance.
     * 
     * @return A {@link String} describing this instance.
     */
    public String toString() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        pw.println();
        pw.println("--------------------------------------------------");
        pw.println("installDirectory   : " + this.getInstallDirectory());
        pw.println("configDirectory    : " + this.getConfigDirectory());
        pw.println("supportDirectory   : " + this.getSupportDirectory());
        pw.println("resourceDirectory  : " + this.getResourceDirectory());
        pw.println("templatesDirectory : " + this.getTemplatesDirectory());
        pw.println("developmentBuild   : " + this.isDevelopmentBuild());

        return sw.toString();
    }

    /**
     * Finds the install directories and returns the {@link InstallLocations}
     * instance describing those locations.
     *
     * @param senzingDir The optional Senzing installation directory if one has
     *                   been provided, <code>null</code> otherwise.
     * 
     * @return The {@link InstallLocations} instance describing the install
     *         locations.
     */
    public static InstallLocations findLocations() {
        final String osName = System.getProperty("os.name");

        boolean windows = false;
        boolean macOS = false;

        String lowerOSName = osName.toLowerCase().trim();
        if (lowerOSName.startsWith("windows")) {
            windows = true;
        } else if (lowerOSName.startsWith("mac")
                   || lowerOSName.indexOf("darwin") >= 0)
        {
            macOS = true;
        }

        File installDir = null;
        File configDir = null;
        File resourceDir = null;
        File supportDir = null;
        File templatesDir = null;
        try {
            String defaultInstallPath;
            String defaultConfigPath = null;

            if (windows) {
                defaultInstallPath = "C:\\\\Program Files\\Senzing\\er";
            } else if (macOS) {
                defaultInstallPath = "/opt/senzing/er";
            } else {
                defaultInstallPath = "/opt/senzing/er";
                defaultConfigPath = "/etc/opt/senzing";
            }

            // set the install path if one has been provided
            String installPath = System.getProperty("senzing.install.dir");
            String configPath = System.getProperty("senzing.config.dir");
            String supportPath = System.getProperty("senzing.support.dir");
            String resourcePath = System.getProperty("senzing.resource.dir");

            // try environment variables if system properties don't work
            if (installPath == null || installPath.trim().length() == 0) {
                installPath = System.getenv("SENZING_DIR");
            }
            if (configPath == null || configPath.trim().length() == 0) {
                configPath = System.getenv("SENZING_ETC_DIR");
            }
            if (supportPath == null || supportPath.trim().length() == 0) {
                supportPath = System.getenv("SENZING_DATA_DIR");
            }

            // normalize empty strings as null
            if (installPath != null && installPath.trim().length() == 0) {
                installPath = null;
            }
            if (configPath != null && configPath.trim().length() == 0) {
                configPath = null;
            }
            if (supportPath != null && supportPath.trim().length() == 0) {
                supportPath = null;
            }
            if (resourcePath != null && resourcePath.trim().length() == 0) {
                resourcePath = null;
            }

            // check the senzing directory
            installDir = new File(installPath == null ? defaultInstallPath : installPath);
            if (!installDir.exists()) {
                System.err.println("Could not find Senzing installation directory:");
                System.err.println("     " + installDir);
                System.err.println();
                if (installPath != null) {
                    System.err.println(
                        "Check the -Dsenzing.install.dir=[path] command line option.");
                } else {
                    System.err.println(
                        "Use the -Dsenzing.install.dir=[path] command line option to "
                        + "specify a path");
                }

                return null;
            }

            // normalize the senzing directory
            String dirName = installDir.getName();
            if (installDir.isDirectory() && !dirName.equalsIgnoreCase("er")
                && dirName.equalsIgnoreCase("senzing"))
            {
                // for windows or linux allow the "Senzing" dir as well
                installDir = new File(installDir, "er");
            }

            if (!installDir.isDirectory()) {
                System.err.println("Senzing installation directory appears invalid:");
                System.err.println("     " + installDir);
                System.err.println();
                if (installPath != null) {
                    System.err.println(
                        "Check the -Dsenzing.install.dir=[path] command line option.");
                } else {
                    System.err.println(
                        "Use the -Dsenzing.install.dir=[path] command line option to "
                        + "specify a path");
                }

                return null;
            }

            if (supportPath == null || supportPath.trim().length() == 0) {
                // try to determine the support path
                File installParent = installDir.getParentFile();
                File dataRoot = new File(installParent, "data");
                if (dataRoot.exists() && dataRoot.isDirectory()) {
                    File versionFile = new File(installDir, "szBuildVersion.json");
                    String dataVersion = null;
                    if (versionFile.exists()) {
                        String text = readTextFileAsString(versionFile, UTF_8);
                        JsonObject jsonObject = parseJsonObject(text);
                        dataVersion = (jsonObject.containsKey("DATA_VERSION")
                            ? jsonObject.getString("DATA_VERSION") : null);
                    }

                    // try the data version directory
                    supportDir = (dataVersion == null) ? null : new File(dataRoot, dataVersion.trim());

                    // check if data version was not found
                    if (supportDir == null || !supportDir.exists()) {
                        // look to see if we only have one data version installed
                        File[] versionDirs = dataRoot.listFiles(f -> {
                            return f.getName().matches("\\d+\\.\\d+\\.\\d+");
                        });
                        if (versionDirs.length == 1 && supportDir == null) {
                            // use the single data version found
                            supportDir = versionDirs[0];

                        } else if (versionDirs.length > 1) {
                            System.err.println(
                                    "Could not infer support directory.  Multiple data "
                                    + "directory versions at: ");
                            System.err.println("     " + dataRoot);
                            if (supportDir != null) {
                                System.err.println();
                                System.err.println("Expected to find: " + supportDir);
                            }
                            throw new IllegalStateException(
                                    ((supportDir == null) ? "Could not infer support directory."
                                            : "Could not find support directory (" + supportDir + ").")
                                            + "  Multiple data directory versions found at: " + dataRoot);
                        } else {
                            // no version directories were found, maybe the data root is
                            // the actual support directory (mapped in a docker image)
                            File[] ibmFiles = dataRoot.listFiles(f -> {
                                return f.getName().toLowerCase().endsWith(".ibm");
                            });
                            File libPostalDir = new File(dataRoot, "libpostal");

                            // require the .ibm files and libpostal to exist
                            if (ibmFiles.length > 0 && libPostalDir.exists()) {
                                supportDir = dataRoot;
                            }
                        }
                    }

                }
                if (supportDir == null) {
                    // use the default path
                    supportDir = new File(installDir, "data");
                }

            } else {
                // use the specified explicit path
                supportDir = new File(supportPath);
            }

            if (!supportDir.exists()) {
                System.err.println("The support directory does not exist:");
                System.err.println("         " + supportDir);
                if (supportPath != null) {
                    System.err.println("Check the -Dsenzing.support.dir=[path] command line option.");
                } else {
                    System.err.println("Use the -Dsenzing.support.dir=[path] command line option to "
                        + "specify a path");
                }

                throw new IllegalStateException("The support directory does not exist: " + supportDir);
            }

            if (!supportDir.isDirectory()) {
                System.err.println("The support directory is invalid:");
                System.err.println("         " + supportDir);
                if (supportPath != null) {
                    System.err.println("Check the -Dsenzing.support.dir=[path] command line option.");
                } else {
                    System.err.println("Use the -Dsenzing.support.dir=[path] command line option to "
                        + "specify a path");
                }
                throw new IllegalStateException("The support directory is invalid: " + supportDir);

            }

            // check the config path
            if (configPath != null) {
                configDir = new File(configPath);
            }

            // check for a dev build installation
            if (configDir == null && installDir != null && "dist".equals(installDir.getName())) {
                configDir = new File(installDir, "data");
            }

            // if still null and there is a default, then use it
            if (configDir == null && defaultConfigPath != null) {
                configDir = new File(defaultConfigPath);
                if (!configDir.exists()) {
                    configDir = null;
                }
            }

            // if still null, try to use the install's etc directory
            if (configDir == null && installDir != null) {
                configDir = new File(installDir, "etc");
                if (!configDir.exists()) {
                    configDir = null;
                }                
            }

            if (configPath != null && !configDir.exists()) {
                System.err.println(
                    "The -Dsenzing.config.dir=[path] option specifies a path that does not exist:");
                System.err.println("         " + configPath);

                throw new IllegalStateException("Explicit config path does not exist: " + configPath);
            }
            if (configDir != null && configDir.exists()) {
                if (!configDir.isDirectory()) {
                    System.err.println(
                        "The -Dsenzing.config.dir=[path] option specifies a file, not a directory:");
                    System.err.println("         " + configPath);

                    throw new IllegalStateException(
                        "Explicit config path is not directory: " + configPath);
                }

                String[] requiredFiles = { "cfgVariant.json" };
                List<String> missingFiles = new ArrayList<>(requiredFiles.length);

                for (String fileName : requiredFiles) {
                    File configFile = new File(configDir, fileName);
                    File supportFile = new File(supportDir, fileName);
                    if (!configFile.exists() && !supportFile.exists()) {
                        missingFiles.add(fileName);
                    }
                }
                if (missingFiles.size() > 0 && configPath != null) {
                    System.err.println(
                        "The -Dsenzing.config.dir=[path] option specifies an invalid config directory:");
                    for (String missing : missingFiles) {
                        System.err.println("         " + missing + " was not found");
                    }
                    throw new IllegalStateException(
                            "Explicit config path missing required files: " + missingFiles);
                }
            }

            // now determine the resource path
            resourceDir = (resourcePath == null) ? null : new File(resourcePath);
            if (resourceDir == null) {
                resourceDir = new File(installDir, "resources");
                if (!resourceDir.exists())
                    resourceDir = null;
            }

            if (resourceDir != null && resourceDir.exists() && resourceDir.isDirectory()) {
                templatesDir = new File(resourceDir, "templates");
            }

            if (resourcePath != null) {
                if (!resourceDir.exists()) {
                    System.err.println(
                        "The -Dsenzing.resource.dir=[path] option specifies a path that does not exist:");
                    System.err.println("         " + resourcePath);

                    throw new IllegalStateException(
                        "Explicit resource path does not exist: " + resourcePath);
                }

                if (!resourceDir.isDirectory() || !templatesDir.exists() || !templatesDir.isDirectory()) {
                    System.err.println(
                            "The -Dsenzing.resource.dir=[path] option specifies an invalid "
                            + "resource directory:");
                    System.err.println("         " + resourcePath);

                    throw new IllegalStateException(
                        "Explicit resource path is not valid: " + resourcePath);
                }

            } else if (!resourceDir.exists() || !resourceDir.isDirectory() || !templatesDir.exists()
                    || !templatesDir.isDirectory()) {
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
            result.devBuild = ("dist".equals(installDir.getName()));

            // return the result
            return result;

        } catch (RuntimeException e) {
            e.printStackTrace();
            throw e;

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
