# Migrating from Version 3 to Version 4 Senzing Java SDK

## Overview

Version 4.0 of the Senzing Java SDK ([`sz-sdk-java`]) is a significant
change from the Senzing G2 Java SDK v3.x ([`g2-sdk-java`]). While the
functionality is largely the same, the semantics differ significantly
in ways that should make development much more straightforward and
natural for Java developers.

## The G2 Naming and Prefix Retired

The "G2" naming and prefix has been retired. Its replacement depends on the usage as given below.

1. The `G2` prefix for class and interface names (as in `G2Engine`) is replaced with `Sz` (as in `SzEngine`). For example:

   - `G2Engine` -> `SzEngine`
   - `G2Product` -> `SzProduct`
   - `G2Config` -> `SzConfig`
   - `G2ConfigMgr` -> `SzConfigManager`
   - `G2Diagnostic` -> `SzDiagnostic`

1. The `com.senzing.g2.engine` package name has been replaced with `com.senzing.sdk` and `com.senzing.sdk.core`. Previously, you would have used the following import statements:

   ```java
   import com.senzing.g2.engine.*;
   ```

   Replace this with:

   ```java
   import com.senzing.sdk.*;
   import com.senzing.sdk.core.SzCoreEnvironment;
   ```

1. The `g2` directory in `[SENZING_PATH]/g2/lib` (e.g.: `/opt/senzing/g2/lib`) has been renamed to `er` as in `[SENZING_PATH]/er/lib` (e.g.: `/opt/senzing/er/lib`). This will require that your native library path settings be updated as follows:

   - Linux:

     ```console
     export LD_LIBRARY_PATH=$SENZING_PATH/er/lib
     ```

   - macOS:

     ```console
     export DYLD_LIBRARY_PATH=$SENZING_PATH/er/lib:$SENZING_PATH/er/lib/macos:$DYLD_LIBRARY_PATH
     ```

   - Windows:

     ```console
     set Path=%SENZING_PATH%\er\lib;%Path%
     ```

1. The name of the JAR file containing the Java SDK classes has been changed from `g2.jar` to `sz-sdk.jar`. Further, it has been relocated from `[SENZING_PATH]/g2/lib/g2.jar` to `[SENZING_PATH]/er/sdk/java/sz-sdk.jar`. Thus, your `pom.xml` file would change as follows:

   - Version 3.x `pom.xml` dependency definition:

     ```xml
     <dependency>
         <groupId>com.senzing</groupId>
         <artifactId>g2</artifactId>
         <version>3.12.6</version>
         <scope>system</scope>
         <systemPath>${SENZING_PATH}/g2/lib/g2.jar</systemPath>
     </dependency>
     ```

   - Version 4.x `pom.xml` dependency definition:

     ```xml
     <dependency>
         <groupId>com.senzing</groupId>
         <artifactId>sz-sdk</artifactId>
         <version>4.0.0</version>
         <scope>system</scope>
         <systemPath>${SENZING_PATH}/er/sdk/java/sz-sdk.jar</systemPath>
     </dependency>
     ```

## Exceptions Replace Return Codes

Previously, you would have to handle return codes from functions and check for a thread-local error code. Error handling in Version 3.x might have looked like this:

```java
// add a record
int returnCode = engine.addRecord(dataSource, recordId, recordJson, loadId);

// check for an error
if (returnCode != 0) {
    String msg = "Senzing error [ " + engine.getLastExceptionCode()
        + " ]: " + engine.getLastException();
    logError(msg);
    throw new Exception(msg);
}
```

In the version 4.0 SDK, you would simply catch the exception thrown:

```java
try {
    // add a record
    engine.addRecord(SzRecordKey.of(dataSource, recordId), recordJson, SZ_NO_FLAGS);

} catch (SzException e) {
    // log the any Senzing exception
    logError(e.toString());
    throw e;
}
```

Further, an exception hierarchy has been introduced so you can respond differently to different types of exceptions. For example:

