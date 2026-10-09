# Python Snippets

## Running the tests

`python/tests` runs every snippet end to end as a subprocess, each against its own temporary SQLite repository, and checks it exits cleanly with nothing on stderr. Your `SENZING_ENGINE_CONFIGURATION_JSON` is not used and your repository is not touched.

```bash
export PYTHONPATH=/opt/senzing/er/sdk/python
export LD_LIBRARY_PATH=/opt/senzing/er/lib
python -m pytest
```

Senzing install locations default to `/opt/senzing/er`, `/opt/senzing/data` and `/etc/opt/senzing`; override them with the same environment variables the Java SnippetRunner uses: `SENZING_PATH`, `SENZING_DIR`, `SENZING_SUPPORT_DIR`, `SENZING_RESOURCE_DIR` and `SENZING_CONFIG_DIR`.

Snippets are discovered automatically. A snippet needing data loaded, registered data sources, console input or a ctrl-c to stop it gets an entry in `SNIPPETS` in `tests/test_snippets.py`.
