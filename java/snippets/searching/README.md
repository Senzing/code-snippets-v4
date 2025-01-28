# Searching for Entities

The search snippets outline searching for entities in the system. Searching for entities uses the same mapped JSON data [specification](https://senzing.zendesk.com/hc/en-us/articles/231925448-Generic-Entity-Specification-JSON-CSV-Mapping) as SDK methods such as `add_record()` to format the search request.

There are [considerations](https://senzing.zendesk.com/hc/en-us/articles/360007880814-Guidelines-for-Successful-Entity-Searching) to be aware of when searching.

## Snippets

- **SearchRecords.java**
  - Basic iteration over a few records, searching for each one
  - To see results first load records with [LoadTruthSetWithInfoViaLoop.java](../loading/LoadTruthSetViaLoop.java)
- **SearchViaFutures.java**
  - Read and search for records from a file using multiple threads
  - To see results first load records with [LoadViaFutures.java](../loading/LoadViaFutures.java)
