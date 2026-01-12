# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This repository contains code snippets demonstrating Senzing SDK V4 usage in Python, Java, and C#. These are educational examples for entity resolution tasks like loading, searching, deleting, and redo processing.

**Warning**: Only run snippets against a test Senzing database - they add/delete data and some purge the entire repository.

## Build and Run Commands

### Python

Python snippets are standalone scripts. Run directly after setting up the environment:

```bash
source <project_path>/setupEnv
export SENZING_ENGINE_CONFIGURATION_JSON='{"PIPELINE": {...}, "SQL": {...}}'
python python/loading/add_records.py
```

**Linting and formatting:**

```bash
black --line-length 120 python/
isort --profile black python/
pylint python/
flake8 python/
mypy python/
bandit python/
```

### Java

Build with Maven (requires `SENZING_PATH` environment variable):

```bash
cd java
mvn package
```

Run individual snippets:

```bash
java -cp target/sz-sdk-snippets.jar loading.LoadRecords
```

Run via SnippetRunner (creates temp repository):

```bash
java -jar target/sz-sdk-snippets.jar all              # Run all
java -jar target/sz-sdk-snippets.jar loading          # Run group
java -jar target/sz-sdk-snippets.jar loading.LoadViaLoop  # Run specific
```

Checkstyle and SpotBugs available via Maven profiles:

```bash
mvn -P checkstyle validate
mvn -P spotbugs validate
```

### C#

Build and run from `csharp/snippets` directory:

```bash
cd csharp/snippets
dotnet run --project loading/LoadRecords
```

Run via SnippetRunner:

```bash
cd csharp/runner
dotnet run --project SnippetRunner all
dotnet run --project SnippetRunner loading
```

## Environment Setup

All languages require `SENZING_ENGINE_CONFIGURATION_JSON` environment variable with connection details:

```json
{
  "PIPELINE": {
    "SUPPORTPATH": "/path/to/data",
    "CONFIGPATH": "/path/to/etc",
    "RESOURCEPATH": "/path/to/resources"
  },
  "SQL": {
    "CONNECTION": "postgresql://user:password@host:5432:g2"
  }
}
```

**Platform-specific library paths:**

- Linux: `export LD_LIBRARY_PATH=$SENZING_PATH/er/lib:$LD_LIBRARY_PATH`
- macOS: `export DYLD_LIBRARY_PATH=$SENZING_PATH/er/lib:$SENZING_PATH/er/lib/macos:$DYLD_LIBRARY_PATH`
- Windows: `set Path=%SENZING_PATH%\er\lib;%Path%`

## Code Architecture

### Snippet Categories (same structure across all languages)

| Category          | Purpose                                          |
| ----------------- | ------------------------------------------------ |
| `initialization/` | Engine setup, factory creation, priming, purging |
| `configuration/`  | Data source registration, config management      |
| `loading/`        | Record ingestion (loop, queue, futures patterns) |
| `deleting/`       | Record removal with various concurrency patterns |
| `searching/`      | Entity search operations                         |
| `redo/`           | Redo record processing (continuous, with-info)   |
| `stewardship/`    | Force resolve/unresolve operations               |
| `information/`    | License, version, stats, repository info         |

### Concurrency Patterns

Snippets demonstrate three main patterns:

- **Loop**: Simple sequential processing
- **Queue**: Producer-consumer with thread pool
- **Futures**: Async execution with concurrent.futures (Python) / CompletableFuture (Java) / Tasks (C#)

### Data Files

Test data in `resources/data/`:

- `load-500.jsonl` - Default load file (fits default 500-record license)
- `load-{5K,10K,25K,50K,100K}.json[l]` - Larger datasets (require license)
- `del-{500,1K,5K,10K}.jsonl` - Delete test data
- `search-{50,5K}.jsonl` - Search test data
- `*-with-errors.jsonl` - Files with intentional errors for testing

## Key Conventions

- `SZ_WITH_INFO` flag on `add_record()`/`delete_record()` returns affected entity details for downstream processing
- Always randomize input data when loading with multiple threads to avoid entity contention
- Purge repository between load tests for accurate performance measurements
- Python uses `senzing` and `senzing_abstract` packages from the Senzing SDK
