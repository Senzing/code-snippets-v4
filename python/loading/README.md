# Loading Data

The loading snippets outline adding new source records. Adding source records ingests [mapped](https://senzing.zendesk.com/hc/en-us/articles/231925448-Generic-Entity-Specification-JSON-CSV-Mapping) JSON data, completes the entity resolution process and persists outcomes in the Senzing repository. Adding a source record with the same data source code and record ID as an existing record will replace it.

## Snippets

- **add_futures.py**
  - Read and load source records from a file using multiple threads
- **add_queue.py**
  - Read and load source records using a queue
- **add_records_loop.py**
  - Basic read and add source records from a file
- **add_records.py**
  - Basic iteration over a few records, adding each one
- **add_truthset_loop.py**
  - Read and load from multiple source files, adding a sample truth set
- **add_with_info_futures.py**
  - Read and load source records from a file using multiple threads
  - Collect the response using the [SZ_WITH_INFO flag](../../README.md#with-info) on the `add_record()` method and write it to a file
