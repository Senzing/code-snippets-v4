#! /usr/bin/env python3

import os
import sys
from pathlib import Path

from senzing import SzError
from senzing_core import SzAbstractFactoryCore

ENGINE_CONFIG_JSON = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")
INSTANCE_NAME = Path(__file__).stem


try:
    sz_factory = SzAbstractFactoryCore("add_records", ENGINE_CONFIG_JSON, verbose_logging=False)
    sz_config = sz_factory.create_config()
    sz_configmanager = sz_factory.create_configmanager()

    config_id = sz_configmanager.get_default_config_id()
    config_definition = sz_configmanager.get_config(config_id)
    config_handle = sz_config.import_config(config_definition)

    for data_source in ("CUSTOMERS", "REFERENCE", "WATCHLIST"):
        response = sz_config.add_data_source(config_handle, data_source)

    config_definition = sz_config.export_config(config_handle)
    config_id = sz_configmanager.add_config(config_definition, INSTANCE_NAME)
    sz_configmanager.set_default_config_id(config_id)

    response2 = sz_config.get_data_sources(config_handle)
    sz_config.close_config(config_handle)
    print(response2)
except SzError as err:
    print(f"{err.__class__.__name__} - {err}", file=sys.stderr)