```java
try {
    // add the record
    engine.addRecord(SzRecordKey.of(dataSource, recordId), recordJson, SZ_NO_FLAGS);

} catch (SzBadInputException e) {
    // log bad records found in the input file
    logBadRecord(dataSource, recordId, recordJson);

} catch (SzRetryableException e) {
    // queue retryable records to be tried again later
    queueRecordForRetry(dataSource, recordId, recordJson);

} catch (Exception e) {
    // trap any other exception (including SzException)
    logError(e.toString());
    throw e;
}
```

## Unified Initialization and Destruction

Previously, you would initialize and destroy each Senzing component individually. For example, if you were using both the `G2Product` and `G2Engine` components, then your code might look like this:

```java
G2Product product = new G2ProductJNI();
G2Engine engine = new G2EngineJNI();

int returnCode = product.init(moduleName, settings, false);
if (returnCode != 0) {
    throw new Exception(
        "Senzing error [ " + product.getLastExceptionCode()
        + " ]: " + product.getLastException());
}

returnCode = engine.init(moduleName, settings, false);
if (returnCode != 0) {
    product.destroy();
    throw new Exception(
        "Senzing error [ " + engine.getLastExceptionCode()
        + " ]: " + engine.getLastException());
}

try {
    // do some work with the engine and product components
    ...

} finally {
    // destroy each component (typically in reverse order of init)
    engine.destroy();
    product.destroy();
}
```

With the version 4 SDK, you would initialize and destroy the `SzEnvironment`
and obtain the individual components from the `SzEnvironment`:

```java
SzEnvironment env = SzCoreEnvironment.newBuilder()
                                     .moduleName(moduleName)
                                     .settings(settings)
                                     .verbose(false)
                                     .build();

try {
    SzProduct product = env.getProduct();
    SzEngine engine = env.getEngine();

    // do some work with the engine and product components
    ...

} catch (SzException e) {
    handleError(e);

} finally {
    // destroy the environment
    env.destroy();
}
```

## Output Parameters Replaced By Return Values

In the version 3.x SDK the use of return codes for error handling precluded the
possibility of returning results directly. As such many methods used the
`java.lang.StringBuffer` class or `com.senzing.g2.engine.Result` class as parameter
types to populate parameters with the results of the operation.

An operation that leveraged a `StringBuffer` in version 3.x might look like the following:

```java
StringBuffer sb = new StringBuffer();
int returnCode = engine.getEntityByEntityID(entityID, sb);
if (returnCode != 0) {
    throw new Exception(
        "Senzing error [ " + engine.getLastExceptionCode()
        + " ]: " + engine.getLastException());
}
String entityResult = sb.toString();

. . .
```

In version 4.0, the call above would look like this:

```java
try {
    String entityResult = engine.getEntity(entityId, SZ_ENTITY_DEFAULT_FLAGS);

    . . .

} catch (SzException e) {
    handleError(e);
}
```

A function call leveraging the `com.senzing.g2.engine.Result` class in version 3.x
might look like:

```java
Result<Long> result = new Result<>();
int returnCode = engine.exportJSONEntityReport(SZ_EXPORT_DEFAULT_FLAGS, result);
if (returnCode != 0) {
    throw new Exception(
        "Senzing error [ " + engine.getLastExceptionCode()
        + " ]: " + engine.getLastException());
}
long exportHandle = result.getValue();
try {
    . . .

} finally {
    engine.closeExport(exportHandle);
}
```

The same function call using the version 4 SDK would look like the following:

```java
long exportHandle = 0;
try {
    exportHandle = engine.exportJsonEntityReport(SZ_EXPORT_DEFAULT_FLAGS);

    . . .

} catch (SzException e) {
    handleError(e);

} finally {
    if (exportHandle != 0L) {
        engine.closeExportReport(exportHandle);
    }
}
```

## Flags as an Enumerated Type

In version 3.x, many functions were overloaded with variants that accepted an
additional `flags` parameter. This parameter was typed as a `long` integer and
would contain bitwise-OR'd values defined as constants in the `G2Engine`
interface. An example pair of such variant functions from `G2Engine` is:

