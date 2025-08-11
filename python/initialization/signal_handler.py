#! /usr/bin/env python3

import os
import signal
import sys
from pathlib import Path

from senzing import SzError
from senzing_core import SzAbstractFactoryCore

INSTANCE_NAME = Path(__file__).stem
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")


def handler(signum, frame):
    print("\nCaught ctrl-c, exiting")
    sys.exit(0)


signal.signal(signal.SIGINT, handler)

try:
    sz_abstract_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS)
    sz_engine = sz_abstract_factory.create_engine()
    # Do work...
    print("\nSimulating work, press ctrl-c to exit...")
    signal.pause()
except SzError as err:
    print(f"\n{err.__class__.__name__} - {err}")
