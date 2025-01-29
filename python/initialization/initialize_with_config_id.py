#! /usr/bin/env python3

import os
from pathlib import Path

from senzing import SzError
from senzing_core import SzAbstractFactoryCore

# The value of config_id is made up, this example will fail if you run it
CONFIG_ID = 2787481550
INSTANCE_NAME = Path(__file__).stem
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")

try:
    sz_abstract_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, CONFIG_ID)
    sz_abstract_factory.create_engine()
except SzError as err:
    print(f"\n{err.__class__.__name__} - {err}")
