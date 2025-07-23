#! /usr/bin/env python3

import os
import sys
from pathlib import Path

from senzing import SzError
from senzing_core import SzAbstractFactoryCore

EXISTING_CONFIG_MSG = "\nA configuration exists in the repository, replace it with a template configuration? (y/n)  "
INSTANCE_NAME = Path(__file__).stem
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")


try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    sz_configmanager = sz_factory.create_configmanager()

    if current_config_id := sz_configmanager.get_default_config_id():
        if not input(EXISTING_CONFIG_MSG).lower() in ("y", "yes"):
            sys.exit(1)

    sz_config = sz_configmanager.create_config_from_template()
    new_default_config = sz_config.export()
    new_config_id = sz_configmanager.set_default_config(new_default_config, "Code snippet init_default_config example")
    print(f"New default config ID: {new_config_id}")
except SzError as err:
    print(f"{err.__class__.__name__} - {err}", file=sys.stderr)