- `int getEntityByEntityID(long entityID, StringBuffer result)`
- `int getEntityByEntityID(long entityID, long flags, StringBuffer result)`

There were several problems with having these flags represented as `long` value:

1. When debugging or logging the value, it would simply appear numerically.
1. There was no built-in way to lookup a flag value by its name.
1. There was no good way to get a list of all flags.
1. Even if you built a lookup table by name and could get a list of all flags,
   you could not easily get a list of all flags applicable to a specific function call.

In version 4.x, the flag values are now represented by an `enum` type called `SzFlag`
and as such are first-class objects. Using an `enum` type provides the following benefits:

1. Converting an `SzFlag` to a `String` for debugging or logging yields a symbolic name.
1. An `SzFlag` value can be obtained from its symbolic name using the standard
   `valueOf(String)` function.
1. An array of all `SzFlag` values can be obtained via the standard `values()` function.

Because the flags are represented by an `enum` type, the bitwise-OR operation can no
longer be used to combine them as was done in version 3.x. In version 4.x, the `flags`
parameters are instead represented by the type `Set<SzFlag>` so that multiple `SzFlag`
values can be specified as a `Set`. Java provides the `java.util.EnumSet` class which
implements the `Set` interface by backing it with a bit vector (`long`) value for
efficiency. The `EnumSet` class can be easily used to efficiently construct
`Set<SzFlag>` values. However, for ease of use, many predefined unmodifiable/immutable
`Set<SzFlag>` values are included as public constants in the `SzFlag` class including
default and common values for most function calls and `SzFlag.SZ_NO_FLAGS` which can
be used to represent passing no flags. As a further convenience, passing `null` for
the `flags` parameter is always interpreted as `SzFlag.SZ_NO_FLAGS`.

Because aggregate flags are now represented by `Set<SzFlag>`, the `SzFlag` class
provides a useful static `toString(Set<SzFlag>)` utility method for debugging an
instance of `Set<SzFlag>`.

To make this concrete, the following example shows how an entity might be obtained using
the version 3.x SDK:

```java
    import static com.senzing.g2.engine.G2Engine.*;

    . . .

    StringBuffer sb = new StringBuffer();

    int returnCode = engine.getEntityByEntityID(entityID, G2_ENTITY_DEFAULT_FLAGS, sb);
    if (returnCode != 0) {
        throw new Exception(
            "Senzing error [ " + engine.getLastExceptionCode()
            + " ]: " + engine.getLastException());
    }

    String entity = sb.toString();
```

In version 4.x, the same code would look like:

```java
    import static com.senzing.sdk.SzFlag.*;

    . . .

    try {
        String entity = engine.getEntity(entityId, SZ_ENTITY_DEFAULT_FLAGS);

    } catch (SzException e) {
        // handle exception or rethrow
    }
```

You might notice that there is a lingering shortcoming from version 3.x that has not
been discussed. Specifically, how to obtain the set of `SzFlag` values that are
applicable to a specific method. To address this, we introduce a supporting `enum`
type called `SzFlagUsageGroup` and leverage the fact that `SzFlag` instances are now
first-class objects and as such can have properties and methods. Every `SzFlag`
instance has a `getGroups()` method to return its "groups" property. This property
is represented by the type `Set<SzFlagUsageGroup>`. This property indicates the
various usage groups that a flag can belong to in order to determine where the flag
applies. Conversely, each `SzFlagUsageGroup` instance has a `getFlags()` method
which returns a `Set<SzFlag>` containing all the `SzFlag` instances belonging to
that group. Every method that takes `flags` parameter is documented to reference
the enumerated `SzFlagUsageGroup` instance that holds the flags applicable to that
method.

At the very basic level, this allows quick cross-referencing in the Javadoc
documentation to see which flags apply to which methods. From a programmatic level,
this can be used to verify if flags being specified to a function will have an affect
or simply be ignored or in some cases to help construct user interface elements
allowing a user to control the level of detail obtained when querying the entity
repository.

## "With Info" Moves From Function to Flag

