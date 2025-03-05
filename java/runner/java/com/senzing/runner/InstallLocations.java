package com.senzing.runner;

import java.io.File;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;

import static com.senzing.runner.OperatingSystemFamily.RUNTIME_OS_FAMILY;

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
     * @return <code>true</code> if this installation represents a
     *         development build, otherwise <code>false</code>.
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
     * @return The {@link InstallLocations} instance describing the install
     *         locations.
     */
    public static InstallLocations findLocations() {
        File homeDir        = new File(System.getProperty("user.home"));
        File homeSenzing    = new File(homeDir, "senzing");
        File homeInstall    = new File(homeSenzing, "er");
        File homeSupport    = new File(homeSenzing, "data");
        
        File senzingDir     = null;
        File installDir     = null;
        File configDir      = null;
        File resourceDir    = null;
        File supportDir     = null;
        File templatesDir   = null;
        try {
            String defaultSenzingPath = null;
            String defaultInstallPath;
            String defaultConfigPath = null;
            String defaultSupportPath = null;
            
            // get the senzing path
            String senzingPath = System.getProperty("senzing.path");
            if (senzingPath == null || senzingPath.trim().length() == 0) {
                senzingPath = System.getenv("SENZING_PATH");
            }
            if (senzingPath != null && senzingPath.trim().length() == 0) {
                senzingPath = null;
            }

            // check if we are in the dev structure with no senzing path defined
            switch (RUNTIME_OS_FAMILY) {
                case WINDOWS:
                    defaultSenzingPath = homeSenzing.getCanonicalPath();
                    defaultInstallPath = homeInstall.getCanonicalPath();
                    defaultSupportPath = homeSupport.getCanonicalPath();
                    break;
                case MAC_OS:
                    defaultSenzingPath = homeSenzing.getCanonicalPath();
                    defaultInstallPath = homeInstall.getCanonicalPath();
                    defaultSupportPath = homeSupport.getCanonicalPath();
                    break;
                case UNIX:
                    defaultSenzingPath  = "/opt/senzing";
                    defaultInstallPath  = defaultSenzingPath + "/er";
                    defaultSupportPath  = defaultSenzingPath + "/data";
                    defaultConfigPath   = "/etc/opt/senzing";
                    break;
                default:
                    throw new IllegalStateException(
                            "Unrecognized Operating System: " + RUNTIME_OS_FAMILY);
            }

            // check for senzing system properties
            String installPath = System.getProperty("senzing.install.dir");
            String configPath = System.getProperty("senzing.config.dir");
            String supportPath = System.getProperty("senzing.support.dir");
            String resourcePath = System.getProperty("senzing.resource.dir");

            // try environment variables if system properties don't work
            if (installPath == null || installPath.trim().length() == 0) {
                installPath = System.getenv("SENZING_DIR");
            }
            if (configPath == null || configPath.trim().length() == 0) {
                configPath = System.getenv("SENZING_CONFIG_DIR");
            }
            if (supportPath == null || supportPath.trim().length() == 0) {
                supportPath = System.getenv("SENZING_SUPPORT_DIR");
            }
            if (resourcePath == null || resourcePath.trim().length() == 0) {
                resourcePath = System.getenv("SENZING_RESOURCE_DIR");
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

            // check for the root senzing dir
            senzingDir = new File((senzingPath == null) ? defaultSenzingPath : senzingPath);
            
            if (!senzingDir.exists()) {
                senzingDir = null;
            }

            // check the senzing install directory
            installDir = (installPath != null)
                ? new File(installPath)
                : ((senzingDir == null) 
                    ? new File(defaultInstallPath) 
                    : (senzingDir.getName().equalsIgnoreCase("dist")
                        ? senzingDir : new File(senzingDir, "er")));

            if ((!installDir.exists()) || (!installDir.isDirectory())) {
                if (!installDir.exists()) {
                    System.err.println("Could not find Senzing ER installation directory:");
                } else {
                    System.err.println("Senzing ER installation directory appears invalid:");
                }
                System.err.println("     " + installDir);
                System.err.println();
                if (installPath != null) {
                    System.err.println(
                            "Check the -Dsenzing.install.dir=[path] command line option "
                            + "or SENZING_DIR environment variable.");
                
                } else if (senzingPath != null) {
                    System.err.println(
                            "Check the -Dsenzing.path=[path] command line option "
                            + "or SENZING_PATH environment variable.");
        
                } else {
                    System.err.println(
                            "Use the -Dsenzing.path=[path] command line option or SENZING_PATH "
                            + "environment variable to specify a base Senzing path.");
                    System.err.println();
                    System.err.println(
                            "Alternatively, use the -Dsenzing.install.dir=[path] command line option "
                            + "or SENZING_DIR environment variable to specify a Senzing ER path");
                }

                return null;
            }


            // check the senzing support path
            supportDir = (supportPath != null) ? new File(supportPath) : null;

            // check if support dir is not defined AND we have a local dev build
            if (supportDir == null && installDir != null
                && installDir.getName().equalsIgnoreCase("dist"))
            {
                supportDir = new File(installDir, "data");
                if (!supportDir.exists()) {
                    supportDir = null;
                }
            }

            // check if support dir is not defined BUT senzing path is defined
            if (supportDir == null && senzingPath != null && senzingDir != null) 
            {
                supportDir = new File(senzingDir, "data");
                if (!supportDir.exists()) {
                    supportDir = null;
                }
            }

            // fall back to whatever the default support directory path is
            if (supportDir == null) 
            {
                supportDir = new File(defaultSupportPath);
            }

            // verify the discovered support directory
            if ((!supportDir.exists()) || (!supportDir.isDirectory())) {
                if (!supportDir.exists()) {
                    System.err.println("Could not find Senzing support directory:");
                } else {
                    System.err.println("Senzing support directory appears invalid:");
                }
                System.err.println("     " + supportDir);
                System.err.println();
                if (supportPath != null) {
                    System.err.println(
                            "Check the -Dsenzing.support.dir=[path] command line option "
                            + "or SENZING_SUPPORT_DIR environment variable.");
                
                } else if (senzingPath != null) {
                    System.err.println(
                            "Check the -Dsenzing.path=[path] command line option "
                            + "or SENZING_PATH environment variable.");
        
                } else {
                    System.err.println(
                            "Use the -Dsenzing.path=[path] command line option or SENZING_PATH "
                            + "environment variable to specify a base Senzing path.");
                    System.err.println();
                    System.err.println(
                            "Alternatively, use the -Dsenzing.support.dir=[path] command line option or "
                            + "SENZING_SUPPORT_DIR environment variable to specify a Senzing ER path.");
                }

                throw new IllegalStateException(
                        "The support directory does not exist or is invalid: " + supportDir);
            }

            // now determine the resource path
            resourceDir = (resourcePath != null) ? new File(resourcePath) : null;

            // try the "resources" sub-directory of the installation
            if (resourceDir == null && installDir != null) {
                resourceDir = new File(installDir, "resources");
                if (!resourceDir.exists()) {
                    resourceDir = null;
                }
            }

            // set the templates directory if we have the resource directory
            if (resourceDir != null && resourceDir.exists()
                && resourceDir.isDirectory()) 
            {
                templatesDir = new File(resourceDir, "templates");
            }

            // verify the discovered resource path
            if ((resourceDir == null) || (!resourceDir.exists()) 
                 || (!resourceDir.isDirectory())) 
            {
                if (resourceDir == null || !resourceDir.exists()) {
                    System.err.println("Could not find Senzing resource directory:");
                } else {
                    System.err.println("Senzing resource directory appears invalid:");
                }
                if (resourceDir != null) System.err.println("         " + resourceDir);
                
                System.err.println();

                if (resourcePath != null) {
                    System.err.println(
                            "Check the -Dsenzing.resource.dir=[path] command line option "
                            + "or SENZING_RESOURCE_DIR environment variable.");
                
                } else if (senzingPath != null) {
                    System.err.println(
                            "Check the -Dsenzing.path=[path] command line option "
                            + "or SENZING_PATH environment variable.");
        
                } else if (installPath != null) {
                    System.err.println(
                            "Check the -Dsenzing.install.dir=[path] command line option "
                            + "or SENZING_DIR environment variable.");

                } else {
                    System.err.println(
                            "Use the -Dsenzing.path=[path] command line option or SENZING_PATH "
                            + "environment variable to specify a valid base Senzing path.");
                    System.err.println();
                    System.err.println(
                            "Alternatively, use the -Dsenzing.resource.dir=[path] command line option or "
                            + "SENZING_RESOURCE_DIR environment variable to specify a Senzing resource path.");
                }

                throw new IllegalStateException(
                        "The resource directory does not exist or is invalid: " + resourceDir);
            }

            // check the senzing config path
            configDir = (configPath != null) ? new File(configPath) : null;

            // check if config dir is not defined AND we have a local dev build
            if (configDir == null && installDir != null && templatesDir != null
                && installDir.getName().equalsIgnoreCase("dist"))
            {
                configDir = templatesDir;
            }

            // check if config dir is still not defined and fall back to default
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

            // validate the contents of the config directory
            List<String> missingFiles = new LinkedList<>();

            // check if the config directory does not exist
            if (configDir != null && configDir.exists()) {
                String[] requiredFiles = { "cfgVariant.json" };

                for (String fileName : requiredFiles) {
                    File configFile = new File(configDir, fileName);
                    File supportFile = new File(supportDir, fileName);
                    if (!configFile.exists() && !supportFile.exists()) {
                        missingFiles.add(fileName);
                    }
                }
            }

            // verify the discovered config directory
            if ((configDir == null) || (!configDir.exists()) 
                 || (!configDir.isDirectory()) || (missingFiles.size() > 0))
            {
                if (configDir == null || !configDir.exists()) {
                    System.err.println("Could not find Senzing config directory:");
                } else {
                    System.err.println("Senzing config directory appears invalid:");
                }
                if (configDir != null) System.err.println("     " + configDir);

                if (missingFiles.size() > 0) {
                    for (String missing : missingFiles) {
                        System.err.println(
                                "         " + missing + " was not found in config directory");
                    }
                }

                System.err.println();
                if (configPath != null) {
                    System.err.println(
                            "Check the -Dsenzing.config.dir=[path] command line option "
                            + "or SENZING_CONFIG_DIR environment variable.");
                
                } else if (senzingPath != null) {
                    System.err.println(
                            "Check the -Dsenzing.path=[path] command line option "
                            + "or SENZING_PATH environment variable.");
        
                } else if (installPath != null) {
                    System.err.println(
                            "Check the -Dsenzing.install.dir=[path] command line option "
                            + "or SENZING_DIR environment variable.");

                } else {
                    System.err.println(
                            "Use the -Dsenzing.path=[path] command line option or SENZING_PATH "
                            + "environment variable to specify a valid base Senzing path.");
                    System.err.println();
                    System.err.println(
                            "Alternatively, use the -Dsenzing.config.dir=[path] command line option or "
                            + "SENZING_CONFIG_DIR environment variable to specify a Senzing config path.");
                }

                throw new IllegalStateException(
                        "The config directory does not exist or is invalid: " + configDir
                        + (missingFiles.size() == 0 ? "" : ", missingFiles=[ " + missingFiles + " ]"));
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
