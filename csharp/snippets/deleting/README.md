# Deleting Data

The deletion snippets outline deleting previously added source records. Deleting source records removes the previously added source record from the system, completes the entity resolution process and persists outcomes in the Senzing repository.

Deleting a record only requires the data source code and record ID for the record to be deleted.

## Snippets

- **DeleteViaFutures**
  - Read and delete source records from a file using multiple threads
- **DeleteViaLoop**
  - Basic read and delete source records from a file
- **DeleteWithInfoViaFutures**
  - Read and delete source records from a file using multiple threads
  - Collect the response using the [SzWithInfo flag](../../../README.md#with-info) on the `DeleteRecord()` method and track the entity ID's.
