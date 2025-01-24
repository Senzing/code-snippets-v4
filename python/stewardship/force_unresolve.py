#! /usr/bin/env python3

import json
import os
import sys
from pathlib import Path

from senzing import SzEngineFlags, SzError
from senzing_core import SzAbstractFactoryCore

INSTANCE_NAME = Path(__file__).stem
PURGE_MSG = """
**************************************** WARNING ****************************************

This example will purge all currently loaded data from the Senzing datastore!
Before proceeding, all instances of Senzing (custom code, tools, etc.) must be shut down.

*****************************************************************************************

Are you sure you want to continue and purge the Senzing datastore? Type YESPURGESENZING to purge: """
RECORDS = [
    {
        "DATA_SOURCE": "TEST",
        "RECORD_ID": "4",
        "PRIMARY_NAME_FULL": "Elizabeth Jonas",
        "ADDR_FULL": "202 Rotary Dr, Rotorville, RI, 78720",
        "SSN_NUMBER": "767-87-7678",
        "DATE_OF_BIRTH": "1/12/1990",
    },
    {
        "DATA_SOURCE": "TEST",
        "RECORD_ID": "5",
        "PRIMARY_NAME_FULL": "Beth Jones",
        "ADDR_FULL": "202 Rotary Dr, Rotorville, RI, 78720",
        "SSN_NUMBER": "767-87-7678",
        "DATE_OF_BIRTH": "1/12/1990",
    },
    {
        "DATA_SOURCE": "TEST",
        "RECORD_ID": "6",
        "PRIMARY_NAME_FULL": "Betsey Jones",
        "ADDR_FULL": "202 Rotary Dr, Rotorville, RI, 78720",
        "PHONE_NUMBER": "202-787-7678",
    },
]
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")

if input(PURGE_MSG) != "YESPURGESENZING":
    sys.exit()

try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    sz_diagnostic = sz_factory.create_diagnostic()
    sz_engine = sz_factory.create_engine()
    sz_diagnostic.purge_repository()

    print()
    for record in RECORDS:
        data_source = record["DATA_SOURCE"]
        record_id = record["RECORD_ID"]
        sz_engine.add_record(data_source, record_id, json.dumps(record))
        print(f"Record {record_id} added")

    print()
    for record_id in ("4", "5", "6"):
        response1 = sz_engine.get_entity_by_record_id(
            "TEST",
            record_id,
            SzEngineFlags.SZ_ENTITY_BRIEF_DEFAULT_FLAGS,
        )
        get_json = json.loads(response1)
        print(f"Record {record_id} currently resolves to entity" f" {get_json['RESOLVED_ENTITY']['ENTITY_ID']}")

    print("\nUpdating records with TRUSTED_ID to force unresolve...\n")
    record1 = sz_engine.get_record("TEST", "4")
    record2 = sz_engine.get_record("TEST", "6")
    get1_json = json.loads(record1)
    get2_json = json.loads(record2)
    get1_json["JSON_DATA"].update({"TRUSTED_ID_NUMBER": "TEST_R4-TEST_R6", "TRUSTED_ID_TYPE": "FORCE_UNRESOLVE"})
    get2_json["JSON_DATA"].update({"TRUSTED_ID_NUMBER": "TEST_R6-TEST_R4", "TRUSTED_ID_TYPE": "FORCE_UNRESOLVE"})
    sz_engine.add_record("TEST", "4", json.dumps(get1_json["JSON_DATA"]))
    sz_engine.add_record("TEST", "6", json.dumps(get2_json["JSON_DATA"]))

    for record_id in ("4", "5", "6"):
        response2 = sz_engine.get_entity_by_record_id(
            "TEST",
            record_id,
            SzEngineFlags.SZ_ENTITY_BRIEF_DEFAULT_FLAGS,
        )
        get_json = json.loads(response2)
        print(f"Record {record_id} now resolves to entity" f" {get_json['RESOLVED_ENTITY']['ENTITY_ID']}")
except SzError as err:
    print(f"{err.__class__.__name__} - {err}", file=sys.stderr)
