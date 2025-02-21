# Java Snippets

The Java snippets are contained in the `snippets` directory under various Java package directories.

## Building

The Java snippets can built using the `pom.xml` in this directory using `mvn package`.  The result will be the `sz-sdk-snippets.jar` file in the `target` directory.

## Running

There are several ways to run the code snippets.

### Run Directly

You may run any individual Snippet class directly providing you have a Senzing repository to run it with and the `SENZING_ENGINE_CONFIGURATION_JSON` environment variable set for connecting to that repository.  Many of the snippets will find a default data file to run with if run from this directory, but also allow the caller to use a different data file if given by the first command-line argument.

1. Run a snippet that takes no command-line arguments.

    ```console
    java -cp target/sz-sdk-snippets.jar loading.LoadRecords
    ```

2. Run a snippet and override the input file using command-line arguments

    ```console
    java -cp target/sz-sdk-snippets.jar loading.LoadRecordsViaLoop ../../resources/data/load-500-with-errors.jsonl
    ```

### Run via Runner

The `com.senzing.runner.SnippetRunner` class will run one or more snippets for you and create a temporary Senzing repository to run
then against.  This is the `Main-Class` of the `sz-sdk-snippets.jar` file so it can be executed using `java -jar target/sz-sdk-snippets.jar`.

**NOTE:** When code snippets are run this way you cannot specify command-line arguments for individual snippets, nor can you respond to command-line input requests (they will be automatically be responded by the runner -- including forced termination of a snippet that is intended to run indefinitely).

1. Execute all code snippets:

    ```console
    java -jar target/sz-sdk-snippets.jar all
    ```

2. Execute all code snippets in a Java package:

    ```console
    java -jar target/sz-sdk-snippets.jar loading
    ```

3. Execute all code snippets from multiple packages:

    ```console
    java -jar target/sz-sdk-snippets.jar loading redo
    ```

4. Execute specific code snippets:

    ```console
    java -jar target/sz-sdk-snippets.jar loading.LoadViaLoop loading.LoadViaQueue
    ```

5. Mix and match packages with individual snippets:

    ```console
    java -jar target/sz-sdk-snippets.jar redo loading.LoadViaLoop
    ```

6. Generate a help message by specifying no arguments:

    ```console
    java -jar target/sz-sdk-snippets.jar

    java -jar sz-sdk-snippets.jar [ all | <group> | <snippet> ]*
    
    - Specifying no arguments will print this message
    - Specifying "all" will run all snippets
    - Specifying one or more groups will run all snippets in those groups
    - Specifying one or more snippets will run those snippet

    Examples:

      java -jar sz-sdk-snippets.jar all

      java -jar sz-sdk-snippets.jar loading.LoadRecords loading.LoadViaFutures

      java -jar sz-sdk-snippets.jar initialization deleting loading.LoadRecords

    Snippet Group Names:
        - configuration
        - deleting
        - information
        - initialization
        - loading
        - redo
        - searching
        - stewardship

    Snippet Names:
        - configuration.AddDataSources
        - configuration.InitDefaultConfig
        - deleting.DeleteViaFutures
        - deleting.DeleteViaLoop
        - deleting.DeleteWithInfoViaFutures
        - information.CheckDatastorePerformance
        - information.GetDatastoreInfo
        - information.GetLicense
        - information.GetVersion
        - initialization.EnginePriming
        - initialization.EnvironmentAndHubs
        - initialization.PurgeRepository
        - loading.LoadRecords
        - loading.LoadTruthSetWithInfoViaLoop
        - loading.LoadViaFutures
        - loading.LoadViaLoop
        - loading.LoadViaQueue
        - loading.LoadWithInfoViaFutures
        - loading.LoadWithStatsViaLoop
        - redo.LoadWithRedoViaLoop
        - redo.RedoContinuous
        - redo.RedoContinuousViaFutures
        - redo.RedoWithInfoContinuous
        - searching.SearchRecords
        - searching.SearchViaFutures
        - stewardship.ForceResolve
        - stewardship.ForceUnresolve
    ```
