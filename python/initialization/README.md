# Initialization

## Snippets

- **abstract_factory_parameters.py**
  - Used to create a dictionary that can be unpacked when creating an abstract factory, also useful for type annotations.
- **abstract_factory.py**
  - Basic example of how to create an abstract factory and each of the available engines.
- **abstract_factory_single_instance_only.py**
  - Try and create >1 abstract factories where any argument between them is different.
  - Only one abstract factory instance can exist at a time.
  - Destroying an abstract factory will destroy it and any engine objects it has created. 
- **abstract_factory_with_config_id.py**
  - Create an abstract factory using a specific configuration ID.
- **abstract_factory_with_debug.py**
  - Create an abstract factory with debug turned on.
- **engine_priming.py**
  - Priming the Senzing engine before use loads resource intensive assets upfront. 
  - Without priming the first SDK call to the engine will appear slower than usual as it causes these assets to be loaded.
- **factory_destroy.py**
  - Calls `destroy()` on the abstract factory destroying the abstract factory and any Senzing objects it has created.
  - The abstract factory must exist for the life of Senzing objects it has created.
  - If the abstract factory goes out of scope `destroy()` is automatically called
- **purge_repository.py**
  - **WARNING** This script will remove all data from a Senzing repository, use with caution! **WARNING**.
  - It will prompt first, still use with caution!.
  **signal_handler.py**
  - Catches signal.SIGINT (ctrl + c) and exits cleanly
  - Exiting cleanly on a signal ensures resource cleanup for the abstract factory is automatically called
  - If sz_abstract_factory goes out of scope 'destroy()` is automatically called, if signals are not caught and handled automatic resource cleanup does not happen
  - `destroy()` could also be called directly on the abstract factory by the signal handler
- **sz_engine_config_ini_to_json.py**
  - The snippets herein utilize the `SENZING_ENGINE_CONFIGURATION_JSON` environment variable for Senzing abstract factory creation.
  - If you are familiar with working with a Senzing project you may be aware the same configuration data is held in the sz_engine_config.ini file.
  - Example to convert sz_engine_config.ini to a JSON string for use with `SENZING_ENGINE_CONFIGURATION_JSON`.
