#! /usr/bin/env python3

import json
import os
import sys
from pathlib import Path

from senzing import SzBadInputError, SzError, SzRetryableError, SzUnrecoverableError
from senzing_core import SzAbstractFactoryCore

INPUT_FILES = [
    Path("../../resources/data/truthset/customers.jsonl").resolve(),
    Path("../../resources/data/truthset/reference.jsonl").resolve(),
    Path("../../resources/data/truthset/watchlist.jsonl").resolve(),
]
INSTANCE_NAME = Path(__file__).stem
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")


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
                data_source = record_dict.get("DATA_SOURCE", None)
                record_id = record_dict.get("RECORD_ID", None)
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

    print(f"\nSuccessfully added {success_recs:,} records, with {error_recs:,} errors")


def process_redo(engine):
    success_recs = 0
    error_recs = 0

    print("\nStarting to process redo records...")

    while 1:
        try:
            response = engine.get_redo_record()
            if not response:
                break
            engine.process_redo_record(response)

            success_recs += 1
            if success_recs % 1 == 0:
                print(f"Processed {success_recs:,} redo records, with" f" {error_recs:,} errors")
        except SzBadInputError as err:
            mock_logger("ERROR", err)
            error_recs += 1
        except SzRetryableError as err:
            mock_logger("WARN", err)
            error_recs += 1
        except (SzUnrecoverableError, SzError) as err:
            mock_logger("CRITICAL", err)
            raise err

    print(f"\nSuccessfully processed {success_recs:,} redo records, with" f" {error_recs:,} errors")


try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    sz_engine = sz_factory.create_engine()
    for load_file in INPUT_FILES:
        add_records_from_file(sz_engine, load_file)
    redo_count = sz_engine.count_redo_records()

    if redo_count:
        process_redo(sz_engine)
    else:
        print("\nNo redo records to process")
except SzError as err:
    mock_logger("CRITICAL", err)
