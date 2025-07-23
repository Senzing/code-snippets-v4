#! /usr/bin/env python3

import os
import sys
from pathlib import Path

from senzing import SzError
from senzing_core import SzAbstractFactoryCore

INSTANCE_NAME = Path(__file__).stem
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")


try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    sz_configmanager = sz_factory.create_configmanager()
    response = sz_configmanager.get_config_registry()
    print(response)
except SzError as err:
    print(f"{err.__class__.__name__} - {err}", file=sys.stderr)
