# Redo Records

The redo snippets outline processing redo records. During normal processing of loading, deleting and replacing data the Senzing engine may determine additional work needs to be completed for an entity. There are times the Senzing engine will decide to defer this additional work. Examples of why this may happen include:

- Records loaded in parallel are clustering around the same entities causing contention
- Automatic corrections
- Cleansing decisions made on attributes determined to no longer be useful for entity resolution

When an entity requires additional work a record is automatically created in the system indicating this requirement. These records are called redo records. Redo records need to be periodically or continuously checked for and processed. Periodically is suitable after manipulating smaller portions of data, for example, at the end of a batch load of data. In contrast, a continuous process checking for and processing redo records is suitable in a streaming system that is constantly manipulating data. In general, it is recommended to have a continuous redo process checking for any redo records to process and processing them.

## Snippets

- **LoadWithRedoViaLoop**
  - Read and load source records from a file and then process any redo records
- **RedoContinuous**
  - Basic example of continuously monitoring for redo records to process
- **RedoContinuousViaFutures**
  - Continuously monitor for redo records to process using multiple threads
- **RedoWithInfoContinuous**
  - Continuously monitor for redo records to process
  - Collect the response using the [SzWithInfo flag](../../../README.md#with-info) on the `ProcessRedoRecord()` method and track the entity ID's for the records.
