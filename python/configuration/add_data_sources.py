#! /usr/bin/env python3

import os
import sys
from pathlib import Path

from senzing import SzError
from senzing_core import SzAbstractFactoryCore

INSTANCE_NAME = Path(__file__).stem
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")


try:
    sz_factory = SzAbstractFactoryCore("add_data_source", SETTINGS, verbose_logging=False)
    sz_configmanager = sz_factory.create_configmanager()

    config_id = sz_configmanager.get_default_config_id()
    sz_config = sz_configmanager.create_config_from_config_id(config_id)

    for data_source in ("CUSTOMERS", "REFERENCE", "WATCHLIST"):
        _ = sz_config.add_data_source(data_source)

    new_config = sz_config.export()
    new_config_id = sz_configmanager.register_config(new_config, INSTANCE_NAME)
    sz_configmanager.set_default_config_id(config_id)

    sz_config = sz_configmanager.create_config_from_config_id(new_config_id)
    response = sz_config.get_data_sources()
    print(response)
except SzError as err:
    print(f"{err.__class__.__name__} - {err}", file=sys.stderr)
