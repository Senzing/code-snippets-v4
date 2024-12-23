#! /usr/bin/env python3

import configparser
from pathlib import Path

INI_FILE = Path("../../resources/g2module/G2Module.ini").resolve()
ENGINE_CONFIG_JSON = {}

cfgp = configparser.ConfigParser()
cfgp.optionxform = str  # type: ignore
cfgp.read(INI_FILE)

for section in cfgp.sections():
    ENGINE_CONFIG_JSON[section] = dict(cfgp.items(section))

print(ENGINE_CONFIG_JSON)