In version 3.x, functions that modified the data in the entity repository by
triggering entity resolution typically had a variant version of the function with the
suffix `WithInfo`. This variant would take an additional parameter of type `StringBuffer`
in order to return the "info" JSON that would identify which entities were affected by
the operation -- allowing the caller to take appropriate action. Examples of such
functions from the `G2Engine` interface were:

- `int addRecord(String dataSource, String recordID, String jsonData)`
- `int addRecordWithInfo(String dataSource, String recordID, String jsonData, StringBuffer result)`

- `int deleteRecord(String dataSource, String recordID)`
- `int deleteRecordWithInfo(String dataSource, String recordID, StringBuffer result)`

- `int reevaluateRecord(String dataSource, String recordID)`
- `int reevaluateRecordWithInfo(String dataSource, String recordID, StringBuffer result)`

- `int reevaluateEntity(long entityID)`
- `int reevaluateEntityWithInfo(long entityID, StringBuffer result)`

In the version 4.0 SDK, the "with info" functionality has been rolled into a new `flags`
parameter using the newly defined `SzFlag.WITH_INFO` flag value. This flag value is
applicable to functions that would trigger modification to the data in the entity repository.

For example, in version 3.x you might have done the following:

```java
    StringBuffer sb = new StringBuffer();
    int returnCode = engine.reevaluateEntityWithInfo(entityID, sb);
    if (returnCode != 0) {
        throw new Exception(
            "Senzing error [ " + engine.getLastExceptionCode()
            + " ]: " + engine.getLastException());
    }

    String info = sb.toString();
```

In the version 4.0 SDK, this would look like:

```java
    import static com.senzing.sdk.SzFlag.*;

    . . .

    try {
        String info = engine.reevaluateEntity(entityID, EnumSet.of(SZ_WITH_INFO));

    } catch (SzException e) {
        // handle and/or rethrow exception
    }
```

For further simplicity, `SzFlag.SZ_WITH_INFO_FLAGS` is a pre-defined unmodifiable/immutable `Set<SzFlag>` constant you can use:

```java
    import static com.senzing.sdk.SzFlag.*;

    . . .

    try {
        String info = engine.reevaluateEntity(entityID, SZ_WITH_INFO_FLAGS);

    } catch (SzException e) {
        // handle and/or rethrow exception
    }
```

And if you do not want the "info" in version 4.x you can simply pass the predefined
`SzFlag.SZ_NO_FLAGS` to the function call:

```java
    import static com.senzing.sdk.SzFlag.*;

    . . .
    try {
        engine.reevaluateEntity(entityID, SZ_NO_FLAGS);

    } catch (SzException e) {
        // handle and/or rethrow exception
    }
```

Or you can pass `null` for your `flags` to indicate the same as `SzFlag.SZ_NO_FLAGS`:

```java
    try {
        engine.reevaluateEntity(entityID, null);

    } catch (SzException e) {
        // handle and/or rethrow exception
    }
```

## Simplified Function Names

In the version 3.x SDK, the Java function names mostly mirrored the underlying
native C SDK function names with function name overloading employed only to handle
function variants that had an optional "flags" parameter. As such, many similar
functions had their names differ slightly by suffix to indicate alternate parameters
or function results. Examples of such suffixes were:

- `ByRecordID`
- `ByEntityID`
- `ExcludingByRecordID`
- `ExcludingByEntityID`
- `IncludingSourceByRecordID`
- `IncludingSourceByEntityID`
- `WithInfo`

This has been simplified through increased use of function overloading such that
more functions have the same name, but differ only by their parameter types.
For example, in version 3.x you would have the following functions on `G2Engine`:

- `int getEntityByEntityID(long entityID, long flags, StringBuffer result)`
- `int getEntityByRecordID(String dataSource, String recordID, long flags, StringBuffer result)`

In The version 4.0 SDK these are replaced in `SzEngine` by:

- `String getEntity(long entityId, Set<SzFlag> flags)`
- `String getEntity(SzRecordKey recordKey, Set<SzFlag> flags)`

## Say Goodbye to JSON-formatted Input Parameters

