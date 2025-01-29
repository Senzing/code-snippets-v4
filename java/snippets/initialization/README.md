# Initialization

## Snippets

- **EnginePriming.java**
  - Priming the Senzing engine before use loads resource intensive assets upfront. Without priming the first SDK call to the engine will appear slower than usual as it causes these assets to be loaded
- **EnvironmentsAndHubs.java**
  - Basic example of how to create an abstract Senzing factory and each of the available engines
- **PurgeRepository.java**
  - **WARNING** This script will remove all data from a Senzing repository, use with caution! **WARNING**
  - It will prompt first, still use with caution!
