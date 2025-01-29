package stewardship;

import java.util.*;
import javax.json.*;
import java.io.*;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

/**
 * Provides a simple example of force-unresolving records that
 * otherwise will not resolve to one another.
 */
public class ForceUnresolve {
    private static final String TEST = "TEST";

    public static void main(String[] args) {
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        // create a descriptive instance name (can be anything)
        String instanceName = ForceUnresolve.class.getSimpleName();

        // initialize the Senzing environment
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(instanceName)
            .verboseLogging(false)
            .build();
        
        try {
            // get the engine from the environment
            SzEngine engine = env.getEngine();

            Map<SzRecordKey, String> recordMap = getRecords();
            // loop through the example records and add them to the repository
            for (Map.Entry<SzRecordKey,String> entry : recordMap.entrySet()) {
                SzRecordKey recordKey = entry.getKey();
                String recordDefinition = entry.getValue();
 
                // call the addRecord() function with no flags
                engine.addRecord(recordKey, recordDefinition, SZ_NO_FLAGS);

                System.out.println("Record " + recordKey.recordId() + " added");
                System.out.flush();
            }

            System.out.println();
            for (SzRecordKey recordKey : recordMap.keySet()) {
                String result = engine.getEntity(recordKey, SZ_ENTITY_BRIEF_DEFAULT_FLAGS);
                JsonObject jsonObj = Json.createReader(new StringReader(result)).readObject();
                long entityId = jsonObj.getJsonObject("RESOLVED_ENTITY")
                                       .getJsonNumber("ENTITY_ID").longValue();
                System.out.println(
                    "Record " + recordKey + " originally resolves to entity " + entityId);
            }
            System.out.println();
            System.out.println("Updating records with TRUSTED_ID to force unresolve...");
            SzRecordKey key4 = SzRecordKey.of(TEST, "4");
            SzRecordKey key6 = SzRecordKey.of(TEST, "6");
            
            String record4 = engine.getRecord(key4, SZ_RECORD_DEFAULT_FLAGS);
            String record6 = engine.getRecord(key6, SZ_RECORD_DEFAULT_FLAGS);

            JsonObject obj4 = Json.createReader(new StringReader(record4)).readObject();
            JsonObject obj6 = Json.createReader(new StringReader(record6)).readObject();

            obj4 = obj4.getJsonObject("JSON_DATA");
            obj6 = obj6.getJsonObject("JSON_DATA");

            JsonObjectBuilder job4 = Json.createObjectBuilder(obj4);
            JsonObjectBuilder job6 = Json.createObjectBuilder(obj6);

            job4.add("TRUSTED_ID_NUMBER", "TEST_R4-TEST_R6");
            job4.add("TRUSTED_ID_TYPE", "FORCE_UNRESOLVE");

            job6.add("TRUSTED_ID_NUMBER", "TEST_R6-TEST_R4");
            job6.add("TRUSTED_ID_TYPE", "FORCE_UNRESOLVE");

            record4 = job4.build().toString();
            record6 = job6.build().toString();

            engine.addRecord(key4, record4, SZ_NO_FLAGS);
            engine.addRecord(key6, record6, SZ_NO_FLAGS);

            System.out.println();
            for (SzRecordKey recordKey : recordMap.keySet()) {
                String result = engine.getEntity(recordKey, SZ_ENTITY_BRIEF_DEFAULT_FLAGS);
                JsonObject jsonObj = Json.createReader(new StringReader(result)).readObject();
                long entityId = jsonObj.getJsonObject("RESOLVED_ENTITY")
                                       .getJsonNumber("ENTITY_ID").longValue();
                System.out.println(
                    "Record " + recordKey + " now resolves to entity " + entityId);
            }
            System.out.println();

        } catch (SzException e) {
            // handle any exception that may have occurred
            System.err.println("Senzing Error Message : " + e.getMessage());
            System.err.println("Senzing Error Code    : " + e.getErrorCode());
            e.printStackTrace();
            throw new RuntimeException(e);

        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof RuntimeException) {
                throw ((RuntimeException) e);
            }
            throw new RuntimeException(e);

        } finally {
            // IMPORTANT: make sure to destroy the environment
            env.destroy();
        }

    }

    /**
     * This is a support method for providing example records to add.
     * 
     * @return A {@link Map} of {@link SzRecordKey} keys to {@link String}
     *         JSON text values desribing the records to be added.
     */
    public static Map<SzRecordKey, String> getRecords() {
        Map<SzRecordKey, String> records = new LinkedHashMap<>();
        records.put(
            SzRecordKey.of("TEST", "4"),
            """
            {
                "DATA_SOURCE": "TEST",
                "RECORD_ID": "4",
                "PRIMARY_NAME_FULL": "Elizabeth Jonas",
                "ADDR_FULL": "202 Rotary Dr, Rotorville, RI, 78720",
                "SSN_NUMBER": "767-87-7678",
                "DATE_OF_BIRTH": "1/12/1990"
            }
            """);
        
        records.put(
            SzRecordKey.of("TEST", "5"),
            """
            {
                "DATA_SOURCE": "TEST",
                "RECORD_ID": "5",
                "PRIMARY_NAME_FULL": "Beth Jones",
                "ADDR_FULL": "202 Rotary Dr, Rotorville, RI, 78720",
                "SSN_NUMBER": "767-87-7678",
                "DATE_OF_BIRTH": "1/12/1990"
            }
            """);
        
        records.put(
            SzRecordKey.of("TEST", "6"),
            """
            {
                "DATA_SOURCE": "TEST",
                "RECORD_ID": "6",
                "PRIMARY_NAME_FULL": "Betsey Jones",
                "ADDR_FULL": "202 Rotary Dr, Rotorville, RI, 78720",
                "PHONE_NUMBER": "202-787-7678"
            }
            """);
       
        return records;
    }
}