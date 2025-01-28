# Deleting Data

The deletion snippets outline deleting previously added source records. Deleting source records removes the previously added source record from the system, completes the entity resolution process and persists outcomes in the Senzing repository.

Deleting a record only requires the data source code and record ID for the record to be deleted.

## Snippets

- **DeleteViaFutures.java**
  - Read and delete source records from a file using multiple threads
- **DeleteViaLoop.java**
  - Basic read and delete source records from a file
- **DeleteWithInfoViaFutures.java**
  - Read and delete source records from a file using multiple threads
  - Collect the response using the [SZ_WITH_INFO flag](../../../README.md#with-info) on the `deleteRecord()` method and track the entity ID's.
