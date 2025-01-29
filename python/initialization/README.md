# Initialization

## Snippets

- **abstract_factory_parameters.py**
  - Used to create a dictionary that can be unpacked when creating an SzAbstractFactoryCore, also useful for type annotations
- **engine_priming.py**
  - Priming the Senzing engine before use loads resource intensive assets upfront. Without priming the first SDK call to the engine will appear slower than usual as it causes these assets to be loaded
- **factory_and_engines.py**
  - Basic example of how to create an abstract Senzing factory and each of the available engines
- **g2_module_ini_to_json.py**
  - The snippets herein utilize the `SENZING_ENGINE_CONFIGURATION_JSON` environment variable for Senzing abstract factory creation
  - If you are familiar with working with a Senzing project you may be aware the same configuration data is held in the G2Module.ini file
  - Example to convert G2Module.ini to a JSON string for use with `SENZING_ENGINE_CONFIGURATION_JSON`
- **purge_repository.py**
  - **WARNING** This script will remove all data from a Senzing repository, use with caution! **WARNING**
  - It will prompt first, still use with caution!
