#! /usr/bin/env python3

import os

from senzing import SzError
from senzing_core import SzAbstractFactoryCore

INSTANCE_NAME_1 = "ABSTRACT_FACTORY_1"
INSTANCE_NAME_2 = "ABSTRACT_FACTORY_2"
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")

# Try and create 2 abstract factories where any of the arguments differs
try:
    print("\nCreating first abstract factory...")
    sz_abstract_factory_1 = SzAbstractFactoryCore(INSTANCE_NAME_1, SETTINGS)
    sz_engine_1 = sz_abstract_factory_1.create_engine()
    print("\tFirst abstract factory and engine created")
    print(f"\tUsing sz_engine_1: {sz_engine_1.get_active_config_id()}")

    print("\nCreating second abstract factory...")
    sz_abstract_factory_2 = SzAbstractFactoryCore(INSTANCE_NAME_2, SETTINGS)
    sz_engine_2 = sz_abstract_factory_2.create_engine()
    print("\tSecond abstract factory and engine created")
    print(f"\tUsing sz_engine_2: {sz_engine_2.get_active_config_id()}")
except SzError as err:
    print(f"\t{err.__class__.__name__} - {err}")
finally:
    sz_abstract_factory_1.destroy()
    print("\nFirst abstract factory has been destroyed")

# First abstract factory has been destroyed, try and use the engine object it created
try:
    print("\nTrying sz_engine_1 from first abstract factory again...")
    print(f"\tUsing sz_engine_1: {sz_engine_1.get_active_config_id()}")
except SzError as err:
    print(f"\t{err.__class__.__name__} - {err}")

# Now abstract factory 1 has been destroyed, try and re-create it and an engine object
try:
    print("\nTrying second abstract factory again...")
    sz_abstract_factory_2 = SzAbstractFactoryCore(INSTANCE_NAME_2, SETTINGS)
    sz_engine_2 = sz_abstract_factory_2.create_engine()
    print("\tCreated second abstract factory and engine after first was destroyed")
    print(f"\tUsing sz_engine_2: {sz_engine_2.get_active_config_id()}")
except SzError as err:
    print(f"\n{err.__class__.__name__} - {err}")