Another side-effect of the version 3.x SDK's mirroring of the native C SDK was that multi-valued
parameters were often represented as JSON `String` values. Some examples of functions where
such parameters existed are:

- `G2Engine.findPathExcludingByEntityID(...)`
- `G2Engine.findPathExcludingByRecordID(...)`
- `G2Engine.findPathIncludingSourceByEntityID(...)`
- `G2Engine.findPathIncludingSourceByRecordID(...)`
- `G2Engine.findNetworkByEntityID(...)`
- `G2Engine.findNetworkByRecordID(...)`

This meant that in order to do something like specify multiple record ID values you would have
to covert your collection of record ID's into a JSON `String` that might look like:

```json
{
  "ENTITIES": [
    {
      "DATA_SOURCE": "CUSTOMERS",
      "RECORD_ID": "ABC123"
    },
    {
      "DATA_SOURCE": "CUSTOMERS",
      "RECORD_ID": "DEF456"
    },
    {
      "DATA_SOURCE": "EMPLOYEES",
      "RECORD_ID": "AAA001"
    }
  ]
}
```

Or similarly, if specifying multiple entity ID's you would format your collection of entity ID's
into a JSON `String` that might look like:

```json
{
  "ENTITIES": [
    { "ENTITY_ID": 123456 },
    { "ENTITY_ID": 789012 },
    { "ENTITY_ID": 345678 }
  ]
}
```

A collection of data source codes to require for inclusion in a "find path" operation might look like:

```json
{ "DATA_SOURCES": ["WATCHLIST"] }
```

There are other examples from the `G2Config` interface that are addressed in the next section, but
suffice it to say that this was an area that we felt we could improve the SDK in version 4.0.

The version 4.0 SDK allows these parameters to be specified as normal Java types rather than
encoded `String` values. For example, a set of required data sources is now specified using the
type `Set<String>` where a `null` parameter value is synonymous with an empty `Set`.

There is, however, a slight catch here due to the details of the Java Language Specification and
function overloading. Version 4.0 reduces the long list of function names from above (including
the basic `findPathByEntityID()` and `findPathByRecordID()` functions) to simply `findPath` and
`findNetwork` with overloaded parameter types. To accomplish this, some new types had to be
introduced:

1. The `SzRecordKey` type was introduced to represent a tuple of `String` values for the
   data source code and record ID pair that identify a record since Java has no native
   representation for tuples. This is a `record` type in Java with `dataSourceCode` and
   `recordId` properties that cannot be modified after the `SzRecordKey` is constructed.
   One can construct an instance using the public constructor or the static
   `SzRecordKey.of(String,String)` function.

1. The `SzRecordKeys` and `SzEntityIds` types were introduced to represent `Set<SzRecordKey>`
   and `Set<Long>`, respectively. This is because at runtime, a `Set<SzRecordKey>` and
   `Set<Long>` are still just a plain old `java.util.Set` containing any `Object` due to type
   erasure on generic types and as such are not different types for the purpose of method
   overloading. These classes implement `Set<SzRecordKey>` and `Set<Long>`, respectively, and
   provide convenient constructors and corresponding static `of()` factory methods for between
   one (1) and ten (10) elements as well as variable-argument variants for specifying more.
   Further, the constructed `Set` instances will be unmodifiable/immutable once constructed.

In version 3.x, a call to find a complex entity path by record ID might look like:

```java
    import static com.senzing.g2.engine.G2Engine.*;

    . . .

    StringBuffer sb = new StringBuffer();

    String excluded = "{ \"ENTITIES\": [ { \"DATA_SOURCE\": \"UNDERCOVER\", \"RECORD_ID\": \"XYZ987\" } ] }";

    String sources = "{ \"DATA_SOURCES\": [ \"WATCHLIST\" ] }";

    int returnCode = engine.findPathIncludingSourceByRecordID(
        "CUSTOMERS",                // starting data source code
        "ABC123",                   // starting record ID
        "EMPLOYEES",                // ending data source code
        "DEF456",                   // ending record ID
        10,                         // max degrees
        excluded,                   // excluded entities by record key
        sources,                    // required data sources
        G2_FIND_PATH_DEFAULT_FLAGS, // flags
        sb);

    if (returnCode != 0) {
        throw new Exception(
            "Senzing error [ " + engine.getLastExceptionCode()
            + " ]: " + engine.getLastException());
    }

    String path = sb.toString();

    . . .
```

