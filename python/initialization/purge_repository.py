#! /usr/bin/env python3

import os
import sys
from pathlib import Path

from senzing import SzError
from senzing_core import SzAbstractFactoryCore

INSTANCE_NAME = Path(__file__).stem
PURGE_MSG = """
**************************************** WARNING ****************************************

This example will purge all currently loaded data from the Senzing datastore!
Before proceeding, all instances of Senzing (custom code, tools, etc.) must be shut down.

*****************************************************************************************

Are you sure you want to continue and purge the Senzing datastore? Type YESPURGESENZING to purge: """
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")


if input(PURGE_MSG) != "YESPURGESENZING":
    sys.exit()

try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    sz_diagnostic = sz_factory.create_diagnostic()
    sz_diagnostic.purge_repository()
    print("\nSenzing datastore purged")
except SzError as err:
    print(f"\n{err.__class__.__name__} - {err}")
