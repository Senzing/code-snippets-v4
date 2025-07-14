# Deleting Data

The configuration snippets outline how to modify the Senzing configuration, register the modified configuration with a configuration ID and update the default configuration ID for the repository.

You may either `setDefaultConfigId()` or `replaceDefaultConfigId()`.  Initially, the the default config ID must be set since there is no existing config ID to replace.  However, when updating you may use `replaceDefaultConfigId()` to guard against race conditions of multiple threads or processes updating at the same time.

## Snippets

* **RegisterDataSources.java**
  * Gets the current default config, creates a modified config with additional data sources, registers that modified config and then replaces the default config ID.
* **InitDefaultConfig.java**
  * Initializes the repository with a default config ID using the template configuration provided by Senzing.
