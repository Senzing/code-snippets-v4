#! /usr/bin/env python3

import os
from pathlib import Path

from senzing import SzError
from senzing_core import SzAbstractFactoryCore

INSTANCE_NAME = Path(__file__).stem
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")

try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    sz_diagnostic = sz_factory.create_diagnostic()
    print(sz_diagnostic.get_datastore_info())
except SzError as err:
    print(f"\n{err.__class__.__name__} - {err}")
