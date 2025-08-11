#! /usr/bin/env python3

import concurrent.futures
import itertools
import json
import os
import sys
from pathlib import Path

from senzing import (
    SzBadInputError,
    SzEngineFlags,
    SzError,
    SzRetryableError,
    SzUnrecoverableError,
)
from senzing_core import SzAbstractFactoryCore

INPUT_FILE = Path("../../resources/data/del-500.jsonl").resolve()
INSTANCE_NAME = Path(__file__).stem
OUTPUT_FILE = Path("../../resources/output/delete_file_with_info.jsonl").resolve()
SETTINGS = os.getenv("SENZING_ENGINE_CONFIGURATION_JSON", "{}")


def mock_logger(level, error, error_record=None):
    print(f"\n{level}: {error.__class__.__name__} - {error}", file=sys.stderr)
    if error_record:
        print(f"{error_record}", file=sys.stderr)


def delete_record(engine, record_to_delete):
    record_dict = json.loads(record_to_delete)
    data_source = record_dict.get("DATA_SOURCE", "")
    record_id = record_dict.get("RECORD_ID", "")
    return engine.delete_record(data_source, record_id, flags=SzEngineFlags.SZ_WITH_INFO)


def futures_del(engine, input_file, output_file):
    error_recs = 0
    shutdown = False
    success_recs = 0

    with open(output_file, "w", encoding="utf-8") as out_file:
        with open(input_file, "r", encoding="utf-8") as in_file:
            with concurrent.futures.ThreadPoolExecutor() as executor:
                futures = {
                    executor.submit(delete_record, engine, record): record
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
                            shutdown = True
                            raise err
                        else:
                            out_file.write(f"{result}\n")

                            success_recs += 1
                            if success_recs % 100 == 0:
                                print(f"Processed {success_recs:,} deletes, with {error_recs:,} errors", flush=True)
                        finally:
                            if not shutdown and (record := in_file.readline()):
                                futures[executor.submit(delete_record, engine, record)] = record

                            del futures[f]

                print(f"\nSuccessfully deleted {success_recs:,} records, with {error_recs:,} errors")
                print(f"\nWith info responses written to {output_file}")


try:
    sz_factory = SzAbstractFactoryCore(INSTANCE_NAME, SETTINGS, verbose_logging=False)
    sz_engine = sz_factory.create_engine()
    futures_del(sz_engine, INPUT_FILE, OUTPUT_FILE)
except SzError as err:
    mock_logger("CRITICAL", err)
