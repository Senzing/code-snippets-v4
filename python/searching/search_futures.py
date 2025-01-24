#! /usr/bin/env python3

import concurrent.futures
import itertools
import json
import os
import sys
from pathlib import Path

from senzing import SzBadInputError, SzError, SzRetryableError, SzUnrecoverableError
from senzing_core import SzAbstractFactoryCore

INPUT_FILE = Path("../../resources/data/search-50.jsonl").resolve()
INSTANCE_NAME = Path(__file__).stem
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")


def mock_logger(level, error, error_record=None):
    print(f"\n{level}: {error.__class__.__name__} - {error}", file=sys.stderr)
    if error_record:
        print(f"{error_record}", file=sys.stderr)


def search_record(engine, record_to_search):
    return engine.search_by_attributes(record_to_search)


def futures_search(engine, input_file):
    success_recs = 0
    error_recs = 0

    with open(input_file, "r", encoding="utf-8") as in_file:
        with concurrent.futures.ThreadPoolExecutor() as executor:
            futures = {
                executor.submit(search_record, engine, record): record
                for record in itertools.islice(in_file, executor._max_workers)
            }

            while futures:
                done, _ = concurrent.futures.wait(futures, return_when=concurrent.futures.FIRST_COMPLETED)
                for f in done:
                    try:
                        result = f.result()
                    except (SzBadInputError, json.JSONDecodeError) as err:
                        mock_logger("ERROR", err, futures[f])
                        error_recs += 1
                    except SzRetryableError as err:
                        mock_logger("WARN", err, futures[f])
                        error_recs += 1
                    except (SzUnrecoverableError, SzError) as err:
                        mock_logger("CRITICAL", err, futures[f])
                        raise err
                    else:
                        record = in_file.readline()
                        if record:
                            futures[executor.submit(search_record, engine, record)] = record

                        success_recs += 1
                        if success_recs % 100 == 0:
                            print(f"Processed {success_recs:,} adds, with {error_recs:,} errors", flush=True)

                        print(f"\n------ Searched: {futures[f]}", flush=True)
                        print(f"\n{result}", flush=True)
                    finally:
                        del futures[f]

            print(f"\nSuccessfully searched {success_recs:,} records, with" f" {error_recs:,} errors")


try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    sz_engine = sz_factory.create_engine()
    futures_search(sz_engine, INPUT_FILE)
except SzError as err:
    mock_logger("CRITICAL", err)
