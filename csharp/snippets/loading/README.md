# Loading Data

The loading snippets outline adding new source records. Adding source records ingests [mapped](https://senzing.zendesk.com/hc/en-us/articles/231925448-Generic-Entity-Specification-JSON-CSV-Mapping) JSON data, completes the entity resolution process and persists outcomes in the Senzing repository. Adding a source record with the same data source code and record ID as an existing record will replace it.

## Snippets

- **LoadRecords**
  - Basic iteration over a few records, adding each one
- **LoadTruthSetWithInfoViaLoop**
  - Read and load from multiple source files, adding a sample truth 
  - Collect the response using the [SzWithInfo flag](../../../README.md#with-info) on the `AddRecord()` method and track the entity ID's for the records.
- **LoaeViaFutures**
  - Read and load source records from a file using multiple threads
- **LoadViaLoop**
  - Basic read and add source records from a file
- **LoadViaQueue**
  - Read and load source records using a queue
- **LoadWithInfoViaFutures**
  - Read and load source records from a file using multiple threads
  - Collect the response using the [SzWithInfo flag](../../../README.md#with-info) on the `AddRecord()` method and track the entity ID's for the records.
- **LoadWithStatsViaLoop**
  - Basic read and add source records from a file
  - Periodic calling to `GetStats()` method during load to track loading statistics.
