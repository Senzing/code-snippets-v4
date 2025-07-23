#! /usr/bin/env python3

import os
from pathlib import Path

from senzing import SzError
from senzing_core import SzAbstractFactoryCore

INSTANCE_NAME = Path(__file__).stem
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")

try:
    sz_abstract_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS)
    sz_engine = sz_abstract_factory.create_engine()
    # Do work...
except SzError as err:
    print(f"\n{err.__class__.__name__} - {err}")
finally:
    # Destroys the abstract factory and all objects it created, such as sz_engine above
    # If sz_abstract_factory goes out of scope destroy() is automatically called
    sz_abstract_factory.destroy()
