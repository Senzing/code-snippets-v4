package searching;

import java.io.StringReader;
import java.util.*;
import javax.json.*;

import com.senzing.sdk.*;
import com.senzing.sdk.core.SzCoreEnvironment;

import static com.senzing.sdk.SzFlag.*;

/**
 * Provides a simple example of searching for entities in the Senzing repository.
 */
public class SearchRecords {
    public static void main(String[] args) {
        // get the senzing repository settings
        String settings = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
        if (settings == null) {
            System.err.println("Unable to get settings.");
            throw new IllegalArgumentException("Unable to get settings");
        }

        // create a descriptive instance name (can be anything)
        String instanceName = SearchRecords.class.getSimpleName();

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
            for (String criteria : getSearchCriteria()) {
                // call the searchByAttributes() function with default flags
                String result = engine.searchByAttributes(
                    criteria, SZ_SEARCH_BY_ATTRIBUTES_DEFAULT_FLAGS);
                
                JsonObject jsonObj = Json.createReader(
                    new StringReader(result)).readObject();

                System.out.println();
                JsonArray jsonArr = jsonObj.getJsonArray("RESOLVED_ENTITIES");
                if (jsonArr.size() == 0) {
                    System.out.println("No results for criteria: " + criteria);
                } else {
                    System.out.println("Results for criteria: " + criteria);
                    for (JsonObject obj : jsonArr.getValuesAs(JsonObject.class)) {
                        obj = obj.getJsonObject("ENTITY");
                        obj = obj.getJsonObject("RESOLVED_ENTITY");
                        long    entityId    = obj.getJsonNumber("ENTITY_ID").longValue();
                        String  name        = obj.getString("ENTITY_NAME", null);
                        System.out.println(entityId + ": " + name);
                    }
                }
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
     * This is a support method for providing a list of criteria to search on.
     * 
     * @return A {@link List} {@link String} JSON text values desribing the
     *         sets of criteria with which to search.
     */
    public static List<String> getSearchCriteria() {
        List<String> records = new LinkedList<>();
        records.add(
            """
            {
                "NAME_FULL": "Susan Moony",
                "DATE_OF_BIRTH": "15/6/1998",
                "SSN_NUMBER": "521212123"
            }
            """);
        
        records.add(
            """
            {
                "NAME_FIRST": "Robert",
                "NAME_LAST": "Smith",
                "ADDR_FULL": "123 Main Street Las Vegas NV 89132"
            }
            """);
        
        records.add(
            """
            {
                "NAME_FIRST": "Makio",
                "NAME_LAST": "Yamanaka",
                "ADDR_FULL": "787 Rotary Drive Rotorville FL 78720"
            }
            """);

        return records;
    }
}