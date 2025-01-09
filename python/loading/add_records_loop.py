#! /usr/bin/env python3

import json
import os
import sys
from pathlib import Path

from senzing import SzBadInputError, SzError, SzRetryableError, SzUnrecoverableError
from senzing_core import SzAbstractFactory

ENGINE_CONFIG_JSON = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")
INPUT_FILE = Path("../../resources/data/load-500.jsonl").resolve()
INSTANCE_NAME = Path(__file__).stem


def mock_logger(level, error, error_record=None):
    print(f"\n{level}: {error.__class__.__name__} - {error}", file=sys.stderr)
    if error_record:
        print(f"{error_record}", file=sys.stderr)


def add_records_from_file(engine, input_file):
    success_recs = 0
    error_recs = 0

    with open(input_file, "r", encoding="utf-8") as file:
        for record_to_add in file:
            try:
                record_dict = json.loads(record_to_add)
                data_source = record_dict.get("DATA_SOURCE", "")
                record_id = record_dict.get("RECORD_ID", "")
                engine.add_record(data_source, record_id, record_to_add)
            except (SzBadInputError, json.JSONDecodeError) as err:
                mock_logger("ERROR", err, record_to_add)
                error_recs += 1
            except SzRetryableError as err:
                mock_logger("WARN", err, record_to_add)
                error_recs += 1
            except (SzUnrecoverableError, SzError) as err:
                mock_logger("CRITICAL", err, record_to_add)
                raise err
            else:
                success_recs += 1

            if success_recs % 100 == 0:
                print(f"Processed {success_recs:,} adds, with {error_recs:,} errors", flush=True)

    print(f"\nSuccessfully loaded {success_recs:,} records, with {error_recs:,} errors")


try:
    sz_factory = SzAbstractFactory(INSTANCE_NAME, ENGINE_CONFIG_JSON, verbose_logging=False)
    sz_engine = sz_factory.create_engine()
    add_records_from_file(sz_engine, INPUT_FILE)
except SzError as err:
    mock_logger("CRITICAL", err)
