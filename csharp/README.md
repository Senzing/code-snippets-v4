# C# Snippets

The C# snippets are contained in the `snippets` directory under various
directories -- each as its own assembly/project.

## Prerequisites

Before attempting to build the snippets you will need to make the make the
`Senzing.Sdk.[version].nupkg` file available to the `dotnet` executable so
it can be used as a dependency.  This is done via these [instructions](https://github.com/senzing-garage/sz-sdk-csharp/blob/main/README.md#Usage).

Further, you will need to set environment variables so the Senzing installation can be located:

- Linux:

    ```console
    export SENZING_PATH=/opt/senzing/
    export LD_LIBRARY_PATH=$SENZING_PATH/er/lib:$LD_LIBRARY_PATH
    ```

- macOS:

    ```console
    export SENZING_PATH=$HOME/senzing
    export DYLD_LIBRARY_PATH=$SENZING_PATH/er/lib:$SENZING_PATH/er/lib/macos:$DYLD_LIBRARY_PATH
    ```

- Windows:

    ```console
    set SENZING_PATH=%USERPROFILE%\senzing
    set Path=%SENZING_PATH%\er\lib;%Path%
    ```

## Using Example Code

Senzing encourages and allows you to freely copy the provided example code and modify it to your own needs as you
see fit.  However, please refer to the [Global Suppression Notes] to understand how to best adapt the example code
to your own coding project.

## Building

The C# snippets can built using the `dotnet build [project-name]` command under each directory.  They can be run using `dotnet run --project [project-name]` command.  Attempting to run a snippet will also trigger building it.

## Running

There are several ways to run the code snippets.

### Run Directly

You may run any individual Snippet class directly providing you have a Senzing repository to run it with and the `SENZING_ENGINE_CONFIGURATION_JSON` environment variable set for connecting to that repository.  Many of the snippets will find a default data file to run with if run from the `snippets` directory, but also allow the caller to use a different data file if given by the first command-line argument.

1. Run a snippet that takes no command-line arguments.

    ```console
    cd snippets
    dotnet run --project loading/LoadRecords
    ```

2. Run a snippet and override the input file using command-line arguments

    ```console
    dotnet run --project loading/LoadRecordsViaLoop ../../resources/data/load-500-with-errors.jsonl
    ```

### Run via Runner

The `SnippetRunner` project will run one or more snippets for you and create a temporary Senzing repository to run then against.  This can be executed using:
    ```console
    cd runner
    dotnet run --project SnippetRunner
    ```

**NOTE:** When code snippets are run this way you cannot specify command-line arguments for individual snippets, nor can you respond to command-line input requests (they will be automatically be responded by the runner -- including forced termination of a snippet that is intended to run indefinitely).

1. Execute all code snippets:

    ```console
    cd runner
    dotnet run --project SnippetRunner all
    ```

2. Execute all code snippets in a group:

    ```console
    cd runner
    dotnet run --project SnippetRunner loading
    ```

3. Execute all code snippets from multiple groups:

    ```console
    cd runner
    dotnet run --project SnippetRunner loading redo
    ```

4. Execute specific code snippets:

    ```console
    cd runner
    dotnet run --project SnippetRunner loading.LoadViaLoop loading.LoadViaQueue
    ```

5. Mix and match packages with individual snippets:

    ```console
    cd runner
    dotnet run --project SnippetRunner redo loading.LoadViaLoop
    ```

6. Generate a help message by specifying no arguments:

    ```console
    cd runner
    dotnet run --project SnippetRunner

    dotnet run --project SnippetRunner [ all | <group> | <snippet> ]*

        - Specifying no arguments will print this message
        - Specifying "all" will run all snippets
        - Specifying one or more groups will run all snippets in those groups
        - Specifying one or more snippets will run those snippet

    Examples:

    dotnet run --project SnippetRunner all

    dotnet run --project SnippetRunner loading.LoadRecords loading.LoadViaFutures

    dotnet run --project SnippetRunner initialization deleting loading.LoadRecords

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

[Global Suppression Notes]: GlobalSuppressions.md