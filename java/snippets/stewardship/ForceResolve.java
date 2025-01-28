package stewardship;

import java.util.*;
import javax.json.*;
import java.io.*;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

/**
 * Provides a simple example of force-resolving records that
 * otherwise will not resolve to one another.
 */
public class ForceResolve {
    private static final String TEST = "TEST";

    public static void main(String[] args) {
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        // create a descriptive instance name (can be anything)
        String instanceName = ForceResolve.class.getSimpleName();

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
            System.out.println("Updating records with TRUSTED_ID_NUMBER to force resolve...");
            SzRecordKey key1 = SzRecordKey.of(TEST, "1");
            SzRecordKey key3 = SzRecordKey.of(TEST, "3");
            
            String record1 = engine.getRecord(key1, SZ_RECORD_DEFAULT_FLAGS);
            String record3 = engine.getRecord(key3, SZ_RECORD_DEFAULT_FLAGS);

            JsonObject obj1 = Json.createReader(new StringReader(record1)).readObject();
            JsonObject obj3 = Json.createReader(new StringReader(record3)).readObject();

            obj1 = obj1.getJsonObject("JSON_DATA");
            obj3 = obj3.getJsonObject("JSON_DATA");

            JsonObjectBuilder job1 = Json.createObjectBuilder(obj1);
            JsonObjectBuilder job3 = Json.createObjectBuilder(obj3);

            for (JsonObjectBuilder job : List.of(job1, job3)) {
                job.add("TRUSTED_ID_NUMBER", "TEST_R1-TEST_R3");
                job.add("TRUSTED_ID_TYPE", "FORCE_RESOLVE");
            }

            record1 = job1.build().toString();
            record3 = job3.build().toString();

            engine.addRecord(key1, record1, SZ_NO_FLAGS);
            engine.addRecord(key3, record3, SZ_NO_FLAGS);

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
            SzRecordKey.of("TEST", "1"),
            """
            {
                "DATA_SOURCE": "TEST",
                "RECORD_ID": "1",
                "PRIMARY_NAME_FULL": "Patrick Smith",
                "AKA_NAME_FULL": "Paddy Smith",
                "ADDR_FULL": "787 Rotary Dr, Rotorville, RI, 78720",
                "PHONE_NUMBER": "787-767-2688",
                "DATE_OF_BIRTH": "1/12/1990"
            }
            """);
        
        records.put(
            SzRecordKey.of("TEST", "2"),
            """
            {
                "DATA_SOURCE": "TEST",
                "RECORD_ID": "2",
                "PRIMARY_NAME_FULL": "Patricia Smith",
                "ADDR_FULL": "787 Rotary Dr, Rotorville, RI, 78720",
                "PHONE_NUMBER": "787-767-2688",
                "DATE_OF_BIRTH": "5/4/1994"
            }
            """);
        
        records.put(
            SzRecordKey.of("TEST", "3"),
            """
            {
                "DATA_SOURCE": "TEST",
                "RECORD_ID": "3",
                "PRIMARY_NAME_FULL": "Pat Smith",
                "ADDR_FULL": "787 Rotary Dr, Rotorville, RI, 78720",
                "PHONE_NUMBER": "787-767-2688"
            }
            """);
       
        return records;
    }
}