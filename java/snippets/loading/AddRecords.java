package loading;

import java.util.Map;
import java.util.LinkedHashMap;

import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.core.SzCoreEnvironment;
import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzException;
import com.senzing.sdk.SzRecordKey;

import static com.senzing.sdk.SzFlag.*;

/**
 * Provides a simple example of adding records to the Senzing repository.
 */
public class AddRecords {
    public static void main(String[] args) {
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        // create a descriptive instance name (can be anything)
        String instanceName = AddRecords.class.getSimpleName();

        // initialize the Senzing environment
        SzEnvironment env = SzCoreEnvironment.newBuilder()
            .settings(settings)
            .instanceName(instanceName)
            .verboseLogging(false)
            .build();
        
        try {
            // get the engine from the environment
            SzEngine engine = env.getEngine();

            // loop through the example records and add them to the repository
            for (Map.Entry<SzRecordKey,String> entry : getRecords().entrySet()) {
                SzRecordKey recordKey = entry.getKey();
                String recordDefinition = entry.getValue();
 
                // call the addRecord() function with no flags
                engine.addRecord(recordKey, recordDefinition, SZ_NO_FLAGS);

                System.out.println("Record " + recordKey.recordId() + " added");
                System.out.flush();
            }

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
            SzRecordKey.of("TEST", "1001"),
            """
            {
                "DATA_SOURCE": "TEST",
                "RECORD_ID": "1001",
                "RECORD_TYPE": "PERSON",
                "PRIMARY_NAME_FIRST": "Robert",
                "PRIMARY_NAME_LAST": "Smith",
                "DATE_OF_BIRTH": "12/11/1978",
                "ADDR_TYPE": "MAILING",
                "ADDR_FULL": "123 Main Street, Las Vegas, NV 89132",
                "PHONE_TYPE": "HOME",
                "PHONE_NUMBER": "702-919-1300",
                "EMAIL_ADDRESS": "bsmith@work.com"
            }
            """);
        
        records.put(
            SzRecordKey.of("TEST", "1002"),
            """
            {
                "DATA_SOURCE": "TEST",
                "RECORD_ID": "1002",
                "RECORD_TYPE": "PERSON",
                "PRIMARY_NAME_FIRST": "Bob",
                "PRIMARY_NAME_LAST": "Smith",
                "PRIMARY_NAME_GENERATION": "II",
                "DATE_OF_BIRTH": "11/12/1978",
                "ADDR_TYPE": "HOME",
                "ADDR_LINE1": "1515 Adela Lane",
                "ADDR_CITY": "Las Vegas",
                "ADDR_STATE": "NV",
                "ADDR_POSTAL_CODE": "89111",
                "PHONE_TYPE": "MOBILE",
                "PHONE_NUMBER": "702-919-1300"
            }
            """);
        
        records.put(
            SzRecordKey.of("TEST", "1003"),
            """
            {
                "DATA_SOURCE": "TEST",
                "RECORD_ID": "1003",
                "RECORD_TYPE": "PERSON",
                "PRIMARY_NAME_FIRST": "Bob",
                "PRIMARY_NAME_LAST": "Smith",
                "PRIMARY_NAME_MIDDLE": "J",
                "DATE_OF_BIRTH": "12/11/1978",
                "EMAIL_ADDRESS": "bsmith@work.com"
            }
            """);

        records.put(
            SzRecordKey.of("TEST", "1004"),
            """
            {
                "DATA_SOURCE": "TEST",
                "RECORD_ID": "1004",
                "RECORD_TYPE": "PERSON",
                "PRIMARY_NAME_FIRST": "B",
                "PRIMARY_NAME_LAST": "Smith",
                "ADDR_TYPE": "HOME",
                "ADDR_LINE1": "1515 Adela Ln",
                "ADDR_CITY": "Las Vegas",
                "ADDR_STATE": "NV",
                "ADDR_POSTAL_CODE": "89132",
                "EMAIL_ADDRESS": "bsmith@work.com"
            }
            """);

        records.put(
            SzRecordKey.of("TEST", "1005"),
            """
            {
                "DATA_SOURCE": "TEST",
                "RECORD_ID": "1005",
                "RECORD_TYPE": "PERSON",
                "PRIMARY_NAME_FIRST": "Rob",
                "PRIMARY_NAME_MIDDLE": "E",
                "PRIMARY_NAME_LAST": "Smith",
                "DRIVERS_LICENSE_NUMBER": "112233",
                "DRIVERS_LICENSE_STATE": "NV",
                "ADDR_TYPE": "MAILING",
                "ADDR_LINE1": "123 E Main St",
                "ADDR_CITY": "Henderson",
                "ADDR_STATE": "NV",
                "ADDR_POSTAL_CODE": "89132"
            }
            """);
        
        return records;
    }
}