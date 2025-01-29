#! /usr/bin/env python3

import os
from pathlib import Path

from senzing import SzError
from senzing_core import SzAbstractFactoryCore

INSTANCE_NAME = Path(__file__).stem
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")
VERBOSE_LOGGING = 1

try:
    sz_abstract_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=VERBOSE_LOGGING)
    # Create an engine to show debug output
    sz_abstract_factory.create_engine()
except SzError as err:
    print(f"\n{err.__class__.__name__} - {err}")