In the version 4.0 SDK, the same operation to get the path by record ID would look like:

```java
    import static com.senzing.sdk.SzFlag.*;

    . . .

    try {
        String path = engine.findPath(
            SzRecordKey.of("CUSTOMERS", "ABC123"),      // starting record key
            SzRecordKey.of("EMPLOYEES", "DEF456"),      // ending record key
            10,                                         // max degrees
            SzRecordKeys.of("UNDERCOVER", "XYZ789"),    // excluded entities by record key
            Set.of("WATCHLIST"),                        // required data sources
            SZ_FIND_PATH_DEFAULT_FLAGS);                // flags

        . . .

    } catch (SzException e) {
        // handle and/or rethrow exception
    }
```

Similarly, a call to find an entity network by entity ID's with the version 3.x SDK might look like:

```java
    import static com.senzing.g2.engine.G2Engine.*;

    . . .

    StringBuffer sb = new StringBuffer();

    String entities = "{ \"ENTITIES\": [ "
        + "{ \"ENTITY_ID\": 123456 }, "
        + "{ \"ENTITY_ID\": 789012 }, "
        + "{ \"ENTITY_ID\": 345678 } ] }";

    int returnCode = engine.findNetworkByEntityID(
        entities,                       // entities to include
        10,                             // max degrees
        1,                              // build-out degrees
        100,                            // max entities
        G2_FIND_NETWORK_DEFAULT_FLAGS,  // flags
        sb);

    if (returnCode != 0) {
        throw new Exception(
            "Senzing error [ " + engine.getLastExceptionCode()
            + " ]: " + engine.getLastException());
    }

    String network = sb.toString();

    . . .
```

With the version 4.0 SDK, this would change as follows:

```java
    import static com.senzing.sdk.SzFlag.*;

    . . .

    try {
        String network = engine.findNetwork(
            SzEntityIds.of(123456, 789012, 345678),     // entities to include
            10,                                         // max degrees
            1,                                          // build-out degrees
            100,                                        // max entities
            SZ_FIND_NETWORK_DEFAULT_FLAGS,              // flags

        . . .

    } catch (SzException e) {
        // handle and/or rethrow exception
    }
```

## Working with Configuration

A fairly significant change in version 4.0 versus the version 3.x SDK pertains to
the semantics surrounding working with configuration. In version 3.x, the `G2Config`
module and `G2ConfigManager` were independently initialized and managed. The `G2Config`
module was a functional interface used to work with in-memory configuration instances
which were accessed via a numeric handle and required the closing of that handle when
complete.

In order to create a config with a specific data source and set it as the default
config in version 3.x you might do the following:

```java
G2Config    config      = new G2ConfigJNI();
G2ConfigMgr configMgr   = new G2ConfigMgrJNI();

int returnCode = config.init(moduleName, settings, false);
if (returnCode != 0) {
    throw createException(config);
}

returnCode = configMgr.init(moduleName, settings, false);
if (returnCode != 0) {
    config.destroy();
    throw createException(config);
}

Result<Long> result         = new Result<>();
Long         configHandle   = null;
String       configDef      = null;

try {
    int returnCode = config.create(result);
    if (returnCode != 0) {
        throw createException(config);
    }

    // get the config handle
    configHandle = result.getValue();

    // add a data source to the associated config
    StringBuffer sb = new StringBuffer();
    returnCode = config.addDataSource(configHandle, "{ \"DSRC_CODE\": \"CUSTOMERS\" }", sb);
    if (returnCode != 0) {
        throw createException(config);
    }

    // export the data source as text
    sb.delete(0, sb.length());
    returnCode = config.save(configHandle, sb);
    if (returnCode != 0) {
        throw createException(config);
    }

    configDef = sb.toString();

    // register the configuration
    returnCode = configMgr.addConfig(configDef, "Data Sources: CUSTOMERS", result);
    if (returnCode != 0) {
        throw createException(configMgr);
    }

    long configID = result.getValue();

    // set the configuration as the default
    returnCode = configMgr.setDefaultConfigID(configID);
    if (returnCode != 0) {
        throw createException(configMgr);
    }

} finally {
    // close the open config handle if we have one
    if (configHandle != null) {
        config.close(configHandle);
    }

    // destroy the config manager module
    configMgr.destroy();

    // destroy the config module
    config.destroy();
}
```

