#! /usr/bin/env python3

import os
from pathlib import Path

from senzing import SzError
from senzing_core import SzAbstractFactoryCore

ENGINE_CONFIG_JSON = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")
INSTANCE_NAME = Path(__file__).stem
SECONDS_TO_RUN = 3

try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, ENGINE_CONFIG_JSON, verbose_logging=False)
    sz_diagnostic = sz_factory.create_diagnostic()
    print(sz_diagnostic.check_datastore_performance(SECONDS_TO_RUN))
except SzError as err:
    print(f"\n{err.__class__.__name__} - {err}")
