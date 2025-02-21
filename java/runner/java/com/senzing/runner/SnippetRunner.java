package com.senzing.runner;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import javax.json.*;

import com.senzing.sdk.*;
import com.senzing.sdk.core.*;

import static com.senzing.runner.Utilities.*;
import static com.senzing.sdk.SzFlag.SZ_NO_FLAGS;

/**
 * Helper class to run each of the snippetts.
 */
public class SnippetRunner {
    public static final String SOURCE_KEY_PREFIX = "source.";

    public static final String LOAD_KEY_PREFIX = "load.";

    public static final String INPUT_KEY_PREFIX = "input.";

    public static final String DESTROY_AFTER_KEY = "destroyAfter";

    private static final String DATA_SOURCE = "DATA_SOURCE";
    private static final String RECORD_ID = "RECORD_ID";
    private static final String TEST_SOURCE = "TEST";

    private static final long ONE_MILLION = 1000000L;

    private static final String JAR_PATH = getJarPath();

    private static final int SIGTERM_EXIT_CODE = 143;

    /**
     * Harness for running one or more of the code snippets.
     * 
     * @param args The command line arguments.
     */
    public static void main(String[] args) {
        try {
            SortedMap<String, SortedSet<String>> snippetMap = getSnippetMap();
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
                    System.err.println("The provided Senzing settings were not valid JSON:");
                    System.err.println();
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
                System.exit(1);
                
            } finally {
                env.destroy();
                env = null;
            }

            // execute each snippet
            for (String snippet : snippets) {
                System.out.println();
                long start = System.nanoTime();
                Properties properties = new Properties();
                String resourceName = "/" + snippet.replaceAll("\\.", "/")
                    + ".properties";
                try (InputStream is = SnippetRunner.class.getResourceAsStream(resourceName)) {
                    if (is != null) {
                        properties.load(is);
                    }
                }
            
                System.out.println("Preparing repository for " + snippet + "...");
                env = SzCoreEnvironment.newBuilder().settings(settings).build();
                try {
                    // first purge the repository
                    SzDiagnostic diagnostic = env.getDiagnostic();
                    diagnostic.purgeRepository();

                    // now set the configuration
                    SzConfigManager configMgr = env.getConfigManager();
                    // check if we need to configure sources
                    if (properties.containsKey(SOURCE_KEY_PREFIX + 0)) {
                        SzConfig    config          = env.getConfig();
                        long        handle          = config.createConfig();
                        String      snippetConfig   = null;                        
                        try {
                            for (int index = 0; 
                                 properties.containsKey(SOURCE_KEY_PREFIX + index);
                                 index++) 
                            {
                                String sourceKey = SOURCE_KEY_PREFIX + index;
                                String source = properties.getProperty(sourceKey);
                                source = source.trim();
                                System.out.println("Adding data source: " + source);
                                config.addDataSource(handle, source);
                            }
                            snippetConfig = config.exportConfig(handle);

                        } finally {
                            config.closeConfig(handle);
                        }

                        // register the config
                        long configId = configMgr.addConfig(snippetConfig, snippet);

                        // set the default config to the snippet config
                        configMgr.setDefaultConfigId(configId);

                    } else {
                        // set the default config to the initial default
                        configMgr.setDefaultConfigId(defaultConfigId);
                    }

                    // check if there are files we need to load
                    if (properties.containsKey(LOAD_KEY_PREFIX + 0)) {
                        SzEngine engine = env.getEngine();
                        for (int index = 0; properties.containsKey(LOAD_KEY_PREFIX + index); index++) 
                        {
                            String loadKey = LOAD_KEY_PREFIX + index;
                            String fileName = properties.getProperty(loadKey);
                            fileName = fileName.trim();
                            System.out.println("Loading records from file resource: " + fileName);
                            try (InputStream is = SnippetRunner.class.getResourceAsStream(fileName)) 
                            {
                                if (is == null) {
                                    throw new IllegalArgumentException(
                                        "Missing resource (" + fileName + ") for load file ("
                                        + loadKey + ") for snippet (" + snippet + ")");
                                }
                                InputStreamReader isr = new InputStreamReader(is, UTF_8);
                                BufferedReader br = new BufferedReader(isr);
                                for (String line = br.readLine(); line != null; line = br.readLine()) {
                                    line = line.trim();
                                    if (line.length() == 0) continue;
                                    if (line.startsWith("#")) continue;
                                    JsonObject record = Json.createReader(new StringReader(line)).readObject();
                                    String dataSource = record.getString(DATA_SOURCE, TEST_SOURCE);
                                    String recordId = record.getString(RECORD_ID, null);
                                    SzRecordKey recordKey = SzRecordKey.of(dataSource, recordId);
                                    engine.addRecord(recordKey, line, SZ_NO_FLAGS);
                                }
                            }
                        }
                    }                    

                } catch (SzException e) {
                    e.printStackTrace();
                } finally {
                    env.destroy();
                }
                long duration = (System.nanoTime() - start) / ONE_MILLION;
                System.out.println("Prepared repository for " + snippet + ". (" + duration + "ms)");

                executeSnippet(snippet, installLocations, settings, properties);
            }
            System.out.println();

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String[] createRuntimeEnv(InstallLocations senzingInstall, String settings) {
        Map<String, String> origEnv = System.getenv();
        List<String> envList = new ArrayList<>(origEnv.size() + 10);
        origEnv.forEach((envKey, envVal) -> {
            envList.add(envKey + "=" + envVal);
        });
        envList.add("SENZING_ENGINE_CONFIGURATION_JSON=" + settings);
        return envList.toArray(new String[envList.size()]);
    }

    private static Thread startOutputThread(InputStream stream, PrintStream ps) {
        Thread thread = new Thread(() -> {  
            final String UTF8 = "UTF-8";
            try (InputStreamReader isr = new InputStreamReader(stream, UTF8);
                 BufferedReader br = new BufferedReader(isr)) 
            {
                for (String line = br.readLine(); line != null; line = br.readLine()) {
                    ps.println(line);
                    ps.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        thread.start();
        return thread;
    }

    private static void executeSnippet(String           snippet, 
                                       InstallLocations senzingInstall,
                                       String           settings,
                                       Properties       properties)
            throws Exception 
    {
        String[] cmdArray = new String[] { "java", "-cp", JAR_PATH, snippet };

        String[] runtimeEnv = createRuntimeEnv(senzingInstall, settings);

        System.out.println();
        System.out.println("---------------------------------------");
        System.out.println("Executing " + snippet + "...");
        long start = System.nanoTime();
        Runtime runtime = Runtime.getRuntime();
        Process process = runtime.exec(cmdArray, runtimeEnv);
        Thread errThread = startOutputThread(process.getErrorStream(), System.err);
        Thread outThread = startOutputThread(process.getInputStream(), System.out);
        if (properties != null && properties.containsKey(INPUT_KEY_PREFIX + 0)) {
            try {
                // sleep for 1 second to give the process a chance to start up
                Thread.sleep(1000L);
            } catch (InterruptedException ignore) {
                // ignore interruptions
            }
            PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(process.getOutputStream(), UTF_8));
            for (int index = 0;
                 properties.containsKey(INPUT_KEY_PREFIX + index);
                 index++) 
            {
                String inputLine = properties.getProperty(INPUT_KEY_PREFIX + index);
                System.out.println(inputLine);
                System.out.flush();
                inputLine = (inputLine == null) ? "" : inputLine.trim();
                pw.println(inputLine);
                pw.flush();
            }
        }
        int exitValue = 0;
        int expectedExitValue = 0;
        if (properties.containsKey(DESTROY_AFTER_KEY)) {
            String propValue = properties.getProperty(DESTROY_AFTER_KEY);
            long delay = Long.parseLong(propValue);
            boolean exited = process.waitFor(delay, TimeUnit.MILLISECONDS);
            if (!exited && process.isAlive()) {
                expectedExitValue = SIGTERM_EXIT_CODE;
                System.out.println();
                System.out.println("Runner destroying " + snippet + " process...");
                // NOTE: using process.destroy() does not trigger the registered
                // shutdown hooks in the snippet sub-process for some reason
                Process killer = runtime.exec("kill " + process.pid());
                Thread killerr = startOutputThread(killer.getErrorStream(), System.err);
                Thread killout = startOutputThread(killer.getInputStream(), System.out);
                killer.waitFor();  // wait for the kill process to complete
                killerr.join();
                killout.join();
            }
            exitValue = process.waitFor();

        } else {
            // wait indefinitely for the process to terminate
            exitValue = process.waitFor();
        }

        errThread.join();
        outThread.join();
        if (exitValue != expectedExitValue) {
            throw new Exception("Failed to execute snippet; " + snippet 
                                + " (" + exitValue + ")");
        }
        long duration = (System.nanoTime() - start) / ONE_MILLION;
        System.out.println("Executed " + snippet + ". (" + duration + "ms)");
    }

    private static void printUsage(SortedMap<String, SortedSet<String>> snippetMap) {
        System.err.println("java -jar sz-sdk-snippets.jar [ all | <group> | <snippet> ]*");
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
        System.err.println("  java -jar sz-sdk-snippets.jar loading.LoadRecords loading.LoadViaFutures");
        System.err.println();
        System.err.println("  java -jar sz-sdk-snippets.jar initialization deleting loading.LoadRecords");
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

    private static String getJarPath() throws RuntimeException {
        try {
            final String osName = System.getProperty("os.name");

            boolean windows = false;
            boolean macOS = false;

            String lowerOSName = osName.toLowerCase().trim();
            if (lowerOSName.startsWith("windows")) {
                windows = true;
            } else if (lowerOSName.startsWith("mac") || lowerOSName.indexOf("darwin") >= 0) {
                macOS = true;
            }

            String resourceName = SnippetRunner.class.getSimpleName() + ".class";
            String url = SnippetRunner.class.getResource(resourceName).toString();
            String jarPath = url.replaceAll("jar:file:(.*\\.jar)\\!/.*\\.class", "$1");

            if (windows && jarPath.startsWith("/")) {
                jarPath = jarPath.replaceAll("[/]+([^/].*)", "$1");
            }

            if (windows && jarPath.startsWith("/")) {
                jarPath = jarPath.substring(1);
            }
            return jarPath;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static SortedMap<String, SortedSet<String>> getSnippetMap() throws Exception {
        SortedMap<String, SortedSet<String>> snippetMap = new TreeMap<>();
        File jarFile = new File(JAR_PATH);
        try (FileInputStream fis = new FileInputStream(jarFile); ZipInputStream zis = new ZipInputStream(fis)) {
            for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
                String name = entry.getName();
                if (name.startsWith("com/")) {
                    continue;
                }
                if (name.startsWith("org/")) {
                    continue;
                }
                if (name.startsWith("javax/")) {
                    continue;
                }
                if (name.startsWith("META-INF/")) {
                    continue;
                }
                if (!name.endsWith(".class")) {
                    continue;
                }
                if (name.indexOf('$') >= 0) {
                    continue;
                }
                int index = name.indexOf('/');
                if (index < 0) {
                    continue;
                }
                String group = name.substring(0, index);
                String snippet = name.substring(0, name.length() - ".class".length()).replace('/', '.');
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
    private static String setupTempRepository(InstallLocations senzingInstall) throws Exception {
        File resourcesDir = senzingInstall.getResourceDirectory();
        File templatesDir = senzingInstall.getTemplatesDirectory();
        File configDir = senzingInstall.getConfigDirectory();
        File schemaDir = new File(resourcesDir, "schema");
        File schemaFile = new File(schemaDir, "szcore-schema-sqlite-create.sql");
        File configFile = new File(templatesDir, "g2config.json");

        // lay down the database schema
        File databaseFile = File.createTempFile("G2C-", ".db");
        String jdbcUrl = "jdbc:sqlite:" + databaseFile.getCanonicalPath();

        try (FileReader rdr = new FileReader(schemaFile, UTF_8_CHARSET);
                BufferedReader br = new BufferedReader(rdr);
                Connection conn = DriverManager.getConnection(jdbcUrl);
                Statement stmt = conn.createStatement()) {
            for (String sql = br.readLine(); sql != null; sql = br.readLine()) {
                sql = sql.trim();
                if (sql.length() == 0)
                    continue;
                stmt.execute(sql);
            }
        }

        String supportPath = senzingInstall.getSupportDirectory().getCanonicalPath().replace("\\", "\\\\");
        String configPath = configDir.getCanonicalPath().replace("\\", "\\\\");
        String resourcePath = resourcesDir.toString().replace("\\", "\\\\");
        String databasePath = databaseFile.getCanonicalPath().replace("\\", "\\\\");
        String baseConfig = readTextFileAsString(configFile, UTF_8);
        String settings = """
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

        } catch (SzException e) {
            System.err.println(settings);
            throw e;

        } finally {
            env.destroy();
        }

        return settings;
    }
}
