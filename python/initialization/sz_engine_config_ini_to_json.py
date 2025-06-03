#! /usr/bin/env python3

import configparser
from pathlib import Path

INI_FILE = Path("../../resources/engine_config/sz_engine_config.ini").resolve()
settings = {}

cfgp = configparser.ConfigParser()
cfgp.optionxform = str  # type: ignore
cfgp.read(INI_FILE)

for section in cfgp.sections():
    settings[section] = dict(cfgp.items(section))

print(settings)
