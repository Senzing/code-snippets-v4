#! /usr/bin/env python3

import os
from pathlib import Path

from senzing_core import SzAbstractFactory, SzError

ENGINE_CONFIG_JSON = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")
INSTANCE_NAME = Path(__file__).stem

try:
    sz_factory = SzAbstractFactory(INSTANCE_NAME, ENGINE_CONFIG_JSON, verbose_logging=False)
    sz_engine = sz_factory.create_engine()
    print("Priming Senzing engine...")
    sz_engine.prime_engine()
    # Do work...
except SzError as err:
    print(f"\n{err.__class__.__name__} - {err}")
