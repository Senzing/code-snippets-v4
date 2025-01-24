#! /usr/bin/env python3

import json
import os
from pathlib import Path

from senzing import SzError
from senzing_core import SzAbstractFactoryCore

INSTANCE_NAME = Path(__file__).stem
RECORDS = [
    {
        "DATA_SOURCE": "TEST",
        "RECORD_ID": "1001",
        "RECORD_TYPE": "PERSON",
        "PRIMARY_NAME_LAST": "Smith",
        "PRIMARY_NAME_FIRST": "Robert",
        "DATE_OF_BIRTH": "12/11/1978",
        "ADDR_TYPE": "MAILING",
        "ADDR_FULL": "123 Main Street, Las Vegas NV 89132",
        "PHONE_TYPE": "HOME",
        "PHONE_NUMBER": "702-919-1300",
        "EMAIL_ADDRESS": "bsmith@work.com",
    },
    {
        "DATA_SOURCE": "TEST",
        "RECORD_ID": "1002",
        "RECORD_TYPE": "PERSON",
        "PRIMARY_NAME_LAST": "Smith II",
        "PRIMARY_NAME_FIRST": "Bob",
        "DATE_OF_BIRTH": "11/12/1978",
        "ADDR_TYPE": "HOME",
        "ADDR_LINE1": "1515 Adela Lane",
        "ADDR_CITY": "Las Vegas",
        "ADDR_STATE": "NV",
        "ADDR_POSTAL_CODE": "89111",
        "PHONE_TYPE": "MOBILE",
        "PHONE_NUMBER": "702-919-1300",
    },
    {
        "DATA_SOURCE": "TEST",
        "RECORD_ID": "1003",
        "RECORD_TYPE": "PERSON",
        "PRIMARY_NAME_LAST": "Smith",
        "PRIMARY_NAME_FIRST": "Bob",
        "PRIMARY_NAME_MIDDLE": "J",
        "DATE_OF_BIRTH": "12/11/1978",
        "EMAIL_ADDRESS": "bsmith@work.com",
    },
    {
        "DATA_SOURCE": "TEST",
        "RECORD_ID": "1004",
        "RECORD_TYPE": "PERSON",
        "PRIMARY_NAME_LAST": "Smith",
        "PRIMARY_NAME_FIRST": "B",
        "ADDR_TYPE": "HOME",
        "ADDR_LINE1": "1515 Adela Ln",
        "ADDR_CITY": "Las Vegas",
        "ADDR_STATE": "NV",
        "ADDR_POSTAL_CODE": "89132",
        "EMAIL_ADDRESS": "bsmith@work.com",
    },
    {
        "DATA_SOURCE": "TEST",
        "RECORD_ID": "1005",
        "RECORD_TYPE": "PERSON",
        "PRIMARY_NAME_LAST": "Smith",
        "PRIMARY_NAME_FIRST": "Rob",
        "PRIMARY_NAME_MIDDLE": "E",
        "DRIVERS_LICENSE_NUMBER": "112233",
        "DRIVERS_LICENSE_STATE": "NV",
        "ADDR_TYPE": "MAILING",
        "ADDR_LINE1": "123 E Main St",
        "ADDR_CITY": "Henderson",
        "ADDR_STATE": "NV",
        "ADDR_POSTAL_CODE": "89132",
    },
]
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")

try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    sz_engine = sz_factory.create_engine()

    for record in RECORDS:
        data_source = record["DATA_SOURCE"]
        record_id = record["RECORD_ID"]
        sz_engine.add_record(data_source, record_id, json.dumps(record))
        print(f"Record {record_id} added", flush=True)
except SzError as err:
    print(f"\n{err.__class__.__name__} - {err}")
