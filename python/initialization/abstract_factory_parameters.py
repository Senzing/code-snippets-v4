#! /usr/bin/env python3


from senzing import SzError
from senzing_core import SzAbstractFactoryCore, SzAbstractFactoryParametersCore

FACTORY_PARAMETERS: SzAbstractFactoryParametersCore = {
    "instance_name": "abstract_factory_parameters",
    "settings": {
        "PIPELINE": {
            "CONFIGPATH": "/etc/opt/senzing",
            "RESOURCEPATH": "/opt/senzing/er/resources",
            "SUPPORTPATH": "/opt/senzing/data",
        },
        "SQL": {"CONNECTION": "sqlite3://na:na@/tmp/sqlite/G2C.db"},
    },
    "verbose_logging": 0,
}

try:
    sz_factory = SzAbstractFactoryCore(**FACTORY_PARAMETERS)
    sz_configmgr = sz_factory.create_configmanager()
    sz_diagnostic = sz_factory.create_diagnostic()
    sz_engine = sz_factory.create_engine()
    sz_product = sz_factory.create_product()
    # Do work...
except SzError as err:
    print(f"\n{err.__class__.__name__} - {err}")
