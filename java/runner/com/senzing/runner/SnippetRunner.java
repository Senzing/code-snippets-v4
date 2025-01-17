package com.senzing.runner;

import java.io.*;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;
import java.util.zip.*;
import javax.json.*;

import com.senzing.sdk.*;
import com.senzing.sdk.core.*;

import static com.senzing.runner.Utilities.*;

/**
 * Helper class to run each of the snippetts.
 */
public class SnippetRunner {
    private static final long ONE_MILLION = 1000000L;
    
    /**
     * Harness for running one or more of the code snippets.
     * @param args The command line arguments.
     */
    public static void main(String[] args) {
        try {
            SortedMap<String,SortedSet<String>> snippetMap = getSnippetMap();
            Set<String> snippetOptions = new LinkedHashSet<>();
            snippetOptions.addAll(snippetMap.keySet());
            for (Set<String> set : snippetMap.values()) {
                snippetOptions.addAll(set);
            }

            if (args.length == 0) {
                printUsage(snippetMap);
                System.exit(1);                
            }
            String settings = System.getProperty("senzing.settings");
            if (settings != null) {
                settings = settings.trim();
            }

            // check for settings in the environment if needed
            if (settings == null) {
                settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
                if (settings != null) {
                    settings = settings.trim();
                }
            }

            // validate the settings if we have them
            if (settings != null) {
                JsonObject settingsJson = null;
                try {
                    settingsJson = parseJsonObject(settings);
                } catch (Exception e) {
                    System.err.println(
                        "The provided Senzing settings were not valid JSON:");
                    System.err.println();
                    System.err.println(toJsonText(settingsJson, true));
                    System.exit(1);
                }
            }

            // validate the SENZING_DIR
            InstallLocations installLocations = null;
            try {
                installLocations = InstallLocations.findLocations();
            
            } catch (Exception e) {
                System.exit(1);
            }

            Set<String> snippets = new LinkedHashSet<>();
            for (int index = 0; index < args.length; index++) {
                String arg = args[index];
                if (arg.equals("all")) {
                    snippetMap.values().forEach(snippetSet -> {
                        for (String snippet : snippetSet) {
                            if (!snippets.contains(snippet)) {
                                snippets.add(snippet);
                            }    
                        }
                    });
                    continue;
                }
                if (!snippetOptions.contains(arg)) {
                    System.err.println("Unrecognized code snippet or snippet group: " + arg);
                    System.exit(1);                    
                }
                if (snippetMap.containsKey(arg)) {
                    for (String snippet : snippetMap.get(arg)) {
                        if (!snippets.contains(snippet)) {
                            snippets.add(snippet);
                        }
                    }
                } else {
                    if (!snippets.contains(arg)) {
                        snippets.add(arg);
                    }
                }
            }

            // check if we do not have settings and if not setup a temporary repository
            if (settings == null) {
                settings = setupTempRepository(installLocations);    
            }

            Long defaultConfigId = null;
            SzEnvironment env = SzCoreEnvironment.newBuilder().settings(settings).build();
            try {
                SzConfigManager configMgr = env.getConfigManager();
                defaultConfigId = configMgr.getDefaultConfigId();

            } catch (SzException e) {
                e.printStackTrace();
            } finally {
                env.destroy();
                env = null;
            }

            // execute each snippet
            for (String snippet : snippets) {
                System.out.println();
                long start = System.nanoTime();
                System.out.println("Preparing repository for " + snippet + "...");
                env = SzCoreEnvironment.newBuilder().settings(settings).build();
                try {
                    SzConfigManager configMgr = env.getConfigManager();
                    configMgr.setDefaultConfigId(defaultConfigId);

                    SzDiagnostic diagnostic = env.getDiagnostic();
                    diagnostic.purgeRepository();

                } catch (SzException e) {
                    e.printStackTrace();
                } finally {
                    env.destroy();
                }
                long duration = (System.nanoTime() - start) / ONE_MILLION;
                System.out.println("Prepared repository for " + snippet + ". (" + duration + "ms)");

                executeSnippet(snippet, installLocations, settings);
            }
            System.out.println();
                        
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void executeSnippet(String snippet, InstallLocations senzingInstall, String settings) 
        throws Exception
    {
        String[]    args            = { settings };
        Class<?>    snippetClass    = Class.forName(snippet);
        Object      snippetInstance = snippetClass.getConstructor().newInstance();
        Method      mainMethod      = snippetClass.getMethod("main", args.getClass());
        Object[]    methodArgs      = { args };
        System.out.println();
        System.out.println("Executing " + snippet + "...");
        long start = System.nanoTime();
        mainMethod.invoke(snippetInstance, methodArgs);
        long duration = (System.nanoTime() - start) / ONE_MILLION;
        System.out.println("Executed " + snippet + ". (" + duration + "ms)");
    }

    private static void printUsage(SortedMap<String,SortedSet<String>> snippetMap) {
        System.err.println("java -jar sz-sdk-snippets.jar [ all | <group> | <snippet> ]* ]");
        System.err.println();
        System.err.println("  - Specifying no arguments will print this message");
        System.err.println("  - Specifying \"all\" will run all snippets");
        System.err.println("  - Specifying one or more groups will run all snippets in those groups");
        System.err.println("  - Specifying one or more snippets will run those snippet");
        System.err.println();
        System.err.println("Examples:");
        System.err.println();
        System.err.println("  java -jar sz-sdk-snippets.jar all");
        System.err.println();
        System.err.println("  java -jar sz-sdk-snippets.jar loading.AddRecords loading.AddFutures");
        System.err.println();
        System.err.println("  java -jar sz-sdk-snippets.jar initialization deleting loading.AddRecords");
        System.err.println();
        System.err.println("Snippet Group Names:");
        snippetMap.keySet().forEach(group -> {
            System.err.println("  - " + group);
        });
        System.err.println();
        System.err.println("Snippet Names:");
        snippetMap.values().forEach(snippetSet -> {
            for (String snippet : snippetSet) {
                System.err.println("  - " + snippet);
            }
        });
        System.err.println();
    }
    
    private static String getJarPath() throws Exception {
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

        String resourceName = SnippetRunner.class.getSimpleName() + ".class";
        String url = SnippetRunner.class.getResource(resourceName).toString();
        String jarPath = url.replaceAll(
            "jar:file:(.*\\.jar)\\!/.*\\.class", "$1");
        
        if (windows && jarPath.startsWith("/")) {
            jarPath = jarPath.replaceAll("[/]+([^/].*)", "$1");
        }

        if (windows && jarPath.startsWith("/")) {
            jarPath = jarPath.substring(1);
        }
        return jarPath;
    }

    private static SortedMap<String,SortedSet<String>> getSnippetMap() 
        throws Exception
    {
        String jarPath = getJarPath();

        SortedMap<String,SortedSet<String>> snippetMap = new TreeMap<>();
        File jarFile = new File(jarPath);
        try (FileInputStream    fis = new FileInputStream(jarFile);
                ZipInputStream     zis = new ZipInputStream(fis)) 
        {
            for (ZipEntry entry = zis.getNextEntry();
                entry != null;
                entry = zis.getNextEntry())
            {
                String name = entry.getName();
                if (name.startsWith("com/")) continue;
                if (name.startsWith("org/")) continue;
                if (name.startsWith("javax/")) continue;
                if (name.startsWith("META-INF/")) continue;
                if (!name.endsWith(".class")) continue;
                int index = name.indexOf('/');
                if (index < 0) continue;
                String group    = name.substring(0, index);
                String snippet  = name.substring(
                    0, name.length() - ".class".length()).replace('/','.');
                SortedSet<String> snippetSet = snippetMap.get(group);
                if (snippetSet == null) {
                    snippetSet = new TreeSet<>();
                    snippetMap.put(group, snippetSet);
                }
                snippetSet.add(snippet);
            }
        }
        return snippetMap;
    }

    /**
     * 
     */
    private static String setupTempRepository(InstallLocations senzingInstall) 
        throws Exception
    {
        File resourcesDir   = senzingInstall.getResourceDirectory();
        File templatesDir   = senzingInstall.getTemplatesDirectory();
        File configDir      = senzingInstall.getConfigDirectory();
        File schemaDir      = new File(resourcesDir, "schema");
        File schemaFile     = new File(schemaDir, "szcore-schema-sqlite-create.sql");
        File configFile     = new File(templatesDir, "g2config.json");

        // lay down the database schema
        File    databaseFile  = File.createTempFile("G2C-", ".db");
        String  jdbcUrl       = "jdbc:sqlite:" + databaseFile.getCanonicalPath();

        try (FileReader     rdr   = new FileReader(schemaFile, UTF_8_CHARSET);
             BufferedReader br    = new BufferedReader(rdr);
             Connection     conn  = DriverManager.getConnection(jdbcUrl);
             Statement      stmt  = conn.createStatement())
        {
            for (String sql = br.readLine(); sql != null; sql = br.readLine()) {
                sql = sql.trim();
                if (sql.length() == 0) continue;
                stmt.execute(sql);
            }
        }

        String supportPath  = senzingInstall.getSupportDirectory().getCanonicalPath();
        String configPath   = configDir.getCanonicalPath();
        String resourcePath = resourcesDir.toString();
        String databasePath = databaseFile.getCanonicalPath();
        String baseConfig   = readTextFileAsString(configFile, UTF_8);
        String settings     = """
            {
                "PIPELINE": {
                    "SUPPORTPATH": "%s",
                    "CONFIGPATH": "%s",
                    "RESOURCEPATH": "%s"
                },
                "SQL": {
                    "CONNECTION": "sqlite3://na:na@%s"
                }
            }
            """.formatted(supportPath, configPath, resourcePath, databasePath).trim();

        SzEnvironment env = SzCoreEnvironment.newBuilder().settings(settings).build();
        try {
            SzConfigManager configMgr = env.getConfigManager();
            
            long configId = configMgr.addConfig(baseConfig, "Default Config");
            configMgr.setDefaultConfigId(configId);

        } finally {
            env.destroy();
        }
        
        return settings;
    }
}