In version 4.0, the `SzConfig` module is subordinate to the `SzConfigManager` and
literally represents a Senzing configuration as an object. It removes the hassle
of dealing with "config handles" that have to be closed as well:

```java
SzEnvironment env = SzCoreEnvironment.newBuilder()
                                     .moduleName(moduleName)
                                     .settings(settings)
                                     .verbose(false)
                                     .build();

try {
    // get the config manager from the environment
    SzConfigManager configMgr = env.getConfigManager();

    // create a new config using the config manager
    SzConfig config = configMgr.createConfig();

    // add the data source (no need to JSON-encode the parameter anymore)
    config.registerDataSource("CUSTOMERS");

    // register and set the default config in one shot with an auto-comment
    long configId = configMgr.setDefaultConfig(config.export());

} catch (SzException e) {
    handleError(e);

} finally {
    // destroy the environment
    env.destroy();
}
```

## Redo Processing

Processing redo records has been simplified as well. In version 3.x, there were
four (4) functions on the `G2Engine` interface whereby a redo record could be
processed with two of them producing "info" responses and two of them producing
no response. These were:

- `G2Engine.process(String)`
- `G2Engine.process(String, StringBuffer)`
- `G2Engine.processRedoRecord(StringBuffer)`
- `G2Engine.processRedoRecordWithInfo(long, StringBuffer, StringBuffer)`

The `processRedoRecord()` functions were confusing because they took a `StringBuffer`
parameter for the redo record itself rather than `String`. As such, the `process(String)`
function was typically used **or** the inconsistently named `process(String,StringBuffer)`
function if an "info" response was required (other such functions in the 3.x SDK would
have been named with the `WithInfo` suffix).

In version 3.x, processing redo records might look like:

```java
    long redoCount = engine.countRedoRecords();
    if (redoCount < 0) {
        throw createException(engine);
    }

    StringBuffer sb = new StringBuffer();

    // loop over the redo count
    while (redoCount > 0L) {
        // decrement the count
        redoCount--;

        // get the next redo record
        sb.delete(0, sb.length());
        int returnCode = engine.getRedoRecord(sb);
        if (returnCode != 0) {
            throw createException(engine);
        }

        String redo = sb.toString();

        // process the redo
        returnCode = engine.process(redo);
        if (returnCode != 0) {
            throw createException(engine);
        }
    }

    . . .
```

In version 4.0, this is clarified and simplified so that processing redo records is
always the same with two ways to loop over available redo records:

```java
    import static com.senzing.sdk.SzFlag.*;

    . . .

    long redoCount = engine.countRedoRecords();

    StringBuffer sb = new StringBuffer();

    // loop over the redo count
    while (redoCount > 0L) {
        // decrement the count
        redoCount--;

        // get the next redo record
        String redo = engine.getRedoRecord();

        // process the redo
        engine.processRedoRecord(redo, SZ_NO_FLAGS);
    }

    . . .

```

## Code Snippets

[Version 4.x Java Code Snippets](https://github.com/Senzing/code-snippets-v4/tree/main/python) provide examples of
how to achieve many common tasks using the Version 4.x SDK.

## Additional Differences

Additional differences are described in the [breaking changes][breaking-changes] document.

[`sz-sdk-java`]: https://github.com/senzing-garage/sz-sdk-java
[`g2-sdk-java`]: https://github.com/senzing-garage/g2-sdk-java
[breaking-changes]: https://www.senzing.com/docs/release/4/4_0_breaking_changes/
