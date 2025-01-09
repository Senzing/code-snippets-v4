#! /usr/bin/env python3

import json
import os
import sys
from pathlib import Path

from senzing import SzEngineFlags, SzError
from senzing_core import SzAbstractFactory

ENGINE_CONFIG_JSON = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")
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
        "RECORD_ID": "1",
        "PRIMARY_NAME_FULL": "Patrick Smith",
        "AKA_NAME_FULL": "Paddy Smith",
        "ADDR_FULL": "787 Rotary Dr, Rotorville, RI, 78720",
        "PHONE_NUMBER": "787-767-2688",
        "DATE_OF_BIRTH": "1/12/1990",
    },
    {
        "DATA_SOURCE": "TEST",
        "RECORD_ID": "2",
        "PRIMARY_NAME_FULL": "Patricia Smith",
        "ADDR_FULL": "787 Rotary Dr, Rotorville, RI, 78720",
        "PHONE_NUMBER": "787-767-2688",
        "DATE_OF_BIRTH": "5/4/1994",
    },
    {
        "DATA_SOURCE": "TEST",
        "RECORD_ID": "3",
        "PRIMARY_NAME_FULL": "Pat Smith",
        "ADDR_FULL": "787 Rotary Dr, Rotorville, RI, 78720",
        "PHONE_NUMBER": "787-767-2688",
    },
]

if input(PURGE_MSG) != "YESPURGESENZING":
    sys.exit()

try:
    sz_factory = SzAbstractFactory(INSTANCE_NAME, ENGINE_CONFIG_JSON, verbose_logging=False)
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
    for record_id in ("1", "2", "3"):
        response1 = sz_engine.get_entity_by_record_id(
            "TEST",
            record_id,
            SzEngineFlags.SZ_ENTITY_BRIEF_DEFAULT_FLAGS,
        )
        get_json = json.loads(response1)
        print(f"Record {record_id} currently resolves to entity" f" {get_json['RESOLVED_ENTITY']['ENTITY_ID']}")

    print("\nUpdating records with TRUSTED_ID to force resolve...\n")
    record1 = sz_engine.get_record("TEST", "1")
    record2 = sz_engine.get_record("TEST", "3")
    get1_json = json.loads(record1)
    get2_json = json.loads(record2)
    get1_json["JSON_DATA"].update({"TRUSTED_ID_NUMBER": "TEST_R1-TEST_R3", "TRUSTED_ID_TYPE": "FORCE_RESOLVE"})
    get2_json["JSON_DATA"].update({"TRUSTED_ID_NUMBER": "TEST_R1-TEST_R3", "TRUSTED_ID_TYPE": "FORCE_RESOLVE"})
    sz_engine.add_record("TEST", "1", json.dumps(get1_json["JSON_DATA"]))
    sz_engine.add_record("TEST", "3", json.dumps(get2_json["JSON_DATA"]))

    for record_id in ("1", "2", "3"):
        response2 = sz_engine.get_entity_by_record_id(
            "TEST",
            record_id,
            SzEngineFlags.SZ_ENTITY_BRIEF_DEFAULT_FLAGS,
        )
        get_json = json.loads(response2)
        print(f"Record {record_id} now resolves to entity" f" {get_json['RESOLVED_ENTITY']['ENTITY_ID']}")
except SzError as err:
    print(f"{err.__class__.__name__} - {err}", file=sys.stderr)
